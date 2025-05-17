package com.example.sqlide;

import atlantafx.base.controls.Spacer;
import com.example.sqlide.Configuration.DatabaseConf;
import com.example.sqlide.Container.Assistant.AssistantBoxCode;
import com.example.sqlide.DatabaseInterface.DatabaseInterface;
import com.example.sqlide.EventLayout.EditEventController;
import com.example.sqlide.EventLayout.EventController;
import com.example.sqlide.Notification.NotificationController;
import com.example.sqlide.ScriptLayout.SearchScriptController;
import com.example.sqlide.TriggerLayout.EditTriggerController;
import com.example.sqlide.TriggerLayout.TriggerController;
import com.example.sqlide.drivers.SQLite.SQLiteDB;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.Editor.EditorController;
import com.example.sqlide.Editor.FileEditor;
import com.example.sqlide.exporter.CSV.CSVController;
import com.example.sqlide.exporter.Excel.excelController;
import com.example.sqlide.exporter.JSON.JSONController;
import com.example.sqlide.exporter.XML.xmlController;
import com.example.sqlide.popupWindow.Notification;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class mainController {

    public JFXButton AssistantButton;
    public JFXButton NotificationButton;
    public TextArea Console;
    @FXML
    private SplitPane HorizontalSplit;
    @FXML
    private BorderPane BorderContainer;

    @FXML
    private TabPane TableOption;

    @FXML
    private Tab Table1;

    @FXML
    private Tab newTable;

    @FXML
    private Button newCol, openScript, createScript;

    @FXML
    private Menu backupMenu;

    @FXML
    private VBox MessagesBox, NotificationBox = new VBox(5);

    private VBox AssistantContainer, NotificationContainer;

    @FXML
    private SplitPane CenterContainer;

    @FXML
    private Label LabelDB;

    @FXML
    private TextArea MessageBox;
    //private TextField MessageBox;

    @FXML
    private Pane con;

    private TabPane ContainerForDB, ContainerForEditor;

    private Popup MenuPopup;

    public String currentInstance = "";

    private boolean editor = false;

    private final HashMap<String, DatabaseInterface> DBopens = new HashMap<>();

    public HashMap<String, DataBase> DatabaseOpened = new HashMap<>();

    HashMap<String, DatabaseInterface> DBopened = new HashMap<>();

    private final ArrayList<String> ScriptsOpened = new ArrayList<>();

    private final ObservableList<String> DatabasesName = FXCollections.observableArrayList();
    private final ObservableList<DataBase> DatabasesOpened = FXCollections.observableArrayList();

    private String messageAi = "";

    @FXML
    MenuButton queryMenu;

    private boolean created = false;

    private int buffer = 250;

    private Stage primaryStage;

    private final BlockingQueue<String> sender = new LinkedBlockingQueue<>();

    private EditorController editorController = null;

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @FXML
    public void openDBWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("openDatabase.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            OpenDatabaseController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initWin(this, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

   /*     FileChooser selectFileWindow = new FileChooser();
        selectFileWindow.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Database SQL", "*.db"),
                new FileChooser.ExtensionFilter("script SQL", "*.sql"));

        final File selectedFile = selectFileWindow.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            openDB(selectedFile.getAbsolutePath(), selectedFile.getName());
        } */
    }

    @FXML
    public void initialize() {
        Delta dragDelta = new Delta();
        LabelDB.setOnMousePressed(event -> {
            dragDelta.x = event.getSceneX() - LabelDB.getLayoutX();
            dragDelta.y = event.getSceneY() - LabelDB.getLayoutY();
        });

        // Evento de arrastar o mouse: atualiza a posição da BorderPane com base na posição atual do mouse
        LabelDB.setOnMouseDragged(event -> {
            LabelDB.setLayoutX(event.getSceneX() - dragDelta.x);
            LabelDB.setLayoutY(event.getSceneY() - dragDelta.y);
        });

        setHDividerSpace();

        loadNotification();

       // CenterContainer.getItems().remove(AssistentContainer);
      /*  initializeSock();
        NotificationBox.setPadding(new Insets(10, 10, 10, 10));
        initializeNotification();
        for (int i = 0; i < 100; i++) {
            createNotificationBox(new Notification.MessageNotification("dgfiusdgf", "duhf9isudghfiudhgf9weyfuoayhfe8sayiugsdfuisadgfisdgafisadfisagfyhdgsfgasdifbkafuagufefoiabgeefgfuisedahgfousagfuisgfujidgfuigs", 0, LocalTime.now().withNano(0)));
        } */

        Thread waiterMessage = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    final String message = sender.take();
                    Platform.runLater(() -> Console.appendText(message + "\n"));
                }
            } catch (InterruptedException _) {
            }
        });
        waiterMessage.setDaemon(true);
        waiterMessage.start();
    }

    @FXML
    private void MenuButtonOver(MouseEvent event) {

        Button buttonSelected = (JFXButton) event.getSource();

        System.out.println("jhsiudgsuwidg");

        final double buttonOrder = buttonSelected.getViewOrder();

        String text = null;

        if (buttonOrder == 0) {
            text = "Assistant AI";
        } else if (buttonOrder == 1) {
            text = "Notifications";
        }  // more for future

        Label l = new Label(text);
        l.setStyle("-fx-font-size: 16px; -fx-background-color: #3A3A3A; -fx-background-radius: 10px;");
        l.setAlignment(Pos.CENTER);
        l.setPrefWidth(100);
        l.setTextFill(Color.WHITE);
        MenuPopup = new Popup();
        MenuPopup.setAutoHide(true);
        MenuPopup.getContent().add(l);
        Bounds local = buttonSelected.localToScreen(buttonSelected.getBoundsInLocal());
        MenuPopup.show(this.primaryStage, local.getCenterX() - 130, local.getCenterY() - 12);
    }

    @FXML
    private void MenuButtonDisover() {
        if (MenuPopup.isShowing()) {
            MenuPopup.getContent().clear();
            MenuPopup.hide();
        }
    }

    private static class Delta {
        double x, y;
    }

    public mainController() {

    }

    private void initializeSock() {
        Thread NotificationFetcher = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(12345)) {
                System.out.println("Servidor aguardando conexões...");

                // Aguarda uma conexão do cliente
                while (true) {
                    try (Socket clientSocket = serverSocket.accept();
                         ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

                        System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                        // Lê mensagem do cliente
                        Notification.MessageNotification message = (Notification.MessageNotification) in.readObject();
                        System.out.println("Mensagem recebida: " + message);

                        Platform.runLater(() -> createNotificationBox(message));

                    } catch (IOException e) {
                        System.err.println("Erro na conexão com o cliente: " + e.getMessage());
                    } catch (ClassNotFoundException e) {
                        System.err.println(e.getMessage());
                    }
                }

            } catch (IOException e) {
                System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
            }
        });
        NotificationFetcher.setDaemon(true);
        NotificationFetcher.start();

    }

    private void loadNotification() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Notification/NotificationStage.fxml"));
            //    VBox miniWindow = loader.load();
            NotificationContainer = loader.load();

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeNotification() {

        VBox static_cenary = new VBox(10);

        Label titleNot = new Label("Notifications");
        titleNot.setStyle("-fx-font-size: 16px; -fx-text-fill: white; -fx-font-weight: bold;");
        titleNot.setPadding(new Insets(15, 0, 0, 15));

        HBox clear_cenary = new HBox();

        Label timeline = new Label("Timeline");
        timeline.setStyle("-fx-text-fill: #6A6E75; -fx-font-size: 14px;");
        timeline.setPadding(new Insets(0, 0, 0, 15));

        Hyperlink clearLink = new Hyperlink("Clear all");
        clearLink.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/Hyper.css")).toExternalForm());
        clearLink.setStyle("-fx-font-size: 14px");
        clearLink.setOnAction(e -> NotificationBox.getChildren().clear());
        clearLink.setPadding(new Insets(0, 15, 0, 0));

        clear_cenary.getChildren().addAll(timeline, new Spacer(), clearLink);

        static_cenary.getChildren().addAll(titleNot, clear_cenary);

        ScrollPane scrollNotifications = new ScrollPane();
        scrollNotifications.setContent(NotificationBox);
        scrollNotifications.setFitToWidth(true);
        //  scrollNotifications.setFitToHeight(true);
        scrollNotifications.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ScrollHbarStyle.css")).toExternalForm());
        scrollNotifications.setStyle("-fx-background: #2A2A2A; -fx-border-color: #2A2A2A;");

        // NotificationContainer.setStyle("-fx-base: #3C3C3C");

        NotificationContainer.getChildren().addAll(static_cenary, scrollNotifications);

    }

    private void createNotificationBox(final Notification.MessageNotification notification) {
        Pane containerForBorder = new Pane();
        containerForBorder.setStyle("-fx-background-color: #3C3C3C; -fx-background-radius: 10px; -fx-border-radius: 10px;");

        VBox containerNot = new VBox(10);
        containerNot.prefWidthProperty().bind(containerForBorder.widthProperty().subtract(20)); // 10px de margem em cada lado
        containerNot.prefHeightProperty().bind(containerForBorder.heightProperty().subtract(20));

        HBox notificationBox = new HBox(5);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(notificationBox, Priority.ALWAYS);

        Label title = new Label(notification.title());
        title.setStyle("-fx-font-size: 14px; -fx-text-fill: white; -fx-font-weight: bold;");
        title.setPadding(new Insets(0, 0, 0, 15));
        HBox.setHgrow(title, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label date = new Label(notification.date().toString());
        date.setStyle("-fx-text-fill: #6A6E75;");
        date.setAlignment(Pos.CENTER_RIGHT);

        notificationBox.getChildren().addAll(title, spacer, date);

        Label message = new Label(notification.message());
        message.setPadding(new Insets(0, 0, 0, 15));
        message.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");
        message.setWrapText(true);
        message.setPrefHeight(Region.USE_COMPUTED_SIZE);
        VBox.setVgrow(message, Priority.ALWAYS);

        // containerNot.setFillWidth(true);

        containerNot.getChildren().addAll(notificationBox, message);
        containerForBorder.getChildren().add(containerNot);

        NotificationBox.getChildren().addFirst(containerForBorder);
    }

    private void setDividerSpace() {
        if (CenterContainer.getDividers() != null && !CenterContainer.getDividers().isEmpty()) {
            Node divider = CenterContainer.lookup(".split-pane-divider");
            if (divider != null) {
                divider.setStyle("-fx-background-color: BLACK; -fx-pref-width: 2px");
            }
            CenterContainer.getDividers().getFirst().positionProperty().addListener((_, _, newVal) -> {
                if (newVal.doubleValue() < 0.7) {
                    CenterContainer.setDividerPositions(0.7);
                }
            });
        }
        CenterContainer.setDividerPositions(0.7);
    }

    private void setHDividerSpace() {
        if (HorizontalSplit.getDividers() != null && !HorizontalSplit.getDividers().isEmpty()) {
            Node divider = HorizontalSplit.lookup(".split-pane-divider");
            if (divider != null) {
                divider.setStyle("-fx-background-color: BLACK; -fx-pref-width: 2px");
            }
            HorizontalSplit.getDividers().getFirst().positionProperty().addListener((_, _, newVal) -> {
                if (newVal.doubleValue() < 0.7) {
                    HorizontalSplit.setDividerPositions(0.7);
                }
            });
        }
        HorizontalSplit.setDividerPositions(0.7);
    }

    @FXML
    public void SQLiteInterface() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("DBLiteInterface.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            SQLiteController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initWin(this, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openDB(final DataBase db, final String URL, final String DBName, final String UserName, final String password) {
        if (!db.connect(URL, DBName, UserName, password)) {
            ShowError("Error SQL", "Error to open Database " + URL + "\n" + db.GetException());
        }
        db.setMessager(sender);
        DatabaseOpened.put(DBName, db);
        //  final String DBName = db.getDatabaseName();
        createContainerDB();
        db.buffer = buffer;

        DatabaseInterface openDB = new DatabaseInterface(db, ContainerForDB, DBName, this);

        final Thread open = new Thread(()->{
            try {

                openDB.readTables();

                Platform.runLater(this::setDividerSpace);

                DBopens.put(DBName, openDB);
                DBopened.put(DBName, openDB);
                DatabasesOpened.add(db);
                DatabasesName.add(DBName);
            } catch (Exception e) {
                try {
                    openDB.closeInterface();
                    db.disconnect();
                } catch (SQLException _) {
                }
                ShowError("Error Open", "Error to open Database " + DBName + "\n" + e.getMessage());
            }
        });
        open.setDaemon(true);
        open.start();

    }

    public void openDB(final String path, final String DBName) throws IOException {
        DataBase db = new SQLiteDB();
        if (!db.connect(path)) {
            ShowError("Error SQL", "Error to open Database " + path + "\n" + db.GetException());
            return;
        }
        db.setMessager(sender);
        createContainerDB();
        db.buffer = buffer;

        DatabaseInterface openDB = new DatabaseInterface(db, ContainerForDB, DBName, this);

        final Thread open = new Thread(()->{
            try {

                Platform.runLater(()->{
                    openDB.readTables();
                    setDividerSpace();
                });

            DatabaseOpened.put(DBName, db);
            DBopens.put(DBName, openDB);
            DBopened.put(DBName, openDB);
                DatabasesOpened.add(db);
                DatabasesName.add(DBName);
        } catch (Exception e) {
            try {
                openDB.closeInterface();
                db.disconnect();
            } catch (SQLException _) {
            }
            ShowError("Error Open", "Error to open Database " + DBName + "\n" + e.getMessage());
        }
        });
        open.setDaemon(true);
        open.start();
    }

    @FXML
    public void createDB(final String DBName, final Map<String, String> modes) throws IOException {
        //   Map<String, String> modes = new HashMap<>();

    /*    modes.put("encoding", "'" + CharMode.getValue() + "'");
        modes.put("journal_mode", JournalMode.getValue());
        final String innit = ScriptPath.getText();
        if (!innit.isEmpty()) {
            modes.put("innit", ScriptPath.getText());
        }
        modes.put("cache_spill", sharedMode.isSelected() ? "SHARED" : "PRIVATE");
        modes.put("cache_size", cacheSize.getValue().toString()); */
        //   modes.put("page_size", Pagesize.getValue().toString());

        final boolean hasScript = modes.containsKey("innit");

        DataBase db = new SQLiteDB();
        if (!db.connect(DBName, modes)) {
            ShowError("Error SQL", "Error to create Database " + DBName + "\n" + db.GetException());
            return;
        }
        db.setMessager(sender);
        //if (db.Connect(DBName + ".db", modes)) {
        //  System.out.println("sucess");
        //if (!created) {
        createContainerDB();
        db.buffer = buffer;
        //}
        //    createTabDB(DBName);

        DatabaseInterface openDB = new DatabaseInterface(db, ContainerForDB, DBName, this);

        setDividerSpace();

        if (hasScript) {
            try {
                openDB.readTables();
            } catch (Exception e) {
                try {
                    openDB.closeInterface();
                    db.disconnect();
                } catch (SQLException _) {
                }
                ShowError("Error Open", "Error to open Database " + DBName + "\n" + e.getMessage());
                return;
            }
        }

        DatabaseOpened.put(DBName, db);
        DBopens.put(DBName, openDB);
        DBopened.put(DBName, openDB);
        DatabasesOpened.add(db);
        DatabasesName.add(DBName);

    }

    private void createContainerDB() {
        if (!created) {
            created = true;
            backupMenu.setDisable(false);
            queryMenu.setDisable(false);
            ContainerForDB = new TabPane();
            ContainerForDB.setId("ContainerDB");
            ContainerForDB.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TabPane.css")).toExternalForm());
            ContainerForDB.getTabs().addListener((ListChangeListener<Tab>) change -> {
                while (change.next()) {  // Chamando next() para avançar no estado da mudança
                    if (change.wasRemoved()) {  // Verifica se houve remoção de abas
                        try {
                            final String id = change.getRemoved().getFirst().getText();
                            DatabaseInterface DatabaseToClose = DBopens.remove(id);
                            DatabaseToClose.closeInterface();
                            DatabaseOpened.remove(id);
                            DBopened.remove(id);
                            System.gc();
                        } catch (SQLException e) {
                            ShowError("Error SQL", "Error to close database");
                        }
                        if (ContainerForDB.getTabs().isEmpty()) {
                            DBopens.clear();
                            DatabaseOpened.clear();
                            DBopened.clear();
                            removeContainerTab();
                            created = false;
                        }
                    }
                }
            });
            //  BorderContainer.setCenter(ContainerForDB);
            CenterContainer.getItems().removeFirst();
            CenterContainer.getItems().addFirst(ContainerForDB);
        }
    }

    private void removeContainerTab() {
        BorderContainer.getChildren().remove(ContainerForDB);
        ContainerForDB = null;
        //   BorderContainer.setCenter(LabelDB);
        CenterContainer.getItems().removeFirst();
        CenterContainer.getItems().addFirst(LabelDB);
        backupMenu.setDisable(true);
        queryMenu.setDisable(true);
    }

    @FXML
    public void initExcelWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("backup/ExcelExport.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            excelController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initExcelController(DatabaseOpened, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void initCSVWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("backup/CSVExporter.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            CSVController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initCSVController(DatabaseOpened, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void initXMLWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("backup/XmlExporter.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            xmlController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initXmlController(DatabaseOpened, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void initJSONWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("backup/JsonExporter.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            JSONController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initJsonController(DatabaseOpened, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void initScriptWindow(ActionEvent event) {

        final MenuItem item = (MenuItem) event.getSource();

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ScriptLayout/SearchScript.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            SearchScriptController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            if (item.getId() != null) {
                secondaryController.initScriptController(DatabaseOpened, subStage);
            } else {
                System.out.println(ContainerForDB.getSelectionModel().getSelectedItem().getId());
                secondaryController.initScriptController(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()), subStage);
            }

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    public void initTriggerWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("TriggerLayout/SearchTrigger.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            TriggerController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initTriggerController(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()), subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void initEditTriggerWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("TriggerLayout/EditTrigger.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            EditTriggerController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initTriggerWindow(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()));

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void initEventWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("EventLayout/SearchEvent.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            EventController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initEventController(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()), subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void initEditEventWindow() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("EventLayout/EditEvent.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            EditEventController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
            secondaryController.initEventWindow(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()));

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public void OpenAtributesWin() {

        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Configuration/Configuration.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            DatabaseConf secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Subjanela");
            subStage.setScene(new Scene(root));
//            secondaryController.initEventWindow(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()));

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @FXML
    public void OpenAssistent() {

        if (AssistantContainer == null) {
            loadAssistant();
        }

        if (AssistantContainer.getParent() == null) {
            CenterContainer.getItems().add(AssistantContainer);
            if (NotificationContainer.getParent() != null) {
                NotificationButton.setStyle("-fx-background-color: transparent;");
                CenterContainer.getItems().remove(NotificationContainer);
            }
            AssistantButton.setStyle("-fx-background-color: #3574F0;");
            setDividerSpace();
        } else {
            AssistantButton.setStyle("-fx-background-color: transparent;");
            CenterContainer.getItems().remove(AssistantContainer);
        }
    }

    private void loadAssistant() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Assistant/AssistantStage.fxml"));
            //    VBox miniWindow = loader.load();
            AssistantContainer = loader.load();

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void OpenNotifications() {
        if (NotificationContainer.getParent() == null) {
            CenterContainer.getItems().add(NotificationContainer);
            if (AssistantContainer != null) {
                AssistantButton.setStyle("-fx-background-color: transparent;");
                CenterContainer.getItems().remove(AssistantContainer);
            }
            NotificationButton.setStyle("-fx-background-color: #3574F0;");
            setDividerSpace();
        } else {
            NotificationButton.setStyle("-fx-background-color: transparent;");
            CenterContainer.getItems().remove(NotificationContainer);
        }
    }

    @FXML
    public void SendMessage(ActionEvent event) {
        final Button sender = (Button) event.getSource();
        final String message = MessageBox.getText();
        if (message != null && !message.isEmpty()) {
            long num = message.chars().filter(ch -> ch == '\n').count() + 2;
            MessagesBox.getChildren().add(createUserMessageBox(message, num));
            MessageBox.setText("");
            sender.setDisable(true);
            final ProgressIndicator progress = createProgress();
            MessagesBox.getChildren().add(progress);
            final Thread Generator = new Thread(() -> {
                final AssistantBoxCode box = new AssistantBoxCode();
                try {
                    System.out.println(parseMessage(message));
                    // final String generated = removeDeepSeekThink(TakToAi(parseMessage(message) + " (use '```' for programing code)"));
                    final String generated = removeDeepSeekThink(TakToAi(parseMessage(message)));
                    System.out.println(generated);
                    styleAiMessage("Assistant:" + generated, box);
                    //   Platform.runLater(()->MessagesBox.getChildren().add(box));
                    messageAi += "User: " + message + "\n" + "ChatBot:" + generated + "\n";
                } catch (Exception e) {
                    //   Platform.runLater(()->MessagesBox.getChildren().add(box.addErrorMessage("Error to generate response\n" + e.getMessage())));
                    Platform.runLater(() -> box.addErrorMessage("Error to generate response\n" + e.getMessage()));

                } finally {
                    Platform.runLater(() -> {
                        MessagesBox.getChildren().add(box);
                        MessagesBox.getChildren().remove(progress);
                    });
                    sender.setDisable(false);
                }
            });
            Generator.setDaemon(true);
            Generator.start();
        }
    }

    private void styleAiMessage(String messageAi, final AssistantBoxCode container) {
        if (messageAi.contains("```")) {
            String copy = messageAi;
            while (true) {
                int startIndex = copy.indexOf("```");
                if (startIndex == -1) {
                    // Não há mais delimitadores, adiciona o restante como mensagem
                    if (!copy.trim().isEmpty()) {
                        container.addMessage(copy);
                    }
                    break;
                }

                // Texto antes do bloco de código
                String beforeCode = copy.substring(0, startIndex);
                if (!beforeCode.trim().isEmpty()) {
                    container.addMessage(beforeCode);
                }

                // Remove o texto processado e o delimitador de abertura
                copy = copy.substring(startIndex + 3);

                int endIndex = copy.indexOf("```");
                if (endIndex == -1) {
                    // Se não encontrar o delimitador de fechamento, trata o restante como código
                    if (!copy.trim().isEmpty()) {
                        container.addCode(copy);
                    }
                    break;
                }

                // Extrai o bloco de código
                String codeBlock = copy.substring(0, endIndex);
                container.addCode(codeBlock);

                // Remove o bloco de código e o delimitador de fechamento
                copy = copy.substring(endIndex + 3).replaceFirst("\n", "");
                //       int indexInnit = messageAi.indexOf("```");
                //     int indexEnd = messageAi.lastIndexOf("```");
                //   container.addMessage(messageAi.substring(0, indexInnit));
                // container.addCode(messageAi.substring(indexInnit, indexEnd - 1));
                // container.addMessage(messageAi.substring(indexEnd));
            }
        } else {
            container.addMessage(messageAi);
        }
    }

    private ProgressIndicator createProgress() {
        final ProgressIndicator progress = new ProgressIndicator();
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        return progress;
    }

    private String parseMessage(final String message) {
        return message.replace("\"", "").replace("\n", "\\\\n");
        //   return message;
    }

    private TextArea createUserMessageBox(final String message, final long lines) {
        final TextArea messageBox = new TextArea();
        messageBox.setEditable(false);
        messageBox.setPrefRowCount(2);
        messageBox.setPrefHeight(lines * 20.0 + 14);
        messageBox.setPrefWidth(lines * 20.0 + 14);
        messageBox.setText("User:\n" + message);
        VBox.setMargin(messageBox, new Insets(0, 0, 0, 100));
        VBox.setVgrow(messageBox, Priority.ALWAYS);
        messageBox.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #3A3A3A; -fx-border-width: 5; -fx-control-inner-background: #3A3A3A; -fx-background-color: #3A3A3A;");
        return messageBox;
    }

    private TextArea createAiMessageBox(final String message, final long lines) {
        final TextArea messageBox = new TextArea();
        messageBox.setEditable(false);
        messageBox.setPrefHeight(lines * 20.0 + 14);
        messageBox.setPrefWidth(lines * 20.0 + 14);
        messageBox.setText("Assistent: " + message);
        VBox.setMargin(messageBox, new Insets(0, 100, 0, 0));
        VBox.setVgrow(messageBox, Priority.ALWAYS);
        messageBox.setStyle("-fx-background-radius: 10; -fx-border-radius: 10; -fx-border-color: #3A3A3A; -fx-border-width: 5; -fx-control-inner-background: #3A3A3A; -fx-background-color: #3A3A3A;");
        return messageBox;
    }

    private String removeDeepSeekThink(String messageAi) {
        final int index = messageAi.indexOf("</think>");
        if (index != -1) {
            return messageAi.substring(index + 9);
        }
        return messageAi;
    }

    private String TakToAi(final String message) throws InterruptedException, IOException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .header("Content-Type", "application/json; charset=utf-16")
                .header("Accept", "application/json")
                //.POST(HttpRequest.BodyPublishers.ofString(String.format("{\"model\": \"%s\", \"prompt\":\"%s\", \"stream\": false}", "codellama", message), StandardCharsets.UTF_8))
                //  .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"model\": \"%s\", \"prompt\":\"%s\", \"stream\": false}", "moondream", message), StandardCharsets.UTF_8))
                .POST(HttpRequest.BodyPublishers.ofString(String.format("{\"model\": \"%s\", \"prompt\":\"%s\", \"stream\": false}", "deepseek-r1:1.5b", message), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        return new JSONObject(response.body()).getString("response");

       /* URL url = new URL("http://localhost:11434/api/generate");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-16");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        JSONObject jsonResponse = getJsonObject(message, conn);
        conn.disconnect();
        System.out.println(jsonResponse.getString("response"));
        return jsonResponse.getString("response"); */

    }

    @NotNull
    private JSONObject getJsonObject(String message, HttpURLConnection conn) throws IOException {
        String jsonInputString = String.format("{\"model\": \"%s\", \"prompt\":\"%s\", \"stream\": false}", "codellama", message);
        OutputStream os = conn.getOutputStream();
        byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            response.append(line);
        }
        in.close();

        return new JSONObject(response.toString());
    }

    @FXML
    public void SwitchFetch(ActionEvent event) {
        final MenuItem widget = (MenuItem) event.getSource();
        final int size = Integer.parseInt(widget.getText());
        if (size != buffer) {
            for (final String dbName : DatabaseOpened.keySet()) {
                DatabaseOpened.get(dbName).buffer = size;
                DBopens.get(dbName).refreshSearch();
            }
            buffer = size;
        }
    }

    @FXML
    public void SwitchToDB() {
        if (editor) {
            editor = false;
            openScript.setDisable(true);
            createScript.setDisable(true);
            CenterContainer.getItems().removeFirst();
            if (ContainerForDB != null) {
                //     BorderContainer.setCenter(ContainerForDB);
                CenterContainer.getItems().addFirst(ContainerForDB);
            } else {
                CenterContainer.getItems().addFirst(LabelDB);
                //  BorderContainer.setCenter(LabelDB);
            }
            setDividerSpace();
        }
    }

    @FXML
    public void SwitchToEditor() {
        if (!editor) {
            editor = true;
            //   if (ContainerForDB != null) {
            //     BorderContainer.getChildren().remove(ContainerForDB);
            CenterContainer.getItems().removeFirst();

            // }
            openScript.setDisable(false);
            createScript.setDisable(false);
            if (editorController == null) {
              //  createEditorPane();
                loadEditor();
            }
            // BorderContainer.setCenter(ContainerForEditor);
            CenterContainer.getItems().addFirst(editorController.getContainer());
            setDividerSpace();
            //  BorderContainer.setCenter(new Label("editor"));
        }
    }

    private void loadEditor() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Editor/EditorStage.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            editorController = loader.getController();
            editorController.setList(DatabasesOpened, DatabasesName);

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createEditorPane() {
        ContainerForEditor = new TabPane();
        ContainerForEditor.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TabPane.css")).toExternalForm());
        ContainerForEditor.getTabs().addListener((ListChangeListener<Tab>) change -> {
            while (change.next()) {
                if (change.wasRemoved()) {
                    ScriptsOpened.remove(change.getRemoved().getFirst().getId());
                } else if (change.wasAdded()) {
                    final Tab FolderTab = change.getAddedSubList().getFirst();
                    ScriptsOpened.add(FolderTab.getId());
                    ContainerForEditor.getSelectionModel().select(FolderTab);
                }
            }
        });
    }

    @FXML
    private void setOpenScript() {
        editorController.setOpenScript();
    }

    @FXML
    private void createScript() {
        editorController.createScript();
    }

    private void createFolderEditor(final String path, final String name) {
        Tab folderTab = new Tab();
        folderTab.setId(path);
        folderTab.setText(name);
        try {
            FileEditor editor = new FileEditor(path);
            //    editor.loadFile();
            //  editor.attachToTab(folderTab);
            editor.readScript();
            editor.putContainer(folderTab);
            ContainerForEditor.getTabs().add(folderTab);
        } catch (IOException e) {
            ShowError("Error to read", "Error to read script " + path + ".\n");
        }
    }
}