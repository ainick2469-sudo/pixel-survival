package io.github.ainick2469.pixelsurvival.registry;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Stream;

public final class RegistryLoader<T, K> {
    private final ObjectMapper objectMapper;
    private final Class<T> type;
    private final BiFunction<Path, T, K> keyExtractor;

    public RegistryLoader(Class<T> type, BiFunction<Path, T, K> keyExtractor) {
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.type = type;
        this.keyExtractor = keyExtractor;
    }

    public Map<K, T> loadDirectory(Path directory) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException("Missing registry directory: " + directory.toAbsolutePath());
        }

        LinkedHashMap<K, T> entries = new LinkedHashMap<>();

        try (Stream<Path> files = Files.list(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> loadEntry(path, entries));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read registry directory " + directory.toAbsolutePath(), exception);
        }

        return Map.copyOf(entries);
    }

    private void loadEntry(Path path, Map<K, T> entries) {
        try {
            T value = objectMapper.readValue(path.toFile(), type);
            K key = keyExtractor.apply(path, value);
            T existing = entries.putIfAbsent(key, value);
            if (existing != null) {
                throw new IllegalStateException("Duplicate registry key '" + key + "' in " + path.toAbsolutePath());
            }
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to parse registry file " + path.toAbsolutePath(), exception);
        }
    }
}
