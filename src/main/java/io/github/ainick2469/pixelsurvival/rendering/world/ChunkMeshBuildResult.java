package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import java.util.Map;

public record ChunkMeshBuildResult(
        ChunkCoord chunkCoord,
        Map<TerrainMaterialKey, ChunkMeshSectionData> sections,
        int visibleBlockCount,
        int faceCount) {
    public boolean isEmpty() {
        return sections.isEmpty();
    }
}
