import type { $Quest } from "@package/dev/ftb/mods/ftbquests/quest";
import type { $ServerPlayer } from "@package/net/minecraft/server/level";

// Subject 0 talks to themselves through the quests: a line when a quest opens, another when it's done.
// Subtitles are `voice.ashen_requiem.<slug>.<open|done>` in kubejs/assets/ashen_requiem/lang. The voice is the sound
// `ashen_requiem:voice.<slug>.<open|done>`; it plays once its recording is listed in kubejs/assets/ashen_requiem/sounds.json,
// until then the subtitle shows alone.
const VOICED: Record<string, string> = {
  "0001000100100000": "waking",
  "0001000100200000": "scrap",
  "0001000100300000": "scrap_knife",
  "0001000100400000": "clean_wood",
  "0001000100500000": "old_workbench",
  "0001000100600000": "dead_planks",
  "0001000100700000": "own_bench",
  "0001000100800000": "wooden_pickaxe",
  "0001000100900000": "ashen_stone"
};
// Waking's opening is the intro scene itself, so it has no open line.
const SILENT_OPEN = ["waking"];
// Lines a player already heard, kept on them so relogs and quest reloads don't repeat any.
const SPOKEN = "ashen_requiem_spoken";

function speak(player: $ServerPlayer, slug: string, kind: "open" | "done"): void {
  const line = `${slug}.${kind}`;
  const spoken = player.persistentData.getCompound(SPOKEN);
  if (spoken.getBoolean(line)) return;
  spoken.putBoolean(line, true);
  player.persistentData.put(SPOKEN, spoken);
  Cinematica.say(player, `voice.ashen_requiem.${line}`, `ashen_requiem:voice.${line}`);
}

FTBQuestsEvents.completed(event => {
  const slug = VOICED[event.object.toString()];
  if (slug == null) return;
  const players: $ServerPlayer[] = [];
  event.onlineMembers.forEach(player => {
    players.push(player as $ServerPlayer);
  });
  for (const player of players) speak(player, slug, "done");
  // A tick later the quest's completion has settled, so whatever it opened now counts its dependencies as done.
  event.server.scheduleInTicks(1, () => {
    for (const player of players) {
      const team = FTBQuests.getServerDataFromPlayer(player);
      for (const id in VOICED) {
        const next = VOICED[id];
        if (SILENT_OPEN.includes(next) || team.isCompleted(id)) continue;
        const quest = FTBQuests.getObject(player.level, id) as $Quest | null;
        if (quest != null && team.data.areDependenciesComplete(quest)) speak(player, next, "open");
      }
    }
  });
});
