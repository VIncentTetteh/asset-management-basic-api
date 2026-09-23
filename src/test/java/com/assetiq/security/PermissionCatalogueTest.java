package com.assetiq.security;

import com.assetiq.enums.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The catalogue is what the product tells customers a role can do. If it drifts
 * from the enum, or from what the code actually enforces, the permission matrix
 * starts lying — quietly, and in the direction customers least forgive.
 */
@DisplayName("The permission catalogue")
class PermissionCatalogueTest {

    @Test
    @DisplayName("describes every permission, with no blanks")
    void describesEveryPermission() {
        for (Permission permission : Permission.values()) {
            PermissionCatalogue.Entry entry = PermissionCatalogue.find(permission.name());
            assertThat(entry).as("catalogue entry for %s", permission).isNotNull();
            assertThat(entry.label()).as("label for %s", permission).isNotBlank();
            assertThat(entry.summary()).as("summary for %s", permission).isNotBlank();
            assertThat(entry.group()).as("group for %s", permission).isNotBlank();
            // A label is for a person to read, not an enum name dressed up.
            assertThat(entry.label()).doesNotContain("_");
        }
        assertThat(PermissionCatalogue.all()).hasSize(Permission.values().length);
    }

    @Test
    @DisplayName("an unknown name is not a permission, and does not throw")
    void unknownNamesAreSkipped() {
        assertThat(PermissionCatalogue.find("NOT_A_PERMISSION")).isNull();
        assertThat(PermissionCatalogue.find(null)).isNull();
        assertThat(PermissionCatalogue.describe(List.of("VIEW_ASSETS", "LEFTOVER_FROM_AN_OLD_BUILD")))
                .extracting(PermissionCatalogue.Entry::key)
                .containsExactly("VIEW_ASSETS");
    }

    @Test
    @DisplayName("the permissions it marks unenforced really are absent from every authority check")
    void unenforcedListIsAccurate() throws IOException {
        String sources = readAllMainSources();

        for (Permission permission : PermissionCatalogue.UNENFORCED) {
            assertThat(occurrencesAsAuthority(sources, permission))
                    .as("%s is listed as unenforced but appears in an authority check — "
                            + "remove it from PermissionCatalogue.UNENFORCED", permission)
                    .isZero();
        }
    }

    @Test
    @DisplayName("nothing else is silently unenforced — a permission that gates nothing must be declared")
    void enforcedPermissionsAreReallyEnforced() {
        String sources;
        try {
            sources = readAllMainSources();
        } catch (IOException unreadable) {
            throw new AssertionError("Could not read the main sources", unreadable);
        }

        List<String> silentlyDead = Stream.of(Permission.values())
                .filter(p -> !PermissionCatalogue.UNENFORCED.contains(p))
                .filter(p -> occurrencesAsAuthority(sources, p) == 0)
                .map(Permission::name)
                .toList();

        assertThat(silentlyDead)
                .as("these permissions can be granted but gate nothing. Either gate an endpoint on them, "
                        + "or add them to PermissionCatalogue.UNENFORCED so the API stops implying they work")
                .isEmpty();
    }

    /**
     * Counts appearances inside a quoted authority list — {@code 'VIEW_ASSETS'} —
     * which is how {@code @PreAuthorize} and the security configuration name a
     * permission. The declaration in the enum itself is not quoted, so it does
     * not count, and neither does a mention in a comment.
     */
    private static int occurrencesAsAuthority(String sources, Permission permission) {
        String needle = "'" + permission.name() + "'";
        int count = 0;
        int from = 0;
        while ((from = sources.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
    }

    private static String readAllMainSources() throws IOException {
        Path root = Path.of("src/main/java/com/assetiq");
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                // The role seeder lists permissions to hand out, not to check; counting
                // it would make every seeded permission look enforced.
                if (file.getFileName().toString().equals("DefaultRoleSeederService.java")) continue;
                all.append(Files.readString(file)).append('\n');
            }
        }
        return all.toString();
    }
}
