package io.github.ainick2469.pixelsurvival.world.gen.topology;

public record CubeSpherePlanetTopologyProfile(
        int faceChunkSpan,
        int nominalPlanetRadiusBlocks,
        int climateLatitudeBands) implements WorldTopologyProfile {
    public CubeSpherePlanetTopologyProfile {
        if (faceChunkSpan < 4) {
            throw new IllegalArgumentException("faceChunkSpan must be at least 4 chunks.");
        }
        if (nominalPlanetRadiusBlocks < 128) {
            throw new IllegalArgumentException("nominalPlanetRadiusBlocks must be at least 128.");
        }
        if (climateLatitudeBands < 3) {
            throw new IllegalArgumentException("climateLatitudeBands must be at least 3.");
        }
    }

    @Override
    public WorldTopologyKind kind() {
        return WorldTopologyKind.CUBE_SPHERE_PLANET;
    }

    @Override
    public boolean supportsWraparoundTraversal() {
        return true;
    }

    @Override
    public String summary() {
        return "Future cube-sphere planetary topology with streamed surface faces and wraparound traversal.";
    }
}
