package com.assetiq.services.impl;

import com.assetiq.dto.invitation.AcceptInvitationRequest;
import com.assetiq.dto.invitation.InvitationIssuedDto;
import com.assetiq.dto.invitation.InviteUserRequest;
import com.assetiq.enums.InvitationStatus;
import com.assetiq.enums.Permission;
import com.assetiq.enums.UserStatus;
import com.assetiq.exceptions.TooManyRequestsException;
import com.assetiq.models.Organisation;
import com.assetiq.models.Role;
import com.assetiq.models.RolePermission;
import com.assetiq.models.User;
import com.assetiq.models.UserInvitation;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.repositories.UserInvitationRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.security.RbacAuditService;
import com.assetiq.security.SecureTokens;
import com.assetiq.services.EmailService;
import com.assetiq.services.UsageLimitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The invitation flow's load-bearing rules: what the token is worth, who may
 * hand out which role, and what happens at the seat limit.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Inviting a colleague")
class UserInvitationServiceImplTest {

    @Mock OrganisationRepository organisationRepository;
    @Mock UserInvitationRepository invitationRepository;
    @Mock UserRepository userRepository;
    @Mock RoleRepository roleRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock EmailService emailService;
    @Mock UsageLimitService usageLimitService;
    @Mock RbacAuditService rbacAuditService;

    private UserInvitationServiceImpl service;

    private Organisation org;
    private Role viewerRole;
    private Role adminRole;
    private final Instant now = Instant.parse("2026-03-01T10:00:00Z");

    /** The raw token most recently handed to a fixture invitation. */
    private String lastRawToken;

    @BeforeEach
    void setUp() {
        service = new UserInvitationServiceImpl(organisationRepository, invitationRepository, userRepository,
                roleRepository, departmentRepository, passwordEncoder, emailService, usageLimitService,
                rbacAuditService);
        ReflectionTestUtils.setField(service, "baseUrl", "https://app.example.com");
        ReflectionTestUtils.setField(service, "ttlHours", 168L);
        ReflectionTestUtils.setField(service, "maxPerHour", 25);
        ReflectionTestUtils.setField(service, "resendCooldownSeconds", 60L);
        service.setClock(Clock.fixed(now, ZoneOffset.UTC));

        org = organisation("Acme Ghana");
        viewerRole = role("VIEWER", Set.of(Permission.VIEW_ASSETS.name(), Permission.VIEW_REPORTS.name()));
        adminRole = grantAllRole("ADMIN");

        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(viewerRole.getId(), org))
                .thenReturn(Optional.of(viewerRole));
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(adminRole.getId(), org))
                .thenReturn(Optional.of(adminRole));
        when(userRepository.findByEmailAndOrganisationId(anyString(), any())).thenReturn(Optional.empty());
        when(invitationRepository.findPendingByEmail(any(), anyString())).thenReturn(Optional.empty());
        when(invitationRepository.countLivePending(any(), any())).thenReturn(0L);
        when(invitationRepository.countSentSince(any(), any())).thenReturn(0L);
        when(invitationRepository.saveAndFlush(any(UserInvitation.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(invitationRepository.save(any(UserInvitation.class))).thenAnswer(i -> withId(i.getArgument(0)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(emailService.isEnabled()).thenReturn(true);

        TenantContext.setOrganisationId(org.getId());
        signInAs("owner@acme.test", "ROLE_ADMIN");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ── Issuing ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the emailed link carries a token that is not stored, and the stored value is its hash")
    void tokenIsStoredOnlyAsAHash() {
        InvitationIssuedDto issued = service.invite(request("New.Person@Acme.test", viewerRole.getId()));

        ArgumentCaptor<UserInvitation> saved = ArgumentCaptor.forClass(UserInvitation.class);
        verify(invitationRepository).saveAndFlush(saved.capture());
        UserInvitation invitation = saved.getValue();

        // 64 hex characters: a SHA-256 digest, not anything guessable.
        assertThat(invitation.getTokenHash()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(invitation.getTokenHash()).isNotEqualTo(invitation.getId().toString());
        assertThat(invitation.getEmail()).isEqualTo("new.person@acme.test");
        assertThat(invitation.getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
        assertThat(issued.emailSent()).isTrue();
        // With email on, the link is never handed back in the response.
        assertThat(issued.acceptUrl()).isNull();
    }

    @Test
    @DisplayName("with email switched off, nothing pretends a message was sent")
    void emailDisabledIsReportedHonestly() {
        when(emailService.isEnabled()).thenReturn(false);

        InvitationIssuedDto issued = service.invite(request("nobody@acme.test", viewerRole.getId()));

        assertThat(issued.emailSent()).isFalse();
        assertThat(issued.acceptUrl()).contains("/accept-invite?token=");
        assertThat(issued.message()).contains("switched off");
        verify(emailService, never()).sendTemplate(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("inviting someone who is already a member is refused, not duplicated")
    void existingMemberIsRefused() {
        when(userRepository.findByEmailAndOrganisationId("already@acme.test", org.getId()))
                .thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.invite(request("already@acme.test", viewerRole.getId())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already a member");
    }

    @Test
    @DisplayName("inviting an address that already has a live invitation re-issues it instead of adding a second")
    void repeatInviteReissuesTheSameRow() {
        UserInvitation existing = pendingInvitation("again@acme.test", viewerRole, now.plus(Duration.ofDays(3)));
        existing.setSendCount(1);
        when(invitationRepository.findPendingByEmail(org, "again@acme.test")).thenReturn(Optional.of(existing));

        service.invite(request("again@acme.test", viewerRole.getId()));

        ArgumentCaptor<UserInvitation> saved = ArgumentCaptor.forClass(UserInvitation.class);
        verify(invitationRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
        assertThat(saved.getValue().getSendCount()).isEqualTo(2);
        // A new token means the link in the older email stops working.
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("a role from another organisation cannot be invited into")
    void crossTenantRoleIsRefused() {
        UUID foreignRoleId = UUID.randomUUID();
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(foreignRoleId, org))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.invite(request("someone@acme.test", foreignRoleId)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role not found in your organisation");
    }

    // ── Privilege escalation ──────────────────────────────────────────────────

    @Test
    @DisplayName("an invitation cannot grant more than the inviter holds")
    void selfEscalationThroughAnInvitationIsRefused() {
        // A user manager with no other authority tries to invite an administrator.
        signInAs("manager@acme.test", Permission.MANAGE_USERS.name());

        assertThatThrownBy(() -> service.invite(request("hopeful@acme.test", adminRole.getId())))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("permissions you do not hold");

        verify(invitationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a user manager may still invite into a role that stays within their own permissions")
    void invitingWithinYourOwnPermissionsIsAllowed() {
        signInAs("manager@acme.test", Permission.MANAGE_USERS.name(),
                Permission.VIEW_ASSETS.name(), Permission.VIEW_REPORTS.name());

        assertThat(service.invite(request("fine@acme.test", viewerRole.getId())).invitation().roleName())
                .isEqualTo("VIEWER");
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("the hourly send ceiling refuses the next invitation with a retry hint")
    void sendRateIsLimitedPerOrganisation() {
        when(invitationRepository.countSentSince(eq(org), any())).thenReturn(25L);

        assertThatThrownBy(() -> service.invite(request("flood@acme.test", viewerRole.getId())))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("25 invitations for this hour");

        verify(emailService, never()).sendTemplate(anyString(), anyString(), anyString(), any());
        verify(invitationRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("resending the same invitation twice in a row is refused by the cooldown")
    void resendHasACooldown() {
        UserInvitation existing = pendingInvitation("wait@acme.test", viewerRole, now.plus(Duration.ofDays(3)));
        existing.setLastSentAt(now.minusSeconds(5));
        when(invitationRepository.findByIdAndOrganisationAndDeletedAtIsNull(existing.getId(), org))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.resend(existing.getId()))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("just sent");
    }

    // ── Seats ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("outstanding invitations are reserved against the plan, so the eleventh at a ten-seat limit fails")
    void sendingReservesSeatsForOutstandingInvitations() {
        when(invitationRepository.countLivePending(eq(org), any())).thenReturn(4L);
        doThrow(new AccessDeniedException("Your plan allows 5 people"))
                .when(usageLimitService).assertCanAddUsers(org, 5L);

        assertThatThrownBy(() -> service.invite(request("overflow@acme.test", viewerRole.getId())))
                .isInstanceOf(AccessDeniedException.class);

        // Five: the four already outstanding, plus this one.
        verify(usageLimitService).assertCanAddUsers(org, 5L);
    }

    @Test
    @DisplayName("the seat limit is enforced again at acceptance, not only when the invitation was sent")
    void seatLimitIsEnforcedAtAcceptance() {
        UserInvitation invitation = redeemableInvitation("late@acme.test", viewerRole);
        doThrow(new AccessDeniedException("Employee limit reached for current plan."))
                .when(usageLimitService).assertCanCreateEmployee(org);

        assertThatThrownBy(() -> service.accept(acceptRequest(lastRawToken)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("limit reached");

        verify(userRepository, never()).save(any(User.class));
    }

    // ── Redeeming ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("accepting creates the user in the inviting organisation with the invited role")
    void acceptanceCreatesTheRightUserInTheRightOrg() {
        UserInvitation invitation = redeemableInvitation("joiner@acme.test", viewerRole);

        var result = service.accept(acceptRequest(lastRawToken));

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(created.capture());
        User user = created.getValue();
        assertThat(user.getEmail()).isEqualTo("joiner@acme.test");
        assertThat(user.getOrganisation()).isSameAs(org);
        assertThat(user.getRole()).isSameAs(viewerRole);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        // Redeeming a link sent to the address proves the mailbox; no second round-trip.
        assertThat(user.getEmailVerifiedAt()).isEqualTo(now);
        assertThat(user.getPasswordHash()).isEqualTo("hashed");

        assertThat(result.organisationId()).isEqualTo(org.getId());
        assertThat(result.roleName()).isEqualTo("VIEWER");
    }

    @Test
    @DisplayName("the invitee cannot pick their own organisation or role")
    void inviteeSuppliesNothingThatDecidesAccess() {
        UserInvitation invitation = redeemableInvitation("joiner@acme.test", viewerRole);
        AcceptInvitationRequest request = acceptRequest(lastRawToken);
        // Nothing on the request names an organisation, a role or an email: the
        // DTO has no such field, which is the point of this assertion.
        assertThat(request.getClass().getDeclaredFields())
                .noneMatch(f -> Set.of("organisationId", "roleId", "email").contains(f.getName()));

        service.accept(request);

        ArgumentCaptor<User> created = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(created.capture());
        assertThat(created.getValue().getRole()).isSameAs(viewerRole);
    }

    @Test
    @DisplayName("a token works once: the second attempt finds nothing")
    void tokenIsSingleUse() {
        UserInvitation invitation = redeemableInvitation("once@acme.test", viewerRole);

        service.accept(acceptRequest(lastRawToken));

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(invitation.getAcceptedAt()).isEqualTo(now);
        // The digest that was redeemable has been replaced, so the same link no
        // longer resolves to this row at all.
        assertThat(invitation.getTokenHash()).isNotEqualTo(SecureTokens.sha256Hex(lastRawToken));

        when(invitationRepository.findByTokenHash(SecureTokens.sha256Hex(lastRawToken)))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(acceptRequest(lastRawToken)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not valid");
    }

    @Test
    @DisplayName("an expired token is refused, and says so")
    void expiredTokenIsRefused() {
        UserInvitation invitation = redeemableInvitation("stale@acme.test", viewerRole);
        invitation.setExpiresAt(now.minusSeconds(1));

        assertThatThrownBy(() -> service.accept(acceptRequest(lastRawToken)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expired");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("a revoked invitation cannot be redeemed")
    void revokedTokenIsRefused() {
        UserInvitation invitation = redeemableInvitation("gone@acme.test", viewerRole);
        invitation.setStatus(InvitationStatus.REVOKED);

        assertThatThrownBy(() -> service.accept(acceptRequest(lastRawToken)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("withdrawn");
    }

    @Test
    @DisplayName("revoking replaces the redeemable digest, so the emailed link stops working immediately")
    void revokeInvalidatesTheLink() {
        UserInvitation invitation = pendingInvitation("bye@acme.test", viewerRole, now.plus(Duration.ofDays(3)));
        String hashBefore = invitation.getTokenHash();
        when(invitationRepository.findByIdAndOrganisationAndDeletedAtIsNull(invitation.getId(), org))
                .thenReturn(Optional.of(invitation));

        assertThat(service.revoke(invitation.getId()).status()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(invitation.getTokenHash()).isNotEqualTo(hashBefore);
    }

    @Test
    @DisplayName("an invitation belonging to another tenant is not found, let alone revocable")
    void crossTenantInvitationIsNotVisible() {
        UUID foreignId = UUID.randomUUID();
        when(invitationRepository.findByIdAndOrganisationAndDeletedAtIsNull(foreignId, org))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(foreignId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found in your organisation");
    }

    @Test
    @DisplayName("an unknown token gives the acceptance screen a reason, not an error")
    void previewOfAnUnknownTokenIsNotAnError() {
        when(invitationRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        var preview = service.preview("made-up-token");

        assertThat(preview.valid()).isFalse();
        assertThat(preview.reason()).isEqualTo("UNKNOWN");
        // Nothing about any organisation leaks from a wrong guess.
        assertThat(preview.organisationName()).isNull();
        assertThat(preview.email()).isNull();
    }

    @Test
    @DisplayName("a valid token shows the company, the role, and what the role can do")
    void previewDescribesTheRoleInPlainLanguage() {
        UserInvitation invitation = redeemableInvitation("curious@acme.test", viewerRole);

        var preview = service.preview(lastRawToken);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.organisationName()).isEqualTo("Acme Ghana");
        assertThat(preview.roleName()).isEqualTo("VIEWER");
        assertThat(preview.permissions()).extracting("label")
                .contains("See the asset register", "See reports");
    }

    @Test
    @DisplayName("acceptance is refused when an account for that address appeared in the meantime")
    void acceptanceRefusesWhenTheAccountAlreadyExists() {
        UserInvitation invitation = redeemableInvitation("raced@acme.test", viewerRole);
        when(userRepository.findByEmailAndOrganisationId("raced@acme.test", org.getId()))
                .thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> service.accept(acceptRequest(lastRawToken)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    /** An invitation already in the repository, with the raw token kept for the test. */
    private TokenedInvitation redeemableInvitation(String email, Role role) {
        TokenedInvitation invitation = new TokenedInvitation();
        lastRawToken = SecureTokens.generate();
        invitation.setOrganisation(org);
        invitation.setEmail(email);
        invitation.setRole(role);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(now.plus(Duration.ofDays(3)));
        invitation.setTokenHash(SecureTokens.sha256Hex(lastRawToken));
        when(invitationRepository.findByTokenHash(SecureTokens.sha256Hex(lastRawToken)))
                .thenReturn(Optional.of(invitation));
        when(roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(role.getId(), org))
                .thenReturn(Optional.of(role));
        return invitation;
    }

    /** Carries the raw token alongside the entity so a test can present it back. */
    static class TokenedInvitation extends UserInvitation {
        String rawToken;
    }

    private UserInvitation pendingInvitation(String email, Role role, Instant expiresAt) {
        UserInvitation invitation = new UserInvitation();
        invitation.setOrganisation(org);
        invitation.setEmail(email);
        invitation.setRole(role);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(expiresAt);
        invitation.setTokenHash(SecureTokens.sha256Hex(SecureTokens.generate()));
        return invitation;
    }

    private static InviteUserRequest request(String email, UUID roleId) {
        InviteUserRequest request = new InviteUserRequest();
        request.setEmail(email);
        request.setRoleId(roleId);
        return request;
    }

    private static AcceptInvitationRequest acceptRequest(String token) {
        AcceptInvitationRequest request = new AcceptInvitationRequest();
        request.setToken(token);
        request.setFirstName("Kwame");
        request.setLastName("Mensah");
        request.setPassword("correct horse battery staple");
        return request;
    }

    private static Organisation organisation(String name) {
        Organisation organisation = new Organisation();
        organisation.setName(name);
        return organisation;
    }

    private static Role role(String name, Set<String> permissions) {
        Role role = new Role();
        role.setName(name);
        for (String permission : permissions) {
            RolePermission rp = new RolePermission();
            rp.setRole(role);
            rp.setPermission(permission);
            role.getRolePermissions().add(rp);
        }
        return role;
    }

    private static Role grantAllRole(String name) {
        Role role = new Role();
        role.setName(name);
        role.setGrantAllPermissions(true);
        return role;
    }

    private static UserInvitation withId(UserInvitation invitation) {
        if (invitation.getId() == null) {
            ReflectionTestUtils.setField(invitation, "id", UUID.randomUUID());
        }
        return invitation;
    }

    private static void signInAs(String email, String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                email, "n/a", List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList()));
    }
}
