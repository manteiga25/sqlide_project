package com.example.sqlide.exporter.JSON;

import com.example.sqlide.Container.loading.loadingController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.popupWindow.Notification;
import com.jfoenix.controls.JFXButton;
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
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.controlsfx.control.TaskProgressView;
import org.controlsfx.control.action.Action;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;

import static com.example.sqlide.popupWindow.handleWindow.ShowConfirmation;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class JSONController {

    @FXML
    private JFXButton folderButton;

    @FXML
    ComboBox<String> DatabaseBox;

    @FXML
    TextField PathBox, nameOfFolder;

    Stage stage;

    HashMap<String, DataBase> db;

    private Stage loadingStage;

    private Thread flushTask;

    private String finalPath;

    private final BooleanProperty TaskState = new SimpleBooleanProperty(true);

    private final DoubleProperty progress = new SimpleDoubleProperty(0);

    private ArrayList<HashMap<String, String>> data = new ArrayList<>();

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

    public void initialize() throws InterruptedException {
        writeSem.acquire();
        TaskState.addListener((observable -> cancelTask()));

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

    public void initJsonController(final HashMap<String, DataBase> db, final Stage stage) {
        this.db = db;
        this.stage = stage;
        for (final String dbName : db.keySet()) {
            DatabaseBox.getItems().add(dbName);
        }
    }

    @FXML
    private void createBackup() {

        final String dbSelected = DatabaseBox.getValue();

        if (dbSelected == null) {
            ShowError("No selected", "You need to select Database to make a backup.");
            DatabaseBox.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            return;
        }

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

        final DataBase cursor = db.get(dbSelected);

           // PrepareJson(json, tables, cursor);
        String finalPath1 = path + ".json";
        new Thread(()->{
            boolean state = false;
            try {
                Platform.runLater(this::setLoadingStage);
                SqlToJson json = new SqlToJson(removeDot(dbSelected), finalPath1);
                final ArrayList<String> tables = cursor.getTables();
                finalPath = finalPath1;
                prepareWork(json, tables, cursor);
                state = true;
            //    if (!json.save(finalPath + ".json")) {
              //      ShowError("Error generate", "Error to generate json file.\n" + json.GetException());
                //}
            } catch (Exception e) {
                ShowError("Json", "Error to convert SQL to JSON.\n " + e.getMessage());
            } finally {
                final boolean finalState = state;
                Platform.runLater(()->
                {
                    loadingStage.close();
                    createNot(finalState);
                });
            }


        }).start();
    }

    private void prepareWork(final SqlToJson json, final ArrayList<String> tables, final DataBase cursor) throws InterruptedException, IOException {
        final long buffer = cursor.buffer * 10L;
        final double interact = (100.0 / tables.size()) / 100.0;
        for (final String sheetName : tables) {
            json.createJsonArrayTable(sheetName);
            final ArrayList<String> columns = cursor.getColumnsName(sheetName);

            final Thread writer = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        writeSem.acquire();
                        json.flushData();
                    } catch (InterruptedException e) {
                        try {
                            json.write(data);
                         //   json.flushData();
                        } catch (Exception _) {
                        }
                        data = null;
                        fetcherSem.release();
                        break;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
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

            final Thread fetcher = new Thread(() -> {
                long offset = 0;
                while (true) {
                    ArrayList<HashMap<String, String>> dataCopy = cursor.fetchDataMap(sheetName, columns, buffer, offset);
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

            json.flushDataAndEndTable();

            Platform.runLater(()->progress.set(progress.get()+interact));

            System.out.println("acabou");

        }
        json.EndFetch();
        json.close();
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
            Notification.showSuccessNotification("Json Success", "Success to export Json", function);
        } else {
            Notification.showErrorNotification("Json Error", "Error to export Json");
        }


    }

    private void PrepareJson(final SqlToJson json, final ArrayList<String> tables, final DataBase cursor) {
        new Thread(()-> {
            for (final String sheetName : tables) {
                json.createJsonArrayTable(sheetName);
                final ArrayList<String> columns = cursor.getColumnsName(sheetName);
                try {
                    addData(sheetName, columns, json, cursor);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            flushTask.start();
        }).start();
    }

    private void addData(final String sheetName, final ArrayList<String> columns, final SqlToJson json, final DataBase cursor) throws IOException {
        long offset = 0;
        final long buffer = cursor.buffer * 10L;
        ArrayList<HashMap<String, String>> data = cursor.fetchDataMap(sheetName, columns, buffer, offset);
        while (data != null) {
            json.write(data);
            offset += 250;
            data = cursor.fetchDataMap(sheetName, columns, buffer, offset);
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
            secondaryController.setAttr(progress, "Exporting Json", stage, TaskState);

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

    }
}
