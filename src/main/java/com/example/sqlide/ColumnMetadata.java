package com.example.sqlide;

import java.util.ArrayList;

public class ColumnMetadata {

    public boolean NOT_NULL = false;
    public boolean IsPrimaryKey = false;
    public String defaultValue = "";
    public String Type = "";
    public String Name = "";
    public int size = 0;
    public boolean isUnique = false;
    public String index = null;
    public int integerDigits = 0;
    public int decimalDigits = 0;
    public ArrayList<String> items;
    public String indexType = null;
    public String aliasType = null;
    public Foreign foreign;

    public ColumnMetadata(final boolean NOT_NULL, final boolean IsPrimaryKey, Foreign foreign, final String defaultValue, final int size, final String Type, final String Name, boolean isUnique, int integerDigits, int decimalDigits, final String index) {
        this.NOT_NULL = NOT_NULL;
        this.IsPrimaryKey = IsPrimaryKey;
        this.defaultValue = defaultValue;
        this.size = size;
        this.index = index;
        this.Type = Type;
        this.foreign = foreign;
        this.Name = Name;
        this.isUnique = isUnique;
        this.integerDigits = integerDigits;
        this.decimalDigits = decimalDigits;
    }

    public static class Foreign {
        public boolean isForeign = false;
        public String onUpdate = "", onEliminate = "", tableRef, columnRef;
    }

}