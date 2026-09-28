package com.example.sqlide.DataScience.Model;

import smile.timeseries.AR;
import smile.timeseries.ARMA;
import smile.timeseries.TimeSeries;

import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * Previsão de séries temporais.
 *
 * <p>Junta o que o Smile traz — {@link AR}, {@link ARMA} e as funções de autocorrelação —
 * às classes de suavização exponencial que já existiam no projeto e que não estavam
 * ligadas a lado nenhum: o {@code TrainExponentialSimple} do pipeline antigo era privado,
 * nunca chamado, e devolvia sempre métricas comentadas.</p>
 */
public final class ForecastService {

    public enum Method {
        AR_YULE_WALKER("Autoregressive (Yule-Walker)"),
        AR_OLS("Autoregressive (least squares)"),
        ARMA("ARMA"),
        SIMPLE_EXPONENTIAL("Simple exponential smoothing"),
        HOLT("Holt (level and trend)"),
        HOLT_WINTERS("Holt-Winters (level, trend and season)");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        /** True para os métodos que precisam do comprimento da estação. */
        public boolean needsSeason() {
            return this == HOLT_WINTERS;
        }

        /** True para os que aceitam a ordem p. */
        public boolean needsOrderP() {
            return this == AR_YULE_WALKER || this == AR_OLS || this == ARMA;
        }

        /** True para os que aceitam a ordem q. */
        public boolean needsOrderQ() {
            return this == ARMA;
        }

        public boolean needsSmoothing() {
            return this == SIMPLE_EXPONENTIAL || this == HOLT || this == HOLT_WINTERS;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Parâmetros de um pedido de previsão. */
    public record Request(Method method,
                          int horizon,
                          int orderP,
                          int orderQ,
                          int seasonLength,
                          double alpha,
                          double beta,
                          double gamma) {
    }

    /**
     * O que a previsão produziu.
     *
     * @param fitted    valores ajustados sobre a própria série, para comparar com o real
     * @param forecast  os {@code horizon} valores previstos a seguir ao fim da série
     */
    public record Result(Method method,
                         double[] observed,
                         double[] fitted,
                         double[] forecast,
                         LinkedHashMap<String, Object> metrics,
                         String summary) {
    }

    private ForecastService() {
    }

    /** Número mínimo de observações para o método pedido fazer sentido. */
    public static int minimumPoints(final Request request) {
        return switch (request.method()) {
            case AR_YULE_WALKER, AR_OLS -> Math.max(3, request.orderP() * 2 + 1);
            case ARMA -> Math.max(5, (request.orderP() + request.orderQ()) * 2 + 1);
            case HOLT_WINTERS -> Math.max(4, request.seasonLength() * 2);
            default -> 3;
        };
    }

    public static Result run(final double[] series, final Request request) throws Exception {
        if (series == null || series.length < minimumPoints(request)) {
            throw new IllegalArgumentException("Need at least " + minimumPoints(request)
                    + " points for " + request.method() + "; got " + (series == null ? 0 : series.length) + ".");
        }

        try {
            return switch (request.method()) {
                case AR_YULE_WALKER -> fromAr(AR.fit(series, request.orderP()), series, request);
                case AR_OLS -> fromAr(AR.ols(series, request.orderP()), series, request);
                case ARMA -> fromArma(ARMA.fit(series, request.orderP(), request.orderQ()), series, request);
                case SIMPLE_EXPONENTIAL, HOLT, HOLT_WINTERS -> fromSmoothing(series, request);
            };
        } catch (ArithmeticException | IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            // O ajuste por mínimos quadrados resolve um sistema que fica singular quando a
            // série é perfeitamente regular ou a ordem é grande de mais para os dados. O
            // Smile deixa passar o erro cru do LAPACK ("POTRF error code"), que não diz
            // nada a quem está a usar o programa.
            final String message = String.valueOf(e.getMessage());
            if (message.contains("POTRF") || message.contains("LAPACK") || message.contains("singular")) {
                throw new IllegalArgumentException(request.method()
                        + " could not be fitted: the series is too regular or the order is too high for "
                        + series.length + " points. Lower the order, or set differencing to 1 to remove "
                        + "the trend first.", e);
            }
            throw e;
        }
    }

    private static Result fromAr(final AR model, final double[] series, final Request request) {
        final LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("R2", model.R2());
        metrics.put("Adjusted R2", model.adjustedR2());
        metrics.put("RSS", model.RSS());
        metrics.put("Variance", model.variance());
        metrics.put("Order p", model.p());
        metrics.put("Intercept", model.intercept());
        metrics.put("Coefficients", format(model.ar()));
        addDiagnostics(metrics, series);

        final String summary = String.format(Locale.US,
                "%s of order %d%nR2 = %.6g, residual variance = %.6g%nForecast of %d step(s): %s",
                request.method(), model.p(), model.R2(), model.variance(),
                request.horizon(), format(model.forecast(request.horizon())));

        return new Result(request.method(), series, model.fittedValues(),
                model.forecast(request.horizon()), metrics, summary);
    }

    private static Result fromArma(final ARMA model, final double[] series, final Request request) {
        final LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("R2", model.R2());
        metrics.put("RSS", model.RSS());
        metrics.put("Variance", model.variance());
        metrics.put("Order p", model.p());
        metrics.put("Order q", model.q());
        metrics.put("Intercept", model.intercept());
        metrics.put("AR coefficients", format(model.ar()));
        metrics.put("MA coefficients", format(model.ma()));
        addDiagnostics(metrics, series);

        final String summary = String.format(Locale.US,
                "ARMA(%d, %d)%nR2 = %.6g, residual variance = %.6g%nForecast of %d step(s): %s",
                model.p(), model.q(), model.R2(), model.variance(),
                request.horizon(), format(model.forecast(request.horizon())));

        return new Result(request.method(), series, model.fittedValues(),
                model.forecast(request.horizon()), metrics, summary);
    }

    /** Suavização exponencial, usando as classes já escritas no projeto. */
    private static Result fromSmoothing(final double[] series, final Request request) throws Exception {
        final double[] fitted;
        final double[] forecast;
        final LinkedHashMap<String, Object> metrics = new LinkedHashMap<>();

        // Os índices pedidos ao predict são os que vêm a seguir ao fim da série.
        final int[] horizonIndexes = new int[request.horizon()];
        for (int i = 0; i < request.horizon(); i++) horizonIndexes[i] = series.length + i;

        final int[] fittedIndexes = new int[series.length];
        for (int i = 0; i < series.length; i++) fittedIndexes[i] = i;

        switch (request.method()) {
            case SIMPLE_EXPONENTIAL -> {
                final SimpleExpSmoothing model = new SimpleExpSmoothing((float) request.alpha());
                model.fit(series);
                fitted = model.predict(fittedIndexes);
                forecast = model.predict(horizonIndexes);
                metrics.put("Alpha", request.alpha());
            }
            case HOLT -> {
                final ExponentialSmoothing model =
                        new ExponentialSmoothing((float) request.alpha(), (float) request.beta());
                model.fit(series);
                fitted = model.predict(fittedIndexes);
                forecast = model.predict(horizonIndexes);
                metrics.put("Alpha", request.alpha());
                metrics.put("Beta", request.beta());
            }
            default -> {
                final ExponentialGammaSmoothing model = new ExponentialGammaSmoothing(
                        (float) request.alpha(), (float) request.beta(),
                        (float) request.gamma(), request.seasonLength());
                model.fit(series);
                fitted = model.predict(fittedIndexes);
                forecast = model.predict(horizonIndexes);
                metrics.put("Alpha", request.alpha());
                metrics.put("Beta", request.beta());
                metrics.put("Gamma", request.gamma());
                metrics.put("Season length", request.seasonLength());
            }
        }

        // As classes de suavização não devolvem métricas; medem-se aqui sobre o ajuste.
        final double[] errors = new double[Math.min(series.length, fitted.length)];
        for (int i = 0; i < errors.length; i++) errors[i] = series[i] - fitted[i];

        double sumSquares = 0, sumAbsolute = 0;
        for (double error : errors) {
            sumSquares += error * error;
            sumAbsolute += Math.abs(error);
        }

        final double mse = errors.length == 0 ? Double.NaN : sumSquares / errors.length;
        metrics.put("MSE", mse);
        metrics.put("RMSE", Math.sqrt(mse));
        metrics.put("MAE", errors.length == 0 ? Double.NaN : sumAbsolute / errors.length);
        addDiagnostics(metrics, series);

        final String summary = String.format(Locale.US,
                "%s%nRMSE on the fitted values = %.6g%nForecast of %d step(s): %s",
                request.method(), Math.sqrt(mse), request.horizon(), format(forecast));

        return new Result(request.method(), series, fitted, forecast, metrics, summary);
    }

    /**
     * Autocorrelação nos primeiros desfasamentos.
     *
     * <p>É o que diz se a série tem memória e de que ordem — sem isto a escolha de p e q
     * fica ao calhas.</p>
     */
    private static void addDiagnostics(final LinkedHashMap<String, Object> metrics, final double[] series) {
        final StringBuilder acf = new StringBuilder();
        final StringBuilder pacf = new StringBuilder();
        final int lags = Math.min(5, series.length - 1);

        for (int lag = 1; lag <= lags; lag++) {
            if (lag > 1) {
                acf.append(", ");
                pacf.append(", ");
            }
            acf.append(String.format(Locale.US, "%d:%.3f", lag, TimeSeries.acf(series, lag)));
            try {
                pacf.append(String.format(Locale.US, "%d:%.3f", lag, TimeSeries.pacf(series, lag)));
            } catch (Exception _) {
                pacf.append(lag).append(":n/a");
            }
        }

        metrics.put("ACF", acf.toString());
        metrics.put("PACF", pacf.toString());
    }

    /** Diferenciação, para estabilizar uma série com tendência antes de a modelar. */
    public static double[] difference(final double[] series, final int order) {
        if (order <= 0) return series;
        return TimeSeries.diff(series, order);
    }

    private static String format(final double[] values) {
        if (values == null || values.length == 0) return "";
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.length && i < 12; i++) {
            if (i > 0) text.append(", ");
            text.append(String.format(Locale.US, "%.6g", values[i]));
        }
        if (values.length > 12) text.append(", ...");
        return text.toString();
    }

}
