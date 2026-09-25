package com.assetiq.security;

import com.assetiq.enums.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards every {@code @PreAuthorize} authority literal against the set of authorities
 * the application can actually grant.
 *
 * <p><b>Why this exists.</b> {@code DpaController} shipped four endpoints guarded on
 * {@code DPA_VIEW}, {@code DPA_MANAGE} and {@code ORG_ADMIN}. None of those strings are
 * {@link Permission} constants, and role authorities are granted with a {@code ROLE_}
 * prefix — so no caller could ever hold them. Consent listing, DSAR listing, DSAR detail
 * and DSAR status update were unreachable by every possible user, in the GDPR/DPA feature
 * the product sells. Nothing failed: not the compiler, not the test suite, not a code
 * review. A typo'd authority is indistinguishable from a deliberately restrictive one,
 * and it fails closed and silently.
 *
 * <p><b>The rule.</b> {@link org.springframework.security.core.GrantedAuthority} strings
 * come from exactly two places in this codebase — see
 * {@code JwtAuthenticationFilter}:
 * <ul>
 *   <li>the user's role, always emitted with a {@code ROLE_} prefix, and</li>
 *   <li>live permission strings, which are {@link Permission} enum names.</li>
 * </ul>
 * So an authority literal is legitimate if it is {@code ROLE_}-prefixed (roles are
 * org-defined and can carry any name, so the suffix cannot be validated here) or if it
 * names a real {@code Permission}. Anything else is dead — it can never be held.
 *
 * <p>This scans source rather than reflecting over beans deliberately: it catches the
 * defect in annotations on endpoints that no test happens to exercise, which is precisely
 * the case that went unnoticed.
 */
@DisplayName("@PreAuthorize authorities resolve to grantable authorities")
class PreAuthorizeAuthorityResolutionTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** Matches the argument list of hasAuthority(...) / hasAnyAuthority(...). */
    private static final Pattern AUTHORITY_CALL =
            Pattern.compile("has(?:Any)?Authority\\s*\\(([^)]*)\\)");

    /** Matches each single-quoted literal inside that argument list. */
    private static final Pattern QUOTED_LITERAL = Pattern.compile("'([^']*)'");

    private static final Set<String> PERMISSION_NAMES =
            Arrays.stream(Permission.values()).map(Enum::name).collect(Collectors.toSet());

    @Test
    @DisplayName("every hasAuthority/hasAnyAuthority literal is a ROLE_ or a real Permission")
    void everyAuthorityLiteralIsGrantable() throws IOException {
        Map<String, List<String>> unresolvable = new LinkedHashMap<>();

        for (Path javaFile : javaSources()) {
            String source = Files.readString(javaFile);
            for (String authority : authorityLiteralsIn(source)) {
                if (!isGrantable(authority)) {
                    unresolvable
                            .computeIfAbsent(SOURCE_ROOT.relativize(javaFile).toString(), k -> new ArrayList<>())
                            .add(authority);
                }
            }
        }

        assertThat(unresolvable)
                .describedAs("""
                        These @PreAuthorize authorities can never be held by any caller, so the \
                        endpoints they guard are unreachable. Each must be either a Permission enum \
                        constant (add it to com.assetiq.enums.Permission and grant it in \
                        DefaultRoleSeederService) or a ROLE_-prefixed role name. Note that a bare \
                        role name such as 'ORG_ADMIN' does NOT work: JwtAuthenticationFilter always \
                        emits role authorities with the ROLE_ prefix.""")
                .isEmpty();
    }

    /**
     * Sanity check on the scanner itself. If a refactor moved or renamed the controllers,
     * a silently-empty scan would make the test above pass while checking nothing.
     */
    @Test
    @DisplayName("the scanner actually finds authority literals")
    void scannerFindsAuthorities() throws IOException {
        List<String> all = new ArrayList<>();
        for (Path javaFile : javaSources()) {
            all.addAll(authorityLiteralsIn(Files.readString(javaFile)));
        }

        assertThat(all)
                .describedAs("Expected to find @PreAuthorize authority literals under %s — "
                        + "an empty scan means the guard above is vacuous", SOURCE_ROOT)
                .isNotEmpty();
        assertThat(all).contains("VIEW_ASSETS");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean isGrantable(String authority) {
        return authority.startsWith("ROLE_") || PERMISSION_NAMES.contains(authority);
    }

    private static List<String> authorityLiteralsIn(String source) {
        List<String> literals = new ArrayList<>();
        Matcher calls = AUTHORITY_CALL.matcher(source);
        while (calls.find()) {
            Matcher quoted = QUOTED_LITERAL.matcher(calls.group(1));
            while (quoted.find()) {
                String literal = quoted.group(1).trim();
                if (!literal.isEmpty()) {
                    literals.add(literal);
                }
            }
        }
        return literals;
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(SOURCE_ROOT)) {
            return paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
        }
    }
}
