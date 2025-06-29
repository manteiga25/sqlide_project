package com.example.sqlide.Notification;

import com.example.sqlide.Container.Notification.NotificationContainer;
import com.example.sqlide.popupWindow.Notification;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.*;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.HashMap;

public class NotificationController {

    @FXML
    private VBox NotificationBox;

    private final HashMap<String, NotificationContainer> NotificationList = new HashMap<>();

    public NotificationController() throws IOException {
        initializeWatchdog(createDir());
    }

    private void updateNotification(final Notification.MessageNotification notification) {
        NotificationContainer notificationContainer = NotificationList.get(notification.id());
        notificationContainer.setMessage(notification.message());
        notificationContainer.setTitle(notification.title());
    }

    private void updateNotificationProgress(final String id, final double progress) {
        NotificationContainer notificationContainer = NotificationList.get(id);
        notificationContainer.setProgress(progress);
    }

    private void createNotificationBox(final Notification.MessageNotification notification) {
       // NotificationList.put(notification.id(), currentNotification);
        NotificationBox.getChildren().addFirst(NotificationList.get(notification.id()).createNotificationBox(notification));

    }

    private void createLoadingNotificationBox(final Notification.MessageNotification notification) {
       // NotificationList.put(notification.id(), currentNotification);
        NotificationBox.getChildren().addFirst(NotificationList.get(notification.id()).createLoadingNotificationBox(notification));

    }

    private void initializeSock() {
        Thread NotificationFetcher = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(12345)) {
                System.out.println("Servidor aguardando conexões...");

                // Aguarda uma conexão do cliente
                while (true) {
                    try (Socket clientSocket = serverSocket.accept();
                         ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream())) {

                        System.out.println("Cliente conectado: " + clientSocket.getInetAddress());

                        // Lê mensagem do cliente
                        Notification.MessageNotification message = (Notification.MessageNotification) in.readObject();
                        System.out.println("Mensagem recebida: " + message);

                        Platform.runLater(() -> createNotificationBox(message));

                    } catch (IOException e) {
                        System.err.println("Erro na conexão com o cliente: " + e.getMessage());
                    } catch (ClassNotFoundException e) {
                        System.err.println(e.getMessage());
                    }
                }

            } catch (IOException e) {
                System.err.println("Erro ao iniciar o servidor: " + e.getMessage());
            }
        });
        NotificationFetcher.setDaemon(true);
        NotificationFetcher.start();

    }

    private Path createDir() throws IOException {
        Path dir = Path.of("Notifications");

        if (Files.exists(dir)) {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
        return Files.createDirectory(dir);
    }

    private void initializeWatchdog(final Path path) {
        Thread.ofVirtual().start(() -> {
            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();

                // Register the directory for specific events
                path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_DELETE,
                        StandardWatchEventKinds.ENTRY_MODIFY);

                while (true) {
                    WatchKey key = watchService.take();

                    for (WatchEvent<?> event : key.pollEvents()) {
                        // Handle the specific event
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                            System.out.println("File created: " + event.context());
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            System.out.println("File deleted: " + event.context());
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
                            System.out.println("File modified: " + event.context());
                            final String content = readFile("Notifications/"+event.context().toString());
                            System.out.println(content);
                            if (content != null && !content.isEmpty()) {
                                JSONObject json = new JSONObject(content);

                                if (json.getInt("type") == Notification.MessageNotification.UPDATE) {
                                    updateNotificationProgress(json.getString("id"), Double.parseDouble(json.get("param").toString()));
                                } else {
                                    Notification.MessageNotification message = new Notification.MessageNotification(json.getString("id"), json.getString("title"), json.getString("message"), json.getInt("type"), LocalTime.now());
                                    System.out.println("Mensagem recebida: " + message);
                                    final NotificationContainer newContainer = new NotificationContainer();
                                    NotificationList.put(message.id(), newContainer);
                                    if (json.getInt("status") == Notification.MessageNotification.INFORMATION) {
                                        final Pane notification = newContainer.createLoadingNotificationBox(message);
                                        Platform.runLater(() -> NotificationBox.getChildren().addFirst(notification));
                                    } else {
                                        final Pane notification = newContainer.createNotificationBox(message);
                                        Platform.runLater(() -> NotificationBox.getChildren().addFirst(notification));
                                    }
                                }
                            }


                          //
                        }
                    }

                    // To receive further events, reset the key
                    key.reset();
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
    });
    }

    private String readFile(final String path) throws IOException {
    //    File file = new File(path.toUri());
      //  FileReader reader = new FileReader(file);
        BufferedReader reader = new BufferedReader(new FileReader(path));
        return reader.readLine();
    }

    @FXML
    private void cleanNotifications() {
        NotificationBox.getChildren().clear();
    }

}
