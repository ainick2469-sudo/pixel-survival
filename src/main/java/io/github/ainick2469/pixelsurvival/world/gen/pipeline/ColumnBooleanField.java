package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;

public final class ColumnBooleanField {
    private final boolean[][] values = new boolean[ChunkData.SIZE_X][ChunkData.SIZE_Z];

    public boolean get(int localX, int localZ) {
        validate(localX, localZ);
        return values[localX][localZ];
    }

    public void set(int localX, int localZ, boolean value) {
        validate(localX, localZ);
        values[localX][localZ] = value;
    }

    private void validate(int localX, int localZ) {
        if (localX < 0 || localX >= ChunkData.SIZE_X || localZ < 0 || localZ >= ChunkData.SIZE_Z) {
            throw new IndexOutOfBoundsException("Out of bounds column coordinate: " + localX + "," + localZ);
        }
    }
}
