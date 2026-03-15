package io.github.ainick2469.pixelsurvival.world.chunk;

import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkDataTest {
    private static final BlockId AIR = BlockId.of("pixel_survival:air");

    @Test
    void defaultsToSingleEntryPaletteStorage() {
        ChunkData chunkData = new ChunkData(new ChunkCoord(0, 0), AIR);

        assertEquals(AIR, chunkData.getBlock(0, 0, 0));
        assertEquals(1, chunkData.paletteSize());
        assertEquals("u8", chunkData.storageFormat());
        assertEquals(ChunkData.SIZE_X * ChunkData.SIZE_Y * ChunkData.SIZE_Z + Long.BYTES, chunkData.estimatedStorageBytes());
    }

    @Test
    void promotesPaletteStorageWhenUniqueBlockCountExceedsByteRange() {
        ChunkData chunkData = new ChunkData(new ChunkCoord(0, 0), AIR);

        for (int index = 0; index < 260; index++) {
            BlockId blockId = BlockId.of("pixel_survival:test_block_" + index);
            int x = index % ChunkData.SIZE_X;
            int z = (index / ChunkData.SIZE_X) % ChunkData.SIZE_Z;
            int y = index / (ChunkData.SIZE_X * ChunkData.SIZE_Z);
            chunkData.setBlock(x, y, z, blockId);
        }

        assertEquals("u16", chunkData.storageFormat());
        assertEquals(BlockId.of("pixel_survival:test_block_259"), chunkData.getBlock(3, 1, 0));
        assertEquals(261, chunkData.paletteSize());
    }
}
