package com.skyblockminer.loader;

import com.skyblockminer.loader.Releases.Channel;
import com.skyblockminer.loader.Status.State;
import net.fabricmc.api.ClientModInitializer;

/** Starts the injected macro and handles channel switches from the title screen. */
public final class LoaderClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        if (Status.current != State.LOADED) {
            return;
        }
        try {
            Injector.initializeClient();
        } catch (Throwable t) {
            LoaderPreLaunch.LOGGER.error("Skyblock Macro failed to start", t);
            Status.set(State.NOT_LOADED, "The macro crashed on start: " + t);
        }
    }

    /**
     * Downloads the newest jar for {@code channel} in the background. Whatever is already running keeps running;
     * a different jar is picked up on the next launch.
     */
    public static void switchTo(Channel channel) {
        LoaderState state = Status.state;
        if (state == null || Status.current == State.WORKING || Status.current == State.DIRECT) {
            return;
        }
        State before = Status.current;
        state.channel = channel;
        state.save();
        Status.set(State.WORKING, "Checking GitHub for " + channel.label.toLowerCase() + "...");
        Thread thread = new Thread(() -> {
            try {
                Updater.update(state, channel);
                if (state.jar.equals(Status.runningJar)) {
                    Status.runningChannel = channel;
                    Status.set(State.LOADED, "Up to date");
                } else {
                    Status.set(State.RESTART, channel.label + " (" + state.label + ") downloaded. Restart to load it");
                }
            } catch (Exception e) {
                LoaderPreLaunch.LOGGER.warn("Could not switch to {}", channel, e);
                Status.set(before, "Couldn't reach GitHub: " + (e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }, "Skymacro loader update");
        thread.setDaemon(true);
        thread.start();
    }
}
