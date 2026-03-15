package io.github.ainick2469.pixelsurvival.world.gen.topology;

public sealed interface WorldTopologyProfile permits PlanarPrototypeTopologyProfile, CubeSpherePlanetTopologyProfile {
    WorldTopologyKind kind();

    boolean supportsWraparoundTraversal();

    String summary();
}
