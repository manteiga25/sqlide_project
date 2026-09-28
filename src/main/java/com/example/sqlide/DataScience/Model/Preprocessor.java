package com.example.sqlide.DataScience.Model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * Pré-processamento aplicado às features antes do treino.
 *
 * <p>Os passos são ajustados uma vez, no primeiro lote, e a partir daí só aplicados: é o
 * que garante que os lotes seguintes, o conjunto de teste e as previsões manuais passam
 * exatamente pela mesma transformação. Ajustar de novo a cada lote mudaria a escala a meio
 * do treino e o modelo deixaria de fazer sentido.</p>
 *
 * <p>As estatísticas são calculadas aqui em vez de se usarem os {@code Transform} do Smile
 * porque estes trabalham sobre {@code DataFrame} e o pipeline precisa de transformar também
 * um único vetor, na altura de prever.</p>
 */
public class Preprocessor {

    public enum Step {
        NONE("None"),
        STANDARDIZE("Standardize (z-score)"),
        MIN_MAX("Min-max scale to [0, 1]"),
        MAX_ABS("Max-abs scale to [-1, 1]"),
        ROBUST("Robust scale (median and IQR)"),
        WINSOR("Winsorize (clip at 5th and 95th percentile)"),
        L2_NORMALIZE("L2 normalize each row");

        private final String label;

        Step(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final List<Step> steps = new ArrayList<>();

    /** Estatísticas por coluna, uma entrada por passo ajustado. */
    private final List<double[][]> parameters = new ArrayList<>();

    private boolean fitted = false;
    private int features = 0;

    public void setSteps(final List<Step> steps) {
        this.steps.clear();
        if (steps != null) {
            for (Step step : steps) if (step != null && step != Step.NONE) this.steps.add(step);
        }
        reset();
    }

    public List<Step> getSteps() {
        return List.copyOf(steps);
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }

    public boolean isFitted() {
        return fitted;
    }

    /** Esquece o que foi ajustado, para um treino novo recomeçar limpo. */
    public void reset() {
        parameters.clear();
        fitted = false;
        features = 0;
    }

    /**
     * Ajusta os passos ao conjunto de treino.
     *
     * <p>Só deve ser chamado uma vez por treino, com o primeiro lote.</p>
     */
    public void fit(final double[][] x) {
        reset();
        if (x == null || x.length == 0) return;

        features = x[0].length;
        double[][] working = copy(x);

        for (Step step : steps) {
            final double[][] stats = computeParameters(step, working);
            parameters.add(stats);
            // O passo seguinte ajusta-se sobre o resultado do anterior, não sobre o original.
            working = applyStep(step, stats, working);
        }

        fitted = true;
    }

    /** Aplica os passos já ajustados a uma matriz. */
    public double[][] transform(final double[][] x) {
        if (!fitted || x == null || x.length == 0) return x;

        double[][] working = copy(x);
        for (int i = 0; i < steps.size(); i++) {
            working = applyStep(steps.get(i), parameters.get(i), working);
        }
        return working;
    }

    /** Aplica os passos a uma única observação, na altura de prever. */
    public double[] transform(final double[] row) {
        if (!fitted || row == null) return row;
        return transform(new double[][]{row})[0];
    }

    // ==== Ajuste ====

    private double[][] computeParameters(final Step step, final double[][] x) {
        final int columns = x[0].length;

        return switch (step) {
            case STANDARDIZE -> {
                final double[] mean = new double[columns];
                final double[] deviation = new double[columns];
                for (int c = 0; c < columns; c++) {
                    double sum = 0;
                    for (double[] row : x) sum += row[c];
                    mean[c] = sum / x.length;

                    double squares = 0;
                    for (double[] row : x) squares += (row[c] - mean[c]) * (row[c] - mean[c]);
                    // Coluna constante: divide-se por 1 para não produzir infinitos.
                    final double variance = x.length > 1 ? squares / (x.length - 1) : 0;
                    deviation[c] = variance > 0 ? Math.sqrt(variance) : 1;
                }
                yield new double[][]{mean, deviation};
            }

            case MIN_MAX -> {
                final double[] min = new double[columns];
                final double[] range = new double[columns];
                for (int c = 0; c < columns; c++) {
                    double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                    for (double[] row : x) {
                        lo = Math.min(lo, row[c]);
                        hi = Math.max(hi, row[c]);
                    }
                    min[c] = lo;
                    range[c] = hi > lo ? hi - lo : 1;
                }
                yield new double[][]{min, range};
            }

            case MAX_ABS -> {
                final double[] scale = new double[columns];
                for (int c = 0; c < columns; c++) {
                    double largest = 0;
                    for (double[] row : x) largest = Math.max(largest, Math.abs(row[c]));
                    scale[c] = largest > 0 ? largest : 1;
                }
                yield new double[][]{scale};
            }

            case ROBUST -> {
                final double[] median = new double[columns];
                final double[] spread = new double[columns];
                for (int c = 0; c < columns; c++) {
                    final double[] column = columnOf(x, c);
                    Arrays.sort(column);
                    median[c] = quantile(column, 0.50);
                    final double iqr = quantile(column, 0.75) - quantile(column, 0.25);
                    spread[c] = iqr > 0 ? iqr : 1;
                }
                yield new double[][]{median, spread};
            }

            case WINSOR -> {
                final double[] low = new double[columns];
                final double[] high = new double[columns];
                for (int c = 0; c < columns; c++) {
                    final double[] column = columnOf(x, c);
                    Arrays.sort(column);
                    low[c] = quantile(column, 0.05);
                    high[c] = quantile(column, 0.95);
                }
                yield new double[][]{low, high};
            }

            // A normalização L2 é por linha: não depende do conjunto de treino.
            case L2_NORMALIZE, NONE -> new double[0][];
        };
    }

    private double[][] applyStep(final Step step, final double[][] stats, final double[][] x) {
        final double[][] out = copy(x);

        switch (step) {
            case STANDARDIZE -> {
                for (double[] row : out) {
                    for (int c = 0; c < row.length && c < stats[0].length; c++) {
                        row[c] = (row[c] - stats[0][c]) / stats[1][c];
                    }
                }
            }
            case MIN_MAX -> {
                for (double[] row : out) {
                    for (int c = 0; c < row.length && c < stats[0].length; c++) {
                        row[c] = (row[c] - stats[0][c]) / stats[1][c];
                    }
                }
            }
            case MAX_ABS -> {
                for (double[] row : out) {
                    for (int c = 0; c < row.length && c < stats[0].length; c++) {
                        row[c] = row[c] / stats[0][c];
                    }
                }
            }
            case ROBUST -> {
                for (double[] row : out) {
                    for (int c = 0; c < row.length && c < stats[0].length; c++) {
                        row[c] = (row[c] - stats[0][c]) / stats[1][c];
                    }
                }
            }
            case WINSOR -> {
                for (double[] row : out) {
                    for (int c = 0; c < row.length && c < stats[0].length; c++) {
                        row[c] = Math.min(Math.max(row[c], stats[0][c]), stats[1][c]);
                    }
                }
            }
            case L2_NORMALIZE -> {
                for (double[] row : out) {
                    double norm = 0;
                    for (double value : row) norm += value * value;
                    norm = Math.sqrt(norm);
                    if (norm > 0) for (int c = 0; c < row.length; c++) row[c] /= norm;
                }
            }
            case NONE -> {
            }
        }

        return out;
    }

    // ==== Auxiliares ====

    private static double[][] copy(final double[][] x) {
        final double[][] out = new double[x.length][];
        for (int i = 0; i < x.length; i++) out[i] = x[i].clone();
        return out;
    }

    private static double[] columnOf(final double[][] x, final int column) {
        final double[] values = new double[x.length];
        for (int i = 0; i < x.length; i++) values[i] = x[i][column];
        return values;
    }

    /** Quantil por interpolação linear sobre o vetor já ordenado. */
    private static double quantile(final double[] sorted, final double fraction) {
        if (sorted.length == 0) return 0;
        if (sorted.length == 1) return sorted[0];

        final double position = fraction * (sorted.length - 1);
        final int lower = (int) Math.floor(position);
        final int upper = (int) Math.ceil(position);
        if (lower == upper) return sorted[lower];

        final double weight = position - lower;
        return sorted[lower] * (1 - weight) + sorted[upper] * weight;
    }

    /** Descrição do que foi ajustado, para o registo de resultados. */
    public LinkedHashMap<String, Object> describe(final List<String> featureNames) {
        final LinkedHashMap<String, Object> description = new LinkedHashMap<>();
        if (steps.isEmpty()) {
            description.put("Preprocessing", "none");
            return description;
        }

        description.put("Preprocessing", steps.size() + " step(s)");
        for (int i = 0; i < steps.size(); i++) {
            final Step step = steps.get(i);
            final double[][] stats = i < parameters.size() ? parameters.get(i) : new double[0][];
            description.put("  " + (i + 1) + ". " + step, summarise(step, stats, featureNames));
        }
        return description;
    }

    private static String summarise(final Step step, final double[][] stats, final List<String> names) {
        if (stats.length == 0) return "per row, no fitted parameters";

        final StringBuilder text = new StringBuilder();
        final int columns = stats[0].length;
        for (int c = 0; c < columns && c < 6; c++) {
            if (c > 0) text.append("; ");
            text.append(c < names.size() ? names.get(c) : "x" + c).append('=');
            text.append(switch (step) {
                case STANDARDIZE -> String.format(Locale.US, "mean %.4g, sd %.4g", stats[0][c], stats[1][c]);
                case MIN_MAX -> String.format(Locale.US, "min %.4g, range %.4g", stats[0][c], stats[1][c]);
                case MAX_ABS -> String.format(Locale.US, "max|x| %.4g", stats[0][c]);
                case ROBUST -> String.format(Locale.US, "median %.4g, IQR %.4g", stats[0][c], stats[1][c]);
                case WINSOR -> String.format(Locale.US, "[%.4g, %.4g]", stats[0][c], stats[1][c]);
                default -> "";
            });
        }
        if (columns > 6) text.append("; ...");
        return text.toString();
    }

}
