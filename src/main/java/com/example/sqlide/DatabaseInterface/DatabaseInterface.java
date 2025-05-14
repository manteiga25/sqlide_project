package com.example.sqlide.DatabaseInterface;

import com.example.sqlide.*;
import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.example.sqlide.drivers.model.DataBase;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.*;

import static com.example.sqlide.popupWindow.handleWindow.*;

public class DatabaseInterface {

    private DataBase DatabaseSeted = null;

    private final String dbName;

    private final ArrayList<TableInterface> TableInterfaceList = new ArrayList<>();

    private final TabPane Container;
    private TabPane DBTabContainer;

    private boolean removing = false;

    public DatabaseInterface(final DataBase GenericDB, final TabPane Container, final String dbName, final mainController ref) {
        DatabaseSeted = GenericDB;
        DatabaseSeted.types = new SQLiteTypes();
        this.Container = Container;
        this.dbName = dbName;
        createTable();
    }

    public void readTables() throws Exception {
        final ArrayList<String> Tables = DatabaseSeted.getTables();
        for (final String t : Tables) {
            TableInterface table = new TableInterface(DatabaseSeted, t, DBTabContainer, this);
            table.createDatabaseTab();
            table.readColumns();
            TableInterfaceList.add(table);
        }
        if (!TableInterfaceList.isEmpty()) {
            TableInterfaceList.getFirst().fetchIfIsPrimeClick(); // faça fetch da primeira tabela
        }
    }

    public HashMap<String, ArrayList<String>> getColumnPrimaryKey(final String TableToIgnore) {
        HashMap<String, ArrayList<String>> KeysList = new HashMap<>();

        for (final TableInterface table : TableInterfaceList) {
            if (!table.TableName.get().equals(TableToIgnore)) {
                System.out.println("pass ");
                KeysList.put(table.TableName.get(), table.getPrimaryKeys());
            }
        }
        return KeysList;
    }

    private void createTable() {
        Tab DBPane = new Tab(dbName);
        DBPane.setId(dbName);

        VBox DBContainer = new VBox(2);

        HBox ButtonsLine = new HBox();

        Button createTab = new Button("create table");
        createTab.setOnAction(e -> createDBTabInterface());
        createTab.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ButtonsModel.css")).toExternalForm());
        createTab.getStyleClass().add("button-64");

        Button deleteTab = new Button("Delete table");
        deleteTab.setOnAction(e -> deleteDBTab());

        ButtonsLine.getChildren().add(createTab);
        ButtonsLine.getChildren().add(deleteTab);

        DBTabContainer = new TabPane();
        DBTabContainer.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TabPane.css")).toExternalForm());
        // DBTabContainer.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());
        DBTabContainer.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            if (!TableInterfaceList.isEmpty() && !removing) {
                TableInterfaceList.get(newValue.intValue()).fetchIfIsPrimeClick();
            }
        });
        DBContainer.getChildren().add(ButtonsLine);
        DBContainer.getChildren().add(DBTabContainer);

        DBPane.setContent(DBContainer);

        Container.getTabs().add(DBPane);
    }

    private void createDBTabInterface() {
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
            secondaryController.NewTableWin(dbName, this, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    public boolean createDBTable(final String Table, final boolean temporary, final boolean rowid) throws SQLException {
        if (!DatabaseSeted.createTable(Table, temporary, rowid)) {
            ShowError("SQL Error", "Error to create Table on Database " + dbName + "\n" + DatabaseSeted.GetException());
            return false;
        }
        createDBTab(Table, !rowid);
        return true;
    }

    @FXML
    public void createDBTab(final String TableName, final boolean rowid) {
        TableInterface table = new TableInterface(DatabaseSeted, TableName, DBTabContainer, this);
        table.createDatabaseTab();
        table.createDBcolContainer(new ColumnMetadata(false, rowid, null, false, null, 0, "INTEGER", "id", false, 0, 0, null));
        if (rowid) {
            table.createRowId();
        }
        TableInterfaceList.add(table);
    }

    @FXML
    public void deleteDBTab() {
        final int indexTab = DBTabContainer.getSelectionModel().getSelectedIndex();
        final TableInterface TableName = TableInterfaceList.get(indexTab);

        if (!ShowConfirmation("Confirmation", "Are you sure to delete Table " + TableName.TableName + "?")) {
            return;
        }

        if (!DatabaseSeted.deleteTable(TableName.TableName.get())) {
            ShowError("ERROR SQL", "Error to delete table " + TableName.TableName + "\n" + DatabaseSeted.GetException());
            return;
        }
        DBTabContainer.getTabs().remove(indexTab);
        TableInterfaceList.remove(indexTab);
    }

    public void deleteTableCallBack(String tableName) {

        for (int tableIndex = 0; tableIndex < TableInterfaceList.size(); tableIndex++) {
            final TableInterface table = TableInterfaceList.get(tableIndex);
            if (table.TableName.get().equals(tableName)) {
                DBTabContainer.getTabs().remove(tableIndex);
                TableInterfaceList.remove(table);
                break;
            }
        }

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

 