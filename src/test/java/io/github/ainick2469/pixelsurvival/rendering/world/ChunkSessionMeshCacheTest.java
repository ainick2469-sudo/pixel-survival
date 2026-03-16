package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ChunkSessionMeshCacheTest {
    @Test
    void storesSeparateDetailVariantsAndResetsStorageOnInvalidation() {
        ChunkSessionMeshCache cache = new ChunkSessionMeshCache(8_192L);
        ChunkCoord chunkCoord = new ChunkCoord(4, -2);
        ChunkMeshBuildResult fullMesh = meshBuildResult(chunkCoord, ChunkDetailLevel.FULL, 12);
        ChunkMeshBuildResult surfaceMesh = meshBuildResult(chunkCoord, ChunkDetailLevel.SURFACE, 6);

        cache.put(fullMesh);
        cache.put(surfaceMesh);

        assertSame(fullMesh, cache.get(chunkCoord, ChunkDetailLevel.FULL));
        assertSame(surfaceMesh, cache.get(chunkCoord, ChunkDetailLevel.SURFACE));
        assertEquals(1, cache.cachedChunkCount());
        assertEquals(2, cache.cachedVariantCount());
        assertEquals(3024L, cache.estimatedStorageBytes());

        cache.invalidate(chunkCoord);

        assertNull(cache.get(chunkCoord, ChunkDetailLevel.FULL));
        assertNull(cache.get(chunkCoord, ChunkDetailLevel.SURFACE));
        assertEquals(0, cache.cachedChunkCount());
        assertEquals(0, cache.cachedVariantCount());
        assertEquals(0L, cache.estimatedStorageBytes());
    }

    @Test
    void evictsLeastRecentlyUsedVariantsWhenTheBudgetIsExceeded() {
        ChunkSessionMeshCache cache = new ChunkSessionMeshCache(5_000L);
        ChunkMeshBuildResult oldestMesh = meshBuildResult(new ChunkCoord(0, 0), ChunkDetailLevel.FULL, 12);
        ChunkMeshBuildResult keptMesh = meshBuildResult(new ChunkCoord(1, 0), ChunkDetailLevel.FULL, 12);
        ChunkMeshBuildResult newestMesh = meshBuildResult(new ChunkCoord(2, 0), ChunkDetailLevel.FULL, 12);

        cache.put(oldestMesh);
        cache.put(keptMesh);
        assertSame(oldestMesh, cache.get(new ChunkCoord(0, 0), ChunkDetailLevel.FULL));

        cache.put(newestMesh);

        assertSame(oldestMesh, cache.get(new ChunkCoord(0, 0), ChunkDetailLevel.FULL));
        assertNull(cache.get(new ChunkCoord(1, 0), ChunkDetailLevel.FULL));
        assertSame(newestMesh, cache.get(new ChunkCoord(2, 0), ChunkDetailLevel.FULL));
        assertEquals(2, cache.cachedChunkCount());
        assertEquals(2, cache.cachedVariantCount());
        assertEquals(4_032L, cache.estimatedStorageBytes());
    }

    private static ChunkMeshBuildResult meshBuildResult(
            ChunkCoord chunkCoord,
            ChunkDetailLevel detailLevel,
            int faceCount) {
        ChunkMeshSectionData sectionData = new ChunkMeshSectionData(
                new float[faceCount * 12],
                new float[faceCount * 12],
                new float[faceCount * 12],
                3,
                new int[faceCount * 6],
                faceCount);
        return new ChunkMeshBuildResult(
                chunkCoord,
                detailLevel,
                Map.of(TerrainMaterialKey.sharedTextured(), sectionData),
                faceCount,
                faceCount);
    }
}
