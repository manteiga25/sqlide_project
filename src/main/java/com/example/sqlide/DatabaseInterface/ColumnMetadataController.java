package com.example.sqlide.DatabaseInterface;

import com.example.sqlide.Metadata.CheckMetadata;
import com.example.sqlide.Metadata.IndexMetadata;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.SQLTypes;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

import static com.example.sqlide.popupWindow.handleWindow.ShowConfirmation;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;

/**
 * Índices e restrições de uma tabela já criada.
 *
 * <p>Esta classe estava vazia e o {@code ColumnMetadata.fxml} não era carregado por
 * ninguém: não havia forma de acrescentar um índice ou um CHECK depois de a tabela
 * existir. O que havia — {@code createIndex}/{@code removeIndex} — estava implementado
 * só no SQLite; no MySQL e no PostgreSQL os métodos tinham corpo vazio.</p>
 */
public class ColumnMetadataController {

    @FXML
    private Label TitleLabel, EngineLabel, IndexStatusLabel, CheckStatusLabel, CheckWarningLabel;
    @FXML
    private ListView<IndexMetadata> IndexList;
    @FXML
    private ListView<String> IndexColumnsList;
    @FXML
    private ListView<CheckMetadata> CheckList;
    @FXML
    private TextField IndexNameField, CheckNameField, CheckExpressionField;
    @FXML
    private ChoiceBox<String> IndexModeBox;
    @FXML
    private Button DropIndexButton, DropCheckButton;

    private DataBase database;
    private String table;
    private Stage dialogStage;

    @FXML
    private void initialize() {
        IndexColumnsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        IndexList.setPlaceholder(new Label("No indexes on this table"));
        CheckList.setPlaceholder(new Label("No check constraints on this table"));

        IndexList.getSelectionModel().selectedItemProperty().addListener((_, _, index) ->
                // Os índices implícitos pertencem à chave primária: apagá-los não é opção.
                DropIndexButton.setDisable(index == null || index.implicit));

        CheckList.getSelectionModel().selectedItemProperty().addListener((_, _, check) ->
                DropCheckButton.setDisable(check == null));

        // Como o SQLite não tem nomes de restrição, o campo do nome não se aplica lá.
        IndexList.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(IndexMetadata item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : item + (item.implicit ? "   (created by the engine)" : ""));
            }
        });

        CheckList.setCellFactory(_ -> new ListCell<>() {
            @Override
            protected void updateItem(CheckMetadata item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public void setTable(final DataBase database, final String table, final List<String> columns) {
        this.database = database;
        this.table = table;

        TitleLabel.setText(table);
        EngineLabel.setText(database.getSQLType() + "  ·  " + database.getDatabaseName());

        IndexColumnsList.setItems(FXCollections.observableArrayList(columns));
        IndexModeBox.setItems(FXCollections.observableArrayList(database.getDatabaseInfo().getIndexModes()));
        IndexModeBox.getSelectionModel().selectFirst();

        final boolean rebuilds = database.getSQLType() == SQLTypes.SQLITE;
        CheckNameField.setDisable(rebuilds);
        CheckWarningLabel.setText(rebuilds
                ? "SQLite has no ALTER TABLE ADD CONSTRAINT: adding or removing a check rebuilds the "
                + "table (rows and indexes are copied over, inside a transaction). Constraints have no "
                + "names here, so they are identified by their expression."
                : "");

        reloadIndexes();
        reloadChecks();
    }

    // ==== Índices ====

    @FXML
    private void reloadIndexes() {
        run(() -> {
            final ArrayList<IndexMetadata> indexes = database.getIndexes(table);
            Platform.runLater(() -> {
                IndexList.setItems(FXCollections.observableArrayList(indexes));
                IndexStatusLabel.setText(indexes.size() + " index(es)");
            });
        }, "Could not read the indexes");
    }

    @FXML
    private void createIndex() {
        final List<String> columns = new ArrayList<>(IndexColumnsList.getSelectionModel().getSelectedItems());
        if (columns.isEmpty()) {
            IndexStatusLabel.setText("Select at least one column.");
            return;
        }

        final String name = IndexNameField.getText() == null ? "" : IndexNameField.getText().trim();
        final String mode = IndexModeBox.getValue() == null ? "" : IndexModeBox.getValue();

        run(() -> {
            database.createIndex(table, new ArrayList<>(columns), name, mode);
            Platform.runLater(() -> {
                IndexNameField.clear();
                IndexColumnsList.getSelectionModel().clearSelection();
                IndexStatusLabel.setText("Index created.");
                reloadIndexes();
            });
        }, "Could not create the index");
    }

    @FXML
    private void dropIndex() {
        final IndexMetadata selected = IndexList.getSelectionModel().getSelectedItem();
        if (selected == null || selected.implicit) return;

        if (!ShowConfirmation("Drop index", "Drop index " + selected.Name + " from " + table + "?")) return;

        run(() -> {
            database.removeIndex(selected.Name);
            Platform.runLater(() -> {
                IndexStatusLabel.setText("Index dropped.");
                reloadIndexes();
            });
        }, "Could not drop the index");
    }

    // ==== Restrições CHECK ====

    @FXML
    private void reloadChecks() {
        run(() -> {
            final ArrayList<CheckMetadata> checks = database.getChecks(table);
            Platform.runLater(() -> {
                CheckList.setItems(FXCollections.observableArrayList(checks));
                CheckStatusLabel.setText(checks.size() + " check(s)");
            });
        }, "Could not read the check constraints");
    }

    @FXML
    private void createCheck() {
        final String expression = CheckExpressionField.getText() == null
                ? "" : CheckExpressionField.getText().trim();
        if (expression.isEmpty()) {
            CheckExpressionField.requestFocus();
            CheckStatusLabel.setText("Write the expression the rows must satisfy.");
            return;
        }

        final String name = CheckNameField.getText() == null || CheckNameField.getText().isBlank()
                ? "chk_" + table + "_" + Math.abs(expression.hashCode() % 10_000)
                : CheckNameField.getText().trim();

        if (database.getSQLType() == SQLTypes.SQLITE
                && !ShowConfirmation("Rebuild table",
                "SQLite cannot add a constraint in place, so " + table + " will be rebuilt: "
                        + "a new table is created with the check, the rows are copied and the "
                        + "indexes recreated, all inside a transaction. Continue?")) {
            return;
        }

        run(() -> {
            database.addCheck(table, name, expression);
            Platform.runLater(() -> {
                CheckNameField.clear();
                CheckExpressionField.clear();
                CheckStatusLabel.setText("Check added.");
                reloadChecks();
            });
        }, "Could not add the check constraint");
    }

    @FXML
    private void dropCheck() {
        final CheckMetadata selected = CheckList.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        if (!ShowConfirmation("Drop check", "Drop this check from " + table + "?\n\n" + selected.expression)) return;

        // No SQLite a restrição não tem nome; é identificada pela própria expressão.
        final String identifier = database.getSQLType() == SQLTypes.SQLITE
                ? selected.expression : selected.Name;

        run(() -> {
            database.dropCheck(table, identifier);
            Platform.runLater(() -> {
                CheckStatusLabel.setText("Check dropped.");
                reloadChecks();
            });
        }, "Could not drop the check constraint");
    }

    // ==== Auxiliares ====

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /** Corre o trabalho de base de dados fora da thread da UI e mostra o erro se falhar. */
    private void run(final ThrowingRunnable work, final String failureTitle) {
        Thread.ofVirtual().start(() -> {
            try {
                work.run();
            } catch (Exception e) {
                Throwable root = e;
                while (root.getCause() != null) root = root.getCause();
                final String message = root.getMessage() == null ? root.toString() : root.getMessage();
                Platform.runLater(() -> ShowError(failureTitle, "The database rejected the request.", message));
            }
        });
    }

    @FXML
    private void close() {
        if (dialogStage != null) dialogStage.close();
    }

    /** Abre a janela para a tabela indicada. */
    public static void open(final Window owner, final DataBase database,
                            final String table, final List<String> columns) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    ColumnMetadataController.class.getResource("/com/example/sqlide/ColumnMetadata.fxml"));
            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle("Table tools — " + table);
            stage.setScene(scene);
            if (owner != null) {
                stage.initOwner(owner);
                stage.initModality(Modality.APPLICATION_MODAL);
            }

            ColumnMetadataController controller = loader.getController();
            controller.setDialogStage(stage);
            controller.setTable(database, table, columns);

            stage.showAndWait();
        } catch (Exception e) {
            ShowError("Error", "Could not open the table tools.", e.getMessage());
        }
    }

}
