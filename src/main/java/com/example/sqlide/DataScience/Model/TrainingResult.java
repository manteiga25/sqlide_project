package com.example.sqlide.DataScience.Model;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Resultado de um passo de treino: o modelo, as métricas medidas no conjunto de teste
 * e os pares real/previsto que alimentam os gráficos de avaliação e de resíduos.
 *
 * <p>As métricas vêm num {@link LinkedHashMap} para manterem a ordem em que fazem sentido
 * ser lidas, em vez da ordem arbitrária de um HashMap como acontecia antes.</p>
 */
public record TrainingResult(
        Object model,
        Models type,
        Models.Task task,
        List<String> features,
        String target,
        int trainingRows,
        int testRows,
        LinkedHashMap<String, Object> metrics,
        double[] actual,
        double[] predicted,
        String[] classLabels) {

    /** Resíduo por observação do conjunto de teste: real menos previsto. */
    public double[] residuals() {
        double[] residuals = new double[Math.min(actual.length, predicted.length)];
        for (int i = 0; i < residuals.length; i++) residuals[i] = actual[i] - predicted[i];
        return residuals;
    }

    /** A métrica que faz sentido seguir ao longo do treino: R² na regressão, exatidão na classificação. */
    public double headlineMetric() {
        Object value = metrics.get(task == Models.Task.REGRESSION ? "R2" : "Accuracy");
        return value instanceof Number number ? number.doubleValue() : Double.NaN;
    }

    public String headlineMetricName() {
        return task == Models.Task.REGRESSION ? "R²" : "Accuracy";
    }

}
