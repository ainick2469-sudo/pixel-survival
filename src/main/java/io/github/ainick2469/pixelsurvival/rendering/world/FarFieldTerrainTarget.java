package io.github.ainick2469.pixelsurvival.rendering.world;

public record FarFieldTerrainTarget(
        FarFieldTerrainRegionCoord regionCoord,
        FarFieldClipMode clipMode) {
    public boolean requiresBoundaryClipping() {
        return clipMode.requiresBoundaryClipping();
    }
}
