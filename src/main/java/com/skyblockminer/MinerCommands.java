package com.skyblockminer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.skyblockminer.gui.Setting;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Client commands. {@code /sm} is the main name; {@code /miner} and {@code /macro} are aliases. */
final class MinerCommands {
    private static final List<String> NAMES = List.of("sm", "miner", "macro");

    private MinerCommands() {
    }

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, Macro macro, MinerConfig config, MacroSettings settings) {
        for (String name : NAMES) {
            dispatcher.register(root(name, macro, config, settings));
        }
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root(String name, Macro macro, MinerConfig config, MacroSettings settings) {
        SuggestionProvider<FabricClientCommandSource> types = (c, b) -> {
            MacroType.SELECTABLE.forEach(type -> b.suggest(type.id));
            return b.buildFuture();
        };
        SuggestionProvider<FabricClientCommandSource> ids = (c, b) -> {
            String typed = b.getRemainingLowerCase();
            settings.ids().stream().filter(id -> id.startsWith(typed)).forEach(b::suggest);
            return b.buildFuture();
        };

        return ClientCommands.literal(name)
            .executes(c -> run(macro::toggle))
            .then(ClientCommands.literal("gui").executes(c -> run(MinerMod::openMenu)))
            .then(ClientCommands.literal("menu").executes(c -> run(MinerMod::openMenu)))
            .then(ClientCommands.literal("hud").executes(c -> run(MinerMod::openHudEditor)))
            .then(ClientCommands.literal("market").executes(c -> run(MinerMod::openMarket)))
            .then(ClientCommands.literal("flips").executes(c -> run(MinerMod::openMarket)))
            .then(ClientCommands.literal("skills").executes(c -> skills()))
            .then(ClientCommands.literal("start")
                .executes(c -> run(() -> macro.start(macro.selected())))
                .then(ClientCommands.argument("type", StringArgumentType.word()).suggests(types)
                    .executes(c -> withType(c, type -> macro.start(type)))))
            .then(ClientCommands.literal("stop").executes(c -> run(() -> macro.stop("Stopped"))))
            .then(ClientCommands.literal("type")
                .then(ClientCommands.argument("type", StringArgumentType.word()).suggests(types)
                    .executes(c -> withType(c, type -> {
                        macro.select(type);
                        MinerMod.message("Macro: " + type.label, ChatFormatting.GREEN);
                    }))))
            .then(ClientCommands.literal("status").executes(c -> {
                MinerMod.message(macro.selected().label + ": " + macro.status() + (macro.running() ? " | " + macro.stats() : ""), ChatFormatting.AQUA);
                return 1;
            }))
            .then(ClientCommands.literal("commissions").executes(c -> run(() -> macro.start(MacroType.COMMISSIONS))))
            .then(ClientCommands.literal("goto")
                .then(ClientCommands.argument("x", IntegerArgumentType.integer())
                    .then(ClientCommands.argument("y", IntegerArgumentType.integer())
                        .then(ClientCommands.argument("z", IntegerArgumentType.integer())
                            .executes(c -> run(() -> macro.goTo(Vec3.atBottomCenterOf(new BlockPos(
                                IntegerArgumentType.getInteger(c, "x"),
                                IntegerArgumentType.getInteger(c, "y"),
                                IntegerArgumentType.getInteger(c, "z"))))))))))
            .then(ClientCommands.literal("set")
                .then(ClientCommands.argument("setting", StringArgumentType.word()).suggests(ids)
                    .executes(c -> show(settings, StringArgumentType.getString(c, "setting")))
                    .then(ClientCommands.argument("value", StringArgumentType.greedyString()).suggests((c, b) -> {
                        Setting setting = settings.find(StringArgumentType.getString(c, "setting"));
                        if (setting instanceof Setting.Choice choice) {
                            choice.options().forEach(b::suggest);
                        } else if (setting instanceof Setting.Toggle) {
                            b.suggest("on").suggest("off");
                        }
                        return b.buildFuture();
                    }).executes(c -> set(settings, StringArgumentType.getString(c, "setting"), StringArgumentType.getString(c, "value"))))))
            .then(ClientCommands.literal("settings").executes(c -> listSettings(settings)))
            .then(ClientCommands.literal("farm")
                .then(ClientCommands.literal("rewarp").executes(c -> set(settings, "farming.setrewarp", "")))
                .then(ClientCommands.literal("clear").executes(c -> set(settings, "farming.clearrewarp", ""))))
            .then(ClientCommands.literal("profile")
                .then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                    String profile = StringArgumentType.getString(c, "name");
                    boolean ok = config.saveProfile(profile);
                    MinerMod.message(ok ? "Saved profile " + profile : "Profile names may use letters, digits, - and _.", ok ? ChatFormatting.GREEN : ChatFormatting.RED);
                    return ok ? 1 : 0;
                })))
                .then(ClientCommands.literal("load").then(ClientCommands.argument("name", StringArgumentType.word())
                    .suggests((c, b) -> {
                        MinerConfig.savedProfiles().forEach(b::suggest);
                        return b.buildFuture();
                    })
                    .executes(c -> {
                        String profile = StringArgumentType.getString(c, "name");
                        boolean ok = config.loadProfile(profile);
                        if (ok) {
                            macro.applyConfig();
                        }
                        MinerMod.message(ok ? "Loaded profile " + profile : "No profile named " + profile, ok ? ChatFormatting.GREEN : ChatFormatting.RED);
                        return ok ? 1 : 0;
                    })))
                .then(ClientCommands.literal("list").executes(c -> {
                    List<String> saved = MinerConfig.savedProfiles();
                    MinerMod.message(saved.isEmpty() ? "No saved profiles" : "Profiles: " + String.join(", ", saved), ChatFormatting.AQUA);
                    return 1;
                })))
            .then(ClientCommands.literal("build")
                .executes(c -> run(() -> macro.start(MacroType.BUILDER)))
                .then(ClientCommands.literal("pos1").executes(c -> set(settings, "builder.pos1", "")))
                .then(ClientCommands.literal("pos2").executes(c -> set(settings, "builder.pos2", ""))))
            .then(ClientCommands.literal("forage")
                .then(ClientCommands.literal("spot").executes(c -> set(settings, "foraging.setspot", "")))
                .then(ClientCommands.literal("clear").executes(c -> set(settings, "foraging.clearspot", "")))
                .then(ClientCommands.literal("add").executes(c -> set(settings, "foraging.addpoint", "")))
                .then(ClientCommands.literal("clearroute").executes(c -> set(settings, "foraging.clearroute", ""))))
            .then(ClientCommands.literal("fish")
                .then(ClientCommands.literal("spot").executes(c -> set(settings, "fishing.setspot", "")))
                .then(ClientCommands.literal("clear").executes(c -> set(settings, "fishing.clearspot", ""))))
            .then(ClientCommands.literal("echo")
                .then(ClientCommands.literal("record").executes(c -> run(macro.recording::start)))
                .then(ClientCommands.literal("stop").executes(c -> run(macro.recording::stop)))
                .then(ClientCommands.literal("clear").executes(c -> run(() -> {
                    macro.recording.clear();
                    MinerMod.message("Recording cleared.", ChatFormatting.YELLOW);
                }))))
            .then(ClientCommands.literal("visitors")
                .then(ClientCommands.literal("spot").executes(c -> set(settings, "visitors.setspot", ""))))
            .then(ClientCommands.literal("combat")
                .then(ClientCommands.literal("spot").executes(c -> set(settings, "combat.setspot", "")))
                .then(ClientCommands.literal("clear").executes(c -> set(settings, "combat.clearspot", ""))))
            .then(ClientCommands.literal("custom")
                .then(ClientCommands.literal("add").then(ClientCommands.argument("block", StringArgumentType.greedyString()).executes(c -> {
                    String block = Targets.normalize(StringArgumentType.getString(c, "block"));
                    if (!config.customBlocks.contains(block)) {
                        config.customBlocks.add(block);
                    }
                    return saved(macro, config, "Custom blocks: " + config.customBlocks);
                })))
                .then(ClientCommands.literal("remove").then(ClientCommands.argument("block", StringArgumentType.greedyString()).executes(c -> {
                    config.customBlocks.remove(Targets.normalize(StringArgumentType.getString(c, "block")));
                    return saved(macro, config, "Custom blocks: " + config.customBlocks);
                })))
                .then(ClientCommands.literal("list").executes(c -> {
                    MinerMod.message("Custom blocks: " + config.customBlocks, ChatFormatting.AQUA);
                    return 1;
                })))
            .then(routeCommand(macro, config))
            .then(ClientCommands.literal("map")
                .executes(c -> {
                    WorldMap map = macro.map;
                    MinerMod.message(map.area() == null
                        ? "No area yet (the map starts recording a few seconds after you arrive)"
                        : "Map of " + map.area() + ": " + map.chunkCount() + " chunks known, " + map.ores().size() + " ores indexed"
                            + (map.loading() ? " (loading)" : ""),
                        ChatFormatting.AQUA);
                    return 1;
                })
                .then(ClientCommands.literal("clear").executes(c -> {
                    macro.map.clear();
                    MinerMod.message("Forgot the map of " + macro.map.area() + "; it is recorded again as you walk around", ChatFormatting.GREEN);
                    return 1;
                })))
            .then(ClientCommands.literal("help").executes(c -> help()));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> routeCommand(Macro macro, MinerConfig config) {
        Routes routes = macro.routes;
        return ClientCommands.literal("route")
            .then(ClientCommands.literal("add").executes(c -> {
                BlockPos floor = floor();
                if (floor == null) {
                    return 0;
                }
                routes.points().add(floor);
                return routeSaved(routes, "Added point " + routes.points().size() + " at " + floor.toShortString());
            }))
            .then(ClientCommands.literal("insert")
                .then(ClientCommands.argument("number", IntegerArgumentType.integer(1)).executes(c -> {
                    BlockPos floor = floor();
                    if (floor == null) {
                        return 0;
                    }
                    int at = Math.min(IntegerArgumentType.getInteger(c, "number"), routes.points().size() + 1);
                    routes.points().add(at - 1, floor);
                    return routeSaved(routes, "Inserted point " + at + " at " + floor.toShortString());
                })))
            .then(ClientCommands.literal("remove")
                .executes(c -> removePoint(routes, routes.points().size()))
                .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                    .executes(c -> removePoint(routes, IntegerArgumentType.getInteger(c, "number")))))
            .then(ClientCommands.literal("clear").executes(c -> {
                routes.set(List.of(), Map.of());
                return routeSaved(routes, "Route \"" + routes.name() + "\" cleared");
            }))
            .then(ClientCommands.literal("walk").then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                .then(ClientCommands.argument("on", BoolArgumentType.bool()).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "number");
                    if (n > routes.points().size()) {
                        return noPoint(routes);
                    }
                    BlockPos point = routes.points().get(n - 1);
                    boolean on = BoolArgumentType.getBool(c, "on");
                    routes.setFlags(point, new Routes.Flags(on, routes.flags(point).waitMs()));
                    return routeSaved(routes, "Point " + n + (on ? " is always walked to" : " uses etherwarp again"));
                }))))
            .then(ClientCommands.literal("wait").then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                .then(ClientCommands.argument("ms", IntegerArgumentType.integer(0, 60000)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "number");
                    if (n > routes.points().size()) {
                        return noPoint(routes);
                    }
                    BlockPos point = routes.points().get(n - 1);
                    int ms = IntegerArgumentType.getInteger(c, "ms");
                    routes.setFlags(point, new Routes.Flags(routes.flags(point).walk(), ms));
                    return routeSaved(routes, "Point " + n + " waits " + ms + " ms on arrival");
                }))))
            .then(ClientCommands.literal("list").executes(c -> {
                List<BlockPos> points = routes.points();
                MinerMod.message("Route \"" + routes.name() + "\": " + points.size() + " points, mining " + config.routeBlocks, ChatFormatting.AQUA);
                for (int i = 0; i < points.size(); i++) {
                    Routes.Flags f = routes.flags(points.get(i));
                    MinerMod.message(i + 1 + ": " + points.get(i).toShortString() + (f.walk() ? " (walk)" : "")
                        + (f.waitMs() > 0 ? " (wait " + f.waitMs() + " ms)" : ""), ChatFormatting.GRAY);
                }
                return 1;
            }))
            .then(ClientCommands.literal("routes").executes(c -> {
                List<String> saved = Routes.saved();
                MinerMod.message(saved.isEmpty() ? "No saved routes" : "Saved routes: " + String.join(", ", saved), ChatFormatting.AQUA);
                return 1;
            }))
            .then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                String name = StringArgumentType.getString(c, "name");
                if (!Routes.validName(name)) {
                    return badName();
                }
                routes.rename(name);
                config.route = name;
                config.save();
                return routeSaved(routes, "Saved as \"" + name + "\" (" + routes.points().size() + " points)");
            })))
            .then(ClientCommands.literal("load").then(ClientCommands.argument("name", StringArgumentType.word())
                .suggests((c, b) -> {
                    Routes.saved().forEach(b::suggest);
                    return b.buildFuture();
                })
                .executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    if (!Routes.validName(name)) {
                        return badName();
                    }
                    boolean found = routes.load(name);
                    config.route = name;
                    config.save();
                    MinerMod.message(found ? "Loaded \"" + name + "\" (" + routes.points().size() + " points)" : "New empty route \"" + name + "\"",
                        ChatFormatting.GREEN);
                    return 1;
                })))
            .then(ClientCommands.literal("import").executes(c -> {
                try {
                    String json = Minecraft.getInstance().keyboardHandler.getClipboard();
                    List<BlockPos> points = Routes.parse(json);
                    routes.set(points, Routes.parseFlags(json));
                    return routeSaved(routes, "Imported " + points.size() + " points from the clipboard into \"" + routes.name() + "\"");
                } catch (RuntimeException e) {
                    MinerMod.message("The clipboard does not hold a route (" + e.getMessage() + ")", ChatFormatting.RED);
                    return 0;
                }
            }))
            .then(ClientCommands.literal("export").executes(c -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(Routes.toJson(routes.points(), routes.allFlags()));
                MinerMod.message("Copied " + routes.points().size() + " points to the clipboard", ChatFormatting.GREEN);
                return 1;
            }))
            .then(ClientCommands.literal("show").then(ClientCommands.argument("on", BoolArgumentType.bool()).executes(c -> {
                config.showRoute = BoolArgumentType.getBool(c, "on");
                return saved(macro, config, config.showRoute ? "Showing the route" : "Route hidden");
            })));
    }

    private interface TypeAction {
        void run(MacroType type);
    }

    private static int withType(CommandContext<FabricClientCommandSource> c, TypeAction action) {
        MacroType type = MacroType.parse(StringArgumentType.getString(c, "type"));
        if (type == null) {
            MinerMod.message("Types: " + String.join(", ", MacroType.SELECTABLE.stream().map(t -> t.id).toList()), ChatFormatting.RED);
            return 0;
        }
        action.run(type);
        return 1;
    }

    private static int show(MacroSettings settings, String id) {
        Setting setting = settings.find(id);
        if (setting == null) {
            return unknown(id);
        }
        if (!setting.hasValue()) {
            return set(settings, id, "");
        }
        MinerMod.message(setting.name + " (" + id + "): " + setting.valueText(), ChatFormatting.AQUA);
        MinerMod.message(setting.description, ChatFormatting.GRAY);
        return 1;
    }

    private static int set(MacroSettings settings, String id, String value) {
        Setting setting = settings.find(id);
        if (setting == null) {
            return unknown(id);
        }
        String error = setting.set(value);
        if (error != null) {
            MinerMod.message(setting.name + ": " + error, ChatFormatting.RED);
            return 0;
        }
        if (setting.hasValue()) {
            MinerMod.message(setting.name + " set to " + setting.valueText(), ChatFormatting.GREEN);
        }
        return 1;
    }

    private static int unknown(String id) {
        MinerMod.message("No setting \"" + id + "\". /sm settings lists them all.", ChatFormatting.RED);
        return 0;
    }

    private static int listSettings(MacroSettings settings) {
        for (Setting.Category category : settings.categories()) {
            StringBuilder line = new StringBuilder(category.name()).append(": ");
            for (Setting.Section section : category.sections()) {
                for (Setting setting : section.settings()) {
                    line.append(setting.id).append(setting.hasValue() ? "=" + setting.valueText() : "").append("  ");
                }
            }
            MinerMod.message(line.toString().trim(), ChatFormatting.GRAY);
        }
        return 1;
    }

    private static BlockPos floor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        BlockPos below = BlockPos.containing(mc.player.position().add(0.0, -0.2, 0.0));
        if (mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty()) {
            MinerMod.message("Stand on the block you want as a point.", ChatFormatting.RED);
            return null;
        }
        return below;
    }

    private static int removePoint(Routes routes, int number) {
        if (number < 1 || number > routes.points().size()) {
            MinerMod.message("No point " + number + " (the route has " + routes.points().size() + ")", ChatFormatting.RED);
            return 0;
        }
        BlockPos removed = routes.points().remove(number - 1);
        return routeSaved(routes, "Removed point " + number + " (" + removed.toShortString() + ")");
    }

    private static int routeSaved(Routes routes, String text) {
        routes.save();
        MinerMod.message(text, ChatFormatting.GREEN);
        return 1;
    }

    private static int badName() {
        MinerMod.message("Route names may use letters, digits, - and _ only.", ChatFormatting.RED);
        return 0;
    }

    private static int noPoint(Routes routes) {
        MinerMod.message("The route only has " + routes.points().size() + " points", ChatFormatting.RED);
        return 0;
    }

    private static int run(Runnable action) {
        action.run();
        return 1;
    }

    private static int saved(Macro macro, MinerConfig config, String text) {
        config.save();
        macro.applyConfig();
        MinerMod.message(text, ChatFormatting.GREEN);
        return 1;
    }

    private static int skills() {
        SkillTracker tracker = MinerMod.skills();
        long now = System.currentTimeMillis();
        List<String> live = tracker.lines(now);
        MinerMod.message(live.isEmpty() ? "No skill XP gained in the last 5 minutes." : "Now: " + String.join(" | ", live), ChatFormatting.AQUA);
        for (String skill : List.of("Farming", "Mining", "Foraging", "Fishing", "Combat")) {
            List<java.util.Map.Entry<String, Double>> best = tracker.bestFor(skill);
            if (!best.isEmpty()) {
                StringBuilder line = new StringBuilder("Best for " + skill + ": ");
                for (int i = 0; i < Math.min(3, best.size()); i++) {
                    MacroType type = MacroType.parse(best.get(i).getKey());
                    line.append(type == null ? best.get(i).getKey() : type.label).append(' ')
                        .append(SkillTracker.compact(best.get(i).getValue())).append("/h  ");
                }
                MinerMod.message(line.toString().trim(), ChatFormatting.GRAY);
            }
        }
        return 1;
    }

    private static int help() {
        String[] lines = {
            "/sm - start or stop | /sm gui - menu (Right Shift) | /sm hud - move the HUD",
            "/sm market - bazaar, auction, craft and NPC flips, minion costs | /sm skills - XP rates and best methods",
            "/sm start [type] | stop | status | type <type>",
            "  types: mithril gemstone ore tunnel custom route powder commissions glacite excavator farming foraging fishing combat builder",
            "/sm set <setting> [value] - view or change any option | /sm settings - list them",
            "/sm farm rewarp|clear | forage spot|clear|add|clearroute | fish spot|clear | combat spot|clear | visitors spot - save spots where you stand",
            "/sm echo record|stop|clear - record a farm walk for the \"Recorded movement\" farm type",
            "/sm build pos1|pos2 - set build corners (block under you) | /sm build - start the farm builder",
            "/sm goto <x> <y> <z> - walk somewhere with the pathfinder",
            "/sm route add|insert <n>|remove [n]|clear|list|save <name>|load <name>|routes|import|export|show <true|false>",
            "/sm route walk <n> <true|false> | wait <n> <ms> - walk to a point instead of etherwarping, or wait there first",
            "/sm custom add|remove|list <block> | map [clear] | profile save|load|list <name>"
        };
        for (String line : lines) {
            MinerMod.message(line, ChatFormatting.GRAY);
        }
        return 1;
    }
}
