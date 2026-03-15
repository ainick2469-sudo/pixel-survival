package io.github.ainick2469.pixelsurvival.world.block;

import java.util.Objects;
import java.util.Set;

public record BlockDefinition(
        BlockId id,
        String displayName,
        String materialFamily,
        boolean solid,
        boolean opaque,
        String debugColor,
        Set<String> tags) {
    public BlockDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(materialFamily, "materialFamily");
        Objects.requireNonNull(debugColor, "debugColor");
        tags = tags == null ? Set.of() : Set.copyOf(tags);
    }
}
