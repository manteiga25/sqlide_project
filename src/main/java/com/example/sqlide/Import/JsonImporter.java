package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

public class JsonImporter implements FileImporter {

    private final List<String> errors = new ArrayList<>();
    private final DoubleProperty progress = new SimpleDoubleProperty(0.0);
    private JSONObject rootObject; // Store parsed JSON for the current file
    private File lastOpenedFile;   // To check if rootObject is for the current file

    private void ensureFileOpened(File file) throws IOException {
        if (this.rootObject == null || this.lastOpenedFile == null || !this.lastOpenedFile.equals(file)) {
            openFile(file);
        }
    }

    @Override
    public void openFile(File file) throws IOException, IllegalArgumentException {
        this.errors.clear();
        this.progress.set(0.0);
        this.rootObject = null; // Reset
        this.lastOpenedFile = null;

        if (file == null || !file.exists() || !file.canRead()) {
            throw new IOException("File is null, does not exist, or cannot be read: " + (file != null ? file.getName() : "null"));
        }
        final boolean jsonValid = file.getName().toLowerCase().endsWith(".json");
        if (!jsonValid) {
            throw new IllegalArgumentException("Invalid file format. Only JSON files are supported.");
        }

        try (FileReader reader = new FileReader(file)) {
            Object jsonValue = new JSONTokener(reader).nextValue();
            if (jsonValue instanceof JSONObject) {
                this.rootObject = (JSONObject) jsonValue;
            } else if (jsonValue instanceof JSONArray) {
                // If root is an array, wrap it in a JSONObject with a synthetic table name based on filename
                this.rootObject = new JSONObject();
                String syntheticTableName = jsonValid
                        ? file.getName().substring(0, file.getName().length() - 5)
                        : file.getName();
                this.rootObject.put(syntheticTableName, jsonValue);
            } else {
                throw new IllegalArgumentException("JSON root must be an Object or Array. Found: " + jsonValue.getClass().getName());
            }
            this.lastOpenedFile = file;
        } catch (Exception e) {
            this.rootObject = null;
            this.lastOpenedFile = null;
            throw new IOException("Failed to parse JSON file: " + e.getMessage(), e);
        }
    }

    // Helper to get the JSONObject that actually contains table_name:JSONArray pairs
    private JSONObject getTablesContainerObject(File file) throws IOException {
        ensureFileOpened(file); // Make sure rootObject is for the correct file and parsed

        // Scenario 1: Exported by SqlToJson: {"dbName": {"table1": [], "table2": []}}
        // In this case, rootObject has one key, and its value is the actual tables container.
        if (this.rootObject.length() == 1) {
            String singleKey = this.rootObject.keys().next();
            Object potentialTablesContainer = this.rootObject.opt(singleKey);
            if (potentialTablesContainer instanceof JSONObject) {
                return (JSONObject) potentialTablesContainer;
            }
        }
        // Scenario 2: Root is already {"table1": [], "table2": []}
        // Scenario 3: Root was an array, now wrapped as {"filename_no_ext": []}
        return this.rootObject;
    }


    @Override
    public List<Map<String, String>> previewData(File file, String tableName) throws IOException {
        ensureFileOpened(file);
        List<Map<String, String>> previewRows = new ArrayList<>();
        JSONObject tablesContainer = getTablesContainerObject(file);

        Object tableData = tablesContainer.opt(tableName);
        if (!(tableData instanceof JSONArray jsonArray)) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found or is not an array in the JSON file.");
        }

        List<String> headers = getColumnHeaders(file, tableName); // Get unified headers

        for (int i = 0; i < jsonArray.length() && i < 5; i++) { // Preview first 5 records
            Object item = jsonArray.opt(i); // Use opt to avoid exception if array is shorter
            if (item instanceof JSONObject jsonObj) {
                Map<String, String> rowMap = new LinkedHashMap<>(); // Preserve header order
                for (String header : headers) { // Iterate through unified headers
                    rowMap.put(header, jsonObj.optString(header, null)); // Get value or null if missing
                }
                previewRows.add(rowMap);
            } else {
                errors.add("Preview warning: Item at index " + i + " in table '" + tableName + "' is not a JSON object. Skipping.");
            }
        }
        return previewRows;
    }

    @Override
    public List<String> getDetectedTableNames(File file) throws IOException {
        ensureFileOpened(file);
        JSONObject tablesContainer = getTablesContainerObject(file);

        List<String> tableNames = new ArrayList<>();
        Iterator<String> keys = tablesContainer.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (tablesContainer.opt(key) instanceof JSONArray) { // Only consider keys that point to arrays
                tableNames.add(key);
            }
        }
        return tableNames;
    }

    @Override
    public List<String> getColumnHeaders(File file, String tableName) throws IOException, IllegalArgumentException {
        ensureFileOpened(file);
        JSONObject tablesContainer = getTablesContainerObject(file);

        Object tableData = tablesContainer.opt(tableName);
        if (!(tableData instanceof JSONArray jsonArray)) {
            throw new IllegalArgumentException("Table '" + tableName + "' not found or is not an array in the JSON file.");
        }

        if (jsonArray.isEmpty()) {
            return new ArrayList<>(); // No data, no headers
        }

        // Scan first few objects (e.g., up to 10) to get a comprehensive list of unique keys (headers)
        // Maintain order of first appearance for headers.
        Set<String> headerSet = new LinkedHashSet<>(); // LinkedHashSet preserves insertion order
        int scanLimit = Math.min(jsonArray.length(), 10); // Scan up to 10 objects

        for (int i = 0; i < scanLimit; i++) {
            Object item = jsonArray.opt(i);
            if (item instanceof JSONObject jsonObj) {
                headerSet.addAll(jsonObj.keySet());
            }
        }
        if(headerSet.isEmpty() && !jsonArray.isEmpty()){
            // If scanLimit was 0 or all scanned items were not JSONObjects, but array is not empty
            // This means the array might contain non-object items, or only non-objects at the start.
            // This is an edge case; for now, we return empty headers if no keys found from JSONObjects.
             errors.add("Warning: No JSON objects found in the first " + scanLimit + " items of table '" + tableName + "' to determine headers.");
        }

        return new ArrayList<>(headerSet);
    }

    @Override
    public String importData(File file, String sourceTableName, DatabaseInserterInterface inserter,
                             final int bufferSize, String targetTableName, boolean createNewTable,
                             Map<String, String> columnMapping)
            throws IOException, IllegalArgumentException, SQLException {
        ensureFileOpened(file);
        this.errors.clear();
        this.progress.set(0.0);

        if (inserter == null) {
            throw new IllegalArgumentException("Database inserter is null.");
        }
        if (targetTableName == null || targetTableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Target table name must be specified.");
        }

        ArrayList<LinkedHashMap<String, String>> data = new ArrayList<>();

        final ObjectMapper objectMapper = new ObjectMapper();
        final JsonFactory jsonFactory = objectMapper.getFactory();

        try (final InputStream inputStream = new FileInputStream(file);
             JsonParser jsonParser = jsonFactory.createParser(inputStream)) {

            JsonToken token;

            do {
                token = jsonParser.nextToken();
            } while (token != JsonToken.START_ARRAY || !jsonParser.currentName().equals(sourceTableName));

            do {
                token = jsonParser.nextToken();

                if (token == JsonToken.END_ARRAY) break;

                if (token == JsonToken.START_OBJECT) {
                    LinkedHashMap<String, String> map = new LinkedHashMap<>();

                    while ((token = jsonParser.nextToken()) != JsonToken.END_OBJECT) {
                        if (token == JsonToken.FIELD_NAME) {
                            String fieldName = jsonParser.currentName();

                            if (columnMapping.containsValue(fieldName)) {
                                // Avança para o valor do campo
                                token = jsonParser.nextToken();
                                String value = (token == JsonToken.VALUE_NULL) ? null : jsonParser.getValueAsString();

                                map.put(getKeyByValue(columnMapping, fieldName), value);
                            }
                        }
                    }

                    data.add(map);

                }
            } while (token != JsonToken.END_ARRAY);

        } catch (Exception e) {
            e.printStackTrace();
        }

        this.progress.set(1);

        if (!inserter.insertData(targetTableName, data)) throw new SQLException(inserter.getException());

        return sourceTableName;
    }

    // stupid code yh
    public static <K, V> K getKeyByValue(Map<K, V> map, V value) {
        for (Map.Entry<K, V> entry : map.entrySet()) {
            if (entry.getValue().equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public double getImportProgress() {
        return progress.get();
    }

    @Override
    public void setImportProprerty(final DoubleProperty property) {
        if (property != null) {
            this.progress.unbind(); // Good practice if it could have been bound before
            property.bind(this.progress);
        }
    }

    @Override
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
}
