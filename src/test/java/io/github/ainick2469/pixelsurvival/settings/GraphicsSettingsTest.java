package io.github.ainick2469.pixelsurvival.settings;

import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRuntimeConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GraphicsSettingsTest {
    @Test
    void clampsRenderDistanceToTheExperimentalOneHundredNinetyTwoChunkCap() {
        assertEquals(192, GraphicsSettings.clampRenderDistance(999));
        assertEquals(2, GraphicsSettings.clampRenderDistance(-50));
        assertEquals(new ChunkRuntimeConfig(196, 192, 4), new GraphicsSettings(192).toChunkRuntimeConfig());
    }

    @Test
    void defaultsForConfiguredRenderDistanceClampsInvalidAndOutOfRangeValues() {
        assertEquals(new GraphicsSettings(96), GraphicsSettings.defaultsForConfiguredRenderDistance("96"));
        assertEquals(new GraphicsSettings(192), GraphicsSettings.defaultsForConfiguredRenderDistance("999"));
        assertEquals(GraphicsSettings.defaults(), GraphicsSettings.defaultsForConfiguredRenderDistance("bad-input"));
        assertEquals(GraphicsSettings.defaults(), GraphicsSettings.defaultsForConfiguredRenderDistance(" "));
    }
}
