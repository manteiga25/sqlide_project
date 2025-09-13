package com.example.sqlide.drivers.PostegreSQL;

import com.example.sqlide.drivers.MySQL.MySQLTypesList;
import com.example.sqlide.drivers.model.DatabaseInfo;
import com.example.sqlide.drivers.model.SQLTypes;

public class PostgreSQLInfo extends DatabaseInfo {

    public PostgreSQLInfo() {
        super.indexModes = new String[]{""};
        super.foreignModes = new String[]{"CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION"};
        super.sqlType = SQLTypes.POSTGRESQL;
        super.typesOfDB = new PostgreSQLTypeList();
    }

}
