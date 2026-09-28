package com.example.sqlide.DataScience.Model;

import smile.validation.metric.RMSE;

public class ExponentialGammaSmoothing {

        private float alpha = -1, beta = -1, gamma = -1;
        private int time = 0;

        private int season_len = 0;

        private double[] x;

        private double[] level, trend, season;

        public ExponentialGammaSmoothing() {}

        public ExponentialGammaSmoothing(float alpha, float beta, float gamma, int season_len) {
            this.alpha = alpha;
            this.beta = beta;
            this.gamma = gamma;
            this.season_len = season_len;
        }

        public float getAlpha() {
            return alpha;
        }

        public float getBeta() {
            return beta;
        }

        public float getGamma() {
            return gamma;
        }

        public void setAlpha(float alpha) {
            if (alpha < 0 || alpha > 1) return;
            this.alpha = alpha;
        }

        public void setGamma(float gamma) {
            if (gamma < 0 || gamma > 1) return;
            this.gamma = gamma;
        }

        public void setBeta(float beta) {
            if (beta < 0 || beta > 1) return;
            this.beta = beta;
        }

        public void fit(double[] x) {
            this.x = x;
            time = x.length;

            if (season_len < 1) season_len = 1;

            if (alpha == -1 || beta == -1 || gamma == -1) {
                float bestAlpha = 0.1F, bestbeta = 0.1F, bestGamma = 0.1F;
                // Mesmo problema das outras duas: o erro comecava a 0 com a condicao
                // invertida, e a procura usava os campos em vez das variaveis do ciclo.
                double error = Double.MAX_VALUE;

                for (float temp_alpha = 0.1F; temp_alpha < 1.0; temp_alpha += 0.1F) {
                    for (float temp_beta = 0.1F; temp_beta < 1.0; temp_beta += 0.1F) {
                        for (float temp_gamma = 0.1F; temp_gamma < 1.0; temp_gamma += 0.1F) {
                            smoothWith(temp_alpha, temp_beta, temp_gamma, x);

                            final double[] candidate = new double[time];
                            for (int index = 0; index < time; index++) {
                                candidate[index] = (level[index] + trend[index]) * season[index];
                            }

                            final double error_ = RMSE.of(x, candidate);
                            if (error_ < error) {
                                error = error_;
                                bestAlpha = temp_alpha;
                                bestbeta = temp_beta;
                                bestGamma = temp_gamma;
                            }
                        }
                    }
                }
                alpha = bestAlpha;
                beta = bestbeta;
                gamma = bestGamma;
            }

            smoothWith(alpha, beta, gamma, x);
        }

        /**
         * Ajusta nivel, tendencia e sazonalidade ao longo da serie.
         *
         * <p>O array {@code season} nunca chegava a ser criado no fit — so {@code level} e
         * {@code trend} — e o primeiro acesso rebentava com NullPointerException. Os indices
         * sazonais tambem eram lidos em {@code index - season_len}, negativo no arranque.</p>
         */
        private void smoothWith(final float alpha, final float beta, final float gamma, final double[] x) {
            level = new double[x.length];
            trend = new double[x.length];
            season = new double[x.length];

            // Indices sazonais iniciais: cada ponto do primeiro ciclo em relacao a media dele.
            final int cycle = Math.min(season_len, x.length);
            double sum = 0;
            for (int index = 0; index < cycle; index++) sum += x[index];
            final double average = cycle > 0 && sum != 0 ? sum / cycle : 1;

            for (int index = 0; index < x.length; index++) {
                final int position = index % cycle;
                // Um indice a zero anularia a serie toda na multiplicacao.
                final double initial = average != 0 ? x[position] / average : 1;
                season[index] = initial == 0 ? 1 : initial;
            }

            level[0] = x[0];
            trend[0] = x.length > 1 ? x[1] - x[0] : 0;

            final float alpha_ = 1 - alpha;
            final float beta_ = 1 - beta;
            final float gamma_ = 1 - gamma;

            for (int index = 1; index < x.length; index++) {
                // O indice sazonal de referencia e o do ciclo anterior; antes do primeiro
                // ciclo completo usa-se o que foi estimado no arranque.
                final int previousSeason = index - season_len;
                final double seasonRef = previousSeason >= 0 ? season[previousSeason] : season[index];
                final double safeSeason = seasonRef == 0 ? 1 : seasonRef;

                final double level_ = alpha * (x[index] / safeSeason)
                        + alpha_ * (level[index - 1] + trend[index - 1]);
                final double trend_ = beta * (level_ - level[index - 1]) + beta_ * trend[index - 1];
                final double season_ = level_ == 0
                        ? safeSeason
                        : gamma * (x[index] / level_) + gamma_ * safeSeason;

                level[index] = level_;
                trend[index] = trend_;
                season[index] = season_ == 0 ? 1 : season_;
            }
        }

        /**
         * Valores para os instantes pedidos.
         *
         * <p>Para lá do fim da serie a previsao de Holt-Winters e
         * {@code (nivel + h * tendencia) * indice sazonal do ciclo correspondente}.</p>
         */
        public double[] predict(int[] times) throws Exception {
            if (x == null || level == null || season == null) throw new Exception("You need to train model");

            final double[] pred = new double[times.length];
            final int last = level.length - 1;

            for (int i = 0; i < times.length; i++) {
                final int t = times[i];

                if (t >= 0 && t < level.length) {
                    pred[i] = (level[t] + trend[t]) * season[t];
                    continue;
                }

                final int horizon = t - last;
                // O indice sazonal repete-se de season_len em season_len.
                final int seasonIndex = last - season_len + 1 + ((horizon - 1) % season_len);
                final double factor = seasonIndex >= 0 && seasonIndex < season.length
                        ? season[seasonIndex] : 1;

                pred[i] = (level[last] + horizon * trend[last]) * factor;
            }

            return pred;
        }

}
