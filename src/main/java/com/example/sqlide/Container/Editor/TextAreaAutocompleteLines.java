package com.example.sqlide.Container.Editor;

import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

public class TextAreaAutocompleteLines extends HBox {

    private final VBox LinesContainer = new VBox();

    private final ScrollPane scrollLine = new ScrollPane();

    private final TextAreaAutocomplete editorText = new TextAreaAutocomplete();

    private long totalLines = 0;

    private double size = 14;

    private boolean changed = false;

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(final boolean changed) {
        this.changed = changed;
    }

    public TextAreaAutocompleteLines() {
        editorText.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/TextAreaStyle.css")).toExternalForm());

        scrollLine.setContent(LinesContainer); // Envolvendo o LinesContainer no ScrollPane
        scrollLine.setFocusTraversable(false);
        setFocusTraversable(false);

        initializeHandler();
        initializeStyle();

        getScrollPane();

        getChildren().addAll(scrollLine, editorText);
    }

    public void setAutoCompleteWords(final ArrayList<String> words) {
        editorText.setAutoCompleteWords(words);
    }

    private void initializeHandler() {

        addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) {
                    if (size != 30) {
                        editorText.setStyle("-fx-font-size: " + ++size + "px;");
                        for (Node lineNode : LinesContainer.getChildren()) {
                            Label line = (Label) lineNode;
                            line.setStyle("-fx-font-size: " + size + "px;");
                        }

                    }
                } else {
                    if (size != 10) {
                        editorText.setStyle("-fx-font-size: " + --size + "px;");
                        for (Node lineNode : LinesContainer.getChildren()) {
                            Label line = (Label) lineNode;
                            line.setStyle("-fx-font-size: " + size + "px;");
                        }
                    }
                }
            }
        });

        editorText.textProperty().addListener(((observableValue, oldString, newString) -> {
            changed = true;
            final long oldCount = oldString.lines().parallel().count();
            final long newCount = newString.lines().parallel().count();
            if (oldCount > newCount) {
                LinesContainer.getChildren().remove((int) newCount, (int) oldCount);
                totalLines -= (oldCount-newCount);
            } else if (oldCount < newCount) {
                for (int line = 0; line < newCount-oldCount; line++) {
                    LinesContainer.getChildren().add(createLine());
                }
            }
        }));

    }

    private Label createLine() {
        Label newLine = new Label(String.valueOf(++totalLines));
        newLine.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/line.css")).toExternalForm());
        newLine.getStyleClass().add("line-number");
        return newLine;
    }

    private void initializeStyle() {
        scrollLine.setFitToWidth(true);

        HBox.setHgrow(editorText, Priority.ALWAYS);  // Faz o editorText ocupar o espaço restante
        HBox.setHgrow(LinesContainer, Priority.ALWAYS);  // Faz o editorText ocupar o espaço restante
        HBox.setHgrow(scrollLine, Priority.NEVER);
        scrollLine.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        LinesContainer.setFillWidth(true);
        LinesContainer.setAlignment(Pos.CENTER);
        LinesContainer.setStyle("-fx-background-color: #2c2c2c;");

        setStyle("-fx-base: #3c3c3c;");
    }

    private void getScrollPane() {
        Platform.runLater(() -> {
            // Força a aplicação dos estilos e layout
            editorText.applyCss();
            editorText.layout();

            Set<Node> nodes = editorText.lookupAll(".scroll-bar");
            for (Node node : nodes) {
                if (node instanceof ScrollBar sb) {
                    if (sb.getOrientation() == Orientation.VERTICAL) {
                        sb.valueProperty().bindBidirectional(scrollLine.vvalueProperty());
                        break;
                    }
                }
            }
        });
    }

    public String getText() {
        return editorText.getText();
    }

    public void setText(final String text) {
        editorText.setText(text);
    }

}
