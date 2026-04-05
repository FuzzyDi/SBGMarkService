package uz.sbg.marking.server.service;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import uz.sbg.marking.contracts.ImportMarkItem;
import uz.sbg.marking.contracts.ImportRequest;
import uz.sbg.marking.contracts.MarkStatus;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class MarkingExcelImportService {
    private static final String[] MARK_CODE_ALIASES = {"markCode", "mark_code", "km", "mark", "code", "kodkm", "kodmarkirovki", "kod"};
    private static final String[] ITEM_ALIASES = {"item", "sku", "itemCode", "tovar"};
    private static final String[] GTIN_ALIASES = {"gtin", "barcode", "ean", "shtrihkod"};
    private static final String[] PRODUCT_TYPE_ALIASES = {"productType", "product_type", "type", "tiptovara"};
    private static final String[] VALID_ALIASES = {"valid", "isValid", "validFlag", "validen"};
    private static final String[] BLOCKED_ALIASES = {"blocked", "isBlocked", "blockedFlag", "zablokirovan"};
    private static final String[] STATUS_ALIASES = {"status", "markStatus", "state", "sostoyanie"};
    private static final String[] FIFO_ALIASES = {"fifoTsEpochMs", "fifo", "fifoTs", "fifo_ts", "fifotime"};

    private final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

    public ImportRequest parseImportRequest(MultipartFile file, String batchId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required.");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("Excel workbook does not contain sheets.");
            }

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = findHeaderRow(sheet);
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row is missing.");
            }

            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            Map<String, Integer> headerMap = toHeaderMap(headerRow, evaluator);

            Integer markCodeCol = findColumn(headerMap, MARK_CODE_ALIASES);
            if (markCodeCol == null) {
                throw new IllegalArgumentException("Column for markCode/KM was not found.");
            }

            Integer itemCol = findColumn(headerMap, ITEM_ALIASES);
            Integer gtinCol = findColumn(headerMap, GTIN_ALIASES);
            Integer productTypeCol = findColumn(headerMap, PRODUCT_TYPE_ALIASES);
            Integer validCol = findColumn(headerMap, VALID_ALIASES);
            Integer blockedCol = findColumn(headerMap, BLOCKED_ALIASES);
            Integer statusCol = findColumn(headerMap, STATUS_ALIASES);
            Integer fifoCol = findColumn(headerMap, FIFO_ALIASES);

            List<ImportMarkItem> items = new ArrayList<>();
            for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                ImportMarkItem item = parseRow(row, evaluator, markCodeCol, itemCol, gtinCol, productTypeCol, validCol, blockedCol, statusCol, fifoCol);
                if (item != null) {
                    items.add(item);
                }
            }

            ImportRequest request = new ImportRequest();
            request.setBatchId(isBlank(batchId) ? defaultBatchId(file.getOriginalFilename()) : batchId);
            request.setItems(items);
            return request;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to read Excel file: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Failed to parse Excel file: " + ex.getMessage(), ex);
        }
    }

    private Row findHeaderRow(Sheet sheet) {
        if (sheet == null) {
            return null;
        }
        int maxRow = Math.min(sheet.getLastRowNum(), 100);
        for (int rowIndex = 0; rowIndex <= maxRow; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            for (int cellIndex = row.getFirstCellNum(); cellIndex < row.getLastCellNum(); cellIndex++) {
                String value = cellAsString(row.getCell(cellIndex), null);
                if (!isBlank(value)) {
                    return row;
                }
            }
        }
        return null;
    }

    private Map<String, Integer> toHeaderMap(Row headerRow, FormulaEvaluator evaluator) {
        Map<String, Integer> map = new HashMap<>();
        for (int cellIndex = headerRow.getFirstCellNum(); cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            String value = cellAsString(headerRow.getCell(cellIndex), evaluator);
            String normalized = normalizeHeader(value);
            if (!isBlank(normalized)) {
                map.put(normalized, cellIndex);
            }
        }
        return map;
    }

    private Integer findColumn(Map<String, Integer> headerMap, String[] aliases) {
        if (headerMap == null || aliases == null) {
            return null;
        }
        for (String alias : aliases) {
            Integer column = headerMap.get(normalizeHeader(alias));
            if (column != null) {
                return column;
            }
        }
        return null;
    }

    private ImportMarkItem parseRow(Row row,
                                    FormulaEvaluator evaluator,
                                    Integer markCodeCol,
                                    Integer itemCol,
                                    Integer gtinCol,
                                    Integer productTypeCol,
                                    Integer validCol,
                                    Integer blockedCol,
                                    Integer statusCol,
                                    Integer fifoCol) {
        String markCode = trimToNull(cellAsString(cell(row, markCodeCol), evaluator));
        String item = trimToNull(cellAsString(cell(row, itemCol), evaluator));
        String gtin = trimToNull(cellAsString(cell(row, gtinCol), evaluator));
        String productType = trimToNull(cellAsString(cell(row, productTypeCol), evaluator));
        Boolean valid = parseBoolean(cell(row, validCol), evaluator);
        Boolean blocked = parseBoolean(cell(row, blockedCol), evaluator);
        MarkStatus status = parseStatus(cellAsString(cell(row, statusCol), evaluator));
        Long fifoTsEpochMs = parseEpochMillis(cell(row, fifoCol), evaluator);

        if (isBlank(markCode)
                && isBlank(item)
                && isBlank(gtin)
                && isBlank(productType)
                && valid == null
                && blocked == null
                && status == null
                && fifoTsEpochMs == null) {
            return null;
        }

        ImportMarkItem importItem = new ImportMarkItem();
        importItem.setMarkCode(markCode);
        importItem.setItem(item);
        importItem.setGtin(gtin);
        importItem.setProductType(productType);
        if (valid != null) {
            importItem.setValid(valid);
        }
        if (blocked != null) {
            importItem.setBlocked(blocked);
        }
        if (status != null) {
            importItem.setStatus(status);
        }
        if (fifoTsEpochMs != null) {
            importItem.setFifoTsEpochMs(fifoTsEpochMs);
        }
        return importItem;
    }

    private Long parseEpochMillis(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA && evaluator != null) {
            type = evaluator.evaluateFormulaCell(cell);
        }

        if (type == CellType.NUMERIC) {
            if (DateUtil.isCellDateFormatted(cell)) {
                return cell.getDateCellValue().toInstant().toEpochMilli();
            }
            long numeric = Math.round(cell.getNumericCellValue());
            if (numeric >= 1_000_000_000L && numeric < 10_000_000_000L) {
                return numeric * 1000L;
            }
            return numeric;
        }

        String raw = trimToNull(cellAsString(cell, evaluator));
        if (isBlank(raw)) {
            return null;
        }

        try {
            long numeric = Long.parseLong(raw);
            if (numeric >= 1_000_000_000L && numeric < 10_000_000_000L) {
                return numeric * 1000L;
            }
            return numeric;
        } catch (NumberFormatException ignored) {
            // Not numeric, try date-time parsing.
        }

        try {
            return LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            // Not ISO local date-time.
        }

        try {
            return java.time.Instant.parse(raw).toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private Boolean parseBoolean(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA && evaluator != null) {
            type = evaluator.evaluateFormulaCell(cell);
        }

        if (type == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        if (type == CellType.NUMERIC) {
            return Math.round(cell.getNumericCellValue()) != 0L;
        }

        String raw = trimToNull(cellAsString(cell, evaluator));
        if (raw == null) {
            return null;
        }

        String normalized = raw.toLowerCase(Locale.ROOT);
        if ("true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized) || "y".equals(normalized) || "da".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "n".equals(normalized) || "net".equals(normalized)) {
            return false;
        }
        return null;
    }

    private MarkStatus parseStatus(String rawStatus) {
        String value = trimToNull(rawStatus);
        if (value == null) {
            return null;
        }

        String normalized = value.toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');

        if ("DOSTUPEN".equalsIgnoreCase(normalized) || "SVOBODEN".equalsIgnoreCase(normalized)) {
            return MarkStatus.AVAILABLE;
        }
        if ("ZAREZERVIROVAN".equalsIgnoreCase(normalized)) {
            return MarkStatus.RESERVED;
        }
        if ("PRODAN".equalsIgnoreCase(normalized)) {
            return MarkStatus.SOLD;
        }
        if ("RETURNRESERVED".equalsIgnoreCase(normalized) || "VOZVRAT_REZERV".equalsIgnoreCase(normalized)) {
            return MarkStatus.RETURN_RESERVED;
        }

        try {
            return MarkStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String cellAsString(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        if (evaluator == null) {
            return dataFormatter.formatCellValue(cell);
        }
        return dataFormatter.formatCellValue(cell, evaluator);
    }

    private Cell cell(Row row, Integer columnIndex) {
        if (row == null || columnIndex == null || columnIndex < 0) {
            return null;
        }
        return row.getCell(columnIndex);
    }

    private String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (char ch : header.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isLetterOrDigit(ch)) {
                normalized.append(ch);
            }
        }
        return normalized.toString();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String defaultBatchId(String fileName) {
        String safeName = isBlank(fileName) ? "excel" : fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        return "excel-" + safeName + "-" + System.currentTimeMillis();
    }
}
