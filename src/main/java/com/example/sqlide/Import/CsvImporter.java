package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import javafx.beans.property.DoubleProperty;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;

public class CsvImporter implements FileImporter {

    private List<String> errors = new ArrayList<>();
    private double progress = 0.0;
    private CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build();

    private List<Map<String, String>> parsedMetadata = null;
    private File lastDataFileForMetadata = null;


    private File getMetadataFile(File dataFile) {
        String dataFileName = dataFile.getName();
        int dotIndex = dataFileName.lastIndexOf('.');
        String baseName = (dotIndex == -1) ? dataFileName : dataFileName.substring(0, dotIndex);
        String metadataFileName = baseName + "-metadata.csv";
        return new File(dataFile.getParentFile(), metadataFileName);
    }

    private void loadMetadata(File dataFile) {
        // Check if metadata is already loaded for this specific dataFile instance
        if (dataFile.equals(this.lastDataFileForMetadata)) {
            // If parsedMetadata is null here, it means it was attempted and failed or not found.
            // If it's not null, it means it was successfully loaded before.
            return;
        }

        this.parsedMetadata = null; // Reset if dataFile is different
        this.lastDataFileForMetadata = dataFile; // Update the last data file processed
        File metadataFile = getMetadataFile(dataFile);

        if (metadataFile.exists() && metadataFile.canRead()) {
            try (Reader reader = new FileReader(metadataFile);
                 CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).setIgnoreEmptyLines(true).build())) {

                List<String> metadataFileHeaders = parser.getHeaderNames();
                if (metadataFileHeaders == null || metadataFileHeaders.isEmpty()) {
                    errors.add("Metadata file " + metadataFile.getName() + " is empty or has no headers.");
                    return; // Keep parsedMetadata null
                }

                List<Map<String, String>> records = new ArrayList<>();
                for (CSVRecord record : parser) {
                    records.add(new LinkedHashMap<>(record.toMap()));
                }

                if (!records.isEmpty()) {
                    this.parsedMetadata = records;
                    // errors.add("Successfully loaded metadata from " + metadataFile.getName()); // Info
                } else {
                    errors.add("Metadata file " + metadataFile.getName() + " contains no data records after the header.");
                    // Keep parsedMetadata null
                }

            } catch (IOException e) {
                errors.add("Error reading metadata file " + metadataFile.getName() + ": " + e.getMessage());
                this.parsedMetadata = null; // Ensure it's null on error
            }
        } else {
            // errors.add("Metadata file " + metadataFile.getName() + " not found or not readable."); // Info
            this.parsedMetadata = null; // Ensure it's null if not found/readable
        }
    }

    @Override
    public void openFile(File file) throws IOException, IllegalArgumentException {
        // Clear instance errors for the new file operation, but not global ones potentially
        // this.errors.clear(); // Cleared in loadMetadata or at start of importData
        this.progress = 0.0;
        // Reset metadata state for the new file
        this.parsedMetadata = null;
        this.lastDataFileForMetadata = null;

        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: " + (file != null ? file.getName() : "null"));
        }
        if (!file.getName().toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Invalid file format. Only CSV files are supported.");
        }

        // Attempt to load metadata related to this data file.
        // loadMetadata will set this.parsedMetadata and this.lastDataFileForMetadata
        loadMetadata(file);

        // Validate data file itself (e.g., headers)
        try (Reader reader = new FileReader(file);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(false).build())) {
            if (parser.getHeaderMap() == null || parser.getHeaderMap().isEmpty()) {
                throw new IllegalArgumentException("CSV data file does not contain a valid header row or is empty.");
            }
        } catch (Exception e) {
            // If metadata loading added errors, they will persist.
            throw new IOException("Failed to parse CSV data file headers: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Map<String, String>> previewData(File file, String tableName) throws IOException {
        loadMetadata(file);
        List<Map<String, String>> previewRows = new ArrayList<>();
        try (Reader reader = new FileReader(file);
             CSVParser parser = new CSVParser(reader, csvFormat)) {
            int count = 0;
            for (CSVRecord record : parser) {
                if (count >= 5) break;
                previewRows.add(new HashMap<>(record.toMap()));
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
        loadMetadata(file);
        String fileName = file.getName();
        int dotIndex = fileName.lastIndexOf('.');
        String tableName = (dotIndex == -1) ? fileName : fileName.substring(0, dotIndex);
        List<String> tableNames = new ArrayList<>();
        tableNames.add(tableName);
        return tableNames;
    }

    @Override
    public List<String> getColumnHeaders(File file, String tableName) throws IOException, IllegalArgumentException {
        loadMetadata(file);
        try (Reader reader = new FileReader(file);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(false).build())) {
            return new ArrayList<>(parser.getHeaderMap().keySet());
        } catch (Exception e) {
            errors.add("Error reading CSV data headers: " + e.getMessage());
            throw new IOException("Error reading CSV data headers: " + e.getMessage(), e);
        }
    }

    @Override
    public String importData(File file, String sourceTableName, DatabaseInserterInterface inserter, final int buffer, String targetTableName, boolean createNewTable, Map<String, String> columnMapping) throws IOException, IllegalArgumentException, SQLException {
        // It's important that errors from previous operations (like openFile->loadMetadata) are not cleared here
        // if they are meant to be persistent for the whole import operation.
        // However, for a fresh import, clearing errors specific to this method call might be desired.
        // For now, let's assume errors list accumulates until explicitly cleared by a new "overall" operation.
        // This instance's error list is used.

      /*  loadMetadata(file); // Ensure metadata is loaded or re-attempted if file changed

        progress = 0.0; // Reset progress for this import attempt

        if (inserter == null) throw new IllegalArgumentException("Database connection (DataBase object) is null.");
        Connection connection = db.getConnection();
        if (connection == null) throw new SQLException("Failed to get database connection from DataBase object.");

        List<String> csvDataHeaders;
        try (Reader headerReader = new FileReader(file);
             CSVParser headerParser = new CSVParser(headerReader, CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(false).build())) {
            csvDataHeaders = new ArrayList<>(headerParser.getHeaderMap().keySet());
            if (csvDataHeaders.isEmpty()) throw new IllegalArgumentException("CSV data file has no headers.");
        }

        final Map<String, String> effectiveColumnMapping = new HashMap<>();
        List<String> dbColumnsForInsert = new ArrayList<>();

        if (columnMapping == null || columnMapping.isEmpty()) {
            csvDataHeaders.forEach(header -> {
                effectiveColumnMapping.put(header, header);
                dbColumnsForInsert.add(header);
            });
        } else {
            csvDataHeaders.forEach(header -> {
                String targetCol = columnMapping.get(header);
                if (targetCol != null && !targetCol.trim().isEmpty()) {
                    effectiveColumnMapping.put(header, targetCol);
                    dbColumnsForInsert.add(targetCol);
                }
            });
            if (dbColumnsForInsert.isEmpty()) throw new IllegalArgumentException("Column mapping resulted in no columns to insert from CSV headers.");
        }

        String actualTargetTableName = targetTableName;
        if (createNewTable && (actualTargetTableName == null || actualTargetTableName.trim().isEmpty())) {
            actualTargetTableName = sourceTableName; // Default new table name to source (CSV filename without ext)
        }
        if (actualTargetTableName == null || actualTargetTableName.trim().isEmpty()){
            throw new IllegalArgumentException("Target table name must be specified.");
        }


        try (Statement stmt = connection.createStatement()) {

            if (createNewTable) {
                StringBuilder createTableSql = new StringBuilder("CREATE TABLE ");
                createTableSql.append(actualTargetTableName).append(" (");

                List<String> colDefs = new ArrayList<>();
                List<String> pkColumnsFromMeta = new ArrayList<>();

                boolean usedMetadataForTable = false;
                if (this.parsedMetadata != null && !this.parsedMetadata.isEmpty()) {
                    errors.add("Attempting to use metadata for table creation: " + actualTargetTableName);

                    // Create a map of target DB column names (from dbColumnsForInsert) to their original CSV header names
                    Map<String, String> targetDbColToCsvHeaderMap = new HashMap<>();
                    if (columnMapping == null || columnMapping.isEmpty()) {
                        dbColumnsForInsert.forEach(col -> targetDbColToCsvHeaderMap.put(col, col));
                    } else {
                        for(String csvHdr : csvDataHeaders) {
                            String mappedTarget = columnMapping.get(csvHdr);
                            if (mappedTarget != null && dbColumnsForInsert.contains(mappedTarget)) {
                                targetDbColToCsvHeaderMap.put(mappedTarget, csvHdr);
                            }
                        }
                    }

                    for (Map<String, String> metaRecord : this.parsedMetadata) {
                        String metaColName = metaRecord.getOrDefault("Name", metaRecord.getOrDefault("name", ""));
                        String metaColType = metaRecord.getOrDefault("Type", metaRecord.getOrDefault("type", "TEXT"));
                        boolean isPk = "true".equalsIgnoreCase(metaRecord.getOrDefault("PK", metaRecord.getOrDefault("pk", "")));

                        // The column name from metadata (metaColName) should correspond to an original CSV header name.
                        // We need to find if this metaColName (as a CSV header) is part of the data to be inserted.
                        String dbTargetColNameForThisMeta = effectiveColumnMapping.get(metaColName);

                        if (dbTargetColNameForThisMeta != null && dbColumnsForInsert.contains(dbTargetColNameForThisMeta)) {
                            // Use dbTargetColNameForThisMeta for the CREATE TABLE statement, as this is the name that will be used in INSERT
                            colDefs.add(dbTargetColNameForThisMeta + " " + metaColType);
                            if (isPk) pkColumnsFromMeta.add(dbTargetColNameForThisMeta);
                            usedMetadataForTable = true;
                        } else {
                            // This metadata column is not being imported from the CSV, so don't create it unless there's a specific need (not covered here)
                            // errors.add("Info: Metadata column '" + metaColName + "' is not among the columns to be inserted from CSV. It will not be part of the new table unless mapped.");
                        }
                    }

                    if (usedMetadataForTable && !pkColumnsFromMeta.isEmpty()) {
                        colDefs.add("PRIMARY KEY (" + String.join(", ", pkColumnsFromMeta) + ")");
                    }

                    // If no column definitions were derived from metadata (e.g. names didn't match any CSV headers being imported)
                    if (!usedMetadataForTable || colDefs.stream().noneMatch(def -> !def.startsWith("PRIMARY KEY"))) {
                        if (usedMetadataForTable) { // Metadata was present but no columns matched
                            errors.add("Metadata found, but no metadata column names matched the CSV headers being imported. Falling back to TEXT columns for: " + String.join(", ", dbColumnsForInsert));
                        }
                        colDefs.clear(); // Clear any partial (like just PK) if no actual cols from meta
                        colDefs.addAll(dbColumnsForInsert.stream().map(c -> c + " TEXT").collect(Collectors.toList()));
                        usedMetadataForTable = false; // Mark that we fell back
                    }
                } else { // No metadata, or it was empty/unusable
                    // errors.add("No metadata available or used. Defaulting to TEXT columns for: " + String.join(", ", dbColumnsForInsert));
                    colDefs.addAll(dbColumnsForInsert.stream().map(c -> c + " TEXT").collect(Collectors.toList()));
                }
                createTableSql.append(String.join(", ", colDefs)).append(")");

                try {
                    stmt.executeUpdate(createTableSql.toString());
                    if(usedMetadataForTable) errors.add("Successfully created table " + actualTargetTableName + " using metadata.");
                    else errors.add("Successfully created table " + actualTargetTableName + " with default TEXT columns.");
                } catch (SQLException e) {
                    throw new SQLException("Error creating new table '" + actualTargetTableName + "'. SQL: " + createTableSql.toString() + ". Error: " + e.getMessage(), e);
                }
            }

            StringBuilder insertSql = new StringBuilder("INSERT INTO ");
            insertSql.append(actualTargetTableName).append(" (").append(String.join(", ", dbColumnsForInsert)).append(") VALUES (");
            insertSql.append(dbColumnsForInsert.stream().map(c -> "?").collect(Collectors.joining(", "))).append(")");

            long totalRecords = 0;
            try (Reader countReader = new FileReader(file); CSVParser countParser = new CSVParser(countReader, this.csvFormat)) { // this.csvFormat skips header
                for (CSVRecord ignored : countParser) totalRecords++;
            }
            if (totalRecords == 0) {
                errors.add("No data records found in CSV (after skipping header): " + file.getName());
                progress = 1.0;
                return "No data records found to import from " + sourceTableName + ".";
            }

            long recordsProcessed = 0;
            int batchSize = 100;
            try (Reader dataReader = new FileReader(file); // Fresh reader for data
                 CSVParser dataParser = new CSVParser(dataReader, this.csvFormat); // this.csvFormat skips header
                 PreparedStatement pstmt = connection.prepareStatement(insertSql.toString())) {

                connection.setAutoCommit(false);
                for (CSVRecord record : dataParser) {
                    // Verify that the record has enough fields corresponding to the original CSV headers
                    // This check is against csvDataHeaders.size() because record.get(String) uses header names.
                    if (!record.isConsistent()) {
                        errors.add(String.format("Line %d (approx): Record field count is inconsistent with CSV header count (%d). Skipping. Record: %s",
                                dataParser.getCurrentLineNumber(), csvDataHeaders.size(), record.toMap()));
                        continue;
                    }

                    int paramIndex = 1;
                    boolean skipThisRecord = false;
                    for (String dbColName : dbColumnsForInsert) { // Iterate over DB columns that will be in INSERT statement
                        String csvHeaderForThisDbCol = null;
                        // Find the original CSV header that maps to this dbColName
                        for(Map.Entry<String,String> mappingEntry : effectiveColumnMapping.entrySet()){
                            if(mappingEntry.getValue().equals(dbColName)){ // value is dbColName
                                csvHeaderForThisDbCol = mappingEntry.getKey(); // key is csvHeader
                                break;
                            }
                        }

                        if (csvHeaderForThisDbCol != null && record.isMapped(csvHeaderForThisDbCol)) {
                            pstmt.setString(paramIndex++, record.get(csvHeaderForThisDbCol));
                        } else {
                            // This means a column in dbColumnsForInsert (which should be a target name)
                            // doesn't have a corresponding CSV source header in the record, or the mapping is off.
                            // This should ideally not happen if dbColumnsForInsert is built correctly from effectiveColumnMapping.
                            errors.add(String.format("Line %d (approx): CSV header '%s' for DB column '%s' not found in record, or not mapped. Expected %d CSV headers. Skipping record.",
                                    dataParser.getCurrentLineNumber(), csvHeaderForThisDbCol, dbColName, csvDataHeaders.size()));
                            skipThisRecord = true;
                            break;
                        }
                    }
                    if(skipThisRecord) continue;

                    if (paramIndex -1 != dbColumnsForInsert.size()){
                        errors.add(String.format("Line %d (approx): Number of values to set in PreparedStatement (%d) does not match number of target columns (%d). Skipping.",
                                dataParser.getCurrentLineNumber(), paramIndex -1, dbColumnsForInsert.size()));
                        continue;
                    }

                    pstmt.addBatch();
                    recordsProcessed++;

                    if (recordsProcessed > 0 && recordsProcessed % batchSize == 0) {
                        try { pstmt.executeBatch(); connection.commit(); }
                        catch (SQLException e) { connection.rollback(); errors.add("Batch exec error (line ~" + dataParser.getCurrentLineNumber() + "): " + e.getMessage()); }
                        pstmt.clearBatch();
                    }
                    if (totalRecords > 0) progress = (double) recordsProcessed / totalRecords;
                }
                try { pstmt.executeBatch(); connection.commit(); }
                catch (SQLException e) { connection.rollback(); errors.add("Final batch exec error: " + e.getMessage()); }
            } finally {
                if(connection != null) connection.setAutoCommit(true);
            }
            progress = 1.0;
            return String.format("Successfully processed %d data records from %s. Attempted to import %d records into %s.",
                    totalRecords, sourceTableName, recordsProcessed, actualTargetTableName);

        } catch (Exception e) {
            errors.add("Critical error during CSV import: " + e.getMessage());
            e.printStackTrace(); // For server logs
            try { if (connection != null && !connection.getAutoCommit()) connection.rollback(); }
            catch (SQLException ex) { errors.add("Critical error during rollback: " + ex.getMessage()); }
            finally { if(connection != null) try {connection.setAutoCommit(true);} catch (SQLException ignored){} }
            throw new IOException("Critical error during CSV import operation: " + e.getMessage(), e);
        } */
        return "";
    }

    @Override
    public double getImportProgress() {
        return progress;
    }

    @Override
    public void setImportProprerty(DoubleProperty proprerty) {

    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors); // Return a copy
    }

}
