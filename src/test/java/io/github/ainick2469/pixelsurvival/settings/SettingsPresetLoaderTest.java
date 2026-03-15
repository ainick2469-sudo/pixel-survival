package io.github.ainick2469.pixelsurvival.settings;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsPresetLoaderTest {
    @Test
    void loadsAllRequiredSurvivalPresets() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));

        assertEquals(4, registries.survivalPresets().size());
        assertTrue(registries.survivalPresets().containsKey("relaxed"));
        assertTrue(registries.survivalPresets().containsKey("standard"));
        assertTrue(registries.survivalPresets().containsKey("harsh"));
        assertTrue(registries.survivalPresets().containsKey("brutal"));
    }
}
