package io.github.ainick2469.pixelsurvival.world.block;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum BlockTextureMode {
    SINGLE("single"),
    TOP_SIDE_BOTTOM("top_side_bottom"),
    EXPLICIT_FACES("explicit_faces"),
    CUBE_NET("cube_net");

    private final String id;

    BlockTextureMode(String id) {
        this.id = id;
    }

    @JsonCreator
    public static BlockTextureMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (BlockTextureMode mode : values()) {
            if (mode.id.equals(normalized)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown block texture mode: " + value);
    }

    @JsonValue
    public String id() {
        return id;
    }
}
