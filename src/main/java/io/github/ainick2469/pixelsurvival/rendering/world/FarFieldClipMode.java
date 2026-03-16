package io.github.ainick2469.pixelsurvival.rendering.world;

public enum FarFieldClipMode {
    FULL_REGION,
    CLIP_INNER,
    CLIP_OUTER,
    CLIP_BOTH;

    public boolean requiresBoundaryClipping() {
        return this != FULL_REGION;
    }
}
