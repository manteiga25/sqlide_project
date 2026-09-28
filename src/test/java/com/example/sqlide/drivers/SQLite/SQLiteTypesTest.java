package com.example.sqlide.drivers.SQLite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A validação que o formulário "Insert data" faz antes de mandar o INSERT. */
class SQLiteTypesTest {

    private final SQLiteTypes types = new SQLiteTypes();

    @Test
    void textWithoutDeclaredSizeAcceptsAnyLength() {
        // Tamanho 0 é "sem limite"; antes qualquer texto era recusado.
        assertTrue(types.checkValue("TEXT", "qualquer coisa", 0, true));
        assertTrue(types.checkValue("VARCHAR", "abc", 3, true));
        assertFalse(types.checkValue("VARCHAR", "abcd", 3, true));
    }

    @Test
    void numbersAreStillChecked() {
        assertTrue(types.checkValue("INTEGER", "42", 0, true));
        assertFalse(types.checkValue("INTEGER", "4x2", 0, true));
        assertTrue(types.checkValue("INTEGER", "", 0, true), "empty is fine when the column accepts NULL");
    }
}
