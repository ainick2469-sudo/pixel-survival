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
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMeshBuilderTest {
    private static final BlockId AIR = BlockId.of("pixel_survival:air");
    private static final BlockId GRASS = BlockId.of("pixel_survival:grass_block");
    private static final BlockId DIRT = BlockId.of("pixel_survival:dirt");
    private static final BlockId STONE = BlockId.of("pixel_survival:stone");

    @Test
    void groupsGrassFacesByTopSideAndBottomTextures() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        centerChunk.setBlock(4, 10, 4, GRASS);
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries).buildChunkMesh(centerChunk);

        assertEquals(1, result.visibleBlockCount());
        assertEquals(6, result.faceCount());
        assertEquals(6, result.sections().size());
        assertEquals(1, faceCount(result, BlockTextureFace.TOP));
        assertEquals(1, faceCount(result, BlockTextureFace.BOTTOM));
        assertEquals(1, faceCount(result, BlockTextureFace.BACK));
        assertEquals(1, faceCount(result, BlockTextureFace.LEFT));
        assertEquals(1, faceCount(result, BlockTextureFace.RIGHT));
        assertEquals(1, faceCount(result, BlockTextureFace.FRONT));
    }

    @Test
    void cullsFacesAgainstLoadedNeighborChunks() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
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

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries).buildChunkMesh(westChunk);

        assertEquals(1, result.visibleBlockCount());
        assertEquals(5, result.faceCount());
        int totalStoneFaces = result.sections().entrySet().stream()
                .filter(entry -> entry.getKey().textureReference() != null)
                .filter(entry -> "Textures/BlockCubeNets/stone_cube_net.png".equals(
                        entry.getKey().textureReference().cubeNetTexturePath()))
                .mapToInt(entry -> entry.getValue().faceCount())
                .sum();
        long stoneSectionCount = result.sections().keySet().stream()
                .filter(key -> key.textureReference() != null)
                .filter(key -> "Textures/BlockCubeNets/stone_cube_net.png".equals(key.textureReference().cubeNetTexturePath()))
                .count();
        assertEquals(5, totalStoneFaces);
        assertTrue(stoneSectionCount >= 1);
    }

    @Test
    void greedilyMergesAdjacentFacesWithTheSameMaterial() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
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

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries).buildChunkMesh(centerChunk);

        assertEquals(4, result.visibleBlockCount());
        assertEquals(6, result.faceCount());
        assertEquals(1, faceCount(result, BlockTextureFace.TOP));
        assertEquals(1, faceCount(result, BlockTextureFace.BOTTOM));
        assertEquals(1, faceCount(result, BlockTextureFace.BACK));
        assertEquals(1, faceCount(result, BlockTextureFace.LEFT));
        assertEquals(1, faceCount(result, BlockTextureFace.RIGHT));
        assertEquals(1, faceCount(result, BlockTextureFace.FRONT));
    }

    @Test
    void surfaceDetailLodCollapsesTallColumnWallsIntoSingleVerticalQuads() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
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

        ChunkMeshBuilder builder = new ChunkMeshBuilder(worldService, registries);
        ChunkMeshBuildResult fullResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.FULL);
        ChunkMeshBuildResult surfaceResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.SURFACE);

        assertEquals(ChunkDetailLevel.FULL, fullResult.detailLevel());
        assertEquals(ChunkDetailLevel.SURFACE, surfaceResult.detailLevel());
        assertEquals(5, surfaceResult.faceCount());
        assertTrue(surfaceResult.faceCount() < fullResult.faceCount());
    }

    @Test
    void horizonDetailLodCollapsesNoisyTerrainIntoCoarseHorizonCells() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
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

        ChunkMeshBuilder builder = new ChunkMeshBuilder(worldService, registries);
        ChunkMeshBuildResult surfaceResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.SURFACE);
        ChunkMeshBuildResult horizonResult = builder.buildChunkMesh(centerChunk, ChunkDetailLevel.HORIZON);

        assertEquals(ChunkDetailLevel.HORIZON, horizonResult.detailLevel());
        assertTrue(horizonResult.faceCount() < surfaceResult.faceCount());
        assertTrue(horizonResult.visibleBlockCount() < surfaceResult.visibleBlockCount());
    }

    @Test
    void keepsGrassSideTexturesUprightOnFacesWhoseVerticalSpanRunsAlongMeshU() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        centerChunk.setBlock(4, 10, 4, GRASS);
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries).buildChunkMesh(centerChunk);
        ChunkMeshSectionData rightFaceSection = result.sections().get(TerrainMaterialKey.textured(
                BlockFaceTextureReference.cubeNet(
                        "Textures/BlockCubeNets/grass_block_cube_net.png",
                        CubeNetLayout.CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM,
                        BlockTextureFace.RIGHT),
                "grass"));

        assertArrayEquals(
                new float[] {0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f},
                rightFaceSection.textureCoordinates(),
                0.0001f);
    }

    @Test
    void keepsGrassSideTexturesUprightOnFacesWhoseVerticalSpanRunsAlongMeshV() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        Map<ChunkCoord, ChunkData> chunks = new HashMap<>();
        ChunkData centerChunk = new ChunkData(new ChunkCoord(0, 0), AIR);
        centerChunk.setBlock(4, 10, 4, GRASS);
        chunks.put(centerChunk.chunkCoord(), centerChunk);
        loadNeighborAirChunks(chunks);

        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, mapBackedGenerator(chunks));
        chunks.keySet().forEach(worldService::loadChunk);

        ChunkMeshBuildResult result = new ChunkMeshBuilder(worldService, registries).buildChunkMesh(centerChunk);
        ChunkMeshSectionData frontFaceSection = result.sections().get(TerrainMaterialKey.textured(
                BlockFaceTextureReference.cubeNet(
                        "Textures/BlockCubeNets/grass_block_cube_net.png",
                        CubeNetLayout.CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM,
                        BlockTextureFace.FRONT),
                "grass"));

        assertArrayEquals(
                new float[] {0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f},
                frontFaceSection.textureCoordinates(),
                0.0001f);
    }

    private static int faceCount(ChunkMeshBuildResult result, BlockTextureFace face) {
        ChunkMeshSectionData section = result.sections().get(TerrainMaterialKey.textured(
                BlockFaceTextureReference.cubeNet(
                        "Textures/BlockCubeNets/grass_block_cube_net.png",
                        CubeNetLayout.CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM,
                        face),
                "grass"));
        return section == null ? 0 : section.faceCount();
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
