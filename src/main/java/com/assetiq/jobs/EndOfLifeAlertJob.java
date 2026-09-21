package com.assetiq.jobs;

import com.assetiq.enums.NotificationType;
import com.assetiq.repositories.ContractRepository;
import com.assetiq.repositories.SoftwareLicenseRepository;
import com.assetiq.services.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Bounded expiry alerts for software licenses and supplier contracts.
 * Asset warranty and useful-life alerts are owned exclusively by
 * {@link LifecycleAlertScheduler}, avoiding the previous duplicate producers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EndOfLifeAlertJob {

    private static final int ALERT_DAYS = 30;
    private static final int BATCH_SIZE = 250;

    private final SoftwareLicenseRepository licenseRepository;
    private final ContractRepository contractRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
    @SchedulerLock(name = "commercialExpiryAlerts", lockAtMostFor = "PT30M", lockAtLeastFor = "PT10M")
    public void run() {
        LocalDate milestone = LocalDate.now().plusDays(ALERT_DAYS);
        log.info("[ExpiryAlert] Scanning bounded pages for expiry date {}", milestone);
        checkLicenseExpiry(milestone);
        checkContractExpiry(milestone);
        log.info("[ExpiryAlert] Scan complete");
    }

    private void checkLicenseExpiry(LocalDate milestone) {
        forEachPage(pageable -> licenseRepository.findExpiringOn(milestone, pageable), license -> {
            String title = "Software License Expiring: " + license.getName();
            String message = "Software license '" + license.getName() + "' (vendor: "
                    + (license.getVendor() == null ? "Unknown" : license.getVendor())
                    + ") expires on " + milestone + ".";
            notificationService.notifyOrgAdminsOnce(
                    license.getOrganisation(), NotificationType.DEPRECATION,
                    title, message, license.getId(), "/licenses",
                    "expiry:license:" + license.getId() + ":" + milestone);
        });
    }

    private void checkContractExpiry(LocalDate milestone) {
        forEachPage(pageable -> contractRepository.findExpiringOn(milestone, pageable), contract -> {
            String supplierName = contract.getSupplier() == null
                    ? "Unknown" : contract.getSupplier().getName();
            String title = "Contract Expiring: " + contract.getTitle();
            String message = "Contract '" + contract.getTitle() + "' (vendor: "
                    + supplierName + ", number: "
                    + (contract.getContractNumber() == null ? "N/A" : contract.getContractNumber())
                    + ") expires on " + milestone + ".";
            notificationService.notifyOrgAdminsOnce(
                    contract.getOrganisation(), NotificationType.MAINTENANCE,
                    title, message, contract.getId(), "/contracts",
                    "expiry:contract:" + contract.getId() + ":" + milestone);
        });
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
