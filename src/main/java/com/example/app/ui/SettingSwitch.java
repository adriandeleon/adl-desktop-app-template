package com.example.app.ui;

import javafx.scene.AccessibleRole;
import javafx.scene.control.ToggleButton;

import com.example.app.i18n.Messages;

/**
 * An on/off switch operable from the keyboard (Space/Enter) with the {@code TOGGLE_BUTTON} role. A
 * bare AtlantaFX {@code ToggleSwitch} is mouse-only.
 */
public final class SettingSwitch extends ToggleButton {

    public SettingSwitch(String accessibleName) {
        getStyleClass().add("setting-switch");
        setAccessibleRole(AccessibleRole.TOGGLE_BUTTON);
        setAccessibleText(accessibleName);
        selectedProperty().addListener((obs, old, on) -> label());
        label();
    }

    private void label() {
        setText(Messages.tr(isSelected() ? "switch.on" : "switch.off"));
    }
}
