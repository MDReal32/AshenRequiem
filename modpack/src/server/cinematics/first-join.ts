// The intro scene plays once per player, the first time they ever join the world.

const SCENE = "ashen_requiem:intro";
// Kept on the player, so it survives relogs and restarts. `tag <player> remove ashen_requiem.intro_seen` replays it.
const SEEN = "ashen_requiem.intro_seen";
// The world mod moves a new player to the Cradle on login; the scene starts from where they stand, so it waits for that.
// One tick is enough, and the client is still loading the world then: the scene holds behind the loading screen and is
// the first thing the player sees, without a glimpse of the world and HUD.
const DELAY = 1;

PlayerEvents.loggedIn(event => {
  const player = event.player;
  if (player.stages.has(SEEN)) return;
  const server = event.server;
  const name = player.getGameProfile().getName();

  server.scheduleInTicks(DELAY, () => {
    const online = server.getPlayerList().getPlayerByName(name);
    if (online == null || online.stages.has(SEEN)) return;
    if (Cinematica.play(online, SCENE)) online.stages.add(SEEN);
    else console.error(`First-join scene ${SCENE} is missing`);
  });
});
