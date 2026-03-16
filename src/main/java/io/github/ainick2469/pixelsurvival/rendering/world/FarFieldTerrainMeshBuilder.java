package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
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
            FarFieldTerrainTarget target,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings) {
        if (centerChunk == null) {
            return new FarFieldTerrainMeshBuildResult(target.regionCoord(), Map.of(), 0, 0);
        }
        int cellsPerAxis = settings.cellsPerRegionAxis();
        boolean usePatchTops = settings.cellSizeBlocks() >= 16;
        CellSample[][] cells = new CellSample[cellsPerAxis][cellsPerAxis];
        int visibleCellCount = 0;
        for (int cellX = 0; cellX < cellsPerAxis; cellX++) {
            for (int cellZ = 0; cellZ < cellsPerAxis; cellZ++) {
                cells[cellX][cellZ] = sampleCell(target, centerChunk, settings, cellX, cellZ, usePatchTops);
                if (cells[cellX][cellZ].visible()) {
                    visibleCellCount++;
                }
            }
        }
        if (visibleCellCount == 0) {
            return new FarFieldTerrainMeshBuildResult(target.regionCoord(), Map.of(), 0, 0);
        }

        Map<TerrainMaterialKey, FarFieldMeshSectionBuilder> sectionBuilders = new LinkedHashMap<>();
        int emittedFaceCount = 0;
        for (int cellX = 0; cellX < cellsPerAxis; cellX++) {
            for (int cellZ = 0; cellZ < cellsPerAxis; cellZ++) {
                CellSample cell = cells[cellX][cellZ];
                if (!cell.visible()) {
                    continue;
                }
                emittedFaceCount += usePatchTops && cell.patchSample() != null
                        ? appendPatchedCell(sectionBuilders, target.regionCoord(), centerChunk, settings, cells, cellX, cellZ, cell)
                        : appendFlatCell(sectionBuilders, target.regionCoord(), centerChunk, settings, cells, cellX, cellZ, cell);
            }
        }

        Map<TerrainMaterialKey, ChunkMeshSectionData> sections = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialKey, FarFieldMeshSectionBuilder> entry : sectionBuilders.entrySet()) {
            ChunkMeshSectionData section = entry.getValue().build();
            if (section.faceCount() > 0) {
                sections.put(entry.getKey(), section);
            }
        }
        return new FarFieldTerrainMeshBuildResult(target.regionCoord(), Map.copyOf(sections), visibleCellCount, emittedFaceCount);
    }

    private int appendFlatCell(
            Map<TerrainMaterialKey, FarFieldMeshSectionBuilder> sectionBuilders,
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            CellSample[][] cells,
            int cellX,
            int cellZ,
            CellSample cell) {
        int cellSize = settings.cellSizeBlocks();
        int localStartX = cellX * cellSize;
        int localStartZ = cellZ * cellSize;
        ResolvedFaceMaterial topMaterial = resolvedMaterialFor(cell.definition(), BlockFace.UP);
        sectionBuilders.computeIfAbsent(topMaterial.sectionKey(), key -> new FarFieldMeshSectionBuilder(key.usesTexture()))
                .appendQuad(
                        quadPositions(localStartX, cell.surfaceHeight() + 1f, localStartZ, cellSize, cellSize),
                        uniformNormals(BlockFace.UP),
                        new float[] {0f, 0f, cellSize, 0f, cellSize, cellSize, 0f, cellSize},
                        seamWeights(regionCoord, centerChunk, settings, new float[][] {
                            {localStartX, localStartZ},
                            {localStartX, localStartZ + cellSize},
                            {localStartX + cellSize, localStartZ + cellSize},
                            {localStartX + cellSize, localStartZ}
                        }),
                        topMaterial.textureLayer(),
                        1);
        int emitted = 1;
        emitted += appendFlatSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.WEST);
        emitted += appendFlatSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.EAST);
        emitted += appendFlatSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.NORTH);
        emitted += appendFlatSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.SOUTH);
        return emitted;
    }

    private int appendPatchedCell(
            Map<TerrainMaterialKey, FarFieldMeshSectionBuilder> sectionBuilders,
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            CellSample[][] cells,
            int cellX,
            int cellZ,
            CellSample cell) {
        int cellSize = settings.cellSizeBlocks();
        int subCellSize = cellSize / 2;
        int localStartX = cellX * cellSize;
        int localStartZ = cellZ * cellSize;
        PatchSample patch = cell.patchSample();
        ResolvedFaceMaterial topMaterial = resolvedMaterialFor(cell.definition(), BlockFace.UP);
        FarFieldMeshSectionBuilder topBuilder =
                sectionBuilders.computeIfAbsent(topMaterial.sectionKey(), key -> new FarFieldMeshSectionBuilder(key.usesTexture()));
        int emitted = 0;
        for (int patchX = 0; patchX < 2; patchX++) {
            for (int patchZ = 0; patchZ < 2; patchZ++) {
                int quadX = localStartX + (patchX * subCellSize);
                int quadZ = localStartZ + (patchZ * subCellSize);
                topBuilder.appendQuad(
                        new float[] {
                            quadX, patch.height(patchX, patchZ), quadZ,
                            quadX, patch.height(patchX, patchZ + 1), quadZ + subCellSize,
                            quadX + subCellSize, patch.height(patchX + 1, patchZ + 1), quadZ + subCellSize,
                            quadX + subCellSize, patch.height(patchX + 1, patchZ), quadZ
                        },
                        new float[] {
                            patch.normalX(patchX, patchZ), patch.normalY(patchX, patchZ), patch.normalZ(patchX, patchZ),
                            patch.normalX(patchX, patchZ + 1), patch.normalY(patchX, patchZ + 1), patch.normalZ(patchX, patchZ + 1),
                            patch.normalX(patchX + 1, patchZ + 1), patch.normalY(patchX + 1, patchZ + 1), patch.normalZ(patchX + 1, patchZ + 1),
                            patch.normalX(patchX + 1, patchZ), patch.normalY(patchX + 1, patchZ), patch.normalZ(patchX + 1, patchZ)
                        },
                        new float[] {
                            quadZ, quadX,
                            quadZ + subCellSize, quadX,
                            quadZ + subCellSize, quadX + subCellSize,
                            quadZ, quadX + subCellSize
                        },
                        new float[] {
                            patch.seamWeight(patchX, patchZ),
                            patch.seamWeight(patchX, patchZ + 1),
                            patch.seamWeight(patchX + 1, patchZ + 1),
                            patch.seamWeight(patchX + 1, patchZ)
                        },
                        topMaterial.textureLayer(),
                        2);
                emitted += 2;
            }
        }

        emitted += appendPatchedSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.WEST);
        emitted += appendPatchedSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.EAST);
        emitted += appendPatchedSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.NORTH);
        emitted += appendPatchedSide(sectionBuilders, regionCoord, centerChunk, settings, cells, cellX, cellZ, cell, BlockFace.SOUTH);
        return emitted;
    }

    private int appendFlatSide(
            Map<TerrainMaterialKey, FarFieldMeshSectionBuilder> sectionBuilders,
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            CellSample[][] cells,
            int cellX,
            int cellZ,
            CellSample cell,
            BlockFace face) {
        NeighborCellSample neighbor = neighborSample(regionCoord, centerChunk, settings, cells, cellX, cellZ, face);
        if (!neighbor.visible() && shouldSuppressInnerBoundarySide(regionCoord, centerChunk, settings, cellX, cellZ, face)) {
            return 0;
        }
        float topY = cell.surfaceHeight() + 1f;
        float bottomY = Math.max(settings.skirtFloorY(), neighbor.surfaceHeight() + 1f);
        if (topY <= bottomY) {
            return 0;
        }
        int cellSize = settings.cellSizeBlocks();
        int localStartX = cellX * cellSize;
        int localStartZ = cellZ * cellSize;
        ResolvedFaceMaterial material = resolvedMaterialFor(cell.definition(), face);
        sectionBuilders.computeIfAbsent(material.sectionKey(), key -> new FarFieldMeshSectionBuilder(key.usesTexture()))
                .appendQuad(
                        sideQuad(face, localStartX, localStartZ, cellSize, bottomY, topY),
                        uniformNormals(face),
                        sideUv(face, cellSize, topY - bottomY),
                        sideSeamWeights(regionCoord, centerChunk, settings, face, localStartX, localStartZ, cellSize),
                        material.textureLayer(),
                        1);
        return 1;
    }

    private int appendPatchedSide(
            Map<TerrainMaterialKey, FarFieldMeshSectionBuilder> sectionBuilders,
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            CellSample[][] cells,
            int cellX,
            int cellZ,
            CellSample cell,
            BlockFace face) {
        if (neighborIsVisible(cells, cellX, cellZ, face)) {
            return 0;
        }
        if (shouldSuppressInnerBoundarySide(regionCoord, centerChunk, settings, cellX, cellZ, face)) {
            return 0;
        }
        int cellSize = settings.cellSizeBlocks();
        int subCellSize = cellSize / 2;
        int localStartX = cellX * cellSize;
        int localStartZ = cellZ * cellSize;
        int worldStartX = regionCoord.worldStartX(settings) + localStartX;
        int worldStartZ = regionCoord.worldStartZ(settings) + localStartZ;
        ResolvedFaceMaterial material = resolvedMaterialFor(cell.definition(), face);
        FarFieldMeshSectionBuilder builder =
                sectionBuilders.computeIfAbsent(material.sectionKey(), key -> new FarFieldMeshSectionBuilder(key.usesTexture()));
        int emitted = 0;
        for (int segment = 0; segment < 2; segment++) {
            SegmentQuad quad = segmentQuad(
                    regionCoord,
                    centerChunk,
                    settings,
                    face,
                    localStartX,
                    localStartZ,
                    worldStartX,
                    worldStartZ,
                    subCellSize,
                    segment,
                    cell.patchSample());
            if (quad == null) {
                continue;
            }
            builder.appendQuad(quad.positions(), uniformNormals(face), quad.uvs(), quad.seamWeights(), material.textureLayer(), 1);
            emitted++;
        }
        return emitted;
    }

    private CellSample sampleCell(
            FarFieldTerrainTarget target,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            int cellX,
            int cellZ,
            boolean usePatchTops) {
        int worldStartX = target.regionCoord().worldStartX(settings) + (cellX * settings.cellSizeBlocks());
        int worldStartZ = target.regionCoord().worldStartZ(settings) + (cellZ * settings.cellSizeBlocks());
        int sampleWorldX = worldStartX + (settings.cellSizeBlocks() / 2);
        int sampleWorldZ = worldStartZ + (settings.cellSizeBlocks() / 2);
        FarFieldTerrainSampler.ColumnSample sample = terrainSampler.sampleColumn(sampleWorldX, sampleWorldZ);
        BlockId blockId = sample.surfaceBlockId();
        BlockDefinition definition = blockId == null ? null : registries.requireBlockDefinition(blockId);
        boolean visible = definition != null
                && definition.solid()
                && sample.surfaceHeight() >= 0
                && (!target.requiresBoundaryClipping() || cellIntersectsFarFieldRing(centerChunk, settings, worldStartX, worldStartZ));
        return new CellSample(
                sample.surfaceHeight(),
                definition,
                visible,
                visible && usePatchTops ? samplePatch(worldStartX, worldStartZ, centerChunk, settings) : null);
    }

    private PatchSample samplePatch(int worldStartX, int worldStartZ, ChunkCoord centerChunk, FarFieldTerrainSettings settings) {
        float[] heights = new float[9];
        float[] seamWeights = new float[9];
        float[] normals = new float[27];
        int sampleSpacing = settings.cellSizeBlocks() / 2;
        for (int gridX = 0; gridX < 3; gridX++) {
            for (int gridZ = 0; gridZ < 3; gridZ++) {
                int worldX = worldStartX + (gridX * sampleSpacing);
                int worldZ = worldStartZ + (gridZ * sampleSpacing);
                int sampleIndex = patchIndex(gridX, gridZ);
                heights[sampleIndex] = sampleSurfaceTopY(worldX, worldZ);
                seamWeights[sampleIndex] = seamWeightForWorld(centerChunk, settings, worldX, worldZ);
            }
        }
        for (int gridX = 0; gridX < 3; gridX++) {
            for (int gridZ = 0; gridZ < 3; gridZ++) {
                float left = heights[patchIndex(Math.max(0, gridX - 1), gridZ)];
                float right = heights[patchIndex(Math.min(2, gridX + 1), gridZ)];
                float back = heights[patchIndex(gridX, Math.max(0, gridZ - 1))];
                float front = heights[patchIndex(gridX, Math.min(2, gridZ + 1))];
                Vector3f normal = new Vector3f(left - right, sampleSpacing, back - front).normalizeLocal();
                int normalIndex = patchIndex(gridX, gridZ) * 3;
                normals[normalIndex] = normal.x;
                normals[normalIndex + 1] = normal.y;
                normals[normalIndex + 2] = normal.z;
            }
        }
        return new PatchSample(heights, seamWeights, normals);
    }

    private SegmentQuad segmentQuad(
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            BlockFace face,
            int localStartX,
            int localStartZ,
            int worldStartX,
            int worldStartZ,
            int sampleSpacing,
            int segment,
            PatchSample patch) {
        float localX0;
        float localZ0;
        float localX1;
        float localZ1;
        int neighborWorldX0;
        int neighborWorldZ0;
        int neighborWorldX1;
        int neighborWorldZ1;
        float topY0;
        float topY1;
        float seam0;
        float seam1;
        switch (face) {
            case WEST -> {
                localX0 = localStartX;
                localZ0 = localStartZ + (segment * sampleSpacing);
                localX1 = localStartX;
                localZ1 = localStartZ + ((segment + 1) * sampleSpacing);
                neighborWorldX0 = worldStartX - 1;
                neighborWorldZ0 = worldStartZ + (segment * sampleSpacing);
                neighborWorldX1 = worldStartX - 1;
                neighborWorldZ1 = worldStartZ + ((segment + 1) * sampleSpacing);
                topY0 = patch.height(0, segment);
                topY1 = patch.height(0, segment + 1);
                seam0 = patch.seamWeight(0, segment);
                seam1 = patch.seamWeight(0, segment + 1);
            }
            case EAST -> {
                localX0 = localStartX + (sampleSpacing * 2);
                localZ0 = localStartZ + (segment * sampleSpacing);
                localX1 = localStartX + (sampleSpacing * 2);
                localZ1 = localStartZ + ((segment + 1) * sampleSpacing);
                neighborWorldX0 = worldStartX + (sampleSpacing * 2);
                neighborWorldZ0 = worldStartZ + (segment * sampleSpacing);
                neighborWorldX1 = worldStartX + (sampleSpacing * 2);
                neighborWorldZ1 = worldStartZ + ((segment + 1) * sampleSpacing);
                topY0 = patch.height(2, segment);
                topY1 = patch.height(2, segment + 1);
                seam0 = patch.seamWeight(2, segment);
                seam1 = patch.seamWeight(2, segment + 1);
            }
            case NORTH -> {
                localX0 = localStartX + (segment * sampleSpacing);
                localZ0 = localStartZ;
                localX1 = localStartX + ((segment + 1) * sampleSpacing);
                localZ1 = localStartZ;
                neighborWorldX0 = worldStartX + (segment * sampleSpacing);
                neighborWorldZ0 = worldStartZ - 1;
                neighborWorldX1 = worldStartX + ((segment + 1) * sampleSpacing);
                neighborWorldZ1 = worldStartZ - 1;
                topY0 = patch.height(segment, 0);
                topY1 = patch.height(segment + 1, 0);
                seam0 = patch.seamWeight(segment, 0);
                seam1 = patch.seamWeight(segment + 1, 0);
            }
            case SOUTH -> {
                localX0 = localStartX + (segment * sampleSpacing);
                localZ0 = localStartZ + (sampleSpacing * 2);
                localX1 = localStartX + ((segment + 1) * sampleSpacing);
                localZ1 = localStartZ + (sampleSpacing * 2);
                neighborWorldX0 = worldStartX + (segment * sampleSpacing);
                neighborWorldZ0 = worldStartZ + (sampleSpacing * 2);
                neighborWorldX1 = worldStartX + ((segment + 1) * sampleSpacing);
                neighborWorldZ1 = worldStartZ + (sampleSpacing * 2);
                topY0 = patch.height(segment, 2);
                topY1 = patch.height(segment + 1, 2);
                seam0 = patch.seamWeight(segment, 2);
                seam1 = patch.seamWeight(segment + 1, 2);
            }
            case UP, DOWN -> throw new IllegalArgumentException("Vertical faces are not valid patch side faces.");
            default -> throw new IllegalArgumentException("Unsupported patch side face: " + face);
        }
        float bottomY0 = sampleSurfaceTopY(neighborWorldX0, neighborWorldZ0);
        float bottomY1 = sampleSurfaceTopY(neighborWorldX1, neighborWorldZ1);
        if (topY0 <= bottomY0 && topY1 <= bottomY1) {
            return null;
        }
        return new SegmentQuad(
                new float[] {localX0, bottomY0, localZ0, localX1, bottomY1, localZ1, localX1, topY1, localZ1, localX0, topY0, localZ0},
                new float[] {0f, topY0 - bottomY0, sampleSpacing, topY1 - bottomY1, sampleSpacing, 0f, 0f, 0f},
                new float[] {seam0, seam1, seam1, seam0});
    }

    private boolean neighborIsVisible(CellSample[][] cells, int cellX, int cellZ, BlockFace face) {
        int neighborCellX = cellX + Integer.signum(face.stepX());
        int neighborCellZ = cellZ + Integer.signum(face.stepZ());
        return neighborCellX >= 0
                && neighborCellX < cells.length
                && neighborCellZ >= 0
                && neighborCellZ < cells[neighborCellX].length
                && cells[neighborCellX][neighborCellZ].visible();
    }

    private NeighborCellSample neighborSample(
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            CellSample[][] cells,
            int cellX,
            int cellZ,
            BlockFace face) {
        int neighborCellX = cellX + Integer.signum(face.stepX());
        int neighborCellZ = cellZ + Integer.signum(face.stepZ());
        if (neighborCellX >= 0 && neighborCellX < cells.length && neighborCellZ >= 0 && neighborCellZ < cells[neighborCellX].length) {
            CellSample neighbor = cells[neighborCellX][neighborCellZ];
            return new NeighborCellSample(neighbor.surfaceHeight(), neighbor.visible());
        }
        int neighborWorldStartX = regionCoord.worldStartX(settings) + (neighborCellX * settings.cellSizeBlocks());
        int neighborWorldStartZ = regionCoord.worldStartZ(settings) + (neighborCellZ * settings.cellSizeBlocks());
        int sampleWorldX = neighborWorldStartX + (settings.cellSizeBlocks() / 2);
        int sampleWorldZ = neighborWorldStartZ + (settings.cellSizeBlocks() / 2);
        FarFieldTerrainSampler.ColumnSample neighborSample = terrainSampler.sampleColumn(sampleWorldX, sampleWorldZ);
        boolean visible = neighborSample.surfaceBlockId() != null
                && neighborSample.surfaceHeight() >= 0
                && cellIntersectsFarFieldRing(centerChunk, settings, neighborWorldStartX, neighborWorldStartZ);
        return new NeighborCellSample(neighborSample.surfaceHeight(), visible);
    }

    private boolean shouldSuppressInnerBoundarySide(
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            int cellX,
            int cellZ,
            BlockFace face) {
        if (settings.renderInnerBoundarySkirts() || centerChunk == null) {
            return false;
        }
        int neighborCellX = cellX + Integer.signum(face.stepX());
        int neighborCellZ = cellZ + Integer.signum(face.stepZ());
        float centerWorldX = centerChunk.x() * ChunkData.SIZE_X + (ChunkData.SIZE_X / 2f);
        float centerWorldZ = centerChunk.z() * ChunkData.SIZE_Z + (ChunkData.SIZE_Z / 2f);
        float neighborCenterWorldX =
                regionCoord.worldStartX(settings) + (neighborCellX * settings.cellSizeBlocks()) + (settings.cellSizeBlocks() / 2f);
        float neighborCenterWorldZ =
                regionCoord.worldStartZ(settings) + (neighborCellZ * settings.cellSizeBlocks()) + (settings.cellSizeBlocks() / 2f);
        float deltaChunkX = (neighborCenterWorldX - centerWorldX) / ChunkData.SIZE_X;
        float deltaChunkZ = (neighborCenterWorldZ - centerWorldZ) / ChunkData.SIZE_Z;
        float neighborDistanceSquared = (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
        return neighborDistanceSquared <= (settings.startRadiusChunks() * settings.startRadiusChunks());
    }

    private boolean cellIntersectsFarFieldRing(ChunkCoord centerChunk, FarFieldTerrainSettings settings, int cellWorldStartX, int cellWorldStartZ) {
        float centerWorldX = centerChunk.x() * ChunkData.SIZE_X + (ChunkData.SIZE_X / 2f);
        float centerWorldZ = centerChunk.z() * ChunkData.SIZE_Z + (ChunkData.SIZE_Z / 2f);
        float cellWorldEndX = cellWorldStartX + settings.cellSizeBlocks();
        float cellWorldEndZ = cellWorldStartZ + settings.cellSizeBlocks();
        float minDeltaX = distanceToRange(centerWorldX, cellWorldStartX, cellWorldEndX) / ChunkData.SIZE_X;
        float minDeltaZ = distanceToRange(centerWorldZ, cellWorldStartZ, cellWorldEndZ) / ChunkData.SIZE_Z;
        float maxDeltaX = Math.max(Math.abs(cellWorldStartX - centerWorldX), Math.abs(cellWorldEndX - centerWorldX)) / ChunkData.SIZE_X;
        float maxDeltaZ = Math.max(Math.abs(cellWorldStartZ - centerWorldZ), Math.abs(cellWorldEndZ - centerWorldZ)) / ChunkData.SIZE_Z;
        float minDistanceSquared = (minDeltaX * minDeltaX) + (minDeltaZ * minDeltaZ);
        float maxDistanceSquared = (maxDeltaX * maxDeltaX) + (maxDeltaZ * maxDeltaZ);
        return minDistanceSquared <= (settings.endRadiusChunks() * settings.endRadiusChunks())
                && maxDistanceSquared >= (settings.startRadiusChunks() * settings.startRadiusChunks());
    }

    private float sampleSurfaceTopY(int worldX, int worldZ) {
        FarFieldTerrainSampler.ColumnSample sample = terrainSampler.sampleColumn(worldX, worldZ);
        if (sample.surfaceBlockId() == null || sample.surfaceHeight() < 0) {
            return 0f;
        }
        return sample.surfaceHeight() + 1f;
    }

    private float seamWeightForWorld(ChunkCoord centerChunk, FarFieldTerrainSettings settings, float worldX, float worldZ) {
        if (settings.endRadiusChunks() <= 96 || settings.overlapChunks() < 2) {
            return 0f;
        }
        float centerWorldX = centerChunk.x() * ChunkData.SIZE_X + (ChunkData.SIZE_X / 2f);
        float centerWorldZ = centerChunk.z() * ChunkData.SIZE_Z + (ChunkData.SIZE_Z / 2f);
        float deltaX = worldX - centerWorldX;
        float deltaZ = worldZ - centerWorldZ;
        float distanceChunks = FastMath.sqrt((deltaX * deltaX) + (deltaZ * deltaZ)) / ChunkData.SIZE_X;
        float blendWidth = Math.max(1f, settings.overlapChunks() * 0.5f);
        return FastMath.clamp(1f - ((distanceChunks - settings.startRadiusChunks()) / blendWidth), 0f, 1f);
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
            BlockFaceTextureReference reference = visuals.textureReferenceFor(textureFaceFor(face));
            return ResolvedFaceMaterial.textured(terrainTexturePalette.layerIndexFor(reference));
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

    private float[] quadPositions(float localStartX, float topY, float localStartZ, int spanX, int spanZ) {
        return new float[] {
            localStartX, topY, localStartZ,
            localStartX, topY, localStartZ + spanZ,
            localStartX + spanX, topY, localStartZ + spanZ,
            localStartX + spanX, topY, localStartZ
        };
    }

    private float[] sideQuad(BlockFace face, int localStartX, int localStartZ, int cellSize, float bottomY, float topY) {
        return switch (face) {
            case WEST -> new float[] {localStartX, bottomY, localStartZ, localStartX, bottomY, localStartZ + cellSize, localStartX, topY, localStartZ + cellSize, localStartX, topY, localStartZ};
            case EAST -> new float[] {localStartX + cellSize, bottomY, localStartZ, localStartX + cellSize, topY, localStartZ, localStartX + cellSize, topY, localStartZ + cellSize, localStartX + cellSize, bottomY, localStartZ + cellSize};
            case NORTH -> new float[] {localStartX, bottomY, localStartZ, localStartX, topY, localStartZ, localStartX + cellSize, topY, localStartZ, localStartX + cellSize, bottomY, localStartZ};
            case SOUTH -> new float[] {localStartX, bottomY, localStartZ + cellSize, localStartX + cellSize, bottomY, localStartZ + cellSize, localStartX + cellSize, topY, localStartZ + cellSize, localStartX, topY, localStartZ + cellSize};
            case UP, DOWN -> throw new IllegalArgumentException("Vertical faces are not valid side faces.");
        };
    }

    private float[] sideUv(BlockFace face, int horizontalSpan, float verticalSpan) {
        return switch (face) {
            case WEST, SOUTH -> new float[] {0f, verticalSpan, horizontalSpan, verticalSpan, horizontalSpan, 0f, 0f, 0f};
            case EAST, NORTH -> new float[] {0f, verticalSpan, 0f, 0f, horizontalSpan, 0f, horizontalSpan, verticalSpan};
            case UP, DOWN -> throw new IllegalArgumentException("Vertical faces are not valid side faces.");
        };
    }

    private float[] uniformNormals(BlockFace face) {
        return new float[] {
            face.normalX(), face.normalY(), face.normalZ(),
            face.normalX(), face.normalY(), face.normalZ(),
            face.normalX(), face.normalY(), face.normalZ(),
            face.normalX(), face.normalY(), face.normalZ()
        };
    }

    private float[] seamWeights(FarFieldTerrainRegionCoord regionCoord, ChunkCoord centerChunk, FarFieldTerrainSettings settings, float[][] localPoints) {
        float[] seamWeights = new float[localPoints.length];
        for (int pointIndex = 0; pointIndex < localPoints.length; pointIndex++) {
            float worldX = regionCoord.worldStartX(settings) + localPoints[pointIndex][0];
            float worldZ = regionCoord.worldStartZ(settings) + localPoints[pointIndex][1];
            seamWeights[pointIndex] = seamWeightForWorld(centerChunk, settings, worldX, worldZ);
        }
        return seamWeights;
    }

    private float[] sideSeamWeights(
            FarFieldTerrainRegionCoord regionCoord,
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            BlockFace face,
            int localStartX,
            int localStartZ,
            int cellSize) {
        return switch (face) {
            case WEST -> seamWeights(regionCoord, centerChunk, settings, new float[][] {{localStartX, localStartZ}, {localStartX, localStartZ + cellSize}, {localStartX, localStartZ + cellSize}, {localStartX, localStartZ}});
            case EAST -> seamWeights(regionCoord, centerChunk, settings, new float[][] {{localStartX + cellSize, localStartZ}, {localStartX + cellSize, localStartZ}, {localStartX + cellSize, localStartZ + cellSize}, {localStartX + cellSize, localStartZ + cellSize}});
            case NORTH -> seamWeights(regionCoord, centerChunk, settings, new float[][] {{localStartX, localStartZ}, {localStartX, localStartZ}, {localStartX + cellSize, localStartZ}, {localStartX + cellSize, localStartZ}});
            case SOUTH -> seamWeights(regionCoord, centerChunk, settings, new float[][] {{localStartX, localStartZ + cellSize}, {localStartX + cellSize, localStartZ + cellSize}, {localStartX + cellSize, localStartZ + cellSize}, {localStartX, localStartZ + cellSize}});
            case UP, DOWN -> throw new IllegalArgumentException("Vertical faces are not valid side faces.");
        };
    }

    private static int patchIndex(int gridX, int gridZ) {
        return (gridX * 3) + gridZ;
    }

    private record CellSample(int surfaceHeight, BlockDefinition definition, boolean visible, PatchSample patchSample) {}
    private record NeighborCellSample(int surfaceHeight, boolean visible) {}
    private record SegmentQuad(float[] positions, float[] uvs, float[] seamWeights) {}
    private record ResolvedFaceMaterial(TerrainMaterialKey sectionKey, int textureLayer) {
        private static ResolvedFaceMaterial textured(int textureLayer) {
            return new ResolvedFaceMaterial(SHARED_TEXTURED_MATERIAL_KEY, textureLayer);
        }

        private static ResolvedFaceMaterial debugColor(String debugColor) {
            return new ResolvedFaceMaterial(TerrainMaterialKey.debugColor(debugColor), -1);
        }
    }

    private static final class PatchSample {
        private final float[] heights;
        private final float[] seamWeights;
        private final float[] normals;

        private PatchSample(float[] heights, float[] seamWeights, float[] normals) {
            this.heights = heights;
            this.seamWeights = seamWeights;
            this.normals = normals;
        }

        private float height(int gridX, int gridZ) {
            return heights[patchIndex(gridX, gridZ)];
        }

        private float seamWeight(int gridX, int gridZ) {
            return seamWeights[patchIndex(gridX, gridZ)];
        }

        private float normalX(int gridX, int gridZ) {
            return normals[patchIndex(gridX, gridZ) * 3];
        }

        private float normalY(int gridX, int gridZ) {
            return normals[(patchIndex(gridX, gridZ) * 3) + 1];
        }

        private float normalZ(int gridX, int gridZ) {
            return normals[(patchIndex(gridX, gridZ) * 3) + 2];
        }
    }
}
