package com.skyblockminer.loader;

import com.skyblockminer.loader.Status.State;
import com.skyblockminer.loader.Updater.Mod;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs before Minecraft's classes load: fetches the newest macro jar for the chosen channel and puts it on the
 * game's class path with its mixins, so it runs this launch without being in the mods folder.
 */
public final class LoaderPreLaunch implements PreLaunchEntrypoint {
    public static final Logger LOGGER = LoggerFactory.getLogger("Skyblock Macro Loader");

    @Override
    public void onPreLaunch() {
        LoaderState state = LoaderState.load(FabricLoader.getInstance().getGameDir().resolve("skymacro"));
        Status.state = state;
        if (FabricLoader.getInstance().isModLoaded(Mod.MACRO_ID)) {
            Status.set(State.DIRECT, "Macro jar is in the mods folder; remove it to get auto updates");
            LOGGER.info("Skyblock Macro is installed in the mods folder, so the loader will not load or update it");
            return;
        }

        String problem = null;
        if (!state.offline || state.jarPath() == null) {
            try {
                Updater.update(state, state.channel);
            } catch (Exception e) {
                problem = e.getMessage() == null ? e.toString() : e.getMessage();
                LOGGER.warn("Could not update Skyblock Macro from GitHub", e);
            }
        }

        Path jar = state.jarPath();
        if (jar == null) {
            Status.set(State.NOT_LOADED, "Couldn't download the macro: " + problem);
            return;
        }
        Updater.cleanup(state);
        try {
            Injector.inject(jar);
        } catch (Throwable t) {
            LOGGER.error("Could not load {}", jar, t);
            Status.set(State.NOT_LOADED, "Couldn't load the macro: " + t);
            return;
        }
        Status.running = state.label;
        Status.runningChannel = state.installedChannel;
        Status.runningJar = state.jar;
        Status.set(State.LOADED, problem == null ? "" : "Offline, using the saved copy");
        LOGGER.info("Loaded Skyblock Macro {} from {}", state.label, jar);
    }
}
