package com.example.sqlide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

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
    public String indexType = "";
    public String aliasType = "";
    public Foreign foreign;
    public String check = "";
    public long autoincrement = -1;
    public String comment = "";

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

    public static LinkedHashMap<String, String> MetadataToMap(final ColumnMetadata meta) {
        final LinkedHashMap<String, String> map = new LinkedHashMap<>();

        // Campos primitivos
        map.put("Name", meta.Name);
        map.put("Type", meta.Type);
        map.put("NOT_NULL", String.valueOf(meta.NOT_NULL));
        map.put("IsPrimaryKey", String.valueOf(meta.IsPrimaryKey));
        map.put("DefaultValue", meta.defaultValue);
        map.put("Size", String.valueOf(meta.size));
        map.put("IsUnique", String.valueOf(meta.isUnique));
        map.put("Index", meta.index != null ? meta.index : "");
        map.put("IntegerDigits", String.valueOf(meta.integerDigits));
        map.put("DecimalDigits", String.valueOf(meta.decimalDigits));
        map.put("IndexType", meta.indexType != null ? meta.indexType : "");
        map.put("AliasType", meta.aliasType != null ? meta.aliasType : "");

        // Lista de items
        if (meta.items != null && !meta.items.isEmpty()) {
            map.put("Items", String.join(",", meta.items));
        }

        // Chave estrangeira
        if (meta.foreign != null) {
            map.put("Foreign_isForeign", String.valueOf(meta.foreign.isForeign));
            map.put("Foreign_onUpdate", meta.foreign.onUpdate);
            map.put("Foreign_onEliminate", meta.foreign.onEliminate);
            map.put("Foreign_tableRef", meta.foreign.tableRef);
            map.put("Foreign_columnRef", meta.foreign.columnRef);
        }

        return map;
    }

    public static ArrayList<Object> MetadataToArrayList(final ColumnMetadata meta) {
        ArrayList<Object> list = new ArrayList<>();

        // Campos primitivos
        list.add(meta.Name);
        list.add(meta.Type);
        list.add(meta.NOT_NULL);
        list.add(meta.IsPrimaryKey);
        list.add(meta.defaultValue);
        list.add(meta.size);
        list.add(meta.isUnique);
        list.add(meta.index != null ? meta.index : "");
        list.add(meta.integerDigits);
        list.add(meta.decimalDigits);
        list.add(meta.indexType != null ? meta.indexType : "");
        list.add(meta.aliasType != null ? meta.aliasType : "");

        // Lista de items
        if (meta.items != null && !meta.items.isEmpty()) {
            list.add(meta.items);
        }

        // Chave estrangeira
        if (meta.foreign != null) {
            ArrayList<Object> foreignList = new ArrayList<>();
            foreignList.add(meta.foreign.isForeign);
            foreignList.add(meta.foreign.onUpdate);
            foreignList.add(meta.foreign.onEliminate);
            foreignList.add(meta.foreign.tableRef);
            foreignList.add(meta.foreign.columnRef);
            list.add(foreignList);
        }

        return list;
    }

}