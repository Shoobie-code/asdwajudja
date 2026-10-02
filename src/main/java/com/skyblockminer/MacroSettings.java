package com.skyblockminer;

import com.skyblockminer.gui.ClickGuiScreen;
import com.skyblockminer.gui.HudEditorScreen;
import com.skyblockminer.gui.Setting;
import com.skyblockminer.gui.Setting.Button;
import com.skyblockminer.gui.Setting.Category;
import com.skyblockminer.gui.Setting.Choice;
import com.skyblockminer.gui.Setting.Multi;
import com.skyblockminer.gui.Setting.Section;
import com.skyblockminer.gui.Setting.Slider;
import com.skyblockminer.gui.Setting.Text;
import com.skyblockminer.gui.Setting.Toggle;
import com.skyblockminer.gui.Theme;
import com.skyblockminer.gui.Toasts;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Every user-facing option, grouped into the ClickGUI's pages. The same list backs {@code /sm set <id> <value>},
 * so a new option only has to be declared here.
 */
public final class MacroSettings {
    private static final List<String> MINING_TYPES = MacroType.SELECTABLE.stream()
        .filter(type -> type.category == MacroType.Category.MINING).map(type -> type.id).toList();

    private final Macro macro;
    private final MinerConfig c;
    private final List<Category> categories;
    private final Map<String, Setting> byId = new LinkedHashMap<>();

    public MacroSettings(Macro macro) {
        this.macro = macro;
        this.c = macro.config;
        this.categories = List.of(this.mining(), this.farming(), this.foraging(), this.fishing(), this.combat(), this.safety(),
            this.notifications(), this.visuals(), this.general());
        for (Category category : this.categories) {
            for (Section section : category.sections()) {
                for (Setting setting : section.settings()) {
                    if (this.byId.put(setting.id, setting) != null) {
                        throw new IllegalStateException("Duplicate setting id " + setting.id);
                    }
                }
            }
        }
    }

    public List<Category> categories() {
        return this.categories;
    }

    public Setting find(String id) {
        return this.byId.get(id);
    }

    public List<String> ids() {
        return List.copyOf(this.byId.keySet());
    }

    private void changed() {
        this.c.markDirty();
        this.macro.applyConfig();
        Toasts.setEnabled(this.c.toasts);
    }

    // ---- pages ----

    private Category mining() {
        return new Category("Mining", "⛏", List.of(
            new Section("Macro", List.of(
                this.choice("mining.type", "Mining macro", "Which mining macro the start button runs.",
                    () -> MINING_TYPES, id -> MacroType.parseOr(id, MacroType.MITHRIL).label,
                    () -> this.macro.selected().category == MacroType.Category.MINING ? this.c.mode : "mithril",
                    id -> this.macro.select(MacroType.parseOr(id, MacroType.MITHRIL))),
                this.start("mining.start", MacroType.Category.MINING))),
            new Section("Mining", List.of(
                this.slider("mining.speed", "Rotation speed", "How fast the view turns toward blocks.", 1, 100, 1, "%",
                    () -> this.c.rotationSpeed, v -> this.c.rotationSpeed = (int) v),
                this.slider("mining.reach", "Reach", "Furthest block distance to mine.", 2.0, 4.5, 0.1, "",
                    () -> this.c.reach, v -> this.c.reach = v),
                this.slider("mining.spread", "Aim spread", "How far from block centers aim points may be.", 0, 45, 1, "%",
                    () -> this.c.aimSpread, v -> this.c.aimSpread = (int) v),
                this.toggle("mining.random", "Varied aim", "Picks different points on each block.",
                    () -> this.c.randomize, v -> this.c.randomize = v),
                this.toggle("mining.sneak", "Sneak while mining", "Holds sneak so you do not walk off edges.",
                    () -> this.c.sneak, v -> this.c.sneak = v),
                this.toggle("mining.ability", "Use pickaxe ability", "Right clicks when the ability is ready.",
                    () -> this.c.useAbility, v -> this.c.useAbility = v),
                this.toggle("mining.titanium", "Titanium first", "Prefers titanium over mithril.",
                    () -> this.c.prioritizeTitanium, v -> this.c.prioritizeTitanium = v),
                this.toggle("mining.orewalk", "Walk to known ores", "When nothing is in reach, walks to the nearest ore the map has seen.",
                    () -> this.c.oreWalk, v -> this.c.oreWalk = v),
                this.slider("mining.orerange", "Ore search range", "How far to look for known ores.", 16, 160, 8, " blocks",
                    () -> this.c.oreWalkRange, v -> this.c.oreWalkRange = (int) v).visibleWhen(() -> this.c.oreWalk),
                this.toggle("mining.chests", "Open treasure chests", "Solves and opens Crystal Hollows chests.",
                    () -> this.c.openChests, v -> this.c.openChests = v))),
            new Section("Gemstones", List.of(
                new Multi("mining.gems", "Gemstones", "Gem types the gemstone, tunnel and route macros mine.",
                    List.copyOf(Targets.GEMSTONES.keySet()), () -> this.c.gemstones).onChange(this::changed),
                this.text("mining.custom", "Custom blocks", "Block ids for the custom macro, comma separated.", "e.g. stone, cobblestone", false,
                    () -> String.join(", ", this.c.customBlocks),
                    v -> this.c.customBlocks = new ArrayList<>(Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList())))),
            new Section("Route miner", List.of(
                this.choice("route.name", "Route", "Saved route the route miner follows.", this::routeNames, Function.identity(),
                    () -> this.c.route, name -> {
                        this.macro.routes.load(name);
                        this.c.route = name;
                    }),
                this.choice("route.blocks", "Blocks to mine", "Block set mined at each route point.", () -> Targets.MODES, Function.identity(),
                    () -> this.c.routeBlocks, v -> this.c.routeBlocks = v),
                this.toggle("route.etherwarp", "Etherwarp", "Teleports between points with an Aspect of the Void.",
                    () -> this.c.etherwarp, v -> this.c.etherwarp = v),
                this.toggle("route.show", "Show route", "Draws route points and lines in the world.",
                    () -> this.c.showRoute, v -> this.c.showRoute = v))),
            new Section("Powder & commissions", List.of(
                this.slider("powder.radius", "Powder radius", "How far the powder macro wanders from its start.", 8, 64, 1, " blocks",
                    () -> this.c.powderRadius, v -> this.c.powderRadius = (int) v),
                this.slider("powder.width", "Tunnel width", "Extra blocks mined on each side of the tunnel.", 0, 2, 1, "",
                    () -> this.c.powderWidth, v -> this.c.powderWidth = (int) v),
                this.toggle("comm.slayer", "Slayer commissions", "Takes mob-killing commissions too.",
                    () -> this.c.slayerCommissions, v -> this.c.slayerCommissions = v),
                this.slider("comm.weapon", "Weapon slot", "Hotbar slot of the weapon for slayer commissions (0 = none).", 0, 9, 1, "",
                    () -> this.c.weaponSlot, v -> this.c.weaponSlot = (int) v),
                this.slider("comm.avoid", "Avoid players", "Skips spots with players this close.", 0, 30, 1, " blocks",
                    () -> this.c.avoidRadius, v -> this.c.avoidRadius = (int) v),
                this.toggle("comm.sell", "Sell trash", "Sells junk to the NPC when the inventory fills.",
                    () -> this.c.sellTrash, v -> this.c.sellTrash = v))),
            new Section("Glacite commissions", List.of(
                this.longText("glacite.rules", "Commission rules", "Commission text=blocks (or mob:names), separated by ; - fix these if a commission is mined wrong.",
                    "Glacite=packed_ice; Umber=terracotta",
                    () -> String.join("; ", this.c.glaciteRules),
                    v -> this.c.glaciteRules = new ArrayList<>(Arrays.stream(v.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList())),
                this.text("glacite.warp", "Warp command", "Command that takes you to the Glacite Tunnels.", "warp camp", false,
                    () -> this.c.glaciteWarpCommand, v -> this.c.glaciteWarpCommand = v),
                this.text("glacite.claim", "Claim item", "Hotbar item that opens the Commissions menu.", "Royal Pigeon", false,
                    () -> this.c.glaciteClaimItem, v -> this.c.glaciteClaimItem = v))),
            new Section("Fossil Excavator", List.of(
                this.text("excavator.scrap", "Scrap item", "Item put into the excavator.", "Suspicious Scrap", false,
                    () -> this.c.excavatorScrap, v -> this.c.excavatorScrap = v),
                this.text("excavator.menu", "Excavator menu title", "Title of the menu that opens on the excavator.", "Fossil Excavator", false,
                    () -> this.c.excavatorMenu, v -> this.c.excavatorMenu = v),
                this.text("excavator.digmenu", "Digging menu title", "Title of the menu with the tiles.", "Fossil Excavator", false,
                    () -> this.c.excavatorDigMenu, v -> this.c.excavatorDigMenu = v),
                this.text("excavator.start", "Start item", "Item clicked to start excavating.", "Start Excavator", false,
                    () -> this.c.excavatorStart, v -> this.c.excavatorStart = v),
                this.text("excavator.tile", "Tile item", "Name of a covered tile to click.", "Dirt", false,
                    () -> this.c.excavatorTile, v -> this.c.excavatorTile = v))),
            new Section("Hazards", List.of(
                this.slider("mining.heat", "Heat limit", "Warps to the Forge at this heat (0 = off).", 0, 100, 1, "",
                    () -> this.c.heatLimit, v -> this.c.heatLimit = (int) v),
                this.slider("mining.cold", "Cold limit", "Warps to Base Camp at this cold (0 = off).", 0, 100, 1, "",
                    () -> this.c.coldLimit, v -> this.c.coldLimit = (int) v)))
        ));
    }

    private Category farming() {
        BooleanSupplier custom = () -> this.c.farmPattern.equals("custom");
        return new Category("Farming", "☘", List.of(
            new Section("Macro", List.of(this.start("farming.start", MacroType.Category.FARMING))),
            new Section("Farm layout", List.of(
                this.choice("farming.pattern", "Farm type", "Movement pattern for your farm design.",
                    () -> FarmingMacro.Pattern.ALL.stream().map(p -> p.id).toList(),
                    id -> {
                        FarmingMacro.Pattern pattern = FarmingMacro.Pattern.parse(id);
                        return pattern == null ? id : pattern.label;
                    },
                    () -> this.c.farmPattern, id -> {
                        this.c.farmPattern = id;
                        FarmingMacro.Pattern pattern = FarmingMacro.Pattern.parse(id);
                        if (pattern != null) {
                            this.c.farmPitch = pattern.pitch;
                        }
                    }),
                this.text("farming.left", "Lane keys (A)", "Keys held in the first lane direction, like A+W.", "A+W", false,
                    () -> this.c.farmCustomLeft, v -> this.c.farmCustomLeft = Keys.valid(v) ? v.toUpperCase() : this.c.farmCustomLeft)
                    .visibleWhen(custom),
                this.text("farming.right", "Lane keys (B)", "Keys held in the other lane direction, like D+W.", "D+W", false,
                    () -> this.c.farmCustomRight, v -> this.c.farmCustomRight = Keys.valid(v) ? v.toUpperCase() : this.c.farmCustomRight)
                    .visibleWhen(custom),
                this.slider("farming.pitch", "Pitch", "Vertical view angle while farming.", -90, 90, 0.5, "°",
                    () -> this.c.farmPitch, v -> this.c.farmPitch = v),
                this.toggle("farming.keepyaw", "Use current yaw", "Keeps the direction you face when starting.",
                    () -> this.c.farmKeepYaw, v -> this.c.farmKeepYaw = v),
                this.toggle("farming.snapyaw", "Snap yaw to 45°", "Rounds the starting yaw to the nearest 45 degrees.",
                    () -> this.c.farmSnapYaw, v -> this.c.farmSnapYaw = v).visibleWhen(() -> this.c.farmKeepYaw),
                this.slider("farming.yaw", "Yaw", "Fixed horizontal view angle.", -180, 180, 0.5, "°",
                    () -> this.c.farmYaw, v -> this.c.farmYaw = v).visibleWhen(() -> !this.c.farmKeepYaw),
                this.slider("farming.switch", "Lane switch delay", "Ticks without movement before switching lanes.", 2, 40, 1, " ticks",
                    () -> this.c.farmSwitchTicks, v -> this.c.farmSwitchTicks = (int) v),
                this.slider("farming.tool", "Tool slot", "Hotbar slot of the farming tool (0 = find automatically).", 0, 9, 1, "",
                    () -> this.c.farmToolSlot, v -> this.c.farmToolSlot = (int) v))),
            new Section("Rewarp", List.of(
                this.button("farming.setrewarp", "Rewarp point", "Stand at the end of your farm and press to save it.", "Set here",
                    this::setRewarpHere),
                this.button("farming.clearrewarp", "Clear rewarp point", "Removes the saved rewarp point.", "Clear", () -> {
                    this.c.farmRewarp = null;
                    this.c.save();
                    Toasts.push("Farming", "Rewarp point cleared", Toasts.Kind.INFO);
                }),
                this.text("farming.warp", "Warp command", "Command that takes you to the start of the farm.", "warp garden", false,
                    () -> this.c.farmWarpCommand, v -> this.c.farmWarpCommand = v),
                this.toggle("farming.stuckwarp", "Rewarp when stuck", "Warps back instead of stopping when no lane can move.",
                    () -> this.c.farmRewarpWhenStuck, v -> this.c.farmRewarpWhenStuck = v))),
            new Section("Visitors", List.of(
                this.toggle("visitors.enabled", "Serve visitors", "At each rewarp, walks to the visitors and accepts offers you have the items for.",
                    () -> this.c.visitorsEnabled, v -> this.c.visitorsEnabled = v),
                this.button("visitors.setspot", "Visitor spot", "Stand where the visitors gather (by the barn) and press.", "Set here", () -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null) {
                        this.c.visitorSpot = new double[]{player.getX(), player.getY(), player.getZ()};
                        this.saved("Visitor spot saved");
                    }
                }).visibleWhen(() -> this.c.visitorsEnabled),
                this.slider("visitors.min", "Wait for", "Visitors waiting before going to them.", 1, 5, 1, "",
                    () -> this.c.visitorMin, v -> this.c.visitorMin = (int) v).visibleWhen(() -> this.c.visitorsEnabled),
                this.text("visitors.accept", "Accept item", "Name of the accept button in a visitor's menu.", "Accept Offer", false,
                    () -> this.c.visitorAccept, v -> this.c.visitorAccept = v).visibleWhen(() -> this.c.visitorsEnabled))),
            new Section("Pests", List.of(
                this.choice("farming.pests", "When a pest spawns", "What to do on a pest spawn message.",
                    () -> List.of("ignore", "notify", "stop", "kill"), v -> v.equals("kill") ? "Kill with vacuum" : capitalize(v),
                    () -> this.c.farmPestAction, v -> this.c.farmPestAction = v)))
        ));
    }

    private Category foraging() {
        return new Category("Foraging", "♣", List.of(
            new Section("Macro", List.of(this.start("foraging.start", MacroType.Category.FORAGING))),
            new Section("Tree farm", List.of(
                this.button("foraging.setspot", "Standing spot", "Where the macro works; it walks here on start and after a rejoin.", "Set here",
                    () -> {
                        BlockPos pos = this.playerPos();
                        if (pos != null) {
                            this.c.forageSpot = new int[]{pos.getX(), pos.getY(), pos.getZ()};
                            this.saved("Foraging spot saved at " + pos.toShortString());
                        }
                    }),
                this.button("foraging.clearspot", "Clear standing spot", "Forgets the saved spot.", "Clear", () -> {
                    this.c.forageSpot = null;
                    this.saved("Foraging spot cleared");
                }),
                this.toggle("foraging.bonemeal", "Use bone meal", "Grows saplings instantly.",
                    () -> this.c.forageBonemeal, v -> this.c.forageBonemeal = v),
                this.toggle("foraging.grass", "Plant on grass", "Also plants on grass blocks (off: dirt and podzol only).",
                    () -> this.c.forageGrass, v -> this.c.forageGrass = v),
                this.slider("foraging.delay", "Action delay", "Pause after each planting or bone meal use.", 50, 1000, 10, " ms",
                    () -> this.c.forageActionDelay, v -> this.c.forageActionDelay = (int) v),
                this.button("foraging.addpoint", "Add tree route point", "Adds where you stand to the tree route. With a route, the macro walks between trees instead of planting.", "Add",
                    () -> {
                        BlockPos pos = this.playerPos();
                        if (pos != null) {
                            this.c.forageRoute.add(new int[]{pos.getX(), pos.getY(), pos.getZ()});
                            this.saved("Tree route point " + this.c.forageRoute.size() + " added at " + pos.toShortString());
                        }
                    }),
                this.button("foraging.clearroute", "Clear tree route", "Removes every tree route point (back to planting mode).", "Clear", () -> {
                    this.c.forageRoute.clear();
                    this.saved("Tree route cleared");
                }),
                this.text("foraging.warp", "Warp command", "Command used after a rejoin to get back to the trees.", "is", false,
                    () -> this.c.forageWarpCommand, v -> this.c.forageWarpCommand = v)))
        ));
    }

    private Category fishing() {
        return new Category("Fishing", "⚓", List.of(
            new Section("Macro", List.of(this.start("fishing.start", MacroType.Category.FISHING))),
            new Section("Fishing", List.of(
                this.button("fishing.setspot", "Fishing spot", "Saves where you stand and look; the macro walks here on start and after a rejoin.", "Set here", () -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player != null) {
                        this.c.fishSpot = new double[]{player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()};
                        this.saved("Fishing spot saved");
                    }
                }),
                this.button("fishing.clearspot", "Clear fishing spot", "Forgets the saved spot.", "Clear", () -> {
                    this.c.fishSpot = null;
                    this.saved("Fishing spot cleared");
                }),
                this.slider("fishing.reel", "Reel delay", "Wait after a bite before reeling in.", 0, 1000, 10, " ms",
                    () -> this.c.fishReelDelay, v -> this.c.fishReelDelay = (int) v),
                this.slider("fishing.recast", "Recast delay", "Wait after reeling before casting again.", 100, 2000, 10, " ms",
                    () -> this.c.fishRecastDelay, v -> this.c.fishRecastDelay = (int) v),
                this.slider("fishing.timeout", "Recast after", "Reels in and recasts if nothing bites for this long.", 5, 120, 1, " s",
                    () -> this.c.fishTimeout, v -> this.c.fishTimeout = (int) v),
                this.text("fishing.warp", "Warp command", "Command used after a rejoin (empty = none).", "warp hub", false,
                    () -> this.c.fishWarpCommand, v -> this.c.fishWarpCommand = v))),
            new Section("Sea creatures", List.of(
                this.toggle("fishing.kill", "Kill sea creatures", "Switches to a weapon when creatures gather.",
                    () -> this.c.fishKillCreatures, v -> this.c.fishKillCreatures = v),
                this.slider("fishing.limit", "Fight at", "Number of nearby creatures that starts a fight.", 1, 10, 1, "",
                    () -> this.c.fishCreatureLimit, v -> this.c.fishCreatureLimit = (int) v),
                this.choice("fishing.attack", "Attack with", "Melee hits, or right click (mage weapons like Hyperion).",
                    () -> List.of("melee", "use"), v -> v.equals("use") ? "Right click" : "Melee",
                    () -> this.c.fishAttackMode, v -> this.c.fishAttackMode = v),
                this.slider("fishing.weapon", "Weapon slot", "Hotbar slot of the weapon (0 = find automatically).", 0, 9, 1, "",
                    () -> this.c.fishWeaponSlot, v -> this.c.fishWeaponSlot = (int) v)))
        ));
    }

    private Category combat() {
        return new Category("Combat", "⚔", List.of(
            new Section("Macro", List.of(this.start("combat.start", MacroType.Category.COMBAT))),
            new Section("Targets", List.of(
                this.text("combat.mobs", "Mob names", "Parts of mob name tags to attack, comma separated.", "e.g. Ghost, Zealot", false,
                    () -> String.join(", ", this.c.combatMobs), v -> this.c.combatMobs = splitList(v)),
                this.slider("combat.radius", "Radius", "How far from the spot to look for mobs.", 4, 64, 1, " blocks",
                    () -> this.c.combatRadius, v -> this.c.combatRadius = (int) v),
                this.button("combat.setspot", "Grinding spot", "Center of the area; the macro walks here on start and after a rejoin.", "Set here",
                    () -> {
                        LocalPlayer player = Minecraft.getInstance().player;
                        if (player != null) {
                            this.c.combatSpot = new double[]{player.getX(), player.getY(), player.getZ()};
                            this.saved("Combat spot saved");
                        }
                    }),
                this.button("combat.clearspot", "Clear grinding spot", "Uses wherever you start the macro instead.", "Clear", () -> {
                    this.c.combatSpot = null;
                    this.saved("Combat spot cleared");
                }),
                this.text("combat.warp", "Warp command", "Command used after a rejoin to get back (empty = none).", "warp crypt", false,
                    () -> this.c.combatWarpCommand, v -> this.c.combatWarpCommand = v))),
            new Section("Slayer quests", List.of(
                this.toggle("slayer.auto", "Auto-start quests", "Starts the next slayer quest when one ends.",
                    () -> this.c.slayerAutoStart, v -> this.c.slayerAutoStart = v),
                this.text("slayer.command", "Menu command", "Command that opens the slayer menu (empty = use the phone item).", "", false,
                    () -> this.c.slayerOpenCommand, v -> this.c.slayerOpenCommand = v).visibleWhen(() -> this.c.slayerAutoStart),
                this.text("slayer.phone", "Phone item", "Hotbar item that opens the slayer menu.", "Maddox Batphone", false,
                    () -> this.c.slayerPhoneItem, v -> this.c.slayerPhoneItem = v).visibleWhen(() -> this.c.slayerAutoStart),
                this.text("slayer.boss", "Boss item", "Name of the boss in the slayer menu.", "Revenant Horror", false,
                    () -> this.c.slayerBoss, v -> this.c.slayerBoss = v).visibleWhen(() -> this.c.slayerAutoStart),
                this.text("slayer.tier", "Tier item", "Words in the tier item's name, like IV.", "IV", false,
                    () -> this.c.slayerTier, v -> this.c.slayerTier = v).visibleWhen(() -> this.c.slayerAutoStart),
                this.text("slayer.confirm", "Confirm item", "Name of the confirm button.", "Confirm", false,
                    () -> this.c.slayerConfirmItem, v -> this.c.slayerConfirmItem = v).visibleWhen(() -> this.c.slayerAutoStart))),
            new Section("Weapon", List.of(
                this.choice("combat.attack", "Attack with", "Melee hits, or right click (mage weapons like Hyperion).",
                    () -> List.of("melee", "use"), v -> v.equals("use") ? "Right click" : "Melee",
                    () -> this.c.combatAttackMode, v -> this.c.combatAttackMode = v),
                this.slider("combat.weapon", "Weapon slot", "Hotbar slot of the weapon (0 = find automatically).", 0, 9, 1, "",
                    () -> this.c.combatWeaponSlot, v -> this.c.combatWeaponSlot = (int) v)))
        ));
    }

    private Category safety() {
        return new Category("Failsafes", "⚠", List.of(
            new Section("Failsafes", List.of(
                this.toggle("safety.failsafes", "Failsafes", "Stops on teleports, view changes, cages and pushes.",
                    () -> this.c.failsafes, v -> this.c.failsafes = v),
                this.toggle("safety.chat", "Chat alerts", "Alerts on private messages, mentions and words like macro.",
                    () -> this.c.chatAlerts, v -> this.c.chatAlerts = v),
                this.slider("safety.players", "Player alert radius", "Warns when a player comes this close (0 = off).", 0, 50, 1, " blocks",
                    () -> this.c.playerRadius, v -> this.c.playerRadius = (int) v),
                this.toggle("safety.playerstop", "Stop for players", "Stops instead of only warning.",
                    () -> this.c.stopForPlayers, v -> this.c.stopForPlayers = v),
                this.toggle("safety.rejoin", "Auto rejoin", "Goes back to SkyBlock after kicks, Limbo and server swaps.",
                    () -> this.c.autoRejoin, v -> this.c.autoRejoin = v))),
            new Section("Scheduler", List.of(
                this.slider("breaks.every", "Break every", "Minutes of work between breaks (0 = no breaks).", 0, 240, 5, " min",
                    () -> this.c.breakEvery, v -> this.c.breakEvery = (int) v),
                this.slider("breaks.length", "Break length", "Minutes per break.", 1, 60, 1, " min",
                    () -> this.c.breakLength, v -> this.c.breakLength = (int) v),
                this.text("schedule.hours", "Active hours", "Only run inside this daily window, like 08:00-23:00 (empty = always).", "08:00-23:00", false,
                    () -> this.c.activeHours, v -> {
                        if (Schedule.valid(v)) {
                            this.c.activeHours = v.trim();
                        } else {
                            Toasts.push("Active hours", "Use HH:MM-HH:MM, like 08:00-23:00", Toasts.Kind.ERROR);
                        }
                    })))
        ));
    }

    private Category notifications() {
        return new Category("Alerts", "♪", List.of(
            new Section("Discord", List.of(
                this.text("webhook.url", "Webhook URL", "Discord webhook for alerts and status reports.", "https://discord.com/api/webhooks/...", true,
                    () -> this.c.webhookUrl, v -> this.c.webhookUrl = v.trim()),
                this.text("webhook.ping", "Ping user id", "Discord user id to mention on failsafes.", "123456789012345678", false,
                    () -> this.c.pingId, v -> this.c.pingId = v.replaceAll("\\D", "")),
                this.slider("webhook.status", "Status report every", "Minutes between status messages (0 = off).", 0, 120, 5, " min",
                    () -> this.c.statusEvery, v -> this.c.statusEvery = (int) v),
                this.button("webhook.test", "Test webhook", "Sends a test message.", "Send", this.macro::testWebhook))),
            new Section("In game", List.of(
                this.toggle("alerts.toasts", "Notifications", "Shows pop-up notifications on screen.",
                    () -> this.c.toasts, v -> this.c.toasts = v)))
        ));
    }

    private Category visuals() {
        return new Category("Visuals", "✦", List.of(
            new Section("HUD", List.of(
                this.toggle("hud.status", "Status HUD", "Shows what the macro is doing.", () -> this.c.hud, v -> this.c.hud = v),
                this.toggle("hud.tracker", "Loot tracker", "Shows items gained this session with rates per hour.",
                    () -> this.c.itemTracker, v -> this.c.itemTracker = v),
                this.toggle("hud.prices", "Profit estimate", "Prices tracked loot at Bazaar instant-sell (fetched from api.hypixel.net).",
                    () -> this.c.bazaarPrices, v -> this.c.bazaarPrices = v).visibleWhen(() -> this.c.itemTracker),
                this.toggle("hud.targets", "Highlight targets", "Outlines the block or spot the macro is working on.",
                    () -> this.c.showTarget, v -> this.c.showTarget = v),
                this.button("hud.edit", "HUD layout", "Drag the HUD panels where you want them.", "Edit",
                    () -> HudEditorScreen.open(this.macro, () -> ClickGuiScreen.open(this.macro, this.categories))))),
            new Section("Theme", List.of(
                this.choice("theme.accent", "Accent color", "Highlight color of the menu and HUD.",
                    () -> Theme.ACCENT_NAMES, MacroSettings::capitalize, () -> this.c.accent, v -> this.c.accent = v)))
        ));
    }

    private Category general() {
        return new Category("General", "⚙", List.of(
            new Section("Window", List.of(
                this.toggle("general.ungrab", "Release mouse", "Frees the cursor while running so you can tab out.",
                    () -> this.c.ungrab, v -> this.c.ungrab = v),
                this.toggle("general.background", "Run unfocused", "Keeps running when the window loses focus.",
                    () -> this.c.keepRunningUnfocused, v -> this.c.keepRunningUnfocused = v))),
            new Section("Auto sell", List.of(
                this.toggle("sell.enabled", "Sell when full", "Sells listed items through /trades when the inventory fills (not commissions).",
                    () -> this.c.autoSell, v -> this.c.autoSell = v),
                this.text("sell.items", "Items to sell", "Parts of item names to sell, comma separated. The hotbar is never sold.", "e.g. Ectoplasm, Raw Fish", false,
                    () -> String.join(", ", this.c.sellItems), v -> this.c.sellItems = splitList(v)).visibleWhen(() -> this.c.autoSell))),
            new Section("Menu solvers", List.of(
                this.toggle("solver.experiments", "Experiment solver", "Solves Ultrasequencer and Chronomatron when you open them.",
                    () -> this.c.solveExperiments, v -> this.c.solveExperiments = v),
                this.toggle("solver.harp", "Harp solver", "Plays Melody's Harp songs when you open the harp.",
                    () -> this.c.solveHarp, v -> this.c.solveHarp = v),
                this.toggle("solver.forge", "Forge auto-claim", "Claims finished items while the Forge menu is open.",
                    () -> this.c.forgeAutoClaim, v -> this.c.forgeAutoClaim = v),
                this.text("solver.forgemenu", "Forge menu title", "Title of the Forge menu.", "The Forge", false,
                    () -> this.c.forgeMenu, v -> this.c.forgeMenu = v).visibleWhen(() -> this.c.forgeAutoClaim),
                this.text("solver.forgeclaim", "Claim text", "Text in a finished slot's lore.", "Claim", false,
                    () -> this.c.forgeClaimText, v -> this.c.forgeClaimText = v).visibleWhen(() -> this.c.forgeAutoClaim),
                this.slider("solver.delay", "Experiment click delay", "Pause between experiment clicks.", 50, 1000, 10, " ms",
                    () -> this.c.solverClickDelay, v -> this.c.solverClickDelay = (int) v))),
            new Section("Movement", List.of(
                this.toggle("general.sprint", "Sprint when walking", "Sprints on long straight path sections.",
                    () -> this.c.sprint, v -> this.c.sprint = v)))
        ));
    }

    // ---- helpers ----

    private Setting start(String id, MacroType.Category category) {
        return new Button(id, "Start / stop", "Runs the " + category.label.toLowerCase() + " macro (same as the toggle key).", "Toggle", () -> {
            MacroType type = category == MacroType.Category.MINING
                ? (this.macro.selected().category == category ? this.macro.selected() : MacroType.MITHRIL)
                : MacroType.SELECTABLE.stream().filter(t -> t.category == category).findFirst().orElseThrow();
            if (this.macro.running() && this.macro.mode() == type) {
                this.macro.stop("Stopped");
            } else {
                Minecraft.getInstance().gui.setScreen(null);
                this.macro.start(type);
            }
        });
    }

    private void setRewarpHere() {
        BlockPos pos = this.playerPos();
        if (pos != null) {
            this.c.farmRewarp = new int[]{pos.getX(), pos.getY(), pos.getZ()};
            this.saved("Rewarp point saved at " + pos.toShortString());
        }
    }

    private BlockPos playerPos() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? null : player.blockPosition();
    }

    private void saved(String text) {
        this.c.save();
        MinerMod.message(text, ChatFormatting.GREEN);
        Toasts.push("Saved", text, Toasts.Kind.SUCCESS);
    }

    private List<String> routeNames() {
        List<String> names = new ArrayList<>(Routes.saved());
        if (!names.contains(this.c.route)) {
            names.add(0, this.c.route);
        }
        return names;
    }

    private static List<String> splitList(String text) {
        return new ArrayList<>(Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private Setting toggle(String id, String name, String description, BooleanSupplier get, Consumer<Boolean> set) {
        return new Toggle(id, name, description, get, set).onChange(this::changed);
    }

    private Setting slider(String id, String name, String description, double min, double max, double step, String unit,
        DoubleSupplier get, DoubleConsumer set) {
        return new Slider(id, name, description, min, max, step, unit, get, set).onChange(this::changed);
    }

    private Setting choice(String id, String name, String description, Supplier<List<String>> options, Function<String, String> label,
        Supplier<String> get, Consumer<String> set) {
        return new Choice(id, name, description, options, label, get, set).onChange(this::changed);
    }

    private Setting text(String id, String name, String description, String placeholder, boolean secret, Supplier<String> get,
        Consumer<String> set) {
        return new Text(id, name, description, placeholder, secret, 200, get, set).onChange(this::changed);
    }

    private Setting longText(String id, String name, String description, String placeholder, Supplier<String> get, Consumer<String> set) {
        return new Text(id, name, description, placeholder, false, 2000, get, set).onChange(this::changed);
    }

    private Setting button(String id, String name, String description, String label, Runnable action) {
        return new Button(id, name, description, label, action);
    }
}
