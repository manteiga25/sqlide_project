package com.example.sqlide.drivers.MySQL;

import com.example.sqlide.Configuration.permissionConfController;
import com.example.sqlide.Function.FunctionController;
import com.example.sqlide.Metadata.BuiltInRoutines;
import com.example.sqlide.Metadata.CheckMetadata;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.Metadata.IndexMetadata;
import com.example.sqlide.Metadata.RoutineMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.Procedure.ProcedureController;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.IOPragma_Interface;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.MemoryPragmaInterface;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.PermissionPragmaInterface;
import com.example.sqlide.drivers.model.QueryBuilder;
import com.example.sqlide.drivers.model.SQLTypes;

import java.sql.*;
import java.time.LocalTime;
import java.util.*;

/**
 * Driver do MySQL.
 *
 * <p>O SQL passa pelo {@link QueryBuilder}. O que estava mal e mudou:</p>
 * <ul>
 *   <li>Metade dos métodos devolvia {@code false} sem fazer nada: {@code renameTable},
 *       {@code renameColumn}, {@code modifyColumnType}, {@code TableHasPrimeKey}, o
 *       {@code removeData} da grelha; {@code executeCode}, {@code createTrigger},
 *       {@code changeCommitMode}, {@code disconnect} e os de scripts tinham corpo vazio.</li>
 *   <li>O MySQL não tem ROWID, mas a leitura das tabelas sem chave primária pedia
 *       {@code SELECT ROWID, *}, e as edições usavam {@code WHERE ROWID = ...}.</li>
 *   <li>No {@code getColumnsMetadata} o NOT NULL vinha invertido, o índice de cada coluna
 *       era o próprio nome da coluna (apagar uma coluna tentava apagar um índice que não
 *       existia) e as chaves estrangeiras nunca ficavam marcadas como tal. Os metadados vêm
 *       agora do {@code information_schema}: o JDBC chama BIT a um TINYINT(1), e redefinir a
 *       coluna com isso mudava-lhe o tipo.</li>
 *   <li>O {@code createColumn} ignorava o DEFAULT e punha a chave estrangeira na própria
 *       coluna, onde o MySQL a ignora sem avisar.</li>
 *   <li>{@code getTriggers} pedia uma coluna TRIGGER_DEFINITION que não existe.</li>
 *   <li>{@code createTable} acrescentava WITHOUT ROWID, que é sintaxe do SQLite.</li>
 * </ul>
 */
public class MySQLDB extends DataBase {

    public MySQLDB() {
        SQLType = SQLTypes.MYSQL;
        super.databaseInfo = new MySQLInfo();
        // O MySQL não tem uma pseudo-coluna que identifique a linha.
        super.idType = "";

        Updater(updaterInterface);
        Inserter(inserterInterface);

        Permission(permissionPragmaInterface);
        Memory(memoryPragmaInterface);
        IO(ioPragmaInterface);
    }

    /** Sem isto o DatabaseMetaData procura as tabelas em todas as bases de dados do servidor. */
    @Override
    protected String metadataCatalog() {
        return databaseName;
    }

    /**
     * Funções embutidas do MySQL, mais as rotinas e vistas criadas nesta base de dados.
     *
     * <p>O {@code information_schema.routines} guarda os procedimentos e funções do
     * utilizador com a assinatura e o comentário que lhes foi dado no CREATE.</p>
     */
    @Override
    public ArrayList<RoutineMetadata> getRoutines() {
        ArrayList<RoutineMetadata> routines = BuiltInRoutines.forDialect(SQLTypes.MYSQL);

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("""
                     SELECT ROUTINE_NAME, ROUTINE_TYPE, DTD_IDENTIFIER, ROUTINE_COMMENT
                     FROM information_schema.routines
                     WHERE ROUTINE_SCHEMA = DATABASE()
                     ORDER BY ROUTINE_NAME""")) {
            while (rs.next()) {
                final String name = rs.getString("ROUTINE_NAME");
                final boolean isProcedure = "PROCEDURE".equalsIgnoreCase(rs.getString("ROUTINE_TYPE"));
                routines.add(new RoutineMetadata(name,
                        isProcedure ? RoutineMetadata.Kind.PROCEDURE : RoutineMetadata.Kind.FUNCTION,
                        "User defined",
                        readParameters(name),
                        isProcedure ? "" : nullToEmpty(rs.getString("DTD_IDENTIFIER")),
                        nullToEmpty(rs.getString("ROUTINE_COMMENT")),
                        false));
            }
        } catch (SQLException e) {
            System.err.println("Could not list MySQL routines: " + e.getMessage());
        }

        routines.addAll(readViewsFromInformationSchema("TABLE_SCHEMA", getDatabaseName()));
        return routines;
    }

    /** Assinatura da rotina, montada a partir dos seus parâmetros declarados. */
    private String readParameters(final String routine) {
        final StringJoiner signature = new StringJoiner(", ", "(", ")");
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT PARAMETER_NAME, DTD_IDENTIFIER
                FROM information_schema.parameters
                WHERE SPECIFIC_SCHEMA = DATABASE() AND SPECIFIC_NAME = ? AND PARAMETER_NAME IS NOT NULL
                ORDER BY ORDINAL_POSITION""")) {
            stmt.setString(1, routine);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    signature.add(rs.getString("PARAMETER_NAME") + " " + rs.getString("DTD_IDENTIFIER"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not read parameters of " + routine + ": " + e.getMessage());
        }
        return signature.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public boolean connect(String DBName, Map<String, String> formatData) {
        return false;
    }

    @Override
    public boolean connect(String DBName) {
        return false;
    }

    private final IOPragma_Interface ioPragmaInterface = new IOPragma_Interface() {

        private boolean getFlag(String query, int expected) throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                if (rs.next()) {
                    return rs.getInt(1) == expected;
                }
            }
            return false;
        }

        private void setVariable(String varName, Object value) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET GLOBAL " + varName + " = " + value);
            }
        }

        @Override
        public boolean getFullSync() throws SQLException {
            // SQLite PRAGMA fullfsync → MySQL = flush log at trx commit = 1
            return getFlag("SELECT @@innodb_flush_log_at_trx_commit", 1);
        }

        @Override
        public boolean getFullSyncCheckpoint() throws SQLException {
            // Não existe igual → podemos mapear para doublewrite
            return getFlag("SELECT @@innodb_doublewrite", 1);
        }

        @Override
        public boolean getDelete() throws SQLException {
            // SQLite secure_delete → MySQL não tem; usamos innodb_file_per_table ON/OFF
            return getFlag("SELECT @@innodb_file_per_table", 1);
        }

        @Override
        public boolean getCellSize() throws SQLException {
            // SQLite cell_size_check → MySQL usa checksums em páginas
            return getFlag("SELECT @@innodb_log_checksums", 1);
        }

        @Override
        public String getFlushMethod() throws SQLException {
            return getStrVar("innodb_flush_method");
        }

        @Override
        public int getBinlogSync() throws SQLException {
            return getIntVar("sync_binlog");
        }

        @Override
        public int getIOCapacity() throws SQLException {
            return getIntVar("innodb_io_capacity");
        }

        @Override
        public int getIOCapacityMax() throws SQLException {
            return getIntVar("innodb_io_capacity_max");
        }

        @Override
        public boolean getFlushNeighbors() throws SQLException {
            return getBoolVar("innodb_flush_neighbors");
        }

        @Override
        public boolean getNativeAIO() throws SQLException {
            return getBoolVar("innodb_use_native_aio");
        }

        private int getIntVar(String var) throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@GLOBAL." + var)) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        private String getStrVar(String var) throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@GLOBAL." + var)) {
                return rs.next() ? rs.getString(1) : null;
            }
        }

        private boolean getBoolVar(String var) throws SQLException {
            return getIntVar(var) == 1;
        }

        private void setVar(String var, Object value) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET GLOBAL " + var + " = " + value);
            }
        }

        @Override
        public void setFullSync(boolean state) throws SQLException {
            // 1 = sync a cada commit, 2 = sync periódico, 0 = mais rápido mas arriscado
            setVariable("innodb_flush_log_at_trx_commit", state ? 1 : 2);
        }

        @Override
        public void setFullSyncCheckpoint(boolean state) throws SQLException {
            // mapeamos para innodb_doublewrite
            setVariable("innodb_doublewrite", state ? 1 : 0);
        }

        @Override
        public void setDelete(boolean state) throws SQLException {
            // não é 100% igual, mas file_per_table define se o drop apaga o tablespace
            setVariable("innodb_file_per_table", state ? 1 : 0);
        }

        @Override
        public void setCellSize(boolean state) throws SQLException {
            // log checksum = ON/OFF
            setVariable("innodb_log_checksums", state ? 1 : 0);
        }

        @Override
        public void setFlushMethod(String method) throws SQLException {
            setVar("innodb_flush_method", "'" + method + "'");
        }

        @Override
        public void setBinlogSync(int value) throws SQLException {
            setVar("sync_binlog", value);
        }

        @Override
        public void setIOCapacity(int value) throws SQLException {
            setVar("innodb_io_capacity", value);
        }

        @Override
        public void setIOCapacityMax(int value) throws SQLException {
            setVar("innodb_io_capacity_max", value);
        }

        @Override
        public void setFlushNeighbors(boolean state) throws SQLException {
            setVar("innodb_flush_neighbors", state ? 1 : 0);
        }

        @Override
        public void setNativeAIO(boolean state) throws SQLException {
            setVar("innodb_use_native_aio", state ? 1 : 0);
        }

    };

    private final PermissionPragmaInterface permissionPragmaInterface = new PermissionPragmaInterface() {

        /** Privilégios ao nível de tabela que o painel mostra, por ordem de utilidade. */
        private final List<String> PRIVILEGES = List.of(
                "SELECT", "INSERT", "UPDATE", "DELETE",
                "CREATE", "DROP", "ALTER", "INDEX", "REFERENCES", "GRANT OPTION");

        @Override
        public List<String> supportedPrivileges() {
            return PRIVILEGES;
        }

        @Override
        public List<String> authenticationMethods() {
            return List.of("caching_sha2_password", "mysql_native_password", "sha256_password");
        }

        /**
         * True se a ligação atual tem SUPER, CREATE USER ou ALL PRIVILEGES.
         *
         * <p>A versão anterior comparava o GRANTEE (que é {@code 'user'@'host'}) com a
         * literal "SUPER" e olhava só para a primeira linha, por isso nunca dava
         * verdadeiro — e o resultado era usado invertido, desativando os controlos
         * justamente para quem podia editar.</p>
         */
        @Override
        public boolean canManageUsers() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("""
                         SELECT PRIVILEGE_TYPE
                         FROM information_schema.user_privileges
                         WHERE GRANTEE = CONCAT("'", SUBSTRING_INDEX(CURRENT_USER(), '@', 1),
                                                "'@'", SUBSTRING_INDEX(CURRENT_USER(), '@', -1), "'")""")) {
                while (rs.next()) {
                    String privilege = rs.getString("PRIVILEGE_TYPE");
                    if ("SUPER".equalsIgnoreCase(privilege)
                            || "CREATE USER".equalsIgnoreCase(privilege)
                            || "ALL PRIVILEGES".equalsIgnoreCase(privilege)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public Map<String, permissionConfController.userInformation> getUsers() throws SQLException {
            Map<String, permissionConfController.userInformation> mapList = new LinkedHashMap<>();

            // Quem não pode administrar continua a ver a sua própria conta, em vez de
            // uma lista vazia sem explicação nenhuma.
            boolean admin = canManageUsers();
            String currentUser = currentUserName();

            String query = admin
                    ? "SELECT user, host, plugin FROM mysql.user ORDER BY user, host"
                    : "SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1) AS user, "
                    + "SUBSTRING_INDEX(CURRENT_USER(), '@', -1) AS host, '' AS plugin";

            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    String name = rs.getString("user");
                    // A palavra-passe nunca é lida: o MySQL só guarda o resumo, e mostrá-lo
                    // num campo de texto dava a ideia errada de que era editável.
                    permissionConfController.userInformation user =
                            new permissionConfController.userInformation(
                                    name, rs.getString("host"), "", rs.getString("plugin"),
                                    name.equals(currentUser));
                    mapList.put(user.toString(), user);
                }
            }
            return mapList;
        }

        private String currentUserName() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT SUBSTRING_INDEX(CURRENT_USER(), '@', 1)")) {
                return rs.next() ? rs.getString(1) : getUsername();
            }
        }

        @Override
        public ArrayList<String> getPermissions(String user) throws SQLException {
            ArrayList<String> permissions = new ArrayList<>();
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SHOW GRANTS FOR " + user)) {
                while (rs.next()) permissions.add(rs.getString(1));
            }
            return permissions;
        }

        /**
         * Lê os privilégios efetivos no âmbito pedido a partir do information_schema, em
         * vez de tentar decifrar o texto do SHOW GRANTS.
         */
        @Override
        public Map<String, Boolean> getPermissions(String user, String db, String table) throws SQLException {
            Map<String, Boolean> permissions = new LinkedHashMap<>();
            for (String privilege : PRIVILEGES) permissions.put(privilege, false);

            String grantee = user.startsWith("'") ? user : "'" + user + "'";

            // Privilégios globais (*.*) valem para tudo o que está abaixo.
            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT PRIVILEGE_TYPE FROM information_schema.user_privileges WHERE GRANTEE = ?")) {
                stmt.setString(1, grantee);
                readPrivileges(stmt, permissions);
            }

            try (PreparedStatement stmt = connection.prepareStatement(
                    "SELECT PRIVILEGE_TYPE FROM information_schema.schema_privileges"
                            + " WHERE GRANTEE = ? AND TABLE_SCHEMA = ?")) {
                stmt.setString(1, grantee);
                stmt.setString(2, db);
                readPrivileges(stmt, permissions);
            }

            if (table != null && !table.isBlank()) {
                try (PreparedStatement stmt = connection.prepareStatement(
                        "SELECT PRIVILEGE_TYPE FROM information_schema.table_privileges"
                                + " WHERE GRANTEE = ? AND TABLE_SCHEMA = ? AND TABLE_NAME = ?")) {
                    stmt.setString(1, grantee);
                    stmt.setString(2, db);
                    stmt.setString(3, table);
                    readPrivileges(stmt, permissions);
                }
            }

            return permissions;
        }

        private void readPrivileges(PreparedStatement stmt, Map<String, Boolean> target) throws SQLException {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String privilege = rs.getString("PRIVILEGE_TYPE");
                    if ("ALL PRIVILEGES".equalsIgnoreCase(privilege)) {
                        target.replaceAll((_, _) -> true);
                    } else if (target.containsKey(privilege.toUpperCase(Locale.ROOT))) {
                        target.put(privilege.toUpperCase(Locale.ROOT), true);
                    }
                }
            }
        }

        @Override
        public void addUser(permissionConfController.userInformation user) throws SQLException {
            // O CREATE USER do MySQL não aceita parâmetros para o nome nem para o plugin,
            // por isso a defesa é escapar as literais e recusar nomes com aspas.
            String sql = String.format("CREATE USER %s IDENTIFIED WITH %s BY %s",
                    account(user.name, user.localhost),
                    identifier(user.plugin),
                    literal(user.password));
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(sql);
            }
        }

        @Override
        public void dropUser(String user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("DROP USER " + normaliseAccount(user));
            }
        }

        @Override
        public void changePassword(permissionConfController.userInformation user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("ALTER USER %s IDENTIFIED BY %s",
                        account(user.name, user.localhost), literal(user.password)));
            }
        }

        @Override
        public void grant(String privilege, String db, String table, String user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("GRANT %s ON %s TO %s",
                        privilegeKeyword(privilege), scope(db, table), normaliseAccount(user)));
            }
        }

        @Override
        public void revoke(String privilege, String db, String table, String user) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(String.format("REVOKE %s ON %s FROM %s",
                        privilegeKeyword(privilege), scope(db, table), normaliseAccount(user)));
            }
        }

        /** {@code db.table}, ou {@code db.*} quando o âmbito é a base de dados inteira. */
        private String scope(String db, String table) {
            String target = (table == null || table.isBlank()) ? "*" : identifier(table);
            return identifier(db) + "." + target;
        }

        private String privilegeKeyword(String privilege) {
            if (!PRIVILEGES.contains(privilege)) {
                throw new IllegalArgumentException("Unknown privilege: " + privilege);
            }
            return privilege;
        }

        private String account(String name, String host) {
            return literal(name) + "@" + literal(host);
        }

        /** Aceita tanto {@code 'a'@'b'} como {@code a} vindos da lista da UI. */
        private String normaliseAccount(String user) {
            if (user.contains("@")) return user;
            return literal(user) + "@'%'";
        }

        private String identifier(String value) {
            if (value == null) return "*";
            return "`" + value.replace("`", "``") + "`";
        }

        private String literal(String value) {
            if (value == null) return "''";
            return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
        }
    };

    private final MemoryPragmaInterface memoryPragmaInterface = new MemoryPragmaInterface() {
        @Override
        public int getCacheSize() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@innodb_buffer_pool_size / @@innodb_page_size")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public int getPageSize() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@innodb_page_size")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public int getMMapSize() throws SQLException {
            throw new SQLFeatureNotSupportedException("Map size not supported for MySQL");
        }

        @Override
        public int getHardHeapSize() throws SQLException {
            // Não existe equivalente direto; pode mapear para buffer de memória máximo
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@max_heap_table_size")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public int getSoftHeapSize() throws SQLException {
            // Pode usar tmp_table_size como soft heap
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@tmp_table_size")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public int getMaxPages() throws SQLException {
            // InnoDB buffer pool pages
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@innodb_buffer_pool_size / @@innodb_page_size")) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        @Override
        public boolean getCacheSpill() throws SQLException {
            // Não há cache spill igual ao SQLite → usar tmp_disk_table
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@tmp_table_size < @@max_heap_table_size")) {
                return rs.next() && rs.getBoolean(1);
            }
        }

        @Override
        public long getTotalPages() throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@innodb_buffer_pool_size / @@innodb_page_size")) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }

        @Override
        public void setCacheSize(int size) throws SQLException {
            // Em MySQL, ajustar buffer pool size (precisa SUPER privilege e restart em alguns casos)
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET GLOBAL innodb_buffer_pool_size = " + size * getPageSize());
            }
        }

        private int getVariable(String varName) throws SQLException {
            try (Statement stmt = connection.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT @@GLOBAL." + varName)) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }

        private void setVariable(String varName, int size) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET GLOBAL " + varName + " = " + size);
            }
        }

        @Override
        public int getLogBufferSize() throws SQLException {
            return getVariable("innodb_log_buffer_size");
        }

        @Override
        public int getSortBufferSize() throws SQLException {
            return getVariable("sort_buffer_size");
        }

        @Override
        public int getJoinBufferSize() throws SQLException {
            return getVariable("join_buffer_size");
        }

        @Override
        public int getReadBufferSize() throws SQLException {
            return getVariable("read_buffer_size");
        }

        @Override
        public int getReadRndBufferSize() throws SQLException {
            return getVariable("read_rnd_buffer_size");
        }

        @Override
        public int getThreadStackSize() throws SQLException {
            return getVariable("thread_stack");
        }

        @Override
        public void setLogBufferSize(int size) throws SQLException {
            setVariable("innodb_log_buffer_size", size);
        }

        @Override
        public void setSortBufferSize(int size) throws SQLException {
            setVariable("sort_buffer_size", size);
        }

        @Override
        public void setJoinBufferSize(int size) throws SQLException {
            setVariable("join_buffer_size", size);
        }

        @Override
        public void setReadBufferSize(int size) throws SQLException {
            setVariable("read_buffer_size", size);
        }

        @Override
        public void setReadRndBufferSize(int size) throws SQLException {
            setVariable("read_rnd_buffer_size", size);
        }

        @Override
        public void setThreadStackSize(int size) throws SQLException {
            setVariable("thread_stack", size);
        }


        @Override
        public void setPageSize(int size) throws SQLException {
            // Page size é fixo no MySQL, não dá para mudar em runtime
            throw new SQLException("Page size is fixed in MySQL and cannot be changed at runtime.");
        }

        @Override
        public void setMMapSize(int size) throws SQLException {
            // Não aplicável → ignorado
        }

        @Override
        public void setHardHeapSize(int size) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET GLOBAL max_heap_table_size = " + size);
            }
        }

        @Override
        public void setSoftHeapSize(int size) throws SQLException {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET GLOBAL tmp_table_size = " + size);
            }
        }

        @Override
        public void setMaxPages(int size) throws SQLException {
            // buffer pool size = pages * page_size
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("SET GLOBAL innodb_buffer_pool_size = " + size * getPageSize());
            }
        }

        @Override
        public void setCacheSpill(boolean state) throws SQLException {
            // Não há flag direta → poderia simular mudando tmp_table_size vs max_heap_table_size
            throw new SQLException("Cache spill behavior is not directly configurable in MySQL.");
        }
    };

    /**
     * Triggers desta base de dados, cada um com o CREATE TRIGGER completo (o que o
     * {@code SHOW CREATE TRIGGER} devolve, sem o DEFINER, para poder ser executado por
     * outra conta).
     */
    @Override
    public HashMap<String, String> getTriggers() {
        final HashMap<String, String> code = new HashMap<>();
        try {
            for (final String trigger : names("SELECT TRIGGER_NAME FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA = DATABASE()")) {
                code.put(trigger, showCreate("SHOW CREATE TRIGGER " + quote(trigger), "SQL Original Statement"));
            }
            return code;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
    }

    /** Eventos agendados, com o CREATE EVENT completo. */
    @Override
    public HashMap<String, String> getEvents() {
        final HashMap<String, String> code = new HashMap<>();
        try {
            for (final String event : names("SELECT EVENT_NAME FROM information_schema.EVENTS WHERE EVENT_SCHEMA = DATABASE()")) {
                code.put(event, showCreate("SHOW CREATE EVENT " + quote(event), "Create Event"));
            }
            return code;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
    }

    private List<String> names(final String query) throws SQLException {
        final List<String> names = new ArrayList<>();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) names.add(rs.getString(1));
        }
        return names;
    }

    private String showCreate(final String query, final String column) throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            if (!rs.next()) return "";
            final String sql = rs.getString(column);
            return sql == null ? "" : sql.replaceFirst("(?i)\\s+DEFINER\\s*=\\s*\\S+", "");
        }
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
     * ADD COLUMN com tudo o que o formulário pede. Uma coluna de chave primária numa tabela
     * que já tem chave torna a chave composta (DROP PRIMARY KEY + ADD PRIMARY KEY no mesmo
     * ALTER); a chave estrangeira vai como restrição com nome, porque o MySQL ignora o
     * REFERENCES escrito na própria coluna.
     */
    @Override
    public boolean createColumn(String table, String column, ColumnMetadata meta, boolean fill) {
        final ColumnMetadata added = meta.copy();
        added.Name = column;
        try {
            final boolean fillForeign = fill && QueryBuilder.isForeign(added);
            final List<String> childKey = PrimaryKeyList(table);
            final List<String> parentKey = fillForeign ? PrimaryKeyList(added.foreign.tableRef) : List.of();
            // Verificado antes do ALTER: no MySQL o DDL não se desfaz.
            if (fillForeign && (childKey == null || childKey.size() != 1 || parentKey == null || parentKey.size() != 1)) {
                throw new SQLException("Fill foreign matches rows by primary key, so both tables need a single-column primary key.");
            }

            final QueryBuilder.AlterTable alter = builder().alterTable(table);
            if (added.IsPrimaryKey && childKey != null && !childKey.isEmpty()) {
                alter.addColumn(added, false);
                alter.dropPrimaryKey(null);
                final List<String> keys = new ArrayList<>(childKey);
                keys.add(column);
                alter.addPrimaryKey(keys);
            } else {
                alter.addColumn(added, true);
            }
            for (final String sql : alter.build()) execute(sql);

            if (fillForeign) {
                execute(builder().updateFromParent(table, column, childKey.getFirst(),
                        added.foreign.tableRef, added.foreign.columnRef, parentKey.getFirst()));
            }
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

    /** O MySQL recusa apagar uma coluna presa a uma chave estrangeira: essa sai primeiro. */
    @Override
    public boolean deleteColumn(ArrayList<ColumnMetadata> columns, String columnName, String table) {
        try {
            for (final String foreignKey : foreignKeyNames(table, columnName)) {
                for (final String sql : builder().alterTable(table).dropForeignKey(foreignKey).build()) execute(sql);
            }
            for (final String sql : builder().alterTable(table).dropColumn(columnName).build()) execute(sql);
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    /** Nomes das chaves estrangeiras que usam a coluna (ou todas as da tabela, com column null). */
    private List<String> foreignKeyNames(final String table, final String column) throws SQLException {
        final List<String> names = new ArrayList<>();
        try (ResultSet rs = connection.getMetaData().getImportedKeys(metadataCatalog(), null, table)) {
            while (rs.next()) {
                final String name = rs.getString("FK_NAME");
                if ((column == null || column.equals(rs.getString("FKCOLUMN_NAME"))) && !names.contains(name)) names.add(name);
            }
        }
        return names;
    }

    private final DatabaseInserterInterface inserterInterface = new DatabaseInserterInterface() {
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

        /** O MySQL não tem rowid: as linhas apagam-se pela chave primária ({@link #deleteRows}). */
        @Override
        public boolean removeData(String Table, ArrayList<String> rowid) {
            return deleteRows(Table, keyColumns(List.of()), List.of());
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

    /** Sem rowid, as edições só funcionam em tabelas com chave primária. */
    private final DatabaseUpdaterInterface updaterInterface = new DatabaseUpdaterInterface() {
        @Override
        public boolean updateData(String Table, final String column, final Object value, final String index, String PrimeKey, final String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(nullToEmpty(tmp)));
        }

        @Override
        public boolean updateData(String Table, HashMap<String, String> data, long index) {
            MsgException = "MySQL has no row id: rows are updated by their primary key.";
            return false;
        }

        @Override
        public boolean updateData(String Table, String column, String value, long index) {
            MsgException = "MySQL has no row id: rows are updated by their primary key.";
            return false;
        }

        @Override
        public boolean updateData(String tableName, String colName, String newValue, long index, String s, String tmp) {
            return false;
        }

        @Override
        public boolean updateData(String Table, String column, Object value, long index, String PrimeKey, String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(nullToEmpty(tmp)));
        }

        @Override
        public boolean updateData(String Table, String column, Object value, String[] index, String PrimeKey, String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(nullToEmpty(tmp)));
        }

        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, String PrimeKey, String tmp) {
            return updateCell(Table, column, value, keyColumns(single(PrimeKey)), List.of(nullToEmpty(tmp)));
        }

        /** O que a grelha chama. */
        @Override
        public boolean updateData(String Table, String column, String value, String[] index, String type, ArrayList<String> PrimeKey, ArrayList<String> tmp) {
            return updateCell(Table, column, value, keyColumns(PrimeKey), tmp == null ? List.of() : tmp);
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
    public ArrayList<String> getTables() {

        ArrayList<String> TablesName = new ArrayList<>();
        try (ResultSet tabledMeta = connection.getMetaData().getTables(databaseName, databaseName, "%", new String[]{"TABLE"})) {
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
        try (ResultSet columns = connection.getMetaData().getColumns(databaseName, databaseName, Table, null)) {
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
    protected HashMap<String, ColumnMetadata.Foreign> getForeign(String Table) {
        // Por nome da restrição: coluna de cá e a chave estrangeira lida.
        final LinkedHashMap<String, List<Map.Entry<String, ColumnMetadata.Foreign>>> keys = new LinkedHashMap<>();
        try (ResultSet foreignKeys = connection.getMetaData().getImportedKeys(metadataCatalog(), null, Table)) {
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
     * Metadados das colunas, do {@code information_schema.COLUMNS}: o tipo exato
     * (TINYINT(1), INT UNSIGNED, DECIMAL(10,2), ENUM('a','b')), NULL, DEFAULT,
     * AUTO_INCREMENT, ON UPDATE e comentário.
     */
    @Override
    public ArrayList<ColumnMetadata> getColumnsMetadata(String Table) {
        final ArrayList<ColumnMetadata> ColumnsMetadata = new ArrayList<>();
        try {
            final ArrayList<String> PrimaryKeyList = PrimaryKeyList(Table);
            final HashMap<String, ColumnMetadata.Foreign> ForeignKeyList = getForeign(Table);
            final HashMap<String, Boolean> uniqueColumns = isUnique(Table);
            final List<String> foreignKeyIndexes = foreignKeyNames(Table, null);
            final ArrayList<IndexMetadata> indexes = readIndexes(Table);

            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT,
                           CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE, EXTRA, COLUMN_COMMENT
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?
                    ORDER BY ORDINAL_POSITION""")) {
                ps.setString(1, Table);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        final ColumnMetadata column = new ColumnMetadata();
                        column.Name = rs.getString("COLUMN_NAME");
                        applyType(column, rs.getString("DATA_TYPE"), rs.getString("COLUMN_TYPE"),
                                rs.getLong("CHARACTER_MAXIMUM_LENGTH"), rs.getInt("NUMERIC_PRECISION"), rs.getInt("NUMERIC_SCALE"));
                        column.NOT_NULL = "NO".equalsIgnoreCase(rs.getString("IS_NULLABLE"));

                        final String extra = nullToEmpty(rs.getString("EXTRA"));
                        column.defaultValue = defaultOf(rs.getString("COLUMN_DEFAULT"), extra, column);
                        if (extra.toLowerCase(Locale.ROOT).contains("auto_increment")) column.autoincrement = 1;
                        final int onUpdate = extra.toLowerCase(Locale.ROOT).indexOf("on update ");
                        if (onUpdate >= 0) column.extra = extra.substring(onUpdate).toUpperCase(Locale.ROOT);
                        column.comment = nullToEmpty(rs.getString("COLUMN_COMMENT"));

                        column.IsPrimaryKey = PrimaryKeyList != null && PrimaryKeyList.contains(column.Name);
                        if (ForeignKeyList != null && ForeignKeyList.containsKey(column.Name)) column.foreign = ForeignKeyList.get(column.Name);
                        column.isUnique = uniqueColumns != null && uniqueColumns.containsKey(column.Name);
                        for (final IndexMetadata index : indexes) {
                            // O índice que o MySQL cria para uma chave estrangeira não é da coluna.
                            if (!index.unique && !index.implicit && index.columns.size() == 1
                                    && index.columns.getFirst().equals(column.Name) && !foreignKeyIndexes.contains(index.Name)) {
                                column.index = index.Name;
                                break;
                            }
                        }
                        ColumnsMetadata.add(column);
                    }
                }
            }
            return ColumnsMetadata;
        } catch (SQLException e) {
            System.out.println("errorrrrr " + e.getMessage());
            MsgException = e.getMessage();
            return null;
        }
    }

    /** Tipo lido do information_schema, com tamanho, precisão ou lista de valores. */
    private static void applyType(final ColumnMetadata column, final String dataType, final String columnType,
                                  final long length, final int precision, final int scale) {
        final String data = dataType == null ? "" : dataType.toUpperCase(Locale.ROOT);
        final String full = columnType == null ? data : columnType.toUpperCase(Locale.ROOT);
        switch (data) {
            case "ENUM", "SET" -> {
                column.Type = data;
                column.items = enumValues(columnType);
            }
            case "CHAR", "VARCHAR", "BINARY", "VARBINARY" -> {
                column.Type = data;
                column.size = (int) Math.min(length, Integer.MAX_VALUE);
            }
            case "DECIMAL", "NUMERIC" -> {
                column.Type = full.contains("UNSIGNED") ? full : data;
                column.size = precision;
                column.decimalDigits = scale;
                column.integerDigits = precision - scale;
            }
            // O resto fica como o MySQL o escreve: TINYINT(1), INT UNSIGNED, DATETIME(3)...
            default -> column.Type = full;
        }
    }

    /** Valores de {@code enum('a','it''s')}: as plicas dobradas são uma plica. */
    private static ArrayList<String> enumValues(final String columnType) {
        final ArrayList<String> values = new ArrayList<>();
        if (columnType == null) return values;
        final int open = columnType.indexOf('(');
        final int close = columnType.lastIndexOf(')');
        if (open < 0 || close <= open) return values;
        final String body = columnType.substring(open + 1, close);
        StringBuilder current = null;
        for (int i = 0; i < body.length(); i++) {
            final char c = body.charAt(i);
            if (current == null) {
                if (c == '\'') current = new StringBuilder();
                continue;
            }
            if (c == '\'') {
                if (i + 1 < body.length() && body.charAt(i + 1) == '\'') {
                    current.append('\'');
                    i++;
                } else {
                    values.add(current.toString());
                    current = null;
                }
            } else if (c == '\\' && i + 1 < body.length()) {
                current.append(body.charAt(++i));
            } else {
                current.append(c);
            }
        }
        return values;
    }

    /**
     * DEFAULT pronto a voltar a ser usado num CHANGE COLUMN. O MySQL devolve os textos sem
     * plicas ({@code abc}) e as expressões marcadas com DEFAULT_GENERATED no EXTRA.
     */
    private String defaultOf(final String value, final String extra, final ColumnMetadata column) {
        if (value == null) return "";
        if (value.startsWith("'")) return value; // o MariaDB já as põe
        if (extra.toUpperCase(Locale.ROOT).contains("DEFAULT_GENERATED")) {
            final String upper = value.toUpperCase(Locale.ROOT);
            if (upper.startsWith("CURRENT_TIMESTAMP") || upper.startsWith("NOW(") || value.startsWith("(")) return value;
            return "(" + value + ")";
        }
        if (value.matches("(?i)[bx]'[0-9a-f]*'")) return value;
        final String base = column.Type.toUpperCase(Locale.ROOT);
        final boolean numeric = base.contains("INT") || base.startsWith("DECIMAL") || base.startsWith("NUMERIC")
                || base.startsWith("FLOAT") || base.startsWith("DOUBLE") || base.startsWith("BIT") || base.startsWith("YEAR");
        return numeric && QueryBuilder.isNumber(value) ? value : builder().literal(value);
    }

    @Override
    public long totalPages(String table) {
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
    public long totalPages(String table, ArrayList<String> columns, String condition) {
        return totalPages(table, columns.getFirst(), condition);
    }

    @Override
    public long totalPages(String table, String column, String condition) {
        final QueryBuilder q = builder();
        final String sql = "SELECT COUNT(" + q.name(column) + ") FROM " + q.quote(table) + " " + (condition == null ? "" : condition);
        System.out.println(sql);
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

    /** O MySQL não sabe mudar o nome de uma base de dados. */
    @Override
    public void renameDatabase(String name) {
        MsgException = "MySQL cannot rename a database.";
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
        try (ResultSet primaryKeys = connection.getMetaData().getPrimaryKeys(metadataCatalog(), null, Table)) {
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
            super.username = userName;
            statement.execute("CREATE DATABASE " + quote(name));
            FormatDBCreation(modes);
            connection = DriverManager.getConnection(completeURL + "/" + name, userName, password);
            statement = connection.createStatement();
            Url = completeURL + "/" + name;
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
    public boolean connect(String url, final String name, String userName, String password, boolean ssl) {
        final String completeURL = "jdbc:mysql://" + url + name + "?useSSL=" + ssl + "&requireSSL=" + ssl;
        System.out.println(completeURL);
        try {
            connection = DriverManager.getConnection(completeURL, userName, password);
            statement = connection.createStatement();
            super.databaseName = name;
            super.username = userName;
            Url = completeURL;
            //  FormatDBCreation(formatData);
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    @Override
    public String getUrl() {
        return Url == null ? "" : Url;
    }

    @Override
    public ArrayList<FunctionController.Function> getFunctions() {

        final ArrayList<FunctionController.Function> functions = new ArrayList<>();

        String query = """
                SELECT ROUTINE_NAME, ROUTINE_DEFINITION, DTD_IDENTIFIER, DEFINER, IS_DETERMINISTIC, SQL_DATA_ACCESS
                FROM information_schema.ROUTINES
                WHERE ROUTINE_TYPE = 'FUNCTION'
                AND ROUTINE_SCHEMA NOT IN ('information_schema', 'performance_schema', 'mysql', 'sys')
                AND ROUTINE_SCHEMA = DATABASE();""";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String functionName = rs.getString("ROUTINE_NAME");
                String code = rs.getString("ROUTINE_DEFINITION");
                String user = rs.getString("DEFINER");
                String return_type = rs.getString("DTD_IDENTIFIER");
                String determinate = rs.getString("IS_DETERMINISTIC");
                determinate = "YES".equals(determinate) ? "DETERMINISTIC" : "NOT DETERMINISTIC";
                String access = rs.getString("SQL_DATA_ACCESS");
                functions.add(new FunctionController.Function(functionName, "RETURNS " + return_type + "\n" + determinate + "\n" + access + "\n" + code, user));
            }

        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
        return functions;
    }

    /** O SELECT não pedia o ROUTINE_DEFINITION que depois era lido, e a lista falhava sempre. */
    @Override
    public ArrayList<ProcedureController.Procedure> getProcedure() {

        final ArrayList<ProcedureController.Procedure> procedures = new ArrayList<>();

        String query = """
                SELECT ROUTINE_SCHEMA, ROUTINE_NAME, ROUTINE_DEFINITION
                FROM information_schema.ROUTINES
                WHERE ROUTINE_TYPE = 'PROCEDURE'
                AND ROUTINE_SCHEMA = DATABASE();""";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                String functionName = rs.getString("ROUTINE_NAME");
                String code =  rs.getString("ROUTINE_DEFINITION");
                procedures.add(new ProcedureController.Procedure(functionName, code));
            }

        } catch (SQLException e) {
            MsgException = e.getMessage();
            return null;
        }
        return procedures;
    }

    @Override
    public boolean createTable(String table, boolean temporary, boolean rowid) {
        final ColumnMetadata id = new ColumnMetadata();
        id.Name = "id";
        id.Type = "INT";
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

    /**
     * CREATE TABLE e os índices pedidos nas colunas. O DDL do MySQL não se desfaz com
     * ROLLBACK, por isso, se um índice falhar, a tabela acabada de criar é apagada para não
     * ficar meio feita.
     */
    @Override
    public boolean createTable(TableMetadata metadata, boolean temporary, boolean rowid) {
        boolean created = false;
        try {
            execute(builder().createTable(metadata.getName())
                    .temporary(temporary)
                    .columns(metadata.getColumnMetadata())
                    .check(metadata.getCheck())
                    .build());
            created = true;
            for (final ColumnMetadata column : metadata.getColumnMetadata()) {
                if (column.index != null && !column.index.isBlank()) {
                    execute(builder().createIndex(column.index, metadata.getName(), List.of(column.Name), column.indexType));
                }
            }
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            if (created) {
                try {
                    execute(builder().dropTable(metadata.getName()));
                } catch (SQLException ignored) {
                }
            }
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

    /** Vistas desta base de dados que usam a tabela. */
    @Override
    public ArrayList<ViewController.View> getViews(String table) throws SQLException {
        final ArrayList<ViewController.View> views = new ArrayList<>();
        for (final ViewController.View view : getViews()) {
            if (view.code.get().contains("`" + table + "`")) views.add(view);
        }
        return views;
    }

    @Override
    public ArrayList<ViewController.View> getViews() throws SQLException {
        final ArrayList<ViewController.View> views = new ArrayList<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT TABLE_NAME, VIEW_DEFINITION FROM information_schema.VIEWS WHERE TABLE_SCHEMA = DATABASE()")) {
            while (rs.next()) {
                views.add(new ViewController.View(rs.getString(1), null, nullToEmpty(rs.getString(2))));
            }
        }
        return views;
    }

    @Override
    public void createTrigger(String trigger, String code) {
        try {
            execute(builder().dropTrigger(trigger, null));
            execute(code);
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    @Override
    public void removeTrigger(String trigger) throws SQLException {
        execute("DROP TRIGGER " + quote(trigger));
    }

    @Override
    public void createEvent(String event, String code) {
        try {
            execute("DROP EVENT IF EXISTS " + quote(event));
            execute(code);
        } catch (SQLException e) {
            MsgException = e.getMessage();
        }
    }

    @Override
    public void removeEvent(String event) throws SQLException {
        execute("DROP EVENT " + quote(event));
    }

    /** Os três métodos de índice estavam vazios: criar um índice não fazia nada. */
    @Override
    public void createIndex(String table, ArrayList<String> columns, String indexName, String mode) throws SQLException {
        execute(builder().createIndex(indexName, table, columns, mode));
    }

    @Override
    public void createIndex(String table, String column, String indexName, String mode) throws SQLException {
        createIndex(table, new ArrayList<>(List.of(column)), indexName, mode);
    }

    /** No MySQL um índice pertence a uma tabela: o DROP INDEX exige indicá-la. */
    @Override
    public void removeIndex(String indexName) throws SQLException {
        final String table = tableOfIndex(indexName);
        if (table == null) throw new SQLException("Index " + indexName + " not found in this schema.");
        execute(builder().dropIndex(indexName, table));
    }

    /** O DROP INDEX do MySQL precisa da tabela: vai-se buscar pelo nome do índice. */
    @Override
    protected void replaceIndex(final String table, final ColumnMetadata before, final ColumnMetadata after) throws SQLException {
        if (before.index != null && !before.index.isBlank()) execute(builder().dropIndex(before.index, table));
        if (after.index != null && !after.index.isBlank()) {
            execute(builder().createIndex(after.index, table, List.of(after.Name), after.indexType));
        }
    }

    private String tableOfIndex(final String indexName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.statistics"
                        + " WHERE TABLE_SCHEMA = DATABASE() AND INDEX_NAME = ? LIMIT 1")) {
            stmt.setString(1, indexName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    @Override
    public ArrayList<CheckMetadata> getChecks(final String table) throws SQLException {
        ArrayList<CheckMetadata> checks = new ArrayList<>();
        // information_schema.check_constraints só existe a partir do MySQL 8.0.16.
        try (PreparedStatement stmt = connection.prepareStatement("""
                SELECT c.CONSTRAINT_NAME, c.CHECK_CLAUSE
                FROM information_schema.check_constraints c
                JOIN information_schema.table_constraints t
                  ON t.CONSTRAINT_NAME = c.CONSTRAINT_NAME
                 AND t.CONSTRAINT_SCHEMA = c.CONSTRAINT_SCHEMA
                WHERE c.CONSTRAINT_SCHEMA = DATABASE() AND t.TABLE_NAME = ?""")) {
            stmt.setString(1, table);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    checks.add(new CheckMetadata(rs.getString(1), table, rs.getString(2)));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not read check constraints: " + e.getMessage());
        }
        return checks;
    }

    @Override
    public String getTableCheck(String table) throws SQLException {
        final ArrayList<CheckMetadata> checks = getChecks(table);
        return checks.isEmpty() ? "" : checks.getFirst().expression;
    }

}
