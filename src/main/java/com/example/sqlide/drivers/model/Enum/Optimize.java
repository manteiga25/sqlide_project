package com.example.sqlide.drivers.model.Enum;

public enum Optimize {

    DEFAULT(0, "0xfffe"),
    DEBUG(1, "0x00001"),
    NOT_ALL(2, "0x00002"),
    TEMPORARY(3, "0x00010"),
    SIZE(4, "0x10000");

    private final int code;
    private final String name;

    Optimize(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getName() {
        return name;
    }

}
