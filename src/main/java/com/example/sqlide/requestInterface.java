package com.example.sqlide;

import com.example.sqlide.drivers.model.SQLTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

public interface requestInterface {

    public abstract boolean ShowData(final String query, final String table);

    public abstract HashMap<String, ArrayList<HashMap<String, String>>> getTableMetadata();

    public abstract boolean sendEmail(final String body);

    public abstract ArrayList<HashMap<String, String>> getData(final String query, final String table);

    public abstract boolean createTable(final String table, final ArrayList<HashMap<String, String>> meta, String string);

    boolean createView(String table, String code, String string);

    public abstract String insertData(final String table, final ArrayList<LinkedHashMap<String, String>> data);

    public abstract boolean createReport(final String title, final String query);

    public abstract String currentTable();

    public boolean createTriggers(final HashMap<String, String> triggers);

    public boolean createEvents(final HashMap<String, String> events);

    public SQLTypes getSQLType();

    public boolean createGraphic(final String table, final String name, final String x, final String y, final ArrayList<HashMap<String, String>> labels);

    public boolean createFunction(final HashMap<String, String> functions);

    public boolean createProcedure(HashMap<String, String> procedures);
}
