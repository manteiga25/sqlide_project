package com.example.sqlide.drivers.msaccess;

import com.example.sqlide.drivers.model.TypesModel;

public class MS_accessTypes extends TypesModel {

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
        } catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkShort(final String val) {
        try {
            Short.parseShort(val);
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
        } catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkFloat(final String val) {
        try {
            Float.parseFloat(val);
            return true;
        } catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkDouble(final String val) {
        try {
            Double.parseDouble(val);
            return true;
        } catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    private boolean checkBoolean(final String val) {
        try {
            if (val.equals("0") || val.equals("1") || val.equals("true") || val.equals("false")) {
                return true;
            }
            throw new Exception("Invalid Boolean value");
        } catch (Exception e) {
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
        } catch (Exception e) {
            ExceptionMessage = e.getMessage();
            return false;
        }
    }

    @Override
    protected boolean checkNumeric(String Value, byte bits) {
        return true;
    }

    @Override
    public boolean checkValue(String Type, String Value) {
        return true;
    }

    @Override
    public boolean checkValue(String Type, String Value, int size) {
        return true;
    }

    @Override
    public boolean checkValue(String Type, String Value, int size, boolean NotNull) {
        if ((Value == null || Value.isEmpty()) && NotNull) {
            return true;
        }

        switch (Type) {
            case "BYTE":
                return checkByte(Value);
            case "SHORT":
                return checkShort(Value);
            case "LONG":
                return checkInt(Value);
            case "FLOAT":
                return checkFloat(Value);
            case "DOUBLE":
                return checkDouble(Value);
            case "BIT":
                return checkBoolean(Value);
            case "TEXT", "MEMO":
                return checkCharOverflow(size, Value);
            default:
                return true;
        }
    }

    @Override
    public boolean checkValue(String Type, String Value, long len) {
        return true;
    }
}
