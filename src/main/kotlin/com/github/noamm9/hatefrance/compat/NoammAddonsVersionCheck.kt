package com.github.noamm9.hatefrance.compat

import net.fabricmc.loader.api.FabricLoader
import net.fabricmc.loader.api.metadata.version.VersionPredicate
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin
import org.spongepowered.asm.mixin.throwables.MixinError

class NoammAddonsVersionCheck : IMixinConfigPlugin {
    override fun onLoad(mixinPackage: String) {
        val loader = FabricLoader.getInstance()
        val versionKey = "noammaddons-version"
        val checkerPath = "${javaClass.name.replace('.', '/')}.class"
        val mod = loader.allMods.singleOrNull {
            it.metadata.containsCustomValue(versionKey) && it.findPath(checkerPath).isPresent
        }?.metadata ?: throw MixinError("Could not resolve the NoammAddons version requirement for ${javaClass.name}.")
        val requiredVersion = mod.getCustomValue(versionKey).asString
        val requirement = "${mod.name} requires NoammAddons $requiredVersion."

        val noammAddons = loader.getModContainer("noammaddons").orElse(null)?.metadata
        if (noammAddons == null || noammAddons.containsCustomValue("noammaddons-loader:stub")) {
            throw MixinError("$requirement Install it or check the NoammAddons Loader log.")
        }

        val loadedVersion = noammAddons.version
        if (!VersionPredicate.parse(requiredVersion).test(loadedVersion)) {
            throw MixinError(
                "$requirement Loaded NoammAddons ${loadedVersion.friendlyString}. " +
                    "Use a build of ${mod.name} compatible with the loaded NoammAddons version."
            )
        }
    }
}
