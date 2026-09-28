package com.example.sqlide.drivers.Access;

import com.example.sqlide.drivers.model.DatabaseInfo;
import com.example.sqlide.drivers.model.SQLTypes;

public class MS_accessInfo extends DatabaseInfo {

    public MS_accessInfo() {
        super.indexModes = new String[]{""};
        super.foreignModes = new String[]{"CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION"};
        super.sqlType = SQLTypes.MS_ACCESS;
        super.typesOfDB = new com.example.sqlide.drivers.msaccess.MS_accessTypesList();
    }

}
