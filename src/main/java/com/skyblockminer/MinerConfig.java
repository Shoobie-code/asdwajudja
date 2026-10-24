package com.skyblockminer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class MinerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public String mode = "mithril";
    public List<String> customBlocks = new ArrayList<>();
    public boolean prioritizeTitanium = true;
    public int rotationSpeed = 48;
    /** How far turns miss before correcting, in percent of natural (0 lands exactly). */
    public int overshoot = 100;
    /** Typical pause before turning to something new, in ms. */
    public int reactionTime = 150;
    /** Split wide turns into strokes with a pause, as when lifting the mouse. */
    public boolean mouseLifts = true;
    /** Let the view wander a few tenths of a degree while holding on a target. */
    public boolean idleDrift = true;
    public double reach = 4.5;
    public boolean sneak = true;
    public boolean useAbility = true;
    public List<String> gemstones = new ArrayList<>(Targets.GEMSTONES.keySet());
    public boolean openChests = true;
    public String route = "default";
    public String routeBlocks = "gemstone";
    public boolean etherwarp = true;
    public boolean showRoute = true;
    public int powderRadius = 32;
    public int powderWidth = 1;
    public int heatLimit = 90;
    public int coldLimit = 80;
    public boolean randomize = true;
    public int aimSpread = 35;
    public boolean sprint = true;
    /** Sprint-jump along long, level stretches of a path (faster than sprinting). */
    public boolean sprintJump = true;
    public boolean slayerCommissions = true;
    public int weaponSlot = 0;
    public int avoidRadius = 10;
    public boolean sellTrash = true;
    public boolean failsafes = true;
    public boolean chatAlerts = true;
    public int playerRadius = 8;
    public boolean stopForPlayers = false;
    /** Private-message another account when a failsafe catches a macro check. */
    public boolean altAlert = true;
    public String altName = "";
    public String altMessage = AltAlert.DEFAULT_MESSAGE;
    public int altCooldown = 5;
    public int breakEvery = 0;
    public int breakLength = 5;
    public String webhookUrl = "";
    public String pingId = "";
    public int statusEvery = 0;
    public boolean ungrab = true;
    public boolean keepRunningUnfocused = true;
    public boolean autoRejoin = true;
    public boolean hud = true;
    public boolean oreWalk = true;
    public boolean pathSafety = true;
    /** Draw the path the walker is following. */
    public boolean showPath = false;
    /** How the walker travels: walk, etherwarp, transmission (Instant Transmission) or both teleports. */
    public String movement = "walk";
    public double etherwarpRange = 57.0;
    public double transmissionRange = 8.0;
    public int warpMinGain = 12;
    public int oreWalkRange = 64;

    // Farming
    public String farmPattern = "vertical";
    public String farmCustomLeft = "A+W";
    public String farmCustomRight = "D+W";
    public boolean farmKeepYaw = true;
    public boolean farmSnapYaw = true;
    public double farmYaw = 0.0;
    public double farmPitch = 3.0;
    public int farmSwitchTicks = 8;
    public int farmToolSlot = 0;
    public int[] farmRewarp = null;
    public String farmWarpCommand = "warp garden";
    public boolean farmRewarpWhenStuck = true;
    public String farmPestAction = "notify";

    // Builder
    public int[] buildPos1 = null;
    public int[] buildPos2 = null;
    public String buildPattern = "lanes";
    public String buildBlock = "dirt";
    public int buildWaterEvery = 9;
    public int buildDelay = 120;

    // Foraging
    public int[] forageSpot = null;
    public boolean forageBonemeal = true;
    public boolean forageGrass = false;
    public int forageActionDelay = 180;
    public String forageWarpCommand = "is";
    public List<int[]> forageRoute = new ArrayList<>();

    // Fishing
    public double[] fishSpot = null;
    public int fishReelDelay = 180;
    public int fishRecastDelay = 450;
    public int fishTimeout = 30;
    public boolean fishKillCreatures = true;
    public int fishCreatureLimit = 1;
    public String fishAttackMode = "melee";
    public int fishWeaponSlot = 0;
    public String fishWarpCommand = "";

    // Combat
    public List<String> combatMobs = new ArrayList<>();
    public int combatRadius = 20;
    public double[] combatSpot = null;
    public int combatWeaponSlot = 0;
    public String combatAttackMode = "melee";
    public String combatWarpCommand = "";

    // Wynncraft combat
    public String wynnClass = "warrior";
    public List<String> wynnMobs = new ArrayList<>();
    public int wynnRadius = 16;
    public double[] wynnSpot = null;
    public int wynnWeaponSlot = 0;
    public int wynnRange = 0;
    public String wynnSpells = "1";
    public double wynnSpellSeconds = 2.0;
    public int wynnMinMana = 30;
    public int wynnHealSpell = 0;
    public int wynnHealBelow = 50;
    public int wynnClickTicks = 2;

    // Auto sell and schedule
    public boolean autoSell = false;
    public List<String> sellItems = new ArrayList<>();
    public String activeHours = "";

    // Glacite commissions and excavator
    public List<String> glaciteRules = new ArrayList<>(List.of(
        "Walker Slayer=mob:Glacite Walker,Ice Walker",
        "Glacite=packed_ice",
        "Umber=terracotta,brown_terracotta,smooth_red_sandstone",
        "Tungsten=clay,infested_cobblestone",
        "Onyx=black_stained_glass,black_stained_glass_pane",
        "Aquamarine=blue_stained_glass,blue_stained_glass_pane",
        "Citrine=brown_stained_glass,brown_stained_glass_pane",
        "Peridot=green_stained_glass,green_stained_glass_pane"
    ));
    public String glaciteWarpCommand = "warp camp";
    public String glaciteClaimItem = "Royal Pigeon";
    public String excavatorScrap = "Suspicious Scrap";
    public String excavatorMenu = "Fossil Excavator";
    public String excavatorDigMenu = "Fossil Excavator";
    public String excavatorStart = "Start Excavator";
    public String excavatorTile = "Dirt";

    // Garden visitors
    public boolean visitorsEnabled = false;
    public double[] visitorSpot = null;
    public int visitorMin = 1;
    public String visitorAccept = "Accept Offer";

    // Slayer auto-start
    public boolean slayerAutoStart = false;
    public String slayerOpenCommand = "";
    public String slayerPhoneItem = "Maddox Batphone";
    public String slayerBoss = "Revenant Horror";
    public String slayerTier = "IV";
    public String slayerConfirmItem = "Confirm";

    // Forge
    public boolean forgeAutoClaim = false;
    public String forgeMenu = "The Forge";
    public String forgeClaimText = "Claim";

    // Menu solvers
    public boolean solveExperiments = false;
    public boolean solveHarp = false;
    public int solverClickDelay = 250;

    // Interface
    public String accent = com.skyblockminer.gui.Theme.DEFAULT_ACCENT;
    /** Off = no macro can start (the menu's master switch). */
    public boolean masterSwitch = true;
    public int guiOpacity = 92;
    public boolean toasts = true;
    public boolean itemTracker = true;
    public boolean bazaarPrices = true;
    public boolean showTarget = true;
    public int hudX = 4;
    public int hudY = 4;
    public int trackerX = 4;
    public int trackerY = 92;
    public int toastX = 4;
    public int toastY = 170;
    public String guiCategory = "Mining";

    // Market
    public boolean marketEnabled = true;
    public double bazaarBudget = 10_000_000;
    public int bazaarMinVolume = 5000;
    public double bazaarMinMargin = 2.0;
    public boolean bazaarFlipperPerk = false;
    public boolean auctionScan = false;
    public int auctionMinProfit = 500_000;
    public double auctionMinMargin = 8.0;
    public int auctionMinListings = 4;
    public int auctionMaxPrice = 50_000_000;
    public boolean auctionAutoOpen = false;
    public boolean auctionAutoBuy = false;
    public int craftMinProfit = 500;
    public double npcBudget = 1_000_000;

    // Skills
    public boolean skillTracker = true;
    public int skillsX = 4;
    public int skillsY = 230;
    public java.util.Map<String, Double> skillBest = new java.util.HashMap<>();

    // Helpers (ideas from SkyHanni)
    public boolean infoPanel = true;
    public int infoEvents = 4;
    public int infoX = 4;
    public int infoY = 330;
    public boolean gardenAlerts = true;
    public int pestAlertAt = 4;
    public boolean chatFilter = false;
    public boolean filterLobby = true;
    public boolean filterWatchdog = true;
    public boolean filterProfile = true;
    public boolean filterAbility = true;
    public boolean filterCombo = true;
    public List<String> filterCustom = new ArrayList<>();

    private transient int revision;
    private transient boolean dirty;
    private transient long dirtySince;

    public String profile = "default";

    private static Path profiles() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyblockminer").resolve("profiles");
    }

    /** Names of saved profiles, sorted. */
    public static List<String> savedProfiles() {
        if (!Files.isDirectory(profiles())) {
            return List.of();
        }
        try (java.util.stream.Stream<Path> files = Files.list(profiles())) {
            return files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".json"))
                .map(n -> n.substring(0, n.length() - 5)).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public static boolean validProfile(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    /** Writes every setting to profiles/<name>.json. */
    public boolean saveProfile(String name) {
        if (!validProfile(name)) {
            return false;
        }
        try {
            Files.createDirectories(profiles());
            this.profile = name;
            Files.writeString(profiles().resolve(name + ".json"), GSON.toJson(this));
            this.save();
            return true;
        } catch (IOException e) {
            MinerMod.LOGGER.warn("Could not save profile {}", name, e);
            return false;
        }
    }

    /** Replaces every setting with the saved profile's values (in place, so live references stay valid). */
    public boolean loadProfile(String name) {
        Path file = profiles().resolve(name + ".json");
        if (!validProfile(name) || !Files.exists(file)) {
            return false;
        }
        try {
            MinerConfig loaded = GSON.fromJson(Files.readString(file), MinerConfig.class);
            if (loaded == null) {
                return false;
            }
            loaded.sanitize();
            for (Field field : MinerConfig.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())) {
                    field.set(this, field.get(loaded));
                }
            }
            this.profile = name;
            this.save();
            return true;
        } catch (IOException | RuntimeException | IllegalAccessException e) {
            MinerMod.LOGGER.warn("Could not load profile {}", name, e);
            return false;
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyblockminer.json");
    }

    public static MinerConfig load() {
        try {
            if (Files.exists(path())) {
                MinerConfig config = GSON.fromJson(Files.readString(path()), MinerConfig.class);
                if (config != null) {
                    config.sanitize();
                    return config;
                }
            }
        } catch (RuntimeException | IOException e) {
            MinerMod.LOGGER.warn("Could not read skyblockminer.json, using defaults", e);
        }

        MinerConfig config = new MinerConfig();
        config.save();
        return config;
    }

    /** Replaces nulls left by hand edits or older versions with defaults and clamps ranges. */
    private void sanitize() {
        MinerConfig defaults = new MinerConfig();
        try {
            for (Field field : MinerConfig.class.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && field.get(this) == null && field.get(defaults) != null) {
                    field.set(this, field.get(defaults));
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }

        if (this.route.isBlank()) {
            this.route = "default";
        }
        if (!Targets.MODES.contains(this.routeBlocks)) {
            this.routeBlocks = "gemstone";
        }
        if (MacroType.parse(this.mode) == null) {
            this.mode = "mithril";
        }
        if (FarmingMacro.Pattern.parse(this.farmPattern) == null) {
            this.farmPattern = defaults.farmPattern;
        }
        if (this.farmRewarp != null && this.farmRewarp.length != 3) {
            this.farmRewarp = null;
        }
        if (this.buildPos1 != null && this.buildPos1.length != 3) {
            this.buildPos1 = null;
        }
        if (this.buildPos2 != null && this.buildPos2.length != 3) {
            this.buildPos2 = null;
        }
        if (this.forageSpot != null && this.forageSpot.length != 3) {
            this.forageSpot = null;
        }
        if (this.fishSpot != null && this.fishSpot.length != 5) {
            this.fishSpot = null;
        }
        this.forageRoute.removeIf(point -> point == null || point.length != 3);
        if (this.visitorSpot != null && this.visitorSpot.length != 3) {
            this.visitorSpot = null;
        }
        if (this.combatSpot != null && this.combatSpot.length != 3) {
            this.combatSpot = null;
        }
        if (!this.combatAttackMode.equals("melee") && !this.combatAttackMode.equals("use")) {
            this.combatAttackMode = defaults.combatAttackMode;
        }
        if (!Schedule.valid(this.activeHours)) {
            this.activeHours = "";
        }
        this.combatRadius = clamp(this.combatRadius, 4, 64);
        if (WynnSpells.WynnClass.parse(this.wynnClass) == null) {
            this.wynnClass = defaults.wynnClass;
        }
        if (this.wynnSpot != null && this.wynnSpot.length != 3) {
            this.wynnSpot = null;
        }
        this.wynnRadius = clamp(this.wynnRadius, 4, 64);
        this.wynnWeaponSlot = clamp(this.wynnWeaponSlot, 0, 9);
        this.wynnRange = clamp(this.wynnRange, 0, 20);
        this.wynnSpellSeconds = Math.max(0.5, Math.min(this.wynnSpellSeconds, 30.0));
        this.wynnMinMana = clamp(this.wynnMinMana, 0, 100);
        this.wynnHealSpell = clamp(this.wynnHealSpell, 0, 4);
        this.wynnHealBelow = clamp(this.wynnHealBelow, 5, 95);
        this.wynnClickTicks = clamp(this.wynnClickTicks, 1, 10);
        this.rotationSpeed = clamp(this.rotationSpeed, 1, 100);
        this.overshoot = clamp(this.overshoot, 0, 300);
        this.reactionTime = clamp(this.reactionTime, 0, 600);
        if (!List.of("walk", "etherwarp", "transmission", "both").contains(this.movement)) {
            this.movement = defaults.movement;
        }
        this.etherwarpRange = Math.max(10.0, Math.min(this.etherwarpRange, 61.0));
        this.transmissionRange = Math.max(4.0, Math.min(this.transmissionRange, 12.0));
        this.warpMinGain = clamp(this.warpMinGain, 4, 60);
        this.infoEvents = clamp(this.infoEvents, 0, 6);
        this.guiOpacity = clamp(this.guiOpacity, 40, 100);
        if (!com.skyblockminer.gui.Theme.ACCENTS.containsKey(this.accent)) {
            this.accent = defaults.accent;
        }
        this.pestAlertAt = clamp(this.pestAlertAt, 1, 8);
        this.reach = Math.max(1.0, Math.min(this.reach, 6.0));
        this.farmSwitchTicks = clamp(this.farmSwitchTicks, 2, 60);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /** Schedules a save; the GUI changes many values in a row, so writes are batched by {@link #saveIfDirty()}. */
    public void markDirty() {
        this.revision++;
        if (!this.dirty) {
            this.dirty = true;
            this.dirtySince = System.currentTimeMillis();
        }
    }

    /** Called every tick: writes pending changes at most once a second. */
    public void saveIfDirty() {
        if (this.dirty && System.currentTimeMillis() - this.dirtySince > 1000L) {
            this.save();
        }
    }

    /** Changes whenever a setting changes, so derived data can be cached. */
    public int revision() {
        return this.revision;
    }

    public void save() {
        this.revision++;
        this.dirty = false;
        try {
            Path target = path();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            Files.writeString(temp, GSON.toJson(this));
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            MinerMod.LOGGER.warn("Could not save skyblockminer.json", e);
        }
    }
}
