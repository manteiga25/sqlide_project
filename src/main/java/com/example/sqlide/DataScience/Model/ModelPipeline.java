package com.example.sqlide.DataScience.Model;

import com.example.sqlide.DataScience.LabelEncoder;
import smile.classification.KNN;
import smile.classification.LogisticRegression;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.data.vector.ValueVector;
import smile.regression.GradientTreeBoost;
import smile.regression.LinearModel;
import smile.regression.OLS;
import smile.regression.RandomForest;
import smile.regression.RegressionTree;
import smile.validation.ClassificationMetrics;
import smile.validation.RegressionMetrics;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Treino e avaliação dos modelos.
 *
 * <p>Reescrito de raiz. O que estava errado na versão anterior:</p>
 *
 * <ul>
 *   <li>O DataFrame era montado com {@code data.add(vector)} dentro de um
 *       {@code parallelStream}, mas em Smile 4 o {@code add} <em>devolve</em> um novo
 *       DataFrame em vez de alterar o existente. O retorno era ignorado, por isso nenhuma
 *       coluna de features chegava sequer ao modelo — treinava-se só com o alvo.</li>
 *   <li>A fração de teste vinha de um campo que nunca era preenchido, ficando a zero: o
 *       conjunto de teste era sempre vazio e as métricas mediam-se sobre nada.</li>
 *   <li>Todos os modelos eram avaliados com {@code Accuracy}, incluindo os de regressão,
 *       truncando previsões contínuas para inteiro.</li>
 *   <li>Cada lote voltava a dividir treino/teste, portanto linhas de teste de um lote
 *       apareciam no treino do seguinte.</li>
 * </ul>
 *
 * <p>Agora o conjunto de teste é retirado uma vez, à cabeça, e fica fixo. Cada lote novo
 * junta-se ao conjunto de treino e o modelo é reajustado sobre tudo o que já entrou,
 * dando uma curva de aprendizagem real ao longo dos passos.</p>
 */
public class ModelPipeline {

    /** Parâmetros de um treino. */
    public record Request(String target,
                          List<String> features,
                          Models model,
                          int testPercent,
                          double learningRate,
                          int neighbours,
                          long seed) {
    }

    private final LabelEncoder labelEncoder = new LabelEncoder();

    /** Transformações aplicadas às features antes de qualquer modelo as ver. */
    private final Preprocessor preprocessor = new Preprocessor();

    private Request request;

    /** Linhas de treino acumuladas ao longo dos lotes. */
    private final List<double[]> trainingFeatures = new ArrayList<>();
    private final List<Double> trainingTargets = new ArrayList<>();

    /** Conjunto de teste, retirado do primeiro lote e nunca mais tocado. */
    private double[][] testFeatures;
    private double[] testTargets;

    private Object model;
    private TrainingResult lastResult;
    private int step;

    // ==== Ciclo de vida ====

    /** Começa um treino novo, esquecendo lotes e codificações do anterior. */
    public void start(Request request) {
        this.request = request;
        this.trainingFeatures.clear();
        this.trainingTargets.clear();
        this.testFeatures = null;
        this.testTargets = null;
        this.model = null;
        this.lastResult = null;
        this.step = 0;
        this.labelEncoder.flush();
        this.preprocessor.reset();
    }

    public Request getRequest() {
        return request;
    }

    public Object getModel() {
        return model;
    }

    public TrainingResult getLastResult() {
        return lastResult;
    }

    public LabelEncoder getLabelEncoder() {
        return labelEncoder;
    }

    public Preprocessor getPreprocessor() {
        return preprocessor;
    }

    /** Define os passos de pré-processamento a aplicar no próximo treino. */
    public void setPreprocessing(final List<Preprocessor.Step> steps) {
        preprocessor.setSteps(steps);
    }

    public int getStep() {
        return step;
    }

    public List<String> getFeatures() {
        return request == null ? List.of() : request.features();
    }

    public String getTarget() {
        return request == null ? null : request.target();
    }

    public Models getModelType() {
        return request == null ? null : request.model();
    }

    // ==== Treino ====

    /**
     * Junta um lote de linhas e reajusta o modelo com tudo o que já foi visto.
     *
     * <p>No primeiro lote separa-se o conjunto de teste, com as linhas baralhadas por uma
     * semente fixa para o resultado ser reprodutível.</p>
     *
     * @return o resultado deste passo, ou null se o lote não trouxe linhas utilizáveis
     */
    public TrainingResult feed(List<Map<String, String>> rows) {
        if (request == null) throw new IllegalStateException("Call start() before feeding rows.");
        if (rows == null || rows.isEmpty()) return lastResult;

        boolean classification = request.model().isClassification();

        // As categorias do alvo têm de ser conhecidas antes de codificar seja o que for.
        if (classification) {
            List<String> labels = new ArrayList<>(rows.size());
            for (Map<String, String> row : rows) {
                String value = row.get(request.target());
                if (isPresent(value)) labels.add(value.trim());
            }
            labelEncoder.updateEncoder(labels);
        }

        List<double[]> batchFeatures = new ArrayList<>(rows.size());
        List<Double> batchTargets = new ArrayList<>(rows.size());

        for (Map<String, String> row : rows) {
            Double target = readTarget(row, classification);
            if (target == null) continue;

            double[] features = new double[request.features().size()];
            boolean complete = true;
            for (int f = 0; f < features.length; f++) {
                Double value = parse(row.get(request.features().get(f)));
                if (value == null) {
                    complete = false;
                    break;
                }
                features[f] = value;
            }
            // Linhas incompletas não entram: nem o treino nem a avaliação sabem o que fazer com elas.
            if (!complete) continue;

            batchFeatures.add(features);
            batchTargets.add(target);
        }

        if (batchFeatures.isEmpty()) return lastResult;

        if (testFeatures == null) {
            splitHoldout(batchFeatures, batchTargets);

            // O pré-processamento ajusta-se uma só vez, no conjunto de treino inicial.
            // Reajustá-lo a cada lote mudaria a escala a meio do treino, e o conjunto de
            // teste deixaria de ser comparável com o que o modelo viu.
            if (!preprocessor.isEmpty() && !trainingFeatures.isEmpty()) {
                preprocessor.fit(trainingFeatures.toArray(new double[0][]));
                replaceAll(trainingFeatures, preprocessor.transform(trainingFeatures.toArray(new double[0][])));
                testFeatures = preprocessor.transform(testFeatures);
            }
        } else {
            // Lotes seguintes passam pela transformação já ajustada.
            final double[][] transformed = preprocessor.isFitted()
                    ? preprocessor.transform(batchFeatures.toArray(new double[0][]))
                    : batchFeatures.toArray(new double[0][]);
            trainingFeatures.addAll(Arrays.asList(transformed));
            trainingTargets.addAll(batchTargets);
        }

        if (trainingFeatures.isEmpty()) return lastResult;

        step++;
        model = fit();
        lastResult = evaluate();
        return lastResult;
    }

    private static void replaceAll(final List<double[]> target, final double[][] values) {
        target.clear();
        target.addAll(Arrays.asList(values));
    }

    /** Retira a fração de teste do primeiro lote e guarda o resto para treino. */
    private void splitHoldout(List<double[]> features, List<Double> targets) {
        List<Integer> order = new ArrayList<>(features.size());
        for (int i = 0; i < features.size(); i++) order.add(i);
        Collections.shuffle(order, new Random(request.seed()));

        int testSize = (int) Math.round(features.size() * (request.testPercent() / 100.0));
        // Deixa sempre pelo menos uma linha de cada lado quando há dados para isso.
        testSize = Math.max(1, Math.min(testSize, features.size() - 1));
        if (features.size() < 2) testSize = 0;

        testFeatures = new double[testSize][];
        testTargets = new double[testSize];
        for (int i = 0; i < testSize; i++) {
            int index = order.get(i);
            testFeatures[i] = features.get(index);
            testTargets[i] = targets.get(index);
        }

        for (int i = testSize; i < order.size(); i++) {
            int index = order.get(i);
            trainingFeatures.add(features.get(index));
            trainingTargets.add(targets.get(index));
        }
    }

    private Object fit() {
        double[][] x = trainingFeatures.toArray(new double[0][]);
        double[] y = trainingTargets.stream().mapToDouble(Double::doubleValue).toArray();

        return switch (request.model()) {
            case LINEAR_REGRESSION -> OLS.fit(formula(), frame(x, y, false));
            case TREE_REGRESSION -> RegressionTree.fit(formula(), frame(x, y, false));
            case RANDOM_FOREST_REGRESSION -> RandomForest.fit(formula(), frame(x, y, false));
            case GRADIENT_REGRESSION -> GradientTreeBoost.fit(formula(), frame(x, y, false));

            case RANDOM_FOREST_CLASSIFICATION ->
                    smile.classification.RandomForest.fit(formula(), frame(x, y, true));
            case GRADIENT_CLASSIFICATION ->
                    smile.classification.GradientTreeBoost.fit(formula(), frame(x, y, true));

            case LOGISTIC_REGRESSION -> LogisticRegression.fit(x, toInt(y));
            case LOGISTIC_BINOMIAL_REGRESSION -> LogisticRegression.binomial(x, toInt(y));
            case LOGISTIC_MULTIMODAL_REGRESSION -> LogisticRegression.multinomial(x, toInt(y));
            case KNN -> KNN.fit(x, toInt(y), Math.max(1, Math.min(request.neighbours(), x.length)));
        };
    }

    // ==== Avaliação ====

    private TrainingResult evaluate() {
        boolean classification = request.model().isClassification();

        double[] predicted = predict(testFeatures);
        double[] actual = testTargets == null ? new double[0] : testTargets;

        LinkedHashMap<String, Object> metrics = classification
                ? classificationMetrics(actual, predicted)
                : regressionMetrics(actual, predicted);

        addModelSpecificMetrics(metrics);
        if (!preprocessor.isEmpty()) metrics.putAll(preprocessor.describe(request.features()));

        return new TrainingResult(
                model,
                request.model(),
                request.model().getTask(),
                request.features(),
                request.target(),
                trainingFeatures.size(),
                actual.length,
                metrics,
                actual,
                predicted,
                classification ? labelEncoder.decode(toInt(actual)) : null);
    }

    /** Previsões para uma matriz de observações, seja qual for a família do modelo. */
    public double[] predict(double[][] x) {
        if (x == null || x.length == 0 || model == null) return new double[0];

        double[] predictions = new double[x.length];

        switch (model) {
            case LinearModel linear -> {
                DataFrame frame = frame(x, new double[x.length], false);
                predictions = linear.predict(frame);
            }
            case RegressionTree tree -> {
                DataFrame frame = frame(x, new double[x.length], false);
                for (int i = 0; i < x.length; i++) predictions[i] = tree.predict(frame.get(i));
            }
            case RandomForest forest -> {
                DataFrame frame = frame(x, new double[x.length], false);
                for (int i = 0; i < x.length; i++) predictions[i] = forest.predict(frame.get(i));
            }
            case GradientTreeBoost boost -> {
                DataFrame frame = frame(x, new double[x.length], false);
                for (int i = 0; i < x.length; i++) predictions[i] = boost.predict(frame.get(i));
            }
            case smile.classification.RandomForest forest -> {
                DataFrame frame = frame(x, new double[x.length], true);
                for (int i = 0; i < x.length; i++) predictions[i] = forest.predict(frame.get(i));
            }
            case smile.classification.GradientTreeBoost boost -> {
                DataFrame frame = frame(x, new double[x.length], true);
                for (int i = 0; i < x.length; i++) predictions[i] = boost.predict(frame.get(i));
            }
            case LogisticRegression logistic -> {
                for (int i = 0; i < x.length; i++) predictions[i] = logistic.predict(x[i]);
            }
            case KNN<?> _ -> {
                @SuppressWarnings("unchecked")
                KNN<double[]> knn = (KNN<double[]>) model;
                for (int i = 0; i < x.length; i++) predictions[i] = knn.predict(x[i]);
            }
            default -> throw new IllegalStateException("Unsupported model: " + model.getClass());
        }

        return predictions;
    }

    /**
     * Previsão para uma única observação, usada pelo separador de previsão manual e pela
     * imputação por modelo.
     *
     * <p>Os valores entram em bruto e passam pela mesma transformação que o treino usou —
     * de outro modo o modelo receberia uma escala diferente daquela em que foi ajustado.</p>
     */
    public double predictOne(double[] features) {
        final double[] prepared = preprocessor.isFitted() ? preprocessor.transform(features) : features;
        double[] result = predict(new double[][]{prepared});
        return result.length == 0 ? Double.NaN : result[0];
    }

    /** Nome da classe prevista, para modelos de classificação com alvo categórico. */
    public String describePrediction(double prediction) {
        if (request == null || !request.model().isClassification()) return String.valueOf(prediction);
        String label = labelEncoder.decode((int) Math.round(prediction));
        return label.isEmpty() ? String.valueOf(prediction) : label;
    }

    private LinkedHashMap<String, Object> regressionMetrics(double[] actual, double[] predicted) {
        LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();
        if (actual.length == 0) {
            metrics.put("info", "Test set is empty — lower the evaluation split or load more rows.");
            return metrics;
        }
        RegressionMetrics computed = RegressionMetrics.of(0, 0, actual, predicted);
        metrics.put("R2", computed.r2());
        metrics.put("RMSE", computed.rmse());
        metrics.put("MSE", computed.mse());
        metrics.put("MAD", computed.mad());
        metrics.put("RSS", computed.rss());
        return metrics;
    }

    private LinkedHashMap<String, Object> classificationMetrics(double[] actual, double[] predicted) {
        LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();
        if (actual.length == 0) {
            metrics.put("info", "Test set is empty — lower the evaluation split or load more rows.");
            return metrics;
        }
        int[] truth = toInt(actual);
        int[] guess = toInt(predicted);

        // Precisão, recall e F1 só estão definidos com duas classes, e o Smile só os
        // preenche pelo construtor binário — o genérico deixa-os a NaN.
        boolean binary = labelEncoder.size() == 2;
        ClassificationMetrics computed = binary
                ? ClassificationMetrics.binary(0, 0, truth, guess)
                : ClassificationMetrics.of(0, 0, truth, guess);

        metrics.put("Accuracy", computed.accuracy());
        metrics.put("Error", computed.error());
        if (binary) {
            metrics.put("Precision", computed.precision());
            metrics.put("Recall", computed.sensitivity());
            metrics.put("F1", computed.f1());
        }
        metrics.put("Classes", labelEncoder.size());
        return metrics;
    }

    /** Métricas que só alguns modelos sabem dar sobre si próprios. */
    private void addModelSpecificMetrics(LinkedHashMap<String, Object> metrics) {
        switch (model) {
            case LinearModel linear -> {
                metrics.put("Intercept", linear.intercept());
                metrics.put("Coefficients", describeCoefficients(linear.coefficients()));
                metrics.put("Adjusted R2", linear.adjustedRSquared());
                metrics.put("F-test", linear.ftest());
                metrics.put("p-value", linear.pvalue());
            }
            case RandomForest forest -> metrics.put("Importance", describeImportance(forest.importance()));
            case smile.classification.RandomForest forest ->
                    metrics.put("Importance", describeImportance(forest.importance()));
            case GradientTreeBoost boost -> metrics.put("Importance", describeImportance(boost.importance()));
            case smile.classification.GradientTreeBoost boost ->
                    metrics.put("Importance", describeImportance(boost.importance()));
            default -> {
                // Os restantes não expõem nada de útil para lá das métricas do conjunto de teste.
            }
        }
    }

    /** Emparelha cada coeficiente com o nome da sua coluna, em vez de imprimir um array solto. */
    private String describeCoefficients(double[] coefficients) {
        StringBuilder text = new StringBuilder();
        List<String> features = request.features();
        for (int i = 0; i < coefficients.length && i < features.size(); i++) {
            if (i > 0) text.append(", ");
            text.append(features.get(i)).append('=').append(String.format("%.6g", coefficients[i]));
        }
        return text.toString();
    }

    private String describeImportance(double[] importance) {
        return describeCoefficients(importance);
    }

    /** Importância por coluna, para o gráfico de barras do painel de modelação. */
    public LinkedHashMap<String, Double> featureImportance() {
        double[] importance = switch (model) {
            case RandomForest forest -> forest.importance();
            case smile.classification.RandomForest forest -> forest.importance();
            case GradientTreeBoost boost -> boost.importance();
            case smile.classification.GradientTreeBoost boost -> boost.importance();
            case LinearModel linear -> linear.coefficients();
            case null, default -> null;
        };

        LinkedHashMap<String, Double> map = new LinkedHashMap<>();
        if (importance == null) return map;
        List<String> features = request.features();
        for (int i = 0; i < importance.length && i < features.size(); i++) {
            map.put(features.get(i), importance[i]);
        }
        return map;
    }

    // ==== Construção do DataFrame ====

    private Formula formula() {
        return Formula.lhs(request.target());
    }

    /**
     * Monta o DataFrame de uma só vez.
     *
     * <p>É aqui que estava a raiz do problema antigo: as colunas eram adicionadas uma a uma
     * ignorando o retorno de {@code add}, e em paralelo, o que também tornava a ordem
     * imprevisível — e a ordem importa, porque os coeficientes são lidos por índice.</p>
     */
    private DataFrame frame(double[][] x, double[] y, boolean categoricalTarget) {
        List<String> features = request.features();
        ValueVector[] vectors = new ValueVector[features.size() + 1];

        for (int f = 0; f < features.size(); f++) {
            double[] column = new double[x.length];
            for (int row = 0; row < x.length; row++) column[row] = x[row][f];
            vectors[f] = new DoubleVector(features.get(f), column);
        }

        vectors[features.size()] = categoricalTarget
                ? new IntVector(request.target(), toInt(y))
                : new DoubleVector(request.target(), y);

        return new DataFrame(vectors);
    }

    private Double readTarget(Map<String, String> row, boolean classification) {
        String raw = row.get(request.target());
        if (!isPresent(raw)) return null;
        if (classification) {
            int encoded = labelEncoder.encode(raw.trim());
            return encoded < 0 ? null : (double) encoded;
        }
        return parse(raw);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank() && !value.equalsIgnoreCase("null");
    }

    private static Double parse(String value) {
        if (!isPresent(value)) return null;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int[] toInt(double[] values) {
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (int) Math.round(values[i]);
        return result;
    }

    // ==== Exportação ====

    public void exportModel(final String path) throws Exception {
        if (model == null) throw new IllegalStateException("Train a model first.");
        String target = path.endsWith(".ser") ? path : path + ".ser";
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(target))) {
            out.writeObject(model);
            out.flush();
        }
    }

    /** Resumo textual do último passo, para a caixa de resultados. */
    public String describeLastResult() {
        if (lastResult == null) return "";
        StringBuilder text = new StringBuilder();
        text.append("Step ").append(step)
                .append(" — ").append(lastResult.type())
                .append(" (").append(lastResult.trainingRows()).append(" train / ")
                .append(lastResult.testRows()).append(" test rows)\n");
        for (Map.Entry<String, Object> metric : lastResult.metrics().entrySet()) {
            text.append("  ").append(metric.getKey()).append(": ")
                    .append(formatMetric(metric.getValue())).append('\n');
        }
        return text.append('\n').toString();
    }

    private static String formatMetric(Object value) {
        if (value instanceof Double number) {
            if (number.isNaN()) return "n/a";
            return String.format("%.6g", number);
        }
        if (value instanceof double[] array) return Arrays.toString(array);
        return String.valueOf(value);
    }

}
