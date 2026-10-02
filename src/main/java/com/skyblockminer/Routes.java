package com.skyblockminer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.Vec3;

final class Routes {
    private static final int POINT = -4174593;
    private static final int POINT_FILL = 817908991;
    private static final int NEXT = -11141291;
    private static final int LINE = -1061139201;
    private static final double DRAW_DISTANCE = 128.0;
    private final List<BlockPos> points = new ArrayList<>();
    private String name = "default";

    List<BlockPos> points() {
        return this.points;
    }

    String name() {
        return this.name;
    }

    static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("skyblockminer").resolve("routes");
    }

    private static Path file(String name) {
        return dir().resolve(name + ".json");
    }

    static boolean validName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,40}");
    }

    boolean load(String name) {
        this.name = name;
        this.points.clear();
        Path file = file(name);
        if (!Files.exists(file)) {
            return false;
        } else {
            try {
                this.points.addAll(parse(Files.readString(file)));
                return true;
            } catch (RuntimeException | IOException e) {
                MinerMod.LOGGER.warn("Could not read route {}", file, e);
                return false;
            }
        }
    }

    void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(file(this.name), toJson(this.points));
        } catch (IOException e) {
            MinerMod.LOGGER.warn("Could not save route {}", this.name, e);
        }
    }

    void rename(String name) {
        this.name = name;
    }

    void set(List<BlockPos> route) {
        this.points.clear();
        this.points.addAll(route);
    }

    static List<String> saved() {
        if (!Files.isDirectory(dir())) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir())) {
            return files.map(path -> path.getFileName().toString())
                .filter(file -> file.endsWith(".json"))
                .map(file -> file.substring(0, file.length() - 5))
                .sorted()
                .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    int nearest(Vec3 from) {
        int best = -1;
        double bestDistance = Double.MAX_VALUE;

        for (int i = 0; i < this.points.size(); i++) {
            double distance = Vec3.atCenterOf((Vec3i)this.points.get(i)).distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }

        return best;
    }

    static List<BlockPos> parse(String json) {
        JsonElement root = JsonParser.parseString(json.trim());
        JsonArray array = null;
        if (root.isJsonArray()) {
            array = root.getAsJsonArray();
        } else if (root.isJsonObject()) {
            JsonObject object = root.getAsJsonObject();

            for (String key : new String[]{"points", "waypoints", "route"}) {
                if (object.has(key) && object.get(key).isJsonArray()) {
                    array = object.getAsJsonArray(key);
                    break;
                }
            }
        }

        if (array == null) {
            throw new IllegalArgumentException("not a list of points");
        } else {
            List<BlockPos> route = new ArrayList<>();

            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    JsonObject point = element.getAsJsonObject();
                    if (point.has("x") && point.has("y") && point.has("z")) {
                        route.add(BlockPos.containing(point.get("x").getAsDouble(), point.get("y").getAsDouble(), point.get("z").getAsDouble()));
                    }
                } else if (element.isJsonArray() && element.getAsJsonArray().size() >= 3) {
                    JsonArray point = element.getAsJsonArray();
                    route.add(BlockPos.containing(point.get(0).getAsDouble(), point.get(1).getAsDouble(), point.get(2).getAsDouble()));
                }
            }

            if (route.isEmpty()) {
                throw new IllegalArgumentException("no points with x, y and z");
            } else {
                return route;
            }
        }
    }

    static String toJson(List<BlockPos> route) {
        StringBuilder json = new StringBuilder("[\n");

        for (int i = 0; i < route.size(); i++) {
            BlockPos point = route.get(i);
            json.append(String.format("  {\"x\": %d, \"y\": %d, \"z\": %d}", point.getX(), point.getY(), point.getZ()));
            json.append(i < route.size() - 1 ? ",\n" : "\n");
        }

        return json.append("]\n").toString();
    }

    void render(LocalPlayer player, int next) {
        if (!this.points.isEmpty()) {
            Vec3 here = player.position();
            double maxSq = 16384.0;

            for (int i = 0; i < this.points.size(); i++) {
                BlockPos point = this.points.get(i);
                boolean near = Vec3.atCenterOf(point).distanceToSqr(here) <= maxSq;
                if (near) {
                    int color = i == next ? -11141291 : -4174593;
                    Gizmos.cuboid(point, GizmoStyle.strokeAndFill(color, 2.0F, i == next ? 810942293 : 817908991));
                    Gizmos.billboardTextOverBlock(String.valueOf(i + 1), point, 0, color, 0.32F);
                }

                if (this.points.size() >= 2) {
                    BlockPos to = this.points.get((i + 1) % this.points.size());
                    if (near || !(Vec3.atCenterOf(to).distanceToSqr(here) > maxSq)) {
                        Gizmos.line(Vec3.atCenterOf(point).add(0.0, 0.5, 0.0), Vec3.atCenterOf(to).add(0.0, 0.5, 0.0), -1061139201, 2.0F);
                    }
                }
            }
        }
    }
}
