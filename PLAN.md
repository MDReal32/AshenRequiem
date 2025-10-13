## 1) Non‑Negotiable Pillars

* Fresh universe — no vanilla mobs/structures in BH layers.
* Distinct **verbs per layer** to avoid "find‑find‑find" fatigue.
* Low content, high reuse; staged systems over continuous sims.
* No softlocks: every mandatory key supports 3 routes (Explore / Combat / Trade).
* Performance first: sparse worldgen, minimal tickers/light sources.

---

## 2) Lore Arc (Tight)

1. **Deadlands** — boot failure shard. You escape by repairing Gate‑01.
2. **Overworld (Reconstruction Layer)** — your escape re‑links quarantine → corruption leaks upstream.
3. **Fey Realm** — you learn system logic and obtain a **Revelation Lens** to see hidden structures back in Deadlands.
4. **Advanced Deadlands** — re‑enter quarantine to retrieve **Root Fragments**; craft a **Root Patch** to reduce global infection.
5. **Twilight Bastion** — single bespoke site where you perform the **Seal Ritual** to close the breach.
6. **Ascended Plane** — reboot succeeds; unlock **Root Access** (creative‑like toolkit).

---

## 3) Layer Map — Verbs, Outcomes, Gates

| Layer              | Verb          | Outcome (New Capability/State)                      | Gate to Next                                     |
| ------------------ | ------------- | --------------------------------------------------- | ------------------------------------------------ |
| Deadlands          | **Repair**    | Gate‑01 online                                      | 4× **Portal Stabilizers** (Explore/Combat/Trade) |
| Overworld          | **Stabilize** | Corruption contained to zones                       | 3× **Containment Beacons** (or trade sink)       |
| Fey Realm          | **Decode**    | **Revelation Lens** (perception overlay + phasing)  | Lens calibration quest                           |
| Advanced Deadlands | **Cleanse**   | **Root Patch (~80% fix)**; reduces global infection | 3× **Root Fragments**                            |
| Twilight Bastion   | **Seal**      | **Seal Core** closes breach                         | Ritual assembly (multi‑step interaction)         |
| Ascended Plane     | **Create**    | **Root Access Tools** (tiered creative‑like)        | Complete prior chapters                          |

**Three‑Path Rule (for every key):**

* **Explore:** structure loot/puzzles.
* **Combat:** mini‑boss/elite event drop.
* **Trade:** currency sink (pity path) — early `raw_memory`, later `root_shard`.

---

## 4) System: Overworld Corruption (Staged, Cheap, Readable)

* Global scalar: `worldCorruptionLevel ∈ [0..1]` stored in server persistent data.
* Thresholds & one‑shot effects:

  * **0.10**: visual hints near arrival (ambient flicker, particles).
  * **0.35**: small **Deadlands patches** (tile conversion batches, not per‑tick).
  * **0.60**: **Cross‑contamination Zones** with altered spawns/structures.
  * **0.80**: critical events (quest‑driven), opening Twilight path.
* No retrogen or continuous scans. Triggers fire once on crossing thresholds.

---

## 5) Core Content Budgets (v0.1 → v1.0)

### v0.1 MVP (ship fast, fun, stable)

**Blocks (≤12):** `nullsoil`, `wraithgrass_block`, `corruptstone`, `memory_crust` (emissive ≤7), `hollowwood_log+planks`, `data_mire`, `gate_frame`, `gate_socket`.

**Items (≤10):** `raw_memory`, `null_resin`, `hollow_circuit`, `pylon_core`, `portal_stabilizer`, `field_tuner`, `revelation_lens (stub)`, `root_fragment (stub)`.

**Mobs (3):** Data Leech (mire), Watcher Fragment (flying), Residual Husk (slow pressure). Simple AI, capped rates.

**Structures (4):** Origin Pod (spawn), Crash Node (surface), System Cache (underground), Gate‑01 Site (fixed, 4 sockets).

### v0.2

* Overworld corruption stages (0.10/0.35/0.60), **Containment Beacon**, 1 hybrid biome.

### v0.3

* **Fey Realm slice** + **Revelation Lens** overlay/phase (tags‑driven: `#bh:hidden_reveal`, `#bh:phaseable`).

### v0.4

* **Advanced Deadlands** (Kernel Vaults + Prime variants) + **Root Patch** (reduces `worldCorruptionLevel`).

### v0.5

* **Twilight Bastion** + Seal ritual (multi‑step interaction, small bill of materials from all layers).

### v1.0

* **Ascended Plane** + **Root Console** (tiered creative‑like).

---

## 6) Deadlands MVP — Gate‑01 Loop

* Gate has **4 sockets**; insert **Portal Stabilizer** into each.
* **Stabilizer recipe:** `pylon_core` + 2× `hollow_circuit` + 4× `null_resin`.
* **pylon_core sources:**

  1. **System Cache – Core Room** (rare variant chest).
  2. **Watcher Swarm** surface event (once per world seed).
  3. **Crash Node (deep)** guaranteed drop (rare structure).
  4. **Trade** 64× `raw_memory` at gate console (pity path).
* On 4/4: activate gate → set `worldCorruptionLevel = 0.10` and unlock Overworld.

---

## 7) Fey Realm — Revelation Lens

* Grants **perception overlay** (client) that highlights `#bh:hidden_reveal` blocks/structures in Deadlands.
* Right‑click **phase** on `#bh:phaseable` for 10s (server‑validated cooldown). Opens hidden passages/loot.
* Calibrated via short puzzle quest; low performance cost.

---

## 8) Advanced Deadlands — Root Patch Return

* Visuals: darker palette, emissive veins, light flicker cycles.
* New family: **Prime** variants (Data Parasite, Watcher Prime, Residual Warden).
* Objective: collect **3 Root Fragments** via Explore/Combat/Trade.
* Craft **Root Patch** → `worldCorruptionLevel = max(0.2, current-0.5)`; locks spread (lore: ~80% fixed).

---

## 9) Twilight Bastion — Seal Ritual (One Location)

* World‑unique structure (placed once per world). Autopinned on discovery.
* **Ritual:** 3 timed interactions (lens timing, beacon sync, mob pressure). No fetch bloat.
* Reward: **Seal Core** → closes breach, sets `worldCorruptionLevel = 0`, opens Ascended Plane.

---

## 10) Ascended Plane — Root Access (Creative‑like)

* Calm, bright aesthetic; no hostile spawns.
* **Root Console** tiers:

  1. **Summon/Craft** anything (GUI; replaces `/give`).
  2. **Flight toggle**.
  3. **World Brush / Instabuild** tools.
  4. Optional full **Creative Mode** switch (manual, gated after story).

---

## 11) Worldgen & Perf Best Practices

* Decorations per 16×16 chunk (Deadlands): Trees 0–3; Crash Node ~1/8–10; Echo/Spire (if used) ~1/28–32; System Cache ~1/56–64; Mire lakes ~1/40–48.
* Disable caves in v0.1 or keep vanilla carver very low.
* Jigsaw pools 1–3 pieces max; avoid deep recursion.
* Emissive light capped at 7; minimal particles; zero ticking blocks in v0.1.

---

## 12) Quests (FTB) & Scripting (KubeJS) Patterns

* One quest per verb step, not per item. Autopin critical structures.
* Server state skeleton:

```js
// KubeJS pseudo
const S = server.persistentData.bh ??= {
  cl: 0.0,         // worldCorruptionLevel
  sockets: 0,      // Gate‑01 sockets filled
  lens: false,
  rootPatch: false,
  sealed: false
}
function setCL(x){ S.cl = Math.max(0, Math.min(1, x)) }
```

* Debug commands: `/bh setCL <0..1>`, `/bh giveKey <id>`, `/bh locate gate`.
* Threshold triggers are **event‑based** (on cross), never per‑tick.

---

## 13) Economy & Rewards

* Early currency: `raw_memory`. Late: `root_shard`.
* Every gate item tradeable (high cost) to prevent softlocks.
* Reward cadence: item/ability every ~10 min in hour 1; every 20–30 min after.

---

## 14) Multiplayer Rules

* `worldCorruptionLevel` is **global**; milestones shared, rewards personal.
* Gate sockets accept duplicates; UI shows `inserted/required`.
* Optional instanced boss rooms to prevent griefing.

---

## 15) Acceptance Criteria (per layer)

* **Deadlands:** spawn → obtain 4 stabilizers via any path → portal opens; ≤2 ms/tick content.
* **Overworld:** threshold events fire once; patches/zones appear; FPS stable.
* **Fey:** Lens overlay & phase work; tag‑driven content reveal.
* **Advanced Deadlands:** 3 fragments obtainable via 3 paths; Patch reduces CL predictably.
* **Twilight:** seal ritual completes; CL locks at 0; quest advances.
* **Ascended:** Root Console tiers unlock; tools function.

---

## 16) Next Sprint (Actionable)

1. Implement server state + debug commands.
2. Build Gate‑01 site (4 sockets), Stabilizer item, and three acquisition paths.
3. Add `raw_memory` trades at Gate console.
4. Trigger Overworld Stage‑0 (visual hints) on gate activation.
5. FTB chapter for Deadlands: **Repair → Insert 4 → Depart**.
