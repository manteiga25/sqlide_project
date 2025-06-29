package com.example.sqlide.Container.Notification;

import com.example.sqlide.popupWindow.Notification;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.*;

public class NotificationContainer {
    private final Pane containerForBorder = new Pane();

    private Label title, message, progressLabel;

    private ProgressBar progress;

    public void setProgress(final double progress) {
        this.progress.setProgress(progress);
        this.progressLabel.setText(progress+"%");
    }

    public void setTitle(final String title) {
        this.title.setText(title);
    }

    public void setMessage(final String message) {
        this.message.setText(message);
    }

    /**
     * Constrói e insere, no topo de NotificationBox,
     * um painel estilizado com os dados da notificação.
     */
    public Pane createNotificationBox(final Notification.MessageNotification notification) {
        // ===== borda arredondada com fundo escuro =====
        containerForBorder.setId(notification.id());
        containerForBorder.setStyle(
                "-fx-background-color: #3C3C3C; " +
                        "-fx-background-radius: 10px; " +
                        "-fx-border-radius: 10px;"
        );

        // ===== conteúdo: título + data + mensagem =====
        VBox containerNot = new VBox(10);
        containerNot.prefWidthProperty().bind(
                containerForBorder.widthProperty().subtract(20)
        ); // margem de 10px em cada lado
        containerNot.prefHeightProperty().bind(
                containerForBorder.heightProperty().subtract(20)
        );

        // HBox com título e data alinhados nas extremidades
        HBox notificationBox = new HBox(5);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(notificationBox, Priority.ALWAYS);

        title = new Label(notification.title());
        title.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold;"
        );
        title.setPadding(new Insets(0, 0, 0, 15));
        HBox.setHgrow(title, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label date = new Label(notification.date().toString());
        date.setStyle("-fx-text-fill: #6A6E75;");
        date.setAlignment(Pos.CENTER_RIGHT);

        notificationBox.getChildren().addAll(title, spacer, date);

        // Mensagem principal abaixo da HBox
        message = new Label(notification.message());
        message.setPadding(new Insets(0, 0, 0, 15));
        message.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-text-fill: white;"
        );
        message.setWrapText(true);
        message.setPrefHeight(Region.USE_COMPUTED_SIZE);
        VBox.setVgrow(message, Priority.ALWAYS);

        // monta hierarquia
        containerNot.getChildren().addAll(notificationBox, message);
        containerForBorder.getChildren().add(containerNot);

        // insere no topo da NotificationBox
        return containerForBorder;
    }

    public Pane createLoadingNotificationBox(final Notification.MessageNotification notification) {
        // ===== borda arredondada com fundo escuro =====
        containerForBorder.setId(notification.id());
        containerForBorder.setStyle(
                "-fx-background-color: #3C3C3C; " +
                        "-fx-background-radius: 10px; " +
                        "-fx-border-radius: 10px;"
        );

        // ===== conteúdo: título + data + mensagem =====
        VBox containerNot = new VBox(10);
        containerNot.prefWidthProperty().bind(
                containerForBorder.widthProperty().subtract(20)
        ); // margem de 10px em cada lado
        containerNot.prefHeightProperty().bind(
                containerForBorder.heightProperty().subtract(20)
        );

        // HBox com título e data alinhados nas extremidades
        HBox notificationBox = new HBox(5);
        notificationBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(notificationBox, Priority.ALWAYS);

        title = new Label(notification.title());
        title.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-weight: bold;"
        );
        title.setPadding(new Insets(0, 0, 0, 15));
        HBox.setHgrow(title, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label date = new Label(notification.date().toString());
        date.setStyle("-fx-text-fill: #6A6E75;");
        date.setAlignment(Pos.CENTER_RIGHT);

        notificationBox.getChildren().addAll(title, spacer, date);

        // Mensagem principal abaixo da HBox
        message = new Label(notification.message());
        message.setPadding(new Insets(0, 0, 0, 15));
        message.setStyle(
                "-fx-font-size: 16px; " +
                        "-fx-text-fill: white;"
        );
        message.setWrapText(true);
        message.setPrefHeight(Region.USE_COMPUTED_SIZE);
        VBox.setVgrow(message, Priority.ALWAYS);

        HBox progressBox = new HBox(5);
        progressBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        progressBox.setPrefWidth(Region.USE_COMPUTED_SIZE);
        progressBox.setPadding(new Insets(0, 0, 10, 15));

        progress = new ProgressBar();
        progress.setProgress(0);
        progress.setPrefWidth(Region.USE_COMPUTED_SIZE);
        progress.setPrefHeight(Region.USE_COMPUTED_SIZE);
        progress.setMaxWidth(1.7976931348623157E308);
        HBox.setHgrow(progress, Priority.ALWAYS);

        progressLabel = new Label("0%");
        progressLabel.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-text-fill: white;"
        );

        progressBox.getChildren().addAll(progress, progressLabel);

        // monta hierarquia
        containerNot.getChildren().addAll(notificationBox, message, progressBox);
        containerForBorder.getChildren().add(containerNot);

        // insere no topo da NotificationBox
        return containerForBorder;
    }

    // Exemplo de uso:
   /* public void showNewNotification(Notification.MessageNotification notification) {
        createNotificationBox(notification);
    } */
}
