package com.example.sqlide.Metadata;

import java.util.ArrayList;
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
    // Começava a null no construtor vazio e havia código a ler foreign.isForeign sem testar.
    public Foreign foreign = new Foreign();
    public String check = "";
    /**
     * -1: a coluna não é preenchida pelo motor. 0: é preenchida sem o pedir (a INTEGER
     * PRIMARY KEY do SQLite, que é o rowid). 1: autoincremento declarado (AUTOINCREMENT,
     * AUTO_INCREMENT, IDENTITY/SERIAL) — este tem de ser repetido quando a coluna é redefinida.
     */
    public long autoincrement = -1;
    public String comment = "";
    /**
     * Cláusulas próprias do motor que não têm campo aqui e não se podem perder quando a
     * coluna é redefinida: o {@code COLLATE NOCASE} do SQLite, o
     * {@code ON UPDATE CURRENT_TIMESTAMP} do MySQL. Vão no fim da definição tal como estão.
     */
    public String extra = "";

    public ColumnMetadata(final boolean NOT_NULL, final boolean IsPrimaryKey, Foreign foreign, final String defaultValue, final int size, final String Type, final String Name, boolean isUnique, int integerDigits, int decimalDigits, final String index) {
        this.NOT_NULL = NOT_NULL;
        this.IsPrimaryKey = IsPrimaryKey;
        this.defaultValue = defaultValue;
        this.size = size;
        this.index = index;
        this.Type = Type;
        this.foreign = foreign == null ? new Foreign() : foreign;
        this.Name = Name;
        this.isUnique = isUnique;
        this.integerDigits = integerDigits;
        this.decimalDigits = decimalDigits;
    }

    public ColumnMetadata() {

    }

    /** Cópia independente, para editar sem mexer no original enquanto a alteração não é gravada. */
    public ColumnMetadata copy() {
        final ColumnMetadata copy = new ColumnMetadata();
        copy.copyFrom(this);
        return copy;
    }

    /**
     * Passa para este objeto todos os campos de {@code other}.
     *
     * <p>As colunas da grelha guardam o seu {@code ColumnMetadata} num campo final, por isso
     * uma alteração tem de ser copiada para dentro do objeto que lá está.</p>
     */
    public void copyFrom(final ColumnMetadata other) {
        NOT_NULL = other.NOT_NULL;
        IsPrimaryKey = other.IsPrimaryKey;
        defaultValue = other.defaultValue;
        Type = other.Type;
        Name = other.Name;
        size = other.size;
        isUnique = other.isUnique;
        index = other.index;
        integerDigits = other.integerDigits;
        decimalDigits = other.decimalDigits;
        items = other.items == null ? null : new ArrayList<>(other.items);
        indexType = other.indexType;
        aliasType = other.aliasType;
        foreign = other.foreign == null ? new Foreign() : other.foreign.copy();
        check = other.check;
        autoincrement = other.autoincrement;
        comment = other.comment;
        extra = other.extra;
    }

    public static class Foreign {
        public boolean isForeign = false;
        public String onUpdate = "", onEliminate = "", tableRef, columnRef;

        public Foreign copy() {
            final Foreign copy = new Foreign();
            copy.isForeign = isForeign;
            copy.onUpdate = onUpdate;
            copy.onEliminate = onEliminate;
            copy.tableRef = tableRef;
            copy.columnRef = columnRef;
            return copy;
        }
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