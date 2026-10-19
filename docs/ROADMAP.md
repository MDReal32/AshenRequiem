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
| Target | Minecraft 1.21.1, NeoForge 21.1.x, Kotlin for Forge |

## 2. Module layout

```
mods/
  core/      ashen_requiem_core     framework: progression state, Requiem mark, cinematic engine,
                                    networking, KubeJS bindings, /ar commands, shared shaders
  world/     ashen_requiem_world    Dead Lands dimension, biomes, blocks, mobs, Cradle, Vigil Station,
                                    Gate, veil/reveal system, Quiet Archive, Ash Maze, Sweeper
  hush/      ashen_requiem_hush     Overworld infection: stages, zones, conversion, hushed mobs,
                                    Containment Lanterns, villager changes
  eden/      ashen_requiem_eden     Eden dimension, Root Tools, the Choir boss, finale, healing
modpack/                            KubeJS (TS): quest glue, recipes, End removal, Twilight gating
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
| `requiem` (mark), `chapter`, cinematics already seen, `awakened` | per player | NeoForge data attachment with `copyOnDeath` (can never be lost) |
| Scars (broken veiled blocks) | per chunk | chunk data attachment |

Every state change fires a core event (`HushStageChanged`, `RequiemGranted`, …) that the other modules and KubeJS
listen to.

### D3 — Quests drive the story, code drives the world
FTB Quests rewards run a command or a KubeJS reward (`AR.setHushStage(3)`, `AR.cinematic(...)`). The mods never read
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

### D8 — Twilight gating
Twilight Forest (the NeoForge 1.21.1 build) is required. The portal pool only works if the **Ember of Waking** is thrown
in together with the diamond (we hook TF's portal-activation event, with a KubeJS fallback). The Snow Queen kill
advancement grants the Requiem.

### D9 — The Quiet Archive
- Its own pocket dimension (`ashen_requiem_world:archive`) entered through a veiled door in the Dead Lands. That makes
  it indestructible and lets the geometry be impossible without breaking the surface world.
- Every block in the dimension: hardness -1, immune to explosions. Placing and breaking are cancelled (with exceptions
  for authored interaction points).
- The maze is made of pre-built "rooms" as structure templates. A **beat-map** (JSON: beat timestamps → room
  transforms) drives the moving geometry, on the server, in sync with the client's music position (the client reports
  how far the track has played).
- Loop corridors are seamless teleports between two identical corridor segments.

## 4. Development stages

Each stage gives you something **playable**. We don't start the next stage until the current one meets its "done
when" criteria. Stage numbers follow the order of work, not the order of the story.

### Stage 0 — Identity & design ✅ (this change)
- Rename to Ashen Requiem. Write STORY / CINEMATICS / ROADMAP.

### Stage 1 — Foundation
- New `core` module: registry helpers, networking, data attachments, SavedData, events, `/ar` commands, KubeJS
  bindings (`AR.*`).
- Apply D1: the Dead Lands become their own dimension, first-join teleport, respawn rules.
- **Done when:** a new world puts you in the Dead Lands. `/ar hush set 3`, `/ar requiem grant|revoke`, `/ar tp deadlands|overworld` work, and state survives death and relogging.

### Stage 2 — Cinematic engine v1
- JSON scene loader, timeline, overlay/camera/audio/control tracks, the post-fx track with 3 shaders (blur,
  desaturate, tunnel), server authority, skip rules.
- Ship **C01 Waking** fully, including placeholder sounds.
- **Done when:** C01 plays on first join, can't be skipped the first time and can on repeat (`/ar cinematic play`),
  works on a dedicated server, and holds 60 FPS on mid-range hardware.

### Stage 3 — Dead Lands v1 (world feel)
- **Biomes (first pass):** Ash Plains, Bone Desert, Petrified Forest, Grey Marsh, Shattered Highlands, Ash Ocean (dry
  basin).
- **Blocks:** about 25 (ashen soil/stone sets, petrified wood set, bone sand, grey marsh mud, ruin concrete, rusted
  metal, glass, Signal Relay). Every texture is original and follows one locked palette (§6).
- **Mobs (first pass):** Hushed Wanderer (slow, silent), Ash Crawler (ambush), Hollow Hound (pack, night), Ash Strider
  (tameable mount for the long walk).
- **Structures:** the Cradle (spawn), Waystations, Signal Relays, ruined towns, dead roads (feature).
- Sky, fog, weather, ambient sound, ash weather effect.
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

### Stage 6 — Nether & Twilight (Act II)
- Ember of Waking, D8 portal gating, Twilight lore pages and Hush hooks, C06, C07.
- **C08 Requiem** and the mark: HUD indicator, hand glow, compass pull, ambient shimmer.
- FTB chapters "The Nether", "The Twilight", "The Requiem".
- **Done when:** the Snow Queen kill grants the mark, the mark survives death, `/kill` and relogging, and the Hush
  stages advance at the right story beats.

### Stage 7 — Reveal system (Act III start)
- D4 veil/decoy/scar system, reveal wave (C09), veiled structures in the Dead Lands (bridges, inscriptions, hidden
  rooms, the Archive's door), Requiem-only biome visuals (true sky and fog when marked).
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
All radii are config values. The Nether and the Twilight are never converted.

### 5.2 Three-path rule (kept from the old plan)
Every required key item: **Explore** (structure/puzzle) · **Combat** (boss/elite) · **Trade** (currency, expensive
pity path). Currencies: `ash_memory` (Act I), `requiem_shard` (Act III).

### 5.3 Performance budgets
- Content tick cost ≤ 2 ms/tick when you're in a Hush zone. The Dead Lands have no ticking blocks except authored
  machines.
- Worldgen per chunk: trees 0–3, ruins about 1 per 10 chunks, Waystations about 1 per 60.
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

Already in dev: FTB Quests / Library / Teams / Chunks / Ultimine, KubeJS + ProbeJS, JEI, JourneyMap, Waystones,
Sodium, Lithium, ModernFix, FerriteCore, and others.

To add: **Twilight Forest** (required), **Lootr** (single-player loot per chest in multiplayer).

Wishlist from the old plan, to review in Stage 11 for fit and balance: AE2 *or* Refined Storage (pick one),
Sophisticated Backpacks, Storage Drawers, Farming for Blockheads + Pam's HarvestCraft, Ender Storage (rename it in
lore: "Departed Vaults"), Avaritia (probably clashes with Eden's creation tools, so likely drop it). **Eden Ring** is
Fabric-only and is replaced by our own Eden.

## 8. Risks

| Risk | Mitigation |
|---|---|
| Per-player veil breaks with rendering mods | Stage 7 starts with a spike test on Sodium; overlay fallback if needed. |
| Twilight Forest API changes | Keep all TF hooks in one adapter class with a KubeJS fallback. |
| Cinematics on servers with lag | Server timeouts, client prediction, teleports behind black frames. |
| Scope (this is a big pack) | Every stage ships something playable; cut content per stage, never per system. |
| Music budget | The beat-map system works with any track, so placeholders work until the final music is in. |
