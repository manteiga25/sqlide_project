package com.example.sqlide;

import com.example.sqlide.DatabaseInterface.DatabaseInterface;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.SQLTypes;
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

import java.sql.SQLException;
import java.util.*;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Janela "Create table".
 *
 * <p>O que estava mal: o botão "delete" tirava a coluna da tabela visível mas não da lista
 * que era gravada, por isso a coluna apagada era criada na mesma — e o "edit" seguinte
 * passava a editar a coluna errada. O nome da tabela e das colunas não era validado e o
 * "ROW ID" aparecia em motores onde não quer dizer nada.</p>
 */
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
    private DataBase context;

    @FXML
    private Label DataBaseLabel, Error;

    @FXML
    private TextField TableNameInput, CheckField;

    @FXML
    private CheckBox TempBox, RowIDBox;

    public NewTable() {
        columnsMetadata.add(new ColumnMetadata(true, true, new ColumnMetadata.Foreign(), "", 0, "INTEGER", "ID", false, 0, 0, null));
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

    public void NewTableWin(final String DBName, final DatabaseInterface ref, final DataBase context, final Stage subStage) {
        DataBaseLabel.setText(DBName);
        this.ref = ref;
        this.context = context;
        window = subStage;
        // "WITHOUT ROWID" só existe no SQLite.
        final boolean sqlite = context.getSQLType() == SQLTypes.SQLITE;
        RowIDBox.setVisible(sqlite);
        RowIDBox.setManaged(sqlite);
    }

    @FXML
    public void TableName() throws SQLException {
        final String TableName = TableNameInput.getText() == null ? "" : TableNameInput.getText().trim();
        final String TableCheck = CheckField.getText();
        if (TableName.isEmpty()) {
            showNameError("'" + TableName + "'" + " is invalid name");
            return;
        }
        if (ref.getTable(TableName) != null) {
            showNameError("Table " + TableName + " already exists");
            return;
        }
        if (columnsMetadata.isEmpty()) {
            showNameError("A table needs at least one column");
            return;
        }
        final Set<String> names = new HashSet<>();
        for (final ColumnMetadata column : columnsMetadata) {
            if (!names.add(column.Name.toLowerCase(Locale.ROOT))) {
                showNameError("Column " + column.Name + " appears twice");
                return;
            }
        }
        if (!RowIDBox.isSelected() && RowIDBox.isVisible() && columnsMetadata.stream().noneMatch(c -> c.IsPrimaryKey)) {
            showNameError("A table without ROW ID needs a primary key");
            return;
        }
        Error.setText("");
        Error.setStyle("");

        final TableMetadata meta = new TableMetadata(TableName);
        meta.addColumns(columnsMetadata);
        meta.setCheck(TableCheck);

        if (ref.createDBTable(meta, TempBox.isSelected(), RowIDBox.isSelected() || !RowIDBox.isVisible())) {
            closeWindow();
         //   ref.createDBColContainer(TableName, new ColumnMetadata(false, false, null, false, null, 0, "INTEGER", "id", false, 0, 0));
        }
    }

    private void showNameError(final String message) {
        Error.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
        Error.setText(message);
    }

    /** Nomes das colunas já na lista, menos a que se está a editar. */
    private List<String> otherColumnNames(final int editing) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < columnsMetadata.size(); i++) if (i != editing) names.add(columnsMetadata.get(i).Name);
        return names;
    }

    /** Colunas que se podem referenciar: as das outras tabelas e as chaves desta que ainda não existe. */
    private Map<String, ColumnMetadata> referenceableColumns() {
        final LinkedHashMap<String, ColumnMetadata> columns = new LinkedHashMap<>(ref.getReferenceableColumns());
        final String name = TableNameInput.getText() == null ? "" : TableNameInput.getText().trim();
        for (final ColumnMetadata column : columnsMetadata) {
            if (column.IsPrimaryKey || column.isUnique) columns.put(name + ": " + column.Name, column);
        }
        return columns;
    }

    private HashMap<String, ArrayList<String>> foreignKeys() {
        final HashMap<String, ArrayList<String>> keys = ref.getColumnPrimaryKeyName("");
        final String name = TableNameInput.getText() == null ? "" : TableNameInput.getText().trim();
        // Uma coluna pode apontar para a própria tabela (um funcionário e o seu chefe).
        if (!name.isEmpty()) {
            final ArrayList<String> own = new ArrayList<>();
            for (final ColumnMetadata column : columnsMetadata) if (column.IsPrimaryKey) own.add(column.Name);
            if (!own.isEmpty()) keys.put(name, own);
        }
        return keys;
    }

    @FXML
    private void EditColumn() {
        final int selected = TableColumns.getSelectionModel().getSelectedIndex();
        if (selected >= 0 && selected < columnsMetadata.size()) {
            try {
                // Carrega o arquivo FXML
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/NewColumn.fxml"));
                //    VBox miniWindow = loader.load();
                Parent root = loader.load();

                NewColumn secondaryController = loader.getController();

                // Criar um novo Stage para a subjanela
                Stage subStage = new Stage();
                subStage.setTitle("Edit Column");
                subStage.setScene(new Scene(root));
                secondaryController.NewColumnWin(context.getDatabaseName(), TableNameInput.getText(), this, subStage, foreignKeys(), context.types, context.getDatabaseInfo());
                secondaryController.setReferencedColumns(referenceableColumns());
                secondaryController.setExistingColumns(otherColumnNames(selected));
                secondaryController.insertMetadata(columnsMetadata.get(selected));
                //       secondaryController.NewColumnWin("", "", this, subStage, context.getColumnPrimaryKey(TableName.get()), Database.types, Database.getList(), Database.getListChars(), Database.getIndexModes());

                // Opcional: definir a modalidade da subjanela
                subStage.initModality(Modality.APPLICATION_MODAL);

                // Mostrar a subjanela
                subStage.show();
            } catch (Exception e) {
                ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
            }
        } else {
            ShowInformation("No selected", "No column selected to edit.");
        }
    }

    /** Tira as colunas das duas listas (a que se vê e a que é gravada), pela posição. */
    @FXML
    private void RemoveColumn() {
        final List<Integer> selected = new ArrayList<>(TableColumns.getSelectionModel().getSelectedIndices());
        selected.sort(Comparator.reverseOrder());
        for (final int index : selected) {
            if (index < 0 || index >= items.size()) continue;
            items.remove(index);
            columnsMetadata.remove(index);
        }
    }

   // @FXML
  //  private void addItem

    @FXML
    private void createDBColInterface() {
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
            //  subStage.setMaxWidth(620);
            //subStage.setMaxHeight(420);
        //    secondaryController.NewColumnWin(context.getDatabaseName(), TableNameInput.getText(), this, subStage, ref.getColumnPrimaryKeyName(""), context.types, context.getList(), context.getListChars(), context.getIndexModes(), context.getSQLType(), context.getForeignModes());
            secondaryController.NewColumnWin(context.getDatabaseName(), TableNameInput.getText(), this, subStage, foreignKeys(), context.types, context.getDatabaseInfo());
            secondaryController.setReferencedColumns(referenceableColumns());
            secondaryController.setExistingColumns(otherColumnNames(-1));

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();

        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public void PutColumnCallback(final ColumnMetadata metadata) {
        columnsMetadata.add(metadata);
        //  columnTable.getItems().add(new TableItems(metadata.Type, metadata.Name, metadata.IsPrimaryKey ? "PRIMARY KEY" : metadata.foreign.isForeign ? "FOREIGN KEY" : "NO KEY", metadata.NOT_NULL));
        items.add(new TableColumnMeta(metadata.Name, metadata.Type, keyLabel(metadata), metadata.NOT_NULL));
    }

    public void EditColumnCallBack(final ColumnMetadata metadata) {
        final int index = TableColumns.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= columnsMetadata.size()) return;
        columnsMetadata.set(index, metadata);
        items.set(index, new TableColumnMeta(metadata.Name, metadata.Type, keyLabel(metadata), metadata.NOT_NULL));
    }

    /** Uma coluna pode ser as duas coisas (numa tabela de ligação). */
    private static String keyLabel(final ColumnMetadata metadata) {
        final boolean foreign = metadata.foreign != null && metadata.foreign.isForeign;
        if (metadata.IsPrimaryKey && foreign) return "PRIMARY + FOREIGN KEY";
        if (metadata.IsPrimaryKey) return "PRIMARY KEY";
        return foreign ? "FOREIGN KEY" : "NO KEY";
    }

    @FXML
    private void closeWindow() {
        window.close();
    }

    public void setTable(final String table) {
        TableNameInput.setText(table);
    }

    /** Colunas pedidas pelo Assistente: mapas com Name, Type, Key e NotNull. */
    public void setColumns(final ArrayList<HashMap<String, String>> columns) {

        if (columns != null) {
            items.clear();
            columnsMetadata.clear();
            for (final HashMap<String, String> column : columns) {
                final String name = column.getOrDefault("Name", "");
                if (name == null || name.isBlank()) continue;
                final String key = column.getOrDefault("Key", "");
                final boolean primary = "PRIMARY KEY".equalsIgnoreCase(key == null ? "" : key.trim());
                final String type = column.get("Type") == null || column.get("Type").isBlank() ? "TEXT" : column.get("Type");
                final boolean notNull = Boolean.parseBoolean(column.get("NotNull"));
                final ColumnMetadata meta = new ColumnMetadata(notNull, primary, new ColumnMetadata.Foreign(), "", 0, type, name, false, 0, 0, null);
                columnsMetadata.add(meta);
                items.add(new TableColumnMeta(name, type, keyLabel(meta), notNull));
            }
        }

    }

    public void setCheck(String check) {
        CheckField.setText(check);
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
