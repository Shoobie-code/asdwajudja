package com.skyblockminer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.skyblockminer.MinerMod;
import com.skyblockminer.macro.AltAlert;
import com.skyblockminer.macro.MacroType;
import com.skyblockminer.macro.Schedule;
import com.skyblockminer.macro.farming.FarmingMacro;
import com.skyblockminer.macro.mining.Targets;
import com.skyblockminer.macro.wynncraft.WynnSpells;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public final class MinerConfig {
    /** The last mod version that showed its "what changed" notice, so the notice appears once per update. */
    public String lastSeenVersion = "";
    /** The Discord webhook URL contains its token, so it is never written to a profile. */
    static final String WEBHOOK_FIELD = "webhookUrl";
    /** Version tag on a shared profile code, so a future format change can be told apart. */
    static final String CODE_PREFIX = "SM1-";
    /** Backups of replaced configs live here, next to the config file. */
    static final String BACKUP_DIR = "skyblockminer-backups";
    static final int KEEP_BACKUPS = 5;
    /** A backup is taken at most this often, so frequent saves do not fill the folder. */
    static final long BACKUP_EVERY_MS = 10L * 60_000L;
    private long lastBackupAt;
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
    /** Comma-separated Discord events to send (see Webhook.EVENTS). Failsafes and chat alerts are not in this list. */
    public String discordEvents = String.join(",", com.skyblockminer.macro.Webhook.EVENTS);
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

    /** File name of the userscript the Userscript macro runs, in config/skyblockminer/scripts. */
    public String script = "example.js";
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
    public boolean showWaypoints = true;
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
    /** Named HUD layouts: each is the panel positions captured by {@link #captureHud()}. */
    public Map<String, int[]> hudPresets = new LinkedHashMap<>();
    /** User command aliases: short name to the rest of a /sm command, run by {@code /sm alias run <name>}. */
    public Map<String, String> aliases = new LinkedHashMap<>();
    /** Named world waypoints, each [x, y, z], drawn in the world and listed by {@code /sm waypoint}. */
    public Map<String, int[]> waypoints = new LinkedHashMap<>();
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

    /** A valid command-alias name: a short word, so it cannot inject spaces or arguments. */
    public static boolean validAlias(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,24}");
    }

    /** Whether {@code current} is a real version that differs from the last one a changelog notice was shown for. */
    public static boolean isNewVersion(String current, String lastSeen) {
        return current != null && !current.isBlank() && !current.equals(lastSeen);
    }

    /** The current positions of every HUD panel, in a fixed order, so a layout can be saved and restored. */
    int[] captureHud() {
        return new int[]{this.hudX, this.hudY, this.trackerX, this.trackerY, this.toastX, this.toastY,
            this.skillsX, this.skillsY, this.infoX, this.infoY};
    }

    /** Sets the HUD panel positions from an array made by {@link #captureHud()}. Ignores arrays of the wrong size. */
    void applyHud(int[] v) {
        if (v == null || v.length != 10) {
            return;
        }
        this.hudX = v[0];
        this.hudY = v[1];
        this.trackerX = v[2];
        this.trackerY = v[3];
        this.toastX = v[4];
        this.toastY = v[5];
        this.skillsX = v[6];
        this.skillsY = v[7];
        this.infoX = v[8];
        this.infoY = v[9];
    }

    /** Saves the current HUD layout under {@code name}. Returns false for an invalid name. */
    public boolean saveHudPreset(String name) {
        if (!validProfile(name)) {
            return false;
        }
        this.hudPresets.put(name, this.captureHud());
        this.save();
        return true;
    }

    /** Applies the saved HUD layout {@code name}. Returns false when there is no such preset. */
    public boolean loadHudPreset(String name) {
        int[] v = this.hudPresets.get(name);
        if (v == null) {
            return false;
        }
        this.applyHud(v);
        this.save();
        return true;
    }

    /** The saved HUD preset names, sorted. */
    public List<String> hudPresetNames() {
        return this.hudPresets.keySet().stream().sorted().toList();
    }

    /** Parses a saved profile into a config without applying it (for a diff), or null when it is missing or unreadable. */
    public static MinerConfig readProfile(String name) {
        if (!validProfile(name)) {
            return null;
        }
        Path file = profiles().resolve(name + ".json");
        if (!Files.exists(file)) {
            return null;
        }
        try {
            MinerConfig loaded = GSON.fromJson(Files.readString(file), MinerConfig.class);
            if (loaded != null) {
                loaded.sanitize();
            }
            return loaded;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** The settings as a profile file stores them: everything except secrets, which a shared profile must not carry. */
    static JsonObject shareable(MinerConfig config) {
        JsonObject json = GSON.toJsonTree(config).getAsJsonObject();
        json.addProperty(WEBHOOK_FIELD, "");
        return json;
    }

    /** Writes every setting to profiles/<name>.json, without the webhook URL (it is a secret). */
    public boolean saveProfile(String name) {
        if (!validProfile(name)) {
            return false;
        }
        try {
            Files.createDirectories(profiles());
            this.profile = name;
            Files.writeString(profiles().resolve(name + ".json"), GSON.toJson(shareable(this)));
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
            this.applyFrom(loaded);
            this.profile = name;
            this.save();
            return true;
        } catch (IOException | RuntimeException | IllegalAccessException e) {
            MinerMod.LOGGER.warn("Could not load profile {}", name, e);
            return false;
        }
    }

    /**
     * The settings that differ between {@code a} and {@code b}, as "field: a -> b" lines sorted by field name. The
     * webhook is left out, since shared configs never carry it. Used to show what a profile would change.
     */
    public static List<String> diff(MinerConfig a, MinerConfig b) {
        JsonObject ja = GSON.toJsonTree(a).getAsJsonObject();
        JsonObject jb = GSON.toJsonTree(b).getAsJsonObject();
        List<String> lines = new ArrayList<>();
        for (String key : new java.util.TreeSet<>(ja.keySet())) {
            if (key.equals(WEBHOOK_FIELD)) {
                continue;
            }
            String va = String.valueOf(ja.get(key));
            String vb = String.valueOf(jb.get(key));
            if (!va.equals(vb)) {
                lines.add(key + ": " + va + " -> " + vb);
            }
        }
        return lines;
    }

    /** Copies every non-secret setting from {@code loaded} into this config in place, so live references stay valid. */
    private void applyFrom(MinerConfig loaded) throws IllegalAccessException {
        for (Field field : MinerConfig.class.getFields()) {
            // Shared configs do not carry the webhook, so applying one must keep this install's webhook.
            if (!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                && !field.getName().equals(WEBHOOK_FIELD)) {
                field.set(this, field.get(loaded));
            }
        }
    }

    /**
     * A portable text code for this config's settings, without the webhook. It is gzipped JSON in URL-safe base64
     * behind a short version tag, so a profile can be shared in chat or a message. Read back with {@link #decode}.
     */
    static String encode(MinerConfig config) {
        try {
            byte[] json = GSON.toJson(shareable(config)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            var bytes = new java.io.ByteArrayOutputStream();
            try (var gzip = new java.util.zip.GZIPOutputStream(bytes)) {
                gzip.write(json);
            }
            return CODE_PREFIX + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parses a code from {@link #encode}, or null if it is not a valid code. The result is not yet sanitized. */
    static MinerConfig decode(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.strip();
        if (!trimmed.startsWith(CODE_PREFIX)) {
            return null;
        }
        try {
            byte[] gz = java.util.Base64.getUrlDecoder().decode(trimmed.substring(CODE_PREFIX.length()));
            var in = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(gz));
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            return GSON.fromJson(json, MinerConfig.class);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Returns this config's shareable code (see {@link #encode}). */
    public String exportCode() {
        return encode(this);
    }

    /** Applies a shared code in place, keeping this install's webhook. Returns false when the code is invalid. */
    public boolean importCode(String code) {
        if (!this.applyCode(code)) {
            return false;
        }
        this.save();
        return true;
    }

    /** The apply half of {@link #importCode}, without the disk save, so it can be tested without a Fabric runtime. */
    boolean applyCode(String code) {
        MinerConfig loaded = decode(code);
        if (loaded == null) {
            return false;
        }
        try {
            loaded.sanitize();
            this.applyFrom(loaded);
            return true;
        } catch (RuntimeException | IllegalAccessException e) {
            MinerMod.LOGGER.warn("Could not import a profile code", e);
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

    /** Replaces nulls left by hand edits or older versions with defaults and clamps ranges. Package-private for tests. */
    void sanitize() {
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
        this.discordEvents = java.util.Arrays.stream(this.discordEvents.split(",")).map(String::strip)
            .filter(com.skyblockminer.macro.Webhook.EVENTS::contains).distinct().collect(java.util.stream.Collectors.joining(","));
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
        this.wynnSpellSeconds = clamp(this.wynnSpellSeconds, 0.5, 30.0);
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
        this.etherwarpRange = clamp(this.etherwarpRange, 10.0, 61.0);
        this.transmissionRange = clamp(this.transmissionRange, 4.0, 12.0);
        this.warpMinGain = clamp(this.warpMinGain, 4, 60);
        this.infoEvents = clamp(this.infoEvents, 0, 6);
        this.guiOpacity = clamp(this.guiOpacity, 40, 100);
        if (!com.skyblockminer.gui.Theme.ACCENTS.containsKey(this.accent)) {
            this.accent = defaults.accent;
        }
        this.pestAlertAt = clamp(this.pestAlertAt, 1, 8);
        this.reach = clamp(this.reach, 1.0, 6.0);
        this.farmSwitchTicks = clamp(this.farmSwitchTicks, 2, 60);

        // Hotbar slots are 0-8; delays and radii must not go negative, or the macros loop or never fire.
        this.weaponSlot = clamp(this.weaponSlot, 0, 8);
        this.farmToolSlot = clamp(this.farmToolSlot, 0, 8);
        this.combatWeaponSlot = clamp(this.combatWeaponSlot, 0, 8);
        this.fishWeaponSlot = clamp(this.fishWeaponSlot, 0, 8);
        this.powderRadius = clamp(this.powderRadius, 1, 64);
        this.powderWidth = clamp(this.powderWidth, 1, 8);
        this.avoidRadius = clamp(this.avoidRadius, 0, 64);
        this.playerRadius = clamp(this.playerRadius, 0, 64);
        this.altCooldown = clamp(this.altCooldown, 0, 3600);
        this.breakEvery = clamp(this.breakEvery, 0, 100_000);
        this.breakLength = clamp(this.breakLength, 1, 3600);
        this.statusEvery = clamp(this.statusEvery, 0, 100_000);
        this.oreWalkRange = clamp(this.oreWalkRange, 1, 256);
        this.buildWaterEvery = clamp(this.buildWaterEvery, 1, 100);
        this.buildDelay = clamp(this.buildDelay, 0, 10_000);
        this.forageActionDelay = clamp(this.forageActionDelay, 0, 10_000);
        this.fishReelDelay = clamp(this.fishReelDelay, 0, 10_000);
        this.fishRecastDelay = clamp(this.fishRecastDelay, 0, 10_000);
        this.fishTimeout = clamp(this.fishTimeout, 1, 3600);
        this.fishCreatureLimit = clamp(this.fishCreatureLimit, 0, 100);
        this.visitorMin = clamp(this.visitorMin, 0, 100);
        this.solverClickDelay = clamp(this.solverClickDelay, 0, 10_000);
        this.bazaarBudget = Math.max(0.0, finite(this.bazaarBudget, defaults.bazaarBudget));
        this.bazaarMinVolume = Math.max(0, this.bazaarMinVolume);
        this.bazaarMinMargin = Math.max(0.0, finite(this.bazaarMinMargin, defaults.bazaarMinMargin));
        this.auctionMinProfit = Math.max(0, this.auctionMinProfit);
        this.auctionMinMargin = Math.max(0.0, finite(this.auctionMinMargin, defaults.auctionMinMargin));
        this.auctionMinListings = Math.max(0, this.auctionMinListings);
        this.auctionMaxPrice = Math.max(0, this.auctionMaxPrice);
        this.craftMinProfit = Math.max(0, this.craftMinProfit);
        this.npcBudget = Math.max(0.0, finite(this.npcBudget, defaults.npcBudget));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    /** Clamps a decimal setting. NaN (which hand-edited JSON can produce) becomes the minimum, because Math.min/max would keep it. */
    private static double clamp(double value, double min, double max) {
        return Double.isNaN(value) ? min : Math.max(min, Math.min(value, max));
    }

    /** Replaces NaN with a fallback. Infinities are left alone, so callers still apply their own bounds. */
    private static double finite(double value, double fallback) {
        return Double.isNaN(value) ? fallback : value;
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

    /** Writes the replaced config into the backups folder, then keeps only the newest {@link #KEEP_BACKUPS} files. */
    static void backup(String previous, Path dir, long now) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("skyblockminer-" + now + ".json"), previous);
        try (java.util.stream.Stream<Path> files = Files.list(dir)) {
            List<Path> backups = files.filter(f -> f.getFileName().toString().matches("skyblockminer-\\d+\\.json")).sorted().toList();
            for (int i = 0; i < backups.size() - KEEP_BACKUPS; i++) {
                Files.deleteIfExists(backups.get(i));
            }
        }
    }

    public void save() {
        this.revision++;
        this.dirty = false;
        try {
            Path target = path();
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            String json = GSON.toJson(this);
            long now = System.currentTimeMillis();
            if (Files.exists(target) && now - this.lastBackupAt >= BACKUP_EVERY_MS) {
                try {
                    String previous = Files.readString(target);
                    if (!previous.equals(json)) {
                        backup(previous, target.resolveSibling(BACKUP_DIR), now);
                        this.lastBackupAt = now;
                    }
                } catch (IOException e) {
                    // A failed backup must not stop the settings from being saved.
                    MinerMod.LOGGER.warn("Could not back up skyblockminer.json", e);
                }
            }
            Files.writeString(temp, json);
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
