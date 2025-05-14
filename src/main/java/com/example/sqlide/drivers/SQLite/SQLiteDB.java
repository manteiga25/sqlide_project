package com.example.sqlide.drivers.SQLite;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.TypesModelList;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SQLiteDB extends DataBase {

    public TypesModelList typesOfDB;

    private String MsgException;

    @Override
    public String GetException() {
        final String ret = MsgException;
        MsgException = "";
        return ret;
    }

    @Override
    public String[] getList() {
        return typesOfDB.listOfTypes;
    }

    @Override
    public String[] getListChars() {
        return typesOfDB.chars;
    }

    public SQLiteDB() throws IOException {
        typesOfDB = new SQLiteTypesList();
        super.idType = "ROWID";
        indexModes = null;
    }

    @Override
    public boolean connect(final String DBName, final Map<String, String> formatData) {
        try {
            driverUrl = "jdbc:sqlite:" + DBName + ".db";
            connection = DriverManager.getConnection(driverUrl);
            statement = connection.createStatement();
            FormatDBCreation(formatData);
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

    @Override
    public HashMap<String, String> getTriggers() {
        final String sql = "SELECT name, sql FROM sqlite_master WHERE type = 'trigger';";
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
        final String sql = "SELECT name, sql FROM sqlite_master WHERE type = 'event';";
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
        statement.close();
        connection.close();
    }

    @Override
    public boolean createTable(String table) {
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
    public boolean renameTable(final String Table, final String newTableName) {
        try {
            statement.execute("ALTER TABLE " + Table + " RENAME TO " + newTableName);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
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
    public boolean createColumn(String table, String column, ColumnMetadata meta, final boolean fill) {
      //  final String ForeignTable = !Objects.equals(meta.ForeignKey[0], "") ? "FOREIGN KEY (" + column + ") REFERENCES " + meta.ForeignKey[0] + "(" + meta.ForeignKey[1] + ")" : "";
        if (meta.isForeign || meta.IsPrimaryKey) {
            System.out.println("chwguei");
            return createSpecialColumn(table, column, meta, fill);
        }
        final String NotNull = meta.NOT_NULL ? " NOT NULL " : "";
        final String IsUnique = meta.isUnique ? " UNIQUE " : "";
        final String Default = meta.defaultValue == null || meta.defaultValue.isEmpty() ? "" : " DEFAULT " + meta.defaultValue;
        final boolean isForeign = meta.isForeign;
        final String index = meta.index;
        String Type = meta.Type;
        final String ColumnName = column;
        String PrimaryKey = "";
        //     else if (isForeign) {
       //     ColumnName = "CONSTRAINT fk_h_fgr FOREIGN KEY (" + column + ") REFERENCES(" + ;
     //       ColumnName = "CONSTRAINT idyhgfvduydf FOREIGN KEY (" + "id" + ") REFERENCES " +  meta.ForeignKey[0] + "(" + meta.ForeignKey[1] + "))";
       //     Type = "";
         //   Default = "";
       // }
        try {
            System.out.println("ALTER TABLE " + table + " ADD " + ColumnName + " " + Type + NotNull + Default + PrimaryKey + ";");
            statement.execute("ALTER TABLE " + table + " ADD " + ColumnName + " " + Type + NotNull + IsUnique + Default + PrimaryKey + ";");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    private boolean copyDataTable(final String table, final ArrayList<ColumnMetadata> columns) {
        StringBuilder command = new StringBuilder("INSERT INTO " + table + " (");
        for (final ColumnMetadata column : columns) {
            command.append(column.Name).append(", ");
        }
        int strSize = command.length();

        command.delete(strSize-2, strSize).append(") SELECT ");

        for (final ColumnMetadata column : columns) {
            command.append(column.Name).append(", ");
        }

        strSize = command.length();

        command.delete(strSize-2, strSize).append(" FROM ").append(table).append("CopySpecial");

        try {
            System.out.println(command);
            statement.execute(command.toString());
        } catch (SQLException e) {
            MsgException = GetException();
            return false;
        }
        return true;
    }

    //@Override
    protected boolean createSpecialColumn(String table, String column, ColumnMetadata meta, final boolean fillForeign) {
        // renameTable(table, column, column + "CopyForeign");
        boolean ret = true;
        final String TableCopy = table + "CopySpecial";
        ArrayList<ColumnMetadata> columns = null;

        try {
            connection.setAutoCommit(false);

            if (!renameTable(table, TableCopy)) {
                throw new SQLException(MsgException);
            }

            columns = getColumnsMetadata(TableCopy);
            if (columns == null) {
                throw new SQLException(MsgException);
            }

            ArrayList<ColumnMetadata> originalColumns = (ArrayList<ColumnMetadata>) columns.clone();
            columns.add(meta);

            if (!createTableSpecial(table, columns)) {
                throw new SQLException(MsgException);
            }

            if (!copyDataTable(table, originalColumns)) {
                throw new SQLException(MsgException);
            }

            if (fillForeign) {
                if (!inserDataForeignTable(table, meta.ForeignKey[0], meta.Name, meta.ForeignKey[1], meta)) {
                    throw new SQLException(MsgException);
                }
            }

            if (!deleteTable(TableCopy)) {
                throw new SQLException(MsgException);
            }

            connection.commit();
        } catch (SQLException e) {
            MsgException = e.getMessage();
            ret = false;
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                MsgException += rollbackEx.getMessage();
            }
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException autoCommitEx) {
                MsgException += autoCommitEx.getMessage();
                ret = false;
            }
        }
        return ret;
    }

    private boolean inserDataForeignTable(final String tableForeign, final String tablePrime, final String columnForeign, final String columnPrime, final ColumnMetadata meta) {
        try {
            // Localiza o nome da PK no 'tableForeign'
            String primaryKeyName = "";
            ArrayList<ColumnMetadata> columnsForeign = getColumnsMetadata(tableForeign);
            for (ColumnMetadata col : columnsForeign) {
                if (col.IsPrimaryKey) {
                    primaryKeyName = col.Name;
                    break;
                }
            } // Se não encontrar PK nomeada, use ROWID
            final String id = primaryKeyName.isEmpty() ? "ROWID" : primaryKeyName;
            String sql = "UPDATE " + tableForeign + " SET " + columnForeign + " = (" + " SELECT " + columnPrime + " FROM " + tablePrime + " p" + " WHERE p." + id + " = " + tableForeign + "." + id + " );";
            statement.execute(sql);
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }


        /*private boolean inserDataForeignTable(final String tableForeign, final String tablePrime, final String columnForeign, final String columnPrime, final ColumnMetadata meta) {
        try {
            String prime = "";
            ArrayList<String> Ids = new ArrayList<>();
            ArrayList<String> values = new ArrayList<>();
            ArrayList<ColumnMetadata> m = getColumnsMetadata(tableForeign);
            for (ColumnMetadata column : m) {
                if (column.IsPrimaryKey) {
                    prime = column.Name;
                    break;
                }
            }
            final String id = prime.isEmpty() ? "ROWID" : prime;
            ResultSet r = statement.executeQuery("SELECT " + id + " FROM " + tablePrime + ";");
            if (r.next()) {
                do {
                    Ids.add(r.getObject(id).toString());
                } while (r.next());
            }
            r = statement.executeQuery("SELECT " + columnPrime + " FROM " + tableForeign + ";");
            if (r.next()) {
                do {
                    values.add(r.getObject(columnPrime).toString());
                } while (r.next());
            }
            for (int i = 0; i < Ids.size() || i < values.size(); i++) {
                System.out.println("UPDATE INTO " + tableForeign + " SET " + columnForeign + " = " + values.get(i) + " WHERE " + id + " = " + Ids.get(i) + ";");
                statement.execute("UPDATE " + tableForeign + " SET " + columnForeign + " = " + values.get(i) + " WHERE " + id + " = " + Ids.get(i) + ";");
            }
           // System.out.println("UPDATE INTO " + tableForeign + " (" + columnForeign + ") SELECT " + columnPrime + " FROM " + tablePrime + ";");
           // statement.execute("INSERT INTO " + tableForeign + " (" + columnForeign + ") SELECT " + columnPrime + " FROM " + tablePrime + ";");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    } */

  /*  @Override
    public boolean createForeignColumn(String table, String column, ColumnMetadata meta) {
       // renameTable(table, column, column + "CopyForeign");
        boolean ret = true;
        try {
            connection.setAutoCommit(false);
            final String TableCopy = table + "CopyForeign";
            if (!renameTable(table, TableCopy)) {
                ret = false;
            }
            connection.commit();
            final ArrayList<ColumnMetadata> columns = getColumnsMetadata(TableCopy);
            if (columns == null) {
                ret = false;
            }
            final ArrayList<ColumnMetadata> copy = (ArrayList<ColumnMetadata>) columns.clone();
            columns.add(meta);
            if (!createTableForeign(table, columns)) {
                ret = false;
            }
            if (copyDataTable(table, copy)) {
                ret = false;
            }
            if (!deleteTable(TableCopy)) {
                ret = false;
            }
            connection.commit();
            connection.setAutoCommit(true);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            try {
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                ret = false;
            }
            return false;
        } finally {
            return ret;
        }
    } */

    private boolean createTableSpecial(final String table, final ArrayList<ColumnMetadata> columns) {

        StringBuilder command = new StringBuilder("CREATE TABLE " + table + " (");

        for (final ColumnMetadata column : columns) {
            final String NotNull = column.NOT_NULL ? " NOT NULL" : "";
            final String Default = !Objects.equals(column.defaultValue, "") && !Objects.equals(column.defaultValue, "null") ? " DEFAULT " + column.defaultValue : "";
            final String IsUnique = column.isUnique ? " UNIQUE " : "";
         //   String Default = "";
            final boolean isForeign = column.isForeign;
      //      final String ForeignTable = column.ForeignKey == null || Objects.equals(column.ForeignKey[0], "") ? "REFERENCES " + column.ForeignKey[0] + "(" + column.ForeignKey[1] + ")" : "";
            final String[] ForeignTable = column.ForeignKey;
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
                ColumnName = column.Name + " " + Type + ", FOREIGN KEY (" + column.Name + ") REFERENCES " + column.ForeignKey[0] + "(" + column.ForeignKey[1] + ")";
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
            System.out.println(command.toString());
            statement.execute(command.toString());
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

   /* private boolean createTablePrime(final String table, final ArrayList<ColumnMetadata> columns) {

        StringBuilder command = new StringBuilder("CREATE TABLE " + table + " (");

        for (final ColumnMetadata column : columns) {
            final String NotNull = column.NOT_NULL ? " NOT NULL" : "";
            // final String Default = !Objects.equals(column.defaultValue, "") && !Objects.equals(column.defaultValue, "null") ? " DEFAULT " + column.defaultValue : "";
            String Default = "";
            final boolean isForeign = column.isForeign;
            //      final String ForeignTable = column.ForeignKey == null || Objects.equals(column.ForeignKey[0], "") ? "REFERENCES " + column.ForeignKey[0] + "(" + column.ForeignKey[1] + ")" : "";
            final String[] ForeignTable = column.ForeignKey;
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
                ColumnName = column.Name + " " + Type + ", FOREIGN KEY (" + column.Name + ") REFERENCES " + column.ForeignKey[0] + "(" + column.ForeignKey[1] + ")";
                Type = "";
            }
            else {
                ColumnName = column.Name;
            }
            command.append(ColumnName).append(" ").append(Type).append(NotNull).append(prime).append(Default).append(", ");
        }

        final int strSize = command.length();

        command.delete(strSize-3, strSize).append(");");

        try {
            System.out.println("dhfuwfibedcbewu " + command.toString());
            statement.execute(command.toString());
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    } */

    @Override
    public boolean renameColumn(String table, String column, String newColumn) {
        try {
            statement.execute("ALTER TABLE " + table + " RENAME COLUMN " + column + " TO " + newColumn + ";");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean modifyColumnType(String Table, String column, String Type) {
        try {
            statement.execute("ALTER TABLE " + Table + " MODIFY COLUMN " + column + " " + Type + ";");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean deleteColumn(String column, String table) {
        try {
            statement.execute("ALTER TABLE " + table + " DROP COLUMN " + column + ";");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public synchronized ArrayList<DataForDB> fetchData(final String Table, ArrayList<String> Columns, final long offset, final String primeKey) {
        Columns = new ArrayList<>(Columns);
        ArrayList<DataForDB> data = new ArrayList<>();
        String rowid = "";
        if (primeKey.isEmpty()) {
            rowid = "ROWID,";
            Columns.add("ROWID");
        } else {
            if (!Columns.contains(primeKey)) {
                Columns.add(primeKey);
            }
        }
        final String command = "SELECT " + rowid + " * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
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
            putMessage(command);
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
            }

            // 2. Ajustar lista de colunas para incluir apenas colunas existentes
            ArrayList<String> validColumns = new ArrayList<>(realColumns);

            // 3. Lógica de chave primária
            String keyToCheck = primeKey.isEmpty() ? "ROWID" : primeKey;
            boolean keyExists = realColumns.contains(keyToCheck);

            // 4. Adicionar chave se necessário
            if (keyExists) {
                if (primeKey.isEmpty() && !validColumns.contains("ROWID")) {
                    validColumns.add("ROWID");
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
            rowid = "ROWID,";
            Columns.add("ROWID");
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
    }

    @Override
    synchronized public ArrayList<ArrayList<String>> fetchDataBackup(final String Table, final ArrayList<String> Columns, long offset) {
        ArrayList<ArrayList<String>> data = new ArrayList<>();
        final String command = "SELECT * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
        System.out.println("command " + command);
        try {
            ResultSet rs = statement.executeQuery(command);
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
            ResultSet rs = statement.executeQuery(command);
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
            return data.isEmpty() ? null : data;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    synchronized public ArrayList<ArrayList<Object>> fetchDataBackupObject(final String Table, final ArrayList<String> Columns, final long limit, long offset) {
        ArrayList<ArrayList<Object>> data = new ArrayList<>();
        String cols = "";
        for (final String col : Columns) {
            cols += col + ", ";
        }
        cols = cols.substring(0, cols.lastIndexOf(", "));
        final String command = "SELECT " + cols + " FROM " + Table + " LIMIT " + limit + " OFFSET " + offset;
        System.out.println("command " + command);
        try {
            ResultSet rs = statement.executeQuery(command);
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
            return data;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
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
        putMessage(query);
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.execute();
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean updateData(String Table, HashMap<String, String> data, final long index) {
        StringBuilder command = new StringBuilder("UPDATE " + Table + " SET ");
        for (final String column : data.keySet()) {
            command.append(column).append(" = '").append(data.get(column)).append("'").append(",");
        }
        command.replace(command.length()-2, command.length(), "");
        command.append(" WHERE ROWID = '").append(index).append("';");
        System.out.println(command);
        final String query = command.toString();
        putMessage(query);
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
        for (final String r : rowid) {
            System.out.println("efew " + r);
        }
        String placeholders = String.join(",", Collections.nCopies(rowid.size(), "?"));
        final String command = "DELETE FROM " + Table + " WHERE ROWID IN (" + placeholders + ");";
        System.out.println(command);
        putMessage(command);
        try (PreparedStatement pstmt = connection.prepareStatement(command)) {

            final boolean isAuto = connection.getAutoCommit();

            if (isAuto) {
                connection.setAutoCommit(false);
            }

            for (int row = 0; row < rowid.size(); row++) {
                pstmt.setString(row+1, rowid.get(row));
            }
            final int r = pstmt.executeUpdate();
            final boolean sucess = r == rowid.size();
            System.out.println("rows " + r);
            if (sucess) {
                connection.commit();
            }
            else {
                try {
                    connection.rollback();
                } catch (SQLException e) {
                    System.out.println("rf");
                }
            }
            if (isAuto) {
                connection.setAutoCommit(true);
            }
            return sucess;

        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    @Override
    public boolean removeData(String Table, HashMap<String, String> data, HashMap<String, String> prime) {
        return false;
    }

    @Override
    public boolean updateData(String Table, final String column, final String value, final long index) {
        final String command = "UPDATE " + Table + " SET " + column + " = '" + value + "' WHERE ROWID = " + index + ";";
        System.out.println(command);
        putMessage(command);
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
    public boolean updateData(String Table, final String column, final Object value, final long index, String PrimeKey, final String tmp) {
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
        putMessage(command);
        try (PreparedStatement pstmt = connection.prepareStatement(command)) {
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
        putMessage(command);
        try (PreparedStatement pstmt = connection.prepareStatement(command)) {
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
    public synchronized long totalPages(final String table) {
        try (ResultSet ret = statement.executeQuery("SELECT COUNT(*) FROM " + table + ";")) {
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
    public synchronized long totalPages(final String table, final ArrayList<String> columns, final String condition) {
        StringBuilder cols = new StringBuilder();
        for (final String column : columns) {
            cols.append(column).append(", ");
        }
        try {
            System.out.println("SELECT COUNT(" + columns.getFirst() + ") FROM " + table + " " + condition + ";");
          //  ResultSet ret = statement.executeQuery("SELECT COUNT(" + cols.substring(0, cols.length()-2) + ") FROM " + table + " " + condition + ";");
            ResultSet ret = statement.executeQuery("SELECT COUNT(" + columns.getFirst() + ") FROM " + table + " " + condition + ";");
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
    public void renameDatabase(final String name) {
        return;
    }

    @Override
    public ArrayList<String> getTables() {
        ArrayList<String> TablesName = new ArrayList<>();
        try {
            ResultSet tabledMeta = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"});
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
        try {
            ResultSet columns = connection.getMetaData().getColumns(null, null, Table, null);
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

//    private

    @Override
    protected HashMap<String, Boolean> isUnique(final String Table) {
        HashMap<String, Boolean> ColumnsUnique = new HashMap<>();
        try {
            ResultSet columns = connection.getMetaData().getIndexInfo(null, null, Table, true, true);
            while (columns.next()) {
                ColumnsUnique.put(columns.getString("COLUMN_NAME"), columns.getBoolean("NON_UNIQUE"));
            }
        } catch (Exception e) {
            return null;
        }
        return ColumnsUnique;
    }

    @Override
    protected HashMap<String, String[]> getForeign(final String Table) {
        HashMap<String, String[]> ColumnForeign = new HashMap<>();
        try {
            ResultSet foreignKeys = connection.getMetaData().getImportedKeys(null, null, Table);
            while (foreignKeys.next()) {
                String[] foreignMeta = new String[2];
                final String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    foreignMeta[0] = foreignKeys.getString("PKTABLE_NAME");
                    foreignMeta[1] = foreignKeys.getString("PKCOLUMN_NAME");
                    ColumnForeign.put(fkColumnName, foreignMeta);
                System.out.println(fkColumnName);
                }
        } catch (Exception e) {
            System.out.println("kdjcflskdjoiwdhfoewhf " + e.getMessage());
            return null;
        }
        return ColumnForeign;
    }

    private ArrayList<String> getCheckConstraintValues(String tableName, String columnName) throws SQLException {
        ArrayList<String> allowedValues = new ArrayList<>();
        String query = "SELECT sql FROM sqlite_master WHERE tbl_name = ? AND type = " + tableName;
        PreparedStatement pstmt = connection.prepareStatement(query);
        pstmt.setString(1, tableName);
        ResultSet rs = pstmt.executeQuery();
        if (rs.next()) {
            String createTableSQL = rs.getString("sql");
            String columnDef = createTableSQL.substring(createTableSQL.indexOf(columnName));
            columnDef = columnDef.substring(0, columnDef.indexOf(",")).trim();
            if (columnDef.contains("CHECK")) {
                // Extrai os valores permitidos da cláusula CHECK
                Pattern pattern = Pattern.compile("CHECK\\s*\\(.*IN\\s*\\(([^)]+)\\)\\)");
                Matcher matcher = pattern.matcher(columnDef);
                if (matcher.find()) {
                    String allowedValuesStr = matcher.group(1);
                    String[] valuesArray = allowedValuesStr.split(",");
                    for (String value : valuesArray) {
                        allowedValues.add(value.trim().replaceAll("'", ""));
                    }
                }
            }
        }
        return allowedValues;
    }

    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(final String Table) {
        ArrayList<ColumnMetadata> ColumnsMetadata = new ArrayList<>();
        final ArrayList<String> PrimaryKeyList = PrimaryKeyList(Table);
        final HashMap<String, String[]> ForeignKeyList = getForeign(Table);
        HashMap<String, Boolean> uniqueColumns = isUnique(Table);
        String[] foreign = new String[2];
        try {
            ResultSet columns = connection.getMetaData().getColumns(null, null, Table, null);
            while (columns.next()) {
                boolean isForeign = false;
                final String name = columns.getString("COLUMN_NAME");
                final String Type = columns.getString("TYPE_NAME");
                final String Default = columns.getString("COLUMN_DEF");
                final int size = columns.getInt("COLUMN_SIZE");
                final String index = indexName(Table, name);
                System.out.println(index);
                final boolean notnull = columns.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
           //     final boolean nonUnique = columns.getBoolean("NON_UNIQUE");
                boolean nonUnique = false;
                final int decimalDigits = columns.getInt("DECIMAL_DIGITS");
                final int integerDigits = size - decimalDigits;
           //     final ArrayList<String> checks = getCheckConstraintValues(Table, name);
                boolean isPrimeKey = false;
                if (PrimaryKeyList != null) {
                    for (final String col : PrimaryKeyList) {
                        if (col.equals(name)) {
                            isPrimeKey = true;
                            PrimaryKeyList.remove(col);
                            break;
                        }
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
                        foreign = ForeignKeyList.get(key);
                        ForeignKeyList.remove(key);
                        isForeign = true;
                        break;
                    }
                }
                // para mudar
                ColumnMetadata TmpCol = new ColumnMetadata(notnull, isPrimeKey, foreign, isForeign, Default, size, Type, name, nonUnique, integerDigits, decimalDigits, index);
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

    @Deprecated
    @Override
    public boolean TableisPimeKey(final String TableName) {
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
    public boolean TableHasPrimeKey(final String TableName) {
        boolean primeFound = false;
        try {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, TableName);
            primeFound = primaryKeys.next();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
            MsgException = e.getMessage();
        }
        System.out.println(primeFound);
        return primeFound;
    }

    @Override
    public ArrayList<String> PrimaryKeyList(final String Table) {
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
    public boolean connect(String url, String name, String userName, String password) {
        return false;
    }

    @Override
    public String getUrl() {
        return super.Url;
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
            System.out.println("erro " + e.getMessage());
        }
    }

    @Override
    public void commit() throws SQLException {
        connection.commit();
    }

    @Override
    public void createTrigger(final String trigger, final String code) {
        try {
            statement.execute("DROP TRIGGER " + trigger);
            statement.execute(code);
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    @Override
    public void removeTrigger(final String trigger) throws SQLException {
            statement.execute("DROP TRIGGER " + trigger);
    }

    @Override
    public void createEvent(final String event, final String code) {
        try {
            statement.execute("DROP EVENT " + event);
            statement.execute(code);
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    @Override
    public void removeEvent(final String event) throws SQLException {
            statement.execute("DROP EVENT " + event);
    }

    @Override
    public void executeCode(final String code) throws SQLException {
        statement.execute(code);
    }

    @Override
    public void createIndex(final String table, final ArrayList<String> columns, String indexName, final String mode) throws SQLException {
        indexName = indexName.isEmpty() ? "idx_" + columns.getFirst() : indexName;
        String command = "CREATE " + mode + " INDEX " + indexName + " ON " + table + " (";
        for (final String column : columns) {
            command += column + ", ";
        }
        command = command.substring(0, command.length()-2);
        command += ");";
        System.out.println(command);
        statement.execute(command);
    }

    @Override
    public void createIndex(final String table, final String column, String indexName, final String mode) throws SQLException {
        indexName = indexName.isEmpty() ? "idx_" + column : indexName;
        final String command = "CREATE " + mode + " INDEX " + indexName + " ON " + table + " (" + column + ");";
        System.out.println(command);
        statement.execute(command);
    }

    @Override
    public void removeIndex(final String indexName) throws SQLException {
        statement.execute("DROP INDEX " + indexName);
    }

}
