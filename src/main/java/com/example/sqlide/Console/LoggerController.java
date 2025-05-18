package com.example.sqlide.Console;

import com.example.sqlide.Logger.Logger;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;

import java.time.LocalTime;
import java.util.concurrent.BlockingQueue;

public class LoggerController {

    private final ObservableList<Logger> items = FXCollections.observableArrayList();

    @FXML
    private Pane Container;

    @FXML
    private TableView<Logger> TableLogger;

    @FXML
    private TableColumn<Logger, String> UserColumn, QueryColumn, WarningColumn, TimeColumn;

    private BlockingQueue<Logger> queue;

    public Pane getContainer() {
        return Container;
    }

    @FXML
    public void initialize() {
        UserColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getUser()));
        QueryColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getQuery()));
        WarningColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getWarning()));
        TimeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getTime().toString()));

        // Associa os dados à TableView
        TableLogger.setItems(items);
    }

    public void setQueue(final BlockingQueue<Logger> queue) {
        this.queue = queue;
        initializeQueue();
    }

    private void initializeQueue() {
        final Thread reciver = new Thread(()->{
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    items.add(queue.take());
                }
            } catch (InterruptedException _) {
            }
        });
        reciver.setDaemon(true);
        reciver.start();
    }

}
