package com.example.sqlide.drivers.MySQL;

import com.example.sqlide.drivers.SQLite.SQLiteTypesList;
import com.example.sqlide.drivers.model.DatabaseInfo;
import com.example.sqlide.drivers.model.SQLTypes;

public class MySQLInfo extends DatabaseInfo {

    public MySQLInfo() {
        super.indexModes = new String[]{""};
        super.foreignModes = new String[]{"CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION"};
        super.sqlType = SQLTypes.MYSQL;
        super.typesOfDB = new MySQLTypesList();
    }

}
