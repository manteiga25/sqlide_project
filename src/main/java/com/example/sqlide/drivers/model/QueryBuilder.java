package com.example.sqlide.drivers.model;

import com.example.sqlide.Metadata.ColumnMetadata;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Composição de SQL por dialeto — o sítio único onde se decide como se escreve cada peça
 * de um comando.
 *
 * <p><b>Porque existe.</b> Os drivers montavam cada comando com {@code StringBuilder} e
 * concatenação, e isso estava na origem de muitos dos erros:</p>
 * <ul>
 *   <li>os nomes nunca levavam aspas, por isso uma tabela {@code order} ou uma coluna
 *       {@code data de nascimento} partiam qualquer comando;</li>
 *   <li>os valores iam colados ao texto entre plicas: um apóstrofo ({@code O'Neill})
 *       estragava o INSERT e abria a porta a injeção de SQL;</li>
 *   <li>a vírgula a mais era cortada à mão com {@code delete(length - 2, length)} — e havia
 *       sítios com {@code -1} e {@code -3}, que comiam uma letra ou deixavam a vírgula;</li>
 *   <li>o mesmo comando estava copiado em cada driver com pequenas diferenças: o que se
 *       corrigia num continuava errado nos outros.</li>
 * </ul>
 *
 * <p><b>Como se usa.</b> Cada driver pede um construtor para o seu dialeto
 * ({@link DataBase#builder()}) e compõe o comando por partes:</p>
 * <pre>{@code
 * String sql = builder.select("*").from("clientes")
 *         .where(builder.condition().and(builder.comparison("idade", Operator.GREATER, "18")))
 *         .orderBy("nome", Order.ASC)
 *         .limit(250).offset(0)
 *         .build();
 * }</pre>
 *
 * <p>Os valores de INSERT, UPDATE e DELETE saem como {@code ?}, para irem ao
 * {@link java.sql.PreparedStatement} como parâmetros. Só as condições da pesquisa avançada
 * levam literais, porque o SQL é mostrado ao utilizador como texto.</p>
 *
 * <p>O construtor não tem estado de ligação nenhum: só sabe escrever SQL. Quem executa e
 * trata dos erros continua a ser o driver.</p>
 */
public final class QueryBuilder {

    // =====================================================================================
    // Enums
    // =====================================================================================

    /** Os JOIN que a pesquisa avançada oferece. */
    public enum Join {
        INNER("INNER JOIN", true),
        LEFT("LEFT JOIN", true),
        RIGHT("RIGHT JOIN", true),
        FULL("FULL OUTER JOIN", true),
        CROSS("CROSS JOIN", false),
        NATURAL("NATURAL JOIN", false);

        private final String keyword;
        private final boolean needsCondition;

        Join(final String keyword, final boolean needsCondition) {
            this.keyword = keyword;
            this.needsCondition = needsCondition;
        }

        public String keyword() {
            return keyword;
        }

        /** INNER, LEFT, RIGHT e FULL precisam de ON; CROSS e NATURAL não o aceitam. */
        public boolean needsCondition() {
            return needsCondition;
        }

        /** O MySQL não tem FULL JOIN e o Access só conhece INNER, LEFT e RIGHT. */
        public boolean supportedBy(final SQLTypes dialect) {
            return switch (dialect) {
                case MYSQL -> this != FULL;
                case MS_ACCESS -> this == INNER || this == LEFT || this == RIGHT;
                default -> true; // o SQLite tem RIGHT e FULL desde a 3.39
            };
        }

        /** Aceita o nome do enum ou o texto mostrado na interface ("LEFT JOIN", "FULL JOIN"). */
        public static Join fromText(final String text) {
            if (text == null || text.isBlank()) return null;
            final String normalised = text.trim().toUpperCase(Locale.ROOT);
            for (Join join : values()) {
                if (join.name().equals(normalised) || join.keyword.equals(normalised)) return join;
            }
            return normalised.equals("FULL JOIN") ? FULL : null;
        }

        @Override
        public String toString() {
            return keyword;
        }
    }

    /** Quantos valores um operador leva à direita. */
    public enum Operands {
        NONE, ONE, TWO, LIST, SUB_QUERY
    }

    /** Operadores das condições do WHERE. */
    public enum Operator {
        EQUAL("=", Operands.ONE),
        NOT_EQUAL("<>", Operands.ONE),
        GREATER(">", Operands.ONE),
        LESS("<", Operands.ONE),
        GREATER_OR_EQUAL(">=", Operands.ONE),
        LESS_OR_EQUAL("<=", Operands.ONE),
        LIKE("LIKE", Operands.ONE),
        NOT_LIKE("NOT LIKE", Operands.ONE),
        IN("IN", Operands.LIST),
        NOT_IN("NOT IN", Operands.LIST),
        BETWEEN("BETWEEN", Operands.TWO),
        NOT_BETWEEN("NOT BETWEEN", Operands.TWO),
        IS_NULL("IS NULL", Operands.NONE),
        IS_NOT_NULL("IS NOT NULL", Operands.NONE),
        EXISTS("EXISTS", Operands.SUB_QUERY),
        NOT_EXISTS("NOT EXISTS", Operands.SUB_QUERY);

        private final String symbol;
        private final Operands operands;

        Operator(final String symbol, final Operands operands) {
            this.symbol = symbol;
            this.operands = operands;
        }

        public String symbol() {
            return symbol;
        }

        public Operands operands() {
            return operands;
        }

        /** EXISTS não tem coluna à esquerda. */
        public boolean usesColumn() {
            return operands != Operands.SUB_QUERY;
        }

        /** Aceita o símbolo ("=", ">=", "NOT IN") e também o "!=" que a interface usava. */
        public static Operator fromSymbol(final String symbol) {
            if (symbol == null || symbol.isBlank()) return null;
            final String normalised = symbol.trim().toUpperCase(Locale.ROOT);
            if (normalised.equals("!=")) return NOT_EQUAL;
            if (normalised.equals("IS")) return IS_NULL;
            for (Operator operator : values()) {
                if (operator.symbol.equals(normalised) || operator.name().equals(normalised)) return operator;
            }
            return null;
        }

        @Override
        public String toString() {
            return symbol;
        }
    }

    /** Conector entre duas condições. */
    public enum Logic {
        AND, OR;

        public static Logic fromText(final String text) {
            return "OR".equalsIgnoreCase(text == null ? "" : text.trim()) ? OR : AND;
        }
    }

    /** Sentido de ordenação. */
    public enum Order {
        ASC, DESC;

        public static Order fromText(final String text) {
            return "DESC".equalsIgnoreCase(text == null ? "" : text.trim()) ? DESC : ASC;
        }
    }

    /** O que acontece às linhas filhas quando a linha referenciada muda ou é apagada. */
    public enum ForeignAction {
        CASCADE("CASCADE"),
        SET_NULL("SET NULL"),
        SET_DEFAULT("SET DEFAULT"),
        RESTRICT("RESTRICT"),
        NO_ACTION("NO ACTION");

        private final String keyword;

        ForeignAction(final String keyword) {
            this.keyword = keyword;
        }

        public String keyword() {
            return keyword;
        }

        /** O InnoDB do MySQL reconhece SET DEFAULT mas recusa a tabela que o use. */
        public boolean supportedBy(final SQLTypes dialect) {
            return !(dialect == SQLTypes.MYSQL && this == SET_DEFAULT);
        }

        /** Null quando não há ação escolhida (o JDBC devolve "UNKNOWN" nalguns motores). */
        public static ForeignAction fromText(final String text) {
            if (text == null || text.isBlank()) return null;
            final String normalised = text.trim().toUpperCase(Locale.ROOT).replace('_', ' ');
            for (ForeignAction action : values()) {
                if (action.keyword.equals(normalised)) return action;
            }
            return null;
        }

        @Override
        public String toString() {
            return keyword;
        }
    }

    /**
     * Modos de índice. No PostgreSQL o que não é UNIQUE é um método ({@code USING gin}),
     * e esses ficam de fora deste enum porque vão noutro sítio do comando.
     */
    public enum IndexKind {
        NORMAL(""),
        UNIQUE("UNIQUE"),
        FULLTEXT("FULLTEXT"),
        SPATIAL("SPATIAL");

        private final String keyword;

        IndexKind(final String keyword) {
            this.keyword = keyword;
        }

        public String keyword() {
            return keyword;
        }

        /** Null quando o texto é um método do PostgreSQL (btree, hash, gin, gist, brin...). */
        public static IndexKind fromText(final String text) {
            if (text == null || text.isBlank()) return NORMAL;
            final String normalised = text.trim().toUpperCase(Locale.ROOT);
            for (IndexKind kind : values()) {
                if (kind.keyword.equals(normalised) || kind.name().equals(normalised)) return kind;
            }
            return null;
        }
    }

    /**
     * O que mudou numa coluna entre o que está na base de dados e o que o formulário pede.
     *
     * <p>Cada motor sabe fazer só algumas destas mudanças com ALTER TABLE: o SQLite quase
     * nenhuma (tem de reconstruir a tabela), o MySQL redefine a coluna inteira com um só
     * CHANGE COLUMN, o PostgreSQL tem um comando para cada uma.</p>
     */
    public enum ColumnChange {
        NAME, TYPE, NOT_NULL, DEFAULT, PRIMARY_KEY, UNIQUE, FOREIGN_KEY, CHECK, AUTOINCREMENT, COMMENT, INDEX;

        /** As mudanças que só se fazem redefinindo a coluna (o CHANGE do MySQL). */
        public static final Set<ColumnChange> DEFINITION =
                Collections.unmodifiableSet(EnumSet.of(TYPE, NOT_NULL, DEFAULT, AUTOINCREMENT, COMMENT));

        public static EnumSet<ColumnChange> between(final ColumnMetadata before, final ColumnMetadata after) {
            final EnumSet<ColumnChange> changes = EnumSet.noneOf(ColumnChange.class);
            if (!Objects.equals(before.Name, after.Name)) changes.add(NAME);
            if (!sameType(before, after)) changes.add(TYPE);
            if (before.NOT_NULL != after.NOT_NULL) changes.add(NOT_NULL);
            if (!sameText(before.defaultValue, after.defaultValue)) changes.add(DEFAULT);
            if (before.IsPrimaryKey != after.IsPrimaryKey) changes.add(PRIMARY_KEY);
            if (before.isUnique != after.isUnique) changes.add(UNIQUE);
            if (!sameForeign(before.foreign, after.foreign)) changes.add(FOREIGN_KEY);
            if (!sameText(before.check, after.check)) changes.add(CHECK);
            if (declaredAutoIncrement(before) != declaredAutoIncrement(after)) changes.add(AUTOINCREMENT);
            if (!sameText(before.comment, after.comment)) changes.add(COMMENT);
            if (!sameText(before.index, after.index) || !sameText(before.indexType, after.indexType)) changes.add(INDEX);
            return changes;
        }

        private static boolean sameType(final ColumnMetadata a, final ColumnMetadata b) {
            if (!upper(a.Type).equals(upper(b.Type))) return false;
            final String type = baseType(a.Type);
            // O tamanho só conta nos tipos que o aceitam: uma coluna INTEGER traz do catálogo
            // um "tamanho" (a precisão) que o formulário não mostra, e isso não é mudança.
            if (LENGTH_TYPES.contains(type) && a.size != b.size) return false;
            if (DECIMAL_TYPES.contains(type)
                    && (a.integerDigits != b.integerDigits || a.decimalDigits != b.decimalDigits)) return false;
            if (type.equals("ENUM") || type.equals("SET")) {
                if (!Objects.equals(listOrEmpty(a.items), listOrEmpty(b.items))) return false;
                return sameText(a.aliasType, b.aliasType);
            }
            return true;
        }

        private static boolean sameForeign(final ColumnMetadata.Foreign a, final ColumnMetadata.Foreign b) {
            final boolean foreignA = a != null && a.isForeign;
            final boolean foreignB = b != null && b.isForeign;
            if (foreignA != foreignB) return false;
            if (!foreignA) return true;
            return sameText(a.tableRef, b.tableRef)
                    && sameText(a.columnRef, b.columnRef)
                    && actionOrDefault(a.onUpdate) == actionOrDefault(b.onUpdate)
                    && actionOrDefault(a.onEliminate) == actionOrDefault(b.onEliminate);
        }

        private static ForeignAction actionOrDefault(final String text) {
            final ForeignAction action = ForeignAction.fromText(text);
            return action == null ? ForeignAction.NO_ACTION : action;
        }

        private static List<String> listOrEmpty(final List<String> list) {
            return list == null ? List.of() : list;
        }
    }

    // =====================================================================================
    // Estado
    // =====================================================================================

    /** Tipos que levam comprimento entre parênteses: VARCHAR(50). */
    private static final Set<String> LENGTH_TYPES = Set.of(
            "CHAR", "VARCHAR", "CHARACTER", "VARYING CHARACTER", "CHARACTER VARYING", "NCHAR",
            "NATIVE CHARACTER", "NVARCHAR", "BINARY", "VARBINARY", "BPCHAR", "BIT", "VARBIT");

    /** Além dos anteriores, o MySQL aceita TEXT(n) e BLOB(n) e o Access TEXT(n). */
    private static final Set<String> LENGTH_TYPES_MYSQL_EXTRA = Set.of("TEXT", "BLOB");

    private static final Set<String> DECIMAL_TYPES = Set.of("DECIMAL", "NUMERIC", "DEC");

    private static final Set<String> SERIAL_TYPES = Set.of("SERIAL", "BIGSERIAL", "SMALLSERIAL", "SERIAL4", "SERIAL8", "SERIAL2");

    /** Pseudo-colunas que identificam a linha e não podem levar aspas ("CTID" não existe). */
    private static final Set<String> ROW_IDENTIFIERS = Set.of("ROWID", "_ROWID_", "OID", "CTID");

    /** Valores que podem ficar num DEFAULT sem aspas. */
    private static final Set<String> DEFAULT_KEYWORDS = Set.of(
            "NULL", "TRUE", "FALSE", "CURRENT_TIMESTAMP", "CURRENT_DATE", "CURRENT_TIME",
            "LOCALTIME", "LOCALTIMESTAMP", "CURRENT_USER", "SESSION_USER", "USER");

    /** Palavras reservadas que obrigam a pôr um nome entre aspas mesmo sendo "simples". */
    private static final Set<String> RESERVED = Set.of(
            "ADD", "ALL", "ALTER", "AND", "ANY", "AS", "ASC", "BETWEEN", "BY", "CASE", "CAST", "CHECK",
            "COLLATE", "COLUMN", "COMMIT", "CONSTRAINT", "CREATE", "CROSS", "CURRENT_DATE", "CURRENT_TIME",
            "CURRENT_TIMESTAMP", "CURRENT_USER", "DATABASE", "DEFAULT", "DELETE", "DESC", "DISTINCT", "DROP",
            "ELSE", "END", "ESCAPE", "EXCEPT", "EXISTS", "FALSE", "FETCH", "FOR", "FOREIGN", "FROM", "FULL",
            "GRANT", "GROUP", "GROUPS", "HAVING", "IF", "IN", "INDEX", "INNER", "INSERT", "INTERSECT", "INTERVAL",
            "INTO", "IS", "JOIN", "KEY", "KEYS", "LEFT", "LIKE", "LIMIT", "NATURAL", "NOT", "NULL", "OF", "OFFSET",
            "ON", "OR", "ORDER", "OUTER", "OVER", "PARTITION", "PRIMARY", "RANGE", "REFERENCES", "RENAME",
            "REPLACE", "REVOKE", "RIGHT", "ROLLBACK", "ROW", "ROWS", "SCHEMA", "SELECT", "SET", "TABLE", "THEN",
            "TO", "TRANSACTION", "TRIGGER", "TRUE", "UNION", "UNIQUE", "UPDATE", "USER", "USING", "VALUES",
            "VIEW", "WHEN", "WHERE", "WINDOW", "WITH");

    private static final Pattern SIMPLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");
    // Sem espaço entre o nome e o parêntese, para "João (admin)" continuar a ser texto.
    private static final Pattern FUNCTION_CALL = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*\\(.*\\)", Pattern.DOTALL);
    private static final Pattern BIT_OR_HEX_LITERAL = Pattern.compile("(?i)[bx]'[0-9a-f]*'");
    private static final Pattern TIMESTAMP_FUNCTION =
            Pattern.compile("(?i)(NOW|CURRENT_TIMESTAMP|LOCALTIME|LOCALTIMESTAMP)\\s*\\(\\s*\\d*\\s*\\)");

    private final SQLTypes dialect;

    /** Com isto os nomes só levam aspas quando é preciso — para o SQL que o utilizador lê. */
    private final boolean minimalQuoting;

    public QueryBuilder(final SQLTypes dialect) {
        this(dialect, false);
    }

    private QueryBuilder(final SQLTypes dialect, final boolean minimalQuoting) {
        this.dialect = dialect == null ? SQLTypes.SQLITE : dialect;
        this.minimalQuoting = minimalQuoting;
    }

    public static QueryBuilder of(final SQLTypes dialect) {
        return new QueryBuilder(dialect);
    }

    /**
     * Versão que só cita os nomes que precisam, para o SQL mostrado ao utilizador na
     * pesquisa avançada ficar legível ({@code SELECT nome FROM clientes}).
     */
    public QueryBuilder readable() {
        return new QueryBuilder(dialect, true);
    }

    public SQLTypes getDialect() {
        return dialect;
    }

    // =====================================================================================
    // Nomes e valores
    // =====================================================================================

    /** Nome entre aspas do dialeto: "nome" no SQLite e PostgreSQL, `nome` no MySQL, [nome] no Access. */
    public String quote(final String identifier) {
        if (identifier == null || identifier.isBlank()) return identifier;
        return switch (dialect) {
            case MYSQL -> "`" + identifier.replace("`", "``") + "`";
            case MS_ACCESS -> "[" + identifier.replace("]", "]]") + "]";
            default -> "\"" + identifier.replace("\"", "\"\"") + "\"";
        };
    }

    /** Cita só quando o nome tem espaços, símbolos, é palavra reservada ou (no PostgreSQL) maiúsculas. */
    public String quoteIfNeeded(final String identifier) {
        if (identifier == null || identifier.isBlank()) return identifier;
        final boolean simple = SIMPLE_NAME.matcher(identifier).matches()
                && !RESERVED.contains(identifier.toUpperCase(Locale.ROOT));
        // No PostgreSQL um nome sem aspas passa a minúsculas: "Clientes" sem aspas é clientes.
        final boolean foldsCase = dialect == SQLTypes.POSTGRESQL && !identifier.equals(identifier.toLowerCase(Locale.ROOT));
        return simple && !foldsCase ? identifier : quote(identifier);
    }

    /** Nome de tabela ou coluna, com as aspas que a configuração deste construtor pede. */
    public String name(final String identifier) {
        if (identifier == null || identifier.isBlank() || identifier.equals("*")) return identifier;
        if (isRowIdentifier(identifier)) return identifier;
        return minimalQuoting ? quoteIfNeeded(identifier) : quote(identifier);
    }

    /** {@code tabela.coluna}, cada parte com as suas aspas. */
    public String qualified(final String table, final String column) {
        if (table == null || table.isBlank()) return name(column);
        return name(table) + "." + name(column);
    }

    /** ROWID, OID e o CTID do PostgreSQL identificam a linha e não são colunas a sério. */
    public static boolean isRowIdentifier(final String identifier) {
        return identifier != null && ROW_IDENTIFIERS.contains(identifier.toUpperCase(Locale.ROOT));
    }

    /** Lista de nomes separados por vírgulas, cada um com aspas. */
    public String names(final Collection<String> identifiers) {
        final StringJoiner joiner = new StringJoiner(", ");
        for (String identifier : identifiers) joiner.add(name(identifier));
        return joiner.toString();
    }

    /** Texto como literal SQL: plicas dobradas e, no MySQL, também as barras invertidas. */
    public String literal(final String value) {
        if (value == null) return "NULL";
        String escaped = value;
        if (dialect == SQLTypes.MYSQL) escaped = escaped.replace("\\", "\\\\");
        return "'" + escaped.replace("'", "''") + "'";
    }

    /** Número validado; lança IllegalArgumentException se o texto não for um número. */
    public String number(final String value) {
        final String trimmed = value == null ? "" : value.trim();
        if (!isNumber(trimmed)) throw new IllegalArgumentException("'" + value + "' is not a number.");
        return trimmed;
    }

    public static boolean isNumber(final String value) {
        return value != null && NUMBER.matcher(value.trim()).matches();
    }

    /**
     * Valor por omissão pronto a ir depois de DEFAULT, ou null se não houver valor.
     *
     * <p>O valor chega de dois sítios: escrito no formulário ({@code abc}, {@code 10},
     * {@code CURRENT_TIMESTAMP}) ou lido do catálogo, onde cada motor o devolve à sua maneira
     * (o SQLite já com plicas, o PostgreSQL com {@code ::tipo}, o MySQL sem nada). Números,
     * NULL, palavras-chave de data, chamadas de função, expressões entre parênteses e o que
     * já vem entre plicas ficam como estão; o resto é texto e leva plicas.</p>
     *
     * <p>Antes o valor ia sempre tal como foi escrito: {@code DEFAULT abc} dava erro de
     * sintaxe, e no MySQL e no PostgreSQL o DEFAULT era mesmo ignorado.</p>
     */
    public String defaultValue(final String value) {
        if (value == null) return null;
        final String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;

        if (NUMBER.matcher(trimmed).matches()) return trimmed;
        if (DEFAULT_KEYWORDS.contains(trimmed.toUpperCase(Locale.ROOT))) return trimmed.toUpperCase(Locale.ROOT);
        if (trimmed.length() >= 2 && trimmed.startsWith("'") && trimmed.endsWith("'")) return trimmed;
        if (BIT_OR_HEX_LITERAL.matcher(trimmed).matches()) return trimmed; // b'0', x'FF'
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) return trimmed;
        if (trimmed.contains("::")) return trimmed;
        if (FUNCTION_CALL.matcher(trimmed).matches()) {
            // O SQLite só aceita expressões num DEFAULT entre parênteses, e o MySQL também,
            // com exceção das funções de data e hora.
            if (dialect == SQLTypes.SQLITE) return "(" + trimmed + ")";
            if (dialect == SQLTypes.MYSQL && !TIMESTAMP_FUNCTION.matcher(trimmed).matches()) return "(" + trimmed + ")";
            return trimmed;
        }
        return literal(trimmed);
    }

    /** Tira o ";" final (e espaços) para se poder acrescentar LIMIT ou meter numa sub-consulta. */
    public static String stripTerminator(final String sql) {
        if (sql == null) return "";
        String result = sql.strip();
        while (result.endsWith(";")) result = result.substring(0, result.length() - 1).strip();
        return result;
    }

    /**
     * Divide uma lista escrita pelo utilizador ({@code a, 'b, c', d}) pelas vírgulas que
     * não estão dentro de plicas. Uma plica só abre um valor entre plicas no início desse
     * valor — o apóstrofo de {@code O'Neill} é só uma letra. Os valores entre plicas saem
     * como foram escritos; {@link #unquote} tira-lhes as plicas.
     */
    public static List<String> splitList(final String text) {
        final List<String> items = new ArrayList<>();
        if (text == null || text.isBlank()) return items;
        final StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (quoted) {
                current.append(c);
                if (c == '\'') {
                    // '' dentro de plicas é uma plica, não o fim do valor.
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\'') {
                        current.append('\'');
                        i++;
                    } else {
                        quoted = false;
                    }
                }
            } else if (c == '\'' && current.toString().isBlank()) {
                current.setLength(0);
                current.append(c);
                quoted = true;
            } else if (c == ',') {
                items.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        items.add(current.toString().trim());
        items.removeIf(String::isEmpty);
        return items;
    }

    /** {@code 'it''s'} → {@code it's}; um valor sem plicas à volta fica como está. */
    public static String unquote(final String item) {
        if (item != null && item.length() >= 2 && item.startsWith("'") && item.endsWith("'")) {
            return item.substring(1, item.length() - 1).replace("''", "'");
        }
        return item;
    }

    // =====================================================================================
    // Tipos e definição de colunas
    // =====================================================================================

    /** Tipo da coluna com o tamanho, a precisão ou a lista de valores quando o tipo os leva. */
    public String columnType(final ColumnMetadata column) {
        String type = column.Type == null || column.Type.isBlank() ? "TEXT" : column.Type.trim();
        String base = baseType(type);

        if (column.autoincrement >= 1) {
            if (dialect == SQLTypes.SQLITE) return "INTEGER"; // AUTOINCREMENT só em INTEGER PRIMARY KEY
            if (dialect == SQLTypes.MS_ACCESS) return "COUNTER";
        }

        // O tipo já vem completo, como "TINYINT(1)" ou "DECIMAL(10,2)": fica como está.
        if (type.contains("(")) return type;

        if (base.equals("ENUM") || base.equals("SET")) {
            if (dialect == SQLTypes.MYSQL) {
                if (column.items == null || column.items.isEmpty()) {
                    throw new IllegalArgumentException(base + " column " + column.Name + " needs at least one value.");
                }
                return base + "(" + literals(column.items) + ")";
            }
            if (dialect == SQLTypes.POSTGRESQL && base.equals("ENUM")) {
                if (column.aliasType == null || column.aliasType.isBlank()) {
                    throw new IllegalArgumentException("ENUM column " + column.Name + " needs the name of the enum type.");
                }
                return quote(column.aliasType);
            }
            return "TEXT";
        }

        if (dialect == SQLTypes.POSTGRESQL && base.equals("VARYING CHARACTER")) {
            type = "CHARACTER VARYING"; // sinónimo do SQLite que o PostgreSQL não conhece
            base = type;
        }

        if (column.size > 0 && acceptsLength(base) && column.size < maxLength()) {
            return type + "(" + column.size + ")";
        }

        if (DECIMAL_TYPES.contains(base) && (column.integerDigits > 0 || column.decimalDigits > 0)) {
            // integerDigits são os dígitos antes da vírgula; a precisão do DECIMAL é o total.
            // Antes ia DECIMAL(integerDigits, decimalDigits), e cada vez que a tabela era
            // recriada a coluna perdia casas: DECIMAL(10,2) lido como 8+2 voltava DECIMAL(8,2).
            final int precision = Math.max(1, column.integerDigits + column.decimalDigits);
            return type + "(" + precision + ", " + column.decimalDigits + ")";
        }

        return type;
    }

    /** Tipo aceite pelo ALTER COLUMN ... TYPE do PostgreSQL, onde SERIAL não existe. */
    private String alterableType(final ColumnMetadata column) {
        final String base = baseType(column.Type);
        if (dialect == SQLTypes.POSTGRESQL && SERIAL_TYPES.contains(base)) {
            return switch (base) {
                case "BIGSERIAL", "SERIAL8" -> "BIGINT";
                case "SMALLSERIAL", "SERIAL2" -> "SMALLINT";
                default -> "INTEGER";
            };
        }
        return columnType(column);
    }

    private boolean acceptsLength(final String base) {
        if (LENGTH_TYPES.contains(base)) return true;
        return (dialect == SQLTypes.MYSQL || dialect == SQLTypes.MS_ACCESS) && LENGTH_TYPES_MYSQL_EXTRA.contains(base);
    }

    /** Acima disto o "tamanho" do catálogo quer dizer "sem limite" e não vai para o SQL. */
    private int maxLength() {
        return dialect == SQLTypes.POSTGRESQL ? 10_485_761 : 1_000_000_000;
    }

    /**
     * Definição completa de uma coluna, como aparece dentro do CREATE TABLE ou depois do
     * ADD COLUMN.
     *
     * @param inlinePrimaryKey escreve PRIMARY KEY na própria coluna (quando é a única chave)
     * @param inlineForeignKey escreve REFERENCES na própria coluna (o MySQL ignora esta forma,
     *                         por isso lá a chave estrangeira vai sempre como restrição)
     */
    public String columnDefinition(final ColumnMetadata column, final boolean inlinePrimaryKey,
                                   final boolean inlineForeignKey) {
        return definition(column, inlinePrimaryKey, inlineForeignKey, true);
    }

    /**
     * @param withConstraints false no CHANGE COLUMN do MySQL: lá um UNIQUE ou um CHECK na
     *                        definição cria mais um índice/restrição de cada vez que a coluna
     *                        é redefinida, por isso esses são tratados à parte.
     */
    private String definition(final ColumnMetadata column, final boolean inlinePrimaryKey,
                              final boolean inlineForeignKey, final boolean withConstraints) {
        final StringJoiner parts = new StringJoiner(" ");
        parts.add(quote(column.Name));
        parts.add(columnType(column));

        final boolean autoIncrement = column.autoincrement >= 1;
        final boolean primaryHere = inlinePrimaryKey && column.IsPrimaryKey;

        if (autoIncrement && dialect == SQLTypes.MYSQL) parts.add("AUTO_INCREMENT");
        if (autoIncrement && dialect == SQLTypes.POSTGRESQL && !SERIAL_TYPES.contains(baseType(column.Type))) {
            parts.add("GENERATED BY DEFAULT AS IDENTITY");
        }

        if (primaryHere) {
            parts.add("PRIMARY KEY");
            if (autoIncrement && dialect == SQLTypes.SQLITE) parts.add("AUTOINCREMENT");
        }

        // No MySQL as colunas da chave primária são sempre NOT NULL; ao redefinir uma sem o
        // dizer, a versão 8 recusa.
        if (column.NOT_NULL || (dialect == SQLTypes.MYSQL && column.IsPrimaryKey)) parts.add("NOT NULL");

        // Uma coluna gerada pelo motor não pode ter DEFAULT (o MySQL e o PostgreSQL recusam).
        final String defaultValue = autoIncrement ? null : defaultValue(column.defaultValue);
        if (defaultValue != null) parts.add("DEFAULT " + defaultValue);

        if (withConstraints && column.isUnique && !column.IsPrimaryKey) parts.add("UNIQUE");

        if (withConstraints && column.check != null && !column.check.isBlank()) {
            parts.add("CHECK (" + column.check.trim() + ")");
        }

        if (dialect == SQLTypes.MYSQL && column.comment != null && !column.comment.isBlank()) {
            parts.add("COMMENT " + literal(column.comment));
        }

        if (inlineForeignKey && isForeign(column)) parts.add(references(column));

        if (column.extra != null && !column.extra.isBlank()) parts.add(column.extra.trim());

        return parts.toString();
    }

    /** {@code REFERENCES tabela (coluna) ON UPDATE ... ON DELETE ...} */
    public String references(final ColumnMetadata column) {
        final ColumnMetadata.Foreign foreign = column.foreign;
        final StringJoiner parts = new StringJoiner(" ");
        parts.add("REFERENCES " + name(foreign.tableRef) + " (" + name(foreign.columnRef) + ")");
        final ForeignAction onUpdate = foreignAction(foreign.onUpdate);
        final ForeignAction onDelete = foreignAction(foreign.onEliminate);
        if (onUpdate != null) parts.add("ON UPDATE " + onUpdate.keyword());
        if (onDelete != null) parts.add("ON DELETE " + onDelete.keyword());
        return parts.toString();
    }

    private ForeignAction foreignAction(final String text) {
        final ForeignAction action = ForeignAction.fromText(text);
        if (action != null && !action.supportedBy(dialect)) {
            throw new IllegalArgumentException(dialect + " does not support ON ... " + action.keyword() + ".");
        }
        return action;
    }

    /** Restrição de chave estrangeira ao nível da tabela, com nome para se poder apagar depois. */
    public String foreignKeyConstraint(final String table, final ColumnMetadata column) {
        final String constraint = "FOREIGN KEY (" + name(column.Name) + ") " + references(column);
        // No SQLite os nomes das restrições não servem para nada (não há DROP CONSTRAINT).
        if (dialect == SQLTypes.SQLITE) return constraint;
        return "CONSTRAINT " + name(constraintName("fk", table, column.Name)) + " " + constraint;
    }

    public static boolean isForeign(final ColumnMetadata column) {
        return column.foreign != null && column.foreign.isForeign
                && column.foreign.tableRef != null && !column.foreign.tableRef.isBlank()
                && column.foreign.columnRef != null && !column.foreign.columnRef.isBlank();
    }

    /** Nome previsível para as restrições criadas aqui: fk_tabela_coluna, uq_..., chk_... */
    public static String constraintName(final String prefix, final String table, final String column) {
        String name = (prefix + "_" + table + "_" + column).replaceAll("[^A-Za-z0-9_]", "_");
        // O MySQL não aceita nomes com mais de 64 caracteres e o PostgreSQL corta aos 63.
        if (name.length() > 60) name = name.substring(0, 51) + "_" + Integer.toHexString(name.hashCode() & 0xfffffff);
        return name;
    }

    /** Nome de índice por omissão: idx_tabela_col1_col2. */
    public static String indexName(final String table, final Collection<String> columns) {
        return constraintName("idx", table, String.join("_", columns));
    }

    private String literals(final Collection<String> values) {
        final StringJoiner joiner = new StringJoiner(", ");
        for (String value : values) joiner.add(literal(value));
        return joiner.toString();
    }

    // =====================================================================================
    // Consultas e escrita de linhas
    // =====================================================================================

    public Select select(final String... columns) {
        return new Select(Arrays.asList(columns));
    }

    public Select select(final Collection<String> columns) {
        return new Select(columns);
    }

    public Insert insert(final String table) {
        return new Insert(table);
    }

    public Update update(final String table) {
        return new Update(table);
    }

    public Delete delete(final String table) {
        return new Delete(table);
    }

    public Condition condition() {
        return new Condition();
    }

    /** {@code SELECT COUNT(*) FROM tabela} */
    public String count(final String table) {
        return "SELECT COUNT(*) FROM " + name(table);
    }

    /**
     * Número de linhas que uma consulta devolve, para calcular as páginas.
     *
     * <p>Substitui o truque antigo de cortar o texto entre WHERE e LIMIT: aquilo apanhava o
     * ORDER BY (que o PostgreSQL recusa num COUNT) e perdia os JOIN.</p>
     */
    public String countQuery(final String query) {
        return "SELECT COUNT(*) FROM (" + stripTerminator(query) + ") " + (dialect == SQLTypes.MS_ACCESS ? "" : "AS ") + "counted";
    }

    /** Acrescenta LIMIT e OFFSET a uma consulta escrita pelo utilizador. */
    public String paginate(final String query, final long limit, final long offset) {
        return stripTerminator(query) + " " + limitClause(limit, offset);
    }

    private String limitClause(final long limit, final long offset) {
        final long effectiveLimit = limit >= 0 ? limit : (dialect == SQLTypes.SQLITE ? -1 : Long.MAX_VALUE);
        return "LIMIT " + effectiveLimit + (offset > 0 ? " OFFSET " + offset : offset == 0 ? " OFFSET 0" : "");
    }

    /**
     * Comparação de uma condição do WHERE. Os valores já têm de vir em SQL (literal, número,
     * coluna, função ou sub-consulta) — quem os conhece é quem sabe o tipo escolhido.
     */
    public String comparison(final String left, final Operator operator, final String... values) {
        return comparison(left, operator, Arrays.asList(values));
    }

    public String comparison(final String left, final Operator operator, final List<String> values) {
        if (operator == null) throw new IllegalArgumentException("Choose an operator.");
        final List<String> operands = values == null ? List.of()
                : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();

        if (operator.usesColumn() && (left == null || left.isBlank())) {
            throw new IllegalArgumentException("Choose a column for " + operator.symbol() + ".");
        }

        return switch (operator.operands()) {
            case NONE -> left + " " + operator.symbol();
            case ONE -> {
                requireOperands(operator, operands, 1);
                yield left + " " + operator.symbol() + " " + operands.getFirst();
            }
            case TWO -> {
                requireOperands(operator, operands, 2);
                yield left + " " + operator.symbol() + " " + operands.get(0) + " AND " + operands.get(1);
            }
            case LIST -> {
                requireOperands(operator, operands, 1);
                // Uma sub-consulta já traz os parênteses: IN (SELECT ...), e não IN ((SELECT ...)).
                final boolean subQuery = operands.size() == 1 && isSubQuery(operands.getFirst());
                yield left + " " + operator.symbol() + " "
                        + (subQuery ? operands.getFirst() : "(" + String.join(", ", operands) + ")");
            }
            case SUB_QUERY -> {
                requireOperands(operator, operands, 1);
                final String query = operands.getFirst();
                yield operator.symbol() + " " + (query.startsWith("(") ? query : "(" + query + ")");
            }
        };
    }

    private static void requireOperands(final Operator operator, final List<String> operands, final int minimum) {
        if (operands.size() < minimum) {
            throw new IllegalArgumentException(operator.symbol() + " needs "
                    + (minimum == 2 ? "two values (write them separated by a comma)." : "a value."));
        }
    }

    private static boolean isSubQuery(final String value) {
        return value.startsWith("(") && value.endsWith(")")
                && value.substring(1).stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT");
    }

    /** SELECT ... FROM ... [JOIN] [WHERE] [ORDER BY] [LIMIT/OFFSET], sempre por esta ordem. */
    public final class Select {
        private final List<String> columns = new ArrayList<>();
        private String from;
        private final List<String> joins = new ArrayList<>();
        private Condition where;
        private final List<String> order = new ArrayList<>();
        private long limit = -1;
        private long offset = -1;

        private Select(final Collection<String> columns) {
            for (String column : columns) if (column != null && !column.isBlank()) this.columns.add(column);
        }

        public Select from(final String table) {
            this.from = name(table);
            return this;
        }

        /**
         * JOIN com outra tabela. As duas colunas do ON vêm já escritas
         * ({@link #qualified(String, String)}); CROSS e NATURAL ignoram-nas.
         */
        public Select join(final Join join, final String table, final String leftColumn, final String rightColumn) {
            if (join == null || table == null || table.isBlank()) return this;
            if (!join.supportedBy(dialect)) {
                throw new IllegalArgumentException(dialect + " does not support " + join.keyword() + ".");
            }
            String clause = join.keyword() + " " + name(table);
            if (join.needsCondition()) {
                if (leftColumn == null || leftColumn.isBlank() || rightColumn == null || rightColumn.isBlank()) {
                    throw new IllegalArgumentException(join.keyword() + " needs the two columns of the ON condition.");
                }
                clause += " ON " + leftColumn + " = " + rightColumn;
            }
            joins.add(clause);
            return this;
        }

        public Select where(final Condition condition) {
            this.where = condition;
            return this;
        }

        public Select orderBy(final String column, final Order direction) {
            if (column != null && !column.isBlank()) {
                order.add(column + " " + (direction == null ? Order.ASC : direction).name());
            }
            return this;
        }

        public Select limit(final long limit) {
            this.limit = limit;
            return this;
        }

        public Select offset(final long offset) {
            this.offset = offset;
            return this;
        }

        public String build() {
            if (columns.isEmpty()) throw new IllegalStateException("SELECT needs at least one column.");
            if (from == null || from.isBlank()) throw new IllegalStateException("SELECT needs a table.");
            final StringJoiner sql = new StringJoiner(" ");
            sql.add("SELECT").add(String.join(", ", columns)).add("FROM").add(from);
            joins.forEach(sql::add);
            if (where != null && !where.isEmpty()) sql.add("WHERE").add(where.toString());
            if (!order.isEmpty()) sql.add("ORDER BY").add(String.join(", ", order));
            if (limit >= 0 || offset >= 0) sql.add(limitClause(limit, offset));
            return sql.toString();
        }

        @Override
        public String toString() {
            return build();
        }
    }

    /**
     * Preenche uma coluna com o valor da linha correspondente de outra tabela (o "Fill
     * foreign" do formulário de colunas). Cada motor escreve isto de maneira diferente: o
     * MySQL com UPDATE ... JOIN, o PostgreSQL com UPDATE ... FROM e o SQLite com uma
     * sub-consulta.
     *
     * @param childKey  coluna da tabela a atualizar que diz qual é a linha correspondente
     * @param parentKey coluna da outra tabela com que {@code childKey} é comparada
     */
    public String updateFromParent(final String child, final String childColumn, final String childKey,
                                   final String parent, final String parentColumn, final String parentKey) {
        return switch (dialect) {
            case MYSQL -> "UPDATE " + name(child) + " c JOIN " + name(parent) + " p ON p." + name(parentKey)
                    + " = c." + name(childKey) + " SET c." + name(childColumn) + " = p." + name(parentColumn);
            case POSTGRESQL -> "UPDATE " + name(child) + " c SET " + name(childColumn) + " = p." + name(parentColumn)
                    + " FROM " + name(parent) + " p WHERE p." + name(parentKey) + " = c." + name(childKey);
            default -> "UPDATE " + name(child) + " SET " + name(childColumn) + " = (SELECT p." + name(parentColumn)
                    + " FROM " + name(parent) + " p WHERE p." + name(parentKey) + " = " + name(child) + "." + name(childKey) + ")";
        };
    }

    /** INSERT INTO tabela (a, b) VALUES (?, ?), ou INSERT ... SELECT a partir de outra tabela. */
    public final class Insert {
        private final String table;
        private final List<String> columns = new ArrayList<>();
        private String source;

        private Insert(final String table) {
            this.table = table;
        }

        public Insert columns(final Collection<String> columns) {
            this.columns.addAll(columns);
            return this;
        }

        /** Copia as mesmas colunas de outra tabela: {@code INSERT INTO t (a, b) SELECT a, b FROM origem}. */
        public Insert fromTable(final String sourceTable) {
            this.source = sourceTable;
            return this;
        }

        public String build() {
            if (source != null) {
                if (columns.isEmpty()) throw new IllegalStateException("INSERT ... SELECT needs the columns to copy.");
                return "INSERT INTO " + name(table) + " (" + names(columns) + ") SELECT " + names(columns) + " FROM " + name(source);
            }
            if (columns.isEmpty()) {
                return dialect == SQLTypes.MYSQL
                        ? "INSERT INTO " + name(table) + " () VALUES ()"
                        : "INSERT INTO " + name(table) + " DEFAULT VALUES";
            }
            return "INSERT INTO " + name(table) + " (" + names(columns) + ") VALUES ("
                    + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        }
    }

    /** UPDATE tabela SET a = ? WHERE chave = ? — nunca sem WHERE. */
    public final class Update {
        private final String table;
        private final List<String> set = new ArrayList<>();
        private final List<String> where = new ArrayList<>();
        private String returning;

        private Update(final String table) {
            this.table = table;
        }

        public Update set(final String column) {
            set.add(name(column) + " = ?");
            return this;
        }

        /** Mais uma coluna da chave: {@code coluna = ?}. */
        public Update where(final String column) {
            where.add(name(column) + " = ?");
            return this;
        }

        public Update where(final Collection<String> columns) {
            for (String column : columns) where(column);
            return this;
        }

        /** Só o PostgreSQL devolve colunas de um UPDATE (é assim que se lê o CTID novo). */
        public Update returning(final String expression) {
            if (dialect == SQLTypes.POSTGRESQL) this.returning = expression;
            return this;
        }

        public String build() {
            if (set.isEmpty()) throw new IllegalStateException("UPDATE needs at least one column to set.");
            if (where.isEmpty()) throw new IllegalStateException("UPDATE without WHERE would change every row.");
            return "UPDATE " + name(table) + " SET " + String.join(", ", set)
                    + " WHERE " + String.join(" AND ", where)
                    + (returning == null ? "" : " RETURNING " + returning);
        }
    }

    /** DELETE FROM tabela WHERE ... */
    public final class Delete {
        private final String table;
        private String where;
        private boolean allRows = false;

        private Delete(final String table) {
            this.table = table;
        }

        public Delete where(final Condition condition) {
            this.where = condition == null || condition.isEmpty() ? null : condition.toString();
            return this;
        }

        /**
         * Apaga as linhas identificadas pela chave. Com uma coluna fica
         * {@code chave IN (?, ?)}; com chave composta cada linha tem o seu par:
         * {@code (a = ? AND b = ?) OR (a = ? AND b = ?)}.
         */
        public Delete whereKeys(final List<String> keyColumns, final int rows) {
            if (keyColumns.isEmpty() || rows <= 0) throw new IllegalArgumentException("No rows to delete.");
            if (keyColumns.size() == 1) {
                where = name(keyColumns.getFirst()) + " IN (" + String.join(", ", Collections.nCopies(rows, "?")) + ")";
            } else {
                final StringJoiner row = new StringJoiner(" AND ", "(", ")");
                for (String key : keyColumns) row.add(name(key) + " = ?");
                where = String.join(" OR ", Collections.nCopies(rows, row.toString()));
            }
            return this;
        }

        /** Tem de ser pedido explicitamente: um DELETE sem condição apaga a tabela toda. */
        public Delete allRows() {
            this.allRows = true;
            return this;
        }

        public String build() {
            if (where == null && !allRows) throw new IllegalStateException("DELETE without WHERE would remove every row.");
            return "DELETE FROM " + name(table) + (where == null ? "" : " WHERE " + where);
        }
    }

    /**
     * Condição de um WHERE, montada peça a peça. Cada peça traz o conector que a liga à
     * anterior (o da primeira não conta); os grupos ficam entre parênteses.
     */
    public static final class Condition {
        private final List<String> parts = new ArrayList<>();
        private final List<Logic> connectors = new ArrayList<>();

        public Condition and(final String condition) {
            return add(Logic.AND, condition);
        }

        public Condition or(final String condition) {
            return add(Logic.OR, condition);
        }

        public Condition add(final Logic before, final String condition) {
            if (condition == null || condition.isBlank()) return this;
            parts.add(condition.trim());
            connectors.add(before == null ? Logic.AND : before);
            return this;
        }

        public Condition add(final Logic before, final Condition group) {
            if (group == null || group.isEmpty()) return this;
            return add(before, group.size() > 1 ? "(" + group + ")" : group.toString());
        }

        public boolean isEmpty() {
            return parts.isEmpty();
        }

        public int size() {
            return parts.size();
        }

        @Override
        public String toString() {
            final StringJoiner sql = new StringJoiner(" ");
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sql.add(connectors.get(i).name());
                sql.add(parts.get(i));
            }
            return sql.toString();
        }
    }

    // =====================================================================================
    // Estrutura: tabelas, colunas, índices
    // =====================================================================================

    public CreateTable createTable(final String table) {
        return new CreateTable(table);
    }

    public AlterTable alterTable(final String table) {
        return new AlterTable(table);
    }

    public String dropTable(final String table) {
        return "DROP TABLE " + name(table);
    }

    public String createView(final String view, final String query) {
        return "CREATE VIEW " + name(view) + " AS " + stripTerminator(query);
    }

    public String dropView(final String view) {
        return "DROP VIEW " + name(view);
    }

    /** No PostgreSQL um trigger pertence à tabela e o DROP tem de a dizer. */
    public String dropTrigger(final String trigger, final String table) {
        final String sql = "DROP TRIGGER IF EXISTS " + name(trigger);
        return dialect == SQLTypes.POSTGRESQL && table != null && !table.isBlank() ? sql + " ON " + name(table) : sql;
    }

    /** Tipo enumerado do PostgreSQL, que tem de existir antes da coluna que o usa. */
    public String createEnumType(final String typeName, final Collection<String> values) {
        return "CREATE TYPE " + quote(typeName) + " AS ENUM (" + literals(values) + ")";
    }

    /**
     * CREATE INDEX. O modo pode ser vazio (índice normal), UNIQUE, FULLTEXT/SPATIAL (MySQL)
     * ou um método do PostgreSQL (btree, hash, gin, gist, brin), que vai para o USING.
     *
     * <p>Antes, sem modo escolhido, o SQLite recebia {@code CREATE null INDEX}.</p>
     */
    public String createIndex(final String indexName, final String table, final List<String> columns, final String mode) {
        if (columns == null || columns.isEmpty()) throw new IllegalArgumentException("An index needs at least one column.");
        final String name = indexName == null || indexName.isBlank() ? indexName(table, columns) : indexName.trim();

        final IndexKind kind = IndexKind.fromText(mode);
        final String method = kind == null ? mode.trim().toLowerCase(Locale.ROOT) : null;

        if (kind == IndexKind.FULLTEXT || kind == IndexKind.SPATIAL) {
            if (dialect != SQLTypes.MYSQL) throw new IllegalArgumentException(kind.name() + " indexes only exist in MySQL.");
        }
        if (method != null && dialect != SQLTypes.POSTGRESQL) {
            throw new IllegalArgumentException("Index method " + mode + " only exists in PostgreSQL.");
        }

        final StringJoiner sql = new StringJoiner(" ");
        sql.add("CREATE");
        if (kind != null && kind != IndexKind.NORMAL) sql.add(kind.keyword());
        sql.add("INDEX").add(name(name)).add("ON").add(name(table));
        if (method != null) sql.add("USING " + method);
        sql.add("(" + names(columns) + ")");
        return sql.toString();
    }

    /** No MySQL um índice pertence à tabela e o DROP INDEX tem de a dizer. */
    public String dropIndex(final String indexName, final String table) {
        if (dialect == SQLTypes.MYSQL) return "DROP INDEX " + name(indexName) + " ON " + name(table);
        return "DROP INDEX " + name(indexName);
    }

    /**
     * CREATE TABLE a partir dos metadados das colunas.
     *
     * <p>Uma chave primária de uma só coluna fica na própria coluna (é a forma que o
     * AUTOINCREMENT do SQLite exige); com várias fica {@code PRIMARY KEY (a, b)} no fim — o
     * código antigo escrevia PRIMARY KEY em cada coluna, e o motor recusava a tabela.</p>
     */
    public final class CreateTable {
        private final String table;
        private boolean temporary = false;
        private boolean withoutRowId = false;
        private boolean strict = false;
        private boolean ifNotExists = false;
        private final List<ColumnMetadata> columns = new ArrayList<>();
        private final List<String> constraints = new ArrayList<>();
        private final List<String> checks = new ArrayList<>();
        private List<String> primaryKeyOrder = null;

        private CreateTable(final String table) {
            this.table = table;
        }

        /**
         * Ordem das colunas da chave primária composta, quando não é a ordem das colunas
         * ({@code PRIMARY KEY (b, a)}). Sem isto segue a ordem das colunas marcadas.
         */
        public CreateTable primaryKeyOrder(final List<String> columns) {
            this.primaryKeyOrder = columns == null || columns.isEmpty() ? null : new ArrayList<>(columns);
            return this;
        }

        public CreateTable temporary(final boolean temporary) {
            this.temporary = temporary;
            return this;
        }

        /** Só o SQLite tem tabelas sem rowid; nos outros motores é ignorado. */
        public CreateTable withoutRowId(final boolean withoutRowId) {
            this.withoutRowId = withoutRowId;
            return this;
        }

        /** Tabelas STRICT do SQLite (3.37+), que só aceitam valores do tipo declarado. */
        public CreateTable strict(final boolean strict) {
            this.strict = strict;
            return this;
        }

        public CreateTable ifNotExists() {
            this.ifNotExists = true;
            return this;
        }

        public CreateTable column(final ColumnMetadata column) {
            columns.add(column);
            return this;
        }

        public CreateTable columns(final Collection<ColumnMetadata> columns) {
            this.columns.addAll(columns);
            return this;
        }

        /** Restrição de tabela já escrita em SQL (UNIQUE ou FOREIGN KEY com várias colunas). */
        public CreateTable constraint(final String sql) {
            if (sql != null && !sql.isBlank()) constraints.add(sql.trim());
            return this;
        }

        public CreateTable check(final String expression) {
            if (expression != null && !expression.isBlank()) checks.add(expression.trim());
            return this;
        }

        public String build() {
            if (columns.isEmpty()) throw new IllegalStateException("A table needs at least one column.");

            final List<ColumnMetadata> keys = columns.stream().filter(c -> c.IsPrimaryKey).toList();
            final boolean inlineKey = keys.size() == 1;

            final StringJoiner body = new StringJoiner(", ");
            for (ColumnMetadata column : columns) body.add(columnDefinition(column, inlineKey, false));
            if (keys.size() > 1) {
                final List<String> keyNames = new ArrayList<>();
                if (primaryKeyOrder != null) {
                    for (String key : primaryKeyOrder) {
                        if (keys.stream().anyMatch(c -> c.Name.equals(key))) keyNames.add(key);
                    }
                }
                for (ColumnMetadata key : keys) if (!keyNames.contains(key.Name)) keyNames.add(key.Name);
                body.add("PRIMARY KEY (" + names(keyNames) + ")");
            }
            for (ColumnMetadata column : columns) {
                if (isForeign(column)) body.add(foreignKeyConstraint(table, column));
            }
            constraints.forEach(body::add);
            for (String check : checks) body.add("CHECK (" + check + ")");

            final StringJoiner sql = new StringJoiner(" ");
            sql.add("CREATE");
            if (temporary && dialect != SQLTypes.MS_ACCESS) sql.add("TEMPORARY");
            sql.add("TABLE");
            if (ifNotExists && dialect != SQLTypes.MS_ACCESS) sql.add("IF NOT EXISTS");
            sql.add(name(table));
            sql.add("(" + body + ")");

            if (dialect == SQLTypes.SQLITE) {
                final List<String> options = new ArrayList<>();
                // WITHOUT ROWID sem chave primária é recusado pelo SQLite.
                if (withoutRowId && !keys.isEmpty()) options.add("WITHOUT ROWID");
                if (strict) options.add("STRICT");
                if (!options.isEmpty()) sql.add(String.join(", ", options));
            }
            return sql.toString();
        }
    }

    /**
     * ALTER TABLE. Cada operação acrescenta os comandos que o dialeto precisa; {@link #build()}
     * devolve-os pela ordem em que têm de correr.
     *
     * <p>No MySQL as cláusulas juntam-se num único ALTER TABLE, porque o MySQL valida o
     * estado final (uma coluna AUTO_INCREMENT tem de ser chave, e isso só é verdade depois
     * do ADD PRIMARY KEY da mesma instrução). As chaves estrangeiras ficam de fora: saem
     * antes (DROP) e depois (ADD), porque o MySQL não deixa mudar uma coluna que as tenha.</p>
     */
    public final class AlterTable {
        private final String table;
        private final List<String> before = new ArrayList<>();
        private final List<String> statements = new ArrayList<>();
        private final List<String> clauses = new ArrayList<>();
        private final List<String> after = new ArrayList<>();

        private AlterTable(final String table) {
            this.table = table;
        }

        private String prefix() {
            return "ALTER TABLE " + name(table) + " ";
        }

        /** No MySQL junta-se ao ALTER TABLE único; nos outros é um comando à parte. */
        private void clause(final String clause) {
            if (dialect == SQLTypes.MYSQL) clauses.add(clause);
            else statements.add(prefix() + clause);
        }

        public AlterTable renameTo(final String newName) {
            statements.add(dialect == SQLTypes.MYSQL
                    ? "RENAME TABLE " + name(table) + " TO " + name(newName)
                    : prefix() + "RENAME TO " + name(newName));
            return this;
        }

        /**
         * ADD COLUMN. No SQLite o chamador tem de garantir que a coluna cabe nas regras do
         * ALTER TABLE de lá (sem PRIMARY KEY nem UNIQUE); se não couber, reconstrói a tabela.
         */
        public AlterTable addColumn(final ColumnMetadata column, final boolean inlinePrimaryKey) {
            switch (dialect) {
                case MYSQL -> {
                    clauses.add("ADD COLUMN " + columnDefinition(column, inlinePrimaryKey, false));
                    if (isForeign(column)) after.add(prefix() + "ADD " + foreignKeyConstraint(table, column));
                }
                case POSTGRESQL -> statements.add(prefix() + "ADD COLUMN " + columnDefinition(column, inlinePrimaryKey, true));
                case SQLITE -> statements.add(prefix() + "ADD COLUMN " + columnDefinition(column, false, true));
                default -> statements.add(prefix() + "ADD COLUMN " + columnDefinition(column, false, false));
            }
            return this;
        }

        public AlterTable dropColumn(final String column) {
            clause("DROP COLUMN " + name(column));
            return this;
        }

        public AlterTable renameColumn(final String oldName, final String newName) {
            if (dialect == SQLTypes.MYSQL) clauses.add("RENAME COLUMN " + name(oldName) + " TO " + name(newName));
            else statements.add(prefix() + "RENAME COLUMN " + name(oldName) + " TO " + name(newName));
            return this;
        }

        /** MySQL: redefine a coluna inteira (nome, tipo, NULL, DEFAULT, AUTO_INCREMENT, COMMENT). */
        public AlterTable redefineColumn(final String currentName, final ColumnMetadata column) {
            if (dialect != SQLTypes.MYSQL) throw new UnsupportedOperationException("CHANGE COLUMN only exists in MySQL.");
            clauses.add("CHANGE COLUMN " + name(currentName) + " " + definition(column, false, false, false));
            return this;
        }

        /** PostgreSQL: {@code ALTER COLUMN c TYPE t USING c::t}; Access: {@code ALTER COLUMN c t}. */
        public AlterTable changeType(final ColumnMetadata column) {
            final String type = alterableType(column);
            if (dialect == SQLTypes.POSTGRESQL) {
                statements.add(prefix() + "ALTER COLUMN " + name(column.Name) + " TYPE " + type
                        + " USING " + name(column.Name) + "::" + type);
            } else if (dialect == SQLTypes.MS_ACCESS) {
                statements.add(prefix() + "ALTER COLUMN " + name(column.Name) + " " + type + (column.NOT_NULL ? " NOT NULL" : ""));
            } else {
                throw new UnsupportedOperationException(dialect + " changes types by redefining the column.");
            }
            return this;
        }

        public AlterTable setNotNull(final String column, final boolean notNull) {
            clause("ALTER COLUMN " + name(column) + (notNull ? " SET NOT NULL" : " DROP NOT NULL"));
            return this;
        }

        /** Valor vazio tira o DEFAULT. */
        public AlterTable setDefault(final String column, final String value) {
            final String rendered = defaultValue(value);
            clause("ALTER COLUMN " + name(column) + (rendered == null ? " DROP DEFAULT" : " SET DEFAULT " + rendered));
            return this;
        }

        public AlterTable addPrimaryKey(final List<String> columns) {
            clause("ADD PRIMARY KEY (" + names(columns) + ")");
            return this;
        }

        /** MySQL: DROP PRIMARY KEY; PostgreSQL: DROP CONSTRAINT com o nome que o catálogo deu. */
        public AlterTable dropPrimaryKey(final String constraintName) {
            if (dialect == SQLTypes.MYSQL) clauses.add("DROP PRIMARY KEY");
            else statements.add(prefix() + "DROP CONSTRAINT " + name(constraintName));
            return this;
        }

        public AlterTable addUnique(final String constraintName, final String column) {
            clause("ADD CONSTRAINT " + name(constraintName) + " UNIQUE (" + name(column) + ")");
            return this;
        }

        /**
         * No MySQL um UNIQUE é um índice; no PostgreSQL pode ser uma restrição ou só um
         * índice único, por isso tenta-se as duas formas com IF EXISTS.
         */
        public AlterTable dropUnique(final String constraintName) {
            if (dialect == SQLTypes.MYSQL) {
                clauses.add("DROP INDEX " + name(constraintName));
            } else {
                statements.add(prefix() + "DROP CONSTRAINT IF EXISTS " + name(constraintName));
                statements.add("DROP INDEX IF EXISTS " + name(constraintName));
            }
            return this;
        }

        public AlterTable addForeignKey(final ColumnMetadata column) {
            final String sql = prefix() + "ADD " + foreignKeyConstraint(table, column);
            if (dialect == SQLTypes.MYSQL) after.add(sql);
            else statements.add(sql);
            return this;
        }

        public AlterTable dropForeignKey(final String constraintName) {
            if (dialect == SQLTypes.MYSQL) before.add(prefix() + "DROP FOREIGN KEY " + name(constraintName));
            else statements.add(prefix() + "DROP CONSTRAINT " + name(constraintName));
            return this;
        }

        public AlterTable addCheck(final String constraintName, final String expression) {
            clause("ADD CONSTRAINT " + name(constraintName) + " CHECK (" + expression.trim() + ")");
            return this;
        }

        public AlterTable dropCheck(final String constraintName) {
            if (dialect == SQLTypes.MYSQL) clauses.add("DROP CHECK " + name(constraintName));
            else statements.add(prefix() + "DROP CONSTRAINT IF EXISTS " + name(constraintName));
            return this;
        }

        /** Só o PostgreSQL tem COMMENT ON; no MySQL o comentário vai na definição da coluna. */
        public AlterTable comment(final String column, final String comment) {
            if (dialect == SQLTypes.POSTGRESQL) {
                statements.add("COMMENT ON COLUMN " + name(table) + "." + name(column) + " IS "
                        + (comment == null || comment.isBlank() ? "NULL" : literal(comment)));
            }
            return this;
        }

        /** Comando solto que tem de correr nesta sequência (um setval, por exemplo). */
        public AlterTable raw(final String sql) {
            statements.add(sql);
            return this;
        }

        public List<String> build() {
            final List<String> all = new ArrayList<>(before);
            all.addAll(statements);
            if (!clauses.isEmpty()) all.add(prefix() + String.join(", ", clauses));
            all.addAll(after);
            return all;
        }
    }

    /**
     * O que a coluna tem hoje na base de dados e que só o catálogo sabe: os nomes que o motor
     * deu às restrições e as colunas da chave primária. Quem lê isto é o driver.
     */
    public record Constraints(String primaryKey, List<String> primaryKeyColumns,
                              String unique, String foreignKey, String check) {
        public Constraints {
            primaryKeyColumns = primaryKeyColumns == null ? List.of() : List.copyOf(primaryKeyColumns);
        }
    }

    /**
     * Comandos que levam uma coluna de {@code before} (o que está na base de dados) a
     * {@code after} (o que o formulário pede), no MySQL, PostgreSQL e Access.
     *
     * <p>O SQLite não passa por aqui: tirando o nome, qualquer mudança obriga a reconstruir a
     * tabela, e isso é feito pelo próprio driver. Os índices também não — são objetos à parte
     * e o driver trata deles com CREATE/DROP INDEX.</p>
     */
    public List<String> alterColumn(final String table, final ColumnMetadata before, final ColumnMetadata after,
                                    final Constraints current) {
        final EnumSet<ColumnChange> changes = ColumnChange.between(before, after);
        changes.remove(ColumnChange.INDEX);
        if (changes.isEmpty()) return List.of();

        final AlterTable alter = alterTable(table);
        switch (dialect) {
            case MYSQL -> planMySql(table, before, after, current, changes, alter);
            case POSTGRESQL -> planPostgres(table, before, after, current, changes, alter);
            case MS_ACCESS -> planAccess(before, after, changes, alter);
            default -> throw new UnsupportedOperationException("SQLite rebuilds the table to change a column.");
        }
        return alter.build();
    }

    /** Chave primária depois da mudança, mantendo a ordem das colunas que já lá estavam. */
    private static List<String> primaryKeyAfter(final ColumnMetadata before, final ColumnMetadata after,
                                                final Constraints current) {
        final List<String> keys = new ArrayList<>();
        for (String key : current.primaryKeyColumns()) {
            if (key.equals(before.Name)) {
                if (after.IsPrimaryKey) keys.add(after.Name);
            } else {
                keys.add(key);
            }
        }
        if (after.IsPrimaryKey && !keys.contains(after.Name)) keys.add(after.Name);
        return keys;
    }

    private void planMySql(final String table, final ColumnMetadata before, final ColumnMetadata after,
                           final Constraints current, final EnumSet<ColumnChange> changes, final AlterTable alter) {
        if (changes.contains(ColumnChange.FOREIGN_KEY) && isForeign(before) && current.foreignKey() != null) {
            alter.dropForeignKey(current.foreignKey());
        }
        if (changes.contains(ColumnChange.CHECK) && before.check != null && !before.check.isBlank() && current.check() != null) {
            alter.dropCheck(current.check());
        }
        if (changes.contains(ColumnChange.UNIQUE) && before.isUnique && current.unique() != null) {
            alter.dropUnique(current.unique());
        }

        // Só o nome: RENAME COLUMN não precisa da definição, por isso não arrisca mudar mais nada.
        final boolean redefine = changes.stream().anyMatch(ColumnChange.DEFINITION::contains);
        if (redefine) alter.redefineColumn(before.Name, after);
        else if (changes.contains(ColumnChange.NAME)) alter.renameColumn(before.Name, after.Name);

        if (changes.contains(ColumnChange.PRIMARY_KEY)) {
            if (!current.primaryKeyColumns().isEmpty()) alter.dropPrimaryKey(current.primaryKey());
            final List<String> keys = primaryKeyAfter(before, after, current);
            if (!keys.isEmpty()) alter.addPrimaryKey(keys);
        }
        if (changes.contains(ColumnChange.UNIQUE) && after.isUnique && !after.IsPrimaryKey) {
            alter.addUnique(constraintName("uq", table, after.Name), after.Name);
        }
        if (changes.contains(ColumnChange.CHECK) && after.check != null && !after.check.isBlank()) {
            alter.addCheck(constraintName("chk", table, after.Name), after.check);
        }
        if (changes.contains(ColumnChange.FOREIGN_KEY) && isForeign(after)) {
            alter.addForeignKey(after);
        }
    }

    private void planPostgres(final String table, final ColumnMetadata before, final ColumnMetadata after,
                              final Constraints current, final EnumSet<ColumnChange> changes, final AlterTable alter) {
        final String column = after.Name;

        // Primeiro sai o que depende da coluna antiga...
        if (changes.contains(ColumnChange.FOREIGN_KEY) && isForeign(before) && current.foreignKey() != null) {
            alter.dropForeignKey(current.foreignKey());
        }
        if (changes.contains(ColumnChange.CHECK) && before.check != null && !before.check.isBlank()) {
            alter.dropCheck(current.check() != null ? current.check() : constraintName("chk", table, before.Name));
        }
        if (changes.contains(ColumnChange.UNIQUE) && before.isUnique && current.unique() != null) {
            alter.dropUnique(current.unique());
        }
        if (changes.contains(ColumnChange.PRIMARY_KEY) && !current.primaryKeyColumns().isEmpty() && current.primaryKey() != null) {
            alter.dropPrimaryKey(current.primaryKey());
        }

        // ...depois muda-se a coluna...
        if (changes.contains(ColumnChange.NAME)) alter.renameColumn(before.Name, column);
        if (changes.contains(ColumnChange.TYPE)) alter.changeType(after);

        final boolean identityAfter = after.autoincrement >= 1;
        if (changes.contains(ColumnChange.AUTOINCREMENT)) {
            if (identityAfter) {
                // IDENTITY exige NOT NULL e nenhum DEFAULT; e a sequência tem de começar depois
                // do maior valor que já existe, senão o próximo INSERT choca com uma linha antiga.
                alter.setDefault(column, null);
                alter.setNotNull(column, true);
                alter.clause("ALTER COLUMN " + name(column) + " ADD GENERATED BY DEFAULT AS IDENTITY");
                alter.raw("SELECT setval(pg_get_serial_sequence(" + literal(quote(table)) + ", " + literal(column) + "), "
                        + "COALESCE((SELECT MAX(" + name(column) + ") FROM " + name(table) + "), 0) + 1, false)");
            } else {
                alter.clause("ALTER COLUMN " + name(column) + " DROP IDENTITY IF EXISTS");
                // Uma coluna SERIAL não é IDENTITY: o que a preenche é o DEFAULT nextval(...).
                if (before.defaultValue != null && before.defaultValue.contains("nextval(")
                        && sameText(before.defaultValue, after.defaultValue)) {
                    alter.setDefault(column, null);
                }
            }
        }
        if (changes.contains(ColumnChange.NOT_NULL) && !(identityAfter && changes.contains(ColumnChange.AUTOINCREMENT))) {
            alter.setNotNull(column, after.NOT_NULL);
        }
        if (changes.contains(ColumnChange.DEFAULT) && !identityAfter) alter.setDefault(column, after.defaultValue);

        // ...e por fim volta o que depende da coluna nova.
        if (changes.contains(ColumnChange.PRIMARY_KEY)) {
            final List<String> keys = primaryKeyAfter(before, after, current);
            if (!keys.isEmpty()) alter.addPrimaryKey(keys);
        }
        if (changes.contains(ColumnChange.UNIQUE) && after.isUnique && !after.IsPrimaryKey) {
            alter.addUnique(constraintName("uq", table, column), column);
        }
        if (changes.contains(ColumnChange.FOREIGN_KEY) && isForeign(after)) alter.addForeignKey(after);
        if (changes.contains(ColumnChange.CHECK) && after.check != null && !after.check.isBlank()) {
            alter.addCheck(constraintName("chk", table, column), after.check);
        }
        if (changes.contains(ColumnChange.COMMENT)) alter.comment(column, after.comment);
    }

    private void planAccess(final ColumnMetadata before, final ColumnMetadata after,
                            final EnumSet<ColumnChange> changes, final AlterTable alter) {
        final EnumSet<ColumnChange> supported = EnumSet.of(ColumnChange.NAME, ColumnChange.TYPE,
                ColumnChange.NOT_NULL, ColumnChange.DEFAULT, ColumnChange.COMMENT);
        final EnumSet<ColumnChange> unsupported = EnumSet.copyOf(changes);
        unsupported.removeAll(supported);
        if (!unsupported.isEmpty()) {
            throw new UnsupportedOperationException("MS Access cannot change " + unsupported + " of an existing column.");
        }
        if (changes.contains(ColumnChange.NAME)) alter.renameColumn(before.Name, after.Name);
        if (changes.contains(ColumnChange.TYPE) || changes.contains(ColumnChange.NOT_NULL)) alter.changeType(after);
        if (changes.contains(ColumnChange.DEFAULT)) alter.setDefault(after.Name, after.defaultValue);
    }

    // =====================================================================================
    // Auxiliares
    // =====================================================================================

    /** "VARCHAR(50)" → "VARCHAR"; "int unsigned" → "INT UNSIGNED". */
    static String baseType(final String type) {
        if (type == null) return "";
        final int parenthesis = type.indexOf('(');
        return (parenthesis >= 0 ? type.substring(0, parenthesis) : type).trim().toUpperCase(Locale.ROOT);
    }

    private static String upper(final String text) {
        return text == null ? "" : text.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean sameText(final String a, final String b) {
        return (a == null ? "" : a.trim()).equals(b == null ? "" : b.trim());
    }

    private static boolean declaredAutoIncrement(final ColumnMetadata column) {
        return column.autoincrement >= 1;
    }
}
