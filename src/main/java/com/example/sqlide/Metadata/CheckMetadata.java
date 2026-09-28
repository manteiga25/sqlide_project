package com.example.sqlide.Metadata;

/**
 * Uma restrição CHECK de uma tabela.
 *
 * <p>O SQLite não tem catálogo de restrições, por isso ali o nome pode vir vazio e a
 * expressão sai do texto do CREATE TABLE guardado no {@code sqlite_master}.</p>
 */
public class CheckMetadata {

    public String Name = "";
    public String table = "";
    public String expression = "";

    public CheckMetadata() {
    }

    public CheckMetadata(String name, String table, String expression) {
        this.Name = name;
        this.table = table;
        this.expression = expression;
    }

    @Override
    public String toString() {
        return (Name == null || Name.isBlank() ? "(unnamed)" : Name) + ": " + expression;
    }

}
