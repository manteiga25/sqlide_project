package com.example.sqlide.Console;

import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.awt.*;
import java.io.*;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SystemTerminalController {

    private final VBox container = new VBox();

    private javafx.scene.control.TextField currentField = new TextField();
    private Label currentLabel = new Label();
    private HBox currentBox = new HBox();

    private Process shellProcess;
    private final BlockingQueue<String> commandQueue = new LinkedBlockingQueue<>(); // For sending commands to the shell

    private boolean state = false;

    private static final String FONT_FAMILY = "JetBrains Mono Medium";

    private String ProcessName = "";

    public SystemTerminalController() {
        container.setStyle("-fx-background-color: #1E1E1E;");
        container.getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/Console/TextStyle.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm());

        launchShell();

        if (shellProcess != null && shellProcess.isAlive()) {
            initializeInputReader();
            initializeErrorReader();
            initializeOutputWriter();
            ProcessName = shellProcess.info().user().get() + ">";
        } else {
            addErrorLine("Failed to start system shell.");
        }
    }

    public VBox getContainer() {
        return container;
    }

    private void launchShell() {
        String osName = System.getProperty("os.name").toLowerCase();
        ProcessBuilder pb;

        if (osName.contains("win")) {
            pb = new ProcessBuilder("cmd.exe");
        } else if (osName.contains("mac") || osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
            // Try /bin/bash first, then /bin/zsh as a fallback for macOS
            if (isCommandAvailable("/bin/bash")) {
                pb = new ProcessBuilder("/bin/bash");
            } else if (osName.contains("mac") && isCommandAvailable("/bin/zsh")) {
                pb = new ProcessBuilder("/bin/zsh");
        //        Logger.info("Using /bin/zsh for macOS.");
            }
            else {
          //      Logger.error("No suitable shell found for " + osName);
                addErrorLine("No suitable shell found for " + osName);
                return;
            }
        } else {
            //Logger.error("Unsupported OS: " + osName);
            addErrorLine("Unsupported OS: " + osName);
            return;
        }

        pb.redirectErrorStream(false); // We want to handle error stream separately

        try {
            shellProcess = pb.start();
       //     Logger.info("Shell process started: " + String.join(" ", pb.command()));
        } catch (IOException e) {
         //   Logger.error("Failed to start shell process: " + e.getMessage(), e);
            shellProcess = null; // Ensure it's null if failed
            // This will be caught by the check in constructor, or display message directly
            Platform.runLater(() -> addErrorLine("Error launching shell: " + e.getMessage()));
        }
    }

    private boolean isCommandAvailable(String commandPath) {
        try {
            Process process = new ProcessBuilder(commandPath, "-c", "echo test").start();
            process.waitFor();
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private void initializeInputReader() {
        Thread inputReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(shellProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String finalLine = line;
                    Platform.runLater(() -> addLine(finalLine));
                }
            } catch (IOException e) {
                if (!shellProcess.isAlive() && e.getMessage().contains("Stream closed")) {
            //        Logger.info("Shell process input stream closed as process ended.");
                } else {
              //      Logger.error("Error reading shell input stream: " + e.getMessage(), e);
                    Platform.runLater(() -> addErrorLine("Error reading shell output: " + e.getMessage()));
                }
            } finally {
                //Logger.info("Shell input reader thread finished.");
            }
        });
        inputReaderThread.setDaemon(true);
        inputReaderThread.setName("SystemTerminal-InputReader");
        inputReaderThread.start();
    }

    private void initializeErrorReader() {
        Thread errorReaderThread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(shellProcess.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    final String finalLine = line;
                    Platform.runLater(() -> addErrorLine(finalLine));
                }
            } catch (IOException e) {
                if (!shellProcess.isAlive() && e.getMessage().contains("Stream closed")) {
              //      Logger.info("Shell process error stream closed as process ended.");
                } else {
                //    Logger.error("Error reading shell error stream: " + e.getMessage(), e);
                    Platform.runLater(() -> addErrorLine("Error reading shell error: " + e.getMessage()));
                }
            } finally {
               // Logger.info("Shell error reader thread finished.");
            }
        });
        errorReaderThread.setDaemon(true);
        errorReaderThread.setName("SystemTerminal-ErrorReader");
        errorReaderThread.start();
    }

    private void initializeOutputWriter() {
        Thread outputWriterThread = new Thread(() -> {
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(shellProcess.getOutputStream()))) {
                while (shellProcess.isAlive()) {
                    state = true;
                    String command = commandQueue.take(); // Blocks until a command is available
                    writer.write(command);
                    writer.newLine();
                    writer.flush();
                }
            } catch (IOException e) {
                if (!shellProcess.isAlive() && e.getMessage().contains("Broken pipe")) {
            //        Logger.info("Shell process output stream broken as process likely ended.");
                } else {
              //      Logger.error("Error writing to shell output stream: " + e.getMessage(), e);
                    Platform.runLater(() -> addErrorLine("Error writing to shell: " + e.getMessage()));
                }
            } catch (InterruptedException e) {
                //Logger.warning("SystemTerminal OutputWriter thread interrupted.", e);
                Thread.currentThread().interrupt(); // Preserve interrupt status
            } finally {
               // Logger.info("Shell output writer thread finished.");
            }
        });
        outputWriterThread.setDaemon(true);
        outputWriterThread.setName("SystemTerminal-OutputWriter");
        outputWriterThread.start();
    }

    private void addLine(final String line) {

        currentField.setEditable(false);
        //   currentLabel.setText("");
        if (!state) {
            container.getChildren().remove(currentBox);
        } else {
            state = false;
            final HBox box = new HBox(5);
            final javafx.scene.control.TextField newLine = new javafx.scene.control.TextField();
            newLine.setStyle("-fx-font-family: 'JetBrains Mono Medium';");
            newLine.setOnAction(_-> {
                try {
                    commandQueue.put(newLine.getText());
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            HBox.setHgrow(newLine, Priority.ALWAYS);
            currentField = newLine;

            final javafx.scene.control.Label labelLine = new javafx.scene.control.Label(ProcessName);
            currentLabel = labelLine;

            box.getChildren().addAll(labelLine, newLine);

            currentBox = box;
            container.getChildren().add(box);
            return;
        }

        final HBox box = new HBox(5);

        final HBox tmp = new HBox(5);

        final javafx.scene.control.TextField textLine = new javafx.scene.control.TextField(line);
        textLine.setStyle("-fx-font-family: 'JetBrains Mono Medium';");
        textLine.setEditable(false);
        HBox.setHgrow(textLine, Priority.ALWAYS);

        tmp.getChildren().addAll(new javafx.scene.control.Label(), textLine);

        final javafx.scene.control.TextField newLine = new javafx.scene.control.TextField();
        newLine.setOnAction(_-> {
            try {
                commandQueue.put(newLine.getText());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        HBox.setHgrow(newLine, Priority.ALWAYS);
        currentField = newLine;

        final javafx.scene.control.Label labelLine = new javafx.scene.control.Label(ProcessName);
        labelLine.setStyle("-fx-font-family: 'JetBrains Mono Medium';");
        currentLabel = labelLine;

        box.getChildren().addAll(labelLine, newLine);

        currentBox = box;

        container.getChildren().addAll(tmp, box);

    }

    private void addErrorLine(final String line) {

        System.out.println("er");

        currentField.setEditable(false);
        currentLabel.setText("");
        container.getChildren().remove(currentBox);

        final HBox box = new HBox(5);

        final HBox tmp = new HBox(5);

        final javafx.scene.control.TextField textLine = new javafx.scene.control.TextField(line);
        textLine.setEditable(false);
        textLine.setStyle("-fx-text-fill: red; -fx-font-family: 'JetBrains Mono Medium';");
        HBox.setHgrow(textLine, Priority.ALWAYS);

        tmp.getChildren().addAll(new javafx.scene.control.Label(), textLine);

        final javafx.scene.control.TextField newLine = new javafx.scene.control.TextField();
        HBox.setHgrow(newLine, Priority.ALWAYS);
        newLine.setOnAction(_-> {
            try {
                commandQueue.put(newLine.getText());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        currentField = newLine;

        final javafx.scene.control.Label labelLine = new Label(ProcessName);
        labelLine.setStyle("-fx-font-family: 'JetBrains Mono Medium';");
        currentLabel = labelLine;

        box.getChildren().addAll(labelLine, newLine);

        VBox.setVgrow(box, Priority.ALWAYS);
        VBox.setVgrow(tmp, Priority.ALWAYS);

        currentBox = box;

        container.getChildren().addAll(tmp, box);

    }

}
