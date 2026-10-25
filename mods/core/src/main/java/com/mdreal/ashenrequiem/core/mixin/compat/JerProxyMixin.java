package com.mdreal.ashenrequiem.core.mixin.compat;

import com.mdreal.ashenrequiem.core.compat.jer.JerHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** JER has just cleared and refilled its lists: mods add theirs now (see {@code JerHooks}). Skipped without JER. */
@Pseudo
@Mixin(targets = "jeresources.proxy.CommonProxy")
public abstract class JerProxyMixin {
    @Inject(method = "initCompatibility", at = @At("TAIL"), require = 0)
    private void ashenRequiem$rebuilt(CallbackInfo ci) {
        JerHooks.rebuilt();
    }
}
