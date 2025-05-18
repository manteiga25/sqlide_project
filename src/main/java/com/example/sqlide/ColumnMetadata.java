package com.example.sqlide;

import java.util.ArrayList;
import java.util.HashMap;

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

    public static HashMap<String, String> MetadataToMap(final ColumnMetadata meta) {
        final HashMap<String, String> map = new HashMap<>();

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

}