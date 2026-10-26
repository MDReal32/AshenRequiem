import type { $ServerPlayer } from "@package/net/minecraft/server/level";

// The intro scene plays when a player first joins the world and again on every login until they have seen it through,
// and finishes the first quest.

const SCENE = "ashen_requiem:intro";
// Set once the scene has played, kept on the player so it survives relogs and restarts. `tag <player> remove ashen_requiem.intro_seen` replays it.
const SEEN = "ashen_requiem.intro_seen";
// The world mod moves a new player to the Cradle on login; the scene starts from where they stand, so it waits for that.
// One tick is enough, and the client is still loading the world then: the scene holds behind the loading screen and is
// the first thing the player sees, without a glimpse of the world and HUD.
const DELAY = 1;

// The first quest, Waking, is the intro itself: it completes when the scene ends with the player still in the world.
const WAKING = "0001000100100000";

function wake(player: $ServerPlayer): void {
  const team = FTBQuests.getServerDataFromPlayer(player);
  if (!team.isCompleted(WAKING)) team.complete(WAKING);
}

PlayerEvents.loggedIn(event => {
  const server = event.server;
  const name = event.player.getGameProfile().getName();
  const seen = event.player.stages.has(SEEN);

  server.scheduleInTicks(DELAY, () => {
    const online = server.getPlayerList().getPlayerByName(name);
    if (online == null) return;
    // Seen before Waking existed: the story starts now.
    if (seen) {
      if (!Cinematica.isInScene(online)) wake(online);
      return;
    }
    if (Cinematica.isInScene(online)) return;
    if (!Cinematica.play(online, SCENE)) {
      console.error(`First-join scene ${SCENE} is missing`);
      wake(online);
    }
  });
});

CinematicaEvents.ended(event => {
  if (event.sceneId.toString() !== SCENE) return;
  const player = event.server.getPlayerList().getPlayer(event.player.uuid.toString());
  // Leaving mid-scene, or the server stopping, doesn't count as seen: it plays again on their next login.
  if (player == null || player.hasDisconnected()) return;
  // Played through, or stopped by a command while they stay in the world: either way the story moves on.
  player.stages.add(SEEN);
  wake(player);
});
