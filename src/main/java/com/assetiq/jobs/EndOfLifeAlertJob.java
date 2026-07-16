package com.assetiq.jobs;

import com.assetiq.enums.NotificationType;
import com.assetiq.models.Asset;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Scheduled job that detects end-of-life conditions across assets,
 * software licenses, and contracts, and dispatches in-app notifications
 * to all ORG_ADMIN users in each affected organisation.
 *
 * Runs daily at 08:00 UTC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EndOfLifeAlertJob {

    /** Alert threshold for warranty expiry and license/contract expiry (days). */
    private static final int WARRANTY_ALERT_DAYS = 30;
    private static final int LICENSE_ALERT_DAYS  = 30;
    private static final int CONTRACT_ALERT_DAYS = 30;

    private final AssetRepository           assetRepository;
    private final SoftwareLicenseRepository licenseRepository;
    private final ContractRepository        contractRepository;
    private final NotificationService       notificationService;

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

        log.warn("[EOL-Alert] {} asset(s) have warranties expiring within {} days",
                expiring.size(), WARRANTY_ALERT_DAYS);

        expiring.forEach(asset -> {
            Organisation org = asset.getOrganisation();
            String title   = "Warranty Expiring Soon: " + asset.getName();
            String message = String.format(
                    "Asset '%s' (tag: %s) has a warranty expiring on %s — within %d days.",
                    asset.getName(),
                    asset.getAssetTag() != null ? asset.getAssetTag() : "N/A",
                    asset.getWarrantyExpiryDate(),
                    WARRANTY_ALERT_DAYS);
            String actionUrl = "/assets/" + asset.getId();

            log.debug("[EOL-Alert] Dispatching WARRANTY_EXPIRY notification for asset {}", asset.getId());
            notificationService.notifyOrgAdmins(
                    org, NotificationType.WARRANTY_EXPIRY, title, message, asset.getId(), actionUrl);
        });
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

        log.warn("[EOL-Alert] {} asset(s) have reached end of useful life", eolAssets.size());

        eolAssets.forEach(asset -> {
            Organisation org  = asset.getOrganisation();
            LocalDate eolDate = asset.getPurchaseDate().plusMonths(asset.getUsefulLifeMonths());
            String title      = "Asset End of Useful Life: " + asset.getName();
            String message    = String.format(
                    "Asset '%s' (tag: %s) reached its end of useful life on %s and may require replacement or disposal.",
                    asset.getName(),
                    asset.getAssetTag() != null ? asset.getAssetTag() : "N/A",
                    eolDate);
            String actionUrl = "/assets/" + asset.getId();

            log.debug("[EOL-Alert] Dispatching END_OF_LIFE notification for asset {}", asset.getId());
            notificationService.notifyOrgAdmins(
                    org, NotificationType.END_OF_LIFE, title, message, asset.getId(), actionUrl);
        });
    }

    // ---- Software license expiry ----

    private void checkLicenseExpiry() {
        LocalDate cutoff = LocalDate.now().plusDays(LICENSE_ALERT_DAYS);
        licenseRepository.findAll().stream()
                .filter(l -> l.getDeletedAt() == null
                          && l.getExpiryDate() != null
                          && !l.getExpiryDate().isAfter(cutoff))
                .forEach(license -> {
                    Organisation org = license.getOrganisation();
                    String title     = "Software License Expiring: " + license.getName();
                    String message   = String.format(
                            "Software license '%s' (vendor: %s) expires on %s — within %d days.",
                            license.getName(),
                            license.getVendor() != null ? license.getVendor() : "Unknown",
                            license.getExpiryDate(),
                            LICENSE_ALERT_DAYS);
                    String actionUrl = "/software-licenses/" + license.getId();

                    log.debug("[EOL-Alert] Dispatching DEPRECATION notification for license {}", license.getId());
                    notificationService.notifyOrgAdmins(
                            org, NotificationType.DEPRECATION, title, message, license.getId(), actionUrl);
                });
    }

    // ---- Contract expiry ----

    private void checkContractExpiry() {
        LocalDate cutoff = LocalDate.now().plusDays(CONTRACT_ALERT_DAYS);
        contractRepository.findAll().stream()
                .filter(c -> c.getDeletedAt() == null
                          && c.getEndDate() != null
                          && !c.getEndDate().isAfter(cutoff))
                .forEach(contract -> {
                    Organisation org = contract.getOrganisation();
                    String title     = "Contract Expiring: " + contract.getTitle();
                    String supplierName = contract.getSupplier() != null ? contract.getSupplier().getName() : "Unknown";
                    String message   = String.format(
                            "Contract '%s' (vendor: %s, number: %s) expires on %s — within %d days.",
                            contract.getTitle(),
                            supplierName,
                            contract.getContractNumber() != null ? contract.getContractNumber() : "N/A",
                            contract.getEndDate(),
                            CONTRACT_ALERT_DAYS);
                    String actionUrl = "/contracts/" + contract.getId();

                    log.debug("[EOL-Alert] Dispatching MAINTENANCE notification for contract {}", contract.getId());
                    notificationService.notifyOrgAdmins(
                            org, NotificationType.MAINTENANCE, title, message, contract.getId(), actionUrl);
                });
    }
}
