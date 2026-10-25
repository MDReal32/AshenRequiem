# Ashen Requiem — Technical Roadmap

The story is in [STORY.md](STORY.md) and the scenes are in [CINEMATICS.md](CINEMATICS.md). This file says **how we
build it, in what order, and when each part counts as done**.

This replaces `PLAN.md`, `PLAN.MOD.md` and `PLAN.MODS.md`. Their good ideas are carried over here: the three-path rule,
staged spread, veil and scars, performance budgets, and the mod list.

---

## 1. Identity

| | |
|---|---|
| Pack name | **Ashen Requiem** |
| Namespace prefix | `ashen_requiem` |
| Java/Kotlin package | `com.mdreal.ashenrequiem.<module>` |
| Debug command | `/ar …` |
| KubeJS global | `AR` |
| Target | Minecraft 1.21.1, NeoForge 21.1.264, Java 21, Kotlin for Forge 5.10 (kept on 1.21.1 for KubeJS + ProbeJS) |

## 2. Module layout

```
mods/
  cinematica/ cinematica_studio    standalone cutscene engine (camera, posable actor, recorder), usable
                                    by any modpack; no Ashen Requiem dependency
  core/      ashen_requiem_core     framework: progression state, Requiem mark, scene triggers,
                                    networking, KubeJS bindings, /ar commands, shared shaders
  world/     ashen_requiem_world    Dead Lands dimension, biomes, blocks, mobs, Cradle, Vigil Station,
                                    Gate, veil/reveal system, Quiet Archive, Ash Maze, Sweeper
  hush/      ashen_requiem_hush     Overworld infection: stages, zones, conversion, hushed mobs,
                                    Containment Lanterns, villager changes
  eden/      ashen_requiem_eden     Eden dimension, Root Tools, the Choir boss, finale, healing
modpack/                            KubeJS (TS): quest glue, recipes, End removal, level gating, mod unlocks
dev/pack/                           dev instance (FTB Quests chapters live in dev/pack/config/ftbquests)
```
Rule: content modules depend only on `core`, never on each other. If two of them need to talk, it goes through core's
events and state.

## 3. Key architecture decisions

### D1 — The Dead Lands become a separate dimension (breaking change)
Right now the `deadlands` world preset **replaces `minecraft:overworld`**. The story needs a real Overworld to return
to, so:
- `ashen_requiem_world:deadlands` becomes its own `LevelStem` (registered in `data/.../dimension/`), and the world preset
  goes back to a vanilla Overworld plus Nether.
- **First join:** the server sends the player to the Dead Lands Cradle *behind C01's black frames*, then sets their
  respawn point there. Until the Gate is repaired, death respawns them in the Dead Lands.
- Nether portals can't be lit in the Dead Lands (stops a shortcut out).
- The custom create-world screen stays, but now it picks the "Ashen Requiem" preset.

### D2 — Progression state
| State | Scope | Storage |
|---|---|---|
| `hushStage` (0–5), `gateRepaired`, `finaleWon`, key structure positions | world-global | `SavedData` on the Overworld |
| `requiem` (mark), `chapter`, `level` (1–6, D11), cinematics already seen, `awakened` | per player | NeoForge data attachment with `copyOnDeath` (can never be lost) |
| Scars (broken veiled blocks) | per chunk | chunk data attachment |

Every state change fires a core event (`HushStageChanged`, `RequiemGranted`, …) that the other modules and KubeJS
listen to.

### D3 — Quests drive the story, code drives the world
FTB Quests rewards run a command or a KubeJS reward (`AR.setHushStage(server, 3)`, `AR.cinematic(...)`). The mods never read
quest data directly. That keeps quests easy to rearrange, and `/ar` debug commands can reproduce any state.

### D4 — Per-player reveal (veil)
Block states are shared by everyone on a server, so "visible only to marked players" is done like this:
- **Veiled blocks** are real blocks on the server.
  - *Rendering:* a custom baked model asks the client "does the local player have the Requiem?" Without it, the model
    returns no quads (air) or a fake model (ash/stone). Gaining the mark makes the chunks around the player re-render
    (driven by C09's reveal wave).
  - *Collision/selection:* `getCollisionShape`/`getShape` read the `EntityCollisionContext`. Unmarked players get an
    empty shape for "air" veils or a full shape for "decoy" veils.
  - *Breaking a decoy without the mark:* it drops the decoy's normal loot, the block becomes the plain vanilla-like
    decoy for good, and the position is scarred (old PLAN.MOD.md design).
- Works with Sodium because it only uses the baked-model API. Needs testing with Embeddium/Iris later.
- Tags: `#ashen_requiem:veiled/air`, `#ashen_requiem:veiled/decoy`, `#ashen_requiem:veiled_structures`.

### D5 — The only lab (Vigil Station)
- When the world is created, pick an angle at random and a distance in **[3000, 5000]** from the Cradle. Store it in
  SavedData.
- A custom `StructurePlacement` type, `ashen_requiem:fixed_unique`, only matches the chunk at that position. That makes
  exactly one station per world, deterministically, with no searching.
- Check before placing: if the spot is unsuitable (for example a deep ravine), nudge it outward in steps of 16 chunks,
  up to 8 tries.
- `/ar locate station` for debugging. Players find it through the signal, the compass and the light pillar.

### D6 — The Gate
A multiblock with 4 socket blocks plus a controller. It isn't a vanilla portal block. Stepping into it while it's
active triggers C04, and the teleport happens on the cinematic's `teleport` marker. It links both ways (the Overworld
exit, the Wound, is created on the first crossing near world spawn) and is stored in SavedData.

### D7 — Removing the End
Remove strongholds from structure sets, and remove the Eye of Ender and End portal frame recipes and loot (KubeJS
plus a datapack). Add a "Sealed Door" lore item in place of any loot that would have pointed to the End.

### D8 — Otherside gating
Deeper and Darker is required. Its Otherside portal is lit with the Warden's Heart of the Deep; ours also needs the
**Ember of Waking** (hook the portal lighting, with a KubeJS fallback). Outlasting the Choir's chorus in the Ancient
Temple's deepest room grants the Requiem.

### D9 — The Quiet Archive
- Its own pocket dimension (`ashen_requiem_world:archive`) entered through a veiled door in the Dead Lands. That makes
  it indestructible and lets the geometry be impossible without breaking the surface world.
- Every block in the dimension: hardness -1, immune to explosions. Placing and breaking are cancelled (with exceptions
  for authored interaction points).
- The maze is made of pre-built "rooms" as structure templates. A **beat-map** (JSON: beat timestamps → room
  transforms) drives the moving geometry, on the server, in sync with the client's music position (the client reports
  how far the track has played).
- Loop corridors are seamless teleports between two identical corridor segments.

### D10 — The Last Garden (hidden biome)
The last living place of *Home*, kept alive and hidden by the Departed. It's generated with the world, 300–800 blocks
from the Cradle on the way to Vigil Station, so every player crosses it in Act I without noticing. A marked player
sees it appear during C09 (per player, through the D4 veil). Goal: *"that wasn't here... how did I not see it?"*
- **Placement:** one per world. A wrapper biome source (`ashen_requiem_world:veiled_region`) puts
  `deadlands/last_garden` in an irregular, wavy-edged blob (radius 112) around a point chosen from the seed, on the line from
  the Cradle to the station (D5). Everything else falls through to the normal multi-noise source.
  - Biome sources never see the seed, so the bearing is hashed from far-away climate samples, which are built from it.
  - The blob must fall entirely on its `disguise` biomes (Ash Plains or Petrified Forest; both have the garden's ground
    blocks). The search swings ±180° around the bearing, then shrinks the blob to 2/3, then looks out to 1600 blocks.
    Over 100 seeds it never needed the last-resort spot.
  - Until Stage 7, `last_garden.json` is an exact copy of Ash Plains (only F3 can tell).
- **Terrain is not different.** The density functions don't know about the garden, so hills and valleys match the
  neighbours exactly. Only what sits *on* the ground differs, so collision never has to lie about solid ground.
- **What the unmarked player gets** (every channel that could leak it):

  | Channel | Hidden by |
  |---|---|
  | Ground blocks (living grass, soil) | Decoy veil: renders, sounds, drops and maps as the neighbour biome's block |
  | Trees, flowers, ruins, benches | Air veil: no model, empty collision and outline, no particles |
  | Shadows | Veiled blocks let skylight through and block no light (`propagatesSkylightDown`, light block 0) |
  | Light | No light-emitting veiled blocks. Garden glow is emissive rendering that only starts after the reveal |
  | Weather / ash particles | Our ash weather uses its own heightmap that skips veiled blocks |
  | Water | No fluids in the veil. The garden ponds are dry hollows that fill (server-side) on the reveal |
  | Sky, fog, grass colour, ambience, F3 biome name | The server sends the disguise biome id in chunk data to unmarked players |
  | Vanilla map items | Veiled blocks report the decoy's map colour |
  | `/locate biome` | The garden is left out of locate for non-ops |
  | Mob spawning and pathing | Mobs treat air veils as air. No spawns on garden-only blocks |
- **Breadcrumbs that only make sense afterwards:** a stone path that runs into empty plain and stops, a lone fence
  gate, a bench facing nothing, a terminal line in the Cradle about "the garden". They're visible to everyone and
  easy to shrug off in Act I.
- **On reveal (C09):** the wave sweeps out, chunks around the player re-render, the true biome is re-sent, the ponds
  fill, and the first green the player has seen in the Dead Lands rises out of ground they've walked a dozen times.

### D11 — Dead Lands crafting (the first hour)
The story side is STORY.md Act I, "The first hour". The Dead Lands' own items follow the hard → normal rule (§7.2): hard
during level 1, vanilla again once level 1's quests are done.
- **Recipe gates** (`core`): the mods do this, not KubeJS. A gate is a data file
  (`data/<namespace>/ashen_requiem/recipe_gates/*.json`) that lists recipe ids and says when they're blocked:
  `until_level` (blocked while the player's `level` is lower) and `workbench_only` (blocked in the 2×2 inventory
  grid). The check sits where the server looks up a crafting recipe for a player: vanilla's crafting grid and
  FastWorkbench's, which replaces it in our pack. Machines and autocrafters have no player, so gates don't apply to
  them. The quest that ends a level runs `/ar level set <player> <n>` (or `AR.setLevel` from KubeJS). A pack replaces or adds
  gates with its own files (see PACKS.md).
- **Gated in level 1** (`world`'s gate files, all `until_level: 2`): every vanilla log → planks recipe and the
  vanilla crafting table; vanilla sticks and our dead planks are `workbench_only`.
- **Per-player level** (`core`): `level` in the player's progress, 1–6. `/ar level get|set`, `AR.getLevel` /
  `AR.setLevel`, and a `LevelChangedEvent`.
- **Dead trees never grow back:** dead leaves drop no saplings (already true), so cut forests stay cut.
- **New items and blocks** (`world`):
  - `scrap`, from `rubble`: half-high heaps scattered over the Dead Lands, with a few always around the Cradle.
    Breaking one drops scrap (70%) and a stick (35%); silk touch keeps the heap. Later also in ruins and Waystation
    loot.
  - `scrap_knife` (scrap + stick, the only inventory recipe at the start). Using it on a dead log or dead wood
    scrapes it into a `scraped_log` / `scraped_wood`, like an axe strips logs. It takes durability.
  - `scraped_log` / `scraped_wood`: not radiated. Only these turn into dead planks, and only at a workbench.
  - `broken_workbench`, placed beside the Cradle once per world. Using 2 scraped logs and 2 scrap on it repairs it
    into a `workbench` (the first quest's goal).
  - `workbench`: the Dead Lands' crafting table, so no vanilla crafting table shows up there. Recipe: 2 dead planks
    over 2 scrap, `workbench_only` for good (it's the hard recipe).
- **Radiated:** dead logs and dead wood (items and blocks) carry a little radiation (D12). Scraped wood and planks
  don't.

### D12 — Radiation
A per-player value with its own HUD bar. It lives in `core`, so every module and other mods can feed it.
- **Scale:** 10 units shown in the HUD, each worth 100 points (1000 points in all). Every number here is a config
  value, to balance in playtests.
- **Damage per second,** by the units the player has. Between the points below it grows in a straight line (6 units
  hurts 2.5 hearts a second):

  | Units | Below 4 | 4 | 5 | 7 | 8 | 10 |
  |---|---|---|---|---|---|---|
  | Damage | none | ½ heart | 1 heart | 4 hearts | 8 hearts | instant death |

  Its own damage type (`ashen_requiem:radiation`), which armour doesn't block, with its own death message. Cures and
  protective gear are left for later (open).
- **A live reading, not a build-up.** The bar shows what the player is exposed to right now: what they carry and
  where they stand. It's recalculated once a second, so dropping the items or walking away lowers it at once. Nothing
  builds up over time.
  - **Carried items:** every radiated item in the inventory adds its strength, per item: the more you carry, the
    higher it goes. Held items count double.
  - **Nearby blocks and dropped items:** each radiated one within 6 blocks adds its strength, falling off with
    distance. The scan skips chunk sections that hold no radiated blocks, so it stays cheap.
  - Strengths (points per item or block) come from NeoForge data maps (`ashen_requiem:radiation`, one for items and
    one for blocks), so modules, KubeJS and datapacks add entries without code.
- **First numbers to try:** a carried dead log is 3 points and a dead log block nearby up to 1. A stack of 64 raw logs
  reads about 2 units (no damage), three stacks about 6 (it hurts). Standing in a dead forest reads well under 1.
- **HUD:** a row of 10 icons above the hunger bar (air bubbles move up a row), filling in tenths. A NeoForge GUI layer,
  so cutscenes hide it with the rest of the HUD. The server owns the value and syncs it to the client when it changes.
- **Debug:** `/ar radiation [player]` prints the reading and its biggest sources.
- **Other mods' radiation:** radioactive items from tech mods join the same data maps: Mekanism's uranium (ore, raw,
  ingot), yellowcake, plutonium, polonium and nuclear waste, and Powah's uraninite. Mekanism has its own radiation
  system; turn it off in its config if it can be, so there is only one bar. Its radioactive chemicals in tanks need a
  small adapter that reads Mekanism's own radioactivity values. Its hazmat suit should cut the reading.

## 4. Development stages

Each stage gives you something **playable**. We don't start the next stage until the current one meets its "done
when" criteria. Stage numbers follow the order of work, not the order of the story.

### Stage 0 — Identity & design ✅
- Rename to Ashen Requiem. Write STORY / CINEMATICS / ROADMAP.

### Stage 1 — Foundation ✅
- New `core` module: data attachments, SavedData, networking (progress sync), events, `/ar` commands, KubeJS
  bindings (`AR.*`).
- Apply D1: the Dead Lands become their own dimension, first-join teleport, respawn rules, no Nether portals there.
- The `ashen_requiem` world preset has no End, so D7 is already half done (strongholds and recipes are left).
- **Done when:** a new world puts you in the Dead Lands. `/ar hush set 3`, `/ar requiem grant|revoke`, `/ar tp <dimension>` work, and state survives death and relogging.
- Debug commands: `/ar state [targets]`, `/ar hush get|set <0-5>`, `/ar gate <true|false>`,
  `/ar requiem grant|revoke [targets]`, `/ar reset [targets]`, `/ar tp <dimension> [targets]`.
- Test loop: `./gradlew :dev:runServer` (headless, RCON on 25575) plus `./gradlew :dev:runClient -PquickPlay=localhost`.

### Stage 2 — Cinematic engine v1
- The engine is **Cinematica Studio** (`mods/cinematica`), see [CINEMATICS.md](CINEMATICS.md) §1.0. v0.1 has the
  timeline, camera/actor/fade tracks, server-driven playback that loads chunks, the in-game recorder and
  `/cinematica`. Still to do here: overlay text and eyelids, post-fx, audio, skip rules, and wiring C01 to first join.
- JSON scene loader, timeline, overlay/camera/audio/control tracks, the post-fx track with 3 shaders (blur,
  desaturate, tunnel), server authority, skip rules.
- Ship **C01 Waking** fully, including placeholder sounds.
- **Done when:** C01 plays on first join, can't be skipped the first time and can on repeat (`/ar cinematic play`),
  works on a dedicated server, and holds 60 FPS on mid-range hardware.

### Stage 3 — Dead Lands v1 (world feel)
- **Worldgen decisions:** vanilla-like terrain mix, fully data-driven (no custom chunk generator). No seas: low
  ground is dry basin (`default_fluid` air, `sea_level` -64 so no lava either), and murky water only appears as
  Grey Marsh pools and rare springs. Underground is almost solid: no carvers, no aquifers, no vanilla ore veins; ores
  come from our own features. The floor is vanilla bedrock (`ashen_bedrock` stays registered so older test
  worlds keep their floor).
- **Biomes (first pass):** Ash Plains, Bone Desert, Petrified Forest, Grey Marsh, Shattered Highlands, Ash Ocean (dry
  basin), plus the hidden Last Garden (D10).
  - Share over 100 seeds within 6000 blocks: Ash Ocean 30%, Ash Plains 28%, Petrified Forest 12%, Shattered
    Highlands 11%, Bone Desert 10%, Grey Marsh 8%. Ash Ocean is vanilla's ocean area, so it only shrinks if the
    terrain changes.
  - Measure with `/ar debug biomes <dimension> [radius] [step] [seeds]`. It asks the biome source directly
    (no chunks are generated), so 100 seeds take seconds.
- **Blocks:** about 25 (ashen soil/stone sets, petrified wood set, bone sand, grey marsh mud, ruin concrete, rusted
  metal, glass, Signal Relay). Every texture is original and follows one locked palette (§6).
- **Mobs (first pass):** Hushed Wanderer (slow, silent), Ash Crawler (ambush), Hollow Hound (pack, night), Ash Strider
  (tameable mount for the long walk).
- **Structures:** the Cradle (spawn), Waystations, Signal Relays, ruined towns, dead roads (feature).
- Sky, fog, weather, ambient sound, ash weather effect.
- The first hour (D11): no vanilla wood crafting, the scrap knife, scraped wood, the Cradle's broken workbench, the
  first quest.
- Radiation v1 (D12): the live reading, HUD bar, damage, data maps, dead wood as the first source.
- **Done when:** you can survive the first night with only Dead Lands resources, and none of the ~10 people you
  playtest it with can name a vanilla block they saw.

### Stage 4 — Vigil Station & the Gate (end of Act I)
- Implement D5 (unique placement) and D6 (the Gate). Station interior with 3 wings, terminals with lore, power-routing
  puzzle, The Custodian mini-boss, VESPER's fabricator trades (`ash_memory`).
- C02, C03, C04, C05. FTB Quests chapter "Act I — Waking".
- **Done when:** a fresh world can be played from waking to the Overworld in 2–4 hours, all three stabilizer routes
  work, and the station is always 3000–5000 blocks away (checked over 50 seeds by an automated test).

### Stage 5 — The Hush (Overworld infection)
- Stage system 0–5. Conversion runs **when a chunk loads and in throttled batches**, never by scanning every tick. Each
  chunk remembers the stage it was last converted to (`hushEpoch`). When it loads with an older epoch, it catches up
  in batches of N blocks per tick, spread across the server.
- Zones grow outward from the Wound in rings. The radius per stage is configurable. Noise makes the edges look natural.
- Hushed variants (animals, zombies, villagers), Containment Lanterns (protect a radius, cost fuel), ash rain event,
  desaturation and silence effects.
- D7 End removal. FTB chapter "Act II — Homecoming".
- **Done when:** stages 1–5 can be set by command and each one looks clearly different, tick time stays ≤ 2 ms under
  load, and a lantern-protected base is never converted.

### Stage 6 — Nether & Otherside (Act II)
- Ember of Waking, D8 portal gating, Otherside lore pages and Hush hooks, C06, C07.
- **C08 Requiem** and the mark: HUD indicator, hand glow, compass pull, ambient shimmer.
- FTB chapters "The Nether", "The Otherside", "The Requiem".
- **Done when:** the Ancient Temple encounter grants the mark, the mark survives death, `/kill` and relogging, and the Hush
  stages advance at the right story beats.

### Stage 7 — Reveal system (Act III start)
- D4 veil/decoy/scar system, reveal wave (C09), veiled structures in the Dead Lands (bridges, inscriptions, hidden
  rooms, the Archive's door), Requiem-only biome visuals (true sky and fog when marked).
- The Last Garden's veil and reveal (D10). Its placement and disguised generation land earlier, in Stage 3, so that
  every Act I playtest world already contains it.
- **Done when:** on a 2-player server, one marked and one unmarked player stand in the same spot and see, collide with,
  and loot different things, with no desync.

### Stage 8 — The Quiet Archive (Act III)
- D9 pocket dimension, brutalist building kit (concrete, office, filing, lamps), the Sweeper NPC (dialogue system with
  subtitles), the Tuning Radio item, the Ash Maze with beat-map, loop corridors, C10–C13.
- **Done when:** the maze can be finished in 5–8 minutes, stopping the radio always makes it loop, the Archive can't
  be broken in any way (TNT, mining, pistons, other mods' tools), and the Eden Seed is handed over.

### Stage 9 — Eden (Act IV)
- Eden dimension (floating islands, custom sky, upward waterfalls), C14 (dying-world return), the Seed defense event,
  C15, C16.
- Root Tools tiers: **Conjure** (craft/summon from a GUI), **Wings** (flight), **Brush** (shape/fill/instant build),
  **Bloom** (cure). Root Tools are disabled in the Archive and in the Choir arena's no-cheese zone.
- **Done when:** every tier unlocks through quests, and the tools are fun but have limits (cooldowns, area caps).

### Stage 10 — The Choir & finale
- Multi-phase boss (an ash colossus, summoned silhouettes, phases that change the battlefield), C17, C18, healing wave,
  credits flyover, epilogue world changes (Sweeper gone, grass at the Cradle).
- **Done when:** the fight can be won with Root Tools and is hard without using them creatively, and after the
  ending, the Overworld heals over time without lag.

### Stage 11 — Polish & release
- Original music (the Requiem theme, the Ash Maze track split into stems, an ambient track per realm), sound design
  pass, localization (en_us, az_az, tr_tr), config and accessibility options, multiplayer pass, performance
  profiling with Spark, CurseForge pages and screenshots, trailer made with the cinematic engine itself.

## 5. Systems reference

### 5.1 Hush stages (technical)
| Stage | Ring radius from the Wound | Conversion | Spawns | Global effects |
|---|---|---|---|---|
| 1 | 48 | grass → ash grass, leaves grey | none | none |
| 2 | 256 | + soil, crops die, water turns grey | hushed animals | none |
| 3 | 768 | + trees petrify, stone pales | hushed mobs at night | rare ash rain |
| 4 | 1536 | + villages hushed | hushed villagers, more at night | sky dims 10% |
| 5 | 3072 + patches everywhere | full | Choir spawns | constant ash, sky dims 30% |
All radii are config values. The Nether and the Otherside are never converted.

### 5.2 Three-path rule (kept from the old plan)
Every required key item: **Explore** (structure/puzzle) · **Combat** (boss/elite) · **Trade** (currency, expensive
pity path). Currencies: `ash_memory` (Act I), `requiem_shard` (Act III).

### 5.3 Performance budgets
- Content tick cost ≤ 2 ms/tick when you're in a Hush zone. The Dead Lands have no ticking blocks except authored
  machines.
- Worldgen per chunk: trees 0–4 (most in the Petrified Forest), ruins about 1 per 10 chunks, Waystations about 1 per 60.
- Light-emitting blocks are capped at level 7 in the Dead Lands. Particles are client-side and scale with distance.
- Shaders: each scene uses at most 2 post-fx passes at once.

### 5.4 Multiplayer
- Hush stage and the Gate are global. Chapters, the mark and cinematics are per player.
- The Hush stage moves forward when the **first** player reaches a trigger. That's a choice: the team shares the
  consequences.
- Archive maze: one instance per player or team (a separate copy of the maze region, managed inside the pocket dimension).

## 6. Art & audio direction

- **Dead Lands palette:** ash greys (#2B2B2E → #BDBAB2), bone white (#E6E1D3), rust (#6E4A35), sick-green accent for
  the Hush (#8FA37A), Requiem gold (#D9B86C) only for things that belong to the mark.
- **Overworld:** vanilla, so the contrast is the point. **Eden:** saturated, warm, pastel light.
- Textures: 16×16, keeping vanilla's level of detail. Hand-made, no AI-generated textures in the release.
- Audio: every Dead Lands sound is original or edited from CC0 sources (credited). Music: commissioned or original.
  We need 6 tracks minimum (see Stage 11).

## 7. Third-party mods

Mods are installed and updated through the CurseForge app (`dev/pack/mods`). Every mod below was checked for a
NeoForge 1.21.1 build on 2026-10-25. Lootr gives each player their own chest loot in multiplayer and needs no lock.

### 7.1 In the pack now

| Role | Mods |
|---|---|
| Our base | Kotlin for Forge, KubeJS + Rhino, KubeJS Create, ProbeJS (client only) |
| Story and progression | FTB Quests / Library / Teams / XMod Compat |
| Content | Create, Deeper and Darker, Lootr, Waystones, Sophisticated Backpacks, Corpse, Falling Trees, Connected Glass, Double Doors, Dummmmmmy, Cosmetic Armor |
| Maps | JourneyMap + JMI, FTB Chunks |
| Info | JEI, Just Enough Resources / Professions / Breeding, Better Advanced Tooltips |
| Convenience | FTB Ultimine, Mouse Tweaks, Controlling, Searchables, Configured, Dark Mode Everywhere, Fast Furnace / Workbench / Suite, Clumps, AI Improvements, Nether Portal Fix |
| Performance | Sodium (+ Dynamic Lights), Lithium, ModernFix, FerriteCore, Fast Async World Save, GPU Memory Leak Fix, BadOptimizations |
| Libraries | Architectury, Balm, Collective, Cupboard, Fusion, Moonlight, PandaLib, Placebo, SuperMartijn642 Core Lib, mezz_config |

Level 3 is Deeper and Darker's Otherside (STORY.md §9). Twilight Forest was removed on 2026-10-24.

### 7.2 Tech and magic unlocks

Every tech and magic mod is usable from the start. What changes as the story moves on is how its items are made:

- **Hard recipe, always there.** KubeJS replaces each item's default recipe with one that needs something earned the
  hard way: a boss drop, a rare find, a long build. Nothing is locked; you can have it early if you put the work in.
- **Normal recipe, added by quests.** Finishing the quests that end a level adds the normal recipe for that set
  ("just crafted or grown"). It doesn't need the earned item, so from then on the player can make as many as they
  like. The hard recipe stays too.

| Set | Tech | Magic | Normal recipe when this level's quests are done |
|---|---|---|---|
| A | Create | — | 2 Overworld + Nether |
| B | Immersive Engineering, Create Crafts & Additions | Ars Nouveau | 3 Otherside |
| C | — | Nature's Aura, Iron's Spells 'n Spellbooks | 4 Advanced Dead Lands |
| D | Mekanism, AE2, Powah | Occultism | 5 Dying Overworld |

- What each hard recipe asks for is picked per item, from things the player can earn by the level where they'd first
  want it (a set A item asks for something from the Dead Lands or the Overworld, not from Eden).
- Our own Dead Lands items follow the same rule: hard in the Dead Lands, normal once level 1's quests are done.
- Eden adds no set. Its reward is our own Root Tools.
- The quest that ends a level raises the player's `level` (D2), and core's recipe gates (D11) open the normal recipes
  for that player. JEI shows both recipes, and the normal one says what unlocks it.
- **To solve:** machine recipes (Create, Mekanism) have no player, so their normal recipe needs a
  catalyst the quest gives instead.

**Apotheosis runs the whole pack.** It's in from level 1 and grows with the story through its five world tiers. Each
tier's unlock criteria are replaced with our level-end quest, so the player moves up one tier per level:

| Level | 1 Dead Lands | 2 Overworld | 3 Otherside | 4 Advanced Dead Lands | 5 Dying Overworld, 6 Eden |
|---|---|---|---|---|---|
| World tier | Haven | Frontier | Ascent | Summit | Pinnacle |

Summit normally needs the Wither; our quest replaces that. Check the exact criteria format once Apotheosis is in the
pack.

### 7.3 Candidates

| Mod | Status |
|---|---|
| Refined Storage | Instead of AE2 if preferred; only one of the two. |
| Industrial Foregoing, Oritech, Modern Industrialization | Extra tech, could join set B or D. |
| Mahou Tsukai, Forbidden & Arcanus | Extra magic, could join set B or C. |
| Mystical Agriculture | Fits "crafted or grown": its seed tiers could carry the hard recipes. CurseForge only; 1.21.1 build unchecked. |
| Storage Drawers | CurseForge only; its 1.21.1 build is unchecked. |
| Ender Storage | Maybe later, renamed in lore to "Departed Vaults". |
| Pam's HarvestCraft, Farming for Blockheads | Probably not: crafting busywork against pillar 7. |
| Avaritia | No: clashes with Eden's creation tools. |
| Botania | No 1.21.1 build; Nature's Aura took its place in set C. |

**Eden Ring** is Fabric-only and is replaced by our own Eden.

### 7.4 Open

- Waystones break Act I's long walk. Block them in the Dead Lands (no generation, no use) and allow them from the
  Overworld on?
- FTB Chunks and JourneyMap both have a minimap. Keep one. The map is also where the veil could leak (D4).
- Create shouldn't be craftable in the Dead Lands, so the "no vanilla" pillar holds there.
- Radiation cures and protective gear: our own items, other mods' (Mekanism's hazmat suit), or both?
- Do items inside backpacks (Sophisticated Backpacks) count toward radiation?

## 8. Risks

| Risk | Mitigation |
|---|---|
| Per-player veil breaks with rendering mods | Stage 7 starts with a spike test on Sodium; overlay fallback if needed. |
| Deeper and Darker changes | Keep all its hooks in one adapter class with a KubeJS fallback. |
| Cinematics on servers with lag | Server timeouts, client prediction, teleports behind black frames. |
| Scope (this is a big pack) | Every stage ships something playable; cut content per stage, never per system. |
| Music budget | The beat-map system works with any track, so placeholders work until the final music is in. |
