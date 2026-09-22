package com.assetiq.services.impl;

import com.assetiq.dto.AssetDto;
import com.assetiq.dto.AssetImportResultDto;
import com.assetiq.models.Department;
import com.assetiq.models.Organisation;
import com.assetiq.models.User;
import com.assetiq.multitenancy.TenantContext;
import com.assetiq.repositories.*;
import com.assetiq.services.AssetService;
import com.assetiq.services.FeatureFlagService;
import com.assetiq.services.UsageLimitService;
import com.assetiq.storage.FileStorageService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Row parsing and reference resolution of the .xlsx asset import. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AssetImportServiceImplTest {

    private static final int STANDARD_COLUMNS = 23;
    private static final int COL_USEFUL_LIFE = 11;
    private static final int COL_DEPARTMENT = 19;
    private static final int COL_USER = 20;

    @Mock AssetService assetService;
    @Mock AssetRepository assetRepository;
    @Mock AssetCustomFieldRepository customFieldRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock LocationRepository locationRepository;
    @Mock SupplierRepository supplierRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock UserRepository userRepository;
    @Mock OrganisationRepository organisationRepository;
    @Mock UsageLimitService usageLimitService;
    @Mock FileStorageService storageService;
    @Mock TransactionTemplate transactionTemplate;
    @Mock FeatureFlagService featureFlagService;

    private AssetImportServiceImpl service;
    private Organisation org;

    @BeforeEach
    void setUp() {
        service = new AssetImportServiceImpl(assetService, assetRepository, customFieldRepository,
                categoryRepository, locationRepository, supplierRepository, departmentRepository,
                userRepository, organisationRepository, usageLimitService, storageService,
                transactionTemplate, featureFlagService);
        org = new Organisation();
        org.setId(UUID.randomUUID());
        TenantContext.setOrganisationId(org.getId());
        when(organisationRepository.findByIdAndDeletedAtIsNull(org.getId())).thenReturn(Optional.of(org));
        doAnswer(inv -> {
            inv.<Consumer<Object>>getArgument(0).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        when(assetService.create(any())).thenAnswer(inv -> {
            AssetDto d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void departmentResolvesByCodeAndUserByEmployeeNumber() throws Exception {
        Department it = new Department();
        it.setId(UUID.randomUUID());
        it.setName("Information Technology");
        it.setDepartmentCode("IT");
        when(departmentRepository.findAllByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of(it));
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ama@example.com");
        user.setEmployeeId("EMP-0042");
        when(userRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(List.of(user));

        AssetImportResultDto result = service.importFromExcelBytes("a.xlsx", null,
                workbook(null, row -> {
                    row.createCell(COL_DEPARTMENT).setCellValue("it");
                    row.createCell(COL_USER).setCellValue("emp-0042");
                }), false);

        assertThat(result.getErrors()).isEmpty();
        ArgumentCaptor<AssetDto> dto = ArgumentCaptor.forClass(AssetDto.class);
        verify(assetService).create(dto.capture());
        assertThat(dto.getValue().getDepartmentId()).isEqualTo(it.getId());
        assertThat(dto.getValue().getAssignedUserId()).isEqualTo(user.getId());
    }

    @Test
    void fractionalUsefulLifeIsRejected() throws Exception {
        AssetImportResultDto result = service.importFromExcelBytes("a.xlsx", null,
                workbook(null, row -> row.createCell(COL_USEFUL_LIFE).setCellValue(12.5)), false);

        assertThat(result.getImported()).isZero();
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getMessage()).contains("whole number"));
        verify(assetService, never()).create(any());
    }

    @Test
    void extraColumnsAreRejectedWhenCustomFieldsAreDisabled() throws Exception {
        when(featureFlagService.isEnabledFor(eq(AssetImportServiceImpl.CUSTOM_FIELDS_FLAG), any())).thenReturn(false);

        AssetImportResultDto result = service.importFromExcelBytes("a.xlsx", null,
                workbook("Colour", row -> row.createCell(STANDARD_COLUMNS).setCellValue("Blue")), false);

        assertThat(result.getImported()).isZero();
        assertThat(result.getErrors()).singleElement()
                .satisfies(e -> assertThat(e.getMessage()).contains("not enabled"));
        verify(customFieldRepository, never()).save(any());
    }

    @Test
    void extraColumnsBecomeCustomFieldsWhenEnabled() throws Exception {
        when(featureFlagService.isEnabledFor(eq(AssetImportServiceImpl.CUSTOM_FIELDS_FLAG), any())).thenReturn(true);
        when(assetRepository.findByIdAndOrganisationAndDeletedAtIsNull(any(), eq(org)))
                .thenReturn(Optional.of(new com.assetiq.models.Asset()));

        AssetImportResultDto result = service.importFromExcelBytes("a.xlsx", null,
                workbook("Colour", row -> row.createCell(STANDARD_COLUMNS).setCellValue("Blue")), false);

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getImported()).isEqualTo(1);
        verify(customFieldRepository).save(any());
    }

    /** A workbook with a header row (plus an optional extra header) and one data row named "Laptop". */
    private static byte[] workbook(String extraHeader, Consumer<Row> fill) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            Row header = sheet.createRow(0);
            for (int i = 0; i < STANDARD_COLUMNS; i++) header.createCell(i).setCellValue("col" + i);
            if (extraHeader != null) header.createCell(STANDARD_COLUMNS).setCellValue(extraHeader);
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("Laptop");
            fill.accept(row);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
