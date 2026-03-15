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
import io.github.ainick2469.pixelsurvival.world.sim.AuthoritativeWorldService;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
    private static final int MAX_PENDING_CHUNK_LOADS = 64;
    private static final int MAX_PENDING_MESH_BUILDS = 24;
    private static final int MAX_COMPLETED_CHUNK_LOADS_PER_UPDATE = 12;
    private static final int MAX_COMPLETED_MESH_ATTACHES_PER_UPDATE = 6;
    private static final long LOAD_RETENTION_NANOS = 1_500_000_000L;
    private static final long METRICS_REFRESH_NANOS = 250_000_000L;

    private final Node terrainRoot = new Node("terrain_root");
    private final AuthoritativeWorldService worldService;
    private final GameRegistries registries;
    private final TerrainMaterialLibrary terrainMaterialLibrary;
    private final ChunkMeshBuilder chunkMeshBuilder;
    private final ChunkVisibilityPlanner visibilityPlanner = new ChunkVisibilityPlanner();
    private final ExecutorService backgroundExecutor;
    private final Map<ChunkCoord, CompletableFuture<ChunkData>> pendingChunkLoads = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>> pendingMeshBuilds = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, ChunkDetailLevel> pendingMeshDetailLevels = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, Node> renderedChunkNodes = new HashMap<>();
    private final Map<ChunkCoord, Integer> renderedChunkFaceCounts = new HashMap<>();
    private final Map<ChunkCoord, ChunkDetailLevel> renderedChunkDetailLevels = new HashMap<>();
    private final Map<ChunkCoord, Long> retainedLoadTargets = new ConcurrentHashMap<>();
    private final Set<ChunkCoord> dirtyChunks = ConcurrentHashMap.newKeySet();
    private volatile ChunkRuntimeConfig runtimeConfig;
    private ChunkVisibilityPlanner.RuntimeTargets activeTargets =
            new ChunkVisibilityPlanner.RuntimeTargets(Set.of(), Set.of(), Set.of());
    private ChunkCoord activeCenterChunk;
    private ChunkRuntimeConfig activeTargetRuntimeConfig;
    private ChunkRuntimeMetrics metrics = ChunkRuntimeMetrics.empty();
    private int totalRenderedFaceCount;
    private long nextMetricsRefreshNanos;

    public ChunkRenderManager(
            Node rootNode,
            AssetManager assetManager,
            AuthoritativeWorldService worldService,
            GameRegistries registries,
            ChunkRuntimeConfig runtimeConfig) {
        this.worldService = worldService;
        this.registries = registries;
        this.runtimeConfig = runtimeConfig;
        this.terrainMaterialLibrary = new TerrainMaterialLibrary(assetManager);
        this.chunkMeshBuilder = new ChunkMeshBuilder(worldService, registries);
        this.backgroundExecutor = Executors.newFixedThreadPool(
                Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2)),
                new ChunkRuntimeThreadFactory());
        rootNode.attachChild(terrainRoot);
    }

    public void primeAround(Vector3f cameraLocation, Vector3f cameraDirection, float horizontalViewDegrees) {
        ChunkCoord centerChunk = visibilityPlanner.centerChunkFor(cameraLocation);
        ChunkRuntimeConfig startupConfig = runtimeConfig.startupPrimeConfig();
        ChunkVisibilityPlanner.RuntimeTargets initialTargets = visibilityPlanner.plan(centerChunk, startupConfig);
        for (ChunkCoord chunkCoord : initialTargets.loadTargets()) {
            worldService.loadChunk(chunkCoord);
        }
        dirtyChunks.addAll(initialTargets.renderTargets());
        buildRenderTargetsSynchronously(centerChunk, startupConfig, initialTargets.renderTargets());
        refreshActiveTargets(centerChunk, System.nanoTime());
        metrics = buildMetrics(activeTargets.simulationTargets());
        nextMetricsRefreshNanos = System.nanoTime() + METRICS_REFRESH_NANOS;
    }

    public void update(Vector3f cameraLocation, Vector3f cameraDirection, float horizontalViewDegrees) {
        long now = System.nanoTime();
        boolean targetsChanged = refreshActiveTargets(visibilityPlanner.centerChunkFor(cameraLocation), now);
        boolean retainedTargetsChanged = purgeExpiredRetainedLoadTargets(now);
        Set<ChunkCoord> effectiveLoadTargets = effectiveLoadTargets(activeTargets.loadTargets());
        if (targetsChanged || retainedTargetsChanged) {
            cancelAndUnloadFarChunks(effectiveLoadTargets, activeTargets.renderTargets());
        }
        attachCompletedLoads();
        if (!pendingChunkLoads.isEmpty() || worldService.getLoadedChunkCount() < effectiveLoadTargets.size()) {
            enqueueChunkLoads(effectiveLoadTargets);
        }
        if (!dirtyChunks.isEmpty() || !pendingMeshBuilds.isEmpty()) {
            enqueueMeshBuilds(activeTargets.renderTargets());
        }
        attachCompletedMeshes(activeTargets.renderTargets());
        if (targetsChanged) {
            detachRenderedChunksOutside(activeTargets.renderTargets());
        }
        if (now >= nextMetricsRefreshNanos || targetsChanged || !pendingChunkLoads.isEmpty() || !pendingMeshBuilds.isEmpty()) {
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
        retainedLoadTargets.clear();
    }

    @Override
    public void close() {
        backgroundExecutor.shutdownNow();
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

    private void cancelAndUnloadFarChunks(Set<ChunkCoord> loadTargets, Set<ChunkCoord> renderTargets) {
        for (ChunkCoord chunkCoord : worldService.loadedChunkCoordsView()) {
            if (!loadTargets.contains(chunkCoord)) {
                cancelFuture(pendingMeshBuilds.remove(chunkCoord));
                pendingMeshDetailLevels.remove(chunkCoord);
                cancelFuture(pendingChunkLoads.remove(chunkCoord));
                detachRenderedChunk(chunkCoord);
                dirtyChunks.remove(chunkCoord);
                retainedLoadTargets.remove(chunkCoord);
                markChunkAndNeighborsDirty(chunkCoord);
                worldService.unloadChunk(chunkCoord);
            }
        }

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
        int availableSlots = Math.max(0, MAX_PENDING_CHUNK_LOADS - pendingChunkLoads.size());
        if (availableSlots == 0) {
            return;
        }

        for (ChunkCoord chunkCoord : loadTargets) {
            if (availableSlots == 0) {
                break;
            }
            if (worldService.isChunkLoaded(chunkCoord) || pendingChunkLoads.containsKey(chunkCoord)) {
                continue;
            }

            CompletableFuture<ChunkData> loadFuture =
                    CompletableFuture.supplyAsync(() -> worldService.loadChunk(chunkCoord), backgroundExecutor);
            pendingChunkLoads.put(chunkCoord, loadFuture);
            availableSlots--;
        }
    }

    private void attachCompletedLoads() {
        int attachedLoads = 0;
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkData>> entry : Set.copyOf(pendingChunkLoads.entrySet())) {
            if (attachedLoads >= MAX_COMPLETED_CHUNK_LOADS_PER_UPDATE) {
                break;
            }
            CompletableFuture<ChunkData> loadFuture = entry.getValue();
            if (!loadFuture.isDone()) {
                continue;
            }

            loadFuture.join();
            pendingChunkLoads.remove(entry.getKey());
            markChunkAndNeighborsDirty(entry.getKey());
            attachedLoads++;
        }
    }

    private void enqueueMeshBuilds(Set<ChunkCoord> renderTargets) {
        int availableSlots = Math.max(0, MAX_PENDING_MESH_BUILDS - pendingMeshBuilds.size());
        if (availableSlots == 0) {
            return;
        }

        for (ChunkCoord chunkCoord : renderTargets) {
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
                            backgroundExecutor);
            pendingMeshBuilds.put(chunkCoord, meshFuture);
            pendingMeshDetailLevels.put(chunkCoord, desiredDetailLevel);
            availableSlots--;
        }
    }

    private void attachCompletedMeshes(Set<ChunkCoord> renderTargets) {
        int attachedMeshes = 0;
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>> entry : Set.copyOf(pendingMeshBuilds.entrySet())) {
            if (attachedMeshes >= MAX_COMPLETED_MESH_ATTACHES_PER_UPDATE) {
                break;
            }
            CompletableFuture<ChunkMeshBuildResult> meshFuture = entry.getValue();
            if (!meshFuture.isDone()) {
                continue;
            }

            ChunkCoord chunkCoord = entry.getKey();
            pendingMeshBuilds.remove(chunkCoord);
            pendingMeshDetailLevels.remove(chunkCoord);
            if (!renderTargets.contains(chunkCoord) || !worldService.isChunkLoaded(chunkCoord)) {
                continue;
            }

            ChunkMeshBuildResult meshBuildResult = meshFuture.join();
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
        detachRenderedChunk(meshBuildResult.chunkCoord());

        Node chunkNode = new Node("chunk_" + meshBuildResult.chunkCoord().x() + "_" + meshBuildResult.chunkCoord().z());
        chunkNode.setLocalTranslation(
                meshBuildResult.chunkCoord().x() * ChunkData.SIZE_X,
                0f,
                meshBuildResult.chunkCoord().z() * ChunkData.SIZE_Z);

        for (Map.Entry<TerrainMaterialKey, ChunkMeshSectionData> sectionEntry : meshBuildResult.sections().entrySet()) {
            ChunkMeshSectionData sectionData = sectionEntry.getValue();
            if (sectionData.faceCount() == 0) {
                continue;
            }

            Mesh mesh = new Mesh();
            mesh.setBuffer(VertexBuffer.Type.Position, 3, BufferUtils.createFloatBuffer(sectionData.positions()));
            mesh.setBuffer(VertexBuffer.Type.Normal, 3, BufferUtils.createFloatBuffer(sectionData.normals()));
            mesh.setBuffer(VertexBuffer.Type.TexCoord, 2, BufferUtils.createFloatBuffer(sectionData.textureCoordinates()));
            mesh.setBuffer(VertexBuffer.Type.Index, 3, BufferUtils.createIntBuffer(sectionData.indices()));
            mesh.updateBound();
            mesh.setStatic();

            Geometry geometry = new Geometry(
                    "chunk_section_" + meshBuildResult.chunkCoord().x() + "_" + meshBuildResult.chunkCoord().z(),
                    mesh);
            geometry.setMaterial(terrainMaterialLibrary.materialFor(sectionEntry.getKey()));
            chunkNode.attachChild(geometry);
        }

        if (chunkNode.getQuantity() > 0) {
            terrainRoot.attachChild(chunkNode);
            renderedChunkNodes.put(meshBuildResult.chunkCoord(), chunkNode);
            renderedChunkFaceCounts.put(meshBuildResult.chunkCoord(), meshBuildResult.faceCount());
            renderedChunkDetailLevels.put(meshBuildResult.chunkCoord(), meshBuildResult.detailLevel());
            totalRenderedFaceCount += meshBuildResult.faceCount();
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
        renderedChunkDetailLevels.remove(chunkCoord);
        if (removedFaceCount != null) {
            totalRenderedFaceCount -= removedFaceCount;
        }
        if (existingNode != null) {
            existingNode.removeFromParent();
        }
    }

    private ChunkRuntimeMetrics buildMetrics(Set<ChunkCoord> simulationTargets) {
        int simulatedLoaded = 0;
        for (ChunkCoord chunkCoord : simulationTargets) {
            if (worldService.isChunkLoaded(chunkCoord)) {
                simulatedLoaded++;
            }
        }

        return new ChunkRuntimeMetrics(
                worldService.getLoadedChunkCount(),
                renderedChunkNodes.size(),
                simulatedLoaded,
                pendingChunkLoads.size(),
                pendingMeshBuilds.size(),
                totalRenderedFaceCount,
                worldService.estimatedLoadedChunkStorageBytes());
    }

    private void cancelFuture(CompletableFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private Set<ChunkCoord> effectiveLoadTargets(Set<ChunkCoord> activeLoadTargets) {
        if (retainedLoadTargets.isEmpty()) {
            return activeLoadTargets;
        }

        LinkedHashSet<ChunkCoord> effectiveTargets = new LinkedHashSet<>(activeLoadTargets);
        effectiveTargets.addAll(retainedLoadTargets.keySet());
        return effectiveTargets;
    }

    private boolean refreshActiveTargets(ChunkCoord centerChunk, long now) {
        if (Objects.equals(centerChunk, activeCenterChunk) && Objects.equals(runtimeConfig, activeTargetRuntimeConfig)) {
            return false;
        }

        Set<ChunkCoord> previousLoadTargets = activeTargets.loadTargets();
        Set<ChunkCoord> previousRenderTargets = activeTargets.renderTargets();
        activeTargets = visibilityPlanner.plan(centerChunk, runtimeConfig);
        if (!previousLoadTargets.isEmpty()) {
            for (ChunkCoord chunkCoord : previousLoadTargets) {
                if (!activeTargets.loadTargets().contains(chunkCoord)) {
                    retainedLoadTargets.put(chunkCoord, now + LOAD_RETENTION_NANOS);
                }
            }
        }
        retainedLoadTargets.keySet().removeAll(activeTargets.loadTargets());
        for (ChunkCoord chunkCoord : activeTargets.renderTargets()) {
            if (!previousRenderTargets.contains(chunkCoord)) {
                dirtyChunks.add(chunkCoord);
            }
            if (renderedChunkDetailLevels.containsKey(chunkCoord)
                    && renderedChunkDetailLevels.get(chunkCoord) != desiredDetailLevel(centerChunk, runtimeConfig, chunkCoord)) {
                dirtyChunks.add(chunkCoord);
            }
            if (pendingMeshDetailLevels.containsKey(chunkCoord)
                    && pendingMeshDetailLevels.get(chunkCoord) != desiredDetailLevel(centerChunk, runtimeConfig, chunkCoord)) {
                cancelFuture(pendingMeshBuilds.remove(chunkCoord));
                pendingMeshDetailLevels.remove(chunkCoord);
                dirtyChunks.add(chunkCoord);
            }
        }
        activeCenterChunk = centerChunk;
        activeTargetRuntimeConfig = runtimeConfig;
        return true;
    }

    private boolean purgeExpiredRetainedLoadTargets(long now) {
        boolean removedAny = false;
        for (Map.Entry<ChunkCoord, Long> retainedEntry : Set.copyOf(retainedLoadTargets.entrySet())) {
            if (retainedEntry.getValue() <= now) {
                retainedLoadTargets.remove(retainedEntry.getKey());
                removedAny = true;
            }
        }
        return removedAny;
    }

    private void markChunkAndNeighborsDirty(ChunkCoord chunkCoord) {
        dirtyChunks.add(chunkCoord);
        dirtyChunks.add(new ChunkCoord(chunkCoord.x() + 1, chunkCoord.z()));
        dirtyChunks.add(new ChunkCoord(chunkCoord.x() - 1, chunkCoord.z()));
        dirtyChunks.add(new ChunkCoord(chunkCoord.x(), chunkCoord.z() + 1));
        dirtyChunks.add(new ChunkCoord(chunkCoord.x(), chunkCoord.z() - 1));
    }

    private ChunkDetailLevel desiredDetailLevel(ChunkCoord chunkCoord) {
        return desiredDetailLevel(activeCenterChunk, runtimeConfig, chunkCoord);
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
        return ChunkDetailLevel.HORIZON;
    }

    private static final class ChunkRuntimeThreadFactory implements ThreadFactory {
        private final AtomicInteger nextThreadId = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "pixel-survival-chunk-runtime-" + nextThreadId.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
