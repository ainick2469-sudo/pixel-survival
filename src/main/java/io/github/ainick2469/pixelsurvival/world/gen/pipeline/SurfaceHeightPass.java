package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public final class SurfaceHeightPass implements ChunkGenerationPass {
    private final SurfaceHeightProfile surfaceHeightProfile;

    public SurfaceHeightPass(SurfaceHeightProfile surfaceHeightProfile) {
        this.surfaceHeightProfile = surfaceHeightProfile;
    }

    @Override
    public WorldGenerationStage stage() {
        return WorldGenerationStage.BASE_TERRAIN;
    }

    @Override
    public void apply(ChunkGenerationContext context) {
        ColumnIntField surfaceHeights = context.scratchpad().intField(WorldGenerationFieldKeys.SURFACE_HEIGHT);
        ChunkData chunkData = context.chunkData();

        for (int localX = 0; localX < ChunkData.SIZE_X; localX++) {
            for (int localZ = 0; localZ < ChunkData.SIZE_Z; localZ++) {
                int worldX = chunkData.toWorldX(localX);
                int worldZ = chunkData.toWorldZ(localZ);
                surfaceHeights.set(localX, localZ, surfaceHeightProfile.sampleSurfaceHeight(worldX, worldZ));
            }
        }
    }
}
