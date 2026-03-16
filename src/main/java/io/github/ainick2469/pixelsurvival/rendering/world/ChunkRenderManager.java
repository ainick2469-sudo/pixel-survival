package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.asset.AssetManager;
import com.jme3.math.Vector3f;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.FarFieldTerrainSampler;
import io.github.ainick2469.pixelsurvival.world.sim.AuthoritativeWorldService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class ChunkRenderManager implements AutoCloseable {
    private static final int MIN_PENDING_CHUNK_LOADS = 64;
    private static final int MAX_PENDING_CHUNK_LOADS = 256;
    private static final int MIN_PENDING_MESH_BUILDS = 24;
    private static final int MAX_PENDING_MESH_BUILDS = 128;
    private static final int MIN_COMPLETED_CHUNK_LOADS_PER_UPDATE = 12;
    private static final int MAX_COMPLETED_CHUNK_LOADS_PER_UPDATE = 64;
    private static final int MIN_COMPLETED_MESH_ATTACHES_PER_UPDATE = 6;
    private static final int MAX_COMPLETED_MESH_ATTACHES_PER_UPDATE = 64;
    private static final long METRICS_REFRESH_NANOS = 250_000_000L;
    private static final int MIN_BRIDGED_FAR_FIELD_START_RADIUS_CHUNKS = 8;
    private static final long MIN_SESSION_CACHE_STORAGE_BYTES = 96L * 1024L * 1024L;
    private static final long MAX_SESSION_CACHE_STORAGE_BYTES = 256L * 1024L * 1024L;
    private static final long MOVING_PROFILE_WINDOW_NANOS = 250_000_000L;
    private static final long SETTLING_PROFILE_WINDOW_NANOS = 1_000_000_000L;
    private static final long MOVING_LOAD_ATTACH_BUDGET_NANOS = 2_000_000L;
    private static final long SETTLING_LOAD_ATTACH_BUDGET_NANOS = 4_000_000L;
    private static final long STILL_LOAD_ATTACH_BUDGET_NANOS = 8_000_000L;
    private static final long MOVING_MESH_ATTACH_BUDGET_NANOS = 2_000_000L;
    private static final long SETTLING_MESH_ATTACH_BUDGET_NANOS = 4_000_000L;
    private static final long STILL_MESH_ATTACH_BUDGET_NANOS = 8_000_000L;

    private final Node terrainRoot = new Node("terrain_root");
    private final AuthoritativeWorldService worldService;
    private final GameRegistries registries;
    private final TerrainTexturePalette terrainTexturePalette;
    private final TerrainMaterialLibrary terrainMaterialLibrary;
    private final ChunkMeshBuilder chunkMeshBuilder;
    private final FarFieldTerrainRenderer farFieldTerrainRenderer;
    private final ChunkVisibilityPlanner visibilityPlanner = new ChunkVisibilityPlanner();
    private final ExecutorService chunkBackgroundExecutor;
    private final ExecutorService farFieldBackgroundExecutor;
    private final ChunkSessionMeshCache sessionMeshCache = new ChunkSessionMeshCache();
    private final Map<ChunkCoord, CompletableFuture<ChunkData>> pendingChunkLoads = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>> pendingMeshBuilds = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, ChunkDetailLevel> pendingMeshDetailLevels = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, Node> renderedChunkNodes = new HashMap<>();
    private final Map<ChunkCoord, Integer> renderedChunkFaceCounts = new HashMap<>();
    private final Map<ChunkCoord, Integer> renderedChunkSectionCounts = new HashMap<>();
    private final Map<ChunkCoord, ChunkDetailLevel> renderedChunkDetailLevels = new HashMap<>();
    private final Set<ChunkCoord> dirtyChunks = ConcurrentHashMap.newKeySet();
    private volatile ChunkRuntimeConfig runtimeConfig;
    private ChunkVisibilityPlanner.RuntimeTargets activeTargets =
            new ChunkVisibilityPlanner.RuntimeTargets(Set.of(), Set.of(), Set.of());
    private ChunkCoord activeCenterChunk;
    private ChunkRuntimeConfig activeTargetRuntimeConfig;
    private ChunkRuntimeMetrics metrics = ChunkRuntimeMetrics.empty();
    private int totalRenderedFaceCount;
    private int totalRenderedSectionCount;
    private long nextMetricsRefreshNanos;
    private ChunkMotionProfile motionProfile = ChunkMotionProfile.STILL;
    private long lastCenterChunkMovementNanos;
    private int lastMovementDeltaChunkX;
    private int lastMovementDeltaChunkZ;
    private Vector3f priorityDirection = new Vector3f(0f, 0f, 1f);

    public ChunkRenderManager(
            Node rootNode,
            AssetManager assetManager,
            AuthoritativeWorldService worldService,
            GameRegistries registries,
            ChunkRuntimeConfig runtimeConfig) {
        this(rootNode, assetManager, worldService, registries, runtimeConfig, null);
    }

    public ChunkRenderManager(
            Node rootNode,
            AssetManager assetManager,
            AuthoritativeWorldService worldService,
            GameRegistries registries,
            ChunkRuntimeConfig runtimeConfig,
            FarFieldTerrainSampler farFieldTerrainSampler) {
        this.worldService = worldService;
        this.registries = registries;
        this.runtimeConfig = runtimeConfig;
        this.terrainTexturePalette = TerrainTexturePalette.build(registries);
        this.terrainMaterialLibrary = new TerrainMaterialLibrary(assetManager, terrainTexturePalette);
        this.chunkMeshBuilder = new ChunkMeshBuilder(worldService, registries, terrainTexturePalette);
        this.chunkBackgroundExecutor = Executors.newFixedThreadPool(
                Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors())),
                new ChunkRuntimeThreadFactory("pixel-survival-chunk-runtime-"));
        this.farFieldBackgroundExecutor = farFieldTerrainSampler == null
                ? null
                : Executors.newFixedThreadPool(
                        Math.max(1, Math.min(2, Runtime.getRuntime().availableProcessors() / 4)),
                        new ChunkRuntimeThreadFactory("pixel-survival-far-field-"));
        this.farFieldTerrainRenderer = farFieldTerrainSampler == null
                ? null
                : new FarFieldTerrainRenderer(
                        rootNode,
                        terrainMaterialLibrary,
                        registries,
                        terrainTexturePalette,
                        farFieldTerrainSampler,
                        farFieldBackgroundExecutor);
        this.sessionMeshCache.setMaxStorageBytes(targetSessionMeshCacheStorageBytes(runtimeConfig));
        rootNode.attachChild(terrainRoot);
    }

    public void primeAround(Vector3f cameraLocation, Vector3f cameraDirection, float horizontalViewDegrees) {
        ChunkCoord centerChunk = visibilityPlanner.centerChunkFor(cameraLocation);
        updateMotionProfile(centerChunk, cameraDirection, System.nanoTime());
        ChunkRuntimeConfig startupConfig = activeChunkRuntimeConfig(runtimeConfig).startupPrimeConfig();
        ChunkVisibilityPlanner.RuntimeTargets initialTargets = visibilityPlanner.plan(centerChunk, startupConfig);
        for (ChunkCoord chunkCoord : initialTargets.loadTargets()) {
            worldService.loadChunk(chunkCoord);
        }
        dirtyChunks.addAll(initialTargets.renderTargets());
        buildRenderTargetsSynchronously(centerChunk, startupConfig, initialTargets.renderTargets());
        refreshActiveTargets(centerChunk, System.nanoTime());
        if (farFieldTerrainRenderer != null) {
            farFieldTerrainRenderer.prime(centerChunk, effectiveFarFieldSettings(centerChunk));
        }
        metrics = buildMetrics(activeTargets.simulationTargets());
        nextMetricsRefreshNanos = System.nanoTime() + METRICS_REFRESH_NANOS;
    }

    public void update(Vector3f cameraLocation, Vector3f cameraDirection, float horizontalViewDegrees) {
        long now = System.nanoTime();
        ChunkCoord centerChunk = visibilityPlanner.centerChunkFor(cameraLocation);
        updateMotionProfile(centerChunk, cameraDirection, now);
        boolean targetsChanged = refreshActiveTargets(centerChunk, now);
        if (targetsChanged) {
            cancelOutOfRangeWork(activeTargets.loadTargets(), activeTargets.renderTargets());
        }
        attachCompletedLoads(now + loadAttachBudgetNanos());
        long meshAttachDeadline = System.nanoTime() + meshAttachBudgetNanos();
        attachCachedMeshes(activeTargets.renderTargets(), meshAttachDeadline);
        enqueueChunkLoads(activeTargets.loadTargets());
        if (!dirtyChunks.isEmpty() || !pendingMeshBuilds.isEmpty()) {
            enqueueMeshBuilds(activeTargets.renderTargets());
        }
        attachCompletedMeshes(activeTargets.renderTargets(), meshAttachDeadline);
        if (targetsChanged) {
            detachRenderedChunksOutside(activeTargets.renderTargets());
            unloadChunksOutside(activeTargets.loadTargets());
        }
        if (farFieldTerrainRenderer != null) {
            farFieldTerrainRenderer.update(centerChunk, effectiveFarFieldSettings(centerChunk), motionProfile, priorityDirection);
        }
        if (now >= nextMetricsRefreshNanos
                || targetsChanged
                || !pendingChunkLoads.isEmpty()
                || !pendingMeshBuilds.isEmpty()
                || (farFieldTerrainRenderer != null && farFieldTerrainRenderer.hasPendingWork())) {
            metrics = buildMetrics(activeTargets.simulationTargets());
            nextMetricsRefreshNanos = now + METRICS_REFRESH_NANOS;
        }
    }

    public ChunkRuntimeMetrics metrics() {
        return metrics;
    }

    public ChunkRuntimeConfig runtimeConfig() {
        return runtimeConfig;
    }

    public void setRuntimeConfig(ChunkRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        this.activeTargetRuntimeConfig = null;
        sessionMeshCache.setMaxStorageBytes(targetSessionMeshCacheStorageBytes(runtimeConfig));
    }

    @Override
    public void close() {
        if (farFieldTerrainRenderer != null) {
            farFieldTerrainRenderer.close();
        }
        sessionMeshCache.clear();
        chunkBackgroundExecutor.shutdownNow();
        if (farFieldBackgroundExecutor != null) {
            farFieldBackgroundExecutor.shutdownNow();
        }
    }

    private void buildRenderTargetsSynchronously(
            ChunkCoord centerChunk,
            ChunkRuntimeConfig runtimeConfig,
            Collection<ChunkCoord> renderTargets) {
        for (ChunkCoord chunkCoord : renderTargets) {
            ChunkData chunkData = worldService.getChunkIfLoaded(chunkCoord);
            if (chunkData == null) {
                continue;
            }
            attachChunkMesh(chunkMeshBuilder.buildChunkMesh(chunkData, desiredDetailLevel(centerChunk, runtimeConfig, chunkCoord)));
            dirtyChunks.remove(chunkCoord);
        }
    }

    private void cancelOutOfRangeWork(Set<ChunkCoord> loadTargets, Set<ChunkCoord> renderTargets) {
        for (ChunkCoord chunkCoord : Set.copyOf(pendingChunkLoads.keySet())) {
            if (!loadTargets.contains(chunkCoord)) {
                cancelFuture(pendingChunkLoads.remove(chunkCoord));
            }
        }
        for (ChunkCoord chunkCoord : Set.copyOf(pendingMeshBuilds.keySet())) {
            if (!renderTargets.contains(chunkCoord)) {
                cancelFuture(pendingMeshBuilds.remove(chunkCoord));
                pendingMeshDetailLevels.remove(chunkCoord);
            }
        }
    }

    private void enqueueChunkLoads(Set<ChunkCoord> loadTargets) {
        int availableSlots = Math.max(0, maxPendingChunkLoads() - pendingChunkLoads.size());
        if (availableSlots == 0) {
            return;
        }

        for (ChunkCoord chunkCoord : prioritizeChunkTargets(loadTargets)) {
            if (availableSlots == 0) {
                break;
            }
            if (worldService.isChunkLoaded(chunkCoord) || pendingChunkLoads.containsKey(chunkCoord)) {
                continue;
            }

            CompletableFuture<ChunkData> loadFuture =
                    CompletableFuture.supplyAsync(() -> worldService.loadChunk(chunkCoord), chunkBackgroundExecutor);
            pendingChunkLoads.put(chunkCoord, loadFuture);
            availableSlots--;
        }
    }

    private void attachCompletedLoads(long deadlineNanos) {
        int attachedLoads = 0;
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkData>> entry : prioritizeCompletedChunkLoads()) {
            if (attachedLoads >= maxCompletedChunkLoadsPerUpdate() || System.nanoTime() >= deadlineNanos) {
                break;
            }
            entry.getValue().join();
            pendingChunkLoads.remove(entry.getKey());
            markChunkAndNeighborsDirty(entry.getKey());
            attachedLoads++;
        }
    }

    private void attachCachedMeshes(Set<ChunkCoord> renderTargets, long deadlineNanos) {
        for (ChunkCoord chunkCoord : prioritizeChunkTargets(renderTargets)) {
            if (System.nanoTime() >= deadlineNanos) {
                break;
            }
            if (!worldService.isChunkLoaded(chunkCoord)) {
                continue;
            }

            ChunkDetailLevel desiredDetailLevel = desiredDetailLevel(chunkCoord);
            if (renderedChunkNodes.containsKey(chunkCoord)
                    && !dirtyChunks.contains(chunkCoord)
                    && desiredDetailLevel == renderedChunkDetailLevels.get(chunkCoord)) {
                continue;
            }
            if (pendingMeshBuilds.containsKey(chunkCoord)
                    && desiredDetailLevel == pendingMeshDetailLevels.get(chunkCoord)) {
                continue;
            }

            ChunkMeshBuildResult cachedMeshBuildResult = sessionMeshCache.get(chunkCoord, desiredDetailLevel);
            if (cachedMeshBuildResult == null) {
                continue;
            }

            attachChunkMesh(cachedMeshBuildResult);
            dirtyChunks.remove(chunkCoord);
        }
    }

    private void enqueueMeshBuilds(Set<ChunkCoord> renderTargets) {
        int availableSlots = Math.max(0, maxPendingMeshBuilds() - pendingMeshBuilds.size());
        if (availableSlots == 0) {
            return;
        }

        for (ChunkCoord chunkCoord : prioritizeChunkTargets(renderTargets)) {
            if (availableSlots == 0) {
                break;
            }
            if (!worldService.isChunkLoaded(chunkCoord)) {
                continue;
            }

            ChunkDetailLevel desiredDetailLevel = desiredDetailLevel(chunkCoord);
            if (pendingMeshBuilds.containsKey(chunkCoord)) {
                if (desiredDetailLevel == pendingMeshDetailLevels.get(chunkCoord)) {
                    continue;
                }
                cancelFuture(pendingMeshBuilds.remove(chunkCoord));
                pendingMeshDetailLevels.remove(chunkCoord);
            }
            if (renderedChunkNodes.containsKey(chunkCoord)
                    && !dirtyChunks.contains(chunkCoord)
                    && desiredDetailLevel == renderedChunkDetailLevels.get(chunkCoord)) {
                continue;
            }

            ChunkData chunkData = worldService.getChunkIfLoaded(chunkCoord);
            if (chunkData == null) {
                continue;
            }

            CompletableFuture<ChunkMeshBuildResult> meshFuture =
                    CompletableFuture.supplyAsync(
                            () -> chunkMeshBuilder.buildChunkMesh(chunkData, desiredDetailLevel),
                            chunkBackgroundExecutor);
            pendingMeshBuilds.put(chunkCoord, meshFuture);
            pendingMeshDetailLevels.put(chunkCoord, desiredDetailLevel);
            availableSlots--;
        }
    }

    private void attachCompletedMeshes(Set<ChunkCoord> renderTargets, long deadlineNanos) {
        int attachedMeshes = 0;
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>> entry : prioritizeCompletedMeshBuilds()) {
            if (attachedMeshes >= maxCompletedMeshAttachesPerUpdate() || System.nanoTime() >= deadlineNanos) {
                break;
            }
            ChunkCoord chunkCoord = entry.getKey();
            pendingMeshBuilds.remove(chunkCoord);
            pendingMeshDetailLevels.remove(chunkCoord);
            if (!renderTargets.contains(chunkCoord) || !worldService.isChunkLoaded(chunkCoord)) {
                continue;
            }

            ChunkMeshBuildResult meshBuildResult = entry.getValue().join();
            sessionMeshCache.put(meshBuildResult);
            if (meshBuildResult.detailLevel() != desiredDetailLevel(chunkCoord)) {
                dirtyChunks.add(chunkCoord);
                continue;
            }

            attachChunkMesh(meshBuildResult);
            if (meshBuildResult.detailLevel() == desiredDetailLevel(chunkCoord)) {
                dirtyChunks.remove(chunkCoord);
            }
            attachedMeshes++;
        }
    }

    private void attachChunkMesh(ChunkMeshBuildResult meshBuildResult) {
        sessionMeshCache.put(meshBuildResult);
        detachRenderedChunk(meshBuildResult.chunkCoord());

        Node chunkNode = new Node("chunk_" + meshBuildResult.chunkCoord().x() + "_" + meshBuildResult.chunkCoord().z());
        chunkNode.setLocalTranslation(
                meshBuildResult.chunkCoord().x() * ChunkData.SIZE_X,
                0f,
                meshBuildResult.chunkCoord().z() * ChunkData.SIZE_Z);
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
                    "chunk_section_" + meshBuildResult.chunkCoord().x() + "_" + meshBuildResult.chunkCoord().z(),
                    mesh);
            geometry.setMaterial(terrainMaterialLibrary.materialFor(sectionEntry.getKey()));
            chunkNode.attachChild(geometry);
            attachedSectionCount++;
        }

        if (chunkNode.getQuantity() > 0) {
            terrainRoot.attachChild(chunkNode);
            renderedChunkNodes.put(meshBuildResult.chunkCoord(), chunkNode);
            renderedChunkFaceCounts.put(meshBuildResult.chunkCoord(), meshBuildResult.faceCount());
            renderedChunkSectionCounts.put(meshBuildResult.chunkCoord(), attachedSectionCount);
            renderedChunkDetailLevels.put(meshBuildResult.chunkCoord(), meshBuildResult.detailLevel());
            totalRenderedFaceCount += meshBuildResult.faceCount();
            totalRenderedSectionCount += attachedSectionCount;
        }
    }

    private void detachRenderedChunksOutside(Set<ChunkCoord> renderTargets) {
        for (ChunkCoord chunkCoord : Set.copyOf(renderedChunkNodes.keySet())) {
            if (!renderTargets.contains(chunkCoord)) {
                detachRenderedChunk(chunkCoord);
            }
        }
    }

    private void detachRenderedChunk(ChunkCoord chunkCoord) {
        Node existingNode = renderedChunkNodes.remove(chunkCoord);
        Integer removedFaceCount = renderedChunkFaceCounts.remove(chunkCoord);
        Integer removedSectionCount = renderedChunkSectionCounts.remove(chunkCoord);
        renderedChunkDetailLevels.remove(chunkCoord);
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

    private void unloadChunksOutside(Set<ChunkCoord> loadTargets) {
        for (ChunkCoord loadedChunkCoord : worldService.loadedChunkCoordsView()) {
            if (loadTargets.contains(loadedChunkCoord)
                    || pendingChunkLoads.containsKey(loadedChunkCoord)
                    || pendingMeshBuilds.containsKey(loadedChunkCoord)) {
                continue;
            }
            worldService.unloadChunk(loadedChunkCoord);
            dirtyChunks.remove(loadedChunkCoord);
        }
    }

    private ChunkRuntimeMetrics buildMetrics(Set<ChunkCoord> simulationTargets) {
        int simulatedLoaded = 0;
        for (ChunkCoord chunkCoord : simulationTargets) {
            if (worldService.isChunkLoaded(chunkCoord)) {
                simulatedLoaded++;
            }
        }

        int farRegionCount = farFieldTerrainRenderer == null ? 0 : farFieldTerrainRenderer.renderedRegionCount();
        int totalSectionCount = totalRenderedSectionCount
                + (farFieldTerrainRenderer == null ? 0 : farFieldTerrainRenderer.renderedSectionCount());
        int totalFaceCount =
                totalRenderedFaceCount + (farFieldTerrainRenderer == null ? 0 : farFieldTerrainRenderer.renderedFaceCount());

        return new ChunkRuntimeMetrics(
                worldService.getLoadedChunkCount(),
                renderedChunkNodes.size(),
                farRegionCount,
                totalSectionCount,
                simulatedLoaded,
                pendingChunkLoads.size(),
                pendingMeshBuilds.size(),
                farFieldTerrainRenderer == null ? 0 : farFieldTerrainRenderer.pendingRegionBuildCount(),
                totalFaceCount,
                worldService.estimatedLoadedChunkStorageBytes(),
                sessionMeshCache.cachedVariantCount(),
                sessionMeshCache.estimatedStorageBytes(),
                farFieldTerrainRenderer == null ? 0 : farFieldTerrainRenderer.rebuiltRegionCountLastWindow(),
                farFieldTerrainRenderer == null ? 0 : farFieldTerrainRenderer.anchorSnapCountLastWindow(),
                motionProfile);
    }

    private void cancelFuture(CompletableFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private boolean refreshActiveTargets(ChunkCoord centerChunk, long now) {
        ChunkRuntimeConfig chunkRuntimeConfig = activeChunkRuntimeConfig(runtimeConfig);
        if (Objects.equals(centerChunk, activeCenterChunk) && Objects.equals(chunkRuntimeConfig, activeTargetRuntimeConfig)) {
            return false;
        }

        Set<ChunkCoord> previousRenderTargets = activeTargets.renderTargets();
        activeTargets = visibilityPlanner.plan(centerChunk, chunkRuntimeConfig);
        for (ChunkCoord chunkCoord : activeTargets.renderTargets()) {
            if (!previousRenderTargets.contains(chunkCoord)) {
                dirtyChunks.add(chunkCoord);
            }
            if (renderedChunkDetailLevels.containsKey(chunkCoord)
                    && renderedChunkDetailLevels.get(chunkCoord) != desiredDetailLevel(centerChunk, chunkRuntimeConfig, chunkCoord)) {
                dirtyChunks.add(chunkCoord);
            }
            if (pendingMeshDetailLevels.containsKey(chunkCoord)
                    && pendingMeshDetailLevels.get(chunkCoord) != desiredDetailLevel(centerChunk, chunkRuntimeConfig, chunkCoord)) {
                cancelFuture(pendingMeshBuilds.remove(chunkCoord));
                pendingMeshDetailLevels.remove(chunkCoord);
                dirtyChunks.add(chunkCoord);
            }
        }
        activeCenterChunk = centerChunk;
        activeTargetRuntimeConfig = chunkRuntimeConfig;
        return true;
    }

    private void markChunkAndNeighborsDirty(ChunkCoord chunkCoord) {
        invalidateChunkMeshAndMarkDirty(chunkCoord);
        invalidateChunkMeshAndMarkDirty(new ChunkCoord(chunkCoord.x() + 1, chunkCoord.z()));
        invalidateChunkMeshAndMarkDirty(new ChunkCoord(chunkCoord.x() - 1, chunkCoord.z()));
        invalidateChunkMeshAndMarkDirty(new ChunkCoord(chunkCoord.x(), chunkCoord.z() + 1));
        invalidateChunkMeshAndMarkDirty(new ChunkCoord(chunkCoord.x(), chunkCoord.z() - 1));
    }

    private void invalidateChunkMeshAndMarkDirty(ChunkCoord chunkCoord) {
        sessionMeshCache.invalidate(chunkCoord);
        dirtyChunks.add(chunkCoord);
    }

    private ChunkDetailLevel desiredDetailLevel(ChunkCoord chunkCoord) {
        return desiredDetailLevel(
                activeCenterChunk,
                activeTargetRuntimeConfig == null ? activeChunkRuntimeConfig(runtimeConfig) : activeTargetRuntimeConfig,
                chunkCoord);
    }

    private ChunkDetailLevel desiredDetailLevel(
            ChunkCoord centerChunk,
            ChunkRuntimeConfig runtimeConfig,
            ChunkCoord chunkCoord) {
        if (centerChunk == null) {
            return ChunkDetailLevel.FULL;
        }
        int fullDetailRadius = Math.max(8, Math.min(24, Math.max(8, runtimeConfig.renderRadius() / 3)));
        int surfaceDetailRadius = Math.max(
                fullDetailRadius + 4,
                Math.min(runtimeConfig.renderRadius(), Math.max(12, (runtimeConfig.renderRadius() / 2) + 8)));
        int deltaX = chunkCoord.x() - centerChunk.x();
        int deltaZ = chunkCoord.z() - centerChunk.z();
        int distanceSquared = (deltaX * deltaX) + (deltaZ * deltaZ);
        if (distanceSquared <= fullDetailRadius * fullDetailRadius) {
            return ChunkDetailLevel.FULL;
        }
        if (distanceSquared <= surfaceDetailRadius * surfaceDetailRadius) {
            return ChunkDetailLevel.SURFACE;
        }

        // The coarse horizon tier remains in the codebase as a future seam, but it is
        // currently disabled in the live runtime because the approximation introduces
        // visible terrain cracks at long range.
        return ChunkDetailLevel.SURFACE;
    }

    private ChunkRuntimeConfig activeChunkRuntimeConfig(ChunkRuntimeConfig runtimeConfig) {
        FarFieldTerrainSettings farFieldSettings = FarFieldTerrainSettings.from(runtimeConfig);
        if (farFieldSettings == null) {
            return runtimeConfig;
        }
        return farFieldSettings.detailedChunkRuntimeConfig(runtimeConfig);
    }

    private FarFieldTerrainSettings effectiveFarFieldSettings(ChunkCoord centerChunk) {
        FarFieldTerrainSettings farFieldSettings = FarFieldTerrainSettings.from(runtimeConfig);
        if (farFieldSettings == null || centerChunk == null) {
            return farFieldSettings;
        }
        if (hasDetailedCoverageInsideRadius(centerChunk, farFieldSettings.startRadiusChunks())) {
            return farFieldSettings;
        }
        return farFieldSettings.withStartRadiusChunks(bridgedFarFieldStartRadiusChunks(farFieldSettings));
    }

    private boolean hasDetailedCoverageInsideRadius(ChunkCoord centerChunk, int radiusChunks) {
        int radiusSquared = radiusChunks * radiusChunks;
        for (ChunkCoord chunkCoord : activeTargets.renderTargets()) {
            int deltaChunkX = chunkCoord.x() - centerChunk.x();
            int deltaChunkZ = chunkCoord.z() - centerChunk.z();
            int distanceSquared = (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
            if (distanceSquared > radiusSquared) {
                continue;
            }
            if (renderedChunkDetailLevels.get(chunkCoord) != desiredDetailLevel(chunkCoord)) {
                return false;
            }
        }
        return true;
    }

    private int bridgedFarFieldStartRadiusChunks(FarFieldTerrainSettings farFieldSettings) {
        return Math.min(
                farFieldSettings.startRadiusChunks(),
                Math.max(MIN_BRIDGED_FAR_FIELD_START_RADIUS_CHUNKS, farFieldSettings.startRadiusChunks() / 3));
    }

    private void updateMotionProfile(ChunkCoord centerChunk, Vector3f cameraDirection, long now) {
        if (activeCenterChunk != null && !activeCenterChunk.equals(centerChunk)) {
            lastCenterChunkMovementNanos = now;
            lastMovementDeltaChunkX = centerChunk.x() - activeCenterChunk.x();
            lastMovementDeltaChunkZ = centerChunk.z() - activeCenterChunk.z();
        }

        long timeSinceMovement = now - lastCenterChunkMovementNanos;
        if (activeCenterChunk == null || timeSinceMovement <= MOVING_PROFILE_WINDOW_NANOS) {
            motionProfile = ChunkMotionProfile.MOVING;
        } else if (timeSinceMovement <= SETTLING_PROFILE_WINDOW_NANOS) {
            motionProfile = ChunkMotionProfile.SETTLING;
        } else {
            motionProfile = ChunkMotionProfile.STILL;
        }

        if (lastMovementDeltaChunkX != 0 || lastMovementDeltaChunkZ != 0) {
            priorityDirection = new Vector3f(lastMovementDeltaChunkX, 0f, lastMovementDeltaChunkZ).normalizeLocal();
            return;
        }
        if (cameraDirection != null && (Math.abs(cameraDirection.x) > 0.0001f || Math.abs(cameraDirection.z) > 0.0001f)) {
            priorityDirection = new Vector3f(cameraDirection.x, 0f, cameraDirection.z).normalizeLocal();
        }
    }

    private List<ChunkCoord> prioritizeChunkTargets(Set<ChunkCoord> targets) {
        List<ChunkCoord> prioritizedTargets = new ArrayList<>(targets);
        prioritizedTargets.sort(this::compareChunkPriority);
        return prioritizedTargets;
    }

    private List<Map.Entry<ChunkCoord, CompletableFuture<ChunkData>>> prioritizeCompletedChunkLoads() {
        List<Map.Entry<ChunkCoord, CompletableFuture<ChunkData>>> completedLoads = new ArrayList<>();
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkData>> entry : Set.copyOf(pendingChunkLoads.entrySet())) {
            if (entry.getValue().isDone()) {
                completedLoads.add(entry);
            }
        }
        completedLoads.sort((left, right) -> compareChunkPriority(left.getKey(), right.getKey()));
        return completedLoads;
    }

    private List<Map.Entry<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>>> prioritizeCompletedMeshBuilds() {
        List<Map.Entry<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>>> completedMeshBuilds = new ArrayList<>();
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>> entry : Set.copyOf(pendingMeshBuilds.entrySet())) {
            if (entry.getValue().isDone()) {
                completedMeshBuilds.add(entry);
            }
        }
        completedMeshBuilds.sort((left, right) -> compareChunkPriority(left.getKey(), right.getKey()));
        return completedMeshBuilds;
    }

    private int compareChunkPriority(ChunkCoord left, ChunkCoord right) {
        if (activeCenterChunk == null) {
            return 0;
        }
        int leftDistanceSquared = distanceSquared(left);
        int rightDistanceSquared = distanceSquared(right);
        int distanceOrder = Integer.compare(leftDistanceSquared, rightDistanceSquared);
        if (distanceOrder != 0) {
            return distanceOrder;
        }
        return Float.compare(forwardBiasScore(right), forwardBiasScore(left));
    }

    private int distanceSquared(ChunkCoord chunkCoord) {
        if (activeCenterChunk == null) {
            return 0;
        }
        int deltaChunkX = chunkCoord.x() - activeCenterChunk.x();
        int deltaChunkZ = chunkCoord.z() - activeCenterChunk.z();
        return (deltaChunkX * deltaChunkX) + (deltaChunkZ * deltaChunkZ);
    }

    private float forwardBiasScore(ChunkCoord chunkCoord) {
        if (motionProfile != ChunkMotionProfile.MOVING || activeCenterChunk == null) {
            return 0f;
        }
        float deltaChunkX = chunkCoord.x() - activeCenterChunk.x();
        float deltaChunkZ = chunkCoord.z() - activeCenterChunk.z();
        return (deltaChunkX * priorityDirection.x) + (deltaChunkZ * priorityDirection.z);
    }

    private int maxPendingChunkLoads() {
        return clampRuntimeBudget(scaleBudget(activeBudgetRuntimeConfig().loadRadius() * 2), MIN_PENDING_CHUNK_LOADS, MAX_PENDING_CHUNK_LOADS);
    }

    private int maxPendingMeshBuilds() {
        return clampRuntimeBudget(scaleBudget(activeBudgetRuntimeConfig().renderRadius()), MIN_PENDING_MESH_BUILDS, MAX_PENDING_MESH_BUILDS);
    }

    private int maxCompletedChunkLoadsPerUpdate() {
        return clampRuntimeBudget(
                scaleBudget(Math.max(24, activeBudgetRuntimeConfig().renderRadius() / 2)),
                MIN_COMPLETED_CHUNK_LOADS_PER_UPDATE,
                MAX_COMPLETED_CHUNK_LOADS_PER_UPDATE);
    }

    private int maxCompletedMeshAttachesPerUpdate() {
        return clampRuntimeBudget(
                scaleBudget(Math.max(16, activeBudgetRuntimeConfig().renderRadius() / 2)),
                MIN_COMPLETED_MESH_ATTACHES_PER_UPDATE,
                MAX_COMPLETED_MESH_ATTACHES_PER_UPDATE);
    }

    private ChunkRuntimeConfig activeBudgetRuntimeConfig() {
        return activeTargetRuntimeConfig == null ? activeChunkRuntimeConfig(runtimeConfig) : activeTargetRuntimeConfig;
    }

    private long targetSessionMeshCacheStorageBytes(ChunkRuntimeConfig runtimeConfig) {
        long requestedStorageBytes = runtimeConfig.renderRadius() * 2L * 1024L * 1024L;
        return Math.max(MIN_SESSION_CACHE_STORAGE_BYTES, Math.min(MAX_SESSION_CACHE_STORAGE_BYTES, requestedStorageBytes));
    }

    private int scaleBudget(int requestedBudget) {
        float scale = switch (motionProfile) {
            case MOVING -> 0.5f;
            case SETTLING -> 0.75f;
            case STILL -> 1f;
        };
        return Math.max(1, Math.round(requestedBudget * scale));
    }

    private long loadAttachBudgetNanos() {
        return switch (motionProfile) {
            case MOVING -> MOVING_LOAD_ATTACH_BUDGET_NANOS;
            case SETTLING -> SETTLING_LOAD_ATTACH_BUDGET_NANOS;
            case STILL -> STILL_LOAD_ATTACH_BUDGET_NANOS;
        };
    }

    private long meshAttachBudgetNanos() {
        return switch (motionProfile) {
            case MOVING -> MOVING_MESH_ATTACH_BUDGET_NANOS;
            case SETTLING -> SETTLING_MESH_ATTACH_BUDGET_NANOS;
            case STILL -> STILL_MESH_ATTACH_BUDGET_NANOS;
        };
    }

    private int clampRuntimeBudget(int requestedBudget, int minimumBudget, int maximumBudget) {
        return Math.max(minimumBudget, Math.min(maximumBudget, requestedBudget));
    }

    private static final class ChunkRuntimeThreadFactory implements ThreadFactory {
        private final String threadNamePrefix;
        private final AtomicInteger nextThreadId = new AtomicInteger(1);

        private ChunkRuntimeThreadFactory(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, threadNamePrefix + nextThreadId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
