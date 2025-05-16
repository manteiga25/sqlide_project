package com.example.sqlide.AdvancedSearch;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.RecursiveTreeItem;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.apache.xmlbeans.impl.xb.xsdschema.AllNNI;

import java.awt.event.ActionEvent;
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

     private HashMap<String, ArrayList<String>> AllColumns;

    private ArrayList<String> columns;

    private final ObservableList<String> columnSelected = FXCollections.observableArrayList();

    private String Table;

    private String statementCode = "";

    private Stage stage;

    private boolean ClosedByUser = false;

    public boolean isClosedByUser() {
        return ClosedByUser;
    }

    public void removeBottomContainer() {
        Container.getChildren().remove(Container.getBottom());
    }

    @FXML
    private void initialize() {

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
            for (final String column : columns) {
                columnSelected.add(Table+"."+column);
            }
            for (final String column : AllColumns.get(text)) {
                columnSelected.add(text+"."+column);
            }
            loadWidgets(new ArrayList<>(columnSelected));
            generateQuery();
        });
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
            String sql = selectedColumns.isEmpty() ? "" : String.format("%s %s FROM %s %s %s %s",
                    statementCode,
                    selectedColumns,
                    Table,
                    JoinBox.getValue() == null || JoinBox.getValue().isEmpty() ? "" : JoinBox.getValue(),
                    TableJoinBox.getValue() == null || TableJoinBox.getValue().isEmpty() ? "" : TableJoinBox.getValue(),
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
        return columns.size() != columnSelected.size() ? sb.toString() : "*";
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

            header.getChildren().addAll(
                    new Label("Group operator:"),
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
