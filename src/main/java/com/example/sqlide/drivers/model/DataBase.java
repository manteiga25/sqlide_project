package com.example.sqlide.drivers.model;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
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

    private BufferedReader cursorScript;
    private String saveNext = "";
    private long executorLineNum = 0;

    public SQLiteTypes types;

    public TypesModelList typesOfDB;

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
        return connection.getClientInfo("charset");
    }

    protected void putMessage(final Logger message) {
        try {
            sender.put(message);
        } catch (InterruptedException _) {
        }
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

    abstract public ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, long offset, String primeKey);

    public abstract ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, String primeKey);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long offset, final boolean primeKey);

    abstract public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset, boolean PrimeKey);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset);

    public abstract ArrayList<HashMap<String, String>> fetchDataMap(String Command, long limit, long offset);

    public abstract ArrayList<ArrayList<String>> fetchDataBackup(String Table, ArrayList<String> Columns, long offset);

    public abstract ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long offset);

    abstract public ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long limit, long offset);

    public abstract boolean insertData(String Table, HashMap<String, String> data);

    public abstract boolean updateData(String Table, HashMap<String, String> data, long index);

    public abstract boolean removeData(String Table, HashMap<String, String> data, ArrayList<Long> rowid);

    public abstract boolean removeData(String Table, ArrayList<String> rowid) throws SQLException;

    public abstract boolean removeData(String Table, HashMap<String, String> data, HashMap<String, String> prime);

   // public abstract boolean updateData(String Table, String data, long index);

    public abstract boolean updateData(String Table, String column, String value, long index);

    public abstract boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, long index, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, String[] index, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, String value, String[] index, String type, String PrimeKey, String tmp);

    public abstract boolean updateData(String Table, String column, Object value, String index, String PrimeKey, String tmp);

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

    public abstract boolean connect(String url, String name, String userName, String password);

    public abstract String getUrl();

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

    public abstract void back();

    public abstract void commit() throws SQLException;

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
}
