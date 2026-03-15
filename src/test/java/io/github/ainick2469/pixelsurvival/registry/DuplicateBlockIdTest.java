package io.github.ainick2469.pixelsurvival.registry;

import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DuplicateBlockIdTest {
    @TempDir
    Path tempDirectory;

    @Test
    void failsFastOnDuplicateBlockIds() throws IOException {
        Files.writeString(
                tempDirectory.resolve("block_a.json"),
                """
                {
                  "id": "pixel_survival:test_block",
                  "displayName": "Test A",
                  "materialFamily": "soil",
                  "solid": true,
                  "opaque": true,
                  "debugColor": "#123456",
                  "tags": ["test"]
                }
                """);
        Files.writeString(
                tempDirectory.resolve("block_b.json"),
                """
                {
                  "id": "pixel_survival:test_block",
                  "displayName": "Test B",
                  "materialFamily": "soil",
                  "solid": true,
                  "opaque": true,
                  "debugColor": "#654321",
                  "tags": ["test"]
                }
                """);

        RegistryLoader<BlockDefinition, BlockId> loader =
                new RegistryLoader<>(BlockDefinition.class, (path, definition) -> definition.id());

        assertThrows(IllegalStateException.class, () -> loader.loadDirectory(tempDirectory));
    }
}
