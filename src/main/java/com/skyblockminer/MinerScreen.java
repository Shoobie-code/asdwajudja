package com.skyblockminer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class MinerScreen extends Screen {
    private static final int WIDTH = 200;
    private static final int TALL = 20;
    private static final int GAP = 4;
    private static final int TEXT = -1;
    private final Macro macro;
    private Button power;
    private Button type;
    private int top;

    MinerScreen(Macro macro) {
        super(Component.literal("Skyblock Miner"));
        this.macro = macro;
    }

    protected void init() {
        int left = this.width / 2 - 100;
        this.top = this.height / 2 - 20 - 2;
        this.power = (Button)this.addRenderableWidget(Button.builder(this.powerLabel(), button -> {
            if (this.macro.running()) {
                this.macro.stop("Stopped");
                button.setMessage(this.powerLabel());
            } else {
                this.onClose();
                this.macro.start(this.macro.selected());
            }
        }).bounds(left, this.top, 200, 20).build());
        this.type = (Button)this.addRenderableWidget(Button.builder(this.typeLabel(), button -> {
            this.macro.select(this.macro.selected().next());
            button.setMessage(this.typeLabel());
            this.power.setMessage(this.powerLabel());
        }).bounds(left, this.top + 20 + 4, 200, 20).build());
    }

    public void tick() {
        this.power.setMessage(this.powerLabel());
        this.type.setMessage(this.typeLabel());
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(this.font, this.title.getString(), this.width / 2, this.top - 16, -1);
    }

    public boolean isPauseScreen() {
        return false;
    }

    private Component powerLabel() {
        return Component.literal("Macro: " + (this.macro.running() ? "ON" : "OFF"));
    }

    private Component typeLabel() {
        return Component.literal("Type: " + this.macro.selected().label);
    }

    static void open(Macro macro) {
        Minecraft.getInstance().gui.setScreen(new MinerScreen(macro));
    }
}
