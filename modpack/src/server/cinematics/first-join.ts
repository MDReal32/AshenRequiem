// The intro scene plays once per player, the first time they ever join the world.

const SCENE = "ashen_requiem:intro";
// Kept on the player, so it survives relogs and restarts. `tag <player> remove ashen_requiem.intro_seen` replays it.
const SEEN = "ashen_requiem.intro_seen";
// The world mod moves a new player to the Cradle on login; the scene starts from where they stand, so it waits for that.
const DELAY = 20;

PlayerEvents.loggedIn(event => {
  const player = event.player;
  if (player.tags.contains(SEEN)) return;
  const server = event.server;
  const uuid = player.uuid;

  server.scheduleInTicks(DELAY, () => {
    const online = server.playerList.getPlayer(uuid);
    if (online == null || online.tags.contains(SEEN)) return;
    if (Cinematica.play(online, SCENE)) online.addTag(SEEN);
    else console.error(`First-join scene ${SCENE} is missing`);
  });
});
