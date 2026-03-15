package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public final class TerrainLayerPass implements ChunkGenerationPass {
    private final BlockId deepLayerBlockId;
    private final BlockId subsurfaceBlockId;
    private final BlockId surfaceBlockId;
    private final int subsurfaceDepth;

    public TerrainLayerPass(
            BlockId deepLayerBlockId,
            BlockId subsurfaceBlockId,
            BlockId surfaceBlockId,
            int subsurfaceDepth) {
        this.deepLayerBlockId = deepLayerBlockId;
        this.subsurfaceBlockId = subsurfaceBlockId;
        this.surfaceBlockId = surfaceBlockId;
        this.subsurfaceDepth = subsurfaceDepth;
    }

    @Override
    public WorldGenerationStage stage() {
        return WorldGenerationStage.TERRAIN_LAYERING;
    }

    @Override
    public void apply(ChunkGenerationContext context) {
        context.registries().requireBlockDefinition(deepLayerBlockId);
        context.registries().requireBlockDefinition(subsurfaceBlockId);
        context.registries().requireBlockDefinition(surfaceBlockId);

        ColumnIntField surfaceHeights = context.scratchpad().requireIntField(WorldGenerationFieldKeys.SURFACE_HEIGHT);
        ChunkData chunkData = context.chunkData();
        for (int localX = 0; localX < ChunkData.SIZE_X; localX++) {
            for (int localZ = 0; localZ < ChunkData.SIZE_Z; localZ++) {
                int surfaceHeight = surfaceHeights.get(localX, localZ);
                int deepLayerTop = Math.max(0, surfaceHeight - subsurfaceDepth - 1);
                int subsurfaceStart = deepLayerTop + 1;

                for (int y = 0; y <= deepLayerTop; y++) {
                    chunkData.setBlock(localX, y, localZ, deepLayerBlockId);
                }
                for (int y = subsurfaceStart; y < surfaceHeight; y++) {
                    chunkData.setBlock(localX, y, localZ, subsurfaceBlockId);
                }
                chunkData.setBlock(localX, surfaceHeight, localZ, surfaceBlockId);
            }
        }
    }
}
