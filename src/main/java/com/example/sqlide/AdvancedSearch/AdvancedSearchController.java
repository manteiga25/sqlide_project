package com.example.sqlide.AdvancedSearch;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.Report.ReportController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.exporter.XML.xmlController;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.RecursiveTreeItem;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.xmlbeans.impl.xb.xsdschema.AllNNI;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AdvancedSearchController {

    @FXML
    private BorderPane Container;

    @FXML
    private VBox ColumnsContainer, ConditionBox;

    @FXML
    private ChoiceBox<String> JoinBox, TableJoinBox;

     @FXML
    private JFXTextField QueryField;

    @FXML // Added for Part 2
    private JFXButton generateReportButton;

     private HashMap<String, ArrayList<String>> AllColumns;

    private ArrayList<String> columns;

    private String selector = "*";

    private final ObservableList<String> columnSelected = FXCollections.observableArrayList();

    private String Table;

    private String statementCode = "";

    private Stage stage, ordenateStage;

    private OrderingController controller;

    private boolean ClosedByUser = false;

    public boolean isClosedByUser() {
        return ClosedByUser;
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

    @FXML
    private void initialize() throws IOException {

        Container.disableProperty().addListener(_->QueryField.setText(""));

        JoinBox.getItems().addAll("", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "CROSS JOIN", "SELF JOIN", "NATURAL JOIN");
        JoinBox.getSelectionModel().selectedIndexProperty().addListener((_, _, text)->{
            TableJoinBox.setDisable(text.intValue() == 0);
            generateQuery();
        });
        TableJoinBox.getSelectionModel().selectedItemProperty().addListener((_, _, text)->{
            ColumnsContainer.getChildren().removeAll();
            ColumnsContainer.getChildren().clear();
            columnSelected.clear();
            final ArrayList<String> list = new ArrayList<>();
            for (final String column : columns) {
                list.add(Table+"."+column);
            }
            for (final String column : AllColumns.get(text)) {
                list.add(text+"."+column);
            }
            loadWidgets(list);
            generateQuery();
        });

        loadOrdenateController();

    }

    private void loadOrdenateController() throws IOException {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ordenate/OrdenateStage.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            controller = loader.getController();

            // Criar um novo Stage para a subjanela
            ordenateStage = new Stage();
            ordenateStage.setTitle("Subjanela");
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

    public void setColumns(final HashMap<String, ArrayList<String>> columns) {
        this.columns = columns.get(Table);
        AllColumns = columns;
        AllColumns.remove(Table);
        loadWidgets(this.columns);
        loadJoin();
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
        TableJoinBox.getItems().add("");
        for (final String Table : AllColumns.keySet()) {
            TableJoinBox.getItems().add(Table);
        }
    }

    @FXML
    private void handleGenerateReport() {

        String currentQuery = getQuery();
        ArrayList<String> availableColumns = getSelected(); // This gets columns selected for output

        if (currentQuery.isEmpty() || availableColumns.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("No Query/Columns");
            alert.setHeaderText("Cannot Generate Report");
            alert.setContentText("Please define a query and select columns first.");
            alert.showAndWait();
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Report.fxml"));
            Parent root = loader.load();

            ReportController dialogController = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Configure Report");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            if (generateReportButton != null && generateReportButton.getScene() != null) {
                dialogStage.initOwner(generateReportButton.getScene().getWindow());
            }

         //   dialogController.initializeDialog(availableColumns, currentQuery, db, dialogStage);

            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Load Error");
            alert.setHeaderText("Could not load report configuration dialog.");
            alert.setContentText("Error: " + e.getMessage());
            alert.showAndWait();
        }
    }


    @FXML
    private void addConditionRow() {
        ConditionBox.getChildren().add(new ConditionRow());
    }

    @FXML
    private void addConditionGroup() {
        ConditionBox.getChildren().add(new ConditionGroup());
    }

    private void generateQuery() {
        try {
            String selectedColumns = getSelectedColumns();
            String whereClause = buildWhereClause();
            String rules = controller.getRules();
            String sql = selectedColumns.isEmpty() ? "" : String.format("%s %s FROM %s %s %s %s %s",
                    statementCode,
                    selectedColumns,
                    Table,
                    JoinBox.getValue() == null || JoinBox.getValue().isEmpty() ? "" : JoinBox.getValue(),
                    TableJoinBox.getValue() == null || TableJoinBox.getValue().isEmpty() ? "" : TableJoinBox.getValue(),
                    rules.isEmpty() ? rules : "ORDER BY " + rules,
                    whereClause.isEmpty() ? "" : "WHERE " + whereClause
            );

            QueryField.setText(sql);
            // Aqui você adicionaria a lógica real de execução da consulta
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    private String getSelectedColumns() {
        StringBuilder sb = new StringBuilder();
        for (final String column : columnSelected) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(column);
        }
        return columns.size() != columnSelected.size() ? sb.toString() : selector;
    }

    private String buildWhereClause() {
        StringBuilder sb = new StringBuilder();
        for (javafx.scene.Node node : ConditionBox.getChildren()) {
            if (node instanceof ConditionRow) {
                String condition = ((ConditionRow) node).getCondition();
                if (!condition.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" AND ");
                    sb.append(condition);
                }
            } else if (node instanceof ConditionGroup) {
                String group = ((ConditionGroup) node).getGroupCondition();
                if (!group.isEmpty()) {
                    if (!sb.isEmpty()) sb.append(" AND ");
                    sb.append("(").append(group).append(")");
                }
            }
        }
        return sb.toString();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    // Classe para representar uma linha de condição
    class ConditionRow extends HBox {
        private final ComboBox<String> columnCombo;
        private final ComboBox<String> operatorCombo;
        private final ComboBox<String> TypeCombo;
        private final ComboBox<String> ColumnBox;
        private final JFXTextField valueField;

        public ConditionRow() {
            super(5);
            setPadding(new Insets(5));

            ColumnBox = new ComboBox<>(FXCollections.observableArrayList(columnSelected));
            ColumnBox.setPromptText("column");
            ColumnBox.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());

            valueField = new JFXTextField();
            valueField.textProperty().addListener(_->generateQuery());
            valueField.setStyle("-fx-text-fill: #f2f2f2;");

            columnCombo = new ComboBox<>(FXCollections.observableArrayList(columns));
            columnCombo.setPromptText("column");
            columnCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());

            TypeCombo = new ComboBox<>(FXCollections.observableArrayList("String", "Number", "Column Value"));
            TypeCombo.setPromptText("Type");
            TypeCombo.getSelectionModel().select("String");
            TypeCombo.getSelectionModel().selectedItemProperty().addListener((_, oldItem, item)->{
                if (!item.equals(oldItem)) {
                    changeWidget(item);
                   // generateQuery();
                }
            });

            operatorCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "=", "!=", ">", "<", ">=", "<=", "LIKE", "IN"
            ));
            operatorCombo.setPromptText("operator");
            operatorCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());

            ComboBox<String> logicCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "AND", "OR"
            ));
            logicCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());

            Button removeBtn = new Button("X");
            removeBtn.setStyle("-fx-background-color: red; -fx-border-color: transparent; -fx-text-fill: white;");
            removeBtn.setOnAction(_ -> {
                ConditionBox.getChildren().remove(this);
                generateQuery();
            });

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
            }
        }

        private String getValue() {
            return switch (TypeCombo.getValue()) {
                case "String" ->
                        valueField.getText().isEmpty() ? "" : "'" + valueField.getText() + "'";
                case "Number" -> valueField.getText().isEmpty() ? "" : valueField.getText();
                case "Column Value" ->
                        ColumnBox.getValue();
                default -> "";
            };
        }

        public String getCondition() {
            if (columnCombo.getValue() == null ||
                    operatorCombo.getValue() == null ||
                    getValue() == null || getValue().isEmpty()) {
                return "";
            }

            return String.format("%s %s %s",
                    columnCombo.getValue(),
                    operatorCombo.getValue(),
                    getValue());
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
            groupLogic = new ComboBox<>(FXCollections.observableArrayList("AND", "OR"));
            groupLogic.setPromptText("logic");
            groupLogic.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());
            JFXButton addConditionBtn = new JFXButton("+ Condition");
            JFXButton addSubGroupBtn = new JFXButton("+ Sub Group");
            Button removeBtn = new Button("X");
            removeBtn.setStyle("-fx-background-color: red; -fx-border-color: transparent; -fx-text-fill: white;");

            final Label label = new Label("Group operator:");
            label.setTextFill(Color.WHITE);

            header.getChildren().addAll(
                    label,
                    groupLogic,
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
            removeBtn.setOnAction(_ -> {

                getChildren().clear();

                ColumnsContainer.getChildren().remove(this);
                setStyle("-fx-border-width: 0;");
                generateQuery();
            });

            getChildren().addAll(header, groupConditions);
        }

        public String getGroupCondition() {
            StringBuilder sb = new StringBuilder();
            for (javafx.scene.Node node : groupConditions.getChildren()) {
                if (node instanceof ConditionRow) {
                    String condition = ((ConditionRow) node).getCondition();
                    if (!condition.isEmpty()) {
                        if (!sb.isEmpty()) sb.append(" ").append(groupLogic.getValue()).append(" ");
                        sb.append(condition);
                    }
                } else if (node instanceof ConditionGroup) {
                    String subGroup = ((ConditionGroup) node).getGroupCondition();
                    if (!subGroup.isEmpty()) {
                        if (!sb.isEmpty()) sb.append(" ").append(groupLogic.getValue()).append(" ");
                        sb.append("(").append(subGroup).append(")");
                    }
                }
            }
            return sb.toString();
        }
    }

    @FXML
    private void close() {
        ClosedByUser = true;
        stage.close();
    }

}
