package com.example.demo.controllers.v1;

import com.example.demo.dto.UserDto;
import com.example.demo.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
            @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.patchMe(authentication.getName(), dto));
    }

    // ── Admin-only endpoints ──────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody UserDto dto) {
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
            @Valid @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> patchUser(@PathVariable UUID id,
            @RequestBody UserDto dto) {
        return ResponseEntity.ok(userService.patchUser(id, dto));
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> deactivateUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.deactivateUser(id));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS','EDIT_USER','DELETE_USER')")
    public ResponseEntity<UserDto> assignRole(@PathVariable UUID id,
            @RequestParam UUID roleId) {
        return ResponseEntity.ok(userService.assignRole(id, roleId));
    }
}
