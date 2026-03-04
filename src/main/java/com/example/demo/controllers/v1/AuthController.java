package com.example.demo.controllers.v1;

import com.example.demo.dto.UserDto;
import com.example.demo.models.User;
import com.example.demo.models.Role;
import com.example.demo.repositories.UserRepository;
import com.example.demo.repositories.OrganisationRepository;
import com.example.demo.repositories.RoleRepository;
import com.example.demo.security.JwtUtil;
import com.example.demo.enums.UserStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, OrganisationRepository organisationRepository,
            RoleRepository roleRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Register a new user
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        // Validate input
        if (!request.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid email format"));
        }

        if (request.getPassword().length() < 8) {
            return ResponseEntity.badRequest().body(Map.of("error", "Password must be at least 8 characters"));
        }

        // Check if email already exists in organization
        var existingUser = userRepository.findByEmailAndOrganisationId(
                request.getEmail(), request.getOrganisationId());
        if (existingUser.isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email already registered in this organization"));
        }

        // Validate organization exists
        var organisation = organisationRepository.findById(request.getOrganisationId())
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        // Get default role or specified role
        Role role = null;
        if (request.getRoleId() != null) {
            role = roleRepository.findById(request.getRoleId())
                    .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        } else {
            // Try to find a default USER role
            role = roleRepository.findByNameAndOrganisationId("USER", request.getOrganisationId())
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
        newUser.setRole(role);
        newUser.setStatus(UserStatus.ACTIVE);
        newUser.setOrganisation(organisation);

        User savedUser = userRepository.save(newUser);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", savedUser.getId(),
                "email", savedUser.getEmail(),
                "firstName", savedUser.getFirstName(),
                "lastName", savedUser.getLastName(),
                "message", "User registered successfully"));
    }

    /**
     * Login user and return JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // Find user by email
        var userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        User user = userOpt.get();

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid email or password"));
        }

        // Check user status
        if (user.getStatus() != UserStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "User account is " + user.getStatus().toString().toLowerCase()));
        }

        // Update last login timestamp
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Build JWT claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());

        if (user.getRole() != null) {
            String roleName = user.getRole().getName();
            claims.put("role", roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName);
            claims.put("permissions", user.getRole().getPermissions());
        }

        if (user.getOrganisation() != null) {
            claims.put("organisationId", user.getOrganisation().getId().toString());
        }

        if (user.getDepartment() != null) {
            claims.put("departmentId", user.getDepartment().getId().toString());
        }

        // Generate JWT token (24 hours expiration)
        String token = jwtUtil.generateToken(user.getEmail(), claims, 1000L * 60 * 60 * 24);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "email", user.getEmail(),
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "role", user.getRole() != null ? user.getRole().getName() : "NONE"),
                "expiresIn", 86400));
    }

    /**
     * Refresh JWT token
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid authorization header"));
        }

        String token = authHeader.substring(7);
        String email = jwtUtil.extractUsername(token);

        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid token"));
        }

        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();

        // Build new token with same claims
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("firstName", user.getFirstName());
        claims.put("lastName", user.getLastName());

        if (user.getRole() != null) {
            String roleName = user.getRole().getName();
            claims.put("role", roleName.startsWith("ROLE_") ? roleName : "ROLE_" + roleName);
        }
        if (user.getOrganisation() != null) {
            claims.put("organisationId", user.getOrganisation().getId().toString());
        }

        String newToken = jwtUtil.generateToken(email, claims, 1000L * 60 * 60 * 24);

        return ResponseEntity.ok(Map.of(
                "token", newToken,
                "expiresIn", 86400));
    }

    /**
     * Request a password reset token
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        var userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "User with this email not found"));
        }

        User user = userOpt.get();
        // Generate a random token
        String token = UUID.randomUUID().toString();

        // Set token and expiry (24 hours from now)
        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiry(Instant.now().plusSeconds(24 * 60 * 60));
        userRepository.save(user);

        // In a real application, we would send an email with the link here
        // For demonstration, we simply return it or pretend it was sent.
        return ResponseEntity.ok(Map.of(
                "message", "Password reset instructions sent to email",
                "token", token // Returning token for easy testing
        ));
    }

    /**
     * Reset password using the token
     */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        var userOpt = userRepository.findByResetPasswordToken(request.getToken());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid reset token"));
        }

        User user = userOpt.get();

        // Check if token expired
        if (user.getResetPasswordTokenExpiry() == null || user.getResetPasswordTokenExpiry().isBefore(Instant.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Reset token has expired"));
        }

        // Encode new password
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // Clear token fields
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        return ResponseEntity.ok(Map.of("message", "Password has been successfully reset"));
    }

    /**
     * Get current user profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestHeader("Authorization") String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing or invalid authorization header"));
        }

        String token = authHeader.substring(7);
        String email = jwtUtil.extractUsername(token);

        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid token"));
        }

        var userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        User user = userOpt.get();
        return ResponseEntity.ok(convertToUserDto(user));
    }

    /**
     * Logout user (client-side should discard token)
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of("message", "Logout successful. Please discard your token."));
    }

    // Request/Response classes
    public static class RegisterRequest {
        @NotBlank(message = "First name is required")
        public String firstName;

        @NotBlank(message = "Last name is required")
        public String lastName;

        @Email(message = "Email must be valid")
        @NotBlank(message = "Email is required")
        public String email;

        public String phone;

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        public String password;

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

    public static class LoginRequest {
        @Email(message = "Email must be valid")
        @NotBlank(message = "Email is required")
        public String email;

        @NotBlank(message = "Password is required")
        public String password;

        // Constructor
        public LoginRequest() {
        }

        public LoginRequest(String email, String password) {
            this.email = email;
            this.password = password;
        }

        // Getters
        public String getEmail() {
            return email;
        }

        public String getPassword() {
            return password;
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
        @Size(min = 8, message = "Password must be at least 8 characters")
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
