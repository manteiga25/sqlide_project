package com.example.sqlide.AdvancedSearch;

import com.example.sqlide.drivers.model.QueryBuilder;
import com.example.sqlide.drivers.model.QueryBuilder.Condition;
import com.example.sqlide.drivers.model.QueryBuilder.Join;
import com.example.sqlide.drivers.model.QueryBuilder.Logic;
import com.example.sqlide.drivers.model.QueryBuilder.Operands;
import com.example.sqlide.drivers.model.QueryBuilder.Operator;
import com.example.sqlide.drivers.model.QueryBuilder.Order;
import com.example.sqlide.drivers.model.SQLTypes;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import com.example.sqlide.Function.RoutineCatalogController;
import com.example.sqlide.Metadata.RoutineMetadata;

import java.io.IOException;
import java.util.*;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Construtor visual de consultas (pesquisa avançada, apagar avançado, gráficos, relatórios e
 * exportações).
 *
 * <p>A consulta passou a ser montada pelo {@link QueryBuilder}. O que estava mal:</p>
 * <ul>
 *   <li>O ORDER BY ia antes do WHERE: {@code SELECT * FROM t ORDER BY a WHERE b = 1} não é
 *       SQL válido em motor nenhum.</li>
 *   <li>O JOIN saía sem ON ({@code FROM a INNER JOIN b}), e havia um "SELF JOIN" que não é
 *       uma palavra do SQL. Agora escolhem-se as duas colunas do ON, com uma sugestão
 *       automática, e só aparecem os JOIN que o motor aceita.</li>
 *   <li>O AND/OR de cada condição era ignorado — era sempre AND — e um grupo sem operador
 *       escolhido escrevia "null" no meio da consulta.</li>
 *   <li>O X de um grupo tirava-o do sítio errado: o grupo ficava vazio no ecrã mas as
 *       condições continuavam no WHERE. O X de uma condição dentro de um grupo não fazia
 *       nada.</li>
 *   <li>Texto com apóstrofos partia a consulta; IN com texto dava {@code IN 'a,b'};
 *       IS com "String" dava {@code IS 'NULL'}. Agora há IS NULL, IS NOT NULL, BETWEEN,
 *       NOT LIKE e listas separadas por vírgulas.</li>
 *   <li>Mudar a tabela do JOIN apagava da lista as colunas de função/sub-consulta, mas elas
 *       continuavam no SELECT; tirar a tabela do JOIN deixava as colunas com o prefixo.</li>
 *   <li>O botão Cancel não fazia nada, e depois de um Confirm a janela voltava a executar a
 *       consulta sempre que era reaberta.</li>
 * </ul>
 */
public class AdvancedSearchController {

    @FXML
    private BorderPane Container;

    @FXML
    private VBox ColumnsContainer, ConditionBox;

    @FXML
    private ChoiceBox<String> JoinBox, TableJoinBox, LeftJoinColumn, RightJoinColumn;

    @FXML
    private HBox JoinOnBox;

    @FXML
    private JFXTextField QueryField;

    @FXML
    private JFXButton ordenateButton;

    @FXML
    private Label StatusLabel;

    private HashMap<String, ArrayList<String>> AllColumns = new HashMap<>();

    private ArrayList<String> columns;

    private String selector = "*";

    private final ObservableList<String> columnSelected = FXCollections.observableArrayList(), currentColumns = FXCollections.observableArrayList();

    /** Coluna como aparece na interface ("clientes.nome") → como vai no SQL (com aspas quando é preciso). */
    private final LinkedHashMap<String, String> columnSql = new LinkedHashMap<>();

    private String Table;

    private String statementCode = "";

    private Stage stage, ordenateStage;

    private OrderingController controller;

    private boolean ClosedByUser = false;

    private QueryBuilder builder = QueryBuilder.of(SQLTypes.SQLITE).readable();

    private SQLTypes dialect = SQLTypes.SQLITE;

    public boolean isClosedByUser() {
        return ClosedByUser;
    }

    /**
     * True uma vez por cada Confirm. A janela é guardada e reaberta, e o aviso antigo ficava
     * ligado: a consulta voltava a ser executada logo que a janela abria outra vez.
     */
    public boolean consumeConfirmed() {
        final boolean confirmed = ClosedByUser;
        ClosedByUser = false;
        return confirmed;
    }

    private boolean disabled = false;

    public void setDisabled(final boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isDisabled(){
        return disabled;
    }

    public void removeBottomContainer() {
        Container.getChildren().remove(Container.getBottom());
    }

    public void removeLeft() {
        Container.getChildren().remove(Container.getLeft());
        generateQuery();
    }

    public void setSelector(final String selector) {
        this.selector = selector;
    }

    public ArrayList<String> getSelected() {
        return new ArrayList<>(columnSelected);
    }

    /** O dialeto decide as aspas dos nomes, os literais e os JOIN que se podem escolher. */
    public void setDialect(final SQLTypes dialect) {
        this.dialect = dialect == null ? SQLTypes.SQLITE : dialect;
        this.builder = QueryBuilder.of(this.dialect).readable();
        loadJoinTypes();
        generateQuery();
    }

    @FXML
    private void initialize() throws IOException {

        // Desligada (separadores da exportação) não há consulta; ao voltar a ligar, refaz-se.
        Container.disableProperty().addListener((_, _, off) -> {
            if (off) QueryField.setText("");
            else generateQuery();
        });

        loadJoinTypes();
        JoinBox.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> {
            final Join join = Join.fromText(JoinBox.getValue());
            TableJoinBox.setDisable(join == null);
            if (join == null && TableJoinBox.getValue() != null && !TableJoinBox.getValue().isEmpty()) {
                TableJoinBox.setValue(""); // o listener da tabela repõe as colunas sem prefixo
            }
            JoinOnBox.setDisable(join == null || !join.needsCondition());
            generateQuery();
        });
        TableJoinBox.getSelectionModel().selectedItemProperty().addListener((_, _, text) -> reloadColumns(text));
        LeftJoinColumn.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> generateQuery());
        RightJoinColumn.getSelectionModel().selectedItemProperty().addListener((_, _, _) -> generateQuery());

        loadOrdenateController();

    }

    /** Só os JOIN que o motor conhece (o MySQL não tem FULL, o Access só tem INNER/LEFT/RIGHT). */
    private void loadJoinTypes() {
        final String current = JoinBox.getValue();
        final List<String> items = new ArrayList<>();
        items.add("");
        for (Join join : Join.values()) if (join.supportedBy(dialect)) items.add(join.keyword());
        JoinBox.getItems().setAll(items);
        JoinBox.setValue(items.contains(current) ? current : "");
    }

    private void loadOrdenateController() throws IOException {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ordenate/OrdenateStage.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            controller = loader.getController();

            // Criar um novo Stage para a subjanela
            ordenateStage = new Stage();
            ordenateStage.setTitle("Order by");
            ordenateStage.setScene(new Scene(root));
            ordenateStage.showingProperty().addListener(_->{
                    generateQuery();
            });
            controller.InflateOrderingResultController(columnSelected);

            // Opcional: definir a modalidade da subjanela
            ordenateStage.initModality(Modality.APPLICATION_MODAL);
    }

    @FXML
    private void openOrdenateStage() {
        ordenateStage.show();
    }

    public void setCode(final String code) {
        statementCode = code;
    }

    public void setTable(final String Table) {
        this.Table = Table;
    }

    public String getTable() {
        return Table;
    }

    public String getQuery() {
        return QueryField.getText();
    }

    public void setQuery(final String query) {
        QueryField.setText(query);
    }

    public void setColumns(final HashMap<String, ArrayList<String>> columns) {
        this.columns = new ArrayList<>(columns.getOrDefault(Table, new ArrayList<>()));
        AllColumns = new HashMap<>(columns);
        // Um JOIN da tabela com ela própria precisa de aliases, que este construtor não faz.
        AllColumns.remove(Table);
        loadJoin();
        reloadColumns(null);
    }

    /**
     * Refaz a lista de colunas: só as da tabela, ou as das duas tabelas com o nome da tabela
     * à frente quando há JOIN. As colunas de função e de sub-consulta ficam.
     */
    private void reloadColumns(final String joinTable) {
        if (columns == null) return;
        final boolean joined = joinTable != null && !joinTable.isBlank();

        ColumnsContainer.getChildren().clear();
        columnSelected.clear();
        columnSql.clear();

        final List<String> display = new ArrayList<>();
        for (final String column : columns) {
            final String name = joined ? Table + "." + column : column;
            display.add(name);
            columnSql.put(name, joined ? builder.qualified(Table, column) : builder.name(column));
        }
        if (joined) {
            for (final String column : AllColumns.getOrDefault(joinTable, new ArrayList<>())) {
                final String name = joinTable + "." + column;
                display.add(name);
                columnSql.put(name, builder.qualified(joinTable, column));
            }
        }
        currentColumns.setAll(display);
        loadWidgets(new ArrayList<>(display));
        ColumnsContainer.getChildren().addAll(expressionColumns);

        LeftJoinColumn.getItems().setAll(columns);
        RightJoinColumn.getItems().setAll(joined ? AllColumns.getOrDefault(joinTable, new ArrayList<>()) : List.of());
        if (joined) suggestJoinColumns(joinTable);

        generateQuery();
    }

    /**
     * Sugere o ON: cliente_id → cliente.id, id ← encomenda.cliente_id, ou a primeira coluna
     * com o mesmo nome nas duas tabelas. O utilizador pode sempre trocar.
     */
    private void suggestJoinColumns(final String joinTable) {
        final List<String> right = AllColumns.getOrDefault(joinTable, new ArrayList<>());
        final String[][] candidates = {
                {joinTable + "_id", "id"}, {joinTable + "id", "id"},
                {"id", Table + "_id"}, {"id", Table + "id"}};
        for (final String[] candidate : candidates) {
            final String left = find(columns, candidate[0]);
            final String other = find(right, candidate[1]);
            if (left != null && other != null) {
                LeftJoinColumn.setValue(left);
                RightJoinColumn.setValue(other);
                return;
            }
        }
        for (final String column : columns) {
            final String other = find(right, column);
            if (other != null) {
                LeftJoinColumn.setValue(column);
                RightJoinColumn.setValue(other);
                return;
            }
        }
        LeftJoinColumn.setValue(null);
        RightJoinColumn.setValue(null);
    }

    private static String find(final List<String> list, final String name) {
        for (final String item : list) if (item.equalsIgnoreCase(name)) return item;
        return null;
    }

    private void loadWidgets(final ArrayList<String> columns) {
        for (final String column : columns) {
            JFXCheckBox box = new JFXCheckBox(column);
            box.selectedProperty().addListener((ObservableValue<? extends Boolean> _, Boolean _, Boolean newValue) -> {
                if (newValue) {
                    columnSelected.add(column);
                } else {
                    columnSelected.remove(column);
                }
                generateQuery();
            });
            box.selectedProperty().set(true);
            ColumnsContainer.getChildren().add(box);
        }
    }

    private void loadJoin() {
        TableJoinBox.getItems().setAll("");
        final List<String> tables = new ArrayList<>(AllColumns.keySet());
        tables.sort(String.CASE_INSENSITIVE_ORDER);
        TableJoinBox.getItems().addAll(tables);
    }

    public void setSelectedColumn(final String column) {
        columnSelected.clear();
        columnSelected.add(column);
        generateQuery();
    }

    /** Rotinas do esquema, para o selector de funções. Vazio se a janela abrir sem elas. */
    private final ArrayList<RoutineMetadata> routines = new ArrayList<>();

    public void setRoutines(final List<RoutineMetadata> routines) {
        this.routines.clear();
        if (routines != null) this.routines.addAll(routines);
    }

    /** Abre o catálogo do esquema e escreve a chamada escolhida no campo indicado. */
    private void pickFunctionInto(final JFXTextField field) {
        if (routines.isEmpty()) {
            ShowInformation("No functions", "The routine catalog was not loaded for this database.");
            return;
        }
        final RoutineMetadata chosen = RoutineCatalogController.open(
                Container.getScene() == null ? null : Container.getScene().getWindow(), routines, true);
        if (chosen == null) return;

        // Acrescenta em vez de substituir, para dar para aninhar: ROUND(AVG(x), 2).
        final String existing = field.getText() == null ? "" : field.getText();
        field.setText(existing.isBlank() ? chosen.callTemplate() : existing + chosen.callTemplate());
        field.requestFocus();
        generateQuery();
    }

    /**
     * Acrescenta uma coluna calculada por função à lista do SELECT.
     *
     * <p>Fica ao lado das colunas normais, com o mesmo comportamento de seleção, para o
     * resto da geração da query não precisar de saber que é diferente.</p>
     */
    @FXML
    private void addFunctionColumn() {
        if (routines.isEmpty()) {
            ShowInformation("No functions", "The routine catalog was not loaded for this database.");
            return;
        }
        final RoutineMetadata chosen = RoutineCatalogController.open(
                Container.getScene() == null ? null : Container.getScene().getWindow(), routines, true);
        if (chosen == null) return;
        addExpressionColumn(chosen.callTemplate(), "function");
    }

    @FXML
    private void addSubQueryColumn() {
        addExpressionColumn("(SELECT ... )", "sub query");
    }

    /** Linha editável no painel das colunas, que entra no SELECT tal como está escrita. */
    private void addExpressionColumn(final String initial, final String kind) {
        final HBox row = new HBox(5);
        row.setPadding(new Insets(2, 0, 2, 0));

        final JFXCheckBox enabled = new JFXCheckBox();
        enabled.setSelected(true);

        final JFXTextField expression = new JFXTextField(initial);
        expression.setPromptText(kind);
        expression.setStyle("-fx-text-fill: #f2f2f2;");
        HBox.setHgrow(expression, Priority.ALWAYS);

        final JFXButton pick = new JFXButton("f(x)");
        pick.setTextFill(Color.WHITE);
        pick.setStyle("-fx-background-color: #3574F0; -fx-background-radius: 4px;");
        pick.setOnAction(_->pickFunctionInto(expression));

        final Button remove = new Button("X");
        remove.setStyle("-fx-background-color: red; -fx-border-color: transparent; -fx-text-fill: white;");
        remove.setOnAction(_->{
            expressionColumns.remove(row);
            ColumnsContainer.getChildren().remove(row);
            generateQuery();
        });

        enabled.selectedProperty().addListener(_->generateQuery());
        expression.textProperty().addListener(_->generateQuery());

        row.getChildren().addAll(enabled, expression, pick, remove);
        expressionColumns.add(row);
        ColumnsContainer.getChildren().add(row);
        generateQuery();
    }

    /** Linhas de expressão acrescentadas às colunas normais. */
    private final ArrayList<HBox> expressionColumns = new ArrayList<>();

    /** As expressões ativas, na ordem em que foram acrescentadas. */
    private ArrayList<String> getExpressionColumns() {
        final ArrayList<String> list = new ArrayList<>();
        for (final HBox row : expressionColumns) {
            final JFXCheckBox enabled = (JFXCheckBox) row.getChildren().getFirst();
            final JFXTextField expression = (JFXTextField) row.getChildren().get(1);
            if (enabled.isSelected() && !expression.getText().isBlank()) list.add(expression.getText().trim());
        }
        return list;
    }

    @FXML
    private void addConditionRow() {
        ConditionBox.getChildren().add(new ConditionRow());
        generateQuery();
    }

    @FXML
    private void addConditionGroup() {
        ConditionBox.getChildren().add(new ConditionGroup());
        generateQuery();
    }

    /** Monta a consulta: SELECT ... FROM ... JOIN ... WHERE ... ORDER BY, por esta ordem. */
    private void generateQuery() {
        if (Table == null || columns == null || QueryField == null) return; // ainda a ser montada
        try {
            final Condition where = buildCondition(ConditionBox.getChildren());
            final String sql;
            if ("DELETE".equalsIgnoreCase(statementCode)) {
                final QueryBuilder.Delete delete = builder.delete(Table);
                // Sem condições apaga tudo; a janela da grelha avisa antes de executar.
                if (where.isEmpty()) delete.allRows();
                else delete.where(where);
                sql = delete.build();
            } else {
                final List<String> selected = selectedColumnsSql();
                if (selected.isEmpty()) {
                    QueryField.setText("");
                    setStatus("Select at least one column.");
                    return;
                }
                final QueryBuilder.Select select = builder.select(selected).from(Table);

                final Join join = Join.fromText(JoinBox.getValue());
                final String joinTable = TableJoinBox.getValue();
                if (join != null && joinTable != null && !joinTable.isBlank()) {
                    final String left = LeftJoinColumn.getValue() == null ? null : builder.qualified(Table, LeftJoinColumn.getValue());
                    final String right = RightJoinColumn.getValue() == null ? null : builder.qualified(joinTable, RightJoinColumn.getValue());
                    select.join(join, joinTable, left, right);
                }

                select.where(where);
                if (controller != null) {
                    for (final Rule rule : controller.getActiveRules()) {
                        select.orderBy(sqlFor(rule.getColumn()), Order.fromText(rule.getRule()));
                    }
                }
                sql = select.build();
            }

            QueryField.setText(sql);
            setStatus(incompleteConditions > 0 ? incompleteConditions + " condition(s) are incomplete and were left out." : "");
        } catch (IllegalArgumentException | IllegalStateException e) {
            QueryField.setText("");
            setStatus(e.getMessage());
        }
    }

    private void setStatus(final String message) {
        if (StatusLabel != null) StatusLabel.setText(message == null ? "" : message);
    }

    /** Colunas do SELECT: "*" quando estão todas marcadas, senão a lista (pela ordem do ecrã). */
    private List<String> selectedColumnsSql() {
        final List<String> list = new ArrayList<>();
        final boolean all = !currentColumns.isEmpty() && new HashSet<>(columnSelected).containsAll(currentColumns)
                && columnSelected.size() == currentColumns.size();
        if (all) {
            list.add(selector.isBlank() ? "*" : selector);
        } else {
            for (final String column : currentColumns) if (columnSelected.contains(column)) list.add(sqlFor(column));
            // O que não é coluna da lista (a expressão de um gráfico, por exemplo) vai como está.
            for (final String column : columnSelected) if (!currentColumns.contains(column)) list.add(column);
        }
        list.addAll(getExpressionColumns());
        return list;
    }

    /** SQL de uma coluna escolhida na interface; tolera nomes com ou sem o prefixo da tabela. */
    private String sqlFor(final String display) {
        if (display == null) return null;
        final String direct = columnSql.get(display);
        if (direct != null) return direct;
        // Condição criada antes de escolher um JOIN: "nome" passa a "clientes.nome".
        final String withTable = columnSql.get(Table + "." + display);
        if (withTable != null) return withTable;
        // Condição criada com JOIN, que depois foi tirado: "clientes.nome" passa a "nome".
        final int dot = display.indexOf('.');
        if (dot > 0) {
            final String bare = columnSql.get(display.substring(dot + 1));
            if (bare != null && display.substring(0, dot).equals(Table)) return bare;
        }
        return null;
    }

    private int incompleteConditions = 0;

    /**
     * Condição de um contentor (a caixa principal ou um grupo). Cada linha liga-se à
     * seguinte com o AND/OR que tem à direita; o da última não conta.
     */
    private Condition buildCondition(final List<Node> nodes) {
        if (nodes == ConditionBox.getChildren()) incompleteConditions = 0;
        final Condition condition = builder.condition();
        Logic connector = Logic.AND;
        for (final Node node : nodes) {
            if (node instanceof ConditionRow row) {
                final String sql = row.getCondition();
                if (sql.isEmpty()) {
                    if (row.isStarted()) incompleteConditions++;
                    continue;
                }
                condition.add(connector, sql);
                connector = row.getLogic();
            } else if (node instanceof ConditionGroup group) {
                final Condition inner = group.condition();
                if (inner.isEmpty()) continue;
                condition.add(connector, inner);
                connector = group.getLogic();
            }
        }
        return condition;
    }

    private String buildWhereClause() {
        return buildCondition(ConditionBox.getChildren()).toString();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void removeOrder() {
        final HBox node = (HBox) ordenateButton.getParent();
        node.getChildren().remove(ordenateButton);
    }

    /** Tira uma linha ou um grupo de onde estiver (a caixa principal ou outro grupo). */
    private void removeFromParent(final Node node) {
        if (node.getParent() instanceof Pane parent) parent.getChildren().remove(node);
        generateQuery();
    }

    private static ComboBox<String> logicBox() {
        final ComboBox<String> logic = new ComboBox<>(FXCollections.observableArrayList("AND", "OR"));
        logic.setValue("AND");
        logic.setTooltip(new Tooltip("How this condition joins the next one"));
        return logic;
    }

    // Classe para representar uma linha de condição
    class ConditionRow extends HBox {
        private final ComboBox<String> columnCombo;
        private final ComboBox<String> operatorCombo;
        private final ComboBox<String> TypeCombo;
        private final ComboBox<String> ColumnBox;
        private final JFXTextField valueField;
        private final HBox FunctionBox;
        private final JFXTextField FunctionField;
        private final JFXTextField SubQueryField;
        private final ComboBox<String> logicCombo;

        public ConditionRow() {
            super(5);
            setPadding(new Insets(5));

            ColumnBox = new ComboBox<>(currentColumns);
            ColumnBox.setPromptText("column");
            ColumnBox.getSelectionModel().selectedItemProperty().addListener(_-> Platform.runLater(()->generateQuery()));

            // Chamada de função: campo editável com um botão que abre o catálogo do esquema.
            FunctionField = new JFXTextField();
            FunctionField.setPromptText("UPPER(name)");
            FunctionField.setStyle("-fx-text-fill: #f2f2f2;");
            FunctionField.textProperty().addListener(_->generateQuery());

            final JFXButton pickFunction = new JFXButton("f(x)");
            pickFunction.setTextFill(Color.WHITE);
            pickFunction.setStyle("-fx-background-color: #3574F0; -fx-background-radius: 4px;");
            pickFunction.setOnAction(_->pickFunctionInto(FunctionField));
            FunctionBox = new HBox(3, FunctionField, pickFunction);

            // Sub-consulta: escreve-se o SELECT e a query final envolve-o em parênteses.
            SubQueryField = new JFXTextField();
            SubQueryField.setPromptText("SELECT id FROM other WHERE ...");
            SubQueryField.setStyle("-fx-text-fill: #f2f2f2;");
            SubQueryField.textProperty().addListener(_->generateQuery());

            valueField = new JFXTextField();
            valueField.setPromptText("value");
            valueField.textProperty().addListener(_->generateQuery());
            valueField.setStyle("-fx-text-fill: #f2f2f2;");

            columnCombo = new ComboBox<>(currentColumns);
            columnCombo.setPromptText("column");
            columnCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());

            TypeCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "String", "Number", "Column Value", "Function", "Sub query"));
            TypeCombo.setPromptText("Type");
            TypeCombo.getSelectionModel().select("String");
            TypeCombo.getSelectionModel().selectedItemProperty().addListener((_, oldItem, item)->{
                if (item != null && !item.equals(oldItem)) {
                    changeWidget(item);
                    generateQuery();
                }
            });

            final List<String> operators = new ArrayList<>();
            for (Operator operator : Operator.values()) operators.add(operator.symbol());
            operatorCombo = new ComboBox<>(FXCollections.observableArrayList(operators));
            operatorCombo.setPromptText("operator");
            operatorCombo.getSelectionModel().selectedItemProperty().addListener((_, _, symbol)->{
                adaptToOperator(Operator.fromSymbol(symbol));
                generateQuery();
            });

            logicCombo = logicBox();
            logicCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());

            Button removeBtn = new Button("X");
            removeBtn.setStyle("-fx-background-color: red; -fx-border-color: transparent; -fx-text-fill: white;");
            removeBtn.setOnAction(_ -> removeFromParent(this));

            getChildren().addAll(
                    columnCombo,
                    TypeCombo,
                    operatorCombo,
                    valueField,
                    logicCombo,
                    removeBtn
            );
        }

        private void changeWidget(final String item) {
            switch (item) {
                case "String", "Number":
                    getChildren().set(3, valueField);
                    break;
                case "Column Value":
                    getChildren().set(3, ColumnBox);
                    break;
                case "Function":
                    getChildren().set(3, FunctionBox);
                    break;
                case "Sub query":
                    getChildren().set(3, SubQueryField);
                    break;
            }
        }

        /** IS NULL não leva valor; EXISTS não leva coluna e o valor é uma sub-consulta. */
        private void adaptToOperator(final Operator operator) {
            if (operator == null) return;
            final boolean subQuery = operator.operands() == Operands.SUB_QUERY;
            columnCombo.setDisable(subQuery);
            TypeCombo.setDisable(subQuery || operator.operands() == Operands.NONE);
            getChildren().get(3).setDisable(operator.operands() == Operands.NONE);
            if (subQuery) TypeCombo.getSelectionModel().select("Sub query");
            valueField.setPromptText(switch (operator.operands()) {
                case LIST -> "a, b, c";
                case TWO -> "from, to";
                default -> "value";
            });
        }

        Logic getLogic() {
            return Logic.fromText(logicCombo.getValue());
        }

        /** O utilizador já começou a preencher a linha (para avisar que ficou incompleta). */
        boolean isStarted() {
            return columnCombo.getValue() != null || operatorCombo.getValue() != null;
        }

        /** Os valores em SQL, conforme o tipo escolhido; lança IllegalArgumentException se estiverem mal. */
        private List<String> values(final Operator operator) {
            if (operator.operands() == Operands.NONE) return List.of();
            final String type = TypeCombo.getValue() == null ? "String" : TypeCombo.getValue();
            final boolean many = operator.operands() == Operands.LIST || operator.operands() == Operands.TWO;
            valueField.setStyle("-fx-text-fill: #f2f2f2;");
            return switch (type) {
                case "String" -> {
                    final String text = valueField.getText() == null ? "" : valueField.getText();
                    if (text.isEmpty()) yield List.of();
                    if (!many) yield List.of(builder.literal(text));
                    final List<String> items = new ArrayList<>();
                    // 'a, b' entre plicas é um só valor com vírgula.
                    for (String item : QueryBuilder.splitList(text)) items.add(builder.literal(QueryBuilder.unquote(item)));
                    yield items;
                }
                case "Number" -> {
                    final String text = valueField.getText() == null ? "" : valueField.getText().trim();
                    if (text.isEmpty()) yield List.of();
                    try {
                        if (!many) yield List.of(builder.number(text));
                        final List<String> items = new ArrayList<>();
                        for (String item : QueryBuilder.splitList(text)) items.add(builder.number(item));
                        yield items;
                    } catch (IllegalArgumentException e) {
                        valueField.setStyle("-fx-text-fill: #ff6b6b;");
                        throw e;
                    }
                }
                case "Column Value" -> {
                    final String column = sqlFor(ColumnBox.getValue());
                    yield column == null ? List.of() : List.of(column);
                }
                case "Function" -> FunctionField.getText() == null || FunctionField.getText().isBlank()
                        ? List.of() : List.of(FunctionField.getText().trim());
                // A sub-consulta vai entre parênteses, que é o que IN, EXISTS e a comparação
                // com um único valor exigem.
                case "Sub query" -> SubQueryField.getText() == null || SubQueryField.getText().isBlank()
                        ? List.of() : List.of("(" + QueryBuilder.stripTerminator(SubQueryField.getText()) + ")");
                default -> List.of();
            };
        }

        /** A condição em SQL, ou "" enquanto a linha não estiver completa. */
        public String getCondition() {
            final Operator operator = Operator.fromSymbol(operatorCombo.getValue());
            if (operator == null) return "";
            try {
                final String left = operator.usesColumn() ? sqlFor(columnCombo.getValue()) : null;
                if (operator.usesColumn() && left == null) return "";
                return builder.comparison(left, operator, values(operator));
            } catch (IllegalArgumentException e) {
                return "";
            }
        }
    }

    // Classe para representar um grupo de condições
    class ConditionGroup extends VBox {
        private final ComboBox<String> groupLogic;
        private final VBox groupConditions;

        public ConditionGroup() {
            super(5);
            setPadding(new Insets(5));
            setStyle("-fx-border-color: black; -fx-border-width: 1;");

            HBox header = new HBox(5);
            JFXButton addConditionBtn = new JFXButton("+ Condition");
            JFXButton addSubGroupBtn = new JFXButton("+ Sub Group");
            Button removeBtn = new Button("X");
            removeBtn.setStyle("-fx-background-color: red; -fx-border-color: transparent; -fx-text-fill: white;");

            final Label label = new Label("Group ( ... )");
            label.setTextFill(Color.WHITE);

            header.getChildren().addAll(
                    label,
                    addConditionBtn,
                    addSubGroupBtn,
                    removeBtn
            );

            groupConditions = new VBox(5);
            addConditionBtn.setOnAction(_ -> {
                groupConditions.getChildren().add(new ConditionRow());
                generateQuery();
            });
            addSubGroupBtn.setOnAction(_ -> {
                groupConditions.getChildren().add(new ConditionGroup());
                generateQuery();
            });
            // Antes procurava o grupo no painel das colunas e só o esvaziava: as condições
            // continuavam no WHERE.
            removeBtn.setOnAction(_ -> removeFromParent(this));

            // O conector do grupo com a condição seguinte fica em baixo, onde o grupo acaba.
            groupLogic = logicBox();
            groupLogic.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());
            final Label then = new Label("then");
            then.setTextFill(Color.WHITE);
            final HBox footer = new HBox(5, then, groupLogic);

            getChildren().addAll(header, groupConditions, footer);
        }

        Logic getLogic() {
            return Logic.fromText(groupLogic.getValue());
        }

        Condition condition() {
            return buildCondition(groupConditions.getChildren());
        }

        public String getGroupCondition() {
            return condition().toString();
        }
    }

    @FXML
    private void close() {
        ClosedByUser = true;
        if (stage != null) stage.close();
    }

    @FXML
    private void cancel() {
        ClosedByUser = false;
        if (stage != null) stage.close();
    }

}
