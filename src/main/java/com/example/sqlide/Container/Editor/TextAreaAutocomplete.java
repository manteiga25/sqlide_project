package com.example.sqlide.Container.Editor;


import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
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

    private final BooleanProperty changed = new SimpleBooleanProperty(false);

    private int size = 14;

    public boolean isChanged() {
        return changed.get();
    }

    public void setChanged(final boolean changed) {
        this.changed.set(changed);
    }

    public BooleanProperty getChanged() {
        return changed;
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
            container.setAlignment(Pos.CENTER_LEFT);
            container.setPrefWidth(100);
            return container;
        });

        addContextMenu();
        entriesPopup.setAutoHide(true);
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
        UndoItem.visibleProperty().bind(this.undoAvailableProperty());
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
        RedoItem.visibleProperty().bind(this.redoAvailableProperty());
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

        DeleteItem.setVisible(false);
        CopyItem.setVisible(false);
        CutItem.setVisible(false);

        this.selectionProperty().addListener((_,_,val)->{
            System.out.println(val.getLength());
            final boolean state = val.getLength() != 0;
            DeleteItem.setVisible(state);
            CopyItem.setVisible(state);
            CutItem.setVisible(state);
        });

    }

    private void initializeHandlers() {
        textProperty().addListener((_, _, text) -> {
            changed.set(true);
            Thread.ofVirtual().start(()->{
                final StyleSpans<Collection<String>> style = computeHighlighting(text);
                Platform.runLater(()->setStyleSpans(0, style));
            });
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
                    //Bounds screenLoc = findCaret();
                    //entriesPopup.show(TextAreaAutocomplete.this, screenLoc.getMaxX(), screenLoc.getMinY());
                    try {
                        Optional<Bounds> caretBounds = getCharacterBoundsOnScreen(getText(getCurrentParagraph()).length(), getCaretPosition());

                        caretBounds.ifPresent(bounds -> {
                            // Mostra o popup usando as coordenadas do caret
                            // bounds.getMinX() -> Posição X (início horizontal do caractere)
                            // bounds.getMaxY() -> Posição Y (base da linha de texto do caractere)
                            entriesPopup.show(this, bounds.getMaxX(), bounds.getMaxY()+10);
                        });
                    } catch (Exception _) {
                        try {
                            Optional<Bounds> caretBounds = getCharacterBoundsOnScreen(getCaretPosition(), getText(getCurrentParagraph()).length());

                            caretBounds.ifPresent(bounds -> {
                                // Mostra o popup usando as coordenadas do caret
                                // bounds.getMinX() -> Posição X (início horizontal do caractere)
                                // bounds.getMaxY() -> Posição Y (base da linha de texto do caractere)
                                entriesPopup.show(this, bounds.getMaxX(), bounds.getMaxY()+10);
                            });
                        } catch (Exception _) {
                        }

                    }

                }
            }
        });

        addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                if (event.getDeltaY() > 0) {
                    if (size != 30) {
                        setStyle("-fx-font-size: " + ++size + "px;");
                    }
                } else {
                    if (size != 10) {
                        setStyle("-fx-font-size: " + --size + "px;");
                    }
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

    private Bounds findCaret() {
        Optional<Bounds> caretBounds = getCaretBounds();

        Bounds bounds = localToScreen(caretBounds.get());

        return bounds;
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
            scrollPane.setMaxHeight(300);//Adjust max height of the popup here
            scrollPane.setStyle("-fx-border-radius: 5px; -fx-background-radius: 5px; -fx-border-width: 0.5px;");
            scrollPane.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/css/ScrollHbarStyle.css")).toExternalForm());
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
                    wordLabel.setStyle("-fx-font-size: 16px;");
                    wordLabel.setPadding(new Insets(0, 0, 0, 5));

                    getChildren().add(wordLabel);
                }
        }

}
