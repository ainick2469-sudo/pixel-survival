package io.github.ainick2469.pixelsurvival.rendering.world;

import java.util.Objects;

public record TerrainMaterialKey(
        String texturePath,
        String tintKey,
        String debugColor) {
    public TerrainMaterialKey {
        if ((texturePath == null || texturePath.isBlank()) && (debugColor == null || debugColor.isBlank())) {
            throw new IllegalArgumentException("A terrain material key needs either a texture path or a fallback color.");
        }
        texturePath = texturePath == null || texturePath.isBlank() ? null : texturePath;
        tintKey = tintKey == null || tintKey.isBlank() ? null : tintKey;
        debugColor = debugColor == null || debugColor.isBlank() ? null : debugColor;
    }

    public static TerrainMaterialKey textured(String texturePath, String tintKey) {
        return new TerrainMaterialKey(Objects.requireNonNull(texturePath, "texturePath"), tintKey, null);
    }

    public static TerrainMaterialKey debugColor(String debugColor) {
        return new TerrainMaterialKey(null, null, Objects.requireNonNull(debugColor, "debugColor"));
    }

    public boolean usesTexture() {
        return texturePath != null;
    }
}
