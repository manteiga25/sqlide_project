package com.example.sqlide;

import java.util.ArrayList;
import java.util.HashMap;

public class DataForDB {

    private final HashMap<String, String> data;

    public ArrayList<String> getColumns() {
        return (ArrayList<String>) data.keySet();
    }

    public DataForDB(final HashMap<String, String> data) {
        this.data = data;
    }

    public String GetData(final String column) {
        return data.get(column);
    }

    public void AddColumn(final String Name, final String data) {
        this.data.put(Name, data);
    }

    public void RenameColumn(final String oldName, final String newName) {
        final String data = this.data.remove(oldName);
        this.data.put(newName, data);
    }

    public void SetData(final String column, final String value) {
        data.put(column, value);
    }

    public void RemoveColumn(final String Name) {
        this.data.remove(Name);
    }

}
