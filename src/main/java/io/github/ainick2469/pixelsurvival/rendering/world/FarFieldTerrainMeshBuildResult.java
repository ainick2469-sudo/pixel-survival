package io.github.ainick2469.pixelsurvival.rendering.world;

import java.util.Map;

public record FarFieldTerrainMeshBuildResult(
        FarFieldTerrainRegionCoord regionCoord,
        Map<TerrainMaterialKey, ChunkMeshSectionData> sections,
        int visibleCellCount,
        int faceCount) {
}
