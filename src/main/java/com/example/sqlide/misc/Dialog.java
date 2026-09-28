package com.example.sqlide.misc;

import com.sun.jna.platform.unix.solaris.LibKstat;
import javafx.collections.FXCollections;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class Dialog {

    public static String TextDialog(final String title, final String header, final String content) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.getDialogPane().getStylesheets().addAll(
                Dialog.class.getResource("/css/Assistant/dialog.css").toExternalForm(),
                Dialog.class.getResource("/css/ContextMenuStyle.css").toExternalForm()
        );
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        Optional<String> result = dialog.showAndWait();

        return result.orElse(null);
    }

    public static String ChoiceDialogStage(final List<String> choices, final String title, final String header, final String content) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.getFirst(), choices);
        dialog.getDialogPane().getStylesheets().addAll(
                Dialog.class.getResource("/css/Assistant/ChoiceDialog.css").toExternalForm(),
                Dialog.class.getResource("/css/ContextMenuStyle.css").toExternalForm(),
                Dialog.class.getResource("/css/ChoiceBoxModern.css").toExternalForm()
        );
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content);

        Optional<String> result = dialog.showAndWait();
        return result.orElse(null);
    }

    public static List<String> ChoiceMultipleDialogStage(final List<List<String>> choices, final String option, final String title, final String header, final List<String> content) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(choices.getFirst().getFirst(), option);
        dialog.getDialogPane().getStylesheets().addAll(
                Dialog.class.getResource("/css/Assistant/ChoiceDialog.css").toExternalForm(),
                Dialog.class.getResource("/css/ContextMenuStyle.css").toExternalForm(),
                Dialog.class.getResource("/css/ChoiceBoxModern.css").toExternalForm()
        );
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setContentText(content.getFirst());

        GridPane grid = (GridPane) dialog.getDialogPane().getChildren().getLast();
        grid.setHgap(5);
        grid.setVgap(5);

        for (int comboIndex = 1; comboIndex < choices.size(); comboIndex++) {
            Label labelChoice = new Label(content.get(comboIndex) + ":");
            labelChoice.setTextFill(Color.WHITE);
            ComboBox<String> comboBox = new ComboBox<>(FXCollections.observableArrayList(choices.get(comboIndex)));
            comboBox.getSelectionModel().selectFirst();
            grid.add(labelChoice, 0, comboIndex+1);
            grid.add(comboBox, 1, comboIndex+1);
        }

        Optional<String> result = dialog.showAndWait();

        List<String> list = new ArrayList<>();
        list.add(result.orElse(null));

        for (int comboIndex = 1; comboIndex < choices.size(); comboIndex += 2) {
            ComboBox<String> comboBox = (ComboBox<String>) grid.getChildren().get(comboIndex);
            list.add(comboBox.getValue());
        }

        return list;
    }

}
