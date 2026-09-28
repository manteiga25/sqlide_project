package com.example.sqlide.DataScience;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.model.Interfaces.DatabaseExecutorInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Estatísticas calculadas no motor da base de dados, sobre a tabela inteira.
 *
 * <p>A ideia que já estava no projeto — empurrar os agregados para o SGBD em vez de trazer
 * a tabela toda — mantém-se, porque é o que permite trabalhar com tabelas grandes. O que
 * mudou foi <em>como</em>:</p>
 *
 * <ul>
 *   <li>Uma única passagem devolve as somas de potências (Σx, Σx², Σx³, Σx⁴) e a partir
 *       delas calcula-se em Java a variância, a assimetria e a curtose. Antes cada medida
 *       era uma query com {@code POW()} e janelas, que nem todos os motores aceitam.</li>
 *   <li>Sem {@code CAST(... AS REAL)}: multiplicar por 1.0 promove a vírgula flutuante em
 *       SQLite, MySQL e PostgreSQL por igual.</li>
 *   <li>Identificadores citados, para nomes de coluna com espaços ou palavras reservadas.</li>
 * </ul>
 */
public class StatisticsService {

    private final DatabaseFetcherInterface fetcher;
    private final DatabaseExecutorInterface executor;
    private final String table;
    private final Map<String, ColumnMetadata> columns = new LinkedHashMap<>();

    public StatisticsService(DatabaseFetcherInterface fetcher, DatabaseExecutorInterface executor,
                             String table, List<ColumnMetadata> metadata) {
        this.fetcher = fetcher;
        this.executor = executor;
        this.table = table;
        for (ColumnMetadata column : metadata) this.columns.put(column.Name, column);
    }

    public String getTable() {
        return table;
    }

    public List<String> getColumnNames() {
        return new ArrayList<>(columns.keySet());
    }

    public List<String> getNumericColumnNames() {
        return columns.values().stream().filter(c -> isNumericType(c.Type)).map(c -> c.Name).toList();
    }

    public boolean isNumeric(String column) {
        ColumnMetadata meta = columns.get(column);
        return meta != null && isNumericType(meta.Type);
    }

    public String typeOf(String column) {
        ColumnMetadata meta = columns.get(column);
        return meta == null ? "" : meta.Type;
    }

    private String quoted(String identifier) {
        return executor.quoteIdentifier(identifier);
    }

    private String qualifiedTable() {
        return quoted(table);
    }

    // ==== Contagens ====

    public long countRows() {
        return firstLong("SELECT COUNT(*) FROM " + qualifiedTable(), 0);
    }

    public long countNulls(String column) {
        return firstLong("SELECT COUNT(*) FROM " + qualifiedTable()
                + " WHERE " + quoted(column) + " IS NULL", 0);
    }

    public long countDistinct(String column) {
        return firstLong("SELECT COUNT(DISTINCT " + quoted(column) + ") FROM " + qualifiedTable(), 0);
    }

    // ==== Perfil completo de uma coluna ====

    /**
     * Recolhe todas as medidas de uma coluna. Para colunas não numéricas só faz sentido
     * contar linhas, nulos e valores distintos — o resto fica NaN.
     */
    public ColumnProfile profile(String column) {
        long totalRows = countRows();
        long nulls = countNulls(column);

        if (!isNumeric(column) || totalRows == nulls) {
            return ColumnProfile.empty(column, typeOf(column), isNumeric(column), totalRows, nulls);
        }

        String col = quoted(column);
        // Uma passagem só: contagem, extremos e as quatro primeiras somas de potências.
        String sql = "SELECT COUNT(" + col + ") AS n"
                + ", MIN(" + col + ") AS min_v"
                + ", MAX(" + col + ") AS max_v"
                + ", SUM(" + col + " * 1.0) AS s1"
                + ", SUM(" + col + " * 1.0 * " + col + ") AS s2"
                + ", SUM(" + col + " * 1.0 * " + col + " * " + col + ") AS s3"
                + ", SUM(" + col + " * 1.0 * " + col + " * " + col + " * " + col + ") AS s4"
                + " FROM " + qualifiedTable()
                + " WHERE " + col + " IS NOT NULL";

        Map<String, String> row = firstRow(sql);
        if (row == null) {
            return ColumnProfile.empty(column, typeOf(column), true, totalRows, nulls);
        }

        long n = (long) toDouble(row.get("n"), 0);
        if (n == 0) return ColumnProfile.empty(column, typeOf(column), true, totalRows, nulls);

        double min = toDouble(row.get("min_v"), Double.NaN);
        double max = toDouble(row.get("max_v"), Double.NaN);
        double s1 = toDouble(row.get("s1"), Double.NaN);
        double s2 = toDouble(row.get("s2"), Double.NaN);
        double s3 = toDouble(row.get("s3"), Double.NaN);
        double s4 = toDouble(row.get("s4"), Double.NaN);

        double mean = s1 / n;

        // Momentos centrais a partir das somas de potências.
        double m2 = s2 / n - mean * mean;
        double m3 = s3 / n - 3 * mean * s2 / n + 2 * mean * mean * mean;
        double m4 = s4 / n - 4 * mean * s3 / n + 6 * mean * mean * s2 / n - 3 * Math.pow(mean, 4);

        // Variância amostral (n-1), que é a que a tabela mostra.
        double variance = n > 1 ? (s2 - n * mean * mean) / (n - 1) : Double.NaN;
        double stdDev = Double.isNaN(variance) ? Double.NaN : Math.sqrt(variance);

        double skewness = m2 > 0 ? m3 / Math.pow(m2, 1.5) : Double.NaN;
        double kurtosis = m2 > 0 ? m4 / (m2 * m2) : Double.NaN;

        double median = quantile(column, 0.50);
        double q1 = quantile(column, 0.25);
        double q3 = quantile(column, 0.75);

        return new ColumnProfile(column, typeOf(column), true, totalRows, nulls,
                countDistinct(column), min, max, mean, median, q1, q3,
                variance, stdDev, skewness, kurtosis);
    }

    // ==== Quantis ====

    /**
     * Quantil por posição ordenada. {@code ORDER BY ... LIMIT 1 OFFSET n} é aceite pelos três
     * motores principais, ao contrário das funções de janela que a versão anterior usava.
     */
    public double quantile(String column, double fraction) {
        String col = quoted(column);
        long n = firstLong("SELECT COUNT(" + col + ") FROM " + qualifiedTable()
                + " WHERE " + col + " IS NOT NULL", 0);
        if (n == 0) return Double.NaN;

        long offset = Math.round(fraction * (n - 1));
        offset = Math.max(0, Math.min(offset, n - 1));

        List<Double> values = fetcher.fetchDataMap("SELECT " + col + " FROM " + qualifiedTable()
                + " WHERE " + col + " IS NOT NULL"
                + " ORDER BY " + col
                + " LIMIT 1 OFFSET " + offset);

        return (values == null || values.isEmpty()) ? Double.NaN : values.getFirst();
    }

    // ==== Correlação ====

    /**
     * Pearson entre duas colunas, numa passagem só. Devolve NaN se alguma das colunas
     * for constante — nesse caso o denominador é zero e o valor não está definido.
     */
    public double correlation(String xColumn, String yColumn) {
        String x = quoted(xColumn);
        String y = quoted(yColumn);

        String sql = "SELECT COUNT(*) AS n"
                + ", SUM(" + x + " * 1.0) AS sx"
                + ", SUM(" + y + " * 1.0) AS sy"
                + ", SUM(" + x + " * 1.0 * " + y + ") AS sxy"
                + ", SUM(" + x + " * 1.0 * " + x + ") AS sxx"
                + ", SUM(" + y + " * 1.0 * " + y + ") AS syy"
                + " FROM " + qualifiedTable()
                + " WHERE " + x + " IS NOT NULL AND " + y + " IS NOT NULL";

        Map<String, String> row = firstRow(sql);
        if (row == null) return Double.NaN;

        double n = toDouble(row.get("n"), 0);
        if (n < 2) return Double.NaN;

        double sx = toDouble(row.get("sx"), Double.NaN);
        double sy = toDouble(row.get("sy"), Double.NaN);
        double sxy = toDouble(row.get("sxy"), Double.NaN);
        double sxx = toDouble(row.get("sxx"), Double.NaN);
        double syy = toDouble(row.get("syy"), Double.NaN);

        double numerator = n * sxy - sx * sy;
        double denominator = Math.sqrt((n * sxx - sx * sx) * (n * syy - sy * sy));

        return denominator == 0 ? Double.NaN : numerator / denominator;
    }

    /** Matriz simétrica de correlações. Só calcula metade e espelha, porque r(x,y) = r(y,x). */
    public double[][] correlationMatrix(List<String> numericColumns) {
        int size = numericColumns.size();
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            matrix[i][i] = 1.0;
            for (int j = i + 1; j < size; j++) {
                double r = correlation(numericColumns.get(i), numericColumns.get(j));
                matrix[i][j] = r;
                matrix[j][i] = r;
            }
        }
        return matrix;
    }

    // ==== Distribuições ====

    /** Frequência de cada valor distinto, para o histograma categórico. */
    public LinkedHashMap<String, Long> valueCounts(String column, int limit) {
        String col = quoted(column);
        String sql = "SELECT " + col + " AS bucket, COUNT(*) AS freq"
                + " FROM " + qualifiedTable()
                + " WHERE " + col + " IS NOT NULL"
                + " GROUP BY " + col
                + " ORDER BY freq DESC"
                + " LIMIT " + limit;

        LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
        ArrayList<HashMap<String, String>> rows = fetcher.fetchRawDataMap(sql);
        if (rows == null) return counts;
        for (Map<String, String> row : rows) {
            counts.put(String.valueOf(row.get("bucket")), (long) toDouble(row.get("freq"), 0));
        }
        return counts;
    }

    /**
     * Histograma de intervalos calculado no motor: divide o domínio em {@code bins} classes
     * e conta cada uma. Evita trazer a coluna inteira só para a repartir em Java.
     */
    public LinkedHashMap<String, Long> histogram(String column, int bins) {
        LinkedHashMap<String, Long> histogram = new LinkedHashMap<>();
        if (bins < 1) bins = 1;

        String col = quoted(column);
        Map<String, String> bounds = firstRow("SELECT MIN(" + col + ") AS min_v, MAX(" + col + ") AS max_v"
                + " FROM " + qualifiedTable() + " WHERE " + col + " IS NOT NULL");
        if (bounds == null) return histogram;

        double min = toDouble(bounds.get("min_v"), Double.NaN);
        double max = toDouble(bounds.get("max_v"), Double.NaN);
        if (Double.isNaN(min) || Double.isNaN(max)) return histogram;

        if (min == max) {
            histogram.put(format(min), countNonNull(column));
            return histogram;
        }

        double width = (max - min) / bins;

        // Uma query por classe manteria isto simples mas lento; um CASE agrupa tudo numa só.
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append("CAST((").append(col).append(" - ").append(format(min))
                .append(") / ").append(format(width)).append(" AS INTEGER) AS bin, COUNT(*) AS freq")
                .append(" FROM ").append(qualifiedTable())
                .append(" WHERE ").append(col).append(" IS NOT NULL")
                .append(" GROUP BY bin ORDER BY bin");

        long[] frequencies = new long[bins];
        ArrayList<HashMap<String, String>> rows = fetcher.fetchRawDataMap(sql.toString());
        if (rows == null) return histogram;

        for (Map<String, String> row : rows) {
            int bin = (int) toDouble(row.get("bin"), -1);
            // O valor máximo cai na classe bins, que pertence à última.
            if (bin >= bins) bin = bins - 1;
            if (bin < 0) continue;
            frequencies[bin] += (long) toDouble(row.get("freq"), 0);
        }

        for (int i = 0; i < bins; i++) {
            double start = min + i * width;
            double end = start + width;
            histogram.put(String.format(Locale.US, "%.3g – %.3g", start, end), frequencies[i]);
        }
        return histogram;
    }

    public long countNonNull(String column) {
        return firstLong("SELECT COUNT(" + quoted(column) + ") FROM " + qualifiedTable(), 0);
    }

    // ==== Auxiliares ====

    /**
     * O fetcher devolve {@code null} quando a query não traz linhas, por isso todas as
     * leituras passam por aqui em vez de chamarem {@code getFirst()} às cegas.
     */
    private Map<String, String> firstRow(String sql) {
        ArrayList<HashMap<String, String>> rows = fetcher.fetchRawDataMap(sql);
        return (rows == null || rows.isEmpty()) ? null : rows.getFirst();
    }

    private long firstLong(String sql, long fallback) {
        List<Double> values = fetcher.fetchDataMap(sql);
        if (values == null || values.isEmpty() || values.getFirst() == null) return fallback;
        return values.getFirst().longValue();
    }

    static double toDouble(String value, double fallback) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return fallback;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    static String format(double value) {
        return String.format(Locale.US, "%.10f", value);
    }

    /** Tipos que dão para tratar como número, independentemente do motor. */
    public static boolean isNumericType(String sqlType) {
        if (sqlType == null) return false;
        String type = sqlType.toUpperCase(Locale.ROOT).trim();
        int parenthesis = type.indexOf('(');
        if (parenthesis > 0) type = type.substring(0, parenthesis).trim();
        return switch (type) {
            case "INTEGER", "INT", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT",
                 "NUMERIC", "DECIMAL", "DEC", "FIXED",
                 "DOUBLE", "DOUBLE PRECISION", "FLOAT", "REAL",
                 "SERIAL", "BIGSERIAL", "SMALLSERIAL",
                 "MONEY", "COUNTER", "BYTE", "LONG", "SINGLE", "CURRENCY" -> true;
            default -> false;
        };
    }

}
