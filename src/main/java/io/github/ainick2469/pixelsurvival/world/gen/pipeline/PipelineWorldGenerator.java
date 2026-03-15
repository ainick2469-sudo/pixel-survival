package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.WorldGenerator;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class PipelineWorldGenerator implements WorldGenerator {
    private final long worldSeed;
    private final BlockId defaultBlockId;
    private final List<ChunkGenerationPass> passes;

    public PipelineWorldGenerator(long worldSeed, BlockId defaultBlockId, List<ChunkGenerationPass> passes) {
        this.worldSeed = worldSeed;
        this.defaultBlockId = Objects.requireNonNull(defaultBlockId, "defaultBlockId");
        this.passes = List.copyOf(Objects.requireNonNull(passes, "passes").stream()
                .sorted(Comparator.comparing(ChunkGenerationPass::stage))
                .toList());
    }

    @Override
    public ChunkData generateChunk(ChunkCoord chunkCoord, GameRegistries registries) {
        registries.requireBlockDefinition(defaultBlockId);
        ChunkData chunkData = new ChunkData(chunkCoord, defaultBlockId);
        ChunkGenerationContext context = new ChunkGenerationContext(worldSeed, registries, chunkData);
        for (ChunkGenerationPass pass : passes) {
            pass.apply(context);
        }
        return chunkData;
    }

    public List<WorldGenerationStage> configuredStages() {
        return passes.stream().map(ChunkGenerationPass::stage).distinct().toList();
    }
}
