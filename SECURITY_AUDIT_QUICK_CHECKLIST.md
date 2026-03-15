# SECURITY AUDIT - QUICK REFERENCE CHECKLIST
**AssetIQ Security Remediation Tasks**

---

## 🔴 CRITICAL ISSUES (MUST FIX - Week 1)

### [ ] 1. Authorization Checks
- **File(s):** All controllers in `src/main/java/com/example/demo/controllers/v1/`
- **Task:** Add `@PreAuthorize` annotations to all endpoints
- **Effort:** 20-30 hours
- **Files to Create/Modify:**
  - ✏️ CREATE: `TenantAuthorizationService.java`
  - ✏️ MODIFY: All controller files (AssetController, UserController, BillingController, etc.)
- **Test:** Verify unauthorized users cannot access resources of other organizations
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.1

### [ ] 2. JWT Secret Management
- **File(s):** `security/JwtUtil.java`, `config/StartupSecurityValidator.java`
- **Task:** Implement entropy validation for JWT secrets
- **Effort:** 5-10 hours
- **Files to Create/Modify:**
  - ✏️ CREATE: `JwtSecretValidator.java`
  - ✏️ MODIFY: `JwtUtil.java` (add validation call)
  - ✏️ MODIFY: `StartupSecurityValidator.java` (use new validator)
- **Test:** Try starting with weak secret (should fail)
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.2
- **Command to Generate Secure Secret:**
  ```bash
  java -cp target/demo-0.0.1-SNAPSHOT.jar com.example.demo.security.JwtSecretValidator
  ```

### [ ] 3. Webhook Signature Validation
- **File(s):** `controllers/v1/WebhooksController.java`, `multitenancy/TenantFilter.java`
- **Task:** Validate Paystack HMAC-SHA512 signatures
- **Effort:** 8-12 hours
- **Files to Create/Modify:**
  - ✏️ CREATE: `WebhookSignatureValidator.java`
  - ✏️ MODIFY: `WebhooksController.java` (add signature check)
  - ✏️ MODIFY: `TenantFilter.java` (reject unsigned webhooks)
- **Test:** Send webhook with invalid signature (should return 401)
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.3

**Week 1 Checklist:**
- [ ] Generated new JWT secret
- [ ] Created TenantAuthorizationService
- [ ] Added @PreAuthorize to all endpoints
- [ ] Created WebhookSignatureValidator
- [ ] Updated TenantFilter for webhooks
- [ ] Updated JwtUtil with entropy validation
- [ ] All tests passing
- [ ] Code review completed
- [ ] Deployed to staging

---

## 🟠 HIGH-PRIORITY ISSUES (URGENT - Week 2)

### [ ] 4. SQL Injection Fix
- **File:** `repositories/AuditEventRepository.java:20`
- **Task:** Replace unsafe LIKE with CONCAT()
- **Effort:** 2-4 hours
- **Change:** 
  ```java
  // OLD: AND e.path LIKE %:assetId%
  // NEW: AND LOWER(e.path) LIKE LOWER(CONCAT('%', :assetId, '%'))
  ```
- **Test:** Query audit events and verify no ReDoS
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.4

### [ ] 5. Rate Limiting on Auth Endpoints
- **File(s):** `controllers/v1/AuthController.java`, `config/WebMvcConfig.java`
- **Task:** Implement rate limiting for login/forgot-password/reset-password
- **Effort:** 6-10 hours
- **Files to Create/Modify:**
  - ✏️ CREATE: `RateLimitingInterceptor.java`
  - ✏️ MODIFY: `WebMvcConfig.java` (register interceptor)
- **Settings:**
  - Auth endpoints: 5 requests/minute per IP
  - General API: 100 requests/minute per IP
- **Test:** Make 6 requests to /login (6th should fail with 429)
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.5

### [ ] 6. Organization Validation Helper
- **File(s):** All entity queries
- **Task:** Add organization ownership checks to all GET/PUT/DELETE endpoints
- **Effort:** 10-15 hours
- **Files to Create/Modify:**
  - ✏️ CREATE: `TenantAuthorizationHelper.java`
  - ✏️ MODIFY: All controller methods (add validation calls)
- **Pattern:**
  ```java
  @GetMapping("/{id}")
  public ResponseEntity<?> getAsset(@PathVariable UUID id) {
      Asset asset = assetRepository.findById(id).orElseThrow(...);
      TenantAuthorizationHelper.validateTenantAccess(asset);  // ← ADD THIS
      return ResponseEntity.ok(toDto(asset));
  }
  ```
- **Test:** Try accessing resource from different organization (should fail)
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.6

**Week 2 Checklist:**
- [ ] Fixed SQL injection in AuditEventRepository
- [ ] Created RateLimitingInterceptor
- [ ] Registered rate limiting in WebMvcConfig
- [ ] Created TenantAuthorizationHelper
- [ ] Added validation to all controllers
- [ ] Rate limiting tests passing
- [ ] Authorization tests passing
- [ ] Code review completed
- [ ] Deployed to staging

---

## 🟡 MEDIUM-PRIORITY ISSUES (IMPORTANT - Week 3-4)

### [ ] 7. Dependency Updates
- **File:** `pom.xml`
- **Task:** Upgrade vulnerable dependencies
- **Effort:** 2-5 hours
- **Changes:**
  - commons-lang3: `3.14.0` → `3.18.0` (CVE-2025-48924)
  - poi-ooxml: `5.2.5` → `5.4.0` (CVE-2025-31672)
- **Test:** Run `mvn clean dependency-tree` and verify no conflicts
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.7

### [ ] 8. CORS Configuration
- **File:** `config/CorsConfig.java`
- **Task:** Tighten CORS settings
- **Effort:** 3-5 hours
- **Changes:**
  - Remove localhost/wildcard origins in production
  - Reduce preflight cache from 3600s to 300s
  - Validate origins at startup
  - Reject credentials with wildcard
- **Environment:** Add `CORS_ALLOWED_ORIGINS` env variable
- **Test:** Test preflight requests and verify headers
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.8

### [ ] 9. Sensitive Data Logging
- **File(s):** Logging configuration, `logback-spring.xml`
- **Task:** Redact sensitive data from logs
- **Effort:** 4-6 hours
- **Files to Create/Modify:**
  - ✏️ CREATE: `SensitiveDataFilter.java`
  - ✏️ MODIFY: `logback-spring.xml` (register filter)
- **Pattern:** Redact passwords, secrets, tokens, credit cards
- **Test:** Trigger errors and verify no sensitive data in logs
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.9

### [ ] 10. Password Reset Tokens
- **File:** `controllers/v1/AuthController.java`
- **Task:** Strengthen password reset token security
- **Effort:** 5-8 hours
- **Changes:**
  - Use HMAC signatures instead of just UUIDs
  - Implement single-use enforcement
  - Add `resetPasswordTokenUsed` column to User entity
- **Files to Modify:**
  - ✏️ MODIFY: `AuthController.java` (forgot-password, reset-password methods)
  - ✏️ MODIFY: `User.java` (add `resetPasswordTokenUsed` field)
  - ✏️ RUN: `ALTER TABLE app_user ADD COLUMN reset_password_token_used BOOLEAN DEFAULT FALSE;`
- **Test:** Verify token cannot be used twice
- **Reference:** IMPLEMENTATION_GUIDE.md § 7.10

**Week 3-4 Checklist:**
- [ ] Updated commons-lang3 to 3.18.0
- [ ] Updated poi-ooxml to 5.4.0
- [ ] Ran dependency check: `mvn dependency-check:check`
- [ ] Updated CORS configuration
- [ ] Added CORS_ALLOWED_ORIGINS environment variable
- [ ] Created SensitiveDataFilter
- [ ] Updated logback-spring.xml
- [ ] Enhanced password reset tokens
- [ ] Added database column for single-use tokens
- [ ] All tests passing
- [ ] Code review completed
- [ ] Deployed to staging

---

## 📋 TESTING CHECKLIST

After each fix, verify:

### Authorization Tests
- [ ] Unauthenticated users get 401
- [ ] Users cannot access other organizations' assets
- [ ] Users cannot perform admin actions
- [ ] Role-based access control works

### JWT Tests
- [ ] Application fails to start with weak secret
- [ ] New secret is generated securely
- [ ] Token expiration is enforced
- [ ] Blacklisted tokens are rejected

### Webhook Tests
- [ ] Invalid signature returns 401
- [ ] Valid signature processes webhook
- [ ] Missing X-Organisation-Id fails
- [ ] Webhook handler validates org ownership

### Rate Limiting Tests
- [ ] First 5 login requests succeed
- [ ] 6th login request gets 429
- [ ] Rate limit headers present
- [ ] Rate limit resets after timeout

### SQL Injection Tests
- [ ] LIKE clause queries safe from injection
- [ ] No ReDoS possible with long inputs
- [ ] Queries execute efficiently

### Logging Tests
- [ ] No passwords in logs
- [ ] No tokens in logs
- [ ] No credit cards in logs
- [ ] Error messages redacted

---

## 🔧 ENVIRONMENT SETUP

### Generate JWT Secret
```bash
# Method 1: Using utility class
java -cp target/demo-0.0.1-SNAPSHOT.jar \
  com.example.demo.security.JwtSecretValidator

# Method 2: Using OpenSSL
openssl rand -base64 32

# Output: Save to environment variable
export APP_JWT_SECRET="<generated-value>"
```

### Configure Environment Variables (Production)
```bash
# REQUIRED
export SPRING_PROFILES_ACTIVE=prod
export SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/assetiq
export SPRING_DATASOURCE_USERNAME=app_user
export SPRING_DATASOURCE_PASSWORD=<secure-password>
export APP_JWT_SECRET=<generated-secret>
export PAYSTACK_SECRET_KEY=sk_live_xxxx
export CORS_ALLOWED_ORIGINS=https://app.assetiq.com,https://api.assetiq.com

# OPTIONAL
export SPRING_DATA_REDIS_HOST=redis-host
export SPRING_DATA_REDIS_PASSWORD=<redis-password>
```

### Database Migration
```bash
# For single-use password reset tokens
sqlite3 assetiq.db << 'EOF'
ALTER TABLE app_user 
  ADD COLUMN reset_password_token_used BOOLEAN DEFAULT FALSE;
EOF
```

---

## 🚀 DEPLOYMENT STEPS

### Pre-Deployment Checklist
- [ ] All fixes implemented and tested
- [ ] Code review completed
- [ ] Security scanning passed
- [ ] Integration tests pass: `mvn clean integration-test`
- [ ] Generated new JWT secret
- [ ] Environment variables configured
- [ ] HTTPS/TLS certificates ready
- [ ] Database backups configured
- [ ] Monitoring/alerting set up

### Deployment Commands
```bash
# Build with security checks
mvn clean package -DskipTests dependency-check:check

# Verify no vulnerabilities
mvn dependency-check:aggregate-report

# Start with production profile
export SPRING_PROFILES_ACTIVE=prod
export APP_JWT_SECRET=<generated-secret>
java -jar target/demo-0.0.1-SNAPSHOT.jar

# Verify application started
curl https://api.assetiq.com/api/info
# Expected: 200 OK with application info
```

### Post-Deployment Verification
- [ ] Application started without errors
- [ ] Health check endpoint responds: `GET /api/v1/health`
- [ ] Login endpoint rate limited: Send 6 requests, 6th fails
- [ ] JWT tokens are being issued and validated
- [ ] Logs are being collected centrally
- [ ] Monitoring alerts are functional
- [ ] Database encryption is active

---

## 📞 ESCALATION PATH

If issues arise during remediation:

1. **Development Questions** → Development Team Lead
2. **Security Questions** → CTO / Chief Security Officer
3. **Architecture Questions** → Technical Architect
4. **Critical Blockers** → Executive Sponsor (C-Level)

---

## 📚 REFERENCE DOCUMENTS

| Document | Purpose |
|----------|---------|
| SECURITY_AUDIT_REPORT.md | Detailed findings & explanations |
| SECURITY_AUDIT_IMPLEMENTATION_GUIDE.md | Step-by-step code fixes |
| SECURITY_AUDIT_EXECUTIVE_SUMMARY.md | Management overview |
| SECURITY_AUDIT_QUICK_CHECKLIST.md | This document |

---

## ✅ FINAL SIGN-OFF

**Audit Completion Date:** March 15, 2026  
**Remediation Start Date:** ________________  
**Planned Completion Date:** ________________  
**Actual Completion Date:** ________________  

**Responsible Parties:**
- [ ] Development Lead: ___________________
- [ ] QA Lead: ___________________
- [ ] Security Lead: ___________________
- [ ] DevOps Lead: ___________________

**Final Approval:** _____________________ (Authorized Signature)

---

## NOTES

- All code examples are provided in IMPLEMENTATION_GUIDE.md
- Estimated total remediation effort: 80-100 hours
- Can be parallelized: Critical fixes (20 hrs) + High fixes (20 hrs) + Medium fixes (20 hrs) + Testing (20-40 hrs)
- Schedule weekly security review meetings during remediation
- Document any deviations from this plan

---

**Last Updated:** March 15, 2026  
**Version:** 1.0  
**Status:** READY FOR IMPLEMENTATION


