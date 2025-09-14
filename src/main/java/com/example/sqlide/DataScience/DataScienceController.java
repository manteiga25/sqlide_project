package com.example.sqlide.DataScience;

import com.example.sqlide.Container.LongField.LongField;
import com.example.sqlide.DataScience.Model.ModelPipeline;
import com.example.sqlide.DataScience.Model.Models;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Task.TaskInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.misc.memoryInterface;
import com.example.sqlide.misc.path;
import com.jfoenix.controls.JFXTextField;
import com.mysql.cj.log.Log;
import com.mysql.cj.util.DnsSrv;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingNode;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.controlsfx.control.CheckComboBox;
import org.docx4j.wml.Numbering;
import org.jetbrains.annotations.NotNull;
import smile.classification.KNN;
import smile.classification.LogisticRegression;
import smile.data.*;
import smile.data.formula.Formula;
import smile.data.vector.DoubleVector;
import smile.data.vector.IntVector;
import smile.data.vector.ValueVector;
import smile.plot.swing.BoxPlot;
import smile.plot.swing.Canvas;
import smile.plot.swing.Line;
import smile.regression.*;
import smile.util.Index;
import smile.validation.metric.MSE;

import javax.swing.*;
import javax.tools.Diagnostic;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Controller for DataScienceStage.fxml
 * NOTE: This is a pragmatic, self-contained controller with zero external deps.
 * It supports: CSV preview/load, dynamic columns, basic stats, mean/median imputation,
 * a simple linear regression imputation (auto-picks strongest single numeric predictor),
 * outlier preview (IQR / Z-score), basic export, and simple charts.
 *
 * Integrate with your own data layer as needed. All algorithmic parts are intentionally
 * lightweight. Replace with your production logic where appropriate.
 */
public class DataScienceController {

    public TableView<ColumnStatistic> tableStat;
    public TableColumn<ColumnStatistic, String> columnName;
    public TableColumn<ColumnStatistic, Double> columnMean, columnMedian, columnSTD, columnMin, columnMax, columnVariance, columnSquareVariance;
    public HBox boxStart;
    public FlowPane TestBox;
    public SwingNode chart;
    public ChoiceBox<String> boxFrequency;
    public Spinner<Integer> binSpinner;
    public BorderPane MainContainer;
    public CheckComboBox<String> comboSerie;
    public ToolBar PageGraph;
    public JFXTextField textQueryTest;
    public Spinner<Integer> limitSpinner, batchSpinner, offsetSpinner;
    public ScatterChart<String, String> graphModelEvaluation;
    private DatabaseUpdaterInterface updater;
    private DatabaseFetcherInterface fetcher;

    private TaskInterface taskInterface = null;

    public void setTaskInterface(final TaskInterface taskInterface) {
        this.taskInterface = taskInterface;
    }

    private final memoryInterface memInfo = new memoryInterface() {
        @Override
        public void onLowMemory() {
        }
    };

    private final SimpleLongProperty memoryState = new SimpleLongProperty();

    private String table;

    private ArrayList<ColumnMetadata> metadata;

    private int pages;

    private final LongField field = new LongField();

    // ==== TOP BAR ====
    @FXML
    private Button btnOpen, btnSave, btnUndo, btnRedo;
    @FXML
    private TextField searchField;
    @FXML
    private ChoiceBox<String> choiceDataset;
    @FXML
    private ProgressBar globalProgress;
    @FXML
    private Label lblStatus;

    // ==== LEFT: datasets/columns/meta ====
    @FXML
    private ListView<String> lvDatasets;
    @FXML
    private TableView<ColumnMeta> tblColumns;
    @FXML
    private TableColumn<ColumnMeta, String> colName;
    @FXML
    private TableColumn<ColumnMeta, String> colType;
    @FXML
    private TableColumn<ColumnMeta, Number> colNulls;
    @FXML
    private TextArea taMetadata;

    // ==== Imputation ====
    @FXML
    private VBox ImputBox;
    @FXML
    private ChoiceBox<String> cbImputeColumn;
    @FXML
    private ComboBox<String> comboImputeMethod;
    @FXML
    private Spinner<Integer> spinK; // K for KNN (placeholder)
    @FXML
    private Spinner<Integer> spinIter; // iterations (placeholder)
    @FXML
    private CheckBox chkNormalize;
    @FXML
    private Button btnRunImpute, btnPreviewImpute, btnRevertImpute;
    @FXML
    private ProgressIndicator piImpute;
    @FXML
    private TextArea taImputeLog;
    @FXML
    private TextArea taModelSummary;
    @FXML
    private StackPane chartResiduals;
    @FXML
    private StackPane chartImputeCompare;

    private final ArrayList<ReadOnlyObjectProperty<String>> ModelImputationColumns = new ArrayList<ReadOnlyObjectProperty<String>>();

    private final ArrayList<ReadOnlyObjectProperty<String>> ModelTrainImputationColumns = new ArrayList<>();

    // ==== Outliers ====
    @FXML
    private ChoiceBox<String> cbOutlierColumn;
    @FXML
    private ComboBox<String> comboOutlierMethod;
    @FXML
    private TextField tfOutlierThresh;
    @FXML
    private Button btnDetectOutliers, btnRemoveOutliers;
    @FXML
    private TableView<ColumnOutliers> tblOutliersPreview;
    @FXML
    private BarChart<String, Number> chartDistribution;

    // ==== Metrics ====
    @FXML
    private TextArea taNumericSummary;
    @FXML
    private StackPane chartCorrelation;
    @FXML
    private Button btnExportCorr;
    @FXML
    private StackPane chartScatterMatrix;
    @FXML
    private Button btnPairPlot;

    // Distribution

    @FXML
    private ChoiceBox<String> boxTarget;
    @FXML
    private TextArea textDistribution;
    @FXML
    private ScrollPane graphstack;

    // ==== Modeling ====
    @FXML
    private ChoiceBox<String> cbTargetColumn;
    @FXML
    private ComboBox<Models> comboModelAlgo;
    @FXML
    private ChoiceBox<String> cbValidation;
    @FXML
    private Button btnTrain, btnEvaluate, btnExportModel;
    @FXML
    private TextArea taModelResults, textPredictHistory;
    @FXML
    private StackPane chartModelMetrics;
    @FXML
    private Spinner<Double> learningRateSpinner;
    @FXML
    private Spinner<Integer> loggingSpinner, testSpinner;
    @FXML
    private LineChart<Number, Number> TrainGraph;
    @FXML
    private VBox boxModel;
    @FXML
    private ScatterChart<Number, Number> graphModelResidual;

    // ==== Visualization ====
    @FXML
    private ChoiceBox<String> cbVizType;
    @FXML
    private ChoiceBox<String> cbVizColumnX;
    @FXML
    private ChoiceBox<String> cbVizColumnY;
    @FXML
    private Button btnRenderViz, btnSaveViz;
    @FXML
    private StackPane chartViz;

    // ==== RIGHT: Quick actions ====
    @FXML
    private Button btnAutoImpute, btnDetectAnomalies, btnQuickStats;
    @FXML
    private ListView<String> lvHistory;
    @FXML
    private Button btnExportCSV, btnExportJSON;

    // ==== BOTTOM ====
    @FXML
    private Label lblFooter, lblMemory;

    // ==== In-memory dataset ====
    private final ObservableList<Map<String, Object>> data = FXCollections.observableArrayList();
    private final List<Map<String, Object>> lastSnapshot = new ArrayList<>(); // for revert
    private final ObservableList<String> headers = FXCollections.observableArrayList();
    private final ObservableList<ColumnMeta> columnMetaList = FXCollections.observableArrayList();

    private final ModelPipeline ml_pipeline = new ModelPipeline();

    // last imputation preview cache (rowIndex -> imputed value)
    private Map<Integer, Object> lastImputePreview = new HashMap<>();

    private Object model = null;
    private String model_type = null;

    // ---- lifecycle ----
    @FXML
    public void initialize() {
        try {
            setupColumnsTable();
            setupTopBar();
            setupImputationPane();
           // setupOutliersPane();
            setupMetricsPane();
            setupDistributionPane();
            setupModelingPane();
            setupVisualizationPane();
            setupQuickActions();
            bindSearch();
            setStatus("Pronto");

            memoryState.bind(memInfo.getAvalaibleMemoryProp());
            memoryState.addListener(_->Platform.runLater(()->lblMemory.setText("Memory Available: " + memoryState.get())));

            field.setPrefWidth(50);
            field.setAlignment(Pos.CENTER);
            field.setStyle("-jfx-focus-color: transparent; -jfx-unfocus-color: transparent;");
            PageGraph.getItems().add(1, field);

        } catch (Exception ex) {
            ex.printStackTrace();
            setStatus("Erro ao inicializar: " + ex.getMessage());
        }
    }

    public void setDatabase(final DatabaseUpdaterInterface updater, final DatabaseFetcherInterface fetcher) {
        this.updater = updater;
        this.fetcher = fetcher;
    }

    public void setMetadata(final String table, final ArrayList<ColumnMetadata> metadata) {
        this.table = table;
        this.metadata = metadata;
        load_metadata();
        load_table_metadata();
    }

    private void load_metadata() {
            for (ColumnMetadata columnMetadata : metadata) headers.add(columnMetadata.Name);
            refreshColumnChoices();
    }

    @FXML
    private void load_table_metadata() {
        tblColumns.getItems().clear();
        Thread.ofVirtual().start(()->{
            for (ColumnMetadata columnMetadata : metadata) columnMetaList.add(new ColumnMeta(columnMetadata.Name, columnMetadata.Type, fetcher.fetchDataMap(String.format("SELECT COUNT(*) FROM %s WHERE %s is null;", table, columnMetadata.Name)).getFirst().intValue()));
        });
    }

    // ---- setup helpers ----
    private void setupColumnsTable() {
        tblColumns.setItems(columnMetaList);
        colName.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().name));
        colType.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().type));
        colNulls.setCellValueFactory(cd -> cd.getValue().nulls);
    }

    private void setupTopBar() {
        if (choiceDataset != null) {
            choiceDataset.setItems(FXCollections.observableArrayList("Dataset atual"));
            choiceDataset.getSelectionModel().selectFirst();
        }
        if (btnSave != null) btnSave.setOnAction(this::handleExportCSV);
        if (btnUndo != null) btnUndo.setOnAction(e -> revertToLastSnapshot());
        if (btnRedo != null) btnRedo.setDisable(true); // placeholder
    }

    private void setupImputationPane() {
        if (comboImputeMethod != null) {
            comboImputeMethod.setItems(FXCollections.observableArrayList(
                    "Linear Regression (prever com outras colunas)",
                    "Média",
                    "Mediana",
                    "KNN (k-Nearest)",
                    "Interpolação"
            ));
            comboImputeMethod.getSelectionModel().selectFirst();
        }
        if (spinK != null) {
            spinK.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 5));
        }
        if (spinIter != null) {
            spinIter.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 10000, 100));
        }
        if (btnPreviewImpute != null) btnPreviewImpute.setOnAction(this::handlePreviewImputation);
        if (btnRunImpute != null) btnRunImpute.setOnAction(this::handleRunImputation);
        if (btnRevertImpute != null) btnRevertImpute.setOnAction(e -> revertToLastSnapshot());

        AddColumn();

    }

   /* private void setupOutliersPane() {
        if (comboOutlierMethod != null) {
          //  comboOutlierMethod.setItems(FXCollections.observableArrayList("IQR", "Z-score", "Isolation Forest"));
            comboOutlierMethod.getSelectionModel().selectFirst();
        }
    } */

    private void setupMetricsPane() {
        if (btnExportCorr != null) btnExportCorr.setOnAction(this::handleExportCorrelationMatrix);
        if (btnPairPlot != null) btnPairPlot.setOnAction(e -> renderScatterMatrix());
        columnName.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().name));
        columnMin.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().min));
        columnMax.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().max));
        columnMean.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().mean));
        columnMedian.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().median));
        columnSTD.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().std));
        columnVariance.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().variance));
        columnSquareVariance.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue().squareVariance));
    }

    private void setupDistributionPane() {
        boxTarget.setItems(headers);
        binSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1, 1));
    }

    private void setupModelingPane() {
        if (cbValidation != null) {
            cbValidation.getSelectionModel().selectFirst();
        }
        if (btnTrain != null)
            btnTrain.setOnAction(e -> TrainModel());
        comboModelAlgo.getItems().addAll(FXCollections.observableArrayList(Models.values()));
        comboModelAlgo.getSelectionModel().selectFirst();
        AddColumnTrain();
        learningRateSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.01,1.0, 0.05,0.01));
        batchSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(50, 1000000, 10000, 50));
        limitSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(-1, Integer.MAX_VALUE, 50, 50));
        offsetSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 50, 50));
        testSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 20, 1));

        XYChart.Series<Number, Number> series = new XYChart.Series<>();
        series.setName("Resíduos");
        graphModelResidual.getData().add(series);

    }

    @FXML
    private void evaluateModel() {
        final String query = textQueryTest.getText();

        final String where = !query.isEmpty() ? "WHERE " + query : "";

       /* if (query == null || query.isEmpty() || query.toUpperCase().contains("UPDATE") || query.toUpperCase().contains("DELETE") || query.toUpperCase().contains("LIMIT") || query.toUpperCase().contains("OFFSET")) {
            return;
        } */

        graphModelEvaluation.getData().clear();

        ArrayList<HashMap<String, String>> data_map = fetcher.fetchRawDataMap(String.format("SELECT %s FROM %s %s LIMIT %d OFFSET %d;", Arrays.toString(ml_pipeline.getSchema().toArray(new String[0])) + ", " + ml_pipeline.getY(), table, where, 200, 0));

        String[] y = data_map.stream().map(k->k.get(ml_pipeline.getY())).toArray(String[]::new);

        LinearModel model = (LinearModel) this.model;

        final List<ValueVector> vector_list = new ArrayList<>();

        ml_pipeline.getSchema().parallelStream().forEach(list_x->{
            ArrayList<Double> x = data_map.parallelStream().map(m -> m.get(list_x))
                    .map(Double::parseDouble)
                    .collect(Collectors.toCollection(ArrayList::new));
            synchronized (vector_list) {
                vector_list.add(new DoubleVector(list_x, x.stream()
                        .mapToDouble(Double::doubleValue)
                        .toArray()));
            }
        });

        DataFrame data = new DataFrame(new IntVector("No_empty_column_for_smile_dataframe", new int[y.length]));
        for (ValueVector vector : vector_list) data.add(vector);
        data = data.drop("No_empty_column_for_smile_dataframe");

        String[] pred = Arrays.stream(model.predict(data)).mapToObj(String::valueOf)
                .toArray(String[]::new);

        XYChart.Series<String, String> serie = new XYChart.Series<>();// We need to use String for classification models

        for (int index = 0; index < pred.length; index++) {
            serie.getData().add(new XYChart.Data<>(y[index], pred[index]));
        }

        graphModelEvaluation.getData().add(serie);

    }

    private void TrainModel() {

        boxStart.setDisable(true);

        String target = cbTargetColumn.getValue();
        if (target == null) {
            setStatus("Escolha a coluna alvo.");
            return;
        }

        TrainGraph.getData().clear();
        comboSerie.getItems().clear();

        final String[] x_columns = new String[ModelTrainImputationColumns.size()];

        HashMap<String, XYChart.Series<Number, Number>> chartMap = new HashMap<>();

        for (int column_index = 0; column_index < ModelTrainImputationColumns.size(); column_index++) {
            String x = ModelTrainImputationColumns.get(column_index).get();
            if (x == null || x.isEmpty()) {
                setStatus("Escolha todas as colunas.");
                return;
            }
            x_columns[column_index] = x;
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName("Coefficient of " + x);
            TrainGraph.getData().add(series);
        }




        Task<Object> task = new Task<Object>() {

            private int step = 0;

            @Override
            protected void scheduled() {
                super.scheduled();

                XYChart.Series<Number, Number> accuracySerie = new XYChart.Series<>();
                accuracySerie.setName("Accuracy");
                TrainGraph.getData().add(accuracySerie);
                comboSerie.getItems().add("Accuracy");

                ml_pipeline.getAccuracyProperty().addListener((_, _, val)->{
                    Platform.runLater(()->chartMap.get("Accuracy").getData().add(new XYChart.Data<>(step, val.intValue())));
                });

                switch (comboModelAlgo.getSelectionModel().getSelectedItem()) {
                    case LINEAR_REGRESSION -> {

                        for (String col : x_columns) {
                            XYChart.Series<Number, Number> coefficient = new XYChart.Series<>();
                            coefficient.setName("Coefficient of " + col);
                            TrainGraph.getData().add(coefficient);
                            chartMap.put(col, coefficient);
                            comboSerie.getItems().add("Coefficient of " + col);
                        }

                        XYChart.Series<Number, Number> pSerie = new XYChart.Series<>();
                        pSerie.setName("P-value");
                        TrainGraph.getData().add(pSerie);
                        XYChart.Series<Number, Number> rSerie = new XYChart.Series<>();
                        rSerie.setName("R-squared");
                        TrainGraph.getData().add(rSerie);
                        XYChart.Series<Number, Number> errorSerie = new XYChart.Series<>();
                        errorSerie.setName("Error");
                        TrainGraph.getData().add(errorSerie);


                        XYChart.Series<Number, Number> InterceptSerie = new XYChart.Series<>();
                        InterceptSerie.setName("Intercept");
                        TrainGraph.getData().add(InterceptSerie);
                        chartMap.put("Intercept", InterceptSerie);

                        chartMap.put("P-value", pSerie);
                        chartMap.put("R-squared", rSerie);
                        chartMap.put("Error", errorSerie);
                        chartMap.put("Accuracy", accuracySerie);

                        comboSerie.getItems().addAll("Intercept", "R-squared", "P-value", "Error");

                       // taModelResults.setText("");
                    }
                /*    case RANDOM_FOREST_REGRESSION -> {
                        model = TrainForest(cbTargetColumn.getValue(), x_columns);
                    }
                    case TREE_REGRESSION -> {
                        model = TrainRidge(cbTargetColumn.getValue(), x_columns);
                    }
                    case LOGISTIC_REGRESSION -> {
                        model = TrainLogistic(cbTargetColumn.getValue(), x_columns);
                    }
                    case LOGISTIC_BINOMIAL_REGRESSION -> {
                        model = TrainLogisticBinomial(cbTargetColumn.getValue(), x_columns);
                    }
                    case LOGISTIC_MULTIMODAL_REGRESSION -> {
                        model = TrainLogisticMultinomial(cbTargetColumn.getValue(), x_columns);
                    }
                    case RANDOM_FOREST_CLASSIFICATION -> {
                        model = TrainClassificationForest(cbTargetColumn.getValue(), x_columns);
                    } */
                }

                comboSerie.getCheckModel().checkAll();
                comboSerie.setTitle(String.join(", ", comboSerie.getCheckModel().getCheckedItems()));

                comboSerie.getCheckModel().getCheckedItems().addListener((ListChangeListener<? super String>) cheked -> {
                    while (cheked.next()) {
                        if (cheked.wasAdded()) {
                            TrainGraph.getData().add(chartMap.get(cheked.getAddedSubList().getFirst()));
                        } else {
                            TrainGraph.getData().remove(chartMap.get(cheked.getRemoved().getFirst()));
                        }
                    }
                    comboSerie.setTitle(String.join(", ", comboSerie.getCheckModel().getCheckedItems()));
                });
            }

            @Override
            protected void running() {
                super.running();
                updateProgress(-1, -1);
                updateTitle("Training model");
            }

            @Override
            protected Object call() throws Exception {
                final int buffer = batchSpinner.getValue();
                final long limit = limitSpinner.getValue() == -1 ? Long.MAX_VALUE : limitSpinner.getValue();
                int offset = offsetSpinner.getValue();
                String y = cbTargetColumn.getValue();

                ml_pipeline.setModel(comboModelAlgo.getSelectionModel().getSelectedItem());
                ml_pipeline.setSchema(List.of(x_columns), y);
                ml_pipeline.getLabelEncoder().flush();
                ml_pipeline.setLearningRate(learningRateSpinner.getValue());

                final String command = getCommand(table, y, x_columns, buffer);

                ArrayList<HashMap<String, String>> raw_data = fetcher.fetchRawDataMap(String.format(command, offset));

                ml_pipeline.Train(raw_data);

                step++;

                Platform.runLater(()->updateGraph(ml_pipeline.getModel()));

                offset += buffer;

                while (raw_data.size() == buffer && offset < limit) {
                    offset += buffer;

                    raw_data = fetcher.fetchRawDataMap(String.format(command, offset));

                    ml_pipeline.update(raw_data);

                    step++;

                    Platform.runLater(()->{
                        updateGraph(ml_pipeline.getModel());

                    });
                    //updateGraphResidual(ml_pipeline.getModel());
                }

                return ml_pipeline.getModel();
            }

            @Override
            protected void failed() {
                super.failed();
                getException().printStackTrace();
                Throwable exception = this.getException();
                if (exception instanceof OutOfMemoryError) ShowError("Error", "Error to train model.", exception.getMessage());
                else ShowError("Error", "Device has no memory to train model. Try to set a minor batch or limit value.");
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                DataScienceController.this.model = this.resultNow();



                switch (ml_pipeline.getModel_type()) {
                    case LINEAR_REGRESSION -> {
                        model_type = "Linear Regression";
                     /*   LinearModel model = (LinearModel) generic_model;
                        pages = model.residuals().length / 200;
                        taModelResults.appendText("Model Linear Regression (Final Result): " + model.RSS() + " " + model.adjustedRSquared() + " " + model.error() + " " + model.ftest() + " " + model.intercept() + " " + model.pvalue()+"\n\n\n");
                        field.setNumber(0); */
                    }
                    case RANDOM_FOREST_REGRESSION -> {
                        model_type = "Random Forest";
                    }
                    case TREE_REGRESSION -> {
                    }
                    case LOGISTIC_REGRESSION -> {
                        model_type = "Logistic Regression";
                    }
                    case RANDOM_FOREST_CLASSIFICATION -> {
                    }

                }

                appendLogModel("Final Result");

                field.setNumber(0);

                TestBox.getChildren().clear();

                for (String col : x_columns) addTestModelText(col);

            }

            @Override
            protected void done() {
                super.done();
                boxStart.setDisable(false);
            }

            private void appendLogModel(String step) {
                Map<String, Object> metrics = ml_pipeline.getModelMetrics();

                StringBuilder result = new StringBuilder(String.format("Model %s (%s): ", model_type, step));

                for (String key : metrics.keySet()) result.append(key).append(" ").append(metrics.get(key)).append("; ");

                taModelResults.appendText(result.append("\n\n\n").toString());
            }

            private void updateGraph(final Object model) {
                switch (comboModelAlgo.getSelectionModel().getSelectedItem()) {
                    case LINEAR_REGRESSION -> {

                        LinearModel linearModel = (LinearModel) model;

                        for (int index = 0; index < linearModel.coefficients().length; index++) chartMap.get(ml_pipeline.getSchema().get(index)).getData().add(new XYChart.Data<>(step, linearModel.coefficients()[index]));

                        chartMap.get("Intercept").getData().add(new XYChart.Data<>(step, linearModel.intercept()));
                        chartMap.get("Error").getData().add(new XYChart.Data<>(step, linearModel.error()));
                        chartMap.get("P-value").getData().add(new XYChart.Data<>(step, linearModel.pvalue()));
                        chartMap.get("R-squared").getData().add(new XYChart.Data<>(step, linearModel.RSquared()));



                        // taModelResults.setText("");
                    }
                    case RANDOM_FOREST_REGRESSION -> {
                        smile.regression.RandomForest randomForest = (RandomForest) model;
                        chartMap.get("R2").getData().add(new XYChart.Data<>(step, randomForest.metrics().r2()));
                        chartMap.get("MSE").getData().add(new XYChart.Data<>(step, randomForest.metrics().mse()));
                        chartMap.get("RES").getData().add(new XYChart.Data<>(step, randomForest.metrics().rss()));
                        chartMap.get("RMSE").getData().add(new XYChart.Data<>(step, randomForest.metrics().rmse()));
                    }
                    case TREE_REGRESSION -> {
                        smile.regression.RegressionTree regressionTree = (RegressionTree) model;

                    }
                  /*  case LOGISTIC_REGRESSION -> {
                        model = TrainLogistic(cbTargetColumn.getValue(), x_columns);
                    }
                    case LOGISTIC_BINOMIAL_REGRESSION -> {
                        model = TrainLogisticBinomial(cbTargetColumn.getValue(), x_columns);
                    }
                    case LOGISTIC_MULTIMODAL_REGRESSION -> {
                        model = TrainLogisticMultinomial(cbTargetColumn.getValue(), x_columns);
                    }
                    case RANDOM_FOREST_CLASSIFICATION -> {
                        model = TrainClassificationForest(cbTargetColumn.getValue(), x_columns);
                    } */
                }
                appendLogModel("step " + step);
            }

            private void updateGraphResidual(final Object model) {
                switch (comboModelAlgo.getSelectionModel().getSelectedItem()) {
                    case LINEAR_REGRESSION -> {

                        LinearModel linearModel = (LinearModel) model;

                        for (double residual : linearModel.residuals()) graphModelResidual.getData().getFirst().getData().addAll(new XYChart.Data<>(residual, 0));

                        // taModelResults.setText("");
                    }
                    case RANDOM_FOREST_REGRESSION -> {
                        smile.regression.RandomForest randomForest = (RandomForest) model;
                        chartMap.get("R2").getData().add(new XYChart.Data<>(step, randomForest.metrics().r2()));
                        chartMap.get("MSE").getData().add(new XYChart.Data<>(step, randomForest.metrics().mse()));
                        chartMap.get("RES").getData().add(new XYChart.Data<>(step, randomForest.metrics().rss()));
                        chartMap.get("RMSE").getData().add(new XYChart.Data<>(step, randomForest.metrics().rmse()));
                    }
                    case TREE_REGRESSION -> {
                        smile.regression.RegressionTree regressionTree = (RegressionTree) model;

                    }
                  /*  case LOGISTIC_REGRESSION -> {
                        model = TrainLogistic(cbTargetColumn.getValue(), x_columns);
                    }
                    case LOGISTIC_BINOMIAL_REGRESSION -> {
                        model = TrainLogisticBinomial(cbTargetColumn.getValue(), x_columns);
                    }
                    case LOGISTIC_MULTIMODAL_REGRESSION -> {
                        model = TrainLogisticMultinomial(cbTargetColumn.getValue(), x_columns);
                    }
                    case RANDOM_FOREST_CLASSIFICATION -> {
                        model = TrainClassificationForest(cbTargetColumn.getValue(), x_columns);
                    } */
                }
            }

        };

        Thread train = new Thread(task);
        train.setDaemon(true);
        train.start();

        taskInterface.addTask(task);


    }

    @FXML
    private void forwardResidualPage() {
        long page = field.getNumber()-1;

        if (page >= 0) {
            field.setNumber(page);
            handleBoxPage();
        }

    }

    @FXML
    private void nextResidualPage() {
        long page = field.getNumber()+1;

        if (page <= pages) {
            field.setNumber(page);
            handleBoxPage();
        }

    }

    private void handleBoxPage() {
        setResidualGraph((int) field.getNumber());
    }

    private void setResidualGraph(final int page) {
        graphModelResidual.getData().getFirst().getData().clear();
        Thread.ofVirtual().start(()->{
            LinearModel model1 = (LinearModel) model;
            final double[] residual = Arrays.copyOfRange(model1.residuals(), pages*page, pages*page+200); // to switch
            int len = residual.length;
            List<XYChart.Data<Number, Number>> data_list = new ArrayList<>(len);
            List<Double> y_data = fetcher.fetchDataMap(String.format("SELECT %s FROM %s LIMIT %d OFFSET %d;", ml_pipeline.getY(), table, 200, pages*page));
            for (int index = 0; index < len; index++) {
                data_list.add(new XYChart.Data<>(residual[index], y_data.get(index)));
            }
            Platform.runLater(()->graphModelResidual.getData().getFirst().getData().addAll(data_list));
        });

    }

    private void addTestModelText(final String x) {
        TextField text = new TextField();
        text.setId(x);
        text.setPadding(new Insets(0, 5, 0, 0));
        TestBox.getChildren().addAll(new Label(x), text);
    }

    private void setupVisualizationPane() {
        if (cbVizType != null) {
            cbVizType.setItems(FXCollections.observableArrayList("Histograma", "Boxplot", "Scatter", "Heatmap (Correlação)"));
            cbVizType.getSelectionModel().selectFirst();
        }
        if (btnRenderViz != null) btnRenderViz.setOnAction(this::handleRenderViz);
        if (btnSaveViz != null)
            btnSaveViz.setOnAction(e -> setStatus("Salvar gráfico: implemente conforme necessidade."));
    }

    private void setupQuickActions() {
        //   if (btnAutoImpute != null) btnAutoImpute.setOnAction(e -> autoImpute());
        if (btnDetectAnomalies != null) btnDetectAnomalies.setOnAction(e -> handleDetectOutliers(null));
        if (btnQuickStats != null) btnQuickStats.setOnAction(e -> computeQuickStats());
        if (btnExportCSV != null) btnExportCSV.setOnAction(this::handleExportCSV);
        if (btnExportJSON != null) btnExportJSON.setOnAction(this::handleExportJSON);
    }

    private void bindSearch() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, old, val) -> filterColumns(val));
        }
    }

    private void handleExportCSV(ActionEvent e) {
        if (headers.isEmpty()) {
            setStatus("Nada para exportar.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File f = fc.showSaveDialog(getWindow(e));
        if (f == null) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            pw.println(String.join(",", headers));
            for (Map<String, Object> row : data) {
                List<String> cells = new ArrayList<>();
                for (String h : headers) {
                    Object v = row.get(h);
                    cells.add(escapeCsv(v == null ? "" : v.toString()));
                }
                pw.println(String.join(",", cells));
            }
            setStatus("Exportado: " + f.getName());
            addHistory("Export CSV: " + f.getName());
        } catch (Exception ex) {
            setStatus("Erro ao exportar: " + ex.getMessage());
        }
    }

    private void handleExportJSON(ActionEvent e) {
        if (headers.isEmpty()) {
            setStatus("Nada para exportar.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File f = fc.showSaveDialog(getWindow(e));
        if (f == null) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            pw.println("[");
            for (int i = 0; i < data.size(); i++) {
                Map<String, Object> row = data.get(i);
                String json = headers.stream()
                        .map(h -> "\"" + escapeJson(h) + "\": " + toJsonValue(row.get(h)))
                        .collect(Collectors.joining(", "));
                pw.print("  {" + json + "}");
                if (i < data.size() - 1) pw.println(",");
                else pw.println();
            }
            pw.println("]");
            setStatus("Exportado JSON: " + f.getName());
            addHistory("Export JSON: " + f.getName());
        } catch (Exception ex) {
            setStatus("Erro ao exportar: " + ex.getMessage());
        }
    }

    private static String escapeCsv(String s) {
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String toJsonValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Number || v instanceof Boolean) return v.toString();
        return '"' + escapeJson(v.toString()) + '"';
    }

    private void loadCSV(File f) {
        setStatus("A carregar " + f.getName() + "...");
        globalProgress.setVisible(true);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IOException("CSV vazio");
            headers.clear();
            headers.addAll(parseCsvLine(headerLine));

            data.clear();
            String line;
            while ((line = br.readLine()) != null) {
                List<String> vals = parseCsvLine(line);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    String key = headers.get(i);
                    String v = i < vals.size() ? vals.get(i) : null;
                    row.put(key, (v == null || v.isEmpty()) ? null : v);
                }
                data.add(row);
            }
            lvDatasets.getItems().add(f.getName());
            lvDatasets.getSelectionModel().selectLast();

            snapshot();
            refreshColumnMeta();
            refreshColumnChoices();
            computeQuickStats();
            setStatus("Carregado: " + f.getName());
            addHistory("Abrir CSV: " + f.getName());
        } catch (Exception ex) {
            setStatus("Erro: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            globalProgress.setVisible(false);
        }
    }

    // Basic CSV parser: handles quotes and commas
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++; // skip escaped quote
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        out.add(sb.toString());
        return out;
    }

    // ---- Column meta, choices, search ----
    private void refreshColumnMeta() {
        columnMetaList.clear();
        Map<String, String> types = inferColumnTypes();
        for (String h : headers) {
            int nulls = (int) data.stream().filter(r -> r.get(h) == null).count();
            columnMetaList.add(new ColumnMeta(h, types.getOrDefault(h, "string"), nulls));
        }
        taMetadata.setText(buildMetadataText(types));
    }

    private String buildMetadataText(Map<String, String> types) {
        StringBuilder sb = new StringBuilder();
        sb.append("Colunas: ").append(headers.size()).append("\n");
        sb.append("Linhas: ").append(data.size()).append("\n\n");
        for (String h : headers) {
            sb.append(h).append(" : ").append(types.getOrDefault(h, "string"));
            int nnull = (int) data.stream().filter(r -> r.get(h) == null).count();
            sb.append(" | nulls=").append(nnull).append("\n");
        }
        return sb.toString();
    }

    private void refreshColumnChoices() {
        if (cbImputeColumn != null) {
            cbImputeColumn.setItems(headers);
            if (!headers.isEmpty()) cbImputeColumn.getSelectionModel().selectFirst();
        }
        if (cbOutlierColumn != null) {
            cbOutlierColumn.setItems(headers);
            if (!headers.isEmpty()) cbOutlierColumn.getSelectionModel().selectFirst();
        }
        if (cbTargetColumn != null) {
            cbTargetColumn.setItems(headers);
            if (!headers.isEmpty()) cbTargetColumn.getSelectionModel().selectFirst();
        }
        if (cbVizColumnX != null) {
            cbVizColumnX.setItems(headers);
            if (!headers.isEmpty()) cbVizColumnX.getSelectionModel().selectFirst();
        }
        if (cbVizColumnY != null) {
            cbVizColumnY.setItems(headers);
            if (headers.size() > 1) cbVizColumnY.getSelectionModel().select(1);
        }
    }

    private void filterColumns(String query) {
        if (query == null || query.isBlank()) {
            tblColumns.setItems(columnMetaList);
            return;
        }
        String q = query.toLowerCase(Locale.ROOT);
        ObservableList<ColumnMeta> filtered = columnMetaList.stream()
                .filter(cm -> cm.name.toLowerCase(Locale.ROOT).contains(q) || cm.type.toLowerCase(Locale.ROOT).contains(q))
                .collect(Collectors.toCollection(FXCollections::observableArrayList));
        tblColumns.setItems(filtered);
    }

    private Map<String, String> inferColumnTypes() {
        Map<String, String> types = new LinkedHashMap<>();
        for (String h : headers) {
            int numericCount = 0, total = 0;
            for (Map<String, Object> row : data) {
                Object v = row.get(h);
                if (v == null || v.toString().isBlank()) continue;
                total++;
                if (isNumeric(v.toString())) numericCount++;
            }
            String type = (total > 0 && (numericCount * 1.0 / total) >= 0.9) ? "numeric" : "string";
            types.put(h, type);
        }
        return types;
    }

    // ---- Imputation ----
    private void handlePreviewImputation(ActionEvent e) {
        String target = cbImputeColumn.getValue();
        if (target == null) {
            setStatus("Escolha a coluna alvo.");
            return;
        }

        final String[] x_columns = new String[ModelImputationColumns.size()];

        for (int column_index = 0; column_index < ModelImputationColumns.size(); column_index++) {
            String x = ModelImputationColumns.get(column_index).get();
            if (x == null || x.isEmpty()) {
                setStatus("Escolha todas as colunas.");
                return;
            }
            x_columns[column_index] = x;
        }

        String method = comboImputeMethod.getValue();
        if (method == null) method = "Média";

        snapshot(); // allow revert
        lastImputePreview = new HashMap<>();
        switch (method) {
            case "Média" -> previewImputeWithValue(target, computeMean(target));
            case "Mediana" -> previewImputeWithValue(target, computeMedian(target));
            case "Linear Regression (prever com outras colunas)" -> {
                System.out.println(Arrays.toString(linearFit(target, x_columns)));
            }//previewImputeLinear(target);
            case "KNN (k-Nearest)" -> {
                appendLog(taImputeLog, "Pré-visualização KNN não implementada (placeholder). Usando Média.");
                previewImputeKNN(target, x_columns);
               // previewImputeWithValue(target, computeMean(target));
            }
            case "Interpolação" -> {
                appendLog(taImputeLog, "Interpolação simples (forward fill) como placeholder.");
                previewImputeInterpolate(target, "Idade");
            }
            default -> previewImputeWithValue(target, computeMean(target));
        }
        renderImputationDiagnostics(target);
        setStatus("Pré-visualização de imputação concluída para " + target);
        addHistory("Preview imputação: " + method + " -> " + target);
    }

    private void handleRunImputation(ActionEvent e) {
        if (lastImputePreview == null || lastImputePreview.isEmpty()) {
            setStatus("Faça a pré-visualização primeiro.");
            return;
        }
        String target = cbImputeColumn.getValue();
        if (target == null) return;
        for (Map.Entry<Integer, Object> en : lastImputePreview.entrySet()) {
            int idx = en.getKey();
            if (idx >= 0 && idx < data.size()) {
                data.get(idx).put(target, en.getValue());
            }
        }
        refreshColumnMeta();
        setStatus("Imputação aplicada em " + lastImputePreview.size() + " linhas.");
        addHistory("Aplicar imputação: " + target + " (" + lastImputePreview.size() + ")");
    }

    private void previewImputeWithValue(String col, Double value) {
        if (value == null) {
            setStatus("Sem estatística para " + col);
            return;
        }
        lastImputePreview.clear();
        final ArrayList<HashMap<String, String>> data = fetcher.fetchDataMap(String.format("SELECT %s FROM %s", col, table), 1000, 0);
        for (int i = 0; i < data.size(); i++) {
            Map<String, String> row = data.get(i);
            if (row.get(col) == null || row.get(col).isBlank()) {
                lastImputePreview.put(i, value);
            }
        }
        appendLog(taImputeLog, "Preview: preencher " + lastImputePreview.size() + " nulls com " + value);
    }

    private void previewImputeKNN(String y, String... col) {
       /* final ArrayList<HashMap<String, String>> data = fetcher.fetchDataMap(String.format("SELECT %s FROM %s", col, table), 10000, 0);
        final double[][] data_e = new double[10000][2];
        for (int s = 0; s < data.size(); s++) data_e[s][0] = Double.parseDouble(data.get(s).get(col));
        final int[] label = new int[10000];
        for (int s = 0; s < data.size(); s++) {
            label[s] = s;
            data_e[s][1] = s;
        }
        KNN<double[]> knn = KNN.fit(data_e, label, 3); */

        int knn_tree = spinK.getValue();

            ml_pipeline.setModel(Models.KNN);
            ml_pipeline.setKNN(knn_tree);
            ml_pipeline.setSchema(List.of(col), y);
            String command = getCommand(table, y, col, 100000);
            ArrayList<HashMap<String, String>> data = fetcher.fetchRawDataMap(String.format(command, 0));
            ml_pipeline.Train(data);

            while (data.size() == 100000) {
                data = fetcher.fetchRawDataMap(String.format(command, 0));
                ml_pipeline.update(data);
            }

            KNN<double[]> model = (KNN<double[]>) ml_pipeline.getModel();

            taModelSummary.appendText(String.format("KNN: Number of Classes - %d; Classes - %s;", model.numClasses(), Arrays.toString(model.classes())));

    }

    @FXML
    private void AddColumn() {
        HBox node = new HBox(5);
        Button del = new Button("Delete");
        del.setOnAction(_->deleteColumn(node));
        ChoiceBox<String> box = new ChoiceBox<>(headers);
        ModelImputationColumns.add(box.getSelectionModel().selectedItemProperty());
        node.getChildren().addAll(new Label("Column:"), box, del);
        ImputBox.getChildren().add(2, node);
    }

    private void deleteColumn(HBox index) {
        ChoiceBox<String> box = (ChoiceBox<String>) index.getChildrenUnmodifiable().get(1);
        ModelImputationColumns.remove(box.getSelectionModel().selectedItemProperty());
        ImputBox.getChildren().remove(index);
    }

    @FXML
    private void AddColumnTrain() {
        HBox node = new HBox(5);
        Button del = new Button("Delete");
        del.setOnAction(_->deleteColumnTrain(node));
        ChoiceBox<String> box = new ChoiceBox<>(headers);
        ModelTrainImputationColumns.add(box.getSelectionModel().selectedItemProperty());
        node.getChildren().addAll(new Label("Column:"), box, del);
        boxModel.getChildren().add(2, node);
    }

    private void deleteColumnTrain(HBox index) {
        ChoiceBox<String> box = (ChoiceBox<String>) index.getChildrenUnmodifiable().get(1);
        ModelTrainImputationColumns.remove(box.getSelectionModel().selectedItemProperty());
        boxModel.getChildren().remove(index);
    }

    private void previewImputeInterpolate(String col, String colX) {
        lastImputePreview.clear();
        final ArrayList<Double> data = fetcher.fetchDataMap(String.format("""
                SELECT d.%s,\s
                   COALESCE(
                       d.%s,
                       y1 + ((d.%s - x1) * (y2 - y1)) / (x2 - x1)
                   ) AS %s_interpolado
            FROM %s d
            LEFT JOIN (
                SELECT d1.%s AS %s,
                       MAX(d2.%s) AS x1,
                       MAX(d2.%s) AS y1
                FROM %s d1
                JOIN %s d2 ON d2.%s < d1.%s AND d2.%s IS NOT NULL
                GROUP BY d1.%s
            ) prev ON prev.%s = d.%s
            LEFT JOIN (
                SELECT d1.%s AS %s,
                       MIN(d2.%s) AS x2,
                       MIN(d2.%s) AS y2
                FROM %s d1
                JOIN %s d2 ON d2.%s > d1.%s AND d2.%s IS NOT NULL
                GROUP BY d1.%s
            ) next ON next.%s = d.%s
            ORDER BY d.%s;
           \s""",
                // substituições para %s (na ordem!)
                colX, col, colX, col, table, // SELECT principal
                colX, colX, colX, col, table, table, colX, colX, col, colX, colX, colX, // prev
                colX, colX, colX, col, table, table, colX, colX, col, colX, colX, colX, // next
                colX));
        for (int i = 0; i < data.size(); i++) {
            lastImputePreview.put(i, data.get(i));
        }
        // forward-fill then backward-fill as simple interpolation
       /* Double last = null;
        for (int i = 0; i < data.size(); i++) {
            Object v = data.get(i).get(col);
            Double d = toDoubleOrNull(v);
            if (d != null) last = d;
            else if (last != null) lastImputePreview.put(i, last);
        }
        Double next = null;
        for (int i = data.size() - 1; i >= 0; i--) {
            if (!isMissing(data.get(i).get(col))) continue;
            if (lastImputePreview.containsKey(i)) continue;
            // backward fill
            Object v = data.get(i).get(col);
            Double d = toDoubleOrNull(v);
            if (d != null) next = d;
            else if (next != null) lastImputePreview.put(i, next);
        } */
        appendLog(taImputeLog, "Preview: interpolação simples em " + lastImputePreview.size() + " linhas.");
    }

    private void previewImputeLinear(String target) {
        // pick best single numeric predictor by |correlation|
        List<String> numericCols = metadata.stream().filter(h -> !h.Name.equals(target) && isNumericColumn(h.Type)).map(f->f.Name).toList();
        if (numericCols.isEmpty()) {
            appendLog(taImputeLog, "Sem colunas numéricas para prever. Usando média.");
            previewImputeWithValue(target, computeMean(target));
            return;
        }
        String best = null;
        double bestAbsCorr = 0;
        double bestSlope = 0;
        double bestIntercept = 0;
        for (String pred : numericCols) {
       /*     List<double[]> pairs = getNumericPairs(pred, target);
            if (pairs.size() < 2) continue; */
            double corr = pearson(target, pred);
            double[] lr = linearFit(target, pred); // [slope, intercept]
            if (Math.abs(corr) > bestAbsCorr) {
                bestAbsCorr = Math.abs(corr);
                best = pred;
                bestSlope = lr[0];
                bestIntercept = lr[1];
            }
        }
        if (best == null) {
            appendLog(taImputeLog, "Não foi possível ajustar regressão. Usando média.");
            previewImputeWithValue(target, computeMean(target));
            return;
        }

        lastImputePreview.clear();
        for (int i = 0; i < data.size(); i++) {
            Map<String, Object> row = data.get(i);
            if (isMissing(row.get(target))) {
                Double x = toDoubleOrNull(row.get(best));
                if (x != null) {
                    double yhat = bestSlope * x + bestIntercept;
                    lastImputePreview.put(i, yhat);
                }
            }
        }

        appendLog(taImputeLog, String.format(Locale.ROOT, "Regressão: alvo=%s, preditor=%s, |corr|=%.4f, preview=%d linhas.", target, best, bestAbsCorr, lastImputePreview.size()));
        taModelSummary.setText(String.format(Locale.ROOT,
                "Modelo (y=%s ~ x=%s)\nSlope=%e\nIntercept=%e\n|Corr|=%f\nObs usadas=%d",
                target, best, bestSlope, bestIntercept, bestAbsCorr, getNumericPairs(best, target).size()));
    }

    private void renderImputationDiagnostics(String target) {
        // Residuals chart: for observed rows only using best single predictor if last summary present
        chartResiduals.getChildren().clear();
        chartImputeCompare.getChildren().clear();

        // Build compare chart: index vs value original/imputed (preview)
        NumberAxis x = new NumberAxis();
        x.setLabel("Linha");
        NumberAxis y = new NumberAxis();
        y.setLabel(target);
        LineChart<Number, Number> line = new LineChart<>(x, y);
        line.setTitle("Originais x Imputados (preview)");
        XYChart.Series<Number, Number> sOrig = new XYChart.Series<>();
        sOrig.setName("Original");
        XYChart.Series<Number, Number> sImp = new XYChart.Series<>();
        sImp.setName("Imputado");
        final ArrayList<HashMap<String, String>> data = fetcher.fetchDataMap(String.format("SELECT %s FROM %s", target, table), 1000, 0);
        for (int i = 0; i < data.size(); i++) {
            Double d = toDoubleOrNull(data.get(i).get(target));
            if (d != null) sOrig.getData().add(new XYChart.Data<>(i, d));
            if (lastImputePreview.containsKey(i)) {
                Object v = lastImputePreview.get(i);
                Double di = toDoubleOrNull(v);
                if (di != null) sImp.getData().add(new XYChart.Data<>(i, di));
            }
        }
        line.getData().addAll(sOrig, sImp);
        chartImputeCompare.getChildren().add(line);

        // Residuals scatter (on observed rows where we can form prediction with chosen predictor)
        // For simplicity, recompute from preview pairs (if linear) else skip
        // Here we approximate residuals as imaginarily predicted = imputed for missing rows -> residual 0
        ScatterChart<Number, Number> scat = new ScatterChart<>(new NumberAxis(), new NumberAxis());
        scat.setTitle("Resíduos (ilustrativo)");
        XYChart.Series<Number, Number> sRes = new XYChart.Series<>();
        int count = 0;
        Double mean = computeMean(target);
        for (int i = 0; i < data.size(); i++) {
            if (lastImputePreview.containsKey(i)) continue; // only observed
            Double observed = toDoubleOrNull(data.get(i).get(target));
            if (observed == null) continue;
            // naive: compare with local mean as baseline (illustrative)

            if (mean != null) {
                sRes.getData().add(new XYChart.Data<>(mean, observed - mean));
                count++;
            }
        }
        scat.getData().add(sRes);
        chartResiduals.getChildren().add(scat);
    }

    // ---- Outliers ----
    @FXML
    private void handleDetectOutliers(ActionEvent e) {
        String col = cbOutlierColumn.getValue();
        if (col == null) {
            setStatus("Escolha a coluna.");
            return;
        }
        String method = comboOutlierMethod.getValue();
        if (method == null) method = "IQR";
        double thr = parseDoubleOrDefault(tfOutlierThresh.getText(), method.equals("Z-score") ? 3.0 : 1.5);
        double[] idxs;
        List<ColumnOutliers> data = new ArrayList<>();
        List<HashMap<String, String>> data_map = new ArrayList<>();
        List<HashMap<String, String>> min_data = new ArrayList<>();
        List<HashMap<String, String>> max_data = new ArrayList<>();
        long min_len = 0, max_len = 0;
        switch (method) {
            case "Z-score" -> {
                //List<Double> data = detectZScore(col, thr);
                idxs = new double[3];

                idxs[1] = -thr;
                idxs[2] = thr;

                double mean = computeMean(col);
                double stdev = stddev(col);

                min_data = fetcher.fetchRawDataMap(String.format(Locale.US, "SELECT %s AS min_outlier FROM %s WHERE %s IS NOT NULL AND ((%s - %f) / %f) <= %f;", col, table, col, col, mean, stdev, idxs[1]));
                max_data = fetcher.fetchRawDataMap(String.format(Locale.US, "SELECT %s AS max_outlier FROM %s WHERE %s IS NOT NULL AND ((%s - %f) / %f) >= %f;", col, table, col, col, mean, stdev, idxs[2]));
            //    inflateZScore(col, data);

                min_len = min_data == null ? 0 : min_data.size();
                max_len = max_data == null ? 0 : max_data.size();

                if (max_data != null) data_map.addAll(max_data);
                if (min_data != null) data_map.addAll(min_data);

                long len = Math.max(min_len, max_len);

                for (int i = 0; i < len; i++) {
                    String minVal = (i < min_len) ? min_data.get(i).get("min_outlier") : "null";
                    String maxVal = (i < max_len) ? max_data.get(i).get("max_outlier") : "null";

                    data.add(new ColumnOutliers(minVal, maxVal));
                }
            }
            case "Mean" -> {
                idxs = new double[3];

                idxs[1] = -2;
                idxs[2] = 2;

                double mean = computeMean(col);
                double stdev = stddev(col);

                double lower = mean - 2 * stdev;
                double upper = mean + 2 * stdev;

                min_data = fetcher.fetchRawDataMap(String.format(Locale.US, "SELECT %s AS min_outlier FROM %s WHERE %s <= %f;", col, table, col, lower));
                max_data = fetcher.fetchRawDataMap(String.format(Locale.US, "SELECT %s AS max_outlier FROM %s WHERE %s >= %f;", col, table, col, upper));

                min_len = min_data == null ? 0 : min_data.size();
                max_len = max_data == null ? 0 : max_data.size();

                if (max_data != null) data_map.addAll(max_data);
                if (min_data != null) data_map.addAll(min_data);

                long len = Math.max(min_len, max_len);

                double[][] data_graph = new double[(int) len][2];

                for (int i = 0; i < len; i++) {
                    String minVal = (i < min_len) ? min_data.get(i).get("min_outlier") : "null";
                    String maxVal = (i < max_len) ? max_data.get(i).get("max_outlier") : "null";
                    data.add(new ColumnOutliers(minVal, maxVal));

                    data_graph[i][0] = Double.parseDouble(minVal);
                    data_graph[i][1] = Double.parseDouble(maxVal);
                }

                Canvas box = BoxPlot.of(data_graph).canvas();

                try {
                    box.window();
                } catch (InterruptedException | InvocationTargetException _) {
                }

              //  chart.setContent();

            }
            case "IQR" -> {
                idxs = detectIQR(col, thr); // Assumindo 'idxs' é uma variável de instância ou local declarada antes
               // List<HashMap<String, String>> raw_data = fetcher.fetchRawDataMap(String.format(Locale.US, "SELECT %s FROM %s WHERE %s <= %f OR %s >= %f;", col, table, col, idxs[1], col, idxs[2]));
                min_data = fetcher.fetchRawDataMap(String.format(Locale.US, "SELECT %s AS min_outlier FROM %s WHERE %s <= %f;", col, table, col, idxs[1]));
                max_data = fetcher.fetchRawDataMap(String.format(Locale.US, "SELECT %s AS max_outlier FROM %s WHERE %s >= %f;", col, table, col, idxs[2]));

                min_len = min_data == null ? 0 : min_data.size();
                max_len = max_data == null ? 0 : max_data.size();

                if (max_data != null) data_map.addAll(max_data);
                if (min_data != null) data_map.addAll(min_data);

                long len = Math.max(min_len, max_len);

                for (int i = 0; i < len; i++) {
                    String minVal = (i < min_len) ? min_data.get(i).get("min_outlier") : "null";
                    String maxVal = (i < max_len) ? max_data.get(i).get("max_outlier") : "null";

                    data.add(new ColumnOutliers(minVal, maxVal));
                }
              //  inflateIQR(col, idxs);
            }
            default -> {
                setStatus("Método não implementado. Usando IQR.");
                idxs = detectIQR(col, thr); // Assumindo 'idxs' é acessível aqui
            }
        }

        final long min_outliers = min_len;
        final long max_outliers = max_len;


        // populate preview table with only outliers
        //   ObservableList<Map<String, Object>> subset = FXCollections.observableArrayList();

        //   for (int i : idxs) subset.add(data.get(i));
        ;


        renderTableData(col, data);

        setStatus("Outliers detectados: " + min_outliers+max_outliers);
        setStatus("Min Outliers detectados: " + min_outliers);
        setStatus("Max Outliers detectados: " + max_outliers);
        addHistory("Detectar outliers (" + ") em " + col + ": " + (min_outliers+max_outliers));

    }

    private void renderTableData(String col, List<ColumnOutliers> data) {
        tblOutliersPreview.getItems().clear();
        tblOutliersPreview.getColumns().clear();
        TableColumn<ColumnOutliers, Void> MainColumn = new TableColumn<>(col);

        TableColumn<ColumnOutliers, Double> min = new TableColumn<>("Min Outliers"), max = new TableColumn<>("Max Outliers");
        min.setCellValueFactory(new PropertyValueFactory<>("min"));
        max.setCellValueFactory(new PropertyValueFactory<>("max"));

        MainColumn.getColumns().addAll(min, max);

        tblOutliersPreview.getColumns().add(MainColumn);

        tblOutliersPreview.getItems().addAll(data);

    }

    @FXML
    private void handleRemoveOutliers(ActionEvent e) {
        String col = cbOutlierColumn.getValue();
        if (col == null) return;
        List<Integer> idxs = new ArrayList<>();//detectIQR(col, 1.5); // default
        snapshot();
        // remove in reverse order
        List<Integer> sorted = new ArrayList<>(idxs);
        sorted.sort(Comparator.reverseOrder());
        for (int i : sorted) data.remove(i);
        refreshColumnMeta();
        setStatus("Removidos " + idxs.size() + " outliers.");
        addHistory("Remover outliers em " + col + ": " + idxs.size());
    }

    private XYChart.Series<String, Number> calculateDistributionDataGraph(List<Double> data, int bins) {
        double min = Collections.min(data), max = Collections.max(data);
        bins = bins == 1 ? Math.max(5, (int) Math.sqrt(data.size())) : bins;
        double width = (max - min) / bins;
        int[] hist = new int[bins];
        for (double v : data) {
            int bi = (int) Math.min(bins - 1, Math.floor((v - min) / Math.max(width, 1e-9)));
            hist[bi]++;
        }
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (int i = 0; i < bins; i++) {
            double start = min + i * width;
            double end = start + width;
            s.getData().add(new XYChart.Data<>(start + "-" + end, hist[i]));
        }
        return s;
    }

    private void renderDistribution(XYChart.Series<String, Number> s) {
     //   chartDistribution.getChildren().clear();

      /*  List<Double> values = new ArrayList<>();
        for (Map<String, String> row : data) {
            Double d = toDoubleOrNull(row.get(col));
                values.add(d);
                min = (min == null ? d : Math.min(min, d));
                max = (max == null ? d : Math.max(max, d));
        } */
    //    if (data.isEmpty()) return;

        chartDistribution.setTitle("Distribuição (histograma aproximado)");

        chartDistribution.setPrefWidth(s.getData().size() * 100);
        chartDistribution.getData().add(s);
     //   graphstack.setContent(chartDistribution);//.getChildren().add(lc);
    }

    // ---- Metrics ----
    private void computeQuickStats() {
       // Map<String, String> types = inferColumnTypes();
        tableStat.getItems().clear();
        Thread.ofVirtual().start(()->{
            for (String h : headers) {
                //  if (!"numeric".equals(types.get(h))) continue;
         /*   List<Double> vals = numericColumnValues(h);
            if (vals.isEmpty()) continue;
            Collections.sort(vals); */
                //double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
                double mean = computeMean(h);
                double med = computeMedian(h);
                double variance = computeVariance(h, mean);
                double min = computeMin(h);
                double max = computeMax(h);
                double std = stddev(h);
                tableStat.getItems().add(new ColumnStatistic(h, mean, med, std, min, max, variance));
            }
            //taNumericSummary.setText(sb.toString());
            Platform.runLater(this::renderCorrelationHeatmap);
        });

    }

    private void handleExportCorrelationMatrix(ActionEvent e) {
        double[][] corr = correlationMatrix();
        if (corr == null) {
            setStatus("Sem colunas numéricas.");
            return;
        }
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File f = fc.showSaveDialog(getWindow(e));
        if (f == null) return;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8))) {
            List<String> numCols = numericHeaders();
            pw.println(String.join(",", numCols));
            for (int i = 0; i < corr.length; i++) {
                List<String> row = new ArrayList<>();
                for (int j = 0; j < corr[i].length; j++) row.add(String.format(Locale.ROOT, "%.6f", corr[i][j]));
                pw.println(String.join(",", row));
            }
            setStatus("Matriz de correlação exportada: " + f.getName());
        } catch (Exception ex) {
            setStatus("Erro: " + ex.getMessage());
        }
    }

    private void renderCorrelationHeatmap() {
        chartCorrelation.getChildren().clear();
        double[][] corr = correlationMatrix();
        if (corr == null) return;
        List<String> cols = numericHeaders();
        // Render as scatter cloud of matrix cells: x=j, y=i, value->size
        ScatterChart<Number, Number> sc = new ScatterChart<>(new NumberAxis(), new NumberAxis());
        sc.setTitle("Correlação (|r| como tamanho)");
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        for (int i = 0; i < corr.length; i++) {
            for (int j = 0; j < corr[i].length; j++) {
                double v = Math.abs(corr[i][j]);
                XYChart.Data<Number, Number> d = new XYChart.Data<>(j, i);
                // custom node with size proportional to |r|
                d.setNode(new Label(String.format(Locale.ROOT, "%.2f", v)));
                s.getData().add(d);
            }
        }
        sc.getData().add(s);
        chartCorrelation.getChildren().add(sc);
    }

    private void renderScatterMatrix() {
        chartScatterMatrix.getChildren().clear();
        // Placeholder: show scatter of first two numeric columns
        List<String> num = numericHeaders();
        if (num.size() < 2) {
            setStatus("Precisa de pelo menos 2 colunas numéricas.");
            return;
        }
        String xcol = num.get(0), ycol = num.get(1);
        ScatterChart<Number, Number> sc = new ScatterChart<>(new NumberAxis(), new NumberAxis());
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        for (Map<String, Object> row : data) {
            Double x = toDoubleOrNull(row.get(xcol));
            Double y = toDoubleOrNull(row.get(ycol));
            if (x != null && y != null) s.getData().add(new XYChart.Data<>(x, y));
        }
        sc.getData().add(s);
        chartScatterMatrix.getChildren().add(sc);
    }

    @FXML
    private void calculateDistribution() {

        final String target = boxTarget.getValue();

        if (target.isEmpty()) {
            boxTarget.requestFocus();
            ShowInformation("No item", "You need to select target to proside.");
            return;
        }

        Task<DistributionMetric> calculateTask = new Task<DistributionMetric>() {
            @Override
            protected DistributionMetric call() throws Exception {

                double kurtosis = computeKurtosis(target);
                double fisher = computeCoefficientFisher(target);
                double pearson = computeCoefficientPearson(target);
                double pearsonMedian = computeCoefficientPearsonMedian(target);
                double kurtosisExcess = computeExcessKurtosis(target);

                return new DistributionMetric(kurtosis, fisher, pearson, pearsonMedian, kurtosisExcess);
            }

            @Override
            protected void failed() {
                super.failed();
                ShowError("Error", "Error to calculate distribution.", this.getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                DistributionMetric metric = this.resultNow();
                textDistribution.appendText(String.format(Locale.US, "Distribution of %s: Kurtosis = %e; fisher = %e; pearson = %e; pearsonMedian = %e; kurtosisExcess = %e.\n\n", target, metric.kurtosis, metric.fisher, metric.pearson, metric.pearsonMedian, metric.kurtosisExcess));
            }
        };

        Task<XYChart.Series<String, Number>> graph = new Task<XYChart.Series<String, Number>>() {

            @Override
            protected void running() {
                super.running();
                cleanGraph();
            }

            @Override
            protected XYChart.Series<String, Number> call() throws Exception {

             //   int buffer = 100000;
               // int offset = 0;

           //     String command = getCommand(table, target, new String[]{}, buffer);

/*                ArrayList<HashMap<String, String>> data_distinct = fetcher.fetchRawDataMap(String.format("SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL;", target, table, target));

                HashMap<String, Double> map = new HashMap<>();

                for (HashMap<String, String> col : data_distinct) {
                        map.put(col.get(target), fetcher.fetchDataMap(String.format("SELECT COUNT(%s) FROM %s WHERE %s = %s;", target, table, target, col.get(target))).getFirst());
                } */

                XYChart.Series<String, Number> serie = new XYChart.Series<>();

                chartDistribution.getYAxis().setLabel("Frequency");

                if (boxFrequency.getSelectionModel().getSelectedIndex() == 0) {

                    chartDistribution.getXAxis().setLabel("Values");

                    ArrayList<HashMap<String, String>> data_counts = fetcher.fetchRawDataMap(
                            String.format("SELECT %s, COUNT(*) as cnt FROM %s WHERE %s IS NOT NULL GROUP BY %s;",
                                    target, table, target, target));

                    // List<Double> data = fetcher.fetchRawDataMap(String.format("SELECT %s FROM %s WHERE %s IS NOT NULL;", target, table, target)).parallelStream().map(c->c.get(target)).map(Double::parseDouble)                // converte para Double
                    //        .collect(Collectors.toCollection(ArrayList::new));

                    for (HashMap<String, String> row : data_counts) {
                        String key = row.remove(target);
                        double count = Double.parseDouble(row.remove("cnt"));
                        serie.getData().add(new XYChart.Data<>(key, count));
                    }
                } else {

                    chartDistribution.getXAxis().setLabel("Intervale");
                    List<Double> data = fetcher.fetchRawDataMap(String.format("SELECT %s FROM %s WHERE %s IS NOT NULL;", target, table, target)).parallelStream().map(c->c.get(target)).map(Double::parseDouble)                // converte para Double
                            .collect(Collectors.toCollection(ArrayList::new));
                    int bins = binSpinner.getValue();
                    serie = calculateDistributionDataGraph(data, bins);
                }

              /*  XYChart.Series<String, Number> serie = new XYChart.Series<>(); //calculateDistributionDataGraph(data);

                do {
                    List<Double> data = fetcher.fetchRawDataMap(String.format(String.format(command, offset), target, table, target)).stream().map(c->c.get(target)).map(Double::parseDouble)                // converte para Double
                            .collect(Collectors.toCollection(ArrayList::new));

                    serie.getData().addAll(calculateDistributionDataGraph(data).getData());
                    offset += buffer;
                } while (data.size() == buffer); */

                //return calculateDistributionDataGraph(data);
                return serie;
            }

            @Override
            protected void failed() {
                super.failed();
                ShowError("Error", "Error to generate graph.", this.getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                renderDistribution(this.getValue());
            }
        };

        Thread.ofVirtual().start(calculateTask);

        new Thread(graph).start();

    //    renderHistogram(target);
    }

    private static class DistributionMetric {
        public double kurtosis, fisher, pearson, pearsonMedian, kurtosisExcess;

        public DistributionMetric(double kurtosis, double fisher, double pearson, double pearsonMedian, double kurtosisExcess) {
            this.kurtosis = kurtosis;
            this.fisher = fisher;
            this.pearson = pearson;
            this.pearsonMedian = pearsonMedian;
            this.kurtosisExcess = kurtosisExcess;
        }

    }

    // ---- Visualization ----
    private void handleRenderViz(ActionEvent e) {
        chartViz.getChildren().clear();
        String type = cbVizType.getValue();
        String xcol = cbVizColumnX.getValue();
        String ycol = cbVizColumnY.getValue();
        if (type == null || xcol == null) {
            setStatus("Escolha o tipo e a coluna X.");
            return;
        }
        switch (type) {
            case "Histograma" -> renderHistogram(xcol);
            case "Boxplot" -> setStatus("Boxplot: implementar conforme necessidade.");
            case "Scatter" -> renderScatter(xcol, ycol);
            case "Heatmap (Correlação)" -> renderCorrelationHeatmap();
            default -> setStatus("Tipo desconhecido.");
        }
    }

    private void renderHistogram(String col) {
        Double min = null, max = null;
        List<Double> values = numericColumnValues(col);
        if (values.isEmpty()) {
            setStatus("Coluna não numérica ou vazia.");
      //      return;
        }
        for (double v : values) {
            min = min == null ? v : Math.min(min, v);
            max = max == null ? v : Math.max(max, v);
        }
        int bins = Math.max(5, (int) Math.sqrt(values.size()));
        double width = (max - min) / bins;
        int[] hist = new int[bins];
        for (double v : values) {
            int bi = (int) Math.min(bins - 1, Math.floor((v - min) / Math.max(width, 1e-9)));
            hist[bi]++;
        }
        NumberAxis x = new NumberAxis(min, max, width);
        NumberAxis y = new NumberAxis();
       // BarChart<Number, Number> lc = new BarChart<>(x, y);
        chartDistribution.setTitle("Histograma: " + col);
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        for (int i = 0; i < bins; i++) {
            double center = min + (i + 0.5) * width;
            s.getData().add(new XYChart.Data<>(String.valueOf(center), hist[i]));
        }
        chartDistribution.getData().add(s);
       // chartDistribution.getChildren().add(lc);
    }

    private void renderScatter(String xcol, String ycol) {
        if (ycol == null) {
            setStatus("Escolha a coluna Y para scatter.");
            return;
        }
        ScatterChart<Number, Number> sc = new ScatterChart<>(new NumberAxis(), new NumberAxis());
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        for (Map<String, Object> row : data) {
            Double x = toDoubleOrNull(row.get(xcol));
            Double y = toDoubleOrNull(row.get(ycol));
            if (x != null && y != null) s.getData().add(new XYChart.Data<>(x, y));
        }
        sc.getData().add(s);
        chartViz.getChildren().add(sc);
    }

    // ---- Utilities & math ----
    private boolean isNumericColumn(String h) {
        return isNumericType(h);
    }

    public static boolean isNumericType(String sqlType) {
        return switch (sqlType) {
            case "INTEGER", "TINYINT", "SMALLINT", "BIGINT", "NUMERIC",
                 "DOUBLE", "FLOAT", "INT", "SERIAL", "BIGSERIAL",
                 "DECIMAL", "DOUBLE PRECISION", "REAL" -> true;
            default -> false;
        };
    }

    private static boolean isNumeric(String s) {
        try {
            Double.parseDouble(s.replace(',', '.'));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Double toDoubleOrNull(Object v) {
        if (v == null) return null;
        String s = v.toString().trim().replace(',', '.');
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    private Double computeMean(String col) {

        return fetcher.fetchDataMap(String.format("SELECT AVG(%s) FROM %s;", col, table)).getFirst();

       /* List<Double> vals = numericColumnValues(col);
        if (vals.isEmpty()) return null;
        double[] values = new double[vals.size()];
        for (int i = 0; i < vals.size(); i++) values[i] = vals.get(i);

        return MathEx.mean(values); */
    }

    private Double computeMedian(String col) {
        return fetcher.fetchDataMap(String.format("""
                WITH OrderedData AS (
                                                                               SELECT %s, ROW_NUMBER() OVER (ORDER BY %s) as rn, COUNT(*) OVER () as total_count
                                                                               FROM %s
                                                                               WHERE %s IS NOT NULL -- Ignorar NULLs para mediana
                                                                             )
                                                                             SELECT %s
                                                                             FROM OrderedData
                                                                             WHERE rn = (total_count + 1) / 2;""", col, col, table, col, col)).getFirst();
      /*  List<Double> vals = numericColumnValues(col);
        if (vals.isEmpty()) return null;
        return median(vals); */
    }

    private Double computeMin(String col) {
        return fetcher.fetchDataMap(String.format("SELECT MIN(%s) FROM %s", col, table)).getFirst();
    }

    private Double computeMax(String col) {
        return fetcher.fetchDataMap(String.format("SELECT MAX(%s) FROM %s", col, table)).getFirst();
    }

    private List<Double> numericColumnValues(String col) {
        List<Double> out = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Double d = toDoubleOrNull(row.get(col));
            if (d != null) out.add(d);
        }
        return out;
    }

    private static double median(List<Double> sortedOrNot) {
        List<Double> vals = new ArrayList<>(sortedOrNot);
        Collections.sort(vals);
        int n = vals.size();
        if (n == 0) return Double.NaN;
        if (n % 2 == 1) return vals.get(n / 2);
        return (vals.get(n / 2 - 1) + vals.get(n / 2)) / 2.0;
    }

    private double computeVariance(String col, final double mean) {
        return fetcher.fetchDataMap(String.format(Locale.US, """
                WITH aggregates AS (
                  SELECT
                    SUM((CAST(%s AS REAL) - %f) * (CAST(%s AS REAL) - %f)) AS sum_sq,
                    1.0/(COUNT(%s)-1.0) AS cnt
                  FROM %s
                  WHERE %s IS NOT NULL
                )
                SELECT
                    cnt*sum_sq AS variance
                FROM aggregates;""", col, mean, col, mean, col, table, col)).getFirst();
    }

    private double stddev(String col) {
   /*     if (vals.isEmpty()) return Double.NaN;
        double ss = 0;
        for (double v : vals) ss += (v - mean) * (v - mean);
        return Math.sqrt(ss / vals.size()); */
        return fetcher.fetchDataMap(String.format("""
                WITH aggregates AS (
                  SELECT
                    SUM(CAST(%s AS REAL) * CAST(%s AS REAL)) AS sum_sq,
                    SUM(CAST(%s AS REAL)) AS sum_val,
                    COUNT(%s) AS cnt
                  FROM %s
                  WHERE %s IS NOT NULL
                )
                SELECT
                CASE
                    WHEN cnt > 1 THEN
                SQRT(
                         (sum_sq - (sum_val * sum_val) / cnt) /
                         (cnt - 1)
                       )
                       ELSE
                             NULL
                       END AS sample_stddev
                FROM aggregates;""", col, col, col, col, table, col)).getFirst();
    }

    private List<double[]> getNumericPairs(String xcol, String ycol) {
        List<double[]> out = new ArrayList<>();
        for (Map<String, Object> row : data) {
            Double x = toDoubleOrNull(row.get(xcol));
            Double y = toDoubleOrNull(row.get(ycol));
            if (x != null && y != null) out.add(new double[]{x, y});
        }
        return out;
    }

    private double pearson(String x, String y) {
       /* int n = pairs.size();
        if (n == 0) return Double.NaN;
        double sx = 0, sy = 0, sxx = 0, syy = 0, sxy = 0;
        for (double[] p : pairs) {
            double x = p[0], y = p[1];
            sx += x;
            sy += y;
            sxx += x * x;
            syy += y * y;
            sxy += x * y;
        }
        double num = n * sxy - sx * sy;
        double den = Math.sqrt((n * sxx - sx * sx) * (n * syy - sy * sy));
        if (den == 0) return 0;
        return num / den; */

        return fetcher.fetchDataMap(String.format("""
                WITH Pearson_constants AS (
                SELECT SUM(CAST(%s AS REAL) * CAST(%s AS REAL)) AS sigma_of_x_mul_y,
                SUM(CAST(%s AS REAL)) AS sigma_of_x,
                SUM(CAST(%s AS REAL)) AS sigma_of_y,
                SUM(CAST(%s AS REAL) * CAST(%s AS REAL)) AS sigma_of_x_pow,
                SUM(CAST(%s AS REAL) * CAST(%s AS REAL)) AS sigma_of_y_pow
                FROM %s
                WHERE %s IS NOT NULL AND %s IS NOT NULL) -- Ignorar NULLs para mediana)
                SELECT (sigma_of_x_mul_y - sigma_of_x * sigma_of_y) / (SQRT((sigma_of_x_pow - (sigma_of_x * sigma_of_x)) * ((sigma_of_y_pow - (sigma_of_y * sigma_of_y))))) AS pearson
                FROM Pearson_constants;""", x, y, x, y, x, x, y, y, table, x, y)).getFirst();
    }

    private double computeKurtosis(String col) {
        double mean = computeMean(col);
        return fetcher.fetchDataMap(String.format(Locale.US, """
                WITH Kurtosis_constants AS (
                SELECT COUNT(%s) AS cnt,
                SUM(POW((CAST(%s AS REAL) - %f), 4)/POW(%f, 4)) AS sigma_of_kurtosis
                FROM %s
                WHERE %s IS NOT NULL) -- Ignorar NULLs para mediana)
                SELECT (cnt*(cnt+1))/((cnt-1)*(cnt-2)*(cnt-3))*sigma_of_kurtosis AS kurtosis
                FROM Kurtosis_constants;""", col, col, mean, computeVariance(col, mean), table, col)).getFirst();
    }

    private double computeExcessKurtosis(String col) {
        return computeKurtosis(col)-3;
    }

    private Object Mode(String col) {
        return fetcher.fetchRawDataMap(String.format(Locale.US, """
                    SELECT %s, COUNT(%s) AS mode
                    FROM %s
                    GROUP BY %s
                    ORDER BY mode DESC
                    LIMIT 1;
                """, col, col, table, col)).getFirst().get("mode");
    }

    private double computeCoefficientFisher(String col) {
        double mean = computeMean(col);
        return fetcher.fetchDataMap(String.format(Locale.US, """
                    WITH Fisher_constants AS (
                    SELECT COUNT(%s) AS cnt,
                    SUM(POW((CAST(%s AS REAL) - %f ), 3)) AS sigma_of_x,
                    SUM(POW(%f, 3)) AS sigma_of_variance
                    FROM %s
                    WHERE %s IS NOT NULL)
                    SELECT cnt/((cnt-1)*(cnt-2))*(sigma_of_x/sigma_of_variance) AS fisher
                    FROM Fisher_constants;""", col, col, mean, computeVariance(col, mean), table, col)).getFirst();
    }

    private double computeCoefficientPearson(String col) {
        double mean = computeMean(col);
        return fetcher.fetchDataMap(String.format(Locale.US, """
                    SELECT (%f-%f)/%f AS pearson_coefficient FROM %s;
                """, mean, Double.parseDouble(Mode(col).toString()), computeVariance(col, mean), table)).getFirst();
    }

    private double computeCoefficientPearsonMedian(String col) {
        double mean = computeMean(col);
        return fetcher.fetchDataMap(String.format(Locale.US, """
                    SELECT (%f-%f)/%f AS pearson_coefficient FROM %s;
                """, mean, computeMedian(col), computeVariance(col, mean), table)).getFirst();
    }

    @NotNull
    private static String getCommand(String table, String y, String[] cols, int buffer) {
        StringBuilder commandBuilder = new StringBuilder("SELECT ");

        StringBuilder complement = new StringBuilder(y + ", "), complementNotNull = new StringBuilder(y + " IS NOT NULL AND ");

        for (String col : cols) {
            complement.append(String.format("%s, ", col));
            complementNotNull.append(String.format("%s IS NOT NULL AND ", col));
           // commandBuilder.append(String.format("%s, ", col));
        }

        complement.delete(complement.length() - 2, complement.length());
        complementNotNull.delete(complementNotNull.length() - 5, complementNotNull.length());

        commandBuilder.append("%s FROM %s WHERE %s LIMIT %d OFFSET ");

        return String.format(commandBuilder.toString(), complement, table, complementNotNull, buffer) + "%d;";
    }

    private double[] linearFit(String y, String... x) {
     /*   int n = pairs.size();
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (double[] p : pairs) {
            double x = p[0], y = p[1];
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }
        double denom = (n * sxx - sx * sx);
        double slope = denom == 0 ? 0 : (n * sxy - sx * sy) / denom;
        double intercept = (sy - slope * sx) / n;
        return new double[]{slope, intercept}; */
        double[] linear_information = new double[x.length+1];

        for (int index = 0; index < x.length; index++) {
            String x_ = x[index];
            java.util.List<Double> information = fetcher.fetchDataMap(String.format("""
                WITH Linear_constants AS (
                SELECT CAST(COUNT(*) AS REAL) AS items,
                SUM(CAST(%s AS REAL) * CAST(%s AS REAL)) AS sigma_of_x_mul_y,
                SUM(CAST(%s AS REAL)) AS sigma_of_x,
                SUM(CAST(%s AS REAL)) AS sigma_of_y,
                SUM(CAST(%s AS REAL) * CAST(%s AS REAL)) AS sigma_of_x_pow
                FROM %s
                WHERE %s IS NOT NULL AND %s IS NOT NULL) -- Ignorar NULLs para mediana)
                SELECT (sigma_of_x_mul_y - 1.0/items * sigma_of_x * sigma_of_y) / (sigma_of_x_pow - 1.0/items * (sigma_of_x * sigma_of_x)) AS coefficient,
                (1.0/items * sigma_of_y) -\s
                   ( (sigma_of_x_mul_y - 1.0/items * sigma_of_x * sigma_of_y) /\s
                     (sigma_of_x_pow - 1.0/items * (sigma_of_x * sigma_of_x))\s
                   ) * (1.0/items * sigma_of_x)
                FROM Linear_constants;""", x_, y, x_, y, x_, x_, table, x_, y));
            linear_information[index] = information.getFirst();
            linear_information[index+1] = information.getLast();
        }

        return linear_information;

    }

    private double[][] correlationMatrix() {
        List<String> cols = numericHeaders();
        int n = cols.size();
        if (n < 2) return null;
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                List<double[]> pairs = getNumericPairs(cols.get(i), cols.get(j));
          //      m[i][j] = pairs.isEmpty() ? Double.NaN : pearson(pairs);
            }
        }
        return m;

    }

    private List<String> numericHeaders() {
        return headers.stream().filter(this::isNumericColumn).collect(Collectors.toList());
    }

    // ---- Outlier detectors ----
    private double[] detectIQR(String col, double k) {
        List<Double> vals = numericColumnValues(col);
        double[] IQR_stat = new double[3];
       // if (vals.isEmpty()) return List.of();
        Collections.sort(vals);
        double q1 = quantile(col, 0.25);
        double q3 = quantile(col, 0.75);
        double iqr = q3 - q1;
        double low = q1 - k * iqr;
        double high = q3 + k * iqr;
        List<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Double d = toDoubleOrNull(data.get(i).get(col));
            if (d != null && (d < low || d > high)) idxs.add(i);
        }
        IQR_stat[0] = iqr;
        IQR_stat[1] = low;
        IQR_stat[2] = high;
        return IQR_stat;
    }

    private List<Double> detectZScore(String col, double thr) {
     /*   List<Double> vals = numericColumnValues(col);
        if (vals.isEmpty()) return List.of();
        double mean = vals.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double sd = stddev(col);
        if (sd == 0) return List.of();
        List<Integer> idxs = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            Double d = toDoubleOrNull(data.get(i).get(col));
            if (d != null) {
                double z = (d - mean) / sd;
                if (Math.abs(z) > thr) idxs.add(i);
            }
        }
        return idxs; */
        return fetcher.fetchDataMap(String.format(Locale.US, "SELECT %s FROM %s WHERE (%s - %f) / %f;", col, table, col, computeMean(col), stddev(col)));
    }

    private double quantile(String col, double percent) {
        return fetcher.fetchDataMap(String.format(Locale.US, """
        SELECT %s
        FROM %s
        ORDER BY %s
        LIMIT 1 OFFSET CAST((SELECT COUNT(*) * %f FROM %s) AS INTEGER);""", col, table, col, percent, table)).getFirst();
     /*   if (sortedVals.isEmpty()) return Double.NaN;
        double pos = q * (sortedVals.size() - 1);
        int i = (int) Math.floor(pos);
        int j = (int) Math.ceil(pos);
        if (i == j) return sortedVals.get(i);
        double frac = pos - i;
        return sortedVals.get(i) * (1 - frac) + sortedVals.get(j) * frac; */
    }

    private long number_of_min_outlier(final String col, final double outlier) {
        return fetcher.fetchDataMap(String.format(Locale.US, """
        SELECT COUNT(%s)
        FROM %s
        WHERE %s <= %f;""", col, table, col, outlier)).getFirst().longValue();
    }

    private long number_of_max_outlier(final String col, final double outlier) {
        return fetcher.fetchDataMap(String.format(Locale.US, """
        SELECT COUNT(%s)
        FROM %s
        WHERE %s >= %f;""", col, table, col, outlier)).getFirst().longValue();
    }

    // ---- Misc helpers ----
    private boolean isMissing(Object v) {
        return v == null || v.toString().isBlank();
    }

    private void snapshot() {
        lastSnapshot.clear();
        for (Map<String, Object> row : data) lastSnapshot.add(new LinkedHashMap<>(row));
    }

    private void revertToLastSnapshot() {
        if (lastSnapshot.isEmpty()) {
            setStatus("Sem snapshot para reverter.");
            return;
        }
        data.clear();
        for (Map<String, Object> row : lastSnapshot) data.add(new LinkedHashMap<>(row));
        refreshColumnMeta();
        setStatus("Revertido para o último snapshot.");
        addHistory("Reverter alterações");
    }

    private void addHistory(String s) {
        lvHistory.getItems().add(s);
        lvHistory.scrollTo(lvHistory.getItems().size() - 1);
    }

    private void appendLog(TextArea ta, String msg) {
        if (ta == null) return;
        ta.appendText(msg + "\n");
    }

    private void setStatus(String s) {
        if (lblStatus != null) lblStatus.setText(s);
    }

    private Window getWindow(ActionEvent e) {
        if (e != null && e.getSource() instanceof Node n) return n.getScene().getWindow();
        return null;
    }

    private double parseDoubleOrDefault(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Double.parseDouble(s.replace(',', '.'));
        } catch (Exception e) {
            return def;
        }
    }

    @FXML
    private void exportModel() {

        if (model != null) {
            try {
                final String path_model = path.selectPath((Stage) MainContainer.getScene().getWindow());

                ml_pipeline.exportModel(path_model);
            } catch (Exception e) {
                ShowError("Error", "Error to export model.", e.getMessage());
            }
        } else ShowInformation("No model", "You need to Train a model to export.");
    }

    @FXML
    private void PredictValue() {

        if (model != null) {

            DataFrame data = new DataFrame(new DoubleVector("dfgb54yer$RE&$SDTGRGs", new double[]{0})); // DataFrame not accept empty structure, so the solution are create a temporary column with a name not used.

            StringBuilder builder = new StringBuilder();

            for (int indexChild = 1, index = 0; indexChild < TestBox.getChildren().size(); indexChild += 2, index++) {
                TextField text = (TextField) TestBox.getChildren().get(indexChild);
                try {
                    data.add(new DoubleVector(text.getId(), new double[]{Double.parseDouble(text.getText())}));
                    builder.append(text.getId()).append(": ").append(text.getText()).append("; ");
                } catch (Exception e) {
                    text.requestFocus();
                    ShowError("Error parsing", "You need to insert a numeric value for the predict.");
                }

            }

            data = data.drop("dfgb54yer$RE&$SDTGRGs");

            String pred = "";
            String model_name = "";

            if (this.model instanceof LinearModel model) {
                // LinearModel model = (LinearModel) this.model;
                model_name = "Linear";
                pred = String.valueOf(model.predict(data)[0]);
            } else if (this.model instanceof RandomForest model) {
                //     RandomForest model = (RandomForest) this.model;
                model_name = "Random Forest";
                pred = String.valueOf(model.predict(data)[0]);
            } else if (this.model instanceof LogisticRegression model) {
                model_name = "Logistic Regression";

                // pred = String.valueOf(model.predict(data)[0]);
            } else if (this.model instanceof smile.classification.RandomForest model) {
                model_name = "Random Forest Classification";
                pred = String.valueOf(model.predict(data)[0]);
            }

            textPredictHistory.appendText(String.format("(%s): Predict of %s: is %s\n\n", model_name, builder, pred));
        } else ShowInformation("No model", "You need to train a model to use predict.");

    }

    @FXML
    private void cleanGraph() {
        chartDistribution.getData().clear();
        chartDistribution.setTitle("");
        chartDistribution.setPrefWidth(Region.USE_COMPUTED_SIZE);
        chartDistribution.getXAxis().setLabel("");
        chartDistribution.getYAxis().setLabel("");
    }

    // ---- TableCell for editing Map-based TableView ----
    private class EditingTableCell extends TableCell<Map<String, Object>, Object> {
        private TextField editor;

        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
                return;
            }
            setText(item == null ? "" : item.toString());
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (editor == null) editor = new TextField();
            editor.setText(getItem() == null ? "" : getItem().toString());
            editor.setOnAction(e -> commitEdit(editor.getText()));
            editor.focusedProperty().addListener((obs, was, is) -> {
                if (!is) commitEdit(editor.getText());
            });
            setText(null);
            setGraphic(editor);
            editor.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setGraphic(null);
            setText(getItem() == null ? "" : getItem().toString());
        }

        @Override
        public void commitEdit(Object newValue) {
            super.commitEdit(newValue);
            int rowIndex = getIndex();
            int colIndex = getTableView().getColumns().indexOf(getTableColumn());
            if (rowIndex >= 0 && rowIndex < data.size()) {
                String header = headers.get(colIndex);
                data.get(rowIndex).put(header, (newValue == null || newValue.toString().isBlank()) ? null : newValue);
                refreshColumnMeta();
            }
            setGraphic(null);
            setText(newValue == null ? "" : newValue.toString());
        }
    }

    // ---- Data holders ----
    public static class ColumnMeta {
        public final String name;
        public final String type;
        public final SimpleIntegerProperty nulls = new SimpleIntegerProperty();

        public ColumnMeta(String name, String type, int nulls) {
            this.name = name;
            this.type = type;
            this.nulls.set(nulls);
        }
    }

    public static class ColumnOutliers {
        private final String min;
        private final String max;

        public ColumnOutliers(String min, String max) {
            this.min = min;
            this.max = max;
        }

        public String getMin() { return min; }
        public String getMax() { return max; }

    }

    public static class ColumnStatistic {
        public final String name;
        public double mean, median, std, min, max, variance, squareVariance;

        public ColumnStatistic(String name, double mean, double median, double std, double min, double max, double variance) {
            this.name = name;
            this.mean = mean;
            this.median = median;
            this.std = std;
            this.min = min;
            this.max = max;
            this.variance = variance;
            this.squareVariance = Math.sqrt(variance);
        }

    }

}
