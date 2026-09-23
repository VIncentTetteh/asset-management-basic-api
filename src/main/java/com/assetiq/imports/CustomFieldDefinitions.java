package com.assetiq.imports;

import com.assetiq.models.CustomFieldDefinition;
import com.assetiq.models.Organisation;
import com.assetiq.repositories.CustomFieldDefinitionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a spreadsheet column the product has no field for into a field the tenant has.
 *
 * <p>This is the third of the three ways this import stops sending people back to Excel.
 * Unrecognised vocabulary is translated, missing referenced records are created, and a
 * column AssetIQ has never heard of becomes a custom field rather than being silently
 * dropped — because "PO Line" and "Room Code" are exactly the columns a customer
 * migrating off a spreadsheet cares most about keeping.</p>
 *
 * <p>Every definition is created against one organisation and matched on the normalised
 * key, so re-importing the same file, or a second file spelling the header differently,
 * lands on the same field rather than multiplying it.</p>
 */
@Component
public class CustomFieldDefinitions {

    private static final Logger log = LoggerFactory.getLogger(CustomFieldDefinitions.class);

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd", Locale.ROOT));

    private static final List<String> BOOLEANS =
            List.of("true", "false", "yes", "no", "y", "n");

    private final CustomFieldDefinitionRepository repository;

    public CustomFieldDefinitions(CustomFieldDefinitionRepository repository) {
        this.repository = repository;
    }

    public Session open(Organisation organisation, String entityType, ImportOptions options,
                        ImportRunReport report) {
        return new Session(organisation, entityType, options, report);
    }

    /**
     * The definitions in play for one run. Holds the tenant's existing fields so a
     * 3000-row file does not re-query per row, and remembers what it created so the
     * bound is counted once per field rather than once per cell.
     */
    public final class Session {

        private final Organisation organisation;
        private final String entityType;
        private final ImportOptions options;
        private final ImportRunReport report;
        /** normalised key → the definition, existing or created this run. */
        private final Map<String, CustomFieldDefinition> known = new HashMap<>();
        private boolean loaded;
        private int createdThisRun;

        private Session(Organisation organisation, String entityType, ImportOptions options,
                        ImportRunReport report) {
            this.organisation = organisation;
            this.entityType = entityType;
            this.options = options;
            this.report = report;
        }

        /**
         * Makes sure a definition exists for this column, returning the field name the
         * values should be stored under.
         *
         * @param header the column header as the user wrote it
         * @param value  one value from that column, used to infer the field's shape
         * @return the stored field name, or null when the header sanitises to nothing
         */
        public String ensure(String header, String value) {
            String name = CustomFieldDefinition.sanitiseName(header);
            if (name.isEmpty()) return null;
            String key = CustomFieldDefinition.key(name);
            if (key.isEmpty()) return null;

            load();
            CustomFieldDefinition existing = known.get(key);
            if (existing != null) {
                widen(existing, value);
                return existing.getFieldName();
            }

            if (createdThisRun >= ImportOptions.MAX_CUSTOM_FIELD_COLUMNS) {
                throw new FieldValidationException(name,
                        "would be custom field number " + (createdThisRun + 1)
                                + " created by this file, past the limit of "
                                + ImportOptions.MAX_CUSTOM_FIELD_COLUMNS
                                + ". Set the columns you do not need to ignore and import again.");
            }

            CustomFieldDefinition definition = new CustomFieldDefinition();
            definition.setOrganisation(organisation);
            definition.setEntityType(entityType);
            definition.setFieldName(name);
            definition.setFieldKey(key);
            definition.setDataType(infer(value));
            definition.setSource("IMPORT");

            if (!options.dryRun()) {
                repository.save(definition);
                log.info("Import: created custom field '{}' ({}) for org={}",
                        name, definition.getDataType(), organisation.getId());
            }
            known.put(key, definition);
            createdThisRun++;
            report.customFieldCreated(name);
            return name;
        }

        private void load() {
            if (loaded) return;
            repository.findByOrganisationAndEntityTypeAndDeletedAtIsNullOrderByFieldNameAsc(
                            organisation, entityType)
                    .forEach(d -> known.putIfAbsent(d.getFieldKey(), d));
            loaded = true;
        }

        /**
         * A column of mixed shapes is text, not a broken number column. Widening rather
         * than rejecting keeps the rule that a custom field never costs anybody a row.
         */
        private void widen(CustomFieldDefinition definition, String value) {
            if ("STRING".equals(definition.getDataType())) return;
            String shape = infer(value);
            if ("STRING".equals(shape) || shape.equals(definition.getDataType())) {
                if (!shape.equals(definition.getDataType())) {
                    definition.setDataType("STRING");
                    if (!options.dryRun()) repository.save(definition);
                }
                return;
            }
            definition.setDataType("STRING");
            if (!options.dryRun()) repository.save(definition);
        }
    }

    /**
     * The shape of a value, for rendering. Deliberately conservative: anything that is
     * not obviously a number, a date or a yes/no is text, because a wrong guess here
     * would have the UI format somebody's serial number as a decimal.
     */
    static String infer(String value) {
        if (value == null || value.isBlank()) return "STRING";
        String trimmed = value.trim();
        if (BOOLEANS.contains(trimmed.toLowerCase(Locale.ROOT))) return "BOOLEAN";
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                LocalDate.parse(trimmed, format);
                return "DATE";
            } catch (Exception ignored) {
                // try the next format
            }
        }
        // A leading zero means an identifier somebody would be upset to see as a number.
        if (trimmed.length() > 1 && trimmed.startsWith("0") && !trimmed.startsWith("0.")) {
            return "STRING";
        }
        try {
            BigDecimal number = new BigDecimal(trimmed.replace(",", ""));
            return number.scale() > 0 ? "DECIMAL" : "INTEGER";
        } catch (NumberFormatException notANumber) {
            return "STRING";
        }
    }
}
