package com.example.sqlide.drivers.model.Interfaces;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public interface DatabaseInserterInterface {

    public abstract boolean insertData(String Table, HashMap<String, String> data);

    public abstract boolean insertData(String Table, ArrayList<HashMap<String, String>> data);

    public abstract boolean removeData(String Table, HashMap<String, String> data, ArrayList<Long> rowid);

    public abstract boolean removeData(String Table, ArrayList<String> rowid) throws SQLException;

    public abstract boolean removeData(String Table, HashMap<String, String> data, HashMap<String, String> prime);

    public abstract String getException();

}
