package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkRenderManagerPriorityTest {
    @Test
    void classifiesHighDistanceChunkWorkIntoCoreSeamPromotionAndBufferBands() {
        DistanceTerrainBands bands = DistanceTerrainBands.from(new ChunkRuntimeConfig(196, 192, 4));
        ChunkRuntimeConfig detailedRuntimeConfig = bands.detailedChunkRuntimeConfig();
        FarFieldTerrainSettings farFieldSettings = bands.transitionTerrainSettings();
        ChunkCoord centerChunk = new ChunkCoord(0, 0);

        assertEquals(
                ChunkRenderManager.ChunkWorkBand.CORE,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(20, 0), detailedRuntimeConfig, farFieldSettings, true));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.SEAM,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(30, 0), detailedRuntimeConfig, farFieldSettings, true));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.SEAM,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(42, 0), detailedRuntimeConfig, farFieldSettings, true));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.BUFFER,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(44, 0), detailedRuntimeConfig, farFieldSettings, true));
    }

    @Test
    void fallsBackToCoreAndBufferBandsWhenUltraDistancePhasingIsDisabled() {
        ChunkRuntimeConfig detailedRuntimeConfig = new ChunkRuntimeConfig(66, 62, 4);
        FarFieldTerrainSettings farFieldSettings = FarFieldTerrainSettings.from(new ChunkRuntimeConfig(100, 96, 4));
        ChunkCoord centerChunk = new ChunkCoord(0, 0);

        assertEquals(
                ChunkRenderManager.ChunkWorkBand.CORE,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(20, 0), detailedRuntimeConfig, farFieldSettings, false));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.CORE,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(62, 0), detailedRuntimeConfig, farFieldSettings, false));
        assertEquals(
                ChunkRenderManager.ChunkWorkBand.BUFFER,
                ChunkRenderManager.classifyChunkWorkBand(centerChunk, new ChunkCoord(64, 0), detailedRuntimeConfig, farFieldSettings, false));
    }

    @Test
    void classifiesForwardLateralAndRearTraversalLanes() {
        ChunkCoord centerChunk = new ChunkCoord(0, 0);
        Vector3f priorityDirection = new Vector3f(1f, 0f, 0f);

        assertEquals(
                ChunkRenderManager.ChunkTraversalLane.FORWARD,
                ChunkRenderManager.classifyTraversalLane(centerChunk, new ChunkCoord(10, 2), priorityDirection));
        assertEquals(
                ChunkRenderManager.ChunkTraversalLane.LATERAL,
                ChunkRenderManager.classifyTraversalLane(centerChunk, new ChunkCoord(0, 12), priorityDirection));
        assertEquals(
                ChunkRenderManager.ChunkTraversalLane.REAR,
                ChunkRenderManager.classifyTraversalLane(centerChunk, new ChunkCoord(-8, 0), priorityDirection));
    }

    @Test
    void lowersGovernorScaleMoreAggressivelyAtUltraDistance() {
        ChunkRuntimeConfig ultraDistanceConfig = new ChunkRuntimeConfig(196, 192, 4);
        ChunkRuntimeConfig standardDistanceConfig = new ChunkRuntimeConfig(52, 48, 4);

        assertEquals(1f, ChunkRenderManager.targetFrameGovernorScale(ultraDistanceConfig, 3.5f));
        assertEquals(0f, ChunkRenderManager.targetFrameGovernorScale(ultraDistanceConfig, 10.0f));
        assertEquals(1f, ChunkRenderManager.targetFrameGovernorScale(standardDistanceConfig, 6.0f));
        assertTrue(
                ChunkRenderManager.targetFrameGovernorScale(ultraDistanceConfig, 7.0f)
                        < ChunkRenderManager.targetFrameGovernorScale(standardDistanceConfig, 7.0f));
    }

    @Test
    void delaysStillStatePromotionCatchUpUntilAfterReleaseWindow() {
        long lastMovementNanos = 10_000_000_000L;
        long beforeReleaseNanos = lastMovementNanos + 2_000_000_000L;
        long midRampNanos = lastMovementNanos + 6_500_000_000L;
        long afterRampNanos = lastMovementNanos + 12_000_000_000L;

        assertEquals(
                0f,
                ChunkRenderManager.targetPromotionCatchUpScale(
                        ChunkMotionProfile.MOVING, beforeReleaseNanos, lastMovementNanos));
        assertEquals(
                0f,
                ChunkRenderManager.targetPromotionCatchUpScale(
                        ChunkMotionProfile.SETTLING, beforeReleaseNanos, lastMovementNanos));
        assertEquals(
                0f,
                ChunkRenderManager.targetPromotionCatchUpScale(
                        ChunkMotionProfile.STILL, beforeReleaseNanos, lastMovementNanos));
        assertTrue(
                ChunkRenderManager.targetPromotionCatchUpScale(
                                ChunkMotionProfile.STILL, midRampNanos, lastMovementNanos)
                        > 0f);
        assertEquals(
                1f,
                ChunkRenderManager.targetPromotionCatchUpScale(
                        ChunkMotionProfile.STILL, afterRampNanos, lastMovementNanos));
    }
}
