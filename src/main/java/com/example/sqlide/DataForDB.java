package com.example.sqlide;

import java.util.ArrayList;
import java.util.HashMap;

public class DataForDB {

   // long index;

    private HashMap<String, String> data = new HashMap<>();

   // HashMap<String, String> type = new HashMap<>();

  /*  public DataForDB(final HashMap<String, String> data, final HashMap<String, String> type) {
        this.data = data;
        this.type = type;
    } */

    public ArrayList<String> getColumns() {
        return (ArrayList<String>) data.keySet();
    }

    public DataForDB(final HashMap<String, String> data) {
        this.data = data;
      //  this.index = index;
    }

    public String GetData(final String column) {
        return data.get(column);
    }

 //   public String GetType(final String column) { return type.get(column); }

    public void AddColumn(final String Name, final String data) {
        this.data.put(Name, data);
     //   this.type.put(Name, type);
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
   //     this.type.remove(Name);
    }

}
