// Gaps in the ProbeJS dump, filled here until a new dump catches up.

import type { $FTBQuestsKubeJSPlayerData } from "@package/dev/ftb/mods/ftbxmodcompat/neoforge/ftbquests/kubejs";
import type { $ResourceLocation_ } from "@package/net/minecraft/resources";
import type { $ServerPlayer } from "@package/net/minecraft/server/level";

declare module "@package/com/mdreal/cinematica/api" {
  interface $Cinematica {
    // Newer than the last dump.
    say(player: $ServerPlayer, key: string, sound?: $ResourceLocation_): void;
  }
}

declare module "@package/dev/ftb/mods/ftbxmodcompat/neoforge/ftbquests/kubejs" {
  interface $FTBQuestsKubeJSWrapper {
    // Every server player is a player, but in the dump $ServerPlayer doesn't extend $Player (a mixin changed drop()).
    getServerDataFromPlayer(player: $ServerPlayer): $FTBQuestsKubeJSPlayerData;
  }
}
