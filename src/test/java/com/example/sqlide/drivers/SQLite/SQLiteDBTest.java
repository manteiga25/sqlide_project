package com.example.sqlide.drivers.SQLite;

import com.example.sqlide.DataForDB;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O driver do SQLite contra um ficheiro de verdade. Cobre o que estava partido: a
 * reconstrução da tabela, o NOT NULL invertido, apagar linhas pela chave e mudar colunas.
 */
class SQLiteDBTest {

    @TempDir
    Path folder;

    private SQLiteDB db;

    @BeforeEach
    void open() {
        db = new SQLiteDB();
        assertTrue(db.connect(folder.resolve("test.db").toString()), db.GetException());
    }

    @AfterEach
    void close() throws SQLException {
        db.disconnect();
    }

    private void sql(String... commands) throws SQLException {
        try (Statement stmt = db.getConnection().createStatement()) {
            for (String command : commands) stmt.execute(command);
        }
    }

    private List<List<String>> rows(String query) throws SQLException {
        List<List<String>> rows = new ArrayList<>();
        try (Statement stmt = db.getConnection().createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= columns; i++) row.add(rs.getString(i));
                rows.add(row);
            }
        }
        return rows;
    }

    private String single(String query) throws SQLException {
        List<List<String>> rows = rows(query);
        return rows.isEmpty() ? null : rows.getFirst().getFirst();
    }

    private ColumnMetadata column(String table, String name) {
        return db.getColumnsMetadata(table).stream().filter(c -> c.Name.equals(name)).findFirst().orElseThrow();
    }

    private static ColumnMetadata meta(String name, String type) {
        ColumnMetadata column = new ColumnMetadata();
        column.Name = name;
        column.Type = type;
        return column;
    }

    // ------------------------------------------------------------------------------------

    @Test
    void createTableReadsBackTheRightMetadata() {
        ColumnMetadata id = meta("id", "INTEGER");
        id.IsPrimaryKey = true;
        id.autoincrement = 1;
        ColumnMetadata name = meta("name", "VARCHAR");
        name.size = 50;
        name.NOT_NULL = true;
        name.index = "idx_client_name";
        ColumnMetadata email = meta("email", "TEXT");
        email.isUnique = true;
        ColumnMetadata credit = meta("credit", "DECIMAL");
        credit.integerDigits = 8;
        credit.decimalDigits = 2;
        credit.defaultValue = "0";

        TableMetadata table = new TableMetadata("client");
        table.addColumns(List.of(id, name, email, credit));
        assertTrue(db.createTable(table, false, true), db.GetException());

        ColumnMetadata readId = column("client", "id");
        assertTrue(readId.IsPrimaryKey);
        assertEquals(1, readId.autoincrement);

        ColumnMetadata readName = column("client", "name");
        assertTrue(readName.NOT_NULL, "NOT NULL used to come back inverted");
        assertEquals("VARCHAR", readName.Type);
        assertEquals(50, readName.size);
        assertEquals("idx_client_name", readName.index);

        ColumnMetadata readEmail = column("client", "email");
        assertFalse(readEmail.NOT_NULL);
        assertTrue(readEmail.isUnique);
        assertFalse(readName.isUnique, "unique used to leak from other columns");

        ColumnMetadata readCredit = column("client", "credit");
        assertEquals(8, readCredit.integerDigits);
        assertEquals(2, readCredit.decimalDigits);
        assertEquals("0", readCredit.defaultValue);
    }

    @Test
    void rebuildKeepsOtherTablesForeignKeysIndexesTriggersAndRows() throws SQLException {
        sql("CREATE TABLE parent (id INTEGER PRIMARY KEY, name TEXT)",
                "CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER REFERENCES parent(id))",
                "CREATE TABLE log (msg TEXT)",
                "CREATE INDEX idx_parent_name ON parent (name)",
                "CREATE TRIGGER parent_log AFTER INSERT ON parent BEGIN INSERT INTO log VALUES ('added ' || NEW.name); END",
                "INSERT INTO parent VALUES (1, 'a'), (2, 'b')",
                "INSERT INTO child VALUES (10, 1), (11, 2)",
                "PRAGMA foreign_keys = ON");

        ColumnMetadata code = meta("code", "TEXT");
        code.isUnique = true; // UNIQUE obriga a reconstruir
        assertTrue(db.createColumn("parent", "code", code, false), db.GetException());

        String childSql = single("SELECT sql FROM sqlite_master WHERE name = 'child'");
        assertTrue(childSql.contains("REFERENCES parent"), childSql);
        assertFalse(childSql.toLowerCase().contains("copy") || childSql.contains("new_parent"), childSql);

        assertEquals("idx_parent_name", single("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'idx_parent_name'"));
        assertEquals("parent_log", single("SELECT name FROM sqlite_master WHERE type = 'trigger'"));
        assertEquals(List.of(Arrays.asList("1", "a", null), Arrays.asList("2", "b", null)), rows("SELECT * FROM parent ORDER BY id"));
        assertEquals("1", single("PRAGMA foreign_keys"), "foreign keys must be switched back on");
        assertTrue(column("parent", "code").isUnique);
        assertTrue(column("child", "parent_id").foreign.isForeign);

        sql("INSERT INTO parent (id, name) VALUES (3, 'c')");
        assertEquals("added c", single("SELECT msg FROM log ORDER BY ROWID DESC LIMIT 1"), "the trigger must survive the rebuild");
    }

    @Test
    void alterColumnRenamesAndChangesTypeInOneGo() throws SQLException {
        sql("CREATE TABLE t (a INTEGER, b TEXT)",
                "CREATE INDEX idx_t_b ON t (b)",
                "INSERT INTO t VALUES (1, 'one'), (2, 'two')");

        ColumnMetadata before = column("t", "b");
        ColumnMetadata after = before.copy();
        after.Name = "label";
        after.Type = "VARCHAR";
        after.size = 20;
        after.NOT_NULL = true;
        after.defaultValue = "x";
        assertTrue(db.alterColumn("t", before, after), db.GetException());

        ColumnMetadata label = column("t", "label");
        assertTrue(label.NOT_NULL);
        assertEquals("'x'", label.defaultValue);
        assertEquals(20, label.size);
        assertEquals("idx_t_b", label.index, "the index follows the renamed column");
        assertEquals(List.of(List.of("1", "one"), List.of("2", "two")), rows("SELECT a, label FROM t ORDER BY a"));
    }

    @Test
    void alterColumnOnlyRenameDoesNotRebuild() throws SQLException {
        sql("CREATE TABLE t (a INTEGER, b TEXT)", "CREATE VIEW v AS SELECT b FROM t", "INSERT INTO t VALUES (1, 'x')");
        ColumnMetadata before = column("t", "b");
        ColumnMetadata after = before.copy();
        after.Name = "c";
        assertTrue(db.alterColumn("t", before, after), db.GetException());
        // O RENAME COLUMN do SQLite atualiza a vista.
        assertEquals("x", single("SELECT * FROM v"));
    }

    @Test
    void alterColumnAddsPrimaryKeyWithAutoincrement() throws SQLException {
        sql("CREATE TABLE t (id INTEGER, v TEXT)", "INSERT INTO t VALUES (1, 'a'), (2, 'b')");
        ColumnMetadata before = column("t", "id");
        ColumnMetadata after = before.copy();
        after.IsPrimaryKey = true;
        after.autoincrement = 1;
        assertTrue(db.alterColumn("t", before, after), db.GetException());

        sql("INSERT INTO t (v) VALUES ('c')");
        assertEquals("3", single("SELECT id FROM t WHERE v = 'c'"));
        assertTrue(column("t", "id").IsPrimaryKey);
    }

    @Test
    void alterColumnFailsCleanlyWhenExistingRowsBreakTheNewRule() throws SQLException {
        sql("CREATE TABLE t (a INTEGER, b TEXT)", "INSERT INTO t VALUES (1, NULL)");
        ColumnMetadata before = column("t", "b");
        ColumnMetadata after = before.copy();
        after.NOT_NULL = true;
        assertFalse(db.alterColumn("t", before, after));
        assertFalse(db.GetException().isBlank());
        // Nada mudou.
        assertFalse(column("t", "b").NOT_NULL);
        assertEquals("1", single("SELECT COUNT(*) FROM t"));
        assertNull(single("SELECT name FROM sqlite_master WHERE name LIKE 'new_%'"));
    }

    @Test
    void rebuildIsRefusedInsideAnOpenTransactionWithForeignKeysOn() throws SQLException {
        sql("CREATE TABLE t (a INTEGER, b TEXT)", "PRAGMA foreign_keys = ON");
        db.changeCommitMode(false);
        ColumnMetadata before = column("t", "b");
        ColumnMetadata after = before.copy();
        after.NOT_NULL = true;
        after.defaultValue = "x";
        assertFalse(db.alterColumn("t", before, after));
        assertTrue(db.GetException().contains("commit"));
        db.changeCommitMode(true);
    }

    @Test
    void autoincrementSequenceSurvivesTheRebuild() throws SQLException {
        sql("CREATE TABLE t (id INTEGER PRIMARY KEY AUTOINCREMENT, v TEXT)",
                "INSERT INTO t (v) VALUES ('a'), ('b'), ('c')",
                "DELETE FROM t WHERE id = 3");
        ColumnMetadata code = meta("code", "TEXT");
        code.isUnique = true;
        assertTrue(db.createColumn("t", "code", code, false), db.GetException());
        sql("INSERT INTO t (v) VALUES ('d')");
        assertEquals("4", single("SELECT id FROM t WHERE v = 'd'"), "id 3 was used before and must not come back");
    }

    @Test
    void createColumnSimpleUsesAlterTable() throws SQLException {
        sql("CREATE TABLE t (a INTEGER)", "INSERT INTO t VALUES (1)");
        ColumnMetadata note = meta("note", "TEXT");
        note.NOT_NULL = true;
        note.defaultValue = "none";
        assertTrue(db.createColumn("t", "note", note, false), db.GetException());
        assertEquals("none", single("SELECT note FROM t"));
    }

    @Test
    void fillForeignCopiesTheValueOfTheRowWithTheSameRowId() throws SQLException {
        // Tabelas 1:1: a linha N de uma corresponde à linha com o mesmo rowid na outra.
        sql("CREATE TABLE parent (id INTEGER PRIMARY KEY, code TEXT)", "INSERT INTO parent VALUES (1, 'A'), (2, 'B')",
                "CREATE TABLE child (v TEXT)", "INSERT INTO child VALUES ('x'), ('y'), ('z')");
        ColumnMetadata code = meta("parent_code", "TEXT");
        code.foreign.isForeign = true;
        code.foreign.tableRef = "parent";
        code.foreign.columnRef = "code";
        assertTrue(db.createColumn("child", "parent_code", code, true), db.GetException());
        assertEquals(List.of(List.of("x", "A"), List.of("y", "B"), Arrays.asList("z", null)),
                rows("SELECT v, parent_code FROM child ORDER BY ROWID"));
        assertTrue(column("child", "parent_code").foreign.isForeign);
    }

    @Test
    void deleteColumnWithIndexAndWithForeignKey() throws SQLException {
        sql("CREATE TABLE parent (id INTEGER PRIMARY KEY)",
                "CREATE TABLE t (a INTEGER, b TEXT, c TEXT, p INTEGER REFERENCES parent(id))",
                "CREATE INDEX idx_t_b ON t (b)",
                "INSERT INTO t VALUES (1, 'x', 'y', NULL)");

        ArrayList<ColumnMetadata> columns = db.getColumnsMetadata("t");
        assertTrue(db.deleteColumn(columns, "b", "t"), db.GetException());
        assertTrue(db.deleteColumn(db.getColumnsMetadata("t"), "p", "t"), db.GetException());

        assertEquals(List.of("a", "c"), db.getColumnsMetadata("t").stream().map(c -> c.Name).toList());
        assertEquals(List.of(List.of("1", "y")), rows("SELECT * FROM t"));
        assertNull(single("SELECT name FROM sqlite_master WHERE name = 'idx_t_b'"));
    }

    @Test
    void deleteRowsUsesTheWholeKey() throws SQLException {
        sql("CREATE TABLE coded (code TEXT PRIMARY KEY, v TEXT)", "INSERT INTO coded VALUES ('A', '1'), ('B', '2')",
                "CREATE TABLE pairs (a INT, b INT, v TEXT, PRIMARY KEY (a, b))",
                "INSERT INTO pairs VALUES (1, 1, 'x'), (1, 2, 'y'), (2, 1, 'z')",
                // INT (e não INTEGER) PRIMARY KEY não é o rowid: os valores não coincidem.
                "CREATE TABLE numbered (id INT PRIMARY KEY, v TEXT)", "INSERT INTO numbered VALUES (20, 'a'), (10, 'b')");

        assertTrue(db.deleteRows("coded", List.of("code"), List.of(List.of("A"))), db.GetException());
        assertEquals(List.of(List.of("B")), rows("SELECT code FROM coded"));

        assertTrue(db.deleteRows("pairs", List.of("a", "b"), List.of(List.of("1", "2"))), db.GetException());
        assertEquals(List.of(List.of("1", "1"), List.of("2", "1")), rows("SELECT a, b FROM pairs ORDER BY a, b"));

        assertTrue(db.deleteRows("numbered", List.of("id"), List.of(List.of("10"))), db.GetException());
        assertEquals(List.of(List.of("20")), rows("SELECT id FROM numbered"));

        assertFalse(db.deleteRows("coded", List.of("code"), List.of(List.of("B"), List.of("ZZZ"))));
        assertEquals(1, rows("SELECT * FROM coded").size(), "a partial match must not delete anything");
    }

    @Test
    void gridUpdatesUseTheKeyAndReportMissingRows() throws SQLException {
        sql("CREATE TABLE pairs (a INT, b INT, v TEXT, PRIMARY KEY (a, b))", "INSERT INTO pairs VALUES (1, 1, 'x'), (1, 2, 'y')",
                "CREATE TABLE loose (v TEXT)", "INSERT INTO loose VALUES ('old')");

        assertTrue(db.Updater().updateData("pairs", "v", "new", new String[]{null}, "TEXT",
                new ArrayList<>(List.of("a", "b")), new ArrayList<>(List.of("1", "2"))), db.GetException());
        assertEquals("new", single("SELECT v FROM pairs WHERE a = 1 AND b = 2"));
        assertEquals("x", single("SELECT v FROM pairs WHERE a = 1 AND b = 1"));

        assertFalse(db.Updater().updateData("pairs", "v", "nope", new String[]{null}, "TEXT",
                new ArrayList<>(List.of("a", "b")), new ArrayList<>(List.of("9", "9"))));
        assertTrue(db.GetException().contains("No row matched"));

        String rowId = single("SELECT ROWID FROM loose");
        assertTrue(db.Updater().updateData("loose", "v", "it's new", new String[]{rowId}, "TEXT",
                new ArrayList<>(), new ArrayList<>()), db.GetException());
        assertEquals("it's new", single("SELECT v FROM loose"));
    }

    @Test
    void insertDataKeepsApostrophes() throws SQLException {
        sql("CREATE TABLE people (name TEXT)");
        assertTrue(db.Inserter().insertData("people", new HashMap<>(Map.of("name", "O'Neill"))), db.GetException());
        assertEquals("O'Neill", single("SELECT name FROM people"));
    }

    @Test
    void fetchersReadByPositionAndKeepDuplicateColumns() throws SQLException {
        sql("CREATE TABLE parent (id INTEGER PRIMARY KEY, name TEXT)",
                "CREATE TABLE child (id INTEGER PRIMARY KEY, parent_id INTEGER)",
                "INSERT INTO parent VALUES (1, 'a')", "INSERT INTO child VALUES (7, 1)");

        ArrayList<String> columns = new ArrayList<>();
        ArrayList<DataForDB> data = db.Fetcher().fetchData("SELECT * FROM parent JOIN child ON child.parent_id = parent.id", columns, "");
        assertNotNull(data, db.GetException());
        assertEquals(4, columns.size(), columns.toString());
        assertEquals(new HashSet<>(columns).size(), columns.size(), "duplicate labels must be renamed");
        assertEquals("1", data.getFirst().GetData("id"));
        assertTrue(columns.contains("child.id") || columns.contains("id (3)"), columns.toString());

        sql("CREATE TABLE loose (v TEXT)", "INSERT INTO loose VALUES ('x')");
        ArrayList<DataForDB> rows = db.Fetcher().fetchData("loose", new ArrayList<>(List.of("v")), 0, new ArrayList<>());
        assertEquals("x", rows.getFirst().GetData("v"));
        assertNotNull(rows.getFirst().GetData("ROWID"));

        ArrayList<HashMap<String, String>> maps = db.Fetcher().fetchDataMap("SELECT v FROM loose;", new ArrayList<>(List.of("v")), 10, 0);
        assertEquals("x", maps.getFirst().get("v"));
    }

    @Test
    void executeScriptSplitsOnlyOnRealStatementEnds() throws Exception {
        Path script = folder.resolve("script.sql");
        Files.writeString(script, """
                -- comentário; com ponto e vírgula
                CREATE TABLE a (v TEXT);
                INSERT INTO a VALUES ('x;y');
                /* bloco; */ CREATE TABLE b (v TEXT);
                CREATE TRIGGER a_copy AFTER INSERT ON a BEGIN
                    INSERT INTO b VALUES (NEW.v);
                    INSERT INTO b VALUES (CASE WHEN NEW.v = 'z' THEN 'zz' ELSE 'other' END);
                END;
                INSERT INTO a VALUES ('z')
                """);
        db.executeScript(script.toString());
        assertEquals(List.of(List.of("x;y"), List.of("z")), rows("SELECT v FROM a"));
        assertEquals(List.of(List.of("z"), List.of("zz")), rows("SELECT v FROM b"));
    }

    @Test
    void pagesOfAUserQuery() throws SQLException {
        sql("CREATE TABLE n (v INTEGER)");
        try (Statement stmt = db.getConnection().createStatement()) {
            for (int i = 0; i < 600; i++) stmt.addBatch("INSERT INTO n VALUES (" + i + ")");
            stmt.executeBatch();
        }
        assertEquals(3, db.totalPagesOfQuery("SELECT * FROM n ORDER BY v;"));
        assertEquals(1, db.totalPagesOfQuery("SELECT * FROM n WHERE v < 10"));
    }

    @Test
    void renameTableUpdatesTheReferencesOfOtherTables() throws SQLException {
        sql("CREATE TABLE parent (id INTEGER PRIMARY KEY)", "CREATE TABLE child (p INTEGER REFERENCES parent(id))");
        assertTrue(db.renameTable("parent", "parents"), db.GetException());
        assertTrue(single("SELECT sql FROM sqlite_master WHERE name = 'child'").contains("\"parents\""));
    }

    @Test
    void checksAreAddedAndDroppedByRebuilding() throws SQLException {
        sql("CREATE TABLE t (v INTEGER)", "INSERT INTO t VALUES (5)");
        db.addCheck("t", "", "v > 0");
        assertEquals(List.of("v > 0"), db.getChecks("t").stream().map(c -> c.expression).toList());
        assertThrows(SQLException.class, () -> sql("INSERT INTO t VALUES (-1)"));

        db.dropCheck("t", "v > 0");
        assertTrue(db.getChecks("t").isEmpty());
        sql("INSERT INTO t VALUES (-1)");
    }

    @Test
    void defaultAndTypeHelpersGoThroughAlterColumn() throws SQLException {
        sql("CREATE TABLE t (v TEXT)");
        assertTrue(db.AlterDefaultValue("t", "v", "hello"), db.GetException());
        assertEquals("'hello'", column("t", "v").defaultValue);
        assertTrue(db.modifyColumnType("t", "v", "VARCHAR(10)"), db.GetException());
        assertEquals("VARCHAR", column("t", "v").Type);
        assertEquals(10, column("t", "v").size);
    }

    @Test
    void newTriggersAreCreated() throws SQLException {
        sql("CREATE TABLE t (v TEXT)", "CREATE TABLE log (v TEXT)");
        db.createTrigger("t_log", "CREATE TRIGGER t_log AFTER INSERT ON t BEGIN INSERT INTO log VALUES (NEW.v); END");
        assertEquals("", Objects.requireNonNullElse(db.GetException(), ""));
        sql("INSERT INTO t VALUES ('a')");
        assertEquals("a", single("SELECT v FROM log"));
    }
}
