package com.assetiq.services;

import com.assetiq.models.*;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Tenant-initiated account closure and data export.
 *
 * <p>Neither existed before. There was no way for a customer to take their data with
 * them or to leave, which is an awkward gap for any SaaS and a pointed one for
 * AssetIQ — the product sells Ghana DPA Act 843 and GDPR compliance and ships a DSAR
 * register with ERASURE and PORTABILITY request types it could not honour for its own
 * tenants.
 *
 * <p>Closure is deliberately two-phase. Confirming closure stops access immediately
 * (soft delete) but leaves the rows in place for a retention window; only after that
 * does {@code AccountPurgeJob} delete them for good. Accidental and malicious
 * deletions are both real and neither is recoverable once rows are gone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLifecycleService {

    private final OrganisationRepository organisationRepository;
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final AssetRepository assetRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SupplierRepository supplierRepository;
    private final DepartmentRepository departmentRepository;
    private final CheckoutRecordRepository checkoutRecordRepository;
    private final EmailService emailService;

    /** How long a closed account is recoverable before it is destroyed. */
    @Value("${app.account.purge-retention-days:30}")
    private int purgeRetentionDays;

    @Value("${app.email.base-url:http://localhost:3000}")
    private String emailBaseUrl;

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Build a portable snapshot of everything the calling tenant owns.
     *
     * <p>Credentials are stripped rather than exported: password hashes, MFA secrets
     * and pending tokens are account-security material, not the customer's data, and
     * an export file is the least controlled artefact the system produces — it lands
     * in inboxes, download folders and ticket attachments.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> exportTenantData() {
        Organisation org = requireCurrentOrganisation();

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now().toString());
        export.put("format", "assetiq.tenant-export.v1");
        export.put("organisation", describeOrganisation(org));
        export.put("users", userRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::describeUser).toList());
        export.put("employees", employeeRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::describeEmployee).toList());
        export.put("assets", assetRepository.findAllByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::describeAsset).toList());
        export.put("departments", departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org)
                .stream().map(d -> named(d.getId(), d.getName())).toList());
        export.put("categories", categoryRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(c -> named(c.getId(), c.getName())).toList());
        export.put("locations", locationRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(l -> named(l.getId(), l.getName())).toList());
        export.put("suppliers", supplierRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(s -> named(s.getId(), s.getName())).toList());
        export.put("checkouts", checkoutRecordRepository.findByOrganisationAndDeletedAtIsNull(org)
                .stream().map(this::describeCheckout).toList());

        log.info("[ACCOUNT] Data export generated for org {}", org.getId());
        return export;
    }

    // ── Closure ───────────────────────────────────────────────────────────────

    /**
     * Close the calling tenant's account.
     *
     * @return the instant after which the data is destroyed.
     */
    @Transactional
    public Instant requestClosure(String reason) {
        Organisation org = requireCurrentOrganisation();
        Instant purgeAfter = Instant.now().plus(Duration.ofDays(purgeRetentionDays));

        org.setDeletedAt(Instant.now());
        org.setPurgeAfter(purgeAfter);
        organisationRepository.save(org);

        log.warn("[ACCOUNT] Closure requested for org {} — purge after {} (reason: {})",
                org.getId(), purgeAfter, reason == null ? "not given" : reason);

        notifyAdmins(org, "Your AssetIQ account is scheduled for deletion",
                "email/account-closure-scheduled", Map.of(
                        "organisationName", org.getName(),
                        "purgeAfter", purgeAfter.toString(),
                        "retentionDays", purgeRetentionDays,
                        "restoreUrl", emailBaseUrl.replaceAll("/+$", "") + "/support"));

        return purgeAfter;
    }

    /**
     * Undo a closure that has not yet been purged.
     *
     * <p>Not reachable through the tenant's own session — closure revokes access, so by
     * design this is an operator action taken on the customer's behalf.
     */
    @Transactional
    public boolean cancelClosure(UUID organisationId) {
        Organisation org = organisationRepository.findById(organisationId).orElse(null);
        if (org == null || org.getDeletedAt() == null) {
            return false;
        }
        org.setDeletedAt(null);
        org.setPurgeAfter(null);
        organisationRepository.save(org);
        log.warn("[ACCOUNT] Closure cancelled for org {} — account restored", organisationId);
        return true;
    }

    /**
     * Permanently delete every account whose retention window has elapsed.
     *
     * <p>A single {@code delete} on the organisation is sufficient: every
     * {@code organisation_id} foreign key in the schema is {@code ON DELETE CASCADE},
     * so the database removes the dependent rows. That is deliberately preferred over
     * a hand-written teardown, which would silently miss each new table.
     *
     * @return how many organisations were destroyed.
     */
    @Transactional
    public int purgeExpiredAccounts() {
        List<Organisation> due =
                organisationRepository.findByPurgeAfterBeforeAndDeletedAtIsNotNull(Instant.now());

        int purged = 0;
        for (Organisation org : due) {
            try {
                UUID id = org.getId();
                String name = org.getName();
                organisationRepository.hardDeleteById(id);
                log.warn("[ACCOUNT] Purged organisation {} ({}) — retention window elapsed", id, name);
                purged++;
            } catch (Exception e) {
                // One undeletable tenant must not block the rest of the queue.
                log.error("[ACCOUNT] Failed to purge organisation {}", org.getId(), e);
            }
        }
        return purged;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Organisation requireCurrentOrganisation() {
        UUID orgId = TenantContext.getOrganisationId();
        if (orgId == null) {
            throw new IllegalStateException("No organisation context");
        }
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new IllegalStateException("Organisation not found"));
    }

    private void notifyAdmins(Organisation org, String subject, String template, Map<String, Object> baseModel) {
        for (User admin : userRepository
                .findByOrganisationAndRole_NameContainingIgnoreCaseAndDeletedAtIsNull(org, "ADMIN")) {
            try {
                Map<String, Object> model = new HashMap<>(baseModel);
                model.put("firstName", admin.getFirstName());
                emailService.sendTemplate(admin.getEmail(), subject, template, model);
            } catch (Exception e) {
                log.error("[ACCOUNT] Failed to notify admin {} for org {}", admin.getId(), org.getId(), e);
            }
        }
    }

    private Map<String, Object> describeOrganisation(Organisation org) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", org.getId());
        m.put("name", org.getName());
        m.put("registrationNumber", org.getRegistrationNumber());
        m.put("taxId", org.getTaxId());
        m.put("industry", org.getIndustry());
        m.put("country", org.getCountry());
        m.put("address", org.getAddress());
        m.put("contactEmail", org.getContactEmail());
        m.put("contactPhone", org.getContactPhone());
        m.put("timezone", org.getTimezone());
        m.put("billingCurrency", org.getBillingCurrency());
        m.put("createdAt", org.getCreatedAt());
        return m;
    }

    /** Note the omissions: passwordHash, mfaSecret and every token field. */
    private Map<String, Object> describeUser(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("firstName", u.getFirstName());
        m.put("lastName", u.getLastName());
        m.put("email", u.getEmail());
        m.put("phone", u.getPhone());
        m.put("employeeId", u.getEmployeeId());
        m.put("jobTitle", u.getJobTitle());
        m.put("role", u.getRole() == null ? null : u.getRole().getName());
        m.put("status", u.getStatus());
        m.put("mfaEnabled", u.getMfaEnabled());
        m.put("emailVerifiedAt", u.getEmailVerifiedAt());
        m.put("lastLoginAt", u.getLastLoginAt());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    private Map<String, Object> describeEmployee(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("employeeNumber", e.getEmployeeNumber());
        m.put("firstName", e.getFirstName());
        m.put("lastName", e.getLastName());
        m.put("email", e.getEmail());
        m.put("phone", e.getPhone());
        m.put("jobTitle", e.getJobTitle());
        m.put("status", e.getStatus());
        m.put("hireDate", e.getHireDate());
        m.put("terminationDate", e.getTerminationDate());
        return m;
    }

    private Map<String, Object> describeAsset(Asset a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("assetTag", a.getAssetTag());
        m.put("name", a.getName());
        m.put("serialNumber", a.getSerialNumber());
        m.put("manufacturer", a.getManufacturer());
        m.put("model", a.getModel());
        m.put("status", a.getStatus());
        m.put("condition", a.getCondition());
        m.put("purchaseCost", a.getPurchaseCost());
        m.put("purchaseDate", a.getPurchaseDate());
        m.put("category", a.getCategory() == null ? null : a.getCategory().getName());
        m.put("location", a.getLocation() == null ? null : a.getLocation().getName());
        m.put("createdAt", a.getCreatedAt());
        return m;
    }

    private Map<String, Object> describeCheckout(CheckoutRecord c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("asset", c.getAsset() == null ? null : c.getAsset().getAssetTag());
        m.put("status", c.getStatus());
        m.put("checkedOutAt", c.getCheckedOutAt());
        m.put("expectedReturnDate", c.getExpectedReturnDate());
        m.put("actualReturnDate", c.getActualReturnDate());
        m.put("employee", c.getEmployee() == null ? null : c.getEmployee().getEmployeeNumber());
        return m;
    }

    private static Map<String, Object> named(UUID id, String name) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        return m;
    }
}
