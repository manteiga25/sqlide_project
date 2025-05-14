package com.example.sqlide.misc;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public abstract class ClipBoard {

    public static void CopyToBoard(final String copy) {
        Clipboard clip = Clipboard.getSystemClipboard();
        ClipboardContent c = new ClipboardContent();
        c.putString(copy);
        clip.setContent(c);
    }

}
