package com.example.demo.jobs;

import com.example.demo.enums.AssetStatus;
import com.example.demo.enums.LeaseStatus;
import com.example.demo.enums.NotificationType;
import com.example.demo.models.Asset;
import com.example.demo.models.Budget;
import com.example.demo.models.LeaseRecord;
import com.example.demo.repositories.*;
import com.example.demo.services.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Comprehensive lifecycle alert scheduler.
 *
 * <p>Runs once per day at 07:30 UTC and dispatches in-app notifications for:
 * <ul>
 *   <li>Warranty expiry (90 / 30 / 7 days ahead)</li>
 *   <li>End-of-useful-life assets</li>
 *   <li>Insurance policy expiry (60 / 30 / 7 days ahead)</li>
 *   <li>Lease expiry (60 / 30 / 7 days ahead)</li>
 *   <li>Budget overspend / threshold breaches (≥ 80% / 100%)</li>
 *   <li>Inactive assets (IN_STOCK with no scan in 180 days)</li>
 * </ul>
 */
@Component
public class LifecycleAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(LifecycleAlertScheduler.class);

    // Warranty / insurance / lease thresholds (days)
    private static final int[] EXPIRY_THRESHOLDS = {90, 30, 7};

    // Budget thresholds (percentage of total)
    private static final BigDecimal BUDGET_WARNING_PCT = new BigDecimal("80");
    private static final BigDecimal BUDGET_CRITICAL_PCT = new BigDecimal("100");

    // Inactive asset threshold
    private static final int INACTIVE_DAYS = 180;

    private final AssetRepository assetRepository;
    private final LeaseRecordRepository leaseRecordRepository;
    private final BudgetRepository budgetRepository;
    private final OrganisationRepository organisationRepository;
    private final NotificationService notificationService;

    public LifecycleAlertScheduler(AssetRepository assetRepository,
                                   LeaseRecordRepository leaseRecordRepository,
                                   BudgetRepository budgetRepository,
                                   OrganisationRepository organisationRepository,
                                   NotificationService notificationService) {
        this.assetRepository = assetRepository;
        this.leaseRecordRepository = leaseRecordRepository;
        this.budgetRepository = budgetRepository;
        this.organisationRepository = organisationRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 7 * * *", zone = "UTC")
    public void run() {
        log.info("[LifecycleAlert] Starting lifecycle alert scan");

        checkWarrantyExpiry();
        checkEndOfLife();
        checkInsuranceExpiry();
        checkLeaseExpiry();
        checkBudgetThresholds();
        checkInactiveAssets();

        log.info("[LifecycleAlert] Scan complete");
    }

    // ── Warranty Expiry ───────────────────────────────────────────────────────

    private void checkWarrantyExpiry() {
        LocalDate today = LocalDate.now();

        for (int days : EXPIRY_THRESHOLDS) {
            LocalDate cutoff = today.plusDays(days);
            List<Asset> expiring = assetRepository.findWarrantyExpiringSoon(cutoff)
                    .stream()
                    .filter(a -> a.getWarrantyExpiryDate() != null
                            && !a.getWarrantyExpiryDate().isBefore(today)
                            && !a.getWarrantyExpiryDate().isAfter(cutoff))
                    .toList();

            for (Asset a : expiring) {
                long daysLeft = ChronoUnit.DAYS.between(today, a.getWarrantyExpiryDate());
                String title = "Warranty Expiring in " + daysLeft + " Day(s)";
                String body = "Asset '" + a.getName() + "' (tag: " + a.getAssetTag()
                        + ") warranty expires on " + a.getWarrantyExpiryDate() + ".";
                notificationService.notifyOrgAdmins(a.getOrganisation(), NotificationType.WARRANTY_EXPIRY,
                        title, body, a.getId(), "/api/v1/assets/" + a.getId());
                log.info("[LifecycleAlert] Warranty expiry alert ({} days): asset {}", daysLeft, a.getId());
            }
        }
    }

    // ── End of Useful Life ────────────────────────────────────────────────────

    private void checkEndOfLife() {
        LocalDate today = LocalDate.now();

        assetRepository.findActiveAssetsWithUsefulLife().stream()
                .filter(a -> a.getPurchaseDate() != null && a.getUsefulLifeMonths() != null)
                .filter(a -> {
                    LocalDate eolDate = a.getPurchaseDate().plusMonths(a.getUsefulLifeMonths());
                    return !eolDate.isAfter(today);
                })
                .filter(a -> a.getStatus() != AssetStatus.DISPOSED && a.getStatus() != AssetStatus.RETIRED)
                .forEach(a -> {
                    LocalDate eolDate = a.getPurchaseDate().plusMonths(a.getUsefulLifeMonths());
                    String title = "Asset Reached End of Useful Life";
                    String body = "Asset '" + a.getName() + "' (tag: " + a.getAssetTag()
                            + ") reached its end of useful life on " + eolDate
                            + ". Consider scheduling disposal or replacement.";
                    notificationService.notifyOrgAdmins(a.getOrganisation(), NotificationType.END_OF_LIFE,
                            title, body, a.getId(), "/api/v1/assets/" + a.getId());
                    log.info("[LifecycleAlert] End-of-life alert: asset {}", a.getId());
                });
    }

    // ── Insurance Expiry ──────────────────────────────────────────────────────

    private void checkInsuranceExpiry() {
        LocalDate today = LocalDate.now();

        for (int days : EXPIRY_THRESHOLDS) {
            LocalDate cutoff = today.plusDays(days);

            assetRepository.findAllByDeletedAtIsNull().stream()
                    .filter(a -> a.getInsurancePolicyExpiry() != null)
                    .filter(a -> !a.getInsurancePolicyExpiry().isBefore(today)
                            && !a.getInsurancePolicyExpiry().isAfter(cutoff))
                    .forEach(a -> {
                        long daysLeft = ChronoUnit.DAYS.between(today, a.getInsurancePolicyExpiry());
                        String title = "Insurance Expiring in " + daysLeft + " Day(s)";
                        String body = "Insurance for asset '" + a.getName() + "' (tag: " + a.getAssetTag()
                                + ") expires on " + a.getInsurancePolicyExpiry() + ". Renew to avoid coverage gap.";
                        notificationService.notifyOrgAdmins(a.getOrganisation(),
                                NotificationType.INSURANCE_EXPIRY,
                                title, body, a.getId(), "/api/v1/assets/" + a.getId());
                        log.info("[LifecycleAlert] Insurance expiry alert ({} days): asset {}", daysLeft, a.getId());
                    });
        }
    }

    // ── Lease Expiry ──────────────────────────────────────────────────────────

    private void checkLeaseExpiry() {
        LocalDate today = LocalDate.now();

        for (int days : EXPIRY_THRESHOLDS) {
            LocalDate cutoff = today.plusDays(days);
            List<LeaseRecord> expiring = leaseRecordRepository.findAll().stream()
                    .filter(l -> l.getDeletedAt() == null
                            && l.getStatus() == LeaseStatus.ACTIVE
                            && l.getEndDate() != null
                            && !l.getEndDate().isBefore(today)
                            && !l.getEndDate().isAfter(cutoff))
                    .toList();

            for (LeaseRecord lr : expiring) {
                long daysLeft = ChronoUnit.DAYS.between(today, lr.getEndDate());
                String title = "Lease Expiring in " + daysLeft + " Day(s)";
                String body = "Lease for asset '" + lr.getAsset().getName()
                        + "' with lessor '" + lr.getLessor().getName()
                        + "' expires on " + lr.getEndDate() + ".";
                notificationService.notifyOrgAdmins(lr.getOrganisation(), NotificationType.LEASE_EXPIRY,
                        title, body, lr.getId(), "/api/v1/leases/" + lr.getId());
                log.info("[LifecycleAlert] Lease expiry alert ({} days): lease {}", daysLeft, lr.getId());
            }
        }
    }

    // ── Budget Thresholds ─────────────────────────────────────────────────────

    private void checkBudgetThresholds() {
        budgetRepository.findAll().stream()
                .filter(b -> b.getDeletedAt() == null
                        && b.getTotalAmount() != null
                        && b.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                        && b.getSpentAmount() != null)
                .forEach(b -> {
                    BigDecimal spentPct = b.getSpentAmount()
                            .multiply(new BigDecimal("100"))
                            .divide(b.getTotalAmount(), 2, RoundingMode.HALF_UP);

                    if (spentPct.compareTo(BUDGET_CRITICAL_PCT) >= 0) {
                        String title = "Budget Exhausted";
                        String body = "Budget '" + b.getName() + "' has been fully spent ("
                                + spentPct + "% used). No further expenses should be charged.";
                        notificationService.notifyOrgAdmins(b.getOrganisation(),
                                NotificationType.BUDGET_THRESHOLD,
                                title, body, b.getId(), null);
                        log.warn("[LifecycleAlert] Budget {} exhausted ({}%)", b.getId(), spentPct);
                    } else if (spentPct.compareTo(BUDGET_WARNING_PCT) >= 0) {
                        String title = "Budget Warning: " + spentPct + "% Used";
                        String body = "Budget '" + b.getName() + "' is " + spentPct
                                + "% spent. Consider reviewing upcoming expenses.";
                        notificationService.notifyOrgAdmins(b.getOrganisation(),
                                NotificationType.BUDGET_THRESHOLD,
                                title, body, b.getId(), null);
                        log.warn("[LifecycleAlert] Budget {} at {}%", b.getId(), spentPct);
                    }
                });
    }

    // ── Inactive Assets ───────────────────────────────────────────────────────

    private void checkInactiveAssets() {
        Instant cutoff = Instant.now().minus(INACTIVE_DAYS, ChronoUnit.DAYS);

        assetRepository.findAllByDeletedAtIsNull().stream()
                .filter(a -> a.getStatus() == AssetStatus.IN_STOCK)
                .filter(a -> {
                    // Consider inactive if it was never scanned or was last scanned before cutoff
                    return a.getLastScannedAt() == null || a.getLastScannedAt().isBefore(cutoff);
                })
                .forEach(a -> {
                    String lastScan = a.getLastScannedAt() == null ? "never"
                            : a.getLastScannedAt().toString();
                    String title = "Inactive Asset Detected";
                    String body = "Asset '" + a.getName() + "' (tag: " + a.getAssetTag()
                            + ") has been inactive in stock for over " + INACTIVE_DAYS
                            + " days (last scan: " + lastScan + ").";
                    notificationService.notifyOrgAdmins(a.getOrganisation(), NotificationType.SYSTEM,
                            title, body, a.getId(), "/api/v1/assets/" + a.getId());
                    log.info("[LifecycleAlert] Inactive asset: {}", a.getId());
                });
    }
}
