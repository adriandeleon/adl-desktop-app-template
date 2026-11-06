package com.example.app;

import javafx.application.Application;
import javafx.stage.Stage;

import com.example.app.cli.CommandLine;
import com.example.app.ui.FxHost;
import com.example.app.ui.Startup;
import org.jspecify.annotations.Nullable;

/** The JavaFX application. All of the start-up lives in {@link Startup}, where the tests reach it. */
public final class App extends Application {

    private Startup.@Nullable Running running;

    @Override
    public void start(Stage stage) {
        CommandLine cli = CommandLine.parse(getParameters().getRaw().toArray(String[]::new));
        running = Startup.start(stage, cli, new FxHost(getHostServices()));
    }

    @Override
    public void stop() {
        if (running != null) running.close();
    }
}
