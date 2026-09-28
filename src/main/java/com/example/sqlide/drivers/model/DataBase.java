package com.example.sqlide.drivers.model;

import com.example.sqlide.Function.FunctionController;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.Metadata.CheckMetadata;
import com.example.sqlide.Metadata.IndexMetadata;
import com.example.sqlide.Metadata.RoutineMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.Procedure.ProcedureController;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.example.sqlide.drivers.model.Interfaces.DatabaseExecutorInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.*;
import com.example.sqlide.drivers.model.QueryBuilder.ColumnChange;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Base comum dos drivers.
 *
 * <p>O SQL deixou de ser montado aqui com concatenação: passa todo pelo
 * {@link QueryBuilder} do dialeto ({@link #builder()}). O que mudou em relação à versão
 * anterior, para reconheceres o que era teu:</p>
 * <ul>
 *   <li>Os <em>fetchers</em> liam as colunas do resultado para um {@code HashSet} — a ordem
 *       das colunas saía baralhada — e pelo nome, pelo que num JOIN com dois {@code id} um
 *       apagava o outro. Agora a leitura é pela posição e os nomes repetidos ganham o nome
 *       da tabela à frente.</li>
 *   <li>Os <em>fetchers</em> de consultas escritas pelo utilizador colavam
 *       {@code LIMIT ... OFFSET} depois do {@code ;} final, ou apagavam todos os {@code ;}
 *       (incluindo os que estavam dentro de texto).</li>
 *   <li>O MySQL e o Access não têm rowid: o {@code SELECT ROWID, *} partia a leitura de
 *       qualquer tabela sem chave primária.</li>
 *   <li>{@link #executeScript(String)} cortava o comando com a posição do {@code ;} na
 *       <em>linha</em> em vez de no comando, e nunca limpava o que já tinha executado.</li>
 *   <li>{@link #AlterTypeColumn} escrevia {@code ALTER COLUMN c SET tipo}, que não existe em
 *       motor nenhum; agora ele e o {@link #AlterDefaultValue} passam por
 *       {@link #alterColumn}.</li>
 *   <li>Novos, para quem está por cima: {@link #alterColumn} (mudar uma coluna a sério),
 *       {@link #deleteRows} (apagar pela chave completa) e {@link #totalPagesOfQuery}.</li>
 * </ul>
 */
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
    protected DatabaseInfo databaseInfo;

    protected String MsgException;

    public String GetException() {
        final String ret = MsgException;
        MsgException = "";
        return ret;
    }

    public DatabaseInfo getDatabaseInfo() {
        return databaseInfo;
    }

    private ConnectionPragmaInterface connectionPragmaInterface;
    private DebugPragmaInterface debugPragmaInterface;
    private IOPragma_Interface ioPragmaInterface;
    private MemoryPragmaInterface memoryPragmaInterface;
    private PermissionPragmaInterface permissionPragmaInterface;
    private PerformancePragmaInterface performancePragmaInterface;
    private SchemaPragmaInterface schemaPragmaInterface;

    /** Construtor de SQL para o dialeto desta ligação. */
    public QueryBuilder builder() {
        return QueryBuilder.of(SQLType);
    }

    /**
     * Há uma pseudo-coluna que identifica a linha? O SQLite tem ROWID e o PostgreSQL CTID;
     * o MySQL e o Access não têm nada equivalente.
     */
    public boolean hasRowId() {
        return idType != null && !idType.isBlank();
    }

    private final DatabaseFetcherInterface databaseFetcherInterface = new DatabaseFetcherInterface() {
        @Override
        public synchronized ArrayList<DataForDB> fetchData(final String Table, ArrayList<String> Columns, final long offset, final ArrayList<String> primeKey) {
            Columns = new ArrayList<>(Columns);
            final ArrayList<DataForDB> data = new ArrayList<>();
            final ArrayList<String> selected = new ArrayList<>();
            if ((primeKey == null || primeKey.isEmpty()) && hasRowId()) {
                // Sem chave primária a linha é identificada pelo rowid, que vem à frente.
                selected.add(getRowId());
                Columns.add(getRowId());
            } else if (primeKey != null) {
                for (final String key : primeKey) {
                    if (!Columns.contains(key)) Columns.add(key);
                }
            }
            selected.add("*");
            final String command = builder().select(selected).from(Table).limit(buffer).offset(offset).build();
            System.out.println("command " + command);
            try (Statement stmt = connection.createStatement()) {
                initializeTime();
                try (ResultSet rs = stmt.executeQuery(command)) {
                    endTime();
                    final Map<String, Integer> positions = columnPositions(rs.getMetaData());
                    while (rs.next()) {
                        final HashMap<String, String> row = new HashMap<>();
                        for (final String col : Columns) row.put(col, text(rs, positions, col));
                        data.add(new DataForDB(row));
                    }
                    putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                }
                return data;
            } catch (SQLException e) {
                System.err.println(e.getMessage());
                MsgException = e.getMessage();
                return null;
            }
        }

        /**
         * Consulta escrita na pesquisa avançada. {@code Columns} fica com as colunas do
         * resultado, pela ordem em que vieram.
         */
        @Override
        public synchronized ArrayList<DataForDB> fetchData(String command, ArrayList<String> Columns, final String primeKey) {
            final ArrayList<DataForDB> data = new ArrayList<>();
            System.out.println(command);
            try (Statement stmt = connection.createStatement()) {
                initializeTime();
                try (ResultSet rs = stmt.executeQuery(command)) {
                    endTime();
                    final ArrayList<String> labels = uniqueLabels(rs.getMetaData());
                    while (rs.next()) {
                        final HashMap<String, String> row = new HashMap<>();
                        for (int i = 0; i < labels.size(); i++) row.put(labels.get(i), text(rs.getObject(i + 1)));
                        data.add(new DataForDB(row));
                    }
                    Columns.clear();
                    Columns.addAll(labels);
                    putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                }
                return data;
            } catch (SQLException e) {
                MsgException = e.getMessage();
                return null;
            }
        }

        @Override
        public ArrayList<HashMap<String, String>> fetchDataMap(String Table, ArrayList<String> Columns, long offset, boolean primeKey) {
            return null;
        }

        @Override
        public synchronized ArrayList<HashMap<String, String>> fetchDataMap(final String Table, final ArrayList<String> Columns, final long limit, final long offset, final boolean PrimeKey) {
            final ArrayList<String> selected = new ArrayList<>();
            if (!PrimeKey && hasRowId()) {
                selected.add(getRowId());
                Columns.add(getRowId());
            }
            selected.add("*");
            final String command = builder().select(selected).from(Table).limit(limit).offset(offset).build();
            return readMaps(command, Columns);
        }

        @Override
        public ArrayList<HashMap<String, String>> fetchDataMap(String command, ArrayList<String> Columns, long limit, long offset) {
            return readMaps(builder().paginate(command, limit, offset), Columns);
        }

        @Override
        public synchronized ArrayList<HashMap<String, String>> fetchDataMap(final String Command, final long limit, final long offset) {
            return readMaps(builder().paginate(Command, limit, offset), null);
        }

        @Override
        public synchronized ArrayList<HashMap<String, String>> fetchRawDataMap(final String Command) {
            return readMaps(Command, null);
        }

        @Override
        public synchronized ArrayList<Double> fetchDataMap(final String Command) {
            final ArrayList<Double> data = new ArrayList<>();
            System.out.println("command " + Command);
            try (Statement stmt = connection.createStatement()) {
                initializeTime();
                try (ResultSet rs = stmt.executeQuery(Command)) {
                    endTime();
                    if (!rs.next()) return null;
                    do {
                        data.add(rs.getDouble(1));
                    } while (rs.next());
                    putMessage(new Logger(getUsername(), Command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                }
                return data;
            } catch (SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        @Override
        synchronized public ArrayList<ArrayList<String>> fetchDataBackup(final String Table, final ArrayList<String> Columns, long offset) {
            final ArrayList<ArrayList<String>> data = new ArrayList<>();
            final String command = builder().select("*").from(Table).limit(buffer).offset(offset).build();
            System.out.println("command " + command);
            try (Statement stmt = connection.createStatement()) {
                initializeTime();
                try (ResultSet rs = stmt.executeQuery(command)) {
                    endTime();
                    final Map<String, Integer> positions = columnPositions(rs.getMetaData());
                    while (rs.next()) {
                        final ArrayList<String> rowData = new ArrayList<>();
                        for (final String col : Columns) rowData.add(text(rs, positions, col));
                        data.add(rowData);
                    }
                    putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                }
                return data.isEmpty() ? null : data;
            } catch (SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        @Override
        synchronized public ArrayList<ArrayList<Object>> fetchDataBackupObject(final String Table, final ArrayList<String> Columns, long offset) {
            final String command = builder().select("*").from(Table).limit(buffer).offset(offset).build();
            try {
                final ArrayList<ArrayList<Object>> data = readObjects(command, Columns);
                return data.isEmpty() ? null : data;
            } catch (SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        @Override
        synchronized public ArrayList<ArrayList<Object>> fetchDataBackupObject(final String Command, final ArrayList<String> Columns, final long limit, long offset) {
            try {
                return readObjects(builder().paginate(Command, limit, offset), Columns);
            } catch (SQLException e) {
                MsgException = e.getMessage();
                return null;
            }
        }

        /** Linhas como mapas coluna → texto. Sem colunas pedidas, vêm todas as do resultado. */
        private ArrayList<HashMap<String, String>> readMaps(final String command, final ArrayList<String> Columns) {
            final ArrayList<HashMap<String, String>> data = new ArrayList<>();
            System.out.println("command " + command);
            try (Statement stmt = connection.createStatement()) {
                initializeTime();
                try (ResultSet rs = stmt.executeQuery(command)) {
                    endTime();
                    final ResultSetMetaData meta = rs.getMetaData();
                    final Map<String, Integer> positions = columnPositions(meta);
                    final List<String> wanted = Columns == null ? uniqueLabels(meta) : Columns;
                    while (rs.next()) {
                        final HashMap<String, String> row = new HashMap<>();
                        if (Columns == null) {
                            for (int i = 0; i < wanted.size(); i++) row.put(wanted.get(i), text(rs.getObject(i + 1)));
                        } else {
                            for (final String col : wanted) row.put(col, text(rs, positions, col));
                        }
                        data.add(row);
                    }
                    putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                }
                return data.isEmpty() ? null : data;
            } catch (SQLException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }

        private ArrayList<ArrayList<Object>> readObjects(final String command, final ArrayList<String> Columns) throws SQLException {
            final ArrayList<ArrayList<Object>> data = new ArrayList<>();
            System.out.println("command " + command);
            try (Statement stmt = connection.createStatement()) {
                initializeTime();
                try (ResultSet rs = stmt.executeQuery(command)) {
                    endTime();
                    final Map<String, Integer> positions = columnPositions(rs.getMetaData());
                    while (rs.next()) {
                        final ArrayList<Object> rowData = new ArrayList<>();
                        for (final String col : Columns) {
                            final Integer position = positions.get(col);
                            rowData.add(position == null ? null : rs.getObject(position));
                        }
                        data.add(rowData);
                    }
                    putMessage(new Logger(getUsername(), command, rs.getWarnings() != null ? rs.getWarnings().getMessage() : "", computeTime()));
                }
            }
            return data;
        }
    };

    private DatabaseUpdaterInterface databaseUpdaterInterface = null;
    private DatabaseInserterInterface databaseInserterInterface = null;

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

    /**
     * Escrita em bloco para as operações do Data Science (imputação, remoção de outliers).
     * A citação de identificadores segue o dialeto: MySQL usa crase, os restantes aspas.
     */
    private final DatabaseExecutorInterface databaseExecutorInterface = new DatabaseExecutorInterface() {

        @Override
        public synchronized int executeUpdate(final String command) throws SQLException {
            System.out.println("command " + command);
            initializeTime();
            try (final Statement stmt = connection.createStatement()) {
                final int affected = stmt.executeUpdate(command);
                endTime();
                putMessage(new Logger(getUsername(), command,
                        stmt.getWarnings() != null ? stmt.getWarnings().getMessage() : "", computeTime()));
                return affected;
            }
        }

        @Override
        public String quoteIdentifier(final String identifier) {
            return builder().quote(identifier);
        }
    };

    public DatabaseExecutorInterface Executor() {
        return databaseExecutorInterface;
    }

    public PermissionPragmaInterface Permission() { return permissionPragmaInterface; }

    public DatabaseUpdaterInterface Updater() {
        return databaseUpdaterInterface;
    }

    public PerformancePragmaInterface Performance() { return performancePragmaInterface; }

    public SchemaPragmaInterface Schema() { return schemaPragmaInterface; }

    public MemoryPragmaInterface Memory() { return memoryPragmaInterface; }

    public DebugPragmaInterface Debug() { return debugPragmaInterface; }

    public IOPragma_Interface IO() {
        return ioPragmaInterface;
    }

    public ConnectionPragmaInterface Connection() { return connectionPragmaInterface; }

    protected void Connection(final ConnectionPragmaInterface connectionPragmaInterface) { this.connectionPragmaInterface = connectionPragmaInterface; }

    protected void Debug(final DebugPragmaInterface debugPragmaInterface) { this.debugPragmaInterface = debugPragmaInterface; }

    protected void IO(final IOPragma_Interface ioPragmaInterface) { this.ioPragmaInterface = ioPragmaInterface; }

    protected void Memory(final MemoryPragmaInterface memoryPragmaInterface) { this.memoryPragmaInterface = memoryPragmaInterface; }

    protected void Performance(final PerformancePragmaInterface performancePragmaInterface) { this.performancePragmaInterface = performancePragmaInterface; }

    protected void Permission(final PermissionPragmaInterface permissionPragmaInterface) { this.permissionPragmaInterface = permissionPragmaInterface; }

    protected void Schema(final SchemaPragmaInterface schemaPragmaInterface) { this.schemaPragmaInterface = schemaPragmaInterface; }

    protected void Updater(final DatabaseUpdaterInterface databaseUpdaterInterface) {
        this.databaseUpdaterInterface = databaseUpdaterInterface;
    }

    public DatabaseInserterInterface Inserter() {
        return databaseInserterInterface;
    }

    protected void Inserter(final DatabaseInserterInterface databaseInserterInterface) {
        this.databaseInserterInterface = databaseInserterInterface;
    }

    public String getRowId() {
        return idType;
    }

    public SQLTypes getSQLType() {
        return SQLType;
    }

    public String getCharset() throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA encoding;")) {
            return rs.next() ? rs.getString(1) : "";
        }
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

    /** Duração do último comando, como hora do dia (é o que o registo de comandos mostra). */
    protected LocalTime computeTime() {
        if (init == null || end == null) return LocalTime.MIDNIGHT;
        final Duration elapsed = Duration.between(init, end);
        return LocalTime.MIDNIGHT.plus(elapsed.isNegative() ? Duration.ZERO : elapsed);
    }

    /**
     * Nome do índice criado à mão (CREATE INDEX) só sobre esta coluna, ou null.
     *
     * <p>Devolvia o primeiro índice que tivesse a coluna, incluindo o da chave primária e os
     * compostos — e apagar a coluna tentava depois apagar esse índice, o que o motor recusa.</p>
     */
    protected String indexName(final String table, final String column) throws SQLException {
        for (IndexMetadata index : readIndexes(table)) {
            if (!index.unique && !index.implicit && index.columns.size() == 1 && index.columns.getFirst().equalsIgnoreCase(column)) {
                return index.Name;
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
        connection.setAutoCommit(primaryState);
    }

    public void setMessager(final BlockingQueue<Logger> sender) {
        this.sender = sender;
    }

    public abstract boolean connect(String DBName, Map<String, String> formatData);

    public abstract boolean connect(String DBName);

    public void executeLittleScript(BufferedReader reader) throws IOException, SQLException {
        final StringBuilder script = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            script.append(line).append('\n');
        }
        for (final String command : splitStatements(script.toString())) {
            execute(command);
        }
    }

    public abstract HashMap<String, String> getTriggers();

    public abstract HashMap<String, String> getEvents();

    /**
     * Funções, agregados, procedimentos e vistas disponíveis no esquema.
     *
     * <p>Inclui tanto o que vem com o motor como o que foi criado por SQL do utilizador.
     * É lido uma vez, quando a base de dados abre, e guardado nos metadados.</p>
     *
     * <p>Não é abstrato de propósito: um motor que ainda não saiba responder devolve uma
     * lista vazia e o catálogo mostra-se vazio, em vez de partir a compilação dos drivers.</p>
     */
    public ArrayList<RoutineMetadata> getRoutines() {
        return new ArrayList<>();
    }

    /** Catálogo a passar ao DatabaseMetaData. O MySQL tem de dizer a base de dados, senão procura em todas. */
    protected String metadataCatalog() {
        return null;
    }

    /** Esquema a passar ao DatabaseMetaData. */
    protected String metadataSchema() {
        return null;
    }

    /**
     * Índices existentes numa tabela.
     *
     * <p>Sai do {@link DatabaseMetaData}, que os três motores preenchem — assim não é
     * preciso uma query diferente por dialeto só para listar.</p>
     */
    public ArrayList<IndexMetadata> getIndexes(final String table) throws SQLException {
        return readIndexes(table);
    }

    /** Índices da tabela; os que suportam a chave primária vêm marcados como implícitos. */
    protected ArrayList<IndexMetadata> readIndexes(final String table) throws SQLException {
        final LinkedHashMap<String, IndexMetadata> indexes = new LinkedHashMap<>();
        final String primaryKey = primaryKeyName(table);

        try (ResultSet rs = connection.getMetaData().getIndexInfo(metadataCatalog(), metadataSchema(), table, false, false)) {
            while (rs.next()) {
                final String name = rs.getString("INDEX_NAME");
                if (name == null) continue; // linhas de estatística, sem índice associado

                final boolean unique = !rs.getBoolean("NON_UNIQUE");
                final IndexMetadata index = indexes.computeIfAbsent(name, key -> {
                    IndexMetadata created = new IndexMetadata(key, table, unique);
                    // Os índices que suportam a chave primária são criados pelo motor e
                    // não devem ser apagados a partir daqui.
                    created.implicit = key.toLowerCase(Locale.ROOT).startsWith("sqlite_autoindex")
                            || key.equalsIgnoreCase("PRIMARY")
                            || key.equalsIgnoreCase(primaryKey);
                    return created;
                });

                final String column = rs.getString("COLUMN_NAME");
                if (column != null && !index.columns.contains(column)) index.columns.add(column);
            }
        }

        return new ArrayList<>(indexes.values());
    }

    /**
     * Restrições CHECK de uma tabela.
     *
     * <p>Sem implementação por omissão: cada motor guarda isto num sítio diferente, e o
     * SQLite nem sequer tem catálogo para elas.</p>
     */
    public ArrayList<CheckMetadata> getChecks(final String table) throws SQLException {
        return new ArrayList<>();
    }

    /**
     * Acrescenta uma restrição CHECK a uma tabela já criada.
     *
     * <p>A forma padrão serve o MySQL e o PostgreSQL; o SQLite tem de reconstruir a tabela
     * e trata disso na sua própria implementação.</p>
     */
    public void addCheck(final String table, final String name, final String expression) throws SQLException {
        for (final String sql : builder().alterTable(table).addCheck(name, expression).build()) execute(sql);
    }

    public void dropCheck(final String table, final String name) throws SQLException {
        for (final String sql : builder().alterTable(table).dropCheck(name).build()) execute(sql);
    }

    /** Citação de identificadores conforme o dialeto, para nomes com espaços ou reservados. */
    protected String quote(final String identifier) {
        return builder().quote(identifier);
    }

    /**
     * Vistas do esquema, lidas do catálogo padrão do SQL.
     *
     * <p>Serve de base comum aos motores: os três principais expõem
     * {@code information_schema.views}, e o SQLite trata disto por si.</p>
     */
    protected ArrayList<RoutineMetadata> readViewsFromInformationSchema(final String schemaColumn,
                                                                       final String schemaValue) {
        ArrayList<RoutineMetadata> views = new ArrayList<>();
        final String sql = "SELECT table_name FROM information_schema.views WHERE " + schemaColumn + " = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, schemaValue);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    views.add(new RoutineMetadata(rs.getString(1), RoutineMetadata.Kind.VIEW,
                            "Views", "", "", "View defined in this schema.", false));
                }
            }
        } catch (SQLException e) {
            System.err.println("Could not list views: " + e.getMessage());
        }
        return views;
    }

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

    public abstract boolean deleteColumn(ArrayList<ColumnMetadata> columns, String columnName, String table);

    /**
     * Leva a coluna do estado {@code before} (o que está na base de dados) ao estado
     * {@code after} (o que o formulário de edição pediu).
     *
     * <p>Era isto que faltava: o {@code alterColumnMetadata} da grelha só imprimia
     * "Simulating: ..." na consola e dava sucesso. Aqui o {@link QueryBuilder} decide os
     * comandos para o dialeto e tudo corre numa transação (no PostgreSQL o DDL é
     * transacional; no MySQL cada ALTER confirma-se sozinho, é uma limitação do motor).
     * O SQLite reconstrói a tabela e tem a sua própria versão deste método.</p>
     */
    public boolean alterColumn(final String table, final ColumnMetadata before, final ColumnMetadata after) {
        try {
            final EnumSet<ColumnChange> changes = ColumnChange.between(before, after);
            if (changes.isEmpty()) return true;

            prepareColumnType(after);
            final List<String> statements = builder().alterColumn(table, before, after, readConstraints(table, before));
            runInTransaction(() -> {
                for (final String sql : statements) execute(sql);
                if (changes.contains(ColumnChange.INDEX)) replaceIndex(table, before, after);
            });
            return true;
        } catch (SQLException | RuntimeException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /**
     * Antes de uma coluna usar um tipo, o tipo tem de existir. Só o PostgreSQL precisa disto
     * (os ENUM são tipos com nome, criados à parte).
     */
    protected void prepareColumnType(final ColumnMetadata column) throws SQLException {
    }

    /** Troca o índice da coluna: apaga o antigo (se havia) e cria o novo (se foi pedido). */
    protected void replaceIndex(final String table, final ColumnMetadata before, final ColumnMetadata after) throws SQLException {
        if (before.index != null && !before.index.isBlank()) execute(builder().dropIndex(before.index, table));
        if (after.index != null && !after.index.isBlank()) {
            execute(builder().createIndex(after.index, table, List.of(after.Name), after.indexType));
        }
    }

    /** Nomes das restrições da coluna, lidos do catálogo — o QueryBuilder precisa deles para as apagar. */
    protected QueryBuilder.Constraints readConstraints(final String table, final ColumnMetadata column) throws SQLException {
        final DatabaseMetaData meta = connection.getMetaData();

        String primaryKey = null;
        final TreeMap<Short, String> keyColumns = new TreeMap<>();
        try (ResultSet rs = meta.getPrimaryKeys(metadataCatalog(), metadataSchema(), table)) {
            while (rs.next()) {
                primaryKey = rs.getString("PK_NAME");
                keyColumns.put(rs.getShort("KEY_SEQ"), rs.getString("COLUMN_NAME"));
            }
        }

        String foreignKey = null;
        try (ResultSet rs = meta.getImportedKeys(metadataCatalog(), metadataSchema(), table)) {
            while (rs.next()) {
                if (column.Name.equals(rs.getString("FKCOLUMN_NAME"))) foreignKey = rs.getString("FK_NAME");
            }
        }

        String unique = null;
        for (IndexMetadata index : readIndexes(table)) {
            if (index.unique && !index.implicit && index.columns.size() == 1 && index.columns.getFirst().equals(column.Name)) {
                unique = index.Name;
            }
        }

        String check = null;
        final String conventional = QueryBuilder.constraintName("chk", table, column.Name);
        for (CheckMetadata existing : getChecks(table)) {
            if (conventional.equalsIgnoreCase(existing.Name)) check = existing.Name;
        }

        return new QueryBuilder.Constraints(primaryKey, new ArrayList<>(keyColumns.values()), unique, foreignKey, check);
    }

    /** Nome da chave primária no catálogo (PRIMARY no MySQL, tabela_pkey no PostgreSQL). */
    protected String primaryKeyName(final String table) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getPrimaryKeys(metadataCatalog(), metadataSchema(), table)) {
            return rs.next() ? rs.getString("PK_NAME") : null;
        }
    }

    /** Metadados atuais de uma coluna, ou null se já não existir. */
    protected ColumnMetadata findColumn(final String table, final String column) {
        final ArrayList<ColumnMetadata> columns = getColumnsMetadata(table);
        if (columns == null) return null;
        return columns.stream().filter(c -> c.Name.equalsIgnoreCase(column)).findFirst().orElse(null);
    }

    /**
     * Apaga as linhas identificadas pela chave: as colunas da chave primária (todas, se for
     * composta) ou o rowid quando a tabela não tem chave.
     *
     * <p>O {@code removeData} antigo recebia só uma lista de valores e comparava-os sempre
     * com o ROWID — numa tabela com chave primária TEXT, ou INT que não fosse a INTEGER
     * PRIMARY KEY do SQLite, apagava as linhas erradas. Com chave composta só olhava para a
     * primeira coluna e podia apagar várias linhas por cada uma selecionada.</p>
     *
     * <p>Se a chave não apanhar exatamente uma linha por cada valor, nada é apagado.</p>
     */
    public boolean deleteRows(final String table, final List<String> keyColumns, final List<List<String>> keyValues) {
        if (keyColumns == null || keyColumns.isEmpty()) {
            MsgException = "Table " + table + " has no primary key, so its rows cannot be told apart. Add a primary key to delete rows here.";
            return false;
        }
        if (keyValues == null || keyValues.isEmpty()) return true;
        try {
            runInTransaction(() -> {
                int deleted = 0;
                // Aos bocados: o SQLite tem um limite de parâmetros por comando.
                for (int from = 0; from < keyValues.size(); from += 200) {
                    final List<List<String>> chunk = keyValues.subList(from, Math.min(keyValues.size(), from + 200));
                    final String sql = builder().delete(table).whereKeys(keyColumns, chunk.size()).build();
                    System.out.println(sql);
                    initializeTime();
                    try (PreparedStatement ps = connection.prepareStatement(sql)) {
                        int parameter = 1;
                        for (final List<String> row : chunk) {
                            for (final String value : row) setParameter(ps, parameter++, value);
                        }
                        deleted += ps.executeUpdate();
                        endTime();
                        putMessage(new Logger(getUsername(), sql, ps.getWarnings() != null ? ps.getWarnings().getMessage() : "", computeTime()));
                    }
                }
                if (deleted != keyValues.size()) {
                    throw new SQLException("Expected to delete " + keyValues.size() + " row(s) but the key matched "
                            + deleted + "; nothing was deleted.");
                }
            });
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /**
     * Grava o valor de uma célula. A linha é identificada pelas colunas da chave primária
     * (todas, se for composta) ou, sem chave, pelo rowid.
     *
     * <p>Os {@code updateData} de cada driver faziam isto cada um à sua maneira: o do
     * PostgreSQL que a grelha chama devolvia simplesmente false, o do MySQL usava um ROWID
     * que o MySQL não tem, e nenhum dizia nada quando a chave não apanhava linha nenhuma — a
     * célula mostrava o valor novo sem ele ter sido gravado.</p>
     */
    protected boolean updateCell(final String table, final String column, final Object value,
                                 final List<String> keyColumns, final List<?> keyValues) {
        if (keyColumns == null || keyColumns.isEmpty()) {
            MsgException = "Table " + table + " has no primary key, so the row cannot be found to be updated. "
                    + "Add a primary key to edit it here.";
            return false;
        }
        final String sql = builder().update(table).set(column).where(keyColumns).build();
        System.out.println(sql);
        initializeTime();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setParameter(ps, 1, value);
            int parameter = 2;
            for (final Object key : keyValues) setParameter(ps, parameter++, key);
            final int affected = ps.executeUpdate();
            endTime();
            putMessage(new Logger(getUsername(), sql, ps.getWarnings() != null ? ps.getWarnings().getMessage() : "", computeTime()));
            if (affected == 0) {
                MsgException = "No row matched the key, so nothing was saved. The row may have been changed or deleted: reload the table.";
                return false;
            }
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /** Colunas que identificam a linha: a chave primária, ou o rowid quando não há chave. */
    protected List<String> keyColumns(final List<String> primaryKey) {
        if (primaryKey != null && !primaryKey.isEmpty()) return primaryKey;
        return hasRowId() ? List.of(getRowId()) : List.of();
    }

    /** Parâmetro de um PreparedStatement. O PostgreSQL substitui isto para deixar o servidor inferir o tipo. */
    protected void setParameter(final PreparedStatement statement, final int index, final Object value) throws SQLException {
        if (value == null) statement.setNull(index, Types.NULL);
        else statement.setObject(index, value);
    }

    @FunctionalInterface
    protected interface SqlWork {
        void run() throws SQLException;
    }

    /**
     * Corre o trabalho numa transação: se a ligação estava em auto-commit, abre e confirma
     * uma; se o utilizador já tinha uma aberta (modo manual), usa um savepoint para desfazer
     * só este trabalho em caso de erro, sem lhe estragar o resto.
     */
    protected void runInTransaction(final SqlWork work) throws SQLException {
        final boolean autoCommit = connection.getAutoCommit();
        Savepoint savepoint = null;
        if (autoCommit) connection.setAutoCommit(false);
        else savepoint = connection.setSavepoint();
        try {
            work.run();
            if (autoCommit) connection.commit();
        } catch (SQLException | RuntimeException e) {
            try {
                if (autoCommit) connection.rollback();
                else connection.rollback(savepoint);
            } catch (SQLException rollback) {
                e.addSuppressed(rollback);
            }
            throw e;
        } finally {
            if (autoCommit) connection.setAutoCommit(true);
        }
    }

    /** Executa um comando e deixa-o no registo de comandos. */
    protected void execute(final String sql) throws SQLException {
        System.out.println(sql);
        initializeTime();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
            endTime();
            putMessage(new Logger(getUsername(), sql, stmt.getWarnings() != null ? stmt.getWarnings().getMessage() : "", computeTime()));
        }
    }

    /**
     * Tabela nova para a importação. Cada definição é "nome TIPO" (o nome pode ter espaços).
     *
     * <p>O {@code Inserter().createTable(...)} estava vazio nos quatro drivers: a importação
     * dizia que tinha criado a tabela e os INSERT a seguir falhavam todos.</p>
     */
    protected void createTableFromDefinitions(final String tableName, final List<String> columnDefinitions,
                                              final List<String> primaryKeyColumns) throws SQLException {
        final QueryBuilder.CreateTable create = builder().createTable(tableName).ifNotExists();
        for (final String definition : columnDefinitions) {
            final String trimmed = definition.trim();
            final int space = trimmed.lastIndexOf(' ');
            final ColumnMetadata column = new ColumnMetadata();
            column.Name = space > 0 ? trimmed.substring(0, space).trim() : trimmed;
            column.Type = space > 0 ? trimmed.substring(space + 1).trim() : "TEXT";
            column.IsPrimaryKey = primaryKeyColumns != null && primaryKeyColumns.contains(column.Name);
            create.column(column);
        }
        execute(create.build());
    }

    /** Número de páginas de uma consulta escrita pelo utilizador (-1 se o motor a recusar). */
    public long totalPagesOfQuery(final String query) {
        final String sql = builder().countQuery(query);
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? pages(rs.getLong(1)) : 0;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return -1;
        }
    }

    /** Páginas necessárias para um número de linhas, com {@link #buffer} linhas por página. */
    protected long pages(final long rows) {
        return (long) Math.ceil((double) rows / buffer);
    }

 //   public abstract void insertData(String tableName, Map<String, Object> data) throws SQLException;

    // Getters e Setters
    public String getDatabaseName() {
        return databaseName;
    }

    protected String fetchDatabaseName() throws SQLException {
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT DATABASE();")) {
            return rs.next() ? rs.getString(1) : null;
        }
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

    public abstract boolean connect(String url, String name, String userName, String password, boolean ssl);

    public abstract String getUrl();


    public boolean createView(final ViewController.View view) throws SQLException {
        try {
            execute(builder().createView(view.Name.get(), view.code.get()));
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    public boolean dropView(final String view) {
        try {
            execute(builder().dropView(view));
            return true;
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
    }

    /** Muda o DEFAULT de uma coluna. Um valor vazio tira-o. */
    public boolean AlterDefaultValue(final String table, final String column, final String value) {
        final ColumnMetadata before = findColumn(table, column);
        if (before == null) {
            MsgException = "Column " + column + " not found in " + table + ".";
            return false;
        }
        final ColumnMetadata after = before.copy();
        after.defaultValue = value;
        return alterColumn(table, before, after);
    }

    /** Muda o tipo de uma coluna; {@code type} é o tipo completo, como "VARCHAR(20)". */
    public boolean AlterTypeColumn(final String table, final String column, final String type) {
        final ColumnMetadata before = findColumn(table, column);
        if (before == null) {
            MsgException = "Column " + column + " not found in " + table + ".";
            return false;
        }
        final ColumnMetadata after = before.copy();
        after.Type = type;
        after.size = 0;
        after.integerDigits = 0;
        after.decimalDigits = 0;
        return alterColumn(table, before, after);
    }

    public boolean createFunction(final FunctionController.Function function) {
        try (Statement smtd = connection.createStatement()) {
            smtd.execute("CREATE FUNCTION " + function.Name.get() + "()\n" + function.code.get());
        } catch (SQLException e) {
            e.printStackTrace();
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    public boolean dropFunction(final String function) {
        try {
            execute("DROP FUNCTION " + quote(function));
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    public boolean dropProcedure(final String function) {
        try {
            execute("DROP PROCEDURE " + quote(function));
        } catch (SQLException e) {
            MsgException = e.getMessage();
            return false;
        }
        return true;
    }

    public abstract ArrayList<FunctionController.Function> getFunctions();

    public abstract ArrayList<ProcedureController.Procedure> getProcedure();

    public abstract boolean createTable(String table, boolean temporary, boolean rowid);

    public abstract boolean createTable(String table, boolean temporary, boolean rowid, ArrayList<ColumnMetadata> columnMetadata);

    public abstract boolean createTable(TableMetadata metadata, boolean temporary, boolean rowid);

    public abstract void changeCommitMode(final boolean mode) throws SQLException;

    /** Corre um ficheiro de SQL comando a comando. */
    public void executeScript(final String path) throws IOException, SQLException {
        if (path == null || path.isEmpty()) {
            return;
        }
        final String script = Files.readString(Path.of(path), StandardCharsets.UTF_8);
        for (final String command : splitStatements(script)) {
            execute(command);
        }
    }

    public void openScript(final String path) throws FileNotFoundException {
        cursorScript = new BufferedReader(new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
        saveNext = "";
        executorLineNum = 0;
    }

    /**
     * Executa o próximo comando do script aberto e devolve o número da linha onde ele acaba,
     * ou -1 no fim do ficheiro.
     */
    public long executeNextCommand() throws IOException, SQLException {
        String line;
        while ((line = cursorScript.readLine()) != null) {
            ++executorLineNum;
            saveNext += line + "\n";
            final int end = statementEnd(saveNext);
            if (end >= 0) {
                final String command = saveNext.substring(0, end).strip();
                saveNext = saveNext.substring(end + 1);
                if (!command.isEmpty()) {
                    execute(command);
                    return executorLineNum;
                }
            }
        }
        // O último comando pode não ter ";" no fim.
        final String rest = saveNext.strip();
        saveNext = "";
        if (!rest.isEmpty() && !isOnlyComments(rest)) {
            execute(rest);
            return executorLineNum;
        }
        return -1;
    }

    /** Divide um script em comandos pelos ";" que estão fora de texto, comentários e corpos de trigger. */
    public static List<String> splitStatements(final String script) {
        final List<String> commands = new ArrayList<>();
        String rest = script == null ? "" : script;
        int end;
        while ((end = statementEnd(rest)) >= 0) {
            final String command = rest.substring(0, end).strip();
            if (!command.isEmpty() && !isOnlyComments(command)) commands.add(command);
            rest = rest.substring(end + 1);
        }
        final String last = rest.strip();
        if (!last.isEmpty() && !isOnlyComments(last)) commands.add(last);
        return commands;
    }

    /** Objetos cujo corpo vai entre BEGIN e END, com ";" lá dentro. */
    private static final Set<String> COMPOUND_OBJECTS = Set.of("TRIGGER", "PROCEDURE", "FUNCTION", "EVENT");

    /** Blocos do MySQL que também acabam em END (END IF, END LOOP...) mas não abrem com BEGIN. */
    private static final Set<String> END_SUFFIXES = Set.of("IF", "LOOP", "WHILE", "REPEAT");

    /**
     * Posição do ";" que termina o primeiro comando do texto, ou -1 se ainda não terminou.
     *
     * <p>Ignora os ";" dentro de texto ('...'), de nomes ("...", `...`, [...]), de comentários
     * (-- e /* *&#47;), do texto $$...$$ do PostgreSQL e do BEGIN ... END de um CREATE TRIGGER
     * (ou PROCEDURE, FUNCTION, EVENT).</p>
     */
    static int statementEnd(final String text) {
        int depth = 0;
        boolean compound = false;
        final List<String> words = new ArrayList<>(); // primeiras palavras, para reconhecer o CREATE TRIGGER
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            final char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';

            if (c == '-' && next == '-') {
                final int newline = text.indexOf('\n', i);
                if (newline < 0) return -1;
                i = newline + 1;
                continue;
            }
            if (c == '/' && next == '*') {
                final int close = text.indexOf("*/", i + 2);
                if (close < 0) return -1;
                i = close + 2;
                continue;
            }
            if (c == '\'' || c == '"' || c == '`' || c == '[') {
                final char closing = c == '[' ? ']' : c;
                int j = i + 1;
                while (j < text.length()) {
                    if (text.charAt(j) == closing) {
                        // Aspas dobradas ('it''s') continuam o texto.
                        if (closing != ']' && j + 1 < text.length() && text.charAt(j + 1) == closing) {
                            j += 2;
                            continue;
                        }
                        break;
                    }
                    j++;
                }
                if (j >= text.length()) return -1;
                i = j + 1;
                continue;
            }
            if (c == '$') {
                final int tagEnd = text.indexOf('$', i + 1);
                if (tagEnd > i && text.substring(i + 1, tagEnd).matches("[A-Za-z0-9_]*")) {
                    final String tag = text.substring(i, tagEnd + 1);
                    final int close = text.indexOf(tag, tagEnd + 1);
                    if (close < 0) return -1;
                    i = close + tag.length();
                    continue;
                }
            }
            if (Character.isLetter(c) || c == '_') {
                int j = i;
                while (j < text.length() && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) j++;
                final String word = text.substring(i, j).toUpperCase(Locale.ROOT);
                if (words.size() < 8) {
                    words.add(word);
                    if (words.getFirst().equals("CREATE") && COMPOUND_OBJECTS.contains(word)) compound = true;
                }
                if (compound && (word.equals("BEGIN") || word.equals("CASE"))) depth++;
                if (compound && word.equals("END") && depth > 0) {
                    // END IF / END LOOP fecham blocos que não foram contados; END CASE fecha um CASE.
                    int k = j;
                    while (k < text.length() && Character.isWhitespace(text.charAt(k))) k++;
                    int l = k;
                    while (l < text.length() && Character.isLetter(text.charAt(l))) l++;
                    final String following = text.substring(k, l).toUpperCase(Locale.ROOT);
                    if (!END_SUFFIXES.contains(following)) depth--;
                    if (following.equals("CASE") || END_SUFFIXES.contains(following)) j = l;
                }
                i = j;
                continue;
            }
            if (c == ';' && depth == 0) return i;
            i++;
        }
        return -1;
    }

    private static boolean isOnlyComments(final String text) {
        final String withoutBlocks = text.replaceAll("(?s)/\\*.*?\\*/", "");
        for (final String line : withoutBlocks.split("\n")) {
            final String trimmed = line.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("--")) return false;
        }
        return true;
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
        execute(code);
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

    public abstract String getTableCheck(final String table) throws SQLException;

    // =====================================================================================
    // Leitura de resultados
    // =====================================================================================

    /** Posição de cada coluna do resultado pelo nome, sem distinguir maiúsculas; ganha a primeira. */
    private static Map<String, Integer> columnPositions(final ResultSetMetaData meta) throws SQLException {
        final Map<String, Integer> positions = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = meta.getColumnCount(); i >= 1; i--) positions.put(label(meta, i), i);
        return positions;
    }

    private static String label(final ResultSetMetaData meta, final int column) throws SQLException {
        final String label = meta.getColumnLabel(column);
        return label == null || label.isBlank() ? meta.getColumnName(column) : label;
    }

    /**
     * Nomes das colunas do resultado pela ordem, sem repetidos: num JOIN o segundo "id"
     * passa a "tabela.id" (ou "id (4)" se o motor não disser a tabela).
     */
    protected static ArrayList<String> uniqueLabels(final ResultSetMetaData meta) throws SQLException {
        final ArrayList<String> labels = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = label(meta, i);
            if (!seen.add(label.toLowerCase(Locale.ROOT))) {
                String table = "";
                try {
                    table = meta.getTableName(i);
                } catch (SQLException _) {
                }
                String candidate = table == null || table.isBlank() ? label + " (" + i + ")" : table + "." + label;
                if (!seen.add(candidate.toLowerCase(Locale.ROOT))) {
                    candidate = label + " (" + i + ")";
                    seen.add(candidate.toLowerCase(Locale.ROOT));
                }
                label = candidate;
            }
            labels.add(label);
        }
        return labels;
    }

    private static String text(final Object value) {
        return value == null ? "null" : value.toString();
    }

    private static String text(final ResultSet rs, final Map<String, Integer> positions, final String column) throws SQLException {
        final Integer position = positions.get(column);
        return position == null ? "null" : text(rs.getObject(position));
    }
}
