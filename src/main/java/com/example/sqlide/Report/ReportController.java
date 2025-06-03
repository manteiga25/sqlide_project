package com.example.sqlide.Report;

import com.example.sqlide.AdvancedSearch.AdvancedSearchController;
import com.example.sqlide.DataForDB;
import com.example.sqlide.drivers.model.DataBase;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.checkerframework.checker.units.qual.C;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class ReportController {

    @FXML
    private Pane TempPane;
    @FXML
    private VBox rootVBox, StyleBox; // Optional: if needed for dynamic sizing or access

    @FXML
    private TextField reportTitleField;

    @FXML
    private VBox columnsVBox;

    private String table;

    private HashMap<String, ArrayList<String>> ColumnsNames;

    private AdvancedSearchController secondaryController;

    private List<String> allAvailableColumns;
    private final List<JFXCheckBox> columnCheckBoxes = new ArrayList<>();
    private String sourceQuery;
    //   private mainController mainCtrlRef; // To store reference to mainController
    private Stage dialogStage;
    private DataBase db;

    // UI Controls for Styling
    private ComboBox<String> titleFontFamilyComboBox;
    private Spinner<Double> titleFontSizeSpinner;
    private ColorPicker titleColorPicker;

    private ComboBox<String> headerFontFamilyComboBox;
    private Spinner<Double> headerFontSizeSpinner;
    private ColorPicker headerTextColorPicker;
    private ColorPicker headerBackgroundColorPicker;

    private ComboBox<String> dataFontFamilyComboBox;
    private Spinner<Double> dataFontSizeSpinner;
    private ColorPicker dataTextColorPicker;

    private ColorPicker alternatingRowBackgroundColorPicker;

    private ComboBox<String> pageNumberFontFamilyComboBox;
    private Spinner<Double> pageNumberFontSizeSpinner;
    private ColorPicker pageNumberColorPicker;

    private Spinner<Double> pageMarginSpinner;
    private Spinner<Double> cellPaddingSpinner;

    private ImageView pdfPreviewImageView;
    private PdfPreviewService pdfPreviewService;



    public void initializeDialog(DataBase db, Stage stage) {
        this.db = db;
        this.dialogStage = stage;
        reportTitleField.setText("Report");
        this.pdfPreviewService = new PdfPreviewService();
        VBox styleControlsVBox = createStyleControlsNode();
        styleControlsVBox.setStyle("-fx-background-color: #2C2C2C;");
        ScrollPane scrollableStyleControls = new ScrollPane(styleControlsVBox);
        scrollableStyleControls.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/ScrollHbarStyle.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/ComboboxModern.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/SpinnerStyle.css")).toExternalForm(),
                Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());
        scrollableStyleControls.setStyle("-fx-background-color: #2C2C2C;");
        scrollableStyleControls.setFitToWidth(true);
        scrollableStyleControls.setPrefHeight(200); // Adjust as needed, or let it grow

        final JFXButton pageViwerButton = new JFXButton("View Page Style");
        pageViwerButton.setOnAction(_->loadPage());
        pageViwerButton.setTextFill(Color.WHITE);

        final Label styleLabel = new Label("Report Style:");
        styleLabel.setTextFill(Color.WHITE);
        styleLabel.setFont(Font.font(20));
        styleLabel.setAlignment(Pos.BASELINE_LEFT);
        styleLabel.setGraphic(pageViwerButton);
        styleLabel.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        pdfPreviewImageView = new ImageView();
       // pdfPreviewImageView.setFitWidth(350); // Adjust as needed
       // pdfPreviewImageView.setFitHeight(400); // Adjust as needed
     //   pdfPreviewImageView.maxWidth(Double.MAX_VALUE);
       // pdfPreviewImageView.maxHeight(Double.MAX_VALUE);
        pdfPreviewImageView.setPreserveRatio(true);
    //    pdfPreviewImageView.setSmooth(true);
        pdfPreviewImageView.setStyle("-fx-border-color: grey; -fx-border-width: 1; -fx-background-color: #E0E0E0;"); // Light grey background

       /* int tempPaneIndex = rootVBox.getChildren().indexOf(TempPane);
        if (tempPaneIndex != -1) {
            rootVBox.getChildren().add(tempPaneIndex-1, styleLabel);
            rootVBox.getChildren().add(tempPaneIndex, scrollableStyleControls);
        } else {
            int lastElementIndex = rootVBox.getChildren().size() - 1;
            if (lastElementIndex > 0) {
                rootVBox.getChildren().add(lastElementIndex, scrollableStyleControls);
            } else {
                rootVBox.getChildren().add(scrollableStyleControls);
            }
        } */
       // HBox.setHgrow(pdfPreviewImageView, Priority.ALWAYS);
       // Container.getChildren().add(pdfPreviewImageView);
        StyleBox.getChildren().addFirst(styleLabel);
        StyleBox.getChildren().add(1, scrollableStyleControls);
     //   Platform.runLater(this::updatePreview);
    }

    private VBox createStyleControlsNode() {
        VBox mainStyleVBox = new VBox(15); // Spacing between sections
        mainStyleVBox.setStyle("-fx-padding: 10;"); // Padding around the style controls

        ReportStyleConfig defaults = new ReportStyleConfig(); // To get default values

        // Font names for ComboBoxes
        List<String> fontNames = Arrays.stream(Standard14Fonts.FontName.values())
                .map(Enum::name)
                .collect(Collectors.toList());

        // Title Styling
        titleFontFamilyComboBox = createFontComboBox(fontNames, defaults.getTitleFontFamily());
       // titleFontFamilyComboBox.valueProperty().addListener(_->updatePreview());
        titleFontSizeSpinner = createFontSizeSpinner(defaults.getTitleFontSize(), 8, 30);
        //titleFontSizeSpinner.valueProperty().addListener(_->updatePreview());
        titleColorPicker = createColorPicker(Color.BLACK);
        //titleColorPicker.valueProperty().addListener(_->updatePreview());
        mainStyleVBox.getChildren().add(createStyledSection("Title Styling",
                createControlGroup("Font:", titleFontFamilyComboBox),
                createControlGroup("Size:", titleFontSizeSpinner),
                createControlGroup("Color:", titleColorPicker)
        ));

        // Header Styling
        headerFontFamilyComboBox = createFontComboBox(fontNames, defaults.getHeaderFontFamily());
    //    headerFontFamilyComboBox.valueProperty().addListener(_->updatePreview());
        headerFontSizeSpinner = createFontSizeSpinner(defaults.getHeaderFontSize(), 7, 20);
      //  headerFontSizeSpinner.valueProperty().addListener(_->updatePreview());
        headerTextColorPicker = createColorPicker(Color.BLACK);
      //  headerTextColorPicker.valueProperty().addListener(_->updatePreview());
        headerBackgroundColorPicker = createColorPicker(Color.WHITE);
      //  headerBackgroundColorPicker.valueProperty().addListener(_->updatePreview());
        mainStyleVBox.getChildren().add(createStyledSection("Header Styling",
                createControlGroup("Font:", headerFontFamilyComboBox),
                createControlGroup("Size:", headerFontSizeSpinner),
                createControlGroup("Text Color:", headerTextColorPicker),
                createControlGroup("Background Color:", headerBackgroundColorPicker)
        ));

        // Data Cell Styling
        dataFontFamilyComboBox = createFontComboBox(fontNames, defaults.getDataFontFamily());
   //     dataFontFamilyComboBox.valueProperty().addListener(_->updatePreview());
        dataFontSizeSpinner = createFontSizeSpinner(defaults.getDataFontSize(), 6, 16);
     //   dataFontSizeSpinner.valueProperty().addListener(_->updatePreview());
        dataTextColorPicker = createColorPicker(Color.BLACK);
       // dataTextColorPicker.valueProperty().addListener(_->updatePreview());
        mainStyleVBox.getChildren().add(createStyledSection("Data Cell Styling",
                createControlGroup("Font:", dataFontFamilyComboBox),
                createControlGroup("Size:", dataFontSizeSpinner),
                createControlGroup("Text Color:", dataTextColorPicker)
        ));

        // Row Styling
        alternatingRowBackgroundColorPicker = createColorPicker(Color.GRAY);
    //    alternatingRowBackgroundColorPicker.valueProperty().addListener(_->updatePreview());
        mainStyleVBox.getChildren().add(createStyledSection("Row Styling",
                createControlGroup("Alt. Row Background:", alternatingRowBackgroundColorPicker)
        ));

        // Page Number Styling
        pageNumberFontFamilyComboBox = createFontComboBox(fontNames, defaults.getPageNumberFontFamily());
    //    pageNumberFontFamilyComboBox.valueProperty().addListener(_->updatePreview());
        pageNumberFontSizeSpinner = createFontSizeSpinner(defaults.getPageNumberFontSize(), 6, 12);
      //  pageNumberFontSizeSpinner.valueProperty().addListener(_->updatePreview());
        pageNumberColorPicker = createColorPicker(Color.BLACK);
        //pageNumberColorPicker.valueProperty().addListener(_->updatePreview());
        mainStyleVBox.getChildren().add(createStyledSection("Page Number Styling",
                createControlGroup("Font:", pageNumberFontFamilyComboBox),
                createControlGroup("Size:", pageNumberFontSizeSpinner),
                createControlGroup("Color:", pageNumberColorPicker)
        ));

        // Layout Configuration
        pageMarginSpinner = createLayoutSpinner(defaults.getPageMargin(), 10, 100);
    //    pageMarginSpinner.valueProperty().addListener(_->updatePreview());
        cellPaddingSpinner = createLayoutSpinner(defaults.getCellPadding(), 0, 20);
      //  cellPaddingSpinner.valueProperty().addListener(_->updatePreview());
        mainStyleVBox.getChildren().add(createStyledSection("Layout Configuration",
                createControlGroup("Page Margin:", pageMarginSpinner),
                createControlGroup("Cell Padding:", cellPaddingSpinner)
        ));

        return mainStyleVBox;
    }

    private VBox createStyledSection(String title, Node... children) {
        VBox sectionVBox = new VBox(5);
        Label sectionTitle = new Label(title);
        sectionTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: white;");
        sectionVBox.getChildren().add(sectionTitle);
        for (Node child : children) {
            sectionVBox.getChildren().add(child);
        }
        return sectionVBox;
    }

    private HBox createControlGroup(String labelText, Node control) {
        HBox hbox = new HBox(10);
        Label label = new Label(labelText);
        label.setTextFill(Color.WHITE);
        label.setMinWidth(150); // Align controls
        control.setStyle("-fx-pref-width: 200;"); // Set preferred width for controls
        hbox.getChildren().addAll(label, control);
        return hbox;
    }

    private ComboBox<String> createFontComboBox(List<String> fontNames, String defaultFont) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().addAll(fontNames);
        comboBox.setValue(defaultFont);
        return comboBox;
    }

    private Spinner<Double> createFontSizeSpinner(double defaultValue, double min, double max) {
        Spinner<Double> spinner = new Spinner<>(min, max, defaultValue, 0.5); // Step 0.5
        spinner.setEditable(true);
        return spinner;
    }

    private Spinner<Double> createLayoutSpinner(double defaultValue, double min, double max) {
        Spinner<Double> spinner = new Spinner<>(min, max, defaultValue, 1.0); // Step 1.0
        spinner.setEditable(true);
        return spinner;
    }

    private ColorPicker createColorPicker(Color defaultColor) {
        ColorPicker colorPicker = new ColorPicker(defaultColor);
        return colorPicker;
    }

    private java.awt.Color convertToAwtColor(javafx.scene.paint.Color fxColor) {
        if (fxColor == null) return null; // Handle null for no background color
        return new java.awt.Color((float) fxColor.getRed(),
                (float) fxColor.getGreen(),
                (float) fxColor.getBlue(),
                (float) fxColor.getOpacity());
    }

    private void updatePreview() {
        if (pdfPreviewService == null || pdfPreviewImageView == null) {
            return; // Not initialized yet
        }

        ReportStyleConfig styleConfig = new ReportStyleConfig();
        // Populate styleConfig from UI controls
        styleConfig.setTitleFontFamily(titleFontFamilyComboBox.getValue());
        styleConfig.setTitleFontSize(titleFontSizeSpinner.getValue().floatValue());
        styleConfig.setTitleColor(convertToAwtColor(titleColorPicker.getValue()));

        styleConfig.setHeaderFontFamily(headerFontFamilyComboBox.getValue());
        styleConfig.setHeaderFontSize(headerFontSizeSpinner.getValue().floatValue());
        styleConfig.setHeaderTextColor(convertToAwtColor(headerTextColorPicker.getValue()));
        styleConfig.setHeaderBackgroundColor(convertToAwtColor(headerBackgroundColorPicker.getValue()));

        styleConfig.setDataFontFamily(dataFontFamilyComboBox.getValue());
        styleConfig.setDataFontSize(dataFontSizeSpinner.getValue().floatValue());
        styleConfig.setDataTextColor(convertToAwtColor(dataTextColorPicker.getValue()));

        styleConfig.setAlternatingRowBackgroundColor(convertToAwtColor(alternatingRowBackgroundColorPicker.getValue()));

        styleConfig.setPageNumberFontFamily(pageNumberFontFamilyComboBox.getValue());
        styleConfig.setPageNumberFontSize(pageNumberFontSizeSpinner.getValue().floatValue());
        styleConfig.setPageNumberColor(convertToAwtColor(pageNumberColorPicker.getValue()));

        styleConfig.setPageMargin(pageMarginSpinner.getValue().floatValue());
        styleConfig.setCellPadding(cellPaddingSpinner.getValue().floatValue());

        String previewTitle = reportTitleField.getText().isEmpty() ? "Sample Title" : reportTitleField.getText();

        // Use actual selected columns if available, otherwise fallback to sample
        List<String> currentSelectedColumns = null;
        if (secondaryController != null && secondaryController.getSelected() != null && !secondaryController.getSelected().isEmpty()) {
            currentSelectedColumns = new ArrayList<>(secondaryController.getSelected());
        } else {
            currentSelectedColumns = List.of("Column 1", "Column 2", "Column 3");
        }
        if (currentSelectedColumns.isEmpty()) { // Ensure it's not empty for preview generation
            currentSelectedColumns = List.of("Preview Column");
        }


        List<List<String>> sampleRows = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            List<String> row = new ArrayList<>();
            for (int j = 1; j <= currentSelectedColumns.size(); j++) {
                row.add("Data " + i + (char)('A' + j - 1));
            }
            sampleRows.add(row);
        }

        ReportData sampleReportData = new ReportData(previewTitle, currentSelectedColumns, sampleRows, styleConfig);

        // Generate and display the image
        // Consider running this on a background thread if it becomes slow
        Image previewImage = pdfPreviewService.generatePreviewImage(
                sampleReportData,
                (float) 800,
                (float) 400
        );

        if (previewImage != null) {
            pdfPreviewImageView.setImage(previewImage);
        } else {
            // Optionally, set a placeholder or error image
            pdfPreviewImageView.setImage(null); // Clears the image
            // You could create a simple placeholder graphic or text as an image here
        }

       // loadPage();

    }

    @FXML
    private void loadPage() {

        updatePreview();

        Pane container = new Pane(pdfPreviewImageView);
        pdfPreviewImageView.fitWidthProperty().bind(container.widthProperty());
        pdfPreviewImageView.fitHeightProperty().bind(container.heightProperty());

        Stage pageViewer = new Stage();
        pageViewer.setTitle("Page");
        pageViewer.initModality(Modality.APPLICATION_MODAL);

        pageViewer.setScene(new Scene(container));
        pageViewer.show();
    }

    public void setTable(final String table, final HashMap<String, ArrayList<String>> ColumnNames) {
        this.table = table;
        this.ColumnsNames = ColumnNames;
        loadAdvancedQuery();
    }

    private void loadAdvancedQuery() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedSearchStage.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            secondaryController.setCode("SELECT");
            secondaryController.setTable(table);
            secondaryController.setColumns(ColumnsNames);
            //  secondaryController.setStage(subStage);
            secondaryController.removeBottomContainer();

            VBox.setVgrow(root, Priority.ALWAYS);
            rootVBox.getChildren().set(rootVBox.getChildren().indexOf(TempPane), root);

          /*  subStage.showingProperty().addListener(_->{
                if (secondaryController.isClosedByUser()) {
                        if (secondaryController.getQuery().toUpperCase().contains("SELECT")) {
                            codeField.setText(secondaryController.getQuery());
                            AdvancedSearchButton.fire();
                        } else {
                            ShowInformation("Invalid query", "The query " + secondaryController.getQuery() + " is invalid.");
                        }
                }
            }); */

            // Opcional: definir a modalidade da subjanela
            // subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            // subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    private void handleGeneratePdf() {
        // 1. Retrieve UI selections
        String title = getReportTitle();
        List<String> selectedReportColumns = secondaryController.getSelected();

        if (title.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Report title cannot be empty.");
            return;
        }
        if (selectedReportColumns.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", "Please select at least one column for the report.");
            return;
        }

        // 2. Check mainCtrlRef and sourceQuery
      /*  if (mainCtrlRef == null || sourceQuery == null || sourceQuery.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Internal Error", "Critical application state missing (controller or query).");
            return;
        } */

        // 3. Fetch Data using mainCtrlRef
        DataBase currentDB;
        ArrayList<DataForDB> fetchedData;
        try {
         /*   if (mainCtrlRef.currentDB == null || mainCtrlRef.currentDB.get() == null || mainCtrlRef.DatabaseOpened == null) {
                showAlert(Alert.AlertType.ERROR, "Database Error", "No active database selected or available.");
                return;
            } */
            //    currentDB = mainCtrlRef.DatabaseOpened.get(mainCtrlRef.currentDB.get());
            if (db == null) {
                showAlert(Alert.AlertType.ERROR, "Database Error", "Could not retrieve the current database connection.");
                return;
            }
            // Assuming null for tableName is acceptable if query is self-contained.
            fetchedData = db.fetchData(secondaryController.getQuery(), new ArrayList<>(), null);
        } catch (Exception e) { // Catching general Exception as specific SQLException is not guaranteed by fetchData signature
            showAlert(Alert.AlertType.ERROR, "Data Fetch Error", "Failed to fetch data for the report: " + e.getMessage());
            e.printStackTrace(); // For logging
            return;
        }

        if (fetchedData == null) { // fetchData might return null on error or no data
            showAlert(Alert.AlertType.INFORMATION, "No Data", "No data returned for the given query.");
            return; // Or proceed to generate an empty report if desired
        }

        // 4. Prepare Data for ReportData
        List<List<String>> dataRows = new ArrayList<>();
        for (DataForDB dataForDB : fetchedData) {
            List<String> row = new ArrayList<>();
            for (String columnName : selectedReportColumns) {
                row.add(dataForDB.GetData(columnName));
            }
            dataRows.add(row);
        }

        // 5. Prompt for File Save Location
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Report As");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf"));
        String defaultFileName = title.replaceAll("[^a-zA-Z0-9\\-_]", "_") + ".pdf"; // Sanitize title for filename
        fileChooser.setInitialFileName(defaultFileName);

        File file = fileChooser.showSaveDialog(dialogStage);
        if (file == null) { // User cancelled
            return;
        }

        // 6. Generate PDF
        ReportService reportService = new ReportService();

        ReportStyleConfig styleConfig = new ReportStyleConfig();
        styleConfig.setTitleFontFamily(titleFontFamilyComboBox.getValue());
        styleConfig.setTitleFontSize(titleFontSizeSpinner.getValue().floatValue());
        styleConfig.setTitleColor(convertToAwtColor(titleColorPicker.getValue()));

        styleConfig.setHeaderFontFamily(headerFontFamilyComboBox.getValue());
        styleConfig.setHeaderFontSize(headerFontSizeSpinner.getValue().floatValue());
        styleConfig.setHeaderTextColor(convertToAwtColor(headerTextColorPicker.getValue()));
        styleConfig.setHeaderBackgroundColor(convertToAwtColor(headerBackgroundColorPicker.getValue()));

        styleConfig.setDataFontFamily(dataFontFamilyComboBox.getValue());
        styleConfig.setDataFontSize(dataFontSizeSpinner.getValue().floatValue());
        styleConfig.setDataTextColor(convertToAwtColor(dataTextColorPicker.getValue()));

        styleConfig.setAlternatingRowBackgroundColor(convertToAwtColor(alternatingRowBackgroundColorPicker.getValue()));

        styleConfig.setPageNumberFontFamily(pageNumberFontFamilyComboBox.getValue());
        styleConfig.setPageNumberFontSize(pageNumberFontSizeSpinner.getValue().floatValue());
        styleConfig.setPageNumberColor(convertToAwtColor(pageNumberColorPicker.getValue()));

        styleConfig.setPageMargin(pageMarginSpinner.getValue().floatValue());
        styleConfig.setCellPadding(cellPaddingSpinner.getValue().floatValue());

        ReportData reportData = new ReportData(title, selectedReportColumns, dataRows, styleConfig);
        Thread.ofVirtual().start(()-> {
            try {
                reportService.generatePdfReport(reportData, file.getAbsolutePath());
            } catch (IOException e) {
                showAlert(Alert.AlertType.ERROR, "PDF Generation Error", "Failed to generate PDF report: " + e.getMessage());
                e.printStackTrace(); // For logging
            }
        });

        // 7. Show Success Message and Close Dialog
        showAlert(Alert.AlertType.INFORMATION, "Success", "Report generated successfully at:\n" + file.getAbsolutePath());
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (dialogStage != null && dialogStage.getScene() != null && dialogStage.getScene().getWindow() != null) {
            alert.initOwner(dialogStage.getScene().getWindow()); // Ensure alert is modal to the dialog
        }
        alert.showAndWait();
    }

    @FXML
    private void handleCancel() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public String getReportTitle() {
        return reportTitleField.getText();
    }

    public List<String> getSelectedReportColumns() {
        return columnCheckBoxes.stream()
                .filter(JFXCheckBox::isSelected)
                .map(JFXCheckBox::getText)
                .collect(Collectors.toList());
    }

    // Getter for sourceQuery if needed by external callers before generation
    public String getSourceQuery() {
        return sourceQuery;
    }

}
