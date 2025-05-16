package com.example.sqlide;

import java.util.ArrayList;

public class ColumnMetadata {

    public boolean NOT_NULL = false;
    public boolean IsPrimaryKey = false;
    public String[] ForeignKey = new String[2];
    public String defaultValue = "";
    public String Type = "";
    public String Name = "";
    public int size = 0;
    public boolean isForeign = false;
    public boolean isUnique = false;
    public String index = null;
    public int integerDigits = 0;
    public int decimalDigits = 0;
    public String foreignColumnRef = "";
    public String foreignTableRef = "";
    public ArrayList<String> items;
    public String indexType = null;
    public String aliasType = null;
    public Foreign foreign;

    public ColumnMetadata(final boolean NOT_NULL, final boolean IsPrimaryKey, final String[] ForeignKey, final boolean isForeign, final String defaultValue, final int size, final String Type, final String Name, boolean isUnique, int integerDigits, int decimalDigits, final String index) {
        this.NOT_NULL = NOT_NULL;
        this.IsPrimaryKey = IsPrimaryKey;
        this.ForeignKey = ForeignKey;
        this.defaultValue = defaultValue;
        this.size = size;
        this.index = index;
        this.Type = Type;
        this.Name = Name;
        this.isForeign = isForeign;
        this.isUnique = isUnique;
        this.integerDigits = integerDigits;
        this.decimalDigits = decimalDigits;
    }

    public static class Foreign {
        public boolean isForeign = false;
        public String onUpdate = "", onEliminate = "";
    }

}