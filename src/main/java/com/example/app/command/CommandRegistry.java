package com.example.app.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jspecify.annotations.Nullable;

/** The one registry of commands. Ids are dotted and grouped by area: {@code file.open}, {@code help.about}. */
public final class CommandRegistry {

    private static final Logger LOG = Logger.getLogger(CommandRegistry.class.getName());

    private final Map<String, Command> commands = new LinkedHashMap<>();

    /** Registers a command that can always run. */
    public Command register(String id, Runnable action) {
        return register(id, action, () -> null);
    }

    /** Registers a command; {@code unavailable} says why it cannot run, or returns null when it can. */
    public Command register(String id, Runnable action, Supplier<@Nullable String> unavailable) {
        if (commands.containsKey(id)) throw new IllegalArgumentException("duplicate command id " + id);
        Command command = new Command(id, action, unavailable);
        commands.put(id, command);
        return command;
    }

    public @Nullable Command get(String id) {
        return commands.get(id);
    }

    /** Every command, in registration order. */
    public Collection<Command> all() {
        return Collections.unmodifiableCollection(commands.values());
    }

    public List<String> ids() {
        return new ArrayList<>(commands.keySet());
    }

    /**
     * Runs a command by id. Returns false when there is no such command or it is unavailable. A command
     * that throws is logged (the Debug Log has it) and counts as run.
     */
    public boolean run(String id) {
        Command command = commands.get(id);
        if (command == null || command.unavailableReason() != null) return false;
        try {
            command.action().run();
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Command " + id + " failed", e);
        }
        return true;
    }
}
