package com.skyblockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Fossil Excavator loop for Glacite Powder: with the crosshair on the excavator, opens it, puts in Suspicious Scrap,
 * starts it and uncovers tiles until the chisel runs out, then repeats until the scrap is gone.
 *
 * <p>Every menu title and item name it looks for is a setting, because none of this could be checked in game.
 */
final class ExcavatorMacro implements Routine {
    private static final long STEP_TIMEOUT_MS = 10000L;

    private long nextActionAt;
    private long waitingSince;
    private int runs;
    private int tiles;
    private boolean opening;

    @Override
    public void reset() {
        this.nextActionAt = 0L;
        this.waitingSince = 0L;
        this.runs = 0;
        this.tiles = 0;
        this.opening = false;
    }

    @Override
    public void resume() {
        this.waitingSince = 0L;
        this.opening = false;
    }

    @Override
    public boolean ownsMenu() {
        return true;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        MinerConfig config = macro.config;
        long now = System.currentTimeMillis();
        if (now < this.nextActionAt) {
            return "Excavating";
        }
        String title = Inv.screenTitle(mc);
        if (title == null) {
            if (!Inv.has(player, config.excavatorScrap)) {
                macro.stop("Out of " + config.excavatorScrap);
                return "Off";
            }
            if (this.opening && now - this.waitingSince > STEP_TIMEOUT_MS) {
                macro.stop("The excavator did not open (aim at it before starting)");
                return "Off";
            }
            if (!this.opening) {
                this.opening = true;
                this.waitingSince = now;
            }
            useCrosshair(mc, player);
            this.nextActionAt = now + 1500L;
            return "Opening the excavator";
        }
        if (this.opening) {
            this.opening = false;
            this.waitingSince = 0L;
        }
        if (Inv.contains(title, config.excavatorMenu)) {
            return this.setup(macro, mc, now);
        }
        if (Inv.contains(title, config.excavatorDigMenu)) {
            return this.dig(macro, mc, player, now);
        }
        player.closeContainer();
        this.nextActionAt = now + 500L;
        return "Closing " + title;
    }

    /** First menu: put scrap in, then press start. */
    private String setup(Macro macro, Minecraft mc, long now) {
        MinerConfig config = macro.config;
        int start = Inv.menuSlotNamed(mc, config.excavatorStart);
        if (start >= 0 && Inv.menuSlotNamed(mc, config.excavatorScrap) >= 0) {
            Inv.click(mc, start);
            this.runs++;
            this.nextActionAt = now + 800L;
            return "Starting the excavator";
        }
        int scrap = Inv.playerSlotNamed(mc, config.excavatorScrap);
        if (scrap >= 0 && Inv.menuSlotNamed(mc, config.excavatorScrap) < 0) {
            Inv.click(mc, scrap);
            this.nextActionAt = now + 500L;
            return "Putting in scrap";
        }
        if (Inv.menuSlotNamed(mc, config.excavatorTile) >= 0) {
            // Some versions dig in the same menu.
            return this.dig(macro, mc, mc.player, now);
        }
        if (this.waitingSince == 0L) {
            this.waitingSince = now;
        } else if (now - this.waitingSince > STEP_TIMEOUT_MS) {
            macro.stop("Could not find \"" + config.excavatorStart + "\" in the excavator menu (check the Excavator settings)");
            return "Off";
        }
        return "Waiting for the excavator";
    }

    /** Dig menu: click covered tiles until none are left or the menu closes. */
    private String dig(Macro macro, Minecraft mc, LocalPlayer player, long now) {
        this.waitingSince = 0L;
        int tile = Inv.menuSlotNamed(mc, macro.config.excavatorTile);
        if (tile < 0) {
            player.closeContainer();
            this.nextActionAt = now + 1000L;
            return "Excavation done";
        }
        Inv.click(mc, tile);
        this.tiles++;
        this.nextActionAt = now + Math.max(100, macro.config.solverClickDelay) + macro.random.nextInt(150);
        return "Uncovering tiles";
    }

    /** Right clicks whatever the crosshair is on (block or entity), like pressing the use key once. */
    static void useCrosshair(Minecraft mc, LocalPlayer player) {
        HitResult hit = mc.hitResult;
        if (hit instanceof EntityHitResult entityHit && hit.getType() == HitResult.Type.ENTITY) {
            mc.gameMode.interact(player, entityHit.getEntity(), entityHit, InteractionHand.MAIN_HAND);
        } else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public String hudLine(Macro macro) {
        return this.runs + " excavations, " + this.tiles + " tiles";
    }
}
