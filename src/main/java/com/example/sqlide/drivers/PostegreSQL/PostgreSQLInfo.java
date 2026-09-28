package com.example.sqlide.drivers.PostegreSQL;

import com.example.sqlide.drivers.MySQL.MySQLTypesList;
import com.example.sqlide.drivers.model.DatabaseInfo;
import com.example.sqlide.drivers.model.SQLTypes;

public class PostgreSQLInfo extends DatabaseInfo {

    public PostgreSQLInfo() {
        // No PostgreSQL, UNIQUE é uma cláusula e os restantes são métodos (USING ...).
        super.indexModes = new String[]{"", "UNIQUE", "btree", "hash", "gin", "gist", "brin"};
        super.foreignModes = new String[]{"CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION"};
        super.sqlType = SQLTypes.POSTGRESQL;
        super.typesOfDB = new PostgreSQLTypeList();
    }

}
