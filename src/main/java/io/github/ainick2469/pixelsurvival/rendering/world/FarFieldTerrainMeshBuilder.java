package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockFaceTextureReference;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.block.BlockTextureFace;
import io.github.ainick2469.pixelsurvival.world.block.BlockVisualDefinition;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.FarFieldTerrainSampler;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FarFieldTerrainMeshBuilder {
    private static final TerrainMaterialKey SHARED_TEXTURED_MATERIAL_KEY = TerrainMaterialKey.sharedTextured();

    private final GameRegistries registries;
    private final TerrainTexturePalette terrainTexturePalette;
    private final FarFieldTerrainSampler terrainSampler;

    public FarFieldTerrainMeshBuilder(
            GameRegistries registries,
            TerrainTexturePalette terrainTexturePalette,
            FarFieldTerrainSampler terrainSampler) {
        this.registries = registries;
        this.terrainTexturePalette = terrainTexturePalette;
        this.terrainSampler = terrainSampler;
    }

    public FarFieldTerrainMeshBuildResult buildRegionMesh(
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings) {
        if (centerChunk == null) {
            return new FarFieldTerrainMeshBuildResult(regionCoord, Map.of(), 0, 0);
        }

        int cellsPerAxis = settings.cellsPerRegionAxis();
        CellSample[][] sampledCells = new CellSample[cellsPerAxis][cellsPerAxis];
        int visibleCellCount = 0;

        for (int cellX = 0; cellX < cellsPerAxis; cellX++) {
            for (int cellZ = 0; cellZ < cellsPerAxis; cellZ++) {
                sampledCells[cellX][cellZ] = sampleCell(regionCoord, centerChunk, settings, cellX, cellZ);
                if (sampledCells[cellX][cellZ].visible()) {
                    visibleCellCount++;
                }
            }
        }

        if (visibleCellCount == 0) {
            return new FarFieldTerrainMeshBuildResult(regionCoord, Map.of(), 0, 0);
        }

        Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders = new LinkedHashMap<>();
        int emittedFaceCount = 0;

        for (int cellX = 0; cellX < cellsPerAxis; cellX++) {
            for (int cellZ = 0; cellZ < cellsPerAxis; cellZ++) {
                CellSample cell = sampledCells[cellX][cellZ];
                if (!cell.visible()) {
                    continue;
                }

                int localStartX = cellX * settings.cellSizeBlocks();
                int localStartZ = cellZ * settings.cellSizeBlocks();
                ResolvedFaceMaterial topMaterial = resolvedMaterialFor(cell.definition(), BlockFace.UP);

                sectionBuilders
                        .computeIfAbsent(topMaterial.sectionKey(), key -> new MeshSectionBuilder(key.usesTexture()))
                        .appendTopQuad(
                                localStartX,
                                localStartZ,
                                settings.cellSizeBlocks(),
                                settings.cellSizeBlocks(),
                                cell.surfaceHeight() + 1,
                                topMaterial.textureLayer());
                emittedFaceCount++;

                emittedFaceCount += appendSideFace(
                        sectionBuilders, regionCoord, centerChunk, settings, sampledCells, cellX, cellZ, BlockFace.WEST);
                emittedFaceCount += appendSideFace(
                        sectionBuilders, regionCoord, centerChunk, settings, sampledCells, cellX, cellZ, BlockFace.EAST);
                emittedFaceCount += appendSideFace(
                        sectionBuilders, regionCoord, centerChunk, settings, sampledCells, cellX, cellZ, BlockFace.NORTH);
                emittedFaceCount += appendSideFace(
                        sectionBuilders, regionCoord, centerChunk, settings, sampledCells, cellX, cellZ, BlockFace.SOUTH);
            }
        }

        Map<TerrainMaterialKey, ChunkMeshSectionData> sections = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialKey, MeshSectionBuilder> entry : sectionBuilders.entrySet()) {
            ChunkMeshSectionData sectionData = entry.getValue().build();
            if (sectionData.faceCount() > 0) {
                sections.put(entry.getKey(), sectionData);
            }
        }

        return new FarFieldTerrainMeshBuildResult(regionCoord, Map.copyOf(sections), visibleCellCount, emittedFaceCount);
    }

    private int appendSideFace(
            Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders,
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            CellSample[][] sampledCells,
            int cellX,
            int cellZ,
            BlockFace face) {
        CellSample cell = sampledCells[cellX][cellZ];
        int currentTopY = cell.surfaceHeight() + 1;
        NeighborCellSample neighbor = neighborSample(regionCoord, centerChunk, settings, sampledCells, cellX, cellZ, face);
        int bottomY = neighbor.visible() ? neighbor.surfaceHeight() + 1 : settings.skirtFloorY();
        if (currentTopY <= bottomY) {
            return 0;
        }

        int localStartX = cellX * settings.cellSizeBlocks();
        int localStartZ = cellZ * settings.cellSizeBlocks();
        ResolvedFaceMaterial sideMaterial = resolvedMaterialFor(cell.definition(), face);
        sectionBuilders
                .computeIfAbsent(sideMaterial.sectionKey(), key -> new MeshSectionBuilder(key.usesTexture()))
                .appendSideQuad(
                        face,
                        localStartX,
                        localStartZ,
                        settings.cellSizeBlocks(),
                        bottomY,
                        currentTopY,
                        sideMaterial.textureLayer());
        return 1;
    }

    private CellSample sampleCell(
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            int cellX,
            int cellZ) {
        int worldStartX = regionCoord.worldStartX(settings) + (cellX * settings.cellSizeBlocks());
        int worldStartZ = regionCoord.worldStartZ(settings) + (cellZ * settings.cellSizeBlocks());
        int sampleWorldX = worldStartX + (settings.cellSizeBlocks() / 2);
        int sampleWorldZ = worldStartZ + (settings.cellSizeBlocks() / 2);
        FarFieldTerrainSampler.ColumnSample sample = terrainSampler.sampleColumn(sampleWorldX, sampleWorldZ);
        BlockId blockId = sample.surfaceBlockId();
        BlockDefinition definition = blockId == null ? null : registries.requireBlockDefinition(blockId);
        boolean visible = definition != null
                && definition.solid()
                && sample.surfaceHeight() >= 0
                && cellIntersectsFarFieldRing(centerChunk, settings, worldStartX, worldStartZ);
        return new CellSample(sample.surfaceHeight(), definition, visible);
    }

    private NeighborCellSample neighborSample(
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            CellSample[][] sampledCells,
            int cellX,
            int cellZ,
            BlockFace face) {
        int neighborCellX = cellX + Integer.signum(face.stepX());
        int neighborCellZ = cellZ + Integer.signum(face.stepZ());
        if (neighborCellX >= 0
                && neighborCellX < sampledCells.length
                && neighborCellZ >= 0
                && neighborCellZ < sampledCells[neighborCellX].length) {
            CellSample localNeighbor = sampledCells[neighborCellX][neighborCellZ];
            return new NeighborCellSample(localNeighbor.surfaceHeight(), localNeighbor.visible());
        }

        int neighborWorldStartX = regionCoord.worldStartX(settings) + (neighborCellX * settings.cellSizeBlocks());
        int neighborWorldStartZ = regionCoord.worldStartZ(settings) + (neighborCellZ * settings.cellSizeBlocks());
        int sampleWorldX = neighborWorldStartX + (settings.cellSizeBlocks() / 2);
        int sampleWorldZ = neighborWorldStartZ + (settings.cellSizeBlocks() / 2);
        FarFieldTerrainSampler.ColumnSample neighborSample = terrainSampler.sampleColumn(sampleWorldX, sampleWorldZ);
        BlockId blockId = neighborSample.surfaceBlockId();
        BlockDefinition definition = blockId == null ? null : registries.requireBlockDefinition(blockId);
        boolean visible = definition != null
                && definition.solid()
                && neighborSample.surfaceHeight() >= 0
                && cellIntersectsFarFieldRing(centerChunk, settings, neighborWorldStartX, neighborWorldStartZ);
        return new NeighborCellSample(neighborSample.surfaceHeight(), visible);
    }

    private boolean cellIntersectsFarFieldRing(
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            int cellWorldStartX,
            int cellWorldStartZ) {
        float centerWorldX = centerChunk.x() * ChunkData.SIZE_X + (ChunkData.SIZE_X / 2f);
        float centerWorldZ = centerChunk.z() * ChunkData.SIZE_Z + (ChunkData.SIZE_Z / 2f);
        float cellWorldEndX = cellWorldStartX + settings.cellSizeBlocks();
        float cellWorldEndZ = cellWorldStartZ + settings.cellSizeBlocks();
        float minDeltaX = distanceToRange(centerWorldX, cellWorldStartX, cellWorldEndX) / ChunkData.SIZE_X;
        float minDeltaZ = distanceToRange(centerWorldZ, cellWorldStartZ, cellWorldEndZ) / ChunkData.SIZE_Z;
        float maxDeltaX = Math.max(
                        Math.abs(cellWorldStartX - centerWorldX),
                        Math.abs(cellWorldEndX - centerWorldX))
                / ChunkData.SIZE_X;
        float maxDeltaZ = Math.max(
                        Math.abs(cellWorldStartZ - centerWorldZ),
                        Math.abs(cellWorldEndZ - centerWorldZ))
                / ChunkData.SIZE_Z;
        float minDistanceSquared = (minDeltaX * minDeltaX) + (minDeltaZ * minDeltaZ);
        float maxDistanceSquared = (maxDeltaX * maxDeltaX) + (maxDeltaZ * maxDeltaZ);
        return minDistanceSquared <= (settings.endRadiusChunks() * settings.endRadiusChunks())
                && maxDistanceSquared >= (settings.startRadiusChunks() * settings.startRadiusChunks());
    }

    private float distanceToRange(float value, float minInclusive, float maxInclusive) {
        if (value < minInclusive) {
            return minInclusive - value;
        }
        if (value > maxInclusive) {
            return value - maxInclusive;
        }
        return 0f;
    }

    private ResolvedFaceMaterial resolvedMaterialFor(BlockDefinition definition, BlockFace face) {
        BlockVisualDefinition visuals = definition.visuals();
        if (visuals != null) {
            BlockFaceTextureReference textureReference = visuals.textureReferenceFor(textureFaceFor(face));
            return ResolvedFaceMaterial.textured(terrainTexturePalette.layerIndexFor(textureReference));
        }
        return ResolvedFaceMaterial.debugColor(definition.debugColor());
    }

    private BlockTextureFace textureFaceFor(BlockFace face) {
        return switch (face) {
            case UP -> BlockTextureFace.TOP;
            case DOWN -> BlockTextureFace.BOTTOM;
            case WEST -> BlockTextureFace.LEFT;
            case SOUTH -> BlockTextureFace.FRONT;
            case EAST -> BlockTextureFace.RIGHT;
            case NORTH -> BlockTextureFace.BACK;
        };
    }

    private static final class MeshSectionBuilder {
        private final boolean textured;
        private final FloatCollector positions = new FloatCollector(1_024);
        private final FloatCollector normals = new FloatCollector(1_024);
        private final FloatCollector textureCoordinates = new FloatCollector(1_024);
        private final IntCollector indices = new IntCollector(1_024);
        private int vertexCount;
        private int faceCount;

        private MeshSectionBuilder(boolean textured) {
            this.textured = textured;
        }

        private void appendTopQuad(
                int localStartX,
                int localStartZ,
                int spanX,
                int spanZ,
                int topY,
                int textureLayer) {
            appendQuad(
                    new float[] {
                        localStartX, topY, localStartZ,
                        localStartX, topY, localStartZ + spanZ,
                        localStartX + spanX, topY, localStartZ + spanZ,
                        localStartX + spanX, topY, localStartZ
                    },
                    0f,
                    1f,
                    0f,
                    new float[] {
                        0f, 0f,
                        spanZ, 0f,
                        spanZ, spanX,
                        0f, spanX
                    },
                    textureLayer);
        }

        private void appendSideQuad(
                BlockFace face,
                int localStartX,
                int localStartZ,
                int horizontalSpan,
                int bottomY,
                int topY,
                int textureLayer) {
            int verticalSpan = topY - bottomY;
            switch (face) {
                case WEST -> appendQuad(
                        new float[] {
                            localStartX, bottomY, localStartZ,
                            localStartX, bottomY, localStartZ + horizontalSpan,
                            localStartX, topY, localStartZ + horizontalSpan,
                            localStartX, topY, localStartZ
                        },
                        face.normalX(),
                        face.normalY(),
                        face.normalZ(),
                        new float[] {
                            0f, verticalSpan,
                            horizontalSpan, verticalSpan,
                            horizontalSpan, 0f,
                            0f, 0f
                        },
                        textureLayer);
                case EAST -> appendQuad(
                        new float[] {
                            localStartX + horizontalSpan, bottomY, localStartZ,
                            localStartX + horizontalSpan, topY, localStartZ,
                            localStartX + horizontalSpan, topY, localStartZ + horizontalSpan,
                            localStartX + horizontalSpan, bottomY, localStartZ + horizontalSpan
                        },
                        face.normalX(),
                        face.normalY(),
                        face.normalZ(),
                        new float[] {
                            0f, verticalSpan,
                            0f, 0f,
                            horizontalSpan, 0f,
                            horizontalSpan, verticalSpan
                        },
                        textureLayer);
                case NORTH -> appendQuad(
                        new float[] {
                            localStartX, bottomY, localStartZ,
                            localStartX, topY, localStartZ,
                            localStartX + horizontalSpan, topY, localStartZ,
                            localStartX + horizontalSpan, bottomY, localStartZ
                        },
                        face.normalX(),
                        face.normalY(),
                        face.normalZ(),
                        new float[] {
                            0f, verticalSpan,
                            0f, 0f,
                            horizontalSpan, 0f,
                            horizontalSpan, verticalSpan
                        },
                        textureLayer);
                case SOUTH -> appendQuad(
                        new float[] {
                            localStartX, bottomY, localStartZ + horizontalSpan,
                            localStartX + horizontalSpan, bottomY, localStartZ + horizontalSpan,
                            localStartX + horizontalSpan, topY, localStartZ + horizontalSpan,
                            localStartX, topY, localStartZ + horizontalSpan
                        },
                        face.normalX(),
                        face.normalY(),
                        face.normalZ(),
                        new float[] {
                            0f, verticalSpan,
                            horizontalSpan, verticalSpan,
                            horizontalSpan, 0f,
                            0f, 0f
                        },
                        textureLayer);
                case UP, DOWN -> throw new IllegalArgumentException("Far-field side quad requires a horizontal face.");
            }
        }

        private void appendQuad(
                float[] quadPositions,
                float normalX,
                float normalY,
                float normalZ,
                float[] quadTextureCoordinates,
                int textureLayer) {
            for (float positionComponent : quadPositions) {
                positions.add(positionComponent);
            }

            for (int vertexIndex = 0; vertexIndex < 4; vertexIndex++) {
                normals.add(normalX);
                normals.add(normalY);
                normals.add(normalZ);
            }

            if (textured) {
                for (int coordinateIndex = 0; coordinateIndex < quadTextureCoordinates.length; coordinateIndex += 2) {
                    textureCoordinates.add(quadTextureCoordinates[coordinateIndex]);
                    textureCoordinates.add(quadTextureCoordinates[coordinateIndex + 1]);
                    textureCoordinates.add(textureLayer);
                }
            } else {
                for (float coordinate : quadTextureCoordinates) {
                    textureCoordinates.add(coordinate);
                }
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
                    textured ? 3 : 2,
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

    private record CellSample(int surfaceHeight, BlockDefinition definition, boolean visible) {
    }

    private record NeighborCellSample(int surfaceHeight, boolean visible) {
    }

    private record ResolvedFaceMaterial(TerrainMaterialKey sectionKey, int textureLayer) {
        private static ResolvedFaceMaterial textured(int textureLayer) {
            return new ResolvedFaceMaterial(SHARED_TEXTURED_MATERIAL_KEY, textureLayer);
        }

        private static ResolvedFaceMaterial debugColor(String debugColor) {
            return new ResolvedFaceMaterial(TerrainMaterialKey.debugColor(debugColor), -1);
        }
    }
}
