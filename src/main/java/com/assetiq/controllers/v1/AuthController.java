package com.assetiq.controllers.v1;

import com.assetiq.dto.UserDto;
import com.assetiq.models.User;
import com.assetiq.models.Role;
import com.assetiq.repositories.UserRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.RoleRepository;
import com.assetiq.security.JwtBlacklist;
import com.assetiq.security.JwtUtil;
import com.assetiq.security.PermissionCacheService;
import com.assetiq.services.EmailService;
import com.assetiq.services.EmailVerificationService;
import com.assetiq.services.RefreshSessionService;
import com.assetiq.services.SessionRevocationService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.enums.UserStatus;
import com.assetiq.multitenancy.TenantContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.InvalidKeyException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Base64;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Lock account after this many consecutive failed password attempts. */
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 10;
    /** How long to lock the account once the threshold is reached. */
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final UsageLimitService usageLimitService;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtBlacklist jwtBlacklist;
    private final EmailService emailService;
    private final PermissionCacheService permissionCacheService;
    private final EmailVerificationService emailVerificationService;
    private final RefreshSessionService refreshSessionService;
    private final SessionRevocationService sessionRevocationService;

    @Value("${app.jwt.expiration:86400000}")
    private long jwtExpirationMillis;

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String emailBaseUrl;

    // Secure-by-default: only ever relaxed via an explicit profile override
    // (application-dev.yml sets this to false for plain-HTTP localhost dev).
    @Value("${app.auth.cookie-secure:true}")
    private boolean authCookieSecure;

    // Secure-by-default, same convention as cookie-secure above: production enforces,
    // dev and test relax it so local work and the test suite don't need a mail server.
    @Value("${app.auth.require-email-verification:true}")
    private boolean requireEmailVerification;

    public AuthController(UserRepository userRepository, OrganisationRepository organisationRepository,
            RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
            JwtBlacklist jwtBlacklist, EmailService emailService,
            PermissionCacheService permissionCacheService,
            EmailVerificationService emailVerificationService,
            RefreshSessionService refreshSessionService,
            SessionRevocationService sessionRevocationService,
            UsageLimitService usageLimitService) {
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.jwtBlacklist = jwtBlacklist;
        this.emailService = emailService;
        this.permissionCacheService = permissionCacheService;
        this.emailVerificationService = emailVerificationService;
        this.refreshSessionService = refreshSessionService;
        this.sessionRevocationService = sessionRevocationService;
        this.usageLimitService = usageLimitService;
    }

    /**
     * Register a new user
     */
    @PostMapping("/register")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ROLE_ORG_ADMIN','MANAGE_USERS')")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Validate input
        if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email format"));
        }

        if (request.getPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
        }

        UUID tenantId = TenantContext.getOrganisationId();
        if (tenantId == null) {
            throw new AccessDeniedException("An authenticated organisation context is required");
        }
        if (request.getOrganisationId() != null && !tenantId.equals(request.getOrganisationId())) {
            throw new AccessDeniedException("Users can only be created in the authenticated organisation");
        }

        // Check if email already exists in the authenticated organisation
        var existingUser = userRepository.findByEmailAndOrganisationId(
                request.getEmail(), tenantId);
        if (existingUser.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered in this organization"));
        }

        // Validate organization exists
        var organisation = organisationRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        // Same plan limit as the user-management create path; without it this endpoint
        // let a tenant add users past its plan (and past a downgrade) without paying.
        usageLimitService.assertCanCreateEmployee(organisation);

        // Get default role or specified role
        Role role = null;
        if (request.getRoleId() != null) {
            role = roleRepository.findByIdAndOrganisationAndDeletedAtIsNull(request.getRoleId(), organisation)
                    .orElseThrow(() -> new IllegalArgumentException("Role not found in your organisation"));
        } else {
            // Try to find a default USER role
            role = roleRepository.findByNameAndOrganisationId("USER", tenantId)
                    .orElse(null);
        }

        // Create new user
        User newUser = new User();
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setEmail(request.getEmail());
        newUser.setPhone(request.getPhone());
        newUser.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        newUser.setJobTitle(request.getJobTitle());
        newUser.setEmployeeId(com.assetiq.models.EmployeeIds.generate());
        newUser.setRole(role);
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setOrganisation(organisation);

        User savedUser = userRepository.save(newUser);
        if (requireEmailVerification) {
            emailVerificationService.sendVerificationEmail(savedUser);
        } else {
            savedUser.setEmailVerifiedAt(Instant.now());
            userRepository.save(savedUser);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", savedUser.getId(),
                "email", savedUser.getEmail(),
                "firstName", savedUser.getFirstName(),
                "lastName", savedUser.getLastName(),
                "message", requireEmailVerification
                        ? "User created. They must verify their email before signing in."
                        : "User registered successfully"));
    }

    /**
     * Login user and return JWT token.
     * In multi-tenant deployments the same email can exist in multiple organisations.
     * Supply {@code organisationId} to disambiguate. Without it, an email in several
     * organisations signs in to the one account that accepts the password; if more
     * than one does, the answer is 409 {@code ORGANISATION_REQUIRED} listing only
     * those organisations (see {@link #resolveAmongTenants}).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletResponse servletResponse) {
        User user;
        boolean passwordVerified = false;
        if (request.getOrganisationId() != null) {
            // Scoped lookup — unambiguous even in multi-tenant deployments
            var userOpt = userRepository.findByEmailAndOrganisationId(
                    request.getEmail(), request.getOrganisationId());
            if (userOpt.isEmpty()) {
                return invalidCredentials();
            }
            user = userOpt.get();
        } else {
            List<User> matches = userRepository.findAllByEmail(request.getEmail());
            if (matches.isEmpty()) {
                return invalidCredentials();
            }
            if (matches.size() == 1) {
                user = matches.get(0);
            } else {
                LoginResolution resolution = resolveAmongTenants(matches, request.getPassword());
                if (resolution.response() != null) {
                    return resolution.response();
                }
                user = resolution.user();
                passwordVerified = true;
            }
        }

        if (!passwordVerified) {
            // ── Account lockout check ─────────────────────────────────────────
            if (user.isLockedOut()) {
                return lockedOut(user);
            }
            if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                recordFailedLogin(user);
                return invalidCredentials();
            }
        }

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "User account is " + user.getStatus().toString().toLowerCase()));
        }

        // ── Email verification gate ──────────────────────────────────────────
        // Placed after the password check on purpose: an attacker must already know
        // the password before this response tells them anything, so it leaks no
        // information about which addresses are registered.
        //
        // Users created before V26 were backfilled as verified, so enabling this
        // cannot lock out an existing account.
        if (requireEmailVerification && !user.isEmailVerified()) {
            log.info("[AUTH] Login blocked pending email verification for user {}", user.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "Please verify your email address before signing in.",
                            // Machine-readable so the client can offer "resend" rather than
                            // having to string-match the message.
                            "emailVerificationRequired", true));
        }

        // ── MFA check ────────────────────────────────────────────────────────
        // If the user has MFA enabled, do NOT issue the full JWT yet.
        // Instead return a short-lived challenge token that the client must
        // exchange at POST /api/v1/auth/mfa-challenge with a valid TOTP code.
        if (Boolean.TRUE.equals(user.getMfaEnabled()) && user.getMfaSecret() != null) {
            Map<String, Object> challengeClaims = new HashMap<>();
            challengeClaims.put("mfaChallenge", true);
            challengeClaims.put("userId", user.getId().toString());
            challengeClaims.put("sessionVersion", user.getSessionVersion());
            if (user.getOrganisation() != null) {
                challengeClaims.put("organisationId", user.getOrganisation().getId().toString());
            }
            // Challenge token is valid for 5 minutes only
            String challengeToken = jwtUtil.generateToken(user.getEmail(), challengeClaims, 5 * 60 * 1000L);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "mfaRequired", true,
                            "mfaChallengeToken", challengeToken));
        }

        // Reset brute-force counters on successful login
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Build JWT claims — permissions are intentionally excluded (B-6).
        // They are loaded live from the permission cache on every request so that
        // role changes take effect immediately without re-login.
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("sessionVersion", user.getSessionVersion());

        if (user.getRole() != null) {
            String roleName = user.getRole().getName();
            claims.put("role", roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName);
            // permissions claim removed — live cache is the source of truth
        }

        if (user.getOrganisation() != null) {
            claims.put("organisationId", user.getOrganisation().getId().toString());
        }

        if (user.getDepartment() != null) {
            claims.put("departmentId", user.getDepartment().getId().toString());
        }

        String token = jwtUtil.generateToken(user.getEmail(), claims, jwtExpirationMillis);
        RefreshSessionService.IssuedRefreshToken refresh = refreshSessionService.issue(user);

        // F-1: set the JWT as an HttpOnly cookie so JavaScript cannot read it.
        // The Authorization: Bearer header path is preserved for API clients and
        // the desktop app — both paths are accepted by JwtAuthenticationFilter.
        setAuthCookie(servletResponse, token, jwtExpirationMillis / 1000);
        setRefreshCookie(servletResponse, refresh.token(), refresh.expiresAt());

        return ResponseEntity.ok(Map.of(
                // token is still returned in the body for clients that need it
                // (e.g. desktop app, Postman). The browser will use the cookie.
                "token", token,
                "refreshToken", refresh.token(),
                "tokenType", "Bearer",
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "role", user.getRole() != null ? user.getRole().getName() : "NONE"),
                "expiresIn", jwtExpirationMillis / 1000));
    }

    /**
     * Refresh JWT token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshHeader,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            HttpServletResponse servletResponse) {
        String rawRefreshToken = firstNonBlank(refreshHeader, refreshCookie);
        final RefreshSessionService.RotatedRefreshToken rotated;
        try {
            rotated = refreshSessionService.rotate(rawRefreshToken);
        } catch (org.springframework.security.access.AccessDeniedException rejected) {
            clearAuthCookies(servletResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", rejected.getMessage()));
        }

        User user = rotated.user();

        // Build new token — permissions intentionally excluded (B-6)
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());
        claims.put("sessionVersion", user.getSessionVersion());

        if (user.getRole() != null) {
            String roleName = user.getRole().getName();
            claims.put("role", roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName);
        }
        if (user.getOrganisation() != null) {
            claims.put("organisationId", user.getOrganisation().getId().toString());
        }
        if (user.getDepartment() != null) {
            claims.put("departmentId", user.getDepartment().getId().toString());
        }

        String newToken = jwtUtil.generateToken(user.getEmail(), claims, jwtExpirationMillis);

        // F-1: refresh also re-issues the HttpOnly cookie
        setAuthCookie(servletResponse, newToken, jwtExpirationMillis / 1000);
        setRefreshCookie(servletResponse, rotated.token(), rotated.expiresAt());

        return ResponseEntity.ok(Map.of(
                "token", newToken,
                "refreshToken", rotated.token(),
                "tokenType", "Bearer",
                "expiresIn", jwtExpirationMillis / 1000));
    }

    /**
     * Redeem a signup verification link.
     *
     * <p>Public by necessity — the whole point is that the user cannot sign in yet.
     * Safe because the token is 32 random bytes plus an HMAC signature and is
     * single-use; guessing one is not a realistic attack.
     */
    @PostMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestBody Map<String, String> body) {
        String token = body == null ? null : body.get("token");

        return emailVerificationService.verify(token)
                .<ResponseEntity<?>>map(user -> ResponseEntity.ok(Map.of(
                        "message", "Email verified. You can now sign in.",
                        "email", user.getEmail())))
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.of(
                        "error", "This verification link is invalid or has expired. "
                                + "Request a new one and try again.")));
    }

    /**
     * Send a fresh verification link.
     *
     * <p>Always responds identically whether or not the address exists or is already
     * verified — same user-enumeration defence as {@code /forgot-password}.
     */
    @PostMapping("/resend-verification")
    public ResponseEntity<?> resendVerification(@RequestBody Map<String, String> body) {
        String email = body == null ? null : body.get("email");

        if (email != null && !email.isBlank()) {
            // The same address can exist in several organisations; each account
            // verifies independently, so every unverified match gets a link.
            for (User user : userRepository.findAllByEmail(email)) {
                if (!user.isEmailVerified()) {
                    emailVerificationService.sendVerificationEmail(user);
                }
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "If that address needs verification, a new link is on its way."));
    }

    /**
     * Request a password reset token.
     * In multi-tenant deployments the same email may exist in multiple organisations.
     * A reset token is issued for every matching account so the user receives instructions
     * regardless of which organisation they intended.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        // Always return the same response to prevent user-enumeration attacks.
        // Reset tokens are delivered via email only — never exposed in the HTTP response.
        List<User> users = userRepository.findAllByEmail(request.getEmail());
        for (User user : users) {
            try {
                String resetToken = generateSecureResetToken(user);
                // Store only the SHA-256 hash — protects against DB breach exposing usable tokens.
                user.setResetPasswordToken(sha256Hex(resetToken));
                user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(24 * 60 * 60));
                user.setResetPasswordTokenUsed(false);  // Mark as unused
                userRepository.save(user);
                String resetUrl = emailBaseUrl.replaceAll("/+$", "") +
                        "/reset-password?token=" + resetToken;
                Map<String, Object> model = new HashMap<>();
                model.put("firstName", user.getFirstName());
                model.put("email", user.getEmail());
                model.put("resetUrl", resetUrl);
                model.put("expiresHours", 24);
                emailService.sendTemplate(user.getEmail(), "Reset your password", "email/password-reset", model);
            } catch (Exception e) {
                // Log error but continue processing other users
                log.error("[AUTH] Failed to generate reset token for user {}", user.getId());
            }
        }

        return ResponseEntity.ok(Map.of(
                "message", "If an account with that email exists, password reset instructions have been sent"));
    }

    /**
     * Reset password using the token.
     * Enforces single-use of reset tokens to prevent reuse attacks.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        var userOpt = userRepository.findByResetPasswordToken(sha256Hex(request.getToken()));
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid reset token"));
        }

        User user = userOpt.get();

        // Check if token was already used
        if (user.getResetPasswordTokenUsed() != null && user.getResetPasswordTokenUsed()) {
            log.warn("[AUTH] Attempt to reuse password reset token for user {}", user.getId());
            return ResponseEntity.badRequest().body(Map.of("error", "Reset token has already been used"));
        }

        // Check if token expired
        if (user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry().isBefore(Instant.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reset token has expired"));
        }

        // Encode new password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // Clear token fields and mark as used
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        user.setResetPasswordTokenUsed(true);
        userRepository.save(user);
        sessionRevocationService.revokeAll(user);

        log.info("[AUTH] Password reset successful for user {}", user.getId());
        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset"));
    }

    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie) {
        // F-1: accept token from Bearer header OR HttpOnly cookie
        String token = resolveToken(authHeader, authCookie);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid authorization"));
        }
        io.jsonwebtoken.Claims profileClaims;
        try {
            profileClaims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
        }
        String email = profileClaims.getSubject();
        String profileOrgIdStr = profileClaims.get("organisationId", String.class);

        Optional<User> userOpt;
        if (profileOrgIdStr != null && !profileOrgIdStr.isBlank()) {
            try {
                userOpt = userRepository.findByEmailAndOrganisationId(
                        email, UUID.fromString(profileOrgIdStr));
            } catch (IllegalArgumentException e) {
                userOpt = Optional.empty();
            }
        } else {
            userOpt = userRepository.findByEmail(email);
        }
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        return ResponseEntity.ok(convertToUserDto(user));
    }

    /**
     * Returns the live set of permissions for the currently authenticated user.
     * Backed by {@link PermissionCacheService} — reflects role changes immediately
     * without requiring a re-login or token refresh.
     */
    @GetMapping("/me/permissions")
    public ResponseEntity<?> getMyPermissions(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie) {
        // F-1: accept token from Bearer header OR HttpOnly cookie
        String token = resolveToken(authHeader, authCookie);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid authorization"));
        }
        io.jsonwebtoken.Claims parsedClaims;
        try {
            parsedClaims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
        }
        String email = parsedClaims.getSubject();
        String orgId = parsedClaims.get("organisationId", String.class);
        List<String> permissions = permissionCacheService.getPermissionsForUser(email, orgId);
        return ResponseEntity.ok(Map.of("permissions", permissions));
    }

    /**
     * Logout user — blacklists the current JWT so it cannot be reused.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @CookieValue(value = AUTH_COOKIE_NAME, required = false) String authCookie,
            @RequestHeader(value = "X-Refresh-Token", required = false) String refreshHeader,
            @CookieValue(value = REFRESH_COOKIE_NAME, required = false) String refreshCookie,
            HttpServletResponse servletResponse) {
        // F-1: resolve token from Bearer header OR cookie, then blacklist it
        String token = resolveToken(authHeader, authCookie);
        if (token != null) {
            try {
                java.util.Date expiry = jwtUtil.getExpiration(token);
                long ttlMillis = expiry.getTime() - System.currentTimeMillis();
                if (ttlMillis > 0) {
                    jwtBlacklist.invalidate(token, java.time.Duration.ofMillis(ttlMillis));
                }
            } catch (Exception ignored) {
                // Token may already be expired or invalid — still clear the cookie
            }
        }
        refreshSessionService.revoke(firstNonBlank(refreshHeader, refreshCookie));
        // F-1: clear the HttpOnly cookie regardless of header presence
        clearAuthCookies(servletResponse);
        return ResponseEntity.ok(Map.of("message", "Logout successful."));
    }

    /** Revoke every device/session for the authenticated account. */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll(
            org.springframework.security.core.Authentication authentication,
            HttpServletResponse servletResponse) {
        UUID organisationId = TenantContext.getOrganisationId();
        if (authentication == null || organisationId == null) {
            clearAuthCookies(servletResponse);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Authenticated account is required"));
        }
        User user = userRepository.findByEmailAndOrganisationId(authentication.getName(), organisationId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated account no longer exists"));
        sessionRevocationService.revokeAll(user);
        clearAuthCookies(servletResponse);
        return ResponseEntity.ok(Map.of("message", "All sessions have been revoked."));
    }

    // Request/Response classes
    public static class RegisterRequest {
        @NotBlank(message = "First name is required")
        @Size(max = 255)
        public String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 255)
        public String lastName;

        @Email(message = "Email must be valid")
        @NotBlank(message = "Email is required")
        @Size(max = 255)
        public String email;

        @Size(max = 255)
        public String phone;

        @NotBlank(message = "Password is required")
        @com.assetiq.validation.ValidPassword
        public String password;

        @Size(max = 255)
        public String jobTitle;

        @jakarta.validation.constraints.NotNull(message = "Organisation ID is required")
        public UUID organisationId;

        public UUID roleId;

        // Constructor
        public RegisterRequest() {
        }

        public RegisterRequest(String firstName, String lastName, String email, String phone,
                String password, String jobTitle, UUID organisationId, UUID roleId) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.phone = phone;
            this.password = password;
            this.jobTitle = jobTitle;
            this.organisationId = organisationId;
            this.roleId = roleId;
        }

        // Getters
        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }

        public String getPassword() {
            return password;
        }

        public String getJobTitle() {
            return jobTitle;
        }

        public UUID getOrganisationId() {
            return organisationId;
        }

        public UUID getRoleId() {
            return roleId;
        }
    }

    /** Outcome of picking an account for an email registered in several tenants. */
    private record LoginResolution(User user, ResponseEntity<?> response) { }

    /**
     * Picks the account to sign in to when an email exists in several organisations
     * and the client did not name one.
     *
     * <p>The password is checked against every unlocked account first, so the
     * organisation list in the 409 only ever names tenants whose password the
     * caller has just proved. A wrong password gets the same 401 as an unknown
     * email, so an unauthenticated caller learns nothing about tenant membership.
     * When exactly one account accepts the password (preferring ACTIVE accounts),
     * the login continues with it and no choice is needed.
     */
    private LoginResolution resolveAmongTenants(List<User> matches, String password) {
        List<User> unlocked = matches.stream().filter(u -> !u.isLockedOut()).toList();
        if (unlocked.isEmpty()) {
            return new LoginResolution(null, lockedOut(matches.get(0)));
        }
        List<User> verified = new ArrayList<>();
        for (User candidate : unlocked) {
            if (passwordEncoder.matches(password, candidate.getPasswordHash())) {
                verified.add(candidate);
            }
        }
        if (verified.isEmpty()) {
            unlocked.forEach(this::recordFailedLogin);
            return new LoginResolution(null, invalidCredentials());
        }
        List<User> active = verified.stream().filter(u -> u.getStatus() == UserStatus.ACTIVE).toList();
        if (active.size() == 1) {
            return new LoginResolution(active.get(0), null);
        }
        if (active.isEmpty()) {
            // Let the normal status check explain why the account cannot sign in.
            return new LoginResolution(verified.get(0), null);
        }
        List<Map<String, String>> organisations = active.stream()
                .filter(u -> u.getOrganisation() != null)
                .map(u -> Map.of(
                        "id", u.getOrganisation().getId().toString(),
                        "name", u.getOrganisation().getName()))
                .sorted(Comparator.comparing(m -> m.get("name").toLowerCase(Locale.ROOT)))
                .toList();
        return new LoginResolution(null, ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "error", "This email belongs to several organisations. Choose one to sign in to.",
                "code", "ORGANISATION_REQUIRED",
                "organisations", organisations)));
    }

    private void recordFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
            log.warn("[AUTH] Account locked for user {} after {} failed attempts", user.getId(), attempts);
        }
        userRepository.save(user);
    }

    private static ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid email or password"));
    }

    private static ResponseEntity<Map<String, String>> lockedOut(User user) {
        log.warn("[AUTH] Login rejected — account locked until {} for user {}", user.getLockedUntil(), user.getId());
        return ResponseEntity.status(HttpStatus.LOCKED)
                .body(Map.of("error",
                        "Account temporarily locked due to too many failed attempts. "
                                + "Try again after " + user.getLockedUntil()));
    }

    public static class LoginRequest {
        @Email(message = "Email must be valid")
        @NotBlank(message = "Email is required")
        public String email;

        @NotBlank(message = "Password is required")
        public String password;

        /**
         * Optional. When the email belongs to several organisations and more than one
         * accepts the password, login answers 409 ORGANISATION_REQUIRED with the
         * candidate organisations; the client resends with this set.
         */
        public UUID organisationId;

        public LoginRequest() {
        }

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
        }

        public UUID getOrganisationId() {
            return organisationId;
        }
    }

    public static class ForgotPasswordRequest {
        @Email(message = "Email must be valid")
        @NotBlank(message = "Email is required")
        public String email;

        public ForgotPasswordRequest() {
        }

        public ForgotPasswordRequest(String email) {
            this.email = email;
        }

        public String getEmail() {
            return email;
        }
    }

    public static class ResetPasswordRequest {
        @NotBlank(message = "Token is required")
        public String token;

        @NotBlank(message = "New password is required")
        @com.assetiq.validation.ValidPassword
        public String newPassword;

        public ResetPasswordRequest() {
        }

        public ResetPasswordRequest(String token, String newPassword) {
            this.token = token;
            this.newPassword = newPassword;
        }

        public String getToken() {
            return token;
        }

        public String getNewPassword() {
            return newPassword;
        }
    }

    // ── F-1: Cookie helpers ───────────────────────────────────────────────────

    /** Name of the HttpOnly cookie that carries the JWT for browser clients. */
    static final String AUTH_COOKIE_NAME = "access_token";
    static final String REFRESH_COOKIE_NAME = "refresh_token";

    /**
     * Sets the JWT as an HttpOnly, Secure, SameSite=Strict cookie.
     * Browser clients use this cookie automatically; API/desktop clients continue
     * to use the Authorization: Bearer header.
     */
    private void setAuthCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite("Strict")    // CSRF protection
                .path("/api")          // Only sent to API paths
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void setRefreshCookie(HttpServletResponse response, String token, Instant expiresAt) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), expiresAt).toSeconds());
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /** Clears the auth cookie on logout. */
    private void clearAuthCookies(HttpServletResponse response) {
        ResponseCookie clear = ResponseCookie.from(AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite("Strict")
                .path("/api")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clear.toString());
        ResponseCookie clearRefresh = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(authCookieSecure)
                .sameSite("Strict")
                .path("/api/v1/auth")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }

    /**
     * Resolves the JWT from either the Authorization: Bearer header or the
     * HttpOnly browser cookie. Header wins so API clients and the Electron main
     * process can override a stale browser cookie during development.
     */
    private static String resolveToken(String authHeader, String authCookie) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }
        if (authCookie != null && !authCookie.isBlank()) {
            return authCookie.trim();
        }
        return null;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /**
     * Generate a cryptographically secure password reset token using HMAC-SHA256.
     * Combines random bytes with HMAC signature to prevent forgery.
     */
    private String generateSecureResetToken(User user) throws Exception {
        // 1. Generate 32 random bytes
        SecureRandom random = new SecureRandom();
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);

        // 2. Create HMAC signature: HMAC-SHA256(randomBytes || userId || timestamp)
        String data = Base64.getEncoder().encodeToString(randomBytes) + 
                      user.getId() + 
                      System.currentTimeMillis();

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // 3. Combine: randomBytes + signature (return as Base64 URL-safe)
        byte[] combined = new byte[randomBytes.length + signature.length];
        System.arraycopy(randomBytes, 0, combined, 0, randomBytes.length);
        System.arraycopy(signature, 0, combined, randomBytes.length, signature.length);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
    }

    // Helper method
    private UserDto convertToUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setEmployeeId(user.getEmployeeId());
        dto.setJobTitle(user.getJobTitle());
        if (user.getRole() != null) {
            dto.setRoleId(user.getRole().getId());
        }
        dto.setStatus(user.getStatus());
        if (user.getOrganisation() != null) {
            dto.setOrganisationId(user.getOrganisation().getId());
        }
        if (user.getDepartment() != null) {
            dto.setDepartmentId(user.getDepartment().getId());
        }
        return dto;
    }
}
