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

- `ChunkDebugRenderer` converts chunk data into jME scene nodes.
- Session 1 uses simple shared cube meshes and debug materials instead of a production meshing pipeline.
- The renderer only emits exposed blocks to avoid wasting work on fully buried interior cubes.

## Registry model

- Blocks and settings presets load from JSON files in `data/`.
- Additional registries already have reserved directories and documentation.
- Duplicate keys fail fast during loading.

## Planned system seams

- `InteractionRequest` is the future entry point for right-click block upgrades and other network-safe interaction requests.
- `GameSettings` already models survival presets so later systems can read tunable values instead of hardcoded constants.
- The world service is isolated behind `AuthoritativeWorldService` so chunk sync and remote queries can layer on top later.
