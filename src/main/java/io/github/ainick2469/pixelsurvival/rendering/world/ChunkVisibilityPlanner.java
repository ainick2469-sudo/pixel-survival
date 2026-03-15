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
    private static final int ALWAYS_LOADED_RADIUS_CHUNKS = 2;
    private static final int QUICK_TURN_RADIUS_CHUNKS = 5;
    private static final float MIN_HORIZONTAL_VIEW_DEGREES = 52f;
    private static final float MAX_HORIZONTAL_VIEW_DEGREES = 120f;
    private static final float RENDER_OVERSCAN_DEGREES = 10f;
    private static final float LOAD_OVERSCAN_DEGREES = 24f;

    public RuntimeTargets plan(
            Vector3f cameraLocation,
            Vector3f cameraDirection,
            float horizontalViewDegrees,
            ChunkRuntimeConfig runtimeConfig) {
        ChunkCoord centerChunk = chunkCoordFor(cameraLocation);
        Vector3f forward = horizontalForward(cameraDirection);
        float renderHalfAngleDegrees = renderHalfAngleDegrees(horizontalViewDegrees);
        float loadHalfAngleDegrees = Math.min(89f, renderHalfAngleDegrees + LOAD_OVERSCAN_DEGREES);
        float renderDot = (float) Math.cos(Math.toRadians(renderHalfAngleDegrees));
        float loadDot = (float) Math.cos(Math.toRadians(loadHalfAngleDegrees));

        List<TargetCandidate> loadCandidates = new ArrayList<>();
        List<TargetCandidate> renderCandidates = new ArrayList<>();
        Set<ChunkCoord> simulationTargets = new LinkedHashSet<>();

        int loadRadiusSquared = runtimeConfig.loadRadius() * runtimeConfig.loadRadius();
        int renderRadiusSquared = runtimeConfig.renderRadius() * runtimeConfig.renderRadius();
        int simulationRadiusSquared = runtimeConfig.simulationRadius() * runtimeConfig.simulationRadius();
        int alwaysLoadedRadiusSquared = ALWAYS_LOADED_RADIUS_CHUNKS * ALWAYS_LOADED_RADIUS_CHUNKS;
        int quickTurnRadiusSquared = QUICK_TURN_RADIUS_CHUNKS * QUICK_TURN_RADIUS_CHUNKS;

        for (int chunkX = centerChunk.x() - runtimeConfig.loadRadius();
                chunkX <= centerChunk.x() + runtimeConfig.loadRadius();
                chunkX++) {
            for (int chunkZ = centerChunk.z() - runtimeConfig.loadRadius();
                    chunkZ <= centerChunk.z() + runtimeConfig.loadRadius();
                    chunkZ++) {
                int deltaChunkX = chunkX - centerChunk.x();
                int deltaChunkZ = chunkZ - centerChunk.z();
                int chunkDistanceSquared = (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
                if (chunkDistanceSquared > loadRadiusSquared) {
                    continue;
                }

                ChunkCoord chunkCoord = new ChunkCoord(chunkX, chunkZ);
                if (chunkDistanceSquared <= simulationRadiusSquared) {
                    simulationTargets.add(chunkCoord);
                }

                boolean alwaysLoaded = chunkDistanceSquared <= alwaysLoadedRadiusSquared;
                boolean quickTurnChunk = chunkDistanceSquared <= quickTurnRadiusSquared;
                float alignment = alignmentToChunk(cameraLocation, forward, chunkCoord);
                boolean withinRenderCone = alignment >= renderDot;
                boolean withinLoadCone = alignment >= loadDot;

                if (alwaysLoaded
                        || (chunkDistanceSquared <= renderRadiusSquared && (quickTurnChunk || withinRenderCone))) {
                    renderCandidates.add(new TargetCandidate(chunkCoord, chunkDistanceSquared, alignment, alwaysLoaded));
                }
                if (alwaysLoaded
                        || quickTurnChunk
                        || (chunkDistanceSquared <= renderRadiusSquared && withinRenderCone)
                        || withinLoadCone) {
                    loadCandidates.add(new TargetCandidate(chunkCoord, chunkDistanceSquared, alignment, alwaysLoaded));
                }
            }
        }

        return new RuntimeTargets(
                orderedTargets(loadCandidates),
                orderedTargets(renderCandidates),
                Set.copyOf(simulationTargets));
    }

    private Set<ChunkCoord> orderedTargets(List<TargetCandidate> candidates) {
        candidates.sort(Comparator.comparingInt(TargetCandidate::priorityBucket)
                .thenComparing(Comparator.comparingDouble(TargetCandidate::alignment).reversed())
                .thenComparingInt(TargetCandidate::chunkDistanceSquared));

        LinkedHashSet<ChunkCoord> orderedTargets = new LinkedHashSet<>();
        for (TargetCandidate candidate : candidates) {
            orderedTargets.add(candidate.chunkCoord());
        }
        return orderedTargets;
    }

    private float alignmentToChunk(Vector3f cameraLocation, Vector3f forward, ChunkCoord chunkCoord) {
        float centerX = (chunkCoord.x() * ChunkData.SIZE_X) + (ChunkData.SIZE_X * 0.5f);
        float centerZ = (chunkCoord.z() * ChunkData.SIZE_Z) + (ChunkData.SIZE_Z * 0.5f);
        float deltaX = centerX - cameraLocation.x;
        float deltaZ = centerZ - cameraLocation.z;
        float planarLength = (float) Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        if (planarLength < 0.0001f) {
            return 1f;
        }
        return ((deltaX / planarLength) * forward.x) + ((deltaZ / planarLength) * forward.z);
    }

    private Vector3f horizontalForward(Vector3f cameraDirection) {
        Vector3f horizontal = new Vector3f(cameraDirection.x, 0f, cameraDirection.z);
        if (horizontal.lengthSquared() < 0.0001f) {
            return new Vector3f(0f, 0f, 1f);
        }
        return horizontal.normalizeLocal();
    }

    private float renderHalfAngleDegrees(float horizontalViewDegrees) {
        float clampedViewDegrees = Math.max(
                MIN_HORIZONTAL_VIEW_DEGREES,
                Math.min(MAX_HORIZONTAL_VIEW_DEGREES, horizontalViewDegrees));
        return Math.min(87f, (clampedViewDegrees * 0.5f) + RENDER_OVERSCAN_DEGREES);
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
            float alignment,
            boolean alwaysLoaded) {
        private int priorityBucket() {
            if (alwaysLoaded) {
                return 0;
            }
            return alignment >= 0.8f ? 1 : 2;
        }
    }
}
