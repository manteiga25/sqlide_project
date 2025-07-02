package com.example.sqlide.Import;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap; // Keep for ordered maps if needed by inserter
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CsvImporter implements FileImporter {

    private List<String> errors = new ArrayList<>();
    private DoubleProperty progress = new SimpleDoubleProperty(0.0); // Use DoubleProperty

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

        List<String> sourceCsvHeaders;
        try {
            sourceCsvHeaders = getColumnHeaders(file, null); // Get actual headers from CSV
            if (sourceCsvHeaders.isEmpty()) {
                throw new IllegalArgumentException("CSV file has no headers or failed to read them.");
            }
        } catch (IOException e) {
            errors.add("Failed to read CSV headers: " + e.getMessage());
            throw e;
        }
        
        // Determine the actual list of headers to use for insertion based on mapping
        // And the list of target column names for the DB
        List<String> finalTargetDbColumnNames = new ArrayList<>();
        Map<String, String> effectiveColumnMapping = new HashMap<>(); // sourceHeader -> targetHeader

        if (columnMapping == null || columnMapping.isEmpty()) {
            // No mapping provided, assume direct 1:1, source headers are target headers
            for (String header : sourceCsvHeaders) {
                effectiveColumnMapping.put(header, header);
                finalTargetDbColumnNames.add(header);
            }
        } else {
            // Mapping provided. Only include mapped columns.
            // Order of finalTargetDbColumnNames should ideally match sourceCsvHeaders for clarity,
            // or a specific order defined by columnMapping's iteration (LinkedHashMap if order matters).
            // For safety, let's iterate sourceCsvHeaders to maintain their order for columns that are mapped.
            for (String srcHeader : sourceCsvHeaders) {
                if (columnMapping.containsKey(srcHeader)) {
                    String targetHeader = columnMapping.get(srcHeader);
                    if (targetHeader != null && !targetHeader.trim().isEmpty() && !targetHeader.equalsIgnoreCase("(Skip Import)")) { // Handle skip
                        effectiveColumnMapping.put(srcHeader, targetHeader);
                        finalTargetDbColumnNames.add(targetHeader);
                    }
                }
            }
        }

        if (finalTargetDbColumnNames.isEmpty()) {
            throw new IllegalArgumentException("No columns selected for import after applying column mapping.");
        }


        // CREATE TABLE logic is deferred. The importer assumes the table exists or will be
        // created by the controller/caller with columns matching finalTargetDbColumnNames.
        // If createNewTable is true, the controller is responsible for DDL.
        // This CsvImporter will not execute CREATE TABLE statements directly.
        // It will, however, prepare data for the columns specified in finalTargetDbColumnNames.

        long totalRecordsForProgress = 0;
        try (Reader counterReader = new FileReader(file);
             CSVParser counterParser = new CSVParser(counterReader, getCsvFormat(true))) { // true to skip header
            for (CSVRecord ignored : counterParser) {
                totalRecordsForProgress++;
            }
        } catch (Exception e) {
            errors.add("Could not count records for progress: " + e.getMessage());
            // continue without precise progress if this fails
        }


        long recordsProcessedCount = 0;
        ArrayList<HashMap<String, String>> batchData = new ArrayList<>();

        try (Reader dataReader = new FileReader(file);
             CSVParser dataParser = new CSVParser(dataReader, getCsvFormat(true))) { // true to skip header for data reading

            for (CSVRecord record : dataParser) {
                if (!record.isConsistent()) {
                    errors.add(String.format("Line %d (approx): Record field count (%d) is inconsistent with CSV header count (%d). Skipping. Record: %s",
                            dataParser.getCurrentLineNumber(), record.size(), sourceCsvHeaders.size(), record.toMap()));
                    continue;
                }

                HashMap<String, String> rowDataForDb = new LinkedHashMap<>(); // Use LinkedHashMap to preserve order if inserter cares
                boolean validRow = true;
                for (String targetDbColName : finalTargetDbColumnNames) {
                    String sourceCsvHeader = null;
                    // Find which sourceCsvHeader maps to this targetDbColName
                    for (Map.Entry<String, String> entry : effectiveColumnMapping.entrySet()) {
                        if (entry.getValue().equals(targetDbColName)) {
                            sourceCsvHeader = entry.getKey();
                            break;
                        }
                    }

                    if (sourceCsvHeader != null && record.isMapped(sourceCsvHeader)) {
                        rowDataForDb.put(targetDbColName, record.get(sourceCsvHeader));
                    } else if (sourceCsvHeader != null) { // Mapped but value not present in record (should be rare with isConsistent)
                         rowDataForDb.put(targetDbColName, null); // Or empty string
                         errors.add(String.format("Line %d: Column '%s' (mapped to '%s') present in header but not in record. Using NULL.", dataParser.getCurrentLineNumber(), sourceCsvHeader, targetDbColName));
                    }
                    else {
                        // This case should ideally not be reached if finalTargetDbColumnNames is derived correctly from effectiveColumnMapping
                        errors.add(String.format("Logic error: Target DB column '%s' has no corresponding source CSV header in mapping. Skipping value for this column.", targetDbColName));
                        // Potentially set to null or skip row, for now, it will be missing from rowDataForDb for this key
                    }
                }
                
                if (rowDataForDb.size() != finalTargetDbColumnNames.size()){
                     errors.add(String.format("Line %d: Row data size (%d) does not match target column count (%d) after mapping. Skipping row.", dataParser.getCurrentLineNumber(), rowDataForDb.size(), finalTargetDbColumnNames.size()));
                     continue; // Skip this row
                }


                if (!rowDataForDb.isEmpty()) {
                    batchData.add(rowDataForDb);
                }

                recordsProcessedCount++;

                if (batchData.size() >= bufferSize) {
                    if (!inserter.insertData(targetTableName, batchData)) {
                        errors.add("Failed to insert batch of data into " + targetTableName + ". Error: " + inserter.getException());
                        // Decide if to stop or continue. For now, continue.
                    }
                    batchData.clear();
                }

                if (totalRecordsForProgress > 0) {
                    this.progress.set((double) recordsProcessedCount / totalRecordsForProgress);
                }
            }

            // Insert any remaining data in the last batch
            if (!batchData.isEmpty()) {
                if (!inserter.insertData(targetTableName, batchData)) {
                    errors.add("Failed to insert final batch of data into " + targetTableName + ". Error: " + inserter.getException());
                }
                batchData.clear();
            }

        } catch (Exception e) {
            errors.add("Critical error during CSV data processing: " + e.getMessage());
            throw new IOException("Error processing CSV data: " + e.getMessage(), e);
        }

        this.progress.set(1.0); // Mark as complete
        if (errors.isEmpty()) {
            return String.format("Successfully imported %d records from CSV into %s.", recordsProcessedCount, targetTableName);
        } else {
            return String.format("Imported %d records from CSV into %s with %d errors/warnings. Check status messages.", recordsProcessedCount, targetTableName, errors.size());
        }
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
