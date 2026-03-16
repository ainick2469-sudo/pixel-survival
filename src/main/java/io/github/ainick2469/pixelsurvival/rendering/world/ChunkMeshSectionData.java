package io.github.ainick2469.pixelsurvival.rendering.world;

public record ChunkMeshSectionData(
        float[] positions,
        float[] normals,
        float[] textureCoordinates,
        int textureCoordinateComponents,
        float[] secondaryTextureCoordinates,
        int secondaryTextureCoordinateComponents,
        int[] indices,
        int faceCount) {
    public ChunkMeshSectionData(
            float[] positions,
            float[] normals,
            float[] textureCoordinates,
            int textureCoordinateComponents,
            int[] indices,
            int faceCount) {
        this(
                positions,
                normals,
                textureCoordinates,
                textureCoordinateComponents,
                new float[0],
                0,
                indices,
                faceCount);
    }
}
