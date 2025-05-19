package com.example.sqlide.AdvancedSearch;

import com.jfoenix.controls.JFXCheckBox;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.paint.Color;

public class OrderingController {

    @FXML
    private TableView<Rule> TableOrdering;
    @FXML
    private TableColumn<Rule, Boolean> StatusColumn;
    @FXML
    private TableColumn<Rule, String> ColumnsColumn;
    @FXML
    private TableColumn<Rule, String> ruleColumn;

    private final ObservableList<Rule> data = FXCollections.observableArrayList();

    private ObservableList<String> columns;

    @FXML
    private void initialize() {
        ColumnsColumn.setCellValueFactory(new PropertyValueFactory<>("column"));
        ruleColumn.setCellValueFactory(new PropertyValueFactory<>("rule"));
        StatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        StatusColumn.setCellFactory(column -> {
            return new TableCell<Rule, Boolean>() {
                private final JFXCheckBox checkBox = new JFXCheckBox();
                {
                    checkBox.setText("State");
                    checkBox.setTextFill(Color.WHITE);
                    checkBox.selectedProperty().addListener((obs, oldValue, newValue) -> {
                        //  final Rule item = getTableRow().getItem();
                        getTableRow().getItem().setStatus(newValue);
                    });
                }

                @Override
                protected void updateItem(Boolean item, boolean empty) {
                    //    updateByUser.set(false);
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        checkBox.setSelected(item);
                        setGraphic(checkBox);
                    }
                    //  updateByUser.set(true);
                }
            };
        });
        ruleColumn.setCellFactory(column -> {
            return new TableCell<Rule, String>() {
                private final ChoiceBox<String> choiceBox = new ChoiceBox<>(FXCollections.observableArrayList("ASC", "DESC"));
                {
                    choiceBox.setStyle("-fx-background-color: #2C2C2C;");
                    choiceBox.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                          final Rule item = getTableRow().getItem();
                        item.setRule(newValue);
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    //    updateByUser.set(false);
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setGraphic(null);
                    } else {
                        choiceBox.setValue(getItem());
                        setGraphic(choiceBox);
                    }
                    //  updateByUser.set(true);
                }
            };
        });
    }

/*    public void InflateOrderingResultController(final ArrayList<String> columns) {
        for (final String column : columns) {
            data.add(new Rule(column, "", false));
        }

        InflateTable();
    } */

    public void InflateOrderingResultController(final ObservableList<String> columns) {
        this.columns = columns;
        this.columns.addListener((ListChangeListener<? super String>) change -> {
            if (change.next()) {
                if (change.wasRemoved()) {
                    change.getRemoved().forEach(this::removeItem);
                }
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(this::addItem);
                }
            }
        });
        for (final String column : columns) {
       //     data.add(new Rule(column, "", false));
        }

        InflateTable();
    }

    private void InflateTable() {
        TableOrdering.setItems(data);
    }

    public void addItem(final String column) {
        System.out.println(column);
        data.add(new Rule(column, "", false));
    }

    public void removeItem(final String column) {
        data.removeIf(col->col.getColumn().equals(column));
        TableOrdering.refresh();
    }

    public String getRules() {
        StringBuilder ruleQuery = new StringBuilder();
        for (Rule rule : data) {
            if (rule.getStatus()) {
                ruleQuery.append(rule.getColumn()).append(", ").append(rule.getRule());
            }
        }
      //  if (!ruleQuery.isEmpty()) ruleQuery.delete(ruleQuery.length() - 2, ruleQuery.length());
        return ruleQuery.toString();
    }

}
