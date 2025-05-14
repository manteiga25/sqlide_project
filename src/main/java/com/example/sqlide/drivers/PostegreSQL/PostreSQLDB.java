package com.example.sqlide.drivers.PostegreSQL;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.TypesModelList;
import org.apache.poi.ss.formula.functions.T;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class PostreSQLDB extends DataBase {

    public TypesModelList typesOfDB;

    public int buffer = 250;

    private String MsgException;

    public PostreSQLDB() {
        typesOfDB = new PostgreSQLTypeList();
        idType = "CTID";
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
        return null;
    }

    @Override
    public HashMap<String, String> getEvents() {
        return null;
    }

    @Override
    public void disconnect() throws SQLException {

    }

    @Override
    public boolean renameTable(String Table, String newTableName) {
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
    public boolean createColumn(String table, String column, ColumnMetadata meta, boolean fill) {
        if (meta.isForeign || meta.IsPrimaryKey) {
            System.out.println("chwguei");
            return createSpecialColumn(table, column, meta);
        }
        final String NotNull = meta.NOT_NULL ? " NOT NULL " : "";
        final String IsUnique = meta.isUnique ? " UNIQUE " : "";
        String Default = meta.defaultValue == null || meta.defaultValue.isEmpty() ? " DEFAULT " + meta.defaultValue : "";
        Default = "";
        final boolean isForeign = meta.isForeign;
        String Type = meta.Type;
        final String ColumnName = column;
        String PrimaryKey = "";
        if (meta.IsPrimaryKey) {
            PrimaryKey = " PRIMARY KEY";
        }
        //     else if (isForeign) {
        //     ColumnName = "CONSTRAINT fk_h_fgr FOREIGN KEY (" + column + ") REFERENCES(" + ;
        //       ColumnName = "CONSTRAINT idyhgfvduydf FOREIGN KEY (" + "id" + ") REFERENCES " +  meta.ForeignKey[0] + "(" + meta.ForeignKey[1] + "))";
        //     Type = "";
        //   Default = "";
        // }
        try {
            System.out.println("ALTER TABLE " + table + " ADD " + ColumnName + Type + NotNull + Default + PrimaryKey + ";");
            statement.execute("ALTER TABLE " + table + " ADD " + ColumnName + " " + Type + NotNull + IsUnique + Default + PrimaryKey + ";");
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
            statement.execute("CREATE TABLE " + table + " (id INTEGER);");
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    protected boolean createSpecialColumn(String table, String column, ColumnMetadata meta) {
        boolean ret = true;
        final String TableCopy = table + "CopySpecial";
        ArrayList<ColumnMetadata> columns = null;

        try {
            connection.setAutoCommit(false);

            if (!renameTable(table, TableCopy)) {
                System.out.println("hmmmmmmm");
                throw new SQLException(MsgException);
            }

            columns = getColumnsMetadata(TableCopy);
            if (columns == null) {
                System.out.println("hmmmmmmm2");
                throw new SQLException(MsgException);
            }

            ArrayList<ColumnMetadata> originalColumns = (ArrayList<ColumnMetadata>) columns.clone();
            columns.add(meta);

            if (!createTableSpecial(table, columns)) {
                System.out.println("hmmmmmmm3");
                throw new SQLException(MsgException);
            }

            if (!copyDataTable(table, originalColumns)) {
                System.out.println("hmmmmmmm4");
                throw new SQLException(MsgException);
            }

            if (!deleteTable(TableCopy)) {
                System.out.println("hmmmmmmm5");
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
                System.out.println("mau " + MsgException);
                ret = false;
            }
        }
        return ret;
    }

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
            System.out.println("ridiculo " + e.getMessage());
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

        command.delete(strSize-1, strSize).append(") SELECT ");

        for (final ColumnMetadata column : columns) {
            command.append(column.Name).append(", ");
        }

        strSize = command.length();

        command.delete(strSize-1, strSize).append(" FROM ").append(table).append("CopySpecial");

        try {
            System.out.println(command);
            statement.execute(command.toString());
        } catch (SQLException e) {
            MsgException = GetException();
            return false;
        }
        return true;
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
    public boolean updateData(String Table, final String column, final Object value, final String[] index, String PrimeKey, final String tmp) {
        String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
        boolean prime = false;
        if (PrimeKey == null || PrimeKey.isEmpty()) {
            command += "CTID = '" + index[0] + "' RETURNING ctid;";
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
          //  pstmt.setObject(1, value);
         //   converter(pstmt, value, "INTEGER");
            if (prime) {
                pstmt.setObject(2, tmp);
            }
            System.out.println(command);
            ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    index[0] = rs.getString("ctid");
                }

                //  int affectedRows = pstmt.executeUpdate();
           // System.out.println("rows " + affectedRows);
            //  statement.execute(command);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    @Override
    public boolean updateData(String Table, final String column, final String value, final String[] index, final String type, String PrimeKey, final String tmp) {
        String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
        boolean prime = false;
        if (PrimeKey == null || PrimeKey.isEmpty()) {
            command += "CTID = '" + index[0] + "' RETURNING ctid;";
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
            pstmt.setObject(1, value, java.sql.Types.OTHER);
           // converter(pstmt, value, type);
            if (prime) {
                pstmt.setObject(2, tmp);
            }
            System.out.println(command);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                index[0] = rs.getString("ctid");
            }

            //  int affectedRows = pstmt.executeUpdate();
            // System.out.println("rows " + affectedRows);
            //  statement.execute(command);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

/*    private void converter(PreparedStatement pstmt, Object value, String type) throws SQLException {
        switch (type.toUpperCase()) {
            case "SMALLINT", "INT2":
                pstmt.setShort(1, Short.parseShort(value.toString()));
                break;
            case "INTEGER", "INT4":
                pstmt.setInt(1, Integer.parseInt(value.toString()));
                break;
                case "BIGINT", "INT8":
                pstmt.setLong(1, Long.parseLong(value.toString()));
                break;
                case "REAL", "FLOAT4":
                pstmt.setFloat(1, Float.parseFloat(value.toString()));
                break;
                case "DOUBLE", "FLOAT8":
                pstmt.setDouble(1, Double.parseDouble(value.toString()));
                break;
                case "BOOLEAN":
                pstmt.setBoolean(1, Boolean.parseBoolean(value.toString()));
                break;
                case "DATE":
                pstmt.setDate(1, Date.valueOf(value.toString()));
                break;
                case "TIME":
                pstmt.setTime(1, Time.valueOf(value.toString()));
                break;
                case "TIMESTAMP":
                pstmt.setTimestamp(1, Timestamp.valueOf(value.toString()));
                break;
                case "CHAR", "VARCHAR":
                pstmt.setString(1, value.toString());
                break;
            case "BLOB":
                        pstmt.setBlob(1, (Blob) value);
                        break;
                        default:
                                         //    throw new RuntimeException("Unknown type: " + type);
                            pstmt.setObject(1, value, java.sql.Types.OTHER);
                            break;

        }
    } */

    @Override
    public boolean updateData(String Table, final String column, final Object value, final String index, String PrimeKey, final String tmp) {
        String command = "UPDATE " + Table + " SET " + column + " = ? WHERE ";
        boolean prime = false;
        if (PrimeKey == null || PrimeKey.isEmpty()) {
            command += "CTID = '" + index + "';";
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
    public void renameDatabase(String name) {
        try {
            statement.execute("ALTER DATABASE " + super.databaseName + " RENAME TO " + name + ";");
            super.databaseName = name;
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    @Override
    public ArrayList<DataForDB> fetchData(String Table, ArrayList<String> Columns, long offset, String primeKey) {
        Columns.add("CTID");
        ArrayList<DataForDB> data = new ArrayList<>();
        final String command = "SELECT CTID, * FROM " + Table + " LIMIT " + buffer + " OFFSET " + offset;
        System.out.println("command " + command);
        try {
            ResultSet rs = statement.executeQuery(command);
            while (rs.next()) {
                HashMap<String, String> tmpData = new HashMap<>();
                for (String col : Columns) {
                    System.out.println(col);
                    Object val = rs.getObject(col);
                    if (col.equals("CTID")) {
                        col = "ROWID";
                    }
                    String valStr = "null";
                    if (val != null) {
                        valStr = val.toString();
                    }
                    System.out.println(valStr);
                    tmpData.put(col, valStr);
                }
                data.add(new DataForDB(tmpData));
            }
           // Columns.
            return  data;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
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
    public ArrayList<ArrayList<String>> fetchDataBackup(String Table, ArrayList<String> Columns, long offset) {
        return null;
    }

    @Override
    public ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long offset) {
        return null;
    }

    @Override
    public ArrayList<ArrayList<Object>> fetchDataBackupObject(String Table, ArrayList<String> Columns, long limit, long offset) {
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
    public boolean updateData(String Table, HashMap<String, String> data, long index) {
        StringBuilder command = new StringBuilder("UPDATE " + Table + " SET ");
        for (final String column : data.keySet()) {
            command.append(column).append(" = '").append(data.get(column)).append("'").append(",");
        }
        command.replace(command.length()-2, command.length(), "");
        command.append(" WHERE CRID = '").append(index).append("';");
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
        final String command = "UPDATE " + Table + " SET " + column + " = '" + value + "' WHERE ctid = " + index + ";";
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
            command += "CTID = '" + index + "';";
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
    public ArrayList<String> getColumnsName(String Table) {
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

    @Override
    protected HashMap<String, Boolean> isUnique(String Table) {
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
    protected HashMap<String, String[]> getForeign(String Table) {
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

    private ArrayList<String> searchTypes(final String table) throws SQLException {
            final ArrayList<String> types = new ArrayList<>();
           /* String sql = "SELECT \n" +
                    "    t.oid AS OID,\n" +
                    "    t.typname AS alias_name,\n" +
                    "    bt.typname AS base_type_name\n" +
                    "FROM pg_type t\n" +
                    "LEFT JOIN pg_type bt ON t.typbasetype = bt.oid\n" +
                    "WHERE t.typtype = 'd';"; */
            String sql = "SELECT \n" +
                    "    a.attname AS column_name,\n" +
                    "    t.typname AS column_type,\n" +
                    "    CASE \n" +
                    "        WHEN t.typtype = 'd' THEN bt.typname \n" +
                    "        WHEN t.typtype = 'e' THEN 'enum' \n" +
                    "        ELSE t.typname \n" +
                    "    END AS real_type\n" +
                    "FROM pg_attribute a\n" +
                    "JOIN pg_type t ON a.atttypid = t.oid\n" +
                    "LEFT JOIN pg_type bt ON t.typbasetype = bt.oid\n" +
                    "WHERE a.attrelid = '" + table + "'::regclass\n" +
                    "  AND a.attnum > 0\n" +
                    "  AND NOT a.attisdropped;";
            PreparedStatement ps = connection.prepareStatement(sql);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
          //      int userTypeId = rs.getInt("OID");
                String aliasName = rs.getString("column_name");
                String baseTypeName = rs.getString("column_type");
                types.add(rs.getString("real_type").toUpperCase());
                System.out.println("Tipo " + aliasName + " ( " +
                        ") é um alias para o tipo " + baseTypeName);
            }
            return types;
       /*     sql = """
                    SELECT e.enumlabel AS enum_value
                    FROM pg_enum e
                    JOIN pg_type t ON t.oid = e.enumtypid
                    WHERE t.typname = 'name_type'
                    ORDER BY e.enumsortorder;""";
            PreparedStatement ps2 = connection.prepareStatement(sql);
            ResultSet rs2 = ps2.executeQuery();
            while (rs2.next()) {
                //      int userTypeId = rs.getInt("OID");
                String e = rs2.getString("enum_value");
                System.out.println("perm " + e);
            } */

    }

    private ArrayList<String> EnumChecks(final String type) throws SQLException {
        final ArrayList<String> enums = new ArrayList<>();
        String sql = "SELECT e.enumlabel AS enum_value FROM pg_enum e JOIN pg_type t ON t.oid = e.enumtypid WHERE t.typname = '" + type + "' ORDER BY e.enumsortorder;";
        PreparedStatement ps = connection.prepareStatement(sql);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            //      int userTypeId = rs.getInt("OID");
            enums.add(rs.getString("enum_value"));
            //System.out.println("perm " + e);
        }
        return enums;
    }

    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(String Table) {
        ArrayList<ColumnMetadata> ColumnsMetadata = new ArrayList<>();
        final ArrayList<String> PrimaryKeyList = PrimaryKeyList(Table);
        final HashMap<String, String[]> ForeignKeyList = getForeign(Table);
        HashMap<String, Boolean> uniqueColumns = isUnique(Table);
        String[] foreign = new String[2];
        try {
            final ArrayList<String> RealTypes = searchTypes(Table);
            ResultSet columns = connection.getMetaData().getColumns(null, null, Table, null);
            for  (int iter = 0; columns.next(); iter++) {
                boolean isForeign = false;
                final String name = columns.getString("COLUMN_NAME");
                String Type = columns.getString("TYPE_NAME");
                String aliasType = null;
                ArrayList<String> listEnum = null;
                if (RealTypes.get(iter).equals("ENUM")) {
                    aliasType = Type;
                    Type = RealTypes.get(iter);
                    listEnum = EnumChecks(aliasType);
                } else {
                    Type = Type.toUpperCase();
                }
                ArrayList<String> checkValues = null;
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
                        foreign = ForeignKeyList.get(key);
                        ForeignKeyList.remove(key);
                        isForeign = true;
                        break;
                    }
                }
               // if (Type.toUpperCase().contains("(")) {
                  //  checkValues = getCheckConstraintValues(Type);
                  //  Type = "ENUM";
               // }
                // para mudar
                ColumnMetadata TmpCol = new ColumnMetadata(notnull, isPrimeKey, foreign, isForeign, Default, size, Type, name, nonUnique, integerDigits, decimalDigits, index);
                     TmpCol.items = listEnum;
                     TmpCol.aliasType = aliasType;

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

    private ArrayList<String> getCheckConstraintValues(String Column) {
        ArrayList<String> values = new ArrayList<>();
        final int init = Column.indexOf("(");
        final int end = Column.indexOf(")");
        if (init != -1 && end != -1) {
            final String check = Column.substring(init + 1, end);
            final String[] checkValues = check.split(",");
            for (final String value : checkValues) {
                values.add(value.trim());
            }
        }
        return values.isEmpty() ? null : values;
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
        final String completeURL = "jdbc:postgresql://" + url;
        try {
            connection = DriverManager.getConnection(completeURL, userName, password);
            statement = connection.createStatement();
            //  FormatDBCreation(formatData);
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    @Override
    public boolean connect(String url, String name, String userName, String password) {
        final String completeURL = "jdbc:postgresql://" + url + name;
        try {
            connection = DriverManager.getConnection(completeURL, userName, password);
            statement = connection.createStatement();
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
            statement.execute("COMMIT;");
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
