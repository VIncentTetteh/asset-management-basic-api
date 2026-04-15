package com.assetiq.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AssetImportResultDto {

    private int totalRows;
    private int imported;
    private int skipped;
    private boolean dryRun;
    private List<RowError> errors = new ArrayList<>();

    @Data
    public static class RowError {
        private int row;
        private String message;

        public RowError(int row, String message) {
            this.row = row;
            this.message = message;
        }
    }
}
