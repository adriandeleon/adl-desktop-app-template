package com.example.app.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;

/** Small vector icons (24 × 24 paths), coloured by CSS ({@code .icon}) so they follow the theme. */
public final class Icons {

    public static final String SLIDERS = "M4 6h9v2H4zM17 6h3v2h-3zM13 4h2v6h-2zM4 16h3v2H4zM11 16h9v2h-9zM7 14h2v6H7z";
    public static final String CONTRAST = "M12 3a9 9 0 1 0 0 18a9 9 0 0 0 0-18zm0 2v14a7 7 0 0 1 0-14z";
    public static final String KEYBOARD =
            "M3 6h18v12H3zM5 8v2h2V8zm4 0v2h2V8zm4 0v2h2V8zm4 0v2h2V8zM5 11v2h2v-2zm12 0v2h2v-2zM7 14v2h10v-2z";
    public static final String SYNC =
            "M12 4V1L8 5l4 4V6a6 6 0 0 1 6 6h2a8 8 0 0 0-8-8zm0 14a6 6 0 0 1-6-6H4a8 8 0 0 0 8 8v3l4-4-4-4z";
    public static final String FOLDER = "M3 5h7l2 2h9v12H3zM5 9v8h14V9z";

    private Icons() {}

    public static Node of(String path) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFillRule(FillRule.EVEN_ODD);
        svg.getStyleClass().add("icon");
        StackPane box = new StackPane(svg);
        box.getStyleClass().add("icon-box");
        box.setMinSize(18, 18);
        box.setPrefSize(18, 18);
        svg.setScaleX(0.75);
        svg.setScaleY(0.75);
        return box;
    }
}
