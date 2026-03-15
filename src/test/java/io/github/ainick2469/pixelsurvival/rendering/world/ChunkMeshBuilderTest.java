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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkMeshBuilderTest {
    private static final BlockId AIR = BlockId.of("pixel_survival:air");
    private static final BlockId GRASS = BlockId.of("pixel_survival:grass_block");
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
