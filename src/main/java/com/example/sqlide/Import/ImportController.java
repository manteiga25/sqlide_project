package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.DataBase;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class ImportController {

    @FXML
    private TextField filePathField;

    @FXML
    private ComboBox<String> fileFormatComboBox;

    @FXML
    private ComboBox<String> sourceTableComboBox; // For sheets in Excel, keys in JSON/XML

    @FXML
    private ComboBox<String> targetTableComboBox; // Existing DB tables or "Create New Table"

    @FXML
    private CheckBox createNewTableCheckBox;

    @FXML
    private TextField newTableNameField;

    @FXML
    private ListView<String> columnMappingListView; // Placeholder for column mapping UI

    @FXML
    private ProgressBar importProgressBar;

    @FXML
    private TextArea statusTextArea;

    private File selectedFile;
    private FileImporter currentImporter;
    private DataBase currentDb;
    private Stage stage;

    private HashMap<String, ArrayList<String>> TableAndColumns = null;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setCurrentDb(DataBase db) {
        this.currentDb = db;
        loadTables();
        // Populate targetTableComboBox with existing tables from currentDb
        if (currentDb != null) {
            try {
                List<String> dbTables = currentDb.getTables();
                targetTableComboBox.getItems().setAll(dbTables);
                targetTableComboBox.getItems().addFirst("Create New Table..."); // Option to create new
            } catch (Exception e) {
                statusTextArea.appendText("Error loading database tables: " + e.getMessage() + "\n");
            }
        }
    }

    private void loadTables() {
        TableAndColumns = new HashMap<>();
        for (final String table : currentDb.getTables()) {
            TableAndColumns.put(table, currentDb.getColumnsName(table));
        }
    }

    @FXML
    public void initialize() {
        fileFormatComboBox.getItems().addAll("CSV", "Excel", "JSON", "XML");
        fileFormatComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateImporter(newVal);
            if (selectedFile != null) {
                loadSourceTables();
            }
        });

        targetTableComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("Create New Table...".equals(newVal)) {
                createNewTableCheckBox.setSelected(true);
                newTableNameField.setDisable(false);
                newTableNameField.requestFocus();
            } else {
                createNewTableCheckBox.setSelected(false);
                newTableNameField.setDisable(true);
                newTableNameField.clear();
            }
        });

        createNewTableCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            newTableNameField.setDisable(!newVal);
            if (newVal) {
                targetTableComboBox.getSelectionModel().select("Create New Table...");
                newTableNameField.requestFocus();
            } else {
                if ("Create New Table...".equals(targetTableComboBox.getValue())) {
                    targetTableComboBox.getSelectionModel().clearSelection();
                }
            }
        });
        newTableNameField.setDisable(true); // Initially disabled

    }

    private void updateImporter(String format) {
        switch (format) {
            case "CSV":
                currentImporter = new CsvImporter();
                break;
            case "Excel":
                currentImporter = new ExcelImporter();
                break;
            case "JSON":
                currentImporter = new JsonImporter();
                break;
            case "XML":
                currentImporter = new XmlImporter();
                break;
            default:
                currentImporter = null;
                statusTextArea.appendText("Invalid file format selected.\n");
                return;
        }
        statusTextArea.appendText(format + " importer selected.\n");
    }

    @FXML
    private void handleBrowseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Data File");
        selectedFile = fileChooser.showOpenDialog(stage);

        if (selectedFile != null) {
            filePathField.setText(selectedFile.getAbsolutePath());
            statusTextArea.appendText("Selected file: " + selectedFile.getName() + "\n");
            autoDetectFileFormat(selectedFile);
            loadSourceTables();
        }
    }

    private void autoDetectFileFormat(File file) {
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".csv")) {
            fileFormatComboBox.setValue("CSV");
        } else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
            fileFormatComboBox.setValue("Excel");
        } else if (fileName.endsWith(".json")) {
            fileFormatComboBox.setValue("JSON");
        } else if (fileName.endsWith(".xml")) {
            fileFormatComboBox.setValue("XML");
        } else {
            fileFormatComboBox.getSelectionModel().clearSelection();
            statusTextArea.appendText("Could not auto-detect file format for: " + file.getName() + "\n");
        }
    }

    private void loadSourceTables() {
        if (selectedFile == null || currentImporter == null) {
            if (selectedFile != null && fileFormatComboBox.getValue() == null) {
                statusTextArea.appendText("Please select a file format first.\n");
            }
            sourceTableComboBox.getItems().clear();
            return;
        }
        try {
            currentImporter.openFile(selectedFile); // Validates file
            List<String> tableNames = currentImporter.getDetectedTableNames(selectedFile);
            sourceTableComboBox.getItems().setAll(tableNames);
            if (!tableNames.isEmpty()) {
                sourceTableComboBox.getSelectionModel().selectFirst();
                statusTextArea.appendText("Detected source tables/sheets: " + tableNames + "\n");
                // Automatically try to load columns for the first detected table
                loadSourceColumns();
            } else {
                statusTextArea.appendText("No tables/sheets detected in the selected file.\n");
                columnMappingListView.getItems().clear();
            }
        } catch (Exception e) {
            statusTextArea.appendText("Error loading source tables: " + e.getMessage() + "\n");
            sourceTableComboBox.getItems().clear();
            columnMappingListView.getItems().clear();
        }
    }

    @FXML
    private void handleSourceTableSelection() {
        // This might be called when sourceTableComboBox selection changes
        loadSourceColumns();
    }

    private void loadSourceColumns() {
        if (selectedFile == null || currentImporter == null || sourceTableComboBox.getValue() == null) {
            columnMappingListView.getItems().clear();
            return;
        }
        String selectedSourceTable = sourceTableComboBox.getValue();
        try {
            List<String> headers = currentImporter.getColumnHeaders(selectedFile, selectedSourceTable);
            statusTextArea.appendText("Columns for '" + selectedSourceTable + "': " + headers + "\n");
            // Placeholder: Just display headers for now. Actual mapping UI will be more complex.
            if (TableAndColumns == null || TableAndColumns.isEmpty()) {
                columnMappingListView.getItems().setAll(headers.stream().map(h -> h + " -> DB_COLUMN_X").toList());
            } else {
                List<String> cols = TableAndColumns.get(selectedSourceTable);
                columnMappingListView.getItems().setAll(
                        IntStream.range(0, headers.size())
                                .mapToObj(i -> {
                                    String header = headers.get(i);
                                    String colValue = i < cols.size() ? cols.get(i) : "";
                                    return header + " -> " + colValue;
                                })
                                .toList()
                );
            }

            // Try to preview data for the selected table
            previewSourceData();

        } catch (Exception e) {
            statusTextArea.appendText("Error loading source columns for '" + selectedSourceTable + "': " + e.getMessage() + "\n");
            columnMappingListView.getItems().clear();
        }
    }

    private void previewSourceData() {
        if (selectedFile == null || currentImporter == null || sourceTableComboBox.getValue() == null) {
            return;
        }
        String selectedSourceTable = sourceTableComboBox.getValue();
        Thread.ofVirtual().start(()->{
            try {
                List<Map<String, String>> preview = currentImporter.previewData(selectedFile, selectedSourceTable);
                Platform.runLater(()->{
                    statusTextArea.appendText("Data preview for '" + selectedSourceTable + "' (first 5 rows):\n");
                    for(Map<String, String> row : preview) {
                        statusTextArea.appendText(row.toString() + "\n");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(()->statusTextArea.appendText("Error previewing data for '" + selectedSourceTable + "': " + e.getMessage() + "\n"));
            }
        });
    }


    @FXML
    private void handleImportButton() {
        if (selectedFile == null || currentImporter == null || sourceTableComboBox.getValue() == null) {
            statusTextArea.appendText("Please select a file, format, and source table.\n");
            return;
        }
        if (currentDb == null) {
            statusTextArea.appendText("Database connection not available.\n");
            return;
        }

        String sourceTable = sourceTableComboBox.getValue();
        String targetTable;
        boolean createNew = createNewTableCheckBox.isSelected();

        if (createNew) {
            targetTable = newTableNameField.getText();
            if (targetTable == null || targetTable.trim().isEmpty()) {
                statusTextArea.appendText("Please enter a name for the new table.\n");
                return;
            }
            targetTable = targetTable.trim();
        } else {
            targetTable = targetTableComboBox.getValue();
            if (targetTable == null || targetTable.equals("Create New Table...")) {
                statusTextArea.appendText("Please select a target database table or choose to create a new one.\n");
                return;
            }
        }

        // Placeholder for column mapping
        Map<String, String> columnMapping = null; // TODO: Get this from the UI

        statusTextArea.appendText(String.format("Starting import: %s (%s) -> %s %s...\n",
                selectedFile.getName(), sourceTable, targetTable, createNew ? "(new table)" : ""));
        importProgressBar.setProgress(0);
        currentImporter.setImportProprerty(importProgressBar.progressProperty());

        // This should run in a background thread in a real app
        String finalTargetTable = targetTable;
        Thread.ofVirtual().start(()->{
            try {
                // For now, we'll call the placeholder importData
                String result = currentImporter.importData(selectedFile, sourceTable, currentDb.Inserter(), currentDb.buffer, finalTargetTable, createNew, columnMapping);
                Platform.runLater(()->{
                    statusTextArea.appendText("Import result: " + result + "\n");
                    List<String> errors = currentImporter.getErrors();
                    if (!errors.isEmpty()) {
                        statusTextArea.appendText("Import errors/warnings:\n");
                        errors.forEach(e -> statusTextArea.appendText("- " + e + "\n"));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(()->statusTextArea.appendText("Import failed: " + e.getMessage() + "\n"));
                e.printStackTrace(); // For debugging
            }
        });
    }

    @FXML
    private void handleCancelButton() {
        if (stage != null) {
            stage.close();
        }
    }

}
