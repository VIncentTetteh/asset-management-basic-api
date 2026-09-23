package com.assetiq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A saved column mapping.
 *
 * <p>The mapping is stored and returned as field name to <em>column header text</em>,
 * not column index: the point of a preset is that next month's export from the same
 * system maps itself, and that export may well have gained a column.</p>
 */
@Data
public class ImportMappingPresetDto {

    private UUID id;

    @NotBlank(message = "Preset name is required")
    @Size(max = 200)
    private String name;

    private Map<String, String> mapping;

    private Instant createdAt;
    private Instant updatedAt;
}
