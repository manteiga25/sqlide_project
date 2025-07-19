package com.example.sqlide.exporter.Excel;

import com.example.sqlide.AdvancedSearch.TableAdvancedSearchController;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Container.loading.loadingController;
import com.example.sqlide.Container.loading.loadingInterface;
import com.example.sqlide.Notification.NotificationInterface;
import com.example.sqlide.Task.TaskInterface;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.popupWindow.Notification;
import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import static com.example.sqlide.Metadata.ColumnMetadata.MetadataToArrayList;
import static com.example.sqlide.Metadata.ColumnMetadata.MetadataToMap;
import static com.example.sqlide.popupWindow.handleWindow.*;

public class excelController implements loadingInterface, NotificationInterface {

    @FXML
    private JFXCheckBox MetaBox;

    @FXML
    private TextField PathBox, nameOfFolder;

    private Stage stage;

    private DataBase db;

    private Thread writer, fetcher;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    private ArrayList<ArrayList<Object>> data = new ArrayList<>();

    private HashMap<String, String> QueryList = new HashMap<>();

    private HashMap<String, ArrayList<String>> TablesAndColumnsNames;

    private HashMap<String, ArrayList<ColumnMetadata>> TablesAndColumns;

    private TaskInterface taskInterface;

    private final Semaphore fetcherSem = new Semaphore(1);
    private final Semaphore writeSem = new Semaphore(1);

    private Stage loadingStage;

    private Stage advancedFetcherstage;

    @FXML
    public void openWindow() {
        DirectoryChooser selectFolderWindow = new DirectoryChooser();

        final File selectedDir = selectFolderWindow.showDialog(stage);
        if (selectedDir != null) {
            PathBox.setText(selectedDir.getAbsolutePath());
        }
    }

    @FXML
    private void openFetchStage() {
        advancedFetcherstage.show();
    }

    private void loadFetchStage() {
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedTableSearchStage.fxml"));
            Parent root = loader.load();

            TableAdvancedSearchController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.setStage(subStage);
            secondaryController.setTables(TablesAndColumnsNames);

            subStage.showingProperty().addListener(_->{
                if (secondaryController.isClosedByUser()) {
                    QueryList = secondaryController.getQueryList();
                }
            });

            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            advancedFetcherstage = subStage;
            //subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public void initExcelController(final DataBase db, final TaskInterface taskInterface, final Stage stage) {
        this.db = db;
        this.taskInterface = taskInterface;
        this.stage = stage;
        try {
            writeSem.acquire();
        } catch (InterruptedException _) {
        }
        final HashMap<String, ArrayList<ColumnMetadata>> TablesAndColumns = new HashMap<>();
        final HashMap<String, ArrayList<String>> TablesAndColumnsName = new HashMap<>();
        for (final String table : db.getTables()) {
            TablesAndColumns.put(table, db.getColumnsMetadata(table));
            TablesAndColumnsName.put(table, db.getColumnsName(table));
        }
        for (final String table : TablesAndColumns.keySet()) {
            QueryList.put(table, "SELECT * FROM " + table + ";");
        }

        this.TablesAndColumns = TablesAndColumns;
        this.TablesAndColumnsNames = TablesAndColumnsName;
        loadFetchStage();
    }

    @FXML
    private void createBackup() {

        String path = PathBox.getText();
        String name = nameOfFolder.getText();

        if (path == null ||  path.isBlank()) {
            ShowError("No selected", "You need to select path for backup.");
            PathBox.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            PathBox.requestFocus();
            return;
        }
        else if (name == null || name.isBlank()) {
            ShowError("No selected", "You need to add name of file.");
            nameOfFolder.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            nameOfFolder.requestFocus();
            return;
        }
        path += "\\" + name;
        if (new File(path + ".xlsx").exists() || new File(path).exists()) {
            if (!ShowConfirmation("File exists", "The file " + path + " exists\n" + "Are you sure to continue?")) {
                return;
            }
        }

        closeWindow();

        path = removeDot(path);

        final String finalPath = path;
        //setLoadingStage();
        final Task<Void> exporterTask = new Task<>() {

            final SqlToExcel excel = new SqlToExcel();

            @Override
            protected void failed() {
                super.failed();
                excel.close();
                createErrorNotification(db.getDatabaseName() + "-xlsx-1", "Excel Error", "Error to export Excel.\n"+getException().getMessage());
                ShowError("Excel", "Error to create Excel.\n" + getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                excel.saveAndClose();
                updateProgress(100, 100);
                ShowSucess("Exporting Excel", "Success to export database to Excel.");
                createSuccessNotification(db.getDatabaseName() + "-xlsx-1", "Excel Success", "Success to export Excel.", finalPath);
            }

            @Override
            protected void running() {
                super.running();
                updateTitle("Exporting Excel");
                updateProgress(0, 100);
                progress.addListener((_,_, val)->updateProgress(val.longValue(), 100));
            }

            @Override
            protected Void call() throws IOException {

                    excel.createFile(finalPath + ".xlsx");

                    final ArrayList<String> tables = new ArrayList<>(TablesAndColumnsNames.keySet());

                    excel.createWorkbook(db.buffer * 10);

                    try {
                        prepareWork(excel, tables, db);
                    } catch (InterruptedException e) {
                        if (!Thread.currentThread().isInterrupted()) throw new RuntimeException(e);
                    }

                return null;
            }

            @Override
            protected void cancelled() {
                super.cancelled();
                Notification.showInformationNotification("Cancel export", "Excel export canceled");
                cancelTask();
                excel.close();
            }

        };
        taskInterface.addTask(exporterTask);
        Thread.ofVirtual().start(exporterTask);

    }

    private void prepareWork(final SqlToExcel excel, final ArrayList<String> tables, final DataBase cursor) throws InterruptedException, IOException {
        final long buffer = cursor.buffer * 10L;
        final double interact = (100.0 / tables.size()) / 100.0;
        for (final String sheetName : tables) {

            final String query = QueryList.get(sheetName);
            if (!query.isEmpty()) {

                if (MetaBox.isSelected()) createMetadata(excel, sheetName);

                final ArrayList<String> columns = cursor.getColumnsName(sheetName);
                excel.createSheet(sheetName, columns);
                //    excel.createColumns(columns, sheetName);

                writer = new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            writeSem.acquire();
                        } catch (InterruptedException e) {
                            try {
                                excel.writeData(data);
                            } catch (Exception _) {
                            }
                            data = null;
                            fetcherSem.release();
                            break;
                        }
                        excel.writeData(data);
                        data = null;
                        fetcherSem.release();
                    }
                });

                fetcher = new Thread(() -> {
                    long offset = 0;
                    while (true) {
                        final ArrayList<ArrayList<Object>> dataCopy = cursor.Fetcher().fetchDataBackupObject(query, columns, buffer, offset);
                        if (dataCopy == null || dataCopy.isEmpty()) {
                            //  writer.interrupt();
                            break;
                        }
                        final ArrayList<ArrayList<Object>> copy = (ArrayList<ArrayList<Object>>) dataCopy.clone();
                        try {
                            fetcherSem.acquire();
                            data = copy;
                            if (data.size() < buffer) break;
                        } catch (InterruptedException _) {
                            writer.interrupt();
                            break;
                        }
                        writeSem.release();
                        offset += buffer;
                    }
                    writer.interrupt();
                });
                fetcher.start();
                writer.start();

                fetcher.join();
                writer.join();

                progress.set(progress.get() + interact);

            }
        }
    }

    private void createMetadata(final SqlToExcel excel, final String sheetName) {
        final ArrayList<ColumnMetadata> columns = TablesAndColumns.get(sheetName);
        //  final ArrayList<String> columns = TablesAndColumns.get(sheetName);
        excel.createSheet(sheetName+"-metadata", new ArrayList<>(MetadataToMap(columns.getFirst()).keySet()));
        final ArrayList<ArrayList<Object>> ArrayMeta = new ArrayList<>();
        for (final ColumnMetadata meta : columns) {
            ArrayMeta.add(MetadataToArrayList(meta));
        }
        excel.writeData(ArrayMeta);
    }

    private void setLoadingStage() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/loadingPane/loadingLayout.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            loadingController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("EXCEL");
            subStage.initOwner(stage);
            subStage.setResizable(false);
            subStage.setScene(new Scene(root));
            secondaryController.setAttr(progress, "Exporting Excel", stage, this);

            subStage.initModality(Modality.NONE);

            // Mostrar a subjanela
            subStage.show();
            loadingStage = subStage;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String removeDot(String s) {
        if (s.contains(".")) {
            return s.substring(0, s.indexOf("."));
        }
        return s;
    }

    @FXML
    public void closeWindow() {
        stage.close();
    }

    public void cancelTask() {
        writer.interrupt();
        fetcher.interrupt();
    }

    @Override
    public void close() {
        cancelTask();
    }
}
