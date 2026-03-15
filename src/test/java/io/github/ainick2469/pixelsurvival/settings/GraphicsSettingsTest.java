package io.github.ainick2469.pixelsurvival.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphicsSettingsTest {
    @Test
    void defaultsToFarButReasonableRenderDistance() {
        GraphicsSettings graphicsSettings = GraphicsSettings.defaults();

        assertEquals(12, graphicsSettings.renderDistanceChunks());
        assertEquals(15, graphicsSettings.toChunkRuntimeConfig().loadRadius());
        assertEquals(12, graphicsSettings.toChunkRuntimeConfig().renderRadius());
        assertEquals(3, graphicsSettings.toChunkRuntimeConfig().simulationRadius());
    }

    @Test
    void rejectsOutOfRangeRenderDistance() {
        assertThrows(IllegalArgumentException.class, () -> new GraphicsSettings(1));
        assertThrows(IllegalArgumentException.class, () -> new GraphicsSettings(49));
    }

    @Test
    void clampsRequestedRenderDistanceChanges() {
        GraphicsSettings graphicsSettings = GraphicsSettings.defaults();

        assertEquals(48, graphicsSettings.withRenderDistanceChunks(64).renderDistanceChunks());
        assertEquals(2, graphicsSettings.withRenderDistanceChunks(-4).renderDistanceChunks());
    }
}
