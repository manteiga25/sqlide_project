package com.example.sqlide.Function;

import com.example.sqlide.Metadata.RoutineMetadata;
import com.example.sqlide.misc.ClipBoard;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.example.sqlide.popupWindow.handleWindow.ShowError;

/**
 * Catálogo das funções, agregados, procedimentos e vistas do esquema aberto.
 *
 * <p>Mostra tanto as que vêm com o motor como as criadas por SQL, agrupadas por categoria,
 * com assinatura, tipo devolvido e descrição. Serve para consulta e também como selector:
 * o construtor de consultas abre-o para escolher uma função.</p>
 */
public class RoutineCatalogController {

    @FXML
    private TreeView<Object> routineTree;
    @FXML
    private TextField searchField;
    @FXML
    private CheckBox onlyUserCheck;
    @FXML
    private Label nameLabel, kindLabel, returnLabel, countLabel;
    @FXML
    private TextArea signatureArea, commentArea;
    @FXML
    private Button useButton, copyButton;

    private final List<RoutineMetadata> routines = new ArrayList<>();

    private Stage dialogStage;
    private RoutineMetadata chosen;

    /** Quando true a janela funciona como selector e o botão "Use" fecha-a. */
    private boolean pickerMode = false;

    @FXML
    private void initialize() {
        routineTree.setShowRoot(false);
        routineTree.getSelectionModel().selectedItemProperty().addListener(
                (_, _, item) -> showDetail(item));

        // O detalhe começa vazio até haver escolha.
        showDetail(null);

        searchField.textProperty().addListener((_, _, _) -> applyFilter());
        useButton.setVisible(false);
        useButton.setManaged(false);
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /** Carrega o catálogo com as rotinas lidas quando a base de dados abriu. */
    public void setRoutines(List<RoutineMetadata> routines) {
        this.routines.clear();
        if (routines != null) this.routines.addAll(routines);
        applyFilter();
    }

    /** Liga o modo de escolha, usado quando o catálogo é aberto a partir de outra janela. */
    public void setPickerMode(boolean pickerMode) {
        this.pickerMode = pickerMode;
        useButton.setVisible(pickerMode);
        useButton.setManaged(pickerMode);
    }

    /** A rotina escolhida, ou null se a janela foi fechada sem escolher. */
    public RoutineMetadata getChosen() {
        return chosen;
    }

    @FXML
    private void applyFilter() {
        final String needle = searchField.getText() == null
                ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        final boolean onlyUser = onlyUserCheck.isSelected();

        // Agrupa por categoria mantendo a ordem em que as categorias aparecem.
        Map<String, List<RoutineMetadata>> byCategory = new LinkedHashMap<>();
        int total = 0;

        for (RoutineMetadata routine : routines) {
            if (onlyUser && routine.builtIn) continue;
            if (!needle.isEmpty() && !routine.Name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            byCategory.computeIfAbsent(routine.category, _ -> new ArrayList<>()).add(routine);
            total++;
        }

        TreeItem<Object> root = new TreeItem<>("root");
        for (Map.Entry<String, List<RoutineMetadata>> group : byCategory.entrySet()) {
            TreeItem<Object> node = new TreeItem<>(group.getKey() + "  (" + group.getValue().size() + ")");
            // Só expande automaticamente quando a filtragem já reduziu bastante a lista.
            node.setExpanded(!needle.isEmpty() || byCategory.size() <= 4);
            for (RoutineMetadata routine : group.getValue()) node.getChildren().add(new TreeItem<>(routine));
            root.getChildren().add(node);
        }

        routineTree.setRoot(root);
        countLabel.setText(total + " of " + routines.size());
    }

    private void showDetail(TreeItem<Object> item) {
        Object value = item == null ? null : item.getValue();

        if (!(value instanceof RoutineMetadata routine)) {
            nameLabel.setText("");
            kindLabel.setText("");
            signatureArea.setText("");
            returnLabel.setText("");
            commentArea.setText("");
            copyButton.setDisable(true);
            useButton.setDisable(true);
            return;
        }

        nameLabel.setText(routine.Name);
        kindLabel.setText(routine.kind + (routine.builtIn ? " · built in" : " · defined in this database"));
        signatureArea.setText(routine.signature());
        returnLabel.setText(routine.returnType == null || routine.returnType.isBlank()
                ? "nothing" : routine.returnType);
        commentArea.setText(routine.comment == null || routine.comment.isBlank()
                ? "No description available." : routine.comment);
        copyButton.setDisable(false);
        useButton.setDisable(false);
    }

    private RoutineMetadata selectedRoutine() {
        TreeItem<Object> item = routineTree.getSelectionModel().getSelectedItem();
        return (item != null && item.getValue() instanceof RoutineMetadata routine) ? routine : null;
    }

    @FXML
    private void copyCall() {
        RoutineMetadata routine = selectedRoutine();
        if (routine != null) ClipBoard.CopyToBoard(routine.callTemplate());
    }

    @FXML
    private void useSelected() {
        chosen = selectedRoutine();
        if (chosen != null && dialogStage != null) dialogStage.close();
    }

    /**
     * Abre o catálogo.
     *
     * @param picker true para o abrir como selector; nesse caso devolve a rotina escolhida
     * @return a rotina escolhida, ou null
     */
    public static RoutineMetadata open(Window owner, List<RoutineMetadata> routines, boolean picker) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    RoutineCatalogController.class.getResource("RoutineCatalog.fxml"));
            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle(picker ? "Pick a function" : "Functions and views");
            stage.setScene(scene);
            if (owner != null) {
                stage.initOwner(owner);
                stage.initModality(Modality.APPLICATION_MODAL);
            }

            RoutineCatalogController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setPickerMode(picker);
            controller.setRoutines(routines);

            stage.showAndWait();
            return controller.getChosen();
        } catch (Exception e) {
            ShowError("Error", "Could not open the function catalog.", e.getMessage());
            return null;
        }
    }

}
