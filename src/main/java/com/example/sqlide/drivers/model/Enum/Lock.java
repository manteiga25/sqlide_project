package com.example.sqlide.drivers.model.Enum;

public enum Lock {

    NORMAL(0, "NORMAL"),
    EXCLUSIVE(1, "EXCLUSIVE");

    private final int code;
    private final String name;

    Lock(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static Lock getValue(int code) {
        return Lock.values()[code];
    }

    public String getName() {
        return name;
    }

}
