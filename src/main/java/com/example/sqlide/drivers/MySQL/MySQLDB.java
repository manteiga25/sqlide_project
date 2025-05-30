package com.example.sqlide.drivers.MySQL;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.TypesModelList;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
import java.time.LocalTime;
import java.util.*;

public class MySQLDB extends DataBase {

    public TypesModelList typesOfDB;

    private String MsgException;

    public MySQLDB() {
        typesOfDB = new MySQLTypesList();
    }

    @Override
    public String GetException() {
        final String tmp = MsgException;
        MsgException = "";
        return tmp;
    }

    @Override
    public String[] getList() {
        return typesOfDB.listOfTypes;
    }

    @Override
    public String[] getListChars() {
        return typesOfDB.chars;
    }

    @Override
    public boolean connect(String DBName, Map<String, String> formatData) {
        return false;
    }

    @Override
    public boolean connect(String DBName) {
        return false;
    }

    @Override
    public void executeLittleScript(BufferedReader reader) throws IOException, SQLException {

    }

    @Override
    public HashMap<String, String> getTriggers() {
        final String sql = "SELECT TRIGGER_NAME AS name, TRIGGER_DEFINITION AS sql FROM information_schema.TRIGGERS;";
        HashMap<String, String> code = new HashMap<>();
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                code.put(rs.getString("name"), rs.getString("sql"));
            }
            return code;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }

    }

    @Override
    public HashMap<String, String> getEvents() {
        final String sql = "SELECT EVENT_NAME AS name, EVENT_DEFINITION AS sql FROM information_schema.EVENTS;";
        HashMap<String, String> code = new HashMap<>();
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                code.put(rs.getString("name"), rs.getString("sql"));
            }
            return code;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }

    }

    @Override
    public void disconnect() throws SQLException {

    }

    @Override
    public boolean renameTable(String Table, String newTableName) {
        return false;
    }

    @Override
    public boolean deleteTable(String table) {
        try {
            statement.execute("DROP TABLE " + table + ";");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean createColumn(String table, String column, ColumnMetadata meta, boolean fill) {
      //  if (meta.foreign.isForeign || meta.IsPrimaryKey) {
        //    System.out.println("chwguei");
          //  return createSpecialColumn(table, column, meta);
       // }
        final String NotNull = meta.NOT_NULL ? " NOT NULL " : "";
        final String IsUnique = meta.isUnique ? " UNIQUE " : "";
        String Default = meta.defaultValue == null || meta.defaultValue.isEmpty() ? " DEFAULT " + meta.defaultValue : "";
        Default = "";
        final boolean isForeign = meta.foreign.isForeign;
        String Type = meta.Type;
        final String ColumnName = column;
        String PrimaryKey = "";
        if (meta.IsPrimaryKey) {
            PrimaryKey = " PRIMARY KEY";
        }
        StringBuilder items = new StringBuilder();
        if (!meta.items.isEmpty()) {
            items.append("(");
            for (final String item : meta.items) {
                items.append("'").append(item).append("'").append(", ");
            }
            items = new StringBuilder(items.substring(0, items.length() - 2));
            items.append(")");
        }
        //     else if (isForeign) {
        //     ColumnName = "CONSTRAINT fk_h_fgr FOREIGN KEY (" + column + ") REFERENCES(" + ;
        //       ColumnName = "CONSTRAINT idyhgfvduydf FOREIGN KEY (" + "id" + ") REFERENCES " +  meta.ForeignKey[0] + "(" + meta.ForeignKey[1] + "))";
        //     Type = "";
        //   Default = "";
        // }
        try {
            String x = "ALTER TABLE " + table + " ADD " + ColumnName + " " + Type + " " + items + NotNull + IsUnique + Default + PrimaryKey + ";";
            System.out.println(x);
            statement.execute(x);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean createTable(String table) throws SQLException {
        System.out.println("Table " + table);
        try {
            statement.execute("CREATE TABLE " + table + " (id INT);");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    protected boolean createSpecialColumn(String table, String column, ColumnMetadata meta) {
        return false;
    }

    @Override
    public boolean renameColumn(String table, String column, String newColumn) {
        return false;
    }

    @Override
    public boolean modifyColumnType(String Table, String column, String Type) {
        return false;
    }

    @Override
    public boolean deleteColumn(String column, String table) {
        return false;
    }

    @Override
    public ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, long offset, String primeKey) {
        ArrayList<DataForDB> data = new ArrayList<>();
        final String command = "SELECT * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
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
            return  data;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, String primeKey) {
        return null;
    }

    @Override
    public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long offset, final boolean primeKey) {
        return null;
    }

    @Override
    public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset, boolean PrimeKey) {
        return null;
    }

    @Override
    public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long limit, long offset) {
        return null;
    }

    @Override
    public ArrayList<HashMap<String, String>> fetchDataMap(String Command, long limit, long offset) {
        return null;
    }

    @Override
    public ArrayList<ArrayList<String>> fetchDataBackup(String Table, ArrayList<String> Columns, long offset) {
        return null;
    }

    @Override
    public ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long offset) {
        return null;
    }

    @Override
    public ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long limit, long offset) {
        return null;
    }

    @Override
    public boolean insertData(String Table, HashMap<String, String> data) {
        StringBuilder command = new StringBuilder("INSERT INTO " + Table + " (");
        for (final String column : data.keySet()) {
            command.append(column).append(", ");
        }
        command.replace(command.length()-2, command.length(), "");
        command.append(") VALUES (");
        for (final String column : data.values()) {
            command.append("'").append(column).append("'").append(", ");
        }
        command.replace(command.length()-2, command.length(), "");
        command.append(");");
        System.out.println(command);
        final String query = command.toString();
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.execute();
            putMessage(new Logger(getUsername(), query, pstmt.getWarnings() != null ? pstmt.getWarnings().getMessage() : "", LocalTime.now()));
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean updateData(String Table, HashMap<String, String> data, long index) {
        StringBuilder command = new StringBuilder("UPDATE " + Table + " SET ");
        for (final String column : data.keySet()) {
            command.append(column).append(" = '").append(data.get(column)).append("'").append(",");
        }
        command.replace(command.length()-2, command.length(), "");
        command.append(" WHERE ROWID = '").append(index).append("';");
        System.out.println(command);
        try {
            PreparedStatement pstmt = connection.prepareStatement(command.toString());
            //  pstmt.execute(command.toString());
            pstmt.execute();
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

    @Override
    public boolean removeData(String Table, ArrayList<String> rowid) {
        return false;
    }

    @Override
    public boolean removeData(String Table, HashMap<String, String> data, HashMap<String, String> prime) {
        return false;
    }

    @Override
    public boolean updateData(String Table, String column, String value, long index) {
        final String command = "UPDATE " + Table + " SET " + column + " = '" + value + "' WHERE ROWID = " + index + ";";
        System.out.println(command);
        try {
            // PreparedStatement pstmt = connection.prepareStatement(command.toString());
            //  pstmt.execute(command.toString());
            //  pstmt.execute();
            statement.execute(command);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp) {
        return false;
    }

    @Override
    public boolean updateData(String Table, String column, Object value, long index, String PrimeKey, String tmp) {
        String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
        boolean prime = false;
        if (PrimeKey == null || PrimeKey.isEmpty()) {
            command += "ROWID = " + index + ";";
        }
        else {
            //  command += PrimeKey + " = '" + tmp + "'";
            command += PrimeKey + " = ?";
            prime = true;

        }
        System.out.println(command);
        try {
            PreparedStatement pstmt = connection.prepareStatement(command);
            //  pstmt.execute(command.toString());
            pstmt.setObject(1, value);
            if (prime) {
                pstmt.setObject(2, tmp);
            }
            int affectedRows = pstmt.executeUpdate();
            System.out.println("rows " + affectedRows);
            //  statement.execute(command);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean updateData(String Table, String column, Object value, String[] index, String PrimeKey, String tmp) {
        String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
        boolean prime = false;
        if (PrimeKey == null || PrimeKey.isEmpty()) {
            command += "ROWID = " + index[0] + ";";
        }
        else {
            //  command += PrimeKey + " = '" + tmp + "'";
            command += PrimeKey + " = ?";
            prime = true;

        }
        System.out.println(command);
        try {
            PreparedStatement pstmt = connection.prepareStatement(command);
            //  pstmt.execute(command.toString());
            pstmt.setObject(1, value);
            if (prime) {
                pstmt.setObject(2, tmp);
            }
            int affectedRows = pstmt.executeUpdate();
            System.out.println("rows " + affectedRows);
            //  statement.execute(command);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean updateData(String Table, String column, String value, String[] index, String type, String PrimeKey, String tmp) {
        String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
        boolean prime = false;
        if (PrimeKey == null || PrimeKey.isEmpty()) {
            command += "ROWID = " + index[0] + ";";
        }
        else {
            //  command += PrimeKey + " = '" + tmp + "'";
            command += PrimeKey + " = ?";
            prime = true;

        }
        System.out.println(command);
        try {
            PreparedStatement pstmt = connection.prepareStatement(command);
            //  pstmt.execute(command.toString());
            pstmt.setObject(1, value);
            if (prime) {
                pstmt.setObject(2, tmp);
            }
            int affectedRows = pstmt.executeUpdate();
            System.out.println("rows " + affectedRows);
            //  statement.execute(command);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public ArrayList<String> getTables() {

        ArrayList<String> TablesName = new ArrayList<>();
        try {
            ResultSet tabledMeta = connection.getMetaData().getTables(databaseName, databaseName, "%", new String[]{"TABLE"});
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
        try {
            ResultSet columns = connection.getMetaData().getColumns(databaseName, databaseName, Table, null);
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

    @Override
    protected HashMap<String, Boolean> isUnique(String Table) {
        HashMap<String, Boolean> ColumnsUnique = new HashMap<>();
        try {
            ResultSet columns = connection.getMetaData().getIndexInfo(databaseName, databaseName, Table, true, true);
            while (columns.next()) {
                ColumnsUnique.put(columns.getString("COLUMN_NAME"), columns.getBoolean("NON_UNIQUE"));
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
            default                                     -> "UNKNOWN";
        };
    }

    @Override
    protected HashMap<String, ColumnMetadata.Foreign> getForeign(String Table) {
        HashMap<String, ColumnMetadata.Foreign> ColumnForeign = new HashMap<>();
        try {
            ResultSet foreignKeys = connection.getMetaData().getImportedKeys(null, null, Table);
            while (foreignKeys.next()) {
                ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
                final String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                foreign.tableRef = foreignKeys.getString("PKTABLE_NAME");
                foreign.columnRef = foreignKeys.getString("PKCOLUMN_NAME");
                short updateRule  = foreignKeys.getShort("UPDATE_RULE");
                short deleteRule  = foreignKeys.getShort("DELETE_RULE");
                foreign.onEliminate = ruleToString(deleteRule);
                foreign.onUpdate = ruleToString(updateRule);
                ColumnForeign.put(fkColumnName, foreign);
                System.out.println(fkColumnName);
            }
        } catch (Exception e) {
            System.out.println("kdjcflskdjoiwdhfoewhf " + e.getMessage());
            return null;
        }
        return ColumnForeign;
    }

    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(String Table) {
        ArrayList<ColumnMetadata> ColumnsMetadata = new ArrayList<>();
        final ArrayList<String> PrimaryKeyList = PrimaryKeyList(Table);
        final HashMap<String, ColumnMetadata.Foreign> ForeignKeyList = getForeign(Table);
        HashMap<String, Boolean> uniqueColumns = isUnique(Table);
        try {
            ResultSet columns = connection.getMetaData().getColumns(databaseName, databaseName, Table, null);
            while (columns.next()) {
                ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
                final String name = columns.getString("COLUMN_NAME");
                String Type = columns.getString("TYPE_NAME");
                ArrayList<String> checkValues = new ArrayList<>();
                final String Default = columns.getString("COLUMN_DEF");
                final int size = columns.getInt("COLUMN_SIZE");
                final String index = indexName(Table, name);
                final boolean notnull = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                //     final boolean nonUnique = columns.getBoolean("NON_UNIQUE");
                boolean nonUnique = false;
                final int decimalDigits = columns.getInt("DECIMAL_DIGITS");
                final int integerDigits = size - decimalDigits;
                //     final ArrayList<String> checks = getCheckConstraintValues(Table, name);
                boolean isPrimeKey = false;
                for (final String col : PrimaryKeyList) {
                    if (col.equals(name)) {
                        isPrimeKey = true;
                        PrimaryKeyList.remove(col);
                        break;
                    }
                }
                for (final String column : uniqueColumns.keySet()) {
                    if (uniqueColumns.get(column)) {
                        nonUnique = true;
                        uniqueColumns.remove(column);
                        break;
                    }
                }
                for (final String key : ForeignKeyList.keySet()) {
                    if (key.equals(name)) {
                        foreign = ForeignKeyList.remove(key);
                        break;
                    }
                }
                if (Type.toUpperCase().contains("ENUM") || Type.toUpperCase().contains("SET")) {
                   // checkValues = getCheckConstraintValues(Type);

                    checkValues = getColumnValues(Table, name);
                    System.out.println("balues are " + checkValues);
                         //   Type = "ENUM";
                }
                // para mudar
                ColumnMetadata TmpCol = new ColumnMetadata(notnull, isPrimeKey, foreign, Default, size, Type, name, nonUnique, integerDigits, decimalDigits, name);
                TmpCol.items = checkValues;
                //     TmpCol.items = checks;


                ColumnsMetadata.add(TmpCol);
            }
            return ColumnsMetadata;
            // return TablesName;
        } catch (SQLException e) {
            System.out.println("errorrrrr " + e.getMessage());
            MsgException = e.getMessage();
            return null;
        }
    }

    public ArrayList<String> getColumnValues(String tableName, String columnName) throws SQLException {
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE " +
                "FROM INFORMATION_SCHEMA.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() " +
                "  AND TABLE_NAME = ? " +
                "  AND COLUMN_NAME = ?";
        ArrayList<String> values = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, tableName);
            stmt.setString(2, columnName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String columnType = rs.getString("COLUMN_TYPE");
                    System.out.println("typeeee " + columnType);
                    values = getCheckConstraintValues(columnType);
                }
            }
        }

        return values;
    }

    private ArrayList<String> getCheckConstraintValues(String Column) {
        ArrayList<String> values = new ArrayList<>();
        Column = Column.replaceAll("'", "");
        final int init = Column.indexOf("(");
        final int end = Column.indexOf(")");
        if (init != -1 && end != -1) {
            final String check = Column.substring(init + 1, end);
            final String[] checkValues = check.split(",");
            for (final String value : checkValues) {
                values.add(value.trim());
            }
        }
        return values;
    }

    @Override
    public boolean updateData(String Table, final String column, final Object value, final String index, String PrimeKey, final String tmp) {
        String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
        boolean prime = false;
        if (PrimeKey == null || PrimeKey.isEmpty()) {
            command += "ROWID = " + index + ";";
        }
        else {
            //  command += PrimeKey + " = '" + tmp + "'";
            command += PrimeKey + " = ?";
            prime = true;

        }
        System.out.println(command);
        try {
            PreparedStatement pstmt = connection.prepareStatement(command);
            //  pstmt.execute(command.toString());
            pstmt.setObject(1, value);
            if (prime) {
                pstmt.setObject(2, tmp);
            }
            int affectedRows = pstmt.executeUpdate();
            System.out.println("rows " + affectedRows);
            //  statement.execute(command);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public long totalPages(String table) {
        try {
            ResultSet ret = statement.executeQuery("SELECT COUNT(*) FROM " + table + ";");
            if (ret.next()) {
                return (long) Math.ceil((double) ret.getLong(1) / buffer);
            }
            return 0;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return -1;
        }
    }

    @Override
    public long totalPages(String table, ArrayList<String> columns, String condition) {
        return 0;
    }

    @Override
    public long totalPages(String table, String column, String condition) {
        try {
            System.out.println("SELECT COUNT(" + column + ") FROM " + table + " " + condition + ";");
            //  ResultSet ret = statement.executeQuery("SELECT COUNT(" + cols.substring(0, cols.length()-2) + ") FROM " + table + " " + condition + ";");
            ResultSet ret = statement.executeQuery("SELECT COUNT(" + column + ") FROM " + table + " " + condition + ";");
            if (ret.next()) {
                System.out.println("total " + (long) Math.ceil((double) ret.getLong(1) / buffer));
                return (long) Math.ceil((double) ret.getLong(1) / buffer);
            }
            return 0;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return -1;
        }
    }

    @Override
    public void renameDatabase(String name) {

    }

    @Override
    public boolean TableisPimeKey(String TableName) {
        boolean primeFound = false;
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, TableName);
            while (primaryKeys.next()) {
                final String columnName = primaryKeys.getString("COLUMN_NAME");
                if (TableName.equals(columnName)) {
                    System.out.println("encontrei");
                    primeFound = true;
                    break;
                }
            }
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
        return primeFound;
    }

    @Override
    public boolean TableHasPrimeKey(String TableName) {
        return false;
    }

    @Override
    public ArrayList<String> PrimaryKeyList(String Table) {
        ArrayList<String> Columns = new ArrayList<>();
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, Table);
            while (primaryKeys.next()) {
                Columns.add(primaryKeys.getString("COLUMN_NAME"));
            }
            return Columns;
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
    public boolean CreateSchema(String url, final String name, String userName, String password, Map<String, String> modes) {
        final String completeURL = "jdbc:mysql://" + url;
        System.out.println(completeURL);
        try {
            connection = DriverManager.getConnection(completeURL, userName, password);
            statement = connection.createStatement();
            super.databaseName = name;
            statement.execute("CREATE DATABASE " + name + ";");
            FormatDBCreation(modes);
            connection = DriverManager.getConnection(completeURL + "/" + name, userName, password);
            statement = connection.createStatement();
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
          //  statement.executeUpdate("PRAGMA " + feature + " = " + formatData.get(feature) + ";");
        }
        // statement.executeUpdate("PRAGMA foreign_keys = ON;");
        try {
            executeScript(scriptPath);
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    @Override
    public boolean connect(String url, final String name, String userName, String password) {
        final String completeURL = "jdbc:mysql://" + url + name;
        System.out.println(completeURL);
        try {
            connection = DriverManager.getConnection(completeURL, userName, password);
            statement = connection.createStatement();
            super.databaseName = name;
            //  FormatDBCreation(formatData);
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    @Override
    public String getUrl() {
        return "";
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid) {
        final String Temporary = temporary ? "TEMPORARY " : "";
        final String Rowid = rowid ? "" : "WITHOUT ROWID";
        final String prime = rowid ? "" : "PRIMARY KEY";
        try {
            statement.execute("CREATE " + Temporary + "TABLE " + table + " (id INT " + prime + ") " + Rowid + ";");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid, ArrayList<ColumnMetadata> columnMetadata) {
        final String Temporary = temporary ? "TEMPORARY " : "";
      //  final String Rowid = rowid ? "" : " WITHOUT ROWID ";
        //  final String prime = rowid ? "" : "PRIMARY KEY";
        StringBuilder command = new StringBuilder("CREATE " + Temporary + "TABLE " + table + " (");

        for (final ColumnMetadata column : columnMetadata) {
            final String NotNull = column.NOT_NULL ? " NOT NULL" : "";
            final String Default = !Objects.equals(column.defaultValue, "") && !Objects.equals(column.defaultValue, "null") ? " DEFAULT " + column.defaultValue : "";
            final String IsUnique = column.isUnique ? " UNIQUE " : "";
            //   String Default = "";
            final boolean isForeign = column.foreign.isForeign;
            //      final String ForeignTable = column.ForeignKey == null || Objects.equals(column.ForeignKey[0], "") ? "REFERENCES " + column.ForeignKey[0] + "(" + column.ForeignKey[1] + ")" : "";
            //   final String[] ForeignTable = column.ForeignKey;
            String Type = column.Type;
            String ColumnName = "";
            String prime = "";
            if (column.IsPrimaryKey) {
                //   ColumnName = "PRIMARY KEY (" + column.Name + ")";
                //     ColumnName = column.Name + " " + Type + " " + NotNull + " PRIMARY KEY";
                ColumnName = column.Name;
                prime = " PRIMARY KEY ";
            }
            else if (isForeign) {
                final String onUpdate = column.foreign.onUpdate.isEmpty() ? "" : " ON UPDATE " + column.foreign.onUpdate;
                final String onDelete = column.foreign.onEliminate.isEmpty() ? "" : " ON DELETE " + column.foreign.onEliminate;
                ColumnName = column.Name + " " + Type + ", FOREIGN KEY (" + column.Name + ") REFERENCES " + column.foreign.tableRef + "(" + column.foreign.columnRef + ")" + onUpdate + onDelete;
                Type = "";
            }
            else {
                ColumnName = column.Name;
            }
            command.append(ColumnName).append(" ").append(Type).append(NotNull).append(prime).append(IsUnique).append(Default).append(", ");
        }

        final int strSize = command.length();

        command.delete(strSize-3, strSize).append(");");
        try {
            System.out.println(command);
            statement.execute(command.toString());
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public void changeCommitMode(boolean mode) {

    }

    @Override
    public void openScript(String path) throws FileNotFoundException {

    }

    @Override
    public long executeNextCommand() throws IOException, SQLException {
        return 0;
    }

    @Override
    public boolean getCommitMode() throws SQLException {
        return false;
    }

    @Override
    public void back() {
        try {
            connection.rollback();
        } catch (SQLException e) {

        }
    }

    @Override
    public void commit() throws SQLException {
        connection.commit();
    }

    @Override
    public void createTrigger(String trigger, String code) {

    }

    @Override
    public void removeTrigger(String trigger) {

    }

    @Override
    public void createEvent(String event, String code) {

    }

    @Override
    public void removeEvent(String event) {

    }

    @Override
    public void executeCode(String code) throws SQLException {

    }

    @Override
    public void createIndex(String table, ArrayList<String> columns, String indexName, String mode) throws SQLException {

    }

    @Override
    public void createIndex(String table, String column, String indexName, String mode) throws SQLException {

    }

    @Override
    public void removeIndex(String indexName) throws SQLException {

    }

}
