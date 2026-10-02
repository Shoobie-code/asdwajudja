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
    public boolean slayerCommissions = true;
    public int weaponSlot = 0;
    public int avoidRadius = 10;
    public boolean sellTrash = true;
    public boolean failsafes = true;
    public boolean chatAlerts = true;
    public int playerRadius = 8;
    public boolean stopForPlayers = false;
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
    public String accent = "violet";
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

    private transient int revision;
    private transient boolean dirty;
    private transient long dirtySince;

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
        this.rotationSpeed = clamp(this.rotationSpeed, 1, 100);
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
