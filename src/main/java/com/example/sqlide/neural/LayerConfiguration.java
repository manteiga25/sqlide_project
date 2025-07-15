package com.example.sqlide.neural;

import com.mysql.cj.conf.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.json.JSONObject;

public class LayerConfiguration {

    public enum LAYER_TYPE {
        INPUT,
        HIDDEN,
        OUTPUT
    }

    private SimpleIntegerProperty OutNeurons = new SimpleIntegerProperty(1), InNeurons = new SimpleIntegerProperty(1);
    private String function, loss;
    private LAYER_TYPE type;

    private LayerConfiguration previewLayer;

    public LayerConfiguration(final LAYER_TYPE layerType) {
        type = layerType;
    }

    public String getLoss() {
        return loss;
    }

    public void setLoss(String loss) {
        this.loss = loss;
    }

    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public int getInNeurons() {
        return InNeurons.getValue();
    }

    public SimpleIntegerProperty getInNeuronsProperty() {
        return InNeurons;
    }

    public void setInNeurons(int inNeurons) {
        InNeurons.setValue(inNeurons);
    }

    public void setInNeuronsProperty(SimpleIntegerProperty inNeurons) {
        InNeurons = inNeurons;
    }

    public int getOutNeurons() {
        return OutNeurons.getValue();
    }

    public SimpleIntegerProperty getOutNeuronsProperty() {
        return OutNeurons;
    }

    public LAYER_TYPE getType() {
        return type;
    }

    public void setType(LAYER_TYPE type) {
        this.type = type;
    }

    public void setPreviewLayer(final LayerConfiguration configuration) throws Exception {
        if (type != LAYER_TYPE.INPUT) {
            previewLayer = configuration;
            InNeurons.bind(configuration.getInNeuronsProperty());
        } else throw new Exception("invalid configuration: the input layer don´t have preview layer.");
    }

    public static JSONObject LayerToJson(final LayerConfiguration configuration) {
        final JSONObject json = new JSONObject();
        json.put("TYPE", configuration.getType().toString());
        json.put("IN", configuration.getInNeurons());
        json.put("OUT", configuration.getOutNeurons());

        json.put("FUNCTION", configuration.getFunction());

        json.put("LOSS", configuration.getLoss());

        return json;
    }

}
