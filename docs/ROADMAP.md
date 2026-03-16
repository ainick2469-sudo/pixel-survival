# Roadmap

## Phase A: Foundation

- Establish the Gradle Java project, docs, devlog, registry scaffolding, settings scaffolding, and authoritative-host architecture seams.
- Keep the project runnable from the first milestone.

## Phase B: Versioned Terrain Beginnings

- `0.001`: render visible spawned blocks in a 3D scene.
- `0.002`: replace the flat plane with procedural height differences.
- `0.003`: introduce stone layers and registry-driven block identities.
- `0.004`: introduce grass surfaces and visible surface rules.
- `0.005`: formalize layered terrain generation for future expansion.

## Phase C: Worldgen Expansion

- `0.006`: replace debug block rendering with the production chunk runtime foundation and textured terrain materials.
- `0.007`: add a Minecraft-style pause/options menu and adjustable high-distance horizon settings on top of the new runtime.
- `0.008`: move to fullscreen-first startup, stabilize buffered chunk streaming/unloading, add greedy chunk meshing, add palette-compressed chunk storage plus runtime telemetry, cache chunk target planning/metrics work, keep the live detailed chunk path on continuity-safe `FULL` plus `SURFACE`, batch terrain through a shared texture-array material path, add stitched far-field terrain regions for the outer ring, and add a stronger terrain art pass that can hold up at higher render distances.
- Prepare the pass-based overworld generator so future terrain systems can be layered without rewriting the current milestone terrain.
- Prepare a topology seam so the project can safely migrate from the planar prototype to a later cube-sphere planetary world.
- Prepare the block visual pipeline for `single`, `top_side_bottom`, `explicit_faces`, and `cube_net` block textures.
- Better noise stacks and macro terrain control
- Planetary topology and climate migration planning
- Biome masks and biome families
- Cave generation by depth band:
  - shallow cave entrances and tunnel networks
  - mid-depth branching cave systems and chambers
  - deep dangerous cave zones, underground water, and rare content
- Landmark generation and rare regional signatures
- Floating mountain and floating island regional pass
- Cloud-region eligibility and rare walkable cloud generation
- Sky ruins, cloud bridges, and eventual cloud-realm / cloud-city structure injection
- Water systems
- Resource bands
- Vegetation and landmarks
- Chunk streaming and structure placement
- Queue prioritization and background-work budgeting for the mixed detailed-chunk plus far-field terrain runtime

## Worldgen sequencing rule

Long-term overworld expansion should continue in this order unless there is a strong reason to change it:

1. planetary topology / coordinate model
2. base terrain height generation
3. terrain material layering
4. biome mask distribution
5. cave carving
6. landmark generation
7. floating landforms
8. cloud-region eligibility
9. walkable cloud generation
10. sky structures
11. vegetation and props
12. structures and POIs
13. settlement placement
14. ecology and creature spawn rules

That order keeps exploration features integrated with chunk generation instead of becoming bolt-on gimmicks.

## Block content pipeline rule

Block/material growth should continue through data and assets, not renderer rewrites.

Priority support path:

1. add or replace texture assets
2. define the block in `data/blocks`
3. load it through the registry
4. let chunk meshing and material resolution handle the rest

## Phase D: Building Systems

- Block targeting
- Placement and removal
- Inventory-backed building
- Right-click block upgrades
- Structural metadata where needed

## Phase E: Survival Systems

- Health
- Hunger
- Stamina
- Sleep and fatigue
- Temperature and wetness
- Configurable survival presets

## Phase F: Creatures And NPCs

- Wildlife spawn ecology
- Predator/prey categories
- Tameable beasts
- Villagers and settlement inhabitants

## Phase G: Crafting And Progression

- Recipe execution
- Hand crafting
- Station tiers
- Bench upgrades
- Backpack upgrades

## Phase H: Combat, Machines, And Later Magic

- Weapon families
- Enemy AI
- Armor and clothing
- Machine systems first
- Magic and arcane branches later
