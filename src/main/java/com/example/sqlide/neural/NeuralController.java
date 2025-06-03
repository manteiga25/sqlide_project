package com.example.sqlide.neural;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;

import java.util.Arrays;

public class NeuralController {

    @FXML
    private JFXTextField ModelField, PathField;
    @FXML
    private Spinner<Integer> EpochSpinner, BatchSpinner, InputSpinner, OutputSpinner, SeedSpinner;
    @FXML
    private ChoiceBox<String> OptimizerBox, LayerBox, FunctionBox;
    @FXML
    private Spinner<Double> LearningSpinner;
    @FXML
    private JFXCheckBox BackPortOption;
    @FXML
    private Label LayerLabel;

    @FXML
    private void initialize() {
        FunctionBox.getItems().addAll(Arrays.asList("ADAM", "SGD", "RMSPROP", "NESTEROVS"));
        FunctionBox.setValue("ADAM");
    }

}
