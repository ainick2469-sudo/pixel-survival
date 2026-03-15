package io.github.ainick2469.pixelsurvival.rendering.world;

import java.util.Objects;

public record TerrainMaterialKey(
        TerrainMaterialKind kind,
        String debugColor) {
    public TerrainMaterialKey {
        Objects.requireNonNull(kind, "kind");
        debugColor = debugColor == null || debugColor.isBlank() ? null : debugColor;
        if (kind == TerrainMaterialKind.DEBUG_COLOR && debugColor == null) {
            throw new IllegalArgumentException("Debug-color terrain materials need a fallback color.");
        }
        if (kind == TerrainMaterialKind.SHARED_TEXTURED && debugColor != null) {
            throw new IllegalArgumentException("Shared textured terrain materials cannot also carry a debug color.");
        }
    }

    public static TerrainMaterialKey sharedTextured() {
        return new TerrainMaterialKey(TerrainMaterialKind.SHARED_TEXTURED, null);
    }

    public static TerrainMaterialKey debugColor(String debugColor) {
        return new TerrainMaterialKey(TerrainMaterialKind.DEBUG_COLOR, Objects.requireNonNull(debugColor, "debugColor"));
    }

    public boolean usesTexture() {
        return kind == TerrainMaterialKind.SHARED_TEXTURED;
    }

    public enum TerrainMaterialKind {
        SHARED_TEXTURED,
        DEBUG_COLOR
    }
}
