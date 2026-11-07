package com.example.app;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;

import javafx.application.Application;

import com.example.app.cli.CommandLine;
import com.example.app.config.ConfigDir;
import com.example.app.instance.SingleInstance;
import com.example.app.report.Browser;
import com.example.app.report.ReportCommand;
import com.example.app.ui.DebugLog;

/**
 * The entry point. Not an {@code Application} subclass, so the fat jar can start JavaFX from the
 * classpath. Everything that must work without a display ({@code --help}, {@code --version},
 * {@code --report}, the single-instance hand-off) happens here, before the toolkit starts.
 */
public final class Main {

    /** {@link #run}'s answer when the GUI should start. */
    static final int LAUNCH = -1;

    private Main() {}

    public static void main(String[] args) {
        // The first statement: the app never uses AWT, and on macOS an initialised AWT contends with
        // JavaFX for the AppKit run loop.
        System.setProperty("java.awt.headless", "true");
        DebugLog.install();
        int code = run(args, System.out, System.err);
        if (code != LAUNCH) System.exit(code);
        Application.launch(App.class, args);
    }

    /** Handles the command line; returns an exit code, or {@link #LAUNCH} to start the GUI. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        CommandLine cli = CommandLine.parse(args);
        if (cli.error() != null) {
            err.println(AppInfo.ID + ": " + cli.error());
            err.println("Try '" + AppInfo.ID + " --help' for the options.");
            return 2;
        }
        if (cli.help()) {
            out.print(CommandLine.usage());
            return 0;
        }
        if (cli.version()) {
            out.println(AppInfo.versionText());
            return 0;
        }
        Path dir = ConfigDir.resolve(cli.configDir(), cli.dev());
        if (cli.report()) return ReportCommand.run(dir, out, Browser::open);
        List<String> files = cli.files().stream()
                .map(f -> Path.of(f).toAbsolutePath().toString())
                .toList();
        if (!cli.newInstance() && SingleInstance.handOff(dir, files)) {
            out.println(AppInfo.NAME + " is already running; handed over to it.");
            return 0;
        }
        return LAUNCH;
    }
}
