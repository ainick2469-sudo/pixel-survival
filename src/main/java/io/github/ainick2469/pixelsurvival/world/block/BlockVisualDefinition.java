package io.github.ainick2469.pixelsurvival.world.block;

import java.util.Objects;

public record BlockVisualDefinition(
        String topTexture,
        String sideTexture,
        String bottomTexture,
        String tintKey) {
    public BlockVisualDefinition {
        Objects.requireNonNull(topTexture, "topTexture");
        Objects.requireNonNull(sideTexture, "sideTexture");
        Objects.requireNonNull(bottomTexture, "bottomTexture");
    }
}
