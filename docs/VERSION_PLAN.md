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
| `0.008` | Visible-range streaming and terrain art upgrade | Fullscreen-first launch, camera-driven chunk loading/unloading up to 48 chunks, and upgraded premium-style terrain textures improve horizon scale and terrain readability |

## After `0.008`

The early locked sequence remains unchanged. Future worldgen expansion should build on top of it rather than rewriting it.

Structural prep now in place:

- pass-based worldgen pipeline
- scratch fields reserved for biome masks, cave density bands, floating-landform eligibility, and cloud eligibility
- reserved solid cloud block/material family for future walkable cloud content

Planned next worldgen direction after `0.008`:

- biome masks and regional identity
- cave foundations
- water systems
- vegetation
- landmark and floating-landform eligibility
- walkable cloud eligibility and sky-structure planning
- structures and settlement seeding

Future exploration systems are planned to land in this rough order:

1. biome masks and macro region control
2. shallow-to-deep cave generation
3. landmark distribution
4. floating mountains / floating islands
5. cloud-region eligibility and rare walkable cloud generation
6. sky structures, shrines, and eventually cloud-city content
7. ecology, creatures, and high-altitude/underground content

## Session 1 target

Deliver each early terrain milestone in a way that does not need a rewrite for chunk streaming, textured terrain, deep cave systems, floating landforms, walkable clouds, and later world detail.
