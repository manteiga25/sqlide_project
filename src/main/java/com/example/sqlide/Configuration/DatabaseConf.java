package com.example.sqlide.Configuration;

import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.SQLTypes;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class DatabaseConf {

    public Button reloadBtn;
    public Button saveBtn;
    @FXML
    private TreeView<String> TreeViewContainer;

    @FXML
    private StackPane MenuContainer;

    private DataBase database;

    private List<String> metadataList;

    public void setDatabase(DataBase database, List<String> metadataList) {
        this.database = database;
        this.metadataList = metadataList;
    }

    @FXML
    public void initialize() {

        TreeItem<String> conf = new TreeItem<>("Configuration");

        TreeItem<String> mem = new TreeItem<>("Memory"), permission = new TreeItem<>("Permission"), schema = new TreeItem<>("Schema"), IO = new TreeItem<>("I/O"), performance = new TreeItem<>("Synchronization/Perf"), debug = new TreeItem<>("Debug"), connection = new TreeItem<>("Connection");

        conf.getChildren().addAll(schema, permission, mem, IO, performance, debug, connection);

        TreeViewContainer.setRoot(conf);

        TreeViewContainer.getSelectionModel().selectedItemProperty().addListener((_, oldItem, newItem) -> {
            if (newItem != null && !newItem.equals(oldItem)) {
                switch (newItem.getValue()) {
                    case "Memory":
                        initializeMem();
                        break;
                    case "Permission":
                        initializePermission();
                        break;
                    case "Schema":
                        initializeSchema();
                        break;
                    case "I/O":
                        initializeIO();
                        break;
                    case "Synchronization/Perf":
                        initializePerformance();
                        break;
                    case "Debug":
                        initializeDebug();
                        break;
                    case "Connection":
                        initializeConnection();
                        break;
                }
            }
        });

    }

    public void initializeMem() {
        if (database.Memory() == null) {
            showUnsupported("Memory settings",
                    database.getSQLType() + " does not expose memory tuning through this driver.");
            return;
        }
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("memoryConf.fxml"));
            //    VBox miniWindow = loader.load();
            Node root = loader.load();

            HBox.setHgrow(root, Priority.ALWAYS);

            memoryConfController secondaryController = loader.getController();
            secondaryController.setDatabase(database.Memory());

            reloadBtn.setOnAction(_->secondaryController.initialize_configuration());
            saveBtn.setOnAction(_->Thread.ofVirtual().start(()->{
                saveBtn.setDisable(true);
                secondaryController.save_settings();
                saveBtn.setDisable(false);
            }));

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializePermission() {
        // O SQLite não tem contas, e o Access não expõe a sua segurança pelo UCanAccess.
        // Antes o Permission() dos motores sem implementação devolvia null e o painel
        // rebentava com NullPointerException em vez de dizer o que se passava.
        if (database.Permission() == null) {
            showUnsupported("User permissions",
                    database.getSQLType() + " does not expose user accounts through this driver.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("permissionConf.fxml"));
            Node root = loader.load();

            HBox.setHgrow(root, Priority.ALWAYS);

            permissionConfController controller = loader.getController();
            controller.setDatabase(database.Permission(), database.getUsername(), database.getDatabaseName(), metadataList);

            reloadBtn.setOnAction(_ -> controller.initialize_configuration());
            // O painel tem o seu próprio botão de gravar, mas o da barra passa a funcionar
            // também, em vez de ficar permanentemente desativado.
            saveBtn.setDisable(false);
            saveBtn.setOnAction(_ -> controller.save_settings());

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);
        } catch (Exception e) {
            e.printStackTrace();
            ShowError("Error", "Could not open the permissions panel.", e.getMessage());
        }
    }

    /** Painel de substituição para as secções que o motor ligado não suporta. */
    private void showUnsupported(String section, String reason) {
        VBox box = new VBox(8);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #2c2c2c;");

        Label title = new Label(section + " is not available");
        title.setStyle("-fx-text-fill: #E0E0E0; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label detail = new Label(reason);
        detail.setStyle("-fx-text-fill: #9A9A9A; -fx-font-size: 11px;");
        detail.setWrapText(true);

        box.getChildren().addAll(title, detail);
        HBox.setHgrow(box, Priority.ALWAYS);

        reloadBtn.setOnAction(null);
        saveBtn.setOnAction(null);
        saveBtn.setDisable(true);

        MenuContainer.getChildren().clear();
        MenuContainer.getChildren().add(box);
    }

    private void initializeDebug() {
        if (database.Debug() == null) {
            showUnsupported("Debug settings",
                    database.getSQLType() + " does not expose debug tracing through this driver.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("DebufConf.fxml"));
            Node root = loader.load();

            HBox.setHgrow(root, Priority.ALWAYS);

            DebugConf controller = loader.getController();
            controller.setDebug(database.Debug());

            reloadBtn.setOnAction(_->controller.initialize_configuration());
            saveBtn.setOnAction(_->Thread.ofVirtual().start(()->{
                saveBtn.setDisable(true);
                controller.save_settings();
                saveBtn.setDisable(false);
            }));
           // controller.setDatabase(database, metadataList);

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeIO() {
        if (database.IO() == null) {
            showUnsupported("I/O settings",
                    database.getSQLType() + " does not expose I/O tuning through this driver.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("IOConf.fxml"));
            Node root = loader.load();

            HBox.setHgrow(root, Priority.ALWAYS);

            IOConf controller = loader.getController();
            controller.setIo(database.IO());

            reloadBtn.setOnAction(_->controller.initialize_configuration());
            saveBtn.setOnAction(_->Thread.ofVirtual().start(()->{
                saveBtn.setDisable(true);
                controller.save_settings();
                saveBtn.setDisable(false);
            }));

           // controller.setDatabase(database, metadataList);

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializePerformance() {
        if (database.Performance() == null) {
            showUnsupported("Performance settings",
                    database.getSQLType() + " does not expose these knobs through this driver.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("PerformanceConf.fxml"));
            Node root = loader.load();

            HBox.setHgrow(root, Priority.ALWAYS);

            PerformanceConf controller = loader.getController();
            controller.setPerformance(database.Performance());

            reloadBtn.setOnAction(_->controller.initialize_configuration());
            saveBtn.setOnAction(_->Thread.ofVirtual().start(()->{
                saveBtn.setDisable(true);
                controller.save_settings();
                saveBtn.setDisable(false);
            }));
           // controller.setDatabase(database, metadataList);

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeSchema() {
        if (database.Schema() == null) {
            showUnsupported("Schema settings",
                    database.getSQLType() + " does not expose schema pragmas through this driver.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("schemaConf.fxml"));
            Node root = loader.load();

            HBox.setHgrow(root, Priority.ALWAYS);

            schemaConf controller = loader.getController();
            controller.setSchemaInterface(database.Schema());

            reloadBtn.setOnAction(_->controller.initialize_configuration());
            saveBtn.setOnAction(_->Thread.ofVirtual().start(()->{
                saveBtn.setDisable(true);
                controller.save_settings();
                saveBtn.setDisable(false);
            }));
            //controller.setDatabase(database, metadataList);

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeConnection() {
        if (database.Connection() == null) {
            showUnsupported("Connection settings",
                    database.getSQLType() + " does not expose a connection timeout through this driver.");
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ConnectionConf.fxml"));
            Node root = loader.load();

            HBox.setHgrow(root, Priority.ALWAYS);

            ConnectionConf controller = loader.getController();
            controller.setConnection(database.Connection());

            reloadBtn.setOnAction(_->controller.initialize_configuration());
            saveBtn.setOnAction(_->Thread.ofVirtual().start(()->{
                saveBtn.setDisable(true);
                controller.save_settings();
                saveBtn.setDisable(false);
            }));
            //controller.setDatabase(database, metadataList);

            MenuContainer.getChildren().clear();
            MenuContainer.getChildren().add(root);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
