package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChunkVisibilityPlanner {
    public RuntimeTargets plan(
            Vector3f cameraLocation,
            Vector3f cameraDirection,
            float horizontalViewDegrees,
            ChunkRuntimeConfig runtimeConfig) {
        ChunkCoord centerChunk = chunkCoordFor(cameraLocation);
        return new RuntimeTargets(
                orderedTargets(centerChunk, runtimeConfig.loadRadius()),
                orderedTargets(centerChunk, runtimeConfig.renderRadius()),
                orderedTargets(centerChunk, runtimeConfig.simulationRadius()));
    }

    private Set<ChunkCoord> orderedTargets(ChunkCoord centerChunk, int radius) {
        LinkedHashSet<ChunkCoord> orderedTargets = new LinkedHashSet<>();
        if (radius < 0) {
            return orderedTargets;
        }

        List<TargetCandidate> candidates = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (int chunkX = centerChunk.x() - radius; chunkX <= centerChunk.x() + radius; chunkX++) {
            for (int chunkZ = centerChunk.z() - radius; chunkZ <= centerChunk.z() + radius; chunkZ++) {
                int deltaChunkX = chunkX - centerChunk.x();
                int deltaChunkZ = chunkZ - centerChunk.z();
                int chunkDistanceSquared = (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
                if (chunkDistanceSquared > radiusSquared) {
                    continue;
                }

                candidates.add(new TargetCandidate(
                        new ChunkCoord(chunkX, chunkZ),
                        chunkDistanceSquared,
                        Math.abs(deltaChunkX) + Math.abs(deltaChunkZ),
                        deltaChunkX,
                        deltaChunkZ));
            }
        }

        candidates.sort(Comparator.comparingInt(TargetCandidate::chunkDistanceSquared)
                .thenComparingInt(TargetCandidate::manhattanDistance)
                .thenComparingInt(TargetCandidate::ringPriority)
                .thenComparingInt(TargetCandidate::deltaChunkX)
                .thenComparingInt(TargetCandidate::deltaChunkZ));

        for (TargetCandidate candidate : candidates) {
            orderedTargets.add(candidate.chunkCoord());
        }
        return orderedTargets;
    }

    private ChunkCoord chunkCoordFor(Vector3f location) {
        return new ChunkCoord(
                Math.floorDiv((int) Math.floor(location.x), ChunkData.SIZE_X),
                Math.floorDiv((int) Math.floor(location.z), ChunkData.SIZE_Z));
    }

    public record RuntimeTargets(
            Set<ChunkCoord> loadTargets,
            Set<ChunkCoord> renderTargets,
            Set<ChunkCoord> simulationTargets) {
    }

    private record TargetCandidate(
            ChunkCoord chunkCoord,
            int chunkDistanceSquared,
            int manhattanDistance,
            int deltaChunkX,
            int deltaChunkZ) {
        private int ringPriority() {
            if (deltaChunkX == 0 && deltaChunkZ == 0) {
                return 0;
            }
            if (deltaChunkX == 0 || deltaChunkZ == 0) {
                return 1;
            }
            return 2;
        }
    }
}
