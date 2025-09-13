package com.example.sqlide.drivers.SQLite;

import com.example.sqlide.drivers.model.DatabaseInfo;
import com.example.sqlide.drivers.model.SQLTypes;

import java.util.ArrayList;
import java.util.List;

public class SQLiteInfo extends DatabaseInfo {

    public SQLiteInfo() {
        super.indexModes = new String[]{""};
        super.foreignModes = new String[]{"CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION"};
        super.sqlType = SQLTypes.SQLITE;
        super.typesOfDB = new SQLiteTypesList();
    }

}
