package com.example.sqlide.drivers.PostegreSQL;

import com.example.sqlide.drivers.model.TypesModelList;

public class PostgreSQLTypeList extends TypesModelList {

    public PostgreSQLTypeList() {
        super.listOfTypes = new String[]{
                // UUID
                "UUID",
                // integers
                "INTEGER",
                "SMALLINT",
                "BIGINT",
                // serials
                "SMALLSERIAL",
                "SERIAL",
                "BIGSERIAL",
                // money
                "MONEY",
                // chars
                "CHARACTER",
                "CHAR",
                "VARCHAR",
                "VARYING CHARACTER",
                "TEXT",
                // byte
                "BYTEA",
                // float
                "REAL",
                "DOUBLE PRECISION",
                "NUMERIC",
                "DECIMAL",
                // boolean
                "BOOLEAN",
                // time
                "DATE",
                "TIME",
                "TIMESTAMP",
                "TIMESTAMPTZ",
                "INTERVAL",
                // lists
                "ENUM",
                // geometry
                "POINT",
                "LINE",
                "POLYGON",
                "LSEG",
                "BOX",
                "PATH",
                "CIRCLE",
                // network
                "MACADDR",
                "INET",
                "CIDR",
        };
        super.chars = new String[]{"CHARACTER",
                "VARCHAR",
                "VARYING CHARACTER",
        "TEXT",
        "CHAR"
        };
        super.geometry = new String[]{
                "POINT",
                "LINE",
                "POLYGON",
                "LSEG",
                "BOX",
                "PATH",
                "CIRCLE"
        };
    }

}
