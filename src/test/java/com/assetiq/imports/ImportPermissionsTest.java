package com.assetiq.imports;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Import permissions are per entity type.
 *
 * <p>The failure this guards against is the obvious one: a single blanket "can import"
 * permission, so trusting somebody with the supplier list quietly hands them the
 * ability to create or overwrite three thousand employee records.</p>
 */
class ImportPermissionsTest {

    private final ImportPermissions permissions = new ImportPermissions();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void aSupplierManagerMayImportSuppliersAndNothingElse() {
        authenticateWith("MANAGE_SUPPLIERS");

        assertThat(permissions.canImport("suppliers")).isTrue();

        assertThat(permissions.canImport("employees")).isFalse();
        assertThat(permissions.canImport("assets")).isFalse();
        assertThat(permissions.canImport("contracts")).isFalse();
        assertThat(permissions.canImport("licenses")).isFalse();
        assertThat(permissions.canImport("locations")).isFalse();
        assertThat(permissions.canImport("departments")).isFalse();
        assertThat(permissions.canImport("categories")).isFalse();
    }

    @Test
    void requireThrowsForATypeTheCallerMayNotImport() {
        authenticateWith("MANAGE_SUPPLIERS");

        assertThatThrownBy(() -> permissions.require(ImportEntityType.EMPLOYEES))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class)
                .hasMessageContaining("employees");
    }

    @Test
    void everyTypeHasItsOwnGrantAndNoTypeIsUngoverned() {
        for (ImportEntityType type : ImportEntityType.values()) {
            assertThat(type.writeAuthorities())
                    .as("%s must name at least one authority that grants importing it", type.slug())
                    .isNotEmpty();

            for (String authority : type.writeAuthorities()) {
                authenticateWith(authority);
                assertThat(permissions.isPermitted(type))
                        .as("%s should be importable with %s", type.slug(), authority)
                        .isTrue();
            }
        }
    }

    @Test
    void organisationAdminsMayImportEverything() {
        for (String role : List.of("ROLE_ADMIN", "ROLE_ORG_ADMIN")) {
            authenticateWith(role);
            for (ImportEntityType type : ImportEntityType.values()) {
                assertThat(permissions.isPermitted(type))
                        .as("%s should be able to import %s", role, type.slug())
                        .isTrue();
            }
        }
    }

    @Test
    void anOrdinaryUserMayImportNothing() {
        authenticateWith("ROLE_USER", "VIEW_ASSETS", "VIEW_SUPPLIERS");

        for (ImportEntityType type : ImportEntityType.values()) {
            assertThat(permissions.isPermitted(type))
                    .as("a read-only user must not be able to import %s", type.slug())
                    .isFalse();
        }
    }

    @Test
    void anUnauthenticatedCallerMayImportNothing() {
        SecurityContextHolder.clearContext();
        assertThat(permissions.canImport("assets")).isFalse();
    }

    @Test
    void anUnknownTypeDenies() {
        authenticateWith("ROLE_ORG_ADMIN");
        assertThat(permissions.canImport("spaceships")).isFalse();
        assertThat(permissions.canImport(null)).isFalse();
    }

    private void authenticateWith(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("someone", "n/a",
                        Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
