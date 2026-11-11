package com.example.app;

import javafx.application.Application;
import javafx.stage.Stage;

import com.example.app.cli.CommandLine;
import com.example.app.ui.FxHost;
import com.example.app.ui.Startup;
import org.jspecify.annotations.Nullable;

/** The JavaFX application. All of the start-up lives in {@link Startup}, where the tests reach it. */
public final class App extends Application {

    private Startup.@Nullable Prepared prepared;
    private Startup.@Nullable Running running;

    /** On the launcher thread: the file work of the start, which {@code main} began reading ahead (PERF-01). */
    @Override
    public void init() {
        prepared =
                Startup.takePrepared(CommandLine.parse(getParameters().getRaw().toArray(String[]::new)));
    }

    @Override
    public void start(Stage stage) {
        Startup.Prepared ready = prepared;
        if (ready == null) throw new IllegalStateException("start before init");
        running = Startup.start(stage, ready, new FxHost(getHostServices()));
    }

    @Override
    public void stop() {
        if (running != null) running.close();
    }
}
