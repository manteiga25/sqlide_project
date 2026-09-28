package com.example.sqlide.drivers.model;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.model.QueryBuilder.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * O SQL que o {@link QueryBuilder} escreve para cada dialeto. Não precisa de base de dados:
 * compara texto.
 */
class QueryBuilderTest {

    private final QueryBuilder sqlite = QueryBuilder.of(SQLTypes.SQLITE);
    private final QueryBuilder mysql = QueryBuilder.of(SQLTypes.MYSQL);
    private final QueryBuilder postgres = QueryBuilder.of(SQLTypes.POSTGRESQL);

    private static ColumnMetadata column(String name, String type) {
        ColumnMetadata column = new ColumnMetadata();
        column.Name = name;
        column.Type = type;
        return column;
    }

    // ---------------------------------------------------------------- nomes e valores

    @Test
    void quotesIdentifiersPerDialect() {
        assertEquals("\"order\"", sqlite.quote("order"));
        assertEquals("`order`", mysql.quote("order"));
        assertEquals("[order]", QueryBuilder.of(SQLTypes.MS_ACCESS).quote("order"));
        assertEquals("\"a\"\"b\"", postgres.quote("a\"b"));
        assertEquals("`a``b`", mysql.quote("a`b"));
    }

    @Test
    void readableBuilderQuotesOnlyWhenNeeded() {
        QueryBuilder readable = sqlite.readable();
        assertEquals("name", readable.name("name"));
        assertEquals("\"first name\"", readable.name("first name"));
        assertEquals("\"order\"", readable.name("order"));
        // No PostgreSQL um nome com maiúsculas sem aspas passava a minúsculas.
        assertEquals("\"Clients\"", postgres.readable().name("Clients"));
        assertEquals("clients", postgres.readable().name("clients"));
    }

    @Test
    void rowIdentifiersAreNeverQuoted() {
        assertEquals("ROWID", sqlite.name("ROWID"));
        assertEquals("CTID", postgres.name("CTID"));
    }

    @Test
    void literalsEscapeQuotesAndMySqlBackslashes() {
        assertEquals("'O''Neill'", sqlite.literal("O'Neill"));
        assertEquals("'C:\\\\temp'", mysql.literal("C:\\temp"));
        assertEquals("'C:\\temp'", postgres.literal("C:\\temp"));
        assertEquals("NULL", sqlite.literal(null));
    }

    @Test
    void defaultValuesAreQuotedOnlyWhenTheyAreText() {
        assertEquals("'abc'", sqlite.defaultValue("abc"));
        assertEquals("'abc'", sqlite.defaultValue("'abc'"));
        assertEquals("10", sqlite.defaultValue("10"));
        assertEquals("-2.5", mysql.defaultValue("-2.5"));
        assertEquals("CURRENT_TIMESTAMP", sqlite.defaultValue("current_timestamp"));
        assertEquals("NULL", postgres.defaultValue("null"));
        assertEquals("'abc'::character varying", postgres.defaultValue("'abc'::character varying"));
        assertNull(sqlite.defaultValue(""));
        assertNull(sqlite.defaultValue(null));
        // Funções: o SQLite exige parênteses, o MySQL também exceto nas de data.
        assertEquals("(datetime('now'))", sqlite.defaultValue("datetime('now')"));
        assertEquals("(uuid())", mysql.defaultValue("uuid()"));
        assertEquals("now()", mysql.defaultValue("now()"));
        assertEquals("now()", postgres.defaultValue("now()"));
        // Texto com parênteses não é uma função.
        assertEquals("'John (admin)'", sqlite.defaultValue("John (admin)"));
    }

    @Test
    void stripsTrailingSemicolonsAndSplitsLists() {
        assertEquals("SELECT 1", QueryBuilder.stripTerminator("  SELECT 1 ; ; "));
        assertEquals(List.of("a", "'b, c'", "d"), QueryBuilder.splitList("a, 'b, c' ,d"));
        assertTrue(QueryBuilder.splitList("  ").isEmpty());
        // O apóstrofo no meio de uma palavra não abre texto entre plicas.
        assertEquals(List.of("O'Neill", "'a, b'"), QueryBuilder.splitList("O'Neill, 'a, b'"));
        assertEquals(List.of("'it''s, ok'", "x"), QueryBuilder.splitList("'it''s, ok', x"));
        assertEquals("it's, ok", QueryBuilder.unquote("'it''s, ok'"));
        assertEquals("O'Neill", QueryBuilder.unquote("O'Neill"));
    }

    // ---------------------------------------------------------------- tipos e colunas

    @Test
    void typesCarryLengthPrecisionAndValues() {
        ColumnMetadata name = column("name", "VARCHAR");
        name.size = 50;
        assertEquals("VARCHAR(50)", sqlite.columnType(name));

        ColumnMetadata count = column("count", "INTEGER");
        count.size = 10; // precisão que o catálogo devolve e que não vai para o SQL
        assertEquals("INTEGER", sqlite.columnType(count));

        // integerDigits são os dígitos antes da vírgula: DECIMAL(8+2, 2).
        ColumnMetadata price = column("price", "DECIMAL");
        price.integerDigits = 8;
        price.decimalDigits = 2;
        assertEquals("DECIMAL(10, 2)", mysql.columnType(price));

        ColumnMetadata state = column("state", "ENUM");
        state.items = new ArrayList<>(List.of("new", "it's done"));
        assertEquals("ENUM('new', 'it''s done')", mysql.columnType(state));

        state.aliasType = "State";
        assertEquals("\"State\"", postgres.columnType(state));

        ColumnMetadata text = column("notes", "TEXT");
        text.size = 2_147_483_647; // "sem limite" no catálogo do PostgreSQL
        assertEquals("TEXT", postgres.columnType(text));

        assertEquals("TINYINT(1)", mysql.columnType(column("flag", "TINYINT(1)")));
    }

    @Test
    void mysqlEnumWithoutValuesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> mysql.columnType(column("state", "ENUM")));
    }

    @Test
    void autoIncrementPerDialect() {
        ColumnMetadata id = column("id", "INT");
        id.IsPrimaryKey = true;
        id.autoincrement = 1;
        assertEquals("\"id\" INTEGER PRIMARY KEY AUTOINCREMENT", sqlite.columnDefinition(id, true, false));
        assertEquals("`id` INT AUTO_INCREMENT PRIMARY KEY NOT NULL", mysql.columnDefinition(id, true, false));
        assertEquals("\"id\" INT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY", postgres.columnDefinition(id, true, false));
    }

    @Test
    void columnDefinitionHasConstraintsInOrder() {
        ColumnMetadata email = column("email", "VARCHAR");
        email.size = 120;
        email.NOT_NULL = true;
        email.defaultValue = "none";
        email.isUnique = true;
        email.check = "length(email) > 3";
        assertEquals("\"email\" VARCHAR(120) NOT NULL DEFAULT 'none' UNIQUE CHECK (length(email) > 3)",
                sqlite.columnDefinition(email, false, false));

        email.comment = "contact";
        assertTrue(mysql.columnDefinition(email, false, false).endsWith("COMMENT 'contact'"));
    }

    // ---------------------------------------------------------------- CREATE TABLE

    @Test
    void compositePrimaryKeyGoesToTheEnd() {
        ColumnMetadata order = column("order_id", "INTEGER");
        order.IsPrimaryKey = true;
        ColumnMetadata product = column("product_id", "INTEGER");
        product.IsPrimaryKey = true;
        product.foreign.isForeign = true;
        product.foreign.tableRef = "product";
        product.foreign.columnRef = "id";
        product.foreign.onEliminate = "CASCADE";

        String sql = sqlite.createTable("order line").columns(List.of(order, product)).build();
        assertEquals("CREATE TABLE \"order line\" (\"order_id\" INTEGER, \"product_id\" INTEGER, "
                + "PRIMARY KEY (\"order_id\", \"product_id\"), "
                + "FOREIGN KEY (\"product_id\") REFERENCES \"product\" (\"id\") ON DELETE CASCADE)", sql);

        String inMysql = mysql.createTable("line").columns(List.of(order, product)).build();
        assertTrue(inMysql.contains("CONSTRAINT `fk_line_product_id` FOREIGN KEY (`product_id`)"), inMysql);
    }

    @Test
    void primaryKeyOrderCanDifferFromColumnOrder() {
        ColumnMetadata a = column("a", "TEXT");
        a.IsPrimaryKey = true;
        ColumnMetadata b = column("b", "TEXT");
        b.IsPrimaryKey = true;
        String sql = sqlite.createTable("t").columns(List.of(a, b)).primaryKeyOrder(List.of("b", "a")).build();
        assertTrue(sql.contains("PRIMARY KEY (\"b\", \"a\")"), sql);
    }

    @Test
    void sqliteOnlyOptions() {
        ColumnMetadata id = column("id", "TEXT");
        id.IsPrimaryKey = true;
        assertEquals("CREATE TEMPORARY TABLE \"t\" (\"id\" TEXT PRIMARY KEY) WITHOUT ROWID",
                sqlite.createTable("t").temporary(true).withoutRowId(true).column(id).build());
        assertEquals("CREATE TABLE `t` (`id` TEXT PRIMARY KEY NOT NULL)",
                mysql.createTable("t").withoutRowId(true).column(id).build());
    }

    @Test
    void mysqlRejectsSetDefaultForeignAction() {
        ColumnMetadata fk = column("c", "INT");
        fk.foreign.isForeign = true;
        fk.foreign.tableRef = "p";
        fk.foreign.columnRef = "id";
        fk.foreign.onUpdate = "SET DEFAULT";
        assertThrows(IllegalArgumentException.class, () -> mysql.references(fk));
        assertEquals("REFERENCES \"p\" (\"id\") ON UPDATE SET DEFAULT", sqlite.references(fk));
    }

    // ---------------------------------------------------------------- SELECT e condições

    @Test
    void selectPutsWhereBeforeOrderBy() {
        String sql = sqlite.readable().select("*").from("people")
                .where(sqlite.condition().and(sqlite.comparison("age", Operator.GREATER, "18")))
                .orderBy("name", Order.DESC)
                .limit(250).offset(500)
                .build();
        assertEquals("SELECT * FROM people WHERE age > 18 ORDER BY name DESC LIMIT 250 OFFSET 500", sql);
    }

    @Test
    void joinNeedsOnUnlessCrossOrNatural() {
        QueryBuilder readable = sqlite.readable();
        String sql = readable.select("*").from("orders")
                .join(Join.LEFT, "client", readable.qualified("orders", "client_id"), readable.qualified("client", "id"))
                .build();
        assertEquals("SELECT * FROM orders LEFT JOIN client ON orders.client_id = client.id", sql);

        assertEquals("SELECT * FROM a CROSS JOIN b", readable.select("*").from("a").join(Join.CROSS, "b", null, null).build());
        assertThrows(IllegalArgumentException.class, () -> readable.select("*").from("a").join(Join.INNER, "b", null, null));
        assertThrows(IllegalArgumentException.class, () -> mysql.select("*").from("a").join(Join.FULL, "b", "a.x", "b.x"));
    }

    @Test
    void comparisonsByOperandCount() {
        assertEquals("name IS NULL", sqlite.comparison("name", Operator.IS_NULL));
        assertEquals("age BETWEEN 1 AND 5", sqlite.comparison("age", Operator.BETWEEN, "1", "5"));
        assertEquals("city IN ('Lisboa', 'Porto')", sqlite.comparison("city", Operator.IN, "'Lisboa'", "'Porto'"));
        assertEquals("id IN (SELECT id FROM x)", sqlite.comparison("id", Operator.IN, "(SELECT id FROM x)"));
        assertEquals("NOT EXISTS (SELECT 1 FROM x)", sqlite.comparison(null, Operator.NOT_EXISTS, "SELECT 1 FROM x"));
        assertThrows(IllegalArgumentException.class, () -> sqlite.comparison("age", Operator.BETWEEN, "1"));
        assertThrows(IllegalArgumentException.class, () -> sqlite.comparison(null, Operator.EQUAL, "1"));
        assertEquals(Operator.NOT_EQUAL, Operator.fromSymbol("!="));
    }

    @Test
    void conditionsKeepTheirConnectorsAndGroups() {
        Condition group = new Condition().and("city = 'Lisboa'").or("city = 'Porto'");
        Condition where = new Condition().and("age > 18").add(Logic.AND, group).add(Logic.OR, "vip = 1");
        assertEquals("age > 18 AND (city = 'Lisboa' OR city = 'Porto') OR vip = 1", where.toString());
        // Um grupo com uma só condição não leva parênteses; um vazio é ignorado.
        assertEquals("a = 1", new Condition().add(Logic.OR, new Condition().and("a = 1")).add(Logic.AND, new Condition()).toString());
    }

    // ---------------------------------------------------------------- escrita de linhas

    @Test
    void insertUpdateAndDeleteUseParameters() {
        assertEquals("INSERT INTO \"t\" (\"a\", \"b\") VALUES (?, ?)", sqlite.insert("t").columns(List.of("a", "b")).build());
        assertEquals("INSERT INTO `t` () VALUES ()", mysql.insert("t").build());
        assertEquals("UPDATE \"t\" SET \"a\" = ? WHERE \"k1\" = ? AND \"k2\" = ?",
                sqlite.update("t").set("a").where(List.of("k1", "k2")).build());
        assertEquals("UPDATE \"t\" SET \"a\" = ? WHERE CTID = ? RETURNING ctid",
                postgres.update("t").set("a").where("CTID").returning("ctid").build());
        assertThrows(IllegalStateException.class, () -> sqlite.update("t").set("a").build());

        assertEquals("DELETE FROM \"t\" WHERE ROWID IN (?, ?, ?)", sqlite.delete("t").whereKeys(List.of("ROWID"), 3).build());
        assertEquals("DELETE FROM \"t\" WHERE (\"a\" = ? AND \"b\" = ?) OR (\"a\" = ? AND \"b\" = ?)",
                sqlite.delete("t").whereKeys(List.of("a", "b"), 2).build());
        assertThrows(IllegalStateException.class, () -> sqlite.delete("t").build());
        assertEquals("DELETE FROM \"t\"", sqlite.delete("t").allRows().build());
    }

    @Test
    void copyingRowsAndFillingFromAnotherTable() {
        assertEquals("INSERT INTO \"new_t\" (\"a\", \"b\") SELECT \"a\", \"b\" FROM \"t\"",
                sqlite.insert("new_t").columns(List.of("a", "b")).fromTable("t").build());
        assertEquals("UPDATE \"c\" SET \"p_id\" = (SELECT p.\"id\" FROM \"p\" p WHERE p.ROWID = \"c\".ROWID)",
                sqlite.updateFromParent("c", "p_id", "ROWID", "p", "id", "ROWID"));
        assertEquals("UPDATE `c` c JOIN `p` p ON p.`id` = c.`id` SET c.`p_id` = p.`code`",
                mysql.updateFromParent("c", "p_id", "id", "p", "code", "id"));
        assertEquals("UPDATE \"c\" c SET \"p_id\" = p.\"code\" FROM \"p\" p WHERE p.\"id\" = c.\"id\"",
                postgres.updateFromParent("c", "p_id", "id", "p", "code", "id"));
    }

    @Test
    void countAndPaginateUserQueries() {
        assertEquals("SELECT COUNT(*) FROM (SELECT * FROM t WHERE a = ';') AS counted",
                sqlite.countQuery("SELECT * FROM t WHERE a = ';';"));
        assertEquals("SELECT * FROM t LIMIT 10 OFFSET 20", sqlite.paginate("SELECT * FROM t;", 10, 20));
    }

    // ---------------------------------------------------------------- índices e ALTER

    @Test
    void indexesPerDialect() {
        assertEquals("CREATE INDEX \"idx_t_a\" ON \"t\" (\"a\")", sqlite.createIndex(null, "t", List.of("a"), null));
        assertEquals("CREATE UNIQUE INDEX \"i\" ON \"t\" (\"a\", \"b\")", sqlite.createIndex("i", "t", List.of("a", "b"), "UNIQUE"));
        assertEquals("CREATE FULLTEXT INDEX `i` ON `t` (`a`)", mysql.createIndex("i", "t", List.of("a"), "FULLTEXT"));
        assertEquals("CREATE INDEX \"i\" ON \"t\" USING gin (\"a\")", postgres.createIndex("i", "t", List.of("a"), "gin"));
        assertThrows(IllegalArgumentException.class, () -> sqlite.createIndex("i", "t", List.of("a"), "gin"));
        assertEquals("DROP INDEX `i` ON `t`", mysql.dropIndex("i", "t"));
        assertEquals("DROP INDEX \"i\"", postgres.dropIndex("i", "t"));
    }

    @Test
    void renameTablePerDialect() {
        assertEquals(List.of("RENAME TABLE `a` TO `b`"), mysql.alterTable("a").renameTo("b").build());
        assertEquals(List.of("ALTER TABLE \"a\" RENAME TO \"b\""), sqlite.alterTable("a").renameTo("b").build());
    }

    @Test
    void sizeNoiseIsNotATypeChange() {
        ColumnMetadata before = column("count", "INTEGER");
        before.size = 10;
        ColumnMetadata after = before.copy();
        after.size = 0; // o formulário não mostra tamanho para INTEGER
        assertTrue(ColumnChange.between(before, after).isEmpty());

        after.Type = "BIGINT";
        assertEquals(EnumSet.of(ColumnChange.TYPE), ColumnChange.between(before, after));
    }

    @Test
    void mysqlRedefinesTheColumnInOneAlter() {
        ColumnMetadata before = column("name", "VARCHAR");
        before.size = 20;
        before.isUnique = true;
        ColumnMetadata after = before.copy();
        after.Name = "full_name";
        after.size = 80;
        after.NOT_NULL = true;
        after.IsPrimaryKey = true;
        after.isUnique = false;

        List<String> sql = mysql.alterColumn("people", before, after,
                new Constraints("PRIMARY", List.of("id"), "name", null, null));
        assertEquals(List.of("ALTER TABLE `people` DROP INDEX `name`, "
                + "CHANGE COLUMN `name` `full_name` VARCHAR(80) NOT NULL, "
                + "DROP PRIMARY KEY, ADD PRIMARY KEY (`id`, `full_name`)"), sql);
    }

    @Test
    void mysqlForeignKeysAreDroppedBeforeAndAddedAfter() {
        ColumnMetadata before = column("client", "INT");
        ColumnMetadata after = before.copy();
        after.Type = "BIGINT";
        after.foreign.isForeign = true;
        after.foreign.tableRef = "client";
        after.foreign.columnRef = "id";
        before.foreign = after.foreign.copy();
        before.foreign.tableRef = "old_client";

        List<String> sql = mysql.alterColumn("orders", before, after, new Constraints(null, List.of(), null, "fk_old", null));
        assertEquals(3, sql.size(), sql.toString());
        assertEquals("ALTER TABLE `orders` DROP FOREIGN KEY `fk_old`", sql.get(0));
        assertEquals("ALTER TABLE `orders` CHANGE COLUMN `client` `client` BIGINT", sql.get(1));
        assertTrue(sql.get(2).startsWith("ALTER TABLE `orders` ADD CONSTRAINT `fk_orders_client` FOREIGN KEY"), sql.get(2));
    }

    @Test
    void postgresChangesEachThingWithItsOwnCommand() {
        ColumnMetadata before = column("price", "NUMERIC");
        before.integerDigits = 6;
        before.decimalDigits = 2;
        before.defaultValue = "0";
        ColumnMetadata after = before.copy();
        after.Name = "unit_price";
        after.integerDigits = 8;
        after.NOT_NULL = true;
        after.defaultValue = "";
        after.comment = "per unit";

        List<String> sql = postgres.alterColumn("item", before, after, new Constraints("item_pkey", List.of("id"), null, null, null));
        assertEquals(List.of(
                "ALTER TABLE \"item\" RENAME COLUMN \"price\" TO \"unit_price\"",
                "ALTER TABLE \"item\" ALTER COLUMN \"unit_price\" TYPE NUMERIC(10, 2) USING \"unit_price\"::NUMERIC(10, 2)",
                "ALTER TABLE \"item\" ALTER COLUMN \"unit_price\" SET NOT NULL",
                "ALTER TABLE \"item\" ALTER COLUMN \"unit_price\" DROP DEFAULT",
                "COMMENT ON COLUMN \"item\".\"unit_price\" IS 'per unit'"), sql);
    }

    @Test
    void postgresIdentityRestartsAfterTheExistingRows() {
        ColumnMetadata before = column("id", "INTEGER");
        ColumnMetadata after = before.copy();
        after.autoincrement = 1;
        List<String> sql = postgres.alterColumn("t", before, after, new Constraints(null, List.of(), null, null, null));
        assertTrue(sql.contains("ALTER TABLE \"t\" ALTER COLUMN \"id\" ADD GENERATED BY DEFAULT AS IDENTITY"), sql.toString());
        assertTrue(sql.getLast().startsWith("SELECT setval(pg_get_serial_sequence('\"t\"', 'id')"), sql.getLast());
    }

    @Test
    void sqliteDoesNotPlanAlterColumns() {
        ColumnMetadata before = column("a", "TEXT");
        ColumnMetadata after = before.copy();
        after.NOT_NULL = true;
        assertThrows(UnsupportedOperationException.class,
                () -> sqlite.alterColumn("t", before, after, new Constraints(null, List.of(), null, null, null)));
    }
}
