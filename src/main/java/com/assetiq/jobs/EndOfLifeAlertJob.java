package com.assetiq.jobs;

import com.assetiq.models.Asset;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduled job that detects and logs end-of-life conditions across assets,
 * software licenses, and contracts.
 *
 * <p>In production this would dispatch notifications (email, webhook, in-app alert)
 * instead of just logging. The alerting integration is injected as a separate
 * notification service to keep this class focused on detection only.
 *
 * Runs daily at 08:00 UTC.
 */
@Component
public class EndOfLifeAlertJob {

    private static final Logger log = LoggerFactory.getLogger(EndOfLifeAlertJob.class);

    /** Alert threshold for warranty expiry and license/contract expiry (days). */
    private static final int WARRANTY_ALERT_DAYS = 30;
    private static final int LICENSE_ALERT_DAYS = 30;
    private static final int CONTRACT_ALERT_DAYS = 30;

    private final AssetRepository assetRepository;
    private final SoftwareLicenseRepository licenseRepository;
    private final ContractRepository contractRepository;

    public EndOfLifeAlertJob(AssetRepository assetRepository,
                             SoftwareLicenseRepository licenseRepository,
                             ContractRepository contractRepository) {
        this.assetRepository = assetRepository;
        this.licenseRepository = licenseRepository;
        this.contractRepository = contractRepository;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    public void run() {
        log.info("[EOL-Alert] Starting end-of-life alert scan");

        checkWarrantyExpiry();
        checkAssetEndOfLife();
        checkLicenseExpiry();
        checkContractExpiry();

        log.info("[EOL-Alert] Scan complete");
    }

    // ---- Warranty expiry ----

    private void checkWarrantyExpiry() {
        LocalDate cutoff = LocalDate.now().plusDays(WARRANTY_ALERT_DAYS);
        List<Asset> expiring = assetRepository.findWarrantyExpiringSoon(cutoff);
        if (expiring.isEmpty()) return;

        log.warn("[EOL-Alert] {} asset(s) have warranties expiring within {} days:", expiring.size(), WARRANTY_ALERT_DAYS);
        expiring.forEach(a -> log.warn("  Asset '{}' (id={}) — warranty expires {}",
                a.getName(), a.getId(), a.getWarrantyExpiryDate()));

        // TODO: dispatch notification via NotificationService
    }

    // ---- End of useful life ----

    private void checkAssetEndOfLife() {
        LocalDate today = LocalDate.now();
        List<Asset> eolAssets = assetRepository.findActiveAssetsWithUsefulLife()
                .stream()
                .filter(a -> {
                    LocalDate eolDate = a.getPurchaseDate().plusMonths(a.getUsefulLifeMonths());
                    return !eolDate.isAfter(today);
                })
                .collect(Collectors.toList());

        if (eolAssets.isEmpty()) return;

        log.warn("[EOL-Alert] {} asset(s) have reached end of useful life:", eolAssets.size());
        eolAssets.forEach(a -> {
            LocalDate eolDate = a.getPurchaseDate().plusMonths(a.getUsefulLifeMonths());
            log.warn("  Asset '{}' (id={}) — EOL date was {}", a.getName(), a.getId(), eolDate);
        });

        // TODO: dispatch notification via NotificationService
    }

    // ---- Software license expiry ----

    private void checkLicenseExpiry() {
        LocalDate cutoff = LocalDate.now().plusDays(LICENSE_ALERT_DAYS);
        // Reuse the repository's expiring-soon query (all orgs)
        // In a proper notification flow, group by org and send per-org emails
        long count = licenseRepository.findAll().stream()
                .filter(l -> l.getDeletedAt() == null
                        && l.getExpiryDate() != null
                        && !l.getExpiryDate().isAfter(cutoff))
                .count();

        if (count > 0) {
            log.warn("[EOL-Alert] {} software license(s) expiring within {} days", count, LICENSE_ALERT_DAYS);
            // TODO: dispatch notification via NotificationService
        }
    }

    // ---- Contract expiry ----

    private void checkContractExpiry() {
        LocalDate cutoff = LocalDate.now().plusDays(CONTRACT_ALERT_DAYS);
        long count = contractRepository.findAll().stream()
                .filter(c -> c.getDeletedAt() == null
                        && c.getEndDate() != null
                        && !c.getEndDate().isAfter(cutoff))
                .count();

        if (count > 0) {
            log.warn("[EOL-Alert] {} contract(s) expiring within {} days", count, CONTRACT_ALERT_DAYS);
            // TODO: dispatch notification via NotificationService
        }
    }
}
