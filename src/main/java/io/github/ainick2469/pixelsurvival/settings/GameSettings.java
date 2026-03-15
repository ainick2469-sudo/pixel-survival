package io.github.ainick2469.pixelsurvival.settings;

import java.util.Map;
import java.util.Objects;

public record GameSettings(String activeSurvivalPresetId, SurvivalSettings survivalSettings) {
    public GameSettings {
        Objects.requireNonNull(activeSurvivalPresetId, "activeSurvivalPresetId");
        Objects.requireNonNull(survivalSettings, "survivalSettings");
    }

    public static GameSettings defaultSettings(Map<String, SurvivalSettings> survivalPresets) {
        SurvivalSettings standardPreset = survivalPresets.get("standard");
        if (standardPreset == null) {
            throw new IllegalStateException("Missing standard survival settings preset");
        }
        return new GameSettings("standard", standardPreset);
    }
}
