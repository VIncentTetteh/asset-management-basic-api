package com.assetiq.services.impl;

import com.assetiq.dto.AssetDto;
import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.dto.AssetImportResultDto.RowError;
import com.assetiq.enums.*;
import com.assetiq.models.*;
import com.assetiq.repositories.*;
import com.assetiq.services.AssetImportService;
import com.assetiq.services.AssetService;
import com.assetiq.services.FeatureFlagService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.storage.FileStorageService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import com.assetiq.security.SpreadsheetUploadPolicy;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Parses an .xlsx workbook whose first sheet has the following header row:
 *
 *  name | assetTag | serialNumber | description | assetType | manufacturer | model |
 *  purchaseDate | purchaseCost | currency | depreciationMethod | usefulLifeMonths |
 *  residualValue | warrantyExpiryDate | status | condition |
 *  category | location | supplier | department | assignedUserEmail |
 *  invoiceId | insurancePolicyId | ...extra columns
 *
 * Extra columns become asset custom fields when the tenant has
 * {@code commercial.governed-custom-fields} enabled; otherwise a row with a value in
 * one is rejected with a row error rather than silently bypassing the flag.
 *
 * {@code department} matches a department name or code; {@code assignedUserEmail}
 * matches a user's email or employee number; category, location and supplier match by
 * name (all case-insensitive). {@code usefulLifeMonths} must be a whole number.
 *
 * Human-readable names/emails are resolved to IDs server-side — users never
 * need to copy-paste UUIDs.  All lookups are pre-cached once per import run
 * to avoid N+1 queries.
 */
@Service
public class AssetImportServiceImpl extends com.assetiq.services.TenantAwareService
        implements AssetImportService {

    private static final Logger log = LoggerFactory.getLogger(AssetImportServiceImpl.class);

    // Column indices (0-based) — must match the header order above.
    private static final int COL_NAME                = 0;
    private static final int COL_ASSET_TAG           = 1;
    private static final int COL_SERIAL_NUMBER       = 2;
    private static final int COL_DESCRIPTION         = 3;
    private static final int COL_ASSET_TYPE          = 4;
    private static final int COL_MANUFACTURER        = 5;
    private static final int COL_MODEL               = 6;
    private static final int COL_PURCHASE_DATE       = 7;
    private static final int COL_PURCHASE_COST       = 8;
    private static final int COL_CURRENCY            = 9;
    private static final int COL_DEPRECIATION_METHOD = 10;
    private static final int COL_USEFUL_LIFE_MONTHS  = 11;
    private static final int COL_RESIDUAL_VALUE      = 12;
    private static final int COL_WARRANTY_EXPIRY     = 13;
    private static final int COL_STATUS              = 14;
    private static final int COL_CONDITION           = 15;
    private static final int COL_CATEGORY            = 16;  // human name
    private static final int COL_LOCATION            = 17;  // human name
    private static final int COL_SUPPLIER            = 18;  // human name
    private static final int COL_DEPARTMENT          = 19;  // human name
    private static final int COL_ASSIGNED_USER_EMAIL = 20;  // email
    private static final int COL_INVOICE_ID          = 21;
    private static final int COL_INSURANCE_POLICY_ID = 22;
    private static final int STANDARD_COLUMN_COUNT   = COL_INSURANCE_POLICY_ID + 1;
    /** Flag that governs whether extra import columns may create asset custom fields. */
    static final String CUSTOM_FIELDS_FLAG = "commercial.governed-custom-fields";

    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final AssetCustomFieldRepository assetCustomFieldRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SupplierRepository supplierRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final UsageLimitService usageLimitService;
    private final FileStorageService storageService;
    private final TransactionTemplate transactionTemplate;
    private final FeatureFlagService featureFlagService;
    private final DataFormatter dataFormatter = new DataFormatter();

    @Value("${app.storage.s3.import-prefix:imports}")
    private String importPrefix;

    public AssetImportServiceImpl(
            AssetService assetService,
            AssetRepository assetRepository,
            AssetCustomFieldRepository assetCustomFieldRepository,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            SupplierRepository supplierRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            OrganisationRepository organisationRepository,
            UsageLimitService usageLimitService,
            FileStorageService storageService,
            TransactionTemplate transactionTemplate,
            FeatureFlagService featureFlagService) {
        super(organisationRepository);
        this.assetService = assetService;
        this.assetRepository = assetRepository;
        this.assetCustomFieldRepository = assetCustomFieldRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.supplierRepository = supplierRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.usageLimitService = usageLimitService;
        this.storageService = storageService;
        this.transactionTemplate = transactionTemplate;
        this.featureFlagService = featureFlagService;
    }

    @Override
    public AssetImportResultDto importFromExcel(MultipartFile file) {
        return importFromExcel(file, false);
    }

    @Override
    public AssetImportResultDto importFromExcel(MultipartFile file, boolean dryRun) {
        if (file == null || file.isEmpty()) {
            AssetImportResultDto result = new AssetImportResultDto();
            result.setDryRun(dryRun);
            result.getErrors().add(new RowError(0, "Uploaded file is empty"));
            return result;
        }

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            AssetImportResultDto result = new AssetImportResultDto();
            result.setDryRun(dryRun);
            result.getErrors().add(new RowError(0, "Only macro-free .xlsx files are supported"));
            return result;
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            AssetImportResultDto result = new AssetImportResultDto();
            result.setDryRun(dryRun);
            result.getErrors().add(new RowError(0, "Failed to read uploaded file"));
            return result;
        }

        // For sync imports, store the import artifact for traceability (phase 2 may refine behavior).
        return importFromExcelInternal(filename, contentType, fileBytes, dryRun, true);
    }

    @Override
    public AssetImportResultDto importFromExcelBytes(String filename, String contentType, byte[] fileBytes, boolean dryRun) {
        // For async import jobs, bytes are already provided; we avoid re-storing import artifacts here.
        return importFromExcelInternal(filename, contentType, fileBytes, dryRun, false);
    }

    private AssetImportResultDto importFromExcelInternal(String filename,
                                                          String contentType,
                                                          byte[] fileBytes,
                                                          boolean dryRun,
                                                          boolean storeArtifact) {
        AssetImportResultDto result = new AssetImportResultDto();
        result.setDryRun(dryRun);

        if (fileBytes == null || fileBytes.length == 0) {
            result.getErrors().add(new RowError(0, "Uploaded file is empty"));
            return result;
        }
        try {
            SpreadsheetUploadPolicy.validate(filename, fileBytes);
        } catch (IllegalArgumentException rejected) {
            result.getErrors().add(new RowError(0, rejected.getMessage()));
            return result;
        }
        contentType = SpreadsheetUploadPolicy.XLSX_CONTENT_TYPE;

        Organisation org;
        try {
            org = requireTenantOrg();
        } catch (AccessDeniedException e) {
            result.getErrors().add(new RowError(0, e.getMessage()));
            return result;
        }

        LookupCache cache = buildCache(org);

        if (storeArtifact) {
            String cleanName = SpreadsheetUploadPolicy.sanitiseFilename(filename);
            String key = importPrefix + "/" + org.getId() + "/" + UUID.randomUUID() + "/" + cleanName;
            storageService.store(key, fileBytes, contentType, cleanName, Map.of(
                    "organisationId", org.getId().toString(),
                    "originalFilename", cleanName
            ));
        }

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                result.getErrors().add(new RowError(0, "Workbook has no sheets"));
                return result;
            }
            List<CustomFieldColumn> customFieldColumns;
            try {
                customFieldColumns = resolveCustomFieldColumns(sheet.getRow(0));
            } catch (IllegalArgumentException e) {
                result.getErrors().add(new RowError(1, e.getMessage()));
                return result;
            }

            boolean customFieldsEnabled = customFieldColumns.isEmpty()
                    || featureFlagService.isEnabledFor(CUSTOM_FIELDS_FLAG, org.getId());

            int lastRow = sheet.getLastRowNum();
            for (int rowIdx = 1; rowIdx <= lastRow; rowIdx++) {
                Row row = sheet.getRow(rowIdx);
                if (row == null) continue;

                String name = getString(row, COL_NAME);
                if (name == null || name.isBlank()) continue; // trailing blank rows

                int humanRow = rowIdx + 1;
                result.setTotalRows(result.getTotalRows() + 1);
                try {
                    AssetDto dto = buildDto(row, name, cache);
                    Map<String, String> customFields = extractCustomFieldValues(row, customFieldColumns);
                    if (!customFields.isEmpty() && !customFieldsEnabled) {
                        throw new IllegalArgumentException("Extra column(s) " + customFields.keySet()
                                + " would become custom fields, which are not enabled for your organisation."
                                + " Remove them or leave them blank.");
                    }
                    if (dryRun) {
                        validateDryRunCreate(dto, org, result.getImported());
                    } else {
                        createAssetWithCustomFields(dto, customFields, org);
                    }
                    result.setImported(result.getImported() + 1);
                } catch (IllegalArgumentException e) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add(new RowError(humanRow, e.getMessage()));
                } catch (IllegalStateException e) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add(new RowError(humanRow, e.getMessage()));
                } catch (AccessDeniedException e) {
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add(new RowError(humanRow, e.getMessage()));
                } catch (Exception e) {
                    log.warn("Excel import: unexpected error at row {}", humanRow, e);
                    result.setSkipped(result.getSkipped() + 1);
                    result.getErrors().add(new RowError(humanRow, "Unexpected error: " + e.getMessage()));
                }
            }
        } catch (IOException e) {
            result.getErrors().add(new RowError(0, "Failed to read file: " + e.getMessage()));
        }

        return result;
    }

    private void validateDryRunCreate(AssetDto dto, Organisation org, int alreadyImported) {
        SubscriptionPlan plan = resolvePlan(org);
        long currentAssets = assetRepository.countByOrganisationAndDeletedAtIsNull(org);
        long projectedCount = currentAssets + alreadyImported;
        if (projectedCount >= plan.getMaxAssets()) {
            throw new AccessDeniedException("Asset limit reached for current plan. Upgrade your subscription.");
        }

        String name = dto.getName();
        UUID departmentId = dto.getDepartmentId();
        boolean duplicate = departmentId != null
                ? assetRepository.existsByNameIgnoreCaseAndOrganisationAndDepartmentAndDeletedAtIsNull(
                name,
                org,
                departmentRepository.findByIdAndOrganisationAndDeletedAtIsNull(departmentId, org)
                        .orElseThrow(() -> new IllegalArgumentException("Department not found in your organisation")))
                : assetRepository.existsByNameIgnoreCaseAndOrganisationAndDeletedAtIsNull(name, org);
        if (duplicate) {
            throw new IllegalStateException("Asset with the same name already exists in this organisation");
        }
    }

    private SubscriptionPlan resolvePlan(Organisation org) {
        // One definition of "which plan applies", shared with the create paths, so an
        // import cannot disagree with asset creation about the dunning grace window.
        return usageLimitService.resolveEffectivePlan(org);
    }

    // ── Lookup cache ──────────────────────────────────────────────────────────

    /** Immutable per-import snapshot of all resolvable entities for this org. */
    private record LookupCache(
            Map<String, UUID> categories,    // lowercase name → id
            Map<String, UUID> locations,     // lowercase name → id
            Map<String, UUID> suppliers,     // lowercase name → id
            Map<String, UUID> departments,   // lowercase name or code → id
            Map<String, UUID> users          // lowercase email or employee number → id
    ) {}

    private record CustomFieldColumn(int columnIndex, String fieldName) {}

    private LookupCache buildCache(Organisation org) {
        return new LookupCache(
                indexByName(categoryRepository.findByOrganisationAndDeletedAtIsNull(org)),
                indexByName(locationRepository.findByOrganisationAndDeletedAtIsNull(org)),
                indexByName(supplierRepository.findByOrganisationAndDeletedAtIsNull(org)),
                indexDepartmentsByName(departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org)),
                indexUsersByEmail(userRepository.findByOrganisationAndDeletedAtIsNull(org))
        );
    }

    private Map<String, UUID> indexByName(Collection<? extends BaseEntity> entities) {
        return entities.stream()
                .filter(e -> nameOf(e) != null)
                .collect(Collectors.toMap(
                        e -> nameOf(e).toLowerCase(),
                        BaseEntity::getId,
                        (a, b) -> a  // keep first on duplicate names
                ));
    }

    /** Departments by name, then by code where the code is not also some department's name. */
    static Map<String, UUID> indexDepartmentsByName(Collection<Department> departments) {
        Map<String, UUID> index = new HashMap<>();
        departments.stream().filter(d -> d.getName() != null)
                .forEach(d -> index.putIfAbsent(d.getName().toLowerCase().trim(), d.getId()));
        departments.stream().filter(d -> d.getDepartmentCode() != null && !d.getDepartmentCode().isBlank())
                .forEach(d -> index.putIfAbsent(d.getDepartmentCode().toLowerCase().trim(), d.getId()));
        return index;
    }

    /** Users by email, then by employee number where it is not also some user's email. */
    static Map<String, UUID> indexUsersByEmail(Collection<User> users) {
        Map<String, UUID> index = new HashMap<>();
        users.stream().filter(u -> u.getEmail() != null)
                .forEach(u -> index.putIfAbsent(u.getEmail().toLowerCase().trim(), u.getId()));
        users.stream().filter(u -> u.getEmployeeId() != null && !u.getEmployeeId().isBlank())
                .forEach(u -> index.putIfAbsent(u.getEmployeeId().toLowerCase().trim(), u.getId()));
        return index;
    }

    /** Resolve the `name` property via reflection-free duck typing. */
    private String nameOf(BaseEntity e) {
        if (e instanceof Category c) return c.getName();
        if (e instanceof Location l) return l.getName();
        if (e instanceof Supplier s) return s.getName();
        return null;
    }

    private List<CustomFieldColumn> resolveCustomFieldColumns(Row headerRow) {
        if (headerRow == null || headerRow.getLastCellNum() <= STANDARD_COLUMN_COUNT) {
            return List.of();
        }

        List<CustomFieldColumn> customFieldColumns = new ArrayList<>();
        Set<String> seenFieldNames = new HashSet<>();
        for (int columnIndex = STANDARD_COLUMN_COUNT; columnIndex < headerRow.getLastCellNum(); columnIndex++) {
            String fieldName = getFormattedString(headerRow, columnIndex);
            if (fieldName == null) {
                continue;
            }
            if (fieldName.length() > 100) {
                throw new IllegalArgumentException(
                        "Custom field header '" + fieldName + "' exceeds the 100 character limit");
            }

            String normalizedName = fieldName.toLowerCase(Locale.ROOT);
            if (!seenFieldNames.add(normalizedName)) {
                throw new IllegalArgumentException(
                        "Duplicate custom field header '" + fieldName + "' found in the import file");
            }
            customFieldColumns.add(new CustomFieldColumn(columnIndex, fieldName));
        }
        return customFieldColumns;
    }

    private Map<String, String> extractCustomFieldValues(Row row, List<CustomFieldColumn> customFieldColumns) {
        if (customFieldColumns.isEmpty()) {
            return Map.of();
        }

        Map<String, String> customFields = new LinkedHashMap<>();
        for (CustomFieldColumn customFieldColumn : customFieldColumns) {
            String fieldValue = getFormattedString(row, customFieldColumn.columnIndex());
            if (fieldValue != null) {
                customFields.put(customFieldColumn.fieldName(), fieldValue);
            }
        }
        return customFields;
    }

    private void createAssetWithCustomFields(AssetDto dto, Map<String, String> customFields, Organisation org) {
        transactionTemplate.executeWithoutResult(status -> {
            AssetDto createdAsset = assetService.create(dto);
            if (customFields.isEmpty()) {
                return;
            }

            Asset asset = assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(createdAsset.getId(), org)
                    .orElseThrow(() -> new IllegalStateException("Imported asset could not be reloaded"));

            for (Map.Entry<String, String> entry : customFields.entrySet()) {
                AssetCustomField field = new AssetCustomField();
                field.setAsset(asset);
                field.setOrganisation(org);
                field.setFieldName(entry.getKey());
                field.setFieldValue(entry.getValue());
                assetCustomFieldRepository.save(field);
            }
        });
    }

    // ── DTO builder ───────────────────────────────────────────────────────────

    private AssetDto buildDto(Row row, String name, LookupCache cache) {
        AssetDto dto = new AssetDto();
        dto.setName(name);
        dto.setAssetTag(getString(row, COL_ASSET_TAG));
        dto.setSerialNumber(getString(row, COL_SERIAL_NUMBER));
        dto.setDescription(getString(row, COL_DESCRIPTION));
        dto.setManufacturer(getString(row, COL_MANUFACTURER));
        dto.setModel(getString(row, COL_MODEL));
        dto.setCurrency(getString(row, COL_CURRENCY));
        dto.setInvoiceId(getString(row, COL_INVOICE_ID));
        dto.setInsurancePolicyId(getString(row, COL_INSURANCE_POLICY_ID));

        dto.setAssetType(parseEnum(AssetType.class, getString(row, COL_ASSET_TYPE), "assetType"));
        dto.setDepreciationMethod(parseEnum(DepreciationMethod.class, getString(row, COL_DEPRECIATION_METHOD), "depreciationMethod"));
        dto.setStatus(parseEnum(AssetStatus.class, getString(row, COL_STATUS), "status"));
        dto.setCondition(parseEnum(AssetCondition.class, getString(row, COL_CONDITION), "condition"));

        dto.setPurchaseDate(parseDate(row, COL_PURCHASE_DATE, "purchaseDate"));
        dto.setWarrantyExpiryDate(parseDate(row, COL_WARRANTY_EXPIRY, "warrantyExpiryDate"));
        dto.setPurchaseCost(parseBigDecimal(row, COL_PURCHASE_COST, "purchaseCost"));
        dto.setResidualValue(parseBigDecimal(row, COL_RESIDUAL_VALUE, "residualValue"));
        dto.setUsefulLifeMonths(parseInteger(row, COL_USEFUL_LIFE_MONTHS, "usefulLifeMonths"));

        // Resolve human-readable references → IDs
        dto.setCategoryId(resolveName(getString(row, COL_CATEGORY), cache.categories(), "category"));
        dto.setLocationId(resolveName(getString(row, COL_LOCATION), cache.locations(), "location"));
        dto.setSupplierId(resolveName(getString(row, COL_SUPPLIER), cache.suppliers(), "supplier"));
        dto.setDepartmentId(resolveName(getString(row, COL_DEPARTMENT), cache.departments(), "department"));
        dto.setAssignedUserId(resolveName(getString(row, COL_ASSIGNED_USER_EMAIL), cache.users(), "assignedUserEmail"));

        return dto;
    }

    /**
     * Looks up a name in the cache map.
     * Returns {@code null} when the cell is blank (field is optional).
     * Throws {@link IllegalArgumentException} when a value is provided but not found,
     * so the row is skipped with a clear error message.
     */
    private UUID resolveName(String value, Map<String, UUID> cache, String fieldName) {
        if (value == null || value.isBlank()) return null;
        UUID id = cache.get(value.toLowerCase().trim());
        if (id == null) {
            throw new IllegalArgumentException(
                    "'" + value + "' not found for '" + fieldName + "' in your organisation");
        }
        return id;
    }

    // ── Cell helpers ─────────────────────────────────────────────────────────

    private String getString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> {
                String v = cell.getStringCellValue().trim();
                yield v.isEmpty() ? null : v;
            }
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private String getFormattedString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;

        String value = dataFormatter.formatCellValue(cell);
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDate(Row row, int col, String fieldName) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        String raw = getString(row, col);
        if (raw == null) return null;
        try {
            return LocalDate.parse(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid date for '" + fieldName + "': '" + raw + "' — expected YYYY-MM-DD");
        }
    }

    private BigDecimal parseBigDecimal(Row row, int col, String fieldName) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String raw = getString(row, col);
        if (raw == null) return null;
        try {
            return new BigDecimal(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for '" + fieldName + "': '" + raw + "'");
        }
    }

    private Integer parseInteger(Row row, int col, String fieldName) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC) {
            double value = cell.getNumericCellValue();
            if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Invalid whole number for '" + fieldName + "': '" + dataFormatter.formatCellValue(cell) + "'");
            }
            return (int) value;
        }
        String raw = getString(row, col);
        if (raw == null) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer for '" + fieldName + "': '" + raw + "'");
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String raw, String fieldName) {
        if (raw == null) return null;
        try {
            return Enum.valueOf(enumClass, raw.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Invalid value for '" + fieldName + "': '" + raw + "'. Valid values: "
                    + Arrays.stream(enumClass.getEnumConstants()).map(Enum::name)
                             .collect(Collectors.joining(", ")));
        }
    }
}
