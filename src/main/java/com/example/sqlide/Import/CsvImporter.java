package com.example.sqlide.Import;

import ai.onnxruntime.OnnxJavaType;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty; // Added for progress
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CsvImporter implements FileImporter {

    private final List<String> errors = new ArrayList<>();
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0); // Use DoubleProperty

    // Define a flexible CSV format, assuming header is present
    private CSVFormat getCsvFormat(boolean withHeader) {
        return CSVFormat.DEFAULT.builder()
                .setHeader() // Assume headers are always read with this
                .setSkipHeaderRecord(withHeader) // Skip header record only when reading data rows
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .setAllowMissingColumnNames(true) // Allow if some columns in header are empty
                .build();
    }

    @Override
    public void openFile(File file) throws IOException, IllegalArgumentException {
        this.errors.clear();
        this.progress.set(0.0);

        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: " + (file != null ? file.getName() : "null"));
        }
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Invalid file format. Only CSV files are supported.");
        }

        // Validate data file itself (e.g., headers)
        try (Reader reader = new FileReader(file);
             // Use CSVFormat that expects a header to validate its presence
             CSVParser parser = new CSVParser(reader, getCsvFormat(false).builder().setSkipHeaderRecord(false).build())) {
            if (parser.getHeaderMap() == null || parser.getHeaderMap().isEmpty()) {
                throw new IllegalArgumentException("CSV data file does not contain a valid header row or is empty.");
            }
            // Check if all header names are empty, which is also problematic
            boolean allHeadersEmpty = true;
            for(String header : parser.getHeaderNames()){
                if(header != null && !header.trim().isEmpty()){
                    allHeadersEmpty = false;
                    break;
                }
            }
            if(allHeadersEmpty){
                 throw new IllegalArgumentException("CSV data file header row contains only empty column names.");
            }

        } catch (IllegalArgumentException iae) {
            throw iae; // rethrow
        }
        catch (Exception e) {
            throw new IOException("Failed to parse CSV data file headers: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, String>> previewData(File file, String tableNameIgnored) throws IOException {
        // tableName is ignored for CSV as file is the table
        List<Map<String, String>> previewRows = new ArrayList<>();
        // CSVFormat for data reading, skips the header record
        try (Reader reader = new FileReader(file);
             CSVParser parser = new CSVParser(reader, getCsvFormat(true))) { // true to skip header for data
            int count = 0;
            for (CSVRecord record : parser) {
                if (count >= 5) break;
                // Use LinkedHashMap to maintain column order from CSV in preview
                previewRows.add(new LinkedHashMap<>(record.toMap()));
                count++;
            }
        } catch (Exception e) {
            errors.add("Error previewing CSV data: " + e.getMessage());
            throw new IOException("Error previewing CSV data: " + e.getMessage(), e);
        }
        return previewRows;
    }

    @Override
    public List<String> getDetectedTableNames(File file) throws IOException {
        // For CSV, the table name is derived from the file name
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String tableName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        List<String> tableNames = new ArrayList<>();
        tableNames.add(tableName);
        return tableNames;
    }

    @Override
    public List<String> getColumnHeaders(File file, String tableNameIgnored) throws IOException, IllegalArgumentException {
        // tableName is ignored for CSV
        // CSVFormat for header reading, does not skip header record
        try (Reader reader = new FileReader(file);
             CSVParser parser = new CSVParser(reader, getCsvFormat(false).builder().setSkipHeaderRecord(false).build())) {
            // getHeaderNames() returns the names in order.
            // Ensure no null or purely empty string headers are returned if parser allows them.
            List<String> headers = parser.getHeaderNames();
            if (headers == null) return new ArrayList<>(); // Should not happen with CSVFormat.DEFAULT.withHeader()
            
            List<String> finalHeaders = new ArrayList<>();
            for(int i=0; i < headers.size(); i++){
                String header = headers.get(i);
                if(header == null || header.trim().isEmpty()){
                    finalHeaders.add("COLUMN_" + (i+1)); // Provide default for empty header
                } else {
                    finalHeaders.add(header.trim());
                }
            }
            return finalHeaders;
        } catch (Exception e) {
            errors.add("Error reading CSV data headers: " + e.getMessage());
            throw new IOException("Error reading CSV data headers: " + e.getMessage(), e);
        }
    }

    @Override
    public String importData(File file, String sourceTableNameIgnored, DatabaseInserterInterface inserter,
                             final int bufferSize, String targetTableName, boolean createNewTable,
                             Map<String, String> columnMapping)
            throws IOException, IllegalArgumentException, SQLException {
        this.errors.clear(); // Clear errors for this import attempt
        this.progress.set(0.0);

        if (inserter == null) {
            throw new IllegalArgumentException("Database inserter is null.");
        }
        if (targetTableName == null || targetTableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Target table name must be specified.");
        }

        ArrayList<LinkedHashMap<String, String>> data = new ArrayList<>();

        try (Reader reader = new FileReader(file);
        CSVParser parser = new CSVParser(reader, getCsvFormat(true))) {
            for (CSVRecord line : parser) {
                LinkedHashMap<String, String> data_map = new LinkedHashMap<>();
                Map<String, String> line_map = line.toMap();
                for (String key : columnMapping.keySet()) {
                    data_map.put(key, line_map.get(columnMapping.get(key)));
                }
                data.add(data_map);
            }
        }

        if (!inserter.insertData(targetTableName, data)) throw new SQLException(inserter.getException());

        this.progress.set(1.0);

        return sourceTableNameIgnored;
    }

    @Override
    public double getImportProgress() {
        return progress.get();
    }

    @Override
    public void setImportProprerty(final DoubleProperty property) { // Renamed parameter to avoid conflict
        // Bind our internal progress to the provided property if it's not null
        if (property != null) {
             // Unbind previous if any, though not strictly necessary if this instance is new each time
            this.progress.unbind(); // Or unbindBidirectional if that was used
            property.bind(this.progress);
        }
        // If you want to control an external property directly without binding:
        // this.progress = property; // But this means the internal progress field might be redundant
        // For now, binding is safer as it updates the external property when internal progress changes.
    }


    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors); // Return a copy
    }
}
