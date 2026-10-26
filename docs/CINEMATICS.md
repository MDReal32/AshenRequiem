# Ashen Requiem — Cinematics & Effects

Every crossing between worlds and every big reveal is a scripted moment, never a plain loading screen. This file covers
**how the cinematic engine works** and lists **every scene**, shot by shot.

---

## 1. Cinematic engine: Cinematica Studio (`mods/cinematica`)

The engine is its own mod, **Cinematica Studio** (`cinematica_studio`), with no Ashen Requiem dependency, so other
modpacks can use it too. Ashen Requiem only decides *when* a scene plays (`Cinematica.play(player, id)`) and continues
on `SceneEndEvent`.

### 1.0 What v0.1 does today
- **Scenes** are JSON: datapack `data/<ns>/cinematica/scenes/<path>.json` (id `<ns>:<path>`) or recordings in
  `<server dir>/cinematica/scenes/<name>.json` (id `local:<name>`, or just `<name>` in commands). Recordings are not a
  datapack: to ship one (C01), copy the file into the Ashen Requiem datapack.
- **Tracks:** `camera` (eye position, yaw, pitch, roll, fov), `actor` (feet position, yaw, six bones plus elbows and knees in `pose`, whole
  body tilt in `root`), `fade` (black, 0..1). Each key has `t` in ticks and an optional `ease` for the segment it
  starts (`linear`, `in_sine`, `out_sine`, `in_out_sine`, `in_cubic`, `out_cubic`, `in_out_cubic`). Paths are smooth
  (Catmull-Rom), and yaw always turns the short way.
- **Coordinates** are relative to the player's feet when the scene starts, unless `"absolute": true`.
- **Ending:** after the last key the camera glides into the actor's eyes over `handoff` ticks (default 20), and the
  player gets control back where the actor stands. Other options: `duration`, `letterbox` (default on), `show_actor`.
- **Playback:** the player becomes a spectator and the server moves their body along the camera path, so chunks load
  where the camera is. The client shows a stand-in in the player's own skin. Game mode and position are restored at
  the end, and on the next login if the game crashed mid-scene.
- **Others nearby:** players within 64 blocks of the actor see it play the scene in the watcher's skin (no camera
  or letterbox), since the watcher's real body is an invisible spectator. It goes away when the watcher is back.
- **Recorder:** `/cinematica record <name>`. Fly as a spectator and drop keys at a time cursor: `K` camera key from
  your view, `J` actor key where you hover, `O` actor pose, `U` ease, `B` fade, `Del` delete, arrows move the cursor
  or jump between keys, `G` previews, `Enter` saves. `/cinematica stop` leaves. Keys can be rebound in Controls.
- **Commands:** `/cinematica play <scene> [targets]`, `stop [targets]`, `record <name>`, `list` (op level 2).
- **Playback controls** (pause, step, snapshot, the bar) only with `developerMode = true` in
  `cinematica-studio-client.toml`. For players a scene just plays.
- **Spoken lines** outside scenes: `Cinematica.say(player, key, sound)` (also from KubeJS). The client shows the
  translated `key` as a subtitle above the hotbar and plays `sound` in the Voice category if its resources have it,
  so a line without a recording yet is just the subtitle. One line at a time; the queue keeps the newest two and
  waits while a scene plays.
- **Known limits:** no overlay text in scenes, eyelids, shaders, audio or skip yet. Those are in the target design below.

The rest of this section is the **target design** for later versions.

### 1.1 Flow
```
server: Cinematics.play(player, "ashen_requiem:waking")
   │  (packet S2C PlayCinematic{id, seed, flags})
   ▼
client: CinematicPlayer loads assets/<ns>/cinematics/<id>.json
   │  runs tracks on a shared timeline (in ticks, rendered with partial-tick interpolation)
   ├─ camera track      → ViewportEvent.ComputeCameraAngles / ComputeFov, detached camera entity
   ├─ overlay track     → GUI layer drawn above everything else (eyelids, fade, letterbox, text)
   ├─ post-fx track     → PostChain shaders (blur, desaturate, chroma, grain, bloom, tunnel)
   ├─ audio track       → sounds, music, ducking/muffling every other sound category
   ├─ control track     → input lock, hide HUD/hand, hide other players
   └─ event track       → named markers sent back to the server (C2S CinematicMarker)
   ▼
server: on marker "teleport" → change dimension while the screen is black
        on "end" (or timeout) → unlock the player and fire a KubeJS event
```

### 1.2 Rules
- **Server is the authority.** While a scene plays, the player is invulnerable, doesn't move, and mobs ignore them. If
  the client never reports back, the server ends the scene after `duration + 5s`.
- **Teleports happen behind black frames.** The world never pops in on camera; chunk loading hides behind a fade or a
  tunnel shader.
- **Skippable on repeat.** The first viewing of a story scene can't be skipped. After that, holding `Space` for 1 second
  skips it. Every scene has a config option for a short version.
- **Multiplayer:** scenes play per player. Other players see the cinematic player standing still with a faint glow.
- **Data-driven:** scenes are JSON, hot-reloaded with `/ar cinematic reload`. KubeJS can trigger them with
  `AR.cinematic(player, id)`.
- **Accessibility:** config to turn off camera shake, flashes and chromatic effects. Subtitles are always on for spoken lines.
- **Compatibility:** shaders are vanilla `PostChain` JSON, so they work with Sodium. If Iris shaders are active, the
  engine skips post-fx and falls back to overlay-only effects.

### 1.3 Scene JSON sketch
```json
{
  "id": "ashen_requiem:waking",
  "duration": 460,
  "skippable": "after_first",
  "controls": { "lock_input": true, "hide_hud": true, "hide_hand": true },
  "tracks": {
    "overlay": [
      { "t": 0,   "type": "fill",   "color": "#000000", "alpha": 1.0 },
      { "t": 60,  "type": "eyelid", "open": 0.08, "ease": "out_sine", "len": 20 },
      { "t": 85,  "type": "eyelid", "open": 0.0,  "len": 10 },
      { "t": 140, "type": "eyelid", "open": 0.35, "ease": "out_quad", "len": 30 },
      { "t": 190, "type": "eyelid", "open": 0.0,  "len": 14 },
      { "t": 250, "type": "eyelid", "open": 1.0,  "ease": "out_cubic", "len": 60 },
      { "t": 380, "type": "subtitle", "speaker": "VESPER", "key": "cine.ar.waking.vesper_1", "len": 80 }
    ],
    "postfx": [
      { "t": 60,  "shader": "ashen_requiem:blur",      "from": 1.0, "to": 0.0, "len": 300 },
      { "t": 250, "shader": "ashen_requiem:desaturate", "from": 0.6, "to": 0.0, "len": 160 }
    ],
    "camera": [
      { "t": 0,   "mode": "head", "pitch": 80, "roll": 6 },
      { "t": 300, "mode": "head", "pitch": 10, "roll": 0, "ease": "in_out_sine", "len": 80 }
    ],
    "audio": [
      { "t": 0,   "sound": "ashen_requiem:cine.heartbeat_slow", "loop": true, "until": 300 },
      { "t": 250, "duck": ["ambient", "weather"], "to": 0.2, "len": 0 },
      { "t": 300, "sound": "ashen_requiem:cine.breath_in" }
    ],
    "events": [ { "t": 460, "name": "end" } ]
  }
}
```

### 1.4 Effect library (reusable building blocks)

| Effect | Type | Used in |
|---|---|---|
| Eyelids (top/bottom curves with soft edges, any amount open) | overlay | C01, C08, C14 |
| Fade to/from colour | overlay | everywhere |
| Letterbox bars (animated) | overlay | all story scenes |
| Subtitles / speaker cards / title cards | overlay | everywhere |
| Glitch text (VESPER's terminal voice) | overlay | C02, C03, C04 |
| Gaussian blur | post-fx | C01, C08 |
| Desaturation (0..1) | post-fx | C01, C05, C14, Hush zones |
| Colour bloom (resaturate past 1.0 for a short burst) | post-fx | C04, C16, C18 |
| Chromatic aberration | post-fx | C04, C09, maze |
| Film grain + vignette | post-fx | Dead Lands ambient |
| Tunnel / warp (radial streaks, centre zoom) | post-fx | C04, C07, C15 |
| Reveal wave (an expanding ring in world space; veiled blocks fade in at its edge) | world render | C09 |
| Frost creep (screen edges freeze over) | overlay | C08 |
| Camera shake (trauma model, decays smoothly) | camera | C17, events |
| Detached orbit / dolly / look-at | camera | C03, C13, C17 |
| Sound muffle (low-pass filter on every category) | audio | C01, C08, C12 |
| Music ducking and stems (layers added one at a time) | audio | C12, C17 |

---

## 2. Scene catalogue

Durations are targets. **(L)** = letterbox, **(I)** = input locked.

### C01 — Waking · 22s · (L)(I) · first join only
1. **0–3s:** pure black. A slow heartbeat. Muffled dripping water.
2. **3–4s:** eyelids open a crack. A blurry white sky through a cracked glass dome. They close.
3. **7–9s:** they open wider. Blur. A shape in the sky (the dead sun, a pale disc). A long blink.
4. **12–15s:** they open fully. The blur fades over 8s and colour goes from washed out to the Dead Lands' muted
   palette. The camera starts looking straight up, then lowers slowly toward the horizon while the player sits up.
5. **15–19s:** a deep breath in (the first sound without muffling). Ash drifts past.
6. **19–22s:** subtitle, VESPER, distorted and very faint: *"...ubject... zero... are you... awake?"*. Title card
   **ASHEN REQUIEM** fades in and out. Control is handed back.

### C02 — First Signal · 6s · in-game, no lock
Radio static gets louder, then VESPER's voice comes through. The Subject Tag's needle twitches. The HUD shows a small
radio waveform in the corner.

### C03 — Station Doors · 12s · (L)(I)
The camera leaves the player and slowly dollies up the station's huge doors, which are covered in ash. Emergency
lights flicker on one by one down the corridor. VESPER: *"Welcome back to Vigil Station. Please mind the bodies.
There are none. That is the problem."*

### C04 — Crossing · 18s · (L)(I) · the first big spectacle
1. The Gate's ring spins up, with a rising hum and dust lifting off the floor.
2. VESPER's last line. Her voice breaks into static.
3. The camera pushes into the portal. Tunnel shader: grey streaks, then blue, then gold.
4. Sound drops out completely. White out (1.5s). **Teleport happens here.**
5. The white fades into the Overworld: **colour bloom** (saturation 1.4 → 1.0 over 4s), birdsong *suddenly*, wind
   in grass. This should feel like a gasp.
6. Title card: **CHAPTER II — CONTAGION**.

### C05 — Something Follows · 7s · (L)(I) · plays right after C04
The camera turns around by itself to look back at the Gate exit. For a moment nothing happens. Then grey ash breathes
out of the portal and creeps across the grass in a short spreading animation (the actual Stage 1 conversion happens
here). A whisper with no source: *"...home..."*

### C06 — First Whisper · 5s · in-game
Deep in a Nether fortress, every sound cuts out for a second. Stereo whispers. The screen desaturates for a moment,
then the heat comes back.

### C07 — Between Worlds · 10s · (L)(I)
The Otherside portal flares. Tunnel shader in deep teal and black. You arrive in the Otherside's dark with a slow
camera pan over glowing sculk. Title card: **THE OTHERSIDE**.

### C08 — Requiem · 30s · (L)(I) · the emotional peak of Act II
1. The Choir's chorus breaks off. **Time stops**: particles freeze in the air (client-side freeze of entity and particle
   ticks), sound muffles.
2. Sculk veins creep in from the edges of the screen.
3. A single note, then a choir line: the **Requiem theme**, heard for the first time.
4. Eyelids close. In the dark: fast flashes of the Dead Lands *as it once was*, green and alive (5–6 pre-rendered
   panoramas or real camera shots of a hidden "memory" area).
5. Eyelids open. The Requiem mark burns into the player's hand (first-person hand overlay glows).
6. Subtitle, many voices: *"Come home."* Back to gameplay. A faint musical shimmer keeps playing as an ambient layer.

### C09 — Unveiling · 14s · (L)(I) · first time a marked player enters the Dead Lands
1. You arrive. Everything looks the same as before. 2 seconds of silence.
2. The Requiem theme plays very quietly. A **reveal wave** spreads out from the player (radius 0 → 96 blocks over 8s).
   At its edge, veiled blocks shimmer into place and fake blocks dissolve into ash particles.
3. Chromatic shimmer on the edge of the wave. Distant structures that weren't there before rise into view.
4. Title card: **CHAPTER III — REQUIEM**.

### C10 — Threshold · 12s · (L)(I)
The Archive's front: a featureless concrete slab with a door that only appears for marked players. Once inside, the
door is *gone*. The camera looks back at a solid wall. A deep, slow ventilation hum. Ceiling lights come on in a
sequence going away from the camera, much too far.

### C11 — The Sweeper · 20s · (L)(I) · dialogue scene
The camera finds an old man sweeping ash in an endless corridor. Shot/reverse-shot camera between him and the player.
Dialogue choices aren't needed. He hands over the **Tuning Radio** and it starts playing. The music *pulls* the
corridor walls into a new shape (preview of the maze mechanic).

### C12 — The Maze · in-game set piece, ~5–8 minutes
- The music is split into stems (drums, bass, choir, lead). Each section of the maze adds a stem.
- Geometry changes on the beat: walls slide, floors rise, stairs fold. The game has an authored beat map for the track.
- If the radio stops (it's dropped or switched off), the walls freeze, corridors start looping (seamless teleport
  loops), and the screen grain gets heavier.
- At the end, everything rises at once on the final chord into a huge chamber, then silence.

### C13 — Last Words · 40s · (L)(I)
The Departed's last recording, played as a 3D "memory": translucent ghost figures re-enact the moment Subject 0 was
put into stasis. The truth is told. A slow dolly toward the **Eden Seed** on its pedestal. Title card:
**CHAPTER IV — EDEN**.

### C14 — Ashfall · 15s · (L)(I) · returning to the Overworld at Stage 5
Arrival: no birdsong. Eyelids blink as if the player can't believe it. Desaturation 0.7. Ash falls like snow. A
village in the distance, completely silent. A slow camera pull back that shows how far the grey goes.

### C15 — Ascension · 16s · (L)(I)
The Seed blooms into a pillar of light. The player floats up (camera rises and tilts up), the clouds part, white.
Tunnel shader in gold. **Teleport.**

### C16 — Eden · 20s · (L)(I)
The player wakes on soft grass, an echo of C01: the same eyelids, but now the sky is *alive*. Maximum colour bloom.
Full orchestral music. Floating islands, waterfalls flowing upward, light everywhere. Title card: **EDEN**.

### C17 — The Choir Rises · 25s · (L)(I)
The ground at the Wound cracks. Ash pulls together into a colossus made of hushed silhouettes. Heavy camera shake,
then a slow low-angle shot. Many-voiced subtitle: *"WE ARE WHAT YOU LEFT BEHIND."* Boss bar appears. Fight starts.

### C18 — Requiem's End · 45s + credits · (L)(I)
1. The Bloom is played. The Requiem theme, finally whole.
2. A colour wave spreads out from the Wound across the Overworld (the client renders it out to the render distance,
   while the server queues real healing).
3. Cut to the Dead Lands: a single blade of green grass next to the open stasis pod.
4. Cut to the Archive: the Sweeper's broom leaning on a wall. No Sweeper.
5. Fade to black. Credits roll over a slow flyover of the places the player visited (camera path scripted per world,
   using the stored locations of key structures).

---

## 3. Ambient effects (outside cutscenes)

These keep the world feeling alive and wrong between scenes.

| Effect | Where | Notes |
|---|---|---|
| Ash weather (drifting particles, light fog) | Dead Lands, Hush zones | Custom weather, density is configurable |
| Film grain + soft vignette | Dead Lands | Very subtle, can be turned off |
| Desaturation by Hush strength | Overworld Hush zones | Smooth as you walk in |
| Silence zones (ambient sounds drop) | Hush zones | Audio ducking, no shader cost |
| Distant silhouettes that vanish when looked at | Dead Lands | Client-only fake entities |
| Footprints in ash | Ash blocks | Decal particles, short-lived |
| Low-health heartbeat + blur | Everywhere | Replaces vanilla's red flash |
| Radio static near Signal Relays | Dead Lands | Gets louder as you get closer |
| Requiem shimmer near veiled things | Marked players only | Hints at secrets |
| Light pillar over Vigil Station | Dead Lands | Visible from far away (a sky effect, not a beam block) |
| Aurora + upward waterfalls | Eden | Custom sky renderer |
| Dimension sky renderers | Dead Lands (pale dead sun, no stars), Eden | `DimensionSpecialEffects` |
