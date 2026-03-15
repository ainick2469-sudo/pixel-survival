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

## Rendering model

- The old prototype renderer emitted one geometry per exposed block and relied on flat debug colors. That made the terrain look washed out, over-bright, and low-detail because the lighting had no real surface breakup to work with.
- `ChunkRenderManager` is now the production path. It keeps separate load, render, and simulation radii and streams chunks around the camera.
- Runtime chunk scheduling is now capped so high render-distance settings do not enqueue unbounded load and mesh work in a single frame.
- `ChunkMeshBuilder` emits chunk-local mesh sections grouped by shared material keys rather than block instances.
- Hidden-face culling now works against loaded neighbor chunks, which removes the worst interior waste and keeps the mesh path compatible with later greedy meshing.
- `TerrainMaterialLibrary` owns reusable textured materials so block visuals remain data-driven and future atlas migration stays localized.

## Settings and UI model

- `GraphicsSettings` owns the live render-distance setting and maps it onto chunk runtime radii.
- `PauseMenuController` owns the current in-game pause/options UI state.
- `Esc` now routes through that pause/options flow instead of acting as a raw mouse-capture toggle.
- Render distance changes are applied live to the chunk runtime and camera far clip so horizons can expand without restarting the game.

## Registry model

- Blocks and settings presets load from JSON files in `data/`.
- Block definitions can now declare `visuals.topTexture`, `visuals.sideTexture`, `visuals.bottomTexture`, and `visuals.tintKey`.
- Additional registries already have reserved directories and documentation.
- Duplicate keys fail fast during loading.

## Planned system seams

- `InteractionRequest` is the future entry point for right-click block upgrades and other network-safe interaction requests.
- `GameSettings` already models survival presets so later systems can read tunable values instead of hardcoded constants.
- The world service is isolated behind `AuthoritativeWorldService` so chunk sync and remote queries can layer on top later.
