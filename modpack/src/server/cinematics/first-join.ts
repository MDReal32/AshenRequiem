// The intro scene plays once per player, the first time they ever join the world, and finishes the first quest.

const SCENE = "ashen_requiem:intro";
// Kept on the player, so it survives relogs and restarts. `tag <player> remove ashen_requiem.intro_seen` replays it.
const SEEN = "ashen_requiem.intro_seen";
// The world mod moves a new player to the Cradle on login; the scene starts from where they stand, so it waits for that.
// One tick is enough, and the client is still loading the world then: the scene holds behind the loading screen and is
// the first thing the player sees, without a glimpse of the world and HUD.
const DELAY = 1;

// The first quest, Waking, is the intro itself: it completes when the scene ends, however it ends.
const WAKING = "0001000100100000";

function wake(player: any): void {
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
    // Seen before Waking existed, or left during it: the story starts now.
    if (seen) {
      if (!Cinematica.isInScene(online)) wake(online);
      return;
    }
    if (online.stages.has(SEEN)) return;
    if (Cinematica.play(online, SCENE)) online.stages.add(SEEN);
    else {
      console.error(`First-join scene ${SCENE} is missing`);
      wake(online);
    }
  });
});

CinematicaEvents.ended(event => {
  if (event.sceneId.toString() === SCENE) wake(event.player);
});
