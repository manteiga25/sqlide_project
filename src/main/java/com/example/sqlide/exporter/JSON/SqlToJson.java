package com.example.sqlide.exporter.JSON;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class SqlToJson {

    private final JsonGenerator jsonGenerator;
    private final FileWriter fileWriter;
    private boolean isTableOpen = false;

    public SqlToJson(String dbName, String path) throws IOException {
        fileWriter = new FileWriter(path);
        JsonFactory factory = new JsonFactory();
        jsonGenerator = factory.createGenerator(fileWriter);
        jsonGenerator.useDefaultPrettyPrinter();

        // Start root: { "dbName":
        jsonGenerator.writeStartObject();
        jsonGenerator.writeFieldName(dbName);
        jsonGenerator.writeStartObject();  // {
    }

    public void createJsonArrayTable(String tableName) throws IOException {
        closeCurrentTable();
        jsonGenerator.writeFieldName(tableName);
        jsonGenerator.writeStartArray(); // "tableName": [
        isTableOpen = true;
    }

    public void write(ArrayList<HashMap<String, String>> data) throws IOException {
        if (!isTableOpen || data == null) throw new IllegalStateException("No open table");

        for (final HashMap<String, String> row : data) {
            jsonGenerator.writeStartObject(); // {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                jsonGenerator.writeStringField(
                        entry.getKey(),
                        entry.getValue() != null ? entry.getValue() : "null"
                );
            }
            jsonGenerator.writeEndObject(); // }
        }
    }

    public void closeCurrentTable() throws IOException {
        if (isTableOpen) {
            jsonGenerator.writeEndArray(); // ]
            isTableOpen = false;
        }
    }

    public void close() throws IOException {
        closeCurrentTable();
        jsonGenerator.writeEndObject(); // } (root)
        jsonGenerator.close();
        fileWriter.close();
    }

}
