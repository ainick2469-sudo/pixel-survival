package io.github.ainick2469.pixelsurvival.registry;

import io.github.ainick2469.pixelsurvival.settings.SurvivalSettings;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import java.nio.file.Path;
import java.util.Map;

public final class GameRegistries {
    private final Path dataRoot;
    private final Map<BlockId, BlockDefinition> blocks;
    private final Map<String, SurvivalSettings> survivalPresets;

    private GameRegistries(
            Path dataRoot, Map<BlockId, BlockDefinition> blocks, Map<String, SurvivalSettings> survivalPresets) {
        this.dataRoot = dataRoot;
        this.blocks = blocks;
        this.survivalPresets = survivalPresets;
    }

    public static GameRegistries load(Path dataRoot) {
        RegistryLoader<BlockDefinition, BlockId> blockLoader =
                new RegistryLoader<>(BlockDefinition.class, (path, definition) -> definition.id());
        RegistryLoader<SurvivalSettings, String> survivalPresetLoader =
                new RegistryLoader<>(SurvivalSettings.class, (path, definition) -> stripExtension(path));

        return new GameRegistries(
                dataRoot,
                blockLoader.loadDirectory(dataRoot.resolve("blocks")),
                survivalPresetLoader.loadDirectory(dataRoot.resolve("settings_presets")));
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public Map<BlockId, BlockDefinition> blocks() {
        return blocks;
    }

    public Map<String, SurvivalSettings> survivalPresets() {
        return survivalPresets;
    }

    public BlockDefinition requireBlockDefinition(BlockId blockId) {
        BlockDefinition definition = blocks.get(blockId);
        if (definition == null) {
            throw new IllegalStateException("Missing block definition for " + blockId);
        }
        return definition;
    }

    private static String stripExtension(Path path) {
        String fileName = path.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        return extensionIndex >= 0 ? fileName.substring(0, extensionIndex) : fileName;
    }
}
