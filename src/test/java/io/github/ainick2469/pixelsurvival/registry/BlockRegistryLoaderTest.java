package io.github.ainick2469.pixelsurvival.registry;

import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRegistryLoaderTest {
    @Test
    void loadsBlockDefinitionsFromDisk() {
        RegistryLoader<BlockDefinition, BlockId> loader =
                new RegistryLoader<>(BlockDefinition.class, (path, definition) -> definition.id());

        Map<BlockId, BlockDefinition> blocks = loader.loadDirectory(Path.of("data", "blocks"));

        assertEquals(4, blocks.size());
        assertTrue(blocks.containsKey(BlockId.of("pixel_survival:air")));
        assertEquals("Dirt", blocks.get(BlockId.of("pixel_survival:dirt")).displayName());
        assertEquals("Stone", blocks.get(BlockId.of("pixel_survival:stone")).displayName());
        assertEquals("Grass Block", blocks.get(BlockId.of("pixel_survival:grass_block")).displayName());
    }
}
