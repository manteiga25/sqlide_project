package com.example.sqlide.DataScience;

import com.example.sqlide.DataScience.Model.ModelPipeline;
import com.example.sqlide.DataScience.Model.TrainingResult;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.model.Interfaces.DatabaseExecutorInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;
import com.example.sqlide.drivers.model.SQLTypes;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Preenchimento de valores em falta.
 *
 * <p>Funciona em dois tempos, como a UI sempre sugeriu mas não fazia: {@link #preview}
 * calcula o que seria escrito e devolve um {@link Plan} para o utilizador inspecionar, e
 * {@link #apply} escreve esse plano na base de dados. Antes o "executar" só mexia numa
 * lista em memória que nunca chegava a ser gravada.</p>
 *
 * <p>Quando o valor é o mesmo para todas as linhas (média, mediana, moda, constante) a
 * escrita é um único UPDATE. Só os métodos que dependem das outras colunas de cada linha
 * precisam de escrita linha a linha — e essa exige que a tabela seja endereçável.</p>
 */
public class ImputationService {

    public enum Method {
        MEAN("Mean"),
        MEDIAN("Median"),
        MODE("Most frequent"),
        CONSTANT("Fixed value"),
        LINEAR_REGRESSION("Linear regression on other columns"),
        KNN("K nearest neighbours"),
        TRAINED_MODEL("Model trained in the Modeling tab");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        /** True se produz o mesmo valor para todas as linhas em falta. */
        public boolean isConstantFill() {
            return this == MEAN || this == MEDIAN || this == MODE || this == CONSTANT;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * O que a imputação vai escrever.
     *
     * @param constantValue valor único, ou null se cada linha leva o seu
     * @param perRow        pares chave-da-linha → valor, para os métodos baseados em modelo
     */
    public record Plan(String column,
                       Method method,
                       String constantValue,
                       List<RowFill> perRow,
                       long missingCount,
                       String summary) {

        public int affectedRows() {
            return constantValue != null ? (int) missingCount : perRow.size();
        }
    }

    public record RowFill(Map<String, String> key, double value) {
    }

    private final DatabaseFetcherInterface fetcher;
    private final DatabaseExecutorInterface executor;
    private final StatisticsService statistics;
    private final SQLTypes dialect;
    private final String table;
    private final List<ColumnMetadata> metadata;

    /** Quantas linhas em falta se carregam para memória nos métodos baseados em modelo. */
    private static final int MODEL_SAMPLE_LIMIT = 20_000;

    public ImputationService(DatabaseFetcherInterface fetcher,
                             DatabaseExecutorInterface executor,
                             StatisticsService statistics,
                             SQLTypes dialect,
                             String table,
                             List<ColumnMetadata> metadata) {
        this.fetcher = fetcher;
        this.executor = executor;
        this.statistics = statistics;
        this.dialect = dialect;
        this.table = table;
        this.metadata = metadata;
    }

    // ==== Pré-visualização ====

    public Plan preview(String column, Method method, List<String> predictors, int k, String constant) {
        return preview(column, method, predictors, k, constant, null);
    }

    /**
     * @param trainedModel pipeline já treinado, usado apenas pelo método
     *                     {@link Method#TRAINED_MODEL}
     */
    public Plan preview(String column, Method method, List<String> predictors, int k,
                        String constant, ModelPipeline trainedModel) {
        long missing = statistics.countNulls(column);
        if (missing == 0) {
            return new Plan(column, method, null, List.of(), 0,
                    "Nothing to fill: " + column + " has no nulls.");
        }

        return switch (method) {
            case MEAN -> constantPlan(column, method, statistics.profile(column).mean(), missing, "mean");
            case MEDIAN -> constantPlan(column, method, statistics.quantile(column, 0.50), missing, "median");
            case MODE -> modePlan(column, missing);
            case CONSTANT -> {
                if (constant == null || constant.isBlank()) {
                    yield new Plan(column, method, null, List.of(), missing, "Type the value to write.");
                }
                yield new Plan(column, method, constant, List.of(), missing,
                        String.format("Fill %d null(s) in %s with '%s'.", missing, column, constant));
            }
            case LINEAR_REGRESSION -> regressionPlan(column, predictors, missing);
            case KNN -> knnPlan(column, predictors, k, missing);
            case TRAINED_MODEL -> trainedModelPlan(column, trainedModel, missing);
        };
    }

    /**
     * Preenche os nulos com as previsões do modelo treinado no separador de modelação.
     *
     * <p>É o que a etiqueta "Or use model on modeling stage" prometia desde o início e
     * nunca esteve ligada a nada. O modelo tem de ter sido treinado a prever exatamente
     * esta coluna, senão estaria a escrever previsões de outra coisa.</p>
     */
    private Plan trainedModelPlan(String column, ModelPipeline model, long missing) {
        if (model == null || model.getModel() == null) {
            return new Plan(column, Method.TRAINED_MODEL, null, List.of(), missing,
                    "Train a model in the Modeling tab first.");
        }

        if (!column.equals(model.getTarget())) {
            return new Plan(column, Method.TRAINED_MODEL, null, List.of(), missing,
                    "The trained model predicts " + model.getTarget() + ", not " + column
                            + ". Train it with " + column + " as the target.");
        }

        final List<String> features = model.getFeatures();
        if (features.isEmpty()) {
            return new Plan(column, Method.TRAINED_MODEL, null, List.of(), missing,
                    "The trained model has no feature columns.");
        }

        final DatasetSample sample = loadSample(features, column, MODEL_SAMPLE_LIMIT);
        if (!sample.isWritable()) return notAddressable(column, Method.TRAINED_MODEL, missing);

        final List<RowFill> fills = new ArrayList<>();
        int skipped = 0;

        for (int i = 0; i < sample.size(); i++) {
            final double[] values = new double[features.size()];
            boolean complete = true;
            for (int f = 0; f < features.size(); f++) {
                final Double value = parse(sample.valueAt(i, features.get(f)));
                if (value == null) {
                    complete = false;
                    break;
                }
                values[f] = value;
            }
            if (!complete) {
                skipped++;
                continue;
            }

            // predictOne aplica o mesmo pré-processamento que o treino usou.
            final double prediction = model.predictOne(values);
            if (Double.isNaN(prediction)) {
                skipped++;
                continue;
            }
            fills.add(new RowFill(sample.keyAt(i), prediction));
        }

        final TrainingResult last = model.getLastResult();
        final String quality = last == null ? ""
                : String.format(Locale.US, " (%s = %.4g on the test set)",
                last.headlineMetricName(), last.headlineMetric());

        final String summary = String.format(Locale.US,
                "Fill %s with the %s trained on %s%s.%nFilling %d of %d null(s)%s.",
                column, model.getModelType(), String.join(", ", features), quality,
                fills.size(), missing,
                skipped > 0 ? "; " + skipped + " skipped for missing or unusable features" : "");

        return new Plan(column, Method.TRAINED_MODEL, null, fills, missing, summary);
    }

    private Plan constantPlan(String column, Method method, double value, long missing, String name) {
        if (Double.isNaN(value)) {
            return new Plan(column, method, null, List.of(), missing,
                    "Could not compute the " + name + " of " + column + " — is it numeric?");
        }
        String text = trimNumber(value);
        return new Plan(column, method, text, List.of(), missing,
                String.format(Locale.US, "Fill %d null(s) in %s with the %s (%s).", missing, column, name, text));
    }

    private Plan modePlan(String column, long missing) {
        LinkedHashMap<String, Long> counts = statistics.valueCounts(column, 1);
        if (counts.isEmpty()) {
            return new Plan(column, Method.MODE, null, List.of(), missing,
                    "Column " + column + " has no values to take a mode from.");
        }
        Map.Entry<String, Long> top = counts.entrySet().iterator().next();
        return new Plan(column, Method.MODE, top.getKey(), List.of(), missing,
                String.format("Fill %d null(s) in %s with '%s' (appears %d times).",
                        missing, column, top.getKey(), top.getValue()));
    }

    /**
     * Ajusta uma regressão simples por cada preditor, fica com o de maior |correlação| e
     * prevê a partir dele. É o que o código antigo tentava fazer, mas escrevia o resultado
     * sobre si próprio no array e nunca o chegava a aplicar.
     */
    private Plan regressionPlan(String column, List<String> predictors, long missing) {
        List<String> usable = usablePredictors(column, predictors);
        if (usable.isEmpty()) {
            return new Plan(column, Method.LINEAR_REGRESSION, null, List.of(), missing,
                    "Pick at least one numeric column to predict " + column + " from.");
        }

        String best = null;
        double bestAbsCorrelation = 0;
        for (String predictor : usable) {
            double r = statistics.correlation(predictor, column);
            if (!Double.isNaN(r) && Math.abs(r) > bestAbsCorrelation) {
                bestAbsCorrelation = Math.abs(r);
                best = predictor;
            }
        }

        if (best == null) {
            return new Plan(column, Method.LINEAR_REGRESSION, null, List.of(), missing,
                    "None of the chosen columns correlates with " + column + ".");
        }

        // Treina no que existe...
        DatasetSample trainingSample = loadSample(List.of(best, column), null, MODEL_SAMPLE_LIMIT);
        List<double[]> pairs = new ArrayList<>();
        for (int i = 0; i < trainingSample.size(); i++) {
            Double x = parse(trainingSample.valueAt(i, best));
            Double y = parse(trainingSample.valueAt(i, column));
            if (x != null && y != null) pairs.add(new double[]{x, y});
        }
        if (pairs.size() < 2) {
            return new Plan(column, Method.LINEAR_REGRESSION, null, List.of(), missing,
                    "Not enough complete rows to fit a regression.");
        }

        double[] xs = pairs.stream().mapToDouble(p -> p[0]).toArray();
        double[] ys = pairs.stream().mapToDouble(p -> p[1]).toArray();
        DataScienceUtils.LinearRegressionResult fit = DataScienceUtils.calculateLinearRegression(xs, ys);

        // ...e prevê nas linhas em falta.
        DatasetSample missingSample = loadSample(List.of(best), column, MODEL_SAMPLE_LIMIT);
        if (!missingSample.isWritable()) return notAddressable(column, Method.LINEAR_REGRESSION, missing);

        List<RowFill> fills = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < missingSample.size(); i++) {
            Double x = parse(missingSample.valueAt(i, best));
            if (x == null) {
                skipped++;
                continue;
            }
            fills.add(new RowFill(missingSample.keyAt(i), fit.slope * x + fit.intercept));
        }

        String summary = String.format(Locale.US,
                "Predict %s from %s (|r| = %.4f, %s).%nFilling %d of %d null(s)%s.",
                column, best, bestAbsCorrelation, fit, fills.size(), missing,
                skipped > 0 ? "; " + skipped + " skipped because " + best + " is also null" : "");

        return new Plan(column, Method.LINEAR_REGRESSION, null, fills, missing, summary);
    }

    /**
     * Média dos k vizinhos mais próximos no espaço dos preditores, com as distâncias
     * calculadas sobre valores normalizados para nenhuma coluna dominar por causa da escala.
     */
    private Plan knnPlan(String column, List<String> predictors, int k, long missing) {
        List<String> usable = usablePredictors(column, predictors);
        if (usable.isEmpty()) {
            return new Plan(column, Method.KNN, null, List.of(), missing,
                    "Pick at least one numeric column to measure neighbours by.");
        }

        List<String> projection = new ArrayList<>(usable);
        projection.add(column);

        DatasetSample trainingSample = loadSample(projection, null, MODEL_SAMPLE_LIMIT);

        List<double[]> neighbourFeatures = new ArrayList<>();
        List<Double> neighbourTargets = new ArrayList<>();
        for (int i = 0; i < trainingSample.size(); i++) {
            Double target = parse(trainingSample.valueAt(i, column));
            if (target == null) continue;
            double[] features = new double[usable.size()];
            boolean complete = true;
            for (int f = 0; f < usable.size(); f++) {
                Double value = parse(trainingSample.valueAt(i, usable.get(f)));
                if (value == null) {
                    complete = false;
                    break;
                }
                features[f] = value;
            }
            if (!complete) continue;
            neighbourFeatures.add(features);
            neighbourTargets.add(target);
        }

        if (neighbourFeatures.size() < k) {
            return new Plan(column, Method.KNN, null, List.of(), missing,
                    "Only " + neighbourFeatures.size() + " complete row(s) available; need at least " + k + ".");
        }

        double[] scale = featureScale(neighbourFeatures, usable.size());

        DatasetSample missingSample = loadSample(usable, column, MODEL_SAMPLE_LIMIT);
        if (!missingSample.isWritable()) return notAddressable(column, Method.KNN, missing);

        List<RowFill> fills = new ArrayList<>();
        int skipped = 0;
        for (int i = 0; i < missingSample.size(); i++) {
            double[] query = new double[usable.size()];
            boolean complete = true;
            for (int f = 0; f < usable.size(); f++) {
                Double value = parse(missingSample.valueAt(i, usable.get(f)));
                if (value == null) {
                    complete = false;
                    break;
                }
                query[f] = value;
            }
            if (!complete) {
                skipped++;
                continue;
            }
            fills.add(new RowFill(missingSample.keyAt(i),
                    averageOfNearest(query, neighbourFeatures, neighbourTargets, scale, k)));
        }

        String summary = String.format(Locale.US,
                "k-NN with k = %d over %s.%nTrained on %d complete row(s); filling %d of %d null(s)%s.",
                k, String.join(", ", usable), neighbourFeatures.size(), fills.size(), missing,
                skipped > 0 ? "; " + skipped + " skipped for missing predictors" : "");

        return new Plan(column, Method.KNN, null, fills, missing, summary);
    }

    private static double[] featureScale(List<double[]> features, int dimensions) {
        double[] scale = new double[dimensions];
        for (int f = 0; f < dimensions; f++) {
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (double[] row : features) {
                min = Math.min(min, row[f]);
                max = Math.max(max, row[f]);
            }
            double range = max - min;
            // Coluna constante não distingue vizinhos; 1 evita a divisão por zero.
            scale[f] = range == 0 ? 1 : range;
        }
        return scale;
    }

    private static double averageOfNearest(double[] query, List<double[]> features,
                                           List<Double> targets, double[] scale, int k) {
        // Mantém os k melhores num varrimento só, em vez de ordenar tudo.
        double[] bestDistances = new double[k];
        double[] bestTargets = new double[k];
        java.util.Arrays.fill(bestDistances, Double.MAX_VALUE);

        for (int i = 0; i < features.size(); i++) {
            double distance = 0;
            double[] candidate = features.get(i);
            for (int f = 0; f < query.length; f++) {
                double delta = (candidate[f] - query[f]) / scale[f];
                distance += delta * delta;
            }
            for (int slot = 0; slot < k; slot++) {
                if (distance < bestDistances[slot]) {
                    System.arraycopy(bestDistances, slot, bestDistances, slot + 1, k - slot - 1);
                    System.arraycopy(bestTargets, slot, bestTargets, slot + 1, k - slot - 1);
                    bestDistances[slot] = distance;
                    bestTargets[slot] = targets.get(i);
                    break;
                }
            }
        }

        double sum = 0;
        int used = 0;
        for (int slot = 0; slot < k; slot++) {
            if (bestDistances[slot] != Double.MAX_VALUE) {
                sum += bestTargets[slot];
                used++;
            }
        }
        return used == 0 ? Double.NaN : sum / used;
    }

    // ==== Aplicação ====

    /**
     * Escreve o plano na base de dados.
     *
     * @return número de linhas alteradas
     */
    public int apply(Plan plan) throws SQLException {
        if (plan.constantValue() != null) return applyConstant(plan);
        return applyPerRow(plan);
    }

    private int applyConstant(Plan plan) throws SQLException {
        String column = executor.quoteIdentifier(plan.column());
        String sql = "UPDATE " + executor.quoteIdentifier(table)
                + " SET " + column + " = " + literal(plan.column(), plan.constantValue())
                + " WHERE " + column + " IS NULL";
        return executor.executeUpdate(sql);
    }

    private int applyPerRow(Plan plan) throws SQLException {
        String column = executor.quoteIdentifier(plan.column());
        int affected = 0;
        for (RowFill fill : plan.perRow()) {
            if (Double.isNaN(fill.value())) continue;
            StringBuilder sql = new StringBuilder("UPDATE ")
                    .append(executor.quoteIdentifier(table))
                    .append(" SET ").append(column).append(" = ").append(trimNumber(fill.value()))
                    .append(" WHERE ");
            boolean first = true;
            for (Map.Entry<String, String> key : fill.key().entrySet()) {
                if (!first) sql.append(" AND ");
                sql.append(executor.quoteIdentifier(key.getKey())).append(" = ")
                        .append(quoteValue(key.getValue()));
                first = false;
            }
            affected += executor.executeUpdate(sql.toString());
        }
        return affected;
    }

    // ==== Auxiliares ====

    private DatasetSample loadSample(List<String> columns, String onlyWhereNull, int limit) {
        return DatasetSample.load(fetcher, executor, dialect, table, metadata, columns, onlyWhereNull, limit, 0);
    }

    private Plan notAddressable(String column, Method method, long missing) {
        return new Plan(column, method, null, List.of(), missing,
                "This table has no primary key, so individual rows cannot be updated safely on "
                        + dialect + ". Use mean, median, most frequent or a fixed value instead.");
    }

    private List<String> usablePredictors(String target, List<String> predictors) {
        if (predictors == null) return List.of();
        return predictors.stream()
                .filter(p -> p != null && !p.isBlank())
                .filter(p -> !p.equals(target))
                .filter(statistics::isNumeric)
                .distinct()
                .toList();
    }

    /** Números vão sem aspas; texto vai citado, para o UPDATE ser válido nos dois casos. */
    private String literal(String column, String value) {
        if (statistics.isNumeric(column)) {
            try {
                return trimNumber(Double.parseDouble(value.trim().replace(',', '.')));
            } catch (NumberFormatException e) {
                return quoteValue(value);
            }
        }
        return quoteValue(value);
    }

    private static String quoteValue(String value) {
        if (value == null) return "NULL";
        return "'" + value.replace("'", "''") + "'";
    }

    /** Formata sem notação científica e sem zeros à direita, que o SQL não gosta. */
    static String trimNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "NULL";
        String text = new java.math.BigDecimal(value)
                .setScale(10, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
        return text.isEmpty() ? "0" : text;
    }

    private static Double parse(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return null;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
