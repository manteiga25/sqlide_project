package com.example.sqlide.popupWindow;

import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import org.controlsfx.control.action.Action;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;

public abstract class Notification {

    public static void showSuccessNotification(final String name, final String title, final String message, final Action func) throws IOException {
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

        sendNotificationInfo(name, title, message, MessageNotification.CREATE, MessageNotification.SUCCESS, null);

    }

    public static void showProgressNotification(final String name, final String title, final String message, final double size) throws IOException {
        sendNotificationInfo(name, title, message, MessageNotification.CREATE, MessageNotification.INFORMATION, size);
    }

    public static void updateProgressNotification(final String name, final double value) throws IOException {
        sendNotificationInfo(name, "", "", MessageNotification.UPDATE, MessageNotification.INFORMATION, value);
    }

    public static void showErrorNotification(final String name, final String title, final String message) throws IOException {
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

        sendNotificationInfo(name, title, message, MessageNotification.CREATE, MessageNotification.ERROR, null);

    }

   /* private static void sendNotificationInfo(final String title, final String message, final int type) {
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
    } */

    private static void sendNotificationInfo(final String name, final String title, final String message, final int type, final int status, final Object object) throws IOException {
        File file = null;
        if (type == MessageNotification.CREATE) {
            file = Files.createFile(Path.of("Notifications\\" + name + ".json")).toFile();
        } else file = new File("Notifications\\" + name + ".json");
        //file.deleteOnExit();
        File finalFile = file;
        Thread.ofVirtual().start(()->{
            try {
             //   final BufferedWriter writer = new BufferedWriter(new FileWriter(finalFile));
                final JSONObject sender = new JSONObject();
                sender.put("id", name);
                sender.put("title", title);
                sender.put("message", message);
                sender.put("type", type);
                sender.put("status", status);
                sender.put("param", object);
                System.out.println("asasas " + sender);
                //writer.write(sender.toString());
                Files.write(finalFile.toPath(), sender.toString().getBytes());
             //   writer.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public static void showInformationNotification(final String title, final String message) {

        Notifications.create()
                .title(title)
                .text(message)
                //.threshold(5, Notifications.create().title("Collapsed Notification"))
                .hideAfter(Duration.millis(5000))
                .darkStyle()
                .action()
                .position(Pos.BOTTOM_RIGHT)
                .showInformation();
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

    public record MessageNotification(String id, String title, String message, int type, LocalTime date) implements Serializable {
            public static int SUCCESS = 0;
            public static int INFORMATION = 1;
            public static int QUESTION = 2;
            public static int ERROR = 3;

            public static int CREATE = 0;
            public static int UPDATE = 1;

    }

}
