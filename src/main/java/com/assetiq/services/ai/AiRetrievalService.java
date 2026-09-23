package com.assetiq.services.ai;

import com.assetiq.models.*;
import com.assetiq.models.compliance.ComplianceControl;
import com.assetiq.models.compliance.RiskRegister;
import com.assetiq.repositories.*;
import com.assetiq.repositories.compliance.ComplianceControlRepository;
import com.assetiq.repositories.compliance.RiskRegisterRepository;
import com.assetiq.services.finance.DepreciationCalculator;
import com.assetiq.services.finance.PortfolioValuation;
import com.assetiq.services.money.CurrencyConversion;
import com.assetiq.services.money.MoneyAccumulator;
import com.assetiq.services.money.MoneyAggregator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the grounding context for one question, from one organisation's records,
 * limited to what the asking user is allowed to read.
 *
 * <h3>Why structured retrieval and not a vector store</h3>
 * The questions this assistant is asked ("what is overdue", "which licences expire
 * this quarter", "how much of the budget is left") are answered by the tenant's
 * own tables, which are small, already indexed and already permission-governed by
 * the same repositories the REST API uses. An embedding index would add a second
 * copy of tenant data that has to be kept in sync, re-secured, and re-checked for
 * cross-tenant bleed on every write — a large new leak surface bought for no
 * accuracy the tables do not already give. If embeddings are added later the rule
 * stands: the index may narrow the candidate set, but the permission check runs at
 * retrieval time against live rows, never baked into the index.
 *
 * <h3>Scoping</h3>
 * Every query takes the tenant {@link Organisation} as a parameter, so the
 * database never returns another tenant's rows — there is no fetch-then-filter
 * step whose counts, totals or error messages could leak across the boundary.
 * Sections the caller cannot read are never queried at all.
 */
@Service
@Transactional(readOnly = true)
public class AiRetrievalService {

    // Per-section caps. Deterministic: the same question on the same data builds
    // the same prompt, and the prompt cannot grow with the tenant.
    static final int MAX_ASSETS      = 60;
    static final int MAX_MAINTENANCE = 40;
    static final int MAX_USERS       = 40;
    static final int MAX_CONTRACTS   = 30;
    static final int MAX_LICENCES    = 30;
    static final int MAX_SUPPLIERS   = 30;
    static final int MAX_DISPOSALS   = 30;
    static final int MAX_COMPLIANCE  = 30;
    static final int MAX_RISKS       = 20;
    static final int MAX_INSIGHTS    = 25;

    private final AssetRepository             assetRepo;
    private final MaintenanceRecordRepository maintenanceRepo;
    private final UserRepository              userRepo;
    private final DepartmentRepository        departmentRepo;
    private final BudgetRepository            budgetRepo;
    private final PredictiveInsightRepository insightRepo;
    private final LocationRepository          locationRepo;
    private final ComplianceControlRepository complianceRepo;
    private final RiskRegisterRepository      riskRepo;
    private final ContractRepository          contractRepo;
    private final SoftwareLicenseRepository   licenceRepo;
    private final SupplierRepository          supplierRepo;
    private final DisposalRecordRepository    disposalRepo;
    private final MoneyAggregator             moneyAggregator;
    private final ObjectMapper                objectMapper;

    public AiRetrievalService(AssetRepository assetRepo,
                              MaintenanceRecordRepository maintenanceRepo,
                              UserRepository userRepo,
                              DepartmentRepository departmentRepo,
                              BudgetRepository budgetRepo,
                              PredictiveInsightRepository insightRepo,
                              LocationRepository locationRepo,
                              ComplianceControlRepository complianceRepo,
                              RiskRegisterRepository riskRepo,
                              ContractRepository contractRepo,
                              SoftwareLicenseRepository licenceRepo,
                              SupplierRepository supplierRepo,
                              DisposalRecordRepository disposalRepo,
                              MoneyAggregator moneyAggregator,
                              ObjectMapper objectMapper) {
        this.assetRepo       = assetRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.userRepo        = userRepo;
        this.departmentRepo  = departmentRepo;
        this.budgetRepo      = budgetRepo;
        this.insightRepo     = insightRepo;
        this.locationRepo    = locationRepo;
        this.complianceRepo  = complianceRepo;
        this.riskRepo        = riskRepo;
        this.contractRepo    = contractRepo;
        this.licenceRepo     = licenceRepo;
        this.supplierRepo    = supplierRepo;
        this.disposalRepo    = disposalRepo;
        this.moneyAggregator = moneyAggregator;
        this.objectMapper    = objectMapper;
    }

    /**
     * Retrieves grounding data for {@code org}, restricted to {@code granted}.
     *
     * @param org     the caller's organisation — the only organisation queried
     * @param granted the sections the caller may read, from {@link AiDataSection#grantedTo}
     */
    public AiContext retrieve(Organisation org, Set<AiDataSection> granted) {
        Objects.requireNonNull(org, "org");
        Set<AiDataSection> allowed = granted == null
                ? EnumSet.noneOf(AiDataSection.class)
                : EnumSet.copyOf(granted.isEmpty() ? EnumSet.noneOf(AiDataSection.class) : granted);

        StringBuilder out     = new StringBuilder();
        List<AiSource> cited  = new ArrayList<>();
        CurrencyConversion fx = moneyAggregator.begin(org);

        out.append("Organisation: ").append(PromptSanitizer.sanitize(org.getName()))
           .append(" | Industry: ").append(nvl(PromptSanitizer.sanitize(org.getIndustry())))
           .append(" | Country: ").append(nvl(PromptSanitizer.sanitize(org.getCountry())))
           .append("\nReporting currency: ").append(fx.baseCurrency())
           .append(" (every total below is converted into it)\n");

        // Assets are the spine: locations, users and licences all reference them,
        // so they are fetched once here and reused by the sections that need counts.
        List<Asset> assets = allowed.contains(AiDataSection.ASSETS)
                ? assetRepo.findAllByOrganisationAndDeletedAtIsNull(org)
                : List.of();

        if (allowed.contains(AiDataSection.ASSETS))      appendAssets(out, cited, assets, fx);
        if (allowed.contains(AiDataSection.MAINTENANCE)) appendMaintenance(out, cited, org);
        if (allowed.contains(AiDataSection.DEPARTMENTS)) appendDepartments(out, org);
        if (allowed.contains(AiDataSection.LOCATIONS))   appendLocations(out, org, assets);
        if (allowed.contains(AiDataSection.USERS))       appendUsers(out, org, assets);
        if (allowed.contains(AiDataSection.BUDGETS))     appendBudgets(out, cited, org, fx);
        if (allowed.contains(AiDataSection.CONTRACTS))   appendContracts(out, cited, org);
        if (allowed.contains(AiDataSection.LICENCES))    appendLicences(out, cited, org);
        if (allowed.contains(AiDataSection.SUPPLIERS))   appendSuppliers(out, cited, org);
        if (allowed.contains(AiDataSection.DISPOSALS))   appendDisposals(out, cited, org);
        if (allowed.contains(AiDataSection.COMPLIANCE))  appendCompliance(out, cited, org);
        if (allowed.contains(AiDataSection.RISKS))       appendRisks(out, cited, org);
        if (allowed.contains(AiDataSection.INSIGHTS))    appendInsights(out, cited, org);

        if (!fx.isComplete()) {
            out.append("\nNOTE: totals exclude amounts with no exchange rate for ")
               .append(String.join(", ", fx.missingRates())).append(".\n");
        }

        EnumSet<AiDataSection> denied = EnumSet.allOf(AiDataSection.class);
        denied.removeAll(allowed);

        return new AiContext(out.toString(), List.copyOf(cited), allowed, denied);
    }

    // ── Sections ─────────────────────────────────────────────────────────────

    private void appendAssets(StringBuilder out, List<AiSource> cited, List<Asset> assets, CurrencyConversion fx) {
        MoneyAccumulator cost = fx.sum(assets, Asset::getPurchaseCost, Asset::getCurrency);
        MoneyAccumulator book = PortfolioValuation.of(fx, assets, LocalDate.now()).netBookValue();

        out.append("\n## ASSETS (").append(assets.size()).append(" total)\n")
           .append("By status: ").append(json(countBy(assets, a -> name(a.getStatus())))).append('\n')
           .append("By condition: ").append(json(countBy(assets, a -> name(a.getCondition())))).append('\n')
           .append("Total purchase cost: ").append(money(cost))
           .append(" | Total net book value: ").append(money(book)).append('\n');

        List<Map<String, Object>> sample = assets.stream().limit(MAX_ASSETS).map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("assetTag",         PromptSanitizer.sanitize(a.getAssetTag()));
            m.put("name",             PromptSanitizer.sanitize(a.getName()));
            m.put("status",           name(a.getStatus()));
            m.put("condition",        name(a.getCondition()));
            m.put("manufacturer",     PromptSanitizer.sanitize(a.getManufacturer()));
            m.put("model",            PromptSanitizer.sanitize(a.getModel()));
            m.put("purchaseCost",     a.getPurchaseCost());
            m.put("currency",         a.getCurrency());
            m.put("netBookValue",     DepreciationCalculator.forAsset(a, LocalDate.now()).netBookValue());
            m.put("warrantyExpiry",   a.getWarrantyExpiryDate());
            m.put("department",       a.getDepartment() != null ? PromptSanitizer.sanitize(a.getDepartment().getName()) : null);
            m.put("location",         a.getLocation()   != null ? PromptSanitizer.sanitize(a.getLocation().getName())   : null);
            cited.add(new AiSource("ASSET", ref(a.getAssetTag(), a.getId()), PromptSanitizer.sanitize(a.getName())));
            return m;
        }).toList();
        appendSample(out, "Assets", sample, assets.size(), MAX_ASSETS);
    }

    private void appendMaintenance(StringBuilder out, List<AiSource> cited, Organisation org) {
        Set<MaintenanceRecord> records = maintenanceRepo.findByOrganisationAndDeletedAtIsNull(org);
        LocalDate today = LocalDate.now();

        long overdue = records.stream().filter(m -> m.getScheduledDate() != null
                && m.getScheduledDate().isBefore(today)
                && m.getStatus() != null
                && !"COMPLETED".equals(m.getStatus().name())
                && !"CANCELLED".equals(m.getStatus().name())).count();
        long next7 = records.stream().filter(m -> m.getScheduledDate() != null
                && !m.getScheduledDate().isBefore(today)
                && m.getScheduledDate().isBefore(today.plusDays(7))).count();

        out.append("\n## MAINTENANCE (").append(records.size()).append(" records)\n")
           .append("By status: ").append(json(countBy(records, m -> name(m.getStatus()))))
           .append(" | Overdue: ").append(overdue)
           .append(" | Due within 7 days: ").append(next7).append('\n');

        List<Map<String, Object>> sample = records.stream().limit(MAX_MAINTENANCE).map(m -> {
            Map<String, Object> r = new LinkedHashMap<>();
            String assetTag = m.getAsset() != null ? m.getAsset().getAssetTag() : null;
            r.put("asset",         PromptSanitizer.sanitize(assetTag));
            r.put("type",          name(m.getMaintenanceType()));
            r.put("status",        name(m.getStatus()));
            r.put("scheduledDate", m.getScheduledDate());
            r.put("performedDate", m.getPerformedDate());
            r.put("nextDueDate",   m.getNextDueDate());
            r.put("cost",          m.getCost());
            r.put("description",   PromptSanitizer.sanitize(m.getDescription()));
            cited.add(new AiSource("MAINTENANCE", ref(assetTag, m.getId()),
                    PromptSanitizer.sanitize(name(m.getMaintenanceType()))));
            return r;
        }).toList();
        appendSample(out, "Maintenance", sample, records.size(), MAX_MAINTENANCE);
    }

    private void appendDepartments(StringBuilder out, Organisation org) {
        List<Department> departments = departmentRepo.findAllByOrganisationAndDeletedAtIsNull(org);
        List<Map<String, Object>> rows = departments.stream().map(d -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",        PromptSanitizer.sanitize(d.getName()));
            r.put("budgetLimit", d.getBudgetLimit());
            r.put("status",      name(d.getStatus()));
            return r;
        }).toList();
        out.append("\n## DEPARTMENTS (").append(departments.size()).append(")\n").append(json(rows)).append('\n');
    }

    private void appendLocations(StringBuilder out, Organisation org, List<Asset> assets) {
        Set<Location> locations = locationRepo.findByOrganisationAndDeletedAtIsNull(org);
        Map<UUID, Long> assetsPerLocation = assets.stream()
                .filter(a -> a.getLocation() != null)
                .collect(Collectors.groupingBy(a -> a.getLocation().getId(), Collectors.counting()));

        List<Map<String, Object>> rows = locations.stream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",       PromptSanitizer.sanitize(l.getName()));
            r.put("building",   PromptSanitizer.sanitize(l.getBuilding()));
            r.put("floor",      PromptSanitizer.sanitize(l.getFloor()));
            r.put("city",       PromptSanitizer.sanitize(l.getCity()));
            r.put("country",    PromptSanitizer.sanitize(l.getCountry()));
            r.put("assetCount", assetsPerLocation.getOrDefault(l.getId(), 0L));
            return r;
        }).sorted(Comparator.comparingLong(m -> -((Long) m.get("assetCount")))).toList();
        out.append("\n## LOCATIONS (").append(locations.size()).append(")\n").append(json(rows)).append('\n');
    }

    private void appendUsers(StringBuilder out, Organisation org, List<Asset> assets) {
        List<User> users = userRepo.findByOrganisationAndDeletedAtIsNull(org);
        Map<UUID, Long> assignedPerUser = assets.stream()
                .filter(a -> a.getAssignedUser() != null)
                .collect(Collectors.groupingBy(a -> a.getAssignedUser().getId(), Collectors.counting()));

        long active = users.stream()
                .filter(u -> u.getStatus() == null || "ACTIVE".equalsIgnoreCase(String.valueOf(u.getStatus())))
                .count();

        // Email is the org's own directory data and the caller holds VIEW_USERS,
        // but it is still personal data leaving the building, so only the display
        // name and job title go to the model. Anyone needing the address has the
        // people page, where the same authority applies.
        List<Map<String, Object>> rows = users.stream().map(u -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",           PromptSanitizer.sanitize(displayName(u)));
            r.put("jobTitle",       PromptSanitizer.sanitize(u.getJobTitle()));
            r.put("status",         u.getStatus() != null ? u.getStatus().name() : "ACTIVE");
            r.put("department",     u.getDepartment() != null ? PromptSanitizer.sanitize(u.getDepartment().getName()) : null);
            r.put("assignedAssets", assignedPerUser.getOrDefault(u.getId(), 0L));
            return r;
        }).sorted(Comparator.comparingLong(m -> -((Long) m.get("assignedAssets"))))
          .limit(MAX_USERS).toList();

        out.append("\n## PEOPLE (").append(users.size()).append(" total, ").append(active).append(" active)\n")
           .append(json(rows)).append('\n');
    }

    private void appendBudgets(StringBuilder out, List<AiSource> cited, Organisation org, CurrencyConversion fx) {
        List<Budget> budgets = budgetRepo.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org);
        MoneyAccumulator allocated = fx.sum(budgets, Budget::getTotalAmount, Budget::getCurrency);
        MoneyAccumulator spent     = fx.sum(budgets, Budget::getSpentAmount, Budget::getCurrency);
        int utilisation = allocated.rawSum().compareTo(BigDecimal.ZERO) > 0
                ? spent.rawSum().multiply(BigDecimal.valueOf(100))
                        .divide(allocated.rawSum(), 0, RoundingMode.HALF_UP).intValue()
                : 0;

        List<Map<String, Object>> rows = budgets.stream().map(b -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",        PromptSanitizer.sanitize(b.getName()));
            r.put("status",      name(b.getStatus()));
            r.put("totalAmount", b.getTotalAmount());
            r.put("spentAmount", b.getSpentAmount());
            r.put("currency",    b.getCurrency());
            r.put("periodStart", b.getPeriodStart());
            r.put("periodEnd",   b.getPeriodEnd());
            r.put("department",  b.getDepartment() != null ? PromptSanitizer.sanitize(b.getDepartment().getName()) : "Org-wide");
            cited.add(new AiSource("BUDGET", ref(b.getName(), b.getId()), PromptSanitizer.sanitize(b.getName())));
            return r;
        }).toList();

        out.append("\n## BUDGETS (").append(budgets.size()).append(")\n")
           .append("Allocated: ").append(money(allocated))
           .append(" | Spent: ").append(money(spent))
           .append(" | Utilisation: ").append(utilisation).append("%\n")
           .append(json(rows)).append('\n');
    }

    private void appendContracts(StringBuilder out, List<AiSource> cited, Organisation org) {
        List<Contract> contracts = contractRepo.findByOrganisationAndDeletedAtIsNullOrderByEndDateAsc(org);
        List<Map<String, Object>> rows = contracts.stream().limit(MAX_CONTRACTS).map(c -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("contractNumber", PromptSanitizer.sanitize(c.getContractNumber()));
            r.put("title",          PromptSanitizer.sanitize(c.getTitle()));
            r.put("type",           name(c.getContractType()));
            r.put("status",         name(c.getStatus()));
            r.put("supplier",       c.getSupplier() != null ? PromptSanitizer.sanitize(c.getSupplier().getName()) : null);
            r.put("startDate",      c.getStartDate());
            r.put("endDate",        c.getEndDate());
            r.put("value",          c.getValue());
            r.put("currency",       c.getCurrency());
            r.put("notes",          PromptSanitizer.sanitize(c.getNotes()));
            cited.add(new AiSource("CONTRACT", ref(c.getContractNumber(), c.getId()), PromptSanitizer.sanitize(c.getTitle())));
            return r;
        }).toList();
        out.append("\n## CONTRACTS (").append(contracts.size()).append(", soonest expiry first)\n");
        appendSample(out, "Contracts", rows, contracts.size(), MAX_CONTRACTS);
    }

    private void appendLicences(StringBuilder out, List<AiSource> cited, Organisation org) {
        List<SoftwareLicense> licences = licenceRepo.findByOrganisationAndDeletedAtIsNull(org);
        // licenseKey is a credential. It is never placed in a prompt.
        List<Map<String, Object>> rows = licences.stream().limit(MAX_LICENCES).map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",              PromptSanitizer.sanitize(l.getName()));
            r.put("vendor",            PromptSanitizer.sanitize(l.getVendor()));
            r.put("productName",       PromptSanitizer.sanitize(l.getProductName()));
            r.put("type",              name(l.getLicenseType()));
            r.put("status",            name(l.getStatus()));
            r.put("totalSeats",        l.getTotalSeats());
            r.put("usedSeats",         l.getUsedSeats());
            r.put("expiryDate",        l.getExpiryDate());
            r.put("renewalDate",       l.getRenewalDate());
            r.put("annualRenewalCost", l.getAnnualRenewalCost());
            r.put("currency",          l.getCurrency());
            cited.add(new AiSource("LICENCE", ref(l.getName(), l.getId()), PromptSanitizer.sanitize(l.getProductName())));
            return r;
        }).toList();
        out.append("\n## SOFTWARE LICENCES (").append(licences.size()).append(")\n");
        appendSample(out, "Licences", rows, licences.size(), MAX_LICENCES);
    }

    private void appendSuppliers(StringBuilder out, List<AiSource> cited, Organisation org) {
        Set<Supplier> suppliers = supplierRepo.findByOrganisationAndDeletedAtIsNull(org);
        // bankDetails and taxId are deliberately excluded.
        List<Map<String, Object>> rows = suppliers.stream().limit(MAX_SUPPLIERS).map(s -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",          PromptSanitizer.sanitize(s.getName()));
            r.put("contactPerson", PromptSanitizer.sanitize(s.getContactPerson()));
            r.put("phone",         PromptSanitizer.sanitize(s.getPhone()));
            cited.add(new AiSource("SUPPLIER", ref(s.getName(), s.getId()), PromptSanitizer.sanitize(s.getName())));
            return r;
        }).toList();
        out.append("\n## SUPPLIERS (").append(suppliers.size()).append(")\n");
        appendSample(out, "Suppliers", rows, suppliers.size(), MAX_SUPPLIERS);
    }

    private void appendDisposals(StringBuilder out, List<AiSource> cited, Organisation org) {
        Set<DisposalRecord> disposals = disposalRepo.findByOrganisationAndDeletedAtIsNull(org);
        List<Map<String, Object>> rows = disposals.stream().limit(MAX_DISPOSALS).map(d -> {
            Map<String, Object> r = new LinkedHashMap<>();
            String assetTag = d.getAsset() != null ? d.getAsset().getAssetTag() : null;
            r.put("asset",        PromptSanitizer.sanitize(assetTag));
            r.put("method",       name(d.getDisposalMethod()));
            r.put("status",       name(d.getStatus()));
            r.put("disposalDate", d.getDisposalDate());
            r.put("saleValue",    d.getSaleValue());
            r.put("currency",     d.getCurrency());
            r.put("reason",       PromptSanitizer.sanitize(d.getReason()));
            cited.add(new AiSource("DISPOSAL", ref(assetTag, d.getId()), PromptSanitizer.sanitize(name(d.getDisposalMethod()))));
            return r;
        }).toList();
        out.append("\n## DISPOSALS (").append(disposals.size()).append(")\n");
        appendSample(out, "Disposals", rows, disposals.size(), MAX_DISPOSALS);
    }

    private void appendCompliance(StringBuilder out, List<AiSource> cited, Organisation org) {
        List<ComplianceControl> controls = complianceRepo.findByOrganisationAndDeletedAtIsNull(org);
        long gaps = controls.stream().filter(c -> c.getStatus() != null
                && ("NOT_IMPLEMENTED".equals(c.getStatus().name()) || "PARTIAL".equals(c.getStatus().name()))).count();

        List<Map<String, Object>> rows = controls.stream()
                .filter(c -> c.getStatus() != null
                        && !"IMPLEMENTED".equals(c.getStatus().name())
                        && !"NOT_APPLICABLE".equals(c.getStatus().name()))
                .limit(MAX_COMPLIANCE)
                .map(c -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("framework",     name(c.getFramework()));
                    r.put("controlRef",    PromptSanitizer.sanitize(c.getControlRef()));
                    r.put("controlName",   PromptSanitizer.sanitize(c.getControlName()));
                    r.put("status",        name(c.getStatus()));
                    r.put("gap",           PromptSanitizer.sanitize(c.getGapDescription()));
                    r.put("remediation",   PromptSanitizer.sanitize(c.getRemediationPlan()));
                    r.put("reviewDueDate", c.getReviewDueDate());
                    cited.add(new AiSource("CONTROL", ref(c.getControlRef(), c.getId()), PromptSanitizer.sanitize(c.getControlName())));
                    return r;
                }).toList();

        out.append("\n## COMPLIANCE (").append(controls.size()).append(" controls)\n")
           .append("By framework: ").append(json(countBy(controls, c -> name(c.getFramework())))).append('\n')
           .append("By status: ").append(json(countBy(controls, c -> name(c.getStatus()))))
           .append(" | Gaps: ").append(gaps).append('\n');
        appendSample(out, "Open controls", rows, rows.size(), MAX_COMPLIANCE);
    }

    private void appendRisks(StringBuilder out, List<AiSource> cited, Organisation org) {
        List<RiskRegister> risks = new ArrayList<>(
                riskRepo.findByOrganisationAndStatusAndDeletedAtIsNull(org, RiskRegister.RiskStatus.OPEN));
        risks.addAll(riskRepo.findByOrganisationAndStatusAndDeletedAtIsNull(org, RiskRegister.RiskStatus.IN_TREATMENT));

        List<Map<String, Object>> rows = risks.stream().limit(MAX_RISKS).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("riskId",      PromptSanitizer.sanitize(r.getRiskId()));
            m.put("title",       PromptSanitizer.sanitize(r.getTitle()));
            m.put("description", PromptSanitizer.sanitize(r.getDescription()));
            m.put("likelihood",  r.getLikelihood());
            m.put("impact",      r.getImpact());
            m.put("riskScore",   r.getRiskScore());
            m.put("treatment",   name(r.getTreatment()));
            m.put("status",      name(r.getStatus()));
            cited.add(new AiSource("RISK", ref(r.getRiskId(), r.getId()), PromptSanitizer.sanitize(r.getTitle())));
            return m;
        }).toList();
        out.append("\n## RISK REGISTER (").append(risks.size()).append(" open or in treatment)\n");
        appendSample(out, "Risks", rows, risks.size(), MAX_RISKS);
    }

    private void appendInsights(StringBuilder out, List<AiSource> cited, Organisation org) {
        List<PredictiveInsight> insights =
                insightRepo.findByOrganisationAndResolvedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(org);
        List<Map<String, Object>> rows = insights.stream().limit(MAX_INSIGHTS).map(i -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("title",       PromptSanitizer.sanitize(i.getTitle()));
            r.put("description", PromptSanitizer.sanitize(i.getDescription()));
            r.put("severity",    name(i.getSeverity()));
            r.put("type",        name(i.getInsightType()));
            cited.add(new AiSource("INSIGHT", String.valueOf(i.getId()), PromptSanitizer.sanitize(i.getTitle())));
            return r;
        }).toList();
        out.append("\n## PREDICTIVE INSIGHTS (").append(insights.size()).append(" unresolved)\n")
           .append("By severity: ").append(json(countBy(insights, i -> name(i.getSeverity())))).append('\n');
        appendSample(out, "Insights", rows, insights.size(), MAX_INSIGHTS);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void appendSample(StringBuilder out, String what, List<Map<String, Object>> rows, int total, int cap) {
        out.append(json(rows)).append('\n');
        if (total > cap) {
            out.append(what).append(": showing ").append(cap).append(" of ").append(total)
               .append(" — say so if the answer depends on the rest.\n");
        }
    }

    private static String displayName(User u) {
        String first = u.getFirstName() != null ? u.getFirstName() : "";
        String last  = u.getLastName()  != null ? u.getLastName()  : "";
        String full  = (first + " " + last).trim();
        return full.isEmpty() ? "(unnamed)" : full;
    }

    /** Business identifier where one exists, otherwise the surrogate key. */
    private static String ref(String businessRef, UUID id) {
        String cleaned = PromptSanitizer.sanitize(businessRef, 64);
        return cleaned != null ? cleaned : String.valueOf(id);
    }

    private static String name(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    private static String nvl(String s) {
        return s != null ? s : "unknown";
    }

    private static String money(MoneyAccumulator total) {
        return total.amount().toPlainString() + " " + total.total().currency();
    }

    private static <T> Map<String, Long> countBy(Iterable<T> items, Function<T, String> keyFn) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (T item : items) {
            String key = keyFn.apply(item);
            counts.merge(key != null ? key : "UNKNOWN", 1L, Long::sum);
        }
        return counts;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
