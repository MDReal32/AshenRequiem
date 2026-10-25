package com.mdreal.ashenrequiem.core.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import java.util.stream.Collectors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Configured lists every JEI config file, including per-world and server ones that have no file while the client is
 * on a remote server. Asking one of those for its file name throws ("Config schema is inactive: jei") and crashes the
 * config screen, so they're left out of the list. Skipped when Configured isn't installed.
 */
@Pseudo
@Mixin(targets = "com.mrcrayfish.configured.impl.jei.JeiConfigProvider")
public abstract class ConfiguredJeiConfigProviderMixin {
    @ModifyReturnValue(method = "getConfigurationsForMod", at = @At("RETURN"), require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Set ashenRequiem$skipInactive(Set configs) {
        if (configs.isEmpty()) return configs;
        return (Set) configs.stream().filter(ConfiguredJeiConfigProviderMixin::hasFile).collect(Collectors.toUnmodifiableSet());
    }

    /** Configured isn't on our compile classpath, so `IModConfig.getFileName()` is called reflectively. */
    private static boolean hasFile(Object config) {
        try {
            config.getClass().getMethod("getFileName").invoke(config);
            return true;
        } catch (InvocationTargetException e) {
            return false;
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
