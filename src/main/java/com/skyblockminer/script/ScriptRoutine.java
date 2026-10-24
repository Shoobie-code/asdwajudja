package com.skyblockminer.script;

import com.skyblockminer.MinerMod;
import com.skyblockminer.game.Inv;
import com.skyblockminer.game.Server;
import com.skyblockminer.game.Sidebar;
import com.skyblockminer.game.TabList;
import com.skyblockminer.gui.Toasts;
import com.skyblockminer.macro.Macro;
import com.skyblockminer.macro.Routine;
import com.skyblockminer.macro.combat.Mob;
import com.skyblockminer.macro.mining.TargetFinder;
import com.skyblockminer.pathing.PathWalker;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the selected userscript as a macro, so scripts get the shared machinery for free: failsafes, breaks,
 * pausing in menus, the panic key and the HUD. The script itself runs in a {@link ScriptRunner}; this class is its
 * hands on the client thread.
 */
public final class ScriptRoutine implements Routine, ScriptGame {
    private final Macro macro;
    public final Scripts scripts;
    private final Set<KeyMapping> held = new LinkedHashSet<>();
    private ScriptRunner runner;
    private String scriptName = "";
    private boolean walking;
    private String walkResult = "";
    private boolean mining;
    private boolean destroying;

    public ScriptRoutine(Macro macro, Path dir) {
        this.macro = macro;
        this.scripts = new Scripts(dir);
    }

    @Override
    public String tick(Macro macro, Minecraft mc, LocalPlayer player, ClientLevel level) {
        if (this.runner == null) {
            String problem = this.launch(mc);
            if (problem != null) {
                macro.stop(problem);
                return "Off";
            }
        }
        String end = this.runner.ended();
        if (end == null) {
            this.runner.tick();
            end = this.runner.ended();
        }
        if (end != null) {
            macro.stop(end);
            if (end.startsWith("Script error")) {
                Macro.alert();
            }
            return "Off";
        }
        String status = this.runner.status();
        return status.isEmpty() ? "Running " + this.scriptName : status;
    }

    /** Starts the selected script; returns why it could not, or null. */
    private String launch(Minecraft mc) {
        this.scripts.reload();
        String selected = this.macro.config.script;
        if (selected == null || selected.isBlank()) {
            return "No script selected (menu → Scripts)";
        }
        Scripts.Info info = this.scripts.find(selected);
        if (info == null) {
            return "Script " + selected + " is not in " + this.scripts.dir();
        }
        Server.Kind on = Server.current(mc);
        if (info.server() != null && on != Server.Kind.OTHER && on != info.server()) {
            return info.name() + " only runs on " + info.server().label + ", not on " + on.label;
        }
        String source;
        try {
            source = this.scripts.read(info);
        } catch (IOException e) {
            return "Could not read " + info.file() + ": " + e.getMessage();
        }
        this.scriptName = info.name();
        this.runner = new ScriptRunner(info.file(), source, this);
        this.runner.start();
        this.macro.message("Running script " + info.name() + ".");
        return null;
    }

    private void stopRunner() {
        if (this.runner != null) {
            this.runner.stop();
            this.runner = null;
        }
    }

    /** All chat lines, player messages included, for the script's handlers. */
    public void onAnyChat(String text) {
        if (this.runner != null) {
            this.runner.post("chat", text);
        }
    }

    @Override
    public void reset() {
        this.stopRunner();
        this.releaseAll();
        this.walkResult = "";
    }

    @Override
    public void stopped() {
        this.stopRunner();
        this.releaseAll();
    }

    @Override
    public void releaseKeys(Minecraft mc) {
        // Keys stay wanted and are pressed again when the macro resumes.
        for (KeyMapping key : this.held) {
            key.setDown(false);
        }
        this.stopDestroying(mc);
    }

    @Override
    public boolean breaking() {
        return this.destroying;
    }

    @Override
    public boolean ownsMenu() {
        return this.runner != null && !this.runner.api().pausesInMenus();
    }

    @Override
    public String hudLine(Macro macro) {
        return this.runner == null ? null : "Script: " + this.scriptName;
    }

    // ---- ScriptGame, on the client thread ----

    @Override
    public State tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return State.EMPTY;
        }
        for (KeyMapping key : this.held) {
            key.setDown(true);
        }
        if (this.walking) {
            PathWalker.State state = this.macro.walker.tick(mc, player, this.macro.rotator, this.macro.config);
            if (state == PathWalker.State.DONE || state == PathWalker.State.FAILED) {
                this.walkResult = state == PathWalker.State.DONE ? "done" : "failed: " + this.macro.walker.failure();
                this.macro.walker.stop(mc);
                this.walking = false;
            }
        }
        if (this.mining) {
            this.mineTick(mc, player);
        }
        Vec3 pos = player.position();
        return new State(player.getGameProfile().name(), pos.x, pos.y, pos.z, player.getYRot(), player.getXRot(), player.getHealth(),
            player.getMaxHealth(), player.onGround(), player.getInventory().getSelectedSlot(), Inv.name(player.getMainHandItem()),
            Inv.screenTitle(mc), this.macro.rotator.settled(player, 1.5), this.walking, this.walkResult);
    }

    @Override
    public void releaseAll() {
        Minecraft mc = Minecraft.getInstance();
        for (KeyMapping key : this.held) {
            key.setDown(false);
        }
        this.held.clear();
        if (this.walking) {
            this.macro.walker.stop(mc);
            this.walking = false;
        }
        this.mining = false;
        this.stopDestroying(mc);
    }

    @Override
    public boolean key(String name, boolean down) {
        var options = Minecraft.getInstance().options;
        KeyMapping key = switch (name) {
            case "forward" -> options.keyUp;
            case "back" -> options.keyDown;
            case "left" -> options.keyLeft;
            case "right" -> options.keyRight;
            case "jump" -> options.keyJump;
            case "sneak" -> options.keyShift;
            case "sprint" -> options.keySprint;
            case "attack" -> options.keyAttack;
            case "use" -> options.keyUse;
            default -> null;
        };
        if (key == null) {
            return false;
        }
        if (down) {
            this.held.add(key);
        } else {
            this.held.remove(key);
        }
        key.setDown(down);
        return true;
    }

    private double speed(double speed) {
        double value = speed < 0.0 ? this.macro.config.rotationSpeed / 100.0 : speed;
        return Math.max(0.01, Math.min(1.0, value));
    }

    @Override
    public void look(float yaw, float pitch, double speed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            this.macro.rotator.look(player, yaw, pitch, this.speed(speed));
        }
    }

    @Override
    public void lookAt(double x, double y, double z, double speed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            this.macro.rotator.track(player, new Vec3(x, y, z), this.speed(speed));
        }
    }

    @Override
    public void stopLook() {
        LocalPlayer player = Minecraft.getInstance().player;
        this.macro.rotator.stop();
        if (player != null) {
            this.macro.rotator.sync(player);
        }
    }

    @Override
    public void walk(double x, double y, double z, double radius) {
        this.macro.walker.go(Minecraft.getInstance(), List.of(new Vec3(x, y, z)), Math.max(0.3, radius));
        this.walking = true;
        this.walkResult = "";
    }

    @Override
    public void stopWalk() {
        if (this.walking) {
            this.macro.walker.stop(Minecraft.getInstance());
            this.walking = false;
            this.walkResult = "stopped";
        }
    }

    @Override
    public void mine(boolean on) {
        this.mining = on;
        if (!on) {
            this.stopDestroying(Minecraft.getInstance());
        }
    }

    private void mineTick(Minecraft mc, LocalPlayer player) {
        HitResult hit = mc.hitResult;
        if (mc.gameMode != null && mc.level != null && hit instanceof BlockHitResult block && hit.getType() == Type.BLOCK) {
            if (mc.gameMode.continueDestroyBlock(block.getBlockPos(), block.getDirection())) {
                mc.level.addBreakingBlockEffect(block.getBlockPos(), block.getDirection());
            }
            player.swing(InteractionHand.MAIN_HAND);
            this.destroying = true;
        } else {
            this.stopDestroying(mc);
        }
    }

    private void stopDestroying(Minecraft mc) {
        if (this.destroying) {
            if (mc.gameMode != null) {
                mc.gameMode.stopDestroyBlock();
            }
            this.destroying = false;
        }
    }

    @Override
    public void attack() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }
        if (mc.hitResult instanceof EntityHitResult entityHit && mc.hitResult.getType() == Type.ENTITY) {
            mc.gameMode.attack(player, entityHit.getEntity());
        }
        player.swing(InteractionHand.MAIN_HAND);
    }

    @Override
    public void use() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    @Override
    public void selectSlot(int slot) {
        this.macro.selectSlot(slot);
    }

    @Override
    public int findHotbar(String name) {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? -1 : Inv.hotbar(player, stack -> !stack.isEmpty() && Inv.contains(Inv.name(stack), name));
    }

    @Override
    public void chat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && !text.isBlank()) {
            mc.getConnection().sendChat(text);
        }
    }

    @Override
    public void command(String command) {
        this.macro.command(command);
    }

    @Override
    public void log(String text) {
        MinerMod.message(text, ChatFormatting.AQUA);
    }

    @Override
    public void toast(String title, String text) {
        Toasts.push(title, text, Toasts.Kind.INFO);
    }

    @Override
    public String screen() {
        return Inv.screenTitle(Minecraft.getInstance());
    }

    @Override
    public int menuSlot(String name) {
        return Inv.menuSlotNamed(Minecraft.getInstance(), name);
    }

    @Override
    public String slotName(int slot) {
        return Inv.name(Inv.slot(Minecraft.getInstance(), slot));
    }

    @Override
    public void clickSlot(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) {
            Inv.click(mc, slot);
        }
    }

    @Override
    public void closeScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gui.screen() != null) {
            mc.player.closeContainer();
        }
    }

    @Override
    public boolean inventoryFull() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Inv.inventoryFull(player);
    }

    @Override
    public String block(int x, int y, int z) {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? "minecraft:air" : TargetFinder.blockId(level, new BlockPos(x, y, z));
    }

    @Override
    public List<String> sidebar() {
        return Sidebar.lines(Minecraft.getInstance());
    }

    @Override
    public List<String> tabList() {
        return TabList.lines(Minecraft.getInstance());
    }

    @Override
    public String area() {
        return TabList.area(Minecraft.getInstance());
    }

    @Override
    public String server() {
        return Server.current(Minecraft.getInstance()).name().toLowerCase(Locale.ROOT);
    }

    @Override
    public List<Mob> mobs(double radius, String filter) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        List<Mob> found = new ArrayList<>();
        if (player == null || mc.level == null) {
            return found;
        }
        String want = filter.toLowerCase(Locale.ROOT);
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ArmorStand stand && stand.hasCustomName()) {
                String tag = ChatFormatting.stripFormatting(stand.getCustomName().getString());
                if (tag == null || tag.isBlank() || !tag.toLowerCase(Locale.ROOT).contains(want)) {
                    continue;
                }
                double distance = Math.sqrt(player.distanceToSqr(stand));
                if (distance <= radius) {
                    found.add(new Mob(tag.trim(), stand.getX(), stand.getY(), stand.getZ(), distance));
                }
            }
        }
        found.sort(Comparator.comparingDouble(Mob::distance));
        return found;
    }
}
