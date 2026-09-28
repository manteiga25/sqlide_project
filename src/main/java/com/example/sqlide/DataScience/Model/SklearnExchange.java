package com.example.sqlide.DataScience.Model;

import org.json.JSONArray;
import org.json.JSONObject;
import smile.classification.LogisticRegression;
import smile.regression.LinearModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Interoperabilidade com o scikit-learn.
 *
 * <p>O {@code exportModel} do pipeline grava o objeto Java serializado, que o Python não
 * sabe ler de todo. Aqui o modelo é reduzido ao que ele é de facto — pesos, intercept,
 * nomes das colunas e codificação do alvo — num JSON que atravessa as duas linguagens,
 * acompanhado de um script que reconstrói o estimador equivalente do lado do sklearn.</p>
 *
 * <p>Cobre a família linear: regressão linear e regressão logística, binomial ou
 * multinomial. Árvores, florestas e boosting não têm representação em coeficientes e
 * ficam de fora — o exportador diz isso em vez de gravar algo incompleto.</p>
 */
public final class SklearnExchange {

    public static final String FORMAT = "sqlide-linear-model";
    public static final int VERSION = 1;

    private SklearnExchange() {
    }

    /** Um modelo lido de JSON, pronto a prever sem o Smile. */
    public record ImportedModel(String estimator,
                                Models.Task task,
                                List<String> features,
                                String target,
                                double[][] coefficients,
                                double[] intercepts,
                                List<String> classes) {

        /**
         * Previsão para uma observação.
         *
         * @return o valor previsto na regressão, ou o índice da classe na classificação
         */
        public double predict(double[] x) {
            if (task == Models.Task.REGRESSION) return score(0, x);

            // Binário: um só conjunto de pesos, decide-se pelo sinal.
            if (coefficients.length == 1) return score(0, x) >= 0 ? 1 : 0;

            // Multinomial: fica a classe com a pontuação mais alta.
            int best = 0;
            double bestScore = score(0, x);
            for (int k = 1; k < coefficients.length; k++) {
                final double current = score(k, x);
                if (current > bestScore) {
                    bestScore = current;
                    best = k;
                }
            }
            return best;
        }

        private double score(int row, double[] x) {
            double sum = row < intercepts.length ? intercepts[row] : 0;
            final double[] weights = coefficients[row];
            for (int i = 0; i < weights.length && i < x.length; i++) sum += weights[i] * x[i];
            return sum;
        }

        /** Nome da classe prevista, quando o alvo era categórico. */
        public String describe(double prediction) {
            if (task == Models.Task.REGRESSION || classes.isEmpty()) return String.valueOf(prediction);
            final int index = (int) Math.round(prediction);
            return index >= 0 && index < classes.size() ? classes.get(index) : String.valueOf(prediction);
        }
    }

    /** Lançada quando o modelo treinado não se reduz a coeficientes. */
    public static class UnsupportedModelException extends Exception {
        public UnsupportedModelException(String message) {
            super(message);
        }
    }

    // ==== Exportação ====

    /**
     * Escreve o modelo em JSON e um script Python que o reconstrói no sklearn.
     *
     * @param path caminho base; são criados {@code path.json} e {@code path.py}
     * @return os dois ficheiros escritos
     */
    public static List<Path> export(final ModelPipeline pipeline, final String path)
            throws UnsupportedModelException, IOException {

        final Object model = pipeline.getModel();
        if (model == null) throw new UnsupportedModelException("Train a model first.");

        final JSONObject json = describe(pipeline, model);

        final String base = path.endsWith(".json") ? path.substring(0, path.length() - 5) : path;
        final Path jsonPath = Path.of(base + ".json");
        final Path scriptPath = Path.of(base + ".py");

        Files.writeString(jsonPath, json.toString(2), StandardCharsets.UTF_8);
        Files.writeString(scriptPath, buildScript(json, jsonPath.getFileName().toString()), StandardCharsets.UTF_8);

        return List.of(jsonPath, scriptPath);
    }

    private static JSONObject describe(final ModelPipeline pipeline, final Object model)
            throws UnsupportedModelException {

        final JSONObject json = new JSONObject();
        json.put("format", FORMAT);
        json.put("version", VERSION);
        json.put("features", new JSONArray(pipeline.getFeatures()));
        json.put("target", pipeline.getTarget());

        switch (model) {
            case LinearModel linear -> {
                json.put("estimator", "LinearRegression");
                json.put("task", "regression");
                json.put("coefficients", new JSONArray(List.of(toList(linear.coefficients()))));
                json.put("intercept", new JSONArray(List.of(linear.intercept())));
            }
            case LogisticRegression.Binomial binomial -> {
                // O Smile guarda o bias no último elemento do vetor de pesos.
                final double[] raw = binomial.coefficients();
                final double[] weights = new double[Math.max(0, raw.length - 1)];
                System.arraycopy(raw, 0, weights, 0, weights.length);

                json.put("estimator", "LogisticRegression");
                json.put("task", "classification");
                json.put("coefficients", new JSONArray(List.of(toList(weights))));
                json.put("intercept", new JSONArray(List.of(raw.length > 0 ? raw[raw.length - 1] : 0.0)));
                json.put("classes", new JSONArray(pipeline.getLabelEncoder().classes()));
            }
            case LogisticRegression.Multinomial multinomial -> {
                final double[][] raw = multinomial.coefficients();
                final List<List<Double>> weights = new ArrayList<>();
                final List<Double> biases = new ArrayList<>();
                for (double[] row : raw) {
                    final double[] withoutBias = new double[Math.max(0, row.length - 1)];
                    System.arraycopy(row, 0, withoutBias, 0, withoutBias.length);
                    weights.add(toList(withoutBias));
                    biases.add(row.length > 0 ? row[row.length - 1] : 0.0);
                }

                json.put("estimator", "LogisticRegression");
                json.put("task", "classification");
                json.put("coefficients", new JSONArray(weights));
                json.put("intercept", new JSONArray(biases));
                json.put("classes", new JSONArray(pipeline.getLabelEncoder().classes()));
            }
            default -> throw new UnsupportedModelException(
                    model.getClass().getSimpleName() + " has no coefficient form, so it cannot be "
                            + "translated to scikit-learn. Only linear and logistic regression can.");
        }

        final TrainingResult last = pipeline.getLastResult();
        if (last != null) {
            final JSONObject metrics = new JSONObject();
            last.metrics().forEach((key, value) -> metrics.put(key, String.valueOf(value)));
            json.put("metrics", metrics);
            json.put("trainingRows", last.trainingRows());
            json.put("testRows", last.testRows());
        }

        return json;
    }

    private static List<Double> toList(final double[] values) {
        final List<Double> list = new ArrayList<>(values.length);
        for (double value : values) list.add(value);
        return list;
    }

    /** Script que reconstrói o estimador do lado do Python, sem re-treinar nada. */
    private static String buildScript(final JSONObject json, final String jsonFileName) {
        final boolean classification = "classification".equals(json.getString("task"));

        return "\"\"\"Rebuilds the model exported by SQLIDE as a scikit-learn estimator.\n"
                + "\n"
                + "The weights come straight from the trained model, so nothing is re-fitted:\n"
                + "the estimator predicts exactly what SQLIDE predicted.\n"
                + "\n"
                + "    pip install scikit-learn numpy\n"
                + "\"\"\"\n"
                + "import json\n"
                + "import numpy as np\n"
                + (classification
                ? "from sklearn.linear_model import LogisticRegression\n"
                : "from sklearn.linear_model import LinearRegression\n")
                + "\n"
                + "with open(\"" + jsonFileName + "\", encoding=\"utf-8\") as handle:\n"
                + "    bundle = json.load(handle)\n"
                + "\n"
                + "features = bundle[\"features\"]\n"
                + "coefficients = np.array(bundle[\"coefficients\"], dtype=float)\n"
                + "intercept = np.array(bundle[\"intercept\"], dtype=float)\n"
                + "\n"
                + (classification
                ? "model = LogisticRegression()\n"
                + "model.classes_ = np.array(bundle[\"classes\"])\n"
                + "# Binary logistic regression keeps a single row of weights in scikit-learn too.\n"
                + "model.coef_ = coefficients\n"
                + "model.intercept_ = intercept\n"
                : "model = LinearRegression()\n"
                + "model.coef_ = coefficients[0]\n"
                + "model.intercept_ = float(intercept[0])\n")
                + "model.n_features_in_ = len(features)\n"
                + "model.feature_names_in_ = np.array(features, dtype=object)\n"
                + "\n"
                + "if __name__ == \"__main__\":\n"
                + "    print(\"target :\", bundle[\"target\"])\n"
                + "    print(\"features:\", features)\n"
                + "    sample = np.zeros((1, len(features)))\n"
                + "    print(\"predict(zeros) =\", model.predict(sample))\n";
    }

    // ==== Importação ====

    /**
     * Lê um modelo em JSON.
     *
     * <p>Aceita o formato escrito por {@link #export} e também um ficheiro produzido do lado
     * do Python com as mesmas chaves — basta gravar {@code coef_} e {@code intercept_} com
     * os nomes das colunas.</p>
     */
    public static ImportedModel importModel(final Path path) throws IOException, UnsupportedModelException {
        final JSONObject json = new JSONObject(Files.readString(path, StandardCharsets.UTF_8));

        if (!json.has("coefficients")) {
            throw new UnsupportedModelException("The file has no \"coefficients\" entry.");
        }

        final List<String> features = readStrings(json.optJSONArray("features"));
        final List<String> classes = readStrings(json.optJSONArray("classes"));
        final String target = json.optString("target", "");
        final String estimator = json.optString("estimator", "LinearRegression");

        final Models.Task task = "classification".equalsIgnoreCase(json.optString("task"))
                ? Models.Task.CLASSIFICATION : Models.Task.REGRESSION;

        final double[][] coefficients = readMatrix(json.getJSONArray("coefficients"));
        final double[] intercepts = readIntercepts(json.opt("intercept"), coefficients.length);

        if (!features.isEmpty() && coefficients[0].length != features.size()) {
            throw new UnsupportedModelException("The file has " + coefficients[0].length
                    + " coefficient(s) but " + features.size() + " feature name(s).");
        }

        return new ImportedModel(estimator, task, features, target, coefficients, intercepts, classes);
    }

    /** Aceita tanto [[..],[..]] como [..], que é o que o sklearn escreve num modelo binário. */
    private static double[][] readMatrix(final JSONArray array) {
        if (array.isEmpty()) return new double[][]{{}};

        if (array.get(0) instanceof JSONArray) {
            final double[][] matrix = new double[array.length()][];
            for (int i = 0; i < array.length(); i++) {
                final JSONArray row = array.getJSONArray(i);
                matrix[i] = new double[row.length()];
                for (int j = 0; j < row.length(); j++) matrix[i][j] = row.getDouble(j);
            }
            return matrix;
        }

        final double[] flat = new double[array.length()];
        for (int i = 0; i < array.length(); i++) flat[i] = array.getDouble(i);
        return new double[][]{flat};
    }

    private static double[] readIntercepts(final Object raw, final int rows) {
        if (raw instanceof JSONArray array) {
            final double[] values = new double[array.length()];
            for (int i = 0; i < array.length(); i++) values[i] = array.getDouble(i);
            return values;
        }
        if (raw instanceof Number number) return new double[]{number.doubleValue()};
        return new double[rows];
    }

    private static List<String> readStrings(final JSONArray array) {
        final List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) values.add(String.valueOf(array.get(i)));
        return values;
    }

}
