package com.example.sqlide.drivers;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.drivers.MySQL.MySQLDB;
import com.example.sqlide.drivers.PostegreSQL.PostreSQLDB;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.SQLTypes;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Os drivers do MySQL e do PostgreSQL contra um servidor de verdade.
 *
 * <p>Só correm quando as variáveis de ambiente apontam para uma base de dados de testes
 * (as tabelas são criadas com o prefixo {@code sqlide_test_} e apagadas no fim):</p>
 * <pre>
 * SQLIDE_MYSQL_URL=localhost:3306/   SQLIDE_MYSQL_DATABASE=sqlide_test
 * SQLIDE_MYSQL_USER=...              SQLIDE_MYSQL_PASSWORD=...
 * SQLIDE_PG_URL=localhost:5432/      SQLIDE_PG_DATABASE=sqlide_test
 * SQLIDE_PG_USER=...                 SQLIDE_PG_PASSWORD=...
 * </pre>
 * <p>Sem elas os testes ficam como "skipped".</p>
 */
class ServerDriversTest {

    private static final String PARENT = "sqlide_test_parent";
    private static final String CHILD = "sqlide_test_child";
    private static final String RENAMED = "sqlide_test_child_renamed";
    private static final String ENUM_TYPE = "sqlide_test_status";

    @Test
    void mysql() throws Exception {
        final String url = System.getenv("SQLIDE_MYSQL_URL");
        assumeTrue(url != null && !url.isBlank(), "SQLIDE_MYSQL_URL not set");
        final MySQLDB db = new MySQLDB();
        assertTrue(db.connect(url, System.getenv("SQLIDE_MYSQL_DATABASE"), System.getenv("SQLIDE_MYSQL_USER"),
                Objects.requireNonNullElse(System.getenv("SQLIDE_MYSQL_PASSWORD"), ""), false), db.GetException());
        scenario(db);
    }

    @Test
    void postgres() throws Exception {
        final String url = System.getenv("SQLIDE_PG_URL");
        assumeTrue(url != null && !url.isBlank(), "SQLIDE_PG_URL not set");
        final PostreSQLDB db = new PostreSQLDB();
        assertTrue(db.connect(url, System.getenv("SQLIDE_PG_DATABASE"), System.getenv("SQLIDE_PG_USER"),
                Objects.requireNonNullElse(System.getenv("SQLIDE_PG_PASSWORD"), ""), false), db.GetException());
        scenario(db);
    }

    private static ColumnMetadata column(String name, String type) {
        ColumnMetadata column = new ColumnMetadata();
        column.Name = name;
        column.Type = type;
        return column;
    }

    private static ColumnMetadata read(DataBase db, String table, String name) {
        return db.getColumnsMetadata(table).stream().filter(c -> c.Name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError("no column " + name + " in " + table));
    }

    private static void quietly(DataBase db, String sql) {
        try (Statement stmt = db.getConnection().createStatement()) {
            stmt.execute(sql);
        } catch (SQLException ignored) {
        }
    }

    private static void cleanUp(DataBase db) {
        for (String table : List.of(RENAMED, CHILD, PARENT)) quietly(db, "DROP TABLE IF EXISTS " + db.builder().quote(table));
        if (db.getSQLType() == SQLTypes.POSTGRESQL) quietly(db, "DROP TYPE IF EXISTS " + db.builder().quote(ENUM_TYPE));
    }

    private static void scenario(DataBase db) throws Exception {
        final boolean postgres = db.getSQLType() == SQLTypes.POSTGRESQL;
        cleanUp(db);
        try {
            // ---- criar as tabelas pelo caminho da janela "Create table"
            ColumnMetadata parentId = column("id", "INTEGER");
            parentId.IsPrimaryKey = true;
            parentId.autoincrement = 1;
            TableMetadata parent = new TableMetadata(PARENT);
            parent.addColumns(List.of(parentId, column("label", "TEXT")));
            assertTrue(db.createTable(parent, false, true), db.GetException());

            ColumnMetadata id = column("id", "INTEGER");
            id.IsPrimaryKey = true;
            ColumnMetadata parentRef = column("parent_id", "INTEGER");
            parentRef.foreign.isForeign = true;
            parentRef.foreign.tableRef = PARENT;
            parentRef.foreign.columnRef = "id";
            parentRef.foreign.onEliminate = "CASCADE";
            ColumnMetadata name = column("name", "VARCHAR");
            name.size = 30;
            name.NOT_NULL = true;
            name.defaultValue = "x";
            name.index = "idx_sqlide_test_name";
            ColumnMetadata price = column("price", "DECIMAL");
            price.integerDigits = 8;
            price.decimalDigits = 2;
            ColumnMetadata status = column("status", "ENUM");
            status.items = new ArrayList<>(List.of("new", "it's done"));
            if (postgres) status.aliasType = ENUM_TYPE;
            TableMetadata child = new TableMetadata(CHILD);
            child.addColumns(List.of(id, parentRef, name, price, status));
            assertTrue(db.createTable(child, false, true), db.GetException());

            // ---- metadados lidos de volta
            assertEquals(1, read(db, PARENT, "id").autoincrement);
            ColumnMetadata readName = read(db, CHILD, "name");
            assertTrue(readName.NOT_NULL, "NOT NULL used to come back inverted");
            assertEquals(30, readName.size);
            assertEquals("idx_sqlide_test_name", readName.index);
            assertFalse(read(db, CHILD, "price").NOT_NULL);
            assertEquals(8, read(db, CHILD, "price").integerDigits);
            assertEquals(2, read(db, CHILD, "price").decimalDigits);
            assertTrue(read(db, CHILD, "parent_id").foreign.isForeign);
            assertEquals("CASCADE", read(db, CHILD, "parent_id").foreign.onEliminate);
            assertEquals(List.of("new", "it's done"), read(db, CHILD, "status").items);

            // ---- linhas: inserir com apóstrofo, editar pela chave, apagar pela chave
            assertTrue(db.Inserter().insertData(PARENT, new HashMap<>(Map.of("label", "O'Neill"))), db.GetException());
            final String parentKey = db.Fetcher().fetchRawDataMap("SELECT id FROM " + db.builder().quote(PARENT)).getFirst().get("id");
            final HashMap<String, String> row = new HashMap<>(Map.of("id", "1", "parent_id", parentKey, "name", "a", "status", "new"));
            assertTrue(db.Inserter().insertData(CHILD, row), db.GetException());
            row.put("id", "2");
            assertTrue(db.Inserter().insertData(CHILD, row), db.GetException());
            assertTrue(db.Updater().updateData(CHILD, "name", "it's b", new String[]{null}, "VARCHAR",
                    new ArrayList<>(List.of("id")), new ArrayList<>(List.of("2"))), db.GetException());
            assertFalse(db.Updater().updateData(CHILD, "name", "nope", new String[]{null}, "VARCHAR",
                    new ArrayList<>(List.of("id")), new ArrayList<>(List.of("99"))));
            assertTrue(db.deleteRows(CHILD, List.of("id"), List.of(List.of("1"))), db.GetException());
            assertEquals(1, db.Fetcher().fetchRawDataMap("SELECT * FROM " + db.builder().quote(CHILD)).size());

            // ---- mudar uma coluna: nome, tamanho, NOT NULL e DEFAULT de uma vez
            ColumnMetadata before = read(db, CHILD, "name");
            ColumnMetadata after = before.copy();
            after.Name = "full_name";
            after.size = 60;
            after.NOT_NULL = false;
            after.defaultValue = "y";
            assertTrue(db.alterColumn(CHILD, before, after), db.GetException());
            ColumnMetadata changed = read(db, CHILD, "full_name");
            assertEquals(60, changed.size);
            assertFalse(changed.NOT_NULL);
            assertTrue(changed.defaultValue.contains("y"), changed.defaultValue);
            assertEquals("idx_sqlide_test_name", changed.index, "the index follows the renamed column");

            // ---- UNIQUE, ação da chave estrangeira e índice
            before = read(db, CHILD, "full_name");
            after = before.copy();
            after.isUnique = true;
            after.index = null;
            assertTrue(db.alterColumn(CHILD, before, after), db.GetException());
            assertTrue(read(db, CHILD, "full_name").isUnique);
            assertNull(read(db, CHILD, "full_name").index);

            before = read(db, CHILD, "parent_id");
            after = before.copy();
            after.foreign.onEliminate = "SET NULL";
            assertTrue(db.alterColumn(CHILD, before, after), db.GetException());
            assertEquals("SET NULL", read(db, CHILD, "parent_id").foreign.onEliminate);

            // ---- coluna de chave numa tabela que já tem chave: a chave passa a composta
            ColumnMetadata extra = column("line", "INTEGER");
            extra.IsPrimaryKey = true;
            extra.NOT_NULL = true;
            extra.defaultValue = "0";
            assertTrue(db.createColumn(CHILD, "line", extra, false), db.GetException());
            assertEquals(List.of("id", "line"), db.PrimaryKeyList(CHILD));

            // ---- apagar uma coluna presa a uma chave estrangeira
            assertTrue(db.deleteColumn(db.getColumnsMetadata(CHILD), "parent_id", CHILD), db.GetException());
            assertFalse(db.getColumnsName(CHILD).contains("parent_id"));

            // ---- páginas de uma consulta e mudar o nome da tabela
            assertEquals(1, db.totalPagesOfQuery("SELECT * FROM " + db.builder().quote(CHILD) + " ORDER BY id;"));
            assertTrue(db.renameTable(CHILD, RENAMED), db.GetException());
            assertTrue(db.getTables().contains(RENAMED));
        } finally {
            cleanUp(db);
            db.disconnect();
        }
    }
}
