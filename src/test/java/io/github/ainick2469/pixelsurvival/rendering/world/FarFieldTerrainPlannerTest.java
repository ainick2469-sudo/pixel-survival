package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarFieldTerrainPlannerTest {
    @Test
    void reducesDetailedChunkTargetsAtFortyEightAndNinetySixChunks() {
        ChunkVisibilityPlanner visibilityPlanner = new ChunkVisibilityPlanner();
        ChunkCoord centerChunk = new ChunkCoord(0, 0);

        ChunkRuntimeConfig fortyEightConfig = new ChunkRuntimeConfig(51, 48, 4);
        FarFieldTerrainSettings fortyEightSettings = FarFieldTerrainSettings.from(fortyEightConfig);
        int legacyFortyEightTargets = visibilityPlanner.plan(centerChunk, fortyEightConfig).renderTargets().size();
        int farFieldFortyEightTargets = visibilityPlanner
                .plan(centerChunk, fortyEightSettings.detailedChunkRuntimeConfig(fortyEightConfig))
                .renderTargets()
                .size();

        assertEquals(32, fortyEightSettings.detailedRenderRadiusChunks());
        assertTrue(farFieldFortyEightTargets < legacyFortyEightTargets / 2);

        ChunkRuntimeConfig ninetySixConfig = new ChunkRuntimeConfig(100, 96, 4);
        FarFieldTerrainSettings ninetySixSettings = FarFieldTerrainSettings.from(ninetySixConfig);
        int legacyNinetySixTargets = visibilityPlanner.plan(centerChunk, ninetySixConfig).renderTargets().size();
        int farFieldNinetySixTargets = visibilityPlanner
                .plan(centerChunk, ninetySixSettings.detailedChunkRuntimeConfig(ninetySixConfig))
                .renderTargets()
                .size();

        assertEquals(62, ninetySixSettings.detailedRenderRadiusChunks());
        assertTrue(farFieldNinetySixTargets < legacyNinetySixTargets / 2);
    }

    @Test
    void plansOuterRingRegionsWithoutReusingNearChunkTargets() {
        FarFieldTerrainPlanner planner = new FarFieldTerrainPlanner();
        FarFieldTerrainSettings settings = FarFieldTerrainSettings.from(new ChunkRuntimeConfig(51, 48, 4));

        var targets = planner.plan(new ChunkCoord(0, 0), settings);

        assertFalse(targets.isEmpty());
        assertFalse(targets.contains(new FarFieldTerrainRegionCoord(0, 0)));
    }

    @Test
    void keepsTheFarFieldAnchorStableUntilTheCameraCrossesTheRegionMidpoint() {
        FarFieldTerrainSettings settings = FarFieldTerrainSettings.from(new ChunkRuntimeConfig(51, 48, 4));
        ChunkCoord initialAnchor = FarFieldTerrainRenderer.resolveAnchorChunk(
                new ChunkCoord(0, 0), settings, null, null);

        ChunkCoord sameAnchor = FarFieldTerrainRenderer.resolveAnchorChunk(
                new ChunkCoord(8, 0), settings, initialAnchor, settings);
        ChunkCoord shiftedAnchor = FarFieldTerrainRenderer.resolveAnchorChunk(
                new ChunkCoord(9, 0), settings, initialAnchor, settings);

        assertEquals(initialAnchor, sameAnchor);
        assertEquals(new ChunkCoord(12, 4), shiftedAnchor);
    }
}
