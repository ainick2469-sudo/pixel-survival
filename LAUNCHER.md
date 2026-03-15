# Local Launcher

## Desktop launcher

The local Windows desktop launcher is intended to live at:

- `C:\Users\nickb\OneDrive\Desktop\Pixel Survival.cmd`

That file is a convenience wrapper outside the repo and is not versioned.

## Repo launcher

The versioned launcher entrypoint is:

- `scripts\run_local.cmd`

It assumes the local tools folder exists at:

- `C:\Users\nickb\OneDrive\Desktop\GAMES\pixel-survival-tools`

with:

- Temurin JDK 21 in `jdk-21.0.10+7`
- the repo at `C:\Users\nickb\OneDrive\Desktop\GAMES\pixel-survival`

If those local paths change later, update `scripts\run_local.cmd`.
