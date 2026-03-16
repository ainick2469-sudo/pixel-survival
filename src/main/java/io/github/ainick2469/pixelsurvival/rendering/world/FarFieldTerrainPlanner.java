package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FarFieldTerrainPlanner {
    public List<FarFieldTerrainTarget> plan(ChunkCoord centerChunk, FarFieldTerrainSettings settings) {
        if (centerChunk == null || settings == null) {
            return List.of();
        }

        int span = settings.regionSpanChunks();
        int minRegionX = Math.floorDiv(centerChunk.x() - settings.endRadiusChunks() - span, span);
        int maxRegionX = Math.floorDiv(centerChunk.x() + settings.endRadiusChunks() + span, span);
        int minRegionZ = Math.floorDiv(centerChunk.z() - settings.endRadiusChunks() - span, span);
        int maxRegionZ = Math.floorDiv(centerChunk.z() + settings.endRadiusChunks() + span, span);
        int innerDistanceSquared = settings.startRadiusChunks() * settings.startRadiusChunks();
        int outerDistanceSquared = settings.endRadiusChunks() * settings.endRadiusChunks();

        List<RegionCandidate> candidates = new ArrayList<>();
        for (int regionX = minRegionX; regionX <= maxRegionX; regionX++) {
            for (int regionZ = minRegionZ; regionZ <= maxRegionZ; regionZ++) {
                FarFieldTerrainRegionCoord regionCoord = new FarFieldTerrainRegionCoord(regionX, regionZ);
                int minDistanceSquared = minDistanceSquared(centerChunk, regionCoord, settings);
                int maxDistanceSquared = maxDistanceSquared(centerChunk, regionCoord, settings);
                if (minDistanceSquared > outerDistanceSquared) {
                    continue;
                }
                if (maxDistanceSquared < innerDistanceSquared) {
                    continue;
                }

                candidates.add(new RegionCandidate(
                        new FarFieldTerrainTarget(
                                regionCoord,
                                clipModeFor(minDistanceSquared, maxDistanceSquared, innerDistanceSquared, outerDistanceSquared)),
                        regionCenterDistanceSquared(centerChunk, regionCoord, settings),
                        manhattanDistance(centerChunk, regionCoord, settings)));
            }
        }

        candidates.sort(Comparator.comparingDouble(RegionCandidate::distanceSquared)
                .thenComparingInt(RegionCandidate::manhattanDistance)
                .thenComparingInt(candidate -> candidate.target().regionCoord().x())
                .thenComparingInt(candidate -> candidate.target().regionCoord().z()));

        List<FarFieldTerrainTarget> orderedTargets = new ArrayList<>(candidates.size());
        for (RegionCandidate candidate : candidates) {
            orderedTargets.add(candidate.target());
        }
        return List.copyOf(orderedTargets);
    }

    private FarFieldClipMode clipModeFor(
            int minDistanceSquared,
            int maxDistanceSquared,
            int innerDistanceSquared,
            int outerDistanceSquared) {
        boolean clipsInner = minDistanceSquared < innerDistanceSquared;
        boolean clipsOuter = maxDistanceSquared > outerDistanceSquared;
        if (clipsInner && clipsOuter) {
            return FarFieldClipMode.CLIP_BOTH;
        }
        if (clipsInner) {
            return FarFieldClipMode.CLIP_INNER;
        }
        if (clipsOuter) {
            return FarFieldClipMode.CLIP_OUTER;
        }
        return FarFieldClipMode.FULL_REGION;
    }

    private int minDistanceSquared(
            ChunkCoord centerChunk,
            FarFieldTerrainRegionCoord regionCoord,
            FarFieldTerrainSettings settings) {
        int minDeltaX = distanceToRange(centerChunk.x(), regionCoord.startChunkX(settings), regionCoord.endChunkX(settings));
        int minDeltaZ = distanceToRange(centerChunk.z(), regionCoord.startChunkZ(settings), regionCoord.endChunkZ(settings));
        return (minDeltaX * minDeltaX) + (minDeltaZ * minDeltaZ);
    }

    private int maxDistanceSquared(
            ChunkCoord centerChunk,
            FarFieldTerrainRegionCoord regionCoord,
            FarFieldTerrainSettings settings) {
        int deltaChunkX = Math.max(
                Math.abs(centerChunk.x() - regionCoord.startChunkX(settings)),
                Math.abs(centerChunk.x() - regionCoord.endChunkX(settings)));
        int deltaChunkZ = Math.max(
                Math.abs(centerChunk.z() - regionCoord.startChunkZ(settings)),
                Math.abs(centerChunk.z() - regionCoord.endChunkZ(settings)));
        return (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
    }

    private double regionCenterDistanceSquared(
            ChunkCoord centerChunk,
            FarFieldTerrainRegionCoord regionCoord,
            FarFieldTerrainSettings settings) {
        double centerChunkX = centerChunk.x() + 0.5d;
        double centerChunkZ = centerChunk.z() + 0.5d;
        double regionCenterChunkX = regionCoord.startChunkX(settings) + (settings.regionSpanChunks() / 2d);
        double regionCenterChunkZ = regionCoord.startChunkZ(settings) + (settings.regionSpanChunks() / 2d);
        double deltaChunkX = regionCenterChunkX - centerChunkX;
        double deltaChunkZ = regionCenterChunkZ - centerChunkZ;
        return (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
    }

    private int manhattanDistance(
            ChunkCoord centerChunk,
            FarFieldTerrainRegionCoord regionCoord,
            FarFieldTerrainSettings settings) {
        int regionCenterChunkX = regionCoord.startChunkX(settings) + (settings.regionSpanChunks() / 2);
        int regionCenterChunkZ = regionCoord.startChunkZ(settings) + (settings.regionSpanChunks() / 2);
        return Math.abs(regionCenterChunkX - centerChunk.x()) + Math.abs(regionCenterChunkZ - centerChunk.z());
    }

    private int distanceToRange(int value, int minInclusive, int maxInclusive) {
        if (value < minInclusive) {
            return minInclusive - value;
        }
        if (value > maxInclusive) {
            return value - maxInclusive;
        }
        return 0;
    }

    private record RegionCandidate(
            FarFieldTerrainTarget target,
            double distanceSquared,
            int manhattanDistance) {
    }
}
