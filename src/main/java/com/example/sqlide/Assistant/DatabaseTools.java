package com.example.sqlide.Assistant;

import com.example.sqlide.requestInterface;
import dev.langchain4j.agent.tool.Tool;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class DatabaseTools {

    private final requestInterface request;

    public DatabaseTools(requestInterface request) {
        this.request = request;
    }

    @Tool("Get the type of SQL on the Schema, it's necessary to generate functions/triggers/events, etc..., to generate a correct code.")
    public String getSQLType() {
        return request.getSQLType().name();
    }

    @Tool("Fetch the current Table on the user is.")
    public String currentTable() {
        return request.currentTable();
    }

    @Tool("Show data for user from a SQL query. If table is not mentioned, the model should decide or use currentTable().")
    public boolean showData(String query, String table) {
        return request.ShowData(query, table);
    }

    @Tool("Request data for user from a SQL query. returns list of data.")
    public String requestData(String query, String table) {
        ArrayList<HashMap<String, String>> data = request.getData(query, table);
        return data != null ? data.toString() : "[]";
    }

    @Tool("Request metadata of table to process metadata of columns.")
    public String getColumnsMetadata() {
        HashMap<String, ArrayList<HashMap<String, String>>> meta = request.getTableMetadata();
        return meta != null ? meta.toString() : "{}";
    }

    @Tool("Generate and send a html email body.")
    public boolean sendEmail(String body) {
        return request.sendEmail(body);
    }

    @Tool("Generate a report.")
    public boolean createReport(String title, String query) {
        return request.createReport(title, query);
    }

    @Tool("Create a sql table.")
    public boolean createTable(String tableName, ArrayList<Map<String, String>> meta, String check) {
        ArrayList<HashMap<String, String>> metaHash = new ArrayList<>();
        for (Map<String, String> m : meta) {
            metaHash.add(new HashMap<>(m));
        }
        return request.createTable(tableName, metaHash, check);
    }

    @Tool("Create data for the table.")
    public String createData(String table, ArrayList<Map<String, String>> data) {
        ArrayList<LinkedHashMap<String, String>> rows = new ArrayList<>();
        for (Map<String, String> m : data) {
            rows.add(new LinkedHashMap<>(m));
        }
        return String.valueOf(request.insertData(table, rows));
    }

    @Tool("Create a view for the table.")
    public boolean createView(String table, String name, String code) {
        return request.createView(table, name, code);
    }

    @Tool("Create SQL triggers for the Schema.")
    public boolean createTrigger(Map<String, String> triggers) {
        return request.createTriggers(new HashMap<>(triggers));
    }

    @Tool("Create SQL functions for the Schema.")
    public boolean createFunction(Map<String, String> functions) {
        return request.createFunction(new HashMap<>(functions));
    }

    @Tool("Create SQL procedures for the Schema.")
    public boolean createProcedure(Map<String, String> procedures) {
        return request.createProcedure(new HashMap<>(procedures));
    }

    @Tool("Create SQL events for the Schema.")
    public boolean createEvent(Map<String, String> events) {
        return request.createEvents(new HashMap<>(events));
    }

    @Tool("Create a Graphic.")
    public boolean createGraphic(String table, String name, String nameX, String nameY, ArrayList<Map<String, String>> labels) {
        ArrayList<HashMap<String, String>> labelsHash = new ArrayList<>();
        for (Map<String, String> l : labels) {
            labelsHash.add(new HashMap<>(l));
        }
        return request.createGraphic(table, name, nameX, nameY, labelsHash);
    }
}
