package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockTextureFace;
import io.github.ainick2469.pixelsurvival.world.block.BlockVisualDefinition;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.sim.AuthoritativeWorldService;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ChunkMeshBuilder {
    private static final Map<BlockFace, FaceGeometry> FACE_GEOMETRY = buildFaceGeometry();

    private final AuthoritativeWorldService worldService;
    private final GameRegistries registries;

    public ChunkMeshBuilder(AuthoritativeWorldService worldService, GameRegistries registries) {
        this.worldService = worldService;
        this.registries = registries;
    }

    public ChunkMeshBuildResult buildChunkMesh(ChunkData chunkData) {
        return buildChunkMesh(chunkData, ChunkDetailLevel.FULL);
    }

    public ChunkMeshBuildResult buildChunkMesh(ChunkData chunkData, ChunkDetailLevel detailLevel) {
        return switch (detailLevel) {
            case FULL -> buildFullDetailMesh(chunkData);
            case SURFACE -> buildSurfaceDetailMesh(chunkData);
            case HORIZON -> buildHorizonDetailMesh(chunkData);
        };
    }

    private ChunkMeshBuildResult buildFullDetailMesh(ChunkData chunkData) {
        Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders = new LinkedHashMap<>();
        boolean[] visibleBlocks = new boolean[ChunkData.SIZE_X * ChunkData.SIZE_Y * ChunkData.SIZE_Z];
        int emittedFaceCount = 0;

        for (BlockFace face : BlockFace.values()) {
            emittedFaceCount += appendGreedyFaces(chunkData, face, sectionBuilders, visibleBlocks);
        }

        int visibleBlockCount = 0;
        for (boolean visible : visibleBlocks) {
            if (visible) {
                visibleBlockCount++;
            }
        }

        return buildResult(chunkData, ChunkDetailLevel.FULL, sectionBuilders, visibleBlockCount, emittedFaceCount);
    }

    private ChunkMeshBuildResult buildSurfaceDetailMesh(ChunkData chunkData) {
        ColumnSurface[][] surfaces = collectColumnSurfaces(chunkData);
        int visibleColumnCount = countVisibleColumns(surfaces);
        Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders = new LinkedHashMap<>();

        int emittedFaceCount = appendSurfaceTopFaces(surfaces, sectionBuilders);
        emittedFaceCount += appendSurfaceSideFaces(chunkData, surfaces, sectionBuilders);

        return buildResult(chunkData, ChunkDetailLevel.SURFACE, sectionBuilders, visibleColumnCount, emittedFaceCount);
    }

    private ChunkMeshBuildResult buildHorizonDetailMesh(ChunkData chunkData) {
        HorizonCell[][] cells = collectHorizonCells(chunkData, 4);
        int visibleCellCount = countVisibleCells(cells);
        Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders = new LinkedHashMap<>();

        int emittedFaceCount = appendHorizonTopFaces(cells, sectionBuilders);
        emittedFaceCount += appendHorizonSideFaces(chunkData, cells, sectionBuilders);

        return buildResult(chunkData, ChunkDetailLevel.HORIZON, sectionBuilders, visibleCellCount, emittedFaceCount);
    }

    private ChunkMeshBuildResult buildResult(
            ChunkData chunkData,
            ChunkDetailLevel detailLevel,
            Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders,
            int visibleBlockCount,
            int emittedFaceCount) {
        Map<TerrainMaterialKey, ChunkMeshSectionData> sections = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialKey, MeshSectionBuilder> entry : sectionBuilders.entrySet()) {
            ChunkMeshSectionData sectionData = entry.getValue().build();
            if (sectionData.faceCount() > 0) {
                sections.put(entry.getKey(), sectionData);
            }
        }

        return new ChunkMeshBuildResult(
                chunkData.chunkCoord(),
                detailLevel,
                Map.copyOf(sections),
                visibleBlockCount,
                emittedFaceCount);
    }

    private int appendGreedyFaces(
            ChunkData chunkData,
            BlockFace face,
            Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders,
            boolean[] visibleBlocks) {
        FaceGeometry geometry = FACE_GEOMETRY.get(face);
        TerrainMaterialKey[] mask = new TerrainMaterialKey[geometry.uSize() * geometry.vSize()];
        int emittedQuads = 0;

        for (int slice = 0; slice < geometry.sliceCount(); slice++) {
            Arrays.fill(mask, null);
            fillMask(chunkData, face, geometry, slice, mask, visibleBlocks);

            for (int v = 0; v < geometry.vSize(); v++) {
                for (int u = 0; u < geometry.uSize(); ) {
                    int maskIndex = (v * geometry.uSize()) + u;
                    TerrainMaterialKey materialKey = mask[maskIndex];
                    if (materialKey == null) {
                        u++;
                        continue;
                    }

                    int width = 1;
                    while (u + width < geometry.uSize()
                            && materialKey.equals(mask[(v * geometry.uSize()) + u + width])) {
                        width++;
                    }

                    int height = 1;
                    scanHeight:
                    while (v + height < geometry.vSize()) {
                        for (int scanU = 0; scanU < width; scanU++) {
                            if (!materialKey.equals(mask[((v + height) * geometry.uSize()) + u + scanU])) {
                                break scanHeight;
                            }
                        }
                        height++;
                    }

                    sectionBuilders
                            .computeIfAbsent(materialKey, ignored -> new MeshSectionBuilder())
                            .appendQuad(geometry, slice, u, v, width, height, face);
                    emittedQuads++;

                    for (int clearV = 0; clearV < height; clearV++) {
                        for (int clearU = 0; clearU < width; clearU++) {
                            mask[((v + clearV) * geometry.uSize()) + u + clearU] = null;
                        }
                    }

                    u += width;
                }
            }
        }

        return emittedQuads;
    }

    private void fillMask(
            ChunkData chunkData,
            BlockFace face,
            FaceGeometry geometry,
            int slice,
            TerrainMaterialKey[] mask,
            boolean[] visibleBlocks) {
        for (int v = 0; v < geometry.vSize(); v++) {
            for (int u = 0; u < geometry.uSize(); u++) {
                int blockX = geometry.coordinateForAxis(0, slice, u, v);
                int blockY = geometry.coordinateForAxis(1, slice, u, v);
                int blockZ = geometry.coordinateForAxis(2, slice, u, v);
                mask[(v * geometry.uSize()) + u] =
                        visibleFaceMaterialKey(chunkData, face, blockX, blockY, blockZ, visibleBlocks);
            }
        }
    }

    private ColumnSurface[][] collectColumnSurfaces(ChunkData chunkData) {
        ColumnSurface[][] surfaces = new ColumnSurface[ChunkData.SIZE_X][ChunkData.SIZE_Z];
        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int z = 0; z < ChunkData.SIZE_Z; z++) {
                surfaces[x][z] = topSurfaceInColumn(chunkData, x, z);
            }
        }
        return surfaces;
    }

    private int countVisibleColumns(ColumnSurface[][] surfaces) {
        int visibleColumns = 0;
        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int z = 0; z < ChunkData.SIZE_Z; z++) {
                if (surfaces[x][z] != null) {
                    visibleColumns++;
                }
            }
        }
        return visibleColumns;
    }

    private HorizonCell[][] collectHorizonCells(ChunkData chunkData, int cellSize) {
        int cellsX = (ChunkData.SIZE_X + cellSize - 1) / cellSize;
        int cellsZ = (ChunkData.SIZE_Z + cellSize - 1) / cellSize;
        HorizonCell[][] cells = new HorizonCell[cellsX][cellsZ];

        for (int cellX = 0; cellX < cellsX; cellX++) {
            for (int cellZ = 0; cellZ < cellsZ; cellZ++) {
                int startX = cellX * cellSize;
                int startZ = cellZ * cellSize;
                int spanX = Math.min(cellSize, ChunkData.SIZE_X - startX);
                int spanZ = Math.min(cellSize, ChunkData.SIZE_Z - startZ);

                ColumnSurface highestSurface = null;
                for (int x = startX; x < startX + spanX; x++) {
                    for (int z = startZ; z < startZ + spanZ; z++) {
                        ColumnSurface surface = topSurfaceInColumn(chunkData, x, z);
                        if (surface != null && (highestSurface == null || surface.topY() > highestSurface.topY())) {
                            highestSurface = surface;
                        }
                    }
                }

                if (highestSurface != null) {
                    cells[cellX][cellZ] = new HorizonCell(
                            startX,
                            startZ,
                            spanX,
                            spanZ,
                            highestSurface.topY(),
                            highestSurface.definition());
                }
            }
        }

        return cells;
    }

    private int countVisibleCells(HorizonCell[][] cells) {
        int visibleCells = 0;
        for (int cellX = 0; cellX < cells.length; cellX++) {
            for (int cellZ = 0; cellZ < cells[cellX].length; cellZ++) {
                if (cells[cellX][cellZ] != null) {
                    visibleCells++;
                }
            }
        }
        return visibleCells;
    }

    private int appendSurfaceTopFaces(
            ColumnSurface[][] surfaces,
            Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders) {
        FaceGeometry topGeometry = FACE_GEOMETRY.get(BlockFace.UP);
        SurfaceTopKey[] mask = new SurfaceTopKey[ChunkData.SIZE_X * ChunkData.SIZE_Z];
        int emittedQuads = 0;

        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int z = 0; z < ChunkData.SIZE_Z; z++) {
                ColumnSurface surface = surfaces[x][z];
                if (surface == null) {
                    continue;
                }
                mask[(x * ChunkData.SIZE_Z) + z] = new SurfaceTopKey(surface.topY(), materialKeyFor(surface.definition(), BlockFace.UP));
            }
        }

        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int z = 0; z < ChunkData.SIZE_Z; ) {
                SurfaceTopKey topKey = mask[(x * ChunkData.SIZE_Z) + z];
                if (topKey == null) {
                    z++;
                    continue;
                }

                int widthZ = 1;
                while (z + widthZ < ChunkData.SIZE_Z
                        && topKey.equals(mask[(x * ChunkData.SIZE_Z) + z + widthZ])) {
                    widthZ++;
                }

                int heightX = 1;
                scanHeight:
                while (x + heightX < ChunkData.SIZE_X) {
                    for (int scanZ = 0; scanZ < widthZ; scanZ++) {
                        if (!topKey.equals(mask[((x + heightX) * ChunkData.SIZE_Z) + z + scanZ])) {
                            break scanHeight;
                        }
                    }
                    heightX++;
                }

                int topSlice = topKey.topY();
                int topU = topGeometry.uAxis() == 0 ? x : z;
                int topV = topGeometry.vAxis() == 0 ? x : z;
                int topWidth = topGeometry.uAxis() == 0 ? heightX : widthZ;
                int topHeight = topGeometry.vAxis() == 0 ? heightX : widthZ;

                sectionBuilders
                        .computeIfAbsent(topKey.materialKey(), ignored -> new MeshSectionBuilder())
                        .appendQuad(topGeometry, topSlice, topU, topV, topWidth, topHeight, BlockFace.UP);
                emittedQuads++;

                for (int clearX = 0; clearX < heightX; clearX++) {
                    for (int clearZ = 0; clearZ < widthZ; clearZ++) {
                        mask[((x + clearX) * ChunkData.SIZE_Z) + z + clearZ] = null;
                    }
                }

                z += widthZ;
            }
        }

        return emittedQuads;
    }

    private int appendSurfaceSideFaces(
            ChunkData chunkData,
            ColumnSurface[][] surfaces,
            Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders) {
        int emittedQuads = 0;
        for (int x = 0; x < ChunkData.SIZE_X; x++) {
            for (int z = 0; z < ChunkData.SIZE_Z; z++) {
                ColumnSurface surface = surfaces[x][z];
                if (surface == null) {
                    continue;
                }

                for (BlockFace face : BlockFace.values()) {
                    if (face == BlockFace.UP || face == BlockFace.DOWN) {
                        continue;
                    }

                    ColumnSurface neighborSurface = neighborSurface(chunkData, surfaces, x, z, face);
                    int neighborTopY = neighborSurface == null ? -1 : neighborSurface.topY();
                    if (surface.topY() <= neighborTopY) {
                        continue;
                    }

                    int spanHeight = surface.topY() - neighborTopY;
                    FaceGeometry geometry = FACE_GEOMETRY.get(face);
                    int slice = geometry.fixedAxis() == 0 ? x : z;
                    int horizontalCoordinate = geometry.fixedAxis() == 0 ? z : x;
                    int verticalStart = neighborTopY + 1;
                    int u = geometry.uAxis() == 1 ? verticalStart : horizontalCoordinate;
                    int v = geometry.vAxis() == 1 ? verticalStart : horizontalCoordinate;
                    int width = geometry.uAxis() == 1 ? spanHeight : 1;
                    int height = geometry.vAxis() == 1 ? spanHeight : 1;

                    sectionBuilders
                            .computeIfAbsent(materialKeyFor(surface.definition(), face), ignored -> new MeshSectionBuilder())
                            .appendQuad(geometry, slice, u, v, width, height, face);
                    emittedQuads++;
                }
            }
        }
        return emittedQuads;
    }

    private int appendHorizonTopFaces(
            HorizonCell[][] cells,
            Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders) {
        FaceGeometry topGeometry = FACE_GEOMETRY.get(BlockFace.UP);
        int emittedQuads = 0;

        for (int cellX = 0; cellX < cells.length; cellX++) {
            for (int cellZ = 0; cellZ < cells[cellX].length; cellZ++) {
                HorizonCell cell = cells[cellX][cellZ];
                if (cell == null) {
                    continue;
                }

                sectionBuilders
                        .computeIfAbsent(materialKeyFor(cell.definition(), BlockFace.UP), ignored -> new MeshSectionBuilder())
                        .appendQuad(
                                topGeometry,
                                cell.topY(),
                                cell.startX(),
                                cell.startZ(),
                                cell.spanX(),
                                cell.spanZ(),
                                BlockFace.UP);
                emittedQuads++;
            }
        }

        return emittedQuads;
    }

    private int appendHorizonSideFaces(
            ChunkData chunkData,
            HorizonCell[][] cells,
            Map<TerrainMaterialKey, MeshSectionBuilder> sectionBuilders) {
        int emittedQuads = 0;
        for (int cellX = 0; cellX < cells.length; cellX++) {
            for (int cellZ = 0; cellZ < cells[cellX].length; cellZ++) {
                HorizonCell cell = cells[cellX][cellZ];
                if (cell == null) {
                    continue;
                }

                for (BlockFace face : BlockFace.values()) {
                    if (face == BlockFace.UP || face == BlockFace.DOWN) {
                        continue;
                    }

                    int neighborTopY = horizonNeighborTopY(chunkData, cells, cellX, cellZ, face);
                    if (cell.topY() <= neighborTopY) {
                        continue;
                    }

                    int verticalSpan = cell.topY() - neighborTopY;
                    FaceGeometry geometry = FACE_GEOMETRY.get(face);
                    int horizontalStart = geometry.fixedAxis() == 0 ? cell.startZ() : cell.startX();
                    int horizontalSpan = geometry.fixedAxis() == 0 ? cell.spanZ() : cell.spanX();
                    int verticalStart = neighborTopY + 1;
                    int slice = switch (face) {
                        case WEST -> cell.startX();
                        case EAST -> cell.startX() + cell.spanX() - 1;
                        case NORTH -> cell.startZ();
                        case SOUTH -> cell.startZ() + cell.spanZ() - 1;
                        case UP, DOWN -> throw new IllegalArgumentException("Vertical faces are not horizon side faces.");
                    };

                    int u = geometry.uAxis() == 1 ? verticalStart : horizontalStart;
                    int v = geometry.vAxis() == 1 ? verticalStart : horizontalStart;
                    int width = geometry.uAxis() == 1 ? verticalSpan : horizontalSpan;
                    int height = geometry.vAxis() == 1 ? verticalSpan : horizontalSpan;

                    sectionBuilders
                            .computeIfAbsent(materialKeyFor(cell.definition(), face), ignored -> new MeshSectionBuilder())
                            .appendQuad(geometry, slice, u, v, width, height, face);
                    emittedQuads++;
                }
            }
        }

        return emittedQuads;
    }

    private ColumnSurface topSurfaceInColumn(ChunkData chunkData, int blockX, int blockZ) {
        for (int blockY = ChunkData.SIZE_Y - 1; blockY >= 0; blockY--) {
            BlockDefinition definition = registries.requireBlockDefinition(chunkData.getBlock(blockX, blockY, blockZ));
            if (definition.solid()) {
                return new ColumnSurface(blockY, definition);
            }
        }
        return null;
    }

    private ColumnSurface neighborSurface(
            ChunkData chunkData,
            ColumnSurface[][] localSurfaces,
            int blockX,
            int blockZ,
            BlockFace face) {
        int neighborX = blockX + face.stepX();
        int neighborZ = blockZ + face.stepZ();
        if (neighborX >= 0 && neighborX < ChunkData.SIZE_X && neighborZ >= 0 && neighborZ < ChunkData.SIZE_Z) {
            return localSurfaces[neighborX][neighborZ];
        }

        int worldX = chunkData.toWorldX(blockX) + face.stepX();
        int worldZ = chunkData.toWorldZ(blockZ) + face.stepZ();
        return topSurfaceAtWorld(worldX, worldZ);
    }

    private ColumnSurface topSurfaceAtWorld(int worldX, int worldZ) {
        for (int blockY = ChunkData.SIZE_Y - 1; blockY >= 0; blockY--) {
            BlockDefinition definition = registries.requireBlockDefinition(worldService.getBlockAtWorldOrAir(worldX, blockY, worldZ));
            if (definition.solid()) {
                return new ColumnSurface(blockY, definition);
            }
        }
        return null;
    }

    private int horizonNeighborTopY(
            ChunkData chunkData,
            HorizonCell[][] cells,
            int cellX,
            int cellZ,
            BlockFace face) {
        int neighborCellX = cellX + Integer.signum(face.stepX());
        int neighborCellZ = cellZ + Integer.signum(face.stepZ());
        if (neighborCellX >= 0
                && neighborCellX < cells.length
                && neighborCellZ >= 0
                && neighborCellZ < cells[neighborCellX].length) {
            HorizonCell neighborCell = cells[neighborCellX][neighborCellZ];
            return neighborCell == null ? -1 : neighborCell.topY();
        }

        return switch (face) {
            case WEST -> highestTopYAlongWorldEdge(
                    chunkData.toWorldX(cells[cellX][cellZ].startX()) - 1,
                    chunkData.toWorldZ(cells[cellX][cellZ].startZ()),
                    cells[cellX][cellZ].spanZ(),
                    false);
            case EAST -> highestTopYAlongWorldEdge(
                    chunkData.toWorldX(cells[cellX][cellZ].startX()) + cells[cellX][cellZ].spanX(),
                    chunkData.toWorldZ(cells[cellX][cellZ].startZ()),
                    cells[cellX][cellZ].spanZ(),
                    false);
            case NORTH -> highestTopYAlongWorldEdge(
                    chunkData.toWorldX(cells[cellX][cellZ].startX()),
                    chunkData.toWorldZ(cells[cellX][cellZ].startZ()) - 1,
                    cells[cellX][cellZ].spanX(),
                    true);
            case SOUTH -> highestTopYAlongWorldEdge(
                    chunkData.toWorldX(cells[cellX][cellZ].startX()),
                    chunkData.toWorldZ(cells[cellX][cellZ].startZ()) + cells[cellX][cellZ].spanZ(),
                    cells[cellX][cellZ].spanX(),
                    true);
            case UP, DOWN -> -1;
        };
    }

    private int highestTopYAlongWorldEdge(int startWorldX, int startWorldZ, int sampleCount, boolean advanceX) {
        int highestTopY = -1;
        for (int sampleIndex = 0; sampleIndex < sampleCount; sampleIndex++) {
            int worldX = startWorldX + (advanceX ? sampleIndex : 0);
            int worldZ = startWorldZ + (advanceX ? 0 : sampleIndex);
            ColumnSurface surface = topSurfaceAtWorld(worldX, worldZ);
            if (surface != null && surface.topY() > highestTopY) {
                highestTopY = surface.topY();
            }
        }
        return highestTopY;
    }

    private TerrainMaterialKey visibleFaceMaterialKey(
            ChunkData chunkData,
            BlockFace face,
            int blockX,
            int blockY,
            int blockZ,
            boolean[] visibleBlocks) {
        BlockDefinition definition = registries.requireBlockDefinition(chunkData.getBlock(blockX, blockY, blockZ));
        if (!definition.solid()) {
            return null;
        }

        BlockDefinition neighborDefinition = neighborDefinition(chunkData, face, blockX, blockY, blockZ);
        if (neighborDefinition.solid() && neighborDefinition.opaque()) {
            return null;
        }

        visibleBlocks[indexOf(blockX, blockY, blockZ)] = true;
        return materialKeyFor(definition, face);
    }

    private BlockDefinition neighborDefinition(ChunkData chunkData, BlockFace face, int blockX, int blockY, int blockZ) {
        int neighborX = blockX + face.stepX();
        int neighborY = blockY + face.stepY();
        int neighborZ = blockZ + face.stepZ();
        if (chunkData.isInBounds(neighborX, neighborY, neighborZ)) {
            return registries.requireBlockDefinition(chunkData.getBlock(neighborX, neighborY, neighborZ));
        }

        return registries.requireBlockDefinition(worldService.getBlockAtWorldOrAir(
                chunkData.toWorldX(blockX) + face.stepX(),
                blockY + face.stepY(),
                chunkData.toWorldZ(blockZ) + face.stepZ()));
    }

    private TerrainMaterialKey materialKeyFor(BlockDefinition definition, BlockFace face) {
        BlockVisualDefinition visuals = definition.visuals();
        if (visuals != null) {
            return TerrainMaterialKey.textured(
                    visuals.textureReferenceFor(textureFaceFor(face)),
                    visuals.tintKey());
        }
        return TerrainMaterialKey.debugColor(definition.debugColor());
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

    private static int indexOf(int x, int y, int z) {
        return (y * ChunkData.SIZE_Z + z) * ChunkData.SIZE_X + x;
    }

    private static Map<BlockFace, FaceGeometry> buildFaceGeometry() {
        Map<BlockFace, FaceGeometry> geometry = new EnumMap<>(BlockFace.class);
        for (BlockFace face : BlockFace.values()) {
            geometry.put(face, FaceGeometry.fromFace(face));
        }
        return Map.copyOf(geometry);
    }

    private static int sizeForAxis(int axis) {
        return switch (axis) {
            case 0 -> ChunkData.SIZE_X;
            case 1 -> ChunkData.SIZE_Y;
            case 2 -> ChunkData.SIZE_Z;
            default -> throw new IllegalArgumentException("Unsupported axis index: " + axis);
        };
    }

    private static int axisIndex(float x, float y, float z) {
        if (Math.abs(x) > 0.5f) {
            return 0;
        }
        if (Math.abs(y) > 0.5f) {
            return 1;
        }
        if (Math.abs(z) > 0.5f) {
            return 2;
        }
        throw new IllegalArgumentException("Vector does not resolve to a primary axis.");
    }

    private record FaceGeometry(
            int fixedAxis,
            int uAxis,
            int vAxis,
            float originOffsetX,
            float originOffsetY,
            float originOffsetZ,
            float uVectorX,
            float uVectorY,
            float uVectorZ,
            float vVectorX,
            float vVectorY,
            float vVectorZ) {
        private static FaceGeometry fromFace(BlockFace face) {
            float[] offsets = face.vertexOffsets();
            float originOffsetX = offsets[0];
            float originOffsetY = offsets[1];
            float originOffsetZ = offsets[2];
            float uVectorX = offsets[3] - offsets[0];
            float uVectorY = offsets[4] - offsets[1];
            float uVectorZ = offsets[5] - offsets[2];
            float vVectorX = offsets[9] - offsets[0];
            float vVectorY = offsets[10] - offsets[1];
            float vVectorZ = offsets[11] - offsets[2];

            return new FaceGeometry(
                    axisIndex(face.normalX(), face.normalY(), face.normalZ()),
                    axisIndex(uVectorX, uVectorY, uVectorZ),
                    axisIndex(vVectorX, vVectorY, vVectorZ),
                    originOffsetX,
                    originOffsetY,
                    originOffsetZ,
                    uVectorX,
                    uVectorY,
                    uVectorZ,
                    vVectorX,
                    vVectorY,
                    vVectorZ);
        }

        private int sliceCount() {
            return sizeForAxis(fixedAxis);
        }

        private int uSize() {
            return sizeForAxis(uAxis);
        }

        private int vSize() {
            return sizeForAxis(vAxis);
        }

        private int coordinateForAxis(int axis, int slice, int u, int v) {
            if (axis == fixedAxis) {
                return slice;
            }
            if (axis == uAxis) {
                return u;
            }
            if (axis == vAxis) {
                return v;
            }
            throw new IllegalArgumentException("Axis is not mapped by face geometry: " + axis);
        }
    }

    private static final class MeshSectionBuilder {
        private final FloatCollector positions = new FloatCollector(1_024);
        private final FloatCollector normals = new FloatCollector(1_024);
        private final FloatCollector textureCoordinates = new FloatCollector(1_024);
        private final IntCollector indices = new IntCollector(1_024);
        private int vertexCount;
        private int faceCount;

        private void appendQuad(
                FaceGeometry geometry,
                int slice,
                int u,
                int v,
                int width,
                int height,
                BlockFace face) {
            float blockX = geometry.coordinateForAxis(0, slice, u, v);
            float blockY = geometry.coordinateForAxis(1, slice, u, v);
            float blockZ = geometry.coordinateForAxis(2, slice, u, v);

            float originX = blockX + geometry.originOffsetX();
            float originY = blockY + geometry.originOffsetY();
            float originZ = blockZ + geometry.originOffsetZ();

            float spanUX = geometry.uVectorX() * width;
            float spanUY = geometry.uVectorY() * width;
            float spanUZ = geometry.uVectorZ() * width;
            float spanVX = geometry.vVectorX() * height;
            float spanVY = geometry.vVectorY() * height;
            float spanVZ = geometry.vVectorZ() * height;

            positions.add(originX);
            positions.add(originY);
            positions.add(originZ);
            positions.add(originX + spanUX);
            positions.add(originY + spanUY);
            positions.add(originZ + spanUZ);
            positions.add(originX + spanUX + spanVX);
            positions.add(originY + spanUY + spanVY);
            positions.add(originZ + spanUZ + spanVZ);
            positions.add(originX + spanVX);
            positions.add(originY + spanVY);
            positions.add(originZ + spanVZ);

            for (int vertex = 0; vertex < 4; vertex++) {
                normals.add(face.normalX());
                normals.add(face.normalY());
                normals.add(face.normalZ());
            }

            appendTextureCoordinates(geometry, width, height);

            indices.add(vertexCount);
            indices.add(vertexCount + 1);
            indices.add(vertexCount + 2);
            indices.add(vertexCount);
            indices.add(vertexCount + 2);
            indices.add(vertexCount + 3);
            vertexCount += 4;
            faceCount++;
        }

        private void appendTextureCoordinates(FaceGeometry geometry, int width, int height) {
            if (geometry.fixedAxis() != 1 && geometry.uAxis() == 1) {
                textureCoordinates.add(0f);
                textureCoordinates.add(width);
                textureCoordinates.add(0f);
                textureCoordinates.add(0f);
                textureCoordinates.add(height);
                textureCoordinates.add(0f);
                textureCoordinates.add(height);
                textureCoordinates.add(width);
                return;
            }

            if (geometry.fixedAxis() != 1 && geometry.vAxis() == 1) {
                textureCoordinates.add(0f);
                textureCoordinates.add(height);
                textureCoordinates.add(width);
                textureCoordinates.add(height);
                textureCoordinates.add(width);
                textureCoordinates.add(0f);
                textureCoordinates.add(0f);
                textureCoordinates.add(0f);
                return;
            }

            textureCoordinates.add(0f);
            textureCoordinates.add(0f);
            textureCoordinates.add(width);
            textureCoordinates.add(0f);
            textureCoordinates.add(width);
            textureCoordinates.add(height);
            textureCoordinates.add(0f);
            textureCoordinates.add(height);
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

    private record ColumnSurface(int topY, BlockDefinition definition) {
    }

    private record HorizonCell(int startX, int startZ, int spanX, int spanZ, int topY, BlockDefinition definition) {
    }

    private record SurfaceTopKey(int topY, TerrainMaterialKey materialKey) {
    }
}
