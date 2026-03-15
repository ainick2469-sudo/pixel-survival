package io.github.ainick2469.pixelsurvival.world.sim;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockDefinition;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.WorldGenerator;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthoritativeWorldService {
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

    public Collection<ChunkData> getLoadedChunks() {
        return loadedChunks.values();
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
