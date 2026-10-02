package com.skyblockminer;

import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/** Mines the closest matching blocks in reach without moving (mithril, gemstone, ore, tunnels, custom). */
final class BlockMining implements Routine {
    private final MacroType type;
    private Map<String, Integer> costs;
    private int costsRevision = -1;

    BlockMining(MacroType type) {
        this.type = type;
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (this.costs == null || this.costsRevision != macro.config.revision()) {
            this.costs = Targets.costs(this.type.blocks, macro.config);
            this.costsRevision = macro.config.revision();
        }
        return macro.mining.tick(mc, player, level, this.costs, macro.config, macro.rotator);
    }

    @Override
    public void reset() {
        this.costs = null;
    }
}
