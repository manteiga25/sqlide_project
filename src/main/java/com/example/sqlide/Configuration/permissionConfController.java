package com.example.sqlide.Configuration;

import com.example.sqlide.drivers.model.Interfaces.PragmasInterface.PermissionPragmaInterface;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.example.sqlide.popupWindow.handleWindow.ShowConfirmation;
import static com.example.sqlide.popupWindow.handleWindow.ShowError;
import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Painel de contas e privilégios.
 *
 * <p>Correções face à versão anterior:</p>
 * <ul>
 *   <li>{@code addUser} chamava {@code database.addUser(...)} duas vezes seguidas, por isso
 *       a segunda falhava sempre com "user already exists" mesmo quando a criação corria bem.</li>
 *   <li>{@code loadPermissions} fazia {@code CurrentUser.toString()} sem verificar se
 *       {@code CurrentUser} tinha sido encontrado, rebentando com NPE em qualquer ligação
 *       cujo nome de utilizador não aparecesse na lista.</li>
 *   <li>Os estados vinham dos grants globais mas o "guardar" escrevia no âmbito de uma
 *       tabela — a caixa dizia uma coisa e o GRANT fazia outra. Agora há um seletor de
 *       âmbito e a leitura e a escrita usam o mesmo.</li>
 *   <li>A lista de privilégios estava fixa no FXML com os nomes do MySQL; agora vem do
 *       motor, o que permite ao PostgreSQL mostrar TRUNCATE e TRIGGER em vez de DROP.</li>
 *   <li>O campo da palavra-passe mostrava o resumo guardado e não fazia nada; passou a ser
 *       um campo de escrita com uma ação própria.</li>
 *   <li>A permissão de edição estava invertida: quem podia administrar via os controlos
 *       desativados.</li>
 * </ul>
 */
public class permissionConfController implements LoadSettings {

    /** Entrada do seletor de âmbito que representa a base de dados inteira. */
    private static final String WHOLE_DATABASE = "Whole database";

    @FXML
    private PasswordField passField;
    @FXML
    private TextField nameField, hostField;
    @FXML
    private Label hostLabel, noticeLabel, statusLabel;
    @FXML
    private ListView<String> usersListView;
    @FXML
    private FlowPane privilegesBox;
    @FXML
    private Button saveButton, changePasswordButton;
    @FXML
    private ChoiceBox<String> TableBox;
    @FXML
    private ToolBar tool;
    @FXML
    private TitledPane globalPrivilegesPane, userInformationPane;

    private PermissionPragmaInterface database;
    private List<String> tableMetadataList = List.of();
    private String userName, databaseName;

    private final ObservableList<String> userList = FXCollections.observableArrayList();
    private final Map<String, userInformation> information = new LinkedHashMap<>();

    /** Uma caixa por privilégio suportado pelo motor. */
    private final Map<String, CheckBox> privilegeChecks = new LinkedHashMap<>();

    /** Estado lido da base de dados, para só emitir GRANT/REVOKE do que mudou. */
    private final Map<String, Boolean> currentPermissions = new LinkedHashMap<>();

    private boolean canManage = false;

    @FXML
    public void initialize() {
        usersListView.setItems(userList);
        usersListView.setPlaceholder(new Label("No accounts"));
        usersListView.getSelectionModel().selectedItemProperty().addListener(
                (_, _, user) -> loadPermissions(user));

        TableBox.getSelectionModel().selectedItemProperty().addListener(
                (_, _, _) -> reloadPermissions());

        setEditable(false);
    }

    public void setDatabase(PermissionPragmaInterface database, String username,
                            String databaseName, List<String> metadataList) {
        this.database = database;
        this.userName = username;
        this.databaseName = databaseName;
        this.tableMetadataList = metadataList == null ? List.of() : metadataList;

        buildPrivilegeChecks();
        buildScopeChoices();
        loadUsers();
    }

    /** Constrói as caixas a partir do que o motor declara suportar. */
    private void buildPrivilegeChecks() {
        privilegesBox.getChildren().clear();
        privilegeChecks.clear();
        for (String privilege : database.supportedPrivileges()) {
            CheckBox check = new CheckBox(privilege);
            check.setTextFill(javafx.scene.paint.Color.web("#E0E0E0"));
            check.setOnAction(_ -> refreshDirtyState());
            privilegeChecks.put(privilege, check);
            privilegesBox.getChildren().add(check);
        }
    }

    private void buildScopeChoices() {
        List<String> scopes = new ArrayList<>();
        scopes.add(WHOLE_DATABASE);
        scopes.addAll(tableMetadataList);
        TableBox.setItems(FXCollections.observableArrayList(scopes));
        TableBox.getSelectionModel().selectFirst();
    }

    /** Nome da tabela do âmbito atual, ou null quando é a base de dados inteira. */
    private String selectedTable() {
        String scope = TableBox.getValue();
        return (scope == null || scope.equals(WHOLE_DATABASE)) ? null : scope;
    }

    private void loadUsers() {
        userList.clear();
        information.clear();

        Thread.ofVirtual().start(() -> {
            try {
                boolean manage = database.canManageUsers();
                Map<String, userInformation> users = database.getUsers();

                Platform.runLater(() -> {
                    canManage = manage;
                    information.putAll(users);
                    userList.setAll(users.keySet());

                    // Quem não pode administrar continua a poder inspecionar; só não grava.
                    tool.setDisable(!manage);
                    noticeLabel.setText(manage ? ""
                            : "Connected as " + userName + ", which cannot administer accounts. "
                            + "Privileges are shown read-only.");

                    if (!userList.isEmpty()) usersListView.getSelectionModel().selectFirst();
                });
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    usersListView.setPlaceholder(new Label("Could not read the account list."));
                    ShowError("SQL Error", "Error reading the accounts.", e.getMessage());
                });
            }
        });
    }

    private void loadPermissions(String user) {
        if (user == null) {
            setEditable(false);
            return;
        }

        userInformation selected = information.get(user);
        if (selected == null) {
            setEditable(false);
            return;
        }

        nameField.setText(selected.name);
        hostField.setText(selected.localhost);
        passField.clear();

        // O PostgreSQL não prende contas a uma origem; esconder o campo evita a confusão.
        boolean hasHost = selected.localhost != null && !selected.localhost.isBlank();
        hostLabel.setVisible(hasHost);
        hostLabel.setManaged(hasHost);
        hostField.setVisible(hasHost);
        hostField.setManaged(hasHost);

        final String table = selectedTable();
        statusLabel.setText("Reading privileges...");

        Thread.ofVirtual().start(() -> {
            try {
                Map<String, Boolean> permissions = database.getPermissions(user, databaseName, table);
                Platform.runLater(() -> {
                    currentPermissions.clear();
                    currentPermissions.putAll(permissions);
                    for (Map.Entry<String, CheckBox> entry : privilegeChecks.entrySet()) {
                        entry.getValue().setSelected(permissions.getOrDefault(entry.getKey(), false));
                    }
                    setEditable(canManage);
                    statusLabel.setText(describeScope(table));
                });
            } catch (SQLException e) {
                Platform.runLater(() -> {
                    setEditable(false);
                    statusLabel.setText("Could not read privileges.");
                    ShowError("SQL Error", "Error reading the privileges of " + user, e.getMessage());
                });
            }
        });
    }

    private String describeScope(String table) {
        return table == null
                ? "Scope: every table in " + databaseName
                : "Scope: " + databaseName + "." + table;
    }

    /** Assinala se há alterações por gravar, para o botão não parecer sempre disponível. */
    private void refreshDirtyState() {
        if (!canManage) return;
        boolean dirty = privilegeChecks.entrySet().stream().anyMatch(entry ->
                entry.getValue().isSelected() != currentPermissions.getOrDefault(entry.getKey(), false));
        saveButton.setDisable(!dirty);
        statusLabel.setText(dirty ? "Unsaved changes" : describeScope(selectedTable()));
    }

    private void setEditable(boolean editable) {
        globalPrivilegesPane.setDisable(false);
        privilegesBox.setDisable(!editable);
        saveButton.setDisable(true);
        passField.setDisable(!editable);
        changePasswordButton.setDisable(!editable);
        userInformationPane.setDisable(false);
    }

    @FXML
    private void reloadPermissions() {
        loadPermissions(usersListView.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void savePermissions() {
        final String selectedUser = usersListView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) return;

        final String table = selectedTable();

        // Só o que mudou vira comando; emitir GRANT do que já existe é ruído e, em alguns
        // motores, erro.
        final Map<String, Boolean> toApply = new LinkedHashMap<>();
        for (Map.Entry<String, CheckBox> entry : privilegeChecks.entrySet()) {
            boolean wanted = entry.getValue().isSelected();
            boolean current = currentPermissions.getOrDefault(entry.getKey(), false);
            if (wanted != current) toApply.put(entry.getKey(), wanted);
        }

        if (toApply.isEmpty()) {
            statusLabel.setText("Nothing changed.");
            return;
        }

        if (!ShowConfirmation("Apply privileges",
                toApply.size() + " privilege change(s) on " + selectedUser
                        + " for " + describeScope(table).toLowerCase() + ". Continue?")) {
            return;
        }

        saveButton.setDisable(true);
        statusLabel.setText("Applying...");

        Thread.ofVirtual().start(() -> {
            List<String> failures = new ArrayList<>();
            for (Map.Entry<String, Boolean> change : toApply.entrySet()) {
                try {
                    if (change.getValue()) {
                        database.grant(change.getKey(), databaseName, table, selectedUser);
                    } else {
                        database.revoke(change.getKey(), databaseName, table, selectedUser);
                    }
                } catch (Exception e) {
                    failures.add(change.getKey() + ": " + e.getMessage());
                }
            }

            Platform.runLater(() -> {
                if (failures.isEmpty()) {
                    statusLabel.setText(toApply.size() + " privilege(s) updated.");
                } else {
                    ShowError("Some privileges failed",
                            failures.size() + " of " + toApply.size() + " changes were rejected.",
                            String.join("\n", failures));
                }
                // Relê para o ecrã passar a mostrar o que ficou mesmo gravado.
                reloadPermissions();
            });
        });
    }

    @FXML
    private void changePassword() {
        final String selectedUser = usersListView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) return;

        final userInformation user = information.get(selectedUser);
        final String password = passField.getText();

        if (password == null || password.isBlank()) {
            passField.requestFocus();
            ShowInformation("No password", "Type the new password first.");
            return;
        }

        if (!ShowConfirmation("Change password", "Change the password of " + selectedUser + "?")) return;

        Thread.ofVirtual().start(() -> {
            try {
                userInformation updated = new userInformation(
                        user.name, user.localhost, password, user.plugin, user.maxUser);
                database.changePassword(updated);
                Platform.runLater(() -> {
                    passField.clear();
                    statusLabel.setText("Password changed.");
                });
            } catch (SQLException e) {
                Platform.runLater(() -> ShowError("SQL Error",
                        "Could not change the password.", e.getMessage()));
            }
        });
    }

    @FXML
    private void delete() {
        final String selectedUser = usersListView.getSelectionModel().getSelectedItem();
        if (selectedUser == null) return;

        if (selectedUser.contains("'" + userName + "'")) {
            ShowInformation("Not allowed", "You cannot delete the account you are connected with.");
            return;
        }

        if (!ShowConfirmation("Delete account", "Permanently delete " + selectedUser + "?")) return;

        try {
            database.dropUser(selectedUser);
            information.remove(selectedUser);
            userList.remove(selectedUser);
        } catch (SQLException e) {
            ShowError("SQL Error", "Error deleting the account.", e.getMessage());
        }
    }

    @FXML
    private void addUser() {
        try {
            Stage stage = new Stage();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("user.fxml"));
            Parent root = loader.load();

            userEditorController editor = loader.getController();
            editor.setDialogStage(stage);
            editor.setAuthenticationMethods(database.authenticationMethods());
            // O PostgreSQL não usa host na criação da conta.
            editor.setHostRequired(!database.authenticationMethods().equals(List.of("password")));

            stage.setTitle("New account");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            stage.showAndWait();

            final userInformation created = editor.getUser();
            if (created == null) return;

            // Uma única chamada: a versão anterior criava a conta e voltava a tentar criá-la.
            database.addUser(created);

            information.put(created.toString(), created);
            userList.add(created.toString());
            usersListView.getSelectionModel().select(created.toString());
        } catch (SQLException e) {
            ShowError("SQL Error", "Could not create the account.", e.getMessage());
        } catch (Exception e) {
            ShowError("Error", "Could not open the account editor.", e.getMessage());
        }
    }

    @Override
    public void initialize_configuration() {
        if (database != null) loadUsers();
    }

    @Override
    public void save_settings() {
        savePermissions();
    }

    /**
     * Uma conta do motor.
     *
     * <p>{@code maxUser} passou a significar "é a conta com que estamos ligados". Antes o
     * nome sugeria "utilizador com privilégios máximos" mas era calculado por uma condição
     * que nunca dava verdadeiro, e usado com o sentido invertido.</p>
     */
    public static class userInformation {
        public String name, localhost, password, plugin;
        public boolean maxUser;

        public userInformation(String name, String localhost, String password, String plugin, boolean maxUser) {
            this.localhost = localhost;
            this.password = password;
            this.name = name;
            this.plugin = plugin;
            this.maxUser = maxUser;
        }

        @Override
        public String toString() {
            return localhost == null || localhost.isBlank()
                    ? "'" + name + "'"
                    : "'" + name + "'@'" + localhost + "'";
        }

    }

}
