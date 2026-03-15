# Devlog

## 2026-03-15 00:41:37 MDT

- Date/Time: 2026-03-15 00:41:37 MDT
- Branch: `codex/session-1-foundation-0.001`
- Version Target: `0.001`
- Milestone: Bootstrap the production repo and ship a runnable 3D block-spawn slice.
- Completed Work:
  - Initialized the `pixel-survival` repo and generated the Gradle 8.10.2 wrapper.
  - Added required project docs, architecture notes, roadmap, version plan, networking plan, registry docs, and recipe docs.
  - Scaffolded `data/` registries, settings presets, and recipe category directories.
  - Implemented registry loading, data root resolution, settings preset loading, the local host session seam, authoritative chunk generation, and the first `FlatSpawnWorldGenerator`.
  - Implemented a jMonkeyEngine bootstrap with lighting, fly camera, HUD, and exposed-block debug rendering.
  - Added validation tests for block loading, duplicate IDs, settings preset loading, and flat terrain generation.
- Files Changed:
  - build and wrapper files
  - root docs and `docs/*`
  - `data/blocks/*`, `data/settings_presets/*`, seeded recipe examples, and empty registry keep files
  - `src/main/java/io/github/ainick2469/pixelsurvival/**`
  - `src/test/java/io/github/ainick2469/pixelsurvival/**`
- Systems Touched:
  - application bootstrap
  - session architecture
  - registry loading
  - survival settings scaffolding
  - chunk/world generation
  - block rendering
  - automated validation
- Tests Run:
  - `./gradlew test`
  - `./gradlew run` smoke launch, verified jME window startup, OpenGL initialization, registry load log, and visible chunk render
- Current Playable State:
  - The project launches into a lit 3D scene with a fly camera and a visible spawned dirt chunk.
  - HUD shows `Pixel Survival v0.001` and `LOCAL_HOST`.
- Known Issues:
  - Terrain is a single flat debug chunk with no persistence, collision, or placement/removal.
  - Rendering still uses one geometry per exposed block, which is fine for `0.001` but not for streamed terrain.
  - Session and networking boundaries exist, but no transport or dedicated server path is implemented yet.
- Next Tasks:
  - Implement `0.002` deterministic height differences.
  - Preserve the current chunk and registry contracts while replacing the flat generator.
  - Start documenting and testing the terrain noise inputs for later `0.003` through `0.005`.
- Risks/Technical Debt:
  - The data resolver currently relies on runtime path discovery heuristics that should be hardened when packaged builds become a real target.
  - Debug rendering is intentionally naive and will need chunk meshing before larger world sizes are practical.
  - Gradle reports generic future deprecation warnings that should be revisited during later build maintenance.

## 2026-03-15 00:45:00 MDT

- Date/Time: 2026-03-15 00:45:00 MDT
- Branch: `codex/session-1-foundation-0.001`
- Version Target: `0.001`
- Milestone: Add a reliable local launcher path from the Windows desktop.
- Completed Work:
  - Added a versioned repo launcher script at `scripts\run_local.cmd`.
  - Added launcher documentation and updated the README to mention the desktop entrypoint.
  - Created a Windows desktop launcher file outside the repo that forwards into the versioned script.
- Files Changed:
  - `README.md`
  - `DEVLOG.md`
  - `LAUNCHER.md`
  - `scripts/run_local.cmd`
  - local desktop file `C:\Users\nickb\OneDrive\Desktop\Pixel Survival.cmd`
- Systems Touched:
  - local developer/run workflow
  - desktop launch entrypoint
- Tests Run:
  - verified launcher target paths exist
  - verified repo launcher script path and toolchain paths resolve correctly
- Current Playable State:
  - The game is launchable from the repo and from the Windows desktop through the local launcher.
- Known Issues:
  - The desktop launcher is local-machine convenience state, not a versioned artifact.
- Next Tasks:
  - Keep `scripts\run_local.cmd` aligned if the tool or repo path changes.
  - Move to `0.002` terrain height differences next.
- Risks/Technical Debt:
  - The launcher currently depends on the current sibling folder layout between the repo and `pixel-survival-tools`.

## Entry Template

- Date/Time:
- Branch:
- Version Target:
- Milestone:
- Completed Work:
- Files Changed:
- Systems Touched:
- Tests Run:
- Current Playable State:
- Known Issues:
- Next Tasks:
- Risks/Technical Debt:
