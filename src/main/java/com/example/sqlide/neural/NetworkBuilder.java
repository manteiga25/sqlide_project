package com.example.sqlide.neural;

import ai.djl.ndarray.NDList;
import ai.djl.nn.Activation;
import ai.djl.nn.Block;
import ai.djl.nn.SequentialBlock;
import ai.djl.nn.core.Linear;
import ai.djl.training.loss.Loss;
import ai.djl.training.optimizer.Adam;
import ai.djl.training.optimizer.Optimizer;
import ai.djl.training.tracker.Tracker;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Traduz a configuração de camadas da UI num modelo do DJL.
 *
 * <p>Cada camada vira uma {@code Linear} com a sua função de ativação. As dimensões saem
 * da cadeia de camadas — entradas de uma são as saídas da anterior — que é o que o
 * {@link LayerConfiguration} já garante.</p>
 */
public final class NetworkBuilder {

    private NetworkBuilder() {
    }

    /**
     * Constrói a rede.
     *
     * <p>A última camada fica sem ativação quando a tarefa é de regressão: aplicar uma
     * softmax ou uma sigmoide à saída limitaria o valor previsto ao intervalo dela.</p>
     */
    public static Block build(final List<LayerConfiguration> layers) {
        if (layers == null || layers.size() < 2) {
            throw new IllegalArgumentException("A network needs at least an input and an output layer.");
        }

        final SequentialBlock network = new SequentialBlock();

        for (int i = 0; i < layers.size(); i++) {
            final LayerConfiguration layer = layers.get(i);
            final int units = layer.getOutNeurons();

            if (units < 1) {
                throw new IllegalArgumentException(
                        "Layer " + (i + 1) + " has " + units + " output neuron(s).");
            }

            network.add(Linear.builder().setUnits(units).build());

            final Function<NDList, NDList> activation = activationOf(layer.getFunction());
            if (activation != null) network.add(activation);
        }

        return network;
    }

    /**
     * Função de ativação a partir do nome escolhido na UI.
     *
     * @return null quando não há ativação a aplicar
     */
    public static Function<NDList, NDList> activationOf(final String name) {
        if (name == null || name.isBlank()) return null;

        return switch (name.toLowerCase(Locale.ROOT)) {
            case "relu" -> Activation::relu;
            case "relu6" -> list -> new NDList(Activation.relu(list.singletonOrThrow()).clip(0, 6));
            case "sigmoid", "hard_sigmoid" -> Activation::sigmoid;
            case "tanh" -> Activation::tanh;
            case "softmax" -> list -> new NDList(list.singletonOrThrow().softmax(-1));
            case "log_softmax" -> list -> new NDList(list.singletonOrThrow().logSoftmax(-1));
            case "elu" -> list -> Activation.elu(list, 1.0f);
            case "selu" -> Activation::selu;
            case "gelu" -> Activation::gelu;
            case "swish", "silu", "hard_swish", "hard_silu" -> list -> Activation.swish(list, 1.0f);
            case "leaky_relu" -> list -> Activation.leakyRelu(list, 0.01f);
            case "softplus" -> list -> new NDList(list.singletonOrThrow().exp().add(1).log());
            case "mish" -> list -> {
                // mish(x) = x * tanh(softplus(x))
                final var x = list.singletonOrThrow();
                return new NDList(x.mul(x.exp().add(1).log().tanh()));
            };
            // "linear", "exponential", "softsign" e "get" não têm equivalente directo
            // e ficam sem ativação, que é o mesmo que a identidade.
            default -> null;
        };
    }

    /** Função de perda a partir do nome escolhido na UI. */
    public static Loss lossOf(final String name) {
        if (name == null || name.isBlank()) return Loss.l2Loss();

        return switch (name.toUpperCase(Locale.ROOT)) {
            case "MAE" -> Loss.l1Loss();
            case "MSE", "MSLE" -> Loss.l2Loss();
            case "KLD" -> Loss.softmaxCrossEntropyLoss();
            case "CROSS_ENTROPY" -> Loss.softmaxCrossEntropyLoss();
            case "HINGE" -> Loss.hingeLoss();
            // MAPE e POISSON não existem no DJL; o L1 é o mais próximo em comportamento.
            default -> Loss.l1Loss();
        };
    }

    /** Otimizador a partir do nome escolhido na UI. */
    public static Optimizer optimizerOf(final String name, final float learningRate) {
        final Tracker tracker = Tracker.fixed(learningRate);

        if (name == null) return Adam.builder().optLearningRateTracker(tracker).build();

        return switch (name.toUpperCase(Locale.ROOT)) {
            case "SGD" -> Optimizer.sgd().setLearningRateTracker(tracker).build();
            case "NESTEROVS" -> Optimizer.sgd().setLearningRateTracker(tracker)
                    .optMomentum(0.9f).build();
            case "ADAGRAD" -> Optimizer.adagrad().optLearningRateTracker(tracker).build();
            case "ADADELTA" -> Optimizer.adadelta().build();
            case "RMSPROP" -> Optimizer.rmsprop().optLearningRateTracker(tracker).build();
            case "ADAMW" -> Optimizer.adamW().optLearningRateTracker(tracker).build();
            // Os restantes nomes da lista da UI vêm do Keras e não existem no DJL;
            // o Adam é o que mais se aproxima e é o que o painel já trazia por omissão.
            default -> Adam.builder().optLearningRateTracker(tracker).build();
        };
    }

    /** Descrição da rede, para o registo de treino. */
    public static String describe(final List<LayerConfiguration> layers) {
        final StringBuilder text = new StringBuilder();
        for (int i = 0; i < layers.size(); i++) {
            final LayerConfiguration layer = layers.get(i);
            if (i > 0) text.append(" -> ");
            text.append(layer.getInNeurons()).append('x').append(layer.getOutNeurons());
            if (layer.getFunction() != null && !layer.getFunction().isBlank()) {
                text.append('[').append(layer.getFunction()).append(']');
            }
        }
        return text.toString();
    }

}
