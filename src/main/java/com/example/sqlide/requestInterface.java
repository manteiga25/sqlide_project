package com.example.sqlide;

import java.util.ArrayList;
import java.util.HashMap;

public interface requestInterface {

    public abstract boolean ShowData(final String query, final String table);

    public abstract HashMap<String, ArrayList<HashMap<String, String>>> getTableMetadata();

    public abstract boolean sendEmail(final String body);

    public abstract HashMap<String, ArrayList<Object>> getData(final String query, final String table);

    public abstract boolean createTable(final String table, final ArrayList<HashMap<String, String>> meta);

}
