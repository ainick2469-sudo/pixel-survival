package io.github.ainick2469.pixelsurvival.registry;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class GameDataPaths {
    private static final String DATA_DIR_NAME = "data";
    private static final String DATA_ENV_VAR = "PIXEL_SURVIVAL_DATA_DIR";

    private GameDataPaths() {
    }

    public static Path resolveDataRoot() {
        List<Path> candidates = new ArrayList<>();

        String configuredPath = System.getenv(DATA_ENV_VAR);
        if (configuredPath != null && !configuredPath.isBlank()) {
            candidates.add(Path.of(configuredPath));
        }

        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        candidates.add(workingDirectory.resolve(DATA_DIR_NAME));

        Path current = codeSourceBase();
        for (int depth = 0; depth < 5 && current != null; depth++) {
            candidates.add(current.resolve(DATA_DIR_NAME));
            current = current.getParent();
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate.normalize();
            }
        }

        return workingDirectory.resolve(DATA_DIR_NAME).normalize();
    }

    private static Path codeSourceBase() {
        try {
            Path codeSource =
                    Path.of(GameDataPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return Files.isRegularFile(codeSource) ? codeSource.getParent() : codeSource;
        } catch (URISyntaxException exception) {
            return null;
        }
    }
}
