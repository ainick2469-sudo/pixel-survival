package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import java.util.Objects;

public final class ChunkGenerationContext {
    private final long worldSeed;
    private final GameRegistries registries;
    private final ChunkData chunkData;
    private final ChunkGenerationScratchpad scratchpad;

    public ChunkGenerationContext(long worldSeed, GameRegistries registries, ChunkData chunkData) {
        this.worldSeed = worldSeed;
        this.registries = Objects.requireNonNull(registries, "registries");
        this.chunkData = Objects.requireNonNull(chunkData, "chunkData");
        this.scratchpad = new ChunkGenerationScratchpad();
    }

    public long worldSeed() {
        return worldSeed;
    }

    public GameRegistries registries() {
        return registries;
    }

    public ChunkData chunkData() {
        return chunkData;
    }

    public ChunkCoord chunkCoord() {
        return chunkData.chunkCoord();
    }

    public ChunkGenerationScratchpad scratchpad() {
        return scratchpad;
    }
}
