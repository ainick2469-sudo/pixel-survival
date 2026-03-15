package io.github.ainick2469.pixelsurvival.session;

import io.github.ainick2469.pixelsurvival.registry.GameDataPaths;
import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.settings.GameSettings;
import io.github.ainick2469.pixelsurvival.world.gen.HeightmapWorldGenerator;
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
                new AuthoritativeWorldService(registries, new HeightmapWorldGenerator());

        return new LocalHostSession(GameSessionMode.LOCAL_HOST, registries, gameSettings, worldService);
    }

    public void start() {
        // Local play still boots through an authoritative host boundary.
        // Chunk loading is now demand-driven by the chunk runtime manager.
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
