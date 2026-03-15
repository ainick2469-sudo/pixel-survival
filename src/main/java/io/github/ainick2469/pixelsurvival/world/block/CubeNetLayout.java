package io.github.ainick2469.pixelsurvival.world.block;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum CubeNetLayout {
    BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM("back_top_left_front_right_bottom"),
    CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM("center_top_surrounding_sides_outer_bottom");

    private final String id;

    CubeNetLayout(String id) {
        this.id = id;
    }

    @JsonCreator
    public static CubeNetLayout fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CubeNetLayout layout : values()) {
            if (layout.id.equals(normalized)) {
                return layout;
            }
        }
        throw new IllegalArgumentException("Unknown cube-net layout: " + value);
    }

    @JsonValue
    public String id() {
        return id;
    }

    public int faceSize(int imageWidth, int imageHeight) {
        if (imageWidth % 3 != 0) {
            throw new IllegalArgumentException("Cube-net image width must be divisible by 3.");
        }
        int derivedFaceSize = imageWidth / 3;
        if (imageHeight != derivedFaceSize * 4) {
            throw new IllegalArgumentException("Cube-net image height must equal faceSize * 4.");
        }
        return derivedFaceSize;
    }

    public int tileX(BlockTextureFace face) {
        return switch (this) {
            case BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM -> switch (face) {
                case BACK, TOP, BOTTOM -> 1;
                case LEFT -> 0;
                case FRONT -> 1;
                case RIGHT -> 2;
            };
            case CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM -> switch (face) {
                case BACK, TOP, BOTTOM, FRONT -> 1;
                case LEFT -> 0;
                case RIGHT -> 2;
            };
        };
    }

    public int tileY(BlockTextureFace face) {
        return switch (this) {
            case BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM -> switch (face) {
                case BACK -> 0;
                case TOP -> 1;
                case LEFT, FRONT, RIGHT -> 2;
                case BOTTOM -> 3;
            };
            case CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM -> switch (face) {
                case BOTTOM -> 0;
                case BACK -> 1;
                case LEFT, TOP, RIGHT -> 2;
                case FRONT -> 3;
            };
        };
    }
}
