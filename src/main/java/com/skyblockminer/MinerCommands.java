package com.skyblockminer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

final class MinerCommands {
    private MinerCommands() {
    }

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, Macro macro, MinerConfig config) {
        dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(
                                                                                                                                                                                            "miner"
                                                                                                                                                                                        )
                                                                                                                                                                                        .executes(
                                                                                                                                                                                            c -> run(
                                                                                                                                                                                                macro::toggle
                                                                                                                                                                                            )
                                                                                                                                                                                        ))
                                                                                                                                                                                    .then(
                                                                                                                                                                                        ClientCommands.literal(
                                                                                                                                                                                                "gui"
                                                                                                                                                                                            )
                                                                                                                                                                                            .executes(
                                                                                                                                                                                                c -> run(
                                                                                                                                                                                                    MinerMod::openMenu
                                                                                                                                                                                                )
                                                                                                                                                                                            )
                                                                                                                                                                                    ))
                                                                                                                                                                                .then(
                                                                                                                                                                                    ClientCommands.literal(
                                                                                                                                                                                            "menu"
                                                                                                                                                                                        )
                                                                                                                                                                                        .executes(
                                                                                                                                                                                            c -> run(
                                                                                                                                                                                                MinerMod::openMenu
                                                                                                                                                                                            )
                                                                                                                                                                                        )
                                                                                                                                                                                ))
                                                                                                                                                                            .then(
                                                                                                                                                                                ClientCommands.literal(
                                                                                                                                                                                        "start"
                                                                                                                                                                                    )
                                                                                                                                                                                    .executes(
                                                                                                                                                                                        c -> run(
                                                                                                                                                                                            () -> macro.start(
                                                                                                                                                                                                macro.selected()
                                                                                                                                                                                            )
                                                                                                                                                                                        )
                                                                                                                                                                                    )
                                                                                                                                                                            ))
                                                                                                                                                                        .then(
                                                                                                                                                                            ClientCommands.literal(
                                                                                                                                                                                    "commissions"
                                                                                                                                                                                )
                                                                                                                                                                                .executes(
                                                                                                                                                                                    c -> run(
                                                                                                                                                                                        () -> macro.start(
                                                                                                                                                                                            MacroType.COMMISSIONS
                                                                                                                                                                                        )
                                                                                                                                                                                    )
                                                                                                                                                                                )
                                                                                                                                                                        ))
                                                                                                                                                                    .then(
                                                                                                                                                                        ClientCommands.literal("stop")
                                                                                                                                                                            .executes(
                                                                                                                                                                                c -> run(
                                                                                                                                                                                    () -> macro.stop(
                                                                                                                                                                                        "Stopped"
                                                                                                                                                                                    )
                                                                                                                                                                                )
                                                                                                                                                                            )
                                                                                                                                                                    ))
                                                                                                                                                                .then(
                                                                                                                                                                    ClientCommands.literal("status")
                                                                                                                                                                        .executes(c -> {
                                                                                                                                                                            MinerMod.message(
                                                                                                                                                                                macro.selected().label
                                                                                                                                                                                    + ": "
                                                                                                                                                                                    + macro.status()
                                                                                                                                                                                    + " | "
                                                                                                                                                                                    + macro.stats(),
                                                                                                                                                                                ChatFormatting.AQUA
                                                                                                                                                                            );
                                                                                                                                                                            return 1;
                                                                                                                                                                        })
                                                                                                                                                                ))
                                                                                                                                                            .then(
                                                                                                                                                                ClientCommands.literal("goto")
                                                                                                                                                                    .then(
                                                                                                                                                                        ClientCommands.argument(
                                                                                                                                                                                "x",
                                                                                                                                                                                IntegerArgumentType.integer()
                                                                                                                                                                            )
                                                                                                                                                                            .then(
                                                                                                                                                                                ClientCommands.argument(
                                                                                                                                                                                        "y",
                                                                                                                                                                                        IntegerArgumentType.integer()
                                                                                                                                                                                    )
                                                                                                                                                                                    .then(
                                                                                                                                                                                        ClientCommands.argument(
                                                                                                                                                                                                "z",
                                                                                                                                                                                                IntegerArgumentType.integer()
                                                                                                                                                                                            )
                                                                                                                                                                                            .executes(
                                                                                                                                                                                                c -> run(
                                                                                                                                                                                                    () -> macro.goTo(
                                                                                                                                                                                                        new Vec3(
                                                                                                                                                                                                            IntegerArgumentType.getInteger(
                                                                                                                                                                                                                    c,
                                                                                                                                                                                                                    "x"
                                                                                                                                                                                                                )
                                                                                                                                                                                                                + 0.5,
                                                                                                                                                                                                            IntegerArgumentType.getInteger(
                                                                                                                                                                                                                c,
                                                                                                                                                                                                                "y"
                                                                                                                                                                                                            ),
                                                                                                                                                                                                            IntegerArgumentType.getInteger(
                                                                                                                                                                                                                    c,
                                                                                                                                                                                                                    "z"
                                                                                                                                                                                                                )
                                                                                                                                                                                                                + 0.5
                                                                                                                                                                                                        )
                                                                                                                                                                                                    )
                                                                                                                                                                                                )
                                                                                                                                                                                            )
                                                                                                                                                                                    )
                                                                                                                                                                            )
                                                                                                                                                                    )
                                                                                                                                                            ))
                                                                                                                                                        .then(typeCommand("type", macro)))
                                                                                                                                                    .then(typeCommand("mode", macro)))
                                                                                                                                                .then(
                                                                                                                                                    ClientCommands.literal("speed")
                                                                                                                                                        .then(
                                                                                                                                                            ClientCommands.argument(
                                                                                                                                                                    "percent",
                                                                                                                                                                    IntegerArgumentType.integer(1, 100)
                                                                                                                                                                )
                                                                                                                                                                .executes(c -> {
                                                                                                                                                                    config.rotationSpeed = IntegerArgumentType.getInteger(
                                                                                                                                                                        c, "percent"
                                                                                                                                                                    );
                                                                                                                                                                    return saved(
                                                                                                                                                                        macro,
                                                                                                                                                                        config,
                                                                                                                                                                        "Rotation speed "
                                                                                                                                                                            + config.rotationSpeed
                                                                                                                                                                            + "%"
                                                                                                                                                                    );
                                                                                                                                                                })
                                                                                                                                                        )
                                                                                                                                                ))
                                                                                                                                            .then(
                                                                                                                                                ClientCommands.literal("reach")
                                                                                                                                                    .then(
                                                                                                                                                        ClientCommands.argument(
                                                                                                                                                                "blocks",
                                                                                                                                                                DoubleArgumentType.doubleArg(2.0, 4.5)
                                                                                                                                                            )
                                                                                                                                                            .executes(c -> {
                                                                                                                                                                config.reach = DoubleArgumentType.getDouble(
                                                                                                                                                                    c, "blocks"
                                                                                                                                                                );
                                                                                                                                                                return saved(
                                                                                                                                                                    macro,
                                                                                                                                                                    config,
                                                                                                                                                                    "Reach " + config.reach + " blocks"
                                                                                                                                                                );
                                                                                                                                                            })
                                                                                                                                                    )
                                                                                                                                            ))
                                                                                                                                        .then(
                                                                                                                                            ClientCommands.literal("spread")
                                                                                                                                                .then(
                                                                                                                                                    ClientCommands.argument(
                                                                                                                                                            "hundredths",
                                                                                                                                                            IntegerArgumentType.integer(0, 45)
                                                                                                                                                        )
                                                                                                                                                        .executes(c -> {
                                                                                                                                                            config.aimSpread = IntegerArgumentType.getInteger(
                                                                                                                                                                c, "hundredths"
                                                                                                                                                            );
                                                                                                                                                            return saved(
                                                                                                                                                                macro,
                                                                                                                                                                config,
                                                                                                                                                                "Aim spread "
                                                                                                                                                                    + config.aimSpread / 100.0
                                                                                                                                                                    + " blocks from the face centre"
                                                                                                                                                            );
                                                                                                                                                        })
                                                                                                                                                )
                                                                                                                                        ))
                                                                                                                                    .then(
                                                                                                                                        ClientCommands.literal("gems")
                                                                                                                                            .then(
                                                                                                                                                ClientCommands.argument(
                                                                                                                                                        "gemstones",
                                                                                                                                                        StringArgumentType.greedyString()
                                                                                                                                                    )
                                                                                                                                                    .suggests((c, b) -> {
                                                                                                                                                        b.suggest("all");
                                                                                                                                                        Targets.GEMSTONES
                                                                                                                                                            .keySet()
                                                                                                                                                            .forEach(b::suggest);
                                                                                                                                                        return b.buildFuture();
                                                                                                                                                    })
                                                                                                                                                    .executes(
                                                                                                                                                        c -> {
                                                                                                                                                            List<String> gems = new ArrayList<>();

                                                                                                                                                            for (String word : StringArgumentType.getString(
                                                                                                                                                                    c, "gemstones"
                                                                                                                                                                )
                                                                                                                                                                .toLowerCase()
                                                                                                                                                                .split("[,\\s]+")) {
                                                                                                                                                                if (word.equals("all")) {
                                                                                                                                                                    gems.addAll(
                                                                                                                                                                        Targets.GEMSTONES.keySet()
                                                                                                                                                                    );
                                                                                                                                                                } else if (Targets.GEMSTONES
                                                                                                                                                                    .containsKey(word)) {
                                                                                                                                                                    gems.add(word);
                                                                                                                                                                } else if (!word.isEmpty()) {
                                                                                                                                                                    MinerMod.message(
                                                                                                                                                                        "Unknown gemstone "
                                                                                                                                                                            + word
                                                                                                                                                                            + ". Gemstones: "
                                                                                                                                                                            + String.join(
                                                                                                                                                                                ", ",
                                                                                                                                                                                Targets.GEMSTONES.keySet()
                                                                                                                                                                            ),
                                                                                                                                                                        ChatFormatting.RED
                                                                                                                                                                    );
                                                                                                                                                                    return 0;
                                                                                                                                                                }
                                                                                                                                                            }

                                                                                                                                                            config.gemstones = new ArrayList<>(
                                                                                                                                                                gems.stream().distinct().toList()
                                                                                                                                                            );
                                                                                                                                                            return saved(
                                                                                                                                                                macro,
                                                                                                                                                                config,
                                                                                                                                                                "Gemstones: "
                                                                                                                                                                    + String.join(", ", config.gemstones)
                                                                                                                                                            );
                                                                                                                                                        }
                                                                                                                                                    )
                                                                                                                                            )
                                                                                                                                    ))
                                                                                                                                .then(
                                                                                                                                    ClientCommands.literal("weapon")
                                                                                                                                        .then(
                                                                                                                                            ClientCommands.argument(
                                                                                                                                                    "slot", IntegerArgumentType.integer(0, 9)
                                                                                                                                                )
                                                                                                                                                .executes(
                                                                                                                                                    c -> {
                                                                                                                                                        config.weaponSlot = IntegerArgumentType.getInteger(
                                                                                                                                                            c, "slot"
                                                                                                                                                        );
                                                                                                                                                        return saved(
                                                                                                                                                            macro,
                                                                                                                                                            config,
                                                                                                                                                            config.weaponSlot == 0
                                                                                                                                                                ? "No weapon: Goblin Slayer is skipped"
                                                                                                                                                                : "Goblin Slayer weapon in hotbar slot "
                                                                                                                                                                    + config.weaponSlot
                                                                                                                                                        );
                                                                                                                                                    }
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                                ))
                                                                                                                            .then(
                                                                                                                                ClientCommands.literal("avoid")
                                                                                                                                    .then(
                                                                                                                                        ClientCommands.argument(
                                                                                                                                                "radius", IntegerArgumentType.integer(0, 64)
                                                                                                                                            )
                                                                                                                                            .executes(
                                                                                                                                                c -> {
                                                                                                                                                    config.avoidRadius = IntegerArgumentType.getInteger(
                                                                                                                                                        c, "radius"
                                                                                                                                                    );
                                                                                                                                                    return saved(
                                                                                                                                                        macro,
                                                                                                                                                        config,
                                                                                                                                                        config.avoidRadius == 0
                                                                                                                                                            ? "Not avoiding other players"
                                                                                                                                                            : "Avoiding spots with players within "
                                                                                                                                                                + config.avoidRadius
                                                                                                                                                                + " blocks"
                                                                                                                                                    );
                                                                                                                                                }
                                                                                                                                            )
                                                                                                                                    )
                                                                                                                            ))
                                                                                                                        .then(
                                                                                                                            ClientCommands.literal("breaks")
                                                                                                                                .then(
                                                                                                                                    ClientCommands.argument(
                                                                                                                                            "every", IntegerArgumentType.integer(0, 600)
                                                                                                                                        )
                                                                                                                                        .then(
                                                                                                                                            ClientCommands.argument(
                                                                                                                                                    "length", IntegerArgumentType.integer(1, 120)
                                                                                                                                                )
                                                                                                                                                .executes(
                                                                                                                                                    c -> {
                                                                                                                                                        config.breakEvery = IntegerArgumentType.getInteger(
                                                                                                                                                            c, "every"
                                                                                                                                                        );
                                                                                                                                                        config.breakLength = IntegerArgumentType.getInteger(
                                                                                                                                                            c, "length"
                                                                                                                                                        );
                                                                                                                                                        return saved(
                                                                                                                                                            macro,
                                                                                                                                                            config,
                                                                                                                                                            config.breakEvery == 0
                                                                                                                                                                ? "Breaks off"
                                                                                                                                                                : "A "
                                                                                                                                                                    + config.breakLength
                                                                                                                                                                    + " min break about every "
                                                                                                                                                                    + config.breakEvery
                                                                                                                                                                    + " min (takes effect next start)"
                                                                                                                                                        );
                                                                                                                                                    }
                                                                                                                                                )
                                                                                                                                        )
                                                                                                                                )
                                                                                                                        ))
                                                                                                                    .then(
                                                                                                                        ((LiteralArgumentBuilder)ClientCommands.literal("powder")
                                                                                                                                .then(
                                                                                                                                    ClientCommands.literal("radius")
                                                                                                                                        .then(
                                                                                                                                            ClientCommands.argument(
                                                                                                                                                    "blocks", IntegerArgumentType.integer(4, 128)
                                                                                                                                                )
                                                                                                                                                .executes(c -> {
                                                                                                                                                    config.powderRadius = IntegerArgumentType.getInteger(
                                                                                                                                                        c, "blocks"
                                                                                                                                                    );
                                                                                                                                                    return saved(
                                                                                                                                                        macro,
                                                                                                                                                        config,
                                                                                                                                                        "Powder macro stays within "
                                                                                                                                                            + config.powderRadius
                                                                                                                                                            + " blocks of where it starts"
                                                                                                                                                    );
                                                                                                                                                })
                                                                                                                                        )
                                                                                                                                ))
                                                                                                                            .then(
                                                                                                                                ClientCommands.literal("width")
                                                                                                                                    .then(
                                                                                                                                        ClientCommands.argument(
                                                                                                                                                "extra", IntegerArgumentType.integer(0, 2)
                                                                                                                                            )
                                                                                                                                            .executes(c -> {
                                                                                                                                                config.powderWidth = IntegerArgumentType.getInteger(
                                                                                                                                                    c, "extra"
                                                                                                                                                );
                                                                                                                                                return saved(
                                                                                                                                                    macro,
                                                                                                                                                    config,
                                                                                                                                                    "Powder tunnel is "
                                                                                                                                                        + (config.powderWidth * 2 + 1)
                                                                                                                                                        + " wide"
                                                                                                                                                );
                                                                                                                                            })
                                                                                                                                    )
                                                                                                                            )
                                                                                                                    ))
                                                                                                                .then(
                                                                                                                    ClientCommands.literal("heat")
                                                                                                                        .then(
                                                                                                                            ClientCommands.argument(
                                                                                                                                    "limit", IntegerArgumentType.integer(0, 100)
                                                                                                                                )
                                                                                                                                .executes(c -> {
                                                                                                                                    config.heatLimit = IntegerArgumentType.getInteger(c, "limit");
                                                                                                                                    return saved(
                                                                                                                                        macro,
                                                                                                                                        config,
                                                                                                                                        config.heatLimit == 0
                                                                                                                                            ? "Heat warp-out off"
                                                                                                                                            : "Warp out at " + config.heatLimit + " Heat"
                                                                                                                                    );
                                                                                                                                })
                                                                                                                        )
                                                                                                                ))
                                                                                                            .then(
                                                                                                                ClientCommands.literal("cold")
                                                                                                                    .then(
                                                                                                                        ClientCommands.argument("limit", IntegerArgumentType.integer(0, 100))
                                                                                                                            .executes(c -> {
                                                                                                                                config.coldLimit = IntegerArgumentType.getInteger(c, "limit");
                                                                                                                                return saved(
                                                                                                                                    macro,
                                                                                                                                    config,
                                                                                                                                    config.coldLimit == 0
                                                                                                                                        ? "Cold warp-out off"
                                                                                                                                        : "Warp out at -" + config.coldLimit + " Cold"
                                                                                                                                );
                                                                                                                            })
                                                                                                                    )
                                                                                                            ))
                                                                                                        .then(
                                                                                                            ClientCommands.literal("webhook")
                                                                                                                .then(
                                                                                                                    ClientCommands.argument("url", StringArgumentType.greedyString())
                                                                                                                        .executes(c -> {
                                                                                                                            String url = StringArgumentType.getString(c, "url").trim();
                                                                                                                            if (url.equalsIgnoreCase("off")) {
                                                                                                                                url = "";
                                                                                                                            } else if (!Webhook.URL.matcher(url).matches()) {
                                                                                                                                MinerMod.message(
                                                                                                                                    "That is not a Discord webhook URL.", ChatFormatting.RED
                                                                                                                                );
                                                                                                                                return 0;
                                                                                                                            }

                                                                                                                            config.webhookUrl = url;
                                                                                                                            return saved(
                                                                                                                                macro,
                                                                                                                                config,
                                                                                                                                url.isEmpty() ? "Discord alerts off" : "Discord alerts on"
                                                                                                                            );
                                                                                                                        })
                                                                                                                )
                                                                                                        ))
                                                                                                    .then(
                                                                                                        ClientCommands.literal("ping")
                                                                                                            .then(ClientCommands.argument("id", StringArgumentType.word()).executes(c -> {
                                                                                                                String id = StringArgumentType.getString(c, "id");
                                                                                                                config.pingId = id.equalsIgnoreCase("off") ? "" : id.replaceAll("\\D", "");
                                                                                                                return saved(
                                                                                                                    macro,
                                                                                                                    config,
                                                                                                                    config.pingId.isEmpty()
                                                                                                                        ? "No Discord pings"
                                                                                                                        : "Failsafes will ping <@" + config.pingId + ">"
                                                                                                                );
                                                                                                            }))
                                                                                                    ))
                                                                                                .then(
                                                                                                    ClientCommands.literal("statusevery")
                                                                                                        .then(
                                                                                                            ClientCommands.argument("minutes", IntegerArgumentType.integer(0, 1440))
                                                                                                                .executes(
                                                                                                                    c -> {
                                                                                                                        config.statusEvery = IntegerArgumentType.getInteger(c, "minutes");
                                                                                                                        return saved(
                                                                                                                            macro,
                                                                                                                            config,
                                                                                                                            config.statusEvery == 0
                                                                                                                                ? "No Discord status updates"
                                                                                                                                : "Discord status every "
                                                                                                                                    + config.statusEvery
                                                                                                                                    + " min (next start)"
                                                                                                                        );
                                                                                                                    }
                                                                                                                )
                                                                                                        )
                                                                                                ))
                                                                                            .then(
                                                                                                toggle(
                                                                                                    "titanium",
                                                                                                    (on, cfg) -> cfg.prioritizeTitanium = on,
                                                                                                    macro,
                                                                                                    config,
                                                                                                    "Prioritise titanium"
                                                                                                )
                                                                                            ))
                                                                                        .then(toggle("sneak", (on, cfg) -> cfg.sneak = on, macro, config, "Sneak while mining")))
                                                                                    .then(toggle("ability", (on, cfg) -> cfg.useAbility = on, macro, config, "Use pickaxe ability")))
                                                                                .then(
                                                                                    toggle("random", (on, cfg) -> cfg.randomize = on, macro, config, "Randomised rotations and aim")
                                                                                ))
                                                                            .then(toggle("sprint", (on, cfg) -> cfg.sprint = on, macro, config, "Sprint while walking")))
                                                                        .then(toggle("chests", (on, cfg) -> cfg.openChests = on, macro, config, "Open treasure chests")))
                                                                    .then(toggle("etherwarp", (on, cfg) -> cfg.etherwarp = on, macro, config, "Etherwarp between route points")))
                                                                .then(toggle("slayer", (on, cfg) -> cfg.slayerCommissions = on, macro, config, "Slayer commissions")))
                                                            .then(toggle("sell", (on, cfg) -> cfg.sellTrash = on, macro, config, "Sell mining trash when the inventory fills")))
                                                        .then(toggle("rejoin", (on, cfg) -> cfg.autoRejoin = on, macro, config, "Rejoin SkyBlock after a kick (commissions)")))
                                                    .then(toggle("failsafes", (on, cfg) -> cfg.failsafes = on, macro, config, "Failsafes")))
                                                .then(toggle("chatalerts", (on, cfg) -> cfg.chatAlerts = on, macro, config, "Chat mention alerts")))
                                            .then(toggle("ungrab", (on, cfg) -> cfg.ungrab = on, macro, config, "Free the mouse while running")))
                                        .then(toggle("background", (on, cfg) -> cfg.keepRunningUnfocused = on, macro, config, "Keep running when the window loses focus")))
                                    .then(toggle("hud", (on, cfg) -> cfg.hud = on, macro, config, "HUD")))
                                .then(
                                    ((LiteralArgumentBuilder)ClientCommands.literal("players")
                                            .then(ClientCommands.argument("radius", IntegerArgumentType.integer(0, 64)).executes(c -> {
                                                config.playerRadius = IntegerArgumentType.getInteger(c, "radius");
                                                return saved(
                                                    macro,
                                                    config,
                                                    config.playerRadius == 0 ? "Player alerts off" : "Alert for players within " + config.playerRadius + " blocks"
                                                );
                                            })))
                                        .then(ClientCommands.literal("stop").then(ClientCommands.argument("on", BoolArgumentType.bool()).executes(c -> {
                                            config.stopForPlayers = BoolArgumentType.getBool(c, "on");
                                            return saved(macro, config, config.stopForPlayers ? "Nearby players stop the macro" : "Nearby players only alert");
                                        })))
                                ))
                            .then(
                                ((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("custom")
                                            .then(ClientCommands.literal("add").then(ClientCommands.argument("block", StringArgumentType.greedyString()).executes(c -> {
                                                String block = Targets.normalize(StringArgumentType.getString(c, "block"));
                                                if (!config.customBlocks.contains(block)) {
                                                    config.customBlocks.add(block);
                                                }

                                                return saved(macro, config, "Custom blocks: " + config.customBlocks);
                                            }))))
                                        .then(ClientCommands.literal("remove").then(ClientCommands.argument("block", StringArgumentType.greedyString()).executes(c -> {
                                            config.customBlocks.remove(Targets.normalize(StringArgumentType.getString(c, "block")));
                                            return saved(macro, config, "Custom blocks: " + config.customBlocks);
                                        }))))
                                    .then(ClientCommands.literal("list").executes(c -> {
                                        MinerMod.message("Custom blocks: " + config.customBlocks, ChatFormatting.AQUA);
                                        return 1;
                                    }))
                            ))
                        .then(routeCommand(macro, config)))
                    .then(
                        ((LiteralArgumentBuilder)ClientCommands.literal("map")
                                .executes(
                                    c -> {
                                        WorldMap map = macro.map;
                                        MinerMod.message(
                                            map.area() == null
                                                ? "No area yet (the map starts recording a few seconds after you arrive)"
                                                : "Map of " + map.area() + ": " + map.chunkCount() + " chunks known" + (map.loading() ? " (loading)" : ""),
                                            ChatFormatting.AQUA
                                        );
                                        return 1;
                                    }
                                ))
                            .then(ClientCommands.literal("clear").executes(c -> {
                                macro.map.clear();
                                MinerMod.message("Forgot the map of " + macro.map.area() + "; it is recorded again as you walk around", ChatFormatting.GREEN);
                                return 1;
                            }))
                    ))
                .then(ClientCommands.literal("help").executes(c -> help()))
        );
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> typeCommand(String name, Macro macro) {
        return (LiteralArgumentBuilder<FabricClientCommandSource>)ClientCommands.literal(name)
            .then(ClientCommands.argument("type", StringArgumentType.word()).suggests((c, b) -> {
                MacroType.SELECTABLE.forEach(type -> b.suggest(type.id));
                return b.buildFuture();
            }).executes(c -> {
                MacroType type = MacroType.parse(StringArgumentType.getString(c, "type"));
                if (type == null) {
                    MinerMod.message("Types: " + String.join(", ", MacroType.SELECTABLE.stream().map(t -> t.id).toList()), ChatFormatting.RED);
                    return 0;
                } else {
                    macro.select(type);
                    MinerMod.message("Type: " + type.label, ChatFormatting.GREEN);
                    return 1;
                }
            }));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> routeCommand(Macro macro, MinerConfig config) {
        Routes routes = macro.routes;
        return (LiteralArgumentBuilder<FabricClientCommandSource>)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal(
                                                            "route"
                                                        )
                                                        .then(ClientCommands.literal("add").executes(c -> {
                                                            BlockPos floor = floor();
                                                            if (floor == null) {
                                                                return 0;
                                                            } else {
                                                                routes.points().add(floor);
                                                                return routeSaved(routes, "Added point " + routes.points().size() + " at " + floor.toShortString());
                                                            }
                                                        })))
                                                    .then(
                                                        ClientCommands.literal("insert")
                                                            .then(ClientCommands.argument("number", IntegerArgumentType.integer(1)).executes(c -> {
                                                                BlockPos floor = floor();
                                                                if (floor == null) {
                                                                    return 0;
                                                                } else {
                                                                    int at = Math.min(IntegerArgumentType.getInteger(c, "number"), routes.points().size() + 1);
                                                                    routes.points().add(at - 1, floor);
                                                                    return routeSaved(routes, "Inserted point " + at + " at " + floor.toShortString());
                                                                }
                                                            }))
                                                    ))
                                                .then(
                                                    ((LiteralArgumentBuilder)ClientCommands.literal("remove").executes(c -> removePoint(routes, routes.points().size())))
                                                        .then(
                                                            ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                                                .executes(c -> removePoint(routes, IntegerArgumentType.getInteger(c, "number")))
                                                        )
                                                ))
                                            .then(ClientCommands.literal("clear").executes(c -> {
                                                routes.points().clear();
                                                return routeSaved(routes, "Route \"" + routes.name() + "\" cleared");
                                            })))
                                        .then(ClientCommands.literal("list").executes(c -> {
                                            List<BlockPos> points = routes.points();
                                            MinerMod.message(
                                                "Route \"" + routes.name() + "\": " + points.size() + " points, mining " + config.routeBlocks, ChatFormatting.AQUA
                                            );

                                            for (int i = 0; i < points.size(); i++) {
                                                MinerMod.message(i + 1 + ": " + points.get(i).toShortString(), ChatFormatting.GRAY);
                                            }

                                            return 1;
                                        })))
                                    .then(ClientCommands.literal("routes").executes(c -> {
                                        List<String> saved = Routes.saved();
                                        MinerMod.message(saved.isEmpty() ? "No saved routes" : "Saved routes: " + String.join(", ", saved), ChatFormatting.AQUA);
                                        return 1;
                                    })))
                                .then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.word()).executes(c -> {
                                    String name = StringArgumentType.getString(c, "name");
                                    if (!Routes.validName(name)) {
                                        return badName();
                                    } else {
                                        routes.rename(name);
                                        config.route = name;
                                        config.save();
                                        return routeSaved(routes, "Saved as \"" + name + "\" (" + routes.points().size() + " points)");
                                    }
                                }))))
                            .then(
                                ClientCommands.literal("load")
                                    .then(
                                        ClientCommands.argument("name", StringArgumentType.word())
                                            .suggests((c, b) -> {
                                                Routes.saved().forEach(b::suggest);
                                                return b.buildFuture();
                                            })
                                            .executes(
                                                c -> {
                                                    String name = StringArgumentType.getString(c, "name");
                                                    if (!Routes.validName(name)) {
                                                        return badName();
                                                    } else {
                                                        boolean found = routes.load(name);
                                                        config.route = name;
                                                        config.save();
                                                        MinerMod.message(
                                                            found ? "Loaded \"" + name + "\" (" + routes.points().size() + " points)" : "New empty route \"" + name + "\"",
                                                            ChatFormatting.GREEN
                                                        );
                                                        return 1;
                                                    }
                                                }
                                            )
                                    )
                            ))
                        .then(ClientCommands.literal("import").executes(c -> {
                            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();

                            try {
                                List<BlockPos> points = Routes.parse(clipboard);
                                routes.set(points);
                                return routeSaved(routes, "Imported " + points.size() + " points from the clipboard into \"" + routes.name() + "\"");
                            } catch (RuntimeException e) {
                                MinerMod.message("The clipboard does not hold a route (" + e.getMessage() + ")", ChatFormatting.RED);
                                return 0;
                            }
                        })))
                    .then(ClientCommands.literal("export").executes(c -> {
                        Minecraft.getInstance().keyboardHandler.setClipboard(Routes.toJson(routes.points()));
                        MinerMod.message("Copied " + routes.points().size() + " points to the clipboard", ChatFormatting.GREEN);
                        return 1;
                    })))
                .then(ClientCommands.literal("blocks").then(ClientCommands.argument("set", StringArgumentType.word()).suggests((c, b) -> {
                    Targets.MODES.forEach(b::suggest);
                    return b.buildFuture();
                }).executes(c -> {
                    String set = StringArgumentType.getString(c, "set").toLowerCase();
                    if (!Targets.MODES.contains(set)) {
                        MinerMod.message("Block sets: " + String.join(", ", Targets.MODES), ChatFormatting.RED);
                        return 0;
                    } else {
                        config.routeBlocks = set;
                        return saved(macro, config, "The route miner mines " + set);
                    }
                }))))
            .then(ClientCommands.literal("show").then(ClientCommands.argument("on", BoolArgumentType.bool()).executes(c -> {
                config.showRoute = BoolArgumentType.getBool(c, "on");
                return saved(macro, config, config.showRoute ? "Showing the route" : "Route hidden");
            })));
    }

    private static BlockPos floor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            BlockPos below = BlockPos.containing(mc.player.position().add(0.0, -0.2, 0.0));
            if (mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty()) {
                MinerMod.message("Stand on the block you want as a point.", ChatFormatting.RED);
                return null;
            } else {
                return below;
            }
        } else {
            return null;
        }
    }

    private static int removePoint(Routes routes, int number) {
        if (number >= 1 && number <= routes.points().size()) {
            BlockPos removed = routes.points().remove(number - 1);
            return routeSaved(routes, "Removed point " + number + " (" + removed.toShortString() + ")");
        } else {
            MinerMod.message("No point " + number + " (the route has " + routes.points().size() + ")", ChatFormatting.RED);
            return 0;
        }
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

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(
        String name, BiConsumer<Boolean, MinerConfig> set, Macro macro, MinerConfig config, String label
    ) {
        return (LiteralArgumentBuilder<FabricClientCommandSource>)ClientCommands.literal(name)
            .then(ClientCommands.argument("on", BoolArgumentType.bool()).executes(c -> {
                boolean on = BoolArgumentType.getBool(c, "on");
                set.accept(on, config);
                return saved(macro, config, label + (on ? " on" : " off"));
            }));
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

    private static int help() {
        String[] lines = new String[]{
            "/miner - macro on/off | /miner gui - menu (or bind keys in Controls)",
            "/miner type <mithril|gemstone|ore|tunnel|custom|route|powder|commissions>",
            "/miner goto <x> <y> <z> - walk somewhere with the pathfinder",
            "/miner route add|insert <n>|remove [n]|clear|list|save <name>|load <name>|routes",
            "/miner route import|export (clipboard JSON) | blocks <set> | show <true|false>",
            "/miner gems <all|ruby amber ...> | powder radius <n> | powder width <0-2>",
            "/miner speed <1-100> | reach <2-4.5> | spread <0-45> | heat|cold <0-100>",
            "/miner random|titanium|sneak|ability|sprint|chests|etherwarp <true|false>",
            "/miner weapon <slot 0-9> | slayer|sell|rejoin <true|false> | avoid <radius>",
            "/miner breaks <every min> <length min> (0 = off) | statusevery <min>",
            "/miner webhook <url|off> | ping <discord id|off>",
            "/miner players <radius> | players stop <true|false> | failsafes|chatalerts <true|false>",
            "/miner ungrab|background|hud <true|false> | custom add|remove <block> | status",
            "/miner map - what the pathfinder knows of this area | map clear"
        };

        for (String line : lines) {
            MinerMod.message(line, ChatFormatting.GRAY);
        }

        return 1;
    }
}
