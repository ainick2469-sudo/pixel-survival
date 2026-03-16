package io.github.ainick2469.pixelsurvival.rendering.world;

import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

final class ChunkSessionMeshCache {
    private final LinkedHashMap<CacheKey, CacheEntry> cachedEntriesByVariant =
            new LinkedHashMap<>(16, 0.75f, true);
    private final Map<ChunkCoord, EnumMap<ChunkDetailLevel, CacheKey>> cachedKeysByChunk = new LinkedHashMap<>();
    private long maxStorageBytes;
    private long estimatedStorageBytes;

    ChunkSessionMeshCache() {
        this(128L * 1024L * 1024L);
    }

    ChunkSessionMeshCache(long maxStorageBytes) {
        this.maxStorageBytes = Math.max(1L, maxStorageBytes);
    }

    public ChunkMeshBuildResult get(ChunkCoord chunkCoord, ChunkDetailLevel detailLevel) {
        CacheEntry cacheEntry = cachedEntriesByVariant.get(new CacheKey(chunkCoord, detailLevel));
        return cacheEntry == null ? null : cacheEntry.meshBuildResult();
    }

    public void put(ChunkMeshBuildResult meshBuildResult) {
        CacheKey cacheKey = new CacheKey(meshBuildResult.chunkCoord(), meshBuildResult.detailLevel());
        int estimatedVariantStorageBytes = estimateStorageBytes(meshBuildResult);

        CacheEntry priorEntry = cachedEntriesByVariant.remove(cacheKey);
        if (priorEntry != null) {
            estimatedStorageBytes -= priorEntry.estimatedStorageBytes();
        }

        CacheEntry cacheEntry = new CacheEntry(meshBuildResult, estimatedVariantStorageBytes);
        cachedEntriesByVariant.put(cacheKey, cacheEntry);
        cachedKeysByChunk
                .computeIfAbsent(meshBuildResult.chunkCoord(), ignored -> new EnumMap<>(ChunkDetailLevel.class))
                .put(meshBuildResult.detailLevel(), cacheKey);
        estimatedStorageBytes += estimatedVariantStorageBytes;
        trimToBudget();
    }

    public void invalidate(ChunkCoord chunkCoord) {
        EnumMap<ChunkDetailLevel, CacheKey> chunkKeys = cachedKeysByChunk.remove(chunkCoord);
        if (chunkKeys == null) {
            return;
        }

        for (CacheKey cacheKey : chunkKeys.values()) {
            CacheEntry removedEntry = cachedEntriesByVariant.remove(cacheKey);
            if (removedEntry != null) {
                estimatedStorageBytes -= removedEntry.estimatedStorageBytes();
            }
        }
    }

    public void setMaxStorageBytes(long maxStorageBytes) {
        this.maxStorageBytes = Math.max(1L, maxStorageBytes);
        trimToBudget();
    }

    public void clear() {
        cachedEntriesByVariant.clear();
        cachedKeysByChunk.clear();
        estimatedStorageBytes = 0L;
    }

    public int cachedChunkCount() {
        return cachedKeysByChunk.size();
    }

    public int cachedVariantCount() {
        return cachedEntriesByVariant.size();
    }

    public long estimatedStorageBytes() {
        return estimatedStorageBytes;
    }

    public long maxStorageBytes() {
        return maxStorageBytes;
    }

    private void trimToBudget() {
        Iterator<Map.Entry<CacheKey, CacheEntry>> iterator = cachedEntriesByVariant.entrySet().iterator();
        while (estimatedStorageBytes > maxStorageBytes && iterator.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> eldestEntry = iterator.next();
            iterator.remove();
            estimatedStorageBytes -= eldestEntry.getValue().estimatedStorageBytes();

            EnumMap<ChunkDetailLevel, CacheKey> chunkKeys = cachedKeysByChunk.get(eldestEntry.getKey().chunkCoord());
            if (chunkKeys != null) {
                chunkKeys.remove(eldestEntry.getKey().detailLevel());
                if (chunkKeys.isEmpty()) {
                    cachedKeysByChunk.remove(eldestEntry.getKey().chunkCoord());
                }
            }
        }
    }

    private int estimateStorageBytes(ChunkMeshBuildResult meshBuildResult) {
        int estimatedBytes = 0;
        for (ChunkMeshSectionData sectionData : meshBuildResult.sections().values()) {
            estimatedBytes += sectionData.positions().length * Float.BYTES;
            estimatedBytes += sectionData.normals().length * Float.BYTES;
            estimatedBytes += sectionData.textureCoordinates().length * Float.BYTES;
            estimatedBytes += sectionData.indices().length * Integer.BYTES;
        }
        return estimatedBytes;
    }

    private record CacheKey(ChunkCoord chunkCoord, ChunkDetailLevel detailLevel) {
    }

    private record CacheEntry(ChunkMeshBuildResult meshBuildResult, int estimatedStorageBytes) {
    }
}
