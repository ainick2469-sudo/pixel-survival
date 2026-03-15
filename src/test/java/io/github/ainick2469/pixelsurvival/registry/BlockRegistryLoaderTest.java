package io.github.ainick2469.pixelsurvival.registry;

import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.block.BlockTextureMode;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryLoaderTest {
    @Test
    void loadsBlockDefinitionsFromDisk() {
        RegistryLoader<BlockDefinition, BlockId> loader =
                new RegistryLoader<>(BlockDefinition.class, (path, definition) -> definition.id());

        Map<BlockId, BlockDefinition> blocks = loader.loadDirectory(Path.of("data", "blocks"));

        assertEquals(6, blocks.size());
        assertTrue(blocks.containsKey(BlockId.of("pixel_survival:air")));
        assertEquals("Dirt", blocks.get(BlockId.of("pixel_survival:dirt")).displayName());
        assertEquals("Stone", blocks.get(BlockId.of("pixel_survival:stone")).displayName());
        assertEquals("Grass Block", blocks.get(BlockId.of("pixel_survival:grass_block")).displayName());
        assertEquals("Sand", blocks.get(BlockId.of("pixel_survival:sand")).displayName());
        assertEquals("Solid Cloud", blocks.get(BlockId.of("pixel_survival:cloud_solid")).displayName());
        assertEquals(
                BlockTextureMode.CUBE_NET,
                blocks.get(BlockId.of("pixel_survival:dirt")).visuals().textureMode());
        assertEquals(
                "Textures/BlockCubeNets/dirt_cube_net.png",
                blocks.get(BlockId.of("pixel_survival:dirt")).visuals().cubeNetTexture());
        assertEquals(
                BlockTextureMode.CUBE_NET,
                blocks.get(BlockId.of("pixel_survival:stone")).visuals().textureMode());
        assertEquals(
                "Textures/BlockCubeNets/stone_cube_net.png",
                blocks.get(BlockId.of("pixel_survival:stone")).visuals().cubeNetTexture());
        assertEquals(
                "Textures/Terrain/grass_top.png",
                blocks.get(BlockId.of("pixel_survival:grass_block")).visuals().topTexture());
        assertEquals(
                "Textures/Terrain/grass_side.png",
                blocks.get(BlockId.of("pixel_survival:grass_block")).visuals().sideTexture());
        assertEquals(
                "Textures/Terrain/sand.png",
                blocks.get(BlockId.of("pixel_survival:sand")).visuals().topTexture());
        assertEquals(
                "Textures/Terrain/cloud_solid.png",
                blocks.get(BlockId.of("pixel_survival:cloud_solid")).visuals().topTexture());
        assertNotNull(blocks.get(BlockId.of("pixel_survival:air")).visuals());
    }
}
