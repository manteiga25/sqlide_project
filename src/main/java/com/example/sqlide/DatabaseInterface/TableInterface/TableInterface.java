package com.example.sqlide.DatabaseInterface.TableInterface;

import com.example.sqlide.*;
import com.example.sqlide.AdvancedSearch.AdvancedSearchController;
import com.example.sqlide.Chart.ChartController;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.ColumnInterface;
import com.example.sqlide.DatabaseInterface.DatabaseInterface;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.DeleteColumn;
import com.example.sqlide.misc.ClipBoard;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.sqlide.popupWindow.handleWindow.*;

public class TableInterface {

    private final DataBase Database;

    public StringProperty TableName;

    private final TabPane DBTabContainer;

    private TableView<DataForDB> tableContainer;

    public long totalPages = 0;

    private boolean advancedSearch = false;

    private ArrayList<String> advancedSearchColumns = null;

    private ArrayList<String> PrimaryKeyList = new ArrayList<>();

    private final ObservableList<DataForDB> dataList = FXCollections.observableArrayList();

    private final AtomicBoolean isFetching = new AtomicBoolean(false);

    private final ArrayList<String> ColumnsNames = new ArrayList<>();
    
    private ArrayList<String> ColumnsFetched = new ArrayList<>();

    private final HashMap<String, TableColumn<DataForDB, String>> TemporaryColumnsContainer = new HashMap<>();

    private final DatabaseInterface context;

    private final ArrayList<ColumnInterface> columnsInterfaceList = new ArrayList<>();

    private final SimpleLongProperty PageNum = new SimpleLongProperty(0);

    private final SimpleStringProperty TablePrimeKey = new SimpleStringProperty("");

    private Label pageLabel;

    private JFXTextField pageField;

    private JFXTextField codeField;

    private Button AdvancedSearchButton;

    private boolean alreadyFetched = false, switching = false;

    private String codeSQL = "";

    private Thread fetcherThread = null;

    public ArrayList<String> getPrimaryKeys() {
        ArrayList<String> primaryKeys = new ArrayList<>();
        LinkedHashMap<String, ColumnMetadata> MetaDataList = getColumnsMetadata();
        for (final ColumnMetadata meta : MetaDataList.values()) {
            if (meta.IsPrimaryKey) {
                primaryKeys.add(meta.Name);
            }
        }
        return primaryKeys;
    }

    public HashMap<String, ArrayList<String>> getAllPrimaryKeys() {
        return context.getColumnPrimaryKey(TableName.get());
    }

    public LinkedHashMap<String, ColumnMetadata> getColumnsMetadata() {
        LinkedHashMap<String, ColumnMetadata> MetaDataList = new LinkedHashMap<>();
        for (final ColumnInterface column : columnsInterfaceList) {
            final ColumnMetadata meta = column.getMetadata();
            MetaDataList.put(meta.Name, meta);
        }
        return MetaDataList;
    }

    public ArrayList<HashMap<String, String>> getColumnsMetadataMap() {
        ArrayList<HashMap<String, String>> MetaDataList = new ArrayList<>();
        for (final ColumnInterface column : columnsInterfaceList) {
            final ColumnMetadata meta = column.getMetadata();
            MetaDataList.add(ColumnMetadata.MetadataToMap(meta));
        }
        return MetaDataList;
    }

    public ArrayList<String> getColumnsMetadataName() {
        ArrayList<String> MetaDataList = new ArrayList<>();
        for (final ColumnInterface column : columnsInterfaceList) {
            final ColumnMetadata meta = column.getMetadata();
            MetaDataList.add(meta.Name);
        }
        return MetaDataList;
    }

    private LinkedHashMap<String, String> getColumnType() {
        LinkedHashMap<String, String> types = new LinkedHashMap<>();
        LinkedHashMap<String, ColumnMetadata> MetaDataList = getColumnsMetadata();
        for (final ColumnMetadata meta : MetaDataList.values()) {
            types.put(meta.Name, meta.Type);
        }
        return types;
    }

    public StringProperty getTableName() {
        return TableName;
    }

    public TableInterface(final DataBase DB, final String TableName, final TabPane DBTabContainer, final DatabaseInterface context) {
        this.Database = DB;
        this.TableName = new SimpleStringProperty(TableName);
        this.DBTabContainer = DBTabContainer;
        this.context = context;
        this.TableName.addListener((observable, oldValue, newValue) -> {
            if (!newValue.equals(oldValue)) {
                DBTabContainer.getTabs().stream().filter(tab -> tab.getId().equals(oldValue)).findFirst().ifPresent(tab -> {tab.setText(newValue); tab.setId(newValue);});
                tableContainer.setId(newValue);
            }
        });
        this.PageNum.addListener((obs, oldValue, newValue) -> {
            final long num = newValue.longValue();
            System.out.println("ups");
            pageField.setText(String.valueOf(num));
            pageLabel.setText(PageNum.get() + ":" + totalPages);
            if (!switching) {
                    prepareFetch();
            } else {
                switching = false;
            }
                // } else {
          //      prepareCodeFetch(codeSQL);
           // }
        });
        Platform.runLater(this::createDatabaseTab);
    }

    public void createRowId() {
        for (DataForDB d : dataList) {
            d.AddColumn("ROWID", null);
        }
    }

    private void createDatabaseTab() {
        Tab newTab = new Tab(TableName.get());
        newTab.setId(TableName.get());
        newTab.setClosable(false);
        createMenu(newTab);
        DBTabContainer.getTabs().add(newTab);

        VBox DBContainer = new VBox(10);
        DBContainer.setPadding(new Insets(10, 10, 10, 10));

        HBox ButtonsLine = new HBox(8);
        // searchBox.setStyle("-fx-border-color: black; -fx-border-radius: 50;");

        ButtonsLine.getChildren().addAll(createReloadButton(), createColumnButton(), createDeleteButton(), createAddButton(), createDelButton(), createAdvDelButton(), createAdvButton(), createCleanButton(), createLabelPage(), createPageField(), createLabelCode(), createCodeField(), createButtonCode(), createChartButton());

        ScrollPane buttonsScroll = new ScrollPane();
        buttonsScroll.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ScrollHbarStyle.css")).toExternalForm());
        //  buttonsScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        buttonsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        buttonsScroll.setContent(ButtonsLine);

        tableContainer = new TableView<DataForDB>();
        tableContainer.setId(TableName.get());
        VBox.setVgrow(tableContainer, Priority.ALWAYS);
        //  tableContainer.setPrefHeight(1280);
        tableContainer.setEditable(true);
        tableContainer.getSelectionModel().setCellSelectionEnabled(true);
        final KeyCombination cntrlC = new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN);
        tableContainer.setOnKeyPressed(e->{
            if (cntrlC.match(e)) {
                new Thread(()->{
                    StringBuilder copy = new StringBuilder();
                    for (final TablePosition tablePosition : tableContainer.getSelectionModel().getSelectedCells()) {
                        copy.append(tablePosition.getTableColumn().getCellObservableValue(tablePosition.getRow()).getValue().toString()).append("\n");
                    }
                    ClipBoard.CopyToBoard(copy.toString());
                }).start();
            }
        });
        tableContainer.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableContainer.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/tableStyle.css")).toExternalForm());
        newTab.setContent(tableContainer);

        HBox ContainerPrev = new HBox(10);
         ContainerPrev.setPrefHeight(20);
        ContainerPrev.setAlignment(Pos.CENTER);
        VBox.setVgrow(ContainerPrev, Priority.NEVER);

        final Font courierNewFontBold36 = Font.font("Arial", FontWeight.NORMAL, 16);

        ContainerPrev.getChildren().addAll(createPreviou(courierNewFontBold36), createLabelPageInfo(), createNext(courierNewFontBold36));

        DBContainer.getChildren().addAll(buttonsScroll, tableContainer, ContainerPrev);
        newTab.setContent(DBContainer);

        tableContainer.setItems(dataList);
    }

    private JFXButton createReloadButton() {
        JFXButton Reload = new JFXButton("Reload");
        Reload.setOnAction(e->prepareFetch());
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.REFRESH);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        Reload.setGraphic(icon);
        return Reload;
    }

    private JFXButton createColumnButton() {
        JFXButton createColumn = new JFXButton("create column");
        createColumn.setOnAction(e-> createDBColInterface());
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.CREATIVE_COMMONS);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        createColumn.setGraphic(icon);
        return createColumn;
    }

    private JFXButton createDeleteButton() {
        JFXButton deleteCol = new JFXButton("Delete column");
        deleteCol.setOnAction(e-> DeleteColumnInterface());
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.REMOVE);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        deleteCol.setGraphic(icon);
        return deleteCol;
    }

    private JFXButton createAddButton() {
        JFXButton AddData = new JFXButton("Insert data");
        AddData.setOnAction(e-> NewRowInterface());
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.CREATIVE_COMMONS);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        AddData.setGraphic(icon);
        return AddData;
    }

    private JFXButton createDelButton() {
        JFXButton DelData = new JFXButton("Delete data");
        DelData.setOnAction(e-> removeItem());
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.TRASH);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        DelData.setGraphic(icon);
        return DelData;
    }

    private JFXButton createAdvDelButton() {
        JFXButton AdvancedDelete = new JFXButton("Advanced delete");
        AdvancedDelete.setOnAction(e->loadAdvancedWin("DELETE"));
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.TRASH_ALT);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        AdvancedDelete.setGraphic(icon);
        return AdvancedDelete;
    }

    private JFXButton createAdvButton() {
        JFXButton AdvancedSearch = new JFXButton("Advanced Search");
        AdvancedSearch.setOnAction(e->loadAdvancedWin("SELECT"));
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.DATABASE);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        AdvancedSearch.setGraphic(icon);
        return AdvancedSearch;
    }

    private JFXButton createCleanButton() {
        JFXButton CleanAdvancedSearch = new JFXButton("Clean Advanced Search");
        CleanAdvancedSearch.setOnAction(e-> resetAdvancedSearch());
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.REMOVE);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        CleanAdvancedSearch.setGraphic(icon);
        return CleanAdvancedSearch;
    }

    private JFXButton createChartButton() {
        JFXButton Chart = new JFXButton("Chart");
        Chart.setOnAction(e-> loadChart("", "", "", null));
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.BAR_CHART_ALT);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);
        Chart.setGraphic(icon);
        return Chart;
    }

    private Label createLabelPage() {
        Label label = new Label("Page");
        label.setPadding(new Insets(5,0,0,0));
        label.setTextFill(Color.WHITE);
        return label;
    }

    private TextField createPageField() {
        pageField = new JFXTextField();
        pageField.setText("0");
        pageField.setAlignment(Pos.CENTER);
        pageField.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TextFieldStyle.css")).toExternalForm());
       // pageField.setStyle("-fx-text-fill: white; -fx-background-color: #3c3c3c; -fx-border-color: black; -fx-border-radius: 5px; -fx-background-radius: 5px");
        pageField.setPrefWidth(40);
        pageField.setOnAction(event -> {
            try {
                final long num = Long.parseLong(pageField.getText());
                if (num <= totalPages && num >= 0) {
                    PageNum.set(num);
                }
            } catch (NumberFormatException e) {
                ShowError("Invalid value", "You need to insert a positive integer value to switch page.");
            }
        });

        return pageField;
    }

    private Hyperlink createPreviou(final Font courierNewFontBold36) {
        Hyperlink previou = new Hyperlink("Previou");
     //   previou.setPrefWidth(100);
        previou.setPrefHeight(100);
        previou.setFont(courierNewFontBold36);
        previou.setFocusTraversable(false);
        previou.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/Hyper.css")).toExternalForm());
        previou.setOnAction(e->{
            final long newNum = PageNum.get()-1;
            if (newNum >= 0) {
                PageNum.set(newNum);
            }
        });
        return previou;
    }

    private Hyperlink createNext(final Font courierNewFontBold36) {
        Hyperlink next = new Hyperlink("Next");
   //     next.setPrefWidth(100);
        next.setPrefHeight(100);
        next.setFont(courierNewFontBold36);
        next.setFocusTraversable(false);
        next.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/Hyper.css")).toExternalForm());
        next.setOnAction(e->{
            final long newNum = PageNum.get()+1;
            if (newNum <= totalPages) {
                PageNum.set(newNum);
            }
        });
        return next;
    }

    private Label createLabelPageInfo() {
        pageLabel = new Label();
        pageLabel.setTextFill(Color.WHITE);
        return pageLabel;
    }

    private Label createLabelCode() {
        Label codeLabel = new Label("Fetch code:");
        codeLabel.setPadding(new Insets(5,0,0,0));
        codeLabel.setTextFill(Color.WHITE);
        return codeLabel;
    }

    private TextField createCodeField() {
        codeField = new JFXTextField();
        codeField.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TextFieldStyle.css")).toExternalForm());
       // codeField.setStyle("-fx-background-radius: 15px");
        codeField.setStyle("-fx-text-fill: white;");
        codeField.setText("SELECT * FROM " + TableName.get() + ";" );
        codeField.setPromptText("select code...");
        codeField.setPrefWidth(300);
        return codeField;
    }

    private Button createButtonCode() {
        AdvancedSearchButton = new Button();

        AdvancedSearchButton.setStyle("-fx-background-color: transparent;");

       // FontAwesomeIcon icon = FontAwesomeIcon.SEARCH;

        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.SEARCH);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);

        AdvancedSearchButton.setGraphic(icon);

      //  AdvancedSearchButton.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/Button.css")).toExternalForm());
        AdvancedSearchButton.setOnAction(e->{
            final Thread advanced = new Thread(()-> {
                String rawCode = codeField.getText();
                if (rawCode != null && !rawCode.isEmpty()) {
                    if (rawCode.toLowerCase().contains("select")) {
                        if (rawCode.contains(" " + TableName.get() + " ") || rawCode.contains(" " + TableName.get() + ";")) {
                            if (!rawCode.toLowerCase().contains("limit") || !rawCode.toLowerCase().contains("offset")) {
                                if (!isFetching.get()) {
                                    isFetching.set(true);
                                    rawCode = rawCode.replace(";", "");
                        /*    int indexOffset = rawCode.toUpperCase().indexOf("OFFSET");
                            String offset = "";
                            if (indexOffset != -1) {
                                offset = " " + rawCode.substring(indexOffset);
                                rawCode = rawCode.replace(offset, "");
                            } */
                                    rawCode += " LIMIT " + Database.buffer;
                                    codeSQL = rawCode;
                                    advancedSearch = true;
                                    switching = true;
                                    //  prepareCodeFetch(codeSQL);
                                    PageNum.set(0);
                                    //  prepareFetch();
                                    //  fetchData(codeSQL);
                                    if (fetcherThread != null && fetcherThread.isAlive()) {
                                        fetcherThread.interrupt();
                                        try {
                                            fetcherThread.join();
                                        } catch (InterruptedException _) {

                                        }
                                    }
                                    //    hideColumns(ColumnsNames);


                                    isFetching.set(false);
                                    prepareFetch();

                                    try {
                                        fetcherThread.join();
                                    } catch (InterruptedException _) {

                                    }

                                    if (rawCode.contains("*")) {
                                        ColumnsFetched = (ArrayList<String>) ColumnsNames.clone();
                                    }

                                    final int conditionIndex = codeSQL.toUpperCase().indexOf("WHERE");

                                    String condition = "";

                                    if (conditionIndex != -1) {
                                        condition = codeSQL.substring(conditionIndex);
                                        String afterCondition = condition.substring(condition.indexOf("LIMIT"));
                                        condition = condition.replace(afterCondition, "");
                                    }

                                    final String finalCondition = condition;



                                    Platform.runLater(() -> hideColumns(ColumnsFetched));

                                    Platform.runLater(this::createTemporaryColumn);

                                    setTotalPages(ColumnsFetched, finalCondition);

                                    //  setTotalPages(ColumnsNames, finalCondition);
                                    isFetching.set(false);
                                }
                            } else {
                                ShowInformation("Invalid query", "The words limit and offset are not accepted for advanced search.");
                            }
                        } else {
                            ShowInformation("Invalid syntaxe", "Only Table " + TableName.get() + " is permited.");
                        }
                    } else {
                        ShowInformation("Invalid syntaxe", "Only select is permited.");
                    }
                } else {
                    ShowInformation("Invalid syntaxe", "You need to write command to fetch.");
                }
            });
            advanced.setDaemon(true);
            advanced.start();
        });
        return AdvancedSearchButton;
    }

    private void createTemporaryColumn() {
        List<String> temporaryColumns = ColumnsFetched.stream()
                .filter(col -> !ColumnsNames.contains(col))
                .toList();

        if (!temporaryColumns.isEmpty()) {
            for (String column : temporaryColumns) {
                createTemporaryDBcolContainer(column);
            }
        }

    }

    private void loadAdvancedWin(final String command) {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedSearchStage.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            AdvancedSearchController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Create Column");
            subStage.setScene(new Scene(root));
            secondaryController.setCode(command);
            secondaryController.setTable(TableName.get());
            secondaryController.setColumns(context.getColumnsNames());
            secondaryController.setStage(subStage);
            if (command.equals("DELETE")) {
                secondaryController.setSelector(" ");
                secondaryController.removeOrder();
                secondaryController.removeLeft();
            }
          //  secondaryController.initWin(ColumnsNames, subStage, this);

            subStage.showingProperty().addListener(_->{
                if (secondaryController.isClosedByUser()) {
                    if (command.equals("SELECT")) {
                        if (secondaryController.getQuery().toUpperCase().contains("SELECT")) {
                            codeField.setText(secondaryController.getQuery());
                            AdvancedSearchButton.fire();
                        } else {
                            ShowInformation("Invalid query", "The query " + secondaryController.getQuery() + " is invalid.");
                        }
                    } else {
                        if (secondaryController.getQuery().toUpperCase().contains("DELETE")) {
                        deleteQuery(secondaryController.getQuery());
                    } else {
                        ShowInformation("Invalid query", "The query " + secondaryController.getQuery() + " is invalid.");
                    }
                    }
                }
            });

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    private void deleteQuery(final String query) {
        final Thread delete = new Thread(()->{
            try {
                Database.executeCode(query);
                prepareFetch();
            } catch (SQLException e) {
                ShowError("Error to delete", "Error to delete items.\n" + e.getMessage());
            }
        });
        delete.setDaemon(true);
        delete.start();
    }

    private void removeItem() {
        ObservableList<DataForDB> selectedRows = tableContainer.getSelectionModel().getSelectedItems();
        if (selectedRows == null || selectedRows.isEmpty()) {
            return;
        }
        final String columnKey = !TablePrimeKey.get().isEmpty() ? TablePrimeKey.get() : "ROWID";
        ArrayList<String> rowids = new ArrayList<>();
        for (final DataForDB row : selectedRows) {
            System.out.println("rrrr " + row.GetData(columnKey));
            rowids.add(row.GetData(columnKey));
        }

        try {
            if (!Database.Inserter().removeData(TableName.get(), rowids)) {
                ShowError("Error SQL", "Error to delete items.\n" + Database.GetException());
                return;
            }
        } catch (SQLException e) {
            System.out.println("jshd");
        }
        tableContainer.getItems().removeAll(selectedRows);
    }

    private void createMenu(final Tab col) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem menuItem1 = new MenuItem("Rename Table");
        menuItem1.setOnAction(e->openRenameWin());
        MenuItem menuItem2 = new MenuItem("Delete Table");
        menuItem2.setOnAction(e->deleteDBTab());
        contextMenu.getItems().addAll(menuItem1, menuItem2);

        col.setContextMenu(contextMenu);

    }

    public void deleteDBTab() {

        if (!ShowConfirmation("Confirmation", "Are you sure to delete Table " + TableName + "?")) {
            return;
        }

        if (!Database.deleteTable(TableName.get())) {
            ShowError("ERROR SQL", "Error to delete table " + TableName + "\n" + Database.GetException());
            return;
        }
        context.deleteTableCallBack(TableName.get());
    }

    private void openRenameWin() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/RenameTable.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            RenameTableController secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Create Column");
            subStage.setScene(new Scene(root));
            secondaryController.createController(Database, TableName);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();

        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public void loadChart(final String title, final String x, final String y, final ArrayList<HashMap<String, String>> labels) {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Chart/ChartStage.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            ChartController secondaryController = loader.getController();
            secondaryController.setAttributes(TableName.get(), getColumnsMetadataName(), Database);
            secondaryController.setTitle(title);
            secondaryController.setNumber(y);
            secondaryController.setAxis(x);
            if (labels != null) secondaryController.setLabels(labels);

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Create Chart");
            subStage.setScene(new Scene(root));

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();

        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

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
            secondaryController.NewColumnWin(Database.getDatabaseName(), TableName.get(), this, subStage, context.getColumnPrimaryKey(TableName.get()), Database.types, Database.getList(), Database.getListChars(), Database.getIndexModes(), Database.getForeignModes(), Database.getSQLType());

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    @FXML
    public void NewRowInterface() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/NewRow.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            NewRow secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Insert Row");
            subStage.setScene(new Scene(root));
            //  secondaryController.NewRowWin(dbName, TableName, this, subStage, dataList.get(TableName).getFirst().type);
            secondaryController.NewRowWin(Database.getDatabaseName(), TableName.get(), this, subStage, getColumnsMetadata(), Database.types);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    private void DeleteColumnInterface() {
        try {

            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/DeleteColumnInterface.fxml"));
            Parent root = loader.load();

            DeleteColumn secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            Stage subStage = new Stage();
            subStage.setTitle("Remove Column");
            subStage.setScene(new Scene(root));
            secondaryController.DeleteColumnInnit(TableName.get(), ColumnsNames, subStage, this);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public void deleteColumn(final String Table, final String column, final int id) {
        final Stage loading = LoadingStage("Deleting column", "This operation can be slower.");
        Thread.ofVirtual().start(()->{
            if (getColumnsMetadata().get(column).index != null) {
                try {
                    Database.removeIndex(getColumnsMetadata().get(column).index);
                } catch (SQLException e) {
                    Platform.runLater(loading::close);
                    ShowError("Error SQL", "Error to delete index column " + column + " from Table " + Table, Database.GetException());
                    return ;
                }
            }
            if (!Database.deleteColumn(column, Table)) {
                Platform.runLater(loading::close);
                ShowError("Error SQL", "Error to delete column " + column + " from Table " + Table, Database.GetException());
                return ;
            }
            Platform.runLater(()->{
                deleteColumnContainer(column, id);
                loading.close();
            });
        });
    }

    private void deleteColumnContainer(final String Column, final int id) {
        tableContainer.getColumns().remove(id);
        ColumnMetadata tmpMeta = getColumnsMetadata().get(Column);
        if (tmpMeta.IsPrimaryKey) {
            TablePrimeKey.set("");
        }

        for (DataForDB data : dataList) {
            data.RemoveColumn(Column);
        }

        columnsInterfaceList.remove(id);
        ColumnsNames.remove(Column);
    }

    public void createDBCol(final String ColName, final ColumnMetadata meta, final boolean fill) {

        final Stage loading = LoadingStage("Creating column", "This operation can be slower.");

        Thread.ofVirtual().start(()->{
            if (!Database.createColumn(TableName.get(), ColName, meta, fill)) {
                Platform.runLater(loading::close);
                ShowError("Error SQL", "Error to create column " + ColName + " on Table " + TableName + " on Database " + "dbName", Database.GetException());
                return;
            }
            if (meta.index != null) {
                try {
                    Database.createIndex(TableName.get(), ColName, meta.index, meta.indexType);
                } catch (SQLException e) {
                    ShowError("Error SQL", "Error to create index for column " + ColName + " on Table " + TableName + " on Database " + Database.getDatabaseName(), Database.GetException());
                }
            }
            Platform.runLater(()->{
                loading.close();
                createDBcolContainer(meta);
            });
        });

    }

    public void createDBcolContainer(final ColumnMetadata meta) {
        if (meta.IsPrimaryKey) {
            TablePrimeKey.set(meta.Name);
        }
        ColumnInterface column = new ColumnInterface(Database, meta, TablePrimeKey, this, tableContainer);
        tableContainer.getColumns().add(column.createDBColContainer(TableName));
        columnsInterfaceList.add(column);
        for (DataForDB d : dataList) {
            d.AddColumn(meta.Name, meta.defaultValue);
        }
        ColumnsNames.add(meta.Name);
    }

    public void createTemporaryDBcolContainer(String column) {

        TableColumn<DataForDB, String> ColumnContainer = new TableColumn<>();
        ColumnContainer.setEditable(false);
        ColumnContainer.setId(column);
        ColumnContainer.setText(column);
        ColumnContainer.setCellValueFactory(cellData -> {
            String value = cellData.getValue().GetData(column);
            return new SimpleStringProperty(value);});

        TemporaryColumnsContainer.put(column, ColumnContainer);

        tableContainer.getColumns().add(ColumnContainer);
     /*   for (DataForDB d : dataList) {
            d.AddColumn(meta.Name, meta.defaultValue);
        }
        ColumnsNames.add(meta.Name); */
    }

    private void prepareFetch() {

        if (!isFetching.get()) {

            isFetching.set(true);

            final AtomicReference<Stage> loading = new AtomicReference<>();
            Platform.runLater(()->loading.set(LoadingStage("Loading data", "You can continue to use application")));

            fetcherThread = new Thread(() -> {

                ArrayList<DataForDB> data;

                if (advancedSearch) {
              //      fetched = fetchData(advancedSearchColumns);
                    data = fetchData(codeSQL);
                } else {
                    data = fetchData();
                }

                if (data != null) {
                    if (!fetcherThread.isInterrupted()) {
                        Platform.runLater(()-> {
                            putData(data);
                            pageLabel.setText(PageNum.get() + ":" + totalPages);
                        });
                    }
                    } else {
                    ShowError("Error", "Error to fetch data.\n" + Database.GetException());
                }

                Platform.runLater(()->loading.get().close());

                isFetching.set(false);

                System.out.println("Executando em uma thread separada! ");
                System.out.println("Page "+ PageNum.get());
                System.out.println(Database.buffer);
            });
            fetcherThread.setDaemon(true);
            fetcherThread.start();
        } else {
            fetcherThread.interrupt();
            System.out.println("ocupado");
        }
    }

    private ColumnInterface getColumnInterfaceByName(String name) {
        for (ColumnInterface ci : columnsInterfaceList) {
            if (ci.getMetadata().Name.equals(name)) {
                return ci;
            }
        }
        return null;
    }

    public void alterColumnMetadata(ColumnMetadata oldMetadata, ColumnMetadata newMetadata) {
        final String tableName = TableName.get();
        boolean overallSuccess = true;
        String errorMessage = "";
        String originalOldName = oldMetadata.Name; // Keep for UI lookup even if name changes mid-process

        // --- PSEUDO-CODE for Database Operations ---
        // IMPORTANT: The actual implementation of these Database.* calls is complex
        // and would involve detailed SQL generation and error handling per database type.
        // For SQLite, many of these would trigger a full table rebuild.

        String currentColumnNameInDB = oldMetadata.Name;

        // 1. Type Change (and size/precision)
        if (!oldMetadata.Type.equals(newMetadata.Type) ||
                oldMetadata.size != newMetadata.size ||
                oldMetadata.integerDigits != newMetadata.integerDigits ||
                oldMetadata.decimalDigits != newMetadata.decimalDigits) {
            // In a real scenario, might need to drop FKs/Indexes before type change
            // and re-add them after. Assuming Database driver handles some of this.
            // if (!Database.changeColumnType(tableName, currentColumnNameInDB, newMetadata.Type, newMetadata.size, newMetadata.integerDigits, newMetadata.decimalDigits)) {
            //     overallSuccess = false; errorMessage = "Failed to change column type: " + Database.GetException();
            // }
            System.out.println("Simulating: Change type for " + currentColumnNameInDB + " to " + newMetadata.Type);
        }

        // 2. Rename Column
        if (overallSuccess && !oldMetadata.Name.equals(newMetadata.Name)) {
            // if (!Database.renameColumn(tableName, currentColumnNameInDB, newMetadata.Name)) {
            //     overallSuccess = false; errorMessage = "Failed to rename column: " + Database.GetException();
            // } else {
            //     currentColumnNameInDB = newMetadata.Name; // Name in DB has changed
            // }
            System.out.println("Simulating: Rename column " + currentColumnNameInDB + " to " + newMetadata.Name);
            currentColumnNameInDB = newMetadata.Name; // Assume success for simulation
        }

        // 3. NOT NULL Constraint
        if (overallSuccess && oldMetadata.NOT_NULL != newMetadata.NOT_NULL) {
            // if (newMetadata.NOT_NULL) {
            //     if (!Database.addNotNullConstraint(tableName, currentColumnNameInDB)) { /* handle error */ }
            // } else {
            //     if (!Database.dropNotNullConstraint(tableName, currentColumnNameInDB)) { /* handle error */ }
            // }
            System.out.println("Simulating: Set NOT NULL to " + newMetadata.NOT_NULL + " for " + currentColumnNameInDB);
        }

        // 4. Default Value
        if (overallSuccess && !Objects.equals(oldMetadata.defaultValue, newMetadata.defaultValue)) {
            // if (newMetadata.defaultValue != null && !newMetadata.defaultValue.isEmpty()) {
            //    if(!Database.setDefaultValue(tableName, currentColumnNameInDB, newMetadata.defaultValue)) { /* error */ }
            // } else {
            //    if(!Database.dropDefaultValue(tableName, currentColumnNameInDB)) { /* error */ }
            // }
            System.out.println("Simulating: Set DEFAULT to '" + newMetadata.defaultValue + "' for " + currentColumnNameInDB);
        }

        // 5. Primary Key (very simplified - actual PK changes are complex)
        if (overallSuccess && oldMetadata.IsPrimaryKey != newMetadata.IsPrimaryKey) {
            // Dropping/Adding PKs can be very involved, potentially requiring dropping dependent FKs.
            // String pkName = "PK_" + tableName + "_" + currentColumnNameInDB;
            // if (newMetadata.IsPrimaryKey) { if(!Database.addPrimaryKey(tableName, currentColumnNameInDB, pkName)) { /* error */ } }
            // else { if(!Database.dropPrimaryKey(tableName, pkName)) { /* error */ } } // Dropping old PK
            System.out.println("Simulating: Set IsPrimaryKey to " + newMetadata.IsPrimaryKey + " for " + currentColumnNameInDB);
        }

        // 6. Unique Constraint
        if (overallSuccess && oldMetadata.isUnique != newMetadata.isUnique) {
            // String uniqueConstraintName = "UQ_" + tableName + "_" + currentColumnNameInDB;
            // if (newMetadata.isUnique) { if(!Database.addUniqueConstraint(tableName, currentColumnNameInDB, uniqueConstraintName)) { /*error*/ }}
            // else { if(!Database.dropUniqueConstraint(tableName, "UQ_" + tableName + "_" + oldMetadata.Name /* Use old name for old constraint */ )) { /*error*/ }}
            System.out.println("Simulating: Set isUnique to " + newMetadata.isUnique + " for " + currentColumnNameInDB);
        }

        // 7. Foreign Key (Highly simplified)
        ColumnMetadata.Foreign oldFk = oldMetadata.foreign;
        ColumnMetadata.Foreign newFk = newMetadata.foreign;
        if (overallSuccess && (oldFk.isForeign != newFk.isForeign || !Objects.equals(oldFk.tableRef, newFk.tableRef) /* etc. */)) {
            // String fkName = "FK_" + tableName + "_" + currentColumnNameInDB;
            // if (oldFk.isForeign) { /* Database.dropForeignKeyConstraint(...) */ }
            // if (newFk.isForeign) { /* Database.addForeignKeyConstraint(...) */ }
            System.out.println("Simulating: Update Foreign Key for " + currentColumnNameInDB);
        }

        // 8. Index
        if (overallSuccess && !Objects.equals(oldMetadata.index, newMetadata.index) || !Objects.equals(oldMetadata.indexType, newMetadata.indexType)) {
            // if (oldMetadata.index != null && !oldMetadata.index.isEmpty()) { /* Database.removeIndex(oldMetadata.index) */ }
            // if (newMetadata.index != null && !newMetadata.index.isEmpty()) { /* Database.createIndex(tableName, currentColumnNameInDB, newMetadata.index, newMetadata.indexType) */ }
            System.out.println("Simulating: Update Index for " + currentColumnNameInDB);
        }

        // 9. Check Constraint
        if (overallSuccess && !Objects.equals(oldMetadata.check, newMetadata.check)) {
            // String chkName = "CHK_" + tableName + "_" + currentColumnNameInDB;
            // if (oldMetadata.check != null && !oldMetadata.check.isEmpty()) { /* Database.dropCheckConstraint(...) */ }
            // if (newMetadata.check != null && !newMetadata.check.isEmpty()) { /* Database.addCheckConstraint(...) */ }
            System.out.println("Simulating: Update Check constraint for " + currentColumnNameInDB);
        }

        // 10. Comment
        if (overallSuccess && !Objects.equals(oldMetadata.comment, newMetadata.comment)) {
            // if(!Database.setColumnComment(tableName, currentColumnNameInDB, newMetadata.comment)) { /* error */ }
            System.out.println("Simulating: Set comment for " + currentColumnNameInDB);
        }


        // --- Actual UI Refresh Logic ---
        if (overallSuccess) {
            ColumnInterface ci = getColumnInterfaceByName(originalOldName); // Find by the original old name
            if (ci != null) {
                // Update the metadata object within ColumnInterface
                ci.getMetadata().NOT_NULL = newMetadata.NOT_NULL;
                ci.getMetadata().IsPrimaryKey = newMetadata.IsPrimaryKey;
                ci.getMetadata().defaultValue = newMetadata.defaultValue;
                ci.getMetadata().Type = newMetadata.Type;
                ci.getMetadata().Name = newMetadata.Name; // Update name last before creating new UI column
                ci.getMetadata().size = newMetadata.size;
                ci.getMetadata().isUnique = newMetadata.isUnique;
                ci.getMetadata().index = newMetadata.index;
                ci.getMetadata().integerDigits = newMetadata.integerDigits;
                ci.getMetadata().decimalDigits = newMetadata.decimalDigits;
                ci.getMetadata().items = newMetadata.items;
                ci.getMetadata().indexType = newMetadata.indexType;
                ci.getMetadata().aliasType = newMetadata.aliasType;
                ci.getMetadata().foreign = newMetadata.foreign;
                ci.getMetadata().check = newMetadata.check;
                ci.getMetadata().autoincrement = newMetadata.autoincrement;
                ci.getMetadata().comment = newMetadata.comment;

                // Remove the old JavaFX TableColumn
                final String finalOriginalOldName = originalOldName;
                tableContainer.getColumns().removeIf(tc -> tc.getId().equals(finalOriginalOldName));

                // Add a new TableColumn created with the updated metadata
                // This ensures that any visual changes in the header (name, icons) are reflected.
                TableColumn<DataForDB, String> newFXColumn = ci.createDBColContainer(TableName);
                tableContainer.getColumns().add(newFXColumn); // Consider order if it matters

                // If name changed, update internal lists and data maps
                if (!originalOldName.equals(newMetadata.Name)) {
                    ColumnsNames.remove(originalOldName);
                    ColumnsNames.add(newMetadata.Name);
                    for (DataForDB d : dataList) {
                        d.RenameColumn(originalOldName, newMetadata.Name);
                    }
                    if (TablePrimeKey.get().equals(originalOldName)) {
                        TablePrimeKey.set(newMetadata.Name);
                    }
                } else {
                    // If only PK status changed for example, the TablePrimeKey might need update
                    if (newMetadata.IsPrimaryKey && !TablePrimeKey.get().equals(newMetadata.Name)) {
                        TablePrimeKey.set(newMetadata.Name);
                    } else if (!newMetadata.IsPrimaryKey && TablePrimeKey.get().equals(newMetadata.Name)) {
                        TablePrimeKey.set(""); // Or re-evaluate if another PK exists
                    }
                }
            }
            tableContainer.refresh(); // Refresh the whole table view
            ShowInformation("Success", "Column metadata updated (simulated). UI refreshed.");
        } else {
            ShowError("Error updating column", errorMessage + " (Simulated DB operation failed)");
            // Best effort to refresh from DB to reflect actual state if some operations failed
            // This would involve re-fetching schema and data. For now, just an error.
            // prepareFetch(); // This would reload data and schema
        }
    }

    private void prepareCodeFetch(final String code) {

        if (!isFetching.get()) {

            isFetching.set(true);

            new Thread(() -> {

                int fetched;

                ArrayList<DataForDB> data = fetchData(code);

                if (data == null) {
                    ShowError("Error", "Error to fetch data.\n" + Database.GetException());
                } else {
                    putData(data);
                }

                isFetching.set(false);

                System.out.println("Executando em uma thread separada! ");
                System.out.println("Page "+ PageNum.get());
                System.out.println(Database.buffer);
            }).start();
        } else {
            System.out.println("ocupado");
        }
    }

    public void CallBackRenameColumn(final String newName, final String oldName) {
        for (TableColumn<DataForDB, ?> col : tableContainer.getColumns()) {
            if (col.getId().equals(oldName)) {
                col.setId(newName);
                col.setText(newName);
                System.out.println(ColumnsNames.remove(oldName));
                ColumnsNames.add(newName);
                for (DataForDB d : dataList) {
                    d.RenameColumn(oldName, newName);
                }
                tableContainer.refresh();
                break;
            }
        }
    }

    public boolean ShowData(String query) {
        query += ";";
        codeField.setText(query);
        AdvancedSearchButton.fire();
        return true;
    }

    public void readColumns() {
        final ArrayList<ColumnMetadata> ColumnsMetadata = Database.getColumnsMetadata(TableName.get());
        for (final ColumnMetadata ColumnMetadata : ColumnsMetadata) {
            if (ColumnMetadata.IsPrimaryKey) {
                TablePrimeKey.set(ColumnMetadata.Name);
            }
            ColumnInterface column = new ColumnInterface(Database, ColumnMetadata, TablePrimeKey, this, tableContainer);
            Platform.runLater(()->tableContainer.getColumns().add(column.createDBColContainer(TableName)));
            columnsInterfaceList.add(column);
            ColumnsNames.add(ColumnMetadata.Name);
            if (!dataList.isEmpty()) {
                for (DataForDB d : dataList) {
                    d.AddColumn(ColumnMetadata.Name, ColumnMetadata.defaultValue);
                }
            }
        }
        if (TablePrimeKey.get().isEmpty()) {
            createRowId();
        }

        //  pageLabel.setText("0:" + totalPages);
       // fetchData();
    //    prepareFetch();
    }

    public void fetchIfIsPrimeClick() {
        Platform.runLater(()->pageLabel.setText("0:" + totalPages));
        if (!alreadyFetched) {
            alreadyFetched = true;
            setTotalPages();
            prepareFetch();
        }
    }

    private ArrayList<DataForDB> fetchData() {
        return Database.Fetcher().fetchData(TableName.get(), ColumnsNames, PageNum.get()*Database.buffer, TablePrimeKey.get());
    }

    private ArrayList<DataForDB> fetchData(final String code) {

        ArrayList<DataForDB> dataFetched = Database.Fetcher().fetchData(code + " OFFSET " + PageNum.get()*Database.buffer, ColumnsFetched, TablePrimeKey.get());

        for (String co : ColumnsFetched) {
            System.out.println("sdfd " + co);
        }

        return dataFetched;
    }

    private void putData(final ArrayList<DataForDB> data) {
       // dataList.remove(0, dataList.size());
        dataList.clear();
        dataList.addAll(data);
    }

    private int fetchData(final ArrayList<String> columns) {

        ArrayList<DataForDB> dataFetched = Database.Fetcher().fetchData(TableName.get(), columns, PageNum.get()*Database.buffer, TablePrimeKey.get());

        if (dataFetched == null || dataFetched.isEmpty()) {
            return -1;
        }
        dataList.remove(0, dataList.size());
        dataList.addAll(dataFetched);
        return dataFetched.size();
    }

    public boolean insertData(HashMap<String, String> values) {
        System.out.println(values);
        if (!Database.Inserter().insertData(TableName.get(), values)) {
            ShowError("SQL Error", "Error to insert data\n" + Database.GetException());
            return false;
        }
        if (dataList.size() < Database.buffer) {
            final DataForDB data = new DataForDB(values);
            dataList.add(data);
        }
        return true;
    }

    private void hideColumns(final ArrayList<String> columns) {
        for (String column : columns) {
            System.out.println("fgfsg " + column);
        }
        for (String key : TemporaryColumnsContainer.keySet()) {
            tableContainer.getColumns().remove(TemporaryColumnsContainer.get(key));
        }
        TemporaryColumnsContainer.clear();
        for (TableColumn<DataForDB, ?> tableColumn : tableContainer.getColumns()) {
            tableColumn.setVisible(columns.contains(tableColumn.getId())); }
        }

        // for advanced search
    public boolean fetchDataCallback(final ArrayList<String> columns) {
        advancedSearchColumns = columns;
      //  advancedSearch = true;
      //  hideColumns(columns);

        final StringBuilder columnsToFetch = new StringBuilder();

        for (String column : columns) {
            columnsToFetch.append(column).append(", ");
        }

        codeField.setText("SELECT " + columnsToFetch.substring(0, columnsToFetch.length()-2) + " FROM " + TableName.get() + ";");
        AdvancedSearchButton.fire();
        return true;
    //    return fetchData() != -1;
    }

    private void resetAdvancedSearch() {
        if (advancedSearch) {
            advancedSearch = false;
            advancedSearchColumns = null;
            codeSQL = "";
            codeField.setText("SELECT * FROM " + TableName.get() + ";");
            fetcherThread.interrupt();
            Thread resetThread = new Thread(() -> {
                isFetching.set(false);
                prepareFetch();
                try {
                    fetcherThread.join();
                } catch (InterruptedException _) {
                    // just ignore
                }
                setTotalPages(ColumnsNames, "");
                Platform.runLater(()->hideColumns(ColumnsNames));
            });
            resetThread.setDaemon(true);
            resetThread.start();
        }
    }

    public void setTotalPages() {
        final Thread fetchPage = new Thread(()->{
            totalPages = Database.totalPages(TableName.get())-1;
            if (totalPages == -2) {
                System.out.println(Database.GetException());
                totalPages = 0;
            }
            System.out.println("encontrado " + totalPages);
            Platform.runLater(()->PageNum.set(0));
        });
        fetchPage.setDaemon(true);
        fetchPage.start();
    }

    public void setTotalPages(final ArrayList<String> columns, final String condition) {
        final Thread fetchPage = new Thread(()->{

            long max = -1;

            final int indexOffset = condition.toUpperCase().indexOf("OFFSET");
            String conditionComplete = condition;
            if (indexOffset != -1) {
                System.out.println("vd");
                conditionComplete = condition.replace(condition.substring(indexOffset-1), "");
            }
            for (final String column : columns) {
                totalPages = Database.totalPages(TableName.get(), column, conditionComplete);
                if (totalPages == -1) {
                    System.out.println(Database.GetException());
                } else if (totalPages > max) {
                    max = totalPages;
                }
            }
            totalPages = max == -1 ? Long.MAX_VALUE : totalPages;
            Platform.runLater(()->PageNum.set(0));
        }
        );
        fetchPage.setDaemon(true);
        fetchPage.start();
    }

    public void closeColumns() {
        DBTabContainer.getTabs().clear();
        tableContainer.getColumns().clear();
        dataList.clear();
    }

}
