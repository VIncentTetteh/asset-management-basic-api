package com.assetiq.imports;

import com.assetiq.models.CustomFieldDefinition;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.CustomFieldDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Turning a spreadsheet column into a field the tenant has.
 *
 * <p>The risky half of this feature is not creating the definition, it is creating too
 * many of them, under names nobody chose, in the wrong tenant. So: the bound, the
 * deduplication and the sanitising are what is pinned here.</p>
 */
class CustomFieldDefinitionsTest {

    private CustomFieldDefinitionRepository repository;
    private CustomFieldDefinitions definitions;
    private ImportRunReport report;
    private Organisation org;

    @BeforeEach
    void setUp() {
        repository = mock(CustomFieldDefinitionRepository.class);
        when(repository.findByOrganisationAndEntityTypeAndDeletedAtIsNullOrderByFieldNameAsc(any(), any()))
                .thenReturn(List.of());
        definitions = new CustomFieldDefinitions(repository);
        report = new ImportRunReport();
        org = new Organisation();
        org.setId(UUID.randomUUID());
    }

    private CustomFieldDefinitions.Session session(boolean dryRun) {
        return definitions.open(org, "ASSETS",
                dryRun ? ImportOptions.defaults().withDryRun(true) : ImportOptions.defaults(), report);
    }

    @Test
    void createsADefinitionAgainstTheCallersOwnOrganisation() {
        CustomFieldDefinitions.Session session = session(false);

        assertThat(session.ensure("Cost Centre Ref", "CC-1")).isEqualTo("Cost Centre Ref");

        ArgumentCaptor<CustomFieldDefinition> saved = ArgumentCaptor.forClass(CustomFieldDefinition.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOrganisation()).isSameAs(org);
        assertThat(saved.getValue().getEntityType()).isEqualTo("ASSETS");
        assertThat(saved.getValue().getFieldKey()).isEqualTo("costcentreref");
        assertThat(saved.getValue().getSource()).isEqualTo("IMPORT");
        assertThat(report.createdCustomFields()).containsExactly("Cost Centre Ref");
    }

    @Test
    void aSecondFileSpellingTheHeaderDifferentlyLandsOnTheSameField() {
        CustomFieldDefinition existing = new CustomFieldDefinition();
        existing.setOrganisation(org);
        existing.setEntityType("ASSETS");
        existing.setFieldName("Cost Centre Ref");
        existing.setFieldKey("costcentreref");
        when(repository.findByOrganisationAndEntityTypeAndDeletedAtIsNullOrderByFieldNameAsc(org, "ASSETS"))
                .thenReturn(List.of(existing));

        CustomFieldDefinitions.Session session = session(false);

        assertThat(session.ensure("cost_centre_ref", "CC-2")).isEqualTo("Cost Centre Ref");
        verify(repository, never()).save(any());
        assertThat(report.createdCustomFields()).isEmpty();
    }

    @Test
    void theSameColumnOnThreeThousandRowsIsOneDefinition() {
        CustomFieldDefinitions.Session session = session(false);
        for (int i = 0; i < 3000; i++) {
            session.ensure("Floor", String.valueOf(i));
        }
        assertThat(report.createdCustomFields()).containsExactly("Floor");
    }

    @Test
    void oneFileCannotQuietlyDefineUnboundedFields() {
        CustomFieldDefinitions.Session session = session(false);
        List<String> accepted = new ArrayList<>();
        assertThatThrownBy(() -> {
            for (int i = 0; i < ImportOptions.MAX_CUSTOM_FIELD_COLUMNS + 5; i++) {
                accepted.add(session.ensure("Column " + i, "x"));
            }
        }).isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("past the limit");

        assertThat(accepted).hasSize(ImportOptions.MAX_CUSTOM_FIELD_COLUMNS);
    }

    @Test
    void aDryRunDecidesWhatItWouldCreateWithoutCreatingIt() {
        CustomFieldDefinitions.Session session = session(true);

        assertThat(session.ensure("Floor", "3")).isEqualTo("Floor");

        verify(repository, never()).save(any());
        assertThat(report.createdCustomFields())
                .as("the preview must still be able to say what a commit would add")
                .containsExactly("Floor");
    }

    @Test
    void aHeaderIsSanitisedAndCappedRatherThanRefused() {
        // Truncating keeps the data. Refusing sends the customer back to Excel, which is
        // the outcome this whole path exists to remove.
        String shouty = "  =Cost\tCentre   Ref  ";
        assertThat(CustomFieldDefinition.sanitiseName(shouty)).isEqualTo("Cost Centre Ref");
        assertThat(CustomFieldDefinition.sanitiseName("x".repeat(250)))
                .hasSize(CustomFieldDefinition.MAX_NAME_LENGTH);
        assertThat(CustomFieldDefinition.sanitiseName("   ")).isEmpty();
    }

    @Test
    void infersTheShapeConservatively() {
        assertThat(CustomFieldDefinitions.infer("12")).isEqualTo("INTEGER");
        assertThat(CustomFieldDefinitions.infer("12.50")).isEqualTo("DECIMAL");
        assertThat(CustomFieldDefinitions.infer("2024-03-12")).isEqualTo("DATE");
        assertThat(CustomFieldDefinitions.infer("yes")).isEqualTo("BOOLEAN");
        assertThat(CustomFieldDefinitions.infer("CC-1")).isEqualTo("STRING");
        // A leading zero is somebody's identifier, and rendering it as a number loses it.
        assertThat(CustomFieldDefinitions.infer("00123")).isEqualTo("STRING");
        assertThat(CustomFieldDefinitions.infer(null)).isEqualTo("STRING");
    }

    @Test
    void aColumnOfMixedShapesWidensToTextRatherThanFailing() {
        CustomFieldDefinitions.Session session = session(false);
        session.ensure("Ref", "12");
        session.ensure("Ref", "CC-1");

        ArgumentCaptor<CustomFieldDefinition> saved = ArgumentCaptor.forClass(CustomFieldDefinition.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues().get(saved.getAllValues().size() - 1).getDataType())
                .isEqualTo("STRING");
    }
}
