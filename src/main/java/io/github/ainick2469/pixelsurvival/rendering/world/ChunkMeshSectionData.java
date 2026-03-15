package io.github.ainick2469.pixelsurvival.rendering.world;

public record ChunkMeshSectionData(
        float[] positions,
        float[] normals,
        float[] textureCoordinates,
        int textureCoordinateComponents,
        int[] indices,
        int faceCount) {
}
