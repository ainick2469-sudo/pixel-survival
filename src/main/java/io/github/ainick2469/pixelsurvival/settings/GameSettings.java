package io.github.ainick2469.pixelsurvival.settings;

import java.util.Map;
import java.util.Objects;

public record GameSettings(
        String activeSurvivalPresetId,
        SurvivalSettings survivalSettings,
        GraphicsSettings graphicsSettings) {
    public GameSettings {
        Objects.requireNonNull(activeSurvivalPresetId, "activeSurvivalPresetId");
        Objects.requireNonNull(survivalSettings, "survivalSettings");
        Objects.requireNonNull(graphicsSettings, "graphicsSettings");
    }

    public static GameSettings defaultSettings(Map<String, SurvivalSettings> survivalPresets) {
        SurvivalSettings standardPreset = survivalPresets.get("standard");
        if (standardPreset == null) {
            throw new IllegalStateException("Missing standard survival settings preset");
        }
        return new GameSettings("standard", standardPreset, GraphicsSettings.defaults());
    }

    public GameSettings withGraphicsSettings(GraphicsSettings graphicsSettings) {
        return new GameSettings(activeSurvivalPresetId, survivalSettings, graphicsSettings);
    }
}
