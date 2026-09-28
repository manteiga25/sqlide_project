package com.example.sqlide.drivers.model.Enum;

public enum VACUUM {

    NONE(0, "NONE"),
    FULL(1, "FULL"),
    INCREMENTAL(2, "INCREMENTAL");

    private final int code;
    private final String name;

    VACUUM(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public static VACUUM getValue(int code) {
        return VACUUM.values()[code];
    }

    public String getName() {
        return name;
    }

}
