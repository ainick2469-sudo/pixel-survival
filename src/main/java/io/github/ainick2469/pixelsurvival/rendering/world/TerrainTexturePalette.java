package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockFaceTextureReference;
import io.github.ainick2469.pixelsurvival.world.block.BlockTextureFace;
import io.github.ainick2469.pixelsurvival.world.block.BlockVisualDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TerrainTexturePalette {
    private static final Comparator<BlockFaceTextureReference> TEXTURE_REFERENCE_COMPARATOR = Comparator
            .comparing((BlockFaceTextureReference reference) -> reference.texturePath() == null ? "" : reference.texturePath())
            .thenComparing(reference -> reference.cubeNetTexturePath() == null ? "" : reference.cubeNetTexturePath())
            .thenComparing(reference -> reference.cubeNetLayout() == null ? "" : reference.cubeNetLayout().id())
            .thenComparing(reference -> reference.cubeNetFace() == null ? "" : reference.cubeNetFace().name());

    private final List<BlockFaceTextureReference> textureReferences;
    private final Map<BlockFaceTextureReference, Integer> layerIndexByTextureReference;

    private TerrainTexturePalette(List<BlockFaceTextureReference> textureReferences) {
        this.textureReferences = List.copyOf(textureReferences);
        Map<BlockFaceTextureReference, Integer> layerIndexLookup = new LinkedHashMap<>();
        for (int index = 0; index < textureReferences.size(); index++) {
            layerIndexLookup.put(textureReferences.get(index), index);
        }
        this.layerIndexByTextureReference = Map.copyOf(layerIndexLookup);
    }

    public static TerrainTexturePalette build(GameRegistries registries) {
        Objects.requireNonNull(registries, "registries");
        List<BlockFaceTextureReference> collectedReferences = new ArrayList<>();
        for (BlockDefinition definition : registries.blocks().values()) {
            BlockVisualDefinition visuals = definition.visuals();
            if (visuals == null) {
                continue;
            }
            for (BlockTextureFace face : BlockTextureFace.values()) {
                BlockFaceTextureReference textureReference = visuals.textureReferenceFor(face);
                if (!collectedReferences.contains(textureReference)) {
                    collectedReferences.add(textureReference);
                }
            }
        }
        collectedReferences.sort(TEXTURE_REFERENCE_COMPARATOR);
        return new TerrainTexturePalette(collectedReferences);
    }

    public List<BlockFaceTextureReference> textureReferences() {
        return textureReferences;
    }

    public int layerIndexFor(BlockFaceTextureReference textureReference) {
        Integer layerIndex = layerIndexByTextureReference.get(Objects.requireNonNull(textureReference, "textureReference"));
        if (layerIndex == null) {
            throw new IllegalArgumentException("Missing terrain texture palette entry for " + textureReference);
        }
        return layerIndex;
    }
}
