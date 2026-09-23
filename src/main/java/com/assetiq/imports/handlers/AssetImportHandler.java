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
import com.assetiq.imports.ImportRunReport;
import com.assetiq.imports.ImportWizardService;
import com.assetiq.models.Asset;
import com.assetiq.models.AssetCustomField;
import com.assetiq.models.Organisation;
import com.assetiq.models.SubscriptionPlan;
import com.assetiq.imports.CustomFieldDefinitions;
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
 * <p>Assets are the only type with anywhere to put a column the product has no field
 * for, so they are the only type that turns one into a custom field. Two ways in: the
 * wizard, where the user marked specific columns "create as a custom field"
 * ({@link ImportOptions#customFieldColumns()}), and the legacy positional path, where
 * everything past the fixed layout has always been taken
 * ({@link ImportOptions#captureUnmappedColumns()}). Both require the tenant to have
 * {@code commercial.governed-custom-fields} enabled; without the flag the columns are
 * left out with a note rather than failing the row, and the wizard is told up front so
 * it never offers the option at all. A column that is neither mapped nor chosen is not
 * read: that is what "ignore" means.</p>
 */
@Component
public class AssetImportHandler implements ImportEntityHandler {

    /** Flag that governs whether extra import columns may create asset custom fields. */
    public static final String CUSTOM_FIELDS_FLAG = ImportWizardService.CUSTOM_FIELDS_FLAG;

    private static final List<ImportFieldDescriptor> FIELDS = List.of(
            field("name", "Asset name", ImportDataType.STRING).required()
                    .example("Dell Latitude 5440")
                    .notes("A column headed just 'Asset' is left unmapped: it reads as the name "
                            + "to some tools and as the tag to others.")
                    .aliases("asset name", "item", "item name", "name",
                            "device name", "device", "title", "equipment", "equipment name",
                            "product name", "item description", "hostname").build(),
            field("assetTag", "Asset tag", ImportDataType.STRING)
                    .example("LT-000123")
                    .notes("Your own label or barcode. Generated for you when left blank.")
                    .aliases("tag", "asset id", "asset number", "asset no", "asset no.",
                            "tag no", "tag no.", "tag #", "tag number", "barcode",
                            "barcode number", "asset barcode", "inventory number",
                            "inventory no", "inventory id", "asset code", "property tag",
                            "finance tag", "fixed asset number", "fa number").build(),
            field("serialNumber", "Serial number", ImportDataType.STRING)
                    .example("7QK2X93")
                    .aliases("serial", "serial no", "serial no.", "serial num", "serial #",
                            "sn", "s/n", "service tag", "device serial",
                            "manufacturer serial", "imei").build(),
            field("description", "Description", ImportDataType.TEXT)
                    .example("14-inch laptop issued to finance staff")
                    .aliases("notes", "details", "comments", "remarks",
                            "long description", "asset description").build(),
            enumField("assetType", "Asset type", AssetType.class)
                    .example("HARDWARE")
                    .aliases("type", "asset class", "kind", "asset kind", "type of asset").build(),
            field("manufacturer", "Manufacturer", ImportDataType.STRING)
                    .example("Dell")
                    .notes("A 'Vendor' column is deliberately left unmapped: on an asset sheet it may "
                            + "mean the maker or the reseller, and guessing is worse than asking.")
                    .aliases("make", "brand", "oem", "manufacturer name", "vendor").build(),
            field("model", "Model", ImportDataType.STRING)
                    .example("Latitude 5440")
                    .aliases("model number", "model no", "model no.", "model num", "model #",
                            "model name", "product model", "product model number",
                            "part number", "part no").build(),
            field("purchaseDate", "Purchase date", ImportDataType.DATE)
                    .example("2024-03-12")
                    .notes("YYYY-MM-DD. DD/MM/YYYY is also accepted.")
                    .aliases("date purchased", "date of purchase", "purchased on", "acquired",
                            "date acquired", "acquired date", "acquisition", "acquisition date",
                            "bought", "received date", "date received", "invoice date",
                            "po date", "in service date").build(),
            field("purchaseCost", "Purchase cost", ImportDataType.DECIMAL)
                    .example("18500.00")
                    .notes("Numbers only; the currency goes in its own column.")
                    .aliases("cost", "price", "value", "purchase price", "purchase amount",
                            "acquisition cost", "acquisition value", "original cost",
                            "original value", "unit cost", "unit price", "cost price",
                            "net cost", "amount").build(),
            field("currency", "Currency", ImportDataType.STRING)
                    .example("GHS")
                    .notes("Three-letter ISO 4217 code, e.g. GHS, USD, GBP.")
                    .aliases("ccy", "currency code", "iso currency").build(),
            enumField("depreciationMethod", "Depreciation method", DepreciationMethod.class)
                    .example("STRAIGHT_LINE")
                    .aliases("depreciation", "method", "dep method", "depreciation method",
                            "depreciation type", "depreciation basis").build(),
            field("usefulLifeMonths", "Useful life (months)", ImportDataType.INTEGER)
                    .example("48")
                    .notes("Whole number of months. A 4-year life is 48, not 4.")
                    .aliases("useful life", "useful life months", "life months", "life in months",
                            "depreciation period", "depreciation months", "life",
                            "lifespan months", "economic life months").build(),
            field("residualValue", "Residual value", ImportDataType.DECIMAL)
                    .example("1500.00")
                    .notes("Salvage value at the end of the useful life.")
                    .aliases("salvage value", "salvage", "scrap value", "residual",
                            "residual amount", "end value").build(),
            field("warrantyExpiryDate", "Warranty expiry date", ImportDataType.DATE)
                    .example("2027-03-11")
                    .notes("YYYY-MM-DD.")
                    .aliases("warranty end", "warranty end date", "warranty expiration",
                            "warranty expires", "warranty until", "warranty expiry",
                            "warranty date", "end of warranty").build(),
            enumField("status", "Status", AssetStatus.class)
                    .example("IN_USE")
                    .aliases("asset status", "state", "asset state", "current status",
                            "lifecycle status", "disposition").build(),
            enumField("condition", "Condition", AssetCondition.class)
                    .example("GOOD")
                    .aliases("asset condition", "condition status", "physical condition",
                            "grade", "state of repair").build(),
            field("category", "Category", ImportDataType.REFERENCE)
                    .example("Laptops")
                    .notes("Name of a category in your organisation.")
                    .aliases("asset category", "category name", "class", "classification",
                            "group", "asset group", "sub category", "sub-category",
                            "family").build(),
            field("location", "Location", ImportDataType.REFERENCE)
                    .example("Accra Head Office")
                    .notes("Name of a location in your organisation.")
                    .aliases("site", "site name", "location name", "office", "office location",
                            "branch", "building", "room", "where",
                            "physical location", "current location").build(),
            field("supplier", "Supplier", ImportDataType.REFERENCE)
                    .example("Acme Technologies Ltd")
                    .notes("Name of a supplier in your organisation.")
                    .aliases("vendor", "supplier name", "vendor name", "purchased from",
                            "bought from", "reseller", "dealer", "seller").build(),
            field("department", "Department", ImportDataType.REFERENCE)
                    .example("Finance")
                    .notes("Name or code of a department in your organisation.")
                    .aliases("dept", "department name", "cost centre", "cost center",
                            "org unit", "business unit", "division", "team",
                            "owning department").build(),
            field("assignedUserEmail", "Assigned user", ImportDataType.REFERENCE)
                    .example("ama.mensah@example.com")
                    .notes("Email address or employee number of an existing AssetIQ user.")
                    .aliases("assigned to", "assigned to email", "assigned user",
                            "assigned user email", "assigned employee", "assignee",
                            "user", "user email", "end user", "owner", "owner email",
                            "custodian", "custodian email", "holder",
                            "employee email").build(),
            field("invoiceId", "Invoice reference", ImportDataType.STRING)
                    .example("INV-2024-0188")
                    .aliases("invoice", "invoice number", "invoice no", "invoice no.",
                            "invoice ref", "invoice reference", "bill reference",
                            "bill number", "receipt number").build(),
            field("insurancePolicyId", "Insurance policy", ImportDataType.STRING)
                    .example("POL-99213")
                    .aliases("policy", "policy id", "policy number", "policy no", "insurance",
                            "insurance policy number", "insurance reference").build()
    );

    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final AssetCustomFieldRepository assetCustomFieldRepository;
    private final CustomFieldDefinitions customFieldDefinitions;
    private final ImportReferenceResolver referenceResolver;
    private final UsageLimitService usageLimitService;
    private final FeatureFlagService featureFlagService;
    private final TransactionTemplate transactionTemplate;
    private final ImportBeanValidator beanValidator;

    public AssetImportHandler(AssetService assetService,
                              AssetRepository assetRepository,
                              AssetCustomFieldRepository assetCustomFieldRepository,
                              CustomFieldDefinitions customFieldDefinitions,
                              ImportReferenceResolver referenceResolver,
                              UsageLimitService usageLimitService,
                              FeatureFlagService featureFlagService,
                              TransactionTemplate transactionTemplate,
                              ImportBeanValidator beanValidator) {
        this.assetService = assetService;
        this.assetRepository = assetRepository;
        this.assetCustomFieldRepository = assetCustomFieldRepository;
        this.customFieldDefinitions = customFieldDefinitions;
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
    public ImportRunner runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
        return new Runner(organisation, options, report);
    }

    /** An asset row: the DTO plus whatever extra columns the sheet carried. */
    public record AssetPayload(AssetDto dto, Map<String, String> customFields) {}

    private final class Runner extends AbstractImportRunner<AssetPayload> {

        private final Organisation organisation;
        private final ImportRunReport report;
        private final ImportReferenceResolver.Refs refs;
        private final CustomFieldDefinitions.Session customFields;
        private final Map<String, UUID> byAssetTag = new LinkedHashMap<>();
        private final Map<String, UUID> byName = new LinkedHashMap<>();
        private Boolean customFieldsEnabled;
        private int created;

        private Runner(Organisation organisation, ImportOptions options, ImportRunReport report) {
            super(options);
            this.organisation = organisation;
            this.report = report;
            this.refs = referenceResolver.open(organisation, options, report);
            this.customFields = customFieldDefinitions.open(
                    organisation, ImportEntityType.ASSETS.name(), options, report);
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

            beanValidator.validateForCreate(dto);
            return new AssetPayload(dto, resolveCustomFields(row));
        }

        /**
         * The extra columns this row carries, under the field names the tenant's own
         * definitions use.
         *
         * <p>A tenant without {@code commercial.governed-custom-fields} does not get a
         * failed row over this. The flag still cannot be bypassed — nothing is written —
         * but the columns are dropped with a note saying why, because losing a column is
         * a smaller harm than losing the row, and the wizard already knows not to offer
         * the option to a tenant who cannot use it.</p>
         */
        private Map<String, String> resolveCustomFields(ImportRow row) {
            Map<String, String> raw = row.unmapped();
            if (raw.isEmpty()) return Map.of();
            if (!customFieldsEnabled()) {
                report.note(null, String.join(", ", raw.keySet()), null,
                        "Custom fields are not enabled for your organisation, so "
                                + raw.size() + " extra column(s) were not imported: "
                                + String.join(", ", raw.keySet()) + ".");
                return Map.of();
            }
            Map<String, String> resolved = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                String fieldName = customFields.ensure(entry.getKey(), entry.getValue());
                if (fieldName != null) resolved.put(fieldName, entry.getValue());
            }
            return resolved;
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
