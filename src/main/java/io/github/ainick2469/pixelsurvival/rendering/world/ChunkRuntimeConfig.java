package io.github.ainick2469.pixelsurvival.rendering.world;

public record ChunkRuntimeConfig(
        int loadRadius,
        int renderRadius,
        int simulationRadius) {
    public static final int STARTUP_PRIME_RENDER_RADIUS = 2;

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
        return new ChunkRuntimeConfig(10, 8, 4);
    }

    public ChunkRuntimeConfig startupPrimeConfig() {
        int primedRenderRadius = Math.min(renderRadius, STARTUP_PRIME_RENDER_RADIUS);
        int primedSimulationRadius = Math.min(simulationRadius, primedRenderRadius);
        return new ChunkRuntimeConfig(primedRenderRadius + 1, primedRenderRadius, primedSimulationRadius);
    }
}
