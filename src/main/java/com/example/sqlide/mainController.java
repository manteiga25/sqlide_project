package com.example.sqlide;

import com.example.sqlide.Assistant.AssistantController;
import com.example.sqlide.Configuration.DatabaseConf;
import com.example.sqlide.Console.ConsoleController;
import com.example.sqlide.DatabaseInterface.DatabaseInterface;
import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.EventLayout.EditEventController;
import com.example.sqlide.EventLayout.EventController;
import com.example.sqlide.Logger.Logger;
import com.example.sqlide.ScriptLayout.SearchScriptController;
import com.example.sqlide.TriggerLayout.EditTriggerController;
import com.example.sqlide.TriggerLayout.TriggerController;
import com.example.sqlide.drivers.SQLite.SQLiteDB;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.Editor.EditorController;
import com.example.sqlide.drivers.model.SQLTypes;
import com.example.sqlide.exporter.CSV.CSVController;
import com.example.sqlide.exporter.Excel.excelController;
import com.example.sqlide.exporter.JSON.JSONController;
import com.example.sqlide.exporter.XML.xmlController;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.*;

import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static com.example.sqlide.popupWindow.handleWindow.*;

public class mainController implements requestInterface {

    public JFXButton AssistantButton;
    public JFXButton NotificationButton;
    @FXML
    private SplitPane HorizontalSplit;
    @FXML
    private BorderPane BorderContainer;

    @FXML
    private Button openScript, createScript;

    @FXML
    private MenuItem backupMenu;

    @FXML
    private VBox MessagesBox, NotificationBox = new VBox(5);

    private VBox AssistantContainer, NotificationContainer;

    @FXML
    private SplitPane CenterContainer;

    @FXML
    private Label LabelDB;

    private TabPane ContainerForDB, ContainerForEditor;

    private Popup MenuPopup;

    public String currentInstance = "";

    private boolean editor = false;

    public HashMap<String, DataBase> DatabaseOpened = new HashMap<>();

    private final HashMap<String, DatabaseInterface> DBopened = new HashMap<>();

    private final ArrayList<String> ScriptsOpened = new ArrayList<>();

    private final ObservableList<String> DatabasesName = FXCollections.observableArrayList();
    private final ObservableList<DataBase> DatabasesOpened = FXCollections.observableArrayList();

    @FXML
    private Menu queryMenu;
  //  MenuButton queryMenu;

    private boolean created = false;

    private int buffer = 250;

    private Stage primaryStage;

    private EditorController editorController = null;

    private ConsoleController consoleController = null;

    private final SimpleStringProperty currentDB = new SimpleStringProperty();

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    @Override
    public boolean ShowData(final String Query, final String table) {
        final DatabaseInterface db = DBopened.get(currentDB.get());

        if (db != null) {
            final TableInterface tableInterface = db.getTable(table);

            if (tableInterface != null) {
                return tableInterface.ShowData(Query);
            } else return false;
        } else return false;
    }

    @Override
    public ArrayList<HashMap<String, String>> getData(final String query, final String table) {

        final DataBase db = DatabaseOpened.get(currentDB.get());

        return db.fetchDataMap(query, db.buffer, 0);

    }

    @Override
    public boolean createTable(String table, ArrayList<HashMap<String, String>> meta) {
        final DatabaseInterface db = DBopened.get(currentDB.get());
        if (db != null) {
            Platform.runLater(()->db.createDBTabInterface(table, meta));
            return true;
        }
        return false;
    }

    @Override
    public HashMap<String, ArrayList<HashMap<String, String>>> getTableMetadata() {
        final DatabaseInterface db = DBopened.get(currentDB.get());
        HashMap<String, ArrayList<HashMap<String, String>>> meta = null;
        if (db != null) {
            meta = db.getTables();
        }
        return meta;
    }

    @Override
    public boolean sendEmail(final String body) {
        final DatabaseInterface db = DBopened.get(currentDB.get());

        if (db != null) {
            Platform.runLater(()->db.openEmailStage(body));
            return true;
        }
        return false;
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
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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

        setHDividerSpace();

        loadNotification();
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

    private void loadNotification() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Notification/NotificationStage.fxml"));
            //    VBox miniWindow = loader.load();
            NotificationContainer = loader.load();

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
        }
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
                if (newVal.doubleValue() < 0.8) {
                    HorizontalSplit.setDividerPositions(0.8);
                }
            });
        }
        HorizontalSplit.setDividerPositions(0.8);
    }

    @FXML
    private void createDBInterface() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("createDatabase.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            CreateDatabaseController secondaryController = loader.getController();

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
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
        }
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
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
        }
    }

    public void openDB(final DataBase db, final String URL, final String DBName, final String UserName, final String password) {
        if (!db.connect(URL, DBName, UserName, password)) {
            ShowError("Error SQL", "Error to open Database " + URL + "\n" + db.GetException());
        }

        for (final DataBase dataBase : DatabasesOpened) {
            if (dataBase.getUrl().equals(db.getUrl())) return;
        }

        final BlockingQueue<Logger> sender = new LinkedBlockingQueue<>();
        db.setMessager(sender);
        //  final String DBName = db.getDatabaseName();
        createContainerDB();
        db.buffer = buffer;

        DatabaseInterface openDB = new DatabaseInterface(db, ContainerForDB, DBName, this);

        final Stage loader = LoadingStage("Opening Database.", "You can continue t se application.");

        final Thread open = new Thread(()->{
            try {

                openDB.readTables();

                if (consoleController == null) Platform.runLater(this::loadConsole);

                Platform.runLater(this::setDividerSpace);

                DBopened.put(DBName, openDB);
                DatabasesOpened.add(db);
                DatabaseOpened.put(DBName, db);
                DatabasesName.add(DBName);
                currentDB.set(DBName);
            } catch (Exception e) {
                try {
                    openDB.closeInterface();
                    db.disconnect();
                } catch (SQLException _) {
                }
                ShowError("Error Open", "Error to open Database " + DBName + "\n" + e.getMessage());
            } finally {
                Platform.runLater(loader::close);
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

        for (final DataBase dataBase : DatabasesOpened) {
            if (dataBase.getUrl().equals(db.getUrl())) return;
        }

        final BlockingQueue<Logger> sender = new LinkedBlockingQueue<>();

        db.setMessager(sender);
        createContainerDB();
        db.buffer = buffer;

        final Stage loader = LoadingStage("Opening Database.", "You can continue t se application.");

        DatabaseInterface openDB = new DatabaseInterface(db, ContainerForDB, DBName, this);

        if (consoleController == null) loadConsole();

        final Thread open = new Thread(()->{
            try {

                openDB.readTables();


                Platform.runLater(this::setDividerSpace);
                Platform.runLater(this::setHDividerSpace);

                Platform.runLater(()->consoleController.addData(DBName, sender, db.getUrl(), db.getSQLType()));
            DatabaseOpened.put(DBName, db);
            DBopened.put(DBName, openDB);
                DatabasesOpened.add(db);
                DatabasesName.add(DBName);
                currentDB.set(DBName);
        } catch (Exception e) {
            try {
                openDB.closeInterface();
                db.disconnect();
            } catch (SQLException _) {
            }
            ContainerForDB.getTabs().remove(ContainerForDB.getSelectionModel().selectedItemProperty().get());
            ShowError("Error Open", "Error to open Database " + DBName + "\n" + e.getMessage());
        } finally {
            Platform.runLater(loader::close);
        }
        });
        open.setDaemon(true);
        open.start();
    }

    public void createDB(final DataBase db, final String url, final String DBName, final String user, final String pass, final Map<String, String> modes) throws IOException {
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

        if (!db.CreateSchema(url, DBName, user, pass)) {

        }

        if (!db.connect(DBName, modes)) {
            ShowError("Error SQL", "Error to create Database " + DBName + "\n" + db.GetException());
            return;
        }
        // db.setMessager(sender);
        //if (db.Connect(DBName + ".db", modes)) {
        //  System.out.println("sucess");
        //if (!created) {
        createContainerDB();
        db.buffer = buffer;
        //}
        //    createTabDB(DBName);

        DatabaseInterface openDB = new DatabaseInterface(db, ContainerForDB, DBName, this);

        if (consoleController == null) Platform.runLater(this::loadConsole);

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
        DBopened.put(DBName, openDB);
        DatabasesOpened.add(db);
        DatabasesName.add(DBName);
        currentDB.set(DBName);

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
       // db.setMessager(sender);
        //if (db.Connect(DBName + ".db", modes)) {
        //  System.out.println("sucess");
        //if (!created) {
        createContainerDB();
        db.buffer = buffer;
        //}
        //    createTabDB(DBName);

        DatabaseInterface openDB = new DatabaseInterface(db, ContainerForDB, DBName, this);

        if (consoleController == null) Platform.runLater(this::loadConsole);

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
        DBopened.put(DBName, openDB);
        DatabasesOpened.add(db);
        DatabasesName.add(DBName);
        currentDB.set(DBName);

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
                            DatabaseInterface DatabaseToClose = DBopened.remove(id);
                            DatabaseOpened.remove(id);
                           // DatabasesOpened.remove(ContainerForDB.getTabs().indexOf(change.getRemoved().getFirst()));
                            DatabasesOpened.clear(); // errata
                            DatabasesName.clear();
                            DatabasesOpened.addAll(DatabaseOpened.values());
                            DatabasesName.addAll(DatabaseOpened.keySet());
                            if (DatabaseToClose != null) DatabaseToClose.closeInterface();
                        } catch (SQLException e) {
                            ShowError("Error SQL", "Error to close database");
                        }
                        if (ContainerForDB.getTabs().isEmpty()) {
                            DatabaseOpened.clear();
                            DatabasesOpened.clear();
                            HorizontalSplit.getItems().removeLast();
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
            secondaryController.initExcelController(DatabaseOpened.get(currentDB.get()), subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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
            secondaryController.initCSVController(DatabaseOpened.get(currentDB.get()), subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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
            secondaryController.initXmlController(DatabaseOpened.get(currentDB.get()), subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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
            secondaryController.initJsonController(DatabaseOpened.get(currentDB.get()), subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
        }

    }

    @FXML
    public void initEditTriggerWindow() {

        final DataBase dataBase = DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId());

        final HashMap<String, String> routines = dataBase.getTriggers();

        if (!routines.isEmpty()) {
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
                secondaryController.initTriggerWindow(dataBase, routines);

                // Opcional: definir a modalidade da subjanela
                subStage.initModality(Modality.APPLICATION_MODAL);

                // Mostrar a subjanela
                subStage.show();
            } catch (Exception e) {
                ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
            }
        } else ShowInformation("No routines", "The database has no routines to edit.");

    }

    @FXML
    public void initEventWindow() {

        final DataBase dataBase = DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId());

        if (dataBase.getSQLType() != SQLTypes.SQLITE) {

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
            secondaryController.initEventController(dataBase, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
        }
        } else ShowError("No supported", "SQLite doesn't support event's.");

    }

    @FXML
    public void initEditEventWindow() {

        final DataBase dataBase = DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId());

        if (dataBase.getSQLType() != SQLTypes.SQLITE) {

            final HashMap<String, String> routines = dataBase.getEvents();

            if (!routines.isEmpty()) {

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
                    secondaryController.initEventWindow(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()), routines);

                    // Opcional: definir a modalidade da subjanela
                    subStage.initModality(Modality.APPLICATION_MODAL);

                    // Mostrar a subjanela
                    subStage.show();
                } catch (Exception e) {
                    ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
                }
            } else ShowInformation("No routines", "The database has no routines to edit.");
        } else ShowError("No supported", "SQLite doesn't support event's.");

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
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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

            AssistantController controller = loader.getController();
            controller.setAssistantFunctionsInterface(this);

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
        }
    }

    private void loadConsole() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("Console/ConsoleStage.fxml"));
            //    VBox miniWindow = loader.load();
            VBox container = loader.load();

            consoleController = loader.getController();
            consoleController.currentDBProperty().bind(currentDB);

            HorizontalSplit.getItems().add(container);

            // Criar um novo Stage para a subjanela
        } catch (Exception e) {
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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
    public void SwitchFetch(ActionEvent event) {
        final MenuItem widget = (MenuItem) event.getSource();
        final int size = Integer.parseInt(widget.getText());
        if (size != buffer) {
            for (final String dbName : DatabaseOpened.keySet()) {
                DatabaseOpened.get(dbName).buffer = size;
                DBopened.get(dbName).refreshSearch();
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
            ShowError("Error to load", "Error tom load stage.\n" + e.getMessage());
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
}