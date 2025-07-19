package com.example.sqlide.DatabaseInterface;

import com.example.sqlide.*;
import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.Email.EmailController;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.Report.ReportController;
import com.example.sqlide.Task.TaskInterface;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.neural.NeuralController;
import com.jfoenix.controls.JFXButton;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

import static com.example.sqlide.popupWindow.handleWindow.*;

public class DatabaseInterface {

    private DataBase DatabaseSeted = null;

    private final String dbName;

    private final ArrayList<TableInterface> TableInterfaceList = new ArrayList<>();

    final Tab DBPane;

    private TabPane DBTabContainer;

    private boolean removing = false;

    private final TaskInterface taskInterface;

    public String getCurrentTable() {
        return DBTabContainer.getSelectionModel().getSelectedItem().getText();
    }

    public Tab getContainer() {
        return DBPane;
    }

    public TaskInterface getTaskInterface() {
        return taskInterface;
    }

    public HashMap<String, ArrayList<String>> getColumnPrimaryKeyName(final String tableToIgnore) {
        final HashMap<String, ArrayList<String>> list = new HashMap<>();
        for (final TableInterface table : TableInterfaceList) {
            if (table.getTableName().get().equals(tableToIgnore)) continue;
            list.put(table.getTableName().get(), table.getPrimaryKeys());
        }
        return list;
    }

    public DatabaseInterface(final DataBase GenericDB, final String dbName, final TaskInterface taskInterface) {
        DatabaseSeted = GenericDB;
        DatabaseSeted.types = new SQLiteTypes();
        this.dbName = dbName;
        this.DBPane = new Tab(dbName);
        this.DBPane.setId(dbName);
        this.taskInterface = taskInterface;
        createTable();
    }

    public void readTables() throws SQLException {
        final ArrayList<String> Tables = DatabaseSeted.getTables();
        final ArrayList<ViewController.View> views = DatabaseSeted.getViews();
        for (final String t : Tables) {
            final ArrayList<ViewController.View> TableView = new ArrayList<>();
            for (final ViewController.View view : views) if (view.code.get().contains(" " + t + " ")) TableView.add(view);
            final TableInterface table = new TableInterface(DatabaseSeted, t, DBTabContainer, this);
            table.getTableMetadata().addViews(TableView);
        //    Platform.runLater(table::createDatabaseTab);
            table.readColumns();
            TableInterfaceList.add(table);


        }

        if (!TableInterfaceList.isEmpty()) {
            TableInterfaceList.getFirst().fetchIfIsPrimeClick(); // faça fetch da primeira tabela
        }

    }

    public void openEmailStage(final String content) {
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Email/emailStage.fxml"));
            Parent root = loader.load();

            EmailController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Send email");
            subStage.setScene(new Scene(root));
            secondaryController.setText(content);
            secondaryController.setDb(DatabaseSeted);
            secondaryController.setTablesAndColumns(getColumnsNames());
            //secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public void openReportStage(final String title, final String query) {

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Report.fxml"));
            Parent root = loader.load();

            ReportController dialogController = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Configure Report");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
          /*  if (generateReportButton != null && generateReportButton.getScene() != null) {
                dialogStage.initOwner(generateReportButton.getScene().getWindow());
            } */

            dialogController.initializeDialog(DatabaseSeted, dialogStage);
            dialogController.setTable(TableInterfaceList.get(DBTabContainer.getSelectionModel().getSelectedIndex()).getTableName().get(), getColumnsNames());
            dialogController.setTitle(title);

            dialogStage.setScene(new Scene(root));
            dialogStage.show();

            if (query != null) dialogController.setQuery(query);

        } catch (IOException e) {
            ShowError("Load Error", "Could not load report configuration dialog.", e.getMessage());
        }
    }

    public void openViewStage(final String name, final String code) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/View/ViewInterface.fxml"));
            Parent root = loader.load();

            ViewController dialogController = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("View");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogController.initViewController(DatabaseSeted, taskInterface, dialogStage);
            dialogController.addView(new ViewController.View(name, "", code));
            dialogStage.setScene(new Scene(root));
            dialogStage.show();

        } catch (IOException | SQLException e) {
            ShowError("Load Error", "Could not load view stage.", e.getMessage());
        }
    }

    private void openTrainStage() {

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/NeuralEngine/NeuralEngineStage.fxml"));
            Parent root = loader.load();

            NeuralController dialogController = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Configure Report");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
          /*  if (generateReportButton != null && generateReportButton.getScene() != null) {
                dialogStage.initOwner(generateReportButton.getScene().getWindow());
            } */

         //   dialogController.initializeDialog(DatabaseSeted, dialogStage);
           // dialogController.setTable(TableInterfaceList.get(DBTabContainer.getSelectionModel().getSelectedIndex()).getTableName().get(), getColumnsNames());

            dialogStage.setScene(new Scene(root));
            dialogStage.show();

        } catch (IOException e) {
            ShowError("Load Error", "Could not load report configuration dialog.", e.getMessage());
        }
    }

    public HashMap<String, ArrayList<String>> getColumnPrimaryKey(final String TableToIgnore) {
        HashMap<String, ArrayList<String>> KeysList = new HashMap<>();

        for (final TableInterface table : TableInterfaceList) {
            if (!table.getTableName().get().equals(TableToIgnore)) {
                System.out.println("pass ");
                KeysList.put(table.getTableName().get(), table.getPrimaryKeys());
            }
        }
        return KeysList;
    }

    public HashMap<String, ArrayList<String>> getColumnsNames() {
        final HashMap<String, ArrayList<String>> ColumnsList = new HashMap<>();

        for (final TableInterface table : TableInterfaceList) {
                ColumnsList.put(table.getTableName().get(), table.getColumnsMetadataName());
        }
        return ColumnsList;
    }

    private void createTable() {

        VBox DBContainer = new VBox();

        DBContainer.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/DarkButton.css")).toExternalForm());

        HBox ButtonsLine = new HBox(5);
        ButtonsLine.setPadding(new Insets(5,0,5,5));

        JFXButton undoButton = new JFXButton();
        undoButton.setTooltip(new Tooltip("Undo"));
        FontAwesomeIconView iconUndo = new FontAwesomeIconView(FontAwesomeIcon.UNDO);
        iconUndo.setSize("1.0em");
        iconUndo.setFill(Color.WHITE);
        undoButton.setGraphic(iconUndo);
        undoButton.setOnAction(_ -> {
            try {
                DatabaseSeted.back();
            } catch (SQLException e) {
                ShowError("SQL Error", "Error to undo database.", e.getMessage());
            }
        });

        JFXButton redoButton = new JFXButton();
        FontAwesomeIconView iconRedo = new FontAwesomeIconView(FontAwesomeIcon.REPEAT);
        iconRedo.setSize("1.0em");
        iconRedo.setFill(Color.WHITE);
        redoButton.setGraphic(iconRedo);
        redoButton.setOnAction(_ -> {
            try {
                DatabaseSeted.redo();
            } catch (SQLException e) {
                ShowError("SQL Error", "Error to redo database.", e.getMessage());
            }
        });

        JFXButton save = new JFXButton();
        save.setTooltip(new Tooltip("Save"));
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.SAVE);
        icon.setSize("1.0em");
        icon.setFill(Color.WHITE);
        save.setGraphic(icon);
        save.setOnAction(e -> commit());

        JFXButton createTab = new JFXButton("create table");
        createTab.setOnAction(e -> createDBTabInterface("", null, null));

        JFXButton deleteTab = new JFXButton("Delete table");
        deleteTab.setOnAction(e -> deleteDBTab());

        JFXButton ViewButton = new JFXButton("Manage View");
        ViewButton.setOnAction(_->openViewStage("", ""));

        JFXButton SendEmailButton = new JFXButton("Send email");
        SendEmailButton.setOnAction(e -> openEmailStage(""));

        JFXButton ReportButton = new JFXButton("Create Report");
        ReportButton.setOnAction(e -> openReportStage("Report", null));

        JFXButton TrainButton = new JFXButton("Create Model");
        TrainButton.setOnAction(e -> openTrainStage());

        ButtonsLine.getChildren().addAll(undoButton, save, createTab, deleteTab, ViewButton, SendEmailButton, ReportButton, TrainButton);

        DBTabContainer = new TabPane();
        VBox.setVgrow(DBTabContainer, Priority.ALWAYS);
        DBTabContainer.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/TabPane.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());
        DBTabContainer.getSelectionModel().selectedIndexProperty().addListener((_, _, newValue) -> {
            if (!TableInterfaceList.isEmpty() && !removing) {
                TableInterfaceList.get(newValue.intValue()).fetchIfIsPrimeClick();
            }
        });
        DBContainer.getChildren().addAll(ButtonsLine, DBTabContainer);

        DBPane.setContent(DBContainer);
    }

    private void commit() {
        try {
            DatabaseSeted.commit();
        } catch (SQLException e) {
            ShowError("Error to save", "Error to commit.\n" + e.getMessage());
        }
    }

    public void createDBTabInterface(final String Table, final ArrayList<HashMap<String, String>> column, final String check) {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/newTable.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            NewTable secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Create Table");
            subStage.setResizable(false);
            subStage.setScene(new Scene(root));
            secondaryController.NewTableWin(dbName, this, DatabaseSeted,subStage);
            secondaryController.setTable(Table);
            secondaryController.setColumns(column);
            secondaryController.setCheck(check);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public boolean createDBTable(final TableMetadata metadata, final boolean temporary, final boolean rowid) throws SQLException {
        final Stage loading = LoadingStage("Creating Table", "This operation can execute long time.");
        if (!DatabaseSeted.createTable(metadata, temporary, rowid)) {
            loading.close();
            ShowError("SQL Error", "Error to create Table on Database " + dbName, DatabaseSeted.GetException());
            return false;
        }
        createDBTab(metadata.getName());
        loading.close();
        return true;
    }

    @FXML
    public void createDBTab(final String TableName) throws SQLException {
        TableInterface table = new TableInterface(DatabaseSeted, TableName, DBTabContainer, this);
      //  table.createDatabaseTab();
        table.readColumns();
      /*  table.createDBcolContainer(new ColumnMetadata(false, rowid, new ColumnMetadata.Foreign(), null, 0, "INTEGER", "id", false, 0, 0, null));
        if (rowid) {
            table.createRowId();
        } */
        TableInterfaceList.add(table);
    }

    @FXML
    public void deleteDBTab() {
        final int indexTab = DBTabContainer.getSelectionModel().getSelectedIndex();
        final TableInterface TableName = TableInterfaceList.get(indexTab);
        final String tableName = TableName.getTableName().get();

        if (!ShowConfirmation("Confirmation", "Are you sure to delete Table " + tableName + "?")) {
            return;
        }

        final Stage loader = LoadingStage("Delete Table", "Deleting Table " + tableName);

        Task<Void> deleteTask = new Task<Void>() {

            @Override
            protected void running() {
                super.running();
                updateTitle("Deleting table");
                updateMessage("This operation can be slower.");
                updateProgress(-1,-1);
            }

            @Override
            protected Void call() throws Exception {
                if (!DatabaseSeted.deleteTable(tableName)) throw new SQLException(DatabaseSeted.GetException());
                return null;
            }

            @Override
            protected void failed() {
                super.failed();
                ShowError("ERROR SQL", "Error to delete table " + tableName, this.getException().getMessage());
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                DBTabContainer.getTabs().remove(indexTab);
                TableInterfaceList.remove(indexTab);
            }

            @Override
            protected void done() {
                super.done();
                Platform.runLater(loader::close);
            }
        };
        taskInterface.addTask(deleteTask);
        Thread.ofVirtual().start(deleteTask);
    }

    public void deleteTableCallBack(String tableName) {

        for (int tableIndex = 0; tableIndex < TableInterfaceList.size(); tableIndex++) {
            final TableInterface table = TableInterfaceList.get(tableIndex);
            if (table.getTableName().get().equals(tableName)) {
                DBTabContainer.getTabs().remove(tableIndex);
                TableInterfaceList.remove(table);
                break;
            }
        }

    }

    public TableInterface getTable(final String table) {
        for (final TableInterface tableInterface : TableInterfaceList) {
            if (tableInterface.getTableName().get().equals(table)) {
                return tableInterface;
            }
        }
        return null;
    }

    public HashMap<String, ArrayList<HashMap<String, String>>> getTables() {
        final HashMap<String, ArrayList<HashMap<String, String>>> meta = new HashMap<>();
            for (final TableInterface tableInterface : TableInterfaceList) meta.put(tableInterface.getTableName().get(), tableInterface.getColumnsMetadataMap());
            return meta;
    }

    public void refreshSearch() {
        for (final TableInterface table : TableInterfaceList) {
            table.setTotalPages();
        }
    }

    public void closeInterface() throws SQLException {
        removing = true;
        //  DatabaseSeted.disconnect();
        for (int i = 0; i < TableInterfaceList.size(); i++) {
            TableInterface tableToClose = TableInterfaceList.remove(i);
            tableToClose.closeColumns();
        }
    }
}

 