package com.example.sqlide.Container.Editor;


import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.stage.Popup;

import java.util.ArrayList;
import java.util.List;

public class TextAreaAutocomplete extends TextArea {

    protected final Popup entriesPopup = new Popup();

    protected final VBox WordsBox = new VBox();

    private int wordIndex = 0, wordCount;

    private ArrayList<String> words = new ArrayList<>();

    public void setAutoCompleteWords(final ArrayList<String> words) {
        this.words = words;
    }

    String bufferWord = "";

    private boolean newWord = false;

    public TextAreaAutocomplete() {
        super();
        entriesPopup.setAutoHide(true);
        WordsBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        initializeHandlers();
}

    private void initializeHandlers() {
        WordsBox.setOnKeyPressed(event-> {
            if (event.getCode() == KeyCode.UP) {
                if (wordIndex > 0) {
                    WordsBox.getChildren().get(--wordIndex).requestFocus();
                }
            } else if (event.getCode() == KeyCode.DOWN) {
                if (wordIndex < wordCount-1) {
                    WordsBox.getChildren().get(++wordIndex).requestFocus();
                }
            }
        });
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.BACK_SPACE && newWord && !bufferWord.isEmpty()) {
                bufferWord = bufferWord.substring(0, bufferWord.length()-1);
            }
        });
        setOnKeyTyped(e -> {
            if (e.getCharacter().equals(" ")) {  // When space is pressed
                bufferWord = "";
                entriesPopup.hide();
                newWord = true;  // Reset for the next word
            } else if (newWord) {
                // Append typed character to the bufferWord
                if (e.getCharacter().equals("\b") && bufferWord.length() > 1) {
                    bufferWord = bufferWord.substring(0, bufferWord.length()-1);
                } else {
                    bufferWord += e.getCharacter().toUpperCase();
                }

                refreshMenu();
                if (entriesPopup.getContent() != null) {
                    Path caret = findCaret(this);
                    Point2D screenLoc = findScreenLocation(caret);
                    entriesPopup.show(TextAreaAutocomplete.this, screenLoc.getX(), screenLoc.getY() + 20);
                }
            }
        });

        // Handle mouse click event
        setOnMouseClicked(e -> {
            newWord = false;
            bufferWord = "";
            entriesPopup.hide();  // Hide the popup when clicked outside
        });
    }

    private Path findCaret(Parent parent) {
        // Warning: this is an ENORMOUS HACK
        for (Node n : parent.getChildrenUnmodifiable()) {
            if (n instanceof Path) {
                return (Path) n;
            } else if (n instanceof Parent) {
                Path p = findCaret((Parent) n);
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    private Point2D findScreenLocation(Node node) {
        Bounds caretBounds = node.getBoundsInLocal();
        return node.localToScreen(caretBounds.getMinX(), caretBounds.getMinY());
    }

// Refresh and show suggestions in the menu
private void refreshMenu() {
        if (entriesPopup.isShowing()) {
            entriesPopup.hide();
        }
    wordIndex = wordCount = 0;
    entriesPopup.getContent().clear();
    WordsBox.getChildren().clear();

    for (final String word : words) {
        if (word.length() <= bufferWord.length()) {
            continue;
        }
        if (!bufferWord.isEmpty() && word.substring(0, bufferWord.length()).equals(bufferWord)) {

            BoxWord wordContainer = new BoxWord(word);
            wordContainer.ADDLabel();

            WordsBox.getChildren().add(wordContainer);
            wordCount++;
        }
    }
    if (wordCount != 0) {
        if (wordCount == 1) {
            entriesPopup.getContent().add(WordsBox);
        } else {
            ScrollPane scrollPane = new ScrollPane(WordsBox);
            scrollPane.setMaxHeight(200);//Adjust max height of the popup here
            scrollPane.setMaxWidth(200);//Adjust max width of the popup here
            WordsBox.getChildren().getFirst().requestFocus();
            entriesPopup.getContent().add(scrollPane);
        }
    }
}

        private class BoxWord extends HBox {
                private final String word;
                public BoxWord(final String word) {
                    super();
                    this.word = word;
                    setFocusTraversable(true);
                    setStyle("-fx-background-color: #3c3c3c;");
                    setOnMouseClicked(event -> {
                            deleteText(getCaretPosition() - bufferWord.length(), getCaretPosition());
                            insertText(getCaretPosition(), word);
                            newWord = false;
                            bufferWord = "";
                            entriesPopup.hide();  // Hide the popup
                    });
                    setOnKeyPressed(event -> {
                            if (event.getCode() == KeyCode.ENTER) {
                                deleteText(getCaretPosition() - bufferWord.length(), getCaretPosition());
                                insertText(getCaretPosition(), word);
                                newWord = false;
                                bufferWord = "";
                                entriesPopup.hide();  // Hide the popup
                                event.consume(); // cancel the press
                            }
                    });
                    focusedProperty().addListener((obs, oldValue, newValue) -> {
                        if (newValue) {
                            setStyle("-fx-background-color: #43454A;");
                        }
                        else {
                            setStyle("-fx-background-color: #3c3c3c;");
                        }
                    });
                }

                public void ADDLabel() {
                    Label wordLabel = new Label(word);
                    wordLabel.setTextFill(Color.WHITE);

                    getChildren().add(wordLabel);
                }
        }

}
