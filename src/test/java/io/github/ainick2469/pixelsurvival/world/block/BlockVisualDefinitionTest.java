package io.github.ainick2469.pixelsurvival.world.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockVisualDefinitionTest {
    @Test
    void infersTopSideBottomModeFromLegacyTerrainFields() {
        BlockVisualDefinition visuals = new BlockVisualDefinition(
                null,
                null,
                "Textures/Terrain/grass_top.png",
                "Textures/Terrain/grass_side.png",
                "Textures/Terrain/dirt.png",
                null,
                null,
                null,
                null,
                null,
                null,
                "grass");

        assertEquals(BlockTextureMode.TOP_SIDE_BOTTOM, visuals.textureMode());
        assertEquals(
                "Textures/Terrain/grass_top.png",
                visuals.textureReferenceFor(BlockTextureFace.TOP).texturePath());
        assertEquals(
                "Textures/Terrain/grass_side.png",
                visuals.textureReferenceFor(BlockTextureFace.LEFT).texturePath());
        assertEquals(
                "Textures/Terrain/dirt.png",
                visuals.textureReferenceFor(BlockTextureFace.BOTTOM).texturePath());
    }

    @Test
    void resolvesCubeNetTexturesDeterministicallyPerFace() {
        BlockVisualDefinition visuals = new BlockVisualDefinition(
                BlockTextureMode.CUBE_NET,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Textures/BlockCubeNets/stone_cube_net.png",
                CubeNetLayout.BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM,
                null);

        BlockFaceTextureReference top = visuals.textureReferenceFor(BlockTextureFace.TOP);
        BlockFaceTextureReference front = visuals.textureReferenceFor(BlockTextureFace.FRONT);

        assertEquals(BlockTextureMode.CUBE_NET, visuals.textureMode());
        assertEquals("Textures/BlockCubeNets/stone_cube_net.png", top.cubeNetTexturePath());
        assertEquals(CubeNetLayout.BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM, top.cubeNetLayout());
        assertEquals(BlockTextureFace.TOP, top.cubeNetFace());
        assertEquals(BlockTextureFace.FRONT, front.cubeNetFace());
    }
}
