package com.example.sqlide;

import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.Metadata.ColumnMetadata;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.example.sqlide.drivers.model.SQLTypes;
import com.example.sqlide.drivers.model.TypesModelList;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.collections.FXCollections;
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

public class NewColumn {

    // Componentes FXML
    @FXML private JFXButton CommentButton;
    @FXML private JFXTextField IndexText, CheckField;
    @FXML private TextField ColumnNameInput, text1, text2, text3, DefaultValueText, SetName, WordBox;
    @FXML private Label LabelDB;
    @FXML private ChoiceBox<String> typeBox, ForeignKeyBox, indexBox, updateForeignBox, deleteForeignBox;
    @FXML private CheckBox primaryKeyOption, NotNullOption, ForeignKeyOption, DefaultOption,
            UniqueOption, FillOption, IndexOption, AutoincrementOption, CheckOption;
    @FXML private Button AddButton, EditList;

    private Stage window;
    private TableInterface ref;
    private NewTable newTable;
    private String TableName, DBName;
    private HashMap<String, ArrayList<String>> KeysForForeign;
    private ColumnMetadata originalMetadata;
    private SQLiteTypes types;
    private String[] charList;
    private TypesModelList list;
    private boolean Edit = false;
    private final ObservableList<String> setList = FXCollections.observableArrayList();
    private String comment = "";

    @FXML
    private void initialize() {
        typeBox.setValue("INTEGER");
        typeBox.setOnAction(_ -> checkType());
        ForeignKeyBox.setDisable(true);
        DefaultValueText.setDisable(true);

        // Configurar placeholders
        ColumnNameInput.setPromptText("Enter column name");
        DefaultValueText.setPromptText("Default value");
        IndexText.setPromptText("Index name");
        CheckField.setPromptText("Check condition");
        WordBox.setPromptText("Add item to list");
        SetName.setPromptText("Set name");
    }

    public void NewColumnWin(String DBName, String TableName, TableInterface ref, Stage subStage,
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
    }

    public void NewColumnWin(String DBName, String TableName, NewTable ref, Stage subStage,
                             HashMap<String, ArrayList<String>> KeysForForeign, SQLiteTypes types,
                             String[] list, String[] charList, String[] modes, SQLTypes sql) {

        setupCommonConfig(DBName, TableName, KeysForForeign, types, list, charList, subStage);
        this.newTable = ref;

        if (modes != null) {
            indexBox.getItems().addAll(modes);
        }

        if (sql == SQLTypes.SQLITE) {
            removeCommentButton();
        }
    }

    private void setupCommonConfig(String DBName, String TableName,
                                   HashMap<String, ArrayList<String>> KeysForForeign,
                                   SQLiteTypes types, String[] list, String[] charList, Stage subStage) {

        this.DBName = DBName;
        this.TableName = TableName;
        this.KeysForForeign = KeysForForeign != null ? KeysForForeign : new HashMap<>();
        this.types = types;
        this.charList = charList;
        this.window = subStage;

        LabelDB.setText("Database: " + DBName + "\nTable: " + TableName);
        typeBox.getItems().addAll(list);
        initForeignKeyBox();
    }

    private void removeCommentButton() {
        HBox parent = (HBox) CommentButton.getParent();
        if (parent != null) {
            parent.getChildren().remove(CommentButton);
        }
    }

    private void initForeignKeyBox() {
        for (Map.Entry<String, ArrayList<String>> entry : KeysForForeign.entrySet()) {
            String key = entry.getKey();
            for (String id : entry.getValue()) {
                ForeignKeyBox.getItems().add(key + ": " + id);
            }
        }
    }

    @FXML
    private void AlterForeignBox() {
        boolean isForeignSelected = ForeignKeyOption.isSelected() && !ForeignKeyOption.isDisabled();

        // Atualizar estado dos componentes
        ForeignKeyBox.setDisable(!isForeignSelected);
        FillOption.setDisable(!isForeignSelected);
        deleteForeignBox.setDisable(!isForeignSelected);
        updateForeignBox.setDisable(!isForeignSelected);

        boolean enableNonForeign = !isForeignSelected;
        primaryKeyOption.setDisable(!enableNonForeign);
        NotNullOption.setDisable(!enableNonForeign);
        DefaultOption.setDisable(!enableNonForeign);
        typeBox.setDisable(!enableNonForeign);
        UniqueOption.setDisable(!enableNonForeign);
        text1.setDisable(!enableNonForeign);
        text2.setDisable(!enableNonForeign);
        text3.setDisable(!enableNonForeign);
        AddButton.setDisable(!enableNonForeign);
        WordBox.setDisable(!enableNonForeign);

        if (!isForeignSelected) {
            switchSize(false);
            switchDecimal(false);
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

        boolean isCharType = Arrays.stream(charList).anyMatch(t -> t.equals(type));
        boolean isDecimal = "DECIMAL".equals(type);
        boolean isListType = "ENUM".equals(type) || "SET".equals(type);

        switchSize(!isCharType);
        switchDecimal(!isDecimal);
        switchList(!isListType);
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
        SetName.setDisable(!enable);
        AddButton.setDisable(!enable);
        WordBox.setDisable(!enable);
    }

    @FXML
    private void addWord() {
        String word = WordBox.getText();
        if (word == null || word.trim().isEmpty()) {
            showFieldError(WordBox, "Please enter a valid item");
            return;
        }

        if (setList.contains(word)) {
            showFieldError(WordBox, "Item already exists in the list");
            return;
        }

        setList.add(word);
        WordBox.clear();
        clearFieldError(WordBox);
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
            ShowError("Error", "Failed to open comment editor: " + e.getMessage());
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
                subStage.setTitle("Subjanela");
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
        if (meta == null) return;

        saveColumn(meta);
    }

    private boolean validateForm() {
        // Validação básica do nome
        if (ColumnNameInput.getText() == null || ColumnNameInput.getText().trim().isEmpty()) {
            showFieldError(ColumnNameInput, "Column name is required");
            return false;
        }

        // Validação da chave estrangeira
        if (ForeignKeyOption.isSelected() && (ForeignKeyBox.getValue() == null || ForeignKeyBox.getValue().isEmpty())) {
            showFieldError(ForeignKeyBox, "Please select a foreign key reference");
            return false;
        }

        // Validação de valores numéricos
        if (!validateNumericField(text1, "Size") ||
                !validateNumericField(text2, "Integer digits") ||
                !validateNumericField(text3, "Decimal digits")) {
            return false;
        }

        // Validação de valor padrão
        if (DefaultOption.isSelected() && (DefaultValueText.getText() == null || DefaultValueText.getText().isEmpty())) {
            showFieldError(DefaultValueText, "Default value is required");
            return false;
        }

        // Validação de lista de valores
        if (!SetName.isDisabled() && (SetName.getText() == null || SetName.getText().isEmpty())) {
            showFieldError(SetName, "Set name is required");
            return false;
        }

        return true;
    }

    private boolean validateNumericField(TextField field, String fieldName) {
        if (field.isDisabled()) return true;

        try {
            int value = Integer.parseInt(field.getText());
            if (value <= 0) {
                showFieldError(field, fieldName + " must be a positive integer");
                return false;
            }
            return true;
        } catch (NumberFormatException e) {
            showFieldError(field, fieldName + " must be a valid integer");
            return false;
        }
    }

    private ColumnMetadata buildColumnMetadata() {
        ColumnMetadata meta = new ColumnMetadata();

        // Propriedades básicas
        meta.Name = ColumnNameInput.getText().trim();
        meta.Type = typeBox.getValue();
        meta.IsPrimaryKey = primaryKeyOption.isSelected() && !primaryKeyOption.isDisabled();
        meta.NOT_NULL = NotNullOption.isSelected() && !NotNullOption.isDisabled();
        meta.isUnique = UniqueOption.isSelected() && !UniqueOption.isDisabled();
        meta.defaultValue = DefaultOption.isSelected() ? DefaultValueText.getText().trim() : "";

        // Configuração de chave estrangeira
        if (ForeignKeyOption.isSelected()) {
            String[] parts = ForeignKeyBox.getValue().split(":");
            if (parts.length >= 2) {
                meta.foreign = new ColumnMetadata.Foreign();
                meta.foreign.isForeign = true;
                meta.foreign.tableRef = parts[0].trim();
                meta.foreign.columnRef = parts[1].trim();
                meta.foreign.onEliminate = deleteForeignBox.getValue();
                meta.foreign.onUpdate = updateForeignBox.getValue();
            }
        }

        // Configurações numéricas
        meta.size = text1.isDisabled() ? 0 : Integer.parseInt(text1.getText());
        meta.integerDigits = text2.isDisabled() ? 0 : Integer.parseInt(text2.getText());
        meta.decimalDigits = text3.isDisabled() ? 0 : Integer.parseInt(text3.getText());

        // Índice
        if (IndexOption.isSelected()) {
            meta.index = IndexText.getText().trim();
            meta.indexType = indexBox.getValue();
        }

        // Lista de valores (ENUM/SET)
        if (!SetName.isDisabled() && !setList.isEmpty()) {
            meta.items = new ArrayList<>(setList);
        }

        // Check constraint
        if (CheckOption.isSelected() && !CheckField.isDisabled()) {
            meta.check = CheckField.getText().trim();
        }

        // Comentário
        meta.comment = comment;

        return meta;
    }

    private void saveColumn(ColumnMetadata meta) {
        try {
            if (Edit) {
                handleEditColumn(meta);
            } else {
                handleNewColumn(meta);
            }
            window.close();
        } catch (Exception e) {
            ShowError("Save Error", "Failed to save column: " + e.getMessage());
        }
    }

    private void handleEditColumn(ColumnMetadata meta) {
        if (ref != null && originalMetadata != null) {
            ref.alterColumnMetadata(originalMetadata, meta);
        } else if (newTable != null) {
            newTable.EditColumnCallBack(meta);
        } else {
            ShowError("Save Error", "No valid reference for editing column");
        }
    }

    private void handleNewColumn(ColumnMetadata meta) {
        if (newTable != null) {
            newTable.PutColumnCallback(meta);
        } else if (ref != null) {
            ref.createDBCol(meta.Name, meta, FillOption.isSelected());
        } else {
            ShowError("Save Error", "No valid reference for creating column");
        }
    }

    public void insertMetadata(ColumnMetadata metadata) {
        Edit = true;
        this.originalMetadata = metadata;

        // Preencher campos básicos
        ColumnNameInput.setText(metadata.Name);
        typeBox.setValue(metadata.Type);
        primaryKeyOption.setSelected(metadata.IsPrimaryKey);
        NotNullOption.setSelected(metadata.NOT_NULL);
        DefaultOption.setSelected(metadata.defaultValue != null && !metadata.defaultValue.isEmpty());
        DefaultValueText.setText(metadata.defaultValue != null ? metadata.defaultValue : "");
        UniqueOption.setSelected(metadata.isUnique);

        // Preencher chave estrangeira
        if (metadata.foreign != null && metadata.foreign.isForeign) {
            ForeignKeyOption.setSelected(true);
            ForeignKeyBox.setValue(metadata.foreign.tableRef + ": " + metadata.foreign.columnRef);
        }

        // Atualizar estados
        checkType();
        AlterForeignBox();

        // Preencher valores numéricos
        text1.setText(String.valueOf(metadata.size));
        text2.setText(String.valueOf(metadata.integerDigits));
        text3.setText(String.valueOf(metadata.decimalDigits));

        // Preencher lista de valores
        if (metadata.items != null) {
            setList.setAll(metadata.items);
        }

        // Comentário
        comment = metadata.comment != null ? metadata.comment : "";
    }

    @FXML
    private void closeWindow() {
        window.close();
    }
}
