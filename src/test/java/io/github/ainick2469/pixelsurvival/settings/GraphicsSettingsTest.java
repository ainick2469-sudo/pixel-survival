package io.github.ainick2469.pixelsurvival.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphicsSettingsTest {
    @Test
    void defaultsToFarButReasonableRenderDistance() {
        GraphicsSettings graphicsSettings = GraphicsSettings.defaults();

        assertEquals(8, graphicsSettings.renderDistanceChunks());
        assertEquals(10, graphicsSettings.toChunkRuntimeConfig().loadRadius());
        assertEquals(8, graphicsSettings.toChunkRuntimeConfig().renderRadius());
        assertEquals(4, graphicsSettings.toChunkRuntimeConfig().simulationRadius());
    }

    @Test
    void rejectsOutOfRangeRenderDistance() {
        assertThrows(IllegalArgumentException.class, () -> new GraphicsSettings(1));
        assertThrows(IllegalArgumentException.class, () -> new GraphicsSettings(25));
    }

    @Test
    void clampsRequestedRenderDistanceChanges() {
        GraphicsSettings graphicsSettings = GraphicsSettings.defaults();

        assertEquals(24, graphicsSettings.withRenderDistanceChunks(30).renderDistanceChunks());
        assertEquals(2, graphicsSettings.withRenderDistanceChunks(-4).renderDistanceChunks());
    }
}
