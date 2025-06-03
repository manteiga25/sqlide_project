package com.example.sqlide.Console;

import com.jfoenix.controls.JFXTextField;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

import java.awt.*;
import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SystemTerminalController {

    private VBox container;
    private JFXTextField currentField;
    private Process shellProcess;
    private BlockingQueue<String> commandQueue; // For sending commands to the shell

    private static final String FONT_FAMILY = "monospaced";
    private static final int FONT_SIZE = 13; // Example size

    public SystemTerminalController() {
        container = new VBox(5); // Spacing between elements
        container.setPadding(new Insets(10));
        container.setStyle("-fx-background-color: #2C2C2C;");

        commandQueue = new LinkedBlockingQueue<>();

        // Initial input field
        setupNewInputField();

        launchShell();

        if (shellProcess != null && shellProcess.isAlive()) {
            initializeInputReader();
            initializeErrorReader();
            initializeOutputWriter();
        } else {
            addLineToTerminal("Failed to start system shell.", true);
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
                addLineToTerminal("No suitable shell found for " + osName, true);
                return;
            }
        } else {
            //Logger.error("Unsupported OS: " + osName);
            addLineToTerminal("Unsupported OS: " + osName, true);
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
            Platform.runLater(() -> addLineToTerminal("Error launching shell: " + e.getMessage(), true));
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
                    Platform.runLater(() -> addLineToTerminal(finalLine, false));
                }
            } catch (IOException e) {
                if (!shellProcess.isAlive() && e.getMessage().contains("Stream closed")) {
            //        Logger.info("Shell process input stream closed as process ended.");
                } else {
              //      Logger.error("Error reading shell input stream: " + e.getMessage(), e);
                    Platform.runLater(() -> addLineToTerminal("Error reading shell output: " + e.getMessage(), true));
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
                    Platform.runLater(() -> addLineToTerminal(finalLine, true));
                }
            } catch (IOException e) {
                if (!shellProcess.isAlive() && e.getMessage().contains("Stream closed")) {
              //      Logger.info("Shell process error stream closed as process ended.");
                } else {
                //    Logger.error("Error reading shell error stream: " + e.getMessage(), e);
                    Platform.runLater(() -> addLineToTerminal("Error reading shell error: " + e.getMessage(), true));
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
                    Platform.runLater(() -> addLineToTerminal("Error writing to shell: " + e.getMessage(), true));
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

    private void addLineToTerminal(String text, boolean isError) {
        JFXTextField lineField = new JFXTextField(text);
        lineField.setEditable(false);
        lineField.setFont(Font.font(FONT_FAMILY, FONT_SIZE));
        lineField.setStyle("-fx-background-color: transparent;"); // No background for output lines
        if (isError) {
         //   lineField.setTextFill(Color.RED);
        } else {
           // lineField.setTextFill(Color.WHITE);
        }
        lineField.setDisableAnimation(true);


        // If currentField is the last child, remove it before adding the new line and new input field
        if (!container.getChildren().isEmpty() && container.getChildren().get(container.getChildren().size() - 1) == currentField) {
            container.getChildren().remove(currentField);
        }

        container.getChildren().add(lineField);

        // If the shell process is still alive, add a new input field.
        // Otherwise, don't add a new one.
        if (shellProcess != null && shellProcess.isAlive()) {
            if (currentField != null && !currentField.isEditable()) { // If the current field was just made non-editable
                setupNewInputField(); // Add new input field
            } else if (currentField == null) { // Initial setup or if shell died and restarted (hypothetically)
                setupNewInputField();
            } else { // currentField is already there and active, add it back
                if (!container.getChildren().contains(currentField)) {
                    container.getChildren().add(currentField);
                }
                currentField.requestFocus();
            }
        } else {
          //  Logger.info("Shell process is not alive. Not adding new input field.");
            if (currentField != null) currentField.setEditable(false); // Disable last input field
        }
    }

    private void setupNewInputField() {
        if (currentField != null) {
            currentField.setEditable(false); // Make the previous field non-editable
            // Optionally change style of previously entered command lines
            currentField.setStyle("-fx-background-color: transparent; -fx-text-fill: #90EE90;"); // Light green for sent commands
        }

        JFXTextField newField = new JFXTextField();
        newField.setFont(Font.font(FONT_FAMILY, FONT_SIZE));
      //  newField.setTextFill(Color.WHITE);
        newField.setPromptText(">"); // Simple prompt
        newField.setStyle("-fx-background-color: transparent; -fx-prompt-text-fill: #888888;");
        newField.setDisableAnimation(true);

        newField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                String command = newField.getText().trim();
                if (!command.isEmpty()) {
                    // Echo command - create a new non-editable field for the command text itself
                    JFXTextField commandEchoField = new JFXTextField(newField.getPromptText() + command);
                    commandEchoField.setEditable(false);
                    commandEchoField.setFont(Font.font(FONT_FAMILY, FONT_SIZE));
                    //commandEchoField.setTextFill(Color.LIGHTGREEN); // Color for echoed command
                    commandEchoField.setStyle("-fx-background-color: transparent;");
                    commandEchoField.setDisableAnimation(true);

                    // Remove the current input field (newField), add echo, then add a brand new input field
                    if (container.getChildren().contains(newField)) {
                        container.getChildren().remove(newField);
                    }
                    container.getChildren().add(commandEchoField);

                    try {
                        commandQueue.put(command);
                    } catch (InterruptedException e) {
                //        Logger.error("Failed to queue command: " + e.getMessage(), e);
                        Thread.currentThread().interrupt();
                    }
                    // currentField is now the commandEchoField, which is non-editable.
                    // A new input field will be set up by setupNewInputField called from addLineToTerminal,
                    // or we call it directly here.
                    currentField = commandEchoField; // update currentField to the echo, which is non-editable
                    setupNewInputField(); // This will create the *next* input field
                } else {
                    // If the command is empty, just create a new prompt line essentially
                    JFXTextField promptField = new JFXTextField(newField.getPromptText());
                    promptField.setEditable(false);
                    promptField.setFont(Font.font(FONT_FAMILY, FONT_SIZE));
                 //   promptField.setTextFill(Color.WHITE);
                    promptField.setStyle("-fx-background-color: transparent;");

                    if (container.getChildren().contains(newField)) {
                        container.getChildren().remove(newField);
                    }
                    container.getChildren().add(promptField);
                    currentField = promptField;
                    setupNewInputField();
                }
            }
        });

        currentField = newField; // Update the reference to the current active field
        if (!container.getChildren().contains(currentField)) {
            container.getChildren().add(currentField);
        }
        currentField.requestFocus();
    }

}
