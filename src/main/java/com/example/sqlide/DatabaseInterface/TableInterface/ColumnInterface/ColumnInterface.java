package com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface;

import com.example.sqlide.ColumnMetadata;
import com.example.sqlide.DataForDB;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater.CellFormater;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellFormater.CellFormaterController;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.CellType.*;
import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.NewColumn;
import com.example.sqlide.RenameColumnController;
import com.example.sqlide.drivers.model.DataBase;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

public class ColumnInterface {

    private final SimpleStringProperty tablePrimeKey;

    private StringProperty table;

    private final ColumnMetadata Metadata;

    private final DataBase Database;

    private final TableInterface context;

    private final TableView<DataForDB> tableView;

    private final CellFormater format = new CellFormater();

    public ColumnInterface(final DataBase Database, final ColumnMetadata meta, SimpleStringProperty tableHasPrimeKey, final TableInterface context, final TableView<DataForDB> tableView) {
        this.Database = Database;
        this.Metadata = meta;
        this.tablePrimeKey = tableHasPrimeKey;
        this.context = context;
        this.tableView = tableView;
    }

    public ColumnMetadata getMetadata() {
        return Metadata;
    }

    //  public void createDBColContainer(final String ColName, final String Type, final String Value, final String TableName, final boolean isPrimeKey, final ColumnMetadata columnInfo) {
    public TableColumn<DataForDB, String> createDBColContainer(final StringProperty TableName) {

        this.table = TableName;
        final String ColName = Metadata.Name;
        final boolean isPrimeKey = Metadata.IsPrimaryKey;

        return getForDBStringTableColumn(TableName, isPrimeKey, Metadata.foreign.isForeign);
    }

    private void createMenu(final TableColumn<DataForDB, String> col) {
        ContextMenu contextMenu = new ContextMenu();
       // col.getTableView().getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());
        MenuItem MetadataMenu = new MenuItem("Column Metadata");
        MetadataMenu.setOnAction(e->loadMetadataWindow());
        MenuItem RenameMenu = new MenuItem("Rename Column");
        RenameMenu.setOnAction(e->{
            final String oldName = Metadata.Name;
            loadRenameWindow();
            System.out.printf("velho " + oldName + " novo " + Metadata.Name);
            if (!oldName.equals(Metadata.Name)) {
                context.CallBackRenameColumn(Metadata.Name, oldName);
            }
        });
        MenuItem FormatMenu = new MenuItem("Format column cell");
        FormatMenu.setOnAction(e->{
            loadFormatWindow();
        });
        contextMenu.getItems().addAll(MetadataMenu, RenameMenu, FormatMenu);

        col.setContextMenu(contextMenu);

    }

    private void loadRenameWindow() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/RenameColumn.fxml"));
            Parent root = loader.load();

            RenameColumnController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Remove Column");
            subStage.setScene(new Scene(root));
            secondaryController.createController(Database, table.get(), this.Metadata);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.showAndWait();
           // subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    private void loadFormatWindow() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Formatter.fxml"));
            Parent root = loader.load();

            CellFormaterController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Format Column");
            subStage.setScene(new Scene(root));
            secondaryController.createWindow(format, subStage);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.showAndWait();
            tableView.refresh();
            // subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    private void loadMetadataWindow() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/NewColumn.fxml"));
            Parent root = loader.load();

            NewColumn secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Remove Column");
            subStage.setScene(new Scene(root));
            secondaryController.NewColumnWin(Database.getDatabaseName(), context.TableName.get(), context, subStage, context.getAllPrimaryKeys(), Database.types, Database.getList(), Database.getListChars(), Database.getIndexModes(), Database.getForeignModes(), Database.getSQLType());
            secondaryController.insertMetadata(Metadata);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    // para mudança
    @NotNull
    private TableColumn<DataForDB, String> getForDBStringTableColumn(final StringProperty TableName, final boolean isPrimeKey, final boolean isForeign) {
        TableColumn<DataForDB, String> ColumnContainer = new TableColumn<>();
        ColumnContainer.setEditable(true);
        ColumnContainer.setId(Metadata.Name);
        createMenu(ColumnContainer);
        System.out.println("id prime " + isPrimeKey);
        if (isPrimeKey || isForeign) {
       //     final String name = isPrimeKey ? "prime.jpg" : "foreign.jpg";
      //      Label columnName = new Label(Metadata.Name + "\uD83D\uDD11");
            final String columnTitle = Metadata.Name;
            final String lockIcon = " \uD83D\uDD11"; // 🔑

            // Criando partes separadas do texto
            final Text nameText = new Text(columnTitle);
            final Text lockText = new Text(lockIcon);

            nameText.setFill(Color.WHITE);
            lockText.setFill(Color.WHITE);

            // Aplicando cor amarela ao cadeado
            lockText.setFill(isPrimeKey ? Color.YELLOW : Color.BLUE);

            // Criando um TextFlow para combinar os textos
            final TextFlow textFlow = new TextFlow(nameText, lockText);
        //    ImageView icon = new ImageView(new Image(getClass().getResource("/com/example/sqlide/images/" + name).toExternalForm()));
          //  icon.setFitHeight(16);
            //icon.setFitWidth(16);
            HBox header = new HBox(5, textFlow);
            header.setAlignment(Pos.CENTER);

            ColumnContainer.setGraphic(header);
        } else {
            ColumnContainer.setText(Metadata.Name);
        }
        if (Metadata.items != null && Metadata.Type.equals("ENUM")) {
            EnumFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }
        else if (Metadata.items != null && Metadata.Type.equals("SET")) {
            SetFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }
        else if (Metadata.Type.equals("DATE")) {
            DateFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }
        else if (Metadata.Type.equals("DATETIME") || Metadata.Type.equals("TIMESTAMP")) {
            DateTimeFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }
        else if (Metadata.Type.equals("CIRCLE")) {
            CircleFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }
        else if (Metadata.Type.equals("BOX")) {
            BoxFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }
        else if (Metadata.Type.equals("POINT")) {
            PointFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }
        else {
            StringFieldCell.createColumn(ColumnContainer, Database, Metadata, tablePrimeKey, TableName, format);
        }

        ColumnContainer.setCellValueFactory(cellData -> {
            String value = cellData.getValue().GetData(Metadata.Name);
            return new SimpleStringProperty(value);});

        return ColumnContainer;
    }

    private Object formatValue(String value) {
        return switch (Metadata.Type) {
            case "INT", "INTEGER", "TINYINT", "SMALLINT", "MEDIUMINT", "BIGINT", "INT2", "INT8" ->
                    Long.parseLong(value);
            case "REAL", "DOUBLE", "FLOAT" -> Double.parseDouble(value);
            case "NUMERIC", "DECIMAL", "UNSIGNED BIG INT" -> new java.math.BigDecimal(value);
            case "BLOB" -> value.getBytes();
            default -> // include String
                    value;
        };
    }

}
