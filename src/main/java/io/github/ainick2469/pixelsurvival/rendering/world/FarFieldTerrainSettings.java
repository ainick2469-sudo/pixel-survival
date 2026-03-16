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
        float verticalBiasBlocks,
        boolean renderInnerBoundarySkirts) {
    private static final int MIN_FAR_FIELD_RENDER_RADIUS = 40;
    private static final int MIN_START_RADIUS_CHUNKS = 24;
    private static final int STANDARD_DETAIL_RADIUS_NUMERATOR = 5;
    private static final int STANDARD_DETAIL_RADIUS_DENOMINATOR = 8;
    private static final int STANDARD_DETAIL_RADIUS_BONUS = 2;
    private static final int STANDARD_REGION_SPAN_CHUNKS = 8;
    private static final int STANDARD_CELL_SIZE_BLOCKS = 8;
    private static final int STANDARD_OVERLAP_CHUNKS = STANDARD_REGION_SPAN_CHUNKS;
    private static final int ULTRA_REGION_SPAN_CHUNKS = 16;
    private static final int ULTRA_CELL_SIZE_BLOCKS = 16;
    private static final int ULTRA_OVERLAP_CHUNKS = ULTRA_REGION_SPAN_CHUNKS;
    private static final int ULTRA_DETAIL_RADIUS_BASELINE = 48;
    private static final int ULTRA_DETAIL_RADIUS_GROWTH_STEP_CHUNKS = 16;
    private static final int MAX_ULTRA_DETAILED_RENDER_RADIUS_CHUNKS = 56;
    private static final int ULTRA_RENDER_DISTANCE_THRESHOLD = 96;
    private static final float VERTICAL_BIAS_BLOCKS = 0.08f;

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

        boolean ultraDistance = runtimeConfig.renderRadius() > ULTRA_RENDER_DISTANCE_THRESHOLD;
        int regionSpanChunks = ultraDistance ? ULTRA_REGION_SPAN_CHUNKS : STANDARD_REGION_SPAN_CHUNKS;
        int cellSizeBlocks = ultraDistance ? ULTRA_CELL_SIZE_BLOCKS : STANDARD_CELL_SIZE_BLOCKS;
        int overlapChunks = ultraDistance ? ULTRA_OVERLAP_CHUNKS : STANDARD_OVERLAP_CHUNKS;
        int anchorHysteresisChunks = Math.max(2, overlapChunks / 2);
        int loadBufferChunks = Math.max(1, runtimeConfig.loadRadius() - runtimeConfig.renderRadius());
        int detailedRenderRadiusChunks = ultraDistance
                ? Math.min(
                        runtimeConfig.renderRadius(),
                        Math.min(
                                MAX_ULTRA_DETAILED_RENDER_RADIUS_CHUNKS,
                                ULTRA_DETAIL_RADIUS_BASELINE
                                        + ((runtimeConfig.renderRadius() - ULTRA_RENDER_DISTANCE_THRESHOLD)
                                                / ULTRA_DETAIL_RADIUS_GROWTH_STEP_CHUNKS)))
                : Math.min(
                        runtimeConfig.renderRadius(),
                        Math.max(
                                        MIN_START_RADIUS_CHUNKS,
                                        (runtimeConfig.renderRadius() * STANDARD_DETAIL_RADIUS_NUMERATOR)
                                                / STANDARD_DETAIL_RADIUS_DENOMINATOR)
                                + STANDARD_DETAIL_RADIUS_BONUS);
        int startRadiusChunks = Math.max(MIN_START_RADIUS_CHUNKS, detailedRenderRadiusChunks - overlapChunks);
        int detailedLoadRadiusChunks = detailedRenderRadiusChunks
                + (ultraDistance ? Math.max(loadBufferChunks, 4) : Math.max(loadBufferChunks, overlapChunks / 2));

        return new FarFieldTerrainSettings(
                startRadiusChunks,
                runtimeConfig.renderRadius(),
                detailedRenderRadiusChunks,
                detailedLoadRadiusChunks,
                overlapChunks,
                regionSpanChunks,
                cellSizeBlocks,
                anchorHysteresisChunks,
                0,
                VERTICAL_BIAS_BLOCKS,
                true);
    }

    public static FarFieldTerrainSettings visualOnlyBand(
            int startRadiusChunks,
            int endRadiusChunks,
            int overlapChunks,
            int regionSpanChunks,
            int cellSizeBlocks,
            int anchorHysteresisChunks) {
        int detailedRenderRadiusChunks = Math.max(startRadiusChunks, 1);
        int detailedLoadRadiusChunks = Math.max(detailedRenderRadiusChunks + 1, detailedRenderRadiusChunks + Math.max(1, overlapChunks / 2));
        return new FarFieldTerrainSettings(
                startRadiusChunks,
                endRadiusChunks,
                detailedRenderRadiusChunks,
                detailedLoadRadiusChunks,
                overlapChunks,
                regionSpanChunks,
                cellSizeBlocks,
                anchorHysteresisChunks,
                0,
                VERTICAL_BIAS_BLOCKS,
                false);
    }

    public ChunkRuntimeConfig detailedChunkRuntimeConfig(ChunkRuntimeConfig runtimeConfig) {
        return new ChunkRuntimeConfig(
                detailedLoadRadiusChunks,
                detailedRenderRadiusChunks,
                Math.min(runtimeConfig.simulationRadius(), detailedRenderRadiusChunks));
    }

    public FarFieldTerrainSettings withStartRadiusChunks(int updatedStartRadiusChunks) {
        return new FarFieldTerrainSettings(
                updatedStartRadiusChunks,
                endRadiusChunks,
                detailedRenderRadiusChunks,
                detailedLoadRadiusChunks,
                overlapChunks,
                regionSpanChunks,
                cellSizeBlocks,
                anchorHysteresisChunks,
                skirtFloorY,
                verticalBiasBlocks,
                renderInnerBoundarySkirts);
    }

    public FarFieldTerrainSettings withRenderInnerBoundarySkirts(boolean updatedRenderInnerBoundarySkirts) {
        return new FarFieldTerrainSettings(
                startRadiusChunks,
                endRadiusChunks,
                detailedRenderRadiusChunks,
                detailedLoadRadiusChunks,
                overlapChunks,
                regionSpanChunks,
                cellSizeBlocks,
                anchorHysteresisChunks,
                skirtFloorY,
                verticalBiasBlocks,
                updatedRenderInnerBoundarySkirts);
    }

    public int regionSpanBlocks() {
        return regionSpanChunks * ChunkData.SIZE_X;
    }

    public int cellsPerRegionAxis() {
        return regionSpanBlocks() / cellSizeBlocks;
    }
}
