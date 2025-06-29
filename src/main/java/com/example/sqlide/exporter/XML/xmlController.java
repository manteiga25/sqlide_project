package com.example.sqlide.exporter.XML;

import com.example.sqlide.AdvancedSearch.TableAdvancedSearchController;
import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.Container.loading.loadingController;
import com.example.sqlide.Container.loading.loadingInterface;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.exporter.CSV.SqlToCSV;
import com.example.sqlide.popupWindow.Notification;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
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
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import static com.example.sqlide.ColumnMetadata.MetadataToArrayList;
import static com.example.sqlide.ColumnMetadata.MetadataToMap;
import static com.example.sqlide.popupWindow.handleWindow.*;

public class xmlController implements loadingInterface {

    @FXML
    private JFXCheckBox MetaBox;

    @FXML
    TextField PathBox, FileName;

    @FXML
    CheckBox Mult, RowBox;

    Stage stage;

    private Stage loadingStage;

    DataBase db;

    private SqlToXml xml = new SqlToXml();

    private HashMap<String, String> QueryList = new HashMap<>();

    private HashMap<String, ArrayList<String>> TablesAndColumnsNames;

    private HashMap<String, ArrayList<ColumnMetadata>> TablesAndColumns;

    private Stage advancedFetcherstage;

    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    private Thread main;

    private ArrayList<ArrayList<Object>> data = new ArrayList<>();

    private final Semaphore fetcherSem = new Semaphore(1);
    private final Semaphore writeSem = new Semaphore(1);

    private Thread writer, fetcher;

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

    public void initXmlController(final DataBase db, final Stage stage) throws InterruptedException {
        this.db = db;
        this.stage = stage;
        writeSem.acquire();
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
    }

    @FXML
    private void createBackup() throws Exception {

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
        if (new File(path + ".xml").exists() || new File(path).exists()) {
            if (!ShowConfirmation("File exists", "The file " + path + " exists\n" + "Are you sure to continue?")) {
                return;
            }
        }

        closeWindow();

        path = removeDot(path);

        xml = new SqlToXml();

        String finalPath = path;
        setLoadingStage();
        main = new Thread(()->{
            boolean state = false;
            try {

                Platform.runLater(this::setLoadingStage);

                final ArrayList<String> tables = new ArrayList<>(TablesAndColumnsNames.keySet());

                if (Mult.isSelected()) {
                    prepareWorkMultiple(db.getDatabaseName(), db, tables, finalPath);
                } else {
                    prepareWorkSingle(db.getDatabaseName(), db, tables, finalPath);
                }
                state = true;
                ShowSucess("Exporting xml", "Success to export database to XML.");
            } catch (Exception e) {
                //   throw new  RuntimeException();
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        xml.abort();
                    } catch (Exception _) {
                    }
                    ShowError("XML", "Error to create XML.\n " + e.getMessage());
                    throw new RuntimeException(e);
                }
            }
            finally {
                final boolean finalState = state;
                Platform.runLater(()->{
                    loadingStage.close();
                    try {
                        createNot(finalState);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    stage.close();
                });
            }
        });
        main.start();
    }

    private void prepareWorkSingle(final String dbSelected, final DataBase cursor, final ArrayList<String> tables, final String path) throws Exception {
        final double interact = (double) (100 / tables.size()) / 100;
        xml.createXML(removeDot(dbSelected), path);
        for (final String sheetName : tables) {
            final String query = QueryList.get(sheetName);
            if (!query.isEmpty()) {
                if (MetaBox.isSelected()) createMetadata(path, sheetName);
                xml.createTableChild(sheetName);
                final ArrayList<String> columns = TablesAndColumnsNames.get(sheetName);
                final boolean hasPrimeKey = cursor.TableHasPrimeKey(sheetName);
                if (RowBox.isSelected() && !hasPrimeKey) {
                    xml.setRow(true);
                    columns.addFirst(cursor.getRowId());
                }
                xml.setStructure(columns);

                writer = new Thread(() -> writer(xml));

                fetcher = new Thread(() -> {
                    try {
                        fetcher(cursor, columns, sheetName, writer);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                fetcher.start();
                writer.start();

                fetcher.join();
                writer.join();

                xml.flushIntermediate();

                Platform.runLater(() -> progress.set(progress.get() + interact));

                System.out.println("acabou");

            }
        }
        xml.close();
        //xml.flush(path);
    }

    private void prepareWorkMultiple(final String dbSelected, final DataBase cursor, final ArrayList<String> tables, final String path) throws Exception {
        final double interact = (100.0 / tables.size()) / 100.0;
        for (final String sheetName : tables) {
            final String query = QueryList.get(sheetName);
            if (!query.isEmpty()) {
                final SqlToXml xml = new SqlToXml();
                xml.createXML(removeDot(dbSelected), path + "-" + sheetName);
                if (MetaBox.isSelected()) createMetadata(xml, sheetName);
                xml.createTableChild(sheetName);
                final ArrayList<String> columns = cursor.getColumnsName(sheetName);
                final boolean hasPrimeKey = cursor.TableHasPrimeKey(sheetName);
                if (RowBox.isSelected() && !hasPrimeKey) {
                    xml.setRow(true);
                    columns.addFirst(cursor.getRowId());
                }
                xml.setStructure(columns);

                writer = new Thread(() -> writer(xml));

                fetcher = new Thread(() -> {
                    try {
                        fetcher(cursor, columns, sheetName, writer);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                fetcher.start();
                writer.start();

                System.out.println(sheetName + " " + 6);

                fetcher.join();
                writer.join();

                System.out.println("foi");

                xml.close();

                // throw new RuntimeException();

                System.out.println("acabou");

                //  xml.flushIntermediate(path + "-" + sheetName);

            }
            Platform.runLater(() -> progress.set(progress.get() + interact));
        }
    }

    private void writer(final SqlToXml xml) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                writeSem.acquire();
            } catch (InterruptedException e) {
                try {
                    xml.addData(data);
                } catch (Exception _) {
                }
                data = null;
                fetcherSem.release();
                break;
            }
            try {
                xml.addData(data);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            data = null;
            fetcherSem.release();
        }
    }

    private void fetcher(final DataBase cursor, final ArrayList<String> columns, final String sheetName, final Thread writer) throws Exception {
        long offset = 0;
        final long buffer = cursor.buffer * 10L;
        while (true) {
            final ArrayList<ArrayList<Object>> dataCopy = cursor.Fetcher().fetchDataBackupObject(QueryList.get(sheetName), columns, buffer, offset);
            if (dataCopy == null) {
                throw new Exception(cursor.GetException());
            }
            if (dataCopy.isEmpty()) {
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
    }

    private void createMetadata(String Path, final String sheetName) throws Exception {
        final SqlToXml xml = new SqlToXml();
        xml.createXML("", Path);
        xml.createTableChild(sheetName + "-metadata");
        final ArrayList<ColumnMetadata> columns = TablesAndColumns.get(sheetName);
        //  final ArrayList<String> columns = TablesAndColumns.get(sheetName);
        xml.setStructure(new ArrayList<>(MetadataToMap(columns.getFirst()).keySet()));
        final ArrayList<ArrayList<Object>> ArrayMeta = new ArrayList<>();
        for (final ColumnMetadata meta : columns) {
            ArrayMeta.add(MetadataToArrayList(meta));
        }
        xml.addData(ArrayMeta);
        xml.flushIntermediate();
    }

    private void createMetadata(SqlToXml xml, final String sheetName) throws Exception {
        xml.createTableChild(sheetName + "-metadata");
        final ArrayList<ColumnMetadata> columns = TablesAndColumns.get(sheetName);
        //  final ArrayList<String> columns = TablesAndColumns.get(sheetName);
        xml.setStructure(new ArrayList<>(MetadataToMap(columns.getFirst()).keySet()));
        final ArrayList<ArrayList<Object>> ArrayMeta = new ArrayList<>();
        for (final ColumnMetadata meta : columns) {
            ArrayMeta.add(MetadataToArrayList(meta));
        }
        xml.addData(ArrayMeta);
        xml.flushIntermediate();
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
            Notification.showSuccessNotification(db.getDatabaseName()+"-xml", "XML Success", "Success to export XML", function);
        } else {
            Notification.showErrorNotification(db.getDatabaseName()+"-xml", "XML Error", "Error to export XML");
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

    private void setLoadingStage() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/loadingPane/loadingLayout.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            loadingController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("XML");
            subStage.initOwner(stage);
            subStage.setResizable(false);
            subStage.setScene(new Scene(root));
            secondaryController.setAttr(progress, "Exporting XML", stage, this);

            subStage.initModality(Modality.NONE);

            // Mostrar a subjanela
            subStage.show();
            loadingStage = subStage;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelTask() {
        fetcher.interrupt();
        writer.interrupt();
        main.interrupt();
    }

    @Override
    public void close() {
        cancelTask();
    }
}