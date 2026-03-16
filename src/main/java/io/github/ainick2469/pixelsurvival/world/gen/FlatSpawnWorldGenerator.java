package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public final class FlatSpawnWorldGenerator implements WorldGenerator, FarFieldTerrainSamplerProvider {
    public static final int SURFACE_HEIGHT = 24;

    private static final BlockId AIR = BlockId.of("pixel_survival:air");
    private static final BlockId DIRT = BlockId.of("pixel_survival:dirt");

    @Override
    public ChunkData generateChunk(ChunkCoord chunkCoord, GameRegistries registries) {
        registries.requireBlockDefinition(AIR);
        registries.requireBlockDefinition(DIRT);

        ChunkData chunkData = new ChunkData(chunkCoord, AIR);
        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int z = 0; z < ChunkData.SIZE_Z; z++) {
                for (int y = 0; y < SURFACE_HEIGHT; y++) {
                    chunkData.setBlock(x, y, z, DIRT);
                }
            }
        }
        return chunkData;
    }

    @Override
    public FarFieldTerrainSampler farFieldTerrainSampler() {
        return (worldX, worldZ) -> new FarFieldTerrainSampler.ColumnSample(SURFACE_HEIGHT - 1, DIRT);
    }
}
