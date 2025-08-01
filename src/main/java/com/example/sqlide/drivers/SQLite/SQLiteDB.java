package com.example.sqlide.drivers.SQLite;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.drivers.model.SQLTypes;
import com.example.sqlide.drivers.model.TypesModelList;

import java.sql.*;
import java.time.LocalTime;
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

    public SQLiteDB() {
        typesOfDB = new SQLiteTypesList();
        super.idType = "ROWID";
        indexModes = new String[]{""};
        foreignModes = new ArrayList<>(List.of("CASCADE", "SET NULL", "SET DEFAULT", "RESTRICT", "NO ACTION"));
        SQLType = SQLTypes.SQLITE;

        Updater(updater);
        Inserter(inserterInterface);

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
        if (meta.foreign.isForeign || meta.IsPrimaryKey) {
            System.out.println("chwguei");
            return createSpecialColumn(table, column, meta, fill);
        }
        final String NotNull = meta.NOT_NULL ? " NOT NULL " : "";
        final String IsUnique = meta.isUnique ? " UNIQUE " : "";
        final String Default = meta.defaultValue == null || meta.defaultValue.isEmpty() ? "" : " DEFAULT " + meta.defaultValue;
 //       final boolean isForeign = meta.isForeign;
        final String index = meta.index;
        String Type = meta.Type;
        String PrimaryKey = "";
        final String check = meta.check.isEmpty() ? "" : " CHECK (" + meta.check + ")";
        //     else if (isForeign) {
       //     ColumnName = "CONSTRAINT fk_h_fgr FOREIGN KEY (" + column + ") REFERENCES(" + ;
     //       ColumnName = "CONSTRAINT idyhgfvduydf FOREIGN KEY (" + "id" + ") REFERENCES " +  meta.ForeignKey[0] + "(" + meta.ForeignKey[1] + "))";
       //     Type = "";
         //   Default = "";
       // }
        try {
            System.out.println("ALTER TABLE " + table + " ADD " + column + " " + Type + NotNull + IsUnique + Default + PrimaryKey + check + ";");
            statement.execute("ALTER TABLE " + table + " ADD " + column + " " + Type + NotNull + IsUnique + Default + PrimaryKey + check + ";");
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
                if (!inserDataForeignTable(table, meta.foreign.tableRef, meta.Name, meta.foreign.columnRef, meta)) {
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
                ColumnName = column.Name + " " + Type + ", FOREIGN KEY (" + column.Name + ") REFERENCES " + column.foreign.tableRef + "(" + column.foreign.columnRef + ")";
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

    final DatabaseInserterInterface inserterInterface = new DatabaseInserterInterface() {
        @Override
        public boolean removeData(String Table, HashMap<String, String> data, HashMap<String, String> prime) {
            return false;
        }

        @Override
        public void createTable(String tableName, List<String> columnDefinitions, List<String> primaryKeyColumns) throws SQLException {

        }

        @Override
        public String getException() {
            return GetException();
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
        public boolean insertData(String Table, ArrayList<HashMap<String, String>> data) {
            StringBuilder command = new StringBuilder("INSERT INTO " + Table + " (");
            for (final String column : data.getFirst().keySet()) {
                command.append(column).append(", ");
            }
            command.replace(command.length()-2, command.length(), "");
            command.append(") VALUES (");
            for (final String _ : data.getFirst().keySet()) {
                command.append("?").append(", ");
            }
            command.replace(command.length()-2, command.length(), "");
            command.append(");");
            System.out.println(command);

            try (PreparedStatement ps = connection.prepareStatement(command.toString())) {
                for (final HashMap<String, String> row : data) {
                    int column = 1;
                    for (final String key : row.keySet()) {
                        ps.setObject(column++, row.get(key));
                    }
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

        @Override
        public boolean removeData(String Table, ArrayList<String> rowid) {
            for (final String r : rowid) {
                System.out.println("efew " + r);
            }
            String placeholders = String.join(",", Collections.nCopies(rowid.size(), "?"));
            final String command = "DELETE FROM " + Table + " WHERE ROWID IN (" + placeholders + ");";
            System.out.println(command);
            initializeTime();
            try (PreparedStatement pstmt = connection.prepareStatement(command)) {
                endTime();

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
                putMessage(new Logger(getUsername(), command, pstmt.getWarnings() != null ? pstmt.getWarnings().getMessage() : "", computeTime()));
                return sucess;

            } catch (SQLException e) {
                MsgException = e.getMessage();
                return false;
            }
        }
    };

    final DatabaseUpdaterInterface updater = new DatabaseUpdaterInterface() {
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
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
                //  pstmt.execute(command.toString());
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
            final String command = "UPDATE " + Table + " SET " + column + " = '" + value + "' WHERE ROWID = " + index + ";";
            System.out.println(command);
            try {
                // PreparedStatement pstmt = connection.prepareStatement(command.toString());
                //  pstmt.execute(command.toString());
                //  pstmt.execute();
                initializeTime();
                statement.execute(command);
                endTime();
                putMessage(new Logger(getUsername(), command, statement.getWarnings() != null ? statement.getWarnings().getMessage() : "", computeTime()));
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
            try (PreparedStatement pstmt = connection.prepareStatement(command)) {
                //  pstmt.execute(command.toString());
                pstmt.setObject(1, value);
                if (prime) {
                    pstmt.setObject(2, tmp);
                }
                int affectedRows = pstmt.executeUpdate();
                putMessage(new Logger(getUsername(), command, pstmt.getWarnings() != null ? pstmt.getWarnings().getMessage() : "", LocalTime.now()));
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
            try (PreparedStatement pstmt = connection.prepareStatement(command)) {
                //  pstmt.execute(command.toString());
                pstmt.setObject(1, value);
                if (prime) {
                    pstmt.setObject(2, tmp);
                }
                int affectedRows = pstmt.executeUpdate();
                putMessage(new Logger(getUsername(), command, pstmt.getWarnings() != null ? pstmt.getWarnings().getMessage() : "", LocalTime.now()));
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
            return false;
        }

        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, ArrayList<String> PrimeKey, ArrayList<String> tmp) {
            String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
            if (PrimeKey == null || PrimeKey.isEmpty()) {
                command += "ROWID = " + index[0] + ";";
            }
            else {
                //  command += PrimeKey + " = '" + tmp + "'";
                for (final String primary : PrimeKey) {
                    command += primary + " = ? AND ";
                }
                command = command.substring(0, command.length()-5) + ";";
            }
            System.out.println(command);
            try {
                PreparedStatement pstmt = connection.prepareStatement(command);
                //  pstmt.execute(command.toString());
                pstmt.setObject(1, value);
                if (PrimeKey != null) {
                    final int size = PrimeKey.size();
                    for (int indexKey = 0; indexKey < size; indexKey++) {
                            pstmt.setObject(indexKey+2, tmp.get(indexKey));
                    }
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
        public String getException() {
            return GetException();
        }
    };

    @Override
    public synchronized long totalPages(final String table) {
        try (final Statement statement = connection.createStatement();
                final ResultSet ret = statement.executeQuery("SELECT COUNT(*) FROM " + table + ";")) {
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
        try (final Statement statement = connection.createStatement();
             final ResultSet ret = statement.executeQuery("SELECT COUNT(" + columns.getFirst() + ") FROM " + table + " " + condition + ";")) {
            System.out.println("SELECT COUNT(" + columns.getFirst() + ") FROM " + table + " " + condition + ";");
          //  ResultSet ret = statement.executeQuery("SELECT COUNT(" + cols.substring(0, cols.length()-2) + ") FROM " + table + " " + condition + ";");
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
    public synchronized long totalPages(final String table, final String column, final String condition) {
        try (final Statement statement = connection.createStatement();
             final ResultSet ret = statement.executeQuery("SELECT COUNT(" + column + ") FROM " + table + " " + condition + ";")) {
            System.out.println("SELECT COUNT(" + column + ") FROM " + table + " " + condition + ";");
            //  ResultSet ret = statement.executeQuery("SELECT COUNT(" + cols.substring(0, cols.length()-2) + ") FROM " + table + " " + condition + ";");
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
    protected HashMap<String, ColumnMetadata.Foreign> getForeign(final String Table) {
        HashMap<String, ColumnMetadata.Foreign> ColumnForeign = new HashMap<>();
        try {
            ResultSet foreignKeys = connection.getMetaData().getImportedKeys(null, null, Table);
            while (foreignKeys.next()) {
                ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
                final String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                foreign.isForeign = true;
                    foreign.tableRef = foreignKeys.getString("PKTABLE_NAME");
                    foreign.columnRef = foreignKeys.getString("PKCOLUMN_NAME");
                short updateRule  = foreignKeys.getShort("UPDATE_RULE");
                short deleteRule  = foreignKeys.getShort("DELETE_RULE");
                foreign.onEliminate = ruleToString(deleteRule);
                foreign.onUpdate = ruleToString(updateRule);
                    ColumnForeign.put(fkColumnName, foreign);
                System.out.println("asdcsda " + fkColumnName);
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

    private HashMap<String, String> getCheck(final String table) {
        final HashMap<String, String> check = new HashMap<>();
        String query =
                "SELECT COLUMN_NAME, CONSTRAINT_NAME, CHECK_CLAUSE " +
                        "FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS " +
                        "WHERE TABLE_NAME = ?";

        try {
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, table);
            ResultSet checkConstraints = stmt.executeQuery();

            while (checkConstraints.next()) {
                String columnName = checkConstraints.getString("COLUMN_NAME");
                String checkClause = checkConstraints.getString("CHECK_CLAUSE");
                check.put(columnName, checkClause);
            }
        } catch (Exception e) {
            System.out.println("kdjcflskdjoiwdhfoewhf " + e.getMessage());
            return null;
        }
        return check;
    }

    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(final String Table) {
        ArrayList<ColumnMetadata> ColumnsMetadata = new ArrayList<>();
        final ArrayList<String> PrimaryKeyList = PrimaryKeyList(Table);
        final HashMap<String, ColumnMetadata.Foreign> ForeignKeyList = getForeign(Table);
       // final HashMap<String, String> CheckList = getCheck(Table);
        HashMap<String, Boolean> uniqueColumns = isUnique(Table);
        try {
            ResultSet columns = connection.getMetaData().getColumns(null, null, Table, null);
            while (columns.next()) {
                ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
                final String name = columns.getString("COLUMN_NAME");
                final String Type = columns.getString("TYPE_NAME");
                System.out.println(Type);
                final String Default = columns.getString("COLUMN_DEF") == null ? "" : columns.getString("COLUMN_DEF");
                final int size = columns.getInt("COLUMN_SIZE");
                final String index = indexName(Table, name);
                String check = "";
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
                        foreign = ForeignKeyList.remove(key);
                        break;
                    }
                }
              /*  for (final String key : CheckList.keySet()) {
                    if (key.equals(name)) {
                        check = CheckList.remove(key);
                        break;
                    }
                } */
                System.out.println("check " + check);
                // para mudar
                ColumnMetadata TmpCol = new ColumnMetadata(notnull, isPrimeKey, foreign, Default, size, Type, name, nonUnique, integerDigits, decimalDigits, index);
           //     TmpCol.items = checks;
                TmpCol.check = check;

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
    public boolean CreateSchema(String url, String name, String userName, String password, Map<String, String> modes) {
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
            final String command = "CREATE " + Temporary + "TABLE " + table + " (id INT " + prime + ") " + Rowid + ";";
            initializeTime();
            statement.execute(command);
            endTime();
            putMessage(new Logger(getUsername(), command, statement.getWarnings() != null ? statement.getWarnings().getMessage() : "", computeTime()));
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid, ArrayList<ColumnMetadata> columnMetadata) {
        return false;
    }

    @Override
    public boolean createTable(final TableMetadata metadata, boolean temporary, boolean rowid) {
        final String Temporary = temporary ? "TEMPORARY " : "";
        final String Rowid = rowid ? "" : " WITHOUT ROWID ";
      //  final String prime = rowid ? "" : "PRIMARY KEY";
        StringBuilder command = new StringBuilder("CREATE " + Temporary + "TABLE " + metadata.getName() + " (");

        for (final ColumnMetadata column : metadata.getColumnMetadata()) {
            final String NotNull = column.NOT_NULL ? " NOT NULL " : "";
            final String Default = !Objects.equals(column.defaultValue, "") && !Objects.equals(column.defaultValue, "null") ? " DEFAULT " + column.defaultValue : "";
            final String IsUnique = column.isUnique ? " UNIQUE " : "";
            //   String Default = "";
            final boolean isForeign = column.foreign.isForeign;
            //      final String ForeignTable = column.ForeignKey == null || Objects.equals(column.ForeignKey[0], "") ? "REFERENCES " + column.ForeignKey[0] + "(" + column.ForeignKey[1] + ")" : "";
            //   final String[] ForeignTable = column.ForeignKey;
            String Type = column.Type;
            if (Arrays.asList(getListChars()).contains(Type)) {
                Type += "(" + column.size + ")";
            } else if (Type.equals("DECIMAL")) Type += "(" + column.integerDigits + ", " + column.decimalDigits + ")";
            String ColumnName;
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

        final String check = metadata.getCheck() == null || metadata.getCheck().isEmpty() ? "" : ", CHECK (" + metadata.getCheck() + ")";

        command.delete(strSize-2, strSize).append(check).append(")").append(Rowid).append(";");
        try {
            System.out.println(command);
            initializeTime();
            statement.execute(command.toString());
            endTime();
            putMessage(new Logger(getUsername(), command.toString(), statement.getWarnings() != null ? statement.getWarnings().getMessage() : "", computeTime()));
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
        StringBuilder command = new StringBuilder("CREATE " + mode + " INDEX " + indexName + " ON " + table + " (");
        for (final String column : columns) {
            command.append(column).append(", ");
        }
        command = new StringBuilder(command.substring(0, command.length() - 2));
        command.append(");");
        System.out.println(command);
        statement.execute(command.toString());
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

    @Override
    public String getTableCheck(final String table) throws SQLException {
        String check_code, check = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sql FROM sqlite_master WHERE type='table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                check_code = rs.next() ? rs.getString("sql") : null;
            }
            System.out.println(check_code);
        }

        if (check_code != null) {
            Pattern pattern = Pattern.compile("CHECK\\s*\\(([^)]+)\\)", Pattern.CASE_INSENSITIVE);
            Matcher m = pattern.matcher(check_code);
            while (m.find()) {
                check = m.group(1);
            }
        }
        return check;
    }

}
