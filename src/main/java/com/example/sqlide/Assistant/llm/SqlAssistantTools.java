package com.example.sqlide.Assistant.llm;

import com.example.sqlide.requestInterface;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ferramentas que o modelo pode invocar sobre a base de dados aberta.
 *
 * <p>Substitui as funções que viviam no {@code aida.py}: em vez de um processo Python a
 * trocar JSON por stdin/stdout, o LangChain4j chama estes métodos diretamente e devolve
 * o resultado ao modelo.</p>
 *
 * <p>Cada método notifica um {@code activityListener} antes de trabalhar, para a UI poder
 * mostrar o que está a acontecer — é o equivalente ao campo {@code message} do protocolo antigo.</p>
 */
public class SqlAssistantTools {

    /** Quantas linhas de uma consulta vão para o contexto do modelo antes de truncar. */
    private static final int MAX_ROWS_RETURNED = 200;

    private final requestInterface request;
    private Consumer<String> activityListener = _ -> {
    };

    public SqlAssistantTools(requestInterface request) {
        this.request = request;
    }

    public void setActivityListener(Consumer<String> activityListener) {
        this.activityListener = activityListener == null ? _ -> {
        } : activityListener;
    }

    private void reportActivity(String message) {
        activityListener.accept(message);
    }

    // ==== Contexto ====

    @Tool("""
            Returns the SQL dialect of the open database (SQLITE, MYSQL, POSTGRESQL, MS_ACCESS).
            Call this before writing any triggers, functions, procedures, events or views,
            so the generated SQL matches the engine.""")
    public String getSqlDialect() {
        reportActivity("Reading SQL dialect");
        return request.getSQLType().name();
    }

    @Tool("""
            Returns the name of the table the user is currently looking at.
            Use it whenever the user says "this table" or omits the table name.
            An empty string means no table is selected.""")
    public String getCurrentTable() {
        reportActivity("Reading current table");
        String table = request.currentTable();
        return table == null ? "" : table;
    }

    @Tool("""
            Returns every table in the schema with its columns and types.
            Call this before writing queries so column names and types are correct.""")
    public String getSchemaMetadata() {
        reportActivity("Reading schema metadata");
        Map<String, ArrayList<HashMap<String, String>>> schema = request.getTableMetadata();
        if (schema == null || schema.isEmpty()) return "The schema has no tables.";

        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, ArrayList<HashMap<String, String>>> table : schema.entrySet()) {
            text.append("TABLE ").append(table.getKey()).append('\n');
            for (Map<String, String> column : table.getValue()) {
                text.append("  ").append(column.getOrDefault("Name", "?"))
                        .append(' ').append(column.getOrDefault("Type", "?"));
                if ("true".equalsIgnoreCase(column.get("IsPrimaryKey"))) text.append(" PRIMARY KEY");
                if ("true".equalsIgnoreCase(column.get("NOT_NULL"))) text.append(" NOT NULL");
                text.append('\n');
            }
        }
        return text.toString();
    }

    // ==== Leitura de dados ====

    @Tool("""
            Runs a read-only SELECT and returns the rows to you, so you can reason about the data.
            Keep the result small: add your own WHERE and LIMIT to the query.""")
    public String queryData(
            @P("The full SELECT statement to run") String query,
            @P("Table the query reads from; use getCurrentTable() if the user did not say") String table) {
        reportActivity("Querying " + table);
        List<HashMap<String, String>> rows = request.getData(query, table);
        if (rows == null || rows.isEmpty()) return "The query returned no rows.";

        // Devolver texto em vez da estrutura: o serializador do LangChain4j lida mal com
        // coleções aninhadas, e o modelo lê melhor uma tabela do que JSON encaixado.
        StringBuilder text = new StringBuilder();
        int limit = Math.min(rows.size(), MAX_ROWS_RETURNED);
        for (int i = 0; i < limit; i++) {
            text.append(rows.get(i).entrySet().stream()
                    .map(cell -> cell.getKey() + "=" + cell.getValue())
                    .reduce((a, b) -> a + " | " + b).orElse("")).append('\n');
        }
        if (rows.size() > limit) {
            text.append("... ").append(rows.size() - limit).append(" more row(s) not shown.\n");
        }
        return text.toString();
    }

    @Tool("""
            Runs a SELECT and displays the result to the user in a new data window.
            Use this when the user asks to *see* data, rather than when you need the data yourself.
            Do not put LIMIT or OFFSET in the query.""")
    public boolean showData(
            @P("The full SELECT statement to display") String query,
            @P("Table the query reads from; use getCurrentTable() if the user did not say") String table) {
        reportActivity("Showing data from " + table);
        return request.ShowData(query, table);
    }

    // ==== Escrita de esquema ====

    @Tool("Creates a table in the open database.")
    public boolean createTable(
            @P("Name of the new table") String tableName,
            @P("Columns of the table") List<ColumnDefinition> columns,
            @P("Optional CHECK constraint body; empty string if the user did not ask for one") String check) {
        reportActivity("Creating table " + tableName);
        ArrayList<HashMap<String, String>> meta = new ArrayList<>();
        for (ColumnDefinition column : columns) meta.add(column.toMap());
        return request.createTable(tableName, meta, check == null ? "" : check);
    }

    @Tool("Creates a view over a table. Call getSqlDialect() first so the SQL is valid for the engine.")
    public boolean createView(
            @P("Table the view reads from") String table,
            @P("Name of the view") String name,
            @P("The full CREATE VIEW body") String code) {
        reportActivity("Creating view " + name);
        return request.createView(table, name, code);
    }

    @Tool("Creates a trigger. Call getSqlDialect() first so the SQL is valid for the engine.")
    public boolean createTrigger(
            @P("Name of the trigger") String name,
            @P("The full trigger definition") String code) {
        reportActivity("Creating trigger " + name);
        return request.createTriggers(singleEntry(name, code));
    }

    @Tool("Creates a stored function. Call getSqlDialect() first so the SQL is valid for the engine.")
    public boolean createFunction(
            @P("Name of the function") String name,
            @P("The full function definition") String code) {
        reportActivity("Creating function " + name);
        return request.createFunction(singleEntry(name, code));
    }

    @Tool("Creates a stored procedure. Call getSqlDialect() first so the SQL is valid for the engine.")
    public boolean createProcedure(
            @P("Name of the procedure") String name,
            @P("The full procedure definition") String code) {
        reportActivity("Creating procedure " + name);
        return request.createProcedure(singleEntry(name, code));
    }

    @Tool("Creates a scheduled event. Call getSqlDialect() first so the SQL is valid for the engine.")
    public boolean createEvent(
            @P("Name of the event") String name,
            @P("The full event definition") String code) {
        reportActivity("Creating event " + name);
        return request.createEvents(singleEntry(name, code));
    }

    // ==== Escrita de dados ====

    @Tool("""
            Inserts rows into a table. Give each row as a list of column/value pairs, with the
            value as text — the application converts it to the column's real type.
            Call getSchemaMetadata() first to get the exact column names.""")
    public String insertRows(
            @P("Table to insert into; use getCurrentTable() if the user did not say") String table,
            @P("Rows to insert") List<RowValues> rows) {
        if (rows == null || rows.isEmpty()) return "No rows given.";
        reportActivity("Inserting " + rows.size() + " row(s) into " + table);

        ArrayList<LinkedHashMap<String, String>> payload = new ArrayList<>(rows.size());
        for (RowValues row : rows) payload.add(row.toMap());

        String error = request.insertData(table, payload);
        return (error == null || error.isEmpty()) ? "Inserted " + rows.size() + " row(s)." : "Error: " + error;
    }

    // ==== Saídas ====

    @Tool("""
            Generates a report for the user from a SELECT statement.
            Call getSchemaMetadata() and getCurrentTable() first to build a correct query.""")
    public boolean createReport(
            @P("Title shown on the report") String title,
            @P("The SELECT statement that feeds the report") String query) {
        reportActivity("Creating report " + title);
        return request.createReport(title, query);
    }

    @Tool("""
            Opens the e-mail composer with an HTML body you generate.
            To merge values from the database use placeholder tags of the form
            <DataSrc=tableName:columnName/> inside the HTML; call getSchemaMetadata()
            first so the table and column names in those tags are real.""")
    public boolean composeEmail(
            @P("The full HTML body of the e-mail") String htmlBody) {
        reportActivity("Composing e-mail");
        return request.sendEmail(htmlBody);
    }

    @Tool("""
            Creates a chart for the user.
            Call getSchemaMetadata() first so the column names in the series are real.""")
    public boolean createChart(
            @P("Table to read from") String table,
            @P("Title of the chart") String name,
            @P("Label of the X axis") String axisX,
            @P("Label of the Y axis") String axisY,
            @P("The series to plot") List<ChartSeries> series) {
        reportActivity("Creating chart " + name);
        ArrayList<HashMap<String, String>> labels = new ArrayList<>();
        for (ChartSeries entry : series) labels.add(entry.toMap());
        return request.createGraphic(table, name, axisX, axisY, labels);
    }

    private static HashMap<String, String> singleEntry(String key, String value) {
        HashMap<String, String> map = new HashMap<>(1);
        map.put(key, value);
        return map;
    }

    /**
     * Uma linha a inserir.
     *
     * <p>A forma óbvia — {@code List<Map<String,String>>} — não pode ser usada: o gerador de
     * schema do LangChain4j rebenta com
     * {@code ParameterizedTypeImpl cannot be cast to Class} quando encontra um genérico
     * dentro de outro genérico. Uma lista de records simples produz o mesmo JSON e passa.</p>
     */
    public record RowValues(
            @P("The cells of this row") List<CellValue> cells) {

        LinkedHashMap<String, String> toMap() {
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            if (cells != null) for (CellValue cell : cells) map.put(cell.column(), cell.value());
            return map;
        }
    }

    /** Um par coluna/valor de uma linha a inserir. */
    public record CellValue(
            @P("Column name") String column,
            @P("Value as text") String value) {
    }

    /**
     * Uma coluna de uma tabela a criar. Os nomes das chaves do mapa são os que o
     * {@code requestInterface} já esperava, para não mexer no lado da base de dados.
     */
    public record ColumnDefinition(
            @P("Column name") String name,
            @P("SQL type, e.g. INTEGER, TEXT, VARCHAR(255)") String type,
            @P("One of: PRIMARY KEY, FOREIGN KEY, NO KEY") String key,
            @P("True if the column must be NOT NULL") boolean notNull) {

        HashMap<String, String> toMap() {
            HashMap<String, String> map = new HashMap<>(4);
            map.put("Name", name);
            map.put("Type", type);
            map.put("Key", key == null || key.isBlank() ? "NO KEY" : key);
            map.put("NotNull", String.valueOf(notNull));
            return map;
        }
    }

    /** Uma série de um gráfico, no formato de rótulo que o construtor de gráficos consome. */
    public record ChartSeries(
            @P("Aggregate to apply: SUM, AVG, COUNT, MIN or MAX") String func,
            @P("Column the aggregate is applied to") String column,
            @P("Group name; series sharing a group are drawn together") String group,
            @P("Unique name identifying this series") String category,
            @P("The SELECT statement that feeds this series") String query) {

        HashMap<String, String> toMap() {
            HashMap<String, String> map = new HashMap<>(5);
            map.put("func", func);
            map.put("column", column);
            map.put("group", group);
            map.put("category", category);
            map.put("query", query);
            return map;
        }
    }

}
