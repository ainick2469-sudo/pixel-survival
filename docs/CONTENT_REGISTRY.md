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
- `pixel_survival:cloud_solid` (reserved for future walkable-cloud worldgen, not spawned yet)
- `pixel_survival:custom_block` (sample imported `.voxelblock` asset, not spawned by worldgen)

## Active block definition shape

Current terrain blocks use this render-oriented contract:

- `id`
- `displayName`
- `materialFamily`
- `solid`
- `opaque`
- `debugColor`
- `visuals.textureMode`
- `visuals.texture`
- `visuals.topTexture`
- `visuals.sideTexture`
- `visuals.bottomTexture`
- `visuals.frontTexture`
- `visuals.backTexture`
- `visuals.leftTexture`
- `visuals.rightTexture`
- `visuals.cubeNetTexture`
- `visuals.cubeNetLayout`
- `visuals.tintKey`
- `tags`

The `visuals` object is now the standard path for block rendering. `debugColor` remains as a fallback/debug aid, not the primary visual path.

## Supported visual modes

### `single`

Use one texture on every face.

```json
"visuals": {
  "textureMode": "single",
  "texture": "Textures/Terrain/sand.png",
  "tintKey": null
}
```

### `top_side_bottom`

Use a top texture, side texture, and bottom texture.

```json
"visuals": {
  "textureMode": "top_side_bottom",
  "topTexture": "Textures/Terrain/grass_top.png",
  "sideTexture": "Textures/Terrain/grass_side.png",
  "bottomTexture": "Textures/Terrain/dirt.png",
  "tintKey": "grass"
}
```

### `explicit_faces`

Use six explicitly named face textures.

```json
"visuals": {
  "textureMode": "explicit_faces",
  "backTexture": "Textures/Blocks/back.png",
  "topTexture": "Textures/Blocks/top.png",
  "leftTexture": "Textures/Blocks/left.png",
  "frontTexture": "Textures/Blocks/front.png",
  "rightTexture": "Textures/Blocks/right.png",
  "bottomTexture": "Textures/Blocks/bottom.png",
  "tintKey": null
}
```

### `cube_net`

Use one cube-net source image that the runtime slices into faces.

```json
"visuals": {
  "textureMode": "cube_net",
  "cubeNetTexture": "Textures/BlockCubeNets/stone_cube_net.png",
  "cubeNetLayout": "back_top_left_front_right_bottom",
  "tintKey": null
}
```

## Supported cube-net layout

The currently supported layout is:

```text
    [back]
    [top]
[left][front][right]
    [bottom]
```

This corresponds to a `3 x 4` face grid:

- column `1`, row `0`: `back`
- column `1`, row `1`: `top`
- column `0`, row `2`: `left`
- column `1`, row `2`: `front`
- column `2`, row `2`: `right`
- column `1`, row `3`: `bottom`

World-face mapping:

- `north` uses `back`
- `south` uses `front`
- `west` uses `left`
- `east` uses `right`
- `up` uses `top`
- `down` uses `bottom`

The engine does not guess face placement. If artists follow this layout, the block will map correctly with no renderer rewrite.

An additional artist-friendly layout is also supported for the newer terrain cube nets:

```text
    [bottom]
    [back]
[left][top][right]
    [front]
```

Use `center_top_surrounding_sides_outer_bottom` for that layout.

This maps as:

- center tile: `top`
- tile directly above center: `back`
- tile directly left of center: `left`
- tile directly right of center: `right`
- tile directly below center: `front`
- extra outer tile above `back`: `bottom`

Runtime note:

- The engine keeps the center tile as `top`, the surrounding tiles as the four side faces, and the far tile as `bottom`.
- For this layout, side faces are rotated automatically at render time where needed so the top edge of each side tile stays aligned with the top of the wall in-game.

This is the layout currently used by:

- `pixel_survival:dirt`
- `pixel_survival:grass_block`

Current reference block:

- `pixel_survival:stone` now points at `Textures/BlockCubeNets/stone_cube_net.png`
- replacing that file with a higher-quality authored stone cube net should not require any rendering-code change
- `pixel_survival:dirt` now points at `Textures/BlockCubeNets/dirt_cube_net.png`
- `pixel_survival:grass_block` now points at `Textures/BlockCubeNets/grass_block_cube_net.png`

## `.voxelblock` authoring import

The repo now supports importing `.voxelblock` assets produced by the external block-maker app into normal runtime block content.

Supported current authoring file shape:

- `type: "survivalcraft2.voxel-block-asset"`
- `version: 1`
- `tileSize`
- `layout` (`cross-3x4` and `top-center-cross-3x4` are currently accepted by the importer)
- `faces.back`
- `faces.top`
- `faces.left`
- `faces.front`
- `faces.right`
- `faces.bottom`

Each face entry may declare:

- `sourceName`
- `rotation`
- `zoom`
- `offsetX`
- `offsetY`
- `fitMode`
- `imageDataUrl`

Current importer behavior:

- reads the `.voxelblock` JSON
- validates the asset type, version, and expected authoring layout
- decodes each face image from `imageDataUrl`
- applies the stored transform fields per face
- interprets the current block-maker export convention as:
  - center tile = `top`
  - surrounding tiles = wall faces
  - far tile = `bottom`
- can optionally promote one authored wall face across all four wall slots during import, which is useful for terrain-style blocks like grass where one canonical side texture should wrap every wall face
- bakes a runtime cube-net PNG in the `center_top_surrounding_sides_outer_bottom` layout
- writes a normal block definition JSON in `data/blocks`

Current import command:

```bat
gradlew.bat importVoxelBlock -PvoxelInput=C:\path\to\block.voxelblock -PvoxelBlockId=pixel_survival:my_block -PvoxelDisplayName="My Block" -PvoxelMaterialFamily=decorative
```

Optional import property for canonical wall-face blocks:

```bat
gradlew.bat importVoxelBlock -PvoxelInput=C:\path\to\grass-block.voxelblock -PvoxelBlockId=pixel_survival:grass_block -PvoxelMaterialFamily=soil -PvoxelUniformSideFace=front -PvoxelTags=terrain,surface_layer
```

Direct `--args` are still supported for the importer task, but the `-Pvoxel...` property path is the recommended Windows workflow because it avoids fragile shell quoting.

The important architecture rule is that `.voxelblock` is an authoring/import format, not a special runtime rendering format. After import, the block still uses the same optimized game path as every other block:

- registry loading
- face-texture resolution
- hidden-face culling
- greedy chunk meshing
- far-chunk surface LOD
- chunk streaming and unload rules

Current imported sample:

- source: local `custom-block.voxelblock` authoring file imported during this session
- runtime block id: `pixel_survival:custom_block`
- generated block definition: `data/blocks/custom_block.json`
- generated cube net: `src/main/resources/Textures/BlockCubeNets/custom_block_cube_net.png`

Current live terrain use:

- `pixel_survival:grass_block` is now imported from the `.voxelblock` authoring pipeline and uses the same center-top layout convention as the block-maker app.
- `pixel_survival:stone` is now also imported from the `.voxelblock` authoring pipeline.

## Asset conventions

- Single-face and per-face terrain textures live under `src/main/resources/Textures/Terrain/`
- Cube-net source images should live under `src/main/resources/Textures/BlockCubeNets/`
- New block assets should be introduced through:
  - the image file
  - a `data/blocks/<block>.json` definition
  - registry loading

The goal is that new custom blocks do not require rendering-code edits.

## Reserved registry expansion

The remaining registry directories are scaffolded now to prevent later architecture drift when recipes, creatures, upgrades, and worldgen content begin to scale.
