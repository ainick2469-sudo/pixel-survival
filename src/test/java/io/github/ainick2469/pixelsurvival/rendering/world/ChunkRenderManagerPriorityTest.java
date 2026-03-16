package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkRenderManagerPriorityTest {
    @Test
    void classifiesHighDistanceChunkWorkIntoCoreSeamPromotionAndBufferBands() {
        ChunkRuntimeConfig detailedRuntimeConfig = new ChunkRuntimeConfig(58, 54, 4);
        FarFieldTerrainSettings farFieldSettings = FarFieldTerrainSettings.from(new ChunkRuntimeConfig(196, 192, 4));
        ChunkCoord centerChunk = new ChunkCoord(0, 0);

        assertEquals(
                ChunkRenderManager.ChunkWorkBand.CORE,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(20, 0), detailedRuntimeConfig, farFieldSettings));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.SEAM,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(36, 0), detailedRuntimeConfig, farFieldSettings));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.PROMOTION,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(50, 0), detailedRuntimeConfig, farFieldSettings));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.BUFFER,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(57, 0), detailedRuntimeConfig, farFieldSettings));
    }

    @Test
    void fallsBackToCoreAndBufferBandsWhenUltraDistancePhasingIsDisabled() {
        ChunkRuntimeConfig detailedRuntimeConfig = new ChunkRuntimeConfig(66, 62, 4);
        FarFieldTerrainSettings farFieldSettings = FarFieldTerrainSettings.from(new ChunkRuntimeConfig(100, 96, 4));
        ChunkCoord centerChunk = new ChunkCoord(0, 0);

        assertEquals(
                ChunkRenderManager.ChunkWorkBand.CORE,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(20, 0), detailedRuntimeConfig, farFieldSettings));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.CORE,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(62, 0), detailedRuntimeConfig, farFieldSettings));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.BUFFER,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(64, 0), detailedRuntimeConfig, farFieldSettings));
    }
}
