package com.example.sqlide;

import com.example.sqlide.DatabaseInterface.DatabaseInterface;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.apache.poi.ss.formula.functions.Column;
import org.apache.poi.ss.formula.functions.T;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class NewTable {

    @FXML
    private TableColumn<TableColumnMeta, String> NameColumn, TypeColumn, KeyColumn;
    @FXML
    private TableColumn<TableColumnMeta, Boolean> NotColumn;
    @FXML
    private TableView<TableColumnMeta> TableColumns;

    private final ObservableList<TableColumnMeta> items = FXCollections.observableArrayList(new TableColumnMeta("ID", "INTEGER", "PRIMARY KEY", true));
    private final ArrayList<ColumnMetadata> columnsMetadata = new ArrayList<>();
    private DatabaseInterface ref;
    private Stage window;

    @FXML
    private Label DataBaseLabel, Error;

    @FXML
    private TextField TableNameInput;

    @FXML
    CheckBox TempBox, RowIDBox;

    public NewTable() {
        columnsMetadata.add(new ColumnMetadata(true, true,null, false, "", 0, "INTEGER", "ID", true, 0, 0, ""));
    }

    @FXML
    private void initialize() {
        TableColumns.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        NameColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        TypeColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType()));
        KeyColumn.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getKey()));
        NotColumn.setCellValueFactory(cellData -> new SimpleBooleanProperty(cellData.getValue().isNotNull()));

        // Associa os dados à TableView
        TableColumns.setItems(items);
    }

    public void NewTableWin(final String DBName, final DatabaseInterface ref, final Stage subStage) {
        DataBaseLabel.setText(DBName);
        this.ref = ref;
        window = subStage;
    }

    @FXML
    public void TableName() throws SQLException {
        final String TableName = TableNameInput.getText();
        if (TableName.isEmpty()) {
            Error.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            Error.setText("'" + TableName + "'" + " is invalid name");
            return;
        }

        if (ref.createDBTable(TableName, TempBox.isSelected(), RowIDBox.isSelected())) {
            closeWindow();
         //   ref.createDBColContainer(TableName, new ColumnMetadata(false, false, null, false, null, 0, "INTEGER", "id", false, 0, 0));
        }
    }

    @FXML
    private void EditColumn() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/NewColumn.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            NewColumn secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Create Column");
            subStage.setScene(new Scene(root));
     //       secondaryController.NewColumnWin("", "", this, subStage, context.getColumnPrimaryKey(TableName.get()), Database.types, Database.getList(), Database.getListChars(), Database.getIndexModes());

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    private void RemoveColumn() {
        final ObservableList<TableColumnMeta> item = TableColumns.getSelectionModel().getSelectedItems();
        items.removeAll(item);
    }

   // @FXML
  //  private void addItem

    @FXML
    private void closeWindow() {
        window.close();
    }


    private static class TableColumnMeta {

        private String Name, Type, Key;
        private boolean NotNull;

        public TableColumnMeta(final String Name, final String Type, final String Key, final boolean NotNull) {
            this.Name = Name;
            this.Type = Type;
            this.Key = Key;
            this.NotNull = NotNull;
        }

        public TableColumnMeta(final HashMap<String, String> column) {
            this.Name = column.get("Name");
            this.Type = column.get("Type");
            this.Key = column.get("Key");
            this.NotNull = Boolean.parseBoolean(column.get("NotNull"));
        }


        public String getName() {
            return Name;
        }

        public void setName(String name) {
            Name = name;
        }

        public String getType() {
            return Type;
        }

        public void setType(String type) {
            Type = type;
        }

        public String getKey() {
            return Key;
        }

        public void setKey(String key) {
            Key = key;
        }

        public boolean isNotNull() {
            return NotNull;
        }

        public void setNotNull(boolean notNull) {
            NotNull = notNull;
        }
    }

}
