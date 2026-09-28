package com.example.sqlide.DatabaseInterface.TableInterface;

import com.example.sqlide.*;
import com.example.sqlide.AdvancedSearch.AdvancedSearchController;
import com.example.sqlide.Chart.ChartController;
import com.example.sqlide.Container.LongField.LongField;
import com.example.sqlide.DataScience.DataScienceController;
import com.example.sqlide.DatabaseInterface.TableInterface.ColumnInterface.ColumnInterface;
import com.example.sqlide.DatabaseInterface.DatabaseInterface;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.Metadata.TableMetadata;
import com.example.sqlide.View.ViewController;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.QueryBuilder;
import com.example.sqlide.misc.ClipBoard;
import com.example.sqlide.misc.Dialog;
import com.example.sqlide.misc.memoryInterface;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
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

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.example.sqlide.popupWindow.handleWindow.*;

/**
 * Separador de uma tabela: a grelha, a paginação, a pesquisa avançada e as operações
 * sobre colunas e linhas.
 *
 * <p>O que estava mal e mudou:</p>
 * <ul>
 *   <li><b>Mudar uma coluna</b> ({@link #alterColumnMetadata}) só imprimia
 *       "Simulating: ..." na consola e dizia que tinha corrido bem. Agora chama
 *       {@link DataBase#alterColumn}, relê a coluna da base de dados e troca-a na grelha no
 *       mesmo sítio (antes ia parar ao fim).</li>
 *   <li><b>Criar e apagar colunas</b>: a janela fechava antes de se saber se tinha
 *       funcionado; o índice era criado com tipo {@code null}; a coluna nova aparecia com o
 *       texto do DEFAULT em vez dos valores verdadeiros; apagar tirava da grelha a coluna na
 *       posição N — errada se as colunas tivessem sido arrastadas ou se houvesse colunas da
 *       pesquisa avançada.</li>
 *   <li><b>Apagar linhas</b> usava só a primeira coluna da chave primária (numa chave
 *       composta apagava mais linhas do que as escolhidas) e rebentava em tabelas sem
 *       chave.</li>
 *   <li><b>Pesquisa avançada</b>: o botão bloqueava a thread da interface com
 *       {@code join()} até a consulta acabar; o teste de LIMIT/OFFSET usava {@code ||} e só
 *       recusava quando havia os dois; o total de páginas cortava o texto entre WHERE e
 *       LIMIT (o ORDER BY ia junto e o PostgreSQL recusava); depois de uma pesquisa, o
 *       primeiro "Next" não fazia nada; a mesma janela servia para pesquisar e para apagar.</li>
 *   <li>O nome da tabela é mudado por binding no separador e na grelha, mas o listener ainda
 *       tentava fazer {@code setId} em propriedades ligadas, o que lança exceção.</li>
 * </ul>
 */
public class TableInterface implements memoryInterface {

    private static enum StagesNamesEnum {
        AdvancedWindow,
        AdvancedDeleteWindow,
        ChartWindow,
        CreateWindow,
        CreateRowWindow,
        DataScience
    }

    /** Janelas guardadas que dependem das colunas e têm de ser refeitas quando elas mudam. */
    private static final List<StagesNamesEnum> SCHEMA_WINDOWS = List.of(
            StagesNamesEnum.AdvancedWindow, StagesNamesEnum.AdvancedDeleteWindow, StagesNamesEnum.ChartWindow,
            StagesNamesEnum.CreateRowWindow, StagesNamesEnum.DataScience);

    private static final Pattern LIMIT_OR_OFFSET = Pattern.compile("(?i)\\b(LIMIT|OFFSET)\\b");

    private final DataBase Database;

    private final TabPane DBTabContainer;

    private TableView<DataForDB> tableContainer;

    public long totalPages = 0;

    private boolean advancedSearch = false;

    private ArrayList<String> advancedSearchColumns = null;

    private final ObservableList<DataForDB> dataList = FXCollections.observableArrayList();

    private final AtomicBoolean isFetching = new AtomicBoolean(false);

    private final ArrayList<String> ColumnsNames = new ArrayList<>();

    private final TableMetadata TableMetadata;

    private ArrayList<String> ColumnsFetched = new ArrayList<>();

    private final HashMap<String, TableColumn<DataForDB, String>> TemporaryColumnsContainer = new HashMap<>();

    private final DatabaseInterface context;

    private final ArrayList<ColumnInterface> columnsInterfaceList = new ArrayList<>();

    private final SimpleLongProperty PageNum = new SimpleLongProperty(0);

    private Label pageLabel;

    private LongField pageField;

    private JFXTextField codeField;

    private Button AdvancedSearchButton;

    private boolean alreadyFetched = false, switching = false;

    private String codeSQL = "";

    private Thread fetcherThread = null;

    private Task<?> fetch = null;

    private final WeakHashMap<StagesNamesEnum, Stage> stagesOpened = new WeakHashMap<>();

    private final Stack<StagesNamesEnum> stageName = new Stack<>();

    public TableMetadata getTableMetadata() {
        return TableMetadata;
    }

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

    /** Chaves primárias de todas as tabelas, incluindo esta (uma coluna pode apontar para a própria tabela). */
    public HashMap<String, ArrayList<String>> getAllPrimaryKeys() {
        return context.getColumnPrimaryKey("");
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
        return TableMetadata.getNameProperty();
    }

    /** Colunas que outra coluna pode referenciar (para o formulário escolher o tipo certo). */
    public Map<String, ColumnMetadata> getReferenceableColumns() {
        return context.getReferenceableColumns();
    }

    public TableInterface(final DataBase DB, final String TableName, final TabPane DBTabContainer, final DatabaseInterface context) throws SQLException {
        this.Database = DB;
        TableMetadata = new TableMetadata(TableName);
        this.DBTabContainer = DBTabContainer;
        this.context = context;
        TableMetadata.setCheck(DB.getTableCheck(TableName));
        this.TableMetadata.getNameProperty().addListener((observable, oldValue, newValue) -> {
            if (oldValue != null && !oldValue.equals(newValue)) {
                // O separador e a grelha seguem o nome por binding; o resto tem de ser avisado.
                if (codeField != null && !advancedSearch) codeField.setText(defaultQuery());
                context.tableRenamed(oldValue, newValue);
            }
        });
        this.PageNum.addListener((obs, oldValue, newValue) -> {
            final long num = newValue.longValue();
            pageField.setText(String.valueOf(num));
            updatePageLabel();
            if (!switching) {
                    prepareFetch();
            } else {
                switching = false;
            }
        });
        //Platform.runLater(this::createDatabaseTab);

        Platform.runLater(this::createDatabaseTab);
        readColumns();
        this.initializeMemory();
    }

    private String defaultQuery() {
        return "SELECT * FROM " + Database.builder().readable().name(TableMetadata.getName()) + ";";
    }

    public void createRowId() {
        for (DataForDB d : dataList) {
            d.AddColumn(Database.getRowId(), null);
        }
    }

    private void createDatabaseTab() {
        Tab newTab = new Tab();
        newTab.textProperty().bind(TableMetadata.getNameProperty());
        newTab.idProperty().bind(TableMetadata.getNameProperty());
        newTab.setClosable(false);
        createMenu(newTab);
        DBTabContainer.getTabs().add(newTab);

        VBox DBContainer = new VBox(10);
        DBContainer.setPadding(new Insets(10, 10, 10, 10));

        HBox ButtonsLine = new HBox(8);
        // searchBox.setStyle("-fx-border-color: black; -fx-border-radius: 50;");

        ButtonsLine.getChildren().addAll(createReloadButton(), createColumnButton(), createDeleteButton(), createAddButton(), createDelButton(), createAdvDelButton(), createAdvButton(), createCleanButton(), createViewBox(), createLabelPage(), createPageField(), createLabelCode(), createCodeField(), createButtonCode(), createChartButton(), createDtaScienceButton());

        ScrollPane buttonsScroll = new ScrollPane();
        buttonsScroll.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ScrollHbarStyle.css")).toExternalForm());
        //  buttonsScroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        buttonsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        buttonsScroll.setContent(ButtonsLine);

        tableContainer = new TableView<>();
        tableContainer.idProperty().bind(TableMetadata.getNameProperty());
        VBox.setVgrow(tableContainer, Priority.ALWAYS);
        tableContainer.setEditable(true);
        tableContainer.getSelectionModel().setCellSelectionEnabled(true);
        final KeyCombination cntrlC = new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN);
        tableContainer.setOnKeyPressed(e->{
            if (cntrlC.match(e)) {
                final StringBuilder copy = new StringBuilder();
                for (final TablePosition<?,?> tablePosition : tableContainer.getSelectionModel().getSelectedCells()) {
                    final Object value = tablePosition.getTableColumn().getCellObservableValue(tablePosition.getRow()).getValue();
                    copy.append(value == null ? "" : value.toString()).append("\n");
                }
                ClipBoard.CopyToBoard(copy.toString());
            }
        });
        tableContainer.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tableContainer.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/tableStyle.css")).toExternalForm());
        newTab.setContent(tableContainer);

        HBox ContainerPrev = new HBox(10);
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

    private ComboBox<ViewController.View> createViewBox() {
        ComboBox<ViewController.View> viewBox = new ComboBox<>();
        viewBox.setPromptText("select view...");
        viewBox.getItems().addAll(TableMetadata.getViews());
        viewBox.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ComboboxModern.css")).toExternalForm());
        viewBox.setStyle("-fx-background-radius: 5px; -fx-border-radius: 5px;");
        viewBox.getSelectionModel().selectedItemProperty().addListener((_,_,value)->{
            if (value == null) return;
            codeField.setText(value.code.get());
            AdvancedSearchButton.fire();
        });
        return viewBox;
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

    private JFXButton createDtaScienceButton() {
        JFXButton Chart = new JFXButton("Data Science");
        Chart.setOnAction(_-> openDataScienceStage());
        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.DASHBOARD);
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

    private LongField createPageField() {
        pageField = new LongField();
        pageField.setNumber(0);
        pageField.setAlignment(Pos.CENTER);
        pageField.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TextFieldStyle.css")).toExternalForm());
      //  pageField.setStyle("-fx-text-fill: white; -fx-background-color: #3c3c3c; -fx-border-color: black; -fx-border-radius: 5px; -fx-background-radius: 5px");
        pageField.setPrefWidth(40);
        pageField.setOnAction(_ -> {
                final long num = pageField.getNumber();
                if (num <= totalPages && num >= 0) {
                    PageNum.set(num);
                }
        });

        return pageField;
    }

    private Hyperlink createPreviou(final Font courierNewFontBold36) {
        Hyperlink previou = new Hyperlink("Previou");
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
      //  next.setPrefHeight(100);
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

    /** "página:última"; quando não se sabe quantas páginas há mostra "?". */
    private void updatePageLabel() {
        if (pageLabel == null) return;
        pageLabel.setText(PageNum.get() + ":" + (totalPages == Long.MAX_VALUE ? "?" : totalPages));
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
        codeField.setText(defaultQuery());
        codeField.setPromptText("select code...");
        codeField.setPrefWidth(300);
        codeField.setOnAction(_->AdvancedSearchButton.fire());
        return codeField;
    }

    private Button createButtonCode() {
        AdvancedSearchButton = new Button();

        AdvancedSearchButton.setStyle("-fx-background-color: transparent;");

        FontAwesomeIconView icon = new FontAwesomeIconView(FontAwesomeIcon.SEARCH);
        icon.setSize("1.5em");
        icon.setFill(Color.WHITE);

        AdvancedSearchButton.setGraphic(icon);

        AdvancedSearchButton.setOnAction(e -> runAdvancedSearch(codeField.getText()));
        return AdvancedSearchButton;
    }

    /**
     * Mostra na grelha o resultado de uma consulta escrita no campo "Fetch code".
     *
     * <p>A consulta corre em segundo plano como as outras leituras; as colunas visíveis e o
     * total de páginas são acertados quando a primeira página chega.</p>
     */
    private void runAdvancedSearch(final String rawCode) {
        final String query = QueryBuilder.stripTerminator(rawCode);
        if (query.isEmpty()) {
            ShowInformation("Invalid syntaxe", "You need to write command to fetch.");
            return;
        }
        final String start = query.toUpperCase(Locale.ROOT);
        if (!start.startsWith("SELECT") && !start.startsWith("WITH")) {
            ShowInformation("Invalid syntaxe", "Only select is permited.");
            return;
        }
        if (!mentionsTable(query)) {
            ShowInformation("Invalid syntaxe", "Only Table " + TableMetadata.getName() + " is permited.");
            return;
        }
        if (LIMIT_OR_OFFSET.matcher(query).find()) {
            ShowInformation("Invalid query", "The words limit and offset are not accepted for advanced search: the pages are added by the grid.");
            return;
        }

        codeSQL = query;
        advancedSearch = true;
        ColumnsFetched = new ArrayList<>();
        // Se a página já é a 0 o listener não dispara e o "switching" ficava ligado: o
        // primeiro "Next" depois de uma pesquisa não fazia nada.
        switching = PageNum.get() != 0;
        PageNum.set(0);
        prepareFetch(() -> {
            hideColumns(ColumnsFetched);
            createTemporaryColumn();
            setTotalPagesOfQuery();
        });
    }

    /** A consulta usa esta tabela (com ou sem aspas)? */
    private boolean mentionsTable(final String query) {
        return Pattern.compile("(?i)(^|[^A-Za-z0-9_$])[\"`\\[]?" + Pattern.quote(TableMetadata.getName()) + "[\"`\\]]?([^A-Za-z0-9_$]|$)")
                .matcher(query).find();
    }

    private void createTemporaryColumn() {
        final Set<String> known = ColumnsNames.stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> temporaryColumns = ColumnsFetched.stream()
                .filter(col -> !known.contains(col.toLowerCase(Locale.ROOT)))
                .toList();

        for (String column : temporaryColumns) {
            createTemporaryDBcolContainer(column);
        }
    }

    private void loadAdvancedWin(final String command) {
        final StagesNamesEnum key = command.equals("DELETE") ? StagesNamesEnum.AdvancedDeleteWindow : StagesNamesEnum.AdvancedWindow;
        final boolean exists = stagesOpened.get(key) != null;

        Stage subStage;

        if (exists) {
            stageName.remove(key);
            subStage = stagesOpened.get(key);
            subStage.show();
            stageName.push(key);
        }  else {
            try {
                // Carrega o arquivo FXML
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/AdvancedSearch/AdvancedSearchStage.fxml"));
                //    VBox miniWindow = loader.load();
                Parent root = loader.load();

                AdvancedSearchController secondaryController = loader.getController();

                // Criar um novo Stage para a subjanela
                subStage = new Stage();
                subStage.setTitle(command.equals("DELETE") ? "Advanced delete - " + TableMetadata.getName()
                        : "Advanced search - " + TableMetadata.getName());
                subStage.setScene(new Scene(root));
                secondaryController.setDialect(Database.getSQLType());
                secondaryController.setCode(command);
                secondaryController.setTable(TableMetadata.getName());
                secondaryController.setColumns(context.getColumnsNames());
                // Funções e vistas do esquema, para o selector f(x) do construtor.
                secondaryController.setRoutines(context.getRoutines());
                secondaryController.setStage(subStage);
                if (command.equals("DELETE")) {
                    secondaryController.setSelector(" ");
                    secondaryController.removeOrder();
                    secondaryController.removeLeft();
                }
                //  secondaryController.initWin(ColumnsNames, subStage, this);

                // Só o botão Confirm conta; fechar a janela ou Cancel não executam nada. Antes o
                // aviso ficava ligado e a consulta voltava a correr quando a janela reabria.
                subStage.setOnHidden(_ -> {
                    if (!secondaryController.consumeConfirmed()) return;
                    final String query = secondaryController.getQuery();
                    if (command.equals("SELECT")) {
                        if (query.toUpperCase(Locale.ROOT).contains("SELECT")) {
                            codeField.setText(query);
                            AdvancedSearchButton.fire();
                        } else {
                            ShowInformation("Invalid query", "The query " + query + " is invalid.");
                        }
                    } else {
                        if (query.toUpperCase(Locale.ROOT).startsWith("DELETE")) {
                            deleteQuery(query);
                        } else {
                            ShowInformation("Invalid query", "The query " + query + " is invalid.");
                        }
                    }
                });

                // Opcional: definir a modalidade da subjanela
                subStage.initModality(Modality.APPLICATION_MODAL);

                subStage.setOnCloseRequest(event -> {
                    event.consume();
                    subStage.hide();
                });

                // Mostrar a subjanela
                subStage.show();

                stageName.push(key);
                stagesOpened.put(key, subStage);
            } catch (Exception e) {
                ShowError("Read asset", "Error to load asset file.", e.getMessage());
            }
        }
    }

    private void deleteQuery(final String query) {
        final boolean everything = !query.toUpperCase(Locale.ROOT).contains(" WHERE ");
        if (!ShowConfirmation("Delete rows", (everything ? "This deletes EVERY row of " + TableMetadata.getName() + ".\n\n" : "")
                + "Run this command?\n\n" + query)) {
            return;
        }
        Thread.ofVirtual().start(()->{
            try {
                Database.executeCode(query);
                Platform.runLater(() -> {
                    setTotalPages();
                    prepareFetch();
                });
            } catch (SQLException e) {
                ShowError("Error to delete", "Error to delete items.", e.getMessage());
            }
        });
    }

    /**
     * Apaga as linhas selecionadas, identificadas por todas as colunas da chave primária ou,
     * sem chave, pelo rowid do motor.
     */
    private void removeItem() {
        final ObservableList<DataForDB> selectedRows = tableContainer.getSelectionModel().getSelectedItems();
        if (selectedRows == null || selectedRows.isEmpty()) {
            return;
        }
        // A seleção é por células: a mesma linha pode aparecer várias vezes.
        final List<DataForDB> rows = new ArrayList<>(new LinkedHashSet<>(selectedRows));

        final List<String> keys = TableMetadata.hasPrimaryKey() ? TableMetadata.getPrimaryKeys()
                : Database.hasRowId() ? List.of(Database.getRowId()) : List.of();
        if (keys.isEmpty()) {
            ShowError("Cannot delete", "Table " + TableMetadata.getName() + " has no primary key, so its rows cannot be told apart. Add a primary key first.");
            return;
        }

        final List<List<String>> values = new ArrayList<>();
        for (final DataForDB row : rows) {
            final List<String> key = row.GetData(keys);
            if (key.stream().anyMatch(Objects::isNull)) {
                ShowError("Cannot delete", "Some selected rows do not have the key columns (" + String.join(", ", keys)
                        + "). Reload the table without advanced search and try again.");
                return;
            }
            values.add(key);
        }

        if (!ShowConfirmation("Delete rows", "Delete " + rows.size() + " row(s) from " + TableMetadata.getName() + "?")) return;

        if (!Database.deleteRows(TableMetadata.getName(), keys, values)) {
            ShowError("Error SQL", "Error to delete items.", Database.GetException());
            return;
        }
        dataList.removeAll(rows);
    }

    private void createMenu(final Tab col) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem menuItem1 = new MenuItem("Rename Table");
        menuItem1.setOnAction(e->openRenameWin());
        MenuItem menuItem2 = new MenuItem("Delete Table");
        menuItem2.setOnAction(e->deleteDBTab());
        MenuItem dataScienceItem = new MenuItem("Data Science Stage");
        dataScienceItem.setOnAction(e -> openDataScienceStage());
        // Índices e restrições de uma tabela já criada.
        MenuItem tableToolsItem = new MenuItem("Indexes and constraints");
        tableToolsItem.setOnAction(e -> openTableTools());
        // Dados geométricos desenhados no mapa-múndi.
        MenuItem mapItem = new MenuItem("Show on map");
        mapItem.setOnAction(e -> openMap());
        contextMenu.getItems().addAll(menuItem1, menuItem2, tableToolsItem, mapItem, dataScienceItem);

        col.setContextMenu(contextMenu);

    }

    public void deleteDBTab() {

        if (!ShowConfirmation("Confirmation", "Are you sure to delete Table " + TableMetadata.getName() + "?")) {
            return;
        }

        if (!Database.deleteTable(TableMetadata.getName())) {
            ShowError("ERROR SQL", "Error to delete table " + TableMetadata.getName() + "\n" + Database.GetException());
            return;
        }
        context.deleteTableCallBack(TableMetadata.getName());
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
            subStage.setTitle("Rename Table");
            subStage.setScene(new Scene(root));
            secondaryController.createController(Database, TableMetadata.getNameProperty());

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();

        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file\n" + e.getMessage());
        }
    }

    public void loadChart(final String title, final String x, final String y, final ArrayList<HashMap<String, String>> labels) {
        final boolean exists = stagesOpened.get(StagesNamesEnum.ChartWindow) != null;

        Stage subStage;

        if (exists) {
            stageName.remove(StagesNamesEnum.ChartWindow);
            subStage = stagesOpened.get(StagesNamesEnum.ChartWindow);
            subStage.show();
            stageName.push(StagesNamesEnum.ChartWindow);
        }  else {
            try {
                // Carrega o arquivo FXML
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/Chart/ChartStage.fxml"));
                //    VBox miniWindow = loader.load();
                Parent root = loader.load();

                ChartController secondaryController = loader.getController();
                secondaryController.setDialect(Database.getSQLType());
                secondaryController.setAttributes(TableMetadata.getName(), getColumnsMetadataName(), Database.Fetcher());
                secondaryController.setTitle(title);
                secondaryController.setNumber(y);
                secondaryController.setAxis(x);
                if (labels != null) secondaryController.setLabels(labels);

                // Criar um novo Stage para a subjanela
                subStage = new Stage();
                subStage.setTitle("Create Chart");
                subStage.setScene(new Scene(root));

                // Opcional: definir a modalidade da subjanela
                subStage.initModality(Modality.WINDOW_MODAL);

                subStage.setOnCloseRequest(event -> {
                    event.consume();
                    subStage.hide();
                });

                // Mostrar a subjanela
                subStage.show();

                stagesOpened.put(StagesNamesEnum.ChartWindow, subStage);
                stageName.push(StagesNamesEnum.ChartWindow);

            } catch (Exception e) {
                ShowError("Read asset", "Error to load asset file", e.getMessage());
            }
        }
    }

    /** Mapa com as geometrias da tabela atual. */
    private void openMap() {
        com.example.sqlide.DatabaseInterface.GeoMapController.open(
                DBTabContainer.getScene() == null ? null : DBTabContainer.getScene().getWindow(),
                Database,
                TableMetadata.getName(),
                new ArrayList<>(TableMetadata.getColumnMetadata()));
    }

    /** Janela de índices e restrições da tabela atual. */
    private void openTableTools() {
        com.example.sqlide.DatabaseInterface.ColumnMetadataController.open(
                DBTabContainer.getScene() == null ? null : DBTabContainer.getScene().getWindow(),
                Database,
                TableMetadata.getName(),
                TableMetadata.getColumnMetadata().stream().map(column -> column.Name).toList());
        // Um índice ou CHECK acabado de criar/apagar muda o que as colunas mostram.
        refreshColumnsFromDatabase(null);
    }

    private void openDataScienceStage() {
        final boolean exists = stagesOpened.get(StagesNamesEnum.DataScience) != null;

        Stage stage;

        if (exists) {
            stageName.remove(StagesNamesEnum.DataScience);
            stage = stagesOpened.get(StagesNamesEnum.DataScience);
            stage.show();
            stageName.push(StagesNamesEnum.DataScience);
        }  else {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/DataScience/DataScienceStage.fxml"));
                Parent root = loader.load();

                DataScienceController controller = loader.getController();
                controller.setDatabase(Database.Updater(), Database.Fetcher(),
                        Database.Executor(), Database.getSQLType());
                controller.setTaskInterface(context.getTaskInterface());
                controller.setMetadata(TableMetadata.getName(), new ArrayList<>(TableMetadata.getColumnMetadata()));
                stage = new Stage();
                stage.setTitle("Data Science Stage - " + TableMetadata.getName());
                stage.setScene(new Scene(root));

                // controller.initializeData(Database, this, stage);

                stage.initModality(Modality.WINDOW_MODAL);
                stage.show();

                stagesOpened.put(StagesNamesEnum.DataScience, stage);
                stageName.push(StagesNamesEnum.DataScience);
            } catch (IOException e) {
                ShowError("Error loading Data Science Stage", "Could not load the FXML file.", e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Janela de criar coluna. É sempre uma janela nova: a antiga era guardada e voltava com
     * o que se tinha escrito da última vez e com a lista de chaves estrangeiras desatualizada.
     */
    private void createDBColInterface() {
        try {
            // Carrega o arquivo FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/NewColumn.fxml"));
            //    VBox miniWindow = loader.load();
            Parent root = loader.load();

            NewColumn secondaryController = loader.getController();

            // Criar um novo Stage para a subjanela
            final Stage subStage = new Stage();
            subStage.setTitle("Create Column - " + TableMetadata.getName());
            subStage.setScene(new Scene(root));
            secondaryController.NewColumnWin(Database.getDatabaseName(), TableMetadata.getName(), this, subStage, getAllPrimaryKeys(), Database.types, Database.getDatabaseInfo());
            secondaryController.setReferencedColumns(getReferenceableColumns());
            secondaryController.setExistingColumns(ColumnsNames);

            // Opcional: definir a modalidade da subjanela
            subStage.initModality(Modality.APPLICATION_MODAL);

            // Mostrar a subjanela
            subStage.show();
        } catch (Exception e) {
            ShowError("Read asset", "Error to load asset file.", e.getMessage());
        }
    }

    @FXML
    public void NewRowInterface() {
        final boolean exists = stagesOpened.get(StagesNamesEnum.CreateRowWindow) != null;

        Stage subStage;

        if (exists) {
            stageName.remove(StagesNamesEnum.CreateRowWindow);
            subStage = stagesOpened.get(StagesNamesEnum.CreateRowWindow);
            subStage.show();
            stageName.push(StagesNamesEnum.CreateRowWindow);
        }  else {
            try {
                // Carrega o arquivo FXML
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/sqlide/NewRow.fxml"));
                //    VBox miniWindow = loader.load();
                Parent root = loader.load();

                NewRow secondaryController = loader.getController();

                // Criar um novo Stage para a subjanela
                subStage = new Stage();
                subStage.setTitle("Insert Row");
                subStage.setScene(new Scene(root));
                //  secondaryController.NewRowWin(dbName, TableName, this, subStage, dataList.get(TableName).getFirst().type);
                secondaryController.NewRowWin(Database.getDatabaseName(), TableMetadata.getName(), this, subStage, getColumnsMetadata(), Database.types);

                // Opcional: definir a modalidade da subjanela
                subStage.initModality(Modality.APPLICATION_MODAL);

                subStage.setOnCloseRequest(event -> {
                    event.consume();
                    subStage.hide();
                });

                // Mostrar a subjanela
                subStage.show();

                stageName.push(StagesNamesEnum.CreateRowWindow);
                stagesOpened.put(StagesNamesEnum.CreateRowWindow, subStage);
            } catch (Exception e) {
                ShowError("Read asset", "Error to load asset file.", e.getMessage());
            }
        }
    }

    /**
     * Janelas guardadas (inserir linha, pesquisa avançada, gráfico, Data Science) foram
     * montadas com as colunas de antes. Depois de uma coluna mudar, são esquecidas para a
     * próxima abertura as refazer.
     */
    private void forgetSchemaWindows() {
        for (final StagesNamesEnum name : SCHEMA_WINDOWS) {
            final Stage stage = stagesOpened.remove(name);
            stageName.remove(name);
            if (stage != null && !stage.isShowing()) stage.close();
        }
    }

    private void DeleteColumnInterface() {
        if (ColumnsNames.isEmpty()) {
            ShowInformation("No data", "This table has no columns to delete.");
            return;
        }

        final String delete = Dialog.ChoiceDialogStage(ColumnsNames, "Delete column", "Choice a column to delete.", "Columns:");
        // Cancelar o diálogo devolve null, e isso rebentava mais à frente.
        if (delete == null) return;

        if (!ShowConfirmation("Delete column", "Delete column " + delete + " from " + TableMetadata.getName() + "? Its data will be lost.")) {
            return;
        }

        deleteColumn(TableMetadata.getName(), delete, ColumnsNames.indexOf(delete));

    }

    /**
     * Apaga a coluna na base de dados e, se correr bem, na grelha. Os índices da coluna são
     * tratados pelo driver: o {@code removeIndex} que estava aqui usava o nome da coluna como
     * nome do índice no MySQL e tentava apagar o índice da chave única no SQLite.
     */
    public void deleteColumn(final String Table, final String column, final int id) {
        final Stage loading = LoadingStage("Deleting column", "This operation can be slower.");
        final ArrayList<ColumnMetadata> columns = columnsInterfaceList.stream().map(ColumnInterface::getMetadata)
                .collect(Collectors.toCollection(ArrayList::new));

        final Task<Void> deleteTask = new Task<>() {
            @Override
            protected void running() {
                super.running();
                updateTitle("Deleting column " + column);
                updateProgress(-1, -1);
                updateMessage("This operation can be slower.");
            }

            @Override
            protected Void call() throws SQLException {
                if (!Database.deleteColumn(columns, column, Table)) throw new SQLException(Database.GetException());
                return null;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                deleteColumnContainer(column, id);
                forgetSchemaWindows();
                prepareFetch();
            }

            @Override
            protected void failed() {
                super.failed();
                ShowError("Error SQL", "Error to delete column " + column + " from Table " + Table, getException().getMessage());
            }

            @Override
            protected void done() {
                super.done();
                Platform.runLater(loading::close);
            }
        };
        context.getTaskInterface().addTask(deleteTask);
        Thread.ofVirtual().start(deleteTask);
    }

    /** Tira a coluna da grelha pelo nome (a posição mudava com as colunas arrastadas ou temporárias). */
    private void deleteColumnContainer(final String Column, final int id) {
        tableContainer.getColumns().removeIf(tableColumn -> Column.equals(tableColumn.getId()));
        ColumnMetadata tmpMeta = TableMetadata.getColumnMetadata(Column);
        if (tmpMeta != null) TableMetadata.removeColumn(tmpMeta);

        for (DataForDB data : dataList) {
            data.RemoveColumn(Column);
        }

        columnsInterfaceList.removeIf(column -> column.getMetadata().Name.equals(Column));
        ColumnsNames.remove(Column);
    }

    public void createDBCol(final String ColName, final ColumnMetadata meta, final boolean fill) {
        createDBCol(ColName, meta, fill, null);
    }

    /**
     * Cria a coluna (e o índice, se foi pedido) em segundo plano. Quando acaba, a coluna é
     * lida outra vez da base de dados — é essa versão, com o tipo e o DEFAULT como o motor
     * os guardou, que vai para a grelha — e os dados são recarregados.
     *
     * @param done chamado na thread da interface com true se a coluna ficou criada
     */
    public void createDBCol(final String ColName, final ColumnMetadata meta, final boolean fill, final Consumer<Boolean> done) {

        final Stage loading = LoadingStage("Creating column", "This operation can be slower.");

        final Task<ColumnMetadata> createTask = new Task<>() {

            @Override
            protected void running() {
                super.running();
                updateTitle("Creating column " + ColName);
                updateProgress(-1,-1);
                updateMessage("This operation can be slower.");
            }

            @Override
            protected ColumnMetadata call() throws SQLException {
                if (!Database.createColumn(TableMetadata.getName(), ColName, meta, fill)) throw new SQLException(Database.GetException());
                if (meta.index != null && !meta.index.isBlank()) {
                    try {
                        Database.createIndex(TableMetadata.getName(), ColName, meta.index, meta.indexType);
                    } catch (SQLException e) {
                        throw new SQLException("The column was created, but not its index: " + e.getMessage(), e);
                    }
                }
                final ArrayList<ColumnMetadata> columns = Database.getColumnsMetadata(TableMetadata.getName());
                if (columns == null) return meta;
                return columns.stream().filter(c -> c.Name.equals(ColName)).findFirst().orElse(meta);
            }

            @Override
            protected void failed() {
                super.failed();
                ShowError("Error SQL", "Error to create column " + ColName + " on Table " + TableMetadata.getName() + " on Database " + Database.getDatabaseName(), getException().getMessage());
                // Se só o índice falhou, a coluna existe: a grelha tem de a mostrar na mesma.
                if (getException().getMessage() != null && getException().getMessage().startsWith("The column was created")) {
                    refreshColumnsFromDatabase(null);
                }
                if (done != null) done.accept(false);
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                final ColumnMetadata created = getValue();
                TableMetadata.addColumn(created);
                createDBcolContainer(created);
                // Uma reconstrução da tabela (SQLite) pode ter mudado as outras colunas.
                refreshColumnsFromDatabase(null);
                forgetSchemaWindows();
                prepareFetch();
                if (done != null) done.accept(true);
            }

            @Override
            protected void done() {
                super.done();
                Platform.runLater(loading::close);
            }

        };
        context.getTaskInterface().addTask(createTask);
        Thread.ofVirtual().start(createTask);

    }

    public void createDBcolContainer(final ColumnMetadata meta) {
        ColumnInterface column = new ColumnInterface(Database, meta, TableMetadata.getPrimaryKeyProperty(), this, tableContainer);
        tableContainer.getColumns().add(column.createDBColContainer(TableMetadata.getNameProperty()));
        columnsInterfaceList.add(column);
        for (DataForDB d : dataList) {
            d.AddColumn(meta.Name, null);
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
        prepareFetch(null);
    }

    /**
     * Lê a página atual (da tabela ou da pesquisa avançada) em segundo plano.
     *
     * <p>Um pedido novo substitui o que estiver a meio — antes era ignorado, e o "Reload"
     * carregado durante uma leitura não fazia nada. Pode ser chamado de qualquer thread.</p>
     *
     * @param afterSuccess corre na thread da interface depois de os dados estarem na grelha
     */
    private void prepareFetch(final Runnable afterSuccess) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> prepareFetch(afterSuccess));
            return;
        }
        if (fetch != null && fetch.isRunning()) fetch.cancel(true);

        isFetching.set(true);
        final Stage loading = LoadingStage("Loading data", "You can continue to use application");
        final boolean advanced = advancedSearch;
        final String code = codeSQL;
        final long page = PageNum.get();

        final Task<ArrayList<DataForDB>> task = new Task<>() {

            @Override
            protected void scheduled() {
                super.scheduled();
                updateMessage("Fetching data.");
                updateTitle("Fetching Data");
            }

            @Override
            protected ArrayList<DataForDB> call() throws Exception {
                final ArrayList<DataForDB> data = advanced ? fetchData(code, page) : fetchData(page);
                if (data == null) throw new Exception(Database.GetException());
                return data;
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                putData(getValue());
                if (afterSuccess != null) afterSuccess.run();
            }

            @Override
            protected void failed() {
                super.failed();
                ShowError("Error", "Error to fetch data.", getException().getMessage());
            }

            @Override
            protected void done() {
                super.done();
                // O done() corre na thread da tarefa; uma tarefa cancelada não mexe no estado da que a substituiu.
                Platform.runLater(() -> {
                    if (fetch == this) isFetching.set(false);
                    updatePageLabel();
                    loading.close();
                });
            }
        };

        fetch = task;
        context.getTaskInterface().addTask(task);

        fetcherThread = new Thread(task);
        fetcherThread.setDaemon(true);
        fetcherThread.start();
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
        alterColumnMetadata(oldMetadata, newMetadata, null);
    }

    /**
     * Grava as mudanças de uma coluna (nome, tipo, NOT NULL, DEFAULT, chaves, UNIQUE, CHECK,
     * índice, comentário).
     *
     * <p>Antes cada passo era "Simulating: ..." na consola e no fim aparecia "Column metadata
     * updated (simulated)" — nada chegava à base de dados, e o que chegava (o tipo e o
     * DEFAULT) usava SQL que nenhum motor aceita. Também mudava o modo de commit e, em caso
     * de erro, fazia {@code back()}, que desfazia outras alterações do utilizador.</p>
     *
     * @param done chamado na thread da interface com true se a coluna ficou gravada
     */
    public void alterColumnMetadata(ColumnMetadata oldMetadata, ColumnMetadata newMetadata, final Consumer<Boolean> done) {
        final String tableName = TableMetadata.getName();
        final String originalOldName = oldMetadata.Name;
        // Cópia: se o motor recusar, a grelha continua a mostrar o que está na base de dados.
        final ColumnMetadata before = oldMetadata.copy();

        final Stage loading = LoadingStage("Changing column", "This operation can be slower.");

        final Task<ColumnMetadata> alterTask = new Task<>() {
            @Override
            protected void running() {
                super.running();
                updateTitle("Changing column " + originalOldName);
                updateProgress(-1, -1);
                updateMessage("This operation can be slower.");
            }

            @Override
            protected ColumnMetadata call() throws SQLException {
                if (!Database.alterColumn(tableName, before, newMetadata)) throw new SQLException(Database.GetException());
                final ArrayList<ColumnMetadata> columns = Database.getColumnsMetadata(tableName);
                if (columns == null) return newMetadata;
                return columns.stream().filter(c -> c.Name.equals(newMetadata.Name)).findFirst().orElse(newMetadata);
            }

            @Override
            protected void succeeded() {
                super.succeeded();
                final ColumnInterface ci = getColumnInterfaceByName(originalOldName);
                if (ci != null) {
                    // O ColumnMetadata é o mesmo objeto que está no TableMetadata: copiar para
                    // dentro dele atualiza os dois.
                    ci.getMetadata().copyFrom(getValue());
                    replaceColumnContainer(ci, originalOldName);
                }
                if (!originalOldName.equals(newMetadata.Name)) {
                    final int position = ColumnsNames.indexOf(originalOldName);
                    if (position >= 0) ColumnsNames.set(position, newMetadata.Name);
                    for (DataForDB d : dataList) {
                        d.RenameColumn(originalOldName, newMetadata.Name);
                    }
                }
                TableMetadata.refreshPrimaryKeys();
                forgetSchemaWindows();
                prepareFetch();
                if (done != null) done.accept(true);
            }

            @Override
            protected void failed() {
                super.failed();
                ShowError("Error SQL", "Error to change column " + originalOldName + ".", getException().getMessage());
                if (done != null) done.accept(false);
            }

            @Override
            protected void done() {
                super.done();
                Platform.runLater(loading::close);
            }
        };
        context.getTaskInterface().addTask(alterTask);
        Thread.ofVirtual().start(alterTask);
    }

    /**
     * Troca a coluna da grelha por uma nova feita a partir dos metadados atuais, no mesmo
     * sítio. O cabeçalho das chaves é um gráfico com o nome lá dentro, e um simples
     * {@code setText} não o mudava.
     */
    private void replaceColumnContainer(final ColumnInterface ci, final String oldId) {
        final ObservableList<TableColumn<DataForDB, ?>> columns = tableContainer.getColumns();
        int position = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (oldId.equals(columns.get(i).getId())) {
                position = i;
                break;
            }
        }
        final TableColumn<DataForDB, String> fresh = ci.createDBColContainer(TableMetadata.getNameProperty());
        if (position >= 0) {
            fresh.setVisible(columns.get(position).isVisible());
            fresh.setPrefWidth(columns.get(position).getWidth());
            columns.set(position, fresh);
        } else {
            columns.add(fresh);
        }
    }

    /**
     * Relê as colunas da base de dados e atualiza os metadados das que a grelha já tem
     * (depois de uma reconstrução da tabela, ou de mexer nos índices e restrições).
     */
    private void refreshColumnsFromDatabase(final Runnable after) {
        final String tableName = TableMetadata.getName();
        Thread.ofVirtual().start(() -> {
            final ArrayList<ColumnMetadata> columns = Database.getColumnsMetadata(tableName);
            if (columns == null) return;
            Platform.runLater(() -> {
                for (final ColumnMetadata fresh : columns) {
                    final ColumnInterface ci = getColumnInterfaceByName(fresh.Name);
                    if (ci == null) continue;
                    final boolean headerChanges = ci.getMetadata().IsPrimaryKey != fresh.IsPrimaryKey
                            || ci.getMetadata().foreign.isForeign != fresh.foreign.isForeign;
                    ci.getMetadata().copyFrom(fresh);
                    if (headerChanges) replaceColumnContainer(ci, fresh.Name);
                }
                TableMetadata.refreshPrimaryKeys();
                if (after != null) after.run();
            });
        });
    }

    /** Depois de "Rename Column": troca a coluna da grelha no mesmo sítio e renomeia os dados. */
    public void CallBackRenameColumn(final String newName, final String oldName) {
        final ColumnInterface ci = getColumnInterfaceByName(newName);
        if (ci != null) replaceColumnContainer(ci, oldName);

        final int position = ColumnsNames.indexOf(oldName);
        if (position >= 0) ColumnsNames.set(position, newName);
        for (DataForDB d : dataList) {
            d.RenameColumn(oldName, newName);
        }
        TableMetadata.setPrimaryKey(oldName, newName);
        forgetSchemaWindows();
        tableContainer.refresh();
    }

    public boolean ShowData(String query) {
        query += ";";
        codeField.setText(query);
        AdvancedSearchButton.fire();
        return true;
    }

    public void readColumns() {
        final ArrayList<ColumnMetadata> ColumnsMetadata = Database.getColumnsMetadata(TableMetadata.getName());
        if (ColumnsMetadata == null) {
            ShowError("Error SQL", "Could not read the columns of " + TableMetadata.getName() + ".", Database.GetException());
            return;
        }
        TableMetadata.addColumns(ColumnsMetadata);
        for (final ColumnMetadata ColumnMetadata : ColumnsMetadata) {
            final ColumnInterface column = new ColumnInterface(Database, ColumnMetadata, TableMetadata.getPrimaryKeyProperty(), this, tableContainer);
            Platform.runLater(()->tableContainer.getColumns().add(column.createDBColContainer(TableMetadata.getNameProperty())));
            columnsInterfaceList.add(column);
            ColumnsNames.add(ColumnMetadata.Name);
            if (!dataList.isEmpty()) {
                for (DataForDB d : dataList) {
                    d.AddColumn(ColumnMetadata.Name, null);
                }
            }
        }
        if (TableMetadata.hasPrimaryKey()) {
            createRowId();
        }
    }

    public void fetchIfIsPrimeClick() {
        Platform.runLater(this::updatePageLabel);
        if (!alreadyFetched) {
            alreadyFetched = true;
            setTotalPages();
            prepareFetch();
        }
    }

    private ArrayList<DataForDB> fetchData() {
        return fetchData(PageNum.get());
    }

    private ArrayList<DataForDB> fetchData(final long page) {
        return Database.Fetcher().fetchData(TableMetadata.getName(), ColumnsNames, page * Database.buffer, TableMetadata.getPrimaryKeys());
    }

    private ArrayList<DataForDB> fetchData(final String code) {
        return fetchData(code, PageNum.get());
    }

    /** Uma página da consulta da pesquisa avançada; o LIMIT/OFFSET é acrescentado aqui. */
    private ArrayList<DataForDB> fetchData(final String code, final long page) {
        final ArrayList<String> columns = new ArrayList<>();
        final ArrayList<DataForDB> dataFetched = Database.Fetcher().fetchData(
                Database.builder().paginate(code, Database.buffer, page * Database.buffer), columns, TableMetadata.getPrimaryKey());
        if (dataFetched == null) return null;
        alignLabels(columns, dataFetched);
        ColumnsFetched = columns;
        return dataFetched;
    }

    /**
     * O SQLite devolve o nome da coluna como foi escrito na consulta ({@code SELECT NAME}),
     * mas as colunas da grelha leem pelo nome verdadeiro ({@code name}): sem isto
     * apareciam vazias.
     */
    private void alignLabels(final ArrayList<String> labels, final ArrayList<DataForDB> rows) {
        for (int i = 0; i < labels.size(); i++) {
            final String label = labels.get(i);
            if (ColumnsNames.contains(label)) continue;
            for (final String name : ColumnsNames) {
                if (name.equalsIgnoreCase(label) && !labels.contains(name)) {
                    labels.set(i, name);
                    for (final DataForDB row : rows) row.RenameColumn(label, name);
                    break;
                }
            }
        }
    }

    private void putData(final ArrayList<DataForDB> data) {
       // dataList.remove(0, dataList.size());
        dataList.clear();
        dataList.addAll(data);
    }

    public boolean insertData(HashMap<String, String> values) {
        System.out.println(values);
        if (!Database.Inserter().insertData(TableMetadata.getName(), values)) {
            ShowError("SQL Error", "Error to insert data\n" + Database.GetException());
            return false;
        }
        // Sem a chave completa (autoincremento, rowid) a linha nova não se deixava editar
        // nem apagar: nesses casos vai-se buscar a página outra vez.
        final boolean keyKnown = TableMetadata.hasPrimaryKey()
                && TableMetadata.getPrimaryKeys().stream().allMatch(key -> values.get(key) != null && !values.get(key).isEmpty());
        if (keyKnown && dataList.size() < Database.buffer) {
            final DataForDB data = new DataForDB(new HashMap<>(values));
            dataList.add(data);
        } else {
            prepareFetch();
        }
        return true;
    }

    /** Mostra só as colunas do resultado (sem distinguir maiúsculas) e tira as temporárias. */
    private void hideColumns(final ArrayList<String> columns) {
        for (TableColumn<DataForDB, String> temporary : TemporaryColumnsContainer.values()) {
            tableContainer.getColumns().remove(temporary);
        }
        TemporaryColumnsContainer.clear();
        final Set<String> visible = columns.stream().map(c -> c.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        for (TableColumn<DataForDB, ?> tableColumn : tableContainer.getColumns()) {
            tableColumn.setVisible(tableColumn.getId() != null && visible.contains(tableColumn.getId().toLowerCase(Locale.ROOT)));
        }
    }

        // for advanced search
    public boolean fetchDataCallback(final ArrayList<String> columns) {
        advancedSearchColumns = columns;
        final QueryBuilder builder = Database.builder().readable();
        final List<String> names = columns.stream().map(builder::name).toList();
        codeField.setText(builder.select(names).from(TableMetadata.getName()).build() + ";");
        AdvancedSearchButton.fire();
        return true;
    }

    private void resetAdvancedSearch() {
        if (advancedSearch) {
            advancedSearch = false;
            advancedSearchColumns = null;
            codeSQL = "";
            codeField.setText(defaultQuery());
            ColumnsFetched = new ArrayList<>(ColumnsNames);
            switching = PageNum.get() != 0;
            PageNum.set(0);
            prepareFetch(() -> hideColumns(ColumnsNames));
            setTotalPages();
        }
    }

    public void setTotalPages() {
        Thread.ofVirtual().start(()->{
            final long pages = Database.totalPages(TableMetadata.getName());
            if (pages < 0) System.out.println(Database.GetException());
            // Uma tabela vazia tem uma página (a 0), não -1.
            totalPages = pages < 0 ? Long.MAX_VALUE : Math.max(0, pages - 1);
            System.out.println("encontrado " + totalPages);
            Platform.runLater(()->{
                if (PageNum.get() != 0) PageNum.set(0);
                updatePageLabel();
            });
        });
    }

    /** Páginas da consulta da pesquisa avançada, contadas pelo motor sobre a própria consulta. */
    private void setTotalPagesOfQuery() {
        final String query = codeSQL;
        Thread.ofVirtual().start(() -> {
            final long pages = Database.totalPagesOfQuery(query);
            Platform.runLater(() -> {
                totalPages = pages < 0 ? Long.MAX_VALUE : Math.max(0, pages - 1);
                updatePageLabel();
            });
        });
    }

    public void setTotalPages(final ArrayList<String> columns, final String condition) {
        Thread.ofVirtual().start(()->{

            long max = -1;

            final int indexOffset = condition.toUpperCase().indexOf("OFFSET");
            String conditionComplete = condition;
            if (indexOffset != -1) {
                System.out.println("vd");
                conditionComplete = condition.replace(condition.substring(indexOffset-1), "");
            }
            for (final String column : columns) {
                final long pages = Database.totalPages(TableMetadata.getName(), column, conditionComplete) - 1;
                if (pages < -1) {
                    System.out.println(Database.GetException());
                } else if (pages > max) {
                    max = pages;
                }
            }
            totalPages = max == -1 ? Long.MAX_VALUE : Math.max(0, max);
            Platform.runLater(()->PageNum.set(0));
        });
    }

    public void closeColumns() {
        DBTabContainer.getTabs().clear();
        tableContainer.getColumns().clear();
        dataList.clear();
    }

    @Override
    public void onLowMemory() {
        // Com a pilha vazia o pop() lançava EmptyStackException.
        if (stageName.isEmpty()) return;
        final StagesNamesEnum stageId = stageName.pop();
        if (stageId != null) {
            final Stage stage = stagesOpened.get(stageId);
            if (stage == null) return;
            if (!stage.isShowing()) {
                stage.close();
                stagesOpened.remove(stageId);
            }
            else {
                stageName.push(stageId);
                stagesOpened.put(stageId, stage);
            }
        }
    }
}
