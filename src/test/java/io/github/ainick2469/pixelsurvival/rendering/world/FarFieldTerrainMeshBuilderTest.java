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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        FarFieldTerrainSettings settings = new FarFieldTerrainSettings(0, 2, 2, 3, 1, 1, 8, 0, 0, 0f, true);

        FarFieldTerrainMeshBuildResult result = builder.buildRegionMesh(
                new FarFieldTerrainTarget(new FarFieldTerrainRegionCoord(0, 0), FarFieldClipMode.FULL_REGION),
                new ChunkCoord(0, 0),
                settings);

        ChunkMeshSectionData sharedSection = result.sections().get(TerrainMaterialKey.sharedTextured());
        assertEquals(1, result.sections().size());
        assertNotNull(sharedSection);
        assertTrue(uniqueTextureLayerCount(sharedSection) > 1);
        assertTrue(result.faceCount() >= result.visibleCellCount());
    }

    @Test
    void addsSkirtsWhenARegionEdgeFallsOutsideTheFarFieldRing() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        FarFieldTerrainSampler sampler = (worldX, worldZ) -> new FarFieldTerrainSampler.ColumnSample(10, GRASS);
        FarFieldTerrainMeshBuilder builder = new FarFieldTerrainMeshBuilder(registries, texturePalette, sampler);
        FarFieldTerrainSettings settings = new FarFieldTerrainSettings(2, 3, 3, 4, 1, 1, 16, 0, 0, 0f, true);

        FarFieldTerrainMeshBuildResult result = builder.buildRegionMesh(
                new FarFieldTerrainTarget(new FarFieldTerrainRegionCoord(2, 0), FarFieldClipMode.CLIP_OUTER),
                new ChunkCoord(0, 0),
                settings);

        assertEquals(1, result.visibleCellCount());
        assertTrue(result.faceCount() >= 2);
    }

    @Test
    void keepsCellsWhoseBoundsCrossTheInnerSeamVisible() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        FarFieldTerrainSampler sampler = (worldX, worldZ) -> new FarFieldTerrainSampler.ColumnSample(10, GRASS);
        FarFieldTerrainMeshBuilder builder = new FarFieldTerrainMeshBuilder(registries, texturePalette, sampler);
        FarFieldTerrainSettings settings = new FarFieldTerrainSettings(2, 4, 4, 5, 2, 1, 8, 0, 0, 0f, true);

        FarFieldTerrainMeshBuildResult result = builder.buildRegionMesh(
                new FarFieldTerrainTarget(new FarFieldTerrainRegionCoord(2, 0), FarFieldClipMode.CLIP_INNER),
                new ChunkCoord(0, 0),
                settings);

        assertTrue(result.visibleCellCount() > 0);
        assertTrue(result.faceCount() >= result.visibleCellCount());
    }

    @Test
    void buildsUltraDistancePatchTopsWithSeamWeightsInsteadOfSingleFlatPlateaus() {
        GameRegistries registries = GameRegistries.load(Path.of("data"));
        TerrainTexturePalette texturePalette = TerrainTexturePalette.build(registries);
        FarFieldTerrainSampler sampler = (worldX, worldZ) -> new FarFieldTerrainSampler.ColumnSample(
                18 + Math.floorDiv(worldX, 8) + Math.floorDiv(worldZ, 8),
                GRASS);
        FarFieldTerrainMeshBuilder builder = new FarFieldTerrainMeshBuilder(registries, texturePalette, sampler);
        FarFieldTerrainSettings settings = FarFieldTerrainSettings.from(new ChunkRuntimeConfig(196, 192, 4));

        FarFieldTerrainMeshBuildResult result = builder.buildRegionMesh(
                new FarFieldTerrainTarget(new FarFieldTerrainRegionCoord(2, 0), FarFieldClipMode.CLIP_INNER),
                new ChunkCoord(0, 0),
                settings);

        ChunkMeshSectionData sharedSection = result.sections().get(TerrainMaterialKey.sharedTextured());
        assertNotNull(sharedSection);
        assertEquals(1, sharedSection.secondaryTextureCoordinateComponents());
        assertTrue(maxSecondaryCoordinate(sharedSection) > 0f);
        assertTrue(uniqueHeightCount(sharedSection) > 4);
        assertTrue(result.faceCount() >= 8);
    }

    private static int uniqueTextureLayerCount(ChunkMeshSectionData section) {
        Set<Integer> uniqueLayers = new HashSet<>();
        float[] coordinates = section.textureCoordinates();
        for (int index = 2; index < coordinates.length; index += 3) {
            uniqueLayers.add(Math.round(coordinates[index]));
        }
        return uniqueLayers.size();
    }

    private static float maxSecondaryCoordinate(ChunkMeshSectionData section) {
        float maximum = 0f;
        for (float value : section.secondaryTextureCoordinates()) {
            maximum = Math.max(maximum, value);
        }
        return maximum;
    }

    private static int uniqueHeightCount(ChunkMeshSectionData section) {
        Set<Integer> uniqueHeights = new HashSet<>();
        float[] positions = section.positions();
        for (int index = 1; index < positions.length; index += 3) {
            uniqueHeights.add(Math.round(positions[index] * 100f));
        }
        return uniqueHeights.size();
    }
}
