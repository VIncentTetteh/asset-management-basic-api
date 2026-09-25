package com.assetiq.controllers.v1;

import com.assetiq.BaseIntegrationTest;
import com.assetiq.dto.TenantRegisterRequest;
import com.assetiq.dto.TenantRegisterResponse;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.BudgetStatus;
import com.assetiq.enums.CheckoutStatus;
import com.assetiq.enums.MaintenanceStatus;
import com.assetiq.enums.MaintenanceType;
import com.assetiq.enums.NotificationType;
import com.assetiq.enums.TransferStatus;
import com.assetiq.models.Asset;
import com.assetiq.models.AssetTransfer;
import com.assetiq.models.Budget;
import com.assetiq.models.CheckoutRecord;
import com.assetiq.models.Department;
import com.assetiq.models.ExchangeRate;
import com.assetiq.models.MaintenanceRecord;
import com.assetiq.models.Notification;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.AssetTransferRepository;
import com.assetiq.repositories.BudgetRepository;
import com.assetiq.repositories.CheckoutRecordRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.ExchangeRateRepository;
import com.assetiq.repositories.MaintenanceRecordRepository;
import com.assetiq.repositories.NotificationRepository;
import com.assetiq.repositories.OrganisationRepository;
import com.assetiq.repositories.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/v1/mobile/home against a real Postgres: the counts match what the
 * list endpoints it replaces would show, and a section the caller may not read
 * comes back null rather than zero.
 *
 * <p>Fixtures go straight through the repositories so each count can be pinned
 * exactly — the API would fire notifications and status transitions that make
 * the expected numbers depend on unrelated behaviour.
 */
@DisplayName("Mobile Home endpoint")
class MobileHomeIntegrationTest extends BaseIntegrationTest {

    private static final String HOME = "/api/v1/mobile/home";
    private static final String PASSWORD = "Password123";
    private static final LocalDate TODAY = LocalDate.now();
    private static final AtomicInteger CLIENT = new AtomicInteger(1);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganisationRepository organisationRepository;
    @Autowired UserRepository userRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired DepartmentRepository departmentRepository;
    @Autowired MaintenanceRecordRepository maintenanceRepository;
    @Autowired AssetTransferRepository transferRepository;
    @Autowired CheckoutRecordRepository checkoutRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired BudgetRepository budgetRepository;
    @Autowired ExchangeRateRepository exchangeRateRepository;

    private Organisation org;
    private String suffix;
    private String adminToken;
    private User admin;
    private String assetManagerToken;
    private User assetManager;
    private String financeToken;

    @BeforeEach
    void registerTenantAndUsers() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        String adminEmail = "home-admin+" + suffix + "@example.com";

        TenantRegisterRequest req = new TenantRegisterRequest();
        req.setOrganisationName("Mobile Home Org " + suffix);
        req.setOrganisationContactEmail("ops+" + suffix + "@example.com");
        req.setAdminFirstName("Akua");
        req.setAdminLastName("Admin");
        req.setAdminEmail(adminEmail);
        req.setPassword(PASSWORD);
        req.setCountry("GH");
        req.setTimezone("UTC");
        req.setIndustry("IT");
        MvcResult reg = mockMvc.perform(post("/api/v1/tenant/register")
                        .header("X-Forwarded-For", nextClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated()).andReturn();
        UUID orgId = objectMapper.readValue(reg.getResponse().getContentAsString(),
                TenantRegisterResponse.class).getOrganisationId();

        org = organisationRepository.findById(orgId).orElseThrow();
        adminToken = login(adminEmail);
        admin = userRepository.findByEmailAndOrganisationId(adminEmail, orgId).orElseThrow();

        // ASSET_MANAGER holds TRANSFER_ASSET but not VIEW_BUDGETS; FINANCE_MANAGER
        // holds VIEW_ASSETS and VIEW_BUDGETS but neither maintenance nor transfers.
        String assetManagerEmail = registerUser("ASSET_MANAGER", "am");
        assetManagerToken = login(assetManagerEmail);
        assetManager = userRepository.findByEmailAndOrganisationId(assetManagerEmail, orgId).orElseThrow();
        financeToken = login(registerUser("FINANCE_MANAGER", "fin"));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an org admin gets every section with exact counts")
    void adminSeesEveryCount() throws Exception {
        long unreadBefore = unread(admin);
        List<Asset> assets = seedEstate();
        notification(admin, false);
        notification(admin, false);
        notification(admin, true);
        notification(assetManager, false);   // someone else's: never counted for the admin

        JsonNode home = home(adminToken);
        JsonNode needsYou = home.path("needsYou");

        assertThat(needsYou.path("overdueMaintenance").asInt()).isEqualTo(2);
        // Two REQUESTED by the asset manager; the admin's own request is not theirs to approve.
        assertThat(needsYou.path("pendingTransferApprovals").asInt()).isEqualTo(2);
        assertThat(needsYou.path("overdueCheckouts").asInt()).isEqualTo(1);
        assertThat(needsYou.path("unreadNotifications").asLong()).isEqualTo(unreadBefore + 2);
        assertThat(needsYou.path("total").asLong()).isEqualTo(2 + 2 + 1 + unreadBefore + 2);

        JsonNode portfolio = home.path("portfolio");
        assertThat(portfolio.path("total").asLong()).isEqualTo(assets.size());
        assertThat(portfolio.path("byStatus").path("IN_USE").asLong()).isEqualTo(8);
        assertThat(portfolio.path("byStatus").path("IN_STOCK").asLong()).isEqualTo(4);
        assertThat(portfolio.path("byStatus").has("DISPOSED")).isFalse();

        // Overdue work by due date (-3, -2, -1 days), then approvals oldest first.
        JsonNode queue = home.path("queue");
        assertThat(queue).hasSize(5);
        assertQueueItem(queue.get(0), "MAINTENANCE", assets.get(0), "Preventive maintenance", "SCHEDULED",
                TODAY.minusDays(3), true);
        assertQueueItem(queue.get(1), "CHECKOUT", assets.get(0), "Checked out to Akua Admin", "ACTIVE",
                TODAY.minusDays(2), true);
        assertQueueItem(queue.get(2), "MAINTENANCE", assets.get(1), "Preventive maintenance", "IN_PROGRESS",
                TODAY.minusDays(1), true);
        assertQueueItem(queue.get(3), "TRANSFER_APPROVAL", assets.get(5), "Requested by am User", "REQUESTED",
                null, false);
        assertQueueItem(queue.get(4), "TRANSFER_APPROVAL", assets.get(6), "Requested by am User", "REQUESTED",
                null, false);

        // ACTIVE 1000/250 plus EXCEEDED 200/300 = 550/1200 = 45.8% -> 46.
        // DRAFT and CLOSED budgets are not in force and are left out.
        assertThat(home.path("budgetUtilisationPct").asInt()).isEqualTo(46);
        assertThat(home.path("generatedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a caller without a permission gets null for that section, never zero")
    void missingPermissionIsNullNotZero() throws Exception {
        seedEstate();

        JsonNode finance = home(financeToken);
        JsonNode needsYou = finance.path("needsYou");
        assertThat(needsYou.has("overdueMaintenance")).isTrue();
        assertThat(needsYou.path("overdueMaintenance").isNull()).isTrue();
        assertThat(needsYou.path("pendingTransferApprovals").isNull()).isTrue();
        // VIEW_ASSETS reads the overdue checkout list, so the count is present.
        assertThat(needsYou.path("overdueCheckouts").asInt()).isEqualTo(1);
        assertThat(needsYou.path("total").asLong())
                .isEqualTo(1 + needsYou.path("unreadNotifications").asLong());
        assertThat(finance.path("portfolio").isObject()).isTrue();
        assertThat(finance.path("budgetUtilisationPct").asInt()).isEqualTo(46);

        // Only what finance may read reaches its queue: the overdue checkout.
        assertThat(finance.path("queue")).hasSize(1);
        assertThat(finance.path("queue").get(0).path("kind").asText()).isEqualTo("CHECKOUT");

        JsonNode manager = home(assetManagerToken);
        assertThat(manager.path("needsYou").path("overdueMaintenance").asInt()).isEqualTo(2);
        assertThat(manager.has("budgetUtilisationPct")).isTrue();
        assertThat(manager.path("budgetUtilisationPct").isNull())
                .as("ASSET_MANAGER lacks VIEW_BUDGETS")
                .isTrue();
    }

    @Test
    @DisplayName("a transfer the caller requested is not counted as awaiting their approval")
    void ownTransferRequestIsNotPendingForTheRequester() throws Exception {
        seedEstate();

        // The asset manager requested three transfers (two still REQUESTED, one
        // APPROVED) and can approve transfers; only the admin's request waits on them.
        JsonNode home = home(assetManagerToken);
        assertThat(home.path("needsYou").path("pendingTransferApprovals").asInt()).isEqualTo(1);

        List<String> approvalAssets = new ArrayList<>();
        home.path("queue").forEach(item -> {
            if ("TRANSFER_APPROVAL".equals(item.path("kind").asText())) {
                approvalAssets.add(item.path("title").asText());
            }
        });
        assertThat(approvalAssets).containsExactly("Home asset 4 " + suffix);
    }

    @Test
    @DisplayName("the queue is capped at five, overdue work ahead of approvals")
    void queueIsCappedAndOverdueFirst() throws Exception {
        List<Asset> assets = seedEstate();
        for (int i = 0; i < 4; i++) {
            maintenance(assets.get(8 + i), MaintenanceStatus.SCHEDULED, TODAY.minusDays(20 + i));
        }

        JsonNode queue = home(adminToken).path("queue");

        assertThat(queue).hasSize(5);
        List<LocalDate> due = new ArrayList<>();
        queue.forEach(item -> {
            assertThat(item.path("overdue").asBoolean()).isTrue();
            due.add(LocalDate.parse(item.path("dueAt").asText()));
        });
        assertThat(due).isSorted();
        assertThat(due.get(0)).isEqualTo(TODAY.minusDays(23));
        // Seven overdue items outrank the approvals, which fall off the end.
        assertThat(home(adminToken).path("needsYou").path("overdueMaintenance").asInt()).isEqualTo(6);
    }

    @Test
    @DisplayName("a caller with nothing to do gets an empty queue, not null")
    void emptyQueueIsAnEmptyList() throws Exception {
        JsonNode home = home(adminToken);
        assertThat(home.path("queue").isArray()).isTrue();
        assertThat(home.path("queue")).isEmpty();
        assertThat(home.path("recentlyUpdated")).isEmpty();
        assertThat(home.path("budgetUtilisationPct").isNull()).isTrue();
    }

    @Test
    @DisplayName("only ACTIVE checkouts past their expected return date are overdue")
    void overdueCheckoutsFollowTheOverdueList() throws Exception {
        seedEstate();

        int fromHome = home(adminToken).path("needsYou").path("overdueCheckouts").asInt();
        JsonNode overdueList = objectMapper.readTree(mockMvc.perform(get("/api/v1/checkouts/overdue")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(fromHome).isEqualTo(1);
        assertThat(overdueList).hasSize(fromHome);
    }

    @Test
    @DisplayName("recentlyUpdated is newest first, capped at ten, and skips deleted and never-updated assets")
    void recentlyUpdatedIsOrderedAndCapped() throws Exception {
        List<Asset> assets = seedEstate();
        // seedEstate stamps asset i with now - i minutes, so asset 0 is newest.
        Asset deleted = asset("Deleted newest " + suffix, AssetStatus.IN_USE);
        jdbc.update("UPDATE asset SET updated_at = ?, deleted_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().plusSeconds(60)), Timestamp.from(Instant.now()), deleted.getId());
        Asset neverUpdated = asset("Never updated " + suffix, AssetStatus.IN_USE);
        jdbc.update("UPDATE asset SET updated_at = NULL WHERE id = ?", neverUpdated.getId());

        JsonNode recent = home(adminToken).path("recentlyUpdated");

        assertThat(recent).hasSize(10);
        List<String> names = new ArrayList<>();
        recent.forEach(row -> names.add(row.path("name").asText()));
        List<String> expected = assets.subList(0, 10).stream().map(Asset::getName).toList();
        assertThat(names).containsExactlyElementsOf(expected);

        JsonNode first = recent.get(0);
        assertThat(first.path("id").asText()).isEqualTo(assets.get(0).getId().toString());
        assertThat(first.path("assetTag").asText()).isEqualTo(assets.get(0).getAssetTag());
        assertThat(first.path("status").asText()).isEqualTo(assets.get(0).getStatus().name());
        assertThat(Instant.parse(first.path("updatedAt").asText()))
                .isAfter(Instant.parse(recent.get(1).path("updatedAt").asText()));
    }

    @Test
    @DisplayName("budget utilisation converts currencies, and is null when a rate is missing")
    void budgetUtilisationIsCurrencySafe() throws Exception {
        String base = org.getBillingCurrency();
        String foreign = "JPY".equals(base) ? "EUR" : "JPY";

        budget(BudgetStatus.ACTIVE, base, "1000.00", "100.00");
        budget(BudgetStatus.ACTIVE, foreign, "100.00", "90.00");
        // No foreign->base rate yet: a figure that silently drops that budget
        // would read 10%, so the endpoint must say "unknown" instead.
        assertThat(home(adminToken).path("budgetUtilisationPct").isNull()).isTrue();

        ExchangeRate rate = new ExchangeRate();
        rate.setBaseCurrency(foreign);
        rate.setTargetCurrency(base);
        rate.setRate(new BigDecimal("10"));
        rate.setEffectiveDate(TODAY.minusDays(1));
        rate.setOrganisation(org);
        exchangeRateRepository.save(rate);

        // 1000 + 100*10 = 2000 allocated; 100 + 90*10 = 1000 spent -> 50%.
        assertThat(home(adminToken).path("budgetUtilisationPct").asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("an unauthenticated caller is refused")
    void unauthenticatedIsRefused() throws Exception {
        mockMvc.perform(get(HOME).header("X-Forwarded-For", nextClient()))
                .andExpect(status().is4xxClientError());
    }

    // ── Fixture ───────────────────────────────────────────────────────────────

    /**
     * Twelve live assets (8 IN_USE, 4 IN_STOCK) with strictly decreasing
     * updated_at, plus maintenance, transfers, checkouts and budgets whose
     * expected counts the tests assert. Returned newest first.
     */
    private List<Asset> seedEstate() {
        List<Asset> assets = new ArrayList<>();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        for (int i = 0; i < 12; i++) {
            Asset a = asset("Home asset " + i + " " + suffix, i % 3 == 2 ? AssetStatus.IN_STOCK : AssetStatus.IN_USE);
            jdbc.update("UPDATE asset SET updated_at = ? WHERE id = ?",
                    Timestamp.from(now.minus(i, ChronoUnit.MINUTES)), a.getId());
            assets.add(a);
        }

        // Maintenance: two open and past due; one due later; one completed long ago.
        maintenance(assets.get(0), MaintenanceStatus.SCHEDULED, TODAY.minusDays(3));
        maintenance(assets.get(1), MaintenanceStatus.IN_PROGRESS, TODAY.minusDays(1));
        maintenance(assets.get(2), MaintenanceStatus.SCHEDULED, TODAY.plusDays(5));
        maintenance(assets.get(3), MaintenanceStatus.COMPLETED, TODAY.minusYears(1));

        Department dest = new Department();
        dest.setName("Destination " + suffix);
        dest.setOrganisation(org);
        dest = departmentRepository.save(dest);
        transfer(assets.get(4), admin, dest, TransferStatus.REQUESTED, false);
        transfer(assets.get(5), assetManager, dest, TransferStatus.REQUESTED, false);
        transfer(assets.get(6), assetManager, dest, TransferStatus.REQUESTED, false);
        transfer(assets.get(7), assetManager, dest, TransferStatus.APPROVED, false);
        transfer(assets.get(8), assetManager, dest, TransferStatus.REQUESTED, true);   // soft-deleted

        // Checkouts: only the first is overdue. Due today is not yet late.
        checkout(assets.get(0), CheckoutStatus.ACTIVE, TODAY.minusDays(2));
        checkout(assets.get(1), CheckoutStatus.ACTIVE, TODAY.plusDays(2));
        checkout(assets.get(2), CheckoutStatus.ACTIVE, TODAY);
        checkout(assets.get(3), CheckoutStatus.ACTIVE, null);
        checkout(assets.get(4), CheckoutStatus.RETURNED, TODAY.minusDays(10));

        String base = org.getBillingCurrency();
        budget(BudgetStatus.ACTIVE, base, "1000.00", "250.00");
        budget(BudgetStatus.EXCEEDED, base, "200.00", "300.00");
        budget(BudgetStatus.DRAFT, base, "5000.00", "0.00");
        budget(BudgetStatus.CLOSED, base, "5000.00", "5000.00");
        return assets;
    }

    private Asset asset(String name, AssetStatus status) {
        Asset a = new Asset();
        a.setName(name);
        a.setAssetTag("HOME-" + UUID.randomUUID().toString().substring(0, 12));
        a.setStatus(status);
        a.setOrganisation(org);
        return assetRepository.save(a);
    }

    private void maintenance(Asset asset, MaintenanceStatus status, LocalDate nextDue) {
        MaintenanceRecord r = new MaintenanceRecord();
        r.setAsset(asset);
        r.setMaintenanceType(MaintenanceType.PREVENTIVE);
        r.setStatus(status);
        r.setScheduledDate(nextDue);
        r.setNextDueDate(nextDue);
        r.setOrganisation(org);
        maintenanceRepository.save(r);
    }

    private void transfer(Asset asset, User requester, Department to, TransferStatus status, boolean deleted) {
        AssetTransfer t = new AssetTransfer();
        t.setAsset(asset);
        t.setToDepartment(to);
        t.setRequestedBy(requester);
        t.setStatus(status);
        t.setOrganisation(org);
        if (deleted) {
            t.setDeletedAt(Instant.now());
        }
        transferRepository.save(t);
    }

    private void checkout(Asset asset, CheckoutStatus status, LocalDate expectedReturn) {
        CheckoutRecord c = new CheckoutRecord();
        c.setAsset(asset);
        c.setCheckedOutBy(admin);
        c.setCheckedOutAt(Instant.now());
        c.setExpectedReturnDate(expectedReturn);
        c.setStatus(status);
        c.setOrganisation(org);
        checkoutRepository.save(c);
    }

    private void budget(BudgetStatus status, String currency, String total, String spent) {
        Budget b = new Budget();
        b.setName("Budget " + status + " " + currency + " " + UUID.randomUUID());
        b.setStatus(status);
        b.setCurrency(currency);
        b.setTotalAmount(new BigDecimal(total));
        b.setSpentAmount(new BigDecimal(spent));
        b.setPeriodStart(TODAY.withDayOfYear(1));
        b.setPeriodEnd(TODAY.withDayOfYear(1).plusYears(1).minusDays(1));
        b.setOrganisation(org);
        budgetRepository.save(b);
    }

    private void notification(User user, boolean read) {
        Notification n = new Notification();
        n.setUser(user);
        n.setOrganisation(org);
        n.setType(NotificationType.SYSTEM);
        n.setTitle("Home test");
        n.setMessage("Home test notification");
        n.setRead(read);
        notificationRepository.save(n);
    }

    private static void assertQueueItem(JsonNode item, String kind, Asset asset, String subtitle, String status,
                                        LocalDate dueAt, boolean overdue) {
        assertThat(item.path("kind").asText()).isEqualTo(kind);
        assertThat(item.path("id").asText()).isNotBlank();
        assertThat(item.path("assetId").asText()).isEqualTo(asset.getId().toString());
        assertThat(item.path("title").asText()).isEqualTo(asset.getName());
        assertThat(item.path("subtitle").asText()).isEqualTo(subtitle);
        assertThat(item.path("status").asText()).isEqualTo(status);
        if (dueAt == null) {
            assertThat(item.has("dueAt")).isTrue();
            assertThat(item.path("dueAt").isNull()).isTrue();
        } else {
            assertThat(item.path("dueAt").asText()).isEqualTo(dueAt.toString());
        }
        assertThat(item.path("overdue").asBoolean()).isEqualTo(overdue);
    }

    private long unread(User user) {
        return notificationRepository.countByUserAndOrganisationAndReadAndDeletedAtIsNull(user, org, false);
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private JsonNode home(String token) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(HOME)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private String registerUser(String roleName, String label) throws Exception {
        JsonNode roles = objectMapper.readTree(mockMvc.perform(get("/api/v1/roles")
                        .param("organisationId", org.getId().toString())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Forwarded-For", nextClient()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String roleId = null;
        for (JsonNode role : roles.isArray() ? roles : roles.path("items")) {
            if (roleName.equals(role.path("name").asText())) {
                roleId = role.path("id").asText();
            }
        }
        assertThat(roleId).as("seeded role %s", roleName).isNotNull();

        String email = label + "+" + suffix + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Forwarded-For", nextClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", label, "lastName", "User", "email", email,
                                "password", PASSWORD, "roleId", roleId,
                                "organisationId", org.getId().toString()))))
                .andExpect(status().isCreated());
        return email;
    }

    private String login(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", nextClient())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", email, "password", PASSWORD,
                                "organisationId", org.getId().toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("token").asText();
    }

    private static String nextClient() {
        return "10.20." + (CLIENT.get() / 250) + "." + (CLIENT.getAndIncrement() % 250 + 1);
    }
}
