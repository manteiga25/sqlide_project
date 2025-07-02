package com.example.sqlide;

import com.example.sqlide.AdvancedSearch.AdvancedSearchController;
import com.example.sqlide.Configuration.DatabaseConf;
import com.example.sqlide.DatabaseInterface.TableInterface.TableInterface;
import com.example.sqlide.drivers.SQLite.SQLiteTypes;
import com.example.sqlide.drivers.model.SQLTypes;
import com.example.sqlide.drivers.model.TypesModelList;
import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextArea;
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
import javafx.scene.layout.Pane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

public class NewColumn {

    @FXML
    private JFXButton CommentButton;
    @FXML
    private JFXTextField IndexText, CheckField;

    private Stage window;

    private TableInterface ref;

    private String TableName, DBName;

    private HashMap<String, ArrayList<String>> KeysForForeign;
    private ColumnMetadata originalMetadata;

    private SQLiteTypes types;

    private String[] charList;

    private TypesModelList list;

    private boolean Edit = false;

    private NewTable newTable;

    @FXML
    private TextField ColumnNameInput, text1, text2, text3;

    @FXML
    private Label LabelDB;

    @FXML
    private ChoiceBox<String> typeBox, ForeignKeyBox, indexBox, updateForeignBox, deleteForeignBox;

    @FXML
    private CheckBox primaryKeyOption, NotNullOption, ForeignKeyOption, DefaultOption, UniqueOption, FillOption, IndexOption, AutoincrementOption, CheckOption;

    @FXML
    private TextField DefaultValueText, SetName;

    @FXML
    private TextField WordBox;

    @FXML
    private Button AddButton, EditList;

    private final ObservableList<String> setList = FXCollections.observableArrayList();

    private ArrayList<String> foreignModes;

    private String comment = "";

    @FXML
    private void AlterForeignBox() {
        if (ForeignKeyOption.isSelected()) {
            ForeignKeyBox.setDisable(false);
            FillOption.setDisable(false);
            deleteForeignBox.setDisable(false);
            updateForeignBox.setDisable(false);
            primaryKeyOption.setDisable(true);
            NotNullOption.setDisable(true);
            DefaultOption.setDisable(true);
            typeBox.setDisable(true);
            UniqueOption.setDisable(true);
            switchDecimal(true);
            switchSize(true);
            AddButton.setDisable(true);
            WordBox.setDisable(true);
        }
        else {
            ForeignKeyBox.setDisable(true);
            FillOption.setDisable(true);
            deleteForeignBox.setDisable(true);
            updateForeignBox.setDisable(true);
            primaryKeyOption.setDisable(false);
            NotNullOption.setDisable(false);
            DefaultOption.setDisable(false);
            typeBox.setDisable(false);
            UniqueOption.setDisable(false);
            text1.setDisable(false);
            text2.setDisable(false);
            text3.setDisable(false);
            switchSize(false);
            switchDecimal(false);
            AddButton.setDisable(false);
            WordBox.setDisable(false);
        }
    }

    @FXML
    private void AlterIndexState() {
        final boolean state = !IndexOption.isSelected();
        IndexText.setDisable(state);
        if (!indexBox.getItems().isEmpty()) {
            indexBox.setDisable(state);
        }
    }

    @FXML
    private void AlterCheckState() {
        final boolean state = !CheckOption.isSelected();
        CheckField.setDisable(state);
    }

    @FXML
    private void AlterDefaultText() {
        DefaultValueText.setDisable(!DefaultOption.isSelected());
    }

    @FXML
    public void initialize() {
        typeBox.setValue("INTEGER");
        typeBox.setOnAction(_->checkType());
        ForeignKeyBox.setDisable(true);
        DefaultValueText.setDisable(true);
    }

    public void NewColumnWin(final String DBName, final String TableName, final TableInterface ref, final Stage subStage, final HashMap<String, ArrayList<String>> KeysForForeign, final SQLiteTypes types, final String[] list, final String[] charList, final String[] modes, final ArrayList<String> foreignModes, SQLTypes sql) {
        LabelDB.setText("Database " + DBName + "\nTable " + TableName);
        this.TableName = TableName;
        this.DBName = DBName;
        this.ref = ref;
        this.KeysForForeign = KeysForForeign;
        this.types = types;
        this.charList = charList;
        typeBox.getItems().addAll(list);
        window = subStage;
        initBox();
        System.out.println("c");
        updateForeignBox.getItems().addAll(foreignModes);
        deleteForeignBox.getItems().addAll(foreignModes);
        if (modes != null) {
            indexBox.getItems().addAll(modes);
        }
        if (sql == SQLTypes.SQLITE) {
            HBox c = (HBox) CommentButton.getParent();
            c.getChildren().remove(CommentButton);
        }

    }

    public void NewColumnWin(final String DBName, final String TableName, final NewTable ref, final Stage subStage, final HashMap<String, ArrayList<String>> KeysForForeign, final SQLiteTypes types, final String[] list, final String[] charList, final String[] modes, SQLTypes sql) {
        LabelDB.setText("Database " + DBName + "\nTable " + TableName);
        this.TableName = TableName;
        this.DBName = DBName;
        //this.ref = ref;
        this.newTable = ref;
        this.KeysForForeign = KeysForForeign;
        this.types = types;
        this.charList = charList;
        typeBox.getItems().addAll(list);
        window = subStage;
        initBox();
        if (modes != null) {
            indexBox.getItems().addAll(modes);
        }
        if (sql == SQLTypes.SQLITE) {
            HBox c = (HBox) CommentButton.getParent();
            c.getChildren().remove(CommentButton);
        }

    }

    @FXML
    private void loadComment() {

        final TextArea text = new TextArea(comment);
        text.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/TextAreaStyle.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());
        text.setWrapText(true);
        text.setStyle("-fx-background-color: #2C2C2C;");

        AnchorPane container = new AnchorPane(text);

        AnchorPane.setTopAnchor(text, 0.0);
        AnchorPane.setRightAnchor(text, 0.0);
        AnchorPane.setBottomAnchor(text, 0.0);
        AnchorPane.setLeftAnchor(text, 0.0);

        Stage pageViewer = new Stage();
        pageViewer.setTitle("Page");
        pageViewer.initModality(Modality.APPLICATION_MODAL);

// Vincular tamanho do container ao tamanho da cena
        container.prefWidthProperty().bind(pageViewer.widthProperty());
        container.prefHeightProperty().bind(pageViewer.heightProperty());

        pageViewer.setOnCloseRequest(_->{
            comment = text.getText();
        });

        pageViewer.setScene(new Scene(container, 400, 600));
        pageViewer.show();
    }

    private void initBox() {
        for (final String key : KeysForForeign.keySet()) {
            final ArrayList<String> ListIDs = KeysForForeign.get(key);
            String[] IDs = new String[ListIDs.size()];
            int index = 0;
            for (final String ID : ListIDs) {
                IDs[index++] = key + ": " + ID;
                System.out.println(IDs[0]);
            }
            ForeignKeyBox.getItems().addAll(IDs);
        }
    }

    @FXML
    private void checkType() {
        final String type = typeBox.getValue();

        boolean sizeMode = true;
        boolean ListMode = type.equals("ENUM") || type.equals("SET");
        final boolean decimalMode = !type.equals("DECIMAL");

        for (final String t : charList) {
            if (type.equals(t)) {
                    sizeMode = false;
                    break;
            }
        }
        switchDecimal(decimalMode);
        switchSize(sizeMode);
        switchList(!ListMode);
    }

    private void switchSize(final boolean mode) {
        text1.setDisable(mode);
    }

    private void switchDecimal(final boolean mode) {
        text2.setDisable(mode);
        text3.setDisable(mode);
    }

    private void switchList(final boolean mode) {
        EditList.setDisable(mode);
        if (!mode) {
            setList.clear();
        }
        SetName.setDisable(mode);
        AddButton.setDisable(mode);
        WordBox.setDisable(mode);
    }

    @FXML
    private void addWord() {
        final String word = WordBox.getText();
        if (word == null || word.isEmpty() || setList.contains(word)) {
            WordBox.requestFocus();
            WordBox.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            ShowError("Invalid Value", "please insert target for List.");
            return;
        }
        setList.add(word);
        WordBox.setText("");
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
        final String colName = ColumnNameInput.getText();
        if (colName.isEmpty()) {
            ColumnNameInput.requestFocus();
            ColumnNameInput.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
            ShowError("Invalid Value", "please insert target for Foreign Key.");
            return;
        }
        final boolean IsPrimeKey = primaryKeyOption.isSelected() && !primaryKeyOption.isDisabled();
        final boolean IsNotNull = NotNullOption.isSelected() && !NotNullOption.isDisabled();
        final boolean IsUnique = UniqueOption.isSelected() && !UniqueOption.isDisabled();
        final ColumnMetadata.Foreign foreign = new ColumnMetadata.Foreign();
        String ForeignKey = "";
        if (ForeignKeyOption.isSelected() && !ForeignKeyOption.isDisabled()) {
            ForeignKey = ForeignKeyBox.getValue();
            if (ForeignKey.isEmpty()) {
                ForeignKeyBox.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
                ShowError("Invalid Value", "please insert target for Foreign Key.");
                return;
            }
            foreign.isForeign = true;
            foreign.tableRef = ForeignKey.substring(0, ForeignKey.indexOf(":"));
            foreign.columnRef = ForeignKey.substring(ForeignKey.indexOf(" ")+1);
            foreign.onEliminate = deleteForeignBox.getValue();
            foreign.onUpdate = updateForeignBox.getValue();
        }
        System.out.println("Gor" + ForeignKey);

        String type = typeBox.getValue();

        System.out.println("type " + type);

        int len = 0;
        if (!text1.isDisabled()) {
            final String vallen = text1.getText();
            try {
                final int tmp = Integer.parseInt(vallen);
                if (tmp <= 0) {
                    throw new Exception("");
                }
                len = tmp;
            } catch (Exception e) {
                text1.requestFocus();
                ShowError("Invalid value", "You need to insert a integer positive value.");
                return;
            }
        }

        int decimal1 = 0;
        if (!text2.isDisabled()) {
            final String vallen = text2.getText();
            try {
                final int tmp = Integer.parseInt(vallen);
                if (tmp <= 0) {
                    throw new Exception("");
                }
                decimal1 = tmp;
            } catch (Exception e) {
                text2.requestFocus();
                ShowError("Invalid value", "You need to insert a integer positive value.");
                return;
            }
        }

        int decimal2 = 0;
        if (!text3.isDisabled()) {
            final String vallen = text3.getText();
            try {
                final int tmp = Integer.parseInt(vallen);
                if (tmp <= 0) {
                    throw new Exception("");
                }
                decimal2 = tmp;
            } catch (Exception e) {
                text3.requestFocus();
                ShowError("Invalid value", "You need to insert a integer positive value.");
                return;
            }
        }

        String indexName = null, indexType = null;
        if (IndexOption.isSelected()) {
            indexName = IndexText.getText();
            indexType = indexBox.getValue();
        }

        String DefaultValue = "";
        if (DefaultOption.isSelected() && !DefaultOption.isDisabled()) {
            DefaultValue = DefaultValueText.getText();
            if (DefaultValue.isEmpty()) {
                DefaultValueText.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
                ShowError("Invalid Value", "please insert default value.");
                return;
            }
            else if (!types.checkValue(type, DefaultValue, len, IsNotNull)) {
                DefaultValueText.setStyle("-fx-border-color: red; -fx-border-width: 2px; border-radius: 25px;");
                ShowError("Value error", "Value " + DefaultValue + " is invalid for " + type + " type.\n" + types.getException());
                return;
            }
        }

            String[] set = {};
        if (!SetName.isDisabled()) {
            if (!SetName.getText().isEmpty()) {
                if (!setList.isEmpty()) {
                    set = setList.toArray(new String[0]);
                   // type = SetName.getText(); for postgre
                } else {
                    WordBox.requestFocus();
                    ShowError("Value error", "You need to insert a values.");
                    return;
                }
            } else {
                SetName.requestFocus();
                ShowError("Value error", "You need to insert a name.");
                return;
            }
        }

        String check = "";
        if (!CheckField.isDisabled()) {
            check = CheckField.getText();
            if (check.isEmpty()) {
                    CheckField.requestFocus();
                    ShowError("Value error", "You need to insert a values.");
                    return;
                }
        }

        System.out.println("is " + IsPrimeKey);

        final ColumnMetadata meta = new ColumnMetadata(IsNotNull, IsPrimeKey, foreign, DefaultValue, len, type, colName, IsUnique, decimal1, decimal2, indexName);
        meta.indexType = indexType;
        meta.items = new ArrayList<>(List.of(set));
        meta.check = check;
        meta.comment = comment;
     //   meta.autoincrement = autoInit;

       // ref.createDBCol(colName, typeBox.getValue(),"0", IsPrimeKey);
        if (Edit) {
          //  ref.AlterFBCol(meta);
           // newTable.EditColumnCallBack(meta);
            if (this.originalMetadata != null && ref instanceof TableInterface) {
                ((TableInterface)ref).alterColumnMetadata(this.originalMetadata, meta);
            } else {
                // Fallback or error if originalMetadata is null or ref is not TableInterface
                // This might happen if NewColumn is used in a context not anticipated for editing existing columns.
                ShowError("Callback Error", "Could not alter column. Original metadata or table reference not found.");
            }
        } else {
            if (newTable != null) {
                newTable.PutColumnCallback(meta);
            } else ref.createDBCol(colName, meta, FillOption.isSelected());
        }
    }

    @FXML
    private void closeWindow() {
        window.close();
    }

    public void insertMetadata(final ColumnMetadata metadata) {
        Edit = true;
        this.originalMetadata = metadata;
        ColumnNameInput.setText(metadata.Name);
        System.out.println(1);
        typeBox.setValue(metadata.Type);
        System.out.println(1);
        primaryKeyOption.setSelected(metadata.IsPrimaryKey);
        System.out.println(1);
        NotNullOption.setSelected(metadata.NOT_NULL);
        System.out.println(1);
        ForeignKeyOption.setSelected(metadata.foreign.isForeign);
        System.out.println(1);
        DefaultOption.setSelected(metadata.defaultValue != null);
        System.out.println(1);
        UniqueOption.setSelected(metadata.isUnique);
        System.out.println(1);
        DefaultValueText.setText(metadata.defaultValue);
        System.out.println(1);
        AlterForeignBox();
        System.out.println(1);

        ForeignKeyBox.setValue(metadata.foreign.tableRef + ": " + metadata.foreign.columnRef);
        System.out.println(1);
        checkType();System.out.println(1);

        comment = metadata.comment;

        text1.setText(String.valueOf(metadata.size));
        System.out.println(1);
        text2.setText(String.valueOf(metadata.integerDigits));
        System.out.println(1);
        text3.setText(String.valueOf(metadata.decimalDigits));
        System.out.println(1);
    }
}
