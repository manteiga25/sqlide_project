package com.example.sqlide.Notification;

import com.example.sqlide.popupWindow.Notification;
import org.controlsfx.control.action.Action;

import java.awt.*;
import java.io.File;
import java.io.IOException;

public interface NotificationInterface {

    default void createSuccessNotification(final String id, final String title, final String message, final String Path) {

        Action function = new Action("open", _->{
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();

                // Verifica se a ação de abrir uma pasta é suportada
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    try {
                        // Cria um objeto File com o caminho da pasta
                        File folder = new File(Path);

                        // Verifica se o caminho é uma pasta válida
                        if (folder.exists() && folder.isDirectory()) {
                            desktop.open(folder); // Abre a pasta no explorador de arquivos
                        }
                    } catch (IOException _) {
                    }
                }
            }
        });
        function.setStyle("-fx-background-color: #3574f0; -fx-text-fill: white");
        try {
            Notification.showSuccessNotification(id, title, message, function);
        } catch (IOException _) {
        }


    }

    default void createErrorNotification(final String id, final String title, final String message) {
        try {
            Notification.showErrorNotification(id, title, message);
        } catch (IOException _) {
        }
    }

    default void createLoading(final String id, final String title, final String message) throws IOException {
        Notification.showProgressNotification(id, title, message, 0);
    }

    default void updateLoading(final String id, final double value) throws IOException {
        Notification.updateProgressNotification(id, value);
    }

}
