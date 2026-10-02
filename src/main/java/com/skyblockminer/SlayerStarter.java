package com.skyblockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Starts the next slayer quest after one ends, for the combat macro: opens the slayer menu (a command you set, or
 * the phone item in your hotbar), then clicks the boss, the tier and the confirm button by name. Every name is a
 * setting. Untested in game; if it can't get through, it says why and the macro keeps fighting.
 */
final class SlayerStarter {
    private static final int MAX_OPEN_TRIES = 3;

    private boolean active;
    private long nextActionAt;
    private int openTries;
    private boolean pickedTier;
    private int started;

    boolean active() {
        return this.active;
    }

    int started() {
        return this.started;
    }

    void reset() {
        this.active = false;
        this.started = 0;
    }

    void onChat(MinerConfig config, String text) {
        if (!config.slayerAutoStart) {
            return;
        }
        if (text.contains("SLAYER QUEST COMPLETE") || text.contains("SLAYER QUEST FAILED")) {
            this.active = true;
            this.nextActionAt = System.currentTimeMillis() + 1500L;
            this.openTries = 0;
            this.pickedTier = false;
        } else if (text.contains("SLAYER QUEST STARTED")) {
            if (this.active) {
                this.started++;
            }
            this.active = false;
        }
    }

    String tick(Macro macro, Minecraft mc, LocalPlayer player) {
        MinerConfig config = macro.config;
        long now = System.currentTimeMillis();
        if (now < this.nextActionAt) {
            return "Starting a slayer quest";
        }
        String title = Inv.screenTitle(mc);
        if (title == null) {
            if (this.pickedTier) {
                this.active = false;
                return "Slayer quest started";
            }
            if (++this.openTries > MAX_OPEN_TRIES) {
                return this.fail(macro, "Could not open the slayer menu");
            }
            if (!config.slayerOpenCommand.isBlank()) {
                macro.command(config.slayerOpenCommand);
            } else {
                int phone = Inv.hotbar(player, stack -> Inv.contains(Inv.name(stack), config.slayerPhoneItem));
                if (phone < 0) {
                    return this.fail(macro, "No " + config.slayerPhoneItem + " in the hotbar and no slayer command set");
                }
                macro.selectSlot(phone);
                mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
            }
            this.nextActionAt = now + 3000L;
            return "Opening the slayer menu";
        }
        int slot = Inv.menuSlotNamed(mc, config.slayerConfirmItem);
        if (slot < 0) {
            slot = Inv.menuSlotNamed(mc, config.slayerTier);
            if (slot >= 0) {
                this.pickedTier = true;
            }
        }
        if (slot < 0 && !this.pickedTier) {
            slot = Inv.menuSlotNamed(mc, config.slayerBoss);
        }
        if (slot < 0) {
            player.closeContainer();
            if (!this.pickedTier) {
                return this.fail(macro, "Could not find \"" + config.slayerBoss + "\" in the " + title + " menu");
            }
            this.nextActionAt = now + 500L;
            return "Closing the slayer menu";
        }
        Inv.click(mc, slot);
        this.nextActionAt = now + Math.max(300, config.solverClickDelay * 2);
        return "Choosing a slayer quest";
    }

    private String fail(Macro macro, String why) {
        this.active = false;
        macro.message(why + ". Start the next quest yourself.");
        macro.notify("Slayer auto-start", why, 15105570);
        return why;
    }
}
