package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ChunkVisibilityPlanner {
    private final Map<Integer, List<ChunkOffset>> orderedOffsetsByRadius = new ConcurrentHashMap<>();

    public RuntimeTargets plan(
            Vector3f cameraLocation,
            Vector3f cameraDirection,
            float horizontalViewDegrees,
            ChunkRuntimeConfig runtimeConfig) {
        return plan(centerChunkFor(cameraLocation), runtimeConfig);
    }

    public RuntimeTargets plan(ChunkCoord centerChunk, ChunkRuntimeConfig runtimeConfig) {
        return new RuntimeTargets(
                applyOffsets(centerChunk, orderedOffsets(runtimeConfig.loadRadius())),
                applyOffsets(centerChunk, orderedOffsets(runtimeConfig.renderRadius())),
                applyOffsets(centerChunk, orderedOffsets(runtimeConfig.simulationRadius())));
    }

    public ChunkCoord centerChunkFor(Vector3f location) {
        return new ChunkCoord(
                Math.floorDiv((int) Math.floor(location.x), ChunkData.SIZE_X),
                Math.floorDiv((int) Math.floor(location.z), ChunkData.SIZE_Z));
    }

    private Set<ChunkCoord> applyOffsets(ChunkCoord centerChunk, List<ChunkOffset> orderedOffsets) {
        LinkedHashSet<ChunkCoord> orderedTargets = new LinkedHashSet<>(orderedOffsets.size());
        for (ChunkOffset offset : orderedOffsets) {
            orderedTargets.add(new ChunkCoord(centerChunk.x() + offset.deltaChunkX(), centerChunk.z() + offset.deltaChunkZ()));
        }
        return orderedTargets;
    }

    private List<ChunkOffset> orderedOffsets(int radius) {
        if (radius < 0) {
            return List.of();
        }
        return orderedOffsetsByRadius.computeIfAbsent(radius, this::buildOrderedOffsets);
    }

    private List<ChunkOffset> buildOrderedOffsets(int radius) {
        List<ChunkOffset> candidates = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (int deltaChunkX = -radius; deltaChunkX <= radius; deltaChunkX++) {
            for (int deltaChunkZ = -radius; deltaChunkZ <= radius; deltaChunkZ++) {
                int chunkDistanceSquared = (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
                if (chunkDistanceSquared > radiusSquared) {
                    continue;
                }

                candidates.add(new ChunkOffset(
                        deltaChunkX,
                        deltaChunkZ,
                        chunkDistanceSquared,
                        Math.abs(deltaChunkX) + Math.abs(deltaChunkZ)));
            }
        }

        candidates.sort(Comparator.comparingInt(ChunkOffset::chunkDistanceSquared)
                .thenComparingInt(ChunkOffset::manhattanDistance)
                .thenComparingInt(ChunkOffset::ringPriority)
                .thenComparingInt(ChunkOffset::deltaChunkX)
                .thenComparingInt(ChunkOffset::deltaChunkZ));

        return List.copyOf(candidates);
    }

    public record RuntimeTargets(
            Set<ChunkCoord> loadTargets,
            Set<ChunkCoord> renderTargets,
            Set<ChunkCoord> simulationTargets) {
    }

    private record ChunkOffset(
            int deltaChunkX,
            int deltaChunkZ,
            int chunkDistanceSquared,
            int manhattanDistance) {
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
