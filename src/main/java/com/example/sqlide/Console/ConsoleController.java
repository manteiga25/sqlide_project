package com.example.sqlide.Console;

import com.example.sqlide.Logger.Logger;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

public class ConsoleController {

    @FXML
    private VBox Container;
    @FXML
    private ChoiceBox<String> ModeBox;

    private final SimpleStringProperty currentDB = new SimpleStringProperty();

    private final HashMap<String, BlockingQueue<Logger>> senders = new HashMap<>();

    private final HashMap<String, Node> tables = new HashMap<>();

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
            if (Container.getChildren().size() > 1) {
                Container.getChildren().removeLast();
            }
            switch (mode.intValue()) {
                case 0:
                    if (tables.get(currentDB.get()) == null) loadLoggerStage();
                    Container.getChildren().add(tables.get(currentDB.get()));
                    break;
                case 1:
                    break;
                case 2:
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

    public void addData(final String name, final BlockingQueue<Logger> queue) {
        senders.put(name, queue);
    }

}
