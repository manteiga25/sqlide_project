package com.example.sqlide.Procedure;

import com.example.sqlide.Function.FunctionController;
import com.jfoenix.controls.JFXTextField;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.stage.Stage;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class AddProcedure {

    @FXML
    private JFXTextField FunctionField;

    private Stage stage;

    private ObservableList<ProcedureController.Procedure> functions;

    public void setStage(final Stage stage) {
        this.stage = stage;
    }

    public void setList(final ObservableList<ProcedureController.Procedure> functions) {
        this.functions = functions;
    }

    @FXML
    private void add() {
        final String name = FunctionField.getText();
        if (!name.isEmpty() && functions.stream().noneMatch(view -> view.Name.get().equals(name))) {
            functions.add(new ProcedureController.Procedure(name, ""));
            stage.close();
        } else ShowInformation("Null", "You need to insert a valid name.");
    }

    @FXML
    private void close() {
        stage.close();
    }

}
