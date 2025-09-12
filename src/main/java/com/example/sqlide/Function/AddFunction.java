package com.example.sqlide.Function;

import com.example.sqlide.View.ViewController;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.stage.Stage;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class AddFunction {

    @FXML
    private JFXCheckBox DeterminateBox;
    @FXML
    private ChoiceBox<String> AccessBox;
    @FXML
    private JFXTextField FunctionField;

    private Stage stage;

    private String user;

    private ObservableList<FunctionController.Function> functions;

    public void setStage(final Stage stage) {
        this.stage = stage;
    }

    public void setUser(final String user) {
        this.user = user;
    }

    public void setList(final ObservableList<FunctionController.Function> functions) {
        this.functions = functions;
    }

    @FXML
    private void initialize() {
        AccessBox.getItems().addAll("NO SQL", "READS SQL DATA", "CONTAINS SQL", "MODIFIES SQL DATA");
        AccessBox.getSelectionModel().selectFirst();
    }

    @FXML
    private void add() {
        final String name = FunctionField.getText();
        if (!name.isEmpty() && functions.stream().noneMatch(view -> view.Name.get().equals(name))) {
            final String determinate = DeterminateBox.isDisable() ? "NOT DETERMINISTIC" : "DETERMINISTIC";
            functions.add(new FunctionController.Function(name, "RETURNS ?\n" + determinate + "\n" + AccessBox.getValue() + "\nBEGIN\n\nEND", user));
            stage.close();
        } else ShowInformation("Null", "You need to insert a valid name.");
    }

    @FXML
    private void close() {
        stage.close();
    }

}
