package io.github.ainick2469.pixelsurvival.session;

import io.github.ainick2469.pixelsurvival.registry.GameDataPaths;
import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.settings.GameSettings;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.gen.FlatSpawnWorldGenerator;
import io.github.ainick2469.pixelsurvival.world.sim.AuthoritativeWorldService;
import java.nio.file.Path;

public final class LocalHostSession {
    private final GameSessionMode mode;
    private final GameRegistries registries;
    private final GameSettings gameSettings;
    private final AuthoritativeWorldService worldService;

    private LocalHostSession(
            GameSessionMode mode,
            GameRegistries registries,
            GameSettings gameSettings,
            AuthoritativeWorldService worldService) {
        this.mode = mode;
        this.registries = registries;
        this.gameSettings = gameSettings;
        this.worldService = worldService;
    }

    public static LocalHostSession bootstrap() {
        Path dataRoot = GameDataPaths.resolveDataRoot();
        GameRegistries registries = GameRegistries.load(dataRoot);
        GameSettings gameSettings = GameSettings.defaultSettings(registries.survivalPresets());
        AuthoritativeWorldService worldService =
                new AuthoritativeWorldService(registries, new FlatSpawnWorldGenerator());

        return new LocalHostSession(GameSessionMode.LOCAL_HOST, registries, gameSettings, worldService);
    }

    public void start() {
        worldService.loadChunk(new ChunkCoord(0, 0));
    }

    public GameSessionMode mode() {
        return mode;
    }

    public GameRegistries registries() {
        return registries;
    }

    public GameSettings gameSettings() {
        return gameSettings;
    }

    public AuthoritativeWorldService worldService() {
        return worldService;
    }
}
