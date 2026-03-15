package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.pipeline.WorldGenerationStage;
import io.github.ainick2469.pixelsurvival.world.gen.topology.WorldTopologyKind;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
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
    void generatesLayeredStoneDirtAndGrassTerrain() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        HeightmapWorldGenerator generator = new HeightmapWorldGenerator();

        ChunkData chunkData = generator.generateChunk(new ChunkCoord(0, 0), registries);
        int sampledHeight = generator.sampleSurfaceHeight(5, 9);

        assertEquals(BlockId.of("pixel_survival:grass_block"), chunkData.getBlock(5, sampledHeight, 9));
        assertEquals(BlockId.of("pixel_survival:dirt"), chunkData.getBlock(5, sampledHeight - 1, 9));
        assertEquals(BlockId.of("pixel_survival:dirt"), chunkData.getBlock(5, sampledHeight - 2, 9));
        assertEquals(BlockId.of("pixel_survival:dirt"), chunkData.getBlock(5, sampledHeight - 3, 9));
        assertEquals(BlockId.of("pixel_survival:stone"), chunkData.getBlock(5, sampledHeight - 4, 9));
        assertEquals(BlockId.of("pixel_survival:air"), chunkData.getBlock(5, sampledHeight + 1, 9));
        assertTrue(sampledHeight >= 14);
        assertTrue(sampledHeight <= 40);
    }

    @Test
    void exposesCurrentPipelineStagesWithoutPrematureFutureFeatureInjection() {
        HeightmapWorldGenerator generator = new HeightmapWorldGenerator();

        assertEquals(
                List.of(WorldGenerationStage.BASE_TERRAIN, WorldGenerationStage.TERRAIN_LAYERING),
                generator.configuredStages());
        assertEquals(WorldTopologyKind.PLANAR_PROTOTYPE, generator.topologyProfile().kind());
    }
}
