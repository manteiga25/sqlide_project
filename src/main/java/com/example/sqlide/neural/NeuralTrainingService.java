package com.example.sqlide.neural;

import ai.djl.Model;
import ai.djl.engine.Engine;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.ArrayDataset;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.translate.TranslateException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * Treina a rede definida na UI, no motor escolhido.
 *
 * <p>Todos os recursos nativos — {@link NDManager}, {@link Model}, {@link Trainer} — são
 * fechados no fim. Sem isso a memória fora do heap da JVM não é libertada e a segunda
 * sessão de treino falha por falta de memória, o que é fácil de tomar por outra coisa.</p>
 */
public class NeuralTrainingService {

    /** Parâmetros de um treino. */
    public record Request(NeuralEngineType engine,
                          List<LayerConfiguration> layers,
                          String optimizer,
                          String loss,
                          float learningRate,
                          int epochs,
                          int batchSize,
                          boolean classification) {
    }

    /** Resultado de uma época, para a UI acompanhar. */
    public record Progress(int epoch, int totalEpochs, float trainingLoss, float validationLoss) {
    }

    /** O que ficou no fim do treino. */
    public record Outcome(String engine,
                          String architecture,
                          int epochs,
                          float finalLoss,
                          List<Progress> history,
                          Path savedTo) {
    }

    private volatile boolean cancelled = false;

    /** Thread onde o treino corre, para o cancelamento a poder interromper. */
    private volatile Thread worker;

    /**
     * Pede o fim do treino.
     *
     * <p>Interrompe tambem a thread: sem isso um cancelamento durante o download da
     * biblioteca nativa, ou a meio de uma epoca longa, so era notado no ciclo seguinte —
     * e enquanto isso a UI ficava presa em "Cancelling..." sem nada acontecer.</p>
     */
    public void cancel() {
        cancelled = true;
        final Thread running = worker;
        if (running != null) running.interrupt();
    }

    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Treina a rede.
     *
     * @param x         observações, uma linha por exemplo
     * @param y         alvos, um por observação
     * @param onEpoch   recebe o progresso ao fim de cada época; pode ser null
     * @param saveTo    pasta onde gravar o modelo; null para não gravar
     */
    public Outcome train(final Request request,
                         final float[][] x,
                         final float[][] y,
                         final BiConsumer<Progress, String> onEpoch,
                         final Path saveTo,
                         final String modelName) throws Exception {

        if (x == null || x.length == 0) throw new IllegalArgumentException("No training rows.");
        if (y == null || y.length != x.length) {
            throw new IllegalArgumentException("Targets and rows do not match in number.");
        }

        worker = Thread.currentThread();

        // Cancelar antes de o motor carregar tem de sair aqui: mais a frente o load()
        // bloqueia no download do nativo e o pedido de cancelamento passava despercebido.
        if (cancelled) {
            return new Outcome(request.engine().toString(),
                    NetworkBuilder.describe(request.layers()), 0, Float.NaN, List.of(), null);
        }

        final int features = x[0].length;
        final int inputNeurons = request.layers().getFirst().getInNeurons();
        if (features != inputNeurons) {
            throw new IllegalArgumentException("The input layer expects " + inputNeurons
                    + " value(s) but the data has " + features + " column(s).");
        }

        // Escolher o motor antes de criar seja o que for: é isto que decide se o trabalho
        // corre em PyTorch ou em TensorFlow. Na primeira utilização de cada motor esta
        // chamada descarrega a biblioteca nativa, o que demora.
        final Engine engine = request.engine().load();

        final Block network = NetworkBuilder.build(request.layers());
        final Loss loss = NetworkBuilder.lossOf(request.loss());

        final List<Progress> history = new ArrayList<>();
        float lastLoss = Float.NaN;
        Path saved = null;

        try (NDManager manager = engine.newBaseManager();
             Model model = Model.newInstance(modelName == null || modelName.isBlank()
                     ? "sqlide-network" : modelName, engine.getEngineName())) {

            model.setBlock(network);

            final DefaultTrainingConfig config = new DefaultTrainingConfig(loss)
                    .optOptimizer(NetworkBuilder.optimizerOf(request.optimizer(), request.learningRate()))
                    .addTrainingListeners(TrainingListener.Defaults.logging());

            if (request.classification()) config.addEvaluator(new Accuracy());

            final ArrayDataset dataset = new ArrayDataset.Builder()
                    .setData(manager.create(x))
                    .optLabels(manager.create(y))
                    .setSampling(Math.max(1, request.batchSize()), true)
                    .build();

            try (Trainer trainer = model.newTrainer(config)) {
                trainer.setMetrics(new ai.djl.metric.Metrics());
                // A forma inicial diz ao DJL as dimensões com que inicializar os pesos.
                trainer.initialize(new Shape(Math.max(1, request.batchSize()), features));

                for (int epoch = 1; epoch <= request.epochs(); epoch++) {
                    if (cancelled || Thread.currentThread().isInterrupted()) break;

                    try {
                        EasyTrain.fit(trainer, 1, dataset, null);
                    } catch (Exception e) {
                        // Uma epoca interrompida a meio nao e um erro de treino.
                        if (cancelled || Thread.currentThread().isInterrupted()) break;
                        throw e;
                    }

                    final float trainingLoss = valueOf(trainer, "train_epoch_" + loss.getName());
                    final float validationLoss = valueOf(trainer, "validate_epoch_" + loss.getName());
                    lastLoss = Float.isNaN(trainingLoss) ? lastLoss : trainingLoss;

                    final Progress progress = new Progress(epoch, request.epochs(), trainingLoss, validationLoss);
                    history.add(progress);
                    if (onEpoch != null) {
                        onEpoch.accept(progress, String.format(Locale.US,
                                "epoch %d/%d  loss %.6f", epoch, request.epochs(), trainingLoss));
                    }
                }
            }

            if (saveTo != null) {
                java.nio.file.Files.createDirectories(saveTo);
                model.save(saveTo, model.getName());
                saved = saveTo;
            }
        }

        return new Outcome(request.engine().toString(), NetworkBuilder.describe(request.layers()),
                history.size(), lastLoss, history, saved);
    }

    /** Lê uma métrica do treinador sem rebentar quando ela ainda não existe. */
    private static float valueOf(final Trainer trainer, final String name) {
        try {
            return trainer.getMetrics().latestMetric(name).getValue().floatValue();
        } catch (Exception _) {
            return Float.NaN;
        }
    }

    /**
     * Previsão para observações novas, com o modelo já treinado em memória.
     *
     * <p>Serve para o painel de teste; para prever com um modelo gravado é preciso voltar
     * a carregá-lo com {@link Model#load(Path)}.</p>
     */
    public static float[][] predict(final NeuralEngineType engineType, final Block network,
                                    final float[][] x) throws TranslateException, IOException {
        final Engine engine = engineType.load();
        try (NDManager manager = engine.newBaseManager()) {
            final var input = new ai.djl.ndarray.NDList(manager.create(x));
            final var output = network.forward(new ai.djl.training.ParameterStore(manager, false),
                    input, false);
            return output.singletonOrThrow().toFloatArray().length == 0
                    ? new float[0][]
                    : reshape(output.singletonOrThrow().toFloatArray(), x.length);
        }
    }

    private static float[][] reshape(final float[] flat, final int rows) {
        if (rows == 0) return new float[0][];
        final int columns = flat.length / rows;
        final float[][] out = new float[rows][columns];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(flat, r * columns, out[r], 0, columns);
        }
        return out;
    }

}
