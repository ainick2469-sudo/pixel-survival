# Local Launcher

## Desktop launcher

The local Windows desktop launcher is intended to live at:

- `C:\Users\nickb\OneDrive\Desktop\Pixel Survival.cmd`

That file is a convenience wrapper outside the repo and is not versioned. It now forwards immediately into a hidden VBScript launcher so the game window can take focus without leaving a visible command prompt in front.
The desktop-facing launcher now boots the game fullscreen by default and retries focus activation a few times after launch so the game window is more likely to end up in front.

## Repo launchers

The versioned debug launcher entrypoint is:

- `scripts\run_local.cmd`

The versioned desktop-facing hidden launcher entrypoint is:

- `scripts\launch_desktop.vbs`

It assumes the local tools folder exists at:

- `C:\Users\nickb\OneDrive\Desktop\GAMES\pixel-survival-tools`

with:

- Temurin JDK 21 in `jdk-21.0.10+7`
- the repo at `C:\Users\nickb\OneDrive\Desktop\GAMES\pixel-survival`

The hidden launcher builds a stable desktop fat jar at `build\libs\pixel-survival-desktop.jar` and then starts it with `javaw.exe`.
It also attempts to activate the `Pixel Survival` window repeatedly after startup so the fullscreen window is foregrounded more reliably on Windows.

If those local paths change later, update `scripts\run_local.cmd` and `scripts\launch_desktop.vbs`.
