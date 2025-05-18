package com.example.sqlide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

public interface requestInterface {

    public abstract boolean ShowData(final String query, final String table);

    public abstract boolean sendEmail(final String body);

    public abstract HashMap<String, ArrayList<Object>> getData(final String query, final String table);

    public abstract ArrayList<HashMap<String, String>> getTableMetadata(final String table);

}
