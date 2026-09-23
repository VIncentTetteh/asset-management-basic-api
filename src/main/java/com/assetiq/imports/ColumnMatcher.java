package com.assetiq.imports;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Suggests which uploaded column feeds which field.
 *
 * <p>Three passes, most confident first, and nothing beyond them:</p>
 * <ol>
 *   <li>the header is exactly the field's label or internal name;</li>
 *   <li>the header matches after case, whitespace and punctuation are collapsed
 *       ({@code "Asset Tag"}, {@code "asset_tag"}, {@code "ASSET-TAG"});</li>
 *   <li>the header matches one of the field's declared aliases, normalised the
 *       same way — this is what makes a competitor's export auto-map.</li>
 * </ol>
 *
 * <p>There is deliberately no fuzzy or substring pass. A wrong silent guess puts the
 * warranty date in the purchase date column across 3000 rows and nobody notices until
 * the depreciation run; an unmapped column is one dropdown in the wizard. A header
 * that would match two fields is left unmapped for the same reason.</p>
 */
@Component
public class ColumnMatcher {

    /**
     * @param fields  the descriptors for the target entity type
     * @param headers the uploaded file's header row, in file order
     * @return field name → column index, containing only confident matches
     */
    public Map<String, Integer> suggest(List<ImportFieldDescriptor> fields, List<String> headers) {
        Map<String, Integer> mapping = new LinkedHashMap<>();
        Set<Integer> takenColumns = new HashSet<>();

        // Index the headers by their normalised form. A form claimed by two columns is
        // ambiguous, so neither column is offered for it.
        Map<String, Integer> byNormalised = new HashMap<>();
        Set<String> ambiguousHeaders = new HashSet<>();
        for (int i = 0; i < headers.size(); i++) {
            String key = ImportFieldDescriptor.normalise(headers.get(i));
            if (key.isEmpty()) continue;
            if (byNormalised.putIfAbsent(key, i) != null) ambiguousHeaders.add(key);
        }
        ambiguousHeaders.forEach(byNormalised::remove);

        // Pass 1: exact header text equals the label or the internal name.
        for (ImportFieldDescriptor field : fields) {
            for (int i = 0; i < headers.size(); i++) {
                String header = headers.get(i);
                if (header == null || takenColumns.contains(i)) continue;
                if (header.equals(field.label()) || header.equals(field.name())) {
                    mapping.put(field.name(), i);
                    takenColumns.add(i);
                    break;
                }
            }
        }

        // Pass 2: normalised label / internal name.
        matchByKeys(fields, mapping, takenColumns, byNormalised, false);

        // Pass 3: normalised aliases.
        matchByKeys(fields, mapping, takenColumns, byNormalised, true);

        return mapping;
    }

    private void matchByKeys(List<ImportFieldDescriptor> fields,
                             Map<String, Integer> mapping,
                             Set<Integer> takenColumns,
                             Map<String, Integer> byNormalised,
                             boolean aliasPass) {
        // A header that several fields would claim in the same pass is left alone,
        // rather than handed to whichever descriptor happens to be declared first.
        // A set per key, not a list: a field whose internal name and label normalise to
        // the same string would otherwise look like two rival claimants and be dropped.
        Map<String, java.util.LinkedHashSet<String>> claims = new LinkedHashMap<>();
        for (ImportFieldDescriptor field : fields) {
            if (mapping.containsKey(field.name())) continue;
            for (String key : aliasPass ? normalisedAliases(field) : ownKeys(field)) {
                Integer column = byNormalised.get(key);
                if (column == null || takenColumns.contains(column)) continue;
                claims.computeIfAbsent(key, k -> new java.util.LinkedHashSet<>()).add(field.name());
            }
        }
        for (Map.Entry<String, java.util.LinkedHashSet<String>> claim : claims.entrySet()) {
            if (claim.getValue().size() != 1) continue;
            String fieldName = claim.getValue().iterator().next();
            if (mapping.containsKey(fieldName)) continue;
            Integer column = byNormalised.get(claim.getKey());
            if (column == null || takenColumns.contains(column)) continue;
            mapping.put(fieldName, column);
            takenColumns.add(column);
        }
    }

    private List<String> ownKeys(ImportFieldDescriptor field) {
        return List.of(ImportFieldDescriptor.normalise(field.name()),
                ImportFieldDescriptor.normalise(field.label()));
    }

    private List<String> normalisedAliases(ImportFieldDescriptor field) {
        return field.aliases().stream()
                .map(ImportFieldDescriptor::normalise)
                .filter(k -> !k.isEmpty())
                .toList();
    }

    /** Column indices no field claims, as the user's own header text. */
    public List<String> unmappedColumns(List<String> headers, Map<String, Integer> mapping) {
        Set<Integer> mapped = new HashSet<>(mapping.values());
        List<String> unmapped = new java.util.ArrayList<>();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i);
            if (mapped.contains(i) || header == null || header.isBlank()) continue;
            unmapped.add(header);
        }
        return unmapped;
    }

    /** Required fields the mapping does not cover — the wizard's blocking list. */
    public List<String> missingRequiredFields(List<ImportFieldDescriptor> fields, Map<String, Integer> mapping) {
        return fields.stream()
                .filter(ImportFieldDescriptor::required)
                .map(ImportFieldDescriptor::name)
                .filter(name -> mapping.get(name) == null)
                .toList();
    }

    /** Lower-cased slug, used where a stable key is needed for a preset name. */
    static String slug(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
