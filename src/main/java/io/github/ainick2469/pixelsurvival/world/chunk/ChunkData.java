package io.github.ainick2469.pixelsurvival.world.chunk;

import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ChunkData {
    public static final int SIZE_X = 16;
    public static final int SIZE_Y = 64;
    public static final int SIZE_Z = 16;
    private static final int TOTAL_BLOCK_COUNT = SIZE_X * SIZE_Y * SIZE_Z;

    private final ChunkCoord chunkCoord;
    private final BlockId defaultBlockId;
    private final List<BlockId> palette = new ArrayList<>();
    private final Map<BlockId, Integer> paletteIndexByBlock = new HashMap<>();

    private byte[] blockIndices8 = new byte[TOTAL_BLOCK_COUNT];
    private short[] blockIndices16;
    private int[] blockIndices32;

    public ChunkData(ChunkCoord chunkCoord, BlockId defaultBlockId) {
        this.chunkCoord = Objects.requireNonNull(chunkCoord, "chunkCoord");
        this.defaultBlockId = Objects.requireNonNull(defaultBlockId, "defaultBlockId");
        palette.add(defaultBlockId);
        paletteIndexByBlock.put(defaultBlockId, 0);
    }

    public ChunkCoord chunkCoord() {
        return chunkCoord;
    }

    public BlockId defaultBlockId() {
        return defaultBlockId;
    }

    public BlockId getBlock(int x, int y, int z) {
        validateBounds(x, y, z);
        return palette.get(readPaletteIndex(indexOf(x, y, z)));
    }

    public void setBlock(int x, int y, int z, BlockId blockId) {
        validateBounds(x, y, z);
        int blockIndex = indexOf(x, y, z);
        writePaletteIndex(blockIndex, paletteIndexFor(Objects.requireNonNull(blockId, "blockId")));
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

    public int paletteSize() {
        return palette.size();
    }

    public long estimatedStorageBytes() {
        long indexStorageBytes = blockIndices8 != null
                ? blockIndices8.length
                : blockIndices16 != null ? (long) blockIndices16.length * Short.BYTES : (long) blockIndices32.length * Integer.BYTES;
        long paletteStorageBytes = (long) palette.size() * Long.BYTES;
        return indexStorageBytes + paletteStorageBytes;
    }

    public String storageFormat() {
        if (blockIndices8 != null) {
            return "u8";
        }
        if (blockIndices16 != null) {
            return "u16";
        }
        return "i32";
    }

    private int paletteIndexFor(BlockId blockId) {
        Integer paletteIndex = paletteIndexByBlock.get(blockId);
        if (paletteIndex != null) {
            return paletteIndex;
        }

        int nextPaletteIndex = palette.size();
        ensureStorageCanRepresent(nextPaletteIndex);
        palette.add(blockId);
        paletteIndexByBlock.put(blockId, nextPaletteIndex);
        return nextPaletteIndex;
    }

    private void ensureStorageCanRepresent(int paletteIndex) {
        if (blockIndices8 != null) {
            if (paletteIndex <= 0xFF) {
                return;
            }
            blockIndices16 = new short[TOTAL_BLOCK_COUNT];
            for (int index = 0; index < TOTAL_BLOCK_COUNT; index++) {
                blockIndices16[index] = (short) Byte.toUnsignedInt(blockIndices8[index]);
            }
            blockIndices8 = null;
        }

        if (blockIndices16 != null) {
            if (paletteIndex <= 0xFFFF) {
                return;
            }
            blockIndices32 = new int[TOTAL_BLOCK_COUNT];
            for (int index = 0; index < TOTAL_BLOCK_COUNT; index++) {
                blockIndices32[index] = Short.toUnsignedInt(blockIndices16[index]);
            }
            blockIndices16 = null;
        }
    }

    private int readPaletteIndex(int blockIndex) {
        if (blockIndices8 != null) {
            return Byte.toUnsignedInt(blockIndices8[blockIndex]);
        }
        if (blockIndices16 != null) {
            return Short.toUnsignedInt(blockIndices16[blockIndex]);
        }
        return blockIndices32[blockIndex];
    }

    private void writePaletteIndex(int blockIndex, int paletteIndex) {
        if (blockIndices8 != null) {
            blockIndices8[blockIndex] = (byte) paletteIndex;
            return;
        }
        if (blockIndices16 != null) {
            blockIndices16[blockIndex] = (short) paletteIndex;
            return;
        }
        blockIndices32[blockIndex] = paletteIndex;
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
