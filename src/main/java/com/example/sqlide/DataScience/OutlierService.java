package com.example.sqlide.DataScience;

import com.example.sqlide.drivers.model.Interfaces.DatabaseExecutorInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deteção e remoção de valores extremos.
 *
 * <p>A deteção calcula os limites a partir do perfil da coluna e depois conta e amostra as
 * linhas fora deles com queries — não é preciso trazer a coluna inteira. A remoção é um
 * DELETE com a mesma condição, o que faz o botão "Remove" passar a fazer alguma coisa: até
 * agora apagava índices de uma lista em memória que estava sempre vazia.</p>
 */
public class OutlierService {

    public enum Method {
        IQR("IQR (Tukey)", 1.5),
        Z_SCORE("Z-score", 3.0),
        MAD("Modified Z-score (MAD)", 3.5),
        PERCENTILE("Percentile cut", 1.0);

        private final String label;
        private final double defaultThreshold;

        Method(String label, double defaultThreshold) {
            this.label = label;
            this.defaultThreshold = defaultThreshold;
        }

        public double getDefaultThreshold() {
            return defaultThreshold;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * O que a deteção encontrou.
     *
     * @param threshold  o parâmetro usado (k do IQR, z, ou percentagem)
     * @param lowSamples primeiros valores abaixo do limite, para a tabela de pré-visualização
     */
    public record Report(String column,
                         Method method,
                         double threshold,
                         double lowerBound,
                         double upperBound,
                         long belowCount,
                         long aboveCount,
                         List<Double> lowSamples,
                         List<Double> highSamples,
                         String summary) {

        public long total() {
            return belowCount + aboveCount;
        }

        public boolean hasBounds() {
            return !Double.isNaN(lowerBound) && !Double.isNaN(upperBound);
        }
    }

    /** Quantos valores extremos se trazem para mostrar na tabela. */
    private static final int SAMPLE_LIMIT = 500;

    private final DatabaseFetcherInterface fetcher;
    private final DatabaseExecutorInterface executor;
    private final StatisticsService statistics;
    private final String table;

    public OutlierService(DatabaseFetcherInterface fetcher, DatabaseExecutorInterface executor,
                          StatisticsService statistics, String table) {
        this.fetcher = fetcher;
        this.executor = executor;
        this.statistics = statistics;
        this.table = table;
    }

    public Report detect(String column, Method method, double threshold) {
        if (!statistics.isNumeric(column)) {
            return empty(column, method, threshold, "Column " + column + " is not numeric.");
        }

        ColumnProfile profile = statistics.profile(column);
        double lower;
        double upper;

        switch (method) {
            case IQR -> {
                double iqr = profile.iqr();
                if (Double.isNaN(iqr)) return empty(column, method, threshold, "Could not compute quartiles.");
                lower = profile.q1() - threshold * iqr;
                upper = profile.q3() + threshold * iqr;
            }
            case Z_SCORE -> {
                if (Double.isNaN(profile.stdDev()) || profile.stdDev() == 0) {
                    return empty(column, method, threshold,
                            "Standard deviation is zero — every value is the same.");
                }
                lower = profile.mean() - threshold * profile.stdDev();
                upper = profile.mean() + threshold * profile.stdDev();
            }
            case MAD -> {
                // Mediana dos desvios absolutos: resiste a extremos que puxam a média e o desvio.
                double mad = medianAbsoluteDeviation(column, profile.median());
                if (Double.isNaN(mad) || mad == 0) {
                    return empty(column, method, threshold,
                            "Median absolute deviation is zero — cannot scale by it.");
                }
                // 0.6745 põe o MAD na mesma escala do desvio padrão numa distribuição normal.
                double scaled = mad / 0.6745;
                lower = profile.median() - threshold * scaled;
                upper = profile.median() + threshold * scaled;
            }
            case PERCENTILE -> {
                double fraction = Math.min(Math.max(threshold, 0.0), 49.0) / 100.0;
                lower = statistics.quantile(column, fraction);
                upper = statistics.quantile(column, 1.0 - fraction);
            }
            default -> {
                return empty(column, method, threshold, "Unknown method.");
            }
        }

        if (Double.isNaN(lower) || Double.isNaN(upper)) {
            return empty(column, method, threshold, "Could not compute bounds for " + column + ".");
        }

        String col = executor.quoteIdentifier(column);
        String qualified = executor.quoteIdentifier(table);

        long below = count(qualified, col + " < " + StatisticsService.format(lower));
        long above = count(qualified, col + " > " + StatisticsService.format(upper));

        List<Double> lowSamples = sample(qualified, col, col + " < " + StatisticsService.format(lower), col);
        List<Double> highSamples = sample(qualified, col, col + " > " + StatisticsService.format(upper), col + " DESC");

        String summary = String.format(Locale.US,
                "%s on %s with threshold %.4g%nBounds: [%.6g , %.6g]%n%d below, %d above — %d of %d row(s), %.2f%%.",
                method, column, threshold, lower, upper, below, above, below + above,
                profile.totalRows(),
                profile.totalRows() == 0 ? 0 : 100.0 * (below + above) / profile.totalRows());

        return new Report(column, method, threshold, lower, upper, below, above, lowSamples, highSamples, summary);
    }

    /**
     * Apaga as linhas fora dos limites do relatório.
     *
     * @return número de linhas apagadas
     */
    public int remove(Report report) throws SQLException {
        if (!report.hasBounds()) return 0;
        String col = executor.quoteIdentifier(report.column());
        String sql = "DELETE FROM " + executor.quoteIdentifier(table)
                + " WHERE " + col + " IS NOT NULL"
                + " AND (" + col + " < " + StatisticsService.format(report.lowerBound())
                + " OR " + col + " > " + StatisticsService.format(report.upperBound()) + ")";
        return executor.executeUpdate(sql);
    }

    /** Substitui os extremos pelos limites em vez de os apagar, quando perder linhas não é opção. */
    public int clamp(Report report) throws SQLException {
        if (!report.hasBounds()) return 0;
        String col = executor.quoteIdentifier(report.column());
        String qualified = executor.quoteIdentifier(table);

        int affected = executor.executeUpdate("UPDATE " + qualified
                + " SET " + col + " = " + StatisticsService.format(report.lowerBound())
                + " WHERE " + col + " < " + StatisticsService.format(report.lowerBound()));

        affected += executor.executeUpdate("UPDATE " + qualified
                + " SET " + col + " = " + StatisticsService.format(report.upperBound())
                + " WHERE " + col + " > " + StatisticsService.format(report.upperBound()));

        return affected;
    }

    // ==== Auxiliares ====

    private double medianAbsoluteDeviation(String column, double median) {
        if (Double.isNaN(median)) return Double.NaN;
        String col = executor.quoteIdentifier(column);
        // ABS existe nos três motores, por isso o desvio pode ser ordenado no servidor.
        String deviation = "ABS(" + col + " - " + StatisticsService.format(median) + ")";

        long n = countNonNull(column);
        if (n == 0) return Double.NaN;

        long offset = Math.max(0, Math.round(0.5 * (n - 1)));
        List<Double> values = fetcher.fetchDataMap("SELECT " + deviation + " AS dev"
                + " FROM " + executor.quoteIdentifier(table)
                + " WHERE " + col + " IS NOT NULL"
                + " ORDER BY dev LIMIT 1 OFFSET " + offset);

        return (values == null || values.isEmpty()) ? Double.NaN : values.getFirst();
    }

    private long countNonNull(String column) {
        return statistics.countNonNull(column);
    }

    private long count(String qualifiedTable, String condition) {
        List<Double> values = fetcher.fetchDataMap(
                "SELECT COUNT(*) FROM " + qualifiedTable + " WHERE " + condition);
        return (values == null || values.isEmpty() || values.getFirst() == null)
                ? 0 : values.getFirst().longValue();
    }

    private List<Double> sample(String qualifiedTable, String column, String condition, String order) {
        List<Double> values = fetcher.fetchDataMap("SELECT " + column
                + " FROM " + qualifiedTable
                + " WHERE " + condition
                + " ORDER BY " + order
                + " LIMIT " + SAMPLE_LIMIT);
        return values == null ? new ArrayList<>() : values;
    }

    private static Report empty(String column, Method method, double threshold, String reason) {
        return new Report(column, method, threshold, Double.NaN, Double.NaN, 0, 0,
                List.of(), List.of(), reason);
    }

}
