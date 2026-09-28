package com.example.sqlide.drivers.SQLite;

import com.example.sqlide.Function.FunctionController;
import com.example.sqlide.Metadata.BuiltInRoutines;
import com.example.sqlide.Metadata.CheckMetadata;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.Metadata.RoutineMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.Procedure.ProcedureController;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Enum.*;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.*;
import com.example.sqlide.drivers.model.QueryBuilder;
import com.example.sqlide.drivers.model.QueryBuilder.ColumnChange;
import com.example.sqlide.drivers.model.SQLTypes;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Driver do SQLite.
 *
 * <p>O SQL passa todo pelo {@link QueryBuilder}. O que estava mal e mudou:</p>
 * <ul>
 *   <li><b>Reconstruir a tabela</b> (acrescentar uma chave primária ou estrangeira, mudar um
 *       tipo, apagar uma coluna presa a um índice): o {@code createSpecialColumn} começava por
 *       renomear a tabela para {@code XCopySpecial}. Desde o SQLite 3.26 esse RENAME também
 *       reescreve as chaves estrangeiras das <em>outras</em> tabelas, que passavam a apontar
 *       para a cópia — e a cópia era apagada no fim. Perdiam-se também os índices, os
 *       triggers, os CHECK, o AUTOINCREMENT e o tamanho dos tipos. Agora segue-se o
 *       procedimento da documentação do SQLite ({@link #rebuildTable}).</li>
 *   <li><b>NOT NULL invertido</b>: o {@code getColumnsMetadata} marcava como NOT NULL as
 *       colunas que aceitavam NULL e vice-versa. A leitura passou para os PRAGMA
 *       ({@link #readDefinition}), que dizem o tipo e o DEFAULT exatamente como foram
 *       declarados.</li>
 *   <li><b>UNIQUE</b>: uma coluna ficava marcada como única se <em>outra</em> coluna
 *       qualquer tivesse um índice não único.</li>
 *   <li><b>Apagar linhas</b>: {@code removeData} comparava os valores com o ROWID mesmo
 *       quando a grelha mandava os da chave primária — numa chave TEXT não apagava nada e
 *       numa chave INT (que não é a INTEGER PRIMARY KEY) apagava as linhas erradas.</li>
 *   <li><b>Triggers novos</b>: {@code createTrigger} fazia DROP TRIGGER antes de criar; se o
 *       trigger ainda não existia o DROP falhava e o CREATE nunca corria.</li>
 *   <li>{@code modifyColumnType} usava {@code MODIFY COLUMN}, que é sintaxe do MySQL.</li>
 * </ul>
 */
public class SQLiteDB extends DataBase {

    public SQLiteDB() {
        super.idType = "ROWID";
        super.databaseInfo = new SQLiteInfo();
        SQLType = SQLTypes.SQLITE;

        Updater(updater);
        Inserter(inserterInterface);

        Performance(performancePragmaInterface);
        Connection(connectionPragmaInterface);
        Debug(debugPragmaInterface);
        IO(ioPragmaInterface);
        Memory(memoryPragmaInterface);
        Schema(schemaPragmaInterface);

    }

    @Override
    public boolean connect(final String DBName, final Map<String, String> formatData) {
        try {
            driverUrl = "jdbc:sqlite:" + DBName + ".db";
            connection = DriverManager.getConnection(driverUrl);
            statement = connection.createStatement();
            FormatDBCreation(formatData);
            DatabaseMetaData meta = connection.getMetaData();
           // databaseName = fetchDatabaseName();
            databaseName = DBName;
            Url = meta.getURL();
            username = meta.getUserName();
            host = "localhost";
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    @Override
    public boolean connect(final String DBName) {
        try {
            super.Url = "jdbc:sqlite:" + DBName;
            connection = DriverManager.getConnection("jdbc:sqlite:" + DBName);
            statement = connection.createStatement();
            DatabaseMetaData meta = connection.getMetaData();
            databaseName = meta.getDatabaseProductName();
            Url = meta.getURL();
            username = meta.getUserName();
            host = "localhost";
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    private void FormatDBCreation(final Map<String, String> formatData) throws SQLException {
        final boolean script = formatData.containsKey("innit");
        String scriptPath = "";
        if (script) {
            scriptPath = formatData.get("innit");
            formatData.remove("innit");
        }
            for (final String feature : formatData.keySet()) {
                System.out.println(formatData.get(feature));
                statement.executeUpdate("PRAGMA " + feature + " = " + formatData.get(feature) + ";");
            }
           // statement.executeUpdate("PRAGMA foreign_keys = ON;");
            try {
                executeScript(scriptPath);
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
    }

    /**
     * Funções embutidas do SQLite mais as vistas do ficheiro.
     *
     * <p>O SQLite não guarda funções definidas pelo utilizador — as que existem são
     * registadas pela aplicação que abre a ligação — por isso o que há para descobrir
     * aqui são as vistas, que estão no {@code sqlite_master}.</p>
     */
    @Override
    public ArrayList<RoutineMetadata> getRoutines() {
        ArrayList<RoutineMetadata> routines = BuiltInRoutines.forDialect(SQLTypes.SQLITE);

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT name, sql FROM sqlite_master WHERE type = 'view' ORDER BY name")) {
            while (rs.next()) {
                routines.add(new RoutineMetadata(rs.getString("name"), RoutineMetadata.Kind.VIEW,
                        "Views", "", "", summarise(rs.getString("sql")), false));
            }
        } catch (SQLException e) {
            System.err.println("Could not list SQLite views: " + e.getMessage());
        }

        return routines;
    }

    /** Primeira linha do SQL da vista, para servir de descrição na lista. */
    private static String summarise(String sql) {
        if (sql == null || sql.isBlank()) return "View defined in this database.";
        String flat = sql.replaceAll("\\s+", " ").trim();
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "...";
    }

    @Override
    public HashMap<String, String> getTriggers() {
        final String sql = "SELECT name, sql FROM sqlite_master WHERE type = 'trigger';";
        HashMap<String, String> code = new HashMap<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                code.put(rs.getString("name"), rs.getString("sql"));
            }
            return code;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }

    }

    /** O SQLite não tem eventos agendados. */
    @Override
    public HashMap<String, String> getEvents() {
        return new HashMap<>();
    }

    @Override
    public void disconnect() throws SQLException {
        statement.close();
        connection.close();
    }

    @Override
    public boolean createTable(String table) {
        final ColumnMetadata id = new ColumnMetadata();
        id.Name = "id";
        id.Type = "INT";
        try {
            execute(builder().createTable(table).column(id).build());
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    protected boolean createSpecialColumn(String table, String column, ColumnMetadata meta) {
        return createSpecialColumn(table, column, meta, false);
    }

    @Override
    public boolean renameTable(final String Table, final String newTableName) {
        try {
            // Sem legacy_alter_table, o SQLite atualiza as chaves estrangeiras das outras
            // tabelas, os triggers e as vistas que falam desta tabela.
            for (final String sql : builder().alterTable(Table).renameTo(newTableName).build()) execute(sql);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteTable(String table) {
        try {
            execute(builder().dropTable(table));
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    /**
     * Acrescenta uma coluna. O ALTER TABLE ADD COLUMN do SQLite não aceita chave primária,
     * UNIQUE, NOT NULL sem DEFAULT, DEFAULT com expressão ou CURRENT_*, nem (com as chaves
     * estrangeiras ligadas) uma REFERENCES com DEFAULT não nulo — nesses casos a tabela é
     * reconstruída com a coluna nova.
     */
    @Override
    public boolean createColumn(String table, String column, ColumnMetadata meta, final boolean fill) {
        final ColumnMetadata added = meta.copy();
        added.Name = column;
        try {
            if (needsRebuildToAdd(added)) return createSpecialColumn(table, column, added, fill);
            runInTransaction(() -> {
                for (final String sql : builder().alterTable(table).addColumn(added, false).build()) execute(sql);
                if (fill && QueryBuilder.isForeign(added)) inserDataForeignTable(table, added);
            });
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    private boolean needsRebuildToAdd(final ColumnMetadata column) throws SQLException {
        if (column.IsPrimaryKey || column.isUnique || column.autoincrement >= 1) return true;
        final String defaultValue = builder().defaultValue(column.defaultValue);
        final boolean nullDefault = defaultValue == null || defaultValue.equalsIgnoreCase("NULL");
        if (column.NOT_NULL && nullDefault) return true;
        if (!nullDefault && (defaultValue.startsWith("(") || defaultValue.toUpperCase(Locale.ROOT).startsWith("CURRENT_"))) return true;
        return QueryBuilder.isForeign(column) && !nullDefault && foreignKeysOn();
    }

    /**
     * Coluna que o ALTER TABLE do SQLite não sabe acrescentar: a tabela é reconstruída já
     * com ela, e as linhas são copiadas.
     */
    protected boolean createSpecialColumn(String table, String column, ColumnMetadata meta, final boolean fillForeign) {
        final ColumnMetadata added = meta.copy();
        added.Name = column;
        try {
            rebuildTable(table, null, current -> {
                if (current.find(column) != null) throw new SQLException("Column " + column + " already exists in " + table + ".");
                final List<ColumnMetadata> columns = new ArrayList<>(current.columns);
                columns.add(added);
                final List<String> keys = new ArrayList<>(current.primaryKey);
                if (added.IsPrimaryKey) keys.add(column);
                return new RebuildPlan(columns, keys, current.checks, Set.of(), Set.of(column));
            }, fillForeign && QueryBuilder.isForeign(added) ? () -> inserDataForeignTable(table, added) : null);
            return true;
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /**
     * Apaga colunas reconstruindo a tabela só com {@code columns} (as que ficam).
     */
    protected boolean dropSpecialColumn(String table, ArrayList<ColumnMetadata> columns) {
        final Set<String> keep = new HashSet<>();
        for (final ColumnMetadata column : columns) keep.add(column.Name.toLowerCase(Locale.ROOT));
        try {
            rebuildTable(table, null, current -> {
                final List<ColumnMetadata> kept = new ArrayList<>();
                final Set<String> dropped = new HashSet<>();
                for (final ColumnMetadata column : current.columns) {
                    if (keep.contains(column.Name.toLowerCase(Locale.ROOT))) kept.add(column);
                    else dropped.add(column.Name);
                }
                if (kept.isEmpty()) throw new SQLException("A table needs at least one column.");
                final List<String> keys = current.primaryKey.stream().filter(k -> !dropped.contains(k)).toList();
                // Um CHECK sobre uma coluna que desaparece deixa de fazer sentido (e o CREATE falharia).
                final List<String> checks = current.checks.stream().filter(check -> dropped.stream().noneMatch(c -> mentions(check, c))).toList();
                return new RebuildPlan(kept, keys, checks, dropped, Set.of());
            }, null);
            return true;
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /**
     * Preenche a coluna estrangeira acabada de criar com a coluna referenciada da linha da
     * outra tabela que tem o mesmo rowid (tabelas 1:1). Linhas sem par ficam a NULL.
     *
     * <p>Antes comparava a chave primária da tabela filha com uma coluna com o mesmo nome na
     * tabela mãe, que normalmente não existe.</p>
     */
    private void inserDataForeignTable(final String tableForeign, final ColumnMetadata meta) throws SQLException {
        execute(builder().updateFromParent(tableForeign, meta.Name, getRowId(),
                meta.foreign.tableRef, meta.foreign.columnRef, getRowId()));
    }

    @Override
    public boolean renameColumn(String table, String column, String newColumn) {
        try {
            // RENAME COLUMN (SQLite 3.25+) atualiza índices, triggers e vistas.
            for (final String sql : builder().alterTable(table).renameColumn(column, newColumn).build()) execute(sql);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean modifyColumnType(String Table, String column, String Type) {
        return AlterTypeColumn(Table, column, Type);
    }

    /**
     * Apaga uma coluna. Primeiro tenta o DROP COLUMN do SQLite (3.35+), que é instantâneo mas
     * recusa colunas que sejam chave, UNIQUE, estrangeira ou usadas num CHECK; nesses casos
     * reconstrói a tabela sem ela.
     */
    @Override
    public boolean deleteColumn(ArrayList<ColumnMetadata> columns, String columnName, String table) {
        try {
            final TableDefinition current = readDefinition(table);
            final ColumnMetadata column = current.find(columnName);
            if (column == null) throw new SQLException("Column " + columnName + " not found in " + table + ".");
            final boolean simple = !column.IsPrimaryKey && !column.isUnique && !QueryBuilder.isForeign(column)
                    && current.constraints.stream().noneMatch(c -> containsIgnoreCase(c.columns(), columnName))
                    && current.checks.stream().noneMatch(check -> mentions(check, columnName));
            if (simple) {
                try {
                    runInTransaction(() -> {
                        // Um índice sobre a coluna faz o DROP COLUMN falhar; vai à frente.
                        for (final SchemaObject index : current.indexes) {
                            if (containsIgnoreCase(index.columns(), columnName)) execute(builder().dropIndex(index.name(), table));
                        }
                        for (final String sql : builder().alterTable(table).dropColumn(columnName).build()) execute(sql);
                    });
                    return true;
                } catch (SQLException ignored) {
                    // Uma vista ou um trigger que usa a coluna: cai para a reconstrução.
                }
            }
            final ArrayList<ColumnMetadata> kept = new ArrayList<>();
            for (final ColumnMetadata existing : current.columns) {
                if (!existing.Name.equalsIgnoreCase(columnName)) kept.add(existing);
            }
            return dropSpecialColumn(table, kept);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /**
     * Muda uma coluna. O nome muda-se com o RENAME COLUMN do próprio SQLite; tudo o resto
     * (tipo, NOT NULL, DEFAULT, chaves, UNIQUE, CHECK, AUTOINCREMENT) só se consegue
     * reconstruindo a tabela, e as duas coisas correm na mesma transação.
     */
    @Override
    public boolean alterColumn(final String table, final ColumnMetadata before, final ColumnMetadata after) {
        final EnumSet<ColumnChange> changes = ColumnChange.between(before, after);
        changes.remove(ColumnChange.COMMENT); // o SQLite não guarda comentários
        if (changes.isEmpty()) return true;

        final EnumSet<ColumnChange> structural = EnumSet.copyOf(changes);
        structural.remove(ColumnChange.NAME);
        structural.remove(ColumnChange.INDEX);

        final Rename rename = changes.contains(ColumnChange.NAME)
                ? new Rename(before.Name, after.Name) : null;

        try {
            if (structural.isEmpty()) {
                runInTransaction(() -> {
                    if (rename != null) renameColumnInside(table, rename);
                    if (changes.contains(ColumnChange.INDEX)) replaceIndex(table, before, after);
                });
                return true;
            }

            rebuildTable(table, rename == null ? null : () -> renameColumnInside(table, rename), current -> {
                final ColumnMetadata existing = current.find(after.Name);
                if (existing == null) throw new SQLException("Column " + after.Name + " not found in " + table + ".");

                final ColumnMetadata changed = after.copy();
                // O índice é recriado a partir do SQL guardado, e o COLLATE não aparece no formulário.
                if (changed.extra == null || changed.extra.isBlank()) changed.extra = existing.extra;
                changed.check = "";

                final List<ColumnMetadata> columns = new ArrayList<>();
                for (final ColumnMetadata column : current.columns) columns.add(column == existing ? changed : column);

                final List<String> keys = new ArrayList<>();
                for (final String key : current.primaryKey) {
                    if (!key.equalsIgnoreCase(after.Name) || after.IsPrimaryKey) keys.add(key);
                }
                if (after.IsPrimaryKey && !containsIgnoreCase(keys, after.Name)) keys.add(after.Name);
                for (final ColumnMetadata column : columns) {
                    column.IsPrimaryKey = containsIgnoreCase(keys, column.Name);
                    // AUTOINCREMENT só numa INTEGER PRIMARY KEY sozinha.
                    if (column.autoincrement >= 1 && (keys.size() != 1 || !column.IsPrimaryKey)) {
                        throw new SQLException("AUTOINCREMENT needs " + column.Name + " to be the only primary key column.");
                    }
                }

                final List<String> checks = new ArrayList<>(current.checks);
                if (before.check != null && !before.check.isBlank()) checks.removeIf(check -> check.trim().equals(before.check.trim()));
                if (after.check != null && !after.check.isBlank()) checks.add(after.check.trim());

                return new RebuildPlan(columns, keys, checks, Set.of(), Set.of());
            }, changes.contains(ColumnChange.INDEX) ? () -> replaceIndex(table, before, after) : null);
            return true;
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    private record Rename(String from, String to) {
    }

    private void renameColumnInside(final String table, final Rename rename) throws SQLException {
        for (final String sql : builder().alterTable(table).renameColumn(rename.from(), rename.to()).build()) execute(sql);
    }

    // =====================================================================================
    // Reconstrução da tabela
    // =====================================================================================

    /** O que a tabela nova vai ter. */
    private record RebuildPlan(List<ColumnMetadata> columns, List<String> primaryKey, List<String> checks,
                               Set<String> dropped, Set<String> added) {
    }

    @FunctionalInterface
    private interface RebuildPlanner {
        RebuildPlan plan(TableDefinition current) throws SQLException;
    }

    /**
     * Reconstrói a tabela seguindo o procedimento da documentação do SQLite
     * (https://www.sqlite.org/lang_altertable.html#otheralter):
     * <ol>
     *   <li>desliga as chaves estrangeiras (tem de ser fora da transação: lá dentro o PRAGMA
     *       não faz nada) e liga o {@code legacy_alter_table}, para o RENAME final não mexer
     *       em vistas nem triggers;</li>
     *   <li>numa transação: cria {@code new_X} com a definição nova, copia as linhas, apaga
     *       X, renomeia {@code new_X} para X e recria os índices e triggers que X tinha;</li>
     *   <li>confere que não apareceram violações de chaves estrangeiras e repõe o valor do
     *       AUTOINCREMENT.</li>
     * </ol>
     * Se algum passo falhar, a transação desfaz tudo.
     *
     * @param prelude trabalho a fazer dentro da transação antes de ler a tabela (um RENAME COLUMN)
     * @param after   trabalho a fazer dentro da transação depois de a tabela estar pronta
     */
    private void rebuildTable(final String table, final SqlWork prelude, final RebuildPlanner planner,
                              final SqlWork after) throws SQLException {
        final boolean autoCommit = connection.getAutoCommit();
        final boolean foreignKeys = foreignKeysOn();
        if (!autoCommit && foreignKeys) {
            throw new SQLException("This change rebuilds table " + table + ", and SQLite cannot switch off foreign keys "
                    + "inside an open transaction. Save (commit) the pending changes first.");
        }
        final boolean legacy = pragmaFlag("legacy_alter_table");

        if (foreignKeys) execute("PRAGMA foreign_keys = OFF");
        if (!legacy) execute("PRAGMA legacy_alter_table = ON");
        try {
            runInTransaction(() -> {
                if (prelude != null) prelude.run();

                final TableDefinition current = readDefinition(table);
                if (current.generatedColumns) {
                    throw new SQLException("Table " + table + " has generated columns, which cannot be copied when the "
                            + "table is rebuilt. Change it with SQL in the editor.");
                }
                final RebuildPlan plan = planner.plan(current);
                final long violationsBefore = foreignKeys ? foreignKeyViolations(table) : 0;

                final String temporary = unusedTableName("new_" + table);
                final QueryBuilder.CreateTable create = builder().createTable(temporary)
                        .withoutRowId(current.withoutRowId)
                        .strict(current.strict)
                        .columns(plan.columns())
                        .primaryKeyOrder(plan.primaryKey());
                for (final TableConstraint constraint : current.constraints) {
                    // Uma restrição de várias colunas que perdeu uma delas já não se aplica.
                    if (plan.dropped().stream().noneMatch(c -> containsIgnoreCase(constraint.columns(), c))) {
                        create.constraint(constraint.sql());
                    }
                }
                for (final String check : plan.checks()) create.check(check);
                execute(create.build());

                final List<String> copied = new ArrayList<>();
                for (final ColumnMetadata column : plan.columns()) {
                    if (!containsIgnoreCase(plan.added(), column.Name)) copied.add(column.Name);
                }
                if (!copied.isEmpty()) execute(builder().insert(temporary).columns(copied).fromTable(table).build());

                execute(builder().dropTable(table));
                for (final String sql : builder().alterTable(temporary).renameTo(table).build()) execute(sql);

                for (final SchemaObject index : current.indexes) {
                    if (plan.dropped().stream().noneMatch(c -> containsIgnoreCase(index.columns(), c))) execute(index.sql());
                }
                for (final SchemaObject trigger : current.triggers) execute(trigger.sql());

                if (current.sequence >= 0) restoreSequence(table, current.sequence);

                if (after != null) after.run();

                if (foreignKeys && foreignKeyViolations(table) > violationsBefore) {
                    throw new SQLException("The change would break foreign keys of " + table + " (PRAGMA foreign_key_check).");
                }
            });
        } finally {
            if (!legacy) execute("PRAGMA legacy_alter_table = OFF");
            if (foreignKeys) execute("PRAGMA foreign_keys = ON");
        }
    }

    private boolean foreignKeysOn() throws SQLException {
        return pragmaFlag("foreign_keys");
    }

    private boolean pragmaFlag(final String pragma) throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA " + pragma)) {
            return rs.next() && rs.getInt(1) != 0;
        }
    }

    private long foreignKeyViolations(final String table) throws SQLException {
        long count = 0;
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM pragma_foreign_key_check(?)")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) count = rs.getLong(1);
            }
        }
        return count;
    }

    /** O DROP TABLE apaga a linha do sqlite_sequence; sem isto os ids apagados voltavam a ser usados. */
    private void restoreSequence(final String table, final long sequence) throws SQLException {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE sqlite_sequence SET seq = MAX(seq, ?) WHERE name = ?")) {
            update.setLong(1, sequence);
            update.setString(2, table);
            if (update.executeUpdate() > 0) return;
        }
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)")) {
            insert.setString(1, table);
            insert.setLong(2, sequence);
            insert.executeUpdate();
        }
    }

    private String unusedTableName(final String base) throws SQLException {
        String candidate = base;
        for (int attempt = 2; tableExists(candidate); attempt++) candidate = base + "_" + attempt;
        return candidate;
    }

    private boolean tableExists(final String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Restrição de tabela com várias colunas (UNIQUE ou FOREIGN KEY), já em SQL. */
    private record TableConstraint(List<String> columns, String sql) {
    }

    /** Índice ou trigger da tabela, com o SQL que o recria. */
    private record SchemaObject(String name, List<String> columns, String sql) {
    }

    /** Tudo o que o CREATE TABLE de uma tabela declara, lido dos PRAGMA e do sqlite_master. */
    private static final class TableDefinition {
        String sql = "";
        final ArrayList<ColumnMetadata> columns = new ArrayList<>();
        /** Colunas da chave primária, pela ordem da chave. */
        final ArrayList<String> primaryKey = new ArrayList<>();
        final ArrayList<TableConstraint> constraints = new ArrayList<>();
        final ArrayList<String> checks = new ArrayList<>();
        final ArrayList<SchemaObject> indexes = new ArrayList<>();
        final ArrayList<SchemaObject> triggers = new ArrayList<>();
        boolean withoutRowId, strict, generatedColumns;
        /** Valor do AUTOINCREMENT no sqlite_sequence, ou -1. */
        long sequence = -1;

        ColumnMetadata find(final String name) {
            for (final ColumnMetadata column : columns) if (column.Name.equalsIgnoreCase(name)) return column;
            return null;
        }
    }

    private static final Pattern DECLARED_TYPE = Pattern.compile(
            "\\s*([A-Za-z_][A-Za-z0-9_ ]*?)\\s*(?:\\(\\s*([+-]?\\d+)\\s*(?:,\\s*([+-]?\\d+)\\s*)?\\))?\\s*");

    private static final Pattern COLLATE = Pattern.compile("(?i)\\bCOLLATE\\s+(\"[^\"]+\"|\\w+)");

    /** Lê a definição completa da tabela. */
    private TableDefinition readDefinition(final String table) throws SQLException {
        final TableDefinition definition = new TableDefinition();
        definition.sql = readCreateStatement(table);
        if (definition.sql == null) throw new SQLException("Table " + table + " not found.");

        final String upperSql = definition.sql.toUpperCase(Locale.ROOT);
        final String tail = upperSql.substring(Math.max(0, upperSql.lastIndexOf(')')));
        definition.withoutRowId = tail.matches("(?s).*WITHOUT\\s+ROWID.*");
        definition.strict = tail.matches("(?s).*\\bSTRICT\\b.*");

        // Colunas: tipo, NOT NULL, DEFAULT e posição na chave primária exatamente como declarados.
        final TreeMap<Integer, String> keyOrder = new TreeMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, type, \"notnull\", dflt_value, pk, hidden FROM pragma_table_xinfo(?) ORDER BY cid")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("hidden") != 0) {
                        definition.generatedColumns = true;
                        continue;
                    }
                    final ColumnMetadata column = new ColumnMetadata();
                    column.Name = rs.getString("name");
                    applyDeclaredType(column, rs.getString("type"));
                    column.NOT_NULL = rs.getInt("notnull") != 0;
                    column.defaultValue = rs.getString("dflt_value") == null ? "" : rs.getString("dflt_value");
                    final int pk = rs.getInt("pk");
                    if (pk > 0) {
                        column.IsPrimaryKey = true;
                        keyOrder.put(pk, column.Name);
                    }
                    definition.columns.add(column);
                }
            }
        }
        definition.primaryKey.addAll(keyOrder.values());

        // A INTEGER PRIMARY KEY sozinha é o rowid: o motor preenche-a, com ou sem AUTOINCREMENT.
        if (definition.primaryKey.size() == 1 && !definition.withoutRowId) {
            final ColumnMetadata key = definition.find(definition.primaryKey.getFirst());
            if (key != null && "INTEGER".equalsIgnoreCase(key.Type)) {
                key.autoincrement = upperSql.contains("AUTOINCREMENT") ? 1 : 0;
                if (key.autoincrement == 1) definition.sequence = readSequence(table);
            }
        }

        // COLLATE de cada coluna, tirado do próprio CREATE TABLE.
        for (final String part : topLevelParts(definition.sql)) {
            final String name = leadingIdentifier(part);
            final ColumnMetadata column = name == null ? null : definition.find(name);
            if (column == null) continue;
            final Matcher collate = COLLATE.matcher(part);
            if (collate.find()) column.extra = "COLLATE " + collate.group(1);
        }

        readIndexes(table, definition);
        readForeignKeys(table, definition);
        definition.checks.addAll(extractChecks(definition.sql));

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND tbl_name = ? AND sql IS NOT NULL")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) definition.triggers.add(new SchemaObject(rs.getString("name"), List.of(), rs.getString("sql")));
            }
        }
        return definition;
    }

    /** "VARCHAR(50)" → VARCHAR com tamanho 50; "DECIMAL(10,2)" → 8 dígitos inteiros e 2 decimais. */
    private static void applyDeclaredType(final ColumnMetadata column, final String declared) {
        final String text = declared == null ? "" : declared.trim();
        final Matcher matcher = DECLARED_TYPE.matcher(text);
        if (text.isEmpty() || !matcher.matches()) {
            column.Type = text; // coluna sem tipo (o SQLite deixa) ou tipo que não se decompõe
            return;
        }
        column.Type = matcher.group(1).trim().toUpperCase(Locale.ROOT);
        final String first = matcher.group(2);
        final String second = matcher.group(3);
        final boolean decimal = column.Type.equals("DECIMAL") || column.Type.equals("NUMERIC");
        if (first != null) {
            final int precision = Integer.parseInt(first);
            final int scale = second == null ? 0 : Integer.parseInt(second);
            column.size = precision;
            if (decimal || second != null) {
                column.decimalDigits = scale;
                column.integerDigits = precision - scale;
            }
        }
    }

    private long readSequence(final String table) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT seq FROM sqlite_sequence WHERE name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            return -1; // o sqlite_sequence só existe depois do primeiro AUTOINCREMENT
        }
    }

    /**
     * UNIQUE (origem 'u'), índices criados com CREATE INDEX (origem 'c') e os que o motor cria
     * para a chave primária (origem 'pk', ignorados).
     */
    private void readIndexes(final String table, final TableDefinition definition) throws SQLException {
        final List<String[]> indexes = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT name, \"unique\", origin FROM pragma_index_list(?)")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) indexes.add(new String[]{rs.getString("name"), rs.getString("unique"), rs.getString("origin")});
            }
        }
        for (final String[] index : indexes) {
            final String name = index[0];
            final boolean unique = "1".equals(index[1]);
            final String origin = index[2];
            final List<String> columns = indexColumns(name);

            if ("u".equals(origin)) {
                if (columns.size() == 1 && definition.find(columns.getFirst()) != null) {
                    definition.find(columns.getFirst()).isUnique = true;
                } else if (!columns.isEmpty()) {
                    definition.constraints.add(new TableConstraint(columns, "UNIQUE (" + builder().names(columns) + ")"));
                }
            } else if ("c".equals(origin)) {
                final String sql = readIndexSql(name);
                if (sql == null) continue;
                definition.indexes.add(new SchemaObject(name, columns, sql));
                if (columns.size() == 1) {
                    final ColumnMetadata column = definition.find(columns.getFirst());
                    if (column != null && (column.index == null || column.index.isBlank())) {
                        column.index = name;
                        column.indexType = unique ? "UNIQUE" : "";
                    }
                }
            }
        }
    }

    private List<String> indexColumns(final String index) throws SQLException {
        final List<String> columns = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT name FROM pragma_index_info(?) ORDER BY seqno")) {
            ps.setString(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    // Índices sobre expressões não têm nome de coluna.
                    if (rs.getString(1) != null) columns.add(rs.getString(1));
                }
            }
        }
        return columns;
    }

    private String readIndexSql(final String index) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT sql FROM sqlite_master WHERE type = 'index' AND name = ?")) {
            ps.setString(1, index);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Chaves estrangeiras: as de uma coluna vão para o ColumnMetadata, as compostas ficam em SQL. */
    private void readForeignKeys(final String table, final TableDefinition definition) throws SQLException {
        final LinkedHashMap<Integer, List<String[]>> keys = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, seq, \"table\", \"from\", \"to\", on_update, on_delete FROM pragma_foreign_key_list(?) ORDER BY id, seq")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.computeIfAbsent(rs.getInt("id"), _ -> new ArrayList<>()).add(new String[]{
                            rs.getString("table"), rs.getString("from"), rs.getString("to"),
                            rs.getString("on_update"), rs.getString("on_delete")});
                }
            }
        }
        final QueryBuilder q = builder();
        for (final List<String[]> key : keys.values()) {
            final String parent = key.getFirst()[0];
            final String onUpdate = key.getFirst()[3];
            final String onDelete = key.getFirst()[4];
            final List<String> from = key.stream().map(k -> k[1]).toList();
            // "REFERENCES pai" sem coluna aponta para a chave primária do pai.
            List<String> to = key.stream().map(k -> k[2]).toList();
            if (to.stream().anyMatch(Objects::isNull)) to = PrimaryKeyList(parent);

            if (from.size() == 1 && to != null && to.size() == 1 && definition.find(from.getFirst()) != null) {
                final ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
                foreign.isForeign = true;
                foreign.tableRef = parent;
                foreign.columnRef = to.getFirst();
                foreign.onUpdate = onUpdate == null ? "" : onUpdate;
                foreign.onEliminate = onDelete == null ? "" : onDelete;
                definition.find(from.getFirst()).foreign = foreign;
            } else {
                String sql = "FOREIGN KEY (" + q.names(from) + ") REFERENCES " + q.quote(parent)
                        + (to == null || to.isEmpty() ? "" : " (" + q.names(to) + ")");
                if (onUpdate != null && !onUpdate.equalsIgnoreCase("NO ACTION")) sql += " ON UPDATE " + onUpdate;
                if (onDelete != null && !onDelete.equalsIgnoreCase("NO ACTION")) sql += " ON DELETE " + onDelete;
                definition.constraints.add(new TableConstraint(from, sql));
            }
        }
    }

    /** Partes do corpo do CREATE TABLE separadas pelas vírgulas de topo. */
    private static List<String> topLevelParts(final String sql) {
        final List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = -1;
        char quote = 0;
        for (int i = 0; i < sql.length(); i++) {
            final char c = sql.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                quote = c;
            } else if (c == '[') {
                quote = ']';
            } else if (c == '(') {
                if (depth == 0) start = i + 1;
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0 && start >= 0) {
                    parts.add(sql.substring(start, i).trim());
                    break;
                }
            } else if (c == ',' && depth == 1) {
                parts.add(sql.substring(start, i).trim());
                start = i + 1;
            }
        }
        return parts;
    }

    /** Nome no início de uma parte do CREATE TABLE ("nome" TEXT, `nome` INT, [nome], nome). */
    private static String leadingIdentifier(final String part) {
        if (part.isEmpty()) return null;
        final char first = part.charAt(0);
        if (first == '"' || first == '`' || first == '[') {
            final char closing = first == '[' ? ']' : first;
            final int end = part.indexOf(closing, 1);
            return end > 0 ? part.substring(1, end) : null;
        }
        final Matcher matcher = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*").matcher(part);
        return matcher.lookingAt() ? matcher.group() : null;
    }

    /** A expressão de um CHECK usa esta coluna? */
    private static boolean mentions(final String expression, final String column) {
        return Pattern.compile("(?i)(^|[^A-Za-z0-9_$])[\"`\\[]?" + Pattern.quote(column) + "[\"`\\]]?([^A-Za-z0-9_$]|$)")
                .matcher(expression).find();
    }

    private static boolean containsIgnoreCase(final Collection<String> values, final String value) {
        for (final String candidate : values) if (candidate != null && candidate.equalsIgnoreCase(value)) return true;
        return false;
    }

    // =====================================================================================
    // PRAGMA
    // =====================================================================================

    private final ConnectionPragmaInterface connectionPragmaInterface = new ConnectionPragmaInterface() {
        @Override
        public int getTimeout() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA busy_timeout;")) {
                rs.next();
                return rs.getInt(1);
            }
        }

        @Override
        public void setTimeout(int timeout) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA busy_timeout  = " + timeout + ";");
            }
        }
    };

    private final IOPragma_Interface ioPragmaInterface = new IOPragma_Interface() {
        @Override
        public boolean getFullSync() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA fullfsync;")) {
                return rs.next() && rs.getInt(1) != 0;
            }
        }

        @Override
        public boolean getFullSyncCheckpoint() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA checkpoint_fullfsync;")) {
                return rs.next() && rs.getInt(1) != 0;
            }
        }

        @Override
        public boolean getDelete() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA secure_delete;")) {
                String value = rs.next() ? rs.getString(1) : "0";
                return !"0".equals(value); // 1 ou FAST → true
            }
        }

        @Override
        public boolean getCellSize() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA cell_size_check;")) {
                return rs.next() && rs.getInt(1) != 0;
            }
        }

        @Override
        public String getFlushMethod() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public int getBinlogSync() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");        }

        @Override
        public int getIOCapacity() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");        }

        @Override
        public int getIOCapacityMax() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");        }

        @Override
        public boolean getFlushNeighbors() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");        }

        @Override
        public boolean getNativeAIO() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");        }

// ---- SETTERS ----

        @Override
        public void setFullSync(boolean state) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA fullfsync = " + (state ? 1 : 0) + ";");
            }
        }

        @Override
        public void setFullSyncCheckpoint(boolean state) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA checkpoint_fullfsync = " + (state ? 1 : 0) + ";");
            }
        }

        @Override
        public void setDelete(boolean state) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                // usa FAST como alternativa a 1 (se quiseres suportar isso também)
                stmt.execute("PRAGMA secure_delete = " + (state ? 1 : 0) + ";");
            }
        }

        @Override
        public void setCellSize(boolean state) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA cell_size_check = " + (state ? 1 : 0) + ";");
            }
        }

        @Override
        public void setFlushMethod(String method) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setBinlogSync(int value) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setIOCapacity(int value) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setIOCapacityMax(int value) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setFlushNeighbors(boolean state) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setNativeAIO(boolean state) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }
    };

    private final SchemaPragmaInterface schemaPragmaInterface = new SchemaPragmaInterface() {
        @Override
        public int getAppID() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA application_id;")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public int getSchemaVersion() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA schema_version;")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public int getUserVersion() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA user_version;")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public String getEncoding() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA encoding;")) {
                return rs.next() ? rs.getString(1) : "";
            }
        }

        @Override
        public boolean getForeign() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA foreign_keys;")) {
                return rs.next() && rs.getInt(1) != 0;
            }
        }

        @Override
        public boolean getIgnoreCheck() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA ignore_check_constraints;")) {
                return rs.next() && rs.getInt(1) != 0;
            }
        }

        @Override
        public String getJournal() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_mode;")) {
                return rs.next() ? rs.getString(1) : "";
            }
        }

        @Override
        public int getJournalSize() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA journal_size_limit;")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public boolean getWritable() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA query_only;")) {
                return !(rs.next() && rs.getInt(1) != 0); // query_only=1 → não é writable
            }
        }

        // ---- Setters ----

        @Override
        public void setAppID(int id) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA application_id = " + id + ";");
            }
        }

        @Override
        public void setSchemaVersion(int id) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA schema_version = " + id + ";");
            }
        }

        @Override
        public void setUserVersion(int id) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA user_version = " + id + ";");
            }
        }

        @Override
        public void setEncoding(String encoding) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA encoding = '" + encoding.replace("'", "''") + "';");
            }
        }

        @Override
        public void setForeign(boolean status) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = " + (status ? 1 : 0) + ";");
            }
        }

        @Override
        public void setIgnoreCheck(boolean status) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA ignore_check_constraints = " + (status ? 1 : 0) + ";");
            }
        }

        @Override
        public void setJournal(String mode) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode = " + mode + ";");
            }
        }

        @Override
        public void setJournalSize(int size) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_size_limit = " + size + ";");
            }
        }

        @Override
        public void setWritable(boolean status) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA query_only = " + (status ? 0 : 1) + ";");
            }
        }
    };

    private final DebugPragmaInterface debugPragmaInterface = new DebugPragmaInterface() {

        @Override
        public boolean getParserTrace() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA parser_trace;")) {
                if (rs.next()) {
                    int val = rs.getInt(1);
                    return val != 0;
                }
                return false;
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public String getDirectory() {
            // poderia mapear para temp_store_directory
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA temp_store_directory;")) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            } catch (SQLException e) {
                // log ou wrap
            }
            return "";
        }

        /** O PRAGMA devolve 0, 1 ou 2; o {@code TempStore.valueOf("0")} de antes rebentava sempre. */
        @Override
        public TempStore getTempMode() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA temp_store;")) {
                if (rs.next()) {
                    int mode = rs.getInt(1);
                    if (mode >= 0 && mode < TempStore.values().length) return TempStore.values()[mode];
                }
            } catch (SQLException e) {
                throw new SQLException(e);
            }
            // default se não for suportado
            return TempStore.DEFAULT;
        }

        // Setters

        @Override
        public void setParserTrace(boolean status) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("PRAGMA parser_trace = %d;", status ? 1 : 0));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setDirectory(String path) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                // path precisa estar entre aspas simples ou duplas
                stmt.execute(String.format("PRAGMA temp_store_directory = '%s';", path.replace("'", "''")));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setTempMode(TempStore tempMode) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("PRAGMA temp_store = %d;", tempMode.ordinal()));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void actionQuick() {

        }
    };

    private final PerformancePragmaInterface performancePragmaInterface = new PerformancePragmaInterface() {
        @Override
        public Synchronization getSynchronizationMode() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return Synchronization.getValue(smtd.executeQuery(String.format("PRAGMA %s.synchronous;", getDatabaseName())).getInt(1));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getThreads() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery("PRAGMA threads;").getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        /** O locking_mode é texto ("normal"/"exclusive"); lido como número dava sempre NORMAL. */
        @Override
        public Lock getLockMode() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                final String mode = smtd.executeQuery(String.format("PRAGMA %s.locking_mode;", getDatabaseName())).getString(1);
                return "EXCLUSIVE".equalsIgnoreCase(mode) ? Lock.EXCLUSIVE : Lock.NORMAL;
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public VACUUM getVacuumMode() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return VACUUM.getValue(smtd.executeQuery(String.format("PRAGMA %s.auto_vacuum;", getDatabaseName())).getInt(1));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public boolean getAutoIndex() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery("PRAGMA automatic_index;").getBoolean(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setSynchronizationMode(Synchronization synchronizationMode) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.synchronous=%d;", getDatabaseName(), synchronizationMode.ordinal()));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setThreads(int threads) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA threads=%d;", threads));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setLock(Lock lock) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.locking_mode=%s;", getDatabaseName(), lock.getName()));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setVacuum(VACUUM vacuum) throws SQLException {
            System.out.println(vacuum);
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.auto_vacuum=%d;", getDatabaseName(), vacuum.ordinal()));
                smtd.execute("VACUUM;");
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        /** O PRAGMA optimize recebe a máscara (0xfffe...), não o nome do enum. */
        @Override
        public void setOptimizer(Optimize optimizer) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA optimize=%s;", optimizer.getName()));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setAutoIndex(boolean autoIndex) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA automatic_index=%b;", autoIndex));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }
    };

    private final MemoryPragmaInterface memoryPragmaInterface = new MemoryPragmaInterface() {
        @Override
        public int getCacheSize() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery(String.format("PRAGMA %s.cache_size", getDatabaseName())).getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getPageSize() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery(String.format("PRAGMA %s.page_size", getDatabaseName())).getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getMMapSize() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery(String.format("PRAGMA %s.mmap_size", getDatabaseName())).getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getHardHeapSize() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery("PRAGMA hard_heap_limit").getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getSoftHeapSize() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery("PRAGMA soft_heap_limit").getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getMaxPages() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery(String.format("PRAGMA %s.max_page_count", getDatabaseName())).getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public boolean getCacheSpill() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery(String.format("PRAGMA %s.cache_spill", getDatabaseName())).getBoolean(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public long getTotalPages() throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                return smtd.executeQuery(String.format("PRAGMA %s.page_count;", getDatabaseName())).getInt(1);
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setCacheSize(int size) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.cache_size=%d;", getDatabaseName(), size));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setPageSize(int size) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.page_size=%d;", getDatabaseName(), size));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setMMapSize(int size) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.mmap_size=%d;", getDatabaseName(), size));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        // O hard_heap_limit e o soft_heap_limit são da ligação, não de um esquema: com "main."
        // à frente o SQLite não os reconhecia.
        @Override
        public void setHardHeapSize(int size) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA hard_heap_limit=%d;", size));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setSoftHeapSize(int size) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA soft_heap_limit=%d;", size));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public void setMaxPages(int size) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.max_page_count=%d;", getDatabaseName(), size));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        /** Escrevia no page_count (que é só de leitura) em vez do cache_spill. */
        @Override
        public void setCacheSpill(boolean state) throws SQLException {
            try (Statement smtd = connection.createStatement()) {
                smtd.execute(String.format("PRAGMA %s.cache_spill=%d;", getDatabaseName(), state ? 1 : 0));
            } catch (SQLException e) {
                throw new SQLException(e);
            }
        }

        @Override
        public int getLogBufferSize() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public int getSortBufferSize() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public int getJoinBufferSize() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public int getReadBufferSize() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public int getReadRndBufferSize() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public int getThreadStackSize() throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }


        @Override
        public void setLogBufferSize(int size) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setSortBufferSize(int size) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setJoinBufferSize(int size) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setReadBufferSize(int size) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setReadRndBufferSize(int size) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }

        @Override
        public void setThreadStackSize(int size) throws SQLException {
            throw new SQLFeatureNotSupportedException("SQLite doesn't support this feature.");
        }
    };

    // =====================================================================================
    // Linhas
    // =====================================================================================

    final DatabaseInserterInterface inserterInterface = new DatabaseInserterInterface() {
        @Override
        public boolean removeData(String Table, HashMap<String, String> data, HashMap<String, String> prime) {
            return false;
        }

        @Override
        public void createTable(String tableName, List<String> columnDefinitions, List<String> primaryKeyColumns) throws SQLException {
            createTableFromDefinitions(tableName, columnDefinitions, primaryKeyColumns);
        }

        @Override
        public String getException() {
            return GetException();
        }

        /** Os valores vão como parâmetros: um apóstrofo no texto já não parte o INSERT. */
        @Override
        public boolean insertData(String Table, HashMap<String, String> data) {
            final List<String> columns = new ArrayList<>(data.keySet());
            final String query = builder().insert(Table).columns(columns).build();
            System.out.println(query);
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                for (int i = 0; i < columns.size(); i++) setParameter(pstmt, i + 1, data.get(columns.get(i)));
                pstmt.execute();
                putMessage(new Logger(getUsername(), query, pstmt.getWarnings() != null ? pstmt.getWarnings().getMessage() : "", LocalTime.now()));
            } catch (SQLException e) {
                MsgException = e.getMessage();
                return false;
            }
            return true;
        }

        @Override
        public boolean insertData(String Table, ArrayList<LinkedHashMap<String, String>> data) {
            if (data == null || data.isEmpty()) return true;
            // As colunas saem da primeira linha e são lidas pelo nome em todas as outras.
            final List<String> columns = new ArrayList<>(data.getFirst().keySet());
            final String query = builder().insert(Table).columns(columns).build();
            System.out.println(query);

            try (PreparedStatement ps = connection.prepareStatement(query)) {
                for (final HashMap<String, String> row : data) {
                    for (int i = 0; i < columns.size(); i++) setParameter(ps, i + 1, row.get(columns.get(i)));
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                System.err.println(e.getMessage());
                MsgException = e.getMessage();
                return false;
            }
            return true;
        }

        @Override
        public boolean removeData(String Table, HashMap<String, String> data, ArrayList<Long> rowid) {
            return false;
        }

        /** Apaga pelo ROWID. Para apagar pela chave primária usa-se {@link #deleteRows}. */
        @Override
        public boolean removeData(String Table, ArrayList<String> rowid) {
            final List<List<String>> values = new ArrayList<>();
            for (final String id : rowid) values.add(List.of(id));
            return deleteRows(Table, List.of(getRowId()), values);
        }
    };

    final DatabaseUpdaterInterface updater = new DatabaseUpdaterInterface() {
        @Override
        public boolean updateData(String Table, HashMap<String, String> data, final long index) {
            final List<String> columns = new ArrayList<>(data.keySet());
            final QueryBuilder.Update update = builder().update(Table);
            for (final String column : columns) update.set(column);
            final String query = update.where(getRowId()).build();
            System.out.println(query);
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                for (int i = 0; i < columns.size(); i++) setParameter(pstmt, i + 1, data.get(columns.get(i)));
                setParameter(pstmt, columns.size() + 1, index);
                pstmt.execute();
                putMessage(new Logger(getUsername(), query, pstmt.getWarnings() != null ? pstmt.getWarnings().getMessage() : "", LocalTime.now()));
            } catch (SQLException e) {
                MsgException = e.getMessage();
                return false;
            }
            return true;
        }

        @Override
        public boolean updateData(String Table, final String column, final String value, final long index) {
            return updateCell(Table, column, value, List.of(getRowId()), List.of(index));
        }

        @Override
        public boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp) {
            return false;
        }

        @Override
        public boolean updateData(String Table, final String column, final Object value, final long index, String PrimeKey, final String tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) return updateCell(Table, column, value, List.of(getRowId()), List.of(index));
            return updateCell(Table, column, value, List.of(PrimeKey), List.of(tmp));
        }

        @Override
        public boolean updateData(String Table, String column, Object value, String[] index, String PrimeKey, String tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) return updateCell(Table, column, value, List.of(getRowId()), List.of(index[0]));
            return updateCell(Table, column, value, List.of(PrimeKey), List.of(tmp));
        }

        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, String PrimeKey, String tmp) {
            return updateData(Table, column, value, index, PrimeKey, tmp);
        }

        /** O que a grelha chama: chave primária (composta ou não) ou, sem chave, o ROWID. */
        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, ArrayList<String> PrimeKey, ArrayList<String> tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) {
                return updateCell(Table, column, value, List.of(getRowId()), Collections.singletonList(index[0]));
            }
            return updateCell(Table, column, value, PrimeKey, tmp);
        }

        @Override
        public boolean updateData(String Table, final String column, final Object value, final String index, String PrimeKey, final String tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) return updateCell(Table, column, value, List.of(getRowId()), List.of(index));
            return updateCell(Table, column, value, List.of(PrimeKey), List.of(tmp));
        }

        @Override
        public String getException() {
            return GetException();
        }
    };

    @Override
    public synchronized long totalPages(final String table) {
        try (final Statement statement = connection.createStatement();
                final ResultSet ret = statement.executeQuery(builder().count(table))) {
            if (ret.next()) {
                return pages(ret.getLong(1));
            }
            return 0;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return -1;
        }
    }

    @Override
    public synchronized long totalPages(final String table, final ArrayList<String> columns, final String condition) {
        return totalPages(table, columns.getFirst(), condition);
    }

    @Override
    public synchronized long totalPages(final String table, final String column, final String condition) {
        final QueryBuilder q = builder();
        final String sql = "SELECT COUNT(" + q.name(column) + ") FROM " + q.quote(table) + " " + (condition == null ? "" : condition);
        try (final Statement statement = connection.createStatement();
             final ResultSet ret = statement.executeQuery(sql)) {
            System.out.println(sql);
            if (ret.next()) {
                return pages(ret.getLong(1));
            }
            return 0;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return -1;
        }
    }

    @Override
    public void renameDatabase(final String name) {
        return;
    }

    @Override
    public ArrayList<String> getTables() {
        ArrayList<String> TablesName = new ArrayList<>();
        try (ResultSet tabledMeta = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (tabledMeta.next()) {
                TablesName.add(tabledMeta.getString("TABLE_NAME"));
            }
            return TablesName;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }

    }

    @Override
    public ArrayList<String> getColumnsName(final String Table) {
        ArrayList<String> TablesMetadata = new ArrayList<>();
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, Table, null)) {
            while (columns.next()) {
                TablesMetadata.add(columns.getString("COLUMN_NAME"));
            }
            return TablesMetadata;
           // return TablesName;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }

    }

    /** Colunas com uma restrição UNIQUE só delas. */
    @Override
    protected HashMap<String, Boolean> isUnique(final String Table) {
        HashMap<String, Boolean> ColumnsUnique = new HashMap<>();
        try {
            for (final ColumnMetadata column : readDefinition(Table).columns) {
                if (column.isUnique) ColumnsUnique.put(column.Name, true);
            }
        } catch (Exception e) {
            return null;
        }
        return ColumnsUnique;
    }

    @Override
    protected HashMap<String, ColumnMetadata.Foreign> getForeign(final String Table) {
        HashMap<String, ColumnMetadata.Foreign> ColumnForeign = new HashMap<>();
        try {
            for (final ColumnMetadata column : readDefinition(Table).columns) {
                if (column.foreign.isForeign) ColumnForeign.put(column.Name, column.foreign);
            }
        } catch (Exception e) {
            System.out.println("Could not read foreign keys: " + e.getMessage());
            return null;
        }
        return ColumnForeign;
    }

    /**
     * Metadados das colunas, lidos dos PRAGMA: tipo e DEFAULT exatamente como declarados,
     * NOT NULL, chave primária (com a ordem), UNIQUE, estrangeira, índice e autoincremento.
     */
    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(final String Table) {
        try {
            return readDefinition(Table).columns;
        } catch (SQLException e) {
            System.out.println("errorrrrr " + e.getMessage());
            MsgException = e.getMessage();
            return null;
        }
    }

    @Deprecated
    @Override
    public boolean TableisPimeKey(final String TableName) {
        final ArrayList<String> keys = PrimaryKeyList(TableName);
        return keys != null && keys.contains(TableName);
    }

    @Override
    public boolean TableHasPrimeKey(final String TableName) {
        final ArrayList<String> keys = PrimaryKeyList(TableName);
        return keys != null && !keys.isEmpty();
    }

    /** Colunas da chave primária, pela ordem da chave (que pode não ser a das colunas). */
    @Override
    public ArrayList<String> PrimaryKeyList(final String Table) {
        final TreeMap<Integer, String> keys = new TreeMap<>();
        try (PreparedStatement ps = connection.prepareStatement("SELECT name, pk FROM pragma_table_info(?)")) {
            ps.setString(1, Table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (rs.getInt("pk") > 0) keys.put(rs.getInt("pk"), rs.getString("name"));
                }
            }
            return new ArrayList<>(keys.values());
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
    }

    @Override
    public boolean connect(String url, String userName, String password) {
        return false;
    }

    @Override
    public boolean CreateSchema(String url, String name, String userName, String password, Map<String, String> modes) {
        return false;
    }

    @Override
    public boolean connect(String url, String name, String userName, String password, boolean ssl) {
        return false;
    }

    @Override
    public String getUrl() {
        return super.Url;
    }

    /** O SQLite não guarda funções nem procedimentos (a janela fazia setAll(null) e rebentava). */
    @Override
    public ArrayList<FunctionController.Function> getFunctions() {
        return new ArrayList<>();
    }

    @Override
    public ArrayList<ProcedureController.Procedure> getProcedure() {
        return new ArrayList<>();
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid) {
        final ColumnMetadata id = new ColumnMetadata();
        id.Name = "id";
        id.Type = "INT";
        // Uma tabela sem rowid tem de ter chave primária.
        id.IsPrimaryKey = !rowid;
        try {
            execute(builder().createTable(table).temporary(temporary).withoutRowId(!rowid).column(id).build());
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid, ArrayList<ColumnMetadata> columnMetadata) {
        final TableMetadata metadata = new TableMetadata(table);
        metadata.addColumns(columnMetadata);
        return createTable(metadata, temporary, rowid);
    }

    /**
     * CREATE TABLE a partir do formulário "Create table", seguido dos índices pedidos em
     * cada coluna (que antes eram ignorados).
     */
    @Override
    public boolean createTable(final TableMetadata metadata, boolean temporary, boolean rowid) {
        try {
            final String command = builder().createTable(metadata.getName())
                    .temporary(temporary)
                    .withoutRowId(!rowid)
                    .columns(metadata.getColumnMetadata())
                    .check(metadata.getCheck())
                    .build();
            runInTransaction(() -> {
                execute(command);
                for (final ColumnMetadata column : metadata.getColumnMetadata()) {
                    if (column.index != null && !column.index.isBlank()) {
                        execute(builder().createIndex(column.index, metadata.getName(), List.of(column.Name), column.indexType));
                    }
                }
            });
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public void changeCommitMode(boolean mode) throws SQLException {
        connection.setAutoCommit(mode);
    }

    @Override
    public boolean getCommitMode() throws SQLException {
       return connection.getAutoCommit();
    }

    @Override
    public ArrayList<ViewController.View> getViews(final String table) throws SQLException {
        final ArrayList<ViewController.View> views = new ArrayList<>();

        // Obtém as views do esquema atual
            String sql = "SELECT name, sql FROM sqlite_master WHERE type = 'view'";

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    String viewName = rs.getString("name");
                    String Query = rs.getString("sql") + " ";
                    if (Query.contains(" " + table + " ")) {
                        Query = Query.substring(Query.toLowerCase().indexOf("select"));
                        views.add(new ViewController.View(viewName, null, Query));
                    }
                }
            }
        return views;
    }

    @Override
    public ArrayList<ViewController.View> getViews() throws SQLException {
        final ArrayList<ViewController.View> views = new ArrayList<>();

        String sql = "SELECT name, sql FROM sqlite_master WHERE type = 'view'";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String viewName = rs.getString("name");
                String Query = rs.getString("sql") + " ";
                Query = Query.substring(Query.toLowerCase().indexOf("select"));
                views.add(new ViewController.View(viewName, null, Query));
            }
        }
        return views;
    }

    /** Substitui o trigger (IF EXISTS: sem isso um trigger novo nunca chegava a ser criado). */
    @Override
    public void createTrigger(final String trigger, final String code) {
        try {
            execute(builder().dropTrigger(trigger, null));
            execute(code);
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    @Override
    public void removeTrigger(final String trigger) throws SQLException {
        execute("DROP TRIGGER " + quote(trigger));
    }

    @Override
    public void createEvent(final String event, final String code) {
        MsgException = "SQLite has no scheduled events.";
    }

    @Override
    public void removeEvent(final String event) throws SQLException {
        throw new SQLFeatureNotSupportedException("SQLite has no scheduled events.");
    }

    @Override
    public void executeCode(final String code) throws SQLException {
        execute(code);
    }

    @Override
    public void createIndex(final String table, final ArrayList<String> columns, String indexName, final String mode) throws SQLException {
        execute(builder().createIndex(indexName, table, columns, mode));
    }

    @Override
    public void createIndex(final String table, final String column, String indexName, final String mode) throws SQLException {
        createIndex(table, new ArrayList<>(List.of(column)), indexName, mode);
    }

    @Override
    public void removeIndex(final String indexName) throws SQLException {
        execute(builder().dropIndex(indexName, null));
    }

    @Override
    public ArrayList<CheckMetadata> getChecks(final String table) throws SQLException {
        final ArrayList<CheckMetadata> checks = new ArrayList<>();

        // O SQLite não tem catálogo de restrições: o que existe é o texto do CREATE TABLE.
        final String sql = readCreateStatement(table);
        if (sql == null) return checks;

        for (String expression : extractChecks(sql)) {
            checks.add(new CheckMetadata("", table, expression));
        }
        return checks;
    }

    /** Extrai o conteúdo de cada CHECK(...) do CREATE TABLE, respeitando parênteses aninhados. */
    private static List<String> extractChecks(final String sql) {
        final List<String> found = new ArrayList<>();
        final String upper = sql.toUpperCase(Locale.ROOT);

        int from = 0;
        while (true) {
            final int at = upper.indexOf("CHECK", from);
            if (at < 0) break;

            // "CHECK" dentro de um nome (checked, is_check) não conta.
            if (at > 0 && (Character.isLetterOrDigit(sql.charAt(at - 1)) || sql.charAt(at - 1) == '_')) {
                from = at + 5;
                continue;
            }

            int open = at + "CHECK".length();
            while (open < sql.length() && Character.isWhitespace(sql.charAt(open))) open++;
            if (open >= sql.length() || sql.charAt(open) != '(') {
                from = at + 5;
                continue;
            }

            int depth = 0;
            int close = open;
            while (close < sql.length()) {
                final char c = sql.charAt(close);
                if (c == '(') depth++;
                else if (c == ')' && --depth == 0) break;
                close++;
            }
            if (close >= sql.length()) break;

            found.add(sql.substring(open + 1, close).trim());
            from = close + 1;
        }
        return found;
    }

    /**
     * Acrescenta uma restrição CHECK a uma tabela existente.
     *
     * <p>O SQLite não aceita {@code ALTER TABLE ... ADD CONSTRAINT}: a única via é reconstruir
     * a tabela ({@link #rebuildTable}). Os CHECK passam a ficar todos ao nível da tabela.</p>
     */
    @Override
    public void addCheck(final String table, final String name, final String expression) throws SQLException {
        rebuildTable(table, null, current -> {
            final List<String> checks = new ArrayList<>(current.checks);
            checks.add(expression.trim());
            return new RebuildPlan(current.columns, current.primaryKey, checks, Set.of(), Set.of());
        }, null);
    }

    /** Sem catálogo, as restrições são identificadas pela própria expressão. */
    @Override
    public void dropCheck(final String table, final String name) throws SQLException {
        rebuildTable(table, null, current -> {
            final List<String> checks = new ArrayList<>(current.checks);
            if (!checks.removeIf(check -> check.trim().equals(name.trim()))) {
                throw new SQLException("Check (" + name + ") not found in " + table + ".");
            }
            return new RebuildPlan(current.columns, current.primaryKey, checks, Set.of(), Set.of());
        }, null);
    }

    private String readCreateStatement(final String table) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** O último CHECK da tabela (é o que o formulário "Create table" mostra). */
    @Override
    public String getTableCheck(final String table) throws SQLException {
        final String sql = readCreateStatement(table);
        if (sql == null) return null;
        final List<String> checks = extractChecks(sql);
        return checks.isEmpty() ? null : checks.getLast();
    }

    @Override
    public String getDatabaseName() {
        return "main";
    }

}
