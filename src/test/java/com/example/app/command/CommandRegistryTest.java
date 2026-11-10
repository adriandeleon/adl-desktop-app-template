package com.example.app.command;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Which command is running: what an overlay asks to credit the button that opened it (TBAR-07). */
class CommandRegistryTest {

    @Test
    void runningNamesTheInnermostCommandAndNothingBetweenCommands() {
        CommandRegistry registry = new CommandRegistry();
        List<String> seen = new ArrayList<>();
        registry.register("inner", () -> seen.add(String.valueOf(registry.running())));
        registry.register("outer", () -> {
            seen.add(String.valueOf(registry.running()));
            registry.run("inner");
            seen.add(String.valueOf(registry.running()));
        });
        assertNull(registry.running());
        registry.run("outer");
        assertEquals(List.of("outer", "inner", "outer"), seen);
        assertNull(registry.running());
    }

    @Test
    void aCommandThatThrowsStillEndsItsRun() {
        CommandRegistry registry = new CommandRegistry();
        registry.register("broken", () -> {
            throw new IllegalStateException("on purpose");
        });
        registry.run("broken");
        assertNull(registry.running());
    }
}
