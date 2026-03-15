package io.github.ainick2469.pixelsurvival.world.chunk;

import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import java.util.Arrays;
import java.util.Objects;

public final class ChunkData {
    public static final int SIZE_X = 16;
    public static final int SIZE_Y = 64;
    public static final int SIZE_Z = 16;

    private final ChunkCoord chunkCoord;
    private final BlockId defaultBlockId;
    private final BlockId[] blocks;

    public ChunkData(ChunkCoord chunkCoord, BlockId defaultBlockId) {
        this.chunkCoord = Objects.requireNonNull(chunkCoord, "chunkCoord");
        this.defaultBlockId = Objects.requireNonNull(defaultBlockId, "defaultBlockId");
        this.blocks = new BlockId[SIZE_X * SIZE_Y * SIZE_Z];
        Arrays.fill(blocks, defaultBlockId);
    }

    public ChunkCoord chunkCoord() {
        return chunkCoord;
    }

    public BlockId defaultBlockId() {
        return defaultBlockId;
    }

    public BlockId getBlock(int x, int y, int z) {
        validateBounds(x, y, z);
        return blocks[indexOf(x, y, z)];
    }

    public void setBlock(int x, int y, int z, BlockId blockId) {
        validateBounds(x, y, z);
        blocks[indexOf(x, y, z)] = Objects.requireNonNull(blockId, "blockId");
    }

    public boolean isInBounds(int x, int y, int z) {
        return x >= 0 && x < SIZE_X && y >= 0 && y < SIZE_Y && z >= 0 && z < SIZE_Z;
    }

    public int toWorldX(int localX) {
        return chunkCoord.x() * SIZE_X + localX;
    }

    public int toWorldZ(int localZ) {
        return chunkCoord.z() * SIZE_Z + localZ;
    }

    private void validateBounds(int x, int y, int z) {
        if (!isInBounds(x, y, z)) {
            throw new IndexOutOfBoundsException("Out of bounds local block coordinate: " + x + "," + y + "," + z);
        }
    }

    private static int indexOf(int x, int y, int z) {
        return (y * SIZE_Z + z) * SIZE_X + x;
    }
}
