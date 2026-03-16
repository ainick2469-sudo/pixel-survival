package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.gen.FarFieldTerrainSampler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public final class FarFieldTerrainRenderer {
    private static final int MAX_PENDING_REGION_BUILDS = 12;
    private static final int MAX_COMPLETED_REGION_ATTACHES_PER_UPDATE = 4;

    private final Node farTerrainRoot = new Node("far_terrain_root");
    private final TerrainMaterialLibrary terrainMaterialLibrary;
    private final FarFieldTerrainPlanner planner = new FarFieldTerrainPlanner();
    private final FarFieldTerrainMeshBuilder meshBuilder;
    private final ExecutorService backgroundExecutor;
    private final Map<FarFieldTerrainRegionCoord, CompletableFuture<FarFieldTerrainMeshBuildResult>> pendingRegionBuilds =
            new ConcurrentHashMap<>();
    private final Map<FarFieldTerrainRegionCoord, Node> renderedRegionNodes = new HashMap<>();
    private final Map<FarFieldTerrainRegionCoord, Integer> renderedRegionFaceCounts = new HashMap<>();
    private final Map<FarFieldTerrainRegionCoord, Integer> renderedRegionSectionCounts = new HashMap<>();
    private final Set<FarFieldTerrainRegionCoord> dirtyRegions = ConcurrentHashMap.newKeySet();
    private Set<FarFieldTerrainRegionCoord> activeTargets = Set.of();
    private ChunkCoord activeAnchorChunk;
    private FarFieldTerrainSettings activeSettings;
    private int totalRenderedFaceCount;
    private int totalRenderedSectionCount;

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

    public void prime(ChunkCoord centerChunk, ChunkRuntimeConfig runtimeConfig) {
        FarFieldTerrainSettings settings = FarFieldTerrainSettings.from(runtimeConfig);
        if (settings == null || centerChunk == null) {
            clear();
            return;
        }

        refreshTargets(centerChunk, settings);
        for (FarFieldTerrainRegionCoord regionCoord : activeTargets) {
            attachRegionMesh(meshBuilder.buildRegionMesh(regionCoord, activeAnchorChunk, activeSettings));
            dirtyRegions.remove(regionCoord);
        }
    }

    public void update(ChunkCoord centerChunk, ChunkRuntimeConfig runtimeConfig) {
        FarFieldTerrainSettings settings = FarFieldTerrainSettings.from(runtimeConfig);
        if (settings == null || centerChunk == null) {
            clear();
            return;
        }

        boolean targetsChanged = refreshTargets(centerChunk, settings);
        enqueueMeshBuilds();
        attachCompletedMeshes();
        if (targetsChanged) {
            detachRenderedRegionsOutside(activeTargets);
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

    public void close() {
        clear();
    }

    private boolean refreshTargets(ChunkCoord centerChunk, FarFieldTerrainSettings settings) {
        ChunkCoord nextAnchorChunk = resolveAnchorChunk(centerChunk, settings, activeAnchorChunk, activeSettings);
        if (Objects.equals(nextAnchorChunk, activeAnchorChunk) && Objects.equals(settings, activeSettings)) {
            return false;
        }

        cancelPendingBuilds();
        activeAnchorChunk = nextAnchorChunk;
        activeSettings = settings;
        activeTargets = planner.plan(activeAnchorChunk, activeSettings);
        dirtyRegions.clear();
        dirtyRegions.addAll(activeTargets);
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

    private void enqueueMeshBuilds() {
        int availableSlots = Math.max(0, MAX_PENDING_REGION_BUILDS - pendingRegionBuilds.size());
        if (availableSlots == 0) {
            return;
        }

        for (FarFieldTerrainRegionCoord regionCoord : activeTargets) {
            if (availableSlots == 0) {
                break;
            }
            if (pendingRegionBuilds.containsKey(regionCoord)) {
                continue;
            }
            if (renderedRegionNodes.containsKey(regionCoord) && !dirtyRegions.contains(regionCoord)) {
                continue;
            }

            CompletableFuture<FarFieldTerrainMeshBuildResult> meshFuture =
                    CompletableFuture.supplyAsync(
                            () -> meshBuilder.buildRegionMesh(regionCoord, activeAnchorChunk, activeSettings),
                            backgroundExecutor);
            pendingRegionBuilds.put(regionCoord, meshFuture);
            availableSlots--;
        }
    }

    private void attachCompletedMeshes() {
        int attachedMeshes = 0;
        for (Map.Entry<FarFieldTerrainRegionCoord, CompletableFuture<FarFieldTerrainMeshBuildResult>> entry :
                Set.copyOf(pendingRegionBuilds.entrySet())) {
            if (attachedMeshes >= MAX_COMPLETED_REGION_ATTACHES_PER_UPDATE) {
                break;
            }
            CompletableFuture<FarFieldTerrainMeshBuildResult> meshFuture = entry.getValue();
            if (!meshFuture.isDone()) {
                continue;
            }

            FarFieldTerrainRegionCoord regionCoord = entry.getKey();
            pendingRegionBuilds.remove(regionCoord);
            if (!activeTargets.contains(regionCoord)) {
                continue;
            }

            attachRegionMesh(meshFuture.join());
            dirtyRegions.remove(regionCoord);
            attachedMeshes++;
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

    private void cancelPendingBuilds() {
        for (CompletableFuture<FarFieldTerrainMeshBuildResult> pendingBuild : pendingRegionBuilds.values()) {
            pendingBuild.cancel(true);
        }
        pendingRegionBuilds.clear();
    }

    private void clear() {
        cancelPendingBuilds();
        activeTargets = Set.of();
        dirtyRegions.clear();
        activeAnchorChunk = null;
        activeSettings = null;
        for (FarFieldTerrainRegionCoord regionCoord : Set.copyOf(renderedRegionNodes.keySet())) {
            detachRenderedRegion(regionCoord);
        }
    }
}
