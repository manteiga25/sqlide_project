package com.example.sqlide.exporter.JSON;

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
import org.controlsfx.control.action.Action;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import static com.example.sqlide.Metadata.ColumnMetadata.MetadataToMap;
import static com.example.sqlide.popupWindow.handleWindow.*;

public class JSONController implements NotificationInterface, loadingInterface {

    @FXML
    private JFXCheckBox MetaBox;

    @FXML
    TextField PathBox, nameOfFolder;

    Stage stage;

    DataBase db;

    private Stage loadingStage;

    private Stage advancedFetcherstage;

    private Thread writer, fetcher;

    private String finalPath;

    private HashMap<String, String> QueryList = new HashMap<>();

    private HashMap<String, ArrayList<String>> TablesAndColumnsNames;

    private HashMap<String, ArrayList<ColumnMetadata>> TablesAndColumns;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    private ArrayList<HashMap<String, String>> data = new ArrayList<>();

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
    private void initialize() throws InterruptedException {
        writeSem.acquire();
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

    public void initJsonController(final DataBase db, final TaskInterface taskInterface, final Stage stage) {
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
        if (new File(path + ".json").exists() || new File(path).exists()) {
            if (!ShowConfirmation("File exists", "The file " + path + " exists\n" + "Are you sure to continue?")) {
                return;
            }
        }

        closeWindow();

        path = removeDot(path);

           // PrepareJson(json, tables, cursor);
        String finalPath1 = path + ".json";
      //  setLoadingStage();
        final Task<Void> exporterTask = new Task<>() {

            @Override
            protected void failed() {
                super.failed();
                createErrorNotification(db.getDatabaseName() + "-json-1", "Json Error", "Error to export Json.\n"+getException().getMessage());
                ShowError("Json", "Error to create Json.\n" + getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                ShowSucess("Exporting Json", "Success to export database to Json.");
                createSuccessNotification(db.getDatabaseName() + "-json-1", "Json Success", "Success to export Json.", finalPath);
            }

            @Override
            protected void running() {
                super.running();
                updateTitle("Exporting Json");
                updateProgress(0, 100);
                progress.addListener((_,_, val)->updateProgress(val.longValue(), 100));
            }

            @Override
            protected Void call() throws Exception {
                    //   Platform.runLater(this::setLoadingStage);
                 //   createLoading(db.getDatabaseName()+"-json", "Export to JSON", "Exporting data to JSON");
                    SqlToJson json = new SqlToJson(db.getDatabaseName(), finalPath1);
                    final ArrayList<String> tables = new ArrayList<>(TablesAndColumnsNames.keySet());
                    prepareWork(json, tables, db);
                return null;
            }

            @Override
            protected void cancelled() {
                super.cancelled();
                Notification.showInformationNotification("Cancel export", "Json export canceled");
                cancelTask();
            }

        };
        taskInterface.addTask(exporterTask);
        Thread.ofVirtual().start(exporterTask);
    }

    private void prepareWork(final SqlToJson json, final ArrayList<String> tables, final DataBase cursor) throws InterruptedException, IOException {
        final long buffer = cursor.buffer * 10L;
        final double interact = (100.0 / tables.size()) / 100.0;
        for (final String sheetName : tables) {

            final String query = QueryList.get(sheetName);
            if (!query.isEmpty()) {

                if (MetaBox.isSelected()) createMetadata(json, sheetName);

                json.createJsonArrayTable(sheetName);
                final ArrayList<String> columns = TablesAndColumnsNames.get(sheetName);

                writer = new Thread(() -> {
                    while (!Thread.currentThread().isInterrupted()) {
                        try {
                            writeSem.acquire();
                      //      json.flushData();
                        } catch (InterruptedException e) {
                            try {
                                json.write(data);
                                //   json.flushData();
                            } catch (Exception _) {
                            }
                            data = null;
                            fetcherSem.release();
                            break;
                        }
                        try {
                            json.write(data);
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
                        ArrayList<HashMap<String, String>> dataCopy = cursor.Fetcher().fetchDataMap(query, columns, buffer, offset);
                        if (dataCopy == null || dataCopy.isEmpty()) {
                            writer.interrupt();
                            break;
                        }
                        final ArrayList<HashMap<String, String>> copy = (ArrayList<HashMap<String, String>>) dataCopy.clone();
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

                //   json.endTable();

             //   json.flushDataAndEndTable();

             //   Platform.runLater(() -> progress.set(progress.get() + interact));

                progress.set(progress.get() + interact);

            //    updateLoading(db.getDatabaseName()+"-json", progress.get());

              //  System.out.println("acabou");

            }
        }
    //    json.EndFetch();
        json.close();
    }

    private void createMetadata(final SqlToJson json, final String sheetName) throws IOException {
        final ArrayList<ColumnMetadata> columns = TablesAndColumns.get(sheetName);
        //  final ArrayList<String> columns = TablesAndColumns.get(sheetName);
        json.createJsonArrayTable(sheetName+"-metadata");
        final ArrayList<HashMap<String, String>> ArrayMeta = new ArrayList<>();
        for (final ColumnMetadata meta : columns) {
            ArrayMeta.add(MetadataToMap(meta));
        }
        json.write(ArrayMeta);
    //    json.flushDataAndEndTable();
     //   json.writeData(ArrayMeta);
    }

    private void createNot(final boolean state) throws IOException {

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
            Notification.showSuccessNotification(db.getDatabaseName()+"-json", "Json Success", "Success to export Json", function);
        } else {
            Notification.showErrorNotification(db.getDatabaseName()+"-json", "Json Error", "Error to export Json");
        }


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
            subStage.setTitle("JSON");
            subStage.initOwner(stage);
            subStage.setResizable(false);
            subStage.setScene(new Scene(root));
            secondaryController.setAttr(progress, "Exporting Json", stage, this);

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
