package com.example.sqlide.Chart;

import com.example.sqlide.AdvancedSearch.AdvancedSearchController;
import com.example.sqlide.Report.ReportController;
import com.example.sqlide.drivers.model.DataBase;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import org.apache.poi.ss.formula.functions.Na;
import org.checkerframework.checker.units.qual.N;
import org.docx4j.wml.Numbering;
import org.xlsx4j.sml.Col;
import javafx.scene.chart.ValueAxis;

import java.awt.event.ActionEvent;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.sqlide.misc.ClipBoard.CopyToBoard;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class ChartController {
    @FXML
    private CategoryAxis CategoryChart;
    @FXML
    private NumberAxis NumberChart;
    @FXML
    private ToggleButton Bar;
    @FXML
    private Button EditButton;
    @FXML
    private TextField titleField, categoryField, numberField;

    @FXML
    private VBox ChartBox;

    @FXML
    private ListView<Label> LabelList;
   // private ListView<String> LabelList;

    private ToggleButton currentChart;

   // private final ObservableMap<String, String> labelMap = FXCollections.observableHashMap();
   private final ObservableList<Label> labelMap = FXCollections.observableArrayList();

   private String table;

   private ArrayList<String> columns;

   private DataBase db;

   private ContextMenu menu;

   public void setTitle(final String title) {
       titleField.setText(title);
   }

    public void setAxis(final String title) {
        categoryField.setText(title);
    }

    public void setNumber(final String title) {
        numberField.setText(title);
    }

   public void setAttributes(final String table, final ArrayList<String> columns, final DataBase db) {
       this.table = table;
       this.columns = columns;
       this.db = db;
   }

   private final ArrayList<Stage> searchStage = new ArrayList<>();
    private final ArrayList<AdvancedSearchController> controllers = new ArrayList<>();

   private int index;

    @FXML
    private void initialize() {
     /*   labelMap.addListener((MapChangeListener<String, String>) change -> {
            if (change.wasAdded()) {
                LabelList.getItems().add(change.getKey() + ": " + change.getValueAdded());
            }
            if (change.wasRemoved()) {
                LabelList.getItems().removeIf(item ->
                        item.startsWith(change.getKey() + ": ")
                );
            }
        }); */

        menu = new ContextMenu();
        MenuItem copy = new MenuItem("Copy");
        copy.setOnAction(_->{
            WritableImage snapshot = ChartBox.getChildren().get(1).snapshot(new SnapshotParameters(), null);
            CopyToBoard(snapshot);
        });
        menu.getItems().add(copy);

        putContextMenu();

        currentChart = Bar;

        LabelList.setItems(labelMap);

        titleField.textProperty().addListener(_->{
            setChartTitle(titleField.getText());
        });
        categoryField.textProperty().addListener(_->{
            setCategoryTitle(categoryField.getText());
        });
        numberField.textProperty().addListener(_->{
            setNumberTitle(numberField.getText());
        });

        labelMap.addListener((ListChangeListener<Label>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    for (Label lbl : change.getAddedSubList()) {
                        String novaCategoria = lbl.Name.get();
                        if (!lbl.Query.get().contains("FROM")) lbl.Query.set(lbl.Query.get()+" FROM " + table + ";");
                        addLabelCategory(novaCategoria);
                        searchStage.add(null);
                        controllers.add(null);
                    }
                }
                // Se houver remoção de labels, você pode também removê-las do eixo:
                if (change.wasRemoved()) {
                    for (Label lbl : change.getRemoved()) {
                        String categoriaRemovida = lbl.Name.get();
                        removeLabelCategory(categoriaRemovida);
                        searchStage.remove(index);
                        controllers.remove(index);
                    }
                }
            }
        });

    }

    private void putContextMenu() {
        ChartBox.getChildren().get(1).setOnContextMenuRequested(e ->
                menu.show(ChartBox.getChildren().get(1), e.getScreenX(), e.getScreenY())
        );

        // Fecha o menu quando clicar fora
        ChartBox.getChildren().get(1).setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.SECONDARY) {
                menu.hide();
            }
        });
    }

    @FXML
    private void setChart(javafx.event.ActionEvent event) {
        final ToggleButton button = (ToggleButton) event.getSource();

        if (!button.getId().equals(currentChart.getId())) {
            currentChart.setSelected(false);
            switch (button.getId()) {
                case "Pie":
                    createPieChart();
                    break;
                case "Line":
                    createLinesChart();
                    break;
                case "Area":
                    createAreaChart();
                    break;
                case "Bar":
                    createBarChart();
                    break;
                case "Bubble":
                    createBubbleChart();
                    break;
                case "Scatter":
                    createScatterChart();
                    break;
                case "Stacked":
                    createStackedChart();
                    break;
                case "Stacked_bar":
                    createStackBarChart();
                    break;
            }
            currentChart = button;
            putContextMenu();
            VBox.setVgrow(ChartBox.getChildren().get(1), Priority.ALWAYS);
        } else button.setSelected(true);

    }

    private void createPieChart() {
        final PieChart chart = new PieChart();
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void createLinesChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();

        // Copie as configurações dos eixos originais
        copyAxisProperties(CategoryChart, xAxis);
        copyAxisProperties(NumberChart, yAxis);
        final LineChart<String, Number> chart = new LineChart<String, Number>(xAxis, yAxis);
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void copyAxisProperties(Axis<?> source, Axis<?> target) {
        target.setLabel(source.getLabel());
        target.setTickLabelFill(source.getTickLabelFill());
        target.setTickLabelFont(source.getTickLabelFont());
        target.setTickLabelGap(source.getTickLabelGap());
        target.setTickLabelRotation(source.getTickLabelRotation());
        target.setTickLength(source.getTickLength());
        target.setTickMarkVisible(source.isTickMarkVisible());
        // Copie outras propriedades conforme necessário
    }

    private void createAreaChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        copyAxisProperties(CategoryChart, xAxis);
        copyAxisProperties(NumberChart, yAxis);
        final AreaChart<String, Number> chart = new AreaChart<String, Number>(CategoryChart, yAxis);
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void createBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        copyAxisProperties(CategoryChart, xAxis);
        copyAxisProperties(NumberChart, yAxis);
        final BarChart<String, Number> chart = new BarChart<String, Number>(CategoryChart, NumberChart);
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void createBubbleChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        copyAxisProperties(CategoryChart, xAxis);
        copyAxisProperties(NumberChart, yAxis);
        final BubbleChart<Number, Number> chart = new BubbleChart<>(yAxis, NumberChart);
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void createScatterChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        copyAxisProperties(CategoryChart, xAxis);
        copyAxisProperties(NumberChart, yAxis);
        final ScatterChart<String, Number> chart = new ScatterChart<>(CategoryChart, NumberChart);
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void createStackedChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        copyAxisProperties(CategoryChart, xAxis);
        copyAxisProperties(NumberChart, yAxis);
        final StackedAreaChart<String, Number> chart = new StackedAreaChart<>(CategoryChart, NumberChart);
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void createStackBarChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        copyAxisProperties(CategoryChart, xAxis);
        copyAxisProperties(NumberChart, yAxis);
        final StackedBarChart<String, Number> chart = new StackedBarChart<>(CategoryChart, NumberChart);
        ChartBox.getChildren().set(1, chart);
        setChartTitle(titleField.getText());
        setCategoryTitle(categoryField.getText());
        setNumberTitle(numberField.getText());
    }

    private void setChartTitle(final String title) {
        final Object chartAbstract = ChartBox.getChildren().get(1);
        switch (chartAbstract) {
            case BarChart<?, ?> chart -> chart.setTitle(title);
            case LineChart<?, ?> chart -> chart.setTitle(title);
            case PieChart chart -> chart.setTitle(title);
            case AreaChart<?, ?> chart -> chart.setTitle(title);
            case ScatterChart<?, ?> chart -> chart.setTitle(title);
            case BubbleChart<?, ?> chart -> chart.setTitle(title);
            case StackedBarChart<?, ?> chart -> chart.setTitle(title);
            case StackedAreaChart<?, ?> chart -> chart.setTitle(title);
            default -> throw new IllegalStateException("Unexpected value: " + chartAbstract);
        }
    }

    private void setData(final XYChart.Series series) {
        final Object chartAbstract = ChartBox.getChildren().get(1);
        switch (chartAbstract) {
            case BarChart<?, ?> chart -> chart.getData().add(series);
            case LineChart<?, ?> chart -> chart.getData().add(series);
           // case PieChart chart -> chart.getData().add(series);
            case AreaChart<?, ?> chart -> chart.getData().add(series);
            case ScatterChart<?, ?> chart -> chart.getData().add(series);
            case BubbleChart<?, ?> chart -> chart.getData().add(convertSeries(series));
            case StackedBarChart<?, ?> chart -> chart.getData().add(series);
            case StackedAreaChart<?, ?> chart -> chart.getData().add(series);
            default -> throw new IllegalStateException("Unexpected value: " + chartAbstract);
        }
    }

    private void setData(final ObservableList<PieChart.Data> series) {
        final PieChart chartAbstract = (PieChart) ChartBox.getChildren().get(1);
        chartAbstract.setData(series);
    }

    private void refreshData() {
        final Object chartAbstract = ChartBox.getChildren().get(1);
        switch (chartAbstract) {
            case BarChart<?, ?> chart -> chart.getData().clear();
            case LineChart<?, ?> chart -> chart.getData().clear();
            case PieChart chart -> chart.getData().clear();
            case AreaChart<?, ?> chart -> chart.getData().clear();
            case ScatterChart<?, ?> chart -> chart.getData().clear();
            case BubbleChart<?, ?> chart -> chart.getData().clear();
            case StackedBarChart<?, ?> chart -> chart.getData().clear();
            case StackedAreaChart<?, ?> chart -> chart.getData().clear();
            default -> throw new IllegalStateException("Unexpected value: " + chartAbstract);
        }
    }

    private void setCategoryTitle(final String title) {
        Object chartNode = ChartBox.getChildren().get(1);
        if (chartNode instanceof XYChart<?, ?> xyChart) {
            // Configura título do eixo X
            Axis<?> xAxis = xyChart.getXAxis();
            xAxis.setLabel(title);
        }
    }

    private void addLabelCategory(final String novaCategoria) {
        Object chartNode = ChartBox.getChildren().get(1);

        if (chartNode instanceof XYChart<?, ?> xyChart) {
            Axis<?> xAxis = xyChart.getXAxis();

            // Só faz sentido em um eixo de categoria:
            if (xAxis instanceof CategoryAxis catAxis) {
                ObservableList<String> categorias = catAxis.getCategories();

                // Verifica se já existe:
                if (!categorias.contains(novaCategoria)) {
                    categorias.add(novaCategoria);
                }
            }
            // Em PieChart, não há CategoryAxis—cada fatia é individual.
        }
    }

    private void removeLabelCategory(final String categoriaRemovida) {
        Object chartNode = ChartBox.getChildren().get(1);

        if (chartNode instanceof XYChart<?, ?> xyChart) {
            Axis<?> xAxis = xyChart.getXAxis();

            if (xAxis instanceof CategoryAxis catAxis) {
                catAxis.getCategories().remove(categoriaRemovida);
            }
        }
    }


    private void setNumberTitle(final String title) {
        Object chartNode = ChartBox.getChildren().get(1);
        if (chartNode instanceof XYChart<?, ?> xyChart) {
            Axis<?> yAxis = xyChart.getYAxis();
            yAxis.setLabel(title);
        }
    }

    @FXML
    private void loadLabelAdder() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Chart/AddLabel.fxml"));
            Parent root = loader.load();

            ChartLabel dialogController = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Configure Report");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogController.setList(labelMap, columns);
          /*  if (generateReportButton != null && generateReportButton.getScene() != null) {
                dialogStage.initOwner(generateReportButton.getScene().getWindow());
            } */

            dialogStage.setScene(new Scene(root));
            dialogStage.showAndWait();



        } catch (IOException e) {
            ShowError("Load Error", "Could not load report configuration dialog.", e.getMessage());
        }
    }

    @FXML
    private void openAdvancedSearch() {
        ObservableList<Integer> ref = LabelList.getSelectionModel().getSelectedIndices();
        if (!ref.isEmpty()) {
            final int val = ref.getFirst();
            if (searchStage.get(val) != null) {
                searchStage.get(val).show();
            } else loadAdvancedSearch(val);
        } else ShowInformation("No selected", "You need to select a row of Table to edit.");
    }

    private void loadAdvancedSearch(final int val) {
                try {
                    // Carrega o arquivo FXML
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedSearchStage.fxml"));
                    //    VBox miniWindow = loader.load();
                    Parent root = loader.load();

                    HashMap<String, ArrayList<String>> col = new HashMap<>();
                    col.put(table, columns);

                    AdvancedSearchController secondaryController = loader.getController();

                    // Criar um novo Stage para a subjanela
                    Stage subStage = new Stage();
                    subStage.setTitle("Create Column");
                    subStage.setScene(new Scene(root));
                    secondaryController.setCode("SELECT");
                    secondaryController.setTable(table);
                    secondaryController.removeLeft();
                    secondaryController.setColumns(col);
               //     secondaryController.setSelectedColumn(labelMap.get(val).Func.get() + "(" + labelMap.get(val).Column.get() + ")");
                    secondaryController.setSelectedColumn(labelMap.get(val).Query.get());
                    secondaryController.setQuery(labelMap.get(val).Query.get());
                    secondaryController.setStage(subStage);
                    //  secondaryController.initWin(ColumnsNames, subStage, this);

                    subStage.showingProperty().addListener(_ -> {
                        if (secondaryController.isClosedByUser()) {
                            labelMap.get(val).Query.set(secondaryController.getQuery());
                        }
                    });

                    // Opcional: definir a modalidade da subjanela
                    subStage.initModality(Modality.APPLICATION_MODAL);

                    searchStage.add(val, subStage);
                    controllers.add(val, secondaryController);

                    // Mostrar a subjanela
                    subStage.show();
                } catch (Exception e) {
                    ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
                }
            }

    @FXML
    private void remove() {
        ObservableList<Integer> ref = LabelList.getSelectionModel().getSelectedIndices();
        if (!ref.isEmpty()) {
            final int val = ref.getFirst();
            index = val;
            LabelList.getItems().remove(val);
        } else ShowInformation("No selected", "You need to select a row of Table to remove.");
    }

    @FXML
    private void loadLabelEdit() {
        ObservableList<Integer> ref = LabelList.getSelectionModel().getSelectedIndices();
        if (!ref.isEmpty()) {
            final int val = ref.getFirst();
           // val = val.substring(0, val.indexOf(":"));
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Chart/AddLabel.fxml"));
                Parent root = loader.load();

                ChartLabel dialogController = loader.getController();

                Stage dialogStage = new Stage();
                dialogStage.setTitle("Configure Report");
                dialogStage.initModality(Modality.APPLICATION_MODAL);
                dialogController.setEditList(labelMap, val, columns);
          /*  if (generateReportButton != null && generateReportButton.getScene() != null) {
                dialogStage.initOwner(generateReportButton.getScene().getWindow());
            } */

                dialogStage.setScene(new Scene(root));
                dialogStage.showAndWait();

                LabelList.refresh();

                if (controllers.get(val) != null) {
                    controllers.get(val).setSelectedColumn(labelMap.get(val).Func.get() + "(" + labelMap.get(val).Column.get() + ")");
                }

            } catch (IOException e) {
                ShowError("Load Error", "Could not load report configuration dialog.", e.getMessage());
            }
        } else ShowInformation("No selected", "You need to select a row of Table to edit.");
    }

    @FXML
    private void create() {
        if (labelMap.isEmpty()) {
            ShowInformation("No label", "No labels to generate data.");
            return;
        }

        refreshData();

        Thread.ofVirtual().start(()->{

            if (ChartBox.getChildren().get(1) instanceof PieChart) {
                setDataPie();
            } else setData();

        });

    }

    private XYChart.Series<Double, Double> convertSeries(XYChart.Series<String, Double> originalSeries) {
        XYChart.Series<Double, Double> newSeries = new XYChart.Series<>();
        newSeries.setName(originalSeries.getName()); // Mantém o nome da série

        for (XYChart.Data<String, Double> data : originalSeries.getData()) {
            try {
                // Converte o valor X de String para Double
                Double xValue = Double.parseDouble(data.getXValue());
                Double yValue = data.getYValue();

                newSeries.getData().add(new XYChart.Data<>(xValue, yValue));
            } catch (NumberFormatException e) {
                System.err.println("Erro na conversão: " + data.getXValue() + " não é um número válido");
                Double xValue = (double) 0;
                Double yValue = data.getYValue();
                newSeries.getData().add(new XYChart.Data<>(xValue, yValue));
                // Opções alternativas:
                // 1. Usar um valor padrão como 0L
                // 2. Ignorar o ponto de dados
                // 3. Usar o índice como valor X
            }
        }

        return newSeries;
    }

    private void setData() {
        final ArrayList<Label> LabelCopy = new ArrayList<>(labelMap);

        while (!LabelCopy.isEmpty()) {
            Label label = LabelCopy.getFirst();
            XYChart.Series<String, Double> series = new XYChart.Series<String, Double>();
            series.setName(label.Func.get() + " of " + label.Name.get());

            final List<Label> reduced = labelMap.stream().filter(lab -> lab.Name.get().equals(label.Name.get())).toList();

            for (final Label subLabel : reduced) {
                final ArrayList<Double> subData = db.Fetcher().fetchDataMap(subLabel.Query.get());
                series.getData().add(new XYChart.Data<String, Double>(subLabel.Category.get(), subData.getFirst()));
            }

            LabelCopy.removeAll(reduced);


            Platform.runLater(()->setData(series));
        }
    }

    private void setDataPie() {

        final ObservableList<PieChart.Data> Data = FXCollections.observableArrayList();

        for (final Label label : labelMap) {
            final ArrayList<Double> subData = db.Fetcher().fetchDataMap(label.Query.get());
            Data.add(new PieChart.Data(label.Category.get(), subData.getFirst()));
        }
        Platform.runLater(()->setData(Data));
    }

    public void setLabels(final ArrayList<HashMap<String, String>> labels) {
        for (final HashMap<String, String> label : labels) {
            labelMap.add(new Label(label));
        }
    }

    static class Label {
        public StringProperty Func = new SimpleStringProperty(), Name = new SimpleStringProperty(), Query = new SimpleStringProperty(), Column = new SimpleStringProperty(), Category = new SimpleStringProperty();

        public Label(final String Name, final String Category, final String Func, final String Column, final String Query) {
            this.Func.set(Func);
            this.Name.set(Name);
            this.Category.set(Category);
            this.Query.set(Query);
            this.Column.set(Column);
        }

        public Label(final HashMap<String, String> label) {
            this.Func.set(label.get("func"));
            this.Name.set(label.get("group"));
            this.Category.set(label.get("category"));
            this.Query.set(label.get("query"));
            this.Column.set(label.get("column"));
        }

        @Override
        public String toString() {
            return Name.get() + ":" + Func.get()+":"+Column.get();
        }

    }

}
