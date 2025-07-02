package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.apache.poi.ss.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExcelImporter implements FileImporter {

    private List<String> errors = new ArrayList<>();
    private DoubleProperty progress = new SimpleDoubleProperty();

    @Override
    public void openFile(File file) throws IOException, IllegalArgumentException {
        this.errors.clear();
        this.progress.set(0);
        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: " + (file != null ? file.getName() : "null"));
        }
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
            throw new IllegalArgumentException("Invalid file format. Only Excel files (.xlsx, .xls) are supported.");
        }

        // Basic validation by trying to open the workbook
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            if (workbook.getNumberOfSheets() == 0) {
                // Consider if a workbook with no sheets is an error or just an empty import.
                // For now, let's assume it's not an error to open, but getDetectedTableNames will be empty.
            }
        } catch (Exception e) {
            throw new IOException("Failed to open or parse Excel file: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, String>> previewData(File file, String tableName) throws IOException {
        openFile(file); // Validate
        List<Map<String, String>> previewRows = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {

            Sheet sheet = workbook.getSheet(tableName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + tableName + "' not found in the Excel file.");
            }

            List<String> headers = getColumnHeadersFromSheet(sheet);
            if (headers.isEmpty()) {
                // Or throw new IOException("Sheet '" + tableName + "' has no header row or is empty.");
                return previewRows; // No headers, no data to preview in a structured way
            }

            DataFormatter dataFormatter = new DataFormatter();
            int rowCount = 0;
            // Start from the row after the header (assuming header is the first row)
            for (Row row : sheet) {
                if (rowCount == 0 && !headers.isEmpty()) { // Skip header row if headers were found
                    rowCount++;
                    continue;
                }
                if (rowCount >= 6 && !headers.isEmpty() ) { // Preview first 5 data rows (rowCount 1 to 5 after header)
                    break;
                }
                if (rowCount >= 5 && headers.isEmpty() ) { // Preview first 5 rows if no headers
                    break;
                }


                Map<String, String> rowMap = new HashMap<>();
                boolean emptyRow = true;
                for (int i = 0; i < headers.size(); i++) {
                    Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    String cellValue = (cell == null) ? "" : dataFormatter.formatCellValue(cell);
                    rowMap.put(headers.get(i), cellValue);
                    if (cell != null && !cellValue.trim().isEmpty()) {
                        emptyRow = false;
                    }
                }
                if (headers.isEmpty()) { // If no headers, use "Column X"
                    for (int i = 0; i < row.getLastCellNum(); i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        String cellValue = (cell == null) ? "" : dataFormatter.formatCellValue(cell);
                        rowMap.put("Column " + (i + 1), cellValue);
                        if (cell != null && !cellValue.trim().isEmpty()) {
                            emptyRow = false;
                        }
                    }
                }


                if (!emptyRow) {
                    previewRows.add(rowMap);
                }
                rowCount++;
            }
        } catch (Exception e) {
            errors.add("Error previewing Excel data: " + e.getMessage());
            throw new IOException("Error previewing Excel data from sheet '" + tableName + "': " + e.getMessage(), e);
        }
        return previewRows;
    }

    @Override
    public List<String> getDetectedTableNames(File file) throws IOException {
        openFile(file); // Validate
        List<String> sheetNames = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }
        } catch (Exception e) {
            errors.add("Error reading sheet names from Excel file: " + e.getMessage());
            throw new IOException("Error reading sheet names from Excel file: " + e.getMessage(), e);
        }
        return sheetNames;
    }

    private List<String> getColumnHeadersFromSheet(Sheet sheet) {
        List<String> headers = new ArrayList<>();
        if (sheet == null || sheet.getRow(0) == null) { // Assuming header is in the first row
            return headers; // No header row or empty sheet
        }
        Row headerRow = sheet.getRow(0);
        DataFormatter dataFormatter = new DataFormatter();
        for (Cell cell : headerRow) {
            String headerValue = dataFormatter.formatCellValue(cell).trim();
            if (!headerValue.isEmpty()) {
                headers.add(headerValue);
            } else {
                // Optional: handle empty header cells, e.g., by skipping or using a placeholder
                // For now, we only add non-empty headers. If all are empty, this results in empty list.
            }
        }
        return headers;
    }


    @Override
    public List<String> getColumnHeaders(File file, String tableName) throws IOException, IllegalArgumentException {
        openFile(file); // Validate
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheet(tableName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + tableName + "' not found in the Excel file.");
            }
            List<String> headers = getColumnHeadersFromSheet(sheet);
            if (headers.isEmpty() && sheet.getPhysicalNumberOfRows() > 0) {
                // If no headers found in the first row, but there is data,
                // we might offer to generate default headers or let user specify.
                // For now, returning empty list if first row is not a valid header.
                // Alternatively, could inspect first data row for number of columns.
            }
            return headers;
        } catch (Exception e) {
            errors.add("Error reading headers from sheet '" + tableName + "': " + e.getMessage());
            throw new IOException("Error reading headers from sheet '" + tableName + "': " + e.getMessage(), e);
        }
    }

    @Override
    public String importData(File file, String sourceTableName, DatabaseInserterInterface inserter, final int buffer, String targetTableName, boolean createNewTable, Map<String, String> columnMapping) throws IOException, IllegalArgumentException, SQLException {
        openFile(file); // Validate
        errors.clear();
        progress.set(0);

        System.out.println("target " + columnMapping);

        long totalRowsProcessed = 0;
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = WorkbookFactory.create(fis)) {
            Sheet sheet = workbook.getSheet(sourceTableName);
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet '" + sourceTableName + "' not found.");
            }

            final double interact = 100.0 / sheet.getPhysicalNumberOfRows();

            final List<String> headers = getColumnHeadersFromSheet(sheet);

            // Iterate and count rows for now
            int counter = 0;
            final ArrayList<HashMap<String, String>> data = new ArrayList<>();
            for (Row row : sheet) {
                // Skip header, assuming it's the first row if getColumnHeaders found some
                if (row.getRowNum() == 0 && !getColumnHeaders(file, sourceTableName).isEmpty()) continue;
                //  for (int cellIndex = 0; cellIndex < headers.size(); cellIndex++) {map.put(headers.get(cellIndex), getCellValue(row.getCell(cellIndex)));
                final HashMap<String, String> map = new HashMap<>(columnMapping);
                data.add(map);
                totalRowsProcessed++;
                if (counter == buffer) {
                    if (!inserter.insertData(targetTableName, data)) throw new SQLException(inserter.getException());
                    data.clear();
                    counter = 0;
                    progress.set(progress.add(interact).get());
                } else counter++;
            }

            if (counter != 0) {
                if (!inserter.insertData(targetTableName, data)) throw new SQLException(inserter.getException());
                data.clear();
            }

        } catch (Exception e) {
            errors.add("Error during Excel import from sheet '" + sourceTableName + "': " + e.getMessage());
            throw new IOException("Error during Excel import from sheet '" + sourceTableName + "': " + e.getMessage(), e);
        }

        progress.set(1.0);
        return String.format("Successfully processed %d rows from sheet '%s' in %s into %s (actual import pending implementation).",
                totalRowsProcessed, sourceTableName, file.getName(), targetTableName);
    }

    private String getCellValue(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();  // Retorna como Date
                } else {
                    return String.valueOf(cell.getNumericCellValue());  // Retorna como double
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
                return "";
            default:
                return null;
        }
    }

    @Override
    public double getImportProgress() {
        return progress.get();
    }

    @Override
    public void setImportProprerty(final DoubleProperty proprerty) {
        this.progress = proprerty;
    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

}
