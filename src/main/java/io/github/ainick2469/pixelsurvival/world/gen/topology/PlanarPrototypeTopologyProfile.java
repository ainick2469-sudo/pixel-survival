package io.github.ainick2469.pixelsurvival.world.gen.topology;

public final class PlanarPrototypeTopologyProfile implements WorldTopologyProfile {
    public static final PlanarPrototypeTopologyProfile INSTANCE = new PlanarPrototypeTopologyProfile();

    private PlanarPrototypeTopologyProfile() {
    }

    @Override
    public WorldTopologyKind kind() {
        return WorldTopologyKind.PLANAR_PROTOTYPE;
    }

    @Override
    public boolean supportsWraparoundTraversal() {
        return false;
    }

    @Override
    public String summary() {
        return "Current prototype topology: infinite planar chunk space for early terrain milestones.";
    }
}
