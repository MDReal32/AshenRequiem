# Building a pack on Ashen Requiem's mods

The work is done in two layers, so anyone can take the mods and build their own pack on them:

1. **The mods** (`mods/core`, `mods/world`, and Cinematica Studio as its own mod) stand alone. They need only
   NeoForge, Minecraft and Kotlin for Forge; `world` also needs `core`. Everything else is optional and switches
   itself off when it's missing: the KubeJS `AR` binding, and the hooks for FastWorkbench and Configured.
2. **The pack** (`modpack/` scripts, `dev/pack/`) is one pack built on top of the mods. It changes them only in ways
   any other pack can too: data files, KubeJS scripts, commands, configs and resource packs.

The rule for our own work: a mod never depends on the pack. A mod ships its features with sensible defaults, as data
where it can; choices that belong to one pack (its quests, which other mods' recipes it gates, its scenes) live in the
pack.

## What a pack can change

All of these go in a datapack, or in KubeJS (`kubejs/data/...` files, or `ServerEvents.highPriorityData` in a script).
A file at the same path replaces the mod's; a new file adds to it.

- **Recipe gates**: `data/<namespace>/ashen_requiem/recipe_gates/*.json`, as
  `{ "recipes": [...], "until_level": n, "workbench_only": true }`. Both conditions are optional. The world mod's
  own gates are in `data/ashen_requiem_world/ashen_requiem/recipe_gates/`; replacing one with `{ "recipes": [] }`
  turns it off.
- **Recipes, loot tables, tags, worldgen** (biomes, features, the Dead Lands dimension): the usual data paths under
  `data/ashen_requiem_world/`.
- **Radiation**: strengths in the data maps `data/ashen_requiem/data_maps/item/radiation.json` (points per carried
  item) and `data/ashen_requiem/data_maps/block/radiation.json` (points from a block right next to the player), as
  `{ "values": { "<item or block id>": 3 } }`. Tags work as keys too. The world mod's dead wood is its first entry.
- **Cinematics**: scene files under `data/<namespace>/cinematica/scenes/`, played by Cinematica Studio.
- **Story state**: `/ar` commands (`/ar level set <player> <n>` and the rest), or `AR` from KubeJS
  (`AR.setLevel(...)`, `AR.setHushStage(...)`). `LevelChangedEvent` is posted on NeoForge's event bus for mods.
- **Settings**: the mods' config files (`config/ashen-requiem/`; radiation's scale, reach and damage are in
  `core.toml`).
- **Textures and models**: a resource pack, under `assets/ashen_requiem_world/`. Most blocks pick between seven
  textures (`<name>`, `<name>_1` … `<name>_6`), set in the blockstates.
