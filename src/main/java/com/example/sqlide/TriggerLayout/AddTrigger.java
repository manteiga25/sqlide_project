package com.example.sqlide.TriggerLayout;

import com.jfoenix.controls.JFXTextField;
import javafx.collections.ObservableMap;
import javafx.fxml.FXML;
import javafx.stage.Stage;

import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class AddTrigger {

    @FXML
    private JFXTextField nameField;

    private Stage stage;

    private ObservableMap<String, String> names;

    public void setStage(final Stage stage) {
        this.stage = stage;
    }

    public void setNames(final ObservableMap<String, String> names) {
        this.names = names;
    }

    @FXML
    private void add() {
        final String name = nameField.getText();
        if (!name.isEmpty() && !names.containsKey(name)) {
            names.put(name, "");
            stage.close();
        } else ShowInformation("Invalid name", "Insert a valid name for Trigger.");
    }

    @FXML
    private void close() {
        stage.close();
    }

}
