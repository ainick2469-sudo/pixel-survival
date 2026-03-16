package io.github.ainick2469.pixelsurvival.rendering.world;

public record ChunkRuntimeMetrics(
        int loadedChunkCount,
        int renderedChunkCount,
        int renderedFarRegionCount,
        int renderedSectionCount,
        int simulatedChunkCount,
        int pendingLoadCount,
        int pendingMeshBuildCount,
        int pendingFarRegionBuildCount,
        int renderedFaceCount,
        long estimatedLoadedChunkStorageBytes,
        int cachedMeshVariantCount,
        long estimatedCachedMeshStorageBytes,
        int rebuiltFarRegionCountLastWindow,
        int farAnchorSnapCountLastWindow,
        ChunkMotionProfile motionProfile,
        int frameGovernorPercent) {
    public static ChunkRuntimeMetrics empty() {
        return new ChunkRuntimeMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, 0, 0L, 0, 0, ChunkMotionProfile.STILL, 100);
    }
}
