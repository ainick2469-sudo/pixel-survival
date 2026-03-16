package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public record FarFieldTerrainSettings(
        int startRadiusChunks,
        int endRadiusChunks,
        int detailedRenderRadiusChunks,
        int detailedLoadRadiusChunks,
        int overlapChunks,
        int regionSpanChunks,
        int cellSizeBlocks,
        int anchorHysteresisChunks,
        int skirtFloorY,
        float verticalBiasBlocks) {
    private static final int MIN_FAR_FIELD_RENDER_RADIUS = 40;
    private static final int MIN_START_RADIUS_CHUNKS = 24;
    private static final int START_RADIUS_NUMERATOR = 5;
    private static final int START_RADIUS_DENOMINATOR = 8;
    private static final int OVERLAP_CHUNKS = 2;
    private static final int REGION_SPAN_CHUNKS = 8;
    private static final int CELL_SIZE_BLOCKS = 8;
    private static final float VERTICAL_BIAS_BLOCKS = 0.35f;

    public FarFieldTerrainSettings {
        if (startRadiusChunks < 0) {
            throw new IllegalArgumentException("startRadiusChunks must be at least 0");
        }
        if (endRadiusChunks < startRadiusChunks) {
            throw new IllegalArgumentException("endRadiusChunks must be greater than or equal to startRadiusChunks");
        }
        if (detailedRenderRadiusChunks < startRadiusChunks) {
            throw new IllegalArgumentException("detailedRenderRadiusChunks must cover the overlap seam");
        }
        if (detailedLoadRadiusChunks < detailedRenderRadiusChunks + 1) {
            throw new IllegalArgumentException("detailedLoadRadiusChunks must buffer detailedRenderRadiusChunks");
        }
        if (regionSpanChunks < 1) {
            throw new IllegalArgumentException("regionSpanChunks must be at least 1");
        }
        if (cellSizeBlocks < 1) {
            throw new IllegalArgumentException("cellSizeBlocks must be at least 1");
        }
        if (regionSpanBlocks() % cellSizeBlocks != 0) {
            throw new IllegalArgumentException("Far-field region span must be divisible by the cell size.");
        }
    }

    public static FarFieldTerrainSettings from(ChunkRuntimeConfig runtimeConfig) {
        if (runtimeConfig.renderRadius() < MIN_FAR_FIELD_RENDER_RADIUS) {
            return null;
        }

        int loadBufferChunks = Math.max(1, runtimeConfig.loadRadius() - runtimeConfig.renderRadius());
        int startRadiusChunks = Math.min(
                runtimeConfig.renderRadius() - OVERLAP_CHUNKS,
                Math.max(
                        MIN_START_RADIUS_CHUNKS,
                        (runtimeConfig.renderRadius() * START_RADIUS_NUMERATOR) / START_RADIUS_DENOMINATOR));
        int detailedRenderRadiusChunks = Math.min(runtimeConfig.renderRadius(), startRadiusChunks + OVERLAP_CHUNKS);
        int detailedLoadRadiusChunks = detailedRenderRadiusChunks + loadBufferChunks;

        return new FarFieldTerrainSettings(
                startRadiusChunks,
                runtimeConfig.renderRadius(),
                detailedRenderRadiusChunks,
                detailedLoadRadiusChunks,
                OVERLAP_CHUNKS,
                REGION_SPAN_CHUNKS,
                CELL_SIZE_BLOCKS,
                REGION_SPAN_CHUNKS / 2,
                0,
                VERTICAL_BIAS_BLOCKS);
    }

    public ChunkRuntimeConfig detailedChunkRuntimeConfig(ChunkRuntimeConfig runtimeConfig) {
        return new ChunkRuntimeConfig(
                detailedLoadRadiusChunks,
                detailedRenderRadiusChunks,
                Math.min(runtimeConfig.simulationRadius(), detailedRenderRadiusChunks));
    }

    public int regionSpanBlocks() {
        return regionSpanChunks * ChunkData.SIZE_X;
    }

    public int cellsPerRegionAxis() {
        return regionSpanBlocks() / cellSizeBlocks;
    }
}
