package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.world.block.BlockId;

public interface FarFieldTerrainSampler {
    ColumnSample sampleColumn(int worldX, int worldZ);

    record ColumnSample(int surfaceHeight, BlockId surfaceBlockId) {
    }
}
