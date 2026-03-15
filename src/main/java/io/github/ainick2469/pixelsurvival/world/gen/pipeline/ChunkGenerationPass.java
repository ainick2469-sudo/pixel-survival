package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

public interface ChunkGenerationPass {
    WorldGenerationStage stage();

    void apply(ChunkGenerationContext context);
}
