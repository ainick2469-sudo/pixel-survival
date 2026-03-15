package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.topology.PlanarPrototypeTopologyProfile;
import io.github.ainick2469.pixelsurvival.world.gen.topology.WorldTopologyProfile;

public interface WorldGenerator {
    ChunkData generateChunk(ChunkCoord chunkCoord, GameRegistries registries);

    default WorldTopologyProfile topologyProfile() {
        return PlanarPrototypeTopologyProfile.INSTANCE;
    }
}
