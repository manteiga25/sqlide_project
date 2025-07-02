package com.example.sqlide.Container.Assistant;

import atlantafx.base.controls.Spacer;
import com.example.sqlide.misc.ClipBoard;
import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class AssistantBoxCode extends Pane {

    private final VBox box = new VBox(10);

  /*  Font customFont = Font.loadFont(
            getClass().getResourceAsStream("/Fonts/JetBrains.ttf"), 18
    ); */

    public AssistantBoxCode() {
        // Melhorar o estilo visual com cantos mais arredondados e efeito de sombra
       // box.setStyle("-fx-background-color: linear-gradient(to bottom, #424242, #393939);");
       // setStyle("-fx-background-radius: 18px; -fx-border-radius: 18px; -fx-border-color: #505050; -fx-border-width: 1px;");
       // box.setStyle("-fx-background-radius: 18px; -fx-border-radius: 18px; -fx-border-color: #505050; -fx-border-width: 1px;");

        // Adicionar efeito de sombra para dar profundidade
        DropShadow shadow = new DropShadow();
        shadow.setRadius(10.0);
        shadow.setOffsetX(1.0);
        shadow.setOffsetY(1.0);
        shadow.setColor(Color.rgb(0, 0, 0, 0.3));
        setEffect(shadow);

        getChildren().add(box);

        // Ajustar margens para melhor equilíbrio visual
        VBox.setMargin(this, new Insets(5, 80, 5, 0));
    }

    private TextArea createMessageContainer(final String message) {
        final TextArea areaMessage = new TextArea();
        areaMessage.setEditable(false);
        areaMessage.setWrapText(true);

        // Adicionar padding interno para melhor legibilidade
        areaMessage.setPadding(new Insets(10, 12, 10, 12));

        // Melhorar a responsividade da largura
        areaMessage.prefWidthProperty().bind(widthProperty().subtract(20));

        // Definir o texto e calcular a altura necessária
        areaMessage.setText(message);

        // Usar um método mais preciso para calcular a altura necessária
        calculateTextAreaHeight(areaMessage, message);

        return areaMessage;
    }

    // Método para calcular a altura necessária do TextArea com base no conteúdo
    private void calculateTextAreaHeight(TextArea textArea, String content) {
        // Usar um Text auxiliar para calcular a altura necessária
        Text text = new Text();
        text.setFont(Font.font("JetBrains Mono Medium", 14)); // Mesmo font do CSS
        text.setText(content);

        // Definir a largura máxima para o cálculo de quebra de linha
        // Subtrair o padding e as bordas para obter a largura real do texto
        double maxWidth = textArea.prefWidthProperty().getValue() - 40; // Ajuste para padding e bordas
        text.setWrappingWidth(maxWidth);

        // Calcular a altura necessária com base no layout do texto
        double textHeight = text.getLayoutBounds().getHeight();

        // Adicionar espaço para padding e bordas
        double totalHeight = textHeight + 30; // Ajuste para padding e bordas

        // Definir uma altura mínima
        totalHeight = Math.max(totalHeight, 60);

        // Aplicar a altura calculada
        textArea.setPrefHeight(totalHeight+30);

        // Adicionar um listener para ajustar a altura quando o tamanho da janela mudar
        textArea.widthProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                text.setWrappingWidth(newVal.doubleValue() - 40);
                double newTextHeight = text.getLayoutBounds().getHeight();
                double newTotalHeight = newTextHeight + 30;
                newTotalHeight = Math.max(newTotalHeight, 60);
                textArea.setPrefHeight(newTotalHeight);
            });
        });
    }

    public void addMessage(final String message) {
        final TextArea areaMessage = createMessageContainer(message);

        // Melhorar o estilo visual com cores mais contrastantes e cantos arredondados
        areaMessage.setStyle("-fx-control-inner-background: #424242; " +
                "-fx-border-color: #505050; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 12px; " +
                "-fx-background-radius: 12px; " +
                "-fx-region-background: #424242; " +
                "-fx-text-fill: #ECECEC; " +
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent; " +
                "-fx-display-caret: false;"); // Desativar cursor para evitar problemas de altura

        box.getChildren().add(areaMessage);
    }

    public void addErrorMessage(final String message) {
        final TextArea areaMessage = createMessageContainer(message);

        // Melhorar o estilo visual para mensagens de erro
        areaMessage.setStyle("-fx-control-inner-background: #4A3939; " +
                "-fx-border-color: #633A3A; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 12px; " +
                "-fx-background-radius: 12px; " +
                "-fx-region-background: #4A3939; " +
                "-fx-text-fill: #FF9A9A; " +
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent; " +
                "-fx-display-caret: false;"); // Desativar cursor para evitar problemas de altura

        box.getChildren().add(areaMessage);
    }

    public void addCode(final String code) {
        final String language = code.substring(0, code.indexOf("\n"));
        final String codeContent = code.replaceFirst(language + "\n", "");

        VBox boxAi = new VBox();

        // Melhorar o botão de cópia
        JFXButton copy = new JFXButton("copy");
        copy.setTextFill(Color.WHITE);
        copy.setStyle("-fx-background-color: #2A2A2A; -fx-background-radius: 4px; -fx-padding: 4 8 4 8;");

        // Adicionar efeito hover
    //    copy.setOnMouseEntered(e -> copy.setStyle("-fx-background-color: #3574F0; -fx-background-radius: 4px; -fx-padding: 4 8 4 8;"));
      //  copy.setOnMouseExited(e -> copy.setStyle("-fx-background-color: #2A2A2A; -fx-background-radius: 4px; -fx-padding: 4 8 4 8;"));

        copy.setOnAction(e-> {
            ClipBoard.CopyToBoard(codeContent);
            // Feedback visual temporário
          //  copy.setText("copied!");
          //  copy.setStyle("-fx-background-color: #2E7D32; -fx-background-radius: 4px; -fx-padding: 4 8 4 8;");

            // Restaurar após 1.5 segundos
       /*     new Thread(() -> {
                //    Thread.sleep(1500);
                Platform.runLater(() -> {
                    copy.setText("copy");
                    copy.setStyle("-fx-background-color: #2A2A2A; -fx-background-radius: 4px; -fx-padding: 4 8 4 8;");
                });
            }).start(); */
        });

        // Melhorar a barra de ferramentas do código
        ToolBar tool = new ToolBar(new Label(language), new Spacer(), copy);
        tool.setStyle("-fx-background-color: #1A1A1A; -fx-background-radius: 8 8 0 0;");

        VBox.setMargin(tool, new Insets(0, 0, 0, 0));

        final TextArea areaMessage = createMessageContainer(codeContent);

        // Melhorar o estilo visual para blocos de código
        areaMessage.setStyle("-fx-control-inner-background: black; " +
                "-fx-border-color: #333333; " +
                "-fx-border-width: 1px; " +
                "-fx-border-radius: 0 0 8 8; " +
                "-fx-background-radius: 0 0 8 8; " +
                "-fx-region-background: black; " +
                "-fx-text-fill: #D4D4D4; " +
             //   "-fx-font-family: 'JetBrains Mono Medium'; " +
                "-fx-focus-color: transparent; " +
                "-fx-faint-focus-color: transparent; " +
                "-fx-display-caret: false;"); // Desativar cursor para evitar problemas de altura

        VBox.setMargin(areaMessage, new Insets(0, 0, 0, 0));

        // Melhorar o estilo do container de código
        boxAi.setStyle("-fx-background-radius: 8; -fx-background-color: #2A2A2A; -fx-border-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0, 0, 0, 0.3), 8, 0, 0, 2);");

        boxAi.getChildren().addAll(tool, areaMessage);
        box.getChildren().add(boxAi);
    }

}