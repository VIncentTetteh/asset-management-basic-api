package com.assetiq.services.impl;

import com.assetiq.config.CachingConfig;
import com.assetiq.dto.PagedResponseDto;
import com.assetiq.dto.invitation.AcceptInvitationRequest;
import com.assetiq.dto.invitation.InvitationAcceptedDto;
import com.assetiq.dto.invitation.InvitationIssuedDto;
import com.assetiq.dto.invitation.InvitationPreviewDto;
import com.assetiq.dto.invitation.InviteUserRequest;
import com.assetiq.dto.invitation.PermissionDescriptionDto;
import com.assetiq.dto.invitation.UserInvitationDto;
import com.assetiq.enums.InvitationStatus;
import com.assetiq.enums.UserStatus;
import com.assetiq.exceptions.TooManyRequestsException;
import com.assetiq.models.Department;
import com.assetiq.models.EmployeeIds;
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
import com.assetiq.security.PermissionCatalogue;
import com.assetiq.security.PermissionGrantGuard;
import com.assetiq.security.RbacAuditService;
import com.assetiq.security.RolePermissionDefaults;
import com.assetiq.security.SecureTokens;
import com.assetiq.services.EmailService;
import com.assetiq.services.TenantAwareService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.services.UserInvitationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Invitations: issuing, withdrawing and redeeming them.
 *
 * <h3>What this is careful about</h3>
 * <ul>
 *   <li><b>The token is the only credential.</b> It is 256 random bits, stored
 *       only as a hash, single-use, and expiring. The invitation id is not a
 *       credential and is never accepted in its place.</li>
 *   <li><b>The invitee chooses nothing that matters.</b> Organisation, role and
 *       email all come from the stored invitation. The accept request carries a
 *       name and a password and is otherwise ignored.</li>
 *   <li><b>No cross-tenant disclosure.</b> Every lookup an administrator makes is
 *       scoped to their own organisation, so inviting an address that belongs to
 *       a different tenant behaves exactly like inviting a stranger. Nothing in
 *       any response distinguishes the two.</li>
 *   <li><b>Seats are reserved, not just checked.</b> Sending counts the
 *       outstanding invitations as well as the existing members, and acceptance
 *       checks again against the members who actually exist by then.</li>
 * </ul>
 */
@Service
@Transactional
public class UserInvitationServiceImpl extends TenantAwareService implements UserInvitationService {

    private static final Logger log = LoggerFactory.getLogger(UserInvitationServiceImpl.class);

    private final UserInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UsageLimitService usageLimitService;
    private final RbacAuditService rbacAuditService;

    /** How long an invitation link stays valid. A week survives a holiday weekend. */
    @Value("${app.invitations.ttl-hours:168}")
    private long ttlHours = 168;

    /**
     * Invitation emails one organisation may send per rolling hour. This is an
     * outbound-mail amplifier: without a ceiling, one compromised admin account
     * turns the product's reputation into a spam cannon.
     */
    @Value("${app.invitations.max-per-hour:25}")
    private int maxPerHour = 25;

    /** Minimum gap between two sends of the same invitation. */
    @Value("${app.invitations.resend-cooldown-seconds:60}")
    private long resendCooldownSeconds = 60;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String baseUrl;

    private Clock clock = Clock.systemUTC();

    public UserInvitationServiceImpl(OrganisationRepository organisationRepository,
                                     UserInvitationRepository invitationRepository,
                                     UserRepository userRepository,
                                     RoleRepository roleRepository,
                                     DepartmentRepository departmentRepository,
                                     PasswordEncoder passwordEncoder,
                                     EmailService emailService,
                                     UsageLimitService usageLimitService,
                                     RbacAuditService rbacAuditService) {
        super(organisationRepository);
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.departmentRepository = departmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.usageLimitService = usageLimitService;
        this.rbacAuditService = rbacAuditService;
    }

    // ── Issuing ───────────────────────────────────────────────────────────────

    @Override
    public InvitationIssuedDto invite(InviteUserRequest request) {
        Organisation org = requireTenantOrg();
        Instant now = clock.instant();
        String email = normaliseEmail(request.getEmail());

        // Already a colleague: say so plainly. This is safe to disclose because
        // the caller can already read their own organisation's user directory.
        if (userRepository.findByEmailAndOrganisationId(email, org.getId()).isPresent()) {
            throw new IllegalStateException(
                    "Someone with that email address is already a member of your organisation."
                            + " Change their role from the people list instead of inviting them again.");
        }

        Role role = requireRole(request.getRoleId(), org);
        // An invitation cannot hand out more than the inviter holds — otherwise
        // MANAGE_USERS alone would be a path to minting an administrator.
        assertCanGrant(role);

        Department department = resolveDepartment(request.getDepartmentId(), org);

        assertWithinSendRate(org, now);

        Optional<UserInvitation> outstandingForEmail = invitationRepository.findPendingByEmail(org, email);
        // Not "does it have an id": BaseEntity assigns one at construction, so an
        // unsaved invitation is indistinguishable from a stored one by that test.
        boolean reissue = outstandingForEmail.isPresent();
        UserInvitation invitation = outstandingForEmail.orElseGet(() -> {
            UserInvitation fresh = new UserInvitation();
            fresh.setOrganisation(org);
            fresh.setEmail(email);
            fresh.setSendCount(0);
            return fresh;
        });

        // A re-issue keeps the same row and the seat it already reserved; only a
        // new invitation adds to what is being reserved against the plan.
        long outstanding = invitationRepository.countLivePending(org, now);
        usageLimitService.assertCanAddUsers(org, reissue ? outstanding : outstanding + 1);

        invitation.setRole(role);
        invitation.setDepartment(department);
        invitation.setFirstName(trimToNull(request.getFirstName()));
        invitation.setLastName(trimToNull(request.getLastName()));
        invitation.setJobTitle(trimToNull(request.getJobTitle()));
        invitation.setNote(trimToNull(request.getNote()));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(currentUser(org).orElse(null));

        String rawToken = issueToken(invitation, now);
        UserInvitation saved = persist(invitation);

        rbacAuditService.recordInvitationSent(saved.getId(), role.getName());
        return send(saved, rawToken, org, role);
    }

    @Override
    public InvitationIssuedDto resend(UUID invitationId) {
        Organisation org = requireTenantOrg();
        Instant now = clock.instant();
        UserInvitation invitation = requireInvitation(invitationId, org);

        if (!invitation.isRedeemable(now)) {
            throw new IllegalStateException(describeUnavailable(invitation.effectiveStatus(now))
                    + " Send a new invitation instead.");
        }
        // The role may have been edited since; re-check the caller can still grant it.
        assertCanGrant(invitation.getRole());

        if (invitation.getLastSentAt() != null) {
            long since = Duration.between(invitation.getLastSentAt(), now).getSeconds();
            if (since < resendCooldownSeconds) {
                throw new TooManyRequestsException(
                        "That invitation was just sent. Try again in a moment.",
                        resendCooldownSeconds - since);
            }
        }
        assertWithinSendRate(org, now);

        String rawToken = issueToken(invitation, now);
        UserInvitation saved = invitationRepository.save(invitation);
        return send(saved, rawToken, org, saved.getRole());
    }

    @Override
    public UserInvitationDto revoke(UUID invitationId) {
        Organisation org = requireTenantOrg();
        Instant now = clock.instant();
        UserInvitation invitation = requireInvitation(invitationId, org);

        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw new IllegalStateException(
                    "That invitation has already been accepted. Deactivate the person's account instead.");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        invitation.setRevokedAt(now);
        invitation.setRevokedBy(currentPrincipal());
        // Clearing the hash is what actually stops the link: a status alone would
        // still leave a redeemable digest in the table if a later change forgot to
        // check it. The column is NOT NULL, so it is replaced, not emptied.
        invitation.setTokenHash(SecureTokens.sha256Hex("revoked:" + invitation.getId() + ":" + now.toEpochMilli()));
        UserInvitation saved = invitationRepository.save(invitation);

        rbacAuditService.recordInvitationRevoked(saved.getId(),
                saved.getRole() != null ? saved.getRole().getName() : null);
        return toDto(saved, now);
    }

    // ── Listing ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PagedResponseDto<UserInvitationDto> list(String status, int limit, int offset) {
        Organisation org = requireTenantOrg();
        Instant now = clock.instant();

        int effectiveLimit = limit > 0 ? Math.min(limit, 200) : 25;
        long effectiveOffset = Math.max(0, offset);
        Pageable pageable = PageRequest.of((int) (effectiveOffset / effectiveLimit), effectiveLimit,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<UserInvitation> page = switch (normaliseStatus(status)) {
            case "PENDING" -> invitationRepository.findLivePending(org, now, pageable);
            case "EXPIRED" -> invitationRepository.findExpired(org, now, pageable);
            case "ACCEPTED" -> invitationRepository.findByOrganisationAndStatusAndDeletedAtIsNull(
                    org, InvitationStatus.ACCEPTED, pageable);
            case "REVOKED" -> invitationRepository.findByOrganisationAndStatusAndDeletedAtIsNull(
                    org, InvitationStatus.REVOKED, pageable);
            default -> invitationRepository.findByOrganisationAndDeletedAtIsNull(org, pageable);
        };

        PagedResponseDto<UserInvitationDto> response = new PagedResponseDto<>();
        response.setTotal(page.getTotalElements());
        response.setLimit(effectiveLimit);
        response.setOffset(effectiveOffset);
        response.setItems(page.getContent().stream().map(i -> toDto(i, now)).toList());
        return response;
    }

    // ── Redeeming ─────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public InvitationPreviewDto preview(String rawToken) {
        Instant now = clock.instant();
        UUID restore = TenantContext.getOrganisationId();
        try {
            // The caller is unauthenticated; any tenant header they sent is theirs
            // to choose and must not decide which rows may be loaded.
            TenantContext.clear();
            Optional<UserInvitation> match = lookup(rawToken);
            if (match.isEmpty()) {
                return InvitationPreviewDto.invalid("UNKNOWN");
            }
            UserInvitation invitation = match.get();
            InvitationStatus effective = invitation.effectiveStatus(now);
            if (effective != InvitationStatus.PENDING) {
                return InvitationPreviewDto.invalid(effective.name());
            }

            Role role = invitation.getRole();
            return new InvitationPreviewDto(true, null,
                    invitation.getOrganisation().getName(),
                    invitation.getEmail(),
                    role.getName(), role.getDescription(),
                    invitation.getFirstName(), invitation.getLastName(),
                    displayName(invitation.getInvitedBy()),
                    invitation.getNote(),
                    invitation.getExpiresAt(),
                    describePermissions(role));
        } finally {
            if (restore != null) TenantContext.setOrganisationId(restore);
        }
    }

    @Override
    @CacheEvict(value = CachingConfig.CacheNames.USERS, allEntries = true)
    public InvitationAcceptedDto accept(AcceptInvitationRequest request) {
        Instant now = clock.instant();
        // Whatever tenant the request's header named is the caller's own choice —
        // they are unauthenticated — so it decides nothing here. It is dropped
        // before the first read and replaced by the one the token names, and it is
        // deliberately not restored afterwards: the transaction commits after this
        // method returns, and the entity-level tenant guard runs on that flush. Put
        // the caller's header back and the guard would reject our own writes.
        TenantContext.clear();
        {
            UserInvitation invitation = lookup(request.getToken())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "That invitation link is not valid. Ask for a new one."));

            InvitationStatus effective = invitation.effectiveStatus(now);
            if (effective != InvitationStatus.PENDING) {
                throw new IllegalArgumentException(describeUnavailable(effective) + " Ask for a new one.");
            }

            Organisation org = invitation.getOrganisation();
            // From here on every write belongs to the inviting organisation, and
            // the entity-level tenant guard should hold us to exactly that.
            TenantContext.setOrganisationId(org.getId());

            // Someone may have been given an account the ordinary way in the
            // meantime. Creating a second one would breach the per-org email
            // uniqueness constraint anyway; say something useful instead.
            if (userRepository.findByEmailAndOrganisationId(invitation.getEmail(), org.getId()).isPresent()) {
                throw new IllegalStateException(
                        "An account already exists for that email address in " + org.getName()
                                + ". Sign in, or use 'forgot password' if you do not know the password.");
            }

            Role role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(invitation.getRole().getId(), org)
                    .orElseThrow(() -> new IllegalStateException(
                            "The role you were invited to no longer exists. Ask for a new invitation."));

            // The seat check that actually matters: ten invitations sent while one
            // seat was free all passed the send-time check together, and only the
            // first of them can be redeemed.
            usageLimitService.assertCanCreateEmployee(org);

            User user = new User();
            user.setFirstName(request.getFirstName().trim());
            user.setLastName(request.getLastName().trim());
            user.setEmail(invitation.getEmail());
            user.setPhone(trimToNull(request.getPhone()));
            user.setJobTitle(trimToNull(request.getJobTitle()) != null
                    ? trimToNull(request.getJobTitle()) : invitation.getJobTitle());
            user.setEmployeeId(EmployeeIds.generate());
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            user.setRole(role);
            user.setDepartment(invitation.getDepartment());
            user.setOrganisation(org);
            user.setStatus(UserStatus.ACTIVE);
            // Redeeming a link sent to the address is itself proof of control of
            // the mailbox, so a second verification round-trip would only be
            // ceremony — and would lock out a tenant that has email switched off.
            user.setEmailVerifiedAt(now);
            user.setCreatedBy(invitation.getEmail());
            User saved = userRepository.save(user);

            invitation.setStatus(InvitationStatus.ACCEPTED);
            invitation.setAcceptedAt(now);
            invitation.setAcceptedUser(saved);
            // Single use: the digest that was redeemable is replaced, so presenting
            // the same link again finds nothing rather than a spent row.
            invitation.setTokenHash(SecureTokens.sha256Hex("accepted:" + invitation.getId() + ":" + now.toEpochMilli()));
            invitationRepository.save(invitation);

            rbacAuditService.recordInvitationAccepted(invitation.getId(), saved.getId(), role.getName());
            log.info("[INVITE] Invitation {} accepted; user {} created in organisation {} as {}",
                    invitation.getId(), saved.getId(), org.getId(), role.getName());

            return new InvitationAcceptedDto(saved.getId(), saved.getEmail(), org.getId(), org.getName(),
                    role.getName(),
                    "Welcome to " + org.getName() + ". Sign in with your new password to get started.");
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Optional<UserInvitation> lookup(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return invitationRepository.findByTokenHash(SecureTokens.sha256Hex(rawToken.trim()));
    }

    /** Sets a fresh token on the invitation and returns the raw value to email. */
    private String issueToken(UserInvitation invitation, Instant now) {
        String rawToken = SecureTokens.generate();
        invitation.setTokenHash(SecureTokens.sha256Hex(rawToken));
        invitation.setExpiresAt(now.plus(Duration.ofHours(ttlHours)));
        invitation.setSendCount(invitation.getSendCount() + 1);
        invitation.setLastSentAt(now);
        return rawToken;
    }

    private UserInvitation persist(UserInvitation invitation) {
        try {
            return invitationRepository.saveAndFlush(invitation);
        } catch (DataIntegrityViolationException raced) {
            // The partial unique index caught a concurrent invite to the same
            // address. Both callers wanted one live invitation; they have one.
            throw new IllegalStateException(
                    "An invitation to that email address was just created. Refresh the list to see it.");
        }
    }

    /**
     * Emails the link, and reports honestly whether it went anywhere. With email
     * switched off the link comes back in the response so the administrator can
     * pass it on — the alternative is telling them a mail was sent that was not.
     */
    private InvitationIssuedDto send(UserInvitation invitation, String rawToken, Organisation org, Role role) {
        String acceptUrl = baseUrl.replaceAll("/+$", "") + "/accept-invite?token=" + rawToken;
        boolean emailAvailable = emailService.isEnabled();

        if (emailAvailable) {
            Map<String, Object> model = new HashMap<>();
            model.put("firstName", invitation.getFirstName() != null ? invitation.getFirstName() : "");
            model.put("organisationName", org.getName());
            model.put("inviterName", displayName(invitation.getInvitedBy()));
            model.put("roleName", role.getName());
            model.put("roleDescription", role.getDescription());
            model.put("note", invitation.getNote());
            model.put("acceptUrl", acceptUrl);
            model.put("expiresHours", ttlHours);
            model.put("permissions", describePermissions(role).stream()
                    .map(PermissionDescriptionDto::label).limit(8).toList());
            emailService.sendTemplate(invitation.getEmail(),
                    "You have been invited to join " + org.getName() + " on AssetIQ",
                    "email/user-invitation", model);
        }

        invitation.setEmailDelivered(emailAvailable);
        invitationRepository.save(invitation);

        // The token is a credential: it appears in the email and, when email is
        // off, in this one response. Never in a log line.
        log.info("[INVITE] Invitation {} issued for organisation {} as role {} (email sent: {})",
                invitation.getId(), org.getId(), role.getName(), emailAvailable);

        String message = emailAvailable
                ? "Invitation sent to " + invitation.getEmail() + "."
                : "Email delivery is switched off in this environment, so no message was sent."
                        + " Share the invitation link below with " + invitation.getEmail() + " yourself.";
        return new InvitationIssuedDto(toDto(invitation, clock.instant()), emailAvailable,
                emailAvailable ? null : acceptUrl, message);
    }

    /**
     * A durable per-tenant ceiling on outbound invitation mail, counted in the
     * database rather than in Redis: the transport rate limiter fails open when
     * Redis is away, which is exactly when an abuser would like it to.
     */
    private void assertWithinSendRate(Organisation org, Instant now) {
        Instant windowStart = now.minus(Duration.ofHours(1));
        long sent = invitationRepository.countSentSince(org, windowStart);
        if (sent >= maxPerHour) {
            log.warn("[INVITE] Organisation {} reached the hourly invitation ceiling ({})", org.getId(), maxPerHour);
            throw new TooManyRequestsException(
                    "Your organisation has sent its " + maxPerHour + " invitations for this hour."
                            + " Try again shortly, or contact support if you are onboarding a large team.",
                    Duration.ofHours(1).getSeconds());
        }
    }

    private Role requireRole(UUID roleId, Organisation org) {
        return roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(roleId, org)
                .orElseThrow(() -> new IllegalArgumentException("Role not found in your organisation"));
    }

    private Department resolveDepartment(UUID departmentId, Organisation org) {
        if (departmentId == null) return null;
        return departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation"));
    }

    private UserInvitation requireInvitation(UUID id, Organisation org) {
        return invitationRepository.findByIdAndOrganisationAndDeletedAtIsNull(id, org)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found in your organisation"));
    }

    /** Refuses to hand out permissions the caller does not hold themselves. */
    private void assertCanGrant(Role role) {
        Set<String> required = role.isGrantAllPermissions()
                ? RolePermissionDefaults.allPermissionNames()
                : role.getRolePermissions().stream().map(RolePermission::getPermission).collect(Collectors.toSet());
        PermissionGrantGuard.assertCallerHolds(required, "invite someone as '" + role.getName() + "'");
    }

    private List<PermissionDescriptionDto> describePermissions(Role role) {
        Iterable<String> names = role.isGrantAllPermissions()
                ? RolePermissionDefaults.allPermissionNames()
                : role.getRolePermissions().stream().map(RolePermission::getPermission).sorted().toList();
        return PermissionCatalogue.describe(names).stream()
                .map(e -> new PermissionDescriptionDto(e.key(), e.label(), e.summary(),
                        e.group(), e.write(), e.enforced()))
                .toList();
    }

    private static String describeUnavailable(InvitationStatus status) {
        return switch (status) {
            case EXPIRED -> "That invitation has expired.";
            case REVOKED -> "That invitation was withdrawn.";
            case ACCEPTED -> "That invitation has already been used.";
            default -> "That invitation is no longer available.";
        };
    }

    private Optional<User> currentUser(Organisation org) {
        String principal = currentPrincipal();
        if (principal == null) return Optional.empty();
        return userRepository.findByEmailAndOrganisationId(principal, org.getId());
    }

    private static String currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private static String displayName(User user) {
        if (user == null) return null;
        String first = user.getFirstName() != null ? user.getFirstName() : "";
        String last = user.getLastName() != null ? user.getLastName() : "";
        String joined = (first + " " + last).trim();
        return joined.isEmpty() ? null : joined;
    }

    private UserInvitationDto toDto(UserInvitation i, Instant now) {
        Role role = i.getRole();
        Department dept = i.getDepartment();
        return new UserInvitationDto(
                i.getId(), i.getEmail(), i.effectiveStatus(now),
                role != null ? role.getId() : null, role != null ? role.getName() : null,
                dept != null ? dept.getId() : null, dept != null ? dept.getName() : null,
                i.getFirstName(), i.getLastName(), i.getJobTitle(), i.getNote(),
                i.getExpiresAt(), i.getCreatedAt(), i.getLastSentAt(),
                i.getSendCount(), i.isEmailDelivered(),
                displayName(i.getInvitedBy()),
                i.getAcceptedAt(), i.getAcceptedUser() != null ? i.getAcceptedUser().getId() : null,
                i.getRevokedAt());
    }

    private static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normaliseStatus(String status) {
        return status == null ? "" : status.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Test seam. */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
