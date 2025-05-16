package com.example.sqlide.Console;

import com.example.sqlide.Logger.Logger;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.LocalTime;

public class LoggerController {

    private ObservableList<Logger> items = null;

    @FXML
    private TableView<Logger> TableLogger;

    @FXML
    private TableColumn<Logger, String> UserColumn, QueryColumn, WarningColumn, TimeColumn;

    @FXML
    public void initialize() {
        UserColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUser()));
        QueryColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getQuery()));
        WarningColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getWarning()));
        TimeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTime().toString()));

        // Associa os dados à TableView
        TableLogger.setItems(items);
    }

    public void setItems(final ObservableList<Logger> items) {
        this.items = items;
    }

}
