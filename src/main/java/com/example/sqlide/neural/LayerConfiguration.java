package com.example.sqlide.neural;

import javafx.beans.property.SimpleIntegerProperty;
import org.json.JSONObject;

/**
 * Uma camada da rede.
 *
 * <p>O número de entradas de uma camada é sempre o número de saídas da anterior — é uma
 * consequência, não uma escolha. Por isso {@code InNeurons} é ligado por
 * {@link #setPreviewLayer} e passa a ser só de leitura; a única coisa editável numa camada
 * escondida ou de saída é {@code OutNeurons}.</p>
 */
public class LayerConfiguration {

    public enum LAYER_TYPE {
        INPUT,
        HIDDEN,
        OUTPUT
    }

    private final SimpleIntegerProperty OutNeurons = new SimpleIntegerProperty(1);
    private SimpleIntegerProperty InNeurons = new SimpleIntegerProperty(1);
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

    /**
     * Define as entradas.
     *
     * <p>Não faz nada quando a camada já as recebe da anterior: escrever numa propriedade
     * ligada lança {@code "A bound value cannot be set"}, que era exatamente o que a UI
     * fazia rebentar ao trocar de camada.</p>
     */
    public void setInNeurons(int inNeurons) {
        if (InNeurons.isBound()) return;
        InNeurons.setValue(inNeurons);
    }

    /** True quando o valor vem da camada anterior e não pode ser editado. */
    public boolean isInNeuronsDerived() {
        return InNeurons.isBound();
    }

    public void setInNeuronsProperty(SimpleIntegerProperty inNeurons) {
        InNeurons = inNeurons;
    }

    public void setOutNeurons(int outNeurons) {
        OutNeurons.setValue(outNeurons);
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
            // Ligava a getInNeuronsProperty da anterior: a entrada desta camada ficava igual
            // à entrada da anterior em vez da sua saída, e a rede saía com as dimensões
            // desencontradas.
            InNeurons.bind(configuration.getOutNeuronsProperty());
        } else throw new Exception("invalid configuration: the input layer don´t have preview layer.");
    }

    /** Desliga a entrada da camada anterior, para poder ser religada a outra. */
    public void clearPreviewLayer() {
        previewLayer = null;
        InNeurons.unbind();
    }

    public LayerConfiguration getPreviewLayer() {
        return previewLayer;
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
