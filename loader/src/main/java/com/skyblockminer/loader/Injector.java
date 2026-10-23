package com.skyblockminer.loader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.skyblockminer.loader.Updater.Mod;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import org.spongepowered.asm.mixin.Mixins;

/**
 * Adds a downloaded mod jar to the running game. Fabric only discovers mods before any mod code runs, so this
 * does the parts of loading the macro needs by hand: class path, mixin configs and client entrypoints.
 * Mixins still apply because this runs in preLaunch, before Minecraft's own classes are loaded.
 */
public final class Injector {
    private static final List<String> CLIENT_ENTRYPOINTS = new ArrayList<>();

    private Injector() {
    }

    public static void inject(Path jar) throws IOException {
        Mod mod = Updater.Mod.read(jar);
        checkMinecraft(mod.json());
        FabricLauncherBase.getLauncher().addToClassPath(jar);
        for (String config : strings(mod.json().get("mixins"), "config")) {
            Mixins.addConfiguration(config);
        }
        JsonElement entrypoints = mod.json().get("entrypoints");
        if (entrypoints != null && entrypoints.isJsonObject()) {
            CLIENT_ENTRYPOINTS.addAll(strings(entrypoints.getAsJsonObject().get("client"), "value"));
        }
    }

    /** Calls the macro's client entrypoints; run from the loader's own client entrypoint. */
    public static void initializeClient() throws ReflectiveOperationException {
        ClassLoader loader = Injector.class.getClassLoader();
        for (String name : CLIENT_ENTRYPOINTS) {
            Object entry = Class.forName(name, true, loader).getDeclaredConstructor().newInstance();
            ((ClientModInitializer) entry).onInitializeClient();
        }
    }

    /** Refuses a jar built for another Minecraft version (an exact "=x.y" dependency) instead of crashing later. */
    private static void checkMinecraft(JsonObject json) throws IOException {
        JsonElement depends = json.get("depends");
        if (depends == null || !depends.isJsonObject()) {
            return;
        }
        JsonElement wanted = depends.getAsJsonObject().get("minecraft");
        if (wanted == null || !wanted.isJsonPrimitive() || !wanted.getAsString().startsWith("=")) {
            return;
        }
        String need = wanted.getAsString().substring(1).trim();
        String have = FabricLoader.getInstance().getModContainer("minecraft")
            .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(need);
        if (!need.equals(have)) {
            throw new IOException("this build needs Minecraft " + need + " (you have " + have + ")");
        }
    }

    /** A fabric.mod.json list whose items are strings or objects holding the string under {@code key}. */
    private static List<String> strings(JsonElement element, String key) {
        List<String> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return out;
        }
        for (JsonElement e : element.getAsJsonArray()) {
            if (e.isJsonPrimitive()) {
                out.add(e.getAsString());
            } else if (e.isJsonObject() && e.getAsJsonObject().has(key)) {
                out.add(e.getAsJsonObject().get(key).getAsString());
            }
        }
        return out;
    }
}
