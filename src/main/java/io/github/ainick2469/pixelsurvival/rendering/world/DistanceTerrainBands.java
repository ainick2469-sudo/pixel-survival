package io.github.ainick2469.pixelsurvival.rendering.world;

public record DistanceTerrainBands(
        ChunkRuntimeConfig detailedChunkRuntimeConfig,
        FarFieldTerrainSettings middleTerrainSettings,
        FarFieldTerrainSettings farTerrainSettings) {
    private static final int ULTRA_DISTANCE_THRESHOLD = 96;
    private static final int MIN_MIDDLE_START_RADIUS_CHUNKS = 24;
    private static final int MIDDLE_OVERLAP_CHUNKS = 8;
    private static final int MIDDLE_REGION_SPAN_CHUNKS = 8;
    private static final int MIDDLE_CELL_SIZE_BLOCKS = 8;
    private static final int MIDDLE_ANCHOR_HYSTERESIS_CHUNKS = 4;
    private static final int FAR_OVERLAP_CHUNKS = 16;
    private static final int FAR_REGION_SPAN_CHUNKS = 16;
    private static final int FAR_CELL_SIZE_BLOCKS = 16;
    private static final int FAR_ANCHOR_HYSTERESIS_CHUNKS = 8;
    private static final int MIDDLE_END_RADIUS_CHUNKS = 96;
    private static final int ULTRA_DETAILED_RADIUS_BASE = 34;
    private static final int ULTRA_DETAILED_RADIUS_GROWTH_STEP_CHUNKS = 24;
    private static final int ULTRA_DETAILED_RADIUS_GROWTH_AMOUNT = 2;
    private static final int MAX_ULTRA_DETAILED_RADIUS_CHUNKS = 42;

    public static DistanceTerrainBands from(ChunkRuntimeConfig runtimeConfig) {
        FarFieldTerrainSettings farFieldSettings = FarFieldTerrainSettings.from(runtimeConfig);
        if (farFieldSettings == null) {
            return new DistanceTerrainBands(runtimeConfig, null, null);
        }
        if (runtimeConfig.renderRadius() <= ULTRA_DISTANCE_THRESHOLD) {
            return new DistanceTerrainBands(farFieldSettings.detailedChunkRuntimeConfig(runtimeConfig), null, farFieldSettings);
        }

        int loadBufferChunks = Math.max(1, runtimeConfig.loadRadius() - runtimeConfig.renderRadius());
        int detailedRenderRadiusChunks = Math.min(
                runtimeConfig.renderRadius(),
                Math.min(
                        MAX_ULTRA_DETAILED_RADIUS_CHUNKS,
                        ULTRA_DETAILED_RADIUS_BASE
                                + (((runtimeConfig.renderRadius() - ULTRA_DISTANCE_THRESHOLD)
                                                / ULTRA_DETAILED_RADIUS_GROWTH_STEP_CHUNKS)
                                        * ULTRA_DETAILED_RADIUS_GROWTH_AMOUNT)));
        int detailedLoadRadiusChunks = detailedRenderRadiusChunks + Math.max(loadBufferChunks, 4);

        int middleStartRadiusChunks = Math.max(MIN_MIDDLE_START_RADIUS_CHUNKS, detailedRenderRadiusChunks);
        int middleEndRadiusChunks = Math.min(runtimeConfig.renderRadius(), MIDDLE_END_RADIUS_CHUNKS);
        // Keep the stitched middle/far seam aligned to one radius instead of letting two opaque
        // distance bands overlap deeply. The previous 16-chunk overlap produced visible
        // band-on-band artifacts at 192 because both region stacks were rendering terrain
        // through the same space with different resolutions.
        int farStartRadiusChunks = middleEndRadiusChunks;

        FarFieldTerrainSettings middleTerrainSettings = FarFieldTerrainSettings.visualOnlyBand(
                middleStartRadiusChunks,
                middleEndRadiusChunks,
                MIDDLE_OVERLAP_CHUNKS,
                MIDDLE_REGION_SPAN_CHUNKS,
                MIDDLE_CELL_SIZE_BLOCKS,
                MIDDLE_ANCHOR_HYSTERESIS_CHUNKS);
        FarFieldTerrainSettings farTerrainSettings = FarFieldTerrainSettings.visualOnlyBand(
                farStartRadiusChunks,
                runtimeConfig.renderRadius(),
                FAR_OVERLAP_CHUNKS,
                FAR_REGION_SPAN_CHUNKS,
                FAR_CELL_SIZE_BLOCKS,
                FAR_ANCHOR_HYSTERESIS_CHUNKS);

        return new DistanceTerrainBands(
                new ChunkRuntimeConfig(
                        detailedLoadRadiusChunks,
                        detailedRenderRadiusChunks,
                        Math.min(runtimeConfig.simulationRadius(), detailedRenderRadiusChunks)),
                middleTerrainSettings,
                farTerrainSettings);
    }

    public FarFieldTerrainSettings transitionTerrainSettings() {
        return middleTerrainSettings != null ? middleTerrainSettings : farTerrainSettings;
    }
}
