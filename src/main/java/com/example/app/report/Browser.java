package com.example.app.report;

import java.io.IOException;
import java.util.List;

import com.example.app.Os;

/**
 * Opens a URL in the default browser without JavaFX or AWT, for {@code --report}, which must work when
 * the app itself cannot start. Inside the app, {@code HostServices.showDocument} does this.
 */
public final class Browser {

    private Browser() {}

    /** The command that opens {@code url} on {@code os}: handing the URL to the OS's own opener. */
    static List<String> command(Os.Kind os, String url) {
        return switch (os) {
            case MAC -> List.of("open", url);
            case WINDOWS -> List.of("rundll32", "url.dll,FileProtocolHandler", url);
            case LINUX -> List.of("xdg-open", url);
        };
    }

    /** Starts the browser; false when it could not be started. */
    public static boolean open(String url) {
        try {
            new ProcessBuilder(command(Os.current(), url))
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
