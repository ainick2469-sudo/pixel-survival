package io.github.ainick2469.pixelsurvival.rendering.world;

public record ChunkRuntimeMetrics(
        int loadedChunkCount,
        int renderedChunkCount,
        int renderedFarRegionCount,
        int renderedSectionCount,
        int simulatedChunkCount,
        int pendingLoadCount,
        int pendingMeshBuildCount,
        int renderedFaceCount,
        long estimatedLoadedChunkStorageBytes) {
    public static ChunkRuntimeMetrics empty() {
        return new ChunkRuntimeMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0L);
    }
}
