package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public final class ColumnIntField {
    private final int[][] values = new int[ChunkData.SIZE_X][ChunkData.SIZE_Z];

    public int get(int localX, int localZ) {
        validate(localX, localZ);
        return values[localX][localZ];
    }

    public void set(int localX, int localZ, int value) {
        validate(localX, localZ);
        values[localX][localZ] = value;
    }

    private void validate(int localX, int localZ) {
        if (localX < 0 || localX >= ChunkData.SIZE_X || localZ < 0 || localZ >= ChunkData.SIZE_Z) {
            throw new IndexOutOfBoundsException("Out of bounds column coordinate: " + localX + "," + localZ);
        }
    }
}
