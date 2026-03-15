package io.github.ainick2469.pixelsurvival.rendering.world;

public enum BlockFace {
    UP(
            0,
            1,
            0,
            new float[] {
                0f, 1f, 0f,
                0f, 1f, 1f,
                1f, 1f, 1f,
                1f, 1f, 0f
            },
            0f,
            1f,
            0f),
    DOWN(
            0,
            -1,
            0,
            new float[] {
                0f, 0f, 0f,
                1f, 0f, 0f,
                1f, 0f, 1f,
                0f, 0f, 1f
            },
            0f,
            -1f,
            0f),
    EAST(
            1,
            0,
            0,
            new float[] {
                1f, 0f, 0f,
                1f, 1f, 0f,
                1f, 1f, 1f,
                1f, 0f, 1f
            },
            1f,
            0f,
            0f),
    WEST(
            -1,
            0,
            0,
            new float[] {
                0f, 0f, 0f,
                0f, 0f, 1f,
                0f, 1f, 1f,
                0f, 1f, 0f
            },
            -1f,
            0f,
            0f),
    SOUTH(
            0,
            0,
            1,
            new float[] {
                0f, 0f, 1f,
                1f, 0f, 1f,
                1f, 1f, 1f,
                0f, 1f, 1f
            },
            0f,
            0f,
            1f),
    NORTH(
            0,
            0,
            -1,
            new float[] {
                0f, 0f, 0f,
                0f, 1f, 0f,
                1f, 1f, 0f,
                1f, 0f, 0f
            },
            0f,
            0f,
            -1f);

    private static final float[] FACE_UVS = {
        0f, 0f,
        1f, 0f,
        1f, 1f,
        0f, 1f
    };

    private final int stepX;
    private final int stepY;
    private final int stepZ;
    private final float[] vertexOffsets;
    private final float normalX;
    private final float normalY;
    private final float normalZ;

    BlockFace(
            int stepX,
            int stepY,
            int stepZ,
            float[] vertexOffsets,
            float normalX,
            float normalY,
            float normalZ) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.vertexOffsets = vertexOffsets;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    public float[] vertexOffsets() {
        return vertexOffsets;
    }

    public float normalX() {
        return normalX;
    }

    public float normalY() {
        return normalY;
    }

    public float normalZ() {
        return normalZ;
    }

    public float[] uvs() {
        return FACE_UVS;
    }
}
