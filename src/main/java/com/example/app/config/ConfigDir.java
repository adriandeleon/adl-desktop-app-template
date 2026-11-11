package com.example.app.config;

import java.nio.file.Path;
import java.util.Map;

import com.example.app.AppInfo;
import org.jspecify.annotations.Nullable;

/** Where the config folder is. Pure, so the precedence is unit-tested. */
public final class ConfigDir {

    /** The environment variable naming the config folder. */
    public static final String ENV = AppInfo.ID.toUpperCase(java.util.Locale.ROOT) + "_CONFIG_DIR";

    /** A system property with the same meaning; the test suite points it into target/. */
    public static final String PROPERTY = AppInfo.ID + ".configDir";

    private ConfigDir() {}

    /**
     * One resolver, in the order {@code --config-dir}, {@code --dev} ({@code ~/.<app>-dev}), the
     * environment variable, the system property, then the default {@code ~/.<app>} (SET-07): a flag is
     * typed for this run, so it wins over the variable. The same on every OS.
     */
    public static Path resolve(
            @Nullable String flag, boolean dev, Map<String, String> env, @Nullable String property, String home) {
        if (flag != null && !flag.isBlank()) return Path.of(flag).toAbsolutePath();
        if (dev) return Path.of(home, "." + AppInfo.ID + "-dev");
        String fromEnv = env.get(ENV);
        if (fromEnv != null && !fromEnv.isBlank()) return Path.of(fromEnv).toAbsolutePath();
        if (property != null && !property.isBlank()) return Path.of(property).toAbsolutePath();
        return Path.of(home, "." + AppInfo.ID);
    }

    /** {@link #resolve} with this process's environment, properties and home folder. */
    public static Path resolve(@Nullable String flag, boolean dev) {
        return resolve(flag, dev, System.getenv(), System.getProperty(PROPERTY), System.getProperty("user.home"));
    }
}
