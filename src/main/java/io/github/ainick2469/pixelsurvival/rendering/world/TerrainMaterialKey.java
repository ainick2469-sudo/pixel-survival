package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.block.BlockFaceTextureReference;
import java.util.Objects;

public record TerrainMaterialKey(
        BlockFaceTextureReference textureReference,
        String tintKey,
        String debugColor) {
    public TerrainMaterialKey {
        if (textureReference == null && (debugColor == null || debugColor.isBlank())) {
            throw new IllegalArgumentException("A terrain material key needs either a texture reference or a fallback color.");
        }
        tintKey = tintKey == null || tintKey.isBlank() ? null : tintKey;
        debugColor = debugColor == null || debugColor.isBlank() ? null : debugColor;
    }

    public static TerrainMaterialKey textured(BlockFaceTextureReference textureReference, String tintKey) {
        return new TerrainMaterialKey(Objects.requireNonNull(textureReference, "textureReference"), tintKey, null);
    }

    public static TerrainMaterialKey debugColor(String debugColor) {
        return new TerrainMaterialKey(null, null, Objects.requireNonNull(debugColor, "debugColor"));
    }

    public boolean usesTexture() {
        return textureReference != null;
    }
}
