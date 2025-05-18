package com.example.sqlide.loading;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class LoadingController {

    @FXML
    private Label Title, Content;

    public void setTitle(final String title) {
        Title.setText(title);
    }

    public void setContent(final String content) {
        Content.setText(content);
    }

}
