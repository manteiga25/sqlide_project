package com.example.sqlide.Email;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.stage.Stage;

import javax.xml.crypto.dsig.spec.XSLTTransformParameterSpec;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class insertDataController {

    @FXML
    private ChoiceBox<String> TableBox, ColumnBox;

    private HashMap<String, ArrayList<String>> items;

    private Stage stage;

    private EmailController controller;

    @FXML
    private void initialize() {
        TableBox.getSelectionModel().selectedItemProperty().addListener((_,_, item)->{
            ColumnBox.getItems().clear();
            ColumnBox.getItems().addAll(items.get(item));
        });
    }

    public void setInterface(final EmailController controller, final Stage stage) {
        this.controller = controller;
        this.stage = stage;
    }

    public void setData(final HashMap<String, ArrayList<String>> items) {
        this.items = items;
        TableBox.setItems(FXCollections.observableArrayList(items.keySet()));
    }

    @FXML
    private void addData() {
        final String table = TableBox.getValue(), column = ColumnBox.getValue();
        if (!table.isEmpty() && !column.isEmpty()) {
            controller.insertData("<DataSrc="+table+":"+column+"/>");
            stage.close();
        } else ShowInformation("Invalid", "You need to insert Table and Column to generate Tag.");
    }

    @FXML
    private void close() {
        stage.close();
    }

}
