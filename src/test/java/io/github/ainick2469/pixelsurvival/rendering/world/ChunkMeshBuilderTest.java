package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockFaceTextureReference;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.block.BlockTextureFace;
import io.github.ainick2469.pixelsurvival.world.block.CubeNetLayout;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.WorldGenerator;
import io.github.ainick2469.pixelsurvival.world.sim.AuthoritativeWorldService;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMeshBuilderTest {
    private static final BlockId AIR = BlockId.of("pixel_survival:air");
    private static final BlockId GRASS = BlockId.of("pixel_survival:grass_block");
    private static final BlockId DIRT = BlockId.of("pixel_survival:dirt");
    private static final BlockId STONE = BlockId.of("pixel_survival:stone");

    @Test
    void batchesSingleGrassBlockIntoOneSharedTerrainSection() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        centerChunk.setBlock(4, 10, 4, GRASS);
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries, texturePalette).buildChunkMesh(centerChunk);

        assertEquals(1, result.visibleBlockCount());
        assertEquals(6, result.faceCount());
        assertEquals(1, result.sections().size());
        assertEquals(6, sharedTexturedSection(result).faceCount());
    }

    @Test
    void batchesMixedGrassAndStoneFacesIntoOneSharedTerrainSection() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        centerChunk.setBlock(4, 10, 4, GRASS);
        centerChunk.setBlock(7, 10, 4, STONE);
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries, texturePalette).buildChunkMesh(centerChunk);

        assertEquals(2, result.visibleBlockCount());
        assertEquals(12, result.faceCount());
        assertEquals(1, result.sections().size());
        assertTrue(uniqueTextureLayerCount(sharedTexturedSection(result)) > 1);
    }

    @Test
    void cullsFacesAgainstLoadedNeighborChunks() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();

        ChunkData westChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        westChunk.setBlock(ChunkData.SIZE_X - 1, 12, 3, STONE);
        chunks.put(westChunk.chunkCoord(), westChunk);

        ChunkData eastChunk = new ChunkData(new ChunkCoord(1, 0), AIR);
        eastChunk.setBlock(0, 12, 3, STONE);
        chunks.put(eastChunk.chunkCoord(), eastChunk);

        chunks.put(new ChunkCoord(-1, 0), new ChunkData(new ChunkCoord(-1, 0), AIR));
        chunks.put(new ChunkCoord(0, 1), new ChunkData(new ChunkCoord(0, 1), AIR));
        chunks.put(new ChunkCoord(0, -1), new ChunkData(new ChunkCoord(0, -1), AIR));
        chunks.put(new ChunkCoord(1, 1), new ChunkData(new ChunkCoord(1, 1), AIR));
        chunks.put(new ChunkCoord(1, -1), new ChunkData(new ChunkCoord(1, -1), AIR));
        chunks.put(new ChunkCoord(2, 0), new ChunkData(new ChunkCoord(2, 0), AIR));

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries, texturePalette).buildChunkMesh(westChunk);

        assertEquals(1, result.visibleBlockCount());
        assertEquals(5, result.faceCount());
        assertEquals(1, result.sections().size());
        assertEquals(5, sharedTexturedSection(result).faceCount());
    }

    @Test
    void greedilyMergesAdjacentFacesWithTheSameTextureLayer() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        for (int x = 4; x <= 5; x++) {
            for (int z = 4; z <= 5; z++) {
                centerChunk.setBlock(x, 10, z, GRASS);
            }
        }
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries, texturePalette).buildChunkMesh(centerChunk);

        assertEquals(4, result.visibleBlockCount());
        assertEquals(6, result.faceCount());
        assertEquals(1, result.sections().size());
        assertEquals(6, sharedTexturedSection(result).faceCount());
    }

    @Test
    void surfaceDetailLodCollapsesTallColumnWallsIntoSingleVerticalQuads() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        for (int y = 0; y < 5; y++) {
            centerChunk.setBlock(4, y, 4, DIRT);
        }
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuilder builder = new ChunkMeshBuilder(worldService, registries, texturePalette);
        ChunkMeshBuildResult fullResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.FULL);
        ChunkMeshBuildResult surfaceResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.SURFACE);

        assertEquals(ChunkDetailLevel.FULL, fullResult.detailLevel());
        assertEquals(ChunkDetailLevel.SURFACE, surfaceResult.detailLevel());
        assertEquals(5, surfaceResult.faceCount());
        assertTrue(surfaceResult.faceCount() < fullResult.faceCount());
        assertEquals(1, surfaceResult.sections().size());
    }

    @Test
    void horizonDetailLodCollapsesNoisyTerrainIntoCoarseHorizonCells() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                int columnHeight = 1 + ((x + z) % 4);
                for (int y = 0; y < columnHeight; y++) {
                    centerChunk.setBlock(x, y, z, DIRT);
                }
            }
        }
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuilder builder = new ChunkMeshBuilder(worldService, registries, texturePalette);
        ChunkMeshBuildResult surfaceResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.SURFACE);
        ChunkMeshBuildResult horizonResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.HORIZON);

        assertEquals(ChunkDetailLevel.HORIZON, horizonResult.detailLevel());
        assertTrue(horizonResult.faceCount() < surfaceResult.faceCount());
        assertTrue(horizonResult.visibleBlockCount() < surfaceResult.visibleBlockCount());
    }

    @Test
    void keepsGrassSideTexturesUprightOnFacesWhoseVerticalSpanRunsAlongMeshU() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        centerChunk.setBlock(4, 10, 4, GRASS);
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries, texturePalette).buildChunkMesh(centerChunk);
        ChunkMeshSectionData sharedSection = sharedTexturedSection(result);
        int expectedLayer = texturePalette.layerIndexFor(BlockFaceTextureReference.cubeNet(
                "Textures/BlockCubeNets/grass_block_cube_net.png",
                CubeNetLayout.CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM,
                BlockTextureFace.RIGHT));

        assertArrayEquals(
                new float[] {
                    0f, 1f, expectedLayer,
                    0f, 0f, expectedLayer,
                    1f, 0f, expectedLayer,
                    1f, 1f, expectedLayer
                },
                extractTexturedQuad(sharedSection, 2),
                0.0001f);
    }

    @Test
    void keepsGrassSideTexturesUprightOnFacesWhoseVerticalSpanRunsAlongMeshV() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        centerChunk.setBlock(4, 10, 4, GRASS);
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries, texturePalette).buildChunkMesh(centerChunk);
        ChunkMeshSectionData sharedSection = sharedTexturedSection(result);
        int expectedLayer = texturePalette.layerIndexFor(BlockFaceTextureReference.cubeNet(
                "Textures/BlockCubeNets/grass_block_cube_net.png",
                CubeNetLayout.CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM,
                BlockTextureFace.FRONT));

        assertArrayEquals(
                new float[] {
                    0f, 1f, expectedLayer,
                    1f, 1f, expectedLayer,
                    1f, 0f, expectedLayer,
                    0f, 0f, expectedLayer
                },
                extractTexturedQuad(sharedSection, 4),
                0.0001f);
    }

    private static ChunkMeshSectionData sharedTexturedSection(ChunkMeshBuildResult result) {
        return result.sections().get(TerrainMaterialKey.sharedTextured());
    }

    private static float[] extractTexturedQuad(ChunkMeshSectionData section, int quadIndex) {
        float[] quad = new float[12];
        System.arraycopy(section.textureCoordinates(), quadIndex * 12, quad, 0, 12);
        return quad;
    }

    private static int uniqueTextureLayerCount(ChunkMeshSectionData section) {
        Set<Integer> uniqueLayers = new HashSet<>();
        float[] coordinates = section.textureCoordinates();
        for (int index = 2; index < coordinates.length; index += 3) {
            uniqueLayers.add(Math.round(coordinates[index]));
        }
        return uniqueLayers.size();
    }

    private static void loadNeighborAirChunks(Map<ChunkCoord, ChunkData> chunks) {
        chunks.put(new ChunkCoord(1, 0), new ChunkData(new ChunkCoord(1, 0), AIR));
        chunks.put(new ChunkCoord(-1, 0), new ChunkData(new ChunkCoord(-1, 0), AIR));
        chunks.put(new ChunkCoord(0, 1), new ChunkData(new ChunkCoord(0, 1), AIR));
        chunks.put(new ChunkCoord(0, -1), new ChunkData(new ChunkCoord(0, -1), AIR));
    }

    private static WorldGenerator mapBackedGenerator(Map<ChunkCoord, ChunkData> chunks) {
        return (chunkCoord, registries) -> {
            ChunkData chunkData = chunks.get(chunkCoord);
            if (chunkData == null) {
                return new ChunkData(chunkCoord, AIR);
            }
            return chunkData;
        };
    }
}
