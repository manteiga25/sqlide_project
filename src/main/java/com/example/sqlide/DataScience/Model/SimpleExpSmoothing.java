package com.example.sqlide.DataScience.Model;

import smile.validation.metric.RMSE;

public class SimpleExpSmoothing {

    private float alpha = -1;
    private int time = 0;

    private double[] x;

    private double[] smooth;

    public SimpleExpSmoothing() {}

    public SimpleExpSmoothing(float alpha) {
        this.alpha = alpha;
    }

    public float getAlpha() {
        return alpha;
    }

    public void setAlpha(float alpha) {
        if (alpha < 0 || alpha > 1) return;
        this.alpha = alpha;
    }

    public void fit(double[] x) {
        this.x = x;
        time = x.length;

        if (alpha == -1) {
            float bestAlpha = 0.1F;
            // O erro começava a 0 e a condição era "error > error_", que nunca dá verdade
            // com um RMSE não negativo: o melhor alpha ficava sempre em 0.
            double error = Double.MAX_VALUE;

            for (float temp_alpha = 0.1F; temp_alpha < 1.0; temp_alpha += 0.1F) {
                final double[] candidate = smoothWith(temp_alpha, x);
                final double error_ = RMSE.of(x, candidate);

                if (error_ < error) {
                    error = error_;
                    bestAlpha = temp_alpha;
                }
            }
            alpha = bestAlpha;
        }

        smooth = smoothWith(alpha, x);
    }

    /** Nível suavizado ao longo da série, para um dado alpha. */
    private double[] smoothWith(final float alpha, final double[] x) {
        final double[] level = new double[x.length];
        level[0] = x[0]; // initial value
        final float alpha_ = 1 - alpha;
        for (int index = 1; index < x.length; index++) {
            level[index] = alpha * x[index] + alpha_ * level[index - 1];
        }
        return level;
    }

    /**
     * Valores para os instantes pedidos.
     *
     * <p>Dentro da série devolve o nível suavizado; para lá do fim devolve o último nível,
     * que é o que a suavização exponencial simples prevê para qualquer horizonte — não tem
     * tendência para projetar.</p>
     *
     * <p>Antes começava em {@code x[time]}, com {@code time == x.length}, o que dava sempre
     * índice fora de limites; e a recorrência usava {@code x[index]}, ou seja valores
     * futuros que ainda não existem quando se está a prever.</p>
     */
    public double[] predict(int[] times) throws Exception {
        if (x == null || smooth == null) throw new Exception("You need to train model");

        final double[] pred = new double[times.length];
        final double lastLevel = smooth[smooth.length - 1];

        for (int i = 0; i < times.length; i++) {
            final int t = times[i];
            pred[i] = (t >= 0 && t < smooth.length) ? smooth[t] : lastLevel;
        }

        return pred;
    }

}