package io.github.ainick2469.pixelsurvival.world.gen.pipeline;

public final class SurfaceHeightProfile {
    private final int baseHeight;
    private final int minSurfaceHeight;
    private final int maxSurfaceHeight;
    private final int broadSeed;
    private final int ridgeSeed;
    private final int detailSeed;

    public SurfaceHeightProfile(
            int baseHeight,
            int minSurfaceHeight,
            int maxSurfaceHeight,
            int broadSeed,
            int ridgeSeed,
            int detailSeed) {
        this.baseHeight = baseHeight;
        this.minSurfaceHeight = minSurfaceHeight;
        this.maxSurfaceHeight = maxSurfaceHeight;
        this.broadSeed = broadSeed;
        this.ridgeSeed = ridgeSeed;
        this.detailSeed = detailSeed;
    }

    public int sampleSurfaceHeight(int worldX, int worldZ) {
        double broadNoise = sampleValueNoise(worldX * 0.045, worldZ * 0.045, broadSeed) * 8.0;
        double ridgeNoise = sampleValueNoise(worldX * 0.022, worldZ * 0.022, ridgeSeed) * 11.0;
        double detailNoise = sampleValueNoise(worldX * 0.12, worldZ * 0.12, detailSeed) * 2.5;

        int height = (int) Math.round(baseHeight + broadNoise + ridgeNoise + detailNoise);
        return Math.max(minSurfaceHeight, Math.min(maxSurfaceHeight, height));
    }

    private double sampleValueNoise(double sampleX, double sampleZ, int seed) {
        int x0 = (int) Math.floor(sampleX);
        int z0 = (int) Math.floor(sampleZ);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double tx = smoothStep(sampleX - x0);
        double tz = smoothStep(sampleZ - z0);

        double v00 = latticeValue(x0, z0, seed);
        double v10 = latticeValue(x1, z0, seed);
        double v01 = latticeValue(x0, z1, seed);
        double v11 = latticeValue(x1, z1, seed);

        double blendX0 = lerp(v00, v10, tx);
        double blendX1 = lerp(v01, v11, tx);
        return lerp(blendX0, blendX1, tz);
    }

    private double latticeValue(int x, int z, int seed) {
        long hash = 1469598103934665603L;
        hash ^= x * 0x9E3779B97F4A7C15L;
        hash *= 1099511628211L;
        hash ^= z * 0xC2B2AE3D27D4EB4FL;
        hash *= 1099511628211L;
        hash ^= seed * 0x165667B19E3779F9L;
        hash *= 1099511628211L;

        long positive = hash & 0x7fffffffffffffffL;
        return (positive / (double) Long.MAX_VALUE) * 2.0 - 1.0;
    }

    private double smoothStep(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
