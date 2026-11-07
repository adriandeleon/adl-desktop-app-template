package com.example.app.ui;

import java.util.List;
import java.util.Locale;

import com.example.app.i18n.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every question names the thing and what happens to it, and its button the action (CONF-02/03, CONF-12). */
class ConfirmationsTest {

    @AfterEach
    void english() {
        Messages.init("en");
    }

    private static List<Confirmations.Question> every() {
        return List.of(
                Confirmations.importConfig("exampleapp-config.zip"),
                Confirmations.resetBinding("About", "Ctrl+Alt+J", "F12"),
                Confirmations.resetBinding("About", "Ctrl+Alt+J", ""));
    }

    @Test
    void eachQuestionNamesTheThingAndWhatHappens() {
        Messages.init("en");
        List<Confirmations.Question> questions = every();
        assertTrue(questions.get(0).message().contains("exampleapp-config.zip"));
        assertTrue(questions.get(0).message().contains("overwritten"));
        assertTrue(questions.get(1).message().contains("About"));
        assertTrue(questions.get(1).message().contains("Ctrl+Alt+J")
                && questions.get(1).message().contains("F12"));
        assertTrue(questions.get(1).message().contains("removed"));
        assertTrue(questions.get(2).message().contains("no keys"));
    }

    @Test
    void theButtonIsTheActionsOwnVerbInEveryLanguage() {
        for (String language : Messages.available().keySet()) {
            Messages.init(language);
            for (Confirmations.Question question : every()) {
                String action = question.action().toLowerCase(Locale.ROOT);
                assertFalse(action.isBlank(), language);
                for (String vague : List.of("ok", "yes", "sí", "sim", "oui", "ja")) {
                    assertFalse(action.equals(vague), language + ": " + question.action());
                }
                assertFalse(question.message().contains("{"), language + ": " + question.message());
                assertTrue(
                        question.message().endsWith(".") || question.message().endsWith("?"), language);
            }
        }
    }
}
