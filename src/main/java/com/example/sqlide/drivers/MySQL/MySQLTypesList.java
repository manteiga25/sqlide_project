package com.example.sqlide.drivers.MySQL;

import com.example.sqlide.drivers.model.TypesModelList;

public class MySQLTypesList extends TypesModelList {

 //   public final String[] listOfTypes = {"INT", "CHAR"};

    public MySQLTypesList() {
        super.listOfTypes = new String[]{"TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT", "DECIMAL", "NUMERIC", "FLOAT", "DOUBLE", "BIT", "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR", "CHAR", "VARCHAR", "BINARY", "VARBINARY", "TINYBLOB", "BLOB", "MEDIUMBLOB", "LONGBLOB", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT", "ENUM", "SET", "GEOMETRY", "POINT", "LINESTRING", "POLYGON", "MULTIPOINT", "MULTILINESTRING", "MULTIPOLYGON",};
        super.chars = new String[]{"CHAR", "VARCHAR", "BINARY", "VARBINARY", "TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT"};
    }

}
