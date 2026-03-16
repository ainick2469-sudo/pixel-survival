# Pixel Survival

Pixel Survival is a Java-based 3D block survival sandbox RPG with a multiplayer-safe foundation from day one. The project starts with disciplined production scaffolding, data-driven registries, and incremental vertical slices instead of a pile of disconnected prototype code.

## Current milestone

- Version target: `0.008`
- Milestone: fullscreen-first launch, stable buffered terrain streaming with a 48-chunk default, a 96-chunk experimental cap, shared-material terrain batching, stitched far-field terrain rendering, in-game screenshots, live F11 display toggling, and a stronger terrain art pass
- Status: repository foundation, docs, registry scaffolding, textured terrain, streamed chunk rendering, profiling HUD metrics, runtime-adjustable render distance with a 48-chunk default and 96-chunk cap, buffered radial chunk streaming, shared texture-array terrain batching, stitched far-field terrain regions for the outer distance ring, in-game screenshot capture, windowed/fullscreen toggling, a Minecraft-style pause/options flow, and `.voxelblock` block-asset import into the normal runtime registry path

## Technology stack

- Java 21
- Gradle 8.10.2
- jMonkeyEngine 3.7.0-stable
- Jackson for registry and config loading
- SLF4J + Logback for logging
- JUnit 5 for validation tests

## Quick start

1. Install or expose a Java 21 JDK and set `JAVA_HOME`.
2. From the repository root, use the committed Gradle wrapper.
3. Run:

```bat
gradlew.bat run
```

For this machine, there is also a desktop double-click launcher that uses a hidden launcher flow so the game window can take focus without a foreground command prompt.
The game now boots fullscreen by default and the launcher retries focus activation so the window is brought to the front more reliably on startup.

## Block import pipeline

Custom blocks can now be authored outside the game as `.voxelblock` assets and imported into the repo as normal optimized runtime content.

Current import command:

```bat
gradlew.bat importVoxelBlock -PvoxelInput=C:\Users\nickb\Downloads\custom-block.voxelblock -PvoxelBlockId=pixel_survival:custom_block -PvoxelDisplayName="Custom Block" -PvoxelMaterialFamily=decorative
```

What the importer does:

- decodes the per-face images from the `.voxelblock` file
- validates the expected `survivalcraft2.voxel-block-asset` / `version 1` / `cross-3x4` authoring format
- applies face transforms such as rotation, zoom, and offsets
- bakes a runtime cube-net PNG into `src/main/resources/Textures/BlockCubeNets/`
- writes a normal block registry JSON into `data/blocks/`

The Gradle task also still accepts explicit `--args` if you want direct CLI control, but the `-Pvoxel...` properties are the recommended Windows-friendly path.

That means the runtime still uses the same optimized path after import:

- hidden-face culling
- greedy chunk meshing
- shared texture-array terrain batching
- far-chunk surface LOD plus stitched far-field regions
- chunk streaming
- palette-compressed chunk storage

The authoring format can evolve, but the shipped runtime block path stays stable.

The repo now also includes a sample imported block generated from a local `custom-block.voxelblock` authoring file during this session:

- block definition: `data/blocks/custom_block.json`
- cube-net asset: `src/main/resources/Textures/BlockCubeNets/custom_block_cube_net.png`

## Controls

- `WASD`: move the debug fly camera
- `Mouse`: look around
- `Shift`: move faster
- `Esc`: open or close the pause menu
- `Left Click`: use the pause/options menu
- `F2` or `Print Screen`: save a screenshot of the current frame to `screenshots/` and copy it to the clipboard
- `F11`: toggle between fullscreen and windowed mode
- `F10`: quit the game intentionally

## Current render/runtime state

- Terrain now renders through chunk-local meshes rather than one scene geometry per exposed block.
- Chunk meshes now greedily merge adjacent coplanar faces that share the same resolved face texture layer, which cuts quad count sharply on large terrain surfaces.
- Textured terrain now batches through a shared texture-array material path, so chunk terrain usually collapses into one textured section per chunk instead of splitting by per-face materials.
- Chunk storage is now palette-compressed instead of storing a raw `BlockId` reference per voxel, which lowers world-memory cost at high loaded-chunk counts.
- Grass, dirt, stone, and sand now use 128x128 terrain textures with data-driven support for single-texture, top/side/bottom, explicit six-face, and cube-net block visuals.
- Grass, dirt, and stone were repainted toward a richer premium stylized-survival look instead of flat pastel debug colors.
- Terrain texture sampling now stays crisper up close while still using mipmaps for distance stability.
- The live `grass_block` and `stone` blocks now both come from imported `.voxelblock` authoring assets instead of the older generated terrain textures.
- The importer now follows the block-maker app convention where the center tile is the top face, the surrounding tiles are the wall faces, and the far tile is the bottom face.
- The grass-block import path now supports promoting one authored wall face across all four side slots, which keeps classic top/side/bottom terrain blocks clean even when the authoring asset only customizes one canonical wall face.
- The chunk runtime now supports a default render distance of `48` chunks and an adjustable cap up to `96` chunks while rate-limiting background load and mesh work.
- Chunk targets now stay in a buffered circular radius around the player so quick turns do not force full-world reloads.
- Background load and mesh completion work is now capped per update to reduce hitching when many chunks finish at once.
- Interior neighbor checks now stay chunk-local whenever possible, so mesh builds do less cross-service lookup work for interior terrain.
- Terrain now has two stable live chunk mesh detail tiers:
  - `FULL` for nearby chunks
  - `SURFACE` for mid-distance chunks
- The outer distance ring now renders through a separate stitched far-field terrain path that builds coarse heightmap-style region meshes instead of normal chunk meshes.
- The far-field path keeps using the shared terrain texture-array material, so imported `.voxelblock` grass and stone visuals still come through the normal block registry pipeline.
- Far-field coverage is intentionally limited to the current heightmap-style terrain model; it is not pretending to solve future caves, overhangs, or floating mountains.
- The far-field anchor snaps on a coarse region grid with overlap at the near/far seam, which keeps the transition more stable and avoids constant boundary thrash as the player moves.
- The detailed chunk ring is now intentionally smaller at high render distances, so the default `48` setting keeps a `32`-chunk detailed chunk radius while the far-field renderer carries the outer ring; `96` keeps a `62`-chunk detailed chunk radius and remains experimental.
- A coarse `HORIZON` prototype seam still exists in code, but it is still disabled in the live runtime because the old approximation introduced visible cracks and holes in distant terrain.
- The HUD now exposes chunk-memory usage plus chunk/UI/render+engine/GC timing so performance tuning is based on actual runtime data instead of only FPS.
- Chunk target planning now reuses cached radius-offset plans and only refreshes full target sets when the player crosses into a new chunk or changes graphics settings.
- Runtime face-count metrics are now tracked incrementally instead of rescanning every rendered chunk node every frame.
- High-distance load buffering stays intentionally lean, so `48` and `96` chunk settings do not silently imply the much larger older prototype load radius.
- The HUD now exposes runtime counts for loaded chunks, rendered chunks, rendered far regions, terrain sections, simulated chunk targets, queue depth, and heap use.
- `Esc` opens a centered pause/options menu where render distance can be adjusted live.
- `F2` and `Print Screen` both capture the current in-game frame directly from the render pipeline, save it into `screenshots/`, and also push the captured image into the system clipboard when clipboard access is available.
- `F11` switches between fullscreen startup mode and a centered resizable window without restarting the game.

## Project principles

- World generation and exploration come first.
- The simulation is written around an authoritative host boundary even in local play.
- Content belongs in registries and data files, not hardcoded gameplay monoliths.
- Every meaningful work session updates the devlog and keeps the game runnable.

## Repository map

- `docs/`: design, roadmap, architecture, networking, registry, and recipe documentation
- `data/`: content and settings registries
- `src/main/java/`: Java source
- `src/test/java/`: validation tests
- `DEVLOG.md`: session-by-session implementation history

## Immediate roadmap

1. `0.001`: spawn blocks
2. `0.002`: add height differences
3. `0.003`: add stone blocks
4. `0.004`: add grass
5. `0.005`: begin terrain layering
6. `0.006`: production chunk runtime foundation and textured terrain readability pass
7. `0.007`: adjustable render distance, pause/options menu, and distant horizons
8. `0.008`: fullscreen-visible startup, buffered chunk streaming stabilization, terrain texture-array batching, stitched far-field terrain rendering, and premium terrain texture upgrade
