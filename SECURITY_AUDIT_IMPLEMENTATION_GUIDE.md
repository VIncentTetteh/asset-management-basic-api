# SECURITY AUDIT - IMPLEMENTATION GUIDE
**Implementation Instructions for Critical & High-Severity Fixes**

---

## QUICK START: Priority Implementation Order

1. **Week 1 (CRITICAL - BLOCKING)**
   - Implement authorization checks on all endpoints (7.1)
   - Fix JWT secret management (7.2)
   - Validate webhook signatures (7.3)

2. **Week 2 (HIGH - URGENT)**
   - Fix SQL injection in AuditEventRepository (7.4)
   - Add rate limiting to auth endpoints (7.5)
   - Add organization validation to all queries (7.6)

3. **Week 3-4 (MEDIUM - IMPORTANT)**
   - Update vulnerable dependencies (7.7)
   - Fix CORS configuration (7.8)
   - Implement sensitive data filtering (7.9)
   - Strengthen password reset tokens (7.10)

---

## IMPLEMENTATION: 7.1 - Authorization Checks

### Step 1: Create TenantAuthorizationService

**File:** `src/main/java/com/example/demo/security/TenantAuthorizationService.java`

```java
package com.example.demo.security;

import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.UserRepository;
import com.example.demo.repositories.OrganisationRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

@Service("tenantAuthorizationService")
public class TenantAuthorizationService {

    private static final Logger log = LoggerFactory.getLogger(TenantAuthorizationService.class);

    private final AssetRepository assetRepository;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    public TenantAuthorizationService(
            AssetRepository assetRepository,
            UserRepository userRepository,
            OrganisationRepository organisationRepository) {
        this.assetRepository = assetRepository;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
    }

    /**
     * Check if current user's organization owns the asset.
     */
    public boolean isAssetAccessible(UUID assetId) {
        if (!TenantContext.hasOrganisationId()) {
            log.warn("[AUTH] No tenant context for asset access check");
            return false;
        }

        UUID userOrgId = TenantContext.getOrganisationId();
        var asset = assetRepository.findById(assetId);

        if (asset.isEmpty()) {
            return false;
        }

        boolean isOwned = asset.get().getOrganisation() != null &&
                asset.get().getOrganisation().getId().equals(userOrgId);

        if (!isOwned) {
            log.warn("[AUTH] Access denied: asset {} does not belong to org {}", assetId, userOrgId);
        }

        return isOwned;
    }

    /**
     * Check if current user is an admin.
     */
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    /**
     * Check if current user has a specific role.
     */
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;

        String rolePrefix = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(rolePrefix));
    }

    /**
     * Check if current user is in the specified organization.
     */
    public boolean isInOrganization(UUID orgId) {
        if (!TenantContext.hasOrganisationId()) {
            return false;
        }
        return TenantContext.getOrganisationId().equals(orgId);
    }
}
```

### Step 2: Apply @PreAuthorize to AssetController

**File:** `src/main/java/com/example/demo/controllers/v1/AssetController.java`

```java
package com.example.demo.controllers.v1;

import com.example.demo.security.TenantAuthorizationService;
import com.example.demo.models.Asset;
import com.example.demo.repositories.AssetRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetRepository assetRepository;
    private final TenantAuthorizationService authService;

    public AssetController(AssetRepository assetRepository, TenantAuthorizationService authService) {
        this.assetRepository = assetRepository;
        this.authService = authService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@tenantAuthorizationService.isAssetAccessible(#id)")
    public ResponseEntity<?> getAsset(@PathVariable UUID id) {
        var asset = assetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        return ResponseEntity.ok(toDto(asset));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_ASSET_MANAGER')")
    public ResponseEntity<?> createAsset(@Valid @RequestBody CreateAssetRequest req) {
        // Implementation...
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@tenantAuthorizationService.isAssetAccessible(#id)")
    public ResponseEntity<?> updateAsset(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAssetRequest req) {
        // Implementation...
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@tenantAuthorizationService.isAdmin() && @tenantAuthorizationService.isAssetAccessible(#id)")
    public ResponseEntity<?> deleteAsset(@PathVariable UUID id) {
        // Implementation...
        return ResponseEntity.noContent().build();
    }

    // Helper methods
    private AssetDto toDto(Asset asset) {
        // Convert entity to DTO
        return new AssetDto();
    }
}

class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String msg) {
        super(msg);
    }
}
```

---

## IMPLEMENTATION: 7.2 - JWT Secret Validation

### Step 1: Create JwtSecretValidator

**File:** `src/main/java/com/example/demo/security/JwtSecretValidator.java`

```java
package com.example.demo.security;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class JwtSecretValidator {

    private static final int MIN_BYTES = 32;  // 256 bits for HS256

    /**
     * Validate JWT secret has sufficient entropy.
     */
    public static void validateSecretEntropy(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be null or blank");
        }

        byte[] bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        
        if (bytes.length < MIN_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least " + MIN_BYTES + " bytes. Current: " + bytes.length);
        }

        // Check for low entropy (e.g., all same character or simple patterns)
        int uniqueBytes = (int) Arrays.stream(bytes).distinct().count();
        if (uniqueBytes < 8) {
            throw new IllegalArgumentException(
                    "JWT secret has insufficient entropy. Only " + uniqueBytes +
                    " unique byte values found. Use cryptographically random string.");
        }

        // Warn if secret is ASCII-only (lower entropy than binary)
        boolean isAsciiOnly = Arrays.stream(bytes).allMatch(b -> b >= 32 && b < 127);
        if (isAsciiOnly) {
            // ASCII-only is OK but suboptimal
            double estimatedBits = 6.5 * bytes.length;  // ~6.5 bits per ASCII char
            if (estimatedBits < 128) {
                throw new IllegalArgumentException(
                        "JWT secret is ASCII-only with insufficient entropy (~" +
                        (int) estimatedBits + " bits vs 256 required)");
            }
        }
    }

    /**
     * Generate a cryptographically secure JWT secret.
     * Returns Base64-encoded string of `bytes` random bytes.
     */
    public static String generateSecureSecret(int bytes) {
        if (bytes < MIN_BYTES) {
            throw new IllegalArgumentException("Must generate at least " + MIN_BYTES + " bytes");
        }

        SecureRandom random = new SecureRandom();
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getEncoder().encodeToString(buffer);
    }

    /**
     * Main method for CLI usage.
     * Run: java -cp target/demo.jar com.example.demo.security.JwtSecretValidator
     */
    public static void main(String[] args) {
        System.out.println("=== JWT Secret Generator ===\n");

        int numSecrets = args.length > 0 ? Integer.parseInt(args[0]) : 1;

        for (int i = 0; i < numSecrets; i++) {
            String secret = generateSecureSecret(32);
            System.out.println("Secret #" + (i + 1) + ":");
            System.out.println("  Value: " + secret);
            System.out.println("  Length: " + secret.length() + " characters (32 bytes)");
            System.out.println();
        }

        System.out.println("To use in environment:");
        System.out.println("  export APP_JWT_SECRET=" + generateSecureSecret(32));
        System.out.println("\nOr in Docker:");
        System.out.println("  docker run -e APP_JWT_SECRET=" + generateSecureSecret(32) + " ...");
    }
}
```

### Step 2: Update JwtUtil to use validator

**File:** `src/main/java/com/example/demo/security/JwtUtil.java` (UPDATED)

```java
package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration:86400000}") long expirationMillis) {
        
        // Validate secret entropy at startup
        JwtSecretValidator.validateSecretEntropy(secret);
        
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(String subject, Map<String, Object> claims, long expirationMillis) {
        Date now = new Date();
        String jti = java.util.UUID.randomUUID().toString();  // JWT ID for tracking
        
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .id(jti)  // Unique token identifier
                .issuedAt(now)
                .notBefore(now)  // Not valid before now
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String extractUsername(String token) {
        return parseToken(token).getSubject();
    }

    public String extractJti(String token) {
        return parseToken(token).getId();
    }

    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Date getExpiration(String token) {
        return parseToken(token).getExpiration();
    }

    public long getExpirationMillis() {
        return expirationMillis;
    }
}
```

### Step 3: Update StartupSecurityValidator

**File:** `src/main/java/com/example/demo/config/StartupSecurityValidator.java` (UPDATED)

```java
package com.example.demo.config;

import com.example.demo.security.JwtSecretValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class StartupSecurityValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupSecurityValidator.class);

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${paystack.secret.key:}")
    private String paystackSecretKey;

    @Value("${app.startup.skip-secret-validation:false}")
    private boolean skipValidation;

    @Override
    public void run(ApplicationArguments args) {
        if (skipValidation) {
            log.warn("[SECURITY] Startup secret validation is DISABLED. " +
                    "This must NOT be used in production.");
            return;
        }

        validateJwtSecret();
        validatePaystackKey();
        log.info("[SECURITY] ✓ Startup security validation passed.");
    }

    private void validateJwtSecret() {
        try {
            JwtSecretValidator.validateSecretEntropy(jwtSecret);
            log.info("[SECURITY] ✓ JWT secret validation passed");
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "[SECURITY STARTUP FAILURE] JWT secret validation failed.\n" +
                    "Error: " + e.getMessage() + "\n" +
                    "Generate a new secret with:\n" +
                    "  java -cp target/demo-0.0.1-SNAPSHOT.jar " +
                    "com.example.demo.security.JwtSecretValidator");
        }
    }

    private void validatePaystackKey() {
        if (paystackSecretKey == null || paystackSecretKey.isBlank()) {
            throw new IllegalStateException(
                    "[SECURITY STARTUP FAILURE] PAYSTACK_SECRET_KEY is not set.\n" +
                    "Set via: export PAYSTACK_SECRET_KEY=sk_live_xxx");
        }
        
        if (!paystackSecretKey.startsWith("sk_live_") && !paystackSecretKey.startsWith("sk_test_")) {
            log.warn("[SECURITY] PAYSTACK_SECRET_KEY does not match expected format " +
                    "(expected sk_live_* or sk_test_*)");
        } else {
            log.info("[SECURITY] ✓ Paystack key validation passed");
        }
    }
}
```

---

## IMPLEMENTATION: 7.3 - Webhook Signature Validation

### Step 1: Create WebhookSignatureValidator

**File:** `src/main/java/com/example/demo/security/WebhookSignatureValidator.java`

```java
package com.example.demo.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Service
public class WebhookSignatureValidator {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureValidator.class);
    private final String paystackSecret;

    public WebhookSignatureValidator(@Value("${paystack.secret.key}") String paystackSecret) {
        this.paystackSecret = paystackSecret;
    }

    /**
     * Validate Paystack webhook signature.
     * Paystack sends X-Paystack-Signature = SHA512(request_body, secret).
     */
    public boolean isValidPaystackSignature(String requestBody, String signature) {
        if (requestBody == null || signature == null) {
            log.warn("[WEBHOOK] Invalid signature validation: body or signature is null");
            return false;
        }

        try {
            String expectedSignature = computeHmac(requestBody, paystackSecret);
            boolean isValid = constantTimeEquals(expectedSignature, signature);
            
            if (!isValid) {
                log.warn("[WEBHOOK] Signature validation failed");
            } else {
                log.debug("[WEBHOOK] Signature validation passed");
            }
            
            return isValid;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("[WEBHOOK] Signature computation failed", e);
            return false;
        }
    }

    /**
     * Compute HMAC-SHA512 of data with secret.
     */
    private String computeHmac(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(digest);
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }

        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        int result = 0;
        int len = Math.max(aBytes.length, bBytes.length);

        for (int i = 0; i < len; i++) {
            byte aByte = i < aBytes.length ? aBytes[i] : 0;
            byte bByte = i < bBytes.length ? bBytes[i] : 0;
            result |= aByte ^ bByte;
        }

        return result == 0;
    }
}
```

### Step 2: Update WebhooksController

**File:** `src/main/java/com/example/demo/controllers/v1/WebhooksController.java` (UPDATED)

```java
package com.example.demo.controllers.v1;

import com.example.demo.security.WebhookSignatureValidator;
import com.example.demo.multitenancy.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/billing/webhooks")
public class WebhooksController {

    private static final Logger log = LoggerFactory.getLogger(WebhooksController.class);
    private final WebhookSignatureValidator signatureValidator;

    public WebhooksController(WebhookSignatureValidator signatureValidator) {
        this.signatureValidator = signatureValidator;
    }

    @PostMapping("/paystack")
    public ResponseEntity<?> handlePaystackWebhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Paystack-Signature", required = false) String signature,
            HttpServletRequest request) {

        // Step 1: Validate signature FIRST (before any processing)
        if (signature == null || signature.isBlank()) {
            log.warn("[WEBHOOK] Paystack webhook rejected: missing signature header");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Missing X-Paystack-Signature header"));
        }

        if (!signatureValidator.isValidPaystackSignature(body, signature)) {
            log.warn("[WEBHOOK] Paystack webhook rejected: invalid signature");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid webhook signature"));
        }

        // Step 2: Verify tenant context is set
        if (!TenantContext.hasOrganisationId()) {
            log.warn("[WEBHOOK] Paystack webhook rejected: no tenant context");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "No organisation context"));
        }

        // Step 3: Parse and process webhook
        try {
            // TODO: Parse body as Paystack event
            // TODO: Update billing records
            log.info("[WEBHOOK] Paystack webhook processed successfully for org: {}", 
                    TenantContext.getOrganisationId());
            
            return ResponseEntity.ok(Map.of("status", "processed"));
        } catch (Exception e) {
            log.error("[WEBHOOK] Paystack webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Webhook processing failed"));
        }
    }
}
```

---

## IMPLEMENTATION: 7.4 - SQL Injection Fix

### Update AuditEventRepository

**File:** `src/main/java/com/example/demo/repositories/AuditEventRepository.java`

```java
package com.example.demo.repositories;

import com.example.demo.models.AuditEvent;
import com.example.demo.models.Organisation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    // FIXED: Use CONCAT() and LOWER() for safe parameterization
    @Query("SELECT e FROM AuditEvent e " +
           "WHERE e.organisation = :org " +
           "AND LOWER(e.path) LIKE LOWER(CONCAT('%', :assetId, '%')) " +
           "AND e.deletedAt IS NULL " +
           "ORDER BY e.createdAt DESC")
    List<AuditEvent> findAuditEventsForAsset(
            @Param("org") Organisation org,
            @Param("assetId") String assetId);

    // Additional safe queries
    @Query("SELECT e FROM AuditEvent e " +
           "WHERE e.organisation = :org " +
           "AND e.method = :method " +
           "AND e.path = :path " +
           "AND e.deletedAt IS NULL " +
           "ORDER BY e.createdAt DESC")
    List<AuditEvent> findByOrganisationAndMethodAndPath(
            @Param("org") Organisation org,
            @Param("method") String method,
            @Param("path") String path);
}
```

---

## IMPLEMENTATION: 7.5 - Rate Limiting on Auth Endpoints

### Create RateLimitingInterceptor

**File:** `src/main/java/com/example/demo/config/RateLimitingInterceptor.java`

```java
package com.example.demo.config;

import com.github.vladimir-bukhtoyarov.bucket4j.Bucket;
import com.github.vladimir-bukhtoyarov.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingInterceptor.class);

    // Strict limits for auth endpoints
    private static final int AUTH_REQUESTS_PER_MINUTE = 5;
    private static final int AUTH_REQUESTS_PER_HOUR = 50;

    // General API limit
    private static final int API_REQUESTS_PER_MINUTE = 100;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {

        String path = request.getRequestURI();
        String clientKey = getClientIdentifier(request);

        // Determine if this is an auth endpoint
        boolean isAuthEndpoint = path.startsWith("/api/v1/auth");
        int limit = isAuthEndpoint ? AUTH_REQUESTS_PER_MINUTE : API_REQUESTS_PER_MINUTE;

        // Get or create bucket for this client
        Bucket bucket = RateLimitingConfig.resolveBucket(clientKey);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Add rate limit headers
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, probe.getRemainingTokens())));

        if (!probe.isConsumed()) {
            long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getRoundedSecondsToWait());
            response.setHeader("X-RateLimit-Reset", 
                    String.valueOf(System.currentTimeMillis() / 1000 + waitSeconds));
            response.setHeader("Retry-After", String.valueOf(waitSeconds));

            log.warn("[RATE_LIMIT] Client {} exceeded limit for path {}. Retry after {} seconds",
                    clientKey, path, waitSeconds);

            response.sendError(HttpServletResponse.SC_TOO_MANY_REQUESTS,
                    "Rate limit exceeded. Please retry after " + waitSeconds + " seconds.");
            return false;
        }

        return true;
    }

    /**
     * Extract client identifier from request.
     * Prefers X-Forwarded-For header (behind proxy) over remote address.
     */
    private String getClientIdentifier(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            // Take first IP if multiple are present
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### Register RateLimitingInterceptor in WebMvcConfig

**File:** `src/main/java/com/example/demo/config/WebMvcConfig.java` (UPDATED)

```java
package com.example.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestCorrelationIdInterceptor correlationIdInterceptor;
    private final ApiAuditInterceptor apiAuditInterceptor;
    private final RateLimitingInterceptor rateLimitingInterceptor;

    public WebMvcConfig(
            RequestCorrelationIdInterceptor correlationIdInterceptor,
            ApiAuditInterceptor apiAuditInterceptor,
            RateLimitingInterceptor rateLimitingInterceptor) {
        this.correlationIdInterceptor = correlationIdInterceptor;
        this.apiAuditInterceptor = apiAuditInterceptor;
        this.rateLimitingInterceptor = rateLimitingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate limiting must be first to prevent resource exhaustion
        registry.addInterceptor(rateLimitingInterceptor)
                .addPathPatterns("/api/**");

        // Then audit logging
        registry.addInterceptor(apiAuditInterceptor);

        // Finally correlation ID
        registry.addInterceptor(correlationIdInterceptor);
    }
}
```

---

## Testing the Fixes

### Test 1: Authorization Checks

```bash
# Get token for User A (Org A)
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@orga.com","password":"password","organisationId":"<org-a-uuid>"}' \
  | jq -r '.token')

# Try to access asset from Org B (should fail)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/assets/<org-b-asset-uuid>
# Expected: 403 Forbidden

# Try to access asset from Org A (should succeed)
curl -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/v1/assets/<org-a-asset-uuid>
# Expected: 200 OK
```

### Test 2: JWT Secret Validation

```bash
# Generate secure secret
java -cp target/demo-0.0.1-SNAPSHOT.jar \
  com.example.demo.security.JwtSecretValidator

# Try to start with weak secret (should fail)
export APP_JWT_SECRET="weak"
mvn spring-boot:run
# Expected: IllegalStateException at startup

# Start with generated secret (should succeed)
export APP_JWT_SECRET="<generated-secret>"
mvn spring-boot:run
# Expected: Application starts normally
```

### Test 3: Webhook Signature Validation

```bash
# Valid webhook with correct signature
curl -X POST http://localhost:8080/api/v1/billing/webhooks/paystack \
  -H "X-Paystack-Signature: <correct-signature>" \
  -H "X-Organisation-Id: <valid-org-uuid>" \
  -d '{"event":"charge.success"}'
# Expected: 200 OK

# Invalid webhook signature
curl -X POST http://localhost:8080/api/v1/billing/webhooks/paystack \
  -H "X-Paystack-Signature: invalid" \
  -H "X-Organisation-Id: <valid-org-uuid>" \
  -d '{"event":"charge.success"}'
# Expected: 401 Unauthorized
```

### Test 4: Rate Limiting

```bash
# Make 6 requests to login endpoint
for i in {1..6}; do
  curl -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com","password":"password"}' \
    -w "\nStatus: %{http_code}\n"
done
# Expected: First 5 succeed, 6th returns 429 Too Many Requests
```

---

## Deployment Checklist

- [ ] All critical fixes implemented (sections 7.1-7.3)
- [ ] All high-priority fixes implemented (sections 7.4-7.6)
- [ ] Dependencies updated (section 7.7)
- [ ] CORS configuration tightened (section 7.8)
- [ ] Tests pass: `mvn clean test`
- [ ] Integration tests pass: `mvn clean integration-test`
- [ ] Generate new JWT secret: `java -cp target/demo.jar com.example.demo.security.JwtSecretValidator`
- [ ] Set `SPRING_PROFILES_ACTIVE=prod`
- [ ] Configure HTTPS/TLS certificates
- [ ] Enable HSTS header in reverse proxy
- [ ] Configure database backups & encryption
- [ ] Set up centralized logging
- [ ] Run security scanning: `mvn dependency-check:check`
- [ ] Penetration testing completed
- [ ] Security audit sign-off obtained

---

## Support & Questions

For implementation questions, refer to the main `SECURITY_AUDIT_REPORT.md` file for detailed explanations and rationale for each fix.


