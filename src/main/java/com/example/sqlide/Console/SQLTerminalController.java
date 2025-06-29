package com.example.sqlide.Console;

import com.example.sqlide.Logger.Logger;
import com.example.sqlide.drivers.model.DataBase;
import com.example.sqlide.drivers.model.SQLTypes;
import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
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

    private final BlockingQueue<String> sender = new LinkedBlockingQueue<>();

    private final TextArea historyArea = new TextArea(), promptArea = new TextArea();

    private final Label promptLabel = new Label("SQL>");

    private TextArea currentField = new TextArea();
    private Label currentLabel = new Label();
    private HBox currentBox = new HBox();

    private boolean state = false;

    public VBox getContainer() {
        return container;
    }

    public SQLTerminalController() {
        historyArea.setEditable(false);
        historyArea.setStyle("-fx-font-family: 'JetBrains Mono Medium';");
        VBox.setVgrow(historyArea, Priority.ALWAYS);
        container.setStyle("-fx-background-color: #1E1E1E;");
        container.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/Console/TextStyle.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/TextAreaStyle.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());

        promptArea.setStyle("-fx-font-family: 'JetBrains Mono Medium';");
        promptArea.setPrefRowCount(3);  // Altura baseada em 3 linhas
        promptArea.setMaxHeight(150);   // Altura máxima fixa

        HBox commandBox = new HBox(promptLabel, promptArea);
        commandBox.setMaxHeight(Region.USE_PREF_SIZE); // Não expandir

        container.getChildren().addAll(historyArea, commandBox);
        driverPath.put(SQLTypes.SQLITE, "sqlite3.exe");
        promptArea.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                try {
                    sender.put(promptArea.getText());
                } catch (InterruptedException _) {
                }
            }
        });
     //   addLine("");
     //   initializeQueue();
    }

    public void load(final SQLTypes type, final String database) throws IOException {
        System.out.println(database);
      //  ProcessBuilder pb = new ProcessBuilder("DriverCLI/" + driverPath.get(type), " --opt --user=" + database.getUsername() + " --password=" + database.getPassword() + " --databases " + database.getDatabaseName() + " > " + database.getUrl());
        //  pb.directory(new File(System.getProperty("user.dir")));
        ProcessBuilder pb = new ProcessBuilder("DriverCLI/" + driverPath.get(type), "-header", "-line", "-bail", database.substring(database.indexOf("C")));
        Process process = pb.start();
        initializeErr(process);
        initializeOutput(process);
        initializeInput(process);
    }

    private void initializeInput(final Process process) {
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    String finalLinha = linha;
                    Platform.runLater(()->addLine(finalLinha));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void initializeOutput(final Process process) {
        Thread.ofVirtual().start(() -> {
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream()))) {
                String linha;
                while (!(linha = sender.take()).isEmpty()) {
                    state = true;
                    final String copy = "SQL> " + linha;
                    Platform.runLater(()->{
                        promptArea.setEditable(false);
                        promptArea.setText("");
                        addLine(copy);
                    });
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
    }

    private void initializeErr(final Process process) {
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String linha;
                while ((linha = reader.readLine()) != null) {
                    //queue.put(linha);
                    String finalLinha = "SQL> Error: " + linha;
                    Platform.runLater(()->{
                        promptArea.setEditable(false);
                        promptArea.setText("");
                        addLine(finalLinha);
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

   /* private void addLine(final String line) {

        currentField.setEditable(false);
     //   currentLabel.setText("");
        if (!state) {
            container.getChildren().remove(currentBox);
        } else state = false;

        final HBox box = new HBox(5);

        final HBox tmp = new HBox(5);

        final TextField textLine = new TextField(line);
        textLine.setEditable(false);
        HBox.setHgrow(textLine, Priority.ALWAYS);

        tmp.getChildren().addAll(new Label(), textLine);

        final TextField newLine = new TextField();
        newLine.setOnAction(_-> {
            try {
                sender.put(newLine.getText());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        HBox.setHgrow(newLine, Priority.ALWAYS);
        //currentField = newLine;

        final Label labelLine = new Label("SQL>");
        currentLabel = labelLine;

        box.getChildren().addAll(labelLine, newLine);

        currentBox = box;

        container.getChildren().addAll(tmp, box);

    } */

    private void addLine(final String line) {

        historyArea.appendText(line+"\n");

        promptArea.setEditable(true);

    }

    private void addErrorLine(final String line) {

        currentField.setEditable(false);
        currentLabel.setText("");
        container.getChildren().remove(currentBox);

        final HBox box = new HBox(5);

        final HBox tmp = new HBox(5);

        final TextField textLine = new TextField(line);
        textLine.setEditable(false);
        textLine.setStyle("-fx-text-fill: red;");
        HBox.setHgrow(textLine, Priority.ALWAYS);

        tmp.getChildren().addAll(new Label(), textLine);

        final TextField newLine = new TextField();
        HBox.setHgrow(newLine, Priority.ALWAYS);
        newLine.setOnAction(_-> {
            try {
                sender.put(newLine.getText());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
       // currentField = newLine;

        final Label labelLine = new Label("SQL>");
        currentLabel = labelLine;

        box.getChildren().addAll(labelLine, newLine);

        VBox.setVgrow(box, Priority.ALWAYS);
        VBox.setVgrow(tmp, Priority.ALWAYS);

        currentBox = box;

        container.getChildren().addAll(tmp, box);

    }

}
