package io.github.ainick2469.pixelsurvival.rendering.world;

public record ChunkRuntimeMetrics(
        int loadedChunkCount,
        int renderedChunkCount,
        int simulatedChunkCount,
        int pendingLoadCount,
        int pendingMeshBuildCount,
        int renderedFaceCount) {
    public static ChunkRuntimeMetrics empty() {
        return new ChunkRuntimeMetrics(0, 0, 0, 0, 0, 0);
    }
}
