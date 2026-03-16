package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.gen.FarFieldTerrainSampler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class FarFieldTerrainRenderer {
    private static final int MIN_PENDING_REGION_BUILDS = 12;
    private static final int MAX_PENDING_REGION_BUILDS = 48;
    private static final long MOVING_ATTACH_BUDGET_NANOS = 750_000L;
    private static final long STILL_ATTACH_BUDGET_NANOS = 3_500_000L;
    private static final long COUNTER_WINDOW_NANOS = 1_000_000_000L;
    private static final int MAX_SYNCHRONOUS_ULTRA_PRIME_REGIONS = 64;

    private final Node farTerrainRoot = new Node("far_terrain_root");
    private final TerrainMaterialLibrary terrainMaterialLibrary;
    private final FarFieldTerrainPlanner planner = new FarFieldTerrainPlanner();
    private final FarFieldTerrainMeshBuilder meshBuilder;
    private final ExecutorService backgroundExecutor;
    private final Map<FarFieldTerrainRegionCoord, CompletableFuture<FarFieldTerrainMeshBuildResult>> pendingRegionBuilds =
            new ConcurrentHashMap<>();
    private final Map<FarFieldTerrainRegionCoord, FarFieldTerrainTarget> pendingRegionTargets = new ConcurrentHashMap<>();
    private final Map<FarFieldTerrainRegionCoord, Node> renderedRegionNodes = new HashMap<>();
    private final Map<FarFieldTerrainRegionCoord, Integer> renderedRegionFaceCounts = new HashMap<>();
    private final Map<FarFieldTerrainRegionCoord, Integer> renderedRegionSectionCounts = new HashMap<>();
    private final Set<FarFieldTerrainRegionCoord> dirtyRegions = ConcurrentHashMap.newKeySet();
    private List<FarFieldTerrainTarget> activeTargets = List.of();
    private Map<FarFieldTerrainRegionCoord, FarFieldTerrainTarget> activeTargetsByRegion = Map.of();
    private ChunkCoord activeAnchorChunk;
    private FarFieldTerrainSettings activeSettings;
    private ChunkMotionProfile motionProfile = ChunkMotionProfile.STILL;
    private Vector3f priorityDirection = new Vector3f(0f, 0f, 1f);
    private float catchUpScale;
    private float frameTimeGovernorScale = 1f;
    private int totalRenderedFaceCount;
    private int totalRenderedSectionCount;
    private long nextCounterWindowNanos;
    private int currentWindowAnchorSnapCount;
    private int currentWindowRebuiltRegionCount;
    private int lastWindowAnchorSnapCount;
    private int lastWindowRebuiltRegionCount;

    public FarFieldTerrainRenderer(
            Node rootNode,
            TerrainMaterialLibrary terrainMaterialLibrary,
            GameRegistries registries,
            TerrainTexturePalette terrainTexturePalette,
            FarFieldTerrainSampler terrainSampler,
            ExecutorService backgroundExecutor) {
        this.terrainMaterialLibrary = terrainMaterialLibrary;
        this.meshBuilder = new FarFieldTerrainMeshBuilder(registries, terrainTexturePalette, terrainSampler);
        this.backgroundExecutor = backgroundExecutor;
        rootNode.attachChild(farTerrainRoot);
    }

    public void prime(ChunkCoord centerChunk, FarFieldTerrainSettings settings) {
        if (settings == null || centerChunk == null) {
            clear();
            return;
        }

        refreshTargets(centerChunk, settings);
        int primeLimit = synchronousPrimeRegionLimit(settings);
        int primedRegionCount = 0;
        for (FarFieldTerrainTarget target : prioritizedTargets()) {
            if (primedRegionCount >= primeLimit) {
                break;
            }
            attachRegionMesh(meshBuilder.buildRegionMesh(target, activeAnchorChunk, activeSettings));
            dirtyRegions.remove(target.regionCoord());
            primedRegionCount++;
        }
    }

    public void update(
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            ChunkMotionProfile motionProfile,
            Vector3f priorityDirection,
            float catchUpScale,
            float frameTimeGovernorScale) {
        if (settings == null || centerChunk == null) {
            clear();
            return;
        }

        rollCounterWindow(System.nanoTime());
        this.motionProfile = motionProfile;
        this.catchUpScale = catchUpScale;
        this.frameTimeGovernorScale = frameTimeGovernorScale;
        if (priorityDirection != null && priorityDirection.lengthSquared() > 0.0001f) {
            this.priorityDirection = priorityDirection.normalize();
        }
        boolean targetsChanged = refreshTargets(centerChunk, settings);
        enqueueMeshBuilds();
        attachCompletedMeshes();
        if (targetsChanged) {
            detachRenderedRegionsOutside(activeTargetsByRegion.keySet());
        }
    }

    public int renderedRegionCount() {
        return renderedRegionNodes.size();
    }

    public int renderedSectionCount() {
        return totalRenderedSectionCount;
    }

    public int renderedFaceCount() {
        return totalRenderedFaceCount;
    }

    public int pendingRegionBuildCount() {
        return pendingRegionBuilds.size();
    }

    public int rebuiltRegionCountLastWindow() {
        return lastWindowRebuiltRegionCount;
    }

    public int anchorSnapCountLastWindow() {
        return lastWindowAnchorSnapCount;
    }

    public boolean hasPendingWork() {
        return !pendingRegionBuilds.isEmpty();
    }

    public void close() {
        clear();
    }

    private boolean refreshTargets(ChunkCoord centerChunk, FarFieldTerrainSettings settings) {
        ChunkCoord nextAnchorChunk = resolveAnchorChunk(centerChunk, settings, activeAnchorChunk, activeSettings);
        boolean anchorChanged = activeAnchorChunk != null && !Objects.equals(nextAnchorChunk, activeAnchorChunk);
        boolean settingsChanged = !Objects.equals(settings, activeSettings);
        if (!anchorChanged && !settingsChanged) {
            return false;
        }

        if (anchorChanged) {
            currentWindowAnchorSnapCount++;
        }

        RefreshTargetsPlan refreshTargetsPlan =
                calculateRefreshTargetsPlan(activeTargetsByRegion, planner.plan(nextAnchorChunk, settings), settingsChanged, anchorChanged);

        for (FarFieldTerrainRegionCoord staleRegionCoord : refreshTargetsPlan.staleRegionCoords()) {
            cancelPendingBuild(staleRegionCoord);
        }
        dirtyRegions.retainAll(refreshTargetsPlan.nextTargetsByRegion().keySet());
        dirtyRegions.addAll(refreshTargetsPlan.dirtyRegionCoords());
        activeAnchorChunk = nextAnchorChunk;
        activeSettings = settings;
        activeTargets = refreshTargetsPlan.orderedTargets();
        activeTargetsByRegion = refreshTargetsPlan.nextTargetsByRegion();
        return true;
    }

    static ChunkCoord resolveAnchorChunk(
            ChunkCoord centerChunk,
            FarFieldTerrainSettings settings,
            ChunkCoord activeAnchorChunk,
            FarFieldTerrainSettings activeSettings) {
        if (activeAnchorChunk == null || activeSettings == null || !activeSettings.equals(settings)) {
            return snappedAnchorChunk(centerChunk, settings.regionSpanChunks());
        }

        int deltaChunkX = centerChunk.x() - activeAnchorChunk.x();
        int deltaChunkZ = centerChunk.z() - activeAnchorChunk.z();
        if (Math.max(Math.abs(deltaChunkX), Math.abs(deltaChunkZ)) <= settings.anchorHysteresisChunks()) {
            return activeAnchorChunk;
        }
        return snappedAnchorChunk(centerChunk, settings.regionSpanChunks());
    }

    static ChunkCoord snappedAnchorChunk(ChunkCoord centerChunk, int regionSpanChunks) {
        return new ChunkCoord(
                Math.floorDiv(centerChunk.x(), regionSpanChunks) * regionSpanChunks + (regionSpanChunks / 2),
                Math.floorDiv(centerChunk.z(), regionSpanChunks) * regionSpanChunks + (regionSpanChunks / 2));
    }

    static RefreshTargetsPlan calculateRefreshTargetsPlan(
            Map<FarFieldTerrainRegionCoord, FarFieldTerrainTarget> activeTargetsByRegion,
            List<FarFieldTerrainTarget> nextTargets,
            boolean settingsChanged,
            boolean anchorChanged) {
        LinkedHashMap<FarFieldTerrainRegionCoord, FarFieldTerrainTarget> nextTargetsByRegion = new LinkedHashMap<>();
        for (FarFieldTerrainTarget target : nextTargets) {
            nextTargetsByRegion.put(target.regionCoord(), target);
        }

        Set<FarFieldTerrainRegionCoord> dirtyRegionCoords = ConcurrentHashMap.newKeySet();
        Set<FarFieldTerrainRegionCoord> staleRegionCoords = ConcurrentHashMap.newKeySet();
        for (FarFieldTerrainTarget target : nextTargets) {
            FarFieldTerrainTarget priorTarget = activeTargetsByRegion.get(target.regionCoord());
            if (priorTarget == null
                    || settingsChanged
                    || priorTarget.clipMode() != target.clipMode()
                    || (anchorChanged && target.clipMode() != FarFieldClipMode.FULL_REGION)) {
                dirtyRegionCoords.add(target.regionCoord());
            }
        }
        for (FarFieldTerrainRegionCoord regionCoord : activeTargetsByRegion.keySet()) {
            if (!nextTargetsByRegion.containsKey(regionCoord)) {
                staleRegionCoords.add(regionCoord);
            }
        }
        return new RefreshTargetsPlan(
                List.copyOf(nextTargets),
                Map.copyOf(nextTargetsByRegion),
                Set.copyOf(dirtyRegionCoords),
                Set.copyOf(staleRegionCoords));
    }

    private void enqueueMeshBuilds() {
        int availableSlots = Math.max(0, maxPendingRegionBuilds() - pendingRegionBuilds.size());
        if (availableSlots == 0) {
            return;
        }

        for (FarFieldTerrainTarget target : prioritizedTargets()) {
            if (availableSlots == 0) {
                break;
            }
            FarFieldTerrainRegionCoord regionCoord = target.regionCoord();
            if (pendingRegionBuilds.containsKey(regionCoord)) {
                if (Objects.equals(target, pendingRegionTargets.get(regionCoord))) {
                    continue;
                }
                cancelPendingBuild(regionCoord);
            }
            if (renderedRegionNodes.containsKey(regionCoord) && !dirtyRegions.contains(regionCoord)) {
                continue;
            }

            CompletableFuture<FarFieldTerrainMeshBuildResult> meshFuture =
                    CompletableFuture.supplyAsync(
                            () -> meshBuilder.buildRegionMesh(target, activeAnchorChunk, activeSettings),
                            backgroundExecutor);
            pendingRegionBuilds.put(regionCoord, meshFuture);
            pendingRegionTargets.put(regionCoord, target);
            availableSlots--;
        }
    }

    private void attachCompletedMeshes() {
        long deadline = System.nanoTime() + attachBudgetNanos();
        List<Map.Entry<FarFieldTerrainRegionCoord, CompletableFuture<FarFieldTerrainMeshBuildResult>>> completedEntries =
                new ArrayList<>();
        for (Map.Entry<FarFieldTerrainRegionCoord, CompletableFuture<FarFieldTerrainMeshBuildResult>> entry :
                Set.copyOf(pendingRegionBuilds.entrySet())) {
            CompletableFuture<FarFieldTerrainMeshBuildResult> meshFuture = entry.getValue();
            if (!meshFuture.isDone()) {
                continue;
            }
            completedEntries.add(entry);
        }
        completedEntries.sort((left, right) -> compareTargetsForPriority(
                pendingRegionTargets.get(left.getKey()),
                pendingRegionTargets.get(right.getKey())));

        for (Map.Entry<FarFieldTerrainRegionCoord, CompletableFuture<FarFieldTerrainMeshBuildResult>> entry : completedEntries) {
            if (System.nanoTime() >= deadline) {
                break;
            }
            FarFieldTerrainRegionCoord regionCoord = entry.getKey();
            FarFieldTerrainTarget pendingTarget = pendingRegionTargets.remove(regionCoord);
            pendingRegionBuilds.remove(regionCoord);
            FarFieldTerrainTarget activeTarget = activeTargetsByRegion.get(regionCoord);
            if (!Objects.equals(pendingTarget, activeTarget)) {
                continue;
            }
            attachRegionMesh(entry.getValue().join());
            dirtyRegions.remove(regionCoord);
        }
    }

    private void attachRegionMesh(FarFieldTerrainMeshBuildResult meshBuildResult) {
        detachRenderedRegion(meshBuildResult.regionCoord());

        if (meshBuildResult.faceCount() == 0 || meshBuildResult.sections().isEmpty()) {
            return;
        }

        Node regionNode = new Node("far_region_" + meshBuildResult.regionCoord().x() + "_" + meshBuildResult.regionCoord().z());
        regionNode.setLocalTranslation(
                meshBuildResult.regionCoord().worldStartX(activeSettings),
                -activeSettings.verticalBiasBlocks(),
                meshBuildResult.regionCoord().worldStartZ(activeSettings));

        int attachedSectionCount = 0;
        for (Map.Entry<TerrainMaterialKey, ChunkMeshSectionData> sectionEntry : meshBuildResult.sections().entrySet()) {
            ChunkMeshSectionData sectionData = sectionEntry.getValue();
            if (sectionData.faceCount() == 0) {
                continue;
            }

            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(sectionData.positions()));
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(sectionData.normals()));
            mesh.setBuffer(
                    VertexBuffer.Type.TexCoord,
                    sectionData.textureCoordinateComponents(),
                    BufferUtils.createFloatBuffer(sectionData.textureCoordinates()));
            if (sectionData.secondaryTextureCoordinateComponents() > 0
                    && sectionData.secondaryTextureCoordinates().length > 0) {
                mesh.setBuffer(
                        VertexBuffer.Type.TexCoord2,
                        sectionData.secondaryTextureCoordinateComponents(),
                        BufferUtils.createFloatBuffer(sectionData.secondaryTextureCoordinates()));
            }
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(sectionData.indices()));
            mesh.updateBound();
            mesh.setStatic();

            Geometry geometry = new Geometry(
                    "far_region_section_" + meshBuildResult.regionCoord().x() + "_" + meshBuildResult.regionCoord().z(),
                    mesh);
            geometry.setMaterial(terrainMaterialLibrary.materialFor(sectionEntry.getKey()));
            regionNode.attachChild(geometry);
            attachedSectionCount++;
        }

        if (regionNode.getQuantity() == 0) {
            return;
        }

        farTerrainRoot.attachChild(regionNode);
        renderedRegionNodes.put(meshBuildResult.regionCoord(), regionNode);
        renderedRegionFaceCounts.put(meshBuildResult.regionCoord(), meshBuildResult.faceCount());
        renderedRegionSectionCounts.put(meshBuildResult.regionCoord(), attachedSectionCount);
        totalRenderedFaceCount += meshBuildResult.faceCount();
        totalRenderedSectionCount += attachedSectionCount;
        currentWindowRebuiltRegionCount++;
    }

    private void detachRenderedRegionsOutside(Set<FarFieldTerrainRegionCoord> targetRegions) {
        for (FarFieldTerrainRegionCoord regionCoord : Set.copyOf(renderedRegionNodes.keySet())) {
            if (!targetRegions.contains(regionCoord)) {
                detachRenderedRegion(regionCoord);
            }
        }
    }

    private void detachRenderedRegion(FarFieldTerrainRegionCoord regionCoord) {
        Node existingNode = renderedRegionNodes.remove(regionCoord);
        Integer removedFaceCount = renderedRegionFaceCounts.remove(regionCoord);
        Integer removedSectionCount = renderedRegionSectionCounts.remove(regionCoord);
        if (removedFaceCount != null) {
            totalRenderedFaceCount -= removedFaceCount;
        }
        if (removedSectionCount != null) {
            totalRenderedSectionCount -= removedSectionCount;
        }
        if (existingNode != null) {
            existingNode.removeFromParent();
        }
    }

    private void cancelPendingBuild(FarFieldTerrainRegionCoord regionCoord) {
        CompletableFuture<FarFieldTerrainMeshBuildResult> pendingBuild = pendingRegionBuilds.remove(regionCoord);
        pendingRegionTargets.remove(regionCoord);
        if (pendingBuild != null) {
            pendingBuild.cancel(true);
        }
    }

    private void clear() {
        for (FarFieldTerrainRegionCoord regionCoord : Set.copyOf(pendingRegionBuilds.keySet())) {
            cancelPendingBuild(regionCoord);
        }
        activeTargets = List.of();
        activeTargetsByRegion = Map.of();
        dirtyRegions.clear();
        activeAnchorChunk = null;
        activeSettings = null;
        for (FarFieldTerrainRegionCoord regionCoord : Set.copyOf(renderedRegionNodes.keySet())) {
            detachRenderedRegion(regionCoord);
        }
    }

    private List<FarFieldTerrainTarget> prioritizedTargets() {
        List<FarFieldTerrainTarget> prioritizedTargets = new ArrayList<>(activeTargets);
        prioritizedTargets.sort(this::compareTargetsForPriority);
        return prioritizedTargets;
    }

    private int compareTargetsForPriority(FarFieldTerrainTarget left, FarFieldTerrainTarget right) {
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        int bandOrder = Integer.compare(targetPriorityBand(left), targetPriorityBand(right));
        if (bandOrder != 0) {
            return bandOrder;
        }
        double leftDistanceSquared = regionDistanceSquared(left.regionCoord());
        double rightDistanceSquared = regionDistanceSquared(right.regionCoord());
        int distanceOrder = Double.compare(leftDistanceSquared, rightDistanceSquared);
        if (distanceOrder != 0) {
            return distanceOrder;
        }
        return Float.compare(forwardBiasScore(right.regionCoord()), forwardBiasScore(left.regionCoord()));
    }

    private double regionDistanceSquared(FarFieldTerrainRegionCoord regionCoord) {
        if (activeAnchorChunk == null || activeSettings == null) {
            return 0d;
        }
        double centerChunkX = activeAnchorChunk.x() + 0.5d;
        double centerChunkZ = activeAnchorChunk.z() + 0.5d;
        double regionCenterChunkX = regionCoord.startChunkX(activeSettings) + (activeSettings.regionSpanChunks() / 2d);
        double regionCenterChunkZ = regionCoord.startChunkZ(activeSettings) + (activeSettings.regionSpanChunks() / 2d);
        double deltaChunkX = regionCenterChunkX - centerChunkX;
        double deltaChunkZ = regionCenterChunkZ - centerChunkZ;
        return (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
    }

    private float forwardBiasScore(FarFieldTerrainRegionCoord regionCoord) {
        if (activeAnchorChunk == null || activeSettings == null || motionProfile == ChunkMotionProfile.STILL) {
            return 0f;
        }
        float regionCenterChunkX = regionCoord.startChunkX(activeSettings) + (activeSettings.regionSpanChunks() / 2f);
        float regionCenterChunkZ = regionCoord.startChunkZ(activeSettings) + (activeSettings.regionSpanChunks() / 2f);
        float deltaChunkX = regionCenterChunkX - activeAnchorChunk.x();
        float deltaChunkZ = regionCenterChunkZ - activeAnchorChunk.z();
        return (deltaChunkX * priorityDirection.x) + (deltaChunkZ * priorityDirection.z);
    }

    private long attachBudgetNanos() {
        long baseBudget =
                MOVING_ATTACH_BUDGET_NANOS + Math.round((STILL_ATTACH_BUDGET_NANOS - MOVING_ATTACH_BUDGET_NANOS) * catchUpScale);
        return Math.max(200_000L, Math.round(baseBudget * (0.4f + (0.6f * frameTimeGovernorScale))));
    }

    private void rollCounterWindow(long now) {
        if (nextCounterWindowNanos == 0L) {
            nextCounterWindowNanos = now + COUNTER_WINDOW_NANOS;
            return;
        }
        if (now < nextCounterWindowNanos) {
            return;
        }
        lastWindowAnchorSnapCount = currentWindowAnchorSnapCount;
        lastWindowRebuiltRegionCount = currentWindowRebuiltRegionCount;
        currentWindowAnchorSnapCount = 0;
        currentWindowRebuiltRegionCount = 0;
        nextCounterWindowNanos = now + COUNTER_WINDOW_NANOS;
    }

    private int maxPendingRegionBuilds() {
        int requestedBudget = activeSettings == null ? MIN_PENDING_REGION_BUILDS : Math.max(16, activeSettings.endRadiusChunks() / 4);
        float scale = (0.35f + (0.65f * catchUpScale)) * (0.5f + (0.5f * frameTimeGovernorScale));
        return Math.max(MIN_PENDING_REGION_BUILDS, Math.min(MAX_PENDING_REGION_BUILDS, Math.round(requestedBudget * scale)));
    }

    private int targetPriorityBand(FarFieldTerrainTarget target) {
        return switch (target.clipMode()) {
            case CLIP_BOTH, CLIP_INNER -> 0;
            case CLIP_OUTER -> 1;
            case FULL_REGION -> 2;
        };
    }

    private int synchronousPrimeRegionLimit(FarFieldTerrainSettings settings) {
        if (settings.endRadiusChunks() <= 96) {
            return activeTargets.size();
        }
        return Math.min(MAX_SYNCHRONOUS_ULTRA_PRIME_REGIONS, activeTargets.size());
    }

    record RefreshTargetsPlan(
            List<FarFieldTerrainTarget> orderedTargets,
            Map<FarFieldTerrainRegionCoord, FarFieldTerrainTarget> nextTargetsByRegion,
            Set<FarFieldTerrainRegionCoord> dirtyRegionCoords,
            Set<FarFieldTerrainRegionCoord> staleRegionCoords) {
    }
}
