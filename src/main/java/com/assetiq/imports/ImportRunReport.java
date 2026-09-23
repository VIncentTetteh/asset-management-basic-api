package com.assetiq.imports;

import com.assetiq.dto.AssetImportResultDto.RowNote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything an import run did that is neither a success nor a failure: a value it could
 * not translate and left blank, a record it created on the caller's behalf, a custom
 * field it defined from a column header.
 *
 * <p>This exists because the old result shape had exactly two verdicts per row — wrote
 * it, or here is your error — and the change this class serves is that a row can now be
 * written <em>with a caveat</em>. A caveat that is not reported is a silent data loss, so
 * every leniency the importer applies leaves a note here and the note reaches the user.</p>
 *
 * <p>One instance per run, mutated on the run's own thread as the engine walks the rows;
 * never shared between runs or tenants. The engine calls {@link #beginRow(int)} before
 * each row so collaborators that have no idea which row they are in — the reference
 * resolver, for one — can still produce a note that names a cell.</p>
 */
public final class ImportRunReport {

    /**
     * Ceiling on notes carried back, mirroring the error ceiling. Past this the file has
     * a systematic problem and listing another two thousand lines helps nobody.
     */
    public static final int MAX_NOTES = 500;

    private final List<RowNote> notes = new ArrayList<>();
    private final Map<String, Set<String>> createdReferences = new LinkedHashMap<>();
    private final Set<String> createdCustomFields = new LinkedHashSet<>();
    private boolean notesTruncated;
    private int currentRow;
    private Map<String, String> headerByField = Map.of();

    /** Notes accumulated for the row in progress, discarded if the row ends up failing. */
    private final List<RowNote> pending = new ArrayList<>();

    /**
     * Gives the report the user's own header text per field, so a collaborator that only
     * knows a field name — the reference resolver — still produces a note naming the
     * column the way it is spelled in the spreadsheet.
     */
    public void useHeaders(Map<String, String> headers) {
        this.headerByField = headers == null ? Map.of() : Map.copyOf(headers);
    }

    /** Tells the report which spreadsheet row subsequent notes belong to. */
    public void beginRow(int rowNumber) {
        this.currentRow = rowNumber;
        this.pending.clear();
    }

    /**
     * Keeps the notes raised while the row was being built.
     *
     * <p>Called only when the row actually landed. A row that failed outright already
     * carries an error explaining itself, and pairing that with "by the way we ignored
     * your status column" is noise on a row that was not written at all.</p>
     */
    public void commitRow() {
        for (RowNote note : pending) {
            if (notes.size() >= MAX_NOTES) {
                notesTruncated = true;
                break;
            }
            notes.add(note);
        }
        pending.clear();
    }

    /** Drops the notes raised while the row was being built. */
    public void discardRow() {
        pending.clear();
    }

    /**
     * Records a leniency applied to one cell.
     *
     * @param field   internal field name, so the wizard can highlight the mapping row
     * @param column  the header as the user wrote it
     * @param value   the cell as the user wrote it
     * @param message what was done about it, in plain words
     */
    public void note(String field, String column, String value, String message) {
        String header = column != null ? column : headerByField.getOrDefault(field, field);
        pending.add(new RowNote(currentRow, message, field, header, value));
    }

    /** Records a referenced record the run created rather than rejecting the row over. */
    public void referenceCreated(String type, String name) {
        createdReferences.computeIfAbsent(type, t -> new LinkedHashSet<>()).add(name);
    }

    /** Records a custom field definition the run created from a column header. */
    public void customFieldCreated(String name) {
        createdCustomFields.add(name);
    }

    /** How many records of a type this run has created so far — the bound's counter. */
    public int createdCount(String type) {
        Set<String> created = createdReferences.get(type);
        return created == null ? 0 : created.size();
    }

    public List<RowNote> notes() {
        return List.copyOf(notes);
    }

    public boolean notesTruncated() {
        return notesTruncated;
    }

    public Map<String, List<String>> createdReferences() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        createdReferences.forEach((type, names) -> copy.put(type, List.copyOf(names)));
        return copy;
    }

    public List<String> createdCustomFields() {
        return List.copyOf(createdCustomFields);
    }

    /** "created 3 departments: Finance, IT, Legal" — the line the result screen shows. */
    public List<String> createdSummary() {
        List<String> lines = new ArrayList<>();
        createdReferences.forEach((type, names) -> lines.add(
                "created " + names.size() + " " + plural(type, names.size()) + ": " + String.join(", ", names)));
        if (!createdCustomFields.isEmpty()) {
            lines.add("created " + createdCustomFields.size() + " custom "
                    + (createdCustomFields.size() == 1 ? "field" : "fields")
                    + ": " + String.join(", ", createdCustomFields));
        }
        return lines;
    }

    private static String plural(String type, int count) {
        String lower = type.toLowerCase(Locale.ROOT);
        if (count == 1) return lower;
        return lower.endsWith("y") ? lower.substring(0, lower.length() - 1) + "ies" : lower + "s";
    }
}
