package com.example.sqlide;

import com.example.sqlide.drivers.model.TypesModelList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

public class NewColumnWinController {

    String type = "None";

    TextField maxChar;
    Label labelChar;

    boolean isChar = false;

    @FXML
    ComboBox<String> TypeBox;

    @FXML
    HBox Horizontal1;

    @FXML
    HBox Horizontal2;

    @FXML
    TextField WordBox;

    @FXML
    ComboBox<String> EditListBox;

    @FXML
    Button AddButton;

    private TypesModelList types;

    public NewColumnWinController(final TypesModelList types) {
        this.types = types;
    }

    @FXML
    public void initialize() {

        TypeBox.getItems().addAll(types.listOfTypes);
    }

    @FXML
    public void CheckType() {
        final String value = TypeBox.getValue();
        if (value.equals(type)) {
            return;
        }

        type = value;

        for (final String type : types.chars) {
            if (value.equals(type)) {
                labelChar = new Label("Max characters");
                Horizontal1.getChildren().add(labelChar);
                maxChar = new TextField();
                maxChar.setPromptText("Ex: 255");
                Horizontal1.getChildren().add(maxChar);
                isChar = true;
                return;
            }
        }
        if (value.equals("DECIMAL")) {
            Horizontal1.getChildren().add(new Label("max whole number"));
            TextField maxWhole = new TextField();
            maxWhole.setPromptText("Ex: 4");
            Horizontal1.getChildren().add(maxWhole);

            Horizontal2.getChildren().add(new Label("fractional"));
            TextField maxfractional = new TextField();
            maxfractional.setPromptText("Ex: 10");
            Horizontal2.getChildren().add(maxfractional);
        }
        else if (value.equals("ENUM") || value.equals("SET")) {
            AddButton.setDisable(false);
        }
    }

}
