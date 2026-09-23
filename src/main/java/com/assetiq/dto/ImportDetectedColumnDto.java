package com.assetiq.dto;

import java.util.List;

/**
 * A column found in the uploaded file.
 *
 * @param index        0-based position in the file, and the value a mapping points at
 * @param name         the header text exactly as the user wrote it
 * @param sampleValues a few real values from that column, so a human can tell what it is
 */
public record ImportDetectedColumnDto(int index, String name, List<String> sampleValues) {}
