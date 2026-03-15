# Version Plan

## Locked early sequence

| Version | Goal | Minimum result |
| --- | --- | --- |
| `0.001` | Spawn blocks | Launchable 3D scene with visible blocks and a player camera |
| `0.002` | Add height differences | Deterministic heightmap terrain replaces the flat scene |
| `0.003` | Add stone blocks | Depth-based stone appears under surface material |
| `0.004` | Add grass | Grass top surfaces visibly differentiate the terrain |
| `0.005` | Begin layering | Grass, dirt, and stone become a true terrain layering pipeline |
| `0.006` | Chunk runtime and terrain readability | Production chunk streaming foundation replaces debug block rendering and textured terrain materials replace pastel debug colors |
| `0.007` | Pause/settings and adjustable horizons | Minecraft-style pause/options menu controls a live high-range render-distance system and distant-horizon camera setup |
| `0.008` | Buffered streaming stabilization and terrain art upgrade | Fullscreen-first launch, stable buffered chunk loading/unloading up to 48 chunks, greedy chunk meshing for lower quad count, and upgraded premium-style terrain textures improve horizon scale and terrain readability |

## After `0.008`

The early locked sequence remains unchanged. Future worldgen expansion should build on top of it rather than rewriting it.

Structural prep now in place:

- topology profile seam for the current planar prototype and a later cube-sphere planetary world
- pass-based worldgen pipeline
- scratch fields reserved for biome masks, cave density bands, floating-landform eligibility, and cloud eligibility
- support for data-driven block texture modes including 6-face cube-net sources
- reserved solid cloud block/material family for future walkable cloud content

Planned next worldgen direction after `0.008`:

- macro biome masks and regional identity
- block-content expansion through the cube-net visual pipeline
- cave foundations
- water systems
- vegetation
- landmark and floating-landform eligibility
- walkable cloud eligibility and sky-structure planning
- structures and settlement seeding

Future exploration systems are planned to land in this rough order:

1. topology-safe climate and biome masks
2. shallow-to-deep cave generation
3. landmark distribution
4. floating mountains / floating islands
5. cloud-region eligibility and rare walkable cloud generation
6. sky structures, shrines, and eventually cloud-city content
7. ecology, creatures, and high-altitude/underground content
8. staged migration from planar terrain prototype to planetary surface topology

## Planet migration rule

The planet migration must be staged, not forced into the early terrain versions.

Recommended path:

1. keep early terrain milestones on the current planar prototype
2. make climate, biome, cave, landmark, and sky passes topology-aware
3. introduce cube-sphere addressing and streaming behind world-service seams
4. switch the mature overworld to planetary traversal only when chunk streaming, meshing, and content systems are ready

The current build remains on the planar prototype by design.

## Session 1 target

Deliver each early terrain milestone in a way that does not need a rewrite for chunk streaming, textured terrain, planetary topology migration, deep cave systems, floating landforms, walkable clouds, custom cube-net block textures, and later world detail.
