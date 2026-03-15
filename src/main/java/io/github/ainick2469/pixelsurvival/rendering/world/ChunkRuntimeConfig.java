package io.github.ainick2469.pixelsurvival.rendering.world;

public record ChunkRuntimeConfig(
        int loadRadius,
        int renderRadius,
        int simulationRadius) {
    public ChunkRuntimeConfig {
        if (loadRadius < 1) {
            throw new IllegalArgumentException("loadRadius must be at least 1");
        }
        if (renderRadius < 1) {
            throw new IllegalArgumentException("renderRadius must be at least 1");
        }
        if (simulationRadius < 0) {
            throw new IllegalArgumentException("simulationRadius must be at least 0");
        }
        if (loadRadius < renderRadius + 1) {
            throw new IllegalArgumentException("loadRadius must be at least renderRadius + 1 for neighbor culling.");
        }
        if (renderRadius < simulationRadius) {
            throw new IllegalArgumentException("renderRadius must be greater than or equal to simulationRadius.");
        }
    }

    public static ChunkRuntimeConfig productionDefaults() {
        return new ChunkRuntimeConfig(4, 3, 2);
    }
}
