package com.example.sqlide;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDateTime;

public class SavePointController {

    private final ObservableList<SavePoint> SaveList = FXCollections.observableArrayList();

    private TextField NameField;
    private Button RemoveButton, RestoreButton;
    private TableView<SavePoint> TablePoints;

    @FXML
    private void initialize() {
        TablePoints.setItems(SaveList);

    }

    private static class SavePoint {

        private final SimpleStringProperty name;
        private final LocalDateTime time;

        public SavePoint(final String name, final LocalDateTime time) {
            this.name = new SimpleStringProperty(name);
            this.time = time;
        }

        public LocalDateTime getTime() {
            return time;
        }

        public SimpleStringProperty nameProperty() {
            return name;
        }
    }

}
