
# Boundless Horizons: Dead Lands — Mod Plan

## Architecture

### Core (`boundless_horizons_core`)
- **Single source of truth** for all Boundless Horizons modules.
- Exposes **utilities & APIs**:
    - Registry helpers (blocks, items, worldgen, events).
    - Config / datagen / networking helpers (future).
- **No content**: only framework.

### World (`boundless_horizons_world`)
- Registers its **own content** (blocks, items, biomes, structures).
- Uses only Core utilities.
- Ships worldgen JSON (datapack-style) to define new dimension + preset.

---

## Dead Lands Dimension

### World Preset
- **ID:** `boundless_horizons_world:deadlands`
- **Display name:** *Dead Lands*
- **Placement:** alongside vanilla presets (not replacing overworld).
- **Includes dimension:** `boundless_horizons_world:deadlands_world`

### Dimension
- **ID:** `boundless_horizons_world:deadlands_world`
- **Dimension type:** `boundless_horizons_world:deadlands_type`
- **Generator:** `minecraft:noise`
- **Noise settings:** `boundless_horizons_world:deadlands_noisy`
- **Biome source:** custom biomes (to be designed).
- **Structures:** allowed, but **custom only** (no vanilla).

### Dimension Type (`deadlands_type`)
- `skylight: true`
- `natural: false`
- `has_ceiling: false`
- `coordinate_scale: 1`
- `min_y: 0`
- `height: 255`
- `logical_height: 255`
- `bed_respawn: true`
- `anchor_respawn: false`
- `fixed_time: null` (normal day/night)
- **Effects:** TBD (custom sky/fog later).
- **Flags (ultrawarm, piglin_safe, raids):** TBD (depends on lore).

### Noise Settings (`deadlands_noisy`)
- `min_y: 0`
- `height: 255`
- `noise_size_vertical`: overworld-like
- `default_block`: TBD (custom block)
- `default_fluid`: TBD (custom fluid or water)
- `caves / aquifers`: TBD
- `ore veins`: TBD
- Terrain: *vanilla-like but different*

---

## Reveal & Progression System

### Concept
- Advanced biomes **exist** in the worldgen, but are **masked** until unlock.
- **No borders or damage** — players can walk through, but everything appears ordinary.
- After progression unlock, biomes/blocks/structures/entities reveal themselves:  
  *“wait, this was here all along?!”*

### Pre-Unlock Behavior
- **Hidden biomes** render as a **mask biome profile** (mundane sky/fog).
- **Hidden blocks**:
    - Look & act like decoys (stone, dirt, etc.).
    - Drop decoy loot when broken.
- **Hidden structures**: appear as ruins/plain variants.
- **Hidden entities**: disguised visuals/loot.
- **Maps/locators**: treat hidden content as absent.

### Post-Unlock Behavior
- Client & server flip per-player state:
    - True biome visuals/sky/fog restored.
    - True blocks/structures/entities revealed.
    - Loot tables and AI behave normally.
- One-shot **reveal effect** (particles, sound, fog shift).

---

## Veil & Scars

### Veil Block
- Common, ubiquitous.
- Pre-unlock: invisible/air to the player.
- Post-unlock: visible, collidable, mineable like a normal block.

### Scars System
- Breaking a decoy pre-unlock:
    - Drops decoy item.
    - Replaces the real block with a true decoy.
    - Position marked **scarred** in persistent chunk data.
- After unlock:
    - Scarred positions **stay mundane forever**.
    - Placing the original block back **does not restore** the hidden version.

### Scar persistence
- Stored per-chunk in NBT (`boundless_horizons:scars/deadlands`).
- Applies to block breaks, explosions, piston pushes, etc.
- Admin override (optional via KubeJS/commands).

---

## Control Surfaces

### Datapack Tags
- `#bh:hidden_biomes/deadlands`
- `#bh:hidden_blocks`
- `#bh:hidden_blocks_interact`
- `#bh:hidden_structures`
- `#bh:hidden_entities`

### Config (server/client)
- `mask.profile` — biome look-alike profile ID.
- `loot.redirect` — `decoy|empty|vanilla`.
- `scar.mode` — `per_block|per_structure`.
- `scar.max_per_chunk` — cap for scar records.
- `visual.subtle_cues` — enable/disable anomalies.

### KubeJS API
- `BH.setUnlocked(player, "deadlands", true/false)`
- `BH.isUnlocked(player, "deadlands")`
- `BH.onReveal("deadlands", (player)=>{ ... })`
- `BH.isScar(world, pos, "deadlands") -> bool`
- `BH.clearScar(world, pos, "deadlands")`
- `BH.clearScarsInBox(world, box, "deadlands")`

---

## TODOs
- Design **custom biomes** for Dead Lands.
- Define **custom structures** + structure sets.
- Pick **default block** and **default fluid** for terrain.
- Decide **caves/aquifers/veins** toggles.
- Choose **sky/fog/effects** for `deadlands_type`.
- Provide **preset icon asset**.
- Finalize **unlock flag id** and lore-based item/ritual.
