package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.shape.Box;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import java.util.HashMap;
import java.util.Map;

public final class ChunkDebugRenderer {
    private static final int[][] NEIGHBOR_OFFSETS = {
        {1, 0, 0},
        {-1, 0, 0},
        {0, 1, 0},
        {0, -1, 0},
        {0, 0, 1},
        {0, 0, -1}
    };

    private final AssetManager assetManager;
    private final Box sharedCubeMesh = new Box(0.5f, 0.5f, 0.5f);
    private final Map<String, Material> materialCache = new HashMap<>();

    public ChunkDebugRenderer(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public Node buildChunkNode(ChunkData chunkData, Map<BlockId, BlockDefinition> blockDefinitions) {
        Node chunkNode =
                new Node("chunk_" + chunkData.chunkCoord().x() + "_" + chunkData.chunkCoord().z());

        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int y = 0; y < ChunkData.SIZE_Y; y++) {
                for (int z = 0; z < ChunkData.SIZE_Z; z++) {
                    BlockId blockId = chunkData.getBlock(x, y, z);
                    BlockDefinition definition = blockDefinitions.get(blockId);
                    if (definition == null || !definition.solid() || !isExposed(chunkData, x, y, z, blockDefinitions)) {
                        continue;
                    }

                    Geometry geometry = new Geometry("block_" + x + "_" + y + "_" + z, sharedCubeMesh);
                    geometry.setMaterial(materialFor(definition.debugColor()));
                    geometry.setLocalTranslation(
                            chunkData.toWorldX(x) + 0.5f, y + 0.5f, chunkData.toWorldZ(z) + 0.5f);
                    chunkNode.attachChild(geometry);
                }
            }
        }

        return chunkNode;
    }

    private boolean isExposed(
            ChunkData chunkData, int x, int y, int z, Map<BlockId, BlockDefinition> blockDefinitions) {
        for (int[] offset : NEIGHBOR_OFFSETS) {
            int neighborX = x + offset[0];
            int neighborY = y + offset[1];
            int neighborZ = z + offset[2];
            if (!chunkData.isInBounds(neighborX, neighborY, neighborZ)) {
                return true;
            }

            BlockDefinition neighborDefinition = blockDefinitions.get(chunkData.getBlock(neighborX, neighborY, neighborZ));
            if (neighborDefinition == null || !neighborDefinition.solid() || !neighborDefinition.opaque()) {
                return true;
            }
        }
        return false;
    }

    private Material materialFor(String debugColor) {
        return materialCache.computeIfAbsent(debugColor, this::createMaterial);
    }

    private Material createMaterial(String debugColor) {
        ColorRGBA color = parseHexColor(debugColor);
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Diffuse", color);
        material.setColor("Ambient", color.mult(0.75f));
        material.setColor("Specular", ColorRGBA.White.mult(0.15f));
        material.setFloat("Shininess", 8f);
        return material;
    }

    private ColorRGBA parseHexColor(String debugColor) {
        String normalized = debugColor.startsWith("#") ? debugColor.substring(1) : debugColor;
        if (normalized.length() != 6) {
            throw new IllegalArgumentException("Expected a 6-digit RGB hex color, got: " + debugColor);
        }

        int rgb = Integer.parseInt(normalized, 16);
        float red = ((rgb >> 16) & 0xFF) / 255f;
        float green = ((rgb >> 8) & 0xFF) / 255f;
        float blue = (rgb & 0xFF) / 255f;
        return new ColorRGBA(red, green, blue, 1f);
    }
}
