package com.skyblockminer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Glacite Tunnels commissions: reads the commission lines from the tab list, mines the blocks (or fights the mobs)
 * the first open commission asks for, and claims finished ones through a Royal Pigeon.
 *
 * <p>Which blocks or mobs belong to which commission comes from the editable {@link MinerConfig#glaciteRules}, so a
 * wrong guess can be fixed in the GUI. Untested in game.
 */
final class GlaciteCommissions implements Routine {
    /** One "Commission text=blocks" rule; {@code mobs} is set instead of {@code blocks} for slayer rules ("mob:" prefix). */
    record Rule(String key, Map<String, Integer> blocks, List<String> mobs) {
        static Rule parse(String line) {
            int eq = line.indexOf('=');
            if (eq <= 0) {
                return null;
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (key.isEmpty() || value.isEmpty()) {
                return null;
            }
            if (value.toLowerCase().startsWith("mob:")) {
                List<String> mobs = new ArrayList<>();
                for (String mob : value.substring(4).split(",")) {
                    if (!mob.isBlank()) {
                        mobs.add(mob.trim());
                    }
                }
                return mobs.isEmpty() ? null : new Rule(key, null, mobs);
            }
            Map<String, Integer> blocks = new HashMap<>();
            for (String block : value.split(",")) {
                if (!block.isBlank()) {
                    blocks.put(Targets.normalize(block), 4);
                }
            }
            return blocks.isEmpty() ? null : new Rule(key, blocks, null);
        }

        boolean matches(String commission) {
            return commission.toLowerCase().contains(this.key.toLowerCase());
        }
    }

    static Rule ruleFor(String commission, List<String> rules) {
        for (String line : rules) {
            Rule rule = Rule.parse(line);
            if (rule != null && rule.matches(commission)) {
                return rule;
            }
        }
        return null;
    }

    private enum Phase {
        WARP,
        CHOOSE,
        WORK,
        CLAIM
    }

    private final BlockMining mining = new BlockMining(MacroType.TUNNELS);
    private final Combat combat = new Combat();
    private Phase phase = Phase.WARP;
    private long phaseAt;
    private long nextActionAt;
    private String current;
    private Rule rule;
    private int completed;

    @Override
    public void reset() {
        this.phase(Phase.WARP);
        this.current = null;
        this.rule = null;
        this.completed = 0;
        this.mining.reset();
        this.combat.clear();
    }

    @Override
    public void resume() {
        this.phase(Phase.CHOOSE);
        this.mining.resume();
    }

    @Override
    public void releaseKeys(Minecraft mc) {
        this.mining.releaseKeys(mc);
    }

    private void phase(Phase next) {
        this.phase = next;
        this.phaseAt = System.currentTimeMillis();
        this.nextActionAt = 0L;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        long now = System.currentTimeMillis();
        return switch (this.phase) {
            case WARP -> {
                String area = TabList.area(mc);
                if (area != null && (area.contains("Glacite") || area.contains("Base Camp") || area.contains("Tunnels"))) {
                    this.phase(Phase.CHOOSE);
                    yield "In the tunnels";
                }
                if (now >= this.nextActionAt) {
                    macro.command(macro.config.glaciteWarpCommand);
                    this.nextActionAt = now + 8000L;
                }
                if (now - this.phaseAt > 40000L) {
                    macro.stop("Could not get to the Glacite Tunnels (check the warp command)");
                }
                yield "Warping to the tunnels";
            }
            case CHOOSE -> this.choose(macro, mc);
            case WORK -> this.work(macro, mc, player, level);
            case CLAIM -> this.claim(macro, mc, player, now);
        };
    }

    private String choose(Macro macro, Minecraft mc) {
        Map<String, Double> progress = TabList.parseProgress(TabList.lines(mc));
        if (progress.isEmpty()) {
            if (System.currentTimeMillis() - this.phaseAt > 10000L) {
                macro.stop("No commissions in the tab list (enable the Commissions tab widget)");
            }
            return "Reading commissions";
        }
        for (Map.Entry<String, Double> entry : progress.entrySet()) {
            if (entry.getValue() >= 1.0) {
                this.current = entry.getKey();
                this.phase(Phase.CLAIM);
                return "Claiming " + this.current;
            }
        }
        List<String> unknown = new ArrayList<>();
        for (String name : progress.keySet()) {
            Rule found = ruleFor(name, macro.config.glaciteRules);
            if (found != null) {
                this.current = name;
                this.rule = found;
                if (found.blocks() != null) {
                    this.mining.useCosts(found.blocks());
                }
                this.phase(Phase.WORK);
                return "Starting " + name;
            }
            unknown.add(name);
        }
        macro.stop("No rule for these commissions: " + String.join(", ", unknown) + " (add one under Mining > Glacite commissions)");
        return "Off";
    }

    private String work(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (System.currentTimeMillis() - this.phaseAt > 2000L) {
            Double value = TabList.parseProgress(TabList.lines(mc)).get(this.current);
            if (value == null || value >= 1.0) {
                macro.mining.release(mc);
                macro.walker.stop(mc);
                this.phase(Phase.CHOOSE);
                return "Commission updated";
            }
        }
        if (this.rule.mobs() != null) {
            int weapon = Inv.toolSlot(player, macro.config.weaponSlot, FishingMacro.WEAPONS);
            if (weapon >= 0 && player.getInventory().getSelectedSlot() != weapon) {
                macro.selectSlot(weapon);
            }
            CommissionData.Mob mob = new CommissionData.Mob(this.rule.mobs(), (x, y, z) -> true);
            return this.current + ": " + this.combat.tick(mc, player, level, mob, macro.rotator, macro.walker, macro.config, macro.random);
        }
        int tool = Inv.hotbar(player, Inv::isMiningTool);
        if (tool < 0) {
            macro.stop("No pickaxe or drill in your hotbar");
            return "Off";
        }
        if (player.getInventory().getSelectedSlot() != tool) {
            macro.selectSlot(tool);
        }
        return this.current + ": " + this.mining.tick(macro, mc, player, level);
    }

    private String claim(Macro macro, Minecraft mc, LocalPlayer player, long now) {
        String title = Inv.screenTitle(mc);
        if (title != null && title.contains("Commissions")) {
            if (now < this.nextActionAt) {
                return "Claiming";
            }
            int slot = Inv.menuSlotWithLore(mc, "COMPLETED");
            if (slot >= 0) {
                Inv.click(mc, slot);
                this.completed++;
                this.nextActionAt = now + 400L + macro.random.nextInt(300);
                return "Claiming";
            }
            player.closeContainer();
            this.phase(Phase.CHOOSE);
            return "Claimed";
        }
        if (title != null) {
            player.closeContainer();
            return "Closing menu";
        }
        if (now - this.phaseAt > 20000L) {
            macro.stop("Could not open the Commissions menu to claim");
            return "Off";
        }
        int pigeon = Inv.hotbar(player, stack -> Inv.contains(Inv.name(stack), macro.config.glaciteClaimItem));
        if (pigeon < 0) {
            macro.stop("Commission done: put a " + macro.config.glaciteClaimItem + " in your hotbar to claim, or claim it yourself");
            return "Off";
        }
        if (player.getInventory().getSelectedSlot() != pigeon) {
            macro.selectSlot(pigeon);
            this.nextActionAt = now + 300L;
        } else if (now >= this.nextActionAt) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            this.nextActionAt = now + 2500L;
        }
        return "Opening Commissions";
    }

    @Override
    public boolean ownsMenu() {
        return this.phase == Phase.CLAIM;
    }

    @Override
    public String hudLine(Macro macro) {
        return (this.current == null ? "" : this.current + ", ") + this.completed + " claimed";
    }

    @Override
    public String rejoinWarp(MinerConfig config) {
        return config.glaciteWarpCommand;
    }
}
