package com.assetiq.repositories;

import com.assetiq.enums.ContractStatus;
import com.assetiq.enums.ContractType;
import com.assetiq.enums.LeaseStatus;
import com.assetiq.enums.LicenseStatus;
import com.assetiq.enums.LicenseType;
import com.assetiq.models.Asset;
import com.assetiq.models.Contract;
import com.assetiq.models.LeaseRecord;
import com.assetiq.models.Organisation;
import com.assetiq.models.SoftwareLicense;
import com.assetiq.models.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The bulk updates behind {@link com.assetiq.jobs.ExpiryStatusJob}, run against a real schema. */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:expiry;MODE=PostgreSQL;NON_KEYWORDS=VALUE,MONTH,YEAR;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
})
// Keep the URL above: H2 must treat VALUE/MONTH/YEAR as identifiers (contract.value).
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ExpiryStatusQueriesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 22);

    @Autowired TestEntityManager em;
    @Autowired ContractRepository contracts;
    @Autowired SoftwareLicenseRepository licenses;
    @Autowired LeaseRecordRepository leases;

    private Organisation org;
    private Asset asset;
    private Supplier lessor;

    @BeforeEach
    void setUp() {
        org = new Organisation();
        org.setName("Org " + UUID.randomUUID());
        em.persist(org);
        asset = new Asset();
        asset.setName("Forklift");
        asset.setOrganisation(org);
        em.persist(asset);
        lessor = new Supplier();
        lessor.setName("LeaseCo");
        lessor.setOrganisation(org);
        em.persist(lessor);
    }

    private Contract contract(ContractStatus status, LocalDate end, boolean autoRenew) {
        Contract c = new Contract();
        c.setTitle("Support");
        c.setContractType(ContractType.MAINTENANCE);
        c.setStatus(status);
        c.setStartDate(end.minusYears(1));
        c.setEndDate(end);
        c.setAutoRenew(autoRenew);
        c.setOrganisation(org);
        return em.persist(c);
    }

    private SoftwareLicense license(LicenseStatus status, LocalDate expiry, boolean autoRenew) {
        SoftwareLicense l = new SoftwareLicense();
        l.setName("IDE");
        l.setVendor("Vendor");
        l.setLicenseType(LicenseType.values()[0]);
        l.setStatus(status);
        l.setExpiryDate(expiry);
        l.setAutoRenew(autoRenew);
        l.setOrganisation(org);
        return em.persist(l);
    }

    private LeaseRecord lease(LeaseStatus status, LocalDate end, boolean autoRenew) {
        LeaseRecord r = new LeaseRecord();
        r.setAsset(asset);
        r.setLessor(lessor);
        r.setStartDate(end.minusYears(1));
        r.setEndDate(end);
        r.setMonthlyPayment(new BigDecimal("100.00"));
        r.setAutoRenew(autoRenew);
        r.setStatus(status);
        r.setOrganisation(org);
        return em.persist(r);
    }

    @Test
    void contractsPastTheirEndDateExpire() {
        Contract lapsed = contract(ContractStatus.ACTIVE, TODAY.minusDays(1), false);
        Contract warned = contract(ContractStatus.EXPIRING_SOON, TODAY.minusDays(30), false);
        Contract endsToday = contract(ContractStatus.ACTIVE, TODAY, false);
        Contract renews = contract(ContractStatus.ACTIVE, TODAY.minusDays(1), true);
        Contract terminated = contract(ContractStatus.TERMINATED, TODAY.minusDays(1), false);
        Contract draft = contract(ContractStatus.DRAFT, TODAY.minusDays(1), false);
        em.flush();

        assertThat(contracts.expirePastEndDate(TODAY, Instant.now())).isEqualTo(2);
        assertThat(contracts.expirePastEndDate(TODAY, Instant.now())).as("idempotent").isZero();

        assertThat(em.find(Contract.class, lapsed.getId()).getStatus()).isEqualTo(ContractStatus.EXPIRED);
        assertThat(em.find(Contract.class, warned.getId()).getStatus()).isEqualTo(ContractStatus.EXPIRED);
        assertThat(em.find(Contract.class, endsToday.getId()).getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(em.find(Contract.class, renews.getId()).getStatus()).isEqualTo(ContractStatus.ACTIVE);
        assertThat(em.find(Contract.class, terminated.getId()).getStatus()).isEqualTo(ContractStatus.TERMINATED);
        assertThat(em.find(Contract.class, draft.getId()).getStatus()).isEqualTo(ContractStatus.DRAFT);
    }

    @Test
    void licensesPastTheirExpiryDateExpire() {
        SoftwareLicense lapsed = license(LicenseStatus.ACTIVE, TODAY.minusDays(1), false);
        SoftwareLicense renews = license(LicenseStatus.ACTIVE, TODAY.minusDays(1), true);
        SoftwareLicense current = license(LicenseStatus.ACTIVE, TODAY, false);
        SoftwareLicense deleted = license(LicenseStatus.ACTIVE, TODAY.minusDays(1), false);
        deleted.setDeletedAt(Instant.now());
        em.flush();

        assertThat(licenses.expirePastExpiryDate(TODAY, Instant.now())).isEqualTo(1);
        assertThat(em.find(SoftwareLicense.class, lapsed.getId()).getStatus()).isEqualTo(LicenseStatus.EXPIRED);
        assertThat(em.find(SoftwareLicense.class, renews.getId()).getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(em.find(SoftwareLicense.class, current.getId()).getStatus()).isEqualTo(LicenseStatus.ACTIVE);
        assertThat(em.find(SoftwareLicense.class, deleted.getId()).getStatus()).isEqualTo(LicenseStatus.ACTIVE);
    }

    @Test
    void leasesPastTheirEndDateExpire() {
        LeaseRecord lapsed = lease(LeaseStatus.ACTIVE, TODAY.minusDays(1), false);
        LeaseRecord renews = lease(LeaseStatus.ACTIVE, TODAY.minusDays(1), true);
        LeaseRecord terminated = lease(LeaseStatus.TERMINATED, TODAY.minusDays(1), false);
        em.flush();

        assertThat(leases.expirePastEndDate(TODAY, Instant.now())).isEqualTo(1);
        assertThat(em.find(LeaseRecord.class, lapsed.getId()).getStatus()).isEqualTo(LeaseStatus.EXPIRED);
        assertThat(em.find(LeaseRecord.class, renews.getId()).getStatus()).isEqualTo(LeaseStatus.ACTIVE);
        assertThat(em.find(LeaseRecord.class, terminated.getId()).getStatus()).isEqualTo(LeaseStatus.TERMINATED);
    }
}
