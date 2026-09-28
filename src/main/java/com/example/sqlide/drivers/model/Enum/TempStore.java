package com.example.sqlide.drivers.model.Enum;

public enum TempStore {

    DEFAULT(0, "DEFAULT"),
    FILE(1, "FILE "),
    MEMORY(2, "MEMORY");

    private final int code;
    private final String name;

    TempStore(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
