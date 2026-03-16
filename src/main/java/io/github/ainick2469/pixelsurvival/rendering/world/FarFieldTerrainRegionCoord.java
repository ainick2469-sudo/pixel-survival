package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public record FarFieldTerrainRegionCoord(int x, int z) {
    public int startChunkX(FarFieldTerrainSettings settings) {
        return x * settings.regionSpanChunks();
    }

    public int startChunkZ(FarFieldTerrainSettings settings) {
        return z * settings.regionSpanChunks();
    }

    public int endChunkX(FarFieldTerrainSettings settings) {
        return startChunkX(settings) + settings.regionSpanChunks() - 1;
    }

    public int endChunkZ(FarFieldTerrainSettings settings) {
        return startChunkZ(settings) + settings.regionSpanChunks() - 1;
    }

    public int worldStartX(FarFieldTerrainSettings settings) {
        return startChunkX(settings) * ChunkData.SIZE_X;
    }

    public int worldStartZ(FarFieldTerrainSettings settings) {
        return startChunkZ(settings) * ChunkData.SIZE_Z;
    }
}
