package com.example.demo.services.impl;

import com.example.demo.dto.AiChatRequest;
import com.example.demo.dto.AiChatResponse;
import com.example.demo.models.*;
import com.example.demo.models.compliance.ComplianceControl;
import com.example.demo.models.compliance.RiskRegister;
import com.example.demo.repositories.*;
import com.example.demo.repositories.compliance.ComplianceControlRepository;
import com.example.demo.repositories.compliance.RiskRegisterRepository;
import com.example.demo.services.AiChatService;
import com.example.demo.services.TenantAwareService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Server-side Retrieval-Augmented Generation (RAG) implementation.
 *
 * Supports three AI providers, selected via {@code ai.provider} in application.properties:
 *
 *   anthropic  — Anthropic Messages API  (claude-haiku-4-5-20251001)
 *   groq       — Groq OpenAI-compatible API  (llama-3.3-70b-versatile) [FREE]
 *   ollama     — Local Ollama server  (llama3.1:8b)  [FREE / offline]
 *
 * Flow:
 *   1. RETRIEVE  — Query all org data from the DB via JPA repositories
 *   2. AUGMENT   — Build a structured system prompt embedding all retrieved data
 *   3. GENERATE  — Call the configured LLM provider and return the answer
 */
@Service
@Transactional(readOnly = true)
public class AiChatServiceImpl extends TenantAwareService implements AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    // ── Provider identifiers ─────────────────────────────────────────────────
    private static final String PROVIDER_ANTHROPIC = "anthropic";
    private static final String PROVIDER_GROQ      = "groq";
    private static final String PROVIDER_OLLAMA    = "ollama";

    // ── Anthropic ────────────────────────────────────────────────────────────
    private static final String ANTHROPIC_URL     = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    // ── Groq (OpenAI-compatible, free tier) ──────────────────────────────────
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";

    // ── Prompt limits ────────────────────────────────────────────────────────
    private static final int MAX_TOKENS             = 1500;
    private static final int MAX_HISTORY_TURNS      = 20;
    private static final int MAX_ASSET_SAMPLE       = 60;
    private static final int MAX_MAINT_SAMPLE       = 40;
    private static final int MAX_INSIGHT_SAMPLE     = 25;
    private static final int MAX_COMPLIANCE_SAMPLE  = 30;
    private static final int MAX_RISK_SAMPLE        = 20;

    // ── Repositories ─────────────────────────────────────────────────────────
    private final AssetRepository             assetRepo;
    private final MaintenanceRecordRepository maintenanceRepo;
    private final UserRepository              userRepo;
    private final DepartmentRepository        departmentRepo;
    private final BudgetRepository            budgetRepo;
    private final PredictiveInsightRepository insightRepo;
    private final LocationRepository          locationRepo;
    private final ComplianceControlRepository complianceRepo;
    private final RiskRegisterRepository      riskRepo;

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;

    // ── Config ────────────────────────────────────────────────────────────────
    /** Which provider to use: anthropic | groq | ollama */
    @Value("${ai.provider:groq}")
    private String aiProvider;

    /** Anthropic API key — required when ai.provider=anthropic */
    @Value("${anthropic.api.key:}")
    private String anthropicApiKey;

    /** Anthropic model name */
    @Value("${anthropic.model:claude-haiku-4-5-20251001}")
    private String anthropicModel;

    /** Groq API key — required when ai.provider=groq. Free at console.groq.com */
    @Value("${groq.api.key:}")
    private String groqApiKey;

    /** Groq model — llama-3.3-70b-versatile is the recommended free model */
    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    /** Ollama base URL — required when ai.provider=ollama */
    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    /** Ollama model name */
    @Value("${ollama.model:llama3.1:8b}")
    private String ollamaModel;

    public AiChatServiceImpl(
            OrganisationRepository organisationRepository,
            AssetRepository assetRepo,
            MaintenanceRecordRepository maintenanceRepo,
            UserRepository userRepo,
            DepartmentRepository departmentRepo,
            BudgetRepository budgetRepo,
            PredictiveInsightRepository insightRepo,
            LocationRepository locationRepo,
            ComplianceControlRepository complianceRepo,
            RiskRegisterRepository riskRepo,
            ObjectMapper objectMapper) {
        super(organisationRepository);
        this.assetRepo       = assetRepo;
        this.maintenanceRepo = maintenanceRepo;
        this.userRepo        = userRepo;
        this.departmentRepo  = departmentRepo;
        this.budgetRepo      = budgetRepo;
        this.insightRepo     = insightRepo;
        this.locationRepo    = locationRepo;
        this.complianceRepo  = complianceRepo;
        this.riskRepo        = riskRepo;
        this.objectMapper    = objectMapper;
        this.httpClient      = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        validateProviderConfig();

        // ── 1. RETRIEVE ──────────────────────────────────────────────────────
        Organisation org = requireTenantOrg();

        List<Asset>             assets      = assetRepo.findAllByOrganisationAndDeletedAtIsNull(org);
        Set<MaintenanceRecord>  maintenance = maintenanceRepo.findByOrganisationAndDeletedAtIsNull(org);
        List<User>              users       = userRepo.findByOrganisationAndDeletedAtIsNull(org);
        List<Department>        departments = departmentRepo.findAllByOrganisationAndDeletedAtIsNull(org);
        List<Budget>            budgets     = budgetRepo.findByOrganisationAndDeletedAtIsNullOrderByPeriodStartDesc(org);
        List<PredictiveInsight> insights    = insightRepo
                .findByOrganisationAndResolvedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(org);
        Set<Location>           locations   = locationRepo.findByOrganisationAndDeletedAtIsNull(org);
        List<ComplianceControl> controls    = complianceRepo.findByOrganisationAndDeletedAtIsNull(org);
        List<RiskRegister>      risks       = riskRepo.findByOrganisationAndStatusAndDeletedAtIsNull(
                org, RiskRegister.RiskStatus.OPEN);
        risks.addAll(riskRepo.findByOrganisationAndStatusAndDeletedAtIsNull(
                org, RiskRegister.RiskStatus.IN_TREATMENT));

        log.info("[AI-RAG] provider={} org={} | assets={} maintenance={} users={} depts={} budgets={} insights={} locations={} controls={} risks={}",
                aiProvider, org.getId(), assets.size(), maintenance.size(),
                users.size(), departments.size(), budgets.size(), insights.size(),
                locations.size(), controls.size(), risks.size());

        // ── 2. AUGMENT ───────────────────────────────────────────────────────
        String systemPrompt = buildSystemPrompt(org, assets, maintenance, users, departments, budgets, insights, locations, controls, risks);

        // ── 3. GENERATE ──────────────────────────────────────────────────────
        return switch (aiProvider.toLowerCase()) {
            case PROVIDER_ANTHROPIC -> callAnthropic(systemPrompt, request);
            case PROVIDER_GROQ      -> callOpenAiCompatible(
                    GROQ_URL, "Bearer " + groqApiKey, groqModel, systemPrompt, request);
            case PROVIDER_OLLAMA    -> callOpenAiCompatible(
                    ollamaBaseUrl + "/api/chat/completions", null, ollamaModel, systemPrompt, request);
            default -> throw new IllegalStateException("Unknown ai.provider: " + aiProvider +
                    ". Valid values: anthropic, groq, ollama");
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // System prompt builder  (the "Augmentation" step)
    // ─────────────────────────────────────────────────────────────────────────

    private String buildSystemPrompt(
            Organisation org,
            List<Asset> assets,
            Set<MaintenanceRecord> maintenance,
            List<User> users,
            List<Department> departments,
            List<Budget> budgets,
            List<PredictiveInsight> insights,
            Set<Location> locations,
            List<ComplianceControl> controls,
            List<RiskRegister> risks) {

        String today = LocalDate.now().toString();

        // ── Asset aggregates ─────────────────────────────────────────────────
        Map<String, Long> byStatus    = groupByName(assets,    a -> a.getStatus()    != null ? a.getStatus().name()    : "UNKNOWN");
        Map<String, Long> byCondition = groupByName(assets,    a -> a.getCondition() != null ? a.getCondition().name() : "UNKNOWN");
        BigDecimal totalPurchaseCost  = sumDecimal(assets,     a -> a.getPurchaseCost());
        BigDecimal totalBookValue     = sumDecimal(assets,     a -> a.getCurrentBookValue() != null ? a.getCurrentBookValue() : a.getPurchaseCost());

        List<Map<String, Object>> assetSample = assets.stream().limit(MAX_ASSET_SAMPLE).map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name",             a.getName());
            m.put("assetTag",         a.getAssetTag());
            m.put("status",           a.getStatus()    != null ? a.getStatus().name()    : null);
            m.put("condition",        a.getCondition() != null ? a.getCondition().name() : null);
            m.put("manufacturer",     a.getManufacturer());
            m.put("model",            a.getModel());
            m.put("purchaseCost",     a.getPurchaseCost());
            m.put("currentBookValue", a.getCurrentBookValue());
            m.put("warrantyExpiry",   a.getWarrantyExpiryDate());
            m.put("department",       a.getDepartment()    != null ? a.getDepartment().getName() : null);
            m.put("location",         a.getLocation()      != null ? a.getLocation().getName()   : null);
            m.put("assignedTo",       a.getAssignedUser()  != null
                    ? a.getAssignedUser().getFirstName() + " " + a.getAssignedUser().getLastName()
                      + " <" + a.getAssignedUser().getEmail() + ">"
                    : null);
            return m;
        }).collect(Collectors.toList());

        // ── Maintenance aggregates ────────────────────────────────────────────
        LocalDate now     = LocalDate.now();
        LocalDate in7Days = now.plusDays(7);

        Map<String, Long> byMStatus = groupByName(maintenance, m -> m.getStatus() != null ? m.getStatus().name() : "UNKNOWN");
        long overdueCount   = maintenance.stream().filter(m ->
                m.getScheduledDate() != null && m.getScheduledDate().isBefore(now) &&
                m.getStatus() != null && !m.getStatus().name().equals("COMPLETED") && !m.getStatus().name().equals("CANCELLED")).count();
        long upcomingCount  = maintenance.stream().filter(m ->
                m.getScheduledDate() != null && !m.getScheduledDate().isBefore(now) && m.getScheduledDate().isBefore(in7Days)).count();
        BigDecimal totalMaintCost = sumDecimal(maintenance, m -> m.getCost());

        List<Map<String, Object>> maintSample = maintenance.stream().limit(MAX_MAINT_SAMPLE).map(m -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("type",          m.getMaintenanceType() != null ? m.getMaintenanceType().name() : null);
            r.put("status",        m.getStatus()          != null ? m.getStatus().name()          : null);
            r.put("scheduledDate", m.getScheduledDate());
            r.put("performedDate", m.getPerformedDate());
            r.put("nextDueDate",   m.getNextDueDate());
            r.put("cost",          m.getCost());
            r.put("description",   m.getDescription());
            return r;
        }).collect(Collectors.toList());

        // ── Users ────────────────────────────────────────────────────────────
        long activeUsers = users.stream()
                .filter(u -> u.getStatus() == null || "ACTIVE".equalsIgnoreCase(String.valueOf(u.getStatus())))
                .count();

        // ── Departments ───────────────────────────────────────────────────────
        List<Map<String, Object>> deptSummary = departments.stream().map(d -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",        d.getName());
            r.put("budgetLimit", d.getBudgetLimit());
            r.put("status",      d.getStatus() != null ? d.getStatus().name() : null);
            return r;
        }).collect(Collectors.toList());

        // ── Budgets ───────────────────────────────────────────────────────────
        BigDecimal totalAllocated = sumDecimal(budgets, b -> b.getTotalAmount());
        BigDecimal totalSpent     = sumDecimal(budgets, b -> b.getSpentAmount());
        int utilizationPct = totalAllocated.compareTo(BigDecimal.ZERO) > 0
                ? totalSpent.multiply(BigDecimal.valueOf(100))
                        .divide(totalAllocated, 0, java.math.RoundingMode.HALF_UP).intValue()
                : 0;

        List<Map<String, Object>> budgetSummary = budgets.stream().map(b -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",        b.getName());
            r.put("status",      b.getStatus() != null ? b.getStatus().name() : null);
            r.put("totalAmount", b.getTotalAmount());
            r.put("spentAmount", b.getSpentAmount());
            r.put("currency",    b.getCurrency());
            r.put("periodStart", b.getPeriodStart());
            r.put("periodEnd",   b.getPeriodEnd());
            r.put("department",  b.getDepartment() != null ? b.getDepartment().getName() : "Org-wide");
            return r;
        }).collect(Collectors.toList());

        // ── Insights ──────────────────────────────────────────────────────────
        Map<String, Long> bySeverity = groupByName(insights, i -> i.getSeverity() != null ? i.getSeverity().name() : "UNKNOWN");
        List<Map<String, Object>> insightSample = insights.stream().limit(MAX_INSIGHT_SAMPLE).map(i -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("title",       i.getTitle());
            r.put("description", i.getDescription());
            r.put("severity",    i.getSeverity()     != null ? i.getSeverity().name()     : null);
            r.put("type",        i.getInsightType()  != null ? i.getInsightType().name()  : null);
            return r;
        }).collect(Collectors.toList());

        // ── Locations ─────────────────────────────────────────────────────────
        List<Map<String, Object>> locationList = locations.stream().map(l -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",     l.getName());
            r.put("building", l.getBuilding());
            r.put("floor",    l.getFloor());
            r.put("room",     l.getRoom());
            r.put("city",     l.getCity());
            r.put("country",  l.getCountry());
            r.put("address",  l.getAddress());
            r.put("parent",   l.getParentLocation() != null ? l.getParentLocation().getName() : null);
            // Count assets at this location
            long assetCount = assets.stream()
                    .filter(a -> a.getLocation() != null && a.getLocation().getId().equals(l.getId()))
                    .count();
            r.put("assetCount", assetCount);
            return r;
        }).sorted(Comparator.comparingLong(m -> -((Long) m.get("assetCount"))))
          .collect(Collectors.toList());

        // ── Users with assignments ─────────────────────────────────────────────
        List<Map<String, Object>> userSummary = users.stream().map(u -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name",       u.getFirstName() + " " + u.getLastName());
            r.put("email",      u.getEmail());
            r.put("jobTitle",   u.getJobTitle());
            r.put("status",     u.getStatus() != null ? u.getStatus().name() : "ACTIVE");
            r.put("department", u.getDepartment() != null ? u.getDepartment().getName() : null);
            long assigned = assets.stream()
                    .filter(a -> a.getAssignedUser() != null && a.getAssignedUser().getId().equals(u.getId()))
                    .count();
            r.put("assignedAssets", assigned);
            return r;
        }).filter(u -> (Long) u.get("assignedAssets") > 0 || "ACTIVE".equals(u.get("status")))
          .sorted(Comparator.comparingLong(m -> -((Long) m.get("assignedAssets"))))
          .limit(40)
          .collect(Collectors.toList());

        // ── Compliance Controls ────────────────────────────────────────────────
        Map<String, Long> byFramework  = groupByName(controls, c -> c.getFramework() != null ? c.getFramework().name() : "UNKNOWN");
        Map<String, Long> byCtrlStatus = groupByName(controls, c -> c.getStatus()    != null ? c.getStatus().name()    : "UNKNOWN");
        long gapCount = controls.stream()
                .filter(c -> c.getStatus() != null &&
                        (c.getStatus().name().equals("NOT_IMPLEMENTED") || c.getStatus().name().equals("PARTIAL")))
                .count();

        List<Map<String, Object>> controlSample = controls.stream()
                .filter(c -> c.getStatus() != null &&
                        !c.getStatus().name().equals("IMPLEMENTED") &&
                        !c.getStatus().name().equals("NOT_APPLICABLE"))
                .limit(MAX_COMPLIANCE_SAMPLE)
                .map(c -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("framework",     c.getFramework()  != null ? c.getFramework().name()  : null);
                    r.put("controlRef",    c.getControlRef());
                    r.put("controlName",   c.getControlName());
                    r.put("status",        c.getStatus()     != null ? c.getStatus().name()     : null);
                    r.put("gapDesc",       c.getGapDescription());
                    r.put("remediation",   c.getRemediationPlan());
                    r.put("reviewDueDate", c.getReviewDueDate());
                    r.put("owner",         c.getOwner() != null
                            ? c.getOwner().getFirstName() + " " + c.getOwner().getLastName()
                            : null);
                    return r;
                }).collect(Collectors.toList());

        // ── Risk Register ──────────────────────────────────────────────────────
        List<Map<String, Object>> riskSample = risks.stream().limit(MAX_RISK_SAMPLE).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("riskId",     r.getRiskId());
            m.put("title",      r.getTitle());
            m.put("description",r.getDescription());
            m.put("likelihood", r.getLikelihood());
            m.put("impact",     r.getImpact());
            m.put("riskScore",  r.getRiskScore());
            m.put("treatment",  r.getTreatment()  != null ? r.getTreatment().name()  : null);
            m.put("status",     r.getStatus()     != null ? r.getStatus().name()     : null);
            m.put("framework",  r.getFramework()  != null ? r.getFramework().name()  : null);
            return m;
        }).collect(Collectors.toList());

        // ── Assemble ──────────────────────────────────────────────────────────
        return String.format("""
You are AssetIQ AI, the intelligent data assistant for **%s**.
Today's date is %s.
You have complete, real-time visibility into the organisation's data as provided below.
Answer questions accurately and concisely, grounding every answer in the actual data.
If a question asks about something not in the data, say so clearly rather than guessing.
Format responses for readability — use short paragraphs or bullet points where helpful.
Do NOT fabricate numbers or asset details.

━━━ ORGANISATION ━━━
Name: %s | Industry: %s | Country: %s

━━━ ASSETS (%d total) ━━━
By Status: %s
By Condition: %s
Total Purchase Cost: %s | Total Book Value: %s
Asset Sample (up to %d, includes location and assigned user):
%s

━━━ MAINTENANCE (%d records) ━━━
By Status: %s | Overdue: %d | Upcoming 7 days: %d
Total Maintenance Cost: %s
Record Sample (up to %d):
%s

━━━ USERS (%d total, %d active) ━━━
Users with assigned assets (sorted by assignment count):
%s

━━━ DEPARTMENTS (%d total) ━━━
%s

━━━ LOCATIONS (%d total) ━━━
%s

━━━ BUDGETS (%d total) ━━━
Total Allocated: %s | Total Spent: %s | Utilization: %d%%
%s

━━━ COMPLIANCE (%d controls total) ━━━
By Framework: %s
By Status: %s
Gaps/Non-implemented: %d controls
Non-compliant controls (sample up to %d):
%s

━━━ RISK REGISTER (%d open/in-treatment risks) ━━━
%s

━━━ AI INSIGHTS (%d unresolved) ━━━
By Severity: %s
%s""",
                org.getName(), today,
                org.getName(), nvl(org.getIndustry()), nvl(org.getCountry()),
                assets.size(), toJson(byStatus), toJson(byCondition),
                totalPurchaseCost.toPlainString(), totalBookValue.toPlainString(),
                MAX_ASSET_SAMPLE, toJson(assetSample),
                maintenance.size(), toJson(byMStatus), overdueCount, upcomingCount,
                totalMaintCost.toPlainString(), MAX_MAINT_SAMPLE, toJson(maintSample),
                users.size(), activeUsers, toJson(userSummary),
                departments.size(), toJson(deptSummary),
                locations.size(), toJson(locationList),
                budgets.size(), totalAllocated.toPlainString(), totalSpent.toPlainString(),
                utilizationPct, toJson(budgetSummary),
                controls.size(), toJson(byFramework), toJson(byCtrlStatus),
                gapCount, MAX_COMPLIANCE_SAMPLE, toJson(controlSample),
                risks.size(), toJson(riskSample),
                insights.size(), toJson(bySeverity), toJson(insightSample));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Provider: Anthropic Messages API
    // ─────────────────────────────────────────────────────────────────────────

    private AiChatResponse callAnthropic(String systemPrompt, AiChatRequest request) {
        try {
            List<Map<String, String>> messages = buildMessageList(request);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",      anthropicModel);
            body.put("max_tokens", MAX_TOKENS);
            body.put("system",     systemPrompt);
            body.put("messages",   messages);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(ANTHROPIC_URL))
                    .header("Content-Type",     "application/json")
                    .header("x-api-key",         anthropicApiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();

            HttpResponse<String> resp = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.error("[AI-RAG] Anthropic error {}: {}", resp.statusCode(), resp.body());
                throw new RuntimeException("AI service error: " + extractError(resp.body(), "error.message"));
            }

            JsonNode json = objectMapper.readTree(resp.body());
            String   text = json.path("content").get(0).path("text").asText("No response received.");
            String   id   = json.path("id").asText(conversationId(request));

            log.info("[AI-RAG] Anthropic response ok, conversationId={}", id);
            return new AiChatResponse(text, id);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reach Anthropic API: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Provider: OpenAI-compatible (Groq, Ollama)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Works with any server that speaks the OpenAI Chat Completions format:
     *   - Groq (groq.api.key, free tier)
     *   - Ollama (local, no key needed)
     *   - OpenAI itself, together.ai, fireworks.ai, etc.
     */
    private AiChatResponse callOpenAiCompatible(
            String apiUrl,
            String authorizationHeader,
            String model,
            String systemPrompt,
            AiChatRequest request) {
        try {
            // System message + history + current user message
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", systemPrompt));
            messages.addAll(buildMessageList(request));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model",      model);
            body.put("max_tokens", MAX_TOKENS);
            body.put("messages",   messages);

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(90))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));

            if (authorizationHeader != null) {
                builder.header("Authorization", authorizationHeader);
            }

            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                log.error("[AI-RAG] {} error {}: {}", aiProvider, resp.statusCode(), resp.body());
                throw new RuntimeException("AI service error: " + extractError(resp.body(), "error.message"));
            }

            JsonNode json = objectMapper.readTree(resp.body());
            String   text = json.path("choices").get(0).path("message").path("content").asText("No response received.");
            String   id   = json.path("id").asText(conversationId(request));

            log.info("[AI-RAG] {} response ok, conversationId={}", aiProvider, id);
            return new AiChatResponse(text, id);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to reach " + aiProvider + " API: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────────

    private void validateProviderConfig() {
        switch (aiProvider.toLowerCase()) {
            case PROVIDER_ANTHROPIC -> {
                if (anthropicApiKey == null || anthropicApiKey.isBlank())
                    throw new IllegalStateException("anthropic.api.key is required when ai.provider=anthropic");
            }
            case PROVIDER_GROQ -> {
                if (groqApiKey == null || groqApiKey.isBlank())
                    throw new IllegalStateException("groq.api.key is required when ai.provider=groq. Get a free key at console.groq.com");
            }
            case PROVIDER_OLLAMA -> {
                if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank())
                    throw new IllegalStateException("ollama.base-url is required when ai.provider=ollama");
            }
            default -> throw new IllegalStateException(
                    "Unknown ai.provider: '" + aiProvider + "'. Valid values: anthropic, groq, ollama");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private List<Map<String, String>> buildMessageList(AiChatRequest request) {
        List<Map<String, String>> messages = new ArrayList<>();
        if (request.history() != null) {
            request.history().stream()
                    .limit(MAX_HISTORY_TURNS)
                    .forEach(h -> messages.add(Map.of("role", h.role(), "content", h.content())));
        }
        messages.add(Map.of("role", "user", "content", request.message()));
        return messages;
    }

    private String conversationId(AiChatRequest request) {
        return request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();
    }

    private <T> Map<String, Long> groupByName(Iterable<T> items, java.util.function.Function<T, String> keyFn) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (T item : items) {
            String key = keyFn.apply(item);
            result.merge(key, 1L, Long::sum);
        }
        return result;
    }

    private <T> BigDecimal sumDecimal(Iterable<T> items, java.util.function.Function<T, BigDecimal> valueFn) {
        BigDecimal sum = BigDecimal.ZERO;
        for (T item : items) {
            BigDecimal v = valueFn.apply(item);
            if (v != null) sum = sum.add(v);
        }
        return sum;
    }

    private String extractError(String body, String dotPath) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String[] parts = dotPath.split("\\.");
            for (String part : parts) node = node.path(part);
            return node.isMissingNode() ? body : node.asText();
        } catch (Exception e) {
            return body;
        }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "[]"; }
    }

    private String nvl(String s) { return s != null ? s : "—"; }
}
