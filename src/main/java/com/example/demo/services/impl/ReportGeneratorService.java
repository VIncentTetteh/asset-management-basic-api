package com.example.demo.services.impl;

import com.example.demo.models.Asset;
import com.example.demo.models.MaintenanceRecord;
import com.example.demo.models.Organisation;
import com.example.demo.multitenancy.TenantContext;
import com.example.demo.repositories.AssetRepository;
import com.example.demo.repositories.MaintenanceRecordRepository;
import com.example.demo.repositories.OrganisationRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportGeneratorService {

    private final AssetRepository assetRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final OrganisationRepository organisationRepository;

    // In-memory cache: reportId → (contentType, bytes)
    private final Map<UUID, ReportEntry> cache = new ConcurrentHashMap<>();

    public ReportGeneratorService(AssetRepository assetRepository,
                                  MaintenanceRecordRepository maintenanceRecordRepository,
                                  OrganisationRepository organisationRepository) {
        this.assetRepository = assetRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.organisationRepository = organisationRepository;
    }

    public record ReportEntry(String contentType, String filename, byte[] bytes) {}

    // ── Public generate methods ───────────────────────────────────────────────

    public UUID generateAssetReport(String format) throws IOException {
        Organisation org = requireOrg();
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        byte[] bytes;
        String contentType;
        String filename;
        switch (format.toUpperCase()) {
            case "EXCEL" -> { bytes = assetExcel(assets);  contentType = EXCEL_MIME; filename = "asset-report.xlsx"; }
            case "CSV"   -> { bytes = assetCsv(assets);    contentType = CSV_MIME;   filename = "asset-report.csv";  }
            default      -> { bytes = assetPdf(assets, org.getName()); contentType = PDF_MIME; filename = "asset-report.pdf"; }
        }
        return store(contentType, filename, bytes);
    }

    public UUID generateMaintenanceReport(String format) throws IOException {
        Organisation org = requireOrg();
        Set<MaintenanceRecord> records = maintenanceRecordRepository.findByOrganisationAndDeletedAtIsNull(org);
        List<MaintenanceRecord> list = new ArrayList<>(records);
        list.sort(Comparator.comparing(r -> r.getScheduledDate() == null ? "" : r.getScheduledDate().toString()));
        byte[] bytes;
        String contentType;
        String filename;
        switch (format.toUpperCase()) {
            case "EXCEL" -> { bytes = maintenanceExcel(list);  contentType = EXCEL_MIME; filename = "maintenance-report.xlsx"; }
            case "CSV"   -> { bytes = maintenanceCsv(list);    contentType = CSV_MIME;   filename = "maintenance-report.csv";  }
            default      -> { bytes = maintenancePdf(list, org.getName()); contentType = PDF_MIME; filename = "maintenance-report.pdf"; }
        }
        return store(contentType, filename, bytes);
    }

    public UUID generateFinancialReport(String format) throws IOException {
        Organisation org = requireOrg();
        List<Asset> assets = assetRepository.findAllByOrganisationAndDeletedAtIsNull(org);
        byte[] bytes;
        String contentType;
        String filename;
        switch (format.toUpperCase()) {
            case "EXCEL" -> { bytes = financialExcel(assets);  contentType = EXCEL_MIME; filename = "financial-report.xlsx"; }
            case "CSV"   -> { bytes = financialCsv(assets);    contentType = CSV_MIME;   filename = "financial-report.csv";  }
            default      -> { bytes = financialPdf(assets, org.getName()); contentType = PDF_MIME; filename = "financial-report.pdf"; }
        }
        return store(contentType, filename, bytes);
    }

    public Optional<ReportEntry> get(UUID reportId) {
        return Optional.ofNullable(cache.get(reportId));
    }

    // ── MIME types ────────────────────────────────────────────────────────────

    private static final String PDF_MIME   = "application/pdf";
    private static final String EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String CSV_MIME   = "text/csv";

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Organisation requireOrg() {
        UUID orgId = TenantContext.getOrganisationId();
        return organisationRepository.findByIdAndDeletedAtIsNull(orgId)
                .orElseThrow(() -> new IllegalStateException("Organisation not found"));
    }

    private UUID store(String contentType, String filename, byte[] bytes) {
        UUID id = UUID.randomUUID();
        cache.put(id, new ReportEntry(contentType, filename, bytes));
        return id;
    }

    private static String safe(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String money(BigDecimal v) {
        return v == null ? "" : v.toPlainString();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ASSET REPORT
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] ASSET_HEADERS = {
            "Name", "Asset Tag", "Serial No.", "Type", "Status", "Condition",
            "Manufacturer", "Model", "Purchase Date", "Purchase Cost", "Currency",
            "Current Book Value", "Residual Value", "Warranty Expiry"
    };

    private String[] assetRow(Asset a) {
        return new String[]{
                safe(a.getName()), safe(a.getAssetTag()), safe(a.getSerialNumber()),
                safe(a.getAssetType()), safe(a.getStatus()), safe(a.getCondition()),
                safe(a.getManufacturer()), safe(a.getModel()),
                safe(a.getPurchaseDate()), money(a.getPurchaseCost()), safe(a.getCurrency()),
                money(a.getCurrentBookValue()), money(a.getResidualValue()),
                safe(a.getWarrantyExpiryDate())
        };
    }

    private byte[] assetPdf(List<Asset> assets, String orgName) throws IOException {
        return buildPdf("Asset Report", orgName, ASSET_HEADERS,
                assets.stream().map(this::assetRow).toList());
    }

    private byte[] assetExcel(List<Asset> assets) throws IOException {
        return buildExcel("Assets", ASSET_HEADERS,
                assets.stream().map(this::assetRow).toList());
    }

    private byte[] assetCsv(List<Asset> assets) {
        return buildCsv(ASSET_HEADERS, assets.stream().map(this::assetRow).toList());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MAINTENANCE REPORT
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] MAINT_HEADERS = {
            "Asset Name", "Type", "Status", "Description",
            "Scheduled Date", "Performed Date", "Next Due Date",
            "Vendor", "Cost"
    };

    private String[] maintRow(MaintenanceRecord r) {
        return new String[]{
                r.getAsset() == null ? "" : safe(r.getAsset().getName()),
                safe(r.getMaintenanceType()), safe(r.getStatus()),
                safe(r.getDescription()), safe(r.getScheduledDate()),
                safe(r.getPerformedDate()), safe(r.getNextDueDate()),
                r.getVendor() == null ? "" : safe(r.getVendor().getName()),
                money(r.getCost())
        };
    }

    private byte[] maintenancePdf(List<MaintenanceRecord> records, String orgName) throws IOException {
        return buildPdf("Maintenance Report", orgName, MAINT_HEADERS,
                records.stream().map(this::maintRow).toList());
    }

    private byte[] maintenanceExcel(List<MaintenanceRecord> records) throws IOException {
        return buildExcel("Maintenance", MAINT_HEADERS,
                records.stream().map(this::maintRow).toList());
    }

    private byte[] maintenanceCsv(List<MaintenanceRecord> records) {
        return buildCsv(MAINT_HEADERS, records.stream().map(this::maintRow).toList());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // FINANCIAL REPORT
    // ══════════════════════════════════════════════════════════════════════════

    private static final String[] FIN_HEADERS = {
            "Asset Name", "Asset Tag", "Purchase Date", "Currency",
            "Purchase Cost", "Current Book Value", "Residual Value",
            "Depreciation Method", "Useful Life (months)", "Warranty Expiry"
    };

    private String[] finRow(Asset a) {
        return new String[]{
                safe(a.getName()), safe(a.getAssetTag()), safe(a.getPurchaseDate()),
                safe(a.getCurrency()), money(a.getPurchaseCost()),
                money(a.getCurrentBookValue()), money(a.getResidualValue()),
                safe(a.getDepreciationMethod()), safe(a.getUsefulLifeMonths()),
                safe(a.getWarrantyExpiryDate())
        };
    }

    private byte[] financialPdf(List<Asset> assets, String orgName) throws IOException {
        return buildPdf("Financial Report", orgName, FIN_HEADERS,
                assets.stream().map(this::finRow).toList());
    }

    private byte[] financialExcel(List<Asset> assets) throws IOException {
        return buildExcel("Financial", FIN_HEADERS,
                assets.stream().map(this::finRow).toList());
    }

    private byte[] financialCsv(List<Asset> assets) {
        return buildCsv(FIN_HEADERS, assets.stream().map(this::finRow).toList());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SHARED BUILDERS
    // ══════════════════════════════════════════════════════════════════════════

    // ── PDF (PDFBox 3.x) ──────────────────────────────────────────────────────

    private byte[] buildPdf(String title, String orgName,
                            String[] headers, List<String[]> rows) throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDType1Font fontBold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float margin      = 30f;
            float pageWidth   = PDRectangle.A4.getWidth();
            float pageHeight  = PDRectangle.A4.getHeight();
            float tableWidth  = pageWidth - 2 * margin;
            float colWidth    = tableWidth / headers.length;
            float rowHeight   = 16f;
            float headerHeight= 20f;
            float titleY      = pageHeight - margin - 14f;
            float tableTopY   = titleY - 30f;

            // Chunk rows across pages
            int rowsPerPage = (int) ((pageHeight - 2 * margin - 60f) / rowHeight);
            int totalPages  = Math.max(1, (int) Math.ceil((double) rows.size() / rowsPerPage));

            for (int page = 0; page < totalPages; page++) {
                PDPage pdPage = new PDPage(PDRectangle.A4);
                doc.addPage(pdPage);

                try (PDPageContentStream cs = new PDPageContentStream(doc, pdPage)) {
                    // Title
                    cs.beginText();
                    cs.setFont(fontBold, 14);
                    cs.newLineAtOffset(margin, titleY);
                    cs.showText(title + "  —  " + orgName);
                    cs.endText();

                    // Page number
                    cs.beginText();
                    cs.setFont(fontRegular, 9);
                    cs.newLineAtOffset(pageWidth - margin - 60, titleY);
                    cs.showText("Page " + (page + 1) + " / " + totalPages);
                    cs.endText();

                    float y = tableTopY;

                    // Header row background
                    cs.setNonStrokingColor(0.2f, 0.4f, 0.7f);
                    cs.addRect(margin, y - headerHeight, tableWidth, headerHeight);
                    cs.fill();
                    cs.setNonStrokingColor(0f, 0f, 0f);

                    // Header text
                    cs.setNonStrokingColor(1f, 1f, 1f);
                    cs.beginText();
                    cs.setFont(fontBold, 7);
                    cs.newLineAtOffset(margin + 3, y - headerHeight + 5);
                    for (int h = 0; h < headers.length; h++) {
                        cs.showText(truncate(headers[h], 14));
                        if (h < headers.length - 1)
                            cs.newLineAtOffset(colWidth, 0);
                    }
                    cs.endText();
                    cs.setNonStrokingColor(0f, 0f, 0f);

                    y -= headerHeight;

                    // Data rows
                    int from = page * rowsPerPage;
                    int to   = Math.min(from + rowsPerPage, rows.size());
                    for (int r = from; r < to; r++) {
                        String[] row = rows.get(r);
                        // Alternating row background
                        if (r % 2 == 0) {
                            cs.setNonStrokingColor(0.95f, 0.95f, 0.95f);
                            cs.addRect(margin, y - rowHeight, tableWidth, rowHeight);
                            cs.fill();
                            cs.setNonStrokingColor(0f, 0f, 0f);
                        }
                        cs.beginText();
                        cs.setFont(fontRegular, 6.5f);
                        cs.newLineAtOffset(margin + 3, y - rowHeight + 4);
                        for (int c = 0; c < headers.length; c++) {
                            String cell = c < row.length ? row[c] : "";
                            cs.showText(truncate(cell, 16));
                            if (c < headers.length - 1)
                                cs.newLineAtOffset(colWidth, 0);
                        }
                        cs.endText();
                        y -= rowHeight;
                    }

                    // Border around table
                    float tableHeight = tableTopY - y;
                    cs.setStrokingColor(0.4f, 0.4f, 0.4f);
                    cs.setLineWidth(0.5f);
                    cs.addRect(margin, y, tableWidth, tableHeight);
                    cs.stroke();

                    // Column dividers
                    for (int c = 1; c < headers.length; c++) {
                        float x = margin + c * colWidth;
                        cs.moveTo(x, tableTopY);
                        cs.lineTo(x, y);
                        cs.stroke();
                    }
                }
            }

            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    // ── Excel (Apache POI) ────────────────────────────────────────────────────

    private byte[] buildExcel(String sheetName, String[] headers, List<String[]> rows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet(sheetName);

            // Header style
            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            // Alternating row style
            CellStyle altStyle = wb.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Header row
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(18);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] data = rows.get(r);
                for (int c = 0; c < headers.length; c++) {
                    Cell cell = row.createCell(c);
                    cell.setCellValue(c < data.length ? data[c] : "");
                    if (r % 2 == 0) cell.setCellStyle(altStyle);
                }
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // cap at 8000 units (~28 chars)
                if (sheet.getColumnWidth(i) > 8000) sheet.setColumnWidth(i, 8000);
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    // ── CSV ───────────────────────────────────────────────────────────────────

    private byte[] buildCsv(String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(csvLine(headers));
        for (String[] row : rows) sb.append(csvLine(row));
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String csvLine(String[] fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            String v = fields[i] == null ? "" : fields[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        sb.append('\n');
        return sb.toString();
    }
}
