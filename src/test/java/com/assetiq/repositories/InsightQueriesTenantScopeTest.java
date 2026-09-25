package com.assetiq.repositories;

import com.assetiq.dto.mobile.RecentAsset;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.enums.ContractStatus;
import com.assetiq.enums.ContractType;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.enums.LeaseStatus;
import com.assetiq.enums.LicenseStatus;
import com.assetiq.enums.LicenseType;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.enums.TransferStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.AssetTransfer;
import com.assetiq.models.Budget;
import com.assetiq.models.Category;
import com.assetiq.models.CheckoutRecord;
import com.assetiq.models.Contract;
import com.assetiq.models.Department;
import com.assetiq.models.DepreciationPolicy;
import com.assetiq.models.LeaseRecord;
import com.assetiq.models.Location;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import com.assetiq.models.Supplier;
import com.assetiq.models.User;
import com.assetiq.services.insights.AssetValuationRow;
import com.assetiq.services.insights.DueRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every aggregate query the dashboards run, against a real schema, with two
 * tenants holding identical-looking data.
 *
 * <p>The point is not that the numbers are right — the service tests do that.
 * The point is that the tenant predicate is in the query, so a caller can only
 * ever be handed their own rows: no fetch-then-filter, nothing that would still
 * return the other tenant's data if a Java-side check were removed.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:insights;MODE=PostgreSQL;NON_KEYWORDS=VALUE,MONTH,YEAR;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Insight queries are scoped to one tenant at the query level")
class InsightQueriesTenantScopeTest {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired TestEntityManager em;
    @Autowired AssetRepository assets;
    @Autowired MaintenanceRecordRepository maintenance;
    @Autowired ContractRepository contracts;
    @Autowired SoftwareLicenseRepository licences;
    @Autowired LeaseRecordRepository leases;
    @Autowired BudgetRepository budgets;
    @Autowired AssetTransferRepository transfers;
    @Autowired CheckoutRecordRepository checkouts;

    private Organisation ours;
    private Organisation theirs;

    @BeforeEach
    void setUp() {
        ours = org("Ours");
        theirs = org("Theirs");
        populate(ours, "our");
        populate(theirs, "their");
        em.flush();
        em.clear();
    }

    // ── The tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the estate projection returns only this tenant's assets, with its joins resolved")
    void valuationRowsAreTenantScoped() {
        List<AssetValuationRow> rows = assets.findValuationRows(ours);

        assertThat(rows).hasSize(1);
        AssetValuationRow row = rows.get(0);
        assertThat(row.name()).isEqualTo("our asset");
        assertThat(row.departmentName()).isEqualTo("our dept");
        assertThat(row.locationName()).isEqualTo("our site");
        assertThat(row.categoryName()).isEqualTo("our category");
        // The category's depreciation policy arrives in the same row: no N+1.
        assertThat(row.policyUsefulLifeMonths()).isEqualTo(24);
        assertThat(row.policyMethod()).isEqualTo(DepreciationMethod.STRAIGHT_LINE);

        assertThat(assets.findValuationRows(theirs))
                .singleElement()
                .extracting(AssetValuationRow::name).isEqualTo("their asset");
    }

    @Test
    @DisplayName("every expiry stream is tenant-scoped")
    void expiryStreamsAreTenantScoped() {
        LocalDate cutoff = TODAY.plusDays(90);

        assertOnlyOurs(assets.findWarrantyDueBy(ours, cutoff), "our asset");
        assertOnlyOurs(assets.findInsuranceDueBy(ours, cutoff), "our asset");
        assertOnlyOurs(maintenance.findDueBy(ours, cutoff), "our asset");
        assertOnlyOurs(contracts.findDueBy(ours, cutoff), "our contract");
        assertOnlyOurs(licences.findDueBy(ours, cutoff), "our licence");
        assertOnlyOurs(leases.findDueBy(ours, cutoff), "our lessor");

        assertOnlyOurs(assets.findWarrantyDueBy(theirs, cutoff), "their asset");
        assertOnlyOurs(contracts.findDueBy(theirs, cutoff), "their contract");
        assertOnlyOurs(licences.findDueBy(theirs, cutoff), "their licence");
    }

    @Test
    @DisplayName("overdue maintenance counts are tenant-scoped and ignore completed work")
    void maintenanceCountsAreTenantScoped() {
        assertThat(maintenance.countOverdue(ours, TODAY)).isZero();      // ours is due in future
        assertThat(maintenance.countAssetsNeedingMaintenance(ours, TODAY.plusDays(20))).isEqualTo(1L);
        assertThat(maintenance.countAssetsNeedingMaintenance(theirs, TODAY.plusDays(20))).isEqualTo(1L);

        // A completed job is never overdue, however old.
        MaintenanceRecord done = new MaintenanceRecord();
        done.setAsset(em.find(Asset.class, assets.findValuationRows(ours).get(0).id()));
        done.setMaintenanceType(MaintenanceType.values()[0]);
        done.setStatus(MaintenanceStatus.COMPLETED);
        done.setNextDueDate(TODAY.minusYears(2));
        done.setOrganisation(ours);
        em.persist(done);
        em.flush();

        assertThat(maintenance.countOverdue(ours, TODAY)).isZero();
    }

    @Test
    @DisplayName("licence seat totals are tenant-scoped")
    void seatTotalsAreTenantScoped() {
        List<Object[]> seats = licences.sumSeats(ours);
        assertThat(((Number) seats.get(0)[0]).longValue()).isEqualTo(10L);
        assertThat(((Number) seats.get(0)[1]).longValue()).isEqualTo(4L);
    }

    @Test
    @DisplayName("budget overlap is tenant-scoped and matches on overlap, not containment")
    void budgetOverlapIsTenantScoped() {
        // Our budget runs Apr-Mar; a calendar-year window must still find it.
        assertThat(budgets.findOverlapping(ours, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .singleElement()
                .extracting(Budget::getName).isEqualTo("our budget");

        assertThat(budgets.findOverlapping(theirs, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .singleElement()
                .extracting(Budget::getName).isEqualTo("their budget");

        // A window entirely outside the budget's period finds nothing.
        assertThat(budgets.findOverlapping(ours, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 12, 31)))
                .isEmpty();
    }

    // ── Mobile Home counts ────────────────────────────────────────────────────

    @Test
    @DisplayName("the recently-updated list is tenant-scoped and skips soft-deleted assets")
    void recentlyUpdatedIsTenantScoped() {
        Asset deleted = asset(ours, "our deleted asset");
        deleted.setDeletedAt(Instant.now());
        em.flush();

        assertThat(assets.findRecentlyUpdated(ours, PageRequest.of(0, 10)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.name()).isEqualTo("our asset");
                    assertThat(row.status()).isEqualTo("IN_USE");
                    assertThat(row.updatedAt()).isNotNull();
                });
        assertThat(assets.findRecentlyUpdated(theirs, PageRequest.of(0, 10)))
                .singleElement()
                .extracting(RecentAsset::name).isEqualTo("their asset");
    }

    @Test
    @DisplayName("the transfer approval count is tenant-scoped and leaves out the caller's own requests")
    void transferApprovalCountIsTenantScoped() {
        User ourRequester = user(ours, "requester");
        User ourApprover = user(ours, "approver");
        transfer(ours, ourRequester, TransferStatus.REQUESTED);
        transfer(ours, ourRequester, TransferStatus.APPROVED);
        User theirRequester = user(theirs, "requester");
        transfer(theirs, theirRequester, TransferStatus.REQUESTED);
        transfer(theirs, theirRequester, TransferStatus.REQUESTED);
        em.flush();

        assertThat(transfers.countByStatusRequestedByOther(ours, TransferStatus.REQUESTED, ourApprover))
                .isEqualTo(1L);
        // A requester cannot approve their own transfer, so it is not waiting on them.
        assertThat(transfers.countByStatusRequestedByOther(ours, TransferStatus.REQUESTED, ourRequester))
                .isZero();
        assertThat(transfers.countByStatusRequestedByOther(theirs, TransferStatus.REQUESTED, ourApprover))
                .isEqualTo(2L);

        // The queue rows are the same set the count covers, and no wider.
        assertThat(transfers.findQueueByStatusRequestedByOther(ours, TransferStatus.REQUESTED, ourApprover,
                PageRequest.of(0, 5)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.requesterFirstName()).isEqualTo("requester");
                    assertThat(row.status()).isEqualTo(TransferStatus.REQUESTED);
                    assertThat(row.assetName()).startsWith("moving ");
                });
        assertThat(transfers.findQueueByStatusRequestedByOther(ours, TransferStatus.REQUESTED, ourRequester,
                PageRequest.of(0, 5))).isEmpty();
        assertThat(transfers.findQueueByStatusRequestedByOther(theirs, TransferStatus.REQUESTED, ourApprover,
                PageRequest.of(0, 1))).hasSize(1);
    }

    @Test
    @DisplayName("the overdue checkout count is tenant-scoped and counts only active, past-due checkouts")
    void overdueCheckoutCountIsTenantScoped() {
        User ourHolder = user(ours, "holder");
        checkout(ours, ourHolder, CheckoutStatus.ACTIVE, TODAY.minusDays(1));   // overdue
        checkout(ours, ourHolder, CheckoutStatus.ACTIVE, TODAY);                // due today: not yet
        checkout(ours, ourHolder, CheckoutStatus.ACTIVE, TODAY.plusDays(3));
        checkout(ours, ourHolder, CheckoutStatus.ACTIVE, null);
        checkout(ours, ourHolder, CheckoutStatus.RETURNED, TODAY.minusDays(5));
        User theirHolder = user(theirs, "holder");
        checkout(theirs, theirHolder, CheckoutStatus.ACTIVE, TODAY.minusDays(2));
        checkout(theirs, theirHolder, CheckoutStatus.ACTIVE, TODAY.minusDays(9));
        em.flush();

        assertThat(checkouts.countPastExpectedReturn(ours, CheckoutStatus.ACTIVE, TODAY)).isEqualTo(1L);
        assertThat(checkouts.countPastExpectedReturn(theirs, CheckoutStatus.ACTIVE, TODAY)).isEqualTo(2L);

        assertThat(checkouts.findPastExpectedReturnQueue(ours, CheckoutStatus.ACTIVE, TODAY, PageRequest.of(0, 5)))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.expectedReturnDate()).isEqualTo(TODAY.minusDays(1));
                    assertThat(row.userFirstName()).isEqualTo("holder");
                });
        // Oldest expected return first.
        assertThat(checkouts.findPastExpectedReturnQueue(theirs, CheckoutStatus.ACTIVE, TODAY, PageRequest.of(0, 5)))
                .extracting(com.assetiq.services.mobile.CheckoutQueueRow::expectedReturnDate)
                .containsExactly(TODAY.minusDays(9), TODAY.minusDays(2));
    }

    @Test
    @DisplayName("the overdue maintenance queue is tenant-scoped, oldest due first, and skips closed work")
    void overdueMaintenanceQueueIsTenantScoped() {
        maintenanceJob(ours, MaintenanceStatus.SCHEDULED, TODAY.minusDays(2));
        maintenanceJob(ours, MaintenanceStatus.IN_PROGRESS, TODAY.minusDays(7));
        maintenanceJob(ours, MaintenanceStatus.COMPLETED, TODAY.minusDays(30));
        maintenanceJob(ours, MaintenanceStatus.CANCELLED, TODAY.minusDays(30));
        maintenanceJob(theirs, MaintenanceStatus.SCHEDULED, TODAY.minusDays(1));
        em.flush();

        assertThat(maintenance.findOverdueQueue(ours, TODAY, PageRequest.of(0, 5)))
                .extracting(com.assetiq.services.mobile.MaintenanceQueueRow::nextDueDate)
                .containsExactly(TODAY.minusDays(7), TODAY.minusDays(2));
        assertThat(maintenance.countOverdue(ours, TODAY)).isEqualTo(2L);
        assertThat(maintenance.findOverdueQueue(theirs, TODAY, PageRequest.of(0, 5)))
                .singleElement()
                .satisfies(row -> assertThat(row.assetName()).startsWith("serviced "));
    }

    @Test
    @DisplayName("budget totals per currency are tenant-scoped and filtered by status")
    void budgetCurrencyTotalsAreTenantScoped() {
        Budget draft = new Budget();
        draft.setName("our draft budget");
        draft.setStatus(BudgetStatus.DRAFT);
        draft.setTotalAmount(new BigDecimal("99999.00"));
        draft.setCurrency("GHS");
        draft.setPeriodStart(LocalDate.of(2026, 1, 1));
        draft.setPeriodEnd(LocalDate.of(2026, 12, 31));
        draft.setOrganisation(ours);
        em.persist(draft);
        em.flush();

        List<Object[]> rows = budgets.sumAllocatedAndSpentByCurrency(ours,
                EnumSet.of(BudgetStatus.ACTIVE, BudgetStatus.EXCEEDED));
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0]).isEqualTo("GHS");
        assertThat((BigDecimal) rows.get(0)[1]).isEqualByComparingTo("10000.00");
        assertThat((BigDecimal) rows.get(0)[2]).isEqualByComparingTo("1000.00");
    }

    @Test
    @DisplayName("a tenant with nothing gets empty results, not another tenant's rows")
    void anEmptyTenantSeesNothing() {
        Organisation newcomer = org("Newcomer");
        em.flush();

        assertThat(assets.findValuationRows(newcomer)).isEmpty();
        assertThat(assets.findWarrantyDueBy(newcomer, TODAY.plusDays(90))).isEmpty();
        assertThat(contracts.findDueBy(newcomer, TODAY.plusDays(90))).isEmpty();
        assertThat(licences.findDueBy(newcomer, TODAY.plusDays(90))).isEmpty();
        assertThat(leases.findDueBy(newcomer, TODAY.plusDays(90))).isEmpty();
        assertThat(maintenance.findDueBy(newcomer, TODAY.plusDays(90))).isEmpty();
        assertThat(budgets.findOverlapping(newcomer, TODAY, TODAY.plusDays(1))).isEmpty();
        assertThat(maintenance.countOverdue(newcomer, TODAY)).isZero();
        assertThat(assets.countActive(newcomer)).isZero();
        assertThat(assets.findRecentlyUpdated(newcomer, PageRequest.of(0, 10))).isEmpty();
        assertThat(checkouts.countPastExpectedReturn(newcomer, CheckoutStatus.ACTIVE, TODAY)).isZero();
        assertThat(checkouts.findPastExpectedReturnQueue(newcomer, CheckoutStatus.ACTIVE, TODAY,
                PageRequest.of(0, 5))).isEmpty();
        assertThat(maintenance.findOverdueQueue(newcomer, TODAY, PageRequest.of(0, 5))).isEmpty();
        assertThat(budgets.sumAllocatedAndSpentByCurrency(newcomer, EnumSet.allOf(BudgetStatus.class))).isEmpty();
        // SUM over no rows must be zero, not null: a new tenant's dashboard would
        // otherwise show a blank where it should show nothing owned yet.
        List<Object[]> seats = licences.sumSeats(newcomer);
        assertThat(((Number) seats.get(0)[0]).longValue()).isZero();
        assertThat(((Number) seats.get(0)[1]).longValue()).isZero();
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    private void assertOnlyOurs(List<DueRow> rows, String expectedName) {
        assertThat(rows).singleElement().extracting(DueRow::name).isEqualTo(expectedName);
    }

    private Organisation org(String name) {
        Organisation o = new Organisation();
        o.setName(name + " " + UUID.randomUUID());
        o.setBillingCurrency("GHS");
        return em.persist(o);
    }

    private User user(Organisation org, String label) {
        User u = new User();
        u.setFirstName(label);
        u.setLastName("Tester");
        u.setEmail(label + "+" + UUID.randomUUID() + "@example.com");
        u.setPasswordHash("not-a-real-hash");
        u.setOrganisation(org);
        return em.persist(u);
    }

    private Asset asset(Organisation org, String name) {
        Asset a = new Asset();
        a.setName(name);
        a.setAssetTag("T-" + UUID.randomUUID());
        a.setStatus(AssetStatus.IN_USE);
        a.setOrganisation(org);
        return em.persist(a);
    }

    /** Each transfer gets its own asset and destination so nothing else is shared. */
    private void transfer(Organisation org, User requester, TransferStatus status) {
        Department to = new Department();
        to.setName("dest " + UUID.randomUUID());
        to.setOrganisation(org);
        em.persist(to);

        AssetTransfer t = new AssetTransfer();
        t.setAsset(asset(org, "moving " + UUID.randomUUID()));
        t.setToDepartment(to);
        t.setRequestedBy(requester);
        t.setStatus(status);
        t.setOrganisation(org);
        em.persist(t);
    }

    private void maintenanceJob(Organisation org, MaintenanceStatus status, LocalDate nextDue) {
        MaintenanceRecord r = new MaintenanceRecord();
        r.setAsset(asset(org, "serviced " + UUID.randomUUID()));
        r.setMaintenanceType(MaintenanceType.values()[0]);
        r.setStatus(status);
        r.setNextDueDate(nextDue);
        r.setOrganisation(org);
        em.persist(r);
    }

    private void checkout(Organisation org, User holder, CheckoutStatus status, LocalDate expectedReturn) {
        CheckoutRecord c = new CheckoutRecord();
        c.setAsset(asset(org, "lent " + UUID.randomUUID()));
        c.setCheckedOutBy(holder);
        c.setCheckedOutAt(Instant.now());
        c.setExpectedReturnDate(expectedReturn);
        c.setStatus(status);
        c.setOrganisation(org);
        em.persist(c);
    }

    /** The same shape of data for each tenant, so a leak shows up as a duplicate. */
    private void populate(Organisation org, String prefix) {
        DepreciationPolicy policy = new DepreciationPolicy();
        policy.setName(prefix + " policy");
        policy.setMethod(DepreciationMethod.STRAIGHT_LINE);
        policy.setUsefulLifeMonths(24);
        policy.setOrganisation(org);
        em.persist(policy);

        Category category = new Category();
        category.setName(prefix + " category");
        category.setDepreciationPolicy(policy);
        category.setOrganisation(org);
        em.persist(category);

        Department department = new Department();
        department.setName(prefix + " dept");
        department.setOrganisation(org);
        em.persist(department);

        Location location = new Location();
        location.setName(prefix + " site");
        location.setOrganisation(org);
        em.persist(location);

        Supplier supplier = new Supplier();
        supplier.setName(prefix + " lessor");
        supplier.setOrganisation(org);
        em.persist(supplier);

        Asset asset = new Asset();
        asset.setName(prefix + " asset");
        asset.setAssetTag(prefix.toUpperCase() + "-1");
        asset.setCurrency("GHS");
        asset.setPurchaseCost(new BigDecimal("1000.00"));
        asset.setPurchaseDate(TODAY.minusMonths(6));
        asset.setStatus(AssetStatus.IN_USE);
        asset.setWarrantyExpiryDate(TODAY.plusDays(10));
        asset.setInsurancePolicyExpiry(TODAY.plusDays(20));
        asset.setCategory(category);
        asset.setDepartment(department);
        asset.setLocation(location);
        asset.setOrganisation(org);
        em.persist(asset);

        MaintenanceRecord record = new MaintenanceRecord();
        record.setAsset(asset);
        record.setMaintenanceType(MaintenanceType.values()[0]);
        record.setStatus(MaintenanceStatus.SCHEDULED);
        record.setNextDueDate(TODAY.plusDays(15));
        record.setCost(new BigDecimal("50.00"));
        record.setCurrency("GHS");
        record.setOrganisation(org);
        em.persist(record);

        Contract contract = new Contract();
        contract.setTitle(prefix + " contract");
        contract.setContractType(ContractType.MAINTENANCE);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(TODAY.minusYears(1));
        contract.setEndDate(TODAY.plusDays(30));
        contract.setValue(new BigDecimal("2000.00"));
        contract.setCurrency("GHS");
        contract.setOrganisation(org);
        em.persist(contract);

        SoftwareLicense licence = new SoftwareLicense();
        licence.setName(prefix + " licence");
        licence.setVendor("Vendor");
        licence.setLicenseType(LicenseType.values()[0]);
        licence.setStatus(LicenseStatus.ACTIVE);
        licence.setExpiryDate(TODAY.plusDays(45));
        licence.setTotalSeats(10);
        licence.setUsedSeats(4);
        licence.setAnnualRenewalCost(new BigDecimal("1200.00"));
        licence.setCurrency("GHS");
        licence.setOrganisation(org);
        em.persist(licence);

        LeaseRecord lease = new LeaseRecord();
        lease.setAsset(asset);
        lease.setLessor(supplier);
        lease.setStartDate(TODAY.minusYears(1));
        lease.setEndDate(TODAY.plusDays(60));
        lease.setMonthlyPayment(new BigDecimal("300.00"));
        lease.setCurrency("GHS");
        lease.setStatus(LeaseStatus.ACTIVE);
        lease.setOrganisation(org);
        em.persist(lease);

        Budget budget = new Budget();
        budget.setName(prefix + " budget");
        budget.setStatus(BudgetStatus.ACTIVE);
        budget.setTotalAmount(new BigDecimal("10000.00"));
        budget.setSpentAmount(new BigDecimal("1000.00"));
        budget.setCommittedAmount(new BigDecimal("500.00"));
        budget.setCurrency("GHS");
        budget.setPeriodStart(LocalDate.of(2026, 4, 1));
        budget.setPeriodEnd(LocalDate.of(2027, 3, 31));
        budget.setOrganisation(org);
        em.persist(budget);
    }
}
