package com.example.sqlide.DataScience.Model;

import smile.validation.metric.RMSE;

public class ExponentialSmoothing {

    private float alpha = -1, beta = -1;
    private int time = 0;

    private double[] x;

    private double[] level, trend;

    public ExponentialSmoothing() {}

    public ExponentialSmoothing(float alpha, float beta) {
        this.alpha = alpha;
        this.beta = beta;
    }

    public float getAlpha() {
        return alpha;
    }

    public float getBeta() {
        return beta;
    }

    public void setAlpha(float alpha) {
        if (alpha < 0 || alpha > 1) return;
        this.alpha = alpha;
    }

    public void fit(double[] x) {
        this.x = x;
        time = x.length;

        if (alpha == -1 || beta == -1) {
            float bestAlpha = 0.1F, bestbeta = 0.1F;
            // O erro arrancava a 0 com a condicao "error > error_", que nunca da verdade
            // num RMSE: os melhores parametros ficavam sempre em 0. E a procura usava os
            // campos alpha/beta em vez do temp_alpha/temp_beta do ciclo, portanto todas as
            // combinacoes davam exactamente o mesmo resultado.
            double error = Double.MAX_VALUE;

            for (float temp_alpha = 0.1F; temp_alpha < 1.0; temp_alpha += 0.1F) {
                for (float temp_beta = 0.1F; temp_beta < 1.0; temp_beta += 0.1F) {
                    smoothWith(temp_alpha, temp_beta, x);

                    final double[] candidate = new double[time];
                    for (int index = 0; index < time; index++) candidate[index] = level[index] + trend[index];

                    final double error_ = RMSE.of(x, candidate);
                    if (error_ < error) {
                        error = error_;
                        bestAlpha = temp_alpha;
                        bestbeta = temp_beta;
                    }
                }
            }
            alpha = bestAlpha;
            beta = bestbeta;
        }

        smoothWith(alpha, beta, x);
    }

    /** Preenche nivel e tendencia ao longo da serie, para um par de parametros. */
    private void smoothWith(final float alpha, final float beta, final double[] x) {
        level = new double[x.length];
        trend = new double[x.length];

        // O nivel comecava em zero por o array nunca ser inicializado, e a recorrencia
        // arrastava esse zero por toda a serie.
        level[0] = x[0];
        trend[0] = x.length > 1 ? x[1] - x[0] : 0;

        final float alpha_ = 1 - alpha;
        final float beta_ = 1 - beta;

        for (int index = 1; index < x.length; index++) {
            final double level_ = alpha * x[index] + alpha_ * (level[index - 1] + trend[index - 1]);
            final double trend_ = beta * (level_ - level[index - 1]) + beta_ * trend[index - 1];
            level[index] = level_;
            trend[index] = trend_;
        }
    }

    /**
     * Valores para os instantes pedidos.
     *
     * <p>Dentro da serie devolve nivel mais tendencia; para lá do fim projecta a tendencia,
     * que e o que o metodo de Holt preve: {@code nivel + h * tendencia}.</p>
     *
     * <p>Antes lia {@code x[time]}, {@code level[time]} e {@code trend[time]} com
     * {@code time == x.length} — todos fora de limites — e a recorrencia usava
     * {@code times[index]}, o proprio indice do tempo, como se fosse uma observacao.</p>
     */
    public double[] predict(int[] times) throws Exception {
        if (x == null || level == null) throw new Exception("You need to train model");

        final double[] pred = new double[times.length];
        final int last = level.length - 1;

        for (int i = 0; i < times.length; i++) {
            final int t = times[i];
            if (t >= 0 && t < level.length) {
                pred[i] = level[t] + trend[t];
            } else {
                final int horizon = t - last;
                pred[i] = level[last] + horizon * trend[last];
            }
        }

        return pred;
    }

}
