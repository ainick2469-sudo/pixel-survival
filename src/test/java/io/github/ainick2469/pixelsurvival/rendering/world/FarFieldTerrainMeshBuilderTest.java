package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.gen.FarFieldTerrainSampler;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarFieldTerrainMeshBuilderTest {
    private static final BlockId GRASS = BlockId.of("pixel_survival:grass_block");

    @Test
    void batchesFarFieldCellsIntoTheSharedTerrainSectionAndPreservesGrassTopAndWallLayers() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        FarFieldTerrainSampler sampler = (worldX, worldZ) -> new FarFieldTerrainSampler.ColumnSample(
                worldX < 8 ? 10 : 14,
                GRASS);
        FarFieldTerrainMeshBuilder builder = new FarFieldTerrainMeshBuilder(registries, texturePalette, sampler);
        FarFieldTerrainSettings settings = new FarFieldTerrainSettings(0, 2, 2, 3, 1, 1, 8, 0, 0, 0f);

        FarFieldTerrainMeshBuildResult result =
                builder.buildRegionMesh(new FarFieldTerrainRegionCoord(0, 0), new ChunkCoord(0, 0), settings);

        assertEquals(1, result.sections().size());
        assertTrue(uniqueTextureLayerCount(result.sections().get(TerrainMaterialKey.sharedTextured())) > 1);
        assertTrue(result.faceCount() > result.visibleCellCount());
    }

    @Test
    void addsSkirtsWhenARegionEdgeFallsOutsideTheFarFieldRing() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        FarFieldTerrainSampler sampler = (worldX, worldZ) -> new FarFieldTerrainSampler.ColumnSample(10, GRASS);
        FarFieldTerrainMeshBuilder builder = new FarFieldTerrainMeshBuilder(registries, texturePalette, sampler);
        FarFieldTerrainSettings settings = new FarFieldTerrainSettings(2, 3, 3, 4, 1, 1, 16, 0, 0, 0f);

        FarFieldTerrainMeshBuildResult result =
                builder.buildRegionMesh(new FarFieldTerrainRegionCoord(2, 0), new ChunkCoord(0, 0), settings);

        assertEquals(1, result.visibleCellCount());
        assertEquals(2, result.faceCount());
    }

    private static int uniqueTextureLayerCount(ChunkMeshSectionData section) {
        Set<Integer> uniqueLayers = new HashSet<>();
        float[] coordinates = section.textureCoordinates();
        for (int index = 2; index < coordinates.length; index += 3) {
            uniqueLayers.add(Math.round(coordinates[index]));
        }
        return uniqueLayers.size();
    }
}
