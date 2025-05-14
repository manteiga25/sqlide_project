package com.example.sqlide;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

abstract public class checkData {

    public static boolean checkNameAbs(final String name) {
        if (name.isEmpty()) {
            return false;
        }
        else return !containsNumber(name);
    }

    public static boolean containsNumber(String str) {
        // Expressão regular que procura por um ou mais dígitos
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(str);

        return matcher.find();
    }

}
