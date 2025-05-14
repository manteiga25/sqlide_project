package com.example.sqlide.popupWindow;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.util.Optional;

abstract public class handleWindow {

    public static void ShowInformation(final String title, final String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }

    public static boolean ShowConfirmation(final String title, final String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);

        ButtonType buttonOk = ButtonType.OK;
        ButtonType buttonCancel = ButtonType.CANCEL;

        alert.getButtonTypes().setAll(buttonOk, buttonCancel);

        Optional<ButtonType> result = alert.showAndWait();

        // Processa a resposta
        return result.filter(buttonType -> buttonType == buttonOk).isPresent();
    }

    public static void ShowError(final String title, final String message) {
        Platform.runLater(() -> {Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }

    public static void ShowSucess(final String title, final String message) {
        Platform.runLater(() -> {
            AlertSucess alert = new AlertSucess();
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();
        });
    }

    public static class AlertSucess extends Alert {
        public AlertSucess() {
            super(AlertType.INFORMATION);
            this.setGraphic(loadIcon());
        }

        private ImageView loadIcon() {
            Image image = new Image(getClass().getResource("/com/example/sqlide/images/sucess.jpg").toExternalForm());  // Caminho para o arquivo de imagem
            ImageView icon = new ImageView(image);
            icon.setFitHeight(25);
            icon.setFitWidth(25);
            return icon;
        }
    }

}
