package io.github.ainick2469.pixelsurvival.world.block;

public record BlockVisualDefinition(
        BlockTextureMode textureMode,
        String texture,
        String topTexture,
        String sideTexture,
        String bottomTexture,
        String frontTexture,
        String backTexture,
        String leftTexture,
        String rightTexture,
        String cubeNetTexture,
        CubeNetLayout cubeNetLayout,
        String tintKey) {
    public BlockVisualDefinition {
        texture = normalize(texture);
        topTexture = normalize(topTexture);
        sideTexture = normalize(sideTexture);
        bottomTexture = normalize(bottomTexture);
        frontTexture = normalize(frontTexture);
        backTexture = normalize(backTexture);
        leftTexture = normalize(leftTexture);
        rightTexture = normalize(rightTexture);
        cubeNetTexture = normalize(cubeNetTexture);
        tintKey = normalize(tintKey);

        textureMode = inferTextureMode(
                textureMode,
                texture,
                topTexture,
                sideTexture,
                bottomTexture,
                frontTexture,
                backTexture,
                leftTexture,
                rightTexture,
                cubeNetTexture);
        cubeNetLayout = cubeNetLayout == null ? CubeNetLayout.BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM : cubeNetLayout;

        switch (textureMode) {
            case SINGLE -> require(texture, "texture");
            case TOP_SIDE_BOTTOM -> {
                require(topTexture, "topTexture");
                require(sideTexture, "sideTexture");
                require(bottomTexture, "bottomTexture");
            }
            case EXPLICIT_FACES -> {
                require(topTexture, "topTexture");
                require(bottomTexture, "bottomTexture");
                require(frontTexture, "frontTexture");
                require(backTexture, "backTexture");
                require(leftTexture, "leftTexture");
                require(rightTexture, "rightTexture");
            }
            case CUBE_NET -> require(cubeNetTexture, "cubeNetTexture");
        }
    }

    public BlockFaceTextureReference textureReferenceFor(BlockTextureFace face) {
        return switch (textureMode) {
            case SINGLE -> BlockFaceTextureReference.direct(texture);
            case TOP_SIDE_BOTTOM -> switch (face) {
                case TOP -> BlockFaceTextureReference.direct(topTexture);
                case BOTTOM -> BlockFaceTextureReference.direct(bottomTexture);
                case BACK, LEFT, FRONT, RIGHT -> BlockFaceTextureReference.direct(sideTexture);
            };
            case EXPLICIT_FACES -> BlockFaceTextureReference.direct(directFaceTexture(face));
            case CUBE_NET -> BlockFaceTextureReference.cubeNet(cubeNetTexture, cubeNetLayout, face);
        };
    }

    private String directFaceTexture(BlockTextureFace face) {
        return switch (face) {
            case TOP -> topTexture;
            case BOTTOM -> bottomTexture;
            case FRONT -> frontTexture;
            case BACK -> backTexture;
            case LEFT -> leftTexture;
            case RIGHT -> rightTexture;
        };
    }

    private static BlockTextureMode inferTextureMode(
            BlockTextureMode explicitMode,
            String texture,
            String topTexture,
            String sideTexture,
            String bottomTexture,
            String frontTexture,
            String backTexture,
            String leftTexture,
            String rightTexture,
            String cubeNetTexture) {
        if (explicitMode != null) {
            return explicitMode;
        }
        if (cubeNetTexture != null) {
            return BlockTextureMode.CUBE_NET;
        }
        if (texture != null) {
            return BlockTextureMode.SINGLE;
        }
        if (topTexture != null && sideTexture != null && bottomTexture != null) {
            return BlockTextureMode.TOP_SIDE_BOTTOM;
        }
        if (topTexture != null
                && bottomTexture != null
                && frontTexture != null
                && backTexture != null
                && leftTexture != null
                && rightTexture != null) {
            return BlockTextureMode.EXPLICIT_FACES;
        }
        throw new IllegalArgumentException("Unable to infer a block texture mode from the provided visual fields.");
    }

    private static void require(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Missing required block visual field: " + fieldName);
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
