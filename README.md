# Pixel Survival

Pixel Survival is a Java-based 3D block survival sandbox RPG with a multiplayer-safe foundation from day one. The project starts with disciplined production scaffolding, data-driven registries, and incremental vertical slices instead of a pile of disconnected prototype code.

## Current milestone

- Version target: `0.008`
- Milestone: fullscreen-first launch, visible-range horizon streaming up to 48 chunks, and a stronger terrain art pass
- Status: repository foundation, docs, registry scaffolding, textured terrain, streamed chunk rendering, profiling HUD metrics, runtime-adjustable render distance up to 48 chunks, camera-driven chunk loading/unloading, and a Minecraft-style pause/options flow

## Technology stack

- Java 21
- Gradle 8.10.2
- jMonkeyEngine 3.7.0-stable
- Jackson for registry and config loading
- SLF4J + Logback for logging
- JUnit 5 for validation tests

## Quick start

1. Install or expose a Java 21 JDK and set `JAVA_HOME`.
2. From the repository root, use the committed Gradle wrapper.
3. Run:

```bat
gradlew.bat run
```

For this machine, there is also a desktop double-click launcher that uses a hidden launcher flow so the game window can take focus without a foreground command prompt.
The game now boots fullscreen by default and the launcher retries focus activation so the window is brought to the front more reliably on startup.

## Controls

- `WASD`: move the debug fly camera
- `Mouse`: look around
- `Shift`: move faster
- `Esc`: open or close the pause menu
- `Left Click`: use the pause/options menu
- `F10`: quit the game intentionally

## Current render/runtime state

- Terrain now renders through chunk-local meshes rather than one scene geometry per exposed block.
- Grass, dirt, stone, and sand now use 128x128 terrain textures with separate top/side/bottom support in the block registry.
- Grass, dirt, and stone were repainted toward a richer premium stylized-survival look instead of flat pastel debug colors.
- Terrain texture sampling now stays crisper up close while still using mipmaps for distance stability.
- The chunk runtime now supports adjustable render distance up to `48` chunks while rate-limiting background load and mesh work.
- Chunk targets are now driven by the active camera heading and view width instead of a simple full-radius square, so far-behind chunks unload and RAM stays focused on visible horizons.
- The HUD now exposes runtime counts for loaded, rendered, and simulated chunk targets plus render distance, queue depth, and heap use.
- `Esc` opens a centered pause/options menu where render distance can be adjusted live.

## Project principles

- World generation and exploration come first.
- The simulation is written around an authoritative host boundary even in local play.
- Content belongs in registries and data files, not hardcoded gameplay monoliths.
- Every meaningful work session updates the devlog and keeps the game runnable.

## Repository map

- `docs/`: design, roadmap, architecture, networking, registry, and recipe documentation
- `data/`: content and settings registries
- `src/main/java/`: Java source
- `src/test/java/`: validation tests
- `DEVLOG.md`: session-by-session implementation history

## Immediate roadmap

1. `0.001`: spawn blocks
2. `0.002`: add height differences
3. `0.003`: add stone blocks
4. `0.004`: add grass
5. `0.005`: begin terrain layering
6. `0.006`: production chunk runtime foundation and textured terrain readability pass
7. `0.007`: adjustable render distance, pause/options menu, and distant horizons
8. `0.008`: fullscreen-visible startup, visible-range chunk streaming, and premium terrain texture upgrade
