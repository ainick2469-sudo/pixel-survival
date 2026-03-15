package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.block.BlockVisualDefinition;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.sim.AuthoritativeWorldService;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChunkMeshBuilder {
    private final AuthoritativeWorldService worldService;
    private final GameRegistries registries;

    public ChunkMeshBuilder(AuthoritativeWorldService worldService, GameRegistries registries) {
        this.worldService = worldService;
        this.registries = registries;
    }

    public ChunkMeshBuildResult buildChunkMesh(ChunkData chunkData) {
        Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders = new LinkedHashMap<>();
        int visibleBlockCount = 0;
        int emittedFaceCount = 0;

        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int y = 0; y < ChunkData.SIZE_Y; y++) {
                for (int z = 0; z < ChunkData.SIZE_Z; z++) {
                    BlockDefinition definition = registries.requireBlockDefinition(chunkData.getBlock(x, y, z));
                    if (!definition.solid()) {
                        continue;
                    }

                    boolean blockVisible = false;
                    int worldX = chunkData.toWorldX(x);
                    int worldZ = chunkData.toWorldZ(z);

                    for (BlockFace face : BlockFace.values()) {
                        BlockDefinition neighborDefinition = registries.requireBlockDefinition(worldService.getBlockAtWorldOrAir(
                                worldX + face.stepX(), y + face.stepY(), worldZ + face.stepZ()));
                        if (neighborDefinition.solid() && neighborDefinition.opaque()) {
                            continue;
                        }

                        TerrainMaterialKey materialKey = materialKeyFor(definition, face);
                        sectionBuilders
                                .computeIfAbsent(materialKey, ignored -> new MeshSectionBuilder())
                                .appendFace(x, y, z, face);
                        emittedFaceCount++;
                        blockVisible = true;
                    }

                    if (blockVisible) {
                        visibleBlockCount++;
                    }
                }
            }
        }

        Map<TerrainMaterialKey, ChunkMeshSectionData> sections = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialKey, MeshSectionBuilder> entry : sectionBuilders.entrySet()) {
            ChunkMeshSectionData sectionData = entry.getValue().build();
            if (sectionData.faceCount() > 0) {
                sections.put(entry.getKey(), sectionData);
            }
        }

        return new ChunkMeshBuildResult(chunkData.chunkCoord(), Map.copyOf(sections), visibleBlockCount, emittedFaceCount);
    }

    private TerrainMaterialKey materialKeyFor(BlockDefinition definition, BlockFace face) {
        BlockVisualDefinition visuals = definition.visuals();
        if (visuals != null) {
            String texturePath = switch (face) {
                case UP -> visuals.topTexture();
                case DOWN -> visuals.bottomTexture();
                case EAST, WEST, SOUTH, NORTH -> visuals.sideTexture();
            };
            return TerrainMaterialKey.textured(texturePath, visuals.tintKey());
        }
        return TerrainMaterialKey.debugColor(definition.debugColor());
    }

    private static final class MeshSectionBuilder {
        private final FloatCollector positions = new FloatCollector(1_024);
        private final FloatCollector normals = new FloatCollector(1_024);
        private final FloatCollector textureCoordinates = new FloatCollector(1_024);
        private final IntCollector indices = new IntCollector(1_024);
        private int vertexCount;
        private int faceCount;

        private void appendFace(int blockX, int blockY, int blockZ, BlockFace face) {
            float[] vertexOffsets = face.vertexOffsets();
            for (int index = 0; index < vertexOffsets.length; index += 3) {
                positions.add(blockX + vertexOffsets[index]);
                positions.add(blockY + vertexOffsets[index + 1]);
                positions.add(blockZ + vertexOffsets[index + 2]);
                normals.add(face.normalX());
                normals.add(face.normalY());
                normals.add(face.normalZ());
            }

            float[] uvs = face.uvs();
            for (int index = 0; index < uvs.length; index += 2) {
                textureCoordinates.add(uvs[index]);
                textureCoordinates.add(uvs[index + 1]);
            }

            indices.add(vertexCount);
            indices.add(vertexCount + 1);
            indices.add(vertexCount + 2);
            indices.add(vertexCount);
            indices.add(vertexCount + 2);
            indices.add(vertexCount + 3);
            vertexCount += 4;
            faceCount++;
        }

        private ChunkMeshSectionData build() {
            return new ChunkMeshSectionData(
                    positions.toArray(),
                    normals.toArray(),
                    textureCoordinates.toArray(),
                    indices.toArray(),
                    faceCount);
        }
    }

    private static final class FloatCollector {
        private float[] values;
        private int size;

        private FloatCollector(int initialCapacity) {
            this.values = new float[initialCapacity];
        }

        private void add(float value) {
            ensureCapacity(1);
            values[size++] = value;
        }

        private void ensureCapacity(int additionalValues) {
            int requiredSize = size + additionalValues;
            if (requiredSize <= values.length) {
                return;
            }

            int newCapacity = Math.max(requiredSize, values.length * 2);
            float[] expanded = new float[newCapacity];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }

        private float[] toArray() {
            float[] compact = new float[size];
            System.arraycopy(values, 0, compact, 0, size);
            return compact;
        }
    }

    private static final class IntCollector {
        private int[] values;
        private int size;

        private IntCollector(int initialCapacity) {
            this.values = new int[initialCapacity];
        }

        private void add(int value) {
            ensureCapacity(1);
            values[size++] = value;
        }

        private void ensureCapacity(int additionalValues) {
            int requiredSize = size + additionalValues;
            if (requiredSize <= values.length) {
                return;
            }

            int newCapacity = Math.max(requiredSize, values.length * 2);
            int[] expanded = new int[newCapacity];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }

        private int[] toArray() {
            int[] compact = new int[size];
            System.arraycopy(values, 0, compact, 0, size);
            return compact;
        }
    }
}
