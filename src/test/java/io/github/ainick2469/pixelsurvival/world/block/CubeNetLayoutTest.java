package io.github.ainick2469.pixelsurvival.world.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CubeNetLayoutTest {
    @Test
    void mapsFacesToTheExpectedCrossLayoutTiles() {
        CubeNetLayout layout = CubeNetLayout.BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM;

        assertEquals(1, layout.tileX(BlockTextureFace.BACK));
        assertEquals(0, layout.tileY(BlockTextureFace.BACK));
        assertEquals(1, layout.tileX(BlockTextureFace.TOP));
        assertEquals(1, layout.tileY(BlockTextureFace.TOP));
        assertEquals(0, layout.tileX(BlockTextureFace.LEFT));
        assertEquals(2, layout.tileY(BlockTextureFace.LEFT));
        assertEquals(1, layout.tileX(BlockTextureFace.FRONT));
        assertEquals(2, layout.tileY(BlockTextureFace.FRONT));
        assertEquals(2, layout.tileX(BlockTextureFace.RIGHT));
        assertEquals(2, layout.tileY(BlockTextureFace.RIGHT));
        assertEquals(1, layout.tileX(BlockTextureFace.BOTTOM));
        assertEquals(3, layout.tileY(BlockTextureFace.BOTTOM));
        assertEquals(128, layout.faceSize(384, 512));
    }
}
