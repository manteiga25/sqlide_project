package com.example.sqlide.AdvancedSearch;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

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

    @FXML
    public void initialize() {
        StatusColumn.setCellFactory(column -> {
            return new TableCell<Rule, Boolean>() {
                private final CheckBox checkBox = new CheckBox();
                {
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
    }

/*    public void InflateOrderingResultController(final ArrayList<String> columns) {
        for (final String column : columns) {
            data.add(new Rule(column, "", false));
        }

        InflateTable();
    } */

    public void InflateOrderingResultController(final ObservableList<String> columns) {
        for (final String column : columns) {
            data.add(new Rule(column, "", false));
        }

        InflateTable();
    }

    private void InflateTable() {
        TableOrdering.setItems(data);
    }

    public void addItem(final String column) {
        data.add(new Rule(column, "", false));
    }

    public void removeItem(final String column) {
        data.removeIf(col->col.getColumn().equals(column));
    }

    public String getRules() {
        StringBuilder ruleQuery = new StringBuilder();
        for (Rule rule : data) {
            if (rule.getStatus()) {
                ruleQuery.append(rule.getColumn()).append(", ").append(rule.getRule());
            }
        }
        ruleQuery.delete(ruleQuery.length()-2, ruleQuery.length());
        return ruleQuery.toString();
    }

}
