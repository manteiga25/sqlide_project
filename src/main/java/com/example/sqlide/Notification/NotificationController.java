package com.example.sqlide.Notification;

import com.example.sqlide.popupWindow.Notification;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class NotificationController {

    @FXML
    private VBox NotificationBox;

    public NotificationController() {
        initializeSock();
    }

    private void createNotificationBox(final Notification.MessageNotification notification) {
        Pane containerForBorder = new Pane();
        containerForBorder.setStyle("-fx-background-color: #3C3C3C; -fx-background-radius: 10px; -fx-border-radius: 10px;");

        VBox containerNot = new VBox(10);
        containerNot.prefWidthProperty().bind(containerForBorder.widthProperty().subtract(20)); // 10px de margem em cada lado
        containerNot.prefHeightProperty().bind(containerForBorder.heightProperty().subtract(20));

        HBox notificationBox = new HBox(5);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(notificationBox, Priority.ALWAYS);

        Label title = new Label(notification.title());
        title.setStyle("-fx-font-size: 14px; -fx-text-fill: white; -fx-font-weight: bold;");
        title.setPadding(new Insets(0, 0, 0, 15));
        HBox.setHgrow(title, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label date = new Label(notification.date().toString());
        date.setStyle("-fx-text-fill: #6A6E75;");
        date.setAlignment(Pos.CENTER_RIGHT);

        notificationBox.getChildren().addAll(title, spacer, date);

        Label message = new Label(notification.message());
        message.setPadding(new Insets(0, 0, 0, 15));
        message.setStyle("-fx-font-size: 16px; -fx-text-fill: white;");
        message.setWrapText(true);
        message.setPrefHeight(Region.USE_COMPUTED_SIZE);
        VBox.setVgrow(message, Priority.ALWAYS);

        // containerNot.setFillWidth(true);

        containerNot.getChildren().addAll(notificationBox, message);
        containerForBorder.getChildren().add(containerNot);

        NotificationBox.getChildren().addFirst(containerForBorder);
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

    @FXML
    private void cleanNotifications() {
        NotificationBox.getChildren().clear();
    }

}
