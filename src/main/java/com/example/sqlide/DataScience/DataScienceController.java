package com.example.sqlide.DataScience;

import com.example.sqlide.DataScience.Model.ForecastService;
import com.example.sqlide.DataScience.Model.ModelPipeline;
import com.example.sqlide.DataScience.Model.Models;
import com.example.sqlide.DataScience.Model.Preprocessor;
import com.example.sqlide.DataScience.Model.SklearnExchange;
import com.example.sqlide.DataScience.Model.TrainingResult;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Task.TaskInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseExecutorInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseFetcherInterface;
import com.example.sqlide.drivers.model.Interfaces.DatabaseUpdaterInterface;
import com.example.sqlide.drivers.model.SQLTypes;
import com.example.sqlide.misc.memoryInterface;
import com.example.sqlide.misc.path;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.sqlide.popupWindow.handleWindow.ShowConfirmation;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Controlador do painel de Data Science.
 *
 * <p>A versão anterior tinha duas fontes de dados a coexistir: uma lista {@code data} em
 * memória, alimentada só por um importador de CSV que nunca chegou a ter botão, e um
 * caminho por SQL. Como a lista ficava sempre vazia, tudo o que dependia dela — aplicar
 * imputação, remover outliers, exportar, dispersão, correlação, desfazer — corria sem
 * erro nenhum e sem fazer absolutamente nada.</p>
 *
 * <p>Agora há uma fonte só: a tabela aberta. Os agregados são calculados no servidor pelo
 * {@link StatisticsService} e as operações que precisam de linhas trazem uma amostra
 * limitada. O que escreve na base de dados escreve mesmo, e diz sempre quantas linhas
 * mexeu.</p>
 */
public class DataScienceController {

    // ==== Cabeçalho ====
    @FXML
    private BorderPane MainContainer;
    @FXML
    private Label lblTable, lblStatus, lblFooter, lblMemory;
    @FXML
    private ProgressBar globalProgress;

    // ==== Colunas ====
    @FXML
    private TextField searchField;
    @FXML
    private TableView<ColumnRow> tblColumns;
    @FXML
    private TableColumn<ColumnRow, String> colName, colType;
    @FXML
    private TableColumn<ColumnRow, Number> colNulls;
    @FXML
    private TableColumn<ColumnRow, String> colNullPct;
    @FXML
    private TextArea taColumnProfile;

    // ==== Resumo ====
    @FXML
    private TableView<ColumnProfile> tableStat;
    @FXML
    private TableColumn<ColumnProfile, String> columnName;
    @FXML
    private TableColumn<ColumnProfile, Number> columnCount, columnNulls;
    @FXML
    private TableColumn<ColumnProfile, String> columnMin, columnMax, columnMean, columnMedian,
            columnSTD, columnVariance, columnSkew, columnKurtosis;
    @FXML
    private StackPane chartCorrelation;
    @FXML
    private Button btnExportCorr;

    // ==== Distribuição ====
    @FXML
    private ChoiceBox<String> boxTarget, boxFrequency;
    @FXML
    private Spinner<Integer> binSpinner;
    @FXML
    private TextArea textDistribution;
    @FXML
    private ScrollPane graphstack;
    @FXML
    private BarChart<String, Number> chartDistribution;

    // ==== Valores em falta ====
    @FXML
    private VBox ImputBox;
    @FXML
    private ChoiceBox<String> cbImputeColumn;
    @FXML
    private ComboBox<ImputationService.Method> comboImputeMethod;
    @FXML
    private Label lblConstant;
    @FXML
    private TextField tfConstantValue;
    @FXML
    private TitledPane paneImputeAdvanced;
    @FXML
    private ListView<String> lvImputePredictors;
    @FXML
    private Spinner<Integer> spinK;
    @FXML
    private Button btnPreviewImpute, btnRunImpute;
    @FXML
    private ProgressIndicator piImpute;
    @FXML
    private TextArea taModelSummary, taImputeLog;
    @FXML
    private TableView<PreviewRow> tblImputePreview;

    // ==== Outliers ====
    @FXML
    private ChoiceBox<String> cbOutlierColumn;
    @FXML
    private ComboBox<OutlierService.Method> comboOutlierMethod;
    @FXML
    private Label lblThreshold;
    @FXML
    private TextField tfOutlierThresh;
    @FXML
    private Button btnDetectOutliers, btnRemoveOutliers, btnClampOutliers;
    @FXML
    private TextArea taOutlierSummary;
    @FXML
    private TableView<OutlierRow> tblOutliersPreview;
    @FXML
    private BarChart<String, Number> chartOutlierSpread;

    // ==== Modelação ====
    @FXML
    private VBox boxModel;
    @FXML
    private HBox boxStart;
    @FXML
    private ChoiceBox<String> cbTargetColumn;
    @FXML
    private ComboBox<Models> comboModelAlgo;
    @FXML
    private ListView<String> lvFeatures;
    @FXML
    private ListView<Preprocessor.Step> lvPreprocessing;

    // ==== Previsão de séries ====
    @FXML
    private ChoiceBox<String> cbForecastValue, cbForecastOrder;
    @FXML
    private ComboBox<ForecastService.Method> comboForecastMethod;
    @FXML
    private Spinner<Integer> horizonSpinner, orderPSpinner, orderQSpinner, seasonSpinner, diffSpinner;
    @FXML
    private Spinner<Double> alphaSpinner, betaSpinner, gammaSpinner;
    @FXML
    private TextArea taForecastResults;
    @FXML
    private LineChart<Number, Number> chartForecast;
    @FXML
    private Label lblForecastHint;
    @FXML
    private Spinner<Integer> testSpinner, batchSpinner, limitSpinner, offsetSpinner;
    @FXML
    private Spinner<Double> learningRateSpinner;
    @FXML
    private Button btnTrain, btnExportModel;
    @FXML
    private TextArea taModelResults, textPredictHistory;
    @FXML
    private LineChart<Number, Number> TrainGraph;
    @FXML
    private ScatterChart<Number, Number> graphModelEvaluation, graphModelResidual;
    @FXML
    private BarChart<String, Number> chartImportance;
    @FXML
    private GridPane TestBox;

    // ==== Ações rápidas ====
    @FXML
    private ListView<String> lvHistory;

    // ==== Estado ====
    private DatabaseUpdaterInterface updater;
    private DatabaseFetcherInterface fetcher;
    private DatabaseExecutorInterface executor;
    private SQLTypes dialect = SQLTypes.SQLITE;

    private TaskInterface taskInterface;

    private String table;
    private List<ColumnMetadata> metadata = new ArrayList<>();

    private StatisticsService statistics;
    private ImputationService imputation;
    private OutlierService outliers;

    private final ModelPipeline pipeline = new ModelPipeline();

    private final ObservableList<String> headers = FXCollections.observableArrayList();
    private final ObservableList<ColumnRow> columnRows = FXCollections.observableArrayList();

    private ImputationService.Plan pendingPlan;
    private OutlierService.Report pendingOutliers;

    /** Campos de previsão manual, um por feature, criados depois do treino. */
    private final Map<String, TextField> predictInputs = new LinkedHashMap<>();

    /** Modelo lido de um JSON do scikit-learn, quando existe. */
    private SklearnExchange.ImportedModel importedModel;

    private final memoryInterface memInfo = new memoryInterface() {
        @Override
        public void onLowMemory() {
            Platform.runLater(() -> setStatus("Low memory — consider lowering the row limit."));
        }
    };

    private final SimpleLongProperty memoryState = new SimpleLongProperty();

    // ==== Ciclo de vida ====

    @FXML
    public void initialize() {
        setupColumnsTable();
        setupStatisticsTable();
        setupDistributionPane();
        setupImputationPane();
        setupOutliersPane();
        setupModelingPane();
        setupForecastPane();
        setupPreviewTables();
        bindSearch();

        memoryState.bind(memInfo.getAvalaibleMemoryProp());
        memoryState.addListener((_, _, value) -> Platform.runLater(
                () -> lblMemory.setText("Memory free: " + humanBytes(value.longValue()))));

        setStatus("Ready");
    }

    /** Chamado pelo {@code TableInterface} ao abrir a janela. */
    public void setDatabase(final DatabaseUpdaterInterface updater,
                            final DatabaseFetcherInterface fetcher,
                            final DatabaseExecutorInterface executor,
                            final SQLTypes dialect) {
        this.updater = updater;
        this.fetcher = fetcher;
        this.executor = executor;
        this.dialect = dialect;
    }

    public void setTaskInterface(final TaskInterface taskInterface) {
        this.taskInterface = taskInterface;
    }

    public void setMetadata(final String table, final ArrayList<ColumnMetadata> metadata) {
        this.table = table;
        this.metadata = new ArrayList<>(metadata);

        this.statistics = new StatisticsService(fetcher, executor, table, this.metadata);
        this.imputation = new ImputationService(fetcher, executor, statistics, dialect, table, this.metadata);
        this.outliers = new OutlierService(fetcher, executor, statistics, table);

        headers.setAll(statistics.getColumnNames());
        lblTable.setText("· " + table + " · " + headers.size() + " columns");
        lblFooter.setText(dialect + " · " + table);

        refreshColumnChoices();
        load_table_metadata();
    }

    // ==== Configuração da UI ====

    private void setupColumnsTable() {
        tblColumns.setItems(columnRows);
        tblColumns.setPlaceholder(new Label("No columns loaded"));
        colName.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().name()));
        colType.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().type()));
        colNulls.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().nulls()));

        // A percentagem ganha cor quando a coluna está muito incompleta.
        colNullPct.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                String.format(Locale.US, "%.1f%%", cell.getValue().nullPercent())));
        colNullPct.setCellFactory(_ -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("ds-cell-warn", "ds-cell-bad");
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                setText(item);
                double percent = Double.parseDouble(item.replace("%", ""));
                if (percent >= 50) getStyleClass().add("ds-cell-bad");
                else if (percent >= 10) getStyleClass().add("ds-cell-warn");
            }
        });

        tblColumns.getSelectionModel().selectedItemProperty().addListener(
                (_, _, selected) -> showColumnProfile(selected));
    }

    private void setupStatisticsTable() {
        tableStat.setPlaceholder(new Label("Press Compute to profile every column"));
        columnName.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().name()));
        columnCount.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().totalRows()));
        columnNulls.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().nulls()));
        columnMin.setCellValueFactory(cell -> number(cell.getValue().min()));
        columnMax.setCellValueFactory(cell -> number(cell.getValue().max()));
        columnMean.setCellValueFactory(cell -> number(cell.getValue().mean()));
        columnMedian.setCellValueFactory(cell -> number(cell.getValue().median()));
        columnSTD.setCellValueFactory(cell -> number(cell.getValue().stdDev()));
        columnVariance.setCellValueFactory(cell -> number(cell.getValue().variance()));
        columnSkew.setCellValueFactory(cell -> number(cell.getValue().skewness()));
        columnKurtosis.setCellValueFactory(cell -> number(cell.getValue().excessKurtosis()));
    }

    private void setupDistributionPane() {
        boxTarget.setItems(headers);
        boxFrequency.setItems(FXCollections.observableArrayList("Value frequency", "Binned intervals"));
        boxFrequency.getSelectionModel().selectFirst();
        binSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 200, 20, 1));
    }

    private void setupImputationPane() {
        comboImputeMethod.setItems(FXCollections.observableArrayList(ImputationService.Method.values()));
        comboImputeMethod.getSelectionModel().select(ImputationService.Method.MEAN);
        comboImputeMethod.getSelectionModel().selectedItemProperty().addListener(
                (_, _, method) -> applyImputationMethodVisibility(method));

        cbImputeColumn.setItems(headers);
        lvImputePredictors.setItems(headers);
        lvImputePredictors.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        spinK.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 5, 1));

        applyImputationMethodVisibility(ImputationService.Method.MEAN);
    }

    /** Só mostra os controlos que o método escolhido usa. */
    private void applyImputationMethodVisibility(ImputationService.Method method) {
        if (method == null) return;
        boolean constant = method == ImputationService.Method.CONSTANT;
        lblConstant.setVisible(constant);
        lblConstant.setManaged(constant);
        tfConstantValue.setVisible(constant);
        tfConstantValue.setManaged(constant);

        // O modelo treinado traz as suas próprias features; escolher preditores aqui não
        // faria diferença nenhuma.
        boolean model = method == ImputationService.Method.LINEAR_REGRESSION
                || method == ImputationService.Method.KNN;
        paneImputeAdvanced.setVisible(model);
        paneImputeAdvanced.setManaged(model);
        spinK.setDisable(method != ImputationService.Method.KNN);

        // Um plano antigo deixa de valer assim que o método muda.
        invalidatePlan();
    }

    private void setupOutliersPane() {
        comboOutlierMethod.setItems(FXCollections.observableArrayList(OutlierService.Method.values()));
        comboOutlierMethod.getSelectionModel().select(OutlierService.Method.IQR);
        comboOutlierMethod.getSelectionModel().selectedItemProperty().addListener((_, _, method) -> {
            if (method != null) tfOutlierThresh.setText(String.valueOf(method.getDefaultThreshold()));
            invalidateOutliers();
        });
        cbOutlierColumn.setItems(headers);
        tfOutlierThresh.setText(String.valueOf(OutlierService.Method.IQR.getDefaultThreshold()));
        taOutlierSummary.setText("");
    }

    private void setupModelingPane() {
        comboModelAlgo.setItems(FXCollections.observableArrayList(Models.values()));
        comboModelAlgo.getSelectionModel().select(Models.LINEAR_REGRESSION);

        cbTargetColumn.setItems(headers);
        lvFeatures.setItems(headers);
        lvFeatures.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Os passos aplicam-se pela ordem em que aparecem na lista, não pela ordem de clique.
        lvPreprocessing.setItems(FXCollections.observableArrayList(Preprocessor.Step.values()));
        lvPreprocessing.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        testSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 50, 20, 5));
        learningRateSpinner.setValueFactory(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(0.001, 1.0, 0.05, 0.01));
        batchSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(100, 1_000_000, 5_000, 100));
        limitSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(100, Integer.MAX_VALUE, 50_000, 1000));
        offsetSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 0, 1000));
    }

    private void setupForecastPane() {
        cbForecastValue.setItems(headers);
        cbForecastOrder.setItems(headers);

        comboForecastMethod.setItems(FXCollections.observableArrayList(ForecastService.Method.values()));
        comboForecastMethod.getSelectionModel().select(ForecastService.Method.AR_YULE_WALKER);
        comboForecastMethod.getSelectionModel().selectedItemProperty().addListener(
                (_, _, method) -> applyForecastVisibility(method));

        horizonSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 500, 10, 1));
        orderPSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 3, 1));
        orderQSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 50, 1, 1));
        seasonSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 365, 12, 1));
        diffSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 3, 0, 1));

        alphaSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.01, 1.0, 0.3, 0.05));
        betaSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.01, 1.0, 0.1, 0.05));
        gammaSpinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.01, 1.0, 0.1, 0.05));

        applyForecastVisibility(ForecastService.Method.AR_YULE_WALKER);
    }

    /** Mostra só os parâmetros que o método escolhido usa. */
    private void applyForecastVisibility(final ForecastService.Method method) {
        if (method == null) return;

        setControlVisible(method.needsOrderP(), orderPSpinner);
        setControlVisible(method.needsOrderQ(), orderQSpinner);
        setControlVisible(method.needsSeason(), seasonSpinner);

        final boolean smoothing = method.needsSmoothing();
        setControlVisible(smoothing, alphaSpinner);
        setControlVisible(smoothing && method != ForecastService.Method.SIMPLE_EXPONENTIAL, betaSpinner);
        setControlVisible(method == ForecastService.Method.HOLT_WINTERS, gammaSpinner);

        lblForecastHint.setText(switch (method) {
            case AR_YULE_WALKER, AR_OLS ->
                    "Order p is how many past points feed each prediction. Look at the PACF in the results.";
            case ARMA -> "p is the autoregressive order, q the moving-average order. Check ACF and PACF.";
            case SIMPLE_EXPONENTIAL -> "For a series with no trend and no season. Alpha weighs the recent past.";
            case HOLT -> "For a series with trend but no season. Beta weighs how fast the trend changes.";
            case HOLT_WINTERS -> "For a series with trend and season. Season length is the number of points per cycle.";
        });
    }

    private static void setControlVisible(final boolean visible, final Node control) {
        // Esconder também a etiqueta que acompanha o campo, que é o nó anterior na grelha.
        control.setVisible(visible);
        control.setManaged(visible);
        final Integer row = GridPane.getRowIndex(control);
        if (row == null) return;
        for (Node sibling : ((GridPane) control.getParent()).getChildren()) {
            if (sibling != control && row.equals(GridPane.getRowIndex(sibling))) {
                sibling.setVisible(visible);
                sibling.setManaged(visible);
            }
        }
    }

    /**
     * Corre a previsão sobre a coluna escolhida.
     *
     * <p>A série sai da base de dados já ordenada, porque numa série temporal a ordem é o
     * que dá sentido aos valores — sem ORDER BY viria pela ordem física das linhas.</p>
     */
    @FXML
    private void runForecast() {
        final String value = cbForecastValue.getValue();
        if (value == null) {
            ShowInformation("No column", "Choose the column with the values to forecast.");
            return;
        }
        if (!statistics.isNumeric(value)) {
            ShowInformation("Not numeric", value + " is not a numeric column.");
            return;
        }

        final String order = cbForecastOrder.getValue();
        final ForecastService.Method method = comboForecastMethod.getValue();
        final int differencing = diffSpinner.getValue();

        final ForecastService.Request request = new ForecastService.Request(
                method, horizonSpinner.getValue(), orderPSpinner.getValue(), orderQSpinner.getValue(),
                seasonSpinner.getValue(), alphaSpinner.getValue(), betaSpinner.getValue(),
                gammaSpinner.getValue());

        runInBackground("Forecasting", () -> {
            final StringBuilder sql = new StringBuilder("SELECT ")
                    .append(executor.quoteIdentifier(value))
                    .append(" FROM ").append(executor.quoteIdentifier(table))
                    .append(" WHERE ").append(executor.quoteIdentifier(value)).append(" IS NOT NULL");
            if (order != null) sql.append(" ORDER BY ").append(executor.quoteIdentifier(order));
            sql.append(" LIMIT ").append(limitSpinner.getValue());

            final List<Double> fetched = fetcher.fetchDataMap(sql.toString());
            if (fetched == null || fetched.isEmpty()) {
                Platform.runLater(() -> ShowInformation("No data", "The column has no values to forecast."));
                return;
            }

            final double[] raw = fetched.stream().mapToDouble(Double::doubleValue).toArray();
            final double[] series = differencing > 0
                    ? ForecastService.difference(raw, differencing) : raw;

            final ForecastService.Result result = ForecastService.run(series, request);

            Platform.runLater(() -> {
                renderForecast(result, differencing);
                taForecastResults.appendText(result.summary() + "\n");
                for (Map.Entry<String, Object> metric : result.metrics().entrySet()) {
                    taForecastResults.appendText("  " + metric.getKey() + ": "
                            + formatMetricValue(metric.getValue()) + "\n");
                }
                taForecastResults.appendText("\n");
                setStatus(method + " over " + series.length + " point(s)");
                addHistory("Forecast " + method + " on " + value);
            });
        });
    }

    private void renderForecast(final ForecastService.Result result, final int differencing) {
        chartForecast.getData().clear();

        final XYChart.Series<Number, Number> observed = new XYChart.Series<>();
        observed.setName(differencing > 0 ? "Observed (differenced)" : "Observed");
        for (int i = 0; i < result.observed().length; i++) {
            observed.getData().add(new XYChart.Data<>(i, result.observed()[i]));
        }

        final XYChart.Series<Number, Number> fitted = new XYChart.Series<>();
        fitted.setName("Fitted");
        // Os valores ajustados de um AR começam depois das primeiras p observações.
        final int offset = result.observed().length - result.fitted().length;
        for (int i = 0; i < result.fitted().length; i++) {
            fitted.getData().add(new XYChart.Data<>(i + Math.max(0, offset), result.fitted()[i]));
        }

        final XYChart.Series<Number, Number> forecast = new XYChart.Series<>();
        forecast.setName("Forecast");
        for (int i = 0; i < result.forecast().length; i++) {
            forecast.getData().add(new XYChart.Data<>(result.observed().length + i, result.forecast()[i]));
        }

        chartForecast.getData().addAll(observed, fitted, forecast);
        chartForecast.setTitle(result.method().toString());
    }

    private static String formatMetricValue(final Object value) {
        if (value instanceof Double number) {
            return number.isNaN() ? "n/a" : String.format(Locale.US, "%.6g", number);
        }
        return String.valueOf(value);
    }

    @FXML
    private void clearForecast() {
        chartForecast.getData().clear();
        chartForecast.setTitle("");
        taForecastResults.clear();
    }

    private void setupPreviewTables() {
        tblImputePreview.setPlaceholder(new Label("Run a preview to see the rows"));
        TableColumn<PreviewRow, String> keyColumn = new TableColumn<>("Row");
        keyColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().key()));
        TableColumn<PreviewRow, String> valueColumn = new TableColumn<>("New value");
        valueColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().value()));
        tblImputePreview.getColumns().setAll(List.of(keyColumn, valueColumn));

        tblOutliersPreview.setPlaceholder(new Label("Run a detection to see the values"));
        TableColumn<OutlierRow, String> sideColumn = new TableColumn<>("Side");
        sideColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().side()));
        TableColumn<OutlierRow, String> outlierValue = new TableColumn<>("Value");
        outlierValue.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                String.format(Locale.US, "%.6g", cell.getValue().value())));
        tblOutliersPreview.getColumns().setAll(List.of(sideColumn, outlierValue));
    }

    private void bindSearch() {
        searchField.textProperty().addListener((_, _, query) -> filterColumns(query));
    }

    private void refreshColumnChoices() {
        selectFirstIfEmpty(cbImputeColumn);
        selectFirstIfEmpty(cbOutlierColumn);
        selectFirstIfEmpty(cbTargetColumn);
        selectFirstIfEmpty(boxTarget);
    }

    private void selectFirstIfEmpty(ChoiceBox<String> box) {
        if (box.getValue() == null && !headers.isEmpty()) box.getSelectionModel().selectFirst();
    }

    // ==== Colunas ====

    @FXML
    private void load_table_metadata() {
        if (statistics == null) return;
        runInBackground("Counting nulls", () -> {
            long totalRows = statistics.countRows();
            List<ColumnRow> rows = new ArrayList<>();
            for (ColumnMetadata column : metadata) {
                long nulls = statistics.countNulls(column.Name);
                rows.add(new ColumnRow(column.Name, column.Type, nulls, totalRows));
            }
            Platform.runLater(() -> {
                columnRows.setAll(rows);
                setStatus(totalRows + " row(s) in " + table);
            });
        });
    }

    private void showColumnProfile(ColumnRow selected) {
        if (selected == null || statistics == null) {
            taColumnProfile.clear();
            return;
        }
        taColumnProfile.setText("Loading " + selected.name() + "...");
        runInBackground("Profiling " + selected.name(), () -> {
            ColumnProfile profile = statistics.profile(selected.name());
            Platform.runLater(() -> taColumnProfile.setText(describeProfile(profile)));
        });
    }

    private String describeProfile(ColumnProfile profile) {
        StringBuilder text = new StringBuilder();
        text.append(profile.name()).append("  (").append(profile.type()).append(")\n");
        text.append("rows      ").append(profile.totalRows()).append('\n');
        text.append("nulls     ").append(profile.nulls())
                .append(String.format(Locale.US, "  (%.1f%%)%n", profile.nullRatio() * 100));
        text.append("distinct  ").append(profile.distinct()).append('\n');
        if (!profile.numeric()) {
            text.append("\nNot a numeric column, so there are no summary statistics.");
            return text.toString();
        }
        text.append('\n');
        text.append(line("min", profile.min()));
        text.append(line("q1", profile.q1()));
        text.append(line("median", profile.median()));
        text.append(line("q3", profile.q3()));
        text.append(line("max", profile.max()));
        text.append(line("mean", profile.mean()));
        text.append(line("std dev", profile.stdDev()));
        text.append(line("variance", profile.variance()));
        text.append(line("skew", profile.skewness()));
        text.append(line("kurtosis", profile.excessKurtosis()));
        return text.toString();
    }

    private static String line(String label, double value) {
        return String.format(Locale.US, "%-9s %s%n", label, formatNumber(value));
    }

    private void filterColumns(String query) {
        if (query == null || query.isBlank()) {
            tblColumns.setItems(columnRows);
            return;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        tblColumns.setItems(columnRows.stream()
                .filter(row -> row.name().toLowerCase(Locale.ROOT).contains(needle)
                        || row.type().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.toCollection(FXCollections::observableArrayList)));
    }

    @FXML
    private void reloadEverything() {
        if (statistics == null) return;
        load_table_metadata();
        computeQuickStats();
        addHistory("Refreshed " + table);
    }

    // ==== Estatísticas descritivas ====

    @FXML
    private void computeQuickStats() {
        if (statistics == null) return;
        tableStat.getItems().clear();
        runInBackground("Computing statistics", () -> {
            List<ColumnProfile> profiles = new ArrayList<>();
            for (String column : headers) profiles.add(statistics.profile(column));
            Platform.runLater(() -> {
                tableStat.getItems().setAll(profiles);
                setStatus("Profiled " + profiles.size() + " column(s)");
                addHistory("Descriptive statistics");
            });
        });
    }

    @FXML
    private void exportStatistics() {
        if (tableStat.getItems().isEmpty()) {
            ShowInformation("Nothing to export", "Compute the statistics first.");
            return;
        }
        File target = chooseSaveFile("CSV", "*.csv");
        if (target == null) return;
        try (PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
            writer.println("column,type,rows,nulls,distinct,min,q1,median,q3,max,mean,stddev,variance,skew,excess_kurtosis");
            for (ColumnProfile profile : tableStat.getItems()) {
                writer.println(String.join(",",
                        escapeCsv(profile.name()), escapeCsv(profile.type()),
                        String.valueOf(profile.totalRows()), String.valueOf(profile.nulls()),
                        String.valueOf(profile.distinct()),
                        csvNumber(profile.min()), csvNumber(profile.q1()), csvNumber(profile.median()),
                        csvNumber(profile.q3()), csvNumber(profile.max()), csvNumber(profile.mean()),
                        csvNumber(profile.stdDev()), csvNumber(profile.variance()),
                        csvNumber(profile.skewness()), csvNumber(profile.excessKurtosis())));
            }
            setStatus("Exported " + target.getName());
            addHistory("Export statistics: " + target.getName());
        } catch (Exception e) {
            ShowError("Export failed", "Could not write the statistics file.", e.getMessage());
        }
    }

    // ==== Correlação ====

    @FXML
    private void renderCorrelation() {
        if (statistics == null) return;
        List<String> numeric = statistics.getNumericColumnNames();
        if (numeric.size() < 2) {
            ShowInformation("Not enough columns", "Correlation needs at least two numeric columns.");
            return;
        }
        chartCorrelation.getChildren().setAll(new Label("Computing..."));
        runInBackground("Computing correlation", () -> {
            double[][] matrix = statistics.correlationMatrix(numeric);
            Platform.runLater(() -> {
                chartCorrelation.getChildren().setAll(buildHeatmap(numeric, matrix));
                addHistory("Correlation matrix");
            });
        });
    }

    /**
     * Mapa de calor em JavaFX puro. A versão anterior embutia um gráfico Swing do Smile
     * num {@code SwingNode}, que não seguia o tema da aplicação e não redimensionava.
     */
    private Node buildHeatmap(List<String> columns, double[][] matrix) {
        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        grid.setPadding(new Insets(8));

        for (int c = 0; c < columns.size(); c++) {
            Label header = new Label(shorten(columns.get(c)));
            header.setRotate(-45);
            header.getStyleClass().add("ds-hint");
            grid.add(new Group(header), c + 1, 0);
        }

        for (int r = 0; r < columns.size(); r++) {
            Label rowLabel = new Label(shorten(columns.get(r)));
            rowLabel.getStyleClass().add("ds-hint");
            grid.add(rowLabel, 0, r + 1);

            for (int c = 0; c < columns.size(); c++) {
                double value = matrix[r][c];
                Label cell = new Label(Double.isNaN(value)
                        ? "—" : String.format(Locale.US, "%.2f", value));
                cell.setAlignment(Pos.CENTER);
                cell.setPrefSize(52, 26);
                cell.setTextFill(Math.abs(value) > 0.55 ? Color.WHITE : Color.web("#D6D6D6"));
                cell.setStyle("-fx-background-color: " + correlationColour(value)
                        + "; -fx-background-radius: 3; -fx-font-size: 10px;");
                cell.setTooltip(new Tooltip(columns.get(r) + " vs " + columns.get(c)
                        + "\nr = " + formatNumber(value)));
                grid.add(cell, c + 1, r + 1);
            }
        }
        return grid;
    }

    /** Azul para correlação positiva, vermelho para negativa, cinza para ausente. */
    private static String correlationColour(double value) {
        if (Double.isNaN(value)) return "#3A3A3A";
        double intensity = Math.min(Math.abs(value), 1.0);
        int alpha = (int) (40 + intensity * 160);
        return value >= 0
                ? String.format("rgba(53,116,240,%.2f)", alpha / 255.0)
                : String.format("rgba(224,108,117,%.2f)", alpha / 255.0);
    }

    @FXML
    private void handleExportCorrelationMatrix() {
        if (statistics == null) return;
        List<String> numeric = statistics.getNumericColumnNames();
        if (numeric.size() < 2) {
            ShowInformation("Not enough columns", "Correlation needs at least two numeric columns.");
            return;
        }
        File target = chooseSaveFile("CSV", "*.csv");
        if (target == null) return;

        runInBackground("Exporting correlation", () -> {
            double[][] matrix = statistics.correlationMatrix(numeric);
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
                writer.println("," + numeric.stream().map(DataScienceController::escapeCsv)
                        .collect(Collectors.joining(",")));
                for (int r = 0; r < numeric.size(); r++) {
                    StringBuilder row = new StringBuilder(escapeCsv(numeric.get(r)));
                    for (int c = 0; c < numeric.size(); c++) row.append(',').append(csvNumber(matrix[r][c]));
                    writer.println(row);
                }
                Platform.runLater(() -> {
                    setStatus("Exported " + target.getName());
                    addHistory("Export correlation: " + target.getName());
                });
            } catch (Exception e) {
                Platform.runLater(() -> ShowError("Export failed",
                        "Could not write the correlation file.", e.getMessage()));
            }
        });
    }

    // ==== Distribuição ====

    @FXML
    private void calculateDistribution() {
        final String column = boxTarget.getValue();
        if (column == null) {
            boxTarget.requestFocus();
            ShowInformation("No column", "Choose the column to analyse.");
            return;
        }

        final boolean binned = boxFrequency.getSelectionModel().getSelectedIndex() == 1;
        final int bins = binSpinner.getValue();

        runInBackground("Analysing " + column, () -> {
            ColumnProfile profile = statistics.profile(column);
            LinkedHashMap<String, Long> counts = binned
                    ? statistics.histogram(column, bins)
                    : statistics.valueCounts(column, 60);

            Platform.runLater(() -> {
                renderDistribution(column, counts, binned);
                textDistribution.appendText(describeShape(profile));
                addHistory("Distribution: " + column);
            });
        });
    }

    private String describeShape(ColumnProfile profile) {
        if (!profile.numeric()) {
            return profile.name() + ": not numeric — showing value frequencies only.\n\n";
        }
        StringBuilder text = new StringBuilder(profile.name()).append('\n');
        text.append(line("mean", profile.mean()));
        text.append(line("median", profile.median()));
        text.append(line("std dev", profile.stdDev()));
        text.append(line("skew", profile.skewness()));
        text.append(line("kurtosis", profile.excessKurtosis()));
        text.append("reading   ").append(readShape(profile)).append("\n\n");
        return text.toString();
    }

    /** Tradução das medidas de forma para uma frase, que é o que se quer mesmo saber. */
    private static String readShape(ColumnProfile profile) {
        StringBuilder reading = new StringBuilder();
        double skew = profile.skewness();
        if (Double.isNaN(skew)) {
            reading.append("not enough spread to judge");
        } else if (Math.abs(skew) < 0.5) {
            reading.append("roughly symmetric");
        } else if (skew > 0) {
            reading.append("right-skewed (a tail of high values)");
        } else {
            reading.append("left-skewed (a tail of low values)");
        }

        double excess = profile.excessKurtosis();
        if (!Double.isNaN(excess)) {
            if (excess > 1) reading.append(", heavy tails");
            else if (excess < -1) reading.append(", light tails");
        }
        return reading.toString();
    }

    private void renderDistribution(String column, LinkedHashMap<String, Long> counts, boolean binned) {
        chartDistribution.getData().clear();
        chartDistribution.setTitle(column + (binned ? " — binned" : " — value frequency"));
        chartDistribution.getXAxis().setLabel(binned ? "Interval" : "Value");
        chartDistribution.getYAxis().setLabel("Rows");

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        chartDistribution.getData().add(series);

        // Muitas categorias precisam de largura para as etiquetas não colidirem.
        chartDistribution.setPrefWidth(Math.max(600, counts.size() * 58.0));
    }

    @FXML
    private void cleanGraph() {
        chartDistribution.getData().clear();
        chartDistribution.setTitle("");
        chartDistribution.setPrefWidth(Region.USE_COMPUTED_SIZE);
        chartDistribution.getXAxis().setLabel("");
        chartDistribution.getYAxis().setLabel("");
    }

    @FXML
    private void clearDistributionLog() {
        textDistribution.clear();
    }

    // ==== Valores em falta ====

    @FXML
    private void clearPredictorSelection() {
        lvImputePredictors.getSelectionModel().clearSelection();
        invalidatePlan();
    }

    @FXML
    private void handlePreviewImputation() {
        final String column = cbImputeColumn.getValue();
        if (column == null) {
            ShowInformation("No column", "Choose the column to fill.");
            return;
        }

        final ImputationService.Method method = comboImputeMethod.getValue();
        final List<String> predictors = new ArrayList<>(lvImputePredictors.getSelectionModel().getSelectedItems());
        final int k = spinK.getValue();
        final String constant = tfConstantValue.getText();

        piImpute.setVisible(true);
        btnPreviewImpute.setDisable(true);

        runInBackground("Previewing imputation", () -> {
            try {
                ImputationService.Plan plan =
                        imputation.preview(column, method, predictors, k, constant, pipeline);
                Platform.runLater(() -> {
                    pendingPlan = plan;
                    taModelSummary.setText(plan.summary());
                    appendLog(taImputeLog, plan.summary());
                    showPlanRows(plan);
                    // Só faz sentido aplicar se o plano vai mesmo escrever alguma coisa.
                    btnRunImpute.setDisable(plan.affectedRows() == 0);
                    setStatus(plan.affectedRows() + " row(s) would change");
                });
            } finally {
                Platform.runLater(() -> {
                    piImpute.setVisible(false);
                    btnPreviewImpute.setDisable(false);
                });
            }
        });
    }

    private void showPlanRows(ImputationService.Plan plan) {
        List<PreviewRow> rows = new ArrayList<>();
        if (plan.constantValue() != null) {
            rows.add(new PreviewRow("every row where " + plan.column() + " IS NULL", plan.constantValue()));
        } else {
            for (ImputationService.RowFill fill : plan.perRow()) {
                rows.add(new PreviewRow(describeKey(fill.key()), formatNumber(fill.value())));
            }
        }
        tblImputePreview.getItems().setAll(rows);
    }

    private static String describeKey(Map<String, String> key) {
        return key.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", "));
    }

    @FXML
    private void handleRunImputation() {
        if (pendingPlan == null || pendingPlan.affectedRows() == 0) {
            ShowInformation("Nothing to apply", "Run a preview first.");
            return;
        }

        final ImputationService.Plan plan = pendingPlan;
        if (!ShowConfirmation("Write to the database",
                "This will update " + plan.affectedRows() + " row(s) of "
                        + plan.column() + " in " + table + ". Continue?")) {
            return;
        }

        piImpute.setVisible(true);
        btnRunImpute.setDisable(true);

        runInBackground("Applying imputation", () -> {
            try {
                int affected = imputation.apply(plan);
                Platform.runLater(() -> {
                    appendLog(taImputeLog, "Applied: " + affected + " row(s) updated.");
                    setStatus(affected + " row(s) updated");
                    addHistory("Imputation " + plan.method() + " on " + plan.column()
                            + " (" + affected + " rows)");
                    invalidatePlan();
                    load_table_metadata();
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    ShowError("Update failed", "Could not write the imputed values.", e.getMessage());
                    appendLog(taImputeLog, "Failed: " + e.getMessage());
                    btnRunImpute.setDisable(false);
                });
            } finally {
                Platform.runLater(() -> piImpute.setVisible(false));
            }
        });
    }

    private void invalidatePlan() {
        pendingPlan = null;
        if (btnRunImpute != null) btnRunImpute.setDisable(true);
        if (tblImputePreview != null) tblImputePreview.getItems().clear();
    }

    // ==== Outliers ====

    @FXML
    private void handleDetectOutliers() {
        final String column = cbOutlierColumn.getValue();
        if (column == null) {
            ShowInformation("No column", "Choose the column to scan.");
            return;
        }
        final OutlierService.Method method = comboOutlierMethod.getValue();
        final double threshold = parseDoubleOrDefault(tfOutlierThresh.getText(), method.getDefaultThreshold());

        runInBackground("Detecting outliers", () -> {
            OutlierService.Report report = outliers.detect(column, method, threshold);
            Platform.runLater(() -> {
                pendingOutliers = report;
                taOutlierSummary.setText(report.summary());
                showOutlierRows(report);
                renderOutlierSpread(report);
                boolean actionable = report.hasBounds() && report.total() > 0;
                btnRemoveOutliers.setDisable(!actionable);
                btnClampOutliers.setDisable(!actionable);
                setStatus(report.total() + " outlier(s) in " + column);
                addHistory("Outliers " + method + " on " + column + ": " + report.total());
            });
        });
    }

    private void showOutlierRows(OutlierService.Report report) {
        List<OutlierRow> rows = new ArrayList<>();
        for (Double value : report.lowSamples()) rows.add(new OutlierRow("below", value));
        for (Double value : report.highSamples()) rows.add(new OutlierRow("above", value));
        tblOutliersPreview.getItems().setAll(rows);
    }

    /** Mostra quantas linhas caem abaixo, dentro e acima dos limites. */
    private void renderOutlierSpread(OutlierService.Report report) {
        chartOutlierSpread.getData().clear();
        if (!report.hasBounds()) return;

        ColumnProfile profile = null;
        long inside = 0;
        try {
            profile = statistics.profile(report.column());
            inside = profile.totalRows() - profile.nulls() - report.total();
        } catch (Exception _) {
            // Sem perfil só se mostram os extremos.
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.getData().add(new XYChart.Data<>("Below", report.belowCount()));
        if (profile != null) series.getData().add(new XYChart.Data<>("Within bounds", Math.max(inside, 0)));
        series.getData().add(new XYChart.Data<>("Above", report.aboveCount()));

        chartOutlierSpread.setTitle(String.format(Locale.US, "%s  [%.4g , %.4g]",
                report.column(), report.lowerBound(), report.upperBound()));
        chartOutlierSpread.getData().add(series);
    }

    @FXML
    private void handleRemoveOutliers() {
        if (pendingOutliers == null) return;
        final OutlierService.Report report = pendingOutliers;

        if (!ShowConfirmation("Delete rows",
                "This permanently deletes " + report.total() + " row(s) from " + table
                        + " where " + report.column() + " falls outside the bounds. Continue?")) {
            return;
        }

        runInBackground("Deleting outliers", () -> {
            try {
                int deleted = outliers.remove(report);
                Platform.runLater(() -> {
                    taOutlierSummary.appendText("\n\nDeleted " + deleted + " row(s).");
                    setStatus(deleted + " row(s) deleted");
                    addHistory("Deleted " + deleted + " outlier row(s) from " + report.column());
                    invalidateOutliers();
                    load_table_metadata();
                });
            } catch (Exception e) {
                Platform.runLater(() -> ShowError("Delete failed",
                        "Could not delete the outlier rows.", e.getMessage()));
            }
        });
    }

    @FXML
    private void handleClampOutliers() {
        if (pendingOutliers == null) return;
        final OutlierService.Report report = pendingOutliers;

        if (!ShowConfirmation("Clamp values",
                "This rewrites " + report.total() + " value(s) of " + report.column()
                        + " to the bounds instead of deleting the rows. Continue?")) {
            return;
        }

        runInBackground("Clamping outliers", () -> {
            try {
                int changed = outliers.clamp(report);
                Platform.runLater(() -> {
                    taOutlierSummary.appendText("\n\nClamped " + changed + " value(s).");
                    setStatus(changed + " value(s) clamped");
                    addHistory("Clamped " + changed + " value(s) of " + report.column());
                    invalidateOutliers();
                });
            } catch (Exception e) {
                Platform.runLater(() -> ShowError("Clamp failed",
                        "Could not clamp the outlier values.", e.getMessage()));
            }
        });
    }

    private void invalidateOutliers() {
        pendingOutliers = null;
        if (btnRemoveOutliers != null) btnRemoveOutliers.setDisable(true);
        if (btnClampOutliers != null) btnClampOutliers.setDisable(true);
    }

    // ==== Modelação ====

    @FXML
    private void TrainModel() {
        final String target = cbTargetColumn.getValue();
        if (target == null) {
            ShowInformation("No target", "Choose the column the model should predict.");
            return;
        }

        final List<String> features = lvFeatures.getSelectionModel().getSelectedItems().stream()
                .filter(column -> !column.equals(target))
                .distinct()
                .toList();

        if (features.isEmpty()) {
            ShowInformation("No features", "Select at least one feature column (Ctrl+click for several).");
            return;
        }

        final Models algorithm = comboModelAlgo.getValue();
        if (algorithm.requiresNumericTarget() && !statistics.isNumeric(target)) {
            ShowInformation("Target is not numeric",
                    algorithm + " needs a numeric target. Pick a classification algorithm instead.");
            return;
        }

        final int batchSize = batchSpinner.getValue();
        final int limit = limitSpinner.getValue();
        final int startOffset = offsetSpinner.getValue();

        resetModelCharts(features);

        // Os passos escolhidos entram antes de o modelo ver os dados.
        pipeline.setPreprocessing(new ArrayList<>(lvPreprocessing.getSelectionModel().getSelectedItems()));

        ModelPipeline.Request request = new ModelPipeline.Request(
                target, features, algorithm, testSpinner.getValue(),
                learningRateSpinner.getValue(), spinK.getValue(), 42L);

        Task<TrainingResult> training = new Task<>() {

            @Override
            protected TrainingResult call() {
                updateTitle("Training " + algorithm);
                pipeline.start(request);

                int offset = startOffset;
                int loaded = 0;
                TrainingResult result = null;

                while (loaded < limit) {
                    if (isCancelled()) break;

                    int pageSize = Math.min(batchSize, limit - loaded);
                    List<Map<String, String>> rows = loadTrainingRows(target, features, pageSize, offset);
                    if (rows.isEmpty()) break;

                    result = pipeline.feed(rows);
                    loaded += rows.size();
                    offset += rows.size();

                    updateProgress(loaded, limit);
                    updateMessage("Loaded " + loaded + " row(s)");

                    final TrainingResult step = result;
                    if (step != null) Platform.runLater(() -> onTrainingStep(step));

                    // Menos linhas do que pedimos significa fim da tabela.
                    if (rows.size() < pageSize) break;
                }

                return result;
            }

            @Override
            protected void running() {
                super.running();
                boxStart.setDisable(true);
                globalProgress.setVisible(true);
                globalProgress.progressProperty().bind(progressProperty());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                TrainingResult result = getValue();
                if (result == null) {
                    ShowInformation("No usable rows",
                            "Every row had a null or non-numeric value in the target or a feature.");
                    return;
                }
                onTrainingFinished(result);
            }

            @Override
            protected void failed() {
                super.failed();
                Throwable error = getException();
                while (error.getCause() != null) error = error.getCause();
                if (error instanceof OutOfMemoryError) {
                    ShowError("Out of memory",
                            "Not enough memory to train on this much data. Lower the row limit.");
                } else {
                    ShowError("Training failed", "Could not train the model.", error.getMessage());
                }
            }

            @Override
            protected void done() {
                super.done();
                Platform.runLater(() -> {
                    boxStart.setDisable(false);
                    globalProgress.progressProperty().unbind();
                    globalProgress.setVisible(false);
                });
            }
        };

        Thread trainingThread = new Thread(training, "ds-training");
        trainingThread.setDaemon(true);
        trainingThread.start();

        if (taskInterface != null) taskInterface.addTask(training);
    }

    /** Traz um lote de linhas com o alvo e as features, descartando as que têm nulos. */
    private List<Map<String, String>> loadTrainingRows(String target, List<String> features,
                                                       int pageSize, int offset) {
        StringBuilder sql = new StringBuilder("SELECT ").append(executor.quoteIdentifier(target));
        for (String feature : features) sql.append(", ").append(executor.quoteIdentifier(feature));
        sql.append(" FROM ").append(executor.quoteIdentifier(table));

        sql.append(" WHERE ").append(executor.quoteIdentifier(target)).append(" IS NOT NULL");
        for (String feature : features) {
            sql.append(" AND ").append(executor.quoteIdentifier(feature)).append(" IS NOT NULL");
        }

        sql.append(" LIMIT ").append(pageSize).append(" OFFSET ").append(offset);

        ArrayList<HashMap<String, String>> rows = fetcher.fetchRawDataMap(sql.toString());
        return rows == null ? List.of() : new ArrayList<>(rows);
    }

    private void resetModelCharts(List<String> features) {
        TrainGraph.getData().clear();
        graphModelEvaluation.getData().clear();
        graphModelResidual.getData().clear();
        chartImportance.getData().clear();
        taModelResults.clear();
        TestBox.getChildren().clear();
        predictInputs.clear();

        XYChart.Series<Number, Number> curve = new XYChart.Series<>();
        curve.setName("Score");
        TrainGraph.getData().add(curve);

        // Um campo de entrada por feature, para a previsão manual.
        for (int i = 0; i < features.size(); i++) {
            String feature = features.get(i);
            Label label = new Label(feature + ":");
            TextField field = new TextField();
            field.setPromptText("numeric value");
            predictInputs.put(feature, field);
            TestBox.add(label, 0, i);
            TestBox.add(field, 1, i);
        }
    }

    /** Atualiza a curva de aprendizagem a cada lote. */
    private void onTrainingStep(TrainingResult result) {
        double score = result.headlineMetric();
        if (!Double.isNaN(score)) {
            TrainGraph.getData().getFirst().getData()
                    .add(new XYChart.Data<>(pipeline.getStep(), score));
            TrainGraph.getYAxis().setLabel(result.headlineMetricName());
        }
        taModelResults.appendText(pipeline.describeLastResult());
    }

    private void onTrainingFinished(TrainingResult result) {
        renderPredictedVsActual(result);
        renderResiduals(result);
        renderImportance();
        setStatus(String.format(Locale.US, "%s trained — %s = %s",
                result.type(), result.headlineMetricName(), formatNumber(result.headlineMetric())));
        addHistory("Trained " + result.type() + " on " + result.target());
    }

    private void renderPredictedVsActual(TrainingResult result) {
        graphModelEvaluation.getData().clear();
        if (result.actual().length == 0) return;

        XYChart.Series<Number, Number> points = new XYChart.Series<>();
        points.setName("Test rows");
        for (int i = 0; i < result.actual().length; i++) {
            points.getData().add(new XYChart.Data<>(result.actual()[i], result.predicted()[i]));
        }
        graphModelEvaluation.getData().add(points);
    }

    private void renderResiduals(TrainingResult result) {
        graphModelResidual.getData().clear();
        double[] residuals = result.residuals();
        if (residuals.length == 0) return;

        XYChart.Series<Number, Number> points = new XYChart.Series<>();
        points.setName("Residuals");
        for (int i = 0; i < residuals.length; i++) {
            points.getData().add(new XYChart.Data<>(result.predicted()[i], residuals[i]));
        }
        graphModelResidual.getData().add(points);
    }

    private void renderImportance() {
        chartImportance.getData().clear();
        LinkedHashMap<String, Double> importance = pipeline.featureImportance();
        if (importance.isEmpty()) return;

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map.Entry<String, Double> entry : importance.entrySet()) {
            series.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()));
        }
        chartImportance.getData().add(series);
    }

    @FXML
    private void PredictValue() {
        // Um modelo importado tem prioridade: foi o que o utilizador acabou de carregar.
        if (importedModel != null) {
            predictWithImported();
            return;
        }

        if (pipeline.getModel() == null) {
            ShowInformation("No model", "Train a model before predicting.");
            return;
        }

        List<String> features = pipeline.getFeatures();
        double[] values = new double[features.size()];
        StringBuilder description = new StringBuilder();

        for (int i = 0; i < features.size(); i++) {
            TextField field = predictInputs.get(features.get(i));
            try {
                values[i] = Double.parseDouble(field.getText().trim().replace(',', '.'));
                description.append(features.get(i)).append('=').append(field.getText().trim()).append("  ");
            } catch (Exception e) {
                field.requestFocus();
                ShowError("Invalid value", "Type a numeric value for " + features.get(i) + ".");
                return;
            }
        }

        double prediction = pipeline.predictOne(values);
        textPredictHistory.appendText(String.format("%s -> %s = %s%n%n",
                description.toString().trim(), pipeline.getTarget(),
                pipeline.describePrediction(prediction)));
    }

    /** Previsão com o modelo lido de JSON, sem passar pelo Smile. */
    private void predictWithImported() {
        final List<String> features = importedModel.features();
        final double[] values = new double[features.size()];
        final StringBuilder description = new StringBuilder();

        for (int i = 0; i < features.size(); i++) {
            final TextField field = predictInputs.get(features.get(i));
            try {
                values[i] = Double.parseDouble(field.getText().trim().replace(',', '.'));
                description.append(features.get(i)).append('=').append(field.getText().trim()).append("  ");
            } catch (Exception e) {
                field.requestFocus();
                ShowError("Invalid value", "Type a numeric value for " + features.get(i) + ".");
                return;
            }
        }

        final double prediction = importedModel.predict(values);
        textPredictHistory.appendText(String.format("[imported] %s -> %s = %s%n%n",
                description.toString().trim(), importedModel.target(),
                importedModel.describe(prediction)));
    }

    @FXML
    private void exportModel() {
        if (pipeline.getModel() == null) {
            ShowInformation("No model", "Train a model before exporting.");
            return;
        }
        try {
            final String target = path.selectPath((Stage) MainContainer.getScene().getWindow());
            if (target == null) return;
            pipeline.exportModel(target);
            setStatus("Model exported");
            addHistory("Exported model to " + target);
        } catch (Exception e) {
            ShowError("Export failed", "Could not export the model.", e.getMessage());
        }
    }

    /**
     * Exporta o modelo num formato que o scikit-learn consegue reconstruir.
     *
     * <p>O export normal grava o objeto Java serializado, que só o próprio Smile relê.</p>
     */
    @FXML
    private void exportForSklearn() {
        if (pipeline.getModel() == null) {
            ShowInformation("No model", "Train a model before exporting.");
            return;
        }
        File target = chooseSaveFile("JSON", "*.json");
        if (target == null) return;

        try {
            List<java.nio.file.Path> written = SklearnExchange.export(pipeline, target.getAbsolutePath());
            setStatus("Exported for scikit-learn");
            addHistory("Exported for scikit-learn: " + written.getFirst().getFileName());
            ShowInformation("Exported",
                    "Written:\n" + written.getFirst() + "\n" + written.get(1)
                            + "\n\nRun the .py next to the .json to rebuild the estimator in Python.");
        } catch (SklearnExchange.UnsupportedModelException e) {
            ShowInformation("Not supported", e.getMessage());
        } catch (Exception e) {
            ShowError("Export failed", "Could not export the model.", e.getMessage());
        }
    }

    /** Carrega um modelo linear em JSON e usa-o no separador de previsão. */
    @FXML
    private void importFromSklearn() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import a model");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON model", "*.json"));
        File source = chooser.showOpenDialog(
                MainContainer.getScene() == null ? null : MainContainer.getScene().getWindow());
        if (source == null) return;

        try {
            importedModel = SklearnExchange.importModel(source.toPath());

            // Os campos de previsão passam a ser os do modelo importado.
            TestBox.getChildren().clear();
            predictInputs.clear();
            for (int i = 0; i < importedModel.features().size(); i++) {
                final String feature = importedModel.features().get(i);
                final TextField field = new TextField();
                field.setPromptText("numeric value");
                predictInputs.put(feature, field);
                TestBox.add(new Label(feature + ":"), 0, i);
                TestBox.add(field, 1, i);
            }

            taModelResults.appendText(String.format(
                    "Imported %s from %s%n  target: %s%n  features: %s%n%n",
                    importedModel.estimator(), source.getName(), importedModel.target(),
                    String.join(", ", importedModel.features())));

            setStatus("Model imported — use the Predict tab");
            addHistory("Imported model: " + source.getName());
        } catch (SklearnExchange.UnsupportedModelException e) {
            ShowInformation("Cannot read this file", e.getMessage());
        } catch (Exception e) {
            ShowError("Import failed", "Could not read the model file.", e.getMessage());
        }
    }

    // ==== Ações rápidas ====

    @FXML
    private void quickMissingReport() {
        if (statistics == null) return;
        runInBackground("Checking missing values", () -> {
            long totalRows = statistics.countRows();
            StringBuilder report = new StringBuilder("Missing values in ").append(table)
                    .append(" (").append(totalRows).append(" rows)\n\n");
            boolean any = false;
            for (String column : headers) {
                long nulls = statistics.countNulls(column);
                if (nulls == 0) continue;
                any = true;
                report.append(String.format(Locale.US, "%-24s %8d  %.1f%%%n",
                        column, nulls, totalRows == 0 ? 0 : 100.0 * nulls / totalRows));
            }
            if (!any) report.append("No nulls anywhere.");

            final String text = report.toString();
            Platform.runLater(() -> {
                textDistribution.setText(text);
                addHistory("Missing values report");
                setStatus("Missing values report ready — see the Distribution tab");
            });
        });
    }

    @FXML
    private void quickScanOutliers() {
        if (statistics == null) return;
        List<String> numeric = statistics.getNumericColumnNames();
        if (numeric.isEmpty()) {
            ShowInformation("No numeric columns", "There is nothing to scan for outliers.");
            return;
        }

        runInBackground("Scanning for outliers", () -> {
            StringBuilder report = new StringBuilder("IQR outlier scan on ").append(table).append("\n\n");
            for (String column : numeric) {
                OutlierService.Report result = outliers.detect(column, OutlierService.Method.IQR,
                        OutlierService.Method.IQR.getDefaultThreshold());
                report.append(String.format(Locale.US, "%-24s %8d outlier(s)%n", column, result.total()));
            }
            final String text = report.toString();
            Platform.runLater(() -> {
                taOutlierSummary.setText(text);
                addHistory("Outlier scan on all columns");
                setStatus("Outlier scan finished — see the Outliers tab");
            });
        });
    }

    // ==== Exportação de dados ====

    @FXML
    private void handleExportCSV() {
        exportRows("CSV", "*.csv", (writer, rows, columns) -> {
            writer.println(columns.stream().map(DataScienceController::escapeCsv)
                    .collect(Collectors.joining(",")));
            for (Map<String, String> row : rows) {
                writer.println(columns.stream()
                        .map(column -> escapeCsv(nullToEmpty(row.get(column))))
                        .collect(Collectors.joining(",")));
            }
        });
    }

    @FXML
    private void handleExportJSON() {
        exportRows("JSON", "*.json", (writer, rows, columns) -> {
            writer.println("[");
            for (int i = 0; i < rows.size(); i++) {
                Map<String, String> row = rows.get(i);
                String body = columns.stream()
                        .map(column -> "\"" + escapeJson(column) + "\": " + jsonValue(row.get(column)))
                        .collect(Collectors.joining(", "));
                writer.print("  {" + body + "}");
                writer.println(i < rows.size() - 1 ? "," : "");
            }
            writer.println("]");
        });
    }

    private interface RowWriter {
        void write(PrintWriter writer, List<Map<String, String>> rows, List<String> columns);
    }

    /**
     * Exporta as linhas da tabela, respeitando o limite definido no painel de modelação.
     * A versão antiga exportava a lista em memória, que estava sempre vazia — o ficheiro
     * saía só com o cabeçalho.
     */
    private void exportRows(String description, String extension, RowWriter rowWriter) {
        if (statistics == null || headers.isEmpty()) {
            ShowInformation("Nothing to export", "Open a table first.");
            return;
        }
        File target = chooseSaveFile(description, extension);
        if (target == null) return;

        final int limit = limitSpinner.getValue();

        runInBackground("Exporting " + description, () -> {
            List<String> columns = new ArrayList<>(headers);
            StringBuilder sql = new StringBuilder("SELECT ");
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) sql.append(", ");
                sql.append(executor.quoteIdentifier(columns.get(i)));
            }
            sql.append(" FROM ").append(executor.quoteIdentifier(table))
                    .append(" LIMIT ").append(limit);

            ArrayList<HashMap<String, String>> fetched = fetcher.fetchRawDataMap(sql.toString());
            List<Map<String, String>> rows = fetched == null ? List.of() : new ArrayList<>(fetched);

            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8))) {
                rowWriter.write(writer, rows, columns);
                Platform.runLater(() -> {
                    setStatus("Exported " + rows.size() + " row(s) to " + target.getName());
                    addHistory("Export " + description + ": " + target.getName());
                });
            } catch (Exception e) {
                Platform.runLater(() -> ShowError("Export failed",
                        "Could not write " + target.getName() + ".", e.getMessage()));
            }
        });
    }

    private File chooseSaveFile(String description, String extension) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(description, extension));
        Window window = MainContainer.getScene() == null ? null : MainContainer.getScene().getWindow();
        return chooser.showSaveDialog(window);
    }

    // ==== Auxiliares ====

    /**
     * Corre trabalho de base de dados fora da thread da UI.
     *
     * <p>Qualquer erro é mostrado em vez de desaparecer num stack trace na consola, que era
     * o que acontecia antes com as tarefas em virtual threads.</p>
     */
    private void runInBackground(String what, ThrowingRunnable work) {
        Thread.ofVirtual().start(() -> {
            try {
                work.run();
            } catch (Exception e) {
                Throwable error = e;
                while (error.getCause() != null) error = error.getCause();
                final String message = error.getMessage() == null ? error.toString() : error.getMessage();
                Platform.runLater(() -> {
                    setStatus(what + " failed");
                    ShowError(what + " failed", "The database rejected the request.", message);
                });
            }
        });
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void setStatus(String message) {
        if (lblStatus != null) lblStatus.setText(message);
    }

    private void addHistory(String entry) {
        lvHistory.getItems().add(entry);
        lvHistory.scrollTo(lvHistory.getItems().size() - 1);
    }

    private void appendLog(TextArea area, String message) {
        if (area != null) area.appendText(message + "\n\n");
    }

    private static ReadOnlyObjectWrapper<String> number(double value) {
        return new ReadOnlyObjectWrapper<>(formatNumber(value));
    }

    static String formatNumber(double value) {
        if (Double.isNaN(value)) return "—";
        if (Double.isInfinite(value)) return value > 0 ? "∞" : "-∞";
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.6g", value);
    }

    private static String csvNumber(double value) {
        return Double.isNaN(value) ? "" : String.format(Locale.US, "%.10g", value);
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String jsonValue(String value) {
        if (value == null || value.equalsIgnoreCase("null")) return "null";
        return '"' + escapeJson(value) + '"';
    }

    private static String nullToEmpty(String value) {
        return (value == null || value.equalsIgnoreCase("null")) ? "" : value;
    }

    private static String shorten(String name) {
        return name.length() <= 12 ? name : name.substring(0, 11) + "…";
    }

    private static double parseDoubleOrDefault(String text, double fallback) {
        if (text == null || text.isBlank()) return fallback;
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes <= 0) return "--";
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = (int) Math.min(units.length - 1, Math.floor(Math.log10(bytes) / 3));
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1000, unit), units[unit]);
    }

    // ==== Linhas das tabelas ====

    /** Uma linha da lista de colunas, à esquerda. */
    public record ColumnRow(String name, String type, long nulls, long totalRows) {
        public double nullPercent() {
            return totalRows == 0 ? 0 : 100.0 * nulls / totalRows;
        }
    }

    /** Uma linha da pré-visualização de imputação. */
    public record PreviewRow(String key, String value) {
    }

    /** Um valor extremo, com o lado em que caiu. */
    public record OutlierRow(String side, double value) {
    }

}
