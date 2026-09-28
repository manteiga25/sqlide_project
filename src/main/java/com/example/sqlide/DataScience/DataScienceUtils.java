package com.example.sqlide.DataScience;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class DataScienceUtils {

    /**
     * Resultado da regressão linear
     */
    public static class LinearRegressionResult {
        public final double slope;
        public final double intercept;
        public final double rSquared;
        public final double meanSquaredError;

        public LinearRegressionResult(double slope, double intercept, double rSquared, double mse) {
            this.slope = slope;
            this.intercept = intercept;
            this.rSquared = rSquared;
            this.meanSquaredError = mse;
        }

        @Override
        public String toString() {
            return String.format("y = %.4fx + %.4f (R² = %.4f, MSE = %.4f)",
                    slope, intercept, rSquared, meanSquaredError);
        }
    }

    /**
     * Métricas estatísticas dos dados
     */
    public static class DataMetrics {
        public final double mean;
        public final double median;
        public final double standardDeviation;
        public final double variance;
        public final double min;
        public final double max;
        public final int count;
        public final int nullCount;
        public final double q1;
        public final double q3;
        public final double iqr;

        public DataMetrics(double mean, double median, double stdDev, double variance,
                           double min, double max, int count, int nullCount,
                           double q1, double q3, double iqr) {
            this.mean = mean;
            this.median = median;
            this.standardDeviation = stdDev;
            this.variance = variance;
            this.min = min;
            this.max = max;
            this.count = count;
            this.nullCount = nullCount;
            this.q1 = q1;
            this.q3 = q3;
            this.iqr = iqr;
        }
    }

    /**
     * Resultado da detecção de outliers
     */
    public static class OutlierResult {
        public final List<Integer> outlierIndices;
        public final List<Double> outlierValues;
        public final double lowerBound;
        public final double upperBound;
        public final String method;

        public OutlierResult(List<Integer> indices, List<Double> values,
                             double lower, double upper, String method) {
            this.outlierIndices = indices;
            this.outlierValues = values;
            this.lowerBound = lower;
            this.upperBound = upper;
            this.method = method;
        }
    }

    /**
     * Calcula regressão linear usando método dos mínimos quadrados
     * @param x variável independente
     * @param y variável dependente
     * @return resultado da regressão linear
     */
    public static LinearRegressionResult calculateLinearRegression(double[] x, double[] y) {
        if (x.length != y.length || x.length < 2) {
            throw new IllegalArgumentException("Arrays devem ter o mesmo tamanho e pelo menos 2 elementos");
        }

        int n = x.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumXX += x[i] * x[i];
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        // Calcular slope (coeficiente angular)
        double slope = (sumXY - n * meanX * meanY) / (sumXX - n * meanX * meanX);

        // Calcular intercept (coeficiente linear)
        double intercept = meanY - slope * meanX;

        // Calcular R²
        double ssTotal = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double predicted = slope * x[i] + intercept;
            ssTotal += Math.pow(y[i] - meanY, 2);
            ssRes += Math.pow(y[i] - predicted, 2);
        }
        double rSquared = 1 - (ssRes / ssTotal);

        // Calcular MSE
        double mse = ssRes / n;

        return new LinearRegressionResult(slope, intercept, rSquared, mse);
    }

    /**
     * Calcula métricas estatísticas dos dados
     * @param data array de dados (valores nulos devem ser filtrados antes)
     * @param totalCount contagem total incluindo nulos
     * @return métricas estatísticas
     */
    public static DataMetrics computeMetrics(double[] data, int totalCount) {
        if (data.length == 0) {
            return new DataMetrics(0, 0, 0, 0, 0, 0, 0, totalCount, 0, 0, 0);
        }

        Arrays.sort(data);

        // Média
        double sum = Arrays.stream(data).sum();
        double mean = sum / data.length;

        // Mediana
        double median;
        int n = data.length;
        if (n % 2 == 0) {
            median = (data[n/2 - 1] + data[n/2]) / 2.0;
        } else {
            median = data[n/2];
        }

        // Variância e desvio padrão
        double variance = Arrays.stream(data)
                .map(x -> Math.pow(x - mean, 2))
                .sum() / data.length;
        double stdDev = Math.sqrt(variance);

        // Min e Max
        double min = data[0];
        double max = data[data.length - 1];

        // Quartis
        double q1 = calculatePercentile(data, 25);
        double q3 = calculatePercentile(data, 75);
        double iqr = q3 - q1;

        int nullCount = totalCount - data.length;

        return new DataMetrics(mean, median, stdDev, variance, min, max,
                data.length, nullCount, q1, q3, iqr);
    }

    /**
     * Detecta outliers usando método IQR
     * @param data dados para análise
     * @return resultado da detecção de outliers
     */
    public static OutlierResult detectOutliersIQR(double[] data) {
        if (data.length < 4) {
            return new OutlierResult(new ArrayList<>(), new ArrayList<>(), 0, 0, "IQR");
        }

        double[] sortedData = data.clone();
        Arrays.sort(sortedData);

        double q1 = calculatePercentile(sortedData, 25);
        double q3 = calculatePercentile(sortedData, 75);
        double iqr = q3 - q1;

        double lowerBound = q1 - 1.5 * iqr;
        double upperBound = q3 + 1.5 * iqr;

        List<Integer> outlierIndices = new ArrayList<>();
        List<Double> outlierValues = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            if (data[i] < lowerBound || data[i] > upperBound) {
                outlierIndices.add(i);
                outlierValues.add(data[i]);
            }
        }

        return new OutlierResult(outlierIndices, outlierValues, lowerBound, upperBound, "IQR");
    }

    /**
     * Detecta outliers usando Z-Score
     * @param data dados para análise
     * @param threshold limite do z-score (padrão: 3.0)
     * @return resultado da detecção de outliers
     */
    public static OutlierResult detectOutliersZScore(double[] data, double threshold) {
        if (data.length < 2) {
            return new OutlierResult(new ArrayList<>(), new ArrayList<>(), 0, 0, "Z-Score");
        }

        double mean = Arrays.stream(data).average().orElse(0);
        double stdDev = Math.sqrt(Arrays.stream(data)
                .map(x -> Math.pow(x - mean, 2))
                .sum() / data.length);

        if (stdDev == 0) {
            return new OutlierResult(new ArrayList<>(), new ArrayList<>(), 0, 0, "Z-Score");
        }

        List<Integer> outlierIndices = new ArrayList<>();
        List<Double> outlierValues = new ArrayList<>();

        for (int i = 0; i < data.length; i++) {
            double zScore = Math.abs((data[i] - mean) / stdDev);
            if (zScore > threshold) {
                outlierIndices.add(i);
                outlierValues.add(data[i]);
            }
        }

        double lowerBound = mean - threshold * stdDev;
        double upperBound = mean + threshold * stdDev;

        return new OutlierResult(outlierIndices, outlierValues, lowerBound, upperBound, "Z-Score");
    }

    /**
     * Preenche valores nulos usando regressão linear
     * @param targetColumn coluna com valores nulos a serem preenchidos
     * @param predictorColumn coluna usada como preditor
     * @return array com valores preenchidos
     */
    public static double[] fillNullsWithRegression(Double[] targetColumn, Double[] predictorColumn) {
        if (targetColumn.length != predictorColumn.length) {
            throw new IllegalArgumentException("Colunas devem ter o mesmo tamanho");
        }

        // Separar dados válidos para treinar o modelo
        List<Double> validTarget = new ArrayList<>();
        List<Double> validPredictor = new ArrayList<>();

        for (int i = 0; i < targetColumn.length; i++) {
            if (targetColumn[i] != null && predictorColumn[i] != null) {
                validTarget.add(targetColumn[i]);
                validPredictor.add(predictorColumn[i]);
            }
        }

        if (validTarget.size() < 2) {
            throw new IllegalArgumentException("Dados insuficientes para regressão linear");
        }

        // Converter para arrays
        double[] x = validPredictor.stream().mapToDouble(Double::doubleValue).toArray();
        double[] y = validTarget.stream().mapToDouble(Double::doubleValue).toArray();

        // Calcular regressão
        LinearRegressionResult regression = calculateLinearRegression(x, y);

        // Preencher valores nulos
        double[] result = new double[targetColumn.length];
        for (int i = 0; i < targetColumn.length; i++) {
            if (targetColumn[i] != null) {
                result[i] = targetColumn[i];
            } else if (predictorColumn[i] != null) {
                result[i] = regression.slope * predictorColumn[i] + regression.intercept;
            } else {
                // Se ambos são nulos, usar a média dos valores válidos
                result[i] = validTarget.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            }
        }

        return result;
    }

    /**
     * Calcula percentil dos dados ordenados
     */
    private static double calculatePercentile(double[] sortedData, double percentile) {
        if (sortedData.length == 0) return 0;
        if (sortedData.length == 1) return sortedData[0];

        double index = (percentile / 100.0) * (sortedData.length - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedData[lowerIndex];
        }

        double weight = index - lowerIndex;
        return sortedData[lowerIndex] * (1 - weight) + sortedData[upperIndex] * weight;
    }

    /**
     * Converte lista de strings para array de doubles, tratando nulos
     */
    public static Double[] parseNumericColumn(List<String> columnData) {
        return columnData.stream()
                .map(value -> {
                    if (value == null || value.trim().isEmpty() || value.equalsIgnoreCase("null")) {
                        return null;
                    }
                    try {
                        return Double.parseDouble(value.trim());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .toArray(Double[]::new);
    }

    /**
     * Filtra valores não nulos de um array
     */
    public static double[] filterNonNull(Double[] data) {
        return Arrays.stream(data)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .toArray();
    }
}
