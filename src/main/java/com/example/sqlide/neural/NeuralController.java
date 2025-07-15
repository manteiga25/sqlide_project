package com.example.sqlide.neural;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.util.StringConverter;

import java.util.Arrays;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class NeuralController {

    @FXML
    private JFXTextField ModelField, PathField;
    @FXML
    private Spinner<Integer> EpochSpinner, BatchSpinner, InputSpinner, OutputSpinner, SeedSpinner;
    @FXML
    private ChoiceBox<String> OptimizerBox, LossBox, FunctionBox;
    @FXML
    private ChoiceBox<LayerConfiguration> LayerBox;
    @FXML
    private Spinner<Double> LearningSpinner;
    @FXML
    private JFXCheckBox BackPortOption;
    @FXML
    private Label LayerLabel;

    private final ObservableList<LayerConfiguration> layers = FXCollections.observableArrayList();

    public NeuralController() {
        final LayerConfiguration inputLayer = new LayerConfiguration(LayerConfiguration.LAYER_TYPE.INPUT);
        final LayerConfiguration hiddenLayer = new LayerConfiguration(LayerConfiguration.LAYER_TYPE.HIDDEN);
        final LayerConfiguration outLayer = new LayerConfiguration(LayerConfiguration.LAYER_TYPE.OUTPUT);

        layers.addAll(inputLayer, hiddenLayer, outLayer);
    }

    @FXML
    private void initialize() {

        OptimizerBox.getItems().addAll(Arrays.asList("ADADELTA", "ADAFACTOR", "ADAGRAD", "ADAM", "ADAMW", "ADAMAX", "FTRL", "LION", "LossScaleOptimizer", "Nadam", "SGD", "RMSPROP", "NESTEROVS"));
        OptimizerBox.setValue("ADAM");

        FunctionBox.getItems().addAll(Arrays.asList("elu", "exponential", "gelu", "get", "hard_sigmoid", "hard_silu", "hard_swish", "leaky_relu", "linear", "log_softmax", "mish", "relu", "relu6", "selu", "sigmoid", "silu", "softmax", "softplus", "softsign", "swish"));
        FunctionBox.setValue("softmax");

        LossBox.getItems().addAll(Arrays.asList("KLD", "MAE", "MAPE", "MSE", "MSLE", "POISSON"));
        LossBox.setValue("MAE");

        EpochSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE));
        BatchSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE));
        InputSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE));
        OutputSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE));
        SeedSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE));

        InputSpinner.valueProperty().addListener((_, _, value)->{
            System.out.println(value);
        });

        OutputSpinner.valueProperty().addListener((_, _, value)->{
            System.out.println(value);
        });

        LearningSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.001, 1.0));

        LayerBox.setItems(layers);
        LayerBox.converterProperty().bind(
                Bindings.createObjectBinding(
                        () -> new StringConverter<LayerConfiguration>() {
            @Override
            public String toString(LayerConfiguration layer) {
                final int idx = layers.indexOf(layer)+1;
                return "layer: " + idx;
            }
            @Override
            public LayerConfiguration fromString(String string) {
                return null;
            }

        }, LayerBox.valueProperty()
        )
        );
        LayerBox.getSelectionModel().selectedItemProperty().addListener((_,_, layer)->{
            updateUI(layer);
        });
        LayerBox.setValue(LayerBox.getItems().getFirst());

        layers.addListener((ListChangeListener.Change<? extends LayerConfiguration> change) -> {
            while (change.next()) {
                if (change.wasAdded() || change.wasRemoved() || change.wasUpdated()) {
                    updateLayerBindings();
                }
            }
        });
    }

    private void updateUI(final LayerConfiguration layerConfiguration) {
        if (layerConfiguration == null) return;

        LayerLabel.setText("Layer Type: " + layerConfiguration.getType());

        // Remova bindings antigos
        InputSpinner.getValueFactory().valueProperty().unbind();
        OutputSpinner.getValueFactory().valueProperty().unbind();

        // Crie novos bindings bidirecionais
        InputSpinner.getValueFactory().valueProperty().bindBidirectional(layerConfiguration.getInNeuronsProperty().asObject());
        OutputSpinner.getValueFactory().valueProperty().bindBidirectional(layerConfiguration.getOutNeuronsProperty().asObject());

        // Forçar atualização imediata
        InputSpinner.getValueFactory().setValue(layerConfiguration.getInNeurons());
        OutputSpinner.getValueFactory().setValue(layerConfiguration.getOutNeurons());
    }

    private void updateLayerBindings() {
        // Primeiro, remova todos os vínculos existentes
        for (LayerConfiguration layer : layers) {
            layer.getInNeuronsProperty().unbind();
        }

        // Agora, reconecte os vínculos
        for (int i = 1; i < layers.size(); i++) {
            LayerConfiguration currentLayer = layers.get(i);
            LayerConfiguration previousLayer = layers.get(i - 1);

            // Vincule os neurônios de entrada da camada atual aos de saída da anterior
            currentLayer.getInNeuronsProperty().bind(previousLayer.getOutNeuronsProperty());
        }
    }

    @FXML
    private void AddLayer() {
        int index = LayerBox.getSelectionModel().getSelectedIndex();
        if (index != layers.size()-1) ++index;
        layers.add(index, new LayerConfiguration(LayerConfiguration.LAYER_TYPE.HIDDEN));
        LayerBox.getSelectionModel().select(index);
    }

    @FXML
    private void RemoveLayer() {
        final int index = LayerBox.getSelectionModel().getSelectedIndex();
        final LayerConfiguration layer = layers.get(index);
        if (layer.getType() == LayerConfiguration.LAYER_TYPE.HIDDEN && layers.size() > 3) {
            layers.remove(index);
            LayerBox.getSelectionModel().select(index);
        } else ShowInformation("Invalid layer", "You cannot remove input or output layer.");
    }

}
