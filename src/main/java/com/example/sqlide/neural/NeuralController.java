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

import com.example.sqlide.AdvancedSearch.AdvancedSearchController;
import com.example.sqlide.DataScience.LabelEncoder;
import com.example.sqlide.DataScience.Model.Preprocessor;
import com.example.sqlide.Metadata.RoutineMetadata;
import com.example.sqlide.drivers.model.DataBase;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import static com.example.sqlide.popupWindow.handleWindow.ShowConfirmation;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;
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

    @FXML
    private ChoiceBox<String> TargetBox;
    @FXML
    private ChoiceBox<NeuralEngineType> EngineBox;
    @FXML
    private ChoiceBox<Preprocessor.Step> PreprocessBox;
    @FXML
    private Label TrainQueryLabel, TestQueryLabel, StatusLabel;
    @FXML
    private Button TrainButton, CancelButton, TrainQueryButton, TestQueryButton;
    @FXML
    private ProgressBar TrainProgress;

    private final ObservableList<LayerConfiguration> layers = FXCollections.observableArrayList();

    /** Base de dados aberta, de onde saem as linhas de treino e de teste. */
    private DataBase database;
    private String table;
    private final ArrayList<String> columns = new ArrayList<>();
    private final ArrayList<RoutineMetadata> routines = new ArrayList<>();

    private String trainQuery, testQuery;

    private NeuralTrainingService trainingService;

    /**
     * Liga o painel a uma tabela.
     *
     * <p>Sem isto os botoes de query nao tinham de onde tirar colunas: o
     * {@code setTable} estava comentado em quem abre esta janela.</p>
     */
    public void setTable(final DataBase database, final String table,
                         final List<String> columns, final List<RoutineMetadata> routines) {
        this.database = database;
        this.table = table;
        this.columns.clear();
        if (columns != null) this.columns.addAll(columns);
        this.routines.clear();
        if (routines != null) this.routines.addAll(routines);

        TargetBox.getItems().setAll(this.columns);
        if (!this.columns.isEmpty()) TargetBox.getSelectionModel().selectLast();

        setStatus("Ready. Build a training query to start.");
    }

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

        // O valor escrito vai para a camada selecionada. Sem ligações: a entrada de uma
        // camada é derivada da anterior e não pode receber escritas.
        InputSpinner.valueProperty().addListener((_, _, value) -> {
            if (refreshing || currentLayer == null || value == null) return;
            currentLayer.setInNeurons(value);
        });

        OutputSpinner.valueProperty().addListener((_, _, value) -> {
            if (refreshing || currentLayer == null || value == null) return;
            currentLayer.setOutNeurons(value);
        });

        FunctionBox.valueProperty().addListener((_, _, value) -> {
            if (!refreshing && currentLayer != null) currentLayer.setFunction(value);
        });

        LossBox.valueProperty().addListener((_, _, value) -> {
            if (!refreshing && currentLayer != null) currentLayer.setLoss(value);
        });

        LearningSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.001, 1.0, 0.01, 0.001));

        LayerBox.setItems(layers);
        // O converter era criado por um binding dependente do próprio valor da ChoiceBox,
        // ou seja recriava-se a cada seleção. Um converter fixo chega e não realimenta.
        LayerBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(LayerConfiguration layer) {
                if (layer == null) return "";
                final int index = layers.indexOf(layer);
                return (index < 0 ? "layer" : "layer " + (index + 1)) + "  (" + layer.getType() + ")";
            }

            @Override
            public LayerConfiguration fromString(String string) {
                return null;
            }
        });
        LayerBox.getSelectionModel().selectedItemProperty().addListener((_, _, layer) -> updateUI(layer));

        // Motores: so os que estao no classpath. Os que ainda nao tem o nativo ficam
        // assinalados, porque escolhe-los significa esperar por um download grande.
        EngineBox.getItems().setAll(NeuralEngineType.registered());
        EngineBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(NeuralEngineType type) {
                return type == null ? "" : type.describe();
            }

            @Override
            public NeuralEngineType fromString(String string) {
                return null;
            }
        });
        final List<NeuralEngineType> readyEngines = NeuralEngineType.ready();
        if (!readyEngines.isEmpty()) EngineBox.setValue(readyEngines.getFirst());
        else if (!EngineBox.getItems().isEmpty()) EngineBox.getSelectionModel().selectFirst();

        // Normalizar as entradas antes de treinar e pratica corrente em aprendizagem
        // profunda: sem isso uma coluna numa escala muito maior domina os gradientes.
        PreprocessBox.getItems().setAll(Preprocessor.Step.values());
        PreprocessBox.setValue(Preprocessor.Step.STANDARDIZE);

        updateLayerBindings();
        LayerBox.getSelectionModel().selectFirst();

        layers.addListener((ListChangeListener.Change<? extends LayerConfiguration> change) -> {
            while (change.next()) {
                if (change.wasAdded() || change.wasRemoved() || change.wasUpdated()) {
                    updateLayerBindings();
                }
            }
        });
    }

    /** Camada mostrada neste momento, para saber onde escrever o que o utilizador altera. */
    private LayerConfiguration currentLayer;

    /** Evita que o listener do spinner reaja às escritas feitas pelo próprio código. */
    private boolean refreshing = false;

    /**
     * Mostra uma camada nos campos.
     *
     * <p>Aqui estava o bug do binder. Havia dois sistemas a disputar a mesma propriedade:</p>
     *
     * <ul>
     *   <li>{@code updateLayerBindings} fazia
     *       {@code inNeurons.bind(anterior.outNeurons)}, o que torna {@code inNeurons}
     *       <em>só de leitura</em>;</li>
     *   <li>e logo a seguir o {@code updateUI} fazia
     *       {@code spinner.valueProperty().bindBidirectional(inNeurons.asObject())}, que
     *       tenta <em>escrever</em> nessa mesma propriedade assim que o spinner mexe —
     *       {@code java.lang.RuntimeException: A bound value cannot be set}.</li>
     * </ul>
     *
     * <p>E havia um segundo problema por baixo: {@code asObject()} devolve um objeto novo
     * a cada chamada, as ligações bidirecionais guardam referências fracas, e o
     * {@code unbind()} unidirecional nunca desfaz uma ligação bidirecional. Ou seja, cada
     * troca de camada deixava mais uma ligação morta pendurada.</p>
     *
     * <p>A solução é não ligar de todo: as entradas são um valor derivado, mostram-se e o
     * campo fica bloqueado; só as saídas se escrevem, e por listener.</p>
     */
    private void updateUI(final LayerConfiguration layerConfiguration) {
        if (layerConfiguration == null) return;

        currentLayer = layerConfiguration;
        LayerLabel.setText("Layer Type: " + layerConfiguration.getType());

        refreshing = true;
        try {
            InputSpinner.getValueFactory().setValue(layerConfiguration.getInNeurons());
            OutputSpinner.getValueFactory().setValue(layerConfiguration.getOutNeurons());

            if (layerConfiguration.getFunction() != null) FunctionBox.setValue(layerConfiguration.getFunction());
            if (layerConfiguration.getLoss() != null) LossBox.setValue(layerConfiguration.getLoss());
        } finally {
            refreshing = false;
        }

        // Só a camada de entrada tem entradas próprias; nas outras é o valor da anterior.
        InputSpinner.setDisable(layerConfiguration.isInNeuronsDerived());
    }

    private void updateLayerBindings() {
        // Desliga tudo antes de religar, senão uma camada que mudou de posição ficaria
        // presa à anterior antiga.
        for (LayerConfiguration layer : layers) layer.clearPreviewLayer();

        for (int i = 1; i < layers.size(); i++) {
            try {
                layers.get(i).setPreviewLayer(layers.get(i - 1));
            } catch (Exception _) {
                // Só lança para a camada de entrada, que nunca entra neste ciclo.
            }
        }

        // O que está no ecrã pode ter mudado de entradas por causa da religação.
        if (currentLayer != null) updateUI(currentLayer);
    }

    @FXML
    private void AddLayer() {
        int index = LayerBox.getSelectionModel().getSelectedIndex();
        // Sem seleção o índice é -1 e o add(-1, ...) lançava IndexOutOfBounds.
        if (index < 0) index = Math.max(0, layers.size() - 1);
        else if (index != layers.size() - 1) ++index;

        layers.add(index, new LayerConfiguration(LayerConfiguration.LAYER_TYPE.HIDDEN));
        LayerBox.getSelectionModel().select(index);
    }

    @FXML
    private void RemoveLayer() {
        final int index = LayerBox.getSelectionModel().getSelectedIndex();
        if (index < 0) return;

        final LayerConfiguration layer = layers.get(index);
        if (layer.getType() == LayerConfiguration.LAYER_TYPE.HIDDEN && layers.size() > 3) {
            layers.remove(index);
            // Depois de remover o último item, esse índice já não existe.
            LayerBox.getSelectionModel().select(Math.min(index, layers.size() - 1));
        } else ShowInformation("Invalid layer", "You cannot remove input or output layer.");
    }

    /** As camadas configuradas, pela ordem em que formam a rede. */
    public ObservableList<LayerConfiguration> getLayers() {
        return layers;
    }


    // ==== Dados ====

    @FXML
    private void buildTrainQuery() {
        final String query = askForQuery("Training data");
        if (query == null) return;
        trainQuery = query;
        TrainQueryLabel.setText(query);
    }

    @FXML
    private void buildTestQuery() {
        final String query = askForQuery("Test data");
        if (query == null) return;
        testQuery = query;
        TestQueryLabel.setText(query);
    }

    /**
     * Abre o construtor visual de consultas, o mesmo que o resto do programa usa.
     *
     * @return a consulta escrita, ou null se a janela foi fechada sem confirmar
     */
    private String askForQuery(final String title) {
        if (database == null) {
            ShowInformation("No table", "This window was opened without a table.");
            return null;
        }

        try {
            final FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedSearchStage.fxml"));
            final Parent root = loader.load();

            final AdvancedSearchController controller = loader.getController();
            final Stage stage = new Stage();
            stage.setTitle(title);
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);

            controller.setCode("SELECT");
            controller.setTable(table);
            final HashMap<String, ArrayList<String>> map = new HashMap<>();
            map.put(table, new ArrayList<>(columns));
            controller.setColumns(map);
            controller.setRoutines(routines);
            controller.setStage(stage);

            stage.showAndWait();

            final String query = controller.getQuery();
            return query == null || query.isBlank() ? null : query;
        } catch (Exception e) {
            ShowError("Error", "Could not open the query builder.", e.getMessage());
            return null;
        }
    }

    // ==== Treino ====

    @FXML
    private void startTraining() {
        if (database == null) {
            ShowInformation("No table", "This window was opened without a table.");
            return;
        }
        if (trainQuery == null) {
            ShowInformation("No training data", "Build the training query first.");
            return;
        }
        if (TargetBox.getValue() == null) {
            ShowInformation("No label", "Choose the column the network should predict.");
            return;
        }
        if (EngineBox.getValue() == null) {
            ShowInformation("No engine", "No deep learning engine is on the classpath.");
            return;
        }

        final NeuralEngineType engine = EngineBox.getValue();
        final String target = TargetBox.getValue();
        final Preprocessor.Step step = PreprocessBox.getValue();
        final String modelName = ModelField.getText();
        final String path = PathField.getText();

        // Escolher um motor sem o nativo descarregado bloqueia a arrancar o treino
        // enquanto varias centenas de MB descem. Mais vale dize-lo antes.
        if (!engine.isDownloaded() && !ShowConfirmation("Download needed",
                "The " + engine + " native library is not on this machine yet. Starting the "
                        + "training will download it first, which can take several minutes. Continue?")) {
            return;
        }

        final List<LayerConfiguration> snapshot = new ArrayList<>(layers);
        final String optimizer = OptimizerBox.getValue();
        final String lossName = LossBox.getValue();
        final float learningRate = LearningSpinner.getValue().floatValue();
        final int epochs = EpochSpinner.getValue();
        final int batch = BatchSpinner.getValue();

        trainingService = new NeuralTrainingService();
        setTrainingActive(true);
        setStatus(engine.isDownloaded() ? "Reading rows..."
                : "Downloading the " + engine + " native library, this can take a while...");

        Thread.ofVirtual().start(() -> {
            try {
                final NeuralData data = readRows(trainQuery, target, step);

                // As dimensoes tem de estar certas ANTES de train() as verificar. Isto era
                // feito dentro do readRows por Platform.runLater, ou seja assincronamente:
                // o treino corria primeiro, via a largura antiga e recusava sempre com
                // "The input layer expects N value(s) but the data has M column(s)".
                final int inputWidth = data.featureNames().size();
                final int outputWidth = data.isClassification() ? data.classes().size() : 1;
                snapshot.getFirst().setInNeurons(inputWidth);
                snapshot.getLast().setOutNeurons(outputWidth);

                Platform.runLater(() -> {
                    if (!layers.isEmpty()) {
                        layers.getFirst().setInNeurons(inputWidth);
                        layers.getLast().setOutNeurons(outputWidth);
                    }
                    updateUI(currentLayer);
                    if (data.isClassification()) {
                        setStatus("Classifying " + data.classes().size() + " class(es): "
                                + String.join(", ", data.classes()));
                    }
                });

                // Com alvo categorico a perda tem de ser de classificacao, senao a rede
                // trata os indices das classes como se fossem uma grandeza continua.
                final NeuralTrainingService.Request request = new NeuralTrainingService.Request(
                        engine, snapshot, optimizer,
                        data.isClassification() ? "CROSS_ENTROPY" : lossName,
                        learningRate, epochs, batch, data.isClassification());

                final NeuralTrainingService.Outcome outcome = trainingService.train(
                        request, data.x(), data.y(),
                        (progress, line) -> Platform.runLater(() -> {
                            TrainProgress.setProgress((double) progress.epoch() / progress.totalEpochs());
                            setStatus(line);
                        }),
                        path == null || path.isBlank() ? null : Path.of(path),
                        modelName);

                // Sem uma mensagem terminal no caso cancelado a UI ficava para sempre
                // em "Cancelling...", como se nada tivesse acontecido.
                Platform.runLater(() -> setStatus(trainingService.isCancelled()
                        ? String.format(Locale.US, "Cancelled after %d epoch(s), last loss %.6f",
                            outcome.epochs(), outcome.finalLoss())
                        : String.format(Locale.US, "Done: %d epoch(s) on %s, final loss %.6f%s",
                            outcome.epochs(), outcome.engine(), outcome.finalLoss(),
                            outcome.savedTo() == null ? "" : ", saved to " + outcome.savedTo())));

                if (testQuery != null && !trainingService.isCancelled()) evaluate(target, step);

            } catch (Exception e) {
                // Um cancelamento interrompe a thread e sai por excecao: nao e uma falha.
                if (trainingService.isCancelled()) {
                    Platform.runLater(() -> setStatus("Cancelled."));
                    return;
                }
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                final String message = root.getMessage() == null ? root.toString() : root.getMessage();
                Platform.runLater(() -> {
                    setStatus("Training failed.");
                    ShowError("Training failed", "The network could not be trained.", message);
                });
            } finally {
                Platform.runLater(() -> setTrainingActive(false));
            }
        });
    }

    /** Corre a consulta de teste, para dizer quantas linhas ficaram utilizaveis. */
    private void evaluate(final String target, final Preprocessor.Step step) {
        try {
            final NeuralData data = readRows(testQuery, target, step);
            Platform.runLater(() -> setStatus(StatusLabel.getText()
                    + "  |  test set: " + data.x().length + " usable row(s)"));
        } catch (Exception e) {
            Platform.runLater(() -> setStatus(StatusLabel.getText()
                    + "  |  test query returned nothing usable"));
        }
    }

    @FXML
    private void cancelTraining() {
        if (trainingService != null) trainingService.cancel();
        setStatus("Cancelling after the current epoch...");
    }

    private void setTrainingActive(final boolean active) {
        TrainButton.setDisable(active);
        CancelButton.setDisable(!active);
        TrainQueryButton.setDisable(active);
        TestQueryButton.setDisable(active);
        if (!active) TrainProgress.setProgress(0);
    }

    private void setStatus(final String message) {
        if (StatusLabel != null) StatusLabel.setText(message);
    }

    /**
     * Matriz de entradas, alvos, e as classes quando o alvo e categorico.
     *
     * @param classes nomes das classes por indice, ou lista vazia numa regressao
     */
    private record NeuralData(float[][] x, float[][] y, List<String> featureNames, List<String> classes) {

        boolean isClassification() {
            return !classes.isEmpty();
        }
    }

    /**
     * Le as linhas da consulta e separa a coluna alvo das restantes.
     *
     * <p>Quando o alvo nao e numerico — um nome de especie, uma categoria — os valores sao
     * codificados em indices por um {@link LabelEncoder}. Antes disso qualquer alvo de
     * texto fazia com que <em>todas</em> as linhas fossem descartadas e o treino falhasse
     * com "No fully numeric rows", que nao dizia qual era o problema.</p>
     *
     * <p>As colunas de entrada continuam a ter de ser numericas: linhas com texto nelas
     * sao descartadas, porque converter texto para zero enviesaria o treino em silencio.</p>
     */
    private NeuralData readRows(final String query, final String target, final Preprocessor.Step step) {
        final ArrayList<HashMap<String, String>> rows = database.Fetcher().fetchRawDataMap(query);
        if (rows == null || rows.isEmpty()) throw new IllegalStateException("The query returned no rows.");

        final List<String> features = new ArrayList<>(rows.getFirst().keySet());
        features.remove(target);
        if (features.isEmpty()) {
            throw new IllegalStateException("The query must select at least one column besides "
                    + target + ". Add the input columns to the query.");
        }

        // Decide-se pela propria coluna: se algum valor nao for numero, e uma categoria.
        boolean categorical = false;
        for (final HashMap<String, String> row : rows) {
            final String raw = row.get(target);
            if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("null")) continue;
            if (parse(raw) == null) {
                categorical = true;
                break;
            }
        }

        final LabelEncoder encoder = new LabelEncoder();
        if (categorical) {
            final List<String> labels = new ArrayList<>();
            for (final HashMap<String, String> row : rows) {
                final String raw = row.get(target);
                if (raw != null && !raw.isBlank() && !raw.equalsIgnoreCase("null")) labels.add(raw.trim());
            }
            encoder.updateEncoder(labels);
            if (encoder.size() < 2) {
                throw new IllegalStateException("Column " + target + " has only one distinct value; "
                        + "there is nothing to classify.");
            }
        }

        final List<double[]> inputs = new ArrayList<>();
        final List<Double> targets = new ArrayList<>();

        for (final HashMap<String, String> row : rows) {
            final String rawLabel = row.get(target);
            final Double label;

            if (categorical) {
                if (rawLabel == null || rawLabel.isBlank() || rawLabel.equalsIgnoreCase("null")) continue;
                final int encoded = encoder.encode(rawLabel.trim());
                if (encoded < 0) continue;
                label = (double) encoded;
            } else {
                label = parse(rawLabel);
                if (label == null) continue;
            }

            final double[] values = new double[features.size()];
            boolean complete = true;
            for (int i = 0; i < features.size(); i++) {
                final Double value = parse(row.get(features.get(i)));
                if (value == null) {
                    complete = false;
                    break;
                }
                values[i] = value;
            }
            if (!complete) continue;

            inputs.add(values);
            targets.add(label);
        }

        if (inputs.isEmpty()) {
            throw new IllegalStateException("No usable rows: every row had a non-numeric value in one of "
                    + "the input columns " + features + ".");
        }

        double[][] matrix = inputs.toArray(new double[0][]);

        if (step != null && step != Preprocessor.Step.NONE) {
            final Preprocessor preprocessor = new Preprocessor();
            preprocessor.setSteps(List.of(step));
            preprocessor.fit(matrix);
            matrix = preprocessor.transform(matrix);
        }

        final float[][] x = new float[matrix.length][features.size()];
        final float[][] y = new float[matrix.length][1];
        for (int r = 0; r < matrix.length; r++) {
            for (int c = 0; c < features.size(); c++) x[r][c] = (float) matrix[r][c];
            y[r][0] = targets.get(r).floatValue();
        }

        return new NeuralData(x, y, features, categorical ? encoder.classes() : List.of());
    }

    private static Double parse(final String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("null")) return null;
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

}
