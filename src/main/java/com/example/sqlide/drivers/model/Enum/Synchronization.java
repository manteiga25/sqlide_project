package com.example.sqlide.drivers.model.Enum;

public enum Synchronization {

    OFF(0, "OFF"),
    NORMAL(1, "NORMAL"),
    FULL(2, "FULL"),
    EXTRA(3, "EXTRA");

    private final int code;
    private final String name;

    public static Synchronization getValue(int code) {
        return Synchronization.values()[code];
    }

    Synchronization(int code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getName() {
        return name;
    }


}
