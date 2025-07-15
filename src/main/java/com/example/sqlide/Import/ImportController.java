package com.example.sqlide.Import;

import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.Interfaces.DatabaseInserterInterface; // Added for createTable
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.SQLException; // Added for createTable
import java.util.*;
import java.util.stream.Collectors;

public class ImportController {

    @FXML
    private TableColumn<ColumnMappingRow, String> srcTable, ColumnTable;

    @FXML
    private TextField filePathField;

    @FXML
    private ComboBox<String> fileFormatComboBox;

    @FXML
    private ComboBox<String> sourceTableComboBox; // For sheets in Excel, keys in JSON/XML

    @FXML
    private ComboBox<String> targetTableComboBox; // Existing DB tables or "Create New Table..."

    @FXML
    private CheckBox createNewTableCheckBox;

    @FXML
    private TextField newTableNameField;

    @FXML
    private TableView<ColumnMappingRow> columnMappingTableView; // New fx:id for the TableView

    @FXML
    private ProgressBar importProgressBar;

    @FXML
    private TextArea statusTextArea;

    private File selectedFile;
    private FileImporter currentImporter;
    private DataBase currentDb;
    private Stage stage;

    private static final String SKIP_IMPORT_OPTION = "<Skip Import>";
    private static final String CREATE_NEW_COLUMN_OPTION_PREFIX = "<Create New Column: ";
    private static final String CREATE_NEW_COLUMN_SUFFIX = ">";

    @FXML
    private void clear() {
        statusTextArea.setText("");
    }

    public static class ColumnMappingRow {
        private final SimpleStringProperty sourceColumnName;
        private final StringProperty selectedTarget;
        private final ChoiceBox<String> choice;

        public ColumnMappingRow(final String sourceColumnName, final List<String> columns) {
            this.sourceColumnName = new SimpleStringProperty(sourceColumnName);
            this.selectedTarget = new SimpleStringProperty(sourceColumnName);
            this.choice = new ChoiceBox<>();
            this.choice.getItems().addAll(columns);
            this.choice.valueProperty().bindBidirectional(this.selectedTarget);
        }

        public String getSourceColumnName() { return sourceColumnName.get(); }
        public SimpleStringProperty sourceColumnNameProperty() { return sourceColumnName; }
        public String getSelectedTarget() { return selectedTarget.get(); }
        public StringProperty selectedTargetProperty() { return selectedTarget; }
        public void setSelectedTarget(String target) { this.selectedTarget.set(target); }
        public ChoiceBox<String> getChoiceBox() { return choice; }
    }

    public void setStage(Stage stage) { this.stage = stage; }

    public void setCurrentDb(DataBase db) {
        this.currentDb = db;
        if (currentDb != null) {
            try {
                targetTableComboBox.getItems().clear();
                List<String> dbTables = currentDb.getTables();
                if (dbTables != null) {
                    targetTableComboBox.getItems().addAll(dbTables);
                }
                targetTableComboBox.getItems().addFirst("Create New Table...");
            } catch (Exception e) {
                statusTextArea.appendText("Error loading database tables: " + e.getMessage() + "\n");
            }
        }
    }

    @FXML
    public void initialize() {
        fileFormatComboBox.getItems().addAll("CSV", "Excel", "JSON", "XML");
        fileFormatComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            updateImporter(newVal);
            if (selectedFile != null && currentImporter != null) { 
                loadSourceTables();
            } else if (currentImporter == null && columnMappingTableView != null){ 
                columnMappingTableView.getItems().clear();
            }
        });

        targetTableComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            boolean isCreatingNew = "Create New Table...".equals(newVal);
            if (isCreatingNew) {
                if (!createNewTableCheckBox.isSelected()) createNewTableCheckBox.setSelected(true);
                newTableNameField.setDisable(false);
                newTableNameField.requestFocus();
            } else {
                if (createNewTableCheckBox.isSelected()) createNewTableCheckBox.setSelected(false);
                newTableNameField.setDisable(true);
                newTableNameField.clear();
            }
            if (selectedFile != null && currentImporter != null && sourceTableComboBox.getValue() != null) {
                loadSourceColumns(); 
            }
        });

        createNewTableCheckBox.selectedProperty().addListener((obs, oldVal, newVal) -> {
            newTableNameField.setDisable(!newVal);
            if (newVal) {
                if (!"Create New Table...".equals(targetTableComboBox.getValue())) {
                    targetTableComboBox.getSelectionModel().select("Create New Table...");
                }
                newTableNameField.requestFocus();
            } else {
                if ("Create New Table...".equals(targetTableComboBox.getValue())) {
                    targetTableComboBox.getSelectionModel().clearSelection();
                }
            }
            if (selectedFile != null && currentImporter != null && sourceTableComboBox.getValue() != null) {
                loadSourceColumns(); 
            }
        });
        newTableNameField.setDisable(true);
        setupColumnMappingTableView();
    }

    private void setupColumnMappingTableView() {

        ColumnTable.setCellValueFactory(cellData -> cellData.getValue().sourceColumnNameProperty());

        srcTable.setCellValueFactory(cellData -> cellData.getValue().selectedTargetProperty());

        // Usar ChoiceBox como renderizador
        srcTable.setCellFactory(column -> new TableCell<>() {
            private final ChoiceBox<String> choiceBox = new ChoiceBox<>();

            {
                // Configurar para preencher toda a célula
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                setAlignment(Pos.CENTER);

                choiceBox.valueProperty().addListener((_,_, value)->{
                    column.getTableView().getItems().get(getIndex()).getChoiceBox().setValue(value);
                });

            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    ColumnMappingRow row = getTableView().getItems().get(getIndex());
                    choiceBox.setItems(row.getChoiceBox().getItems());
                    choiceBox.setValue(item);

                    setGraphic(choiceBox);
                }
            }
        });

        /*targetColumn.setCellFactory(col -> {
            ComboBoxTableCell<ColumnMappingRow, String> cell = new ComboBoxTableCell<>();
            cell.setComboBoxEditable(false);
            cell.startEdit(event -> {
                ColumnMappingRow currentRow = event().getItems().get(event.getTablePosition().getRow());
                if (currentRow != null) {
                    ObservableList<String> options = FXCollections.observableArrayList(
                        getAvailableTargetOptions(currentRow.getSourceColumnName())
                    );
                    cell.getItems().setAll(options);
                    if (options.contains(currentRow.getSelectedTarget())) {
                        cell.getComboBox().setValue(currentRow.getSelectedTarget());
                    } else if (!options.isEmpty()) {
                        cell.getComboBox().setValue(options.get(0));
                    }
                }
            });
            return cell;
        }); */

       // columnMappingTableView.setPlaceholder(new Label("Select source file, format, and table to map columns."));
    }

    private List<String> getAvailableTargetOptions(String sourceColumnName) {
        List<String> options = new ArrayList<>();
        options.add(SKIP_IMPORT_OPTION);

        boolean isEffectivelyCreatingNewTable = createNewTableCheckBox.isSelected() || "Create New Table...".equals(targetTableComboBox.getValue());

        if (isEffectivelyCreatingNewTable) {
            options.add(CREATE_NEW_COLUMN_OPTION_PREFIX + sourceColumnName + CREATE_NEW_COLUMN_SUFFIX);
        } else {
            String selectedDbTable = targetTableComboBox.getValue();
            if (selectedDbTable != null && !selectedDbTable.equals("Create New Table...") && currentDb != null) {
                try {
                    List<String> dbTableColumns = currentDb.getColumnsName(selectedDbTable);
                    if (dbTableColumns != null) options.addAll(dbTableColumns);
                } catch (Exception e) {
                    statusTextArea.appendText("Error fetching columns for DB table '" + selectedDbTable + "': " + e.getMessage() + "\n");
                }
            } else if (selectedDbTable == null && !isEffectivelyCreatingNewTable) {
                 options.add(CREATE_NEW_COLUMN_OPTION_PREFIX + sourceColumnName + CREATE_NEW_COLUMN_SUFFIX);
            }
        }
        return options.stream().distinct().collect(Collectors.toList());
    }

    private void updateImporter(String format) {
        if (format == null) {
            currentImporter = null;
            if (columnMappingTableView != null) columnMappingTableView.getItems().clear();
            return;
        }
        switch (format) {
            case "CSV": currentImporter = new CsvImporter(); break;
            case "Excel": currentImporter = new ExcelImporter(); break;
            case "JSON": currentImporter = new JsonImporter(); break;
            case "XML": currentImporter = new XmlImporter(); break;
            default:
                currentImporter = null;
                statusTextArea.appendText("Invalid file format selected.\n");
                if (columnMappingTableView != null) columnMappingTableView.getItems().clear();
                return;
        }
        statusTextArea.appendText(format + " importer selected.\n");
        if (columnMappingTableView != null) columnMappingTableView.getItems().clear();
    }

    @FXML
    private void handleBrowseFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Data File");
        File newSelectedFile = fileChooser.showOpenDialog(stage);
        if (newSelectedFile != null) {
            selectedFile = newSelectedFile;
            filePathField.setText(selectedFile.getAbsolutePath());
            statusTextArea.appendText("Selected file: " + selectedFile.getName() + "\n");
            autoDetectFileFormat(selectedFile);
            if (currentImporter == null && fileFormatComboBox.getValue() != null){
                updateImporter(fileFormatComboBox.getValue());
            }
            if (currentImporter != null) loadSourceTables(); 
        }
    }

    private void autoDetectFileFormat(File file) {
        String fileName = file.getName().toLowerCase();
        String currentFormat = fileFormatComboBox.getValue();
        String detectedFormat = null;
        if (fileName.endsWith(".csv")) detectedFormat = "CSV";
        else if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) detectedFormat = "Excel";
        else if (fileName.endsWith(".json")) detectedFormat = "JSON";
        else if (fileName.endsWith(".xml")) detectedFormat = "XML";

        if (detectedFormat != null) {
            if (!detectedFormat.equals(currentFormat)) {
                fileFormatComboBox.setValue(detectedFormat); 
            } else { 
                if (currentImporter != null) loadSourceTables();
            }
        } else {
            if(currentFormat != null) fileFormatComboBox.getSelectionModel().clearSelection(); 
            else updateImporter(null); 
            statusTextArea.appendText("Could not auto-detect file format for: " + file.getName() + "\n");
        }
    }

    private void loadSourceTables() {
        if (selectedFile == null || currentImporter == null) {
            if (selectedFile != null && fileFormatComboBox.getValue() == null) {
                statusTextArea.appendText("Please select a file format.\n");
            }
            sourceTableComboBox.getItems().clear();
            if (columnMappingTableView != null) columnMappingTableView.getItems().clear();
            return;
        }
        try {
            currentImporter.openFile(selectedFile);
            List<String> tableNames = currentImporter.getDetectedTableNames(selectedFile);
            sourceTableComboBox.getItems().setAll(tableNames);
            if (!tableNames.isEmpty()) {
                sourceTableComboBox.getSelectionModel().selectFirst(); 
            } else {
                statusTextArea.appendText("No tables/sheets detected in the selected file.\n");
                if (columnMappingTableView != null) columnMappingTableView.getItems().clear();
            }
        } catch (Exception e) {
            statusTextArea.appendText("Error loading source tables: " + e.getMessage() + "\n");
            sourceTableComboBox.getItems().clear();
            if (columnMappingTableView != null) columnMappingTableView.getItems().clear();
        }
    }

    @FXML
    private void handleSourceTableSelection() { loadSourceColumns(); }

    private void loadSourceColumns() {
        columnMappingTableView.getItems().clear();
        if (selectedFile == null || currentImporter == null || sourceTableComboBox.getValue() == null || targetTableComboBox.getValue() == null) return;

        String selectedSourceTable = sourceTableComboBox.getValue();
        try {
            List<String> sourceHeaders = currentImporter.getColumnHeaders(selectedFile, selectedSourceTable);
            statusTextArea.appendText("Source columns for '" + selectedSourceTable + "': " + sourceHeaders + "\n");

            boolean isEffCreatingNew = createNewTableCheckBox.isSelected() || "Create New Table...".equals(targetTableComboBox.getValue());
            String targetDbTable = targetTableComboBox.getValue();
            List<String> dbTableColumns = null;
            if (!isEffCreatingNew && targetDbTable != null && !targetDbTable.equals("Create New Table...") && currentDb != null) {
                try { dbTableColumns = currentDb.getColumnsName(targetDbTable); }
                catch (Exception e) { statusTextArea.appendText("Error getting columns for target DB table '" + targetDbTable + "': " + e.getMessage() + "\n"); }
            }

            final ObservableList<ColumnMappingRow> rows = FXCollections.observableArrayList();
            for (final String column : dbTableColumns) {
                final ColumnMappingRow row = new ColumnMappingRow(column, sourceHeaders);
                String autoSelectedTarget = SKIP_IMPORT_OPTION;
                if (isEffCreatingNew) {
                    autoSelectedTarget = CREATE_NEW_COLUMN_OPTION_PREFIX + column + CREATE_NEW_COLUMN_SUFFIX;
                } else {
                    String matchedDbCol = sourceHeaders.stream().filter(dbCol -> dbCol.equalsIgnoreCase(column)).findFirst().orElse(null);
                    if (matchedDbCol != null) autoSelectedTarget = matchedDbCol;
                }
                List<String> tempOpts = getAvailableTargetOptions(column); // Get options for current context
                if (tempOpts.contains(autoSelectedTarget)) row.setSelectedTarget(autoSelectedTarget);
                else if (!tempOpts.isEmpty()) row.setSelectedTarget(tempOpts.getFirst());
                else row.setSelectedTarget(SKIP_IMPORT_OPTION); 
                
                rows.add(row);
            }
            columnMappingTableView.setItems(rows);
            previewSourceData();
        } catch (Exception e) {
            statusTextArea.appendText("Error loading source columns for '" + selectedSourceTable + "': " + e.getMessage() + "\n" + e.getMessage());
            columnMappingTableView.getItems().clear();
        }
    }

    private void previewSourceData() {
        if (selectedFile == null || currentImporter == null || sourceTableComboBox.getValue() == null) return;
        String selectedSourceTable = sourceTableComboBox.getValue();
        Thread.ofVirtual().start(() -> {
            try {
                List<Map<String, String>> preview = currentImporter.previewData(selectedFile, selectedSourceTable);
                Platform.runLater(() -> {
                    statusTextArea.appendText("\nData preview for '" + selectedSourceTable + "' (first " + preview.size() + " rows):\n");
                    List<String> previewH = new ArrayList<>();
                    if(!preview.isEmpty() && columnMappingTableView != null && !columnMappingTableView.getItems().isEmpty()){
                        previewH.addAll(columnMappingTableView.getItems().stream().map(ColumnMappingRow::getSourceColumnName).toList());
                    } else if (!preview.isEmpty() && !preview.getFirst().isEmpty()) {
                        previewH.addAll(preview.getFirst().keySet());
                    } else if (!preview.isEmpty() && !preview.getFirst().isEmpty()) {
                        previewH.addAll(preview.getFirst().keySet());
                    }

                    if (!previewH.isEmpty()) {
                        statusTextArea.appendText(String.join("\t|\t", previewH) + "\n");
                        statusTextArea.appendText(String.join("", Collections.nCopies(String.join("\t|\t", previewH).length() + previewH.size() * 2, "-")) + "\n");
                    } else { statusTextArea.appendText("(Could not determine preview headers)\n");}

                    for (Map<String, String> row : preview) {
                        List<String> values = new ArrayList<>();
                        if(!previewH.isEmpty()) {
                            for (String h : previewH) values.add(row.getOrDefault(h, ""));
                        } else {
                            values.addAll(row.values());
                        }
                        statusTextArea.appendText(String.join("\t|\t", values) + "\n");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> statusTextArea.appendText("Error previewing data for '" + selectedSourceTable + "': " + e.getMessage() + "\n"));
            }
        });
    }

    @FXML
    private void handleImportButton() {
        if (columnMappingTableView == null || columnMappingTableView.getItems().isEmpty()) {
            statusTextArea.appendText("No column mappings defined. Load file and configure mappings.\n"); return;
        }
        if (selectedFile == null || currentImporter == null || sourceTableComboBox.getValue() == null) {
            statusTextArea.appendText("Please select file, format, and source table.\n"); return;
        }
        if (currentDb == null) { statusTextArea.appendText("Database connection not available.\n"); return; }

        String sourceFileTable = sourceTableComboBox.getValue();
        String targetDbTableName;
        boolean isEffCreatingNew = createNewTableCheckBox.isSelected() || "Create New Table...".equals(targetTableComboBox.getValue());

        if (isEffCreatingNew) {
            targetDbTableName = newTableNameField.getText();
            if (targetDbTableName == null || targetDbTableName.trim().isEmpty()) {
                statusTextArea.appendText("Please enter name for the new table.\n"); return;
            }
            targetDbTableName = targetDbTableName.trim();
        } else {
            targetDbTableName = targetTableComboBox.getValue();
            if (targetDbTableName == null || targetDbTableName.equals("Create New Table...")) {
                statusTextArea.appendText("Please select target table or choose to create new.\n"); return;
            }
        }

        Map<String, String> finalColumnMapping = new HashMap<>();
        List<String> newTableColDefs = new ArrayList<>(); 

        for (ColumnMappingRow row : columnMappingTableView.getItems()) {
            String sourceCol = row.getSourceColumnName();
            String targetSelection = row.getSelectedTarget();
            System.out.println("source " + sourceCol + " target " + targetSelection);
            if (targetSelection == null || targetSelection.equals(SKIP_IMPORT_OPTION)) continue;

            if (targetSelection.startsWith(CREATE_NEW_COLUMN_OPTION_PREFIX)) {
                finalColumnMapping.put(sourceCol, sourceCol);
                if (isEffCreatingNew) newTableColDefs.add(sourceCol + " TEXT"); // Default type
            } else {
                finalColumnMapping.put(sourceCol, targetSelection);
                if (isEffCreatingNew) newTableColDefs.add(targetSelection + " TEXT"); // Default type
            }
        }

        if (finalColumnMapping.isEmpty()) {
            statusTextArea.appendText("No columns mapped for import.\n"); return;
        }
        
        final String effectiveTargetTableName = targetDbTableName; // Final name for use in thread

        if (isEffCreatingNew) {
            statusTextArea.appendText("Attempting to create new table '" + effectiveTargetTableName + "' with columns (all TEXT type for now): " + newTableColDefs.stream().map(d -> d.split(" ")[0]).toList() + "\n");
            try {
                currentDb.Inserter().createTable(effectiveTargetTableName, newTableColDefs, null); // Pass null for PKs for now
                statusTextArea.appendText("Table '" + effectiveTargetTableName + "' created successfully or already existed with compatible schema.\n");
            } catch (SQLException e) {
                statusTextArea.appendText("Failed to create new table '" + effectiveTargetTableName + "': " + e.getMessage() + "\n");
                // e.printStackTrace();
                return; // Abort import if table creation fails
            }
        }

        statusTextArea.appendText(String.format("Starting import: %s (%s) -> %s %s...\n",
            selectedFile.getName(), sourceFileTable, effectiveTargetTableName, isEffCreatingNew ? "(new table)" : ""));
        
        if(currentImporter.getImportProgress() >= 0) { 
            importProgressBar.progressProperty().unbind(); 
            currentImporter.setImportProprerty(importProgressBar.progressProperty());
        } else { 
            importProgressBar.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        }

        Thread.ofVirtual().start(() -> {
            try {
                String result = currentImporter.importData(selectedFile, sourceFileTable, currentDb.Inserter(), currentDb.buffer, effectiveTargetTableName, isEffCreatingNew, finalColumnMapping);
                Platform.runLater(() -> {
                    statusTextArea.appendText("Import result: " + result + "\n");
                    List<String> errors = currentImporter.getErrors();
                    if (!errors.isEmpty()) {
                        statusTextArea.appendText("Import errors/warnings:\n");
                        errors.forEach(e -> statusTextArea.appendText("- " + e + "\n"));
                    }
                    if (isEffCreatingNew) {
                        setCurrentDb(currentDb); 
                        targetTableComboBox.setValue(effectiveTargetTableName); 
                    }
                    if(currentImporter.getImportProgress() >= 0) importProgressBar.progressProperty().unbind(); 
                    importProgressBar.setProgress(1); 
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusTextArea.appendText("Import failed: " + e.getMessage() + "\n");
                    // e.printStackTrace();
                    if(currentImporter.getImportProgress() >= 0) importProgressBar.progressProperty().unbind();
                    importProgressBar.setProgress(0); 
                });
            }
        });
    }

    @FXML
    private void handleCancelButton() {
        if (stage != null) stage.close();
    }
}
