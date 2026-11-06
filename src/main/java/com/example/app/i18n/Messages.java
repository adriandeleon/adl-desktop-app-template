package com.example.app.i18n;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The UI string catalog: {@code tr("some.key")} is the only way text reaches a control.
 *
 * <p>English ({@code messages.properties}) is the base and the fallback; each other language is an
 * overlay, {@code messages_<lang>.properties}. Read with a UTF-8 reader from this module's own
 * resources rather than through {@code ResourceBundle}, so no {@code opens} and no module-path surprises.
 * A missing key renders as the key itself, never an exception. {@code MessagesTest} holds every
 * catalog to the same keys.
 */
public final class Messages {

    /** Bundled languages, code to endonym, English first. Six is the floor, not the set. */
    private static final Map<String, String> LANGUAGES = new LinkedHashMap<>();

    static {
        LANGUAGES.put("en", "English");
        LANGUAGES.put("es", "Español");
        LANGUAGES.put("pt", "Português");
        LANGUAGES.put("fr", "Français");
        LANGUAGES.put("it", "Italiano");
        LANGUAGES.put("de", "Deutsch");
    }

    /** An argument placeholder ({@code {0}}, {@code {1,number,#}}): what makes a value a pattern. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\s*\\d");

    private static Map<String, String> base = load("en");
    private static Map<String, String> active = base;
    private static String current = "en";

    private Messages() {}

    /** Loads English plus the chosen language's overlay; an unknown code means English. */
    public static synchronized void init(String lang) {
        current = LANGUAGES.containsKey(lang) ? lang : "en";
        active = "en".equals(current) ? base : load(current);
    }

    /** The active language code. */
    public static String current() {
        return current;
    }

    /** Bundled languages, code to endonym, in display order. */
    public static Map<String, String> available() {
        return new LinkedHashMap<>(LANGUAGES);
    }

    /** The localized text for {@code key}: active language, then English, then the key itself. */
    public static String tr(String key, @Nullable Object... args) {
        String value = active.get(key);
        if (value == null) value = base.get(key);
        if (value == null) return key;
        return render(value, args);
    }

    /** Whether a key exists in the English base catalog. */
    public static boolean has(String key) {
        return base.containsKey(key);
    }

    /**
     * Whether a value is a {@link MessageFormat} pattern. Decided by the value, never by the call: a
     * value with a placeholder doubles its apostrophes ({@code ''}), one without writes them single.
     */
    public static boolean isPattern(String value) {
        return PLACEHOLDER.matcher(value).find();
    }

    static String render(String value, @Nullable Object @Nullable [] args) {
        if (!isPattern(value)) return value;
        try {
            return new MessageFormat(value, Locale.forLanguageTag(current)).format(args == null ? new Object[0] : args);
        } catch (IllegalArgumentException e) {
            return value; // a malformed pattern must not take the UI down
        }
    }

    /**
     * The effective language: the explicit choice if bundled, else the system's if bundled, else
     * English. Blank {@code pref} means "follow the system".
     */
    public static String resolve(String pref, String systemLang) {
        if (LANGUAGES.containsKey(pref)) return pref;
        if (LANGUAGES.containsKey(systemLang)) return systemLang;
        return "en";
    }

    /** Every key and raw value of one catalog, for the parity test. */
    static Map<String, String> bundle(String lang) {
        return load(lang);
    }

    private static Map<String, String> load(String lang) {
        String name = "en".equals(lang) ? "messages.properties" : "messages_" + lang + ".properties";
        Properties properties = new Properties();
        try (InputStream in = Messages.class.getResourceAsStream(name)) {
            if (in != null) properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            // An unreadable catalog leaves the map empty; tr() falls back to English, then the key.
        }
        Map<String, String> map = new HashMap<>();
        for (String key : properties.stringPropertyNames()) map.put(key, properties.getProperty(key));
        return map;
    }
}
