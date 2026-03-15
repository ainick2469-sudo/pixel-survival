package io.github.ainick2469.pixelsurvival.world.block;

import java.util.Objects;

public record BlockFaceTextureReference(
        String texturePath,
        String cubeNetTexturePath,
        CubeNetLayout cubeNetLayout,
        BlockTextureFace cubeNetFace) {
    public BlockFaceTextureReference {
        texturePath = normalize(texturePath);
        cubeNetTexturePath = normalize(cubeNetTexturePath);
        if (texturePath == null && cubeNetTexturePath == null) {
            throw new IllegalArgumentException("A face texture reference must declare either a direct texture or a cube-net source.");
        }
        if (texturePath != null && cubeNetTexturePath != null) {
            throw new IllegalArgumentException("A face texture reference cannot be both direct-texture and cube-net based.");
        }
        if (cubeNetTexturePath != null) {
            Objects.requireNonNull(cubeNetLayout, "cubeNetLayout");
            Objects.requireNonNull(cubeNetFace, "cubeNetFace");
        }
    }

    public static BlockFaceTextureReference direct(String texturePath) {
        return new BlockFaceTextureReference(texturePath, null, null, null);
    }

    public static BlockFaceTextureReference cubeNet(
            String cubeNetTexturePath,
            CubeNetLayout cubeNetLayout,
            BlockTextureFace cubeNetFace) {
        return new BlockFaceTextureReference(null, cubeNetTexturePath, cubeNetLayout, cubeNetFace);
    }

    public boolean usesCubeNet() {
        return cubeNetTexturePath != null;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
