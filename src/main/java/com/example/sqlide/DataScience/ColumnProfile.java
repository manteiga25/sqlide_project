package com.example.sqlide.DataScience;

/**
 * Retrato estatístico de uma coluna, calculado sobre a tabela inteira.
 *
 * <p>Os campos numéricos ficam a {@link Double#NaN} para colunas não numéricas, para a
 * tabela de estatísticas poder mostrar a linha na mesma (contagens e nulos aplicam-se
 * a qualquer tipo).</p>
 */
public record ColumnProfile(
        String name,
        String type,
        boolean numeric,
        long totalRows,
        long nulls,
        long distinct,
        double min,
        double max,
        double mean,
        double median,
        double q1,
        double q3,
        double variance,
        double stdDev,
        double skewness,
        double kurtosis) {

    /** Percentagem de nulos, para a coluna de qualidade da tabela de colunas. */
    public double nullRatio() {
        return totalRows == 0 ? 0 : (double) nulls / totalRows;
    }

    public double iqr() {
        return q3 - q1;
    }

    /** Excesso de curtose: 0 numa distribuição normal. */
    public double excessKurtosis() {
        return kurtosis - 3.0;
    }

    /** Perfil vazio para colunas que não dá para medir (texto, ou tabela sem linhas). */
    public static ColumnProfile empty(String name, String type, boolean numeric, long totalRows, long nulls) {
        return new ColumnProfile(name, type, numeric, totalRows, nulls, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

}
