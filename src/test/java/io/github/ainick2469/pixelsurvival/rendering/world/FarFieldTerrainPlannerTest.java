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
        assertEquals(24, fortyEightSettings.startRadiusChunks());
        assertTrue(fortyEightSettings.overlapChunks() > fortyEightSettings.anchorHysteresisChunks());
        assertTrue(farFieldFortyEightTargets < legacyFortyEightTargets / 2);

        ChunkRuntimeConfig ninetySixConfig = new ChunkRuntimeConfig(100, 96, 4);
        FarFieldTerrainSettings ninetySixSettings = FarFieldTerrainSettings.from(ninetySixConfig);
        int legacyNinetySixTargets = visibilityPlanner.plan(centerChunk, ninetySixConfig).renderTargets().size();
        int farFieldNinetySixTargets = visibilityPlanner
                .plan(centerChunk, ninetySixSettings.detailedChunkRuntimeConfig(ninetySixConfig))
                .renderTargets()
                .size();

        assertEquals(62, ninetySixSettings.detailedRenderRadiusChunks());
        assertEquals(54, ninetySixSettings.startRadiusChunks());
        assertTrue(ninetySixSettings.overlapChunks() > ninetySixSettings.anchorHysteresisChunks());
        assertTrue(farFieldNinetySixTargets < legacyNinetySixTargets / 2);

        ChunkRuntimeConfig oneNinetyTwoConfig = new ChunkRuntimeConfig(196, 192, 4);
        FarFieldTerrainSettings oneNinetyTwoSettings = FarFieldTerrainSettings.from(oneNinetyTwoConfig);
        int legacyOneNinetyTwoTargets = visibilityPlanner.plan(centerChunk, oneNinetyTwoConfig).renderTargets().size();
        int farFieldOneNinetyTwoTargets = visibilityPlanner
                .plan(centerChunk, oneNinetyTwoSettings.detailedChunkRuntimeConfig(oneNinetyTwoConfig))
                .renderTargets()
                .size();

        assertEquals(54, oneNinetyTwoSettings.detailedRenderRadiusChunks());
        assertEquals(38, oneNinetyTwoSettings.startRadiusChunks());
        assertEquals(16, oneNinetyTwoSettings.regionSpanChunks());
        assertEquals(16, oneNinetyTwoSettings.cellSizeBlocks());
        assertTrue(oneNinetyTwoSettings.overlapChunks() > oneNinetyTwoSettings.anchorHysteresisChunks());
        assertTrue(farFieldOneNinetyTwoTargets < legacyOneNinetyTwoTargets / 4);
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
    void keepsTheFarFieldAnchorStableUntilTheCameraCrossesTheInnerSeamSafetyBand() {
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
