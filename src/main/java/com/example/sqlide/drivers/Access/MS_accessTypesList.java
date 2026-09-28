package com.example.sqlide.drivers.msaccess;

import com.example.sqlide.drivers.model.TypesModelList;

public class MS_accessTypesList extends TypesModelList {

    public MS_accessTypesList() {
        super.listOfTypes = new String[]{
                "BIT",
                "BYTE",
                "SHORT",
                "LONG",
                "CURRENCY",
                "FLOAT",
                "DOUBLE",
                "DATETIME",
                "GUID",
                "TEXT",
                "OLE",
                "MEMO",
                "REPLID",
                "NUMERIC"
        };
        super.chars = new String[]{"TEXT", "MEMO"};
        super.geometry = null;
    }

}
