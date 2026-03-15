package io.github.ainick2469.pixelsurvival.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GraphicsSettingsTest {
    @Test
    void defaultsToFarButReasonableRenderDistance() {
        GraphicsSettings graphicsSettings = GraphicsSettings.defaults();

        assertEquals(48, graphicsSettings.renderDistanceChunks());
        assertEquals(51, graphicsSettings.toChunkRuntimeConfig().loadRadius());
        assertEquals(48, graphicsSettings.toChunkRuntimeConfig().renderRadius());
        assertEquals(4, graphicsSettings.toChunkRuntimeConfig().simulationRadius());
    }

    @Test
    void rejectsOutOfRangeRenderDistance() {
        assertThrows(IllegalArgumentException.class, () -> new GraphicsSettings(1));
        assertThrows(IllegalArgumentException.class, () -> new GraphicsSettings(97));
    }

    @Test
    void clampsRequestedRenderDistanceChanges() {
        GraphicsSettings graphicsSettings = GraphicsSettings.defaults();

        assertEquals(96, graphicsSettings.withRenderDistanceChunks(128).renderDistanceChunks());
        assertEquals(2, graphicsSettings.withRenderDistanceChunks(-4).renderDistanceChunks());
    }

    @Test
    void keepsHighDistanceLoadBufferSmallerThanTheOldPrototypePath() {
        GraphicsSettings graphicsSettings = new GraphicsSettings(96);

        assertEquals(100, graphicsSettings.toChunkRuntimeConfig().loadRadius());
        assertEquals(96, graphicsSettings.toChunkRuntimeConfig().renderRadius());
    }
}
