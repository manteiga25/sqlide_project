package com.example.sqlide.drivers.model;

public abstract class TypesModel {

    String type;

    protected abstract boolean checkNumeric(String Value, byte bits);

    public abstract boolean checkValue(final String Type, String Value);

    public abstract boolean checkValue(String Type, String Value, int size);

    public abstract boolean checkValue(String Type, String Value, int size, boolean NotNull);

    public abstract boolean checkValue(String Type, String Value, long len);
}
