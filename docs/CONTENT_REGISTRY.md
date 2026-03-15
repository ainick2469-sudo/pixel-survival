# Content Registry

## Registry families

- `data/blocks`
- `data/items`
- `data/recipes`
- `data/stations`
- `data/creatures`
- `data/biomes`
- `data/regions`
- `data/structures`
- `data/loot_tables`
- `data/factions`
- `data/upgrades`
- `data/settings_presets`

## Naming rules

- Use namespaced IDs such as `pixel_survival:dirt`.
- File names should be lowercase snake_case.
- One major content definition per JSON file.

## Validation rules

- Duplicate IDs fail loading.
- Unknown required fields should be treated as schema errors as registries mature.
- Registries should remain data-driven so gameplay logic reads IDs, tags, and typed definitions rather than hand-maintained switch trees.

## Session 1 active registries

- Block definitions
- Survival settings presets

Current shipped terrain blocks:

- `pixel_survival:air`
- `pixel_survival:grass_block`
- `pixel_survival:dirt`
- `pixel_survival:stone`
- `pixel_survival:sand`

## Active block definition shape

Current terrain blocks use this render-oriented contract:

- `id`
- `displayName`
- `materialFamily`
- `solid`
- `opaque`
- `debugColor`
- `visuals.topTexture`
- `visuals.sideTexture`
- `visuals.bottomTexture`
- `visuals.tintKey`
- `tags`

The `visuals` object is now the standard path for terrain rendering. `debugColor` remains as a fallback/debug aid, not the primary visual path.

## Reserved registry expansion

The remaining registry directories are scaffolded now to prevent later architecture drift when recipes, creatures, upgrades, and worldgen content begin to scale.
