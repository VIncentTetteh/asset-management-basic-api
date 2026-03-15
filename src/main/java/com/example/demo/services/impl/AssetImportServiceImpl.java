package com.example.demo.services.impl;

import com.example.demo.dto.AssetDto;
import com.example.demo.dto.AssetImportResultDto;
import com.example.demo.dto.AssetImportResultDto.RowError;
import com.example.demo.enums.*;
import com.example.demo.models.*;
import com.example.demo.repositories.*;
import com.example.demo.services.AssetImportService;
import com.example.demo.services.AssetService;
import com.example.demo.storage.FileStorageService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
 *  invoiceId | insurancePolicyId
 *
 * Human-readable names/emails are resolved to IDs server-side — users never
 * need to copy-paste UUIDs.  All lookups are pre-cached once per import run
 * to avoid N+1 queries.
 */
@Service
public class AssetImportServiceImpl extends com.example.demo.services.TenantAwareService
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

    private final AssetService assetService;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SupplierRepository supplierRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final FileStorageService storageService;

    @Value("${app.storage.s3.import-prefix:imports}")
    private String importPrefix;

    public AssetImportServiceImpl(
            AssetService assetService,
            CategoryRepository categoryRepository,
            LocationRepository locationRepository,
            SupplierRepository supplierRepository,
            DepartmentRepository departmentRepository,
            UserRepository userRepository,
            OrganisationRepository organisationRepository,
            FileStorageService storageService) {
        super(organisationRepository);
        this.assetService = assetService;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.supplierRepository = supplierRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    @Override
    public AssetImportResultDto importFromExcel(MultipartFile file) {
        AssetImportResultDto result = new AssetImportResultDto();

        if (file == null || file.isEmpty()) {
            result.getErrors().add(new RowError(0, "Uploaded file is empty"));
            return result;
        }
        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            result.getErrors().add(new RowError(0, "Only .xlsx and .xls files are supported"));
            return result;
        }

        Organisation org;
        try {
            org = requireTenantOrg();
        } catch (AccessDeniedException e) {
            result.getErrors().add(new RowError(0, e.getMessage()));
            return result;
        }

        LookupCache cache = buildCache(org);

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            result.getErrors().add(new RowError(0, "Failed to read uploaded file"));
            return result;
        }

        String cleanName = filename.replaceAll("[^A-Za-z0-9._-]", "_");
        String key = importPrefix + "/" + org.getId() + "/" + UUID.randomUUID() + "/" + cleanName;
        storageService.store(key, fileBytes, file.getContentType(), cleanName, Map.of(
                "organisationId", org.getId().toString(),
                "originalFilename", cleanName
        ));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(fileBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                result.getErrors().add(new RowError(0, "Workbook has no sheets"));
                return result;
            }

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
                    assetService.create(dto);
                    result.setImported(result.getImported() + 1);
                } catch (IllegalArgumentException e) {
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

    // ── Lookup cache ──────────────────────────────────────────────────────────

    /** Immutable per-import snapshot of all resolvable entities for this org. */
    private record LookupCache(
            Map<String, UUID> categories,    // lowercase name → id
            Map<String, UUID> locations,     // lowercase name → id
            Map<String, UUID> suppliers,     // lowercase name → id
            Map<String, UUID> departments,   // lowercase name → id
            Map<String, UUID> users          // lowercase email → id
    ) {}

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

    private Map<String, UUID> indexDepartmentsByName(Collection<Department> departments) {
        return departments.stream()
                .filter(d -> d.getName() != null)
                .collect(Collectors.toMap(
                        d -> d.getName().toLowerCase(),
                        Department::getId,
                        (a, b) -> a
                ));
    }

    private Map<String, UUID> indexUsersByEmail(Collection<User> users) {
        return users.stream()
                .filter(u -> u.getEmail() != null)
                .collect(Collectors.toMap(
                        u -> u.getEmail().toLowerCase(),
                        User::getId,
                        (a, b) -> a
                ));
    }

    /** Resolve the `name` property via reflection-free duck typing. */
    private String nameOf(BaseEntity e) {
        if (e instanceof Category c) return c.getName();
        if (e instanceof Location l) return l.getName();
        if (e instanceof Supplier s) return s.getName();
        return null;
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
            return (int) cell.getNumericCellValue();
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
