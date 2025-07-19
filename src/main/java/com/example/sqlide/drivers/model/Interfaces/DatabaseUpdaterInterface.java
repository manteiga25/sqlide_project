package com.example.sqlide.drivers.model.Interfaces;

import java.util.ArrayList;
import java.util.HashMap;

public interface DatabaseUpdaterInterface {

    public abstract boolean updateData(String Table, HashMap<String, String> data, long index);

    public abstract boolean updateData(String Table, String column, String value, long index);

    public abstract boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, long index, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, String[] index, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, String value, String[] index, String type, String PrimeKey, String tmp);

    boolean updateData(String Table, String column, String value, String[] index, String type, ArrayList<String> PrimeKey, ArrayList<String> tmp);

    public abstract boolean updateData(String Table, String column, Object value, String index, String PrimeKey, String tmp);

    public abstract String getException();

}
