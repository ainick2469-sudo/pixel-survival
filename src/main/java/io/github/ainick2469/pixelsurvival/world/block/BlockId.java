package io.github.ainick2469.pixelsurvival.world.block;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

public record BlockId(String value) {
    public BlockId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank() || !value.contains(":")) {
            throw new IllegalArgumentException("Block IDs must be namespaced, for example pixel_survival:dirt");
        }
    }

    @JsonCreator
    public static BlockId of(String value) {
        return new BlockId(value);
    }

    @JsonValue
    @Override
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
