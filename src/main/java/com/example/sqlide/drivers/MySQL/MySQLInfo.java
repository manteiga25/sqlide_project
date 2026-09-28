package com.example.sqlide.drivers.MySQL;

import com.example.sqlide.drivers.SQLite.SQLiteTypesList;
import com.example.sqlide.drivers.model.DatabaseInfo;
import com.example.sqlide.drivers.model.SQLTypes;

public class MySQLInfo extends DatabaseInfo {

    public MySQLInfo() {
        // FULLTEXT e SPATIAL são cláusulas do CREATE INDEX no MySQL, como UNIQUE.
        super.indexModes = new String[]{"", "UNIQUE", "FULLTEXT", "SPATIAL"};
        // O InnoDB recusa ON ... SET DEFAULT, por isso não aparece na lista.
        super.foreignModes = new String[]{"CASCADE", "SET NULL", "RESTRICT", "NO ACTION"};
        super.sqlType = SQLTypes.MYSQL;
        super.typesOfDB = new MySQLTypesList();
    }

}
