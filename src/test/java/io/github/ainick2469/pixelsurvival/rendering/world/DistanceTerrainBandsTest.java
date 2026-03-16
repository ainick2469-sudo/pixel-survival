package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistanceTerrainBandsTest {
    @Test
    void keepsSingleOuterBandAtNinetySixAndBelow() {
        ChunkRuntimeConfig runtimeConfig = new ChunkRuntimeConfig(100, 96, 4);

        DistanceTerrainBands bands = DistanceTerrainBands.from(runtimeConfig);

        assertNull(bands.middleTerrainSettings());
        assertNotNull(bands.farTerrainSettings());
        assertEquals(62, bands.detailedChunkRuntimeConfig().renderRadius());
        assertEquals(66, bands.detailedChunkRuntimeConfig().loadRadius());
    }

    @Test
    void introducesMiddleDistanceBandAndShrinksDetailedChunkRadiusAtOneNinetyTwo() {
        ChunkVisibilityPlanner visibilityPlanner = new ChunkVisibilityPlanner();
        ChunkCoord centerChunk = new ChunkCoord(0, 0);
        ChunkRuntimeConfig runtimeConfig = new ChunkRuntimeConfig(196, 192, 4);
        FarFieldTerrainSettings legacyFarFieldSettings = FarFieldTerrainSettings.from(runtimeConfig);

        DistanceTerrainBands bands = DistanceTerrainBands.from(runtimeConfig);

        assertNotNull(bands.middleTerrainSettings());
        assertNotNull(bands.farTerrainSettings());
        assertEquals(42, bands.detailedChunkRuntimeConfig().renderRadius());
        assertEquals(46, bands.detailedChunkRuntimeConfig().loadRadius());
        assertEquals(34, bands.middleTerrainSettings().startRadiusChunks());
        assertEquals(96, bands.middleTerrainSettings().endRadiusChunks());
        assertEquals(8, bands.middleTerrainSettings().regionSpanChunks());
        assertEquals(8, bands.middleTerrainSettings().cellSizeBlocks());
        assertEquals(80, bands.farTerrainSettings().startRadiusChunks());
        assertEquals(192, bands.farTerrainSettings().endRadiusChunks());
        assertEquals(16, bands.farTerrainSettings().regionSpanChunks());
        assertEquals(16, bands.farTerrainSettings().cellSizeBlocks());

        int legacyDetailedTargets = visibilityPlanner
                .plan(centerChunk, legacyFarFieldSettings.detailedChunkRuntimeConfig(runtimeConfig))
                .renderTargets()
                .size();
        int middleBandDetailedTargets = visibilityPlanner
                .plan(centerChunk, bands.detailedChunkRuntimeConfig())
                .renderTargets()
                .size();
        assertTrue(middleBandDetailedTargets < legacyDetailedTargets);
    }
}
