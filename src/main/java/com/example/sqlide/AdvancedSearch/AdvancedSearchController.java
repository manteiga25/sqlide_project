package com.example.sqlide.AdvancedSearch;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.xmlbeans.impl.xb.xsdschema.AllNNI;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class AdvancedSearchController {

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

    @FXML
    private void initialize() {
        JoinBox.getItems().addAll("", "INNER JOIN", "LEFT JOIN", "RIGHT JOIN", "FULL JOIN", "CROSS JOIN", "SELF JOIN", "NATURAL JOIN");
        JoinBox.getSelectionModel().selectedIndexProperty().addListener(_->generateQuery());
        TableJoinBox.getSelectionModel().selectedItemProperty().addListener(_->{
            ColumnsContainer.getChildren().removeAll();
            ColumnsContainer.getChildren().clear();
            columnSelected.clear();
            for (final String column : columns) {
                columnSelected.add(Table+"."+column);
            }
            for (final String column : AllColumns.get(TableJoinBox.getValue())) {
                columnSelected.add(Table+"."+column);
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
            String sql = String.format("%s %s FROM %s %s %s %s",
                    statementCode,
                    selectedColumns,
                    Table,
                    JoinBox.getValue(),
                    TableJoinBox.getValue(),
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
        return !sb.isEmpty() ? sb.toString() : "*";
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

    // Classe para representar uma linha de condição
    class ConditionRow extends HBox {
        private final ComboBox<String> columnCombo;
        private final ComboBox<String> operatorCombo;
        private final ComboBox<String> TypeCombo;
        private final JFXTextField valueField;

        public ConditionRow() {
            super(5);
            setPadding(new Insets(5));

            columnCombo = new ComboBox<>(FXCollections.observableArrayList(columnSelected));
            columnCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());
            TypeCombo = new ComboBox<>(FXCollections.observableArrayList("String", "Number", "Column"));
            TypeCombo.getSelectionModel().select("String");
            TypeCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());
            TypeCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());
            operatorCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "=", "!=", ">", "<", ">=", "<=", "LIKE", "IN"
            ));
            operatorCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());
            valueField = new JFXTextField();
            valueField.textProperty().addListener(_->generateQuery());
            ComboBox<String> logicCombo = new ComboBox<>(FXCollections.observableArrayList(
                    "AND", "OR"
            ));
            logicCombo.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());

            Button removeBtn = new Button("X");
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

        public String getCondition() {
            if (columnCombo.getValue() == null ||
                    operatorCombo.getValue() == null ||
                    valueField.getText().isEmpty()) {
                return "";
            }

            return String.format("%s %s %s",
                    columnCombo.getValue(),
                    operatorCombo.getValue(),
                    TypeCombo.getValue().equals("String") ? "'" + valueField.getText() + "'" : valueField.getText());
        }
    }

    // Classe para representar um grupo de condições
    class ConditionGroup extends VBox {
        private final ComboBox<String> groupLogic;
        private final VBox groupConditions;

        public ConditionGroup() {
            super(5);
            setPadding(new Insets(5));
            setStyle("-fx-border-color: #999; -fx-border-width: 1;");

            HBox header = new HBox(5);
            groupLogic = new ComboBox<>(FXCollections.observableArrayList("AND", "OR"));
            groupLogic.getSelectionModel().selectedItemProperty().addListener(_->generateQuery());
            Button addConditionBtn = new Button("+ Condition");
            Button addSubGroupBtn = new Button("+ Sub Group");
            Button removeBtn = new Button("X");

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

}
