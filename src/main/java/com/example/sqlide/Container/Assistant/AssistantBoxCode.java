package com.example.sqlide.Container.Assistant;

import atlantafx.base.controls.Spacer;
import com.example.sqlide.misc.ClipBoard;
import com.jfoenix.controls.JFXButton;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;

public class AssistantBoxCode extends Pane {

    private final VBox box = new VBox(10);

    public AssistantBoxCode() {
        setStyle("-fx-background-radius: 15px; -fx-border-radius-15px; -fx-background-color: #393939;");
        getChildren().add(box);
        VBox.setMargin(this, new Insets(0,60,0,0));
    }

    private TextArea createMessageContainer(final String message) {
        final long lines = message.chars().filter(ch->ch == '\n').count()+1;

        final TextArea areaMessage = new TextArea();
        areaMessage.setEditable(false);
        areaMessage.setWrapText(true);
        areaMessage.setPrefHeight(lines * 20 + 14);
        areaMessage.setText(message);
       // areaMessage.setStyle("-fx-control-inner-background: #393939; -fx-border-color: #393939; -fx-region-background: #393939;");
        areaMessage.prefWidthProperty().bind(widthProperty().subtract(30));
        return areaMessage;
    }

    public void addMessage(final String message) {
        final TextArea areaMessage = createMessageContainer(message);
        areaMessage.setStyle("-fx-control-inner-background: #393939; -fx-border-color: #393939; -fx-region-background: #393939; -fx-text-fill: white;");
        box.getChildren().add(areaMessage);
    }

    public void addErrorMessage(final String message) {
        final TextArea areaMessage = createMessageContainer(message);
        areaMessage.setStyle("-fx-control-inner-background: #393939; -fx-border-color: #393939; -fx-region-background: #393939;-fx-text-fill: red;");
        box.getChildren().add(areaMessage);
    }

    public void addCode(final String code) {

        final String language = code.substring(0, code.indexOf("\n"));

        VBox boxAi = new VBox();

        JFXButton copy = new JFXButton("copy");

        copy.setOnAction(e-> ClipBoard.CopyToBoard(code));

        ToolBar tool = new ToolBar(new Label(language), new Spacer(), copy);
        tool.setStyle("-fx-background-color: #1A1A1A");

        VBox.setMargin(tool, new Insets(0,0,0,10));

     /*   TextArea areaMessage = new TextArea();
        areaMessage.setEditable(false);
        areaMessage.setWrapText(true);
        areaMessage.setPrefHeight(lines * 20 + 14);
        areaMessage.setText(code.replaceFirst(language + "\n", ""));
       // areaMessage.setStyle("-fx-control-inner-background: black; -fx-region-background: #1A1A1A;");
        areaMessage.setStyle("-fx-control-inner-background: #393939; -fx-border-color: #393939; -fx-region-background: #393939;");
        areaMessage.prefWidthProperty().bind(widthProperty().subtract(30));*/
        final TextArea areaMessage = createMessageContainer(code.replaceFirst(language + "\n", ""));
        areaMessage.setStyle("-fx-control-inner-background: black; -fx-region-background: #1A1A1A;");
        VBox.setMargin(areaMessage, new Insets(0,0,0,10));
        boxAi.getChildren().addAll(tool, areaMessage);
        box.getChildren().add(boxAi);
    }

}