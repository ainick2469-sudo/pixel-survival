package io.github.ainick2469.pixelsurvival.world.gen;

import io.github.ainick2469.pixelsurvival.registry.GameRegistries;
import io.github.ainick2469.pixelsurvival.world.block.BlockId;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import io.github.ainick2469.pixelsurvival.world.gen.pipeline.PipelineWorldGenerator;
import io.github.ainick2469.pixelsurvival.world.gen.pipeline.SurfaceHeightPass;
import io.github.ainick2469.pixelsurvival.world.gen.pipeline.SurfaceHeightProfile;
import io.github.ainick2469.pixelsurvival.world.gen.pipeline.TerrainLayerPass;
import io.github.ainick2469.pixelsurvival.world.gen.pipeline.WorldGenerationStage;
import io.github.ainick2469.pixelsurvival.world.gen.topology.PlanarPrototypeTopologyProfile;
import io.github.ainick2469.pixelsurvival.world.gen.topology.WorldTopologyProfile;
import java.util.List;

public final class HeightmapWorldGenerator implements WorldGenerator, FarFieldTerrainSamplerProvider {
    private static final int WORLD_SEED = 2469;
    private static final int BASE_HEIGHT = 22;
    private static final int MIN_SURFACE_HEIGHT = 14;
    private static final int MAX_SURFACE_HEIGHT = 40;
    private static final int DIRT_LAYER_DEPTH = 3;

    private static final BlockId AIR = BlockId.of("pixel_survival:air");
    private static final BlockId DIRT = BlockId.of("pixel_survival:dirt");
    private static final BlockId STONE = BlockId.of("pixel_survival:stone");
    private static final BlockId GRASS_BLOCK = BlockId.of("pixel_survival:grass_block");

    private final SurfaceHeightProfile surfaceHeightProfile;
    private final PipelineWorldGenerator pipelineWorldGenerator;

    public HeightmapWorldGenerator() {
        this.surfaceHeightProfile = new SurfaceHeightProfile(
                BASE_HEIGHT,
                MIN_SURFACE_HEIGHT,
                MAX_SURFACE_HEIGHT,
                WORLD_SEED,
                WORLD_SEED + 101,
                WORLD_SEED + 202);
        this.pipelineWorldGenerator = new PipelineWorldGenerator(
                WORLD_SEED,
                AIR,
                List.of(
                        new SurfaceHeightPass(surfaceHeightProfile),
                        new TerrainLayerPass(STONE, DIRT, GRASS_BLOCK, DIRT_LAYER_DEPTH)));
    }

    @Override
    public ChunkData generateChunk(ChunkCoord chunkCoord, GameRegistries registries) {
        registries.requireBlockDefinition(AIR);
        registries.requireBlockDefinition(DIRT);
        registries.requireBlockDefinition(STONE);
        registries.requireBlockDefinition(GRASS_BLOCK);
        return pipelineWorldGenerator.generateChunk(chunkCoord, registries);
    }

    public int sampleSurfaceHeight(int worldX, int worldZ) {
        return surfaceHeightProfile.sampleSurfaceHeight(worldX, worldZ);
    }

    @Override
    public WorldTopologyProfile topologyProfile() {
        return PlanarPrototypeTopologyProfile.INSTANCE;
    }

    public List<WorldGenerationStage> configuredStages() {
        return pipelineWorldGenerator.configuredStages();
    }

    @Override
    public FarFieldTerrainSampler farFieldTerrainSampler() {
        return (worldX, worldZ) -> new FarFieldTerrainSampler.ColumnSample(
                surfaceHeightProfile.sampleSurfaceHeight(worldX, worldZ),
                GRASS_BLOCK);
    }
}
