# Pixel Survival

Pixel Survival is a Java-based 3D block survival sandbox RPG with a multiplayer-safe foundation from day one. The project starts with disciplined production scaffolding, data-driven registries, and incremental vertical slices instead of a pile of disconnected prototype code.

## Current milestone

- Version target: `0.008`
- Milestone: fullscreen-first launch, stable buffered terrain streaming with a 48-chunk default, a 192-chunk experimental cap, shared-material terrain batching, stitched far-field terrain rendering, bounded session mesh reuse, phased `192` traversal fill, in-game screenshots, live F11 display toggling, and a stronger terrain art pass
- Status: repository foundation, docs, registry scaffolding, textured terrain, streamed chunk rendering, profiling HUD metrics, runtime-adjustable render distance with a 48-chunk default and 192-chunk experimental cap, buffered radial chunk streaming, shared texture-array terrain batching, stitched far-field terrain regions for the outer distance ring, bounded session mesh caching plus restored raw chunk unloading, phased high-distance traversal fill/catch-up, in-game screenshot capture, windowed/fullscreen toggling, a Minecraft-style pause/options flow, and `.voxelblock` block-asset import into the normal runtime registry path

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
- The chunk runtime now supports a default render distance of `48` chunks and an adjustable experimental cap up to `192` chunks while rate-limiting background load and mesh work.
- Chunk targets now stay in a buffered circular radius around the player so quick turns do not force full-world reloads.
- Background load and mesh completion work is now capped per update to reduce hitching when many chunks finish at once.
- Interior neighbor checks now stay chunk-local whenever possible, so mesh builds do less cross-service lookup work for interior terrain.
- Detailed startup coverage is now bridged toward the far-field seam at high render distances so the game does not open with the earlier giant middle-band void between the near chunk ring and the stitched far-field ring.
- Session reuse now happens through a bounded mesh cache instead of keeping every raw chunk loaded forever, so revisits can still reuse mesh work without letting loaded-chunk memory grow without limit.
- Raw authoritative chunks are unloaded again outside the active buffered load radius, while the bounded session mesh cache retains recently-built chunk meshes for quick reattachment during the same session.
- Terrain now has two stable live chunk mesh detail tiers:
  - `FULL` for nearby chunks
  - `SURFACE` for mid-distance chunks
- The outer distance ring now renders through a separate stitched far-field terrain path that builds coarse heightmap-style region meshes instead of normal chunk meshes.
- The far-field path keeps using the shared terrain texture-array material, so imported `.voxelblock` grass and stone visuals still come through the normal block registry pipeline.
- Far-field target planning is now clip-aware, so unchanged interior far regions stay clean across anchor snaps while only new or boundary-touching regions are dirtied and rebuilt.
- The `192` ultra-distance path now uses subdivided height patches plus seam-mask weights instead of one flat top quad per `16 x 16`-block coarse cell, which reduces the obvious giant box-mountain look near the far seam.
- Chunk and far-field completion work now use motion-aware time budgets, so moving, settling, and stationary states spend different amounts of main-thread attach time instead of draining large completion bursts in one frame.
- At `192`, the far-field startup prime is now phased instead of synchronously building the whole stitched outer ring in one burst, which reduces the first-fill hitch cost.
- High-distance chunk work is now split into `CORE`, `SEAM`, `PROMOTION`, and `BUFFER` bands so movement prioritizes the playable ring and seam continuity while delaying less important outer detailed promotion.
- Still-state catch-up now ramps up over time instead of immediately spending the full attach/build budget when the player stops moving, which reduces the worst post-movement catch-up spikes.
- Far-field coverage is intentionally limited to the current heightmap-style terrain model; it is not pretending to solve future caves, overhangs, or floating mountains.
- The far-field anchor snaps on a coarse region grid with overlap at the near/far seam, which keeps the transition more stable and avoids constant boundary thrash as the player moves.
- The detailed chunk ring is now intentionally smaller at high render distances, so the default `48` setting keeps a `32`-chunk detailed chunk radius while the far-field renderer carries the outer ring; `96` keeps a `62`-chunk detailed chunk radius, and `192` shrinks detailed chunk coverage more aggressively and remains strictly experimental.
- The HUD now exposes bounded session mesh cache counts and estimated mesh-cache memory, which makes it easier to tell whether a high-distance run is reusing recent terrain or blowing out residency.
- The HUD now also reports far-field queue depth, motion profile, far anchor snaps per second, and far-region rebuilds per second so high-distance tuning is grounded in the live seam/runtime behavior instead of only FPS.
- A coarse `HORIZON` prototype seam still exists in code, but it is still disabled in the live runtime because the old approximation introduced visible cracks and holes in distant terrain.
- The HUD now exposes chunk-memory usage plus chunk/UI/render+engine/GC timing so performance tuning is based on actual runtime data instead of only FPS.
- Chunk target planning now reuses cached radius-offset plans and only refreshes full target sets when the player crosses into a new chunk or changes graphics settings.
- Runtime face-count metrics are now tracked incrementally instead of rescanning every rendered chunk node every frame.
- High-distance load buffering stays intentionally lean, so `48` and `96` chunk settings do not silently imply the much larger older prototype load radius.
- The HUD now exposes runtime counts for loaded chunks, rendered chunks, rendered far regions, terrain sections, simulated chunk targets, queue depth, and heap use.
- `scripts/desktop_smoke.ps1` now drives smoke validation through startup render-distance override, in-game scheduled framebuffer screenshots, and optional JSONL motion reports instead of brittle menu automation and OS-level window capture.
- The smoke harness now proves `48`, `96`, and `192` startup directly and can run a scripted `192` forward-motion benchmark with JSONL telemetry, so high-distance changes are validated under motion instead of only while standing still.
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
