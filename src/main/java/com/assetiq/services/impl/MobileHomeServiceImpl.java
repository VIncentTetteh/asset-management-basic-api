package com.assetiq.services.impl;

import com.assetiq.dto.mobile.MobileHomeResponse;
import com.assetiq.dto.mobile.NeedsYou;
import com.assetiq.dto.mobile.Portfolio;
import com.assetiq.dto.mobile.QueueItem;
import com.assetiq.dto.mobile.RecentAsset;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.enums.TransferStatus;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.AssetTransferRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.CheckoutRecordRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.NotificationRepository;
import com.assetiq.services.MobileHomeService;
import com.assetiq.services.mobile.CheckoutQueueRow;
import com.assetiq.services.mobile.MaintenanceQueueRow;
import com.assetiq.services.mobile.MobileHomeSection;
import com.assetiq.services.mobile.TransferQueueRow;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAggregator;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the mobile Home payload from count and top-N queries only; nothing
 * here loads a tenant-sized list. Not cached: the counts are per caller and
 * each query is an indexed aggregate.
 */
@Service
@Transactional(readOnly = true)
public class MobileHomeServiceImpl implements MobileHomeService {

    static final int RECENTLY_UPDATED_LIMIT = 10;
    static final int QUEUE_LIMIT = 5;

    /** Overdue items: earliest due first; kind then id only to make ties stable. */
    private static final Comparator<QueueItem> BY_DUE_DATE = Comparator
            .comparing(QueueItem::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(QueueItem::kind)
            .thenComparing(QueueItem::id);

    /**
     * Budgets that are in force. EXCEEDED is an ACTIVE budget whose spend has
     * passed its total (Budget keeps the two in step), so leaving it out would
     * hide exactly the overspend the figure exists to show.
     */
    static final Set<BudgetStatus> IN_FORCE_BUDGETS = EnumSet.of(BudgetStatus.ACTIVE, BudgetStatus.EXCEEDED);

    private static final String UNKNOWN_STATUS = "UNKNOWN";
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final AssetTransferRepository assetTransferRepository;
    private final CheckoutRecordRepository checkoutRecordRepository;
    private final NotificationRepository notificationRepository;
    private final BudgetRepository budgetRepository;
    private final MoneyAggregator moneyAggregator;

    public MobileHomeServiceImpl(AssetRepository assetRepository,
                                 MaintenanceRecordRepository maintenanceRecordRepository,
                                 AssetTransferRepository assetTransferRepository,
                                 CheckoutRecordRepository checkoutRecordRepository,
                                 NotificationRepository notificationRepository,
                                 BudgetRepository budgetRepository,
                                 MoneyAggregator moneyAggregator) {
        this.assetRepository = assetRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.assetTransferRepository = assetTransferRepository;
        this.checkoutRecordRepository = checkoutRecordRepository;
        this.notificationRepository = notificationRepository;
        this.budgetRepository = budgetRepository;
        this.moneyAggregator = moneyAggregator;
    }

    @Override
    public MobileHomeResponse getHome(Organisation org, User user, Authentication authentication) {
        Set<MobileHomeSection> granted = MobileHomeSection.grantedTo(authentication);
        // Same "today" as the list endpoints these counts stand in for.
        LocalDate today = LocalDate.now();
        boolean seesAssets = granted.contains(MobileHomeSection.ASSETS);

        return new MobileHomeResponse(
                needsYou(org, user, granted, today),
                queue(org, user, granted, today),
                seesAssets ? portfolio(org) : null,
                seesAssets ? recentlyUpdated(org) : null,
                granted.contains(MobileHomeSection.BUDGETS) ? budgetUtilisationPct(org) : null,
                Instant.now());
    }

    private NeedsYou needsYou(Organisation org, User user, Set<MobileHomeSection> granted, LocalDate today) {
        Integer overdueMaintenance = granted.contains(MobileHomeSection.MAINTENANCE)
                ? toInt(maintenanceRecordRepository.countOverdue(org, today)) : null;
        Integer pendingApprovals = granted.contains(MobileHomeSection.TRANSFER_APPROVALS)
                ? toInt(assetTransferRepository.countByStatusRequestedByOther(org, TransferStatus.REQUESTED, user))
                : null;
        Integer overdueCheckouts = granted.contains(MobileHomeSection.CHECKOUTS)
                ? toInt(checkoutRecordRepository.countPastExpectedReturn(org, CheckoutStatus.ACTIVE, today))
                : null;
        long unread = notificationRepository.countByUserAndOrganisationAndReadAndDeletedAtIsNull(user, org, false);

        long total = unread;
        for (Integer part : new Integer[] {overdueMaintenance, pendingApprovals, overdueCheckouts}) {
            if (part != null) {
                total += part;
            }
        }
        return new NeedsYou(toInt(total), overdueMaintenance, pendingApprovals, overdueCheckouts, unread);
    }

    /**
     * Overdue work first, then approvals. Each source query is capped at
     * {@link #QUEUE_LIMIT}, which is enough: the merged top five can never need
     * a sixth row from any one source.
     */
    private List<QueueItem> queue(Organisation org, User user, Set<MobileHomeSection> granted, LocalDate today) {
        PageRequest top = PageRequest.of(0, QUEUE_LIMIT);
        List<QueueItem> overdue = new ArrayList<>();
        if (granted.contains(MobileHomeSection.MAINTENANCE)) {
            maintenanceRecordRepository.findOverdueQueue(org, today, top)
                    .forEach(row -> overdue.add(toQueueItem(row)));
        }
        if (granted.contains(MobileHomeSection.CHECKOUTS)) {
            checkoutRecordRepository.findPastExpectedReturnQueue(org, CheckoutStatus.ACTIVE, today, top)
                    .forEach(row -> overdue.add(toQueueItem(row)));
        }
        overdue.sort(BY_DUE_DATE);

        List<QueueItem> queue = new ArrayList<>(overdue);
        if (queue.size() < QUEUE_LIMIT && granted.contains(MobileHomeSection.TRANSFER_APPROVALS)) {
            // Already oldest request first from the query.
            assetTransferRepository.findQueueByStatusRequestedByOther(org, TransferStatus.REQUESTED, user, top)
                    .forEach(row -> queue.add(toQueueItem(row)));
        }
        return List.copyOf(queue.subList(0, Math.min(queue.size(), QUEUE_LIMIT)));
    }

    private static QueueItem toQueueItem(MaintenanceQueueRow row) {
        String type = row.maintenanceType() != null
                ? capitalise(row.maintenanceType().name()) + " maintenance" : "Maintenance";
        return new QueueItem(QueueItem.KIND_MAINTENANCE, row.id(), row.assetId(), row.assetName(), type,
                row.status() != null ? row.status().name() : null, row.nextDueDate(), true);
    }

    private static QueueItem toQueueItem(CheckoutQueueRow row) {
        String holder = row.employeeFirstName() != null || row.employeeLastName() != null
                ? fullName(row.employeeFirstName(), row.employeeLastName(), null)
                : fullName(row.userFirstName(), row.userLastName(), null);
        return new QueueItem(QueueItem.KIND_CHECKOUT, row.id(), row.assetId(), row.assetName(),
                holder != null ? "Checked out to " + holder : "Checked out",
                row.status() != null ? row.status().name() : null, row.expectedReturnDate(), true);
    }

    private static QueueItem toQueueItem(TransferQueueRow row) {
        String requester = fullName(row.requesterFirstName(), row.requesterLastName(), row.requesterEmail());
        return new QueueItem(QueueItem.KIND_TRANSFER_APPROVAL, row.id(), row.assetId(), row.assetName(),
                requester != null ? "Requested by " + requester : "Transfer requested",
                row.status() != null ? row.status().name() : null, null, false);
    }

    /** Full name, else the fallback (an email), else null — the UserDisplayNames rule on bare columns. */
    private static String fullName(String first, String last, String fallback) {
        String name = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return name.isEmpty() ? fallback : name;
    }

    /** PREVENTIVE -> Preventive. */
    private static String capitalise(String enumName) {
        String lower = enumName.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private Portfolio portfolio(Organisation org) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (Object[] row : assetRepository.countGroupedByStatus(org)) {
            String status = row[0] != null ? row[0].toString() : UNKNOWN_STATUS;
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            byStatus.merge(status, count, Long::sum);
            total += count;
        }
        // Summed from the grouped rows rather than a second COUNT so the total
        // always equals the sum of byStatus, even if an asset lands in between.
        return new Portfolio(total, byStatus);
    }

    private List<RecentAsset> recentlyUpdated(Organisation org) {
        return assetRepository.findRecentlyUpdated(org, PageRequest.of(0, RECENTLY_UPDATED_LIMIT));
    }

    /**
     * Spent / allocated over in-force budgets, as a whole percent, in the tenant
     * base currency. Null when nothing is allocated, and null — not a partial
     * figure — when any budget's currency has no exchange rate: leaving that
     * budget out would silently misstate the ratio.
     */
    private Integer budgetUtilisationPct(Organisation org) {
        List<Object[]> rows = budgetRepository.sumAllocatedAndSpentByCurrency(org, IN_FORCE_BUDGETS);
        if (rows.isEmpty()) {
            return null;
        }
        CurrencyConversion fx = moneyAggregator.begin(org);
        BigDecimal allocated = BigDecimal.ZERO;
        BigDecimal spent = BigDecimal.ZERO;
        for (Object[] row : rows) {
            String currency = (String) row[0];
            Optional<BigDecimal> rowAllocated = fx.toBase((BigDecimal) row[1], currency);
            Optional<BigDecimal> rowSpent = fx.toBase((BigDecimal) row[2], currency);
            if (rowAllocated.isEmpty() || rowSpent.isEmpty()) {
                return null;
            }
            allocated = allocated.add(rowAllocated.get());
            spent = spent.add(rowSpent.get());
        }
        if (allocated.signum() <= 0) {
            return null;
        }
        return spent.multiply(HUNDRED).divide(allocated, 0, RoundingMode.HALF_UP).intValueExact();
    }

    /** Counts are far below int range; saturate rather than overflow if one ever is not. */
    private static int toInt(long value) {
        return (int) Math.min(value, Integer.MAX_VALUE);
    }
}
