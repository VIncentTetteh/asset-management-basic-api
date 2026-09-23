package com.assetiq.controllers.v1;

import com.assetiq.dto.PagedResponseDto;
import com.assetiq.dto.invitation.AcceptInvitationRequest;
import com.assetiq.dto.invitation.InvitationAcceptedDto;
import com.assetiq.dto.invitation.InvitationIssuedDto;
import com.assetiq.dto.invitation.InvitationPreviewDto;
import com.assetiq.dto.invitation.InvitationTokenRequest;
import com.assetiq.dto.invitation.InviteUserRequest;
import com.assetiq.dto.invitation.UserInvitationDto;
import com.assetiq.services.UserInvitationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Inviting colleagues, and joining on an invitation.
 *
 * <p>Two audiences share this path. The administrative endpoints are
 * tenant-scoped and gated on the same authorities as creating a user, because
 * inviting someone <em>is</em> creating a user, on a delay. The two acceptance
 * endpoints are necessarily public: the person calling them has no account yet,
 * which is the whole point. They authenticate with the invitation token, which
 * travels in the request body rather than the query string so it stays out of
 * access logs, browser history and referrer headers.
 */
@RestController
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final UserInvitationService invitationService;

    public InvitationController(UserInvitationService invitationService) {
        this.invitationService = invitationService;
    }

    // ── Administrative ────────────────────────────────────────────────────────

    /**
     * POST /api/v1/invitations — invite a colleague to join with a chosen role.
     *
     * <p>Answers 201 with the invitation. When the environment cannot send mail
     * the response carries the link instead, and says so.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS')")
    public ResponseEntity<InvitationIssuedDto> invite(@Valid @RequestBody InviteUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.invite(request));
    }

    /** GET /api/v1/invitations?status=PENDING&limit=25&offset=0 */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','VIEW_USERS')")
    public ResponseEntity<PagedResponseDto<UserInvitationDto>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "25") int limit,
            @RequestParam(required = false, defaultValue = "0") int offset) {
        return ResponseEntity.ok(invitationService.list(status, limit, offset));
    }

    /** POST /api/v1/invitations/{id}/resend — new token, new expiry, same person and role. */
    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS')")
    public ResponseEntity<InvitationIssuedDto> resend(@PathVariable UUID id) {
        return ResponseEntity.ok(invitationService.resend(id));
    }

    /** POST /api/v1/invitations/{id}/revoke — the link stops working immediately. */
    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS')")
    public ResponseEntity<UserInvitationDto> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(invitationService.revoke(id));
    }

    // ── Public: redeeming a link ──────────────────────────────────────────────

    /**
     * POST /api/v1/invitations/lookup — what this token is worth, for the
     * acceptance screen. Always 200: an unusable token answers
     * {@code valid: false} with a reason rather than an error status, so the page
     * can explain itself.
     */
    @PostMapping("/lookup")
    public ResponseEntity<InvitationPreviewDto> lookup(@Valid @RequestBody InvitationTokenRequest request) {
        return ResponseEntity.ok(invitationService.preview(request.getToken()));
    }

    /**
     * POST /api/v1/invitations/accept — create the account and spend the token.
     *
     * <p>No session is issued. The invitee signs in through the ordinary login
     * flow with the password they just chose, so MFA, lockout and organisation
     * disambiguation are all decided in one place rather than two.
     */
    @PostMapping("/accept")
    public ResponseEntity<InvitationAcceptedDto> accept(@Valid @RequestBody AcceptInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(invitationService.accept(request));
    }
}
