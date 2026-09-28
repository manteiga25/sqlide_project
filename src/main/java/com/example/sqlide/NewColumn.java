package com.example.sqlide;

import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.example.sqlide.drivers.model.DatabaseInfo;
import com.example.sqlide.drivers.model.QueryBuilder;
import com.example.sqlide.drivers.model.SQLTypes;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Formulário para criar ou editar uma coluna (numa tabela que já existe ou na janela
 * "Create table").
 *
 * <p>O que estava mal e mudou:</p>
 * <ul>
 *   <li>O tipo começava fixo em "INTEGER" — que nem existe na lista do MySQL — e os campos
 *       de tamanho só acordavam depois de se mexer no tipo.</li>
 *   <li>Marcar "Foreign Key" desativava o tipo, a chave primária, NOT NULL, DEFAULT e UNIQUE,
 *       e desmarcar voltava a ligar os campos de tamanho e da lista mesmo quando o tipo não
 *       os usava. Uma coluna pode ser chave primária e estrangeira ao mesmo tempo (numa tabela
 *       de ligação) e precisa do mesmo tipo da coluna referenciada, que agora é escolhido
 *       sozinho quando se escolhe a referência.</li>
 *   <li>Na janela "Create table" as caixas ON UPDATE / ON DELETE ficavam vazias.</li>
 *   <li>A opção Autoincrement não ia para lado nenhum.</li>
 *   <li>Um índice sem tipo escolhido dava {@code CREATE null INDEX}.</li>
 *   <li>O "Set name" era exigido em qualquer ENUM/SET, mas só o PostgreSQL precisa de um nome
 *       de tipo; o campo dos valores pedia "value1,value2,..." e guardava o texto inteiro como
 *       um só valor, e a lista nunca aparecia.</li>
 *   <li>A janela fechava antes de a base de dados responder: se o ALTER falhasse, perdia-se
 *       o que estava escrito. Agora só fecha depois de a coluna estar gravada.</li>
 * </ul>
 */
public class NewColumn {

    // Componentes FXML
    @FXML private JFXButton CommentButton, CreateButton;
    @FXML private JFXTextField IndexText, CheckField;
    @FXML private TextField ColumnNameInput, text1, text2, text3, DefaultValueText, SetName, WordBox;
    @FXML private Label LabelDB, ItemsLabel;
    @FXML private ChoiceBox<String> typeBox, ForeignKeyBox, indexBox, updateForeignBox, deleteForeignBox;
    @FXML private JFXCheckBox primaryKeyOption, NotNullOption, ForeignKeyOption, DefaultOption,
            UniqueOption, FillOption, IndexOption, AutoincrementOption, CheckOption;
    @FXML private Button AddButton, EditList;

    private Stage window;
    private TableInterface ref;
    private NewTable newTable;
    private HashMap<String, ArrayList<String>> KeysForForeign;
    private ColumnMetadata originalMetadata;
    private String[] charList;
    private boolean Edit = false;
    private final ObservableList<String> setList = FXCollections.observableArrayList();
    private String comment = "";

    private String tableName = "";
    private SQLTypes sqlType = SQLTypes.SQLITE;
    /** "tabela: coluna" → metadados da coluna, para copiar o tipo da coluna referenciada. */
    private Map<String, ColumnMetadata> referencedColumns = Map.of();
    /** Nomes que já existem na tabela, para não criar duas colunas com o mesmo nome. */
    private final Set<String> existingColumns = new HashSet<>();

    @FXML
    private void initialize() {
        typeBox.setOnAction(_ -> checkType());
        ForeignKeyBox.setDisable(true);
        DefaultValueText.setDisable(true);

        // Configurar placeholders
        ColumnNameInput.setPromptText("Enter column name");
        DefaultValueText.setPromptText("Default value");
        IndexText.setPromptText("Index name (optional)");
        CheckField.setPromptText("Check condition");
        WordBox.setPromptText("value1, value2, ...");
        SetName.setPromptText("PostgreSQL enum");

        setList.addListener((ListChangeListener<String>) _ -> updateItemsLabel());
        ForeignKeyBox.getSelectionModel().selectedItemProperty().addListener((_, _, reference) -> applyReferencedType(reference));
        AutoincrementOption.setOnAction(_ -> {
            // Uma coluna preenchida pelo motor é, em quase todos os casos, a chave primária.
            if (AutoincrementOption.isSelected() && sqlType != SQLTypes.POSTGRESQL) primaryKeyOption.setSelected(true);
        });
    }

/*    public void NewColumnWin(String DBName, String TableName, TableInterface ref, Stage subStage,
                             HashMap<String, ArrayList<String>> KeysForForeign, SQLiteTypes types,
                             String[] list, String[] charList, String[] modes,
                             List<String> foreignModes, SQLTypes sql) {

        setupCommonConfig(DBName, TableName, KeysForForeign, types, list, charList, subStage);
        this.ref = ref;

        updateForeignBox.getItems().addAll(foreignModes);
        deleteForeignBox.getItems().addAll(foreignModes);

        if (modes != null) {
            indexBox.getItems().addAll(modes);
        }

        if (sql == SQLTypes.SQLITE) {
            removeCommentButton();
        }
    } */

    public void NewColumnWin(String DBName, String TableName, TableInterface ref, Stage subStage,
                             HashMap<String, ArrayList<String>> KeysForForeign, SQLiteTypes types,
                             DatabaseInfo databaseInfo) {
        this.ref = ref;
        setupCommonConfig(DBName, TableName, KeysForForeign, databaseInfo, subStage);
    }

    public void NewColumnWin(String DBName, String TableName, NewTable ref, Stage subStage,
                             HashMap<String, ArrayList<String>> KeysForForeign, SQLiteTypes types,
                             DatabaseInfo databaseInfo) {
        this.newTable = ref;
        setupCommonConfig(DBName, TableName, KeysForForeign, databaseInfo, subStage);
        // Numa tabela que ainda não existe não há linhas para preencher.
        FillOption.setVisible(false);
        FillOption.setManaged(false);
    }

    /** Tipos das colunas que podem ser referenciadas, com a mesma chave que a caixa da chave estrangeira. */
    public void setReferencedColumns(final Map<String, ColumnMetadata> referencedColumns) {
        this.referencedColumns = referencedColumns == null ? Map.of() : referencedColumns;
    }

    /** Colunas que a tabela já tem. */
    public void setExistingColumns(final Collection<String> columns) {
        existingColumns.clear();
        if (columns != null) for (String column : columns) existingColumns.add(column.toLowerCase(Locale.ROOT));
    }

    private void setupCommonConfig(String DBName, String TableName,
                                   HashMap<String, ArrayList<String>> KeysForForeign,
                                   DatabaseInfo databaseInfo, Stage subStage) {

        this.KeysForForeign = KeysForForeign != null ? KeysForForeign : new HashMap<>();
        this.charList = databaseInfo.getListChars() == null ? new String[0] : databaseInfo.getListChars();
        this.window = subStage;
        this.tableName = TableName == null ? "" : TableName;
        this.sqlType = databaseInfo.getSqlType();

        LabelDB.setText("Database: " + DBName + "\nTable: " + TableName);
        typeBox.getItems().setAll(databaseInfo.getList());
        initForeignKeyBox();

        // As duas janelas (tabela nova e tabela existente) precisam das mesmas opções.
        updateForeignBox.getItems().setAll(databaseInfo.getForeignModes());
        deleteForeignBox.getItems().setAll(databaseInfo.getForeignModes());
        if (databaseInfo.getIndexModes() != null) {
            indexBox.getItems().setAll(databaseInfo.getIndexModes());
            indexBox.getSelectionModel().selectFirst();
        }

        // O SQLite não guarda comentários de colunas.
        if (sqlType == SQLTypes.SQLITE) {
            removeCommentButton();
        }

        selectDefaultType();
    }

    private void selectDefaultType() {
        for (String candidate : List.of("INTEGER", "INT")) {
            if (typeBox.getItems().contains(candidate)) {
                typeBox.setValue(candidate);
                checkType();
                return;
            }
        }
        if (!typeBox.getItems().isEmpty()) typeBox.setValue(typeBox.getItems().getFirst());
        checkType();
    }

    private void removeCommentButton() {
        if (CommentButton.getParent() instanceof HBox parent) {
            parent.getChildren().remove(CommentButton);
        }
    }

    private void initForeignKeyBox() {
        ForeignKeyBox.getItems().clear();
        for (Map.Entry<String, ArrayList<String>> entry : KeysForForeign.entrySet()) {
            String key = entry.getKey();
            for (String id : entry.getValue()) {
                ForeignKeyBox.getItems().add(key + ": " + id);
            }
        }
        ForeignKeyBox.getItems().sort(String.CASE_INSENSITIVE_ORDER);
    }

    /** Liga e desliga só o que é da chave estrangeira; o resto do formulário não muda. */
    @FXML
    private void AlterForeignBox() {
        boolean isForeignSelected = ForeignKeyOption.isSelected();

        ForeignKeyBox.setDisable(!isForeignSelected);
        FillOption.setDisable(!isForeignSelected || Edit);
        deleteForeignBox.setDisable(!isForeignSelected);
        updateForeignBox.setDisable(!isForeignSelected);

        if (isForeignSelected) applyReferencedType(ForeignKeyBox.getValue());
    }

    /**
     * A coluna estrangeira fica com o tipo da coluna referenciada (o MySQL recusa a chave se
     * os tipos não coincidirem). Um SERIAL do PostgreSQL passa a INTEGER: a sequência é da
     * coluna de lá, não desta.
     */
    private void applyReferencedType(final String reference) {
        if (reference == null || Edit || !ForeignKeyOption.isSelected()) return;
        final ColumnMetadata referenced = referencedColumns.get(reference);
        if (referenced == null || referenced.Type == null || referenced.Type.isBlank()) return;

        String type = referenced.Type.toUpperCase(Locale.ROOT);
        type = switch (type) {
            case "SERIAL", "SERIAL4" -> "INTEGER";
            case "BIGSERIAL", "SERIAL8" -> "BIGINT";
            case "SMALLSERIAL", "SERIAL2" -> "SMALLINT";
            case "COUNTER" -> "LONG";
            default -> type;
        };
        if (!typeBox.getItems().contains(type)) typeBox.getItems().add(type);
        typeBox.setValue(type);
        checkType();
        if (!text1.isDisabled() && referenced.size > 0) text1.setText(String.valueOf(referenced.size));
        if (!text2.isDisabled()) {
            text2.setText(String.valueOf(referenced.integerDigits));
            text3.setText(String.valueOf(referenced.decimalDigits));
        }
    }

    @FXML
    private void AlterIndexState() {
        boolean disable = !IndexOption.isSelected();
        IndexText.setDisable(disable);
        indexBox.setDisable(disable);
    }

    @FXML
    private void AlterCheckState() {
        CheckField.setDisable(!CheckOption.isSelected());
    }

    @FXML
    private void AlterDefaultText() {
        DefaultValueText.setDisable(!DefaultOption.isSelected());
    }

    @FXML
    private void checkType() {
        String type = typeBox.getValue();
        if (type == null) return;
        final String base = type.toUpperCase(Locale.ROOT);

        boolean isCharType = Arrays.asList(charList).contains(type);
        boolean isDecimal = "DECIMAL".equals(base) || "NUMERIC".equals(base);
        boolean isListType = "ENUM".equals(base) || "SET".equals(base);

        switchSize(isCharType);
        switchDecimal(isDecimal);
        switchList(isListType);
        // Só o PostgreSQL dá nome aos tipos enumerados.
        SetName.setDisable(!(isListType && sqlType == SQLTypes.POSTGRESQL));
    }

    private void switchSize(boolean enable) {
        text1.setDisable(!enable);
    }

    private void switchDecimal(boolean enable) {
        text2.setDisable(!enable);
        text3.setDisable(!enable);
    }

    private void switchList(boolean enable) {
        EditList.setDisable(!enable);
        AddButton.setDisable(!enable);
        WordBox.setDisable(!enable);
        ItemsLabel.setDisable(!enable);
    }

    /** Aceita vários valores de uma vez, separados por vírgulas (como o campo sugere). */
    @FXML
    private void addWord() {
        final List<String> words = QueryBuilder.splitList(WordBox.getText());
        if (words.isEmpty()) {
            showFieldError(WordBox, "Please enter a valid item");
            return;
        }

        final List<String> repeated = new ArrayList<>();
        for (String word : words) {
            // 'valor' e valor são o mesmo valor.
            final String value = QueryBuilder.unquote(word);
            if (setList.contains(value)) repeated.add(value);
            else setList.add(value);
        }

        WordBox.clear();
        clearFieldError(WordBox);
        if (!repeated.isEmpty()) ShowInformation("Repeated values", "Already in the list: " + String.join(", ", repeated));
    }

    private void updateItemsLabel() {
        ItemsLabel.setText(setList.isEmpty()
                ? "No values yet."
                : setList.size() + " value(s): " + String.join(", ", setList));
    }

    private void showFieldError(Control field, String message) {
        field.setStyle("-fx-border-color: red;");
        ShowError("Invalid Value", message);
        field.requestFocus();
    }

    private void clearFieldError(Control field) {
        field.setStyle("");
    }

    @FXML
    private void loadComment() {
        try {
            TextArea textArea = new TextArea(comment);
            textArea.setWrapText(true);
            textArea.setStyle("-fx-control-inner-background: #2C2C2C; -fx-text-fill: white;");

            AnchorPane container = new AnchorPane(textArea);
            AnchorPane.setTopAnchor(textArea, 0.0);
            AnchorPane.setRightAnchor(textArea, 0.0);
            AnchorPane.setBottomAnchor(textArea, 0.0);
            AnchorPane.setLeftAnchor(textArea, 0.0);

            Stage dialog = new Stage();
            dialog.setTitle("Column Comment");
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.setScene(new Scene(container, 400, 300));

            dialog.setOnHiding(_ -> comment = textArea.getText());
            dialog.show();
        } catch (Exception e) {
            ShowError("Error", "Failed to open comment editor.", e.getMessage());
        }
    }

    @FXML
    private void EditSet() {
        if (!setList.isEmpty()) {
            try {
                // Carrega o arquivo FXML
                FXMLLoader loader = new FXMLLoader(getClass().getResource("EditSet.fxml"));
                //    VBox miniWindow = loader.load();
                Parent root = loader.load();

                EditSetController secondaryController = loader.getController();

                // Criar um novo Stage para a subjanela
                Stage subStage = new Stage();
                subStage.setTitle("Edit values");
                subStage.setScene(new Scene(root));
                secondaryController.InitializeController(setList);
//            secondaryController.initEventWindow(DatabaseOpened.get(ContainerForDB.getSelectionModel().getSelectedItem().getId()));

                // Opcional: definir a modalidade da subjanela
                subStage.initModality(Modality.APPLICATION_MODAL);

                // Mostrar a subjanela
                subStage.show();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            ShowInformation("No data", "No values to edit.");
        }
    }

    @FXML
    private void createColumn() {
        if (!validateForm()) {
            return;
        }

        ColumnMetadata meta = buildColumnMetadata();

        saveColumn(meta);
    }

    private boolean validateForm() {
        // Validação básica do nome
        final String name = ColumnNameInput.getText() == null ? "" : ColumnNameInput.getText().trim();
        if (name.isEmpty()) {
            showFieldError(ColumnNameInput, "Column name is required");
            return false;
        }
        final boolean sameAsBefore = Edit && originalMetadata != null && originalMetadata.Name.equalsIgnoreCase(name);
        if (!sameAsBefore && existingColumns.contains(name.toLowerCase(Locale.ROOT))) {
            showFieldError(ColumnNameInput, "The table already has a column called " + name + ".");
            return false;
        }

        if (typeBox.getValue() == null || typeBox.getValue().isBlank()) {
            showFieldError(typeBox, "Choose a type");
            return false;
        }
        final String type = typeBox.getValue().toUpperCase(Locale.ROOT);

        // Validação da chave estrangeira
        if (ForeignKeyOption.isSelected() && (ForeignKeyBox.getValue() == null || ForeignKeyBox.getValue().isEmpty())) {
            showFieldError(ForeignKeyBox, "Please select a foreign key reference");
            return false;
        }

        // Tamanho e casas decimais são opcionais; se estiverem escritos têm de ser números.
        // O VARCHAR do MySQL é o único que exige tamanho.
        final boolean sizeRequired = sqlType == SQLTypes.MYSQL && (type.equals("VARCHAR") || type.equals("VARBINARY"));
        if (!validateNumericField(text1, "Size", 1, sizeRequired) ||
                !validateNumericField(text2, "Integer digits", 0, false) ||
                !validateNumericField(text3, "Decimal digits", 0, false)) {
            return false;
        }
        if (!text2.isDisabled() && !text3.getText().isBlank() && text2.getText().isBlank()) {
            showFieldError(text2, "Write the integer digits too");
            return false;
        }

        // Validação de valor padrão
        if (DefaultOption.isSelected() && (DefaultValueText.getText() == null || DefaultValueText.getText().isEmpty())) {
            showFieldError(DefaultValueText, "Default value is required");
            return false;
        }

        // Lista de valores (ENUM/SET)
        if (!WordBox.isDisabled() && setList.isEmpty()) {
            showFieldError(WordBox, "Add at least one value to the list");
            return false;
        }
        if (!SetName.isDisabled() && (SetName.getText() == null || SetName.getText().isBlank())) {
            showFieldError(SetName, "PostgreSQL needs a name for the enum type");
            return false;
        }

        if (CheckOption.isSelected() && (CheckField.getText() == null || CheckField.getText().isBlank())) {
            showFieldError(CheckField, "Write the check condition or untick Enable Check");
            return false;
        }

        if (AutoincrementOption.isSelected()) {
            if (!isIntegerType(type)) {
                showFieldError(typeBox, "Autoincrement needs an integer type");
                return false;
            }
            if (sqlType == SQLTypes.SQLITE && !primaryKeyOption.isSelected()) {
                showFieldError(primaryKeyOption, "In SQLite an autoincrement column has to be the primary key");
                return false;
            }
            if (sqlType == SQLTypes.MYSQL && !primaryKeyOption.isSelected() && !UniqueOption.isSelected()) {
                showFieldError(primaryKeyOption, "In MySQL an autoincrement column has to be a key (primary key or unique)");
                return false;
            }
        }

        return true;
    }

    private static boolean isIntegerType(final String type) {
        final String base = type.contains("(") ? type.substring(0, type.indexOf('(')) : type;
        return base.contains("INT") || base.contains("SERIAL") || base.equals("COUNTER") || base.equals("LONG");
    }

    private boolean validateNumericField(TextField field, String fieldName, int minimum, boolean required) {
        if (field.isDisabled()) return true;
        final String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty() && !required) {
            clearFieldError(field);
            return true;
        }

        try {
            int value = Integer.parseInt(text);
            if (value < minimum) {
                showFieldError(field, fieldName + " must be " + minimum + " or greater");
                return false;
            }
            clearFieldError(field);
            return true;
        } catch (NumberFormatException e) {
            showFieldError(field, fieldName + " must be a valid integer");
            return false;
        }
    }

    private static int number(final TextField field) {
        if (field.isDisabled() || field.getText() == null || field.getText().isBlank()) return 0;
        return Integer.parseInt(field.getText().trim());
    }

    private ColumnMetadata buildColumnMetadata() {
        ColumnMetadata meta = new ColumnMetadata();

        meta.Name = ColumnNameInput.getText().trim();
        meta.Type = typeBox.getValue();
        meta.IsPrimaryKey = primaryKeyOption.isSelected();
        meta.NOT_NULL = NotNullOption.isSelected();
        meta.isUnique = UniqueOption.isSelected();
        meta.defaultValue = DefaultOption.isSelected() ? DefaultValueText.getText().trim() : "";
        meta.foreign = new ColumnMetadata.Foreign();

        if (ForeignKeyOption.isSelected()) {
            String[] parts = ForeignKeyBox.getValue().split(":", 2);
            if (parts.length >= 2) {
                meta.foreign.isForeign = true;
                meta.foreign.tableRef = parts[0].trim();
                meta.foreign.columnRef = parts[1].trim();
                meta.foreign.onEliminate = deleteForeignBox.getValue() == null ? "" : deleteForeignBox.getValue();
                meta.foreign.onUpdate = updateForeignBox.getValue() == null ? "" : updateForeignBox.getValue();
            }
        }

        meta.size = number(text1);
        meta.integerDigits = number(text2);
        meta.decimalDigits = number(text3);

        // Índice: sem nome escolhido, fica idx_tabela_coluna (antes o nome vazio dava erro).
        if (IndexOption.isSelected()) {
            final String indexName = IndexText.getText() == null ? "" : IndexText.getText().trim();
            meta.index = indexName.isEmpty() ? QueryBuilder.indexName(tableName, List.of(meta.Name)) : indexName;
            meta.indexType = indexBox.getValue() == null ? "" : indexBox.getValue();
        }

        // Lista de valores (ENUM/SET)
        if (!WordBox.isDisabled()) {
            meta.items = new ArrayList<>(setList);
            if (!SetName.isDisabled()) meta.aliasType = SetName.getText().trim();
        }

        // Check constraint
        if (CheckOption.isSelected()) {
            meta.check = CheckField.getText().trim();
        }

        // Autoincremento: 1 é pedido explicitamente; 0 é a INTEGER PRIMARY KEY do SQLite, que se mantém.
        if (AutoincrementOption.isSelected()) meta.autoincrement = 1;
        else if (Edit && originalMetadata != null && originalMetadata.autoincrement == 0) meta.autoincrement = 0;

        // O que o formulário não mostra (COLLATE, ON UPDATE...) não se perde ao editar.
        if (Edit && originalMetadata != null) meta.extra = originalMetadata.extra;

        // Comentário
        meta.comment = comment;

        return meta;
    }

    private void saveColumn(ColumnMetadata meta) {
        try {
            if (Edit) handleEditColumn(meta);
            else handleNewColumn(meta);
        } catch (Exception e) {
            finished(false);
            ShowError("Save Error", "Failed to save column.", e.getMessage());
        }
    }

    /** Fecha a janela quando a base de dados confirmou; se falhou, o formulário fica como estava. */
    private void finished(final boolean saved) {
        if (saved) window.close();
        else CreateButton.setDisable(false);
    }

    private boolean handleEditColumn(ColumnMetadata meta) {
        if (ref != null && originalMetadata != null) {
            CreateButton.setDisable(true);
            ref.alterColumnMetadata(originalMetadata, meta, this::finished);
            return true;
        } else if (newTable != null) {
            newTable.EditColumnCallBack(meta);
            finished(true);
            return true;
        }
        ShowError("Save Error", "No valid reference for editing column");
        return false;
    }

    private boolean handleNewColumn(ColumnMetadata meta) {
        if (newTable != null) {
            newTable.PutColumnCallback(meta);
            finished(true);
            return true;
        } else if (ref != null) {
            CreateButton.setDisable(true);
            ref.createDBCol(meta.Name, meta, FillOption.isSelected() && !FillOption.isDisabled(), this::finished);
            return true;
        }
        ShowError("Save Error", "No valid reference for creating column");
        return false;
    }

    public void insertMetadata(ColumnMetadata metadata) {
        Edit = true;
        this.originalMetadata = metadata;
        CreateButton.setText("Save");

        // Preencher campos básicos
        ColumnNameInput.setText(metadata.Name);
        // Tipos que o catálogo devolve e não estão na lista (INT4, TINYINT(1)...) entram nela.
        if (metadata.Type != null && !metadata.Type.isBlank() && !typeBox.getItems().contains(metadata.Type)) {
            typeBox.getItems().add(metadata.Type);
        }
        typeBox.setValue(metadata.Type);
        primaryKeyOption.setSelected(metadata.IsPrimaryKey);
        NotNullOption.setSelected(metadata.NOT_NULL);
        DefaultOption.setSelected(metadata.defaultValue != null && !metadata.defaultValue.isEmpty());
        DefaultValueText.setText(metadata.defaultValue != null ? metadata.defaultValue : "");
        UniqueOption.setSelected(metadata.isUnique);
        AutoincrementOption.setSelected(metadata.autoincrement >= 1);

        // Preencher chave estrangeira
        if (metadata.foreign != null && metadata.foreign.isForeign) {
            final String reference = metadata.foreign.tableRef + ": " + metadata.foreign.columnRef;
            if (!ForeignKeyBox.getItems().contains(reference)) ForeignKeyBox.getItems().add(reference);
            ForeignKeyOption.setSelected(true);
            ForeignKeyBox.setValue(reference);
            // As ações ON UPDATE / ON DELETE não eram repostas: ao gravar a edição
            // voltavam a null e a referência perdia o comportamento configurado.
            if (metadata.foreign.onUpdate != null && !metadata.foreign.onUpdate.isBlank()) {
                updateForeignBox.setValue(metadata.foreign.onUpdate);
            }
            if (metadata.foreign.onEliminate != null && !metadata.foreign.onEliminate.isBlank()) {
                deleteForeignBox.setValue(metadata.foreign.onEliminate);
            }
        }

        // Índice: sem isto, editar qualquer coisa numa coluna indexada apagava o índice.
        if (metadata.index != null && !metadata.index.isBlank()) {
            IndexOption.setSelected(true);
            IndexText.setText(metadata.index);
            if (metadata.indexType != null && !metadata.indexType.isBlank()) {
                indexBox.setValue(metadata.indexType);
            }
        }
        AlterIndexState();

        // Check constraint: mesma situação — era descartado ao gravar.
        if (metadata.check != null && !metadata.check.isBlank()) {
            CheckOption.setSelected(true);
            CheckField.setText(metadata.check);
        }
        AlterCheckState();

        // Atualizar estados
        checkType();
        AlterForeignBox();
        AlterDefaultText();

        // Preencher valores numéricos
        text1.setText(metadata.size > 0 ? String.valueOf(metadata.size) : "");
        text2.setText(metadata.integerDigits > 0 || metadata.decimalDigits > 0 ? String.valueOf(metadata.integerDigits) : "");
        text3.setText(metadata.integerDigits > 0 || metadata.decimalDigits > 0 ? String.valueOf(metadata.decimalDigits) : "");

        // Preencher lista de valores
        if (metadata.items != null) {
            setList.setAll(metadata.items);
        }
        if (metadata.aliasType != null && !metadata.aliasType.isBlank()) {
            SetName.setText(metadata.aliasType);
        }

        // Comentário
        comment = metadata.comment != null ? metadata.comment : "";
    }

    @FXML
    private void closeWindow() {
        window.close();
    }
}
