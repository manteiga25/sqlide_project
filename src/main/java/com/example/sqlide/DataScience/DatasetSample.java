package com.example.sqlide.DataScience;

import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.model.Interfaces.DatabaseExecutorInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;
import com.example.sqlide.drivers.model.SQLTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Bloco de linhas trazido da base de dados para memória.
 *
 * <p>Os agregados vivem no SGBD (ver {@link StatisticsService}), mas há operações que
 * precisam mesmo das linhas: o gráfico de dispersão, os resíduos do modelo, a
 * pré-visualização da imputação. É isso que esta classe carrega — um bloco de tamanho
 * controlado, nunca a tabela inteira.</p>
 *
 * <p>Cada linha traz também a sua chave, para que uma imputação possa ser escrita de volta
 * na linha certa. A chave é a chave primária da tabela, ou o identificador interno do motor
 * quando existe.</p>
 */
public class DatasetSample {

    private final List<String> columns;
    private final List<String> keyColumns;
    private final List<Map<String, String>> rows;

    private DatasetSample(List<String> columns, List<String> keyColumns, List<Map<String, String>> rows) {
        this.columns = columns;
        this.keyColumns = keyColumns;
        this.rows = rows;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<String> getKeyColumns() {
        return keyColumns;
    }

    public List<Map<String, String>> getRows() {
        return rows;
    }

    public int size() {
        return rows.size();
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }

    /** True se as linhas podem ser reescritas individualmente na base de dados. */
    public boolean isWritable() {
        return !keyColumns.isEmpty();
    }

    /** Valores de uma coluna como doubles, com null onde o valor falta ou não é numérico. */
    public Double[] numericColumn(String column) {
        Double[] values = new Double[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            values[i] = parse(rows.get(i).get(column));
        }
        return values;
    }

    public String valueAt(int rowIndex, String column) {
        return rows.get(rowIndex).get(column);
    }

    public Map<String, String> keyAt(int rowIndex) {
        Map<String, String> key = new HashMap<>(keyColumns.size());
        for (String keyColumn : keyColumns) key.put(keyColumn, rows.get(rowIndex).get(keyColumn));
        return key;
    }

    private static Double parse(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return null;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==== Carregamento ====

    /**
     * Traz até {@code limit} linhas com as colunas pedidas, mais o que for preciso para
     * identificar cada linha.
     *
     * @param onlyWhereNull se não for null, restringe às linhas em que essa coluna é nula
     */
    public static DatasetSample load(DatabaseFetcherInterface fetcher,
                                     DatabaseExecutorInterface executor,
                                     SQLTypes dialect,
                                     String table,
                                     List<ColumnMetadata> metadata,
                                     List<String> wantedColumns,
                                     String onlyWhereNull,
                                     int limit,
                                     int offset) {

        List<String> keyColumns = resolveKeyColumns(metadata, dialect);

        // A projeção junta as colunas pedidas às da chave, sem repetir.
        List<String> projection = new ArrayList<>(wantedColumns);
        for (String key : keyColumns) if (!projection.contains(key)) projection.add(key);

        StringBuilder select = new StringBuilder("SELECT ");
        for (int i = 0; i < projection.size(); i++) {
            if (i > 0) select.append(", ");
            select.append(executor.quoteIdentifier(projection.get(i)));
        }
        select.append(" FROM ").append(executor.quoteIdentifier(table));

        if (onlyWhereNull != null && !onlyWhereNull.isBlank()) {
            select.append(" WHERE ").append(executor.quoteIdentifier(onlyWhereNull)).append(" IS NULL");
        }

        select.append(" LIMIT ").append(limit).append(" OFFSET ").append(offset);

        ArrayList<HashMap<String, String>> fetched = fetcher.fetchRawDataMap(select.toString());
        List<Map<String, String>> rows = new ArrayList<>();
        if (fetched != null) rows.addAll(fetched);

        return new DatasetSample(new ArrayList<>(wantedColumns), keyColumns, rows);
    }

    /**
     * Como endereçar uma linha: primeiro a chave primária, que existe em qualquer motor;
     * senão o identificador interno, que só o SQLite e o PostgreSQL expõem.
     *
     * <p>Devolve lista vazia quando não há forma segura de identificar uma linha — o
     * MySQL sem chave primária cai neste caso, e a UI avisa em vez de escrever ao calhas.</p>
     */
    private static List<String> resolveKeyColumns(List<ColumnMetadata> metadata, SQLTypes dialect) {
        List<String> primaryKey = metadata.stream()
                .filter(column -> column.IsPrimaryKey)
                .map(column -> column.Name)
                .toList();

        if (!primaryKey.isEmpty()) return new ArrayList<>(primaryKey);

        return switch (dialect) {
            case SQLITE -> List.of("rowid");
            case POSTGRESQL -> List.of("ctid");
            default -> List.of();
        };
    }

}
