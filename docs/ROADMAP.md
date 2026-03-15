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
- `0.008`: move to fullscreen-first startup, camera-visible chunk streaming/unloading, and a stronger terrain art pass that can hold up at higher render distances.
- Prepare the pass-based overworld generator so future terrain systems can be layered without rewriting the current milestone terrain.
- Better noise stacks and macro terrain control
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

## Worldgen sequencing rule

Long-term overworld expansion should continue in this order unless there is a strong reason to change it:

1. base terrain height generation
2. terrain material layering
3. biome mask distribution
4. cave carving
5. landmark generation
6. floating landforms
7. cloud-region eligibility
8. walkable cloud generation
9. sky structures
10. vegetation and props
11. structures and POIs
12. ecology and creature spawn rules

That order keeps exploration features integrated with chunk generation instead of becoming bolt-on gimmicks.

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
