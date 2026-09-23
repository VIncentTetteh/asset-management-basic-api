package com.assetiq.imports;

import com.assetiq.dto.CategoryDto;
import com.assetiq.dto.DepartmentDto;
import com.assetiq.models.Category;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.AssetRepository;
import com.assetiq.repositories.CategoryRepository;
import com.assetiq.repositories.DepartmentRepository;
import com.assetiq.repositories.EmployeeRepository;
import com.assetiq.repositories.LocationRepository;
import com.assetiq.repositories.SupplierRepository;
import com.assetiq.repositories.UserRepository;
import com.assetiq.services.CategoryService;
import com.assetiq.services.DepartmentService;
import com.assetiq.services.LocationService;
import com.assetiq.services.SupplierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens when a sheet names a record the tenant does not have.
 *
 * <p>Creating references from a spreadsheet is a write amplifier — one column of free
 * text becomes rows in somebody's department list — so the three properties that keep it
 * safe are pinned here: it creates against the caller's own organisation, it creates each
 * distinct name once however it is spelled, and it stops at a bound rather than
 * manufacturing hundreds unnoticed.</p>
 */
class ImportReferenceResolverTest {

    private CategoryRepository categoryRepository;
    private DepartmentRepository departmentRepository;
    private UserRepository userRepository;
    private CategoryService categoryService;
    private DepartmentService departmentService;
    private ImportReferenceResolver resolver;
    private ImportRunReport report;
    private Organisation org;

    @BeforeEach
    void setUp() {
        categoryRepository = mock(CategoryRepository.class);
        departmentRepository = mock(DepartmentRepository.class);
        userRepository = mock(UserRepository.class);
        categoryService = mock(CategoryService.class);
        departmentService = mock(DepartmentService.class);

        when(categoryRepository.findByOrganisationAndDeletedAtIsNull(any())).thenReturn(java.util.Set.of());
        when(departmentRepository.findAllByOrganisationAndDeletedAtIsNull(any())).thenReturn(List.of());
        when(userRepository.findByOrganisationAndDeletedAtIsNull(any())).thenReturn(List.of());
        when(categoryService.createCategory(any(), any())).thenAnswer(call -> {
            CategoryDto created = new CategoryDto();
            created.setId(UUID.randomUUID());
            return created;
        });
        when(departmentService.create(any())).thenAnswer(call -> {
            DepartmentDto created = new DepartmentDto();
            created.setId(UUID.randomUUID());
            return created;
        });

        resolver = new ImportReferenceResolver(categoryRepository,
                mock(LocationRepository.class), mock(SupplierRepository.class), departmentRepository,
                userRepository, mock(EmployeeRepository.class), mock(AssetRepository.class),
                categoryService, mock(LocationService.class), mock(SupplierService.class),
                departmentService);

        report = new ImportRunReport();
        org = new Organisation();
        org.setId(UUID.randomUUID());
    }

    private ImportReferenceResolver.Refs refs(boolean create, boolean dryRun) {
        ImportOptions options = new ImportOptions(
                ImportOptions.DuplicateStrategy.SKIP, create, dryRun, true);
        return resolver.open(org, options, report);
    }

    @Test
    void createsAMissingCategoryAgainstTheCallersOwnOrganisation() {
        ImportReferenceResolver.Refs refs = refs(true, false);

        assertThat(refs.category("IT Equipment", "category")).isNotNull();

        ArgumentCaptor<UUID> organisationId = ArgumentCaptor.forClass(UUID.class);
        verify(categoryService).createCategory(any(), organisationId.capture());
        assertThat(organisationId.getValue()).isEqualTo(org.getId());
        assertThat(report.createdReferences().get("category")).containsExactly("IT Equipment");
    }

    @Test
    void twoSpellingsOfOneNameAreOneRecord() {
        ImportReferenceResolver.Refs refs = refs(true, false);

        UUID first = refs.category("IT Equipment", "category");
        UUID second = refs.category("  it equipment ", "category");

        assertThat(second).isEqualTo(first);
        verify(categoryService, times(1)).createCategory(any(), any());
        assertThat(report.createdReferences().get("category"))
                .as("one line on the receipt, under the spelling it was created with")
                .containsExactly("IT Equipment");
    }

    @Test
    void anExistingRecordIsReusedRatherThanDuplicated() {
        Category existing = new Category();
        existing.setId(UUID.randomUUID());
        existing.setName("IT Equipment");
        when(categoryRepository.findByOrganisationAndDeletedAtIsNull(org)).thenReturn(java.util.Set.of(existing));

        assertThat(refs(true, false).category("it equipment", "category")).isEqualTo(existing.getId());
        verify(categoryService, never()).createCategory(any(), any());
        assertThat(report.createdReferences()).isEmpty();
    }

    @Test
    void oneFileCannotQuietlyCreateHundredsOfDepartments() {
        ImportReferenceResolver.Refs refs = refs(true, false);
        for (int i = 0; i < ImportOptions.MAX_CREATED_PER_REFERENCE_TYPE; i++) {
            refs.department("Dept " + i, "department");
        }

        assertThatThrownBy(() -> refs.department("One Too Many", "department"))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("past the limit of " + ImportOptions.MAX_CREATED_PER_REFERENCE_TYPE);

        verify(departmentService, times(ImportOptions.MAX_CREATED_PER_REFERENCE_TYPE)).create(any());
    }

    @Test
    void theBoundIsCountedIdenticallyOnADryRun() {
        // If the preview counted differently from the commit it could promise an import
        // the bound would then refuse -- the exact class of bug preview exists to prevent.
        ImportReferenceResolver.Refs refs = refs(true, true);
        for (int i = 0; i < ImportOptions.MAX_CREATED_PER_REFERENCE_TYPE; i++) {
            refs.department("Dept " + i, "department");
        }

        assertThatThrownBy(() -> refs.department("One Too Many", "department"))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("past the limit");
        verify(departmentService, never()).create(any());
    }

    @Test
    void aDryRunSaysWhatItWouldCreateWithoutCreatingIt() {
        ImportReferenceResolver.Refs refs = refs(true, true);

        assertThat(refs.category("IT Equipment", "category")).isNull();
        refs.category("IT EQUIPMENT", "category");

        verify(categoryService, never()).createCategory(any(), any());
        assertThat(report.createdReferences().get("category")).containsExactly("IT Equipment");
    }

    @Test
    void creationTurnedOffRestoresTheStrictRefusal() {
        assertThatThrownBy(() -> refs(false, false).category("Nowhere", "category"))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("does not exist in your organisation");
        verify(categoryService, never()).createCategory(any(), any());
    }

    @Test
    void aUserTheTenantDoesNotHaveCostsTheFieldAndNotTheRow() {
        // An account is an identity and is never invented from a spreadsheet cell. But a
        // leaver still listed as a custodian in the old system must not hold up the
        // migration either, so the field is left blank and the note names the cell.
        report.beginRow(7);

        assertThat(refs(true, false).user("leaver@example.com", "assignedUserEmail")).isNull();

        report.commitRow();
        assertThat(report.notes()).singleElement().satisfies(note -> {
            assertThat(note.getRow()).isEqualTo(7);
            assertThat(note.getField()).isEqualTo("assignedUserEmail");
            assertThat(note.getValue()).isEqualTo("leaver@example.com");
            assertThat(note.getMessage()).contains("No user named 'leaver@example.com'");
        });
    }

    @Test
    void aBlankCellIsNotAReferenceAtAll() {
        ImportReferenceResolver.Refs refs = refs(true, false);
        assertThat(refs.category(null, "category")).isNull();
        assertThat(refs.category("   ", "category")).isNull();
        verify(categoryService, never()).createCategory(any(), any());
        assertThat(report.createdReferences()).isEmpty();
    }
}
