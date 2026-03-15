# Architecture

## Core runtime split

- `rendering` and `input` own client presentation concerns.
- `session`, `world`, and future `networking` packages own authoritative gameplay state and access patterns.
- Shared data contracts such as block IDs, chunk data, and interaction requests are serializable and future network-safe.

## Session model

Session 1 implements only `LOCAL_HOST`, but it does so through an explicit `LocalHostSession` seam. Single-player is treated as an embedded host rather than special-case gameplay logic.

Long-term session modes:

- `LOCAL_HOST`
- `REMOTE_CLIENT`
- `DEDICATED_SERVER`

## World model

- Block coordinates are integer-based.
- Chunk coordinates are separate from local block coordinates.
- Session 1 uses `16 x 64 x 16` chunks.
- Authoritative chunk data is kept independent from jMonkeyEngine scene objects.

## Planetary world direction

The current build is still a planar terrain prototype. That is intentional for the early milestone ladder. The long-term world is not planned to stay a fake infinite flat plane forever.

Long-term direction:

- the mature overworld should become a streamed planetary surface
- traveling far enough in one direction should eventually bring the player back around the planet
- the preferred long-term topology is a chunk-friendly cube-sphere style planet rather than a naive perfect sphere mesh

Current code seam:

- `WorldGenerator` now exposes a `WorldTopologyProfile`
- the live generator reports `PLANAR_PROTOTYPE`
- a reserved `CUBE_SPHERE_PLANET` topology profile now exists for the later migration path

Why cube-sphere is the preferred target:

- six chunk-addressable faces are easier to stream than a naive spherical voxel shell
- climate, latitude, and regional masks can be distributed per face and then blended at edges
- cave carving, landmarks, floating landforms, and cloud eligibility can still operate as deterministic passes on streamed chunk data
- it avoids a fake teleport seam while remaining more practical than treating the whole planet as one active volume

Safe migration path from the current prototype:

1. keep the early milestone terrain on planar chunk space
2. move worldgen logic into ordered topology-aware passes
3. add macro climate and regional masks that are conceptually topology-agnostic
4. introduce topology-aware addressing and streaming behind generator/service seams
5. migrate from planar chunk coordinates to cube-sphere surface addressing only after chunk streaming, meshing, and content passes are mature enough

Important rule:

- no giant always-loaded planet mesh
- no teleport seam gimmick
- no full-planet active memory model
- world state and render state remain separate

## World generation model

The current shipped overworld still only generates surface terrain layering, but the generator is no longer treated as a single monolithic class. The long-term target is an ordered pass pipeline that stays chunk-deterministic, topology-aware, and chunk-mesh friendly.

Current implementation details:

- `HeightmapWorldGenerator` now runs through a pass-driven pipeline instead of baking every rule into one method.
- `PipelineWorldGenerator` applies ordered `ChunkGenerationPass` instances to a `ChunkGenerationContext`.
- `ChunkGenerationContext` owns the authoritative `ChunkData` plus a worldgen scratchpad for intermediate fields.
- The scratchpad already supports named integer, float, and boolean column fields so future systems can share data without rewriting the generator again.
- `WorldGenerationStage` now reserves `PLANETARY_TOPOLOGY` ahead of terrain generation so the mature pipeline can move to planetary surface addressing later without reordering the rest of the world stack.
- The current live passes are only:
  - `BASE_TERRAIN`
  - `TERRAIN_LAYERING`

Reserved long-term pass order:

1. `PLANETARY_TOPOLOGY`
2. `BASE_TERRAIN`
3. `TERRAIN_LAYERING`
4. `BIOME_MASKS`
5. `CAVE_CARVING`
6. `LANDMARKS`
7. `FLOATING_LANDFORMS`
8. `CLOUD_ELIGIBILITY`
9. `WALKABLE_CLOUDS`
10. `SKY_STRUCTURES`
11. `VEGETATION_AND_PROPS`
12. `STRUCTURES_AND_POIS`
13. `ECOLOGY`

Reserved field channels already named in code:

- `planetary_latitude`
- `planetary_macro_region`
- `surface_height`
- `temperature_mask`
- `moisture_mask`
- `shallow_cave_density`
- `mid_cave_density`
- `deep_cave_density`
- `floating_landform_eligibility`
- `cloud_region_eligibility`
- `walkable_cloud_eligibility`

This matters because future caves, floating mountains, and walkable cloud regions should be expressed as deterministic generation passes that operate on chunk data and field masks, not as manual scene props or post-render hacks.

### Floating mountain path

- Floating mountains and floating islands are planned as a rare `FLOATING_LANDFORMS` pass, not part of every biome.
- The pass should operate only in eligible regions or altitude bands so it does not spam expensive suspended terrain across the whole world.
- Shapes should remain procedural and chunk-continuous so they mesh naturally with the existing block/chunk pipeline.
- Future content on these landforms can include rare resources, ruins, nests, shrines, traversal routes, and cloud-adjacent exploration.
- They should be driven by regional rarity and world seed logic, not stored as manual block dumps or scene props.

### Cave path

- Caves are planned as chunk-data carving, not as separate visible cave objects.
- The architecture already reserves separate shallow, mid, and deep cave-density fields so cave logic can evolve by depth band instead of becoming one noisy tunnel layer.
- Long-term cave passes can branch into tunnels, shafts, chambers, underground water, dens, dungeon entrances, and dangerous deep zones without bypassing chunk storage or chunk meshing.

### Walkable cloud path

- Atmospheric sky clouds and walkable cloud structures are intentionally separate systems.
- Atmospheric clouds should stay lightweight and mostly visual.
- Walkable clouds are planned as rare solid voxel content that only appears in eligible sky regions through `CLOUD_ELIGIBILITY` and `WALKABLE_CLOUDS` passes.
- A reserved `cloud` material family now exists in the block registry for future solid cloud platforms and cloud-city foundations.
- A later `SKY_STRUCTURES` pass can layer shrines, cloud bridges, sky ruins, and eventually a rare cloud realm or cloud city on top of those eligible zones.
- Cloud-city or cloud-realm content is an advanced exploration layer and should remain rare enough to feel like a major discovery.

## Rendering model

- The old prototype renderer emitted one geometry per exposed block and relied on flat debug colors. That made the terrain look washed out, over-bright, and low-detail because the lighting had no real surface breakup to work with.
- `ChunkRenderManager` is now the production path. It keeps separate load, render, and simulation radii and streams chunks around the camera.
- Chunk selection now stays centered on the player chunk in a buffered circular radius instead of a camera-facing cone. That keeps the nearby world stable when the player turns around and avoids full horizon reloads on fast view changes.
- Runtime chunk scheduling is capped and chunk completion work is batched per update so high render-distance settings do not enqueue or attach unbounded work in one frame.
- Chunk target planning is now cached by radius and refreshed only when the player crosses into a new chunk or changes runtime settings, which avoids rebuilding and sorting thousands of targets every frame while stationary.
- `ChunkMeshBuilder` emits chunk-local mesh sections grouped by shared material keys rather than block instances.
- The mesh builder now greedily merges adjacent coplanar faces that share the same material key, which reduces quad count dramatically on flat terrain and cliff bands.
- The runtime now also supports a cheaper far-distance surface LOD: nearby chunks keep full voxel face detail, while distant chunks collapse into a surface-only mesh that renders top surfaces plus compressed vertical walls per height column.
- That LOD path is intentionally a runtime seam for the current heightmap terrain, not a permanent shortcut. It gives the project a clean place to evolve into richer far-distance strategies once caves, overhangs, and floating landforms become more common.
- Interior face visibility checks now resolve against the local `ChunkData` first and only fall back to world-service lookups at chunk boundaries.
- `ChunkData` now stores voxels through a palette-compressed index buffer instead of a raw `BlockId[]`, which reduces loaded-world memory pressure and gives a clear path toward later palette/disk serialization.
- Hidden-face culling now works against authoritative world block lookups instead of waiting for all neighbor meshes to be resident, which keeps border meshes correct while allowing more aggressive chunk eviction.
- `TerrainMaterialLibrary` owns reusable textured materials so block visuals remain data-driven and future atlas migration stays localized.
- The terrain material path now uses crisp close-up filtering with mipmaps and reusable shared materials rather than one-off block-instance materials.
- Rendered face totals are now tracked incrementally when chunk meshes attach/detach instead of rescanning every rendered geometry every frame.

## Block visual pipeline

The block visual system is now being treated as content architecture, not only a terrain hack.

Supported visual modes:

- `single`
- `top_side_bottom`
- `explicit_faces`
- `cube_net`

Current runtime behavior:

- block definitions resolve face textures through `BlockVisualDefinition`
- `ChunkMeshBuilder` requests the correct face texture per visible block face
- `TerrainMaterialLibrary` caches reusable materials and can now slice a face out of a cube-net source image at runtime
- per-block-instance materials are still avoided

Supported cube-net format:

```text
    [back]
    [top]
[left][front][right]
    [bottom]
```

World-face mapping:

- `NORTH -> back`
- `SOUTH -> front`
- `WEST -> left`
- `EAST -> right`
- `UP -> top`
- `DOWN -> bottom`

The cube-net path is deterministic and does not guess face assignments.

Why this matters:

- artists can author one cube-net image and define a block in data
- stone, dirt, grass, logs, bricks, and later specialty blocks can all use the same import contract
- future texture-atlas work can remain localized to the material/texture layer instead of changing every block definition

## Settings and UI model

- `GraphicsSettings` owns the live render-distance setting and maps it onto chunk runtime radii.
- `PauseMenuController` owns the current in-game pause/options UI state.
- `Esc` now routes through that pause/options flow instead of acting as a raw mouse-capture toggle.
- Render distance changes are applied live to the chunk runtime and camera far clip so horizons can expand without restarting the game.
- The current default is `48` chunks and the experimental ceiling is `96` chunks. The runtime now favors stable buffered residency plus capped background work over aggressive view-cone eviction so turning remains smooth.
- The load-radius buffer is now intentionally smaller at high render distances so horizon rendering does not automatically keep an oversized extra ring of chunks resident.
- The HUD now reports chunk-memory usage and a basic frame-time split for chunk work, UI work, approximate render/engine work, and garbage collection time.

## Registry model

- Blocks and settings presets load from JSON files in `data/`.
- Block definitions now support data-driven texture modes including a 6-face cube-net source path.
- A reserved `pixel_survival:cloud_solid` block now exists so future walkable cloud content does not need a special-case material path.
- Additional registries already have reserved directories and documentation.
- Duplicate keys fail fast during loading.

## Planned system seams

- `InteractionRequest` is the future entry point for right-click block upgrades and other network-safe interaction requests.
- `GameSettings` already models survival presets so later systems can read tunable values instead of hardcoded constants.
- The world service is isolated behind `AuthoritativeWorldService` so chunk sync and remote queries can layer on top later.
- The new worldgen pass pipeline is the seam that future biome masks, cave carvers, floating landforms, cloud-region passes, and sky-structure injectors should plug into.
