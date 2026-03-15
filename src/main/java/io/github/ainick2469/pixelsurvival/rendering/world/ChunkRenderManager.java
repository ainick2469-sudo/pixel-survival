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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    private final Node terrainRoot = new Node("terrain_root");
    private final AuthoritativeWorldService worldService;
    private final GameRegistries registries;
    private final TerrainMaterialLibrary terrainMaterialLibrary;
    private final ChunkMeshBuilder chunkMeshBuilder;
    private final ChunkVisibilityPlanner visibilityPlanner = new ChunkVisibilityPlanner();
    private final ExecutorService backgroundExecutor;
    private final Map<ChunkCoord, CompletableFuture<ChunkData>> pendingChunkLoads = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>> pendingMeshBuilds = new ConcurrentHashMap<>();
    private final Map<ChunkCoord, Node> renderedChunkNodes = new HashMap<>();
    private final Map<ChunkCoord, Long> retainedLoadTargets = new ConcurrentHashMap<>();
    private final Set<ChunkCoord> dirtyChunks = ConcurrentHashMap.newKeySet();
    private volatile ChunkRuntimeConfig runtimeConfig;
    private ChunkRuntimeMetrics metrics = ChunkRuntimeMetrics.empty();

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
        ChunkVisibilityPlanner.RuntimeTargets initialTargets =
                visibilityPlanner.plan(
                        cameraLocation,
                        cameraDirection,
                        horizontalViewDegrees,
                        runtimeConfig.startupPrimeConfig());
        for (ChunkCoord chunkCoord : initialTargets.loadTargets()) {
            worldService.loadChunk(chunkCoord);
        }
        dirtyChunks.addAll(initialTargets.renderTargets());
        buildRenderTargetsSynchronously(initialTargets.renderTargets());
        metrics = buildMetrics(
                visibilityPlanner.plan(cameraLocation, cameraDirection, horizontalViewDegrees, runtimeConfig));
    }

    public void update(Vector3f cameraLocation, Vector3f cameraDirection, float horizontalViewDegrees) {
        ChunkVisibilityPlanner.RuntimeTargets targets =
                visibilityPlanner.plan(cameraLocation, cameraDirection, horizontalViewDegrees, runtimeConfig);
        long now = System.nanoTime();
        Set<ChunkCoord> effectiveLoadTargets = effectiveLoadTargets(targets.loadTargets(), now);
        cancelAndUnloadFarChunks(effectiveLoadTargets, targets.renderTargets());
        attachCompletedLoads();
        enqueueChunkLoads(effectiveLoadTargets);
        enqueueMeshBuilds(targets.renderTargets());
        attachCompletedMeshes(targets.renderTargets());
        detachRenderedChunksOutside(targets.renderTargets());
        metrics = buildMetrics(targets);
    }

    public ChunkRuntimeMetrics metrics() {
        return metrics;
    }

    public ChunkRuntimeConfig runtimeConfig() {
        return runtimeConfig;
    }

    public void setRuntimeConfig(ChunkRuntimeConfig runtimeConfig) {
        this.runtimeConfig = runtimeConfig;
        retainedLoadTargets.clear();
    }

    @Override
    public void close() {
        backgroundExecutor.shutdownNow();
    }

    private void buildRenderTargetsSynchronously(Collection<ChunkCoord> renderTargets) {
        for (ChunkCoord chunkCoord : renderTargets) {
            ChunkData chunkData = worldService.getChunkIfLoaded(chunkCoord);
            if (chunkData == null) {
                continue;
            }
            attachChunkMesh(chunkMeshBuilder.buildChunkMesh(chunkData));
            dirtyChunks.remove(chunkCoord);
        }
    }

    private void cancelAndUnloadFarChunks(Set<ChunkCoord> loadTargets, Set<ChunkCoord> renderTargets) {
        List<ChunkCoord> loadedChunkCoords = new ArrayList<>(worldService.getLoadedChunkCoords());
        for (ChunkCoord chunkCoord : loadedChunkCoords) {
            if (!loadTargets.contains(chunkCoord)) {
                cancelFuture(pendingMeshBuilds.remove(chunkCoord));
                cancelFuture(pendingChunkLoads.remove(chunkCoord));
                detachRenderedChunk(chunkCoord);
                dirtyChunks.remove(chunkCoord);
                retainedLoadTargets.remove(chunkCoord);
                markChunkAndNeighborsDirty(chunkCoord);
                worldService.unloadChunk(chunkCoord);
            }
        }

        for (ChunkCoord chunkCoord : new ArrayList<>(pendingChunkLoads.keySet())) {
            if (!loadTargets.contains(chunkCoord)) {
                cancelFuture(pendingChunkLoads.remove(chunkCoord));
            }
        }
        for (ChunkCoord chunkCoord : new ArrayList<>(pendingMeshBuilds.keySet())) {
            if (!renderTargets.contains(chunkCoord)) {
                cancelFuture(pendingMeshBuilds.remove(chunkCoord));
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
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkData>> entry : new ArrayList<>(pendingChunkLoads.entrySet())) {
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
            if (!worldService.isChunkLoaded(chunkCoord) || pendingMeshBuilds.containsKey(chunkCoord)) {
                continue;
            }
            if (renderedChunkNodes.containsKey(chunkCoord) && !dirtyChunks.contains(chunkCoord)) {
                continue;
            }

            ChunkData chunkData = worldService.getChunkIfLoaded(chunkCoord);
            if (chunkData == null) {
                continue;
            }

            CompletableFuture<ChunkMeshBuildResult> meshFuture =
                    CompletableFuture.supplyAsync(() -> chunkMeshBuilder.buildChunkMesh(chunkData), backgroundExecutor);
            pendingMeshBuilds.put(chunkCoord, meshFuture);
            availableSlots--;
        }
    }

    private void attachCompletedMeshes(Set<ChunkCoord> renderTargets) {
        int attachedMeshes = 0;
        for (Map.Entry<ChunkCoord, CompletableFuture<ChunkMeshBuildResult>> entry :
                new ArrayList<>(pendingMeshBuilds.entrySet())) {
            if (attachedMeshes >= MAX_COMPLETED_MESH_ATTACHES_PER_UPDATE) {
                break;
            }
            CompletableFuture<ChunkMeshBuildResult> meshFuture = entry.getValue();
            if (!meshFuture.isDone()) {
                continue;
            }

            ChunkCoord chunkCoord = entry.getKey();
            pendingMeshBuilds.remove(chunkCoord);
            if (!renderTargets.contains(chunkCoord) || !worldService.isChunkLoaded(chunkCoord)) {
                continue;
            }

            attachChunkMesh(meshFuture.join());
            dirtyChunks.remove(chunkCoord);
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
        }
    }

    private void detachRenderedChunksOutside(Set<ChunkCoord> renderTargets) {
        for (ChunkCoord chunkCoord : new ArrayList<>(renderedChunkNodes.keySet())) {
            if (!renderTargets.contains(chunkCoord)) {
                detachRenderedChunk(chunkCoord);
            }
        }
    }

    private void detachRenderedChunk(ChunkCoord chunkCoord) {
        Node existingNode = renderedChunkNodes.remove(chunkCoord);
        if (existingNode != null) {
            existingNode.removeFromParent();
        }
    }

    private ChunkRuntimeMetrics buildMetrics(ChunkVisibilityPlanner.RuntimeTargets targets) {
        int simulatedLoaded = 0;
        for (ChunkCoord chunkCoord : targets.simulationTargets()) {
            if (worldService.isChunkLoaded(chunkCoord)) {
                simulatedLoaded++;
            }
        }

        int renderedFaceCount = 0;
        for (Node chunkNode : renderedChunkNodes.values()) {
            for (int childIndex = 0; childIndex < chunkNode.getQuantity(); childIndex++) {
                Geometry geometry = (Geometry) chunkNode.getChild(childIndex);
                Mesh mesh = geometry.getMesh();
                renderedFaceCount += mesh.getTriangleCount() / 2;
            }
        }

        return new ChunkRuntimeMetrics(
                worldService.getLoadedChunkCount(),
                renderedChunkNodes.size(),
                simulatedLoaded,
                pendingChunkLoads.size(),
                pendingMeshBuilds.size(),
                renderedFaceCount,
                worldService.estimatedLoadedChunkStorageBytes());
    }

    private void cancelFuture(CompletableFuture<?> future) {
        if (future != null) {
            future.cancel(true);
        }
    }

    private Set<ChunkCoord> effectiveLoadTargets(Set<ChunkCoord> activeLoadTargets, long now) {
        for (ChunkCoord chunkCoord : activeLoadTargets) {
            retainedLoadTargets.put(chunkCoord, now + LOAD_RETENTION_NANOS);
        }

        LinkedHashSet<ChunkCoord> effectiveTargets = new LinkedHashSet<>(activeLoadTargets);
        for (Map.Entry<ChunkCoord, Long> retainedEntry : new ArrayList<>(retainedLoadTargets.entrySet())) {
            if (retainedEntry.getValue() <= now) {
                retainedLoadTargets.remove(retainedEntry.getKey());
                continue;
            }
            effectiveTargets.add(retainedEntry.getKey());
        }
        return effectiveTargets;
    }

    private void markChunkAndNeighborsDirty(ChunkCoord chunkCoord) {
        dirtyChunks.add(chunkCoord);
        dirtyChunks.add(new ChunkCoord(chunkCoord.x() + 1, chunkCoord.z()));
        dirtyChunks.add(new ChunkCoord(chunkCoord.x() - 1, chunkCoord.z()));
        dirtyChunks.add(new ChunkCoord(chunkCoord.x(), chunkCoord.z() + 1));
        dirtyChunks.add(new ChunkCoord(chunkCoord.x(), chunkCoord.z() - 1));
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
