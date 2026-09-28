package com.example.sqlide.drivers.PostegreSQL;

import com.example.sqlide.Configuration.permissionConfController;
import com.example.sqlide.Function.FunctionController;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.Metadata.BuiltInRoutines;
import com.example.sqlide.Metadata.CheckMetadata;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Metadata.IndexMetadata;
import com.example.sqlide.Metadata.RoutineMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.Procedure.ProcedureController;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.ConnectionPragmaInterface;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.PermissionPragmaInterface;
import com.example.sqlide.drivers.model.QueryBuilder;
import com.example.sqlide.drivers.model.SQLTypes;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;

/**
 * Driver do PostgreSQL.
 *
 * <p>O SQL passa pelo {@link QueryBuilder}. O que estava mal e mudou:</p>
 * <ul>
 *   <li>{@code getViews()} devolvia null e o {@code DatabaseInterface.readTables()} fazia
 *       um {@code for} sobre isso: abrir uma base de dados PostgreSQL rebentava com
 *       NullPointerException.</li>
 *   <li>Estavam por fazer: {@code createTable(TableMetadata)} (criar tabela pela janela
 *       falhava sempre), {@code renameColumn}, {@code modifyColumnType},
 *       {@code deleteColumn}, o {@code updateData} que a grelha chama, o
 *       {@code removeData}, os triggers, {@code CreateSchema}, {@code disconnect},
 *       {@code executeCode} e o modo de commit.</li>
 *   <li>O {@code createSpecialColumn} copiava a tabela inteira para acrescentar uma chave —
 *       perdendo sequências, índices e restrições — quando o PostgreSQL faz isso com um
 *       simples {@code ALTER TABLE ... ADD COLUMN}.</li>
 *   <li>Havia um campo {@code buffer} aqui que escondia o da classe base: o tamanho de
 *       página configurado não chegava às contas de páginas.</li>
 *   <li>No {@code getColumnsMetadata} o NOT NULL vinha invertido, as chaves estrangeiras
 *       nunca ficavam marcadas e os tipos ENUM eram emparelhados com as colunas pela
 *       posição numa consulta sem ORDER BY.</li>
 * </ul>
 */
public class PostreSQLDB extends DataBase {

    /** Esquema atual da ligação (normalmente "public"), para não misturar tabelas de outros. */
    private String schema = null;

    public PostreSQLDB() {
        idType = "CTID";
        SQLType = SQLTypes.POSTGRESQL;
        super.databaseInfo = new PostgreSQLInfo();
        Updater(updater);
        Inserter(inserterInterface);

        Permission(permissionPragmaInterface);
        Connection(connectionPragmaInterface);
    }

    @Override
    protected String metadataSchema() {
        return schema;
    }

    /** Os parâmetros vão sem tipo: o servidor deduz o tipo da coluna (texto → integer, date, enum...). */
    @Override
    protected void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
        if (value == null) statement.setNull(index, Types.OTHER);
        else if (value instanceof String text) statement.setObject(index, text, Types.OTHER);
        else statement.setObject(index, value);
    }

    @Override
    public boolean connect(String DBName, Map<String, String> formatData) {
        return false;
    }

    @Override
    public boolean connect(String DBName) {
        return false;
    }

    /**
     * Rotinas do PostgreSQL, lidas do {@code pg_proc}.
     *
     * <p>É o único dos motores suportados que descreve as suas próprias funções embutidas
     * em catálogo, com assinatura, tipo de retorno e comentário — por isso aqui a lista sai
     * quase toda da base de dados em vez de estar escrita à mão.</p>
     */
    @Override
    public ArrayList<RoutineMetadata> getRoutines() {
        ArrayList<RoutineMetadata> routines = new ArrayList<>();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT p.proname AS name,
                            n.nspname AS schema,
                            pg_get_function_arguments(p.oid) AS args,
                            pg_get_function_result(p.oid) AS result,
                            p.prokind AS kind,
                            n.nspname NOT IN ('pg_catalog', 'information_schema') AS user_defined,
                            COALESCE(obj_description(p.oid, 'pg_proc'), '') AS comment
                     FROM pg_proc p
                     JOIN pg_namespace n ON n.oid = p.pronamespace
                     WHERE n.nspname NOT LIKE 'pg_toast%'
                     ORDER BY user_defined DESC, n.nspname, p.proname""")) {

            while (rs.next()) {
                final boolean userDefined = rs.getBoolean("user_defined");
                final String kindCode = rs.getString("kind");

                RoutineMetadata.Kind kind = switch (kindCode == null ? "f" : kindCode) {
                    case "p" -> RoutineMetadata.Kind.PROCEDURE;
                    case "a" -> RoutineMetadata.Kind.AGGREGATE;
                    default -> RoutineMetadata.Kind.FUNCTION;
                };

                routines.add(new RoutineMetadata(
                        rs.getString("name"),
                        kind,
                        userDefined ? "User defined" : categoryOf(rs.getString("schema"), kind),
                        "(" + nullToEmpty(rs.getString("args")) + ")",
                        nullToEmpty(rs.getString("result")),
                        nullToEmpty(rs.getString("comment")),
                        !userDefined));
            }
        } catch (SQLException e) {
            System.err.println("Could not list PostgreSQL routines: " + e.getMessage());
            // Sem acesso ao catálogo mostra-se pelo menos o conjunto conhecido.
            routines.addAll(BuiltInRoutines.forDialect(SQLTypes.POSTGRESQL));
        }

        routines.addAll(readViewsFromInformationSchema("table_schema", schema == null ? "public" : schema));
        return routines;
    }

    /** O pg_catalog tem milhares de funções; agrupá-las torna a lista navegável. */
    private static String categoryOf(String schema, RoutineMetadata.Kind kind) {
        if (kind == RoutineMetadata.Kind.AGGREGATE) return "Aggregate";
        return "pg_catalog".equals(schema) ? "Built in" : schema;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /** Triggers do esquema, com o CREATE TRIGGER que o próprio PostgreSQL reconstrói. */
    @Override
    public HashMap<String, String> getTriggers() {
        final HashMap<String, String> code = new HashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT t.tgname, pg_get_triggerdef(t.oid) AS definition
                     FROM pg_trigger t
                     JOIN pg_class c ON c.oid = t.tgrelid
                     JOIN pg_namespace n ON n.oid = c.relnamespace
                     WHERE NOT t.tgisinternal AND n.nspname = current_schema()""")) {
            while (rs.next()) code.put(rs.getString(1), rs.getString(2));
            return code;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
    }

    /** O PostgreSQL não tem eventos agendados. */
    @Override
    public HashMap<String, String> getEvents() {
        return new HashMap<>();
    }

    @Override
    public void disconnect() throws SQLException {
        if (statement != null) statement.close();
        if (connection != null) connection.close();
    }

    @Override
    public boolean renameTable(String Table, String newTableName) {
        try {
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
     * ADD COLUMN com tudo na própria coluna: DEFAULT, NOT NULL, UNIQUE, CHECK, REFERENCES e
     * IDENTITY. Uma chave primária numa tabela que já tem chave passa a composta. Corre
     * numa transação — no PostgreSQL o DDL também se desfaz.
     */
    @Override
    public boolean createColumn(String table, String column, ColumnMetadata meta, boolean fill) {
        final ColumnMetadata added = meta.copy();
        added.Name = column;
        try {
            prepareColumnType(added);
            final QueryBuilder.Constraints current = readConstraints(table, added);
            final boolean fillForeign = fill && QueryBuilder.isForeign(added);
            final List<String> parentKey = fillForeign ? PrimaryKeyList(added.foreign.tableRef) : List.of();
            if (fillForeign && (current.primaryKeyColumns().size() != 1 || parentKey == null || parentKey.size() != 1)) {
                throw new SQLException("Fill foreign matches rows by primary key, so both tables need a single-column primary key.");
            }

            final QueryBuilder.AlterTable alter = builder().alterTable(table);
            if (added.IsPrimaryKey && !current.primaryKeyColumns().isEmpty()) {
                alter.addColumn(added, false);
                alter.dropPrimaryKey(current.primaryKey());
                final List<String> keys = new ArrayList<>(current.primaryKeyColumns());
                keys.add(column);
                alter.addPrimaryKey(keys);
            } else {
                alter.addColumn(added, true);
            }
            if (added.comment != null && !added.comment.isBlank()) alter.comment(column, added.comment);

            runInTransaction(() -> {
                for (final String sql : alter.build()) execute(sql);
                if (fillForeign) {
                    execute(builder().updateFromParent(table, column, current.primaryKeyColumns().getFirst(),
                            added.foreign.tableRef, added.foreign.columnRef, parentKey.getFirst()));
                }
            });
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean createTable(String table) throws SQLException {
        final ColumnMetadata id = new ColumnMetadata();
        id.Name = "id";
        id.Type = "INTEGER";
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
        return createColumn(table, column, meta, false);
    }

    @Override
    public boolean renameColumn(String table, String column, String newColumn) {
        try {
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
     * DROP COLUMN. Os índices e restrições da própria coluna saem com ela; se outra tabela
     * ou uma vista dependerem dela, o PostgreSQL recusa e a mensagem diz quem é.
     */
    @Override
    public boolean deleteColumn(ArrayList<ColumnMetadata> columns, String columnName, String table) {
        try {
            for (final String sql : builder().alterTable(table).dropColumn(columnName).build()) execute(sql);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    /**
     * Os ENUM do PostgreSQL são tipos com nome, que têm de existir antes da coluna. Se o tipo
     * já existe, acrescentam-se os valores que faltam (fora da transação: o ADD VALUE não
     * pode ser usado na mesma transação em que é criado).
     */
    @Override
    protected void prepareColumnType(final ColumnMetadata column) throws SQLException {
        if (!"ENUM".equalsIgnoreCase(column.Type) || column.aliasType == null || column.aliasType.isBlank()) return;
        final List<String> values = column.items == null ? List.of() : column.items;
        final QueryBuilder q = builder();
        if (!enumTypeExists(column.aliasType)) {
            if (values.isEmpty()) throw new SQLException("Enum type " + column.aliasType + " needs at least one value.");
            execute(q.createEnumType(column.aliasType, values));
            return;
        }
        final List<String> existing = EnumChecks(column.aliasType);
        for (final String value : values) {
            if (!existing.contains(value)) {
                execute("ALTER TYPE " + q.quote(column.aliasType) + " ADD VALUE IF NOT EXISTS " + q.literal(value));
            }
        }
    }

    private boolean enumTypeExists(final String type) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM pg_type t JOIN pg_namespace n ON n.oid = t.typnamespace WHERE t.typname = ? AND t.typtype = 'e'")) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * UPDATE de uma célula numa tabela sem chave primária: a linha é encontrada pelo CTID.
     * Um UPDATE no PostgreSQL cria uma versão nova da linha com outro CTID, que é devolvido
     * ({@code RETURNING ctid}) para a grelha continuar a conseguir editar a mesma linha.
     */
    private boolean updateByCtid(final String table, final String column, final Object value, final String[] index) {
        final String sql = builder().update(table).set(column).where(getRowId()).returning("ctid").build();
        System.out.println(sql);
        initializeTime();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setParameter(ps, 1, value);
            setParameter(ps, 2, index[0]);
            try (ResultSet rs = ps.executeQuery()) {
                endTime();
                if (!rs.next()) {
                    MsgException = "No row matched, so nothing was saved. The row may have been changed or deleted: reload the table.";
                    return false;
                }
                index[0] = rs.getString(1);
            }
            putMessage(new Logger(getUsername(), sql, ps.getWarnings() != null ? ps.getWarnings().getMessage() : "", computeTime()));
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    final DatabaseUpdaterInterface updater = new DatabaseUpdaterInterface() {
        @Override
        public boolean updateData(String Table, final String column, final Object value, final String[] index, String PrimeKey, final String tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) return updateByCtid(Table, column, value, index);
            return updateCell(Table, column, value, List.of(PrimeKey), List.of(nullToEmpty(tmp)));
        }

        @Override
        public boolean updateData(String Table, final String column, final String value, final String[] index, final String type, String PrimeKey, final String tmp) {
            return updateData(Table, column, value, index, PrimeKey, tmp);
        }

        /** O que a grelha chama: estava a devolver false e nenhuma célula se gravava. */
        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, ArrayList<String> PrimeKey, ArrayList<String> tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) return updateByCtid(Table, column, value, index);
            return updateCell(Table, column, value, PrimeKey, tmp);
        }

        @Override
        public boolean updateData(String Table, final String column, final Object value, final String index, String PrimeKey, final String tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) return updateByCtid(Table, column, value, new String[]{index});
            return updateCell(Table, column, value, List.of(PrimeKey), List.of(nullToEmpty(tmp)));
        }

        @Override
        public String getException() {
            return GetException();
        }

        @Override
        public boolean updateData(String Table, HashMap<String, String> data, long index) {
            MsgException = "PostgreSQL rows are identified by CTID or primary key, not by a number.";
            return false;
        }

        @Override
        public boolean updateData(String Table, String column, String value, long index) {
            MsgException = "PostgreSQL rows are identified by CTID or primary key, not by a number.";
            return false;
        }

        @Override
        public boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp) {
            return false;
        }

        @Override
        public boolean updateData(String Table, String column, Object value, long index, String PrimeKey, String tmp) {
            if (PrimeKey == null || PrimeKey.isEmpty()) {
                MsgException = "PostgreSQL rows are identified by CTID or primary key, not by a number.";
                return false;
            }
            return updateCell(Table, column, value, List.of(PrimeKey), List.of(nullToEmpty(tmp)));
        }
    };

    @Override
    public long totalPages(String table) {
        try (Statement stmt = connection.createStatement(); ResultSet ret = stmt.executeQuery(builder().count(table))) {
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
    public long totalPages(String table, ArrayList<String> columns, String condition) {
        return totalPages(table, columns.getFirst(), condition);
    }

    @Override
    public long totalPages(String table, String column, String condition) {
        final QueryBuilder q = builder();
        final String sql = "SELECT COUNT(" + q.name(column) + ") FROM " + q.quote(table) + " " + (condition == null ? "" : condition);
        try (Statement stmt = connection.createStatement(); ResultSet ret = stmt.executeQuery(sql)) {
            if (ret.next()) {
                return pages(ret.getLong(1));
            }
            return 0;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return -1;
        }
    }

    /** O PostgreSQL não deixa mudar o nome da base de dados a que se está ligado. */
    @Override
    public void renameDatabase(String name) {
        try {
            execute("ALTER DATABASE " + quote(super.databaseName) + " RENAME TO " + quote(name));
            super.databaseName = name;
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    final DatabaseInserterInterface inserterInterface = new DatabaseInserterInterface() {
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
                MsgException = e.getMessage();
                return false;
            }
            return true;
        }

        @Override
        public boolean removeData(String Table, HashMap<String, String> data, ArrayList<Long> rowid) {
            return false;
        }

        /** Apaga pelo CTID. Para apagar pela chave primária usa-se {@link #deleteRows}. */
        @Override
        public boolean removeData(String Table, ArrayList<String> rowid) {
            final List<List<String>> values = new ArrayList<>();
            for (final String id : rowid) values.add(List.of(id));
            return deleteRows(Table, List.of(getRowId()), values);
        }

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
    };

    @Override
    public ArrayList<String> getTables() {
        ArrayList<String> TablesName = new ArrayList<>();
        try (ResultSet tabledMeta = connection.getMetaData().getTables(null, schema, "%", new String[]{"TABLE"})) {
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
    public ArrayList<String> getColumnsName(String Table) {
        ArrayList<String> TablesMetadata = new ArrayList<>();
        try (ResultSet columns = connection.getMetaData().getColumns(null, schema, Table, null)) {
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

    /** Colunas com um índice UNIQUE só delas (a chave primária não conta). */
    @Override
    protected HashMap<String, Boolean> isUnique(String Table) {
        HashMap<String, Boolean> ColumnsUnique = new HashMap<>();
        try {
            for (final IndexMetadata index : readIndexes(Table)) {
                if (index.unique && !index.implicit && index.columns.size() == 1) ColumnsUnique.put(index.columns.getFirst(), true);
            }
        } catch (Exception e) {
            return null;
        }
        return ColumnsUnique;
    }

    private String ruleToString(short rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade   -> "CASCADE";
            case DatabaseMetaData.importedKeyRestrict  -> "RESTRICT";
            case DatabaseMetaData.importedKeySetNull   -> "SET NULL";
            case DatabaseMetaData.importedKeyNoAction  -> "NO ACTION";
            case DatabaseMetaData.importedKeySetDefault-> "SET DEFAULT";
            default                                     -> "";
        };
    }

    /** Chaves estrangeiras de uma coluna só (as compostas não cabem no ColumnMetadata). */
    @Override
    protected HashMap<String, ColumnMetadata.Foreign> getForeign(final String Table) {
        final LinkedHashMap<String, List<Map.Entry<String, ColumnMetadata.Foreign>>> keys = new LinkedHashMap<>();
        try (ResultSet foreignKeys = connection.getMetaData().getImportedKeys(null, schema, Table)) {
            while (foreignKeys.next()) {
                ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
                foreign.isForeign = true;
                foreign.tableRef = foreignKeys.getString("PKTABLE_NAME");
                foreign.columnRef = foreignKeys.getString("PKCOLUMN_NAME");
                foreign.onEliminate = ruleToString(foreignKeys.getShort("DELETE_RULE"));
                foreign.onUpdate = ruleToString(foreignKeys.getShort("UPDATE_RULE"));
                keys.computeIfAbsent(foreignKeys.getString("FK_NAME"), _ -> new ArrayList<>())
                        .add(Map.entry(foreignKeys.getString("FKCOLUMN_NAME"), foreign));
            }
        } catch (Exception e) {
            System.out.println("Could not read foreign keys: " + e.getMessage());
            return null;
        }
        final HashMap<String, ColumnMetadata.Foreign> ColumnForeign = new HashMap<>();
        for (final List<Map.Entry<String, ColumnMetadata.Foreign>> columns : keys.values()) {
            if (columns.size() == 1) ColumnForeign.put(columns.getFirst().getKey(), columns.getFirst().getValue());
        }
        return ColumnForeign;
    }

    /**
     * Tipo de cada coluna que é um ENUM (coluna → nome do tipo). Antes a lista vinha por
     * posição de uma consulta sem ORDER BY e era emparelhada com as colunas pelo índice.
     */
    private HashMap<String, String> searchTypes(final String table) throws SQLException {
        final HashMap<String, String> enums = new HashMap<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT a.attname, t.typname
                FROM pg_attribute a
                JOIN pg_class c ON c.oid = a.attrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_type t ON t.oid = a.atttypid
                WHERE c.relname = ? AND n.nspname = current_schema()
                  AND a.attnum > 0 AND NOT a.attisdropped AND t.typtype = 'e'""")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) enums.put(rs.getString(1), rs.getString(2));
            }
        }
        return enums;
    }

    private ArrayList<String> EnumChecks(final String type) throws SQLException {
        final ArrayList<String> enums = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT e.enumlabel FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid WHERE t.typname = ? ORDER BY e.enumsortorder")) {
            ps.setString(1, type);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) enums.add(rs.getString(1));
            }
        }
        return enums;
    }

    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(String Table) {
        final ArrayList<ColumnMetadata> ColumnsMetadata = new ArrayList<>();
        try {
            final ArrayList<String> PrimaryKeyList = PrimaryKeyList(Table);
            final HashMap<String, ColumnMetadata.Foreign> ForeignKeyList = getForeign(Table);
            final HashMap<String, Boolean> uniqueColumns = isUnique(Table);
            final HashMap<String, String> enums = searchTypes(Table);
            final ArrayList<IndexMetadata> indexes = readIndexes(Table);

            try (ResultSet columns = connection.getMetaData().getColumns(null, schema, Table, null)) {
                while (columns.next()) {
                    final ColumnMetadata column = new ColumnMetadata();
                    column.Name = columns.getString("COLUMN_NAME");
                    if (enums.containsKey(column.Name)) {
                        column.Type = "ENUM";
                        column.aliasType = enums.get(column.Name);
                        column.items = EnumChecks(column.aliasType);
                    } else {
                        column.Type = columns.getString("TYPE_NAME").toUpperCase(Locale.ROOT);
                    }
                    column.defaultValue = nullToEmpty(columns.getString("COLUMN_DEF"));
                    column.size = columns.getInt("COLUMN_SIZE");
                    column.decimalDigits = columns.getInt("DECIMAL_DIGITS");
                    column.integerDigits = Math.max(0, column.size - column.decimalDigits);
                    // NULLABLE == columnNullable quer dizer que ACEITA null: o NOT NULL é o contrário.
                    column.NOT_NULL = columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
                    if ("YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT"))) column.autoincrement = 1;
                    column.comment = nullToEmpty(columns.getString("REMARKS"));

                    column.IsPrimaryKey = PrimaryKeyList != null && PrimaryKeyList.contains(column.Name);
                    if (ForeignKeyList != null && ForeignKeyList.containsKey(column.Name)) column.foreign = ForeignKeyList.get(column.Name);
                    column.isUnique = uniqueColumns != null && uniqueColumns.containsKey(column.Name);
                    for (final IndexMetadata index : indexes) {
                        if (!index.unique && !index.implicit && index.columns.size() == 1 && index.columns.getFirst().equals(column.Name)) {
                            column.index = index.Name;
                            break;
                        }
                    }
                    ColumnsMetadata.add(column);
                }
            }
            return ColumnsMetadata;
        } catch (SQLException e) {
            System.out.println("errorrrrr " + e.getMessage());
            MsgException = e.getMessage();
            return null;
        }
    }

    @Override
    public boolean TableisPimeKey(String TableName) {
        final ArrayList<String> keys = PrimaryKeyList(TableName);
        return keys != null && keys.contains(TableName);
    }

    @Override
    public boolean TableHasPrimeKey(String TableName) {
        final ArrayList<String> keys = PrimaryKeyList(TableName);
        return keys != null && !keys.isEmpty();
    }

    /** Colunas da chave primária, pela ordem da chave. */
    @Override
    public ArrayList<String> PrimaryKeyList(String Table) {
        final TreeMap<Short, String> keys = new TreeMap<>();
        try (ResultSet primaryKeys = connection.getMetaData().getPrimaryKeys(null, schema, Table)) {
            while (primaryKeys.next()) {
                keys.put(primaryKeys.getShort("KEY_SEQ"), primaryKeys.getString("COLUMN_NAME"));
            }
            return new ArrayList<>(keys.values());
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
    }

    @Override
    public boolean connect(String url, String userName, String password) {
        final String completeURL = "jdbc:postgresql://" + url;
        try {
            connection = DriverManager.getConnection(completeURL, userName, password);
            statement = connection.createStatement();
            Url = completeURL;
            username = userName;
            readSchema();
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /**
     * Cria a base de dados ligando-se à "postgres" (o CREATE DATABASE não pode correr dentro
     * da base que se cria) e depois liga-se à nova.
     */
    @Override
    public boolean CreateSchema(String url, String name, String userName, String password, Map<String, String> modes) {
        final String server = "jdbc:postgresql://" + (url.endsWith("/") ? url : url + "/");
        try (Connection maintenance = DriverManager.getConnection(server + "postgres", userName, password);
             Statement stmt = maintenance.createStatement()) {
            stmt.execute("CREATE DATABASE " + quote(name));
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        if (!connect(url.endsWith("/") ? url : url + "/", name, userName, password, false)) return false;
        final String script = modes == null ? null : modes.get("innit");
        try {
            executeScript(script);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
        return true;
    }

    /** O PostgreSQL usa sslmode; o useSSL/requireSSL que ia no URL é do MySQL e era ignorado. */
    @Override
    public boolean connect(String url, String name, String userName, String password, boolean ssl) {
        final String completeURL = "jdbc:postgresql://" + url + name + (ssl ? "?sslmode=require" : "");
        try {
            connection = DriverManager.getConnection(completeURL, userName, password);
            statement = connection.createStatement();
            Url = completeURL;
            databaseName = name;
            username = userName;
            readSchema();
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    private void readSchema() throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT current_schema()")) {
            schema = rs.next() ? rs.getString(1) : "public";
        }
    }

    @Override
    public String getUrl() {
        return Url == null ? "" : Url;
    }

    /** Funções do esquema; o código fica no formato que o createFunction espera (depois do nome). */
    @Override
    public ArrayList<FunctionController.Function> getFunctions() {
        final ArrayList<FunctionController.Function> functions = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT p.proname, pg_get_function_result(p.oid) AS result, l.lanname, p.prosrc,
                            pg_get_userbyid(p.proowner) AS owner
                     FROM pg_proc p
                     JOIN pg_namespace n ON n.oid = p.pronamespace
                     JOIN pg_language l ON l.oid = p.prolang
                     WHERE n.nspname = current_schema() AND p.prokind = 'f'
                     ORDER BY p.proname""")) {
            while (rs.next()) {
                final String code = "RETURNS " + rs.getString("result") + "\nLANGUAGE " + rs.getString("lanname")
                        + "\nAS $$" + rs.getString("prosrc") + "$$";
                functions.add(new FunctionController.Function(rs.getString("proname"), code, rs.getString("owner")));
            }
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
        return functions;
    }

    @Override
    public ArrayList<ProcedureController.Procedure> getProcedure() {
        final ArrayList<ProcedureController.Procedure> procedures = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT p.proname, pg_get_functiondef(p.oid)
                     FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                     WHERE n.nspname = current_schema() AND p.prokind = 'p'
                     ORDER BY p.proname""")) {
            while (rs.next()) procedures.add(new ProcedureController.Procedure(rs.getString(1), rs.getString(2)));
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
        return procedures;
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid) {
        final ColumnMetadata id = new ColumnMetadata();
        id.Name = "id";
        id.Type = "INTEGER";
        id.IsPrimaryKey = !rowid;
        try {
            execute(builder().createTable(table).temporary(temporary).column(id).build());
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

    /** CREATE TABLE, os tipos ENUM de que precisa, os comentários e os índices, tudo ou nada. */
    @Override
    public boolean createTable(TableMetadata metadata, boolean temporary, boolean rowid) {
        try {
            for (final ColumnMetadata column : metadata.getColumnMetadata()) prepareColumnType(column);
            final String command = builder().createTable(metadata.getName())
                    .temporary(temporary)
                    .columns(metadata.getColumnMetadata())
                    .check(metadata.getCheck())
                    .build();
            runInTransaction(() -> {
                execute(command);
                for (final ColumnMetadata column : metadata.getColumnMetadata()) {
                    if (column.comment != null && !column.comment.isBlank()) {
                        for (final String sql : builder().alterTable(metadata.getName()).comment(column.Name, column.comment).build()) execute(sql);
                    }
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
    public void back() {
        try {
            connection.rollback();
        } catch (SQLException e) {

        }
    }

    /** Vistas do esquema que usam a tabela. */
    @Override
    public ArrayList<ViewController.View> getViews(String table) throws SQLException {
        final ArrayList<ViewController.View> views = new ArrayList<>();
        for (final ViewController.View view : getViews()) {
            if (view.code.get().contains(" " + table + " ") || view.code.get().contains(" " + table + ".")
                    || view.code.get().contains("\"" + table + "\"")) views.add(view);
        }
        return views;
    }

    @Override
    public ArrayList<ViewController.View> getViews() throws SQLException {
        final ArrayList<ViewController.View> views = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT table_name, view_definition FROM information_schema.views WHERE table_schema = current_schema()")) {
            while (rs.next()) {
                // O PostgreSQL devolve a definição em várias linhas e com ";" no fim.
                final String code = QueryBuilder.stripTerminator(nullToEmpty(rs.getString(2)).replaceAll("\\s+", " ")) + " ";
                views.add(new ViewController.View(rs.getString(1), null, code));
            }
        }
        return views;
    }

    /** No PostgreSQL um trigger pertence a uma tabela: o DROP precisa dela. */
    private String tableOfTrigger(final String trigger) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT c.relname FROM pg_trigger t
                JOIN pg_class c ON c.oid = t.tgrelid
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE t.tgname = ? AND NOT t.tgisinternal AND n.nspname = current_schema()""")) {
            ps.setString(1, trigger);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Override
    public void createTrigger(String trigger, String code) {
        try {
            runInTransaction(() -> {
                final String table = tableOfTrigger(trigger);
                if (table != null) execute(builder().dropTrigger(trigger, table));
                execute(code);
            });
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    @Override
    public void removeTrigger(String trigger) throws SQLException {
        final String table = tableOfTrigger(trigger);
        if (table == null) throw new SQLException("Trigger " + trigger + " not found.");
        execute(builder().dropTrigger(trigger, table));
    }

    @Override
    public void createEvent(String event, String code) {
        MsgException = "PostgreSQL has no scheduled events.";
    }

    @Override
    public void removeEvent(String event) throws SQLException {
        throw new SQLFeatureNotSupportedException("PostgreSQL has no scheduled events.");
    }

    /** Estavam vazios: o botão de criar índice não produzia comando nenhum. */
    @Override
    public void createIndex(String table, ArrayList<String> columns, String indexName, String mode) throws SQLException {
        execute(builder().createIndex(indexName, table, columns, mode));
    }

    @Override
    public void createIndex(String table, String column, String indexName, String mode) throws SQLException {
        createIndex(table, new ArrayList<>(List.of(column)), indexName, mode);
    }

    @Override
    public void removeIndex(String indexName) throws SQLException {
        execute(builder().dropIndex(indexName, null));
    }

    @Override
    public ArrayList<CheckMetadata> getChecks(final String table) throws SQLException {
        ArrayList<CheckMetadata> checks = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT con.conname, pg_get_constraintdef(con.oid) AS definition
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                WHERE con.contype = 'c' AND rel.relname = ? AND nsp.nspname = current_schema()""")) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    // pg_get_constraintdef devolve "CHECK ((expr))"; guarda-se só a expressão.
                    String definition = rs.getString("definition");
                    if (definition != null && definition.toUpperCase(Locale.ROOT).startsWith("CHECK")) {
                        definition = definition.substring(definition.indexOf('(') + 1,
                                definition.lastIndexOf(')')).trim();
                    }
                    checks.add(new CheckMetadata(rs.getString("conname"), table, definition));
                }
            }
        }
        return checks;
    }

    @Override
    public String getTableCheck(String table) throws SQLException {
        final ArrayList<CheckMetadata> checks = getChecks(table);
        return checks.isEmpty() ? "" : checks.getFirst().expression;
    }

    /**
     * Gestão de contas no PostgreSQL.
     *
     * <p>Não existia implementação nenhuma: {@code database.Permission()} devolvia null e o
     * painel de configuração rebentava com NullPointerException mal se clicava em
     * "Permission" numa ligação PostgreSQL.</p>
     *
     * <p>O modelo é diferente do MySQL — não há contas por máquina de origem (isso vive no
     * {@code pg_hba.conf}), há papéis (roles) que podem ou não iniciar sessão, e o âmbito
     * de uma tabela é o esquema, não a base de dados.</p>
     */
    private final PermissionPragmaInterface permissionPragmaInterface = new PermissionPragmaInterface() {

        private final List<String> PRIVILEGES = List.of(
                "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER");

        @Override
        public List<String> supportedPrivileges() {
            return PRIVILEGES;
        }

        @Override
        public List<String> authenticationMethods() {
            // O método é decidido pelo pg_hba.conf, não pelo comando de criação.
            return List.of("password");
        }

        @Override
        public boolean canManageUsers() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT rolsuper OR rolcreaterole AS can_manage"
                                 + " FROM pg_roles WHERE rolname = current_user")) {
                return rs.next() && rs.getBoolean("can_manage");
            }
        }

        @Override
        public Map<String, permissionConfController.userInformation> getUsers() throws SQLException {
            Map<String, permissionConfController.userInformation> users = new LinkedHashMap<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(
                         "SELECT rolname, rolsuper, rolname = current_user AS is_current"
                                 + " FROM pg_roles WHERE rolcanlogin ORDER BY rolname")) {
                while (rs.next()) {
                    // O host fica vazio de propósito: no PostgreSQL a conta não está presa
                    // a uma origem, e inventar um "%" daria a ideia errada.
                    permissionConfController.userInformation user =
                            new permissionConfController.userInformation(
                                    rs.getString("rolname"), "", "",
                                    rs.getBoolean("rolsuper") ? "superuser" : "login",
                                    rs.getBoolean("is_current"));
                    users.put(user.toString(), user);
                }
            }
            return users;
        }

        @Override
        public List<String> getPermissions(String user) throws SQLException {
            List<String> grants = new ArrayList<>();
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT table_schema, table_name, privilege_type"
                            + " FROM information_schema.table_privileges"
                            + " WHERE grantee = ? ORDER BY table_schema, table_name")) {
                stmt.setString(1, roleName(user));
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        grants.add(String.format("GRANT %s ON %s.%s TO %s",
                                rs.getString("privilege_type"), rs.getString("table_schema"),
                                rs.getString("table_name"), roleName(user)));
                    }
                }
            }
            return grants;
        }

        @Override
        public Map<String, Boolean> getPermissions(String user, String db, String table) throws SQLException {
            Map<String, Boolean> permissions = new LinkedHashMap<>();
            for (String privilege : PRIVILEGES) permissions.put(privilege, false);

            String role = roleName(user);

            if (table != null && !table.isBlank()) {
                // has_table_privilege resolve a herança de papéis, ao contrário de ler os grants.
                for (String privilege : PRIVILEGES) {
                    try (PreparedStatement stmt = connection.prepareStatement(
                            "SELECT has_table_privilege(?, ?, ?) AS granted")) {
                        stmt.setString(1, role);
                        stmt.setString(2, qualified(table));
                        stmt.setString(3, privilege);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) permissions.put(privilege, rs.getBoolean("granted"));
                        }
                    } catch (SQLException _) {
                        // Tabela inexistente ou sem visibilidade: fica a falso.
                    }
                }
                return permissions;
            }

            // Sem tabela indicada, o privilégio conta como presente se valer em todas
            // as tabelas do esquema — é o que o utilizador espera de um âmbito "toda a BD".
            for (String privilege : PRIVILEGES) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT bool_and(has_table_privilege(?, c.oid, ?)) AS granted"
                                + " FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace"
                                + " WHERE c.relkind = 'r' AND n.nspname = current_schema()")) {
                    stmt.setString(1, role);
                    stmt.setString(2, privilege);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) permissions.put(privilege, rs.getBoolean("granted"));
                    }
                } catch (SQLException _) {
                    // Esquema vazio: bool_and devolve null e o privilégio fica a falso.
                }
            }
            return permissions;
        }

        @Override
        public void addUser(permissionConfController.userInformation user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("CREATE ROLE %s LOGIN PASSWORD %s",
                        identifier(user.name), literal(user.password)));
            }
        }

        @Override
        public void dropUser(String user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP ROLE " + identifier(roleName(user)));
            }
        }

        @Override
        public void changePassword(permissionConfController.userInformation user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("ALTER ROLE %s WITH PASSWORD %s",
                        identifier(user.name), literal(user.password)));
            }
        }

        @Override
        public void grant(String privilege, String db, String table, String user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("GRANT %s ON %s TO %s",
                        privilegeKeyword(privilege), scope(table), identifier(roleName(user))));
            }
        }

        @Override
        public void revoke(String privilege, String db, String table, String user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("REVOKE %s ON %s FROM %s",
                        privilegeKeyword(privilege), scope(table), identifier(roleName(user))));
            }
        }

        /** Uma tabela concreta, ou todas as do esquema atual. */
        private String scope(String table) {
            if (table == null || table.isBlank()) return "ALL TABLES IN SCHEMA current_schema";
            return identifier(table);
        }

        private String privilegeKeyword(String privilege) {
            if (!PRIVILEGES.contains(privilege)) {
                throw new IllegalArgumentException("Unknown privilege: " + privilege);
            }
            return privilege;
        }

        /** A UI guarda contas como {@code 'nome'@'host'}; aqui só o nome interessa. */
        private String roleName(String user) {
            if (user == null) return "";
            String name = user;
            int at = name.indexOf("'@'");
            if (at >= 0) name = name.substring(0, at);
            return name.replace("'", "");
        }

        private String qualified(String table) {
            return table.contains(".") ? table : "\"" + table.replace("\"", "\"\"") + "\"";
        }

        private String identifier(String value) {
            return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
        }

        private String literal(String value) {
            return "'" + (value == null ? "" : value.replace("'", "''")) + "'";
        }
    };

    /** Definições de sessão que o painel de ligação mostra. */
    private final ConnectionPragmaInterface connectionPragmaInterface = new ConnectionPragmaInterface() {
        @Override
        public int getTimeout() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW statement_timeout")) {
                if (!rs.next()) return 0;
                return parseDuration(rs.getString(1));
            }
        }

        @Override
        public void setTimeout(int timeout) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET statement_timeout = " + (timeout * 1000L));
            }
        }

        /** O PostgreSQL devolve durações como "5s", "250ms" ou "0". */
        private int parseDuration(String value) {
            if (value == null || value.isBlank()) return 0;
            String text = value.trim().toLowerCase(Locale.ROOT);
            try {
                if (text.endsWith("ms")) {
                    return (int) (Double.parseDouble(text.substring(0, text.length() - 2)) / 1000);
                }
                if (text.endsWith("s")) return (int) Double.parseDouble(text.substring(0, text.length() - 1));
                if (text.endsWith("min")) {
                    return (int) (Double.parseDouble(text.substring(0, text.length() - 3)) * 60);
                }
                // Sem sufixo o valor vem em milissegundos.
                return (int) (Double.parseDouble(text) / 1000);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    };

    public PermissionPragmaInterface getPermissionPragma() {
        return permissionPragmaInterface;
    }

}
