package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlatSpawnWorldGeneratorTest {
    @Test
    void fillsTerrainBelowSurfaceWithDirtAndLeavesAirAbove() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        FlatSpawnWorldGenerator generator = new FlatSpawnWorldGenerator();

        ChunkData chunkData = generator.generateChunk(new ChunkCoord(0, 0), registries);

        assertEquals(BlockId.of("pixel_survival:dirt"), chunkData.getBlock(0, 0, 0));
        assertEquals(BlockId.of("pixel_survival:dirt"), chunkData.getBlock(7, FlatSpawnWorldGenerator.SURFACE_HEIGHT - 1, 7));
        assertEquals(BlockId.of("pixel_survival:air"), chunkData.getBlock(7, FlatSpawnWorldGenerator.SURFACE_HEIGHT, 7));
    }
}
