package com.example.sqlide.drivers.model;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;

import java.io.*;
import java.sql.*;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class DataBase {
    protected String databaseName;
    protected String Url;
    protected String driverUrl;
    protected String username;
    protected String password;
    protected String host;
    protected int port;
    protected Connection connection;
    protected Statement statement;
    public int buffer = 250;
    protected String idType;
    protected BlockingQueue<Logger> sender = new LinkedBlockingQueue<>();
    protected SQLTypes SQLType;

    protected String[] indexModes;
    protected ArrayList<String> foreignModes;

    private final DatabaseFetcherInterface databaseFetcherInterface = new DatabaseFetcherInterface() {
        @Override
        public synchronized ArrayList<DataForDB> fetchData(final String Table, ArrayList<String> Columns, final long offset, final String primeKey) {
            Columns = new ArrayList<>(Columns);
            ArrayList<DataForDB> data = new ArrayList<>();
            String rowid = "";
            if (primeKey.isEmpty()) {
                rowid = getRowId()+",";
                Columns.add(getRowId());
            } else {
                if (!Columns.contains(primeKey)) {
                    Columns.add(primeKey);
                }
            }
            final String command = "SELECT " + rowid + " * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
            System.out.println("command " + command);
            try {
                initializeTime();
                ResultSet rs = statement.executeQuery(command);
                endTime();
                while (rs.next()) {
                    HashMap<String, String> tmpData = new HashMap<>();
                    for (final String col : Columns) {
                        System.out.println(col);
                        Object val = rs.getObject(col);
                        String valStr = "null";
                        if (val != null) {
                            valStr = val.toString();
                        }
                        System.out.println(valStr);
                        tmpData.put(col, valStr);
                    }
                    data.add(new DataForDB(tmpData));
                }
                putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return  data;
            } catch (SQLException e) {
                System.err.println(e.getMessage());
                return null;
            }
        }

        @Override
        public synchronized ArrayList<DataForDB> fetchData(String command, ArrayList<String> Columns, final String primeKey) {
            ArrayList<DataForDB> data = new ArrayList<>();
            try {
                System.out.println(command);
                ResultSet rs = statement.executeQuery(command);
                ResultSetMetaData metaData = rs.getMetaData();

                // 1. Obter colunas reais do ResultSet
                Set<String> realColumns = new HashSet<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    realColumns.add(metaData.getColumnName(i));
                    System.out.println("label " + metaData.getColumnName(i));
                    System.out.println("label2 " + metaData.getColumnLabel(i));
                    System.out.println("label3 " + metaData.getColumnClassName(i));
                }

                // 2. Ajustar lista de colunas para incluir apenas colunas existentes
                ArrayList<String> validColumns = new ArrayList<>(realColumns);

                // 3. Lógica de chave primária
                String keyToCheck = getRowId();
                boolean keyExists = realColumns.contains(keyToCheck);

                // 4. Adicionar chave se necessário
                if (keyExists) {
                    if (primeKey.isEmpty() && !validColumns.contains(getRowId())) {
                        validColumns.add(getRowId());
                    } else if (!primeKey.isEmpty() && !validColumns.contains(primeKey)) {
                        validColumns.add(primeKey);
                    }
                }

                // 5. Coletar dados
                while (rs.next()) {
                    HashMap<String, String> row = new HashMap<>();
                    for (String col : validColumns) {
                        Object value = rs.getObject(col);
                        row.put(col, (value != null) ? value.toString() : "null");
                    }
                    data.add(new DataForDB(row));
                }

                // 6. Atualizar lista Columns
                Columns.clear();
                //  Columns.addAll(validColumns);
                Columns.addAll(validColumns);

                for (String column : Columns) {
                    System.out.println("giygiy " + column);
                }

                // 7. Remover chave se não existir
                if (!keyExists) {
                    Columns.removeIf(col -> col.equalsIgnoreCase(keyToCheck));
                }

                return data;

            } catch (SQLException e) {
                MsgException = e.getMessage();
                return null;
            }
        }

        // faz um fetch das colunas que esta em code e as colunas na Columns e se a primeKey não estiver em code remove para Columns não o ter
 /*   @Override
    public synchronized ArrayList<DataForDB> fetchData(String command, ArrayList<String> Columns, final String primeKey) {
        ArrayList<DataForDB> data = new ArrayList<>();
        ArrayList<String> columns = new ArrayList<>();
        String rowid = "";
        boolean remove = false;
        if (primeKey.isEmpty()) {
            rowid = "ROWID,";
            Columns.add("ROWID");
            if (!command.contains(primeKey.toUpperCase())) {
                remove = true;
            }
        } else {
            if (!Columns.contains(primeKey)) {
                Columns.add(primeKey);
            }
        }
      //  final String command = "SELECT " + rowid + " * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
        System.out.println("command " + command);
        try {
            ResultSet rs = statement.executeQuery(command);
            while (rs.next()) {
                HashMap<String, String> tmpData = new HashMap<>();
                for (final String col : Columns) {
                    System.out.println(col);
                    Object val = rs.getObject(col);
                    String valStr = "null";
                    if (val != null) {
                        valStr = val.toString();
                    }
                    System.out.println(valStr);
                    tmpData.put(col, valStr);
                }
                data.add(new DataForDB(tmpData));
            }

            columns = data.getFirst().getColumns();

            if (remove) {
                columns.remove(primeKey.toUpperCase());
            }
            Columns = columns;

            return data;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }*/

        @Override
        public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long offset, boolean primeKey) {
            return null;
        }

        @Override
        public synchronized ArrayList<HashMap<String, String>> fetchDataMap(final String Table, final ArrayList<String> Columns, final long limit, final long offset, final boolean PrimeKey) {
            ArrayList<HashMap<String, String>> data = new ArrayList<>();
            String rowid = "";
            if (!PrimeKey) {
                rowid = getRowId()+",";
                Columns.add(getRowId());
            }
            final String command = "SELECT " + rowid + " * FROM " + Table + " LIMIT " + limit + " OFFSET " + offset;
            System.out.println("command " + command);
            try {
                ResultSet rs = statement.executeQuery(command);
                if (!rs.next()) {
                    return null;
                }
                do {
                    HashMap<String, String> tmpData = new HashMap<>();
                    for (final String col : Columns) {
                        System.out.println(col);
                        Object val = rs.getObject(col);
                        String valStr = "null";
                        if (val != null) {
                            valStr = val.toString();
                        }
                        System.out.println(valStr);
                        tmpData.put(col, valStr);
                    }
                    data.add(tmpData);
                } while (rs.next());
                return data;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public ArrayList<HashMap<String, String>> fetchDataMap(String command, ArrayList<String> Columns, long limit, long offset) {
            ArrayList<HashMap<String, String>> data = new ArrayList<>();
            command = command.replace(";", "");
            command += " LIMIT " + limit + " OFFSET " + offset + ";";
            System.out.println("command " + command);
            try {
                initializeTime();
                ResultSet rs = statement.executeQuery(command);
                endTime();
                if (!rs.next()) {
                    return null;
                }
                do {
                    HashMap<String, String> tmpData = new HashMap<>();
                    for (final String col : Columns) {
                        System.out.println(col);
                        Object val = rs.getObject(col);
                        String valStr = "null";
                        if (val != null) {
                            valStr = val.toString();
                        }
                        System.out.println(valStr);
                        tmpData.put(col, valStr);
                    }
                    data.add(tmpData);
                } while (rs.next());
                putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return data;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

   /* @Override
    public synchronized ArrayList<HashMap<String, String>> fetchDataMap(final String Table, final ArrayList<String> Columns, final long limit, final long offset) {
        ArrayList<HashMap<String, String>> data = new ArrayList<>();
        final String command = "SELECT " + " * FROM " + Table + " LIMIT " + limit + " OFFSET " + offset;
        System.out.println("command " + command);
        try {
            ResultSet rs = statement.executeQuery(command);
            if (!rs.next()) {
                return null;
            }
            do {
                HashMap<String, String> tmpData = new HashMap<>();
                for (final String col : Columns) {
                    System.out.println(col);
                    Object val = rs.getObject(col);
                    String valStr = "null";
                    if (val != null) {
                        valStr = val.toString();
                    }
                    System.out.println(valStr);
                    tmpData.put(col, valStr);
                }
                data.add(tmpData);
            } while (rs.next());
            return data;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    } */

        @Override
        public synchronized ArrayList<HashMap<String, String>> fetchDataMap(final String Command, final long limit, final long offset) {
            ArrayList<HashMap<String, String>> data = new ArrayList<>();
            final String command = Command + " LIMIT " + limit + " OFFSET " + offset + ";";
            System.out.println("command " + command);
            initializeTime();
            try (final ResultSet rs = statement.executeQuery(command)) {
                endTime();
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData metaData = rs.getMetaData();

                ArrayList<String> Columns = new ArrayList<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    Columns.add(metaData.getColumnName(i));
                }
                do {
                    HashMap<String, String> tmpData = new HashMap<>();
                    for (final String col : Columns) {
                        System.out.println(col);
                        Object val = rs.getObject(col);
                        String valStr = "null";
                        if (val != null) {
                            valStr = val.toString();
                        }
                        //    System.out.println(valStr);
                        tmpData.put(col, valStr);
                    }
                    data.add(tmpData);
                } while (rs.next());
                putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return data;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized ArrayList<HashMap<String, String>> fetchRawDataMap(final String Command) {
            ArrayList<HashMap<String, String>> data = new ArrayList<>();
            System.out.println("command " + Command);
            initializeTime();
            try (final ResultSet rs = statement.executeQuery(Command)) {
                endTime();
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData metaData = rs.getMetaData();

                ArrayList<String> Columns = new ArrayList<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    Columns.add(metaData.getColumnName(i));
                }
                do {
                    HashMap<String, String> tmpData = new HashMap<>();
                    for (final String col : Columns) {
                        System.out.println(col);
                        Object val = rs.getObject(col);
                        String valStr = "null";
                        if (val != null) {
                            valStr = val.toString();
                        }
                        //    System.out.println(valStr);
                        tmpData.put(col, valStr);
                    }
                    data.add(tmpData);
                } while (rs.next());
                putMessage(new Logger(getUsername(), Command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return data;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public synchronized ArrayList<Double> fetchDataMap(final String Command) {
            ArrayList<Double> data = new ArrayList<>();
            // final String command = Command + " LIMIT " + limit + " OFFSET " + offset + ";";
            System.out.println("command " + Command);
            initializeTime();
            try (final ResultSet rs = statement.executeQuery(Command)) {
                endTime();
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData metaData = rs.getMetaData();

                ArrayList<String> Columns = new ArrayList<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    Columns.add(metaData.getColumnName(i));
                }

                data.add(rs.getDouble(Columns.getFirst()));
                putMessage(new Logger(getUsername(), Command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return data;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        synchronized public ArrayList<ArrayList<String>> fetchDataBackup(final String Table, final ArrayList<String> Columns, long offset) {
            ArrayList<ArrayList<String>> data = new ArrayList<>();
            final String command = "SELECT * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
            System.out.println("command " + command);
            try {
                initializeTime();
                ResultSet rs = statement.executeQuery(command);
                endTime();
                while (rs.next()) {
                    ArrayList<String> rowData = new ArrayList<>();
                    for (final String col : Columns) {
                        System.out.println(col);
                        Object val = rs.getObject(col);
                        String valStr = "null";
                        if (val != null) {
                            valStr = val.toString();
                        }
                        System.out.println(valStr);
                        rowData.add(valStr);
                    }
                    data.add(rowData);
                }
                putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return data.isEmpty() ? null : data;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        synchronized public ArrayList<ArrayList<Object>> fetchDataBackupObject(final String Table, final ArrayList<String> Columns, long offset) {
            ArrayList<ArrayList<Object>> data = new ArrayList<>();
            final String command = "SELECT * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
            System.out.println("command " + command);
            try {
                initializeTime();
                ResultSet rs = statement.executeQuery(command);
                endTime();
                while (rs.next()) {
                    ArrayList<Object> rowData = new ArrayList<>();
                    for (final String col : Columns) {
                        System.out.println(col);
                        Object val = rs.getObject(col);
                        if (val != null) {
                            System.out.println("TIPOOOOO: " + val.getClass());
                        }
                        rowData.add(val);
                    }
                    data.add(rowData);
                }
                putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return data.isEmpty() ? null : data;
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        synchronized public ArrayList<ArrayList<Object>> fetchDataBackupObject(final String Command, final ArrayList<String> Columns, final long limit, long offset) {
            ArrayList<ArrayList<Object>> data = new ArrayList<>();
            final String command = Command + " LIMIT " + limit + " OFFSET " + offset;
            System.out.println("command " + command);
            try {
                initializeTime();
                ResultSet rs = statement.executeQuery(command);
                endTime();
                while (rs.next()) {
                    ArrayList<Object> rowData = new ArrayList<>();
                    for (final String col : Columns) {
                        //System.out.println(col);
                        Object val = rs.getObject(col);
                        if (val != null) {
                            //   System.out.println("TIPOOOOO: " + val.getClass());
                        }
                        rowData.add(val);
                    }
                    data.add(rowData);
                }
                putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                return data;
            } catch (SQLException e) {
                MsgException = e.getMessage();
                return null;
            }
        }
    };
    private DatabaseUpdaterInterface databaseUpdaterInterface = null;
    private DatabaseInserterInterface databaseInserterInterface = null;

    protected String MsgException;

    private LocalTime init, end;

    private BufferedReader cursorScript;
    private String saveNext = "";
    private long executorLineNum = 0;

    protected final Stack<Savepoint> savepoints = new Stack<>();

    public SQLiteTypes types;

    public TypesModelList typesOfDB;

    public DatabaseFetcherInterface Fetcher() {
        return databaseFetcherInterface;
    }

    public DatabaseUpdaterInterface Updater() {
        return databaseUpdaterInterface;
    }

    protected void Updater(final DatabaseUpdaterInterface databaseUpdaterInterface) {
        this.databaseUpdaterInterface = databaseUpdaterInterface;
    }

    public DatabaseInserterInterface Inserter() {
        return databaseInserterInterface;
    }

    protected void Inserter(final DatabaseInserterInterface databaseInserterInterface) {
        this.databaseInserterInterface = databaseInserterInterface;
    }

    public String[] getIndexModes() {
        return indexModes;
    }

    public abstract String GetException();

    public abstract String[] getList();

    public abstract String[] getListChars();

    public String getRowId() {
        return idType;
    }

    public SQLTypes getSQLType() {
        return SQLType;
    }

    public ArrayList<String> getForeignModes() {
        return foreignModes;
    }

    public String getCharset() throws SQLException {
        System.out.println(statement.executeQuery("PRAGMA encoding;").getString(1));
        return statement.executeQuery("PRAGMA encoding;").getString(1);
    }

    protected void putMessage(final Logger message) {
        try {
            sender.put(message);
        } catch (InterruptedException _) {
        }
    }

    protected void initializeTime() {
        init = LocalTime.now();
    }

    protected void endTime() {
        end = LocalTime.now();
    }

    protected LocalTime computeTime() {
        return LocalTime.of(Math.abs(end.getHour()-init.getHour()), Math.abs(end.getMinute()-init.getMinute()), Math.abs(end.getSecond()-init.getSecond()), Math.abs(end.getNano()-init.getNano()));
    }

    protected String indexName(final String table, final String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet indexes = metaData.getIndexInfo(null, null, table, false, false)) {
            while (indexes.next()) {
                String columnName = indexes.getString("COLUMN_NAME");
                String indexName = indexes.getString("INDEX_NAME");
                short type = indexes.getShort("TYPE");
                if (type != DatabaseMetaData.tableIndexStatistic
                        && column.equalsIgnoreCase(columnName)) {
                    return indexName;
                }
            }
        }
        return null;
    }

    protected boolean initializeTransation() throws SQLException {
        final boolean initState = connection.getAutoCommit();
        if (initState) {
            connection.setAutoCommit(false);
        }
        return initState;
    }

    protected void endTransation(final boolean primaryState) throws SQLException {
        connection.commit();
            connection.setAutoCommit(true);
    }

    public void setMessager(final BlockingQueue<Logger> sender) {
        this.sender = sender;
    }

    public abstract boolean connect(String DBName, Map<String, String> formatData);

    public abstract boolean connect(String DBName);

    public void executeLittleScript(BufferedReader reader) throws IOException, SQLException {
        String line = "";
        String command = "";
        while ((line = reader.readLine()) != null) {
            command += line + "\n";
        }
        System.out.println(command);
        statement.execute(command);
    }

    public abstract HashMap<String, String> getTriggers();

    public abstract HashMap<String, String> getEvents();

    public abstract void disconnect() throws SQLException;

    public abstract boolean renameTable(String Table, String newTableName);

    public abstract boolean deleteTable(String table);

    public abstract boolean createColumn(String table, String column, ColumnMetadata meta, boolean fill);

    public abstract boolean createTable(String table) throws SQLException;

   // public abstract boolean createColumn(String table, String column, String Type, boolean prime);

  //  public abstract boolean createColumn(String command);

   // public abstract boolean deleteColumn(String command);

   // public abstract boolean renameColumn(String table);

    protected abstract boolean createSpecialColumn(String table, String column, ColumnMetadata meta);

  //  public abstract boolean createForeignColumn(String table, String column, ColumnMetadata meta);

    public abstract boolean renameColumn(String table, String column, String newColumn);

   // public abstract boolean modifyColumnType(String table, String column, String newColumn);

    public abstract boolean modifyColumnType(String Table, String column, String Type);

    public abstract boolean deleteColumn(String column, String table);

 //   public abstract void insertData(String tableName, Map<String, Object> data) throws SQLException;

    // Getters e Setters
    public String getDatabaseName() {
        return databaseName;
    }

    protected String fetchDatabaseName() throws SQLException {
        return statement.executeQuery("SELECT DATABASE();").getString(1);
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    //public abstract boolean insertData(String command);

    //public abstract ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, long offset, final boolean primeKey);

  /*  abstract public ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, long offset, String primeKey);

    public abstract ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, String primeKey);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long offset, final boolean primeKey);

    abstract public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset, boolean PrimeKey);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Command, long limit, long offset);

    public abstract ArrayList<HashMap<String, String>> fetchRawDataMap(String Command);

    public abstract ArrayList<Double> fetchDataMap(String Command);

    public abstract ArrayList<ArrayList<String>> fetchDataBackup(String Table, ArrayList<String> Columns, long offset);

    public abstract ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long offset);

    abstract public ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long limit, long offset);*/

   /* public abstract boolean insertData(String Table, HashMap<String, String> data);

    public abstract boolean insertData(String Table, ArrayList<HashMap<String, String>> data);

    public abstract boolean removeData(String Table, HashMap<String, String> data, ArrayList<Long> rowid);

    public abstract boolean removeData(String Table, ArrayList<String> rowid) throws SQLException;

    public abstract boolean removeData(String Table, HashMap<String, String> data, HashMap<String, String> prime); */

   // public abstract boolean updateData(String Table, String data, long index);

   /* public abstract boolean updateData(String Table, HashMap<String, String> data, long index);

    public abstract boolean updateData(String Table, String column, String value, long index);

    public abstract boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, long index, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, String[] index, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, String value, String[] index, String type, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, String index, String PrimeKey, String tmp); */

    public abstract long totalPages(String table);

    public abstract long totalPages(String table, ArrayList<String> columns, String condition);

    public abstract long totalPages(String table, String column, String condition);

    public abstract void renameDatabase(String name);

    public abstract ArrayList<String> getTables();

   // public abstract ArrayList<ArrayList<Object>> getColumns(String Table);

    //depresiado
    public abstract ArrayList<String> getColumnsName(String Table);

    protected abstract HashMap<String, Boolean> isUnique(String Table);

    protected abstract HashMap<String, ColumnMetadata.Foreign> getForeign(String Table);

    public abstract ArrayList<ColumnMetadata> getColumnsMetadata(String Table);

    public abstract boolean TableisPimeKey(String TableName);

    public abstract boolean TableHasPrimeKey(String TableName);

    public abstract ArrayList<String> PrimaryKeyList(String Table);

    public abstract boolean connect(String url, String userName, String password);

    public abstract boolean CreateSchema(String url, String name, String userName, String password, Map<String, String> modes);

    public abstract boolean connect(String url, String name, String userName, String password);

    public abstract String getUrl();


    public boolean createView(final ViewController.View view) throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            System.out.println("CREATE VIEW " + view.Name + " AS " + view.code);
            stmt.execute("CREATE VIEW " + view.Name + " AS " + view.code);
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    public boolean dropView(final String view) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DROP VIEW " + view + ";");
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    public boolean AlterDefaultValue(final String table, final String column, final String value) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE " + table + "ALTER COLUMN " + column + " SET DEFAULT " + value + ";");
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    public boolean AlterTypeColumn(final String table, final String column,final String type) {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ALTER TABLE " + table + "ALTER COLUMN " + column + " SET " + type + ";");
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    public abstract boolean createTable(String table, boolean temporary, boolean rowid);

    public abstract boolean createTable(String table, boolean temporary, boolean rowid, ArrayList<ColumnMetadata> columnMetadata);

    public abstract void changeCommitMode(final boolean mode) throws SQLException;

    public void executeScript(final String path) throws IOException, SQLException {
        if (path.isEmpty()) {
            return;
        }

        File myObj = new File(path);
        FileReader reader = new FileReader(myObj);
        BufferedReader myReader = new BufferedReader(reader);
        String line = "";
        String save = "";
        String command = "";
        while ((line = myReader.readLine()) != null) {
            command += save + line;
            save = "";
            if (!line.contains(";")) {
                continue;
            }
            statement.execute(command.substring(0, line.indexOf(";")+1));
            save = line.substring(line.indexOf(";")+1);
            System.out.println(command);
            line = "";
        }
    }

    public void openScript(final String path) throws FileNotFoundException {
        cursorScript = new BufferedReader(new FileReader(path));
    }

    public long executeNextCommand() throws IOException, SQLException {
        String line;
        String command = "";
        while ((line = cursorScript.readLine()) != null) {
            command += saveNext + line;
            saveNext = "";
            if (line.contains(";")) { // line é menor que command então procurar ";" leva menos processamento
                final int indexOfTerm = command.indexOf(";");
                statement.execute(command.substring(0, indexOfTerm));
                saveNext = command.substring(indexOfTerm+1);
                System.out.println(command);
                return ++executorLineNum;
            }
            ++executorLineNum;
        }
        return -1;
    }

    public abstract boolean getCommitMode() throws SQLException;

    public void back() throws SQLException {
        savepoints.push(connection.setSavepoint());
        connection.rollback();
    }

    public void redo() throws SQLException {
        if (!savepoints.isEmpty()) {
            connection.rollback(savepoints.pop());
        }
    }

    public void commit() throws SQLException {
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
        savepoints.clear();
    }

    public abstract ArrayList<ViewController.View> getViews(String table) throws SQLException;

    public abstract ArrayList<ViewController.View> getViews() throws SQLException;

    public abstract void createTrigger(String trigger, String code);

    public abstract void removeTrigger(String trigger) throws SQLException;

    public abstract void createEvent(String event, String code);

    public abstract void removeEvent(String event) throws SQLException;

    public void executeCode(String code) throws SQLException {
        statement.execute(code);
    }

    public void setFetchSize(int size) {
        buffer = size;
    }

    public abstract void createIndex(String table, ArrayList<String> columns, String indexName, String mode) throws SQLException;

    public abstract void createIndex(String table, String column, String indexName, String mode) throws SQLException;

    public abstract void removeIndex(String indexName) throws SQLException;

    public Connection getConnection() {
        return connection;
    }
}
