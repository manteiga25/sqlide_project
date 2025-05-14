package com.example.sqlide.popupWindow;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.controlsfx.control.action.Action;

import java.io.*;
import java.net.Socket;
import java.time.LocalTime;

public abstract class Notification {

    public static void showSuccessNotification(final String title, final String message, final Action func) {
        Image image = new Image(Notification.class.getResource("/com/example/sqlide/images/sucess.jpg").toExternalForm());  // Caminho para o arquivo de imagem
        ImageView icon = new ImageView(image);
        icon.setFitHeight(25);
        icon.setFitWidth(25);



        Notifications.create()
                .title(title)
                .text(message)
                .graphic(icon)
                //.threshold(5, Notifications.create().title("Collapsed Notification"))
                .hideAfter(Duration.millis(5000))
                .darkStyle()
                .action(func)
                .position(Pos.BOTTOM_RIGHT)
                .show();

        sendNotificationInfo(title, message, MessageNotification.SUCCESS);

    }

    public static void showErrorNotification(final String title, final String message) {
        Image image = new Image(Notification.class.getResource("/com/example/sqlide/images/sucess.jpg").toExternalForm());  // Caminho para o arquivo de imagem
        ImageView icon = new ImageView(image);
        icon.setFitHeight(25);
        icon.setFitWidth(25);



        Notifications.create()
                .title(title)
                .text(message)
                //.threshold(5, Notifications.create().title("Collapsed Notification"))
                .hideAfter(Duration.millis(5000))
                .darkStyle()
                .position(Pos.BOTTOM_RIGHT)
                .showError();

        sendNotificationInfo(title, message, MessageNotification.ERROR);

    }

    private static void sendNotificationInfo(final String title, final String message, final int type) {
        Thread sender = new Thread(()->{
            try (Socket socket = new Socket("localhost", 12345);
                 ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream())) {

                System.out.println("Conectado ao servidor.");

                // Envia mensagem ao servidor
                //    out.println("Olá, servidor!");
                out.writeObject(new MessageNotification(title, message, type, LocalTime.now().withNano(0)));

            } catch (IOException e) {
                System.err.println("Erro na conexão: " + e.getMessage());
            }
        });
        sender.setDaemon(true);
        sender.start();
    }

    public static void showSuccessNotification(final String title, final String message) {

        Image image = new Image(Notification.class.getResource("/com/example/sqlide/images/sucess.jpg").toExternalForm());  // Caminho para o arquivo de imagem
        ImageView icon = new ImageView(image);
        icon.setFitHeight(25);
        icon.setFitWidth(25);

        Notifications.create()
                .title(title)
                .text(message)
                .graphic(icon)
                //.threshold(5, Notifications.create().title("Collapsed Notification"))
                .hideAfter(Duration.millis(5000))
                .darkStyle()
                .action()
                .position(Pos.BOTTOM_RIGHT)
                .show();
    }

    public record MessageNotification(String title, String message, int type, LocalTime date) implements Serializable {
            public static int SUCCESS = 0;
            public static int INFORMATION = 1;
            public static int QUESTION = 2;
            public static int ERROR = 3;

    }

}
