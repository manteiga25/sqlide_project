package com.example.sqlide.exporter.CSV;

import com.example.sqlide.AdvancedSearch.TableAdvancedSearchController;
import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.Container.loading.loadingController;
import com.example.sqlide.EventLayout.EventController;
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
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import static com.example.sqlide.ColumnMetadata.MetadataToArrayList;
import static com.example.sqlide.ColumnMetadata.MetadataToMap;
import static com.example.sqlide.popupWindow.handleWindow.*;

public class CSVController {

    @FXML
    private JFXCheckBox MetaBox;
    @FXML
    private JFXButton folderButton;

    @FXML
    ComboBox<String> ComboMode;

    @FXML
    TextField FileName;

    @FXML
    JFXTextField PathBox;

    private Stage loadingStage, stage;

    private Thread main, writer, fetcher;

    private final ArrayList<Thread> flusherThreads = new ArrayList<>();

    private final BooleanProperty TaskState = new SimpleBooleanProperty(true);

    private DataBase db;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    private ArrayList<ArrayList<Object>> data = new ArrayList<>();

    private HashMap<String, String> QueryList = new HashMap<>();

    private HashMap<String, ArrayList<String>> TablesAndColumnsNames;

    private HashMap<String, ArrayList<ColumnMetadata>> TablesAndColumns;

    private Stage advancedFetcherstage;

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

        final String imagePath = getClass().getResource("/com/example/sqlide/images/folder.png").getPath();

        // Cria um objeto File a partir do caminho
        File imageFile = new File(imagePath);

        // Converte o caminho do arquivo para uma URL
        String imageUrl = imageFile.toURI().toString();

        ImageView view = new ImageView(imageUrl);
        view.setFitHeight(17);
        view.setPreserveRatio(true);
        folderButton.setPadding(Insets.EMPTY);
        folderButton.setGraphic(view);

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

    public void initCSVController(final DataBase db, final Stage stage) {
        this.db = db;
        this.stage = stage;
        TaskState.addListener((observable -> cancelTask()));
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
        main = new Thread(()->{
            boolean state = false;
            try {

                Platform.runLater(this::setLoadingStage);

               // final ArrayList<String> tables = cursor.getTables();

                prepareWork(finalPath, db, CSVmode);
                state = true;
                ShowSucess("Exporting csv", "Success to export database to CSV.");
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) {
                    ShowError("CSV", "Error to create CSV.\n" + e.getMessage());
                }
                } finally {
                final boolean finalState = state;
                Platform.runLater(()->{
                    loadingStage.close();
                    createNot(finalState);
                });
            }
        });
        main.start();
    }

    private void prepareWork(String Path, final DataBase cursor, final int CSVmode) throws InterruptedException, IOException {
        final long buffer = cursor.buffer * 10L;
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
                    ArrayList<ArrayList<Object>> dataCopy = cursor.fetchDataBackupObject(query, columns, buffer, offset);
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
                    Platform.runLater(() -> progress.set(progress.get() + interact));
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

    private void createNot(final boolean state) {

        Action function = new Action("open", event->{
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                // Verifica se a ação de abrir uma pasta é suportada
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    try {
                        // Cria um objeto File com o caminho da pasta
                        File folder = new File(PathBox.getText());

                        // Verifica se o caminho é uma pasta válida
                        if (folder.exists() && folder.isDirectory()) {
                            desktop.open(folder); // Abre a pasta no explorador de arquivos
                        }
                    } catch (IOException _) {
                    }
                }
            }
        });
        function.setStyle("-fx-background-color: #3574f0; -fx-text-fill: white");

        if (state) {
            Notification.showSuccessNotification("CSV Success", "Success to export CSV", function);
        } else {
            Notification.showErrorNotification("CSV Error", "Error to export CSV");
        }


    }

   /* private void addData(final String table, final ArrayList<String> columns, final SqlToCSV csv, final DataBase cursor) {
        long offset = 0;
        final long buffer = cursor.buffer * 10L;
        ArrayList<ArrayList<Object>> data = cursor.fetchDataBackupObject(table, columns, buffer, offset);
        while (data != null && !data.isEmpty()) {
            csv.writeCSVData(data);
            offset += buffer;
            data = cursor.fetchDataBackupObject(table, columns, buffer, offset);
        }
    } */

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
            secondaryController.setAttr(progress, "Exporting CSV", stage, TaskState);

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
}
