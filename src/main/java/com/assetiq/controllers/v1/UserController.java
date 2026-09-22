package com.assetiq.controllers.v1;

import com.assetiq.dto.UserDto;
import com.assetiq.services.UserService;
import com.assetiq.security.annotation.RequireFreshMfa;
import com.assetiq.validation.OnCreate;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

/**
 * H6: User management endpoints — create, list, update, deactivate,
 * assign-role.
 * All operations are scoped to the current tenant organisation via
 * TenantContext.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── Self-service endpoints (any authenticated user) ───────────────────────

    /**
     * GET /api/v1/users/me — returns the currently-authenticated user's own profile.
     * No admin role required.
     *
     * Uses Authentication directly instead of @AuthenticationPrincipal UserDetails
     * because the JWT filter sets the principal as a String (the email), not a
     * UserDetails object — @AuthenticationPrincipal would resolve to null.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> getMe(Authentication authentication) {
        return ResponseEntity.ok(userService.getMe(authentication.getName()));
    }

    /**
     * PATCH /api/v1/users/me — allows any authenticated user to update their own
     * safe personal fields (firstName, lastName, phone, jobTitle).
     * Role, status, department and organisation fields are intentionally ignored.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserDto> patchMe(
            Authentication authentication,
            @Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.patchMe(authentication.getName(), dto));
    }

    /**
     * POST /api/v1/users/me/password — change your own password. Checks the
     * current password, applies the password policy, then signs out every
     * session (including this one): the client must sign in again.
     */
    @PostMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody com.assetiq.dto.ChangePasswordRequest request) {
        userService.changeOwnPassword(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/users/me — delete your own account.
     *
     * <p>Before this, a non-admin user could only file a DSAR and wait for a
     * compliance officer; App Store 5.1.1(v) and the equivalent Play policy both
     * require an in-app path. Two proofs are required, because this is not
     * reversible: a fresh MFA step-up (the annotation) and the account password
     * (the body). The organisation's last administrator is refused with 409 —
     * a tenant nobody can administer is an outage, not a deletion; that caller
     * closes the organisation instead.
     */
    @DeleteMapping("/me")
    @RequireFreshMfa
    public ResponseEntity<Void> deleteMe(
            Authentication authentication,
            @Valid @RequestBody com.assetiq.dto.VerifyPasswordRequest request) {
        userService.deleteMe(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    // ── Admin-only endpoints ──────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> createUser(@Validated(OnCreate.class) @RequestBody UserDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(dto));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_USERS')")
    public ResponseEntity<UserDto> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','ROLE_USER','VIEW_USERS')")
    public ResponseEntity<Set<UserDto>> listUsers(
            @RequestParam(required = false) UUID departmentId) {
        if (departmentId != null) {
            return ResponseEntity.ok(userService.listUsersByDepartment(departmentId));
        }
        return ResponseEntity.ok(userService.listUsers());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> updateUser(@PathVariable UUID id,
            @Validated(OnCreate.class) @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> patchUser(@PathVariable UUID id,
            @Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.patchUser(id, dto));
    }

    @PutMapping("/{id}/deactivate")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.deactivateUser(id));
    }

    @PutMapping("/{id}/activate")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> activateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.activateUser(id));
    }

    @PutMapping("/{id}/role")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> assignRole(@PathVariable UUID id,
            @RequestParam UUID roleId) {
        return ResponseEntity.ok(userService.assignRole(id, roleId));
    }

    /**
     * Removes the user's role. They keep their login and resolve to no
     * permissions; a role could previously be changed but never taken off.
     */
    @DeleteMapping("/{id}/role")
    @RequireFreshMfa
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> clearRole(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.clearRole(id));
    }
}
