package com.example.app.ui;

import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

/** Puts text on the system clipboard: the one place the commands that copy go through. */
final class Clipboards {

    private Clipboards() {}

    static void copy(String text) {
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
    }
}
