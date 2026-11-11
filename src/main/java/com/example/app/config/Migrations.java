package com.example.app.config;

import java.util.Map;
import java.util.function.UnaryOperator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Forward migrations of {@code settings.json}: step {@code v} turns a version-{@code v} tree into
 * version {@code v + 1}. A purely additive change still gets a step (the identity), so every version
 * has one and the chain never has a gap ({@code MigrationsTest} checks).
 */
public final class Migrations {

    /** A file with no {@code schemaVersion} predates versioning and is version 1. */
    static final int UNVERSIONED = 1;

    private static final Map<Integer, UnaryOperator<ObjectNode>> STEPS = Map.of(
            1,
            Migrations::v1ToV2,
            2,
            UnaryOperator.identity(),
            3,
            UnaryOperator.identity(),
            4,
            UnaryOperator.identity(),
            5,
            UnaryOperator.identity(),
            6,
            UnaryOperator.identity(),
            7,
            Migrations::v7SessionApart);

    private Migrations() {}

    /** The version a tree says it is. */
    public static int versionOf(JsonNode tree) {
        JsonNode version = tree.get("schemaVersion");
        return version != null && version.canConvertToInt() ? version.asInt() : UNVERSIONED;
    }

    /** Runs every step from the tree's version up to {@link Settings#SCHEMA_VERSION}. */
    public static ObjectNode migrate(ObjectNode tree) {
        ObjectNode current = tree;
        for (int v = versionOf(tree); v < Settings.SCHEMA_VERSION; v++) {
            UnaryOperator<ObjectNode> step = STEPS.get(v);
            if (step == null) throw new IllegalStateException("no migration from schema " + v);
            current = step.apply(current);
            current.put("schemaVersion", v + 1);
        }
        return current;
    }

    /** Whether every version below the current one has a step. */
    static boolean complete() {
        for (int v = UNVERSIONED; v < Settings.SCHEMA_VERSION; v++) {
            if (!STEPS.containsKey(v)) return false;
        }
        return true;
    }

    // Version 2 to 3 added titleBarMenus, 3 to 4 showToolbar, 4 to 5 showTabBar and the tool windows' state
    // (window.toolWindows), 5 to 6 simpleMode and 6 to 7 uiZoom: purely additive, so the identity steps
    // above; their defaults fill in. 7 to 8 adds textZoom and takes the window out: it moves to session.json
    // (SET-08), which ConfigStore writes from the old file's window the first time.

    /** Version 8 keeps no session state in settings.json: the window block goes (ConfigStore carries it over). */
    static ObjectNode v7SessionApart(ObjectNode tree) {
        tree.remove("window");
        return tree;
    }

    /** Version 1 had a {@code darkMode} switch; version 2 has a three-way {@code theme}. */
    static ObjectNode v1ToV2(ObjectNode tree) {
        JsonNode dark = tree.remove("darkMode");
        if (dark != null && dark.isBoolean()) {
            tree.put("theme", dark.asBoolean() ? ThemeChoice.DARK.name() : ThemeChoice.LIGHT.name());
        }
        return tree;
    }
}
