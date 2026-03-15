package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkGenerationScratchpad {
    private final Map<String, ColumnIntField> intFields = new ConcurrentHashMap<>();
    private final Map<String, ColumnFloatField> floatFields = new ConcurrentHashMap<>();
    private final Map<String, ColumnBooleanField> booleanFields = new ConcurrentHashMap<>();

    public ColumnIntField intField(String key) {
        return intFields.computeIfAbsent(key, ignored -> new ColumnIntField());
    }

    public ColumnFloatField floatField(String key) {
        return floatFields.computeIfAbsent(key, ignored -> new ColumnFloatField());
    }

    public ColumnBooleanField booleanField(String key) {
        return booleanFields.computeIfAbsent(key, ignored -> new ColumnBooleanField());
    }

    public ColumnIntField requireIntField(String key) {
        ColumnIntField field = intFields.get(key);
        if (field == null) {
            throw new IllegalStateException("Missing required int worldgen field: " + key);
        }
        return field;
    }
}
