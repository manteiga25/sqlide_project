package com.example.sqlide.exporter.CSV;

import com.example.sqlide.AdvancedSearch.TableAdvancedSearchController;
import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.Container.loading.loadingController;
import com.example.sqlide.Container.loading.loadingInterface;
import com.example.sqlide.Notification.NotificationInterface;
import com.example.sqlide.TaskInterface;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.popupWindow.Notification;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.action.Action;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import static com.example.sqlide.ColumnMetadata.MetadataToArrayList;
import static com.example.sqlide.ColumnMetadata.MetadataToMap;
import static com.example.sqlide.popupWindow.handleWindow.*;

public class CSVController implements NotificationInterface, loadingInterface {

    @FXML
    private Label NameLabel;
    @FXML
    private JFXCheckBox MetaBox;

    @FXML
    ComboBox<String> ComboMode;

    @FXML
    TextField FileName;

    @FXML
    JFXTextField PathBox;

    private Stage loadingStage, stage;

    private Thread main, writer, fetcher;

    private final ArrayList<Thread> flusherThreads = new ArrayList<>();

    private DataBase db;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    private ArrayList<ArrayList<Object>> data = new ArrayList<>();

    private HashMap<String, String> QueryList = new HashMap<>();

    private HashMap<String, ArrayList<String>> TablesAndColumnsNames;

    private HashMap<String, ArrayList<ColumnMetadata>> TablesAndColumns;

    private Stage advancedFetcherstage;

    private TaskInterface taskInterface;

    private final Semaphore fetcherSem = new Semaphore(1);
    private final Semaphore writeSem = new Semaphore(1);

    @FXML
    public void openWindow() {
        DirectoryChooser selectFolderWindow = new DirectoryChooser();

        final File selectedDir = selectFolderWindow.showDialog(stage);
        if (selectedDir != null) {
            PathBox.setText(selectedDir.getAbsolutePath());
        }
    }

    @FXML
    public void initialize() throws InterruptedException {
        ComboMode.getItems().addAll("DEFAULT", "EXCEL", "INFORMIX_UNLOAD", "INFORMIX_UNLOAD_CSV", "MONGODB_CSV", "MONGODB_TSV", "MYSQL", "ORACLE", "POSTGRESQL_CSV", "POSTGRESQL_TEXT", "RFC4180", "TDF");
        ComboMode.setValue("DEFAULT");
        writeSem.acquire();
    }

    public void setTablesAndColumns(final HashMap<String, ArrayList<String>> TablesAndColumns) {
      // this.TablesAndColumns = TablesAndColumns;
        for (final String table : TablesAndColumns.keySet()) {
            QueryList.put(table, "SELECT * FROM " + table + ";");
        }
        //loadFetchStage();
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

    public void initCSVController(final DataBase db, final TaskInterface taskInterface, final Stage stage) {
        this.db = db;
        this.taskInterface = taskInterface;
        this.stage = stage;
        final HashMap<String, ArrayList<ColumnMetadata>> TablesAndColumns = new HashMap<>();
        final HashMap<String, ArrayList<String>> TablesAndColumnsName = new HashMap<>();
        for (final String table : db.getTables()) {
            TablesAndColumns.put(table, db.getColumnsMetadata(table));
            TablesAndColumnsName.put(table, db.getColumnsName(table));
        }
        for (final String table : TablesAndColumns.keySet()) {
            QueryList.put(table, "SELECT * FROM " + table);
        }

        this.TablesAndColumns = TablesAndColumns;
        this.TablesAndColumnsNames = TablesAndColumnsName;
        loadFetchStage();
        NameLabel.setText("Database: " + db.getDatabaseName());
    }

    @FXML
    private void createBackup() throws IOException {

        final int CSVmode = ComboMode.getSelectionModel().getSelectedIndex();

        String path = PathBox.getText();
        String name = FileName.getText();

        if (path == null || path.isBlank()) {
            ShowError("No selected", "You need to select path for backup.");
            PathBox.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            PathBox.requestFocus();
            return;
        } else if (name == null || name.isBlank()) {
            ShowError("No selected", "You need to add name of file.");
            FileName.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            FileName.requestFocus();
            return;
        }
        path += "\\" + name;
        path = removeDot(path);
        if (new File(path + ".csv").exists() || new File(path).exists()) {
            if (!ShowConfirmation("File exists", "The file " + path + " exists\n" + "Are you sure to continue?")) {
                return;
            }
        }
        else {
            Files.createDirectory(Path.of(path));
        }

        closeWindow();

        String finalPath = path;
        Notification.showInformationNotification("Export to CSV", "Exporting data to CSV file.");
        //setLoadingStage();
        final Task<Void> exporterTask = new Task<>() {

            @Override
            protected void failed() {
                super.failed();
                createErrorNotification(db.getDatabaseName() + "-csv-1", "CSV Error", "Error to export CSV.\n"+getException().getMessage());
                ShowError("CSV", "Error to create CSV.", getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                ShowSucess("Exporting csv", "Success to export database to CSV.");
                createSuccessNotification(db.getDatabaseName() + "-csv-1", "CSV Success", "Success to export CSV.", finalPath);
            }

            @Override
            protected void running() {
                super.running();
                updateTitle("Exporting CSV");
                updateProgress(0, 100);
                progress.addListener((_,_, val)->updateProgress(val.longValue(), 100));
            }

            @Override
            protected Void call() {
                try {
                    prepareWork(finalPath, db, CSVmode);
                } catch (Exception e) {
                    if (Thread.currentThread().isInterrupted()) {
                        throw new RuntimeException(e);
                    }
                }
                return null;
        }

            @Override
            protected void cancelled() {
                super.cancelled();
                Notification.showInformationNotification("Cancel export", "CSV export canceled");
                cancelTask();
            }

        };
        taskInterface.addTask(exporterTask);
        Thread.ofVirtual().start(exporterTask);
    }

    private void prepareWork(String Path, final DataBase cursor, final int CSVmode) throws InterruptedException, IOException {
        final long buffer = cursor.buffer * 100L;
        final double interact = (100.0 / TablesAndColumns.size()) / 100.0;
        for (final String sheetName : TablesAndColumns.keySet()) {

            final String query = QueryList.get(sheetName);
            if (!query.isEmpty()) {

            if (MetaBox.isSelected()) createMetadata(Path, sheetName, CSVmode);

            final SqlToCSV csv = new SqlToCSV();

            csv.createCSV(Path + "\\" + sheetName + ".csv", CSVmode);
            //   final ArrayList<String> columns = cursor.getColumnsName(sheetName);
            final ArrayList<String> columns = TablesAndColumnsNames.get(sheetName);
            csv.createCSVHeader(columns);

            writer = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        writeSem.acquire();
                    } catch (InterruptedException e) {
                        try {
                            csv.writeCSVData(data);
                        } catch (Exception _) {
                        }
                        data = null;
                        fetcherSem.release();
                        break;
                    }
                    try {
                        csv.writeCSVData(data);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    data = null;
                    fetcherSem.release();
                }
            });

            fetcher = new Thread(() -> {
                long offset = 0;
                while (true) {
                    ArrayList<ArrayList<Object>> dataCopy = cursor.Fetcher().fetchDataBackupObject(query, columns, buffer, offset);
                    if (dataCopy == null || dataCopy.isEmpty()) {
                        break;
                    }
                    final ArrayList<ArrayList<Object>> copy = (ArrayList<ArrayList<Object>>) dataCopy.clone();
                    try {
                        fetcherSem.acquire();
                        data = copy;
                    } catch (InterruptedException _) {
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

            final Thread flusherTask = new Thread(() -> {
                try {
                    csv.SaveAndClose();
                    //Platform.runLater(() -> progress.set(progress.get() + interact));
                    progress.set(progress.get() + interact);
                    updateLoading(db.getDatabaseName()+"-csv", progress.get());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    flusherThreads.remove(Thread.currentThread());
                }
            });
            flusherTask.start();
            flusherThreads.add(flusherTask);

            System.out.println("acabou");


        }
        }
    }

    private void createMetadata(String Path, final String sheetName, final int CSVmode) throws IOException {
        final SqlToCSV csv = new SqlToCSV();
        csv.createCSV(Path + "\\" + sheetName + "-metadata.csv", CSVmode);
        final ArrayList<ColumnMetadata> columns = TablesAndColumns.get(sheetName);
      //  final ArrayList<String> columns = TablesAndColumns.get(sheetName);
        csv.createCSVHeader(new ArrayList<>(MetadataToMap(columns.getFirst()).keySet()));
        final ArrayList<ArrayList<Object>> ArrayMeta = new ArrayList<>();
        for (final ColumnMetadata meta : columns) {
            ArrayMeta.add(MetadataToArrayList(meta));
        }
        csv.writeCSVData(ArrayMeta);
        csv.SaveAndClose();
    }

    private String removeDot(String s) {
        if (s.contains(".")) {
            return s.substring(0, s.indexOf("."));
        }
        return s;
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
            subStage.setTitle("CSV");
            subStage.initOwner(stage);
            subStage.setResizable(false);
            subStage.setScene(new Scene(root));
            secondaryController.setAttr(progress, "Exporting CSV", stage, this);

            subStage.initModality(Modality.NONE);

            // Mostrar a subjanela
            subStage.show();
            loadingStage = subStage;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void closeWindow() {
        stage.close();
    }

    public void cancelTask() {
        for (Thread flush : flusherThreads) {
            flush.interrupt();
        }
        writer.interrupt();
        fetcher.interrupt();
        main.interrupt();
        flusherThreads.clear();
    }

    @Override
    public void close() {
        cancelTask();
    }
}
