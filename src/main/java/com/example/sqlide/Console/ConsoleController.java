package com.example.sqlide.Console;

import com.example.sqlide.Logger.Logger;
import com.example.sqlide.drivers.model.SQLTypes;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

public class ConsoleController {

    @FXML
    private ScrollPane Scroll;
    @FXML
    private ChoiceBox<String> ModeBox;

    private final SimpleStringProperty currentDB = new SimpleStringProperty();

    private final HashMap<String, BlockingQueue<Logger>> senders = new HashMap<>();

    private final HashMap<String, Node> tables = new HashMap<>(), SQLConsole = new HashMap<>(), SystemConsole = new HashMap<>();

    private final HashMap<String, String> paths = new HashMap<>();

    private final HashMap<String, SQLTypes> types = new HashMap<>();

    public SimpleStringProperty currentDBProperty() {
        return currentDB;
    }

    @FXML
    private void initialize() {
        currentDB.addListener((_,_, _)->{
            ModeBox.getSelectionModel().select(ModeBox.getSelectionModel().selectedIndexProperty().get());
        });
        ModeBox.getItems().addAll("Logger", "SQL Terminal", "System Terminal");
        ModeBox.getSelectionModel().selectedIndexProperty().addListener((_,_,mode)->{
            switch (mode.intValue()) {
                case 0:
                    if (tables.get(currentDB.get()) == null) loadLoggerStage();
                    Scroll.setContent(tables.get(currentDB.get()));
                    break;
                case 1:
                    if (SQLConsole.get(currentDB.get()) == null) loadSQLStage();
                    Scroll.setContent(SQLConsole.get(currentDB.get()));
                    break;
                case 2:
                    if (SystemConsole.get(currentDB.get()) == null) loadSystemStage();
                    Scroll.setContent(SystemConsole.get(currentDB.get()));
                    break;
            }
        });
    }

    private void loadLoggerStage() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("LoggerStage.fxml"));
            Node _ = loader.load();
            LoggerController controller = loader.getController();
            controller.setQueue(senders.get(currentDB.get()));
            tables.put(currentDB.get(), controller.getContainer().getChildren().removeFirst());

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSQLStage() {
        try {
            // Carrega o arquivo FXML
            SQLTerminalController SQLcontroller = new SQLTerminalController();
            SQLcontroller.load(types.get(currentDB.get()), paths.get(currentDB.get()));
            SQLConsole.put(currentDB.get(), SQLcontroller.getContainer());

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadSystemStage() {
        try {
            // Carrega o arquivo FXML
            SystemTerminalController SQLcontroller = new SystemTerminalController();
          //  SQLcontroller.load(types.get(currentDB.get()), paths.get(currentDB.get()));
            SystemConsole.put(currentDB.get(), SQLcontroller.getContainer());

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addData(final String name, final BlockingQueue<Logger> queue, final String path, final SQLTypes sql) {
        senders.put(name, queue);
        paths.put(name, path);
        types.put(name, sql);
        ModeBox.getSelectionModel().select(0);
    }

    @FXML
    private void delete() {
        if (tables.get(currentDB.get()) != null) {
            TableView<?> tab = (TableView<?>) tables.get(currentDB.get());
            tab.getItems().clear();
        }
        if (SQLConsole.get(currentDB.get()) != null) {
            VBox box = (VBox) SQLConsole.get(currentDB.get());
            box.getChildren().remove(0, box.getChildren().size()-1);
        }
        if (SystemConsole.get(currentDB.get()) != null) {
            VBox box = (VBox) SystemConsole.get(currentDB.get());
            box.getChildren().remove(0, box.getChildren().size()-1);
        }
    }
}
