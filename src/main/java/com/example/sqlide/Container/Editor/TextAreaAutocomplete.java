package com.example.sqlide.Container.Editor;


import javafx.collections.ObservableList;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Path;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextAreaAutocomplete extends CodeArea {

    protected final Popup entriesPopup = new Popup();

    protected final VBox WordsBox = new VBox();

    private int wordIndex = 0, wordCount;

    private ArrayList<String> words = new ArrayList<>();

    Font customFont = Font.loadFont(
            getClass().getResourceAsStream("/Fonts/JetBrains.ttf"), 18
    );

    private boolean changed = false;

    public boolean isChanged() {
        return changed;
    }

    public void setChanged(final boolean changed) {
        this.changed = changed;
    }

    private static final String COMMENT_REGEX =
            "(?<COMMENT>" +
                    "--[^\\n]*" +
                    "|" +
                    "/\\*(?>[^*]+|\\*+[^*/])*\\*+/" + // Comentários fechados
                    "|" +
                    "(?s)/\\*.*" + // Comentários não fechados
                    ")";

    private static final String NumberFormat = "(?<NUMBER>\\b\\d+(\\.\\d+)?\\b)";

    private static final String STRING_REGEX =
            "(?<STRING>\"((?:\\\\\\.|[^\"])*)\")";

    private Pattern SQL_KEYWORDS;

    public void setAutoCompleteWords(final ArrayList<String> words) {
        this.words = words;
        final String regex = words.stream()
                .map(Pattern::quote) // Escapa caracteres especiais
                .collect(Collectors.joining(" |"));
        this.SQL_KEYWORDS = Pattern.compile("(?<KEYWORD>"+regex+")|"+COMMENT_REGEX+"|"+NumberFormat+"|"+STRING_REGEX, Pattern.CASE_INSENSITIVE);
    }

    String bufferWord = "";

    private boolean newWord = false;

    public void setText(final String text) {
        replaceText(text);
    }

    public TextAreaAutocomplete() {
        super();
        getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/Editor/CodeStyle.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/Editor/KeyWordsStyle.css")).toExternalForm());
        setParagraphGraphicFactory(LineNumberFactory.get(this));
        setParagraphGraphicFactory(line -> {
            Label lineNumber = new Label(String.valueOf(line + 1));
            lineNumber.getStyleClass().add("lineno-label"); // Aplica a classe CSS
            HBox container = new HBox(lineNumber);
            container.getStyleClass().add("lineno-container");
            container.setAlignment(Pos.CENTER_RIGHT);
            return container;
        });

        addContextMenu();
        entriesPopup.setAutoHide(true);
        WordsBox.setPrefHeight(Region.USE_COMPUTED_SIZE);
        initializeHandlers();
    }

    private StyleSpans<Collection<String>> computeHighlighting(final String text) {
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        Matcher matcher = SQL_KEYWORDS.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            String styleClass =
                    matcher.group("COMMENT") != null ? "comment" :
                            matcher.group("KEYWORD") != null ? "keyword" :
                                    matcher.group("NUMBER") != null ? "number" :
                                        matcher.group("STRING") != null ? "string" : null;

            if (styleClass != null) {
                spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
                lastEnd = matcher.end();
            }
        }

        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    private void addContextMenu() {

        getStylesheets().addAll(Objects.requireNonNull(getClass().getResource("/css/ContextMenuStyle.css")).toExternalForm(), Objects.requireNonNull(getClass().getResource("/css/Editor/MenuStyle.css")).toExternalForm());

        MenuItem UndoItem = new MenuItem();
        HBox undoBox = new HBox(
                new Label("Undo"),
                new Region(), // Spacer dinâmico
                new Text("Ctrl+Z")
        );
        undoBox.setAlignment(Pos.CENTER_LEFT); // Alinha elementos
        HBox.setHgrow(undoBox.getChildren().get(1), Priority.ALWAYS); // Spacer ocupa espaço restante
        undoBox.setPrefWidth(120); // Largura fixa
        UndoItem.setGraphic(undoBox);
        UndoItem.setOnAction(_ -> undo());

// Repita o mesmo padrão para os demais itens
        MenuItem RedoItem = new MenuItem();
        HBox redoBox = new HBox(
                new Label("Redo"),
                new Region(),
                new Text("Ctrl+Y")
        );
        redoBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(redoBox.getChildren().get(1), Priority.ALWAYS);
        redoBox.setPrefWidth(120);
        RedoItem.setGraphic(redoBox);
        RedoItem.setOnAction(_ -> redo());

        MenuItem CutItem = new MenuItem();
        HBox cutBox = new HBox(
                new Label("Cut"),
                new Region(),
                new Text("Ctrl+X")
        );
        cutBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(cutBox.getChildren().get(1), Priority.ALWAYS);
        cutBox.setPrefWidth(120);
        CutItem.setGraphic(cutBox);
        CutItem.setOnAction(_ -> cut());

        MenuItem CopyItem = new MenuItem();
        HBox copyBox = new HBox(
                new Label("Copy"),
                new Region(),
                new Text("Ctrl+C")
        );
        copyBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(copyBox.getChildren().get(1), Priority.ALWAYS);
        copyBox.setPrefWidth(120);
        CopyItem.setGraphic(copyBox);
        CopyItem.setOnAction(_->copy());

        MenuItem PasteItem = new MenuItem();
        HBox pasteBox = new HBox(
                new Label("Paste"),
                new Region(),
                new Text("Ctrl+V")
        );
        pasteBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(pasteBox.getChildren().get(1), Priority.ALWAYS);
        pasteBox.setPrefWidth(120);
        PasteItem.setGraphic(pasteBox);
        PasteItem.setOnAction(_->paste());

        MenuItem DeleteItem = new MenuItem();
        HBox delBox = new HBox(
                new Label("Delete"),
                new Region(),
                new Text("Del")
        );
        delBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(delBox.getChildren().get(1), Priority.ALWAYS);
        delBox.setPrefWidth(120);
        DeleteItem.setGraphic(delBox);
        DeleteItem.setOnAction(_->deleteText(getSelection()));

        MenuItem SelectWord = new MenuItem();
        HBox selBox = new HBox(
                new Label("Select word"),
                new Region()
        );
        selBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(selBox.getChildren().get(1), Priority.ALWAYS);
        selBox.setPrefWidth(120);
        SelectWord.setGraphic(selBox);
        SelectWord.setOnAction(_->selectWord());

        MenuItem SelectLine = new MenuItem();
        HBox selLineBox = new HBox(
                new Label("Select Line"),
                new Region()
        );
        selLineBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(selLineBox.getChildren().get(1), Priority.ALWAYS);
        selLineBox.setPrefWidth(120);
        SelectLine.setGraphic(selLineBox);
        SelectLine.setOnAction(_->selectLine());

        MenuItem SelectItem = new MenuItem();
        HBox selAllBox = new HBox(
                new Label("Select All"),
                new Region()
        );
        selAllBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(selAllBox.getChildren().get(1), Priority.ALWAYS);
        selAllBox.setPrefWidth(120);
        SelectItem.setGraphic(selAllBox);
        SelectItem.setOnAction(_->selectAll());

        setContextMenu(new ContextMenu(UndoItem, RedoItem, new SeparatorMenuItem(), CutItem, CopyItem, PasteItem, new SeparatorMenuItem(), DeleteItem, SelectWord, SelectLine, SelectItem));

    }

    private void initializeHandlers() {
        textProperty().addListener((_, _, text) -> {
            changed = true;
            setStyleSpans(0, computeHighlighting(text));
        });
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
                    Point2D screenLoc = findCaret();
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

    private Point2D findCaret() {
        Optional<Bounds> caretBounds = getCaretBounds();
        Point2D screenCoords = null;
        if (caretBounds.isPresent()) {
            Bounds bounds = caretBounds.get();
            double x = bounds.getMinX(); // Posição X relativa ao CodeArea
            double y = bounds.getMinY(); // Posição Y relativa ao CodeArea

            // Converter para coordenadas da tela (opcional)
            screenCoords = localToScreen(x, y);
            System.out.println("Posição na tela: X=" + screenCoords.getX() + ", Y=" + screenCoords.getY());
        }
        return screenCoords;
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
