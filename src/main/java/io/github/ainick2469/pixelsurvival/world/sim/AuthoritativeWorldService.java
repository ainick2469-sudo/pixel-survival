package io.github.ainick2469.pixelsurvival.world.sim;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.WorldGenerator;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthoritativeWorldService {
    private static final io.github.ainick2469.pixelsurvival.world.block.BlockId AIR =
            io.github.ainick2469.pixelsurvival.world.block.BlockId.of("pixel_survival:air");

    private final GameRegistries registries;
    private final WorldGenerator worldGenerator;
    private final Map<ChunkCoord, ChunkData> loadedChunks = new ConcurrentHashMap<>();

    public AuthoritativeWorldService(GameRegistries registries, WorldGenerator worldGenerator) {
        this.registries = registries;
        this.worldGenerator = worldGenerator;
    }

    public ChunkData loadChunk(ChunkCoord chunkCoord) {
        return loadedChunks.computeIfAbsent(chunkCoord, coord -> worldGenerator.generateChunk(coord, registries));
    }

    public boolean isChunkLoaded(ChunkCoord chunkCoord) {
        return loadedChunks.containsKey(chunkCoord);
    }

    public ChunkData getChunkIfLoaded(ChunkCoord chunkCoord) {
        return loadedChunks.get(chunkCoord);
    }

    public Collection<ChunkData> getLoadedChunks() {
        return loadedChunks.values();
    }

    public Set<ChunkCoord> getLoadedChunkCoords() {
        return Set.copyOf(loadedChunks.keySet());
    }

    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }

    public void unloadChunk(ChunkCoord chunkCoord) {
        loadedChunks.remove(chunkCoord);
    }

    public io.github.ainick2469.pixelsurvival.world.block.BlockId getBlockAtWorldOrAir(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= ChunkData.SIZE_Y) {
            return AIR;
        }

        ChunkCoord chunkCoord = new ChunkCoord(
                Math.floorDiv(worldX, ChunkData.SIZE_X),
                Math.floorDiv(worldZ, ChunkData.SIZE_Z));
        ChunkData chunkData = loadedChunks.get(chunkCoord);
        if (chunkData == null) {
            return AIR;
        }

        int localX = Math.floorMod(worldX, ChunkData.SIZE_X);
        int localZ = Math.floorMod(worldZ, ChunkData.SIZE_Z);
        return chunkData.getBlock(localX, worldY, localZ);
    }

    public int findSurfaceY(int worldX, int worldZ) {
        ChunkCoord chunkCoord = new ChunkCoord(
                Math.floorDiv(worldX, ChunkData.SIZE_X),
                Math.floorDiv(worldZ, ChunkData.SIZE_Z));
        ChunkData chunkData = loadChunk(chunkCoord);
        int localX = Math.floorMod(worldX, ChunkData.SIZE_X);
        int localZ = Math.floorMod(worldZ, ChunkData.SIZE_Z);

        for (int y = ChunkData.SIZE_Y - 1; y >= 0; y--) {
            BlockDefinition definition = registries.requireBlockDefinition(chunkData.getBlock(localX, y, localZ));
            if (definition.solid()) {
                return y;
            }
        }

        return 0;
    }
}
