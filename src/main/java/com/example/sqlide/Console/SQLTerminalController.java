package com.example.sqlide.Console;

import com.example.sqlide.Logger.Logger;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.SQLTypes;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.*;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.Line;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SQLTerminalController {

    private static final HashMap<SQLTypes, String> driverPath = new HashMap<>();

    private final VBox container = new VBox();

    private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(), sender = new LinkedBlockingQueue<>();

    private TextField currentField = new TextField();

    public VBox getContainer() {
        return container;
    }

    public SQLTerminalController() {
        container.setStyle("-fx-background-color: #1E1E1E;");
        container.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/Console/TextStyle.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());
        driverPath.put(SQLTypes.SQLITE, "sqlite3.exe");
        addLine("SQL> ");
     //   initializeQueue();
    }

    public void load(final SQLTypes type, final String database) throws IOException {
        System.out.println(database);
      //  ProcessBuilder pb = new ProcessBuilder("DriverCLI/" + driverPath.get(type), " --opt --user=" + database.getUsername() + " --password=" + database.getPassword() + " --databases " + database.getDatabaseName() + " > " + database.getUrl());
        //  pb.directory(new File(System.getProperty("user.dir")));
        ProcessBuilder pb = new ProcessBuilder("DriverCLI//" + driverPath.get(type), "-header", "-line", "-bail", database.substring(database.indexOf("C")));
        Process process = pb.start();
        initializeErr(process);
        initializeOutput(process);
        initializeInput(process);
    }

    private void initializeQueue() {
        final Thread reciver = new Thread(()->{
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    addLine(queue.take());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        reciver.setDaemon(true);
        reciver.start();
    }

    private void initializeInput(final Process process) {
        final Thread readerT = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    System.out.println("Python: " + linha);
                    String finalLinha = linha;
                    Platform.runLater(()->addLine(finalLinha));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        readerT.setDaemon(true);
        readerT.start();
    }

    private void initializeOutput(final Process process) {
        final Thread writerT = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream()))) {
                String linha;
                while (!(linha = sender.take()).isEmpty()) {
                    System.out.println(linha);
                    writer.write(linha);
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        writerT.setDaemon(true);
        writerT.start();
    }

    private void initializeErr(final Process process) {
        final Thread readerT = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    //queue.put(linha);
                    String finalLinha = linha;
                    Platform.runLater(()->addErrorLine(finalLinha));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        readerT.setDaemon(true);
        readerT.start();
    }

    private void addLine(final String line) {

        currentField.setEditable(false);

        final HBox box = new HBox(5);

        final HBox tmp = new HBox(5);

        final TextField textLine = new TextField(line);
        textLine.setEditable(false);

        tmp.getChildren().addAll(new Label("SQL>"), textLine);

        final TextField newLine = new TextField();
        newLine.setOnAction(_-> {
            try {
                sender.put(newLine.getText());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        currentField = newLine;

        box.getChildren().addAll(new Label("SQL>"), newLine);

        container.getChildren().addAll(tmp, box);

    }

    private void addErrorLine(final String line) {

        currentField.setEditable(false);

        final HBox box = new HBox(5);

        final TextField textLine = new TextField(line);
        textLine.setEditable(false);
        textLine.setStyle("-fx-text-fill: red;");

        final TextField newLine = new TextField();
        newLine.setOnAction(_-> {
            try {
                sender.put(newLine.getText());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        currentField = newLine;

        box.getChildren().addAll(new Label("SQL>"), newLine);

        container.getChildren().add(box);

    }

}
