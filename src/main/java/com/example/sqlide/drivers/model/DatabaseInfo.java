package com.example.sqlide.drivers.model;

public abstract class DatabaseInfo {

    protected String[] indexModes, foreignModes;
    protected TypesModelList typesOfDB;
    protected SQLTypes sqlType;

    public String[] getListChars() {
        return typesOfDB.chars;
    }

    public String[] getList() {
        return typesOfDB.listOfTypes;
    }

    public String[] getIndexModes() {
        return indexModes;
    }

    public String[] getForeignModes() {
        return foreignModes;
    }

    public SQLTypes getSqlType() {
        return sqlType;
    }
}
