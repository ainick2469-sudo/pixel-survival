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

## After `0.006`

- biome masks
- caves
- water systems
- vegetation
- chunk streaming
- structures
- settlement seeding

## Session 1 target

Deliver each early terrain milestone in a way that does not need a rewrite for chunk streaming, textured terrain, and later world detail.
