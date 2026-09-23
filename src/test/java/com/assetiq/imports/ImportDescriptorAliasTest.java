package com.assetiq.imports;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards on the alias lists themselves.
 *
 * <p>Widening aliases makes more real-world exports map themselves, which is the point.
 * It also makes it easy to create a header two fields both claim — and the matcher's
 * answer to that is to map <em>neither</em>, so an over-eager alias silently costs
 * coverage rather than causing a visible failure. This test makes those collisions
 * visible: it lists every normalised key claimed by more than one field of the same
 * type and pins it to a short, deliberate allow-list.</p>
 */
class ImportDescriptorAliasTest {

    /**
     * Keys two fields genuinely share, kept ambiguous on purpose so the matcher asks
     * instead of guessing. The value is the set of field names that claim the key.
     */
    private static final Map<ImportEntityType, Map<String, Set<String>>> ACCEPTED_AMBIGUITY = Map.of(
            // On an asset sheet "Vendor" may mean who made it or who sold it. Both are
            // plausible and the cost of being wrong is a silently mis-filed record.
            ImportEntityType.ASSETS, Map.of("vendor", Set.of("manufacturer", "supplier"))
    );

    @Test
    void noFieldClaimsAKeyAnotherFieldOfTheSameTypeAlsoClaims() {
        for (ImportEntityType type : ImportEntityType.values()) {
            Map<String, Set<String>> collisions = collisions(ImportTestDescriptors.fieldsFor(type));
            assertThat(collisions)
                    .as("ambiguous match keys for %s -- each of these maps to no column at all,"
                            + " so add it to ACCEPTED_AMBIGUITY only if that is what you want", type.slug())
                    .isEqualTo(ACCEPTED_AMBIGUITY.getOrDefault(type, Map.of()));
        }
    }

    @Test
    void everyFieldDeclaresAnAliasOrIsItsOwnObviousName() {
        // Not a style rule: a field with no aliases only maps when the customer's header
        // happens to equal our label, which for a migration is the uncommon case.
        for (ImportEntityType type : ImportEntityType.values()) {
            for (ImportFieldDescriptor field : ImportTestDescriptors.fieldsFor(type)) {
                assertThat(field.aliases())
                        .as("%s.%s should declare the header names other platforms use",
                                type.slug(), field.name())
                        .isNotEmpty();
            }
        }
    }

    @Test
    void everyFieldCarriesAnExampleOrIsDeliberatelyBlank() {
        // The example row is generated from these, so a null would print "null" in a
        // template the customer then copies.
        for (ImportEntityType type : ImportEntityType.values()) {
            for (ImportFieldDescriptor field : ImportTestDescriptors.fieldsFor(type)) {
                assertThat(field.example())
                        .as("%s.%s example", type.slug(), field.name())
                        .isNotNull();
                assertThat(field.notes())
                        .as("%s.%s notes", type.slug(), field.name())
                        .isNotNull();
            }
        }
    }

    @Test
    void requiredFieldsAreNamedInTheTypesTheyBelongTo() {
        // A sanity net on the descriptor sets, since "required" drives both the
        // template's red headers and the wizard's blocking list.
        assertThat(requiredNames(ImportEntityType.ASSETS)).containsExactly("name");
        assertThat(requiredNames(ImportEntityType.SUPPLIERS)).containsExactly("name");
        assertThat(requiredNames(ImportEntityType.EMPLOYEES)).containsExactly("firstName", "lastName");
        assertThat(requiredNames(ImportEntityType.LOCATIONS)).containsExactly("name");
        assertThat(requiredNames(ImportEntityType.DEPARTMENTS)).containsExactly("name");
        assertThat(requiredNames(ImportEntityType.CATEGORIES)).containsExactly("name");
        assertThat(requiredNames(ImportEntityType.SOFTWARE_LICENCES))
                .containsExactly("name", "vendor", "licenseType");
        assertThat(requiredNames(ImportEntityType.CONTRACTS))
                .containsExactly("title", "contractType", "startDate", "endDate");
    }

    private List<String> requiredNames(ImportEntityType type) {
        return ImportTestDescriptors.fieldsFor(type).stream()
                .filter(ImportFieldDescriptor::required)
                .map(ImportFieldDescriptor::name)
                .toList();
    }

    /** Normalised keys claimed by two or more fields, with the fields that claim them. */
    private Map<String, Set<String>> collisions(List<ImportFieldDescriptor> fields) {
        Map<String, Set<String>> claimants = new LinkedHashMap<>();
        for (ImportFieldDescriptor field : fields) {
            for (String key : field.matchKeys()) {
                claimants.computeIfAbsent(key, k -> new TreeSet<>()).add(field.name());
            }
        }
        Map<String, Set<String>> collisions = new LinkedHashMap<>();
        claimants.forEach((key, names) -> {
            if (names.size() > 1) collisions.put(key, new LinkedHashSet<>(names));
        });
        return collisions;
    }
}
