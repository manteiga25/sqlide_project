package com.example.sqlide.TriggerLayout;

import com.example.sqlide.Container.Editor.TextAreaAutocomplete;
import com.example.sqlide.Container.Editor.TextAreaAutocompleteLines;
import com.example.sqlide.drivers.model.DataBase;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.Pane;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.example.sqlide.Container.Editor.Words.SQLWords.getWords;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class EditTriggerController {

    @FXML
    ComboBox<String> TriggersBox;

    @FXML
    Pane CodeViewContainer;

    private final TextAreaAutocomplete codeView = new TextAreaAutocomplete();

    private DataBase db;

    private HashMap<String, String> list;

    public void initTriggerWindow(final DataBase db, final HashMap<String, String> list) {
        this.db = db;
        this.list = list;
        initCombo();
        codeView.setPrefHeight(CodeViewContainer.getPrefHeight());
        codeView.setPrefWidth(CodeViewContainer.getPrefWidth());
        codeView.setAutoCompleteWords(new ArrayList<>(List.of(getWords(db.getSQLType().ordinal()))));
        CodeViewContainer.getChildren().add(codeView);
    }

    @FXML
    public void RemoveTrigger() {
        final String trigger = TriggersBox.getValue();
        if (trigger == null) {
            ShowError("Error", "You need to select one trigger to delete.");
            return;
        }
        try {
            db.removeTrigger(trigger);
            TriggersBox.getItems().remove(trigger);
        } catch (SQLException e) {
            ShowError("Error", "Error to remove trigger.\n" + e.getMessage());
        }
    }

    private void initCombo() {
        for (final String TriggerName : list.keySet()) {
            TriggersBox.getItems().add(TriggerName);
        }
    }

    @FXML
    public void TriggerChanged() {
        final String Trigger = TriggersBox.getValue();
        codeView.setText(list.get(Trigger));
    }

    @FXML
    public void save() {
        final String Trigger = TriggersBox.getValue();
        final String code = codeView.getText();
        db.createTrigger(Trigger, code);
    }

}
