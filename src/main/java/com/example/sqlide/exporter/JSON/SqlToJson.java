package com.example.sqlide.exporter.JSON;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class SqlToJson {

    private final JSONObject DatabaseJson;
    private final JSONObject tableJson;
    private JSONArray currentTable;
    private String MSGException = "";

    private final FileWriter file;

    private StringBuilder buffer = new StringBuilder();

    private String tableBuffer = "";

    public SqlToJson(final String dbName, final String path) throws IOException {

        file = new FileWriter(path);

        DatabaseJson = new JSONObject();
        tableJson = new JSONObject();
        DatabaseJson.put(dbName, tableJson);
        file.append("{\"").append(dbName).append("\": ");
    }

    public void createJsonArrayTable(final String Table) {
        buffer.append("\n\t{\"").append(Table).append("\": [");
        //tableJson.clear();
        //currentTable = new JSONArray();
        //tableJson.put(Table, currentTable);
    }

    public void write(final ArrayList<HashMap<String, String>> data) throws IOException {
        if (data != null) {
            for (final HashMap<String, String> list : data) {
                buffer.append("\n\t\t{");
                //   JSONObject values = new JSONObject();
                for (final String key : list.keySet()) {
                    // values.put(key, list.get(key));
                    buffer.append("\n\t\t\t\"").append(key).append("\": \"").append(list.get(key)).append("\",");
                }
                buffer = buffer.replace(buffer.lastIndexOf(","), buffer.lastIndexOf(",") + 1, "");
                buffer.append("\n\t\t},");
                // currentTable.put(values);
            }
          //  file.append(buffer);
            tableBuffer = buffer.toString();
            //buffer = new StringBuilder();
        }
    }

    public void resetBuffer() {
        buffer = new StringBuilder();
    }

    public void flushData() throws IOException {
        file.append(buffer);
        buffer = new StringBuilder();
    }

    public void flushDataAndEndTable() throws IOException {
        System.out.println(buffer.toString());
        buffer = buffer.replace(buffer.lastIndexOf(","), buffer.lastIndexOf(",") + 1, "");
        file.append(buffer);
        buffer = new StringBuilder();
        endTable();
    }

    public void endTable() throws IOException {
     /*   file.write(127);
        file.write(127);
        file.write("\n\t]\n\t},"); */
        buffer.append("\n\t]\n\t},");
    }

    public void EndFetch() throws IOException {
        buffer = buffer.replace(buffer.lastIndexOf(","), buffer.lastIndexOf(",") + 1, "").append("\n}");
       // file.write(127);
        //file.write("\n}");
        file.append(buffer);
    }

    public void close() throws IOException {
        file.close();
        buffer = null;
    }

    public boolean save(final String path) {
        try {
            FileWriter file = new FileWriter(path);
            file.write(DatabaseJson.toString(4));
            file.close();
            return true;
        } catch (IOException e) {
            MSGException = e.getMessage();
            return false;
        }
    }

    public String GetException() {
        final String tmp = MSGException;
        MSGException = "";
        return tmp;
    }

}
