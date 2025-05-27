package com.example.sqlide.Report;

import com.example.sqlide.AdvancedSearch.AdvancedSearchController;
import com.example.sqlide.DataForDB;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.mainController;
import com.jfoenix.controls.JFXCheckBox;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class ReportController {

    @FXML
    private Pane TempPane;
    @FXML
    private VBox rootVBox; // Optional: if needed for dynamic sizing or access

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

    public void initializeDialog(DataBase db, Stage stage) {
        this.db = db;
   //     this.mainCtrlRef = mainCtrl; // Store mainController reference
        this.dialogStage = stage;

       // columnsVBox.getChildren().clear(); // Clear any previous checkboxes

        // Set default title if needed
        reportTitleField.setText("Report");
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
        ReportData reportData = new ReportData(title, selectedReportColumns, dataRows);
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
