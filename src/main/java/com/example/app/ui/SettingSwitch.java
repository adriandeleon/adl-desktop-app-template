package com.example.app.ui;

import javafx.scene.AccessibleAction;
import javafx.scene.AccessibleAttribute;
import javafx.scene.AccessibleRole;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import atlantafx.base.controls.ToggleSwitch;
import org.jspecify.annotations.Nullable;

/**
 * The Settings on/off control, Editora's: an AtlantaFX {@link ToggleSwitch} that a keyboard and a screen
 * reader can use. The stock switch only reacts to a mouse release and reports the accessible role
 * {@code PARENT}; this one toggles on Space and Enter, reports {@code TOGGLE_BUTTON} with its selected state,
 * and fires through the accessibility API.
 *
 * <p>{@link #setOnChange} runs when the person flips it; {@link #show} sets it from the settings without
 * running that, so loading a page never writes back the value it just read.
 */
public final class SettingSwitch extends ToggleSwitch {

    private @Nullable Runnable onChange;
    private boolean quiet;

    public SettingSwitch(String accessibleName) {
        getStyleClass().add("setting-switch");
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        setAccessibleText(accessibleName);
        setFocusTraversable(true);
        addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (isToggleKey(e.getCode(), e.isShortcutDown() || e.isAltDown() || e.isControlDown() || e.isMetaDown())) {
                fire();
                e.consume();
            }
        });
        selectedProperty().addListener((o, was, now) -> {
            notifyAccessibleAttributeChanged(AccessibleAttribute.SELECTED);
            Runnable action = onChange;
            if (!quiet && action != null) action.run();
        });
    }

    /** What a flip by the person does: the setting's write. */
    public void setOnChange(Runnable action) {
        onChange = action;
    }

    /** Shows {@code on} without running the change action: the page loading its value. */
    public void show(boolean on) {
        quiet = true;
        try {
            setSelected(on);
        } finally {
            quiet = false;
        }
    }

    /** Flips the switch as a click would, unless it is disabled. */
    public void fire() {
        if (!isDisabled()) setSelected(!isSelected());
    }

    /** Whether a key press flips the switch: an unmodified Space or Enter. Pure. */
    static boolean isToggleKey(KeyCode code, boolean modified) {
        return !modified && (code == KeyCode.SPACE || code == KeyCode.ENTER);
    }

    @Override
    public @Nullable Object queryAccessibleAttribute(AccessibleAttribute attribute, Object... parameters) {
        if (attribute == AccessibleAttribute.SELECTED) return isSelected();
        return super.queryAccessibleAttribute(attribute, parameters);
    }

    @Override
    public void executeAccessibleAction(AccessibleAction action, Object... parameters) {
        if (action == AccessibleAction.FIRE) fire();
        else super.executeAccessibleAction(action, parameters);
    }
}
