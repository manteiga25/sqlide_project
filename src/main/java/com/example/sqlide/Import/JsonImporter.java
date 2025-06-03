package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.DataBase;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class JsonImporter implements FileImporter {

    private List<String> errors = new ArrayList<>();
    private double progress = 0.0;
    private JSONObject rootObject; // To store parsed JSON for reuse across methods for the same file

    @Override
    public void openFile(File file) throws IOException, IllegalArgumentException {
        this.errors.clear();
        this.progress = 0.0;
        this.rootObject = null;

        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: " + (file != null ? file.getName() : "null"));
        }
        if (!file.getName().toLowerCase().endsWith(".json")) {
            throw new IllegalArgumentException("Invalid file format. Only JSON files are supported.");
        }

        try (FileReader reader = new FileReader(file)) {
            JSONTokener tokener = new JSONTokener(reader);
            // Attempt to parse. We expect either a JSONObject or a JSONArray at the root.
            // For now, let's assume the primary structure for multi-table export might be a JSONObject
            // where keys are table names, and values are JSONArrays of records.
            // A single table might be a direct JSONArray of records.
            // The SqlToJson exporter creates a root object with a single key (dbName) whose value is another object containing table arrays.
            // e.g. {"dbName": {"tableName1": [...], "tableName2": [...]}}

            // Let's parse it once and store it.
            Object jsonValue = new JSONTokener(new FileReader(file)).nextValue();
            if (jsonValue instanceof JSONObject) {
                this.rootObject = (JSONObject) jsonValue;
                // Check if it's the nested structure from our exporter
                if (this.rootObject.length() == 1) {
                    String potentialDbNameKey = this.rootObject.keys().next();
                    Object tablesObject = this.rootObject.get(potentialDbNameKey);
                    if (tablesObject instanceof JSONObject) {
                        // This looks like our export format. The actual tables are in this nested object.
                        // No error, getDetectedTableNames will handle this.
                    } else {
                        // A simple JSON object, keys might be table names, or it's a single object not an array
                    }
                }
            } else if (jsonValue instanceof JSONArray) {
                // This could be a single table import. We can wrap it in a JSONObject with a default table name.
                // For now, this is fine. getDetectedTableNames will handle it.
                this.rootObject = new JSONObject(); // Create a dummy root
                String syntheticTableName = file.getName().replace(".json", "");
                this.rootObject.put(syntheticTableName, jsonValue); // Store the array under a synthetic name
            } else {
                throw new IllegalArgumentException("JSON root must be an Object or Array.");
            }

        } catch (Exception e) {
            this.rootObject = null;
            throw new IOException("Failed to parse JSON file: " + e.getMessage(), e);
        }
    }

    private JSONObject getTablesObjectFromFile(File file) throws IOException {
        if (this.rootObject == null) { // If called directly without openFile or after an error
            openFile(file); // This will parse and set this.rootObject
        }
        // Our exporter creates: {"dbName": {"table1": [], "table2": []}}
        // We need the inner {"table1": [], "table2": []} object.
        if (this.rootObject.length() == 1) {
            String dbNameKey = this.rootObject.keys().next();
            Object tablesObj = this.rootObject.opt(dbNameKey);
            if (tablesObj instanceof JSONObject) {
                return (JSONObject) tablesObj;
            }
        }
        // Otherwise, assume rootObject itself contains tables: {"table1": [], "table2": []}
        // Or it might be the synthetic one: {"filename_without_extension": []}
        return this.rootObject;
    }


    @Override
    public List<Map<String, String>> previewData(File file, String tableName) throws IOException {
        openFile(file); // Ensure rootObject is populated and file is valid
        List<Map<String, String>> previewRows = new ArrayList<>();
        JSONObject tablesObject = getTablesObjectFromFile(file);

        Object tableData = tablesObject.opt(tableName);
        if (!(tableData instanceof JSONArray)) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found or is not an array in the JSON file.");
        }

        JSONArray jsonArray = (JSONArray) tableData;
        for (int i = 0; i < jsonArray.length() && i < 5; i++) { // Preview first 5 records
            Object item = jsonArray.get(i);
            if (item instanceof JSONObject) {
                JSONObject jsonObj = (JSONObject) item;
                Map<String, String> rowMap = new HashMap<>();
                for (String key : jsonObj.keySet()) {
                    rowMap.put(key, jsonObj.optString(key, "")); // Handle various types by converting to string
                }
                previewRows.add(rowMap);
            }
            // else: array contains non-object items, how to handle? For now, skip.
        }
        return previewRows;
    }

    @Override
    public List<String> getDetectedTableNames(File file) throws IOException {
        openFile(file); // Ensure rootObject is populated and valid
        JSONObject tablesObject = getTablesObjectFromFile(file);

        List<String> tableNames = new ArrayList<>();
        Iterator<String> keys = tablesObject.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (tablesObject.opt(key) instanceof JSONArray) { // Only consider keys that point to arrays (potential tables)
                tableNames.add(key);
            }
        }
        return tableNames;
    }

    @Override
    public List<String> getColumnHeaders(File file, String tableName) throws IOException, IllegalArgumentException {
        openFile(file); // Ensure rootObject is populated
        JSONObject tablesObject = getTablesObjectFromFile(file);

        Object tableData = tablesObject.opt(tableName);
        if (!(tableData instanceof JSONArray)) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found or is not an array in the JSON file.");
        }

        JSONArray jsonArray = (JSONArray) tableData;
        if (jsonArray.isEmpty()) {
            return new ArrayList<>(); // No data, no headers
        }

        // Assume headers are keys of the first object in the array
        Object firstItem = jsonArray.get(0);
        if (firstItem instanceof JSONObject) {
            JSONObject firstJsonObject = (JSONObject) firstItem;
            // Return keys in the order they appear if possible, otherwise just the set of keys
            // JSONObject internal order is not guaranteed, but keySet() is what we get.
            return new ArrayList<>(firstJsonObject.keySet());
        } else {
            // Array does not contain objects, cannot determine headers in this way
            throw new IllegalArgumentException("Cannot determine headers: Table '" + tableName + "' does not contain JSON objects.");
        }
    }

    @Override
    public String importData(File file, String sourceTableName, DataBase db, String targetTableName, boolean createNewTable, Map<String, String> columnMapping) throws IOException, IllegalArgumentException, SQLException {
        openFile(file); // Validate and parse
        errors.clear();
        progress = 0.0;
        JSONObject tablesObject = getTablesObjectFromFile(file);

        Object tableDataObj = tablesObject.opt(sourceTableName);
        if (!(tableDataObj instanceof JSONArray)) {
            throw new IllegalArgumentException("Source table '" + sourceTableName + "' not found or is not an array in JSON.");
        }
        JSONArray recordsArray = (JSONArray) tableDataObj;

        // TODO: Implement actual data insertion, similar to CSV/Excel importers
        // 1. Get headers (e.g., from first object in recordsArray).
        // 2. If createNewTable: determine types, create table.
        // 3. Prepare INSERT SQL.
        // 4. Iterate through recordsArray:
        //    - Convert each JSONObject record to a list/map of values based on columnMapping.
        //    - Add to batch.
        //    - Update progress.

        long totalRecordsProcessed = recordsArray.length();

        progress = 1.0;
        return String.format("Successfully processed %d records from JSON table '%s' in %s into %s (actual import pending implementation).",
                totalRecordsProcessed, sourceTableName, file.getName(), targetTableName);
    }

    @Override
    public double getImportProgress() {
        return progress;
    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

}
