package com.assetiq.jobs;

import com.assetiq.assets.AssetLabels;
import com.assetiq.enums.NotificationType;
import com.assetiq.models.Asset;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.LeaseRecordRepository;
import com.assetiq.services.NotificationService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bounded, idempotent lifecycle alert scheduler.
 *
 * <p>Every database scan is filtered and paged. Expiry notifications are sent
 * only at the configured milestones, and stable deduplication keys make job
 * retries safe across restarts.</p>
 */
@Component
public class LifecycleAlertScheduler {

    private static final Logger log = LoggerFactory.getLogger(LifecycleAlertScheduler.class);
    private static final int[] EXPIRY_THRESHOLDS = {90, 30, 7};
    private static final BigDecimal BUDGET_WARNING_PCT = new BigDecimal("80");
    private static final BigDecimal BUDGET_CRITICAL_PCT = new BigDecimal("100");
    private static final int INACTIVE_DAYS = 180;
    private static final int BATCH_SIZE = 250;

    private final AssetRepository assetRepository;
    private final LeaseRecordRepository leaseRecordRepository;
    private final BudgetRepository budgetRepository;
    private final NotificationService notificationService;

    public LifecycleAlertScheduler(AssetRepository assetRepository,
                                   LeaseRecordRepository leaseRecordRepository,
                                   BudgetRepository budgetRepository,
                                   NotificationService notificationService) {
        this.assetRepository = assetRepository;
        this.leaseRecordRepository = leaseRecordRepository;
        this.budgetRepository = budgetRepository;
        this.notificationService = notificationService;
    }

    @Scheduled(cron = "0 30 7 * * *", zone = "UTC")
    @SchedulerLock(name = "lifecycleAlerts", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10M")
    public void run() {
        log.info("[LifecycleAlert] Starting bounded lifecycle alert scan");
        checkWarrantyExpiry();
        checkEndOfLife();
        checkInsuranceExpiry();
        checkLeaseExpiry();
        checkBudgetThresholds();
        checkInactiveAssets();
        log.info("[LifecycleAlert] Scan complete");
    }

    private void checkWarrantyExpiry() {
        LocalDate today = LocalDate.now();
        for (int days : EXPIRY_THRESHOLDS) {
            LocalDate milestone = today.plusDays(days);
            forEachPage(
                    pageable -> assetRepository.findWarrantyExpiringOn(milestone, pageable),
                    asset -> {
                        String title = "Warranty Expiring in " + days + " Day(s)";
                        String body = AssetLabels.describe(asset)
                                + " warranty expires on " + milestone + ".";
                        notifyOnce(asset, NotificationType.WARRANTY_EXPIRY, title, body,
                                "warranty", milestone);
                    });
        }
    }

    private void checkEndOfLife() {
        LocalDate today = LocalDate.now();
        forEachPage(assetRepository::findActiveAssetsWithUsefulLife, asset -> {
            LocalDate endOfLife = asset.getPurchaseDate().plusMonths(asset.getUsefulLifeMonths());
            if (endOfLife.isAfter(today)) {
                return;
            }
            String title = "Asset Reached End of Useful Life";
            String body = AssetLabels.describe(asset)
                    + " reached its end of useful life on " + endOfLife
                    + ". Consider scheduling disposal or replacement.";
            notifyOnce(asset, NotificationType.END_OF_LIFE, title, body, "eol", endOfLife);
        });
    }

    private void checkInsuranceExpiry() {
        LocalDate today = LocalDate.now();
        for (int days : EXPIRY_THRESHOLDS) {
            LocalDate milestone = today.plusDays(days);
            forEachPage(
                    pageable -> assetRepository.findInsuranceExpiringOn(milestone, pageable),
                    asset -> {
                        String title = "Insurance Expiring in " + days + " Day(s)";
                        String body = "Insurance for asset '" + asset.getName() + "'"
                                + AssetLabels.tagClause(asset.getAssetTag()) + " expires on " + milestone
                                + ". Renew to avoid a coverage gap.";
                        notifyOnce(asset, NotificationType.INSURANCE_EXPIRY, title, body,
                                "insurance", milestone);
                    });
        }
    }

    private void checkLeaseExpiry() {
        LocalDate today = LocalDate.now();
        for (int days : EXPIRY_THRESHOLDS) {
            LocalDate milestone = today.plusDays(days);
            forEachPage(
                    pageable -> leaseRecordRepository.findActiveExpiringOn(milestone, pageable),
                    lease -> {
                        String assetName = lease.getAsset() == null ? "Unlinked asset" : lease.getAsset().getName();
                        String lessorName = lease.getLessor() == null ? "Unknown lessor" : lease.getLessor().getName();
                        String title = "Lease Expiring in " + days + " Day(s)";
                        String body = "Lease for asset '" + assetName + "' with lessor '"
                                + lessorName + "' expires on " + milestone + ".";
                        notificationService.notifyOrgAdminsOnce(
                                lease.getOrganisation(), NotificationType.LEASE_EXPIRY,
                                title, body, lease.getId(), "/leases",
                                "lifecycle:lease:" + lease.getId() + ":" + milestone);
                    });
        }
    }

    private void checkBudgetThresholds() {
        forEachPage(budgetRepository::findActiveWithSpend, budget -> {
            BigDecimal spentPct = budget.getSpentAmount()
                    .multiply(new BigDecimal("100"))
                    .divide(budget.getTotalAmount(), 2, RoundingMode.HALF_UP);

            if (spentPct.compareTo(BUDGET_CRITICAL_PCT) >= 0) {
                notificationService.notifyOrgAdminsOnce(
                        budget.getOrganisation(), NotificationType.BUDGET_THRESHOLD,
                        "Budget Exhausted",
                        "Budget '" + budget.getName() + "' has been fully spent ("
                                + spentPct + "% used). No further expenses should be charged.",
                        budget.getId(), "/budgets",
                        "lifecycle:budget:" + budget.getId() + ":100");
            } else if (spentPct.compareTo(BUDGET_WARNING_PCT) >= 0) {
                notificationService.notifyOrgAdminsOnce(
                        budget.getOrganisation(), NotificationType.BUDGET_THRESHOLD,
                        "Budget Warning: " + spentPct + "% Used",
                        "Budget '" + budget.getName() + "' is " + spentPct
                                + "% spent. Consider reviewing upcoming expenses.",
                        budget.getId(), "/budgets",
                        "lifecycle:budget:" + budget.getId() + ":80");
            }
        });
    }

    private void checkInactiveAssets() {
        Instant cutoff = Instant.now().minus(INACTIVE_DAYS, ChronoUnit.DAYS);
        forEachPage(pageable -> assetRepository.findInactiveInStock(cutoff, pageable), asset -> {
            String lastScan = asset.getLastScannedAt() == null ? "never" : asset.getLastScannedAt().toString();
            String body = AssetLabels.describe(asset)
                    + " has been inactive in stock for over " + INACTIVE_DAYS
                    + " days (last scan: " + lastScan + ").";
            notificationService.notifyOrgAdminsOnce(
                    asset.getOrganisation(), NotificationType.SYSTEM,
                    "Inactive Asset Detected", body, asset.getId(), "/assets/" + asset.getId(),
                    "lifecycle:inactive:" + asset.getId() + ":" + lastScan);
        });
    }

    private void notifyOnce(Asset asset, NotificationType type, String title,
                            String body, String event, LocalDate milestone) {
        notificationService.notifyOrgAdminsOnce(
                asset.getOrganisation(), type, title, body, asset.getId(),
                "/assets/" + asset.getId(),
                "lifecycle:" + event + ":" + asset.getId() + ":" + milestone);
    }

    private <T> void forEachPage(Function<Pageable, Page<T>> query, Consumer<T> consumer) {
        Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("id").ascending());
        Page<T> page;
        do {
            page = query.apply(pageable);
            page.getContent().forEach(consumer);
            pageable = page.nextPageable();
        } while (page.hasNext());
    }
}
