package com.example.sqlide.drivers.Access;

import com.example.sqlide.Function.FunctionController;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.Metadata.IndexMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.Procedure.ProcedureController;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.drivers.model.QueryBuilder;
import com.example.sqlide.drivers.model.SQLTypes;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;

/**
 * Driver do MS Access (UCanAccess).
 *
 * <p>O SQL passa pelo {@link QueryBuilder}. Corrigido: o NOT NULL vinha invertido; o
 * {@code idType} era "COUNTER" (um tipo, não uma coluna), por isso a leitura de tabelas sem
 * chave pedia {@code SELECT COUNTER, *}; o apagar linhas assumia uma coluna chamada ID; e
 * vários métodos chamados pela interface normal (TableHasPrimeKey, getCommitMode,
 * createTable...) lançavam UnsupportedOperationException.</p>
 */
public class MS_accessDB extends DataBase {

    public MS_accessDB() {
        // O Access não tem uma pseudo-coluna que identifique a linha.
        super.idType = "";
        super.databaseInfo = new com.example.sqlide.drivers.Access.MS_accessInfo();
        SQLType = SQLTypes.MS_ACCESS;

        Updater(updater);
        Inserter(inserterInterface);
    }

    @Override
    public boolean connect(final String DBName, final Map<String, String> formatData) {
        try {
            driverUrl = "jdbc:ucanaccess://" + DBName + ".accdb";
            connection = DriverManager.getConnection(driverUrl);
            statement = connection.createStatement();
            DatabaseMetaData meta = connection.getMetaData();
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
            super.Url = "jdbc:ucanaccess://" + DBName;
            connection = DriverManager.getConnection(super.Url);
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

    @Override
    public HashMap<String, String> getTriggers() {
        return new HashMap<>(); // MS Access does not support triggers in the same way
    }

    @Override
    public HashMap<String, String> getEvents() {
        return new HashMap<>(); // MS Access does not support events
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
        id.Type = "COUNTER";
        id.IsPrimaryKey = true;
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
    public boolean renameTable(final String Table, final String newTableName) {
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

    @Override
    public boolean createColumn(String table, String column, ColumnMetadata meta, final boolean fill) {
        final ColumnMetadata added = meta.copy();
        added.Name = column;
        try {
            for (final String sql : builder().alterTable(table).addColumn(added, false).build()) execute(sql);
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
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

        @Override
        public boolean insertData(String Table, HashMap<String, String> data) {
            final List<String> columns = new ArrayList<>(data.keySet());
            final String query = builder().insert(Table).columns(columns).build();
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

        /** Sem rowid: as linhas apagam-se pela chave primária ({@link #deleteRows}). */
        @Override
        public boolean removeData(String Table, ArrayList<String> rowid) {
            return deleteRows(Table, keyColumns(List.of()), List.of());
        }
    };

    final DatabaseUpdaterInterface updater = new DatabaseUpdaterInterface() {
        @Override
        public boolean updateData(String Table, HashMap<String, String> data, final long index) {
            MsgException = "MS Access rows are updated by their primary key.";
            return false;
        }

        @Override
        public boolean updateData(String Table, final String column, final String value, final long index) {
            MsgException = "MS Access rows are updated by their primary key.";
            return false;
        }

        @Override
        public boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp) {
            return false;
        }

        @Override
        public boolean updateData(String Table, final String column, final Object value, final long index, String PrimeKey, final String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(tmp == null ? "" : tmp));
        }

        @Override
        public boolean updateData(String Table, String column, Object value, String[] index, String PrimeKey, String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(tmp == null ? "" : tmp));
        }

        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, String PrimeKey, String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(tmp == null ? "" : tmp));
        }

        /** O que a grelha chama (lançava UnsupportedOperationException). */
        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, ArrayList<String> PrimeKey, ArrayList<String> tmp) {
            return updateCell(Table, column, value, keyColumns(PrimeKey), tmp == null ? List.of() : tmp);
        }

        @Override
        public boolean updateData(String Table, final String column, final Object value, final String index, String PrimeKey, final String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(tmp == null ? "" : tmp));
        }

        @Override
        public String getException() {
            return GetException();
        }
    };

    private static List<String> single(final String key) {
        return key == null || key.isBlank() ? List.of() : List.of(key);
    }

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
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
    }

    @Override
    protected HashMap<String, Boolean> isUnique(final String Table) {
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

    @Override
    protected HashMap<String, ColumnMetadata.Foreign> getForeign(final String Table) {
        HashMap<String, ColumnMetadata.Foreign> ColumnForeign = new HashMap<>();
        try (ResultSet foreignKeys = connection.getMetaData().getImportedKeys(null, null, Table)) {
            while (foreignKeys.next()) {
                ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
                final String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                foreign.isForeign = true;
                foreign.tableRef = foreignKeys.getString("PKTABLE_NAME");
                foreign.columnRef = foreignKeys.getString("PKCOLUMN_NAME");
                short updateRule = foreignKeys.getShort("UPDATE_RULE");
                short deleteRule = foreignKeys.getShort("DELETE_RULE");
                foreign.onEliminate = ruleToString(deleteRule);
                foreign.onUpdate = ruleToString(updateRule);
                ColumnForeign.put(fkColumnName, foreign);
            }
        } catch (Exception e) {
            return null;
        }
        return ColumnForeign;
    }

    private String ruleToString(short rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            default -> "";
        };
    }

    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(final String Table) {
        ArrayList<ColumnMetadata> ColumnsMetadata = new ArrayList<>();
        final ArrayList<String> PrimaryKeyList = PrimaryKeyList(Table);
        final HashMap<String, ColumnMetadata.Foreign> ForeignKeyList = getForeign(Table);
        final HashMap<String, Boolean> uniqueColumns = isUnique(Table);
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, Table, null)) {
            while (columns.next()) {
                final ColumnMetadata column = new ColumnMetadata();
                column.Name = columns.getString("COLUMN_NAME");
                column.Type = columns.getString("TYPE_NAME");
                column.defaultValue = columns.getString("COLUMN_DEF") == null ? "" : columns.getString("COLUMN_DEF");
                column.size = columns.getInt("COLUMN_SIZE");
                column.decimalDigits = columns.getInt("DECIMAL_DIGITS");
                column.integerDigits = Math.max(0, column.size - column.decimalDigits);
                // NULLABLE == columnNullable quer dizer que ACEITA null: o NOT NULL é o contrário.
                column.NOT_NULL = columns.getInt("NULLABLE") == DatabaseMetaData.columnNoNulls;
                if ("YES".equalsIgnoreCase(columns.getString("IS_AUTOINCREMENT"))) column.autoincrement = 1;
                column.IsPrimaryKey = PrimaryKeyList != null && PrimaryKeyList.contains(column.Name);
                if (ForeignKeyList != null && ForeignKeyList.containsKey(column.Name)) column.foreign = ForeignKeyList.get(column.Name);
                column.isUnique = uniqueColumns != null && uniqueColumns.containsKey(column.Name);
                ColumnsMetadata.add(column);
            }
            return ColumnsMetadata;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
    }

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

    @Override
    public ArrayList<String> PrimaryKeyList(final String Table) {
        final TreeMap<Short, String> keys = new TreeMap<>();
        try (ResultSet primaryKeys = connection.getMetaData().getPrimaryKeys(null, null, Table)) {
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
        MsgException = "MS Access databases are opened from a file.";
        return false;
    }

    @Override
    public boolean CreateSchema(String url, String name, String userName, String password, Map<String, String> modes) {
        MsgException = "MS Access databases are created from a file.";
        return false;
    }

    @Override
    public boolean connect(String url, String name, String userName, String password, boolean ssl) {
        MsgException = "MS Access databases are opened from a file.";
        return false;
    }

    @Override
    public String getUrl() {
        return super.Url;
    }

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
        return createTable(table);
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid, ArrayList<ColumnMetadata> columnMetadata) {
        final TableMetadata metadata = new TableMetadata(table);
        metadata.addColumns(columnMetadata);
        return createTable(metadata, temporary, rowid);
    }

    @Override
    public boolean createTable(final TableMetadata metadata, boolean temporary, boolean rowid) {
        try {
            execute(builder().createTable(metadata.getName()).columns(metadata.getColumnMetadata()).check(metadata.getCheck()).build());
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
        return new ArrayList<>();
    }

    @Override
    public ArrayList<ViewController.View> getViews() throws SQLException {
        return new ArrayList<>();
    }

    @Override
    public void createTrigger(final String trigger, final String code) {
        MsgException = "MS Access has no triggers.";
    }

    @Override
    public void removeTrigger(final String trigger) throws SQLException {
        throw new SQLFeatureNotSupportedException("MS Access has no triggers.");
    }

    @Override
    public void createEvent(final String event, final String code) {
        MsgException = "MS Access has no scheduled events.";
    }

    @Override
    public void removeEvent(final String event) throws SQLException {
        throw new SQLFeatureNotSupportedException("MS Access has no scheduled events.");
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
    public String getTableCheck(final String table) throws SQLException {
        return null;
    }
}
