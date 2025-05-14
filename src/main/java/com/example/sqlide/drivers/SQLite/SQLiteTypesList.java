package com.example.sqlide.drivers.SQLite;

import com.example.sqlide.drivers.model.TypesModelList;

public class SQLiteTypesList extends TypesModelList {

    public SQLiteTypesList() {
        super.listOfTypes = new String[]{
                "INT",
                "INTEGER",
                "TINYINT",
                "SMALLINT",
                "MEDIUMINT",
                "BIGINT",
                "UNSIGNED BIG INT",
                "INT2",
                "INT8",
                "CHARACTER",
                "VARCHAR",
                "VARYING CHARACTER",
                "NCHAR",
                "NATIVE CHARACTER",
                "NVARCHAR",
                "TEXT",
                "CLOB",
                "BLOB",
                "REAL",
                "DOUBLE",
                "DOUBLE PRECISION",
                "FLOAT",
                "NUMERIC",
                "DECIMAL",
                "BOOLEAN",
                "DATE",
                "DATETIME"
        };
        super.chars = new String[]{"CHARACTER",
                "VARCHAR",
                "VARYING CHARACTER",
                "NCHAR",
                "NATIVE CHARACTER",
                "NVARCHAR"};
        super.geometry = null;
    }

}
