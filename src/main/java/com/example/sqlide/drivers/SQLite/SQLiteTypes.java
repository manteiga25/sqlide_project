package com.example.sqlide.drivers.SQLite;

import com.example.sqlide.drivers.model.TypesModel;

import java.math.BigInteger;

public class SQLiteTypes extends TypesModel {

    final static int MAX_MEDIUM = 16777216;
    final static int MIN_MEDIUM = -16777217;

    private String ExceptionMessage = "";

    public String getException() {
        final String ret = ExceptionMessage;
        ExceptionMessage = "";
        return ret;
    }

    private boolean checkByte(final String val) {
        try {
            Byte.parseByte(val);
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkShort(final String val) {
        try {
            Short.parseShort(val);
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkMedium(final String val) {
        try {
            final int medium = Integer.parseInt(val);
            if (medium > MAX_MEDIUM || medium < MIN_MEDIUM) {
                throw new Exception("Strong value for medium type 24 bits");
            }
            return true;
        } catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkInt(final String val) {
        try {
            Integer.parseInt(val);
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkLong(final String val) {
        try {
            Long.parseLong(val);
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkULong(final String val) {
        try {
            new BigInteger(val);
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkCharOverflow(final long len, final String val) {
        try {
            if (val.length() > len) {
                throw new Exception("Overflow Word size max " + len + " characters");
            }
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkFloat(final String val) {
        try {
            Float.parseFloat(val);
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkDouble(final String val) {
        try {
            Double.parseDouble(val);
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkBoolean(final String val) {
        try {
            if (val.equals("0") || val.equals("1") || val.equals("true") || val.equals("false")) {
                throw new Exception("Invalid Boolean value");
            }
            return true;
        }
        catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    @Override
    protected boolean checkNumeric(final String Value, final byte bits) {
        try {
            final long val = Long.parseLong(Value);
            final long MAX_BITS = (long) Math.pow(2, bits);
            final long MIN_BITS = -MAX_BITS-1;
            if (val > MAX_BITS || val < MIN_BITS) {
                throw new Exception("Overflow value for " + bits + " bits");
            }
            return true;
        } catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    @Override
    public boolean checkValue(String Type, String Value) {
        return false;
    }

    @Override
    public boolean checkValue(String Type, String Value, int size) {
        return false;
    }

    @Override
    public boolean checkValue(final String Type, String Value, final int size, final boolean NotNull) {

        System.out.println((Value == null || Value.isEmpty()) && NotNull);

        if ((Value == null || Value.isEmpty()) && NotNull) {
            return true;
        }

        switch (Type) {
            case "INT", "INTEGER" -> {
                return checkInt(Value);
            }
            case "TINYINT" -> {
                return checkByte(Value);
            }
            case "SMALLINT", "INT2" -> {
                return checkShort(Value);
            }
            case "MEDIUMINT" -> {
                return checkMedium(Value);
            }
            case "BIGINT", "INT8" -> {
                return checkLong(Value);
            }
            case "UNSIGNED BIG INT" -> {
                return checkULong(Value);
            }
            case "FLOAT" -> {
                return checkFloat(Value);
            }
            case "DOUBLE" -> {
                return checkDouble(Value);
            }
            case "DECIMAL" -> System.out.println("Tipo DECIMAL");
            case "BOOLEAN" -> {
                return checkBoolean(Value.toLowerCase());
            }
            case "DATE" -> System.out.println("Tipo DATE");
            case "DATETIME" -> System.out.println("Tipo DATETIME");
            default -> { return size != 0 && checkCharOverflow(size, Value); }
        }
        return true;
    }

    @Override
    public boolean checkValue(final String Type, String Value, final long len) {
        return false;
    }

}
