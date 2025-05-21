package com.example.sqlide.EventLayout;

import com.example.sqlide.Container.Editor.TextAreaAutocompleteLines;
import com.example.sqlide.drivers.model.DataBase;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;

import java.sql.SQLException;
import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class EditEventController {

    @FXML
    ComboBox<String> EventBox;

    @FXML
    Pane CodeViewContainer;

    private final TextAreaAutocompleteLines codeView = new TextAreaAutocompleteLines();

    private DataBase db;

    private HashMap<String, String> list;

    public void initEventWindow(final DataBase db, final HashMap<String, String> list) {
        this.db = db;
        this.list = list;
        initCombo();
        codeView.setPrefHeight(CodeViewContainer.getPrefHeight());
        codeView.setPrefWidth(CodeViewContainer.getPrefWidth());
        CodeViewContainer.getChildren().add(codeView);
    }

    private void initCombo() {
        for (final String EventName : list.keySet()) {
            EventBox.getItems().add(EventName);
        }
    }

    @FXML
    public void removeEvent() {
        final String event = EventBox.getValue();
        if (event == null) {
            ShowError("Error", "You need to select one event to delete.");
            return;
        }
        try {
            db.removeEvent(event);
            EventBox.getItems().remove(event);
        } catch (SQLException e) {
            ShowError("Error", "Error to remove event.\n" + e.getMessage());
        }
    }

    @FXML
    public void EventChanged() {
        final String Trigger = EventBox.getValue();
        codeView.setText(list.get(Trigger));
    }

    @FXML
    public void save() {
        final String event = EventBox.getValue();
        final String code = codeView.getText();
        db.createEvent(event, code);
    }

}
