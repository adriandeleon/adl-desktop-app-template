package com.example.app.command;

import java.util.function.Supplier;

import com.example.app.i18n.Messages;
import org.jspecify.annotations.Nullable;

/**
 * One user-facing action. Menus, the Command Palette and key bindings all run commands by id; nothing
 * is wired straight to logic.
 *
 * <p>The title is {@code command.<id>} and the palette description {@code command.<id>.desc} in the
 * message catalogs, so a registered command is localized and discoverable with no extra work.
 *
 * @param unavailable why the command cannot run right now (shown greyed with this tooltip), or null
 */
public record Command(String id, Runnable action, Supplier<@Nullable String> unavailable) {

    public String title() {
        return Messages.tr("command." + id);
    }

    public String description() {
        return Messages.tr("command." + id + ".desc");
    }

    /** Why it cannot run, or null when it can. */
    public @Nullable String unavailableReason() {
        return unavailable.get();
    }
}
