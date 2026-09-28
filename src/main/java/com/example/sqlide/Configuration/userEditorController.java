package com.example.sqlide.Configuration;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.List;

import static com.example.sqlide.popupWindow.handleWindow.ShowInformation;

/**
 * Caixa de criação de conta.
 *
 * <p>A lista de métodos de autenticação nunca era preenchida, por isso {@code plugBox}
 * devolvia sempre null e a validação recusava tudo — não era possível criar uma conta.
 * Agora os métodos vêm do motor, e o campo do host desaparece nos motores que não o usam.</p>
 */
public class userEditorController {

    @FXML
    private ChoiceBox<String> plugBox;
    @FXML
    private TextField usernameField, hostField;
    @FXML
    private Label hostLabel;
    @FXML
    private PasswordField passwordField;

    private Stage dialogStage;
    private permissionConfController.userInformation user;
    private boolean hostRequired = true;

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /** Métodos de autenticação aceites pelo motor ligado. */
    public void setAuthenticationMethods(List<String> methods) {
        plugBox.setItems(FXCollections.observableArrayList(methods));
        if (!methods.isEmpty()) plugBox.getSelectionModel().selectFirst();
    }

    /** O PostgreSQL não associa contas a uma máquina de origem. */
    public void setHostRequired(boolean hostRequired) {
        this.hostRequired = hostRequired;
        hostLabel.setVisible(hostRequired);
        hostLabel.setManaged(hostRequired);
        hostField.setVisible(hostRequired);
        hostField.setManaged(hostRequired);
    }

    public permissionConfController.userInformation getUser() {
        return user;
    }

    @FXML
    private void cancel() {
        user = null;
        dialogStage.close();
    }

    @FXML
    private void add() {
        final String plugin = plugBox.getValue();
        final String username = usernameField.getText();
        final String password = passwordField.getText();
        final String host = hostField.getText();

        if (username == null || username.isBlank()) {
            usernameField.requestFocus();
            ShowInformation("Missing name", "Type the account name.");
            return;
        }

        if (password == null || password.isBlank()) {
            passwordField.requestFocus();
            ShowInformation("Missing password", "Type a password for the account.");
            return;
        }

        // Uma aspa no nome partiria o comando de criação, que não aceita parâmetros.
        if (username.contains("'") || username.contains("`") || username.contains("\"")) {
            usernameField.requestFocus();
            ShowInformation("Invalid name", "The account name cannot contain quotes.");
            return;
        }

        // O host em branco vale como "qualquer origem", que é o que o MySQL entende por %.
        final String resolvedHost = hostRequired
                ? (host == null || host.isBlank() ? "%" : host.trim())
                : "";

        user = new permissionConfController.userInformation(
                username.trim(), resolvedHost, password, plugin, false);

        dialogStage.close();
    }

}
