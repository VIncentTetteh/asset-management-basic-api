package com.assetiq.imports.handlers;

import com.assetiq.dto.AssetDto;
import com.assetiq.enums.AssetCondition;
import com.assetiq.enums.AssetStatus;
import com.assetiq.enums.AssetType;
import com.assetiq.enums.DepreciationMethod;
import com.assetiq.imports.AbstractImportRunner;
import com.assetiq.imports.ImportBeanValidator;
import com.assetiq.imports.ImportDataType;
import com.assetiq.imports.ImportEntityHandler;
import com.assetiq.imports.ImportEntityType;
import com.assetiq.imports.ImportFieldDescriptor;
import com.assetiq.imports.ImportOptions;
import com.assetiq.imports.ImportReferenceResolver;
import com.assetiq.imports.ImportRow;
import com.assetiq.models.Asset;
import com.assetiq.models.AssetCustomField;
import com.assetiq.models.Organisation;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.repositories.AssetCustomFieldRepository;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.services.AssetService;
import com.assetiq.services.FeatureFlagService;
import com.assetiq.services.UsageLimitService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static com.assetiq.imports.ImportFieldDescriptor.enumField;
import static com.assetiq.imports.ImportFieldDescriptor.field;

/**
 * Assets — the original import, now expressed as a handler like every other type.
 *
 * <p>{@link #FIELDS} is declared in the exact order of the legacy positional template
 * ({@code name, assetTag, serialNumber, …, insurancePolicyId}). That is load-bearing:
 * {@code AssetImportServiceImpl} maps column <i>i</i> to descriptor <i>i</i>, so a file
 * in the historical header order still imports byte-for-byte as it always did, while
 * the wizard can map any other order onto the same fields.</p>
 *
 * <p>Columns beyond the mapped set become asset custom fields when the tenant has
 * {@code commercial.governed-custom-fields} enabled, and are a row error when it does
 * not — unchanged from before, so the flag cannot be bypassed via a spreadsheet.</p>
 */
@Component
public class AssetImportHandler implements ImportEntityHandler {

    /** Flag that governs whether extra import columns may create asset custom fields. */
    public static final String CUSTOM_FIELDS_FLAG = "commercial.governed-custom-fields";

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("name", "Asset name", ImportDataType.STRING).required()
                    .example("Dell Latitude 5440")
                    .aliases("asset", "asset name", "item", "item name", "description",
                            "device name", "title", "equipment name", "hostname").build(),
            field("assetTag", "Asset tag", ImportDataType.STRING)
                    .example("LT-000123")
                    .notes("Your own label or barcode. Generated for you when left blank.")
                    .aliases("tag", "asset id", "asset number", "barcode", "inventory number",
                            "tag number", "asset code", "property tag", "finance tag").build(),
            field("serialNumber", "Serial number", ImportDataType.STRING)
                    .example("7QK2X93")
                    .aliases("serial", "serial no", "sn", "s/n", "service tag",
                            "manufacturer serial").build(),
            field("description", "Description", ImportDataType.TEXT)
                    .example("14-inch laptop issued to finance staff")
                    .aliases("notes", "details", "comments", "remarks").build(),
            enumField("assetType", "Asset type", AssetType.class)
                    .example("HARDWARE")
                    .aliases("type", "asset class", "kind", "asset kind").build(),
            field("manufacturer", "Manufacturer", ImportDataType.STRING)
                    .example("Dell")
                    .aliases("make", "brand", "oem", "vendor").build(),
            field("model", "Model", ImportDataType.STRING)
                    .example("Latitude 5440")
                    .aliases("model number", "model name", "product model", "part number").build(),
            field("purchaseDate", "Purchase date", ImportDataType.DATE)
                    .example("2024-03-12")
                    .notes("YYYY-MM-DD. DD/MM/YYYY is also accepted.")
                    .aliases("date purchased", "acquired", "acquisition date", "bought",
                            "invoice date", "in service date").build(),
            field("purchaseCost", "Purchase cost", ImportDataType.DECIMAL)
                    .example("18500.00")
                    .notes("Numbers only; the currency goes in its own column.")
                    .aliases("cost", "price", "value", "purchase price", "acquisition cost",
                            "original cost", "amount").build(),
            field("currency", "Currency", ImportDataType.STRING)
                    .example("GHS")
                    .notes("Three-letter ISO 4217 code, e.g. GHS, USD, GBP.")
                    .aliases("ccy", "currency code", "iso currency").build(),
            enumField("depreciationMethod", "Depreciation method", DepreciationMethod.class)
                    .example("STRAIGHT_LINE")
                    .aliases("depreciation", "method", "dep method", "depreciation type").build(),
            field("usefulLifeMonths", "Useful life (months)", ImportDataType.INTEGER)
                    .example("48")
                    .notes("Whole number of months. A 4-year life is 48, not 4.")
                    .aliases("useful life", "life months", "depreciation period",
                            "life", "lifespan months").build(),
            field("residualValue", "Residual value", ImportDataType.DECIMAL)
                    .example("1500.00")
                    .notes("Salvage value at the end of the useful life.")
                    .aliases("salvage value", "scrap value", "residual", "end value").build(),
            field("warrantyExpiryDate", "Warranty expiry date", ImportDataType.DATE)
                    .example("2027-03-11")
                    .notes("YYYY-MM-DD.")
                    .aliases("warranty end", "warranty expiration", "warranty until",
                            "warranty expiry", "warranty date").build(),
            enumField("status", "Status", AssetStatus.class)
                    .example("IN_USE")
                    .aliases("asset status", "state", "lifecycle status", "disposition").build(),
            enumField("condition", "Condition", AssetCondition.class)
                    .example("GOOD")
                    .aliases("asset condition", "physical condition", "grade", "state of repair").build(),
            field("category", "Category", ImportDataType.REFERENCE)
                    .example("Laptops")
                    .notes("Name of a category in your organisation.")
                    .aliases("asset category", "class", "classification", "group", "sub category").build(),
            field("location", "Location", ImportDataType.REFERENCE)
                    .example("Accra Head Office")
                    .notes("Name of a location in your organisation.")
                    .aliases("site", "office", "branch", "building", "room", "where",
                            "physical location").build(),
            field("supplier", "Supplier", ImportDataType.REFERENCE)
                    .example("Acme Technologies Ltd")
                    .notes("Name of a supplier in your organisation.")
                    .aliases("vendor", "supplier name", "vendor name", "purchased from",
                            "reseller").build(),
            field("department", "Department", ImportDataType.REFERENCE)
                    .example("Finance")
                    .notes("Name or code of a department in your organisation.")
                    .aliases("dept", "cost centre", "cost center", "org unit", "business unit",
                            "team", "owning department").build(),
            field("assignedUserEmail", "Assigned user", ImportDataType.REFERENCE)
                    .example("ama.mensah@example.com")
                    .notes("Email address or employee number of an existing AssetIQ user.")
                    .aliases("assigned to", "assignee", "user", "owner", "custodian",
                            "holder", "assigned user", "employee email").build(),
            field("invoiceId", "Invoice reference", ImportDataType.STRING)
                    .example("INV-2024-0188")
                    .aliases("invoice", "invoice number", "invoice no", "bill reference",
                            "receipt number").build(),
            field("insurancePolicyId", "Insurance policy", ImportDataType.STRING)
                    .example("POL-99213")
                    .aliases("policy", "policy number", "insurance", "insurance reference").build()
    );

    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final AssetCustomFieldRepository assetCustomFieldRepository;
    private final ImportReferenceResolver referenceResolver;
    private final UsageLimitService usageLimitService;
    private final FeatureFlagService featureFlagService;
    private final TransactionTemplate transactionTemplate;
    private final ImportBeanValidator beanValidator;

    public AssetImportHandler(AssetService assetService,
                              AssetRepository assetRepository,
                              AssetCustomFieldRepository assetCustomFieldRepository,
                              ImportReferenceResolver referenceResolver,
                              UsageLimitService usageLimitService,
                              FeatureFlagService featureFlagService,
                              TransactionTemplate transactionTemplate,
                              ImportBeanValidator beanValidator) {
        this.assetService = assetService;
        this.assetRepository = assetRepository;
        this.assetCustomFieldRepository = assetCustomFieldRepository;
        this.referenceResolver = referenceResolver;
        this.usageLimitService = usageLimitService;
        this.featureFlagService = featureFlagService;
        this.transactionTemplate = transactionTemplate;
        this.beanValidator = beanValidator;
    }

    @Override
    public ImportEntityType entityType() {
        return ImportEntityType.ASSETS;
    }

    @Override
    public List<ImportFieldDescriptor> fields() {
        return FIELDS;
    }

    @Override
    public boolean unmappedColumnsBecomeCustomFields() {
        return true;
    }

    @Override
    public ImportRunner runner(Organisation organisation, ImportOptions options) {
        return new Runner(organisation, options);
    }

    /** An asset row: the DTO plus whatever extra columns the sheet carried. */
    public record AssetPayload(AssetDto dto, Map<String, String> customFields) {}

    private final class Runner extends AbstractImportRunner<AssetPayload> {

        private final Organisation organisation;
        private final ImportReferenceResolver.Refs refs;
        private final Map<String, UUID> byAssetTag = new LinkedHashMap<>();
        private final Map<String, UUID> byName = new LinkedHashMap<>();
        private Boolean customFieldsEnabled;
        private int created;

        private Runner(Organisation organisation, ImportOptions options) {
            super(options);
            this.organisation = organisation;
            this.refs = referenceResolver.open(organisation, options);
            for (Asset asset : assetRepository.findAllByOrganisationAndDeletedAtIsNull(organisation)) {
                if (asset.getAssetTag() != null && !asset.getAssetTag().isBlank()) {
                    byAssetTag.putIfAbsent(key(asset.getAssetTag()), asset.getId());
                }
                if (asset.getName() != null) {
                    byName.putIfAbsent(nameKey(asset.getName(),
                            asset.getDepartment() == null ? null : asset.getDepartment().getId()),
                            asset.getId());
                }
            }
        }

        @Override
        protected AssetPayload build(ImportRow row) {
            AssetDto dto = new AssetDto();
            dto.setName(row.requiredString("name"));
            dto.setAssetTag(row.string("assetTag"));
            dto.setSerialNumber(row.string("serialNumber"));
            dto.setDescription(row.string("description"));
            dto.setAssetType(row.enumValue(AssetType.class, "assetType"));
            dto.setManufacturer(row.string("manufacturer"));
            dto.setModel(row.string("model"));
            dto.setPurchaseDate(row.date("purchaseDate"));
            dto.setPurchaseCost(row.decimal("purchaseCost"));
            String currency = row.string("currency");
            dto.setCurrency(currency == null ? null : currency.toUpperCase(Locale.ROOT));
            dto.setDepreciationMethod(row.enumValue(DepreciationMethod.class, "depreciationMethod"));
            dto.setUsefulLifeMonths(row.integer("usefulLifeMonths"));
            dto.setResidualValue(row.decimal("residualValue"));
            dto.setWarrantyExpiryDate(row.date("warrantyExpiryDate"));
            dto.setStatus(row.enumValue(AssetStatus.class, "status"));
            dto.setCondition(row.enumValue(AssetCondition.class, "condition"));
            dto.setInvoiceId(row.string("invoiceId"));
            dto.setInsurancePolicyId(row.string("insurancePolicyId"));

            dto.setCategoryId(refs.category(row.string("category"), "category"));
            dto.setLocationId(refs.location(row.string("location"), "location"));
            dto.setSupplierId(refs.supplier(row.string("supplier"), "supplier"));
            dto.setDepartmentId(refs.department(row.string("department"), "department"));
            dto.setAssignedUserId(refs.user(row.string("assignedUserEmail"), "assignedUserEmail"));

            Map<String, String> customFields = new LinkedHashMap<>(row.unmapped());
            if (!customFields.isEmpty() && !customFieldsEnabled()) {
                throw new IllegalArgumentException("Extra column(s) " + customFields.keySet()
                        + " would become custom fields, which are not enabled for your organisation."
                        + " Remove them or leave them blank.");
            }
            beanValidator.validateForCreate(dto);
            return new AssetPayload(dto, customFields);
        }

        private boolean customFieldsEnabled() {
            if (customFieldsEnabled == null) {
                customFieldsEnabled = featureFlagService.isEnabledFor(CUSTOM_FIELDS_FLAG, organisation.getId());
            }
            return customFieldsEnabled;
        }

        @Override
        protected UUID findExisting(ImportRow row, AssetPayload payload) {
            AssetDto dto = payload.dto();
            if (dto.getAssetTag() != null && !dto.getAssetTag().isBlank()) {
                return byAssetTag.get(key(dto.getAssetTag()));
            }
            // Without a tag, an asset is only a duplicate of one with the same name in
            // the same department -- the same rule AssetService enforces on create, so
            // the import cannot disagree with the API about what a duplicate is.
            return byName.get(nameKey(dto.getName(), dto.getDepartmentId()));
        }

        /**
         * The plan ceiling, checked before the row is written and also on a dry run, so
         * a preview cannot promise an import the subscription will refuse halfway
         * through.
         */
        @Override
        protected void beforeCreate(AssetPayload payload) {
            if (!options.dryRun()) {
                // A real create goes through AssetService, which enforces the same limit
                // against live counts; re-checking here would double-count.
                created++;
                return;
            }
            SubscriptionPlan plan = usageLimitService.resolveEffectivePlan(organisation);
            if (plan == null) return;
            long projected = assetRepository.countByOrganisationAndDeletedAtIsNull(organisation) + created;
            if (projected >= plan.getMaxAssets()) {
                throw new AccessDeniedException("Asset limit reached for current plan. Upgrade your subscription.");
            }
            created++;
        }

        @Override
        protected void create(AssetPayload payload) {
            AssetDto dto = payload.dto();
            Map<String, String> customFields = payload.customFields();
            transactionTemplate.executeWithoutResult(status -> {
                AssetDto createdAsset = assetService.create(dto);
                if (createdAsset.getAssetTag() != null && !createdAsset.getAssetTag().isBlank()) {
                    byAssetTag.put(key(createdAsset.getAssetTag()), createdAsset.getId());
                }
                byName.putIfAbsent(nameKey(dto.getName(), dto.getDepartmentId()), createdAsset.getId());
                if (customFields.isEmpty()) return;

                Asset asset = assetRepository
                        .findByIdAndOrganisationAndDeletedAtIsNull(createdAsset.getId(), organisation)
                        .orElseThrow(() -> new IllegalStateException("Imported asset could not be reloaded"));
                for (Map.Entry<String, String> entry : customFields.entrySet()) {
                    AssetCustomField field = new AssetCustomField();
                    field.setAsset(asset);
                    field.setOrganisation(organisation);
                    field.setFieldName(entry.getKey());
                    field.setFieldValue(entry.getValue());
                    assetCustomFieldRepository.save(field);
                }
            });
        }

        @Override
        protected void update(UUID id, AssetPayload payload) {
            assetService.patch(id, payload.dto());
        }

        @Override
        protected String describe() {
            return "asset";
        }

        @Override
        protected String naturalKey(ImportRow row, AssetPayload payload) {
            AssetDto dto = payload.dto();
            return dto.getAssetTag() != null && !dto.getAssetTag().isBlank()
                    ? "asset tag '" + dto.getAssetTag() + "'"
                    : "name '" + dto.getName() + "'";
        }

        private String key(String value) {
            return value.trim().toLowerCase(Locale.ROOT);
        }

        private String nameKey(String name, UUID departmentId) {
            return key(name) + "\u0000" + (departmentId == null ? "" : departmentId);
        }
    }
}
