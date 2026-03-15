package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public final class HeightmapWorldGenerator implements WorldGenerator {
    private static final int WORLD_SEED = 2469;
    private static final int BASE_HEIGHT = 22;
    private static final int MIN_SURFACE_HEIGHT = 14;
    private static final int MAX_SURFACE_HEIGHT = 40;
    private static final int DIRT_LAYER_DEPTH = 3;

    private static final BlockId AIR = BlockId.of("pixel_survival:air");
    private static final BlockId DIRT = BlockId.of("pixel_survival:dirt");
    private static final BlockId STONE = BlockId.of("pixel_survival:stone");
    private static final BlockId GRASS_BLOCK = BlockId.of("pixel_survival:grass_block");

    @Override
    public ChunkData generateChunk(ChunkCoord chunkCoord, GameRegistries registries) {
        registries.requireBlockDefinition(AIR);
        registries.requireBlockDefinition(DIRT);
        registries.requireBlockDefinition(STONE);
        registries.requireBlockDefinition(GRASS_BLOCK);

        ChunkData chunkData = new ChunkData(chunkCoord, AIR);
        for (int localX = 0; localX < ChunkData.SIZE_X; localX++) {
            for (int localZ = 0; localZ < ChunkData.SIZE_Z; localZ++) {
                int worldX = chunkData.toWorldX(localX);
                int worldZ = chunkData.toWorldZ(localZ);
                int surfaceHeight = sampleSurfaceHeight(worldX, worldZ);
                int stoneTopY = Math.max(0, surfaceHeight - DIRT_LAYER_DEPTH - 1);
                int dirtStartY = stoneTopY + 1;

                for (int y = 0; y <= stoneTopY; y++) {
                    chunkData.setBlock(localX, y, localZ, STONE);
                }
                for (int y = dirtStartY; y < surfaceHeight; y++) {
                    chunkData.setBlock(localX, y, localZ, DIRT);
                }
                chunkData.setBlock(localX, surfaceHeight, localZ, GRASS_BLOCK);
            }
        }
        return chunkData;
    }

    public int sampleSurfaceHeight(int worldX, int worldZ) {
        double broadNoise = sampleValueNoise(worldX * 0.045, worldZ * 0.045, WORLD_SEED) * 8.0;
        double ridgeNoise = sampleValueNoise(worldX * 0.022, worldZ * 0.022, WORLD_SEED + 101) * 11.0;
        double detailNoise = sampleValueNoise(worldX * 0.12, worldZ * 0.12, WORLD_SEED + 202) * 2.5;

        int height = (int) Math.round(BASE_HEIGHT + broadNoise + ridgeNoise + detailNoise);
        return Math.max(MIN_SURFACE_HEIGHT, Math.min(MAX_SURFACE_HEIGHT, height));
    }

    private double sampleValueNoise(double sampleX, double sampleZ, int seed) {
        int x0 = (int) Math.floor(sampleX);
        int z0 = (int) Math.floor(sampleZ);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double tx = smoothStep(sampleX - x0);
        double tz = smoothStep(sampleZ - z0);

        double v00 = latticeValue(x0, z0, seed);
        double v10 = latticeValue(x1, z0, seed);
        double v01 = latticeValue(x0, z1, seed);
        double v11 = latticeValue(x1, z1, seed);

        double blendX0 = lerp(v00, v10, tx);
        double blendX1 = lerp(v01, v11, tx);
        return lerp(blendX0, blendX1, tz);
    }

    private double latticeValue(int x, int z, int seed) {
        long hash = 1469598103934665603L;
        hash ^= x * 0x9E3779B97F4A7C15L;
        hash *= 1099511628211L;
        hash ^= z * 0xC2B2AE3D27D4EB4FL;
        hash *= 1099511628211L;
        hash ^= seed * 0x165667B19E3779F9L;
        hash *= 1099511628211L;

        long positive = hash & 0x7fffffffffffffffL;
        return (positive / (double) Long.MAX_VALUE) * 2.0 - 1.0;
    }

    private double smoothStep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
