# Game Design

## Core fantasy

Players begin vulnerable in a large procedural block world and grow into explorers, builders, crafters, traders, tamers, and settlement founders. The world should feel readable enough to enjoy immediately and deep enough to grow for a long roadmap.

The long-term exploration fantasy is not only "walk around pretty terrain." The world should eventually feel huge, vertical, mysterious, and layered with discoveries that change how players travel and what they believe is possible.

## Pillars

### Worldgen and exploration first

Travel should become rewarding over time through terrain variety, vertical layers, landmarks, structures, ecology, and later settlements.

Long-term exploration targets include:

- rare floating mountains and floating islands
- dramatic overhangs and suspended terrain masses
- deep layered caves with real depth bands
- rare walkable cloud formations and hidden sky destinations
- vertical discovery routes that support traversal challenge, rare resources, ruins, creatures, and late-game wonder

### Building with functional meaning

Building is not only cosmetic. Shelter, storage, crafting efficiency, defense, and settlement identity should matter.

### Tunable survival

Hunger, temperature, wetness, hostile density, loot rates, and death penalties should all be adjustable through presets and custom tuning.

### Deep crafting and upgrades

The project aims for large recipe counts eventually, but through data architecture and progression bands rather than chaotic hardcoding.

### Civilization and ecology

Animals, predators, tameable creatures, villagers, villages, towns, and cities are long-term identity systems and should be anticipated structurally.

## Long-term exploration layers

### Surface world

- readable survival terrain
- biome identity
- landmarks and traversal routes
- eventually settlements, ruins, camps, shrines, and roads

### Underground world

The underground should become a real exploration layer, not a few random holes.

Depth bands should eventually include:

- shallow cave networks near the surface
- mid-depth branching systems with chambers, water, resource pockets, and den spaces
- deep dangerous cave zones with rarer materials, stronger threats, dungeon hooks, and more oppressive atmosphere

Caves should support:

- winding tunnels
- vertical shafts
- large chambers
- underground lakes and rivers
- narrow passages
- branching systems
- biome-specific variation
- creature dens
- dungeon entrances

### Sky world

The sky is a later discovery layer, not a default travel plane.

Planned sky features:

- rare floating islands
- giant suspended mountain masses
- cloud shelves near rare floating landforms
- rare walkable cloud pads and cloud bridges
- sky ruins and shrines
- eventually a hidden cloud realm / cloud city

Important rule:

- most clouds stay atmospheric only
- only rare eligible sky zones produce walkable cloud content
- reaching those zones should usually require intentional traversal effort

The cloud realm / cloud city should feel like a major discovery moment, not background scenery.

## Floating mountain design intent

- rare, regional, and visually majestic
- integrated into biome/region logic instead of global spam
- useful for traversal, resources, ruins, nests, and future high-altitude content
- shaped with cliff shelves, undersides, erosion, and hanging root-like stone rather than cubes or bland blobs

## Current implementation boundary

The current shipped build still only generates surface terrain layering. Floating mountains, complex caves, and walkable cloud systems are intentionally deferred until later milestones.

This session only prepares the architecture so those systems can enter the worldgen pipeline cleanly without rewriting the current terrain milestone.

## Session 1 scope

Session 1 is intentionally narrow:

- production repo foundation
- documentation and registry scaffolding
- settings scaffolding
- a runnable `0.001` scene with visible spawned blocks

This is the smallest meaningful slice that proves the project is a real game codebase instead of a design-only document set.
