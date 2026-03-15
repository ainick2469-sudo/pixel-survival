package io.github.ainick2469.pixelsurvival.settings;

import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRuntimeConfig;

public record GraphicsSettings(int renderDistanceChunks) {
    public static final int MIN_RENDER_DISTANCE_CHUNKS = 2;
    public static final int DEFAULT_RENDER_DISTANCE_CHUNKS = 12;
    public static final int MAX_RENDER_DISTANCE_CHUNKS = 48;

    public GraphicsSettings {
        if (renderDistanceChunks < MIN_RENDER_DISTANCE_CHUNKS
                || renderDistanceChunks > MAX_RENDER_DISTANCE_CHUNKS) {
            throw new IllegalArgumentException(
                    "renderDistanceChunks must be between "
                            + MIN_RENDER_DISTANCE_CHUNKS
                            + " and "
                            + MAX_RENDER_DISTANCE_CHUNKS);
        }
    }

    public static GraphicsSettings defaults() {
        return new GraphicsSettings(DEFAULT_RENDER_DISTANCE_CHUNKS);
    }

    public GraphicsSettings withRenderDistanceChunks(int renderDistanceChunks) {
        return new GraphicsSettings(clampRenderDistance(renderDistanceChunks));
    }

    public ChunkRuntimeConfig toChunkRuntimeConfig() {
        int loadRadius = renderDistanceChunks + Math.max(2, Math.min(4, renderDistanceChunks / 16));
        int simulationRadius = Math.min(4, Math.max(2, renderDistanceChunks / 4));
        return new ChunkRuntimeConfig(loadRadius, renderDistanceChunks, simulationRadius);
    }

    public static int clampRenderDistance(int renderDistanceChunks) {
        return Math.max(
                MIN_RENDER_DISTANCE_CHUNKS,
                Math.min(MAX_RENDER_DISTANCE_CHUNKS, renderDistanceChunks));
    }
}
