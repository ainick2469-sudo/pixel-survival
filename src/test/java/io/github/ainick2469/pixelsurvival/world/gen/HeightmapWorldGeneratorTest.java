package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeightmapWorldGeneratorTest {
    @Test
    void producesDeterministicHeightDifferencesAcrossTheWorld() {
        HeightmapWorldGenerator generator = new HeightmapWorldGenerator();
        Set<Integer> sampledHeights = new HashSet<>();

        int originHeight = generator.sampleSurfaceHeight(0, 0);
        int repeatedOriginHeight = generator.sampleSurfaceHeight(0, 0);
        for (int worldX = 0; worldX <= 96; worldX += 16) {
            for (int worldZ = 0; worldZ <= 96; worldZ += 16) {
                sampledHeights.add(generator.sampleSurfaceHeight(worldX, worldZ));
            }
        }

        assertEquals(originHeight, repeatedOriginHeight);
        assertTrue(sampledHeights.size() > 1);
    }

    @Test
    void generatesDirtToTheSurfaceAndAirAbove() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        HeightmapWorldGenerator generator = new HeightmapWorldGenerator();

        ChunkData chunkData = generator.generateChunk(new ChunkCoord(0, 0), registries);
        int sampledHeight = generator.sampleSurfaceHeight(5, 9);

        assertEquals(BlockId.of("pixel_survival:dirt"), chunkData.getBlock(5, sampledHeight, 9));
        assertEquals(BlockId.of("pixel_survival:air"), chunkData.getBlock(5, sampledHeight + 1, 9));
        assertTrue(sampledHeight >= 14);
        assertTrue(sampledHeight <= 40);
    }
}
