# HateFrance

![minecraft-version](https://img.shields.io/badge/Minecraft-26.2%20%7C%2026.3-6BAA57?style=for-the-badge&logoColor=white)
[![GitHub downloads](https://img.shields.io/github/downloads/jcnlk/HateFrance/total?label=downloads&color=6BAA57&logo=github&logoColor=white&style=for-the-badge)](https://github.com/jcnlk/HateFrance/releases)

A client-side Fabric addon for NoammAddons that automates Hypixel SkyBlock tasks.
Formerly known as **DancingSquared**.

## Commands

- `/hatefrance` or `/hf` - Opens the configuration menu.
- `/na`, `/noamm`, or `/noammaddons` - Opens the same menu through NoammAddons.
- `/hatefrance autoclicker <add|remove|clear> <left|right>` - Adds or removes the held item or clears the selected whitelist. `/hf ac` is the shorthand.

## Features

<details>
<summary><b>Addon</b></summary>

- **Auto Clicker**
  - Repeats left or right clicks while the configured key or mouse button is held.
- **AutoExperiments**
  - Automatically plays the Experimentation Table and claims its rewards.
- **DancingSquared**
  - Completes the Dance Room sequence in the Rift Mirrorverse.

</details>

## Compatibility

| Minecraft | HateFrance | NoammAddons |
|-----------|------------|-------------|
| 26.2      | 1.3.6      | 1.3.6       |
| 26.3      | 1.3.6      | 1.3.6       |

NoammAddons Loader downloads the latest Cheat build for your Minecraft version. If it installs a newer NoammAddons version, update HateFrance to match.

## Setup

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.2 or 26.3.
2. Add [Fabric API](https://modrinth.com/mod/fabric-api), [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin), and the matching [NoammAddons](https://github.com/Noamm9/NoammAddons) version to your `mods` folder.
    For automatic updates, replace the NoammAddons jar with [NoammAddons Loader 1.0.2](https://github.com/Noamm9/NoammAddons-Loader/releases/tag/1.0.2).
3. Add the matching HateFrance jar.
4. Open the configuration screen with `/hf` or `/na`. [Mod Menu](https://modrinth.com/mod/modmenu) adds a direct entry for it.

> [!IMPORTANT]
> Before using the DancingSquared feature, disable **Play music** in the SkyBlock settings.
> Disable Sodium's FPS limiter and set Sodium's **Reduce FPS when** option to **Minimized**.

[Watch the DancingSquared demonstration on YouTube](https://www.youtube.com/watch?v=5fQQJVvan1g)

## Support

For help, contact `jcnlk` on Discord or open an [issue](https://github.com/jcnlk/HateFrance/issues).

## Credits

The original DancingSquared implementation was created by [Noamm](https://github.com/Noamm9).
HateFrance is released under [CC0 1.0](LICENSE).
