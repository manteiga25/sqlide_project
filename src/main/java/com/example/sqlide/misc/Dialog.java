package com.example.sqlide.misc;

import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;

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

}
