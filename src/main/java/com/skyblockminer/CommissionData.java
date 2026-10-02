package com.skyblockminer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

final class CommissionData {
    static final List<Vec3> EMISSARIES = List.of(
        v(129, 194, 194), v(42, 134, 22), v(171, 149, 31), v(-73, 152, -11), v(-133, 173, -51), v(-38, 199, -132), v(58, 197, -9)
    );
    static final List<String> TRASH = List.of("Mithril", "Titanium", "Rune", "Glacite", "Goblin", "Cobblestone", "Stone");
    static final CommissionData.Mob GOBLIN = new CommissionData.Mob(
        List.of("Goblin", "Weakling", "Knifethrower", "Fireslinger"), (x, y, z) -> y > 127.0 && (!(z > 153.0) || !(x < -157.0)) && (!(z < 148.0) || !(x > -77.0))
    );
    static final CommissionData.Mob ICE_WALKER = new CommissionData.Mob(
        List.of("Ice Walker", "Glacite Walker"), (x, y, z) -> y >= 127.0 && y <= 136.0 && z <= 180.0 && z >= 134.0 && x <= 80.0
    );
    static final CommissionData.Mob TREASURE = new CommissionData.Mob(List.of("Treasuer Hunter", "Treasure Hunter"), (x, y, z) -> y >= 200.0 && y <= 210.0);
    private static final List<CommissionData.Commission> MINING_AREAS = List.of(
        new CommissionData.Commission(
            List.of("Royal Mines Titanium", "Royal Mines Mithril"),
            CommissionData.Type.MINING,
            5,
            List.of(v(141, 151, 24), v(173, 149, 70), v(166, 148, 90)),
            null
        ),
        new CommissionData.Commission(
            List.of("Cliffside Veins Mithril", "Cliffside Veins Titanium"),
            CommissionData.Type.MINING,
            10,
            List.of(v(46, 134, 11), v(25, 128, 27), v(10, 127, 37)),
            null
        ),
        new CommissionData.Commission(
            List.of("Upper Mines Titanium", "Upper Mines Mithril"),
            CommissionData.Type.MINING,
            15,
            List.of(v(-113, 166, -75), v(-125, 170, -76), v(-78, 187, -74)),
            null
        ),
        new CommissionData.Commission(
            List.of("Rampart's Quarry Titanium", "Rampart's Quarry Mithril"),
            CommissionData.Type.MINING,
            15,
            List.of(v(-87, 146, -14), v(-118, 149, -31), v(-116, 149, -25)),
            null
        ),
        new CommissionData.Commission(
            List.of("Lava Springs Mithril", "Lava Springs Titanium"), CommissionData.Type.MINING, 20, List.of(v(50, 197, -26), v(42, 197, -20)), null
        )
    );
    static final List<CommissionData.Commission> ALL = build();

    private CommissionData() {
    }

    private static List<CommissionData.Commission> build() {
        List<CommissionData.Commission> all = new ArrayList<>(MINING_AREAS);
        List<Vec3> everywhere = new ArrayList<>();
        MINING_AREAS.forEach(area -> everywhere.addAll(area.spots()));
        all.add(new CommissionData.Commission(List.of("Titanium Miner", "Mithril Miner"), CommissionData.Type.MINING, 12, everywhere, null));
        all.add(new CommissionData.Commission(List.of("Goblin Slayer"), CommissionData.Type.SLAYER, 30, List.of(v(-130, 145, 147)), "goblin"));
        all.add(
            new CommissionData.Commission(List.of("Glacite Walker Slayer", "Mines Slayer"), CommissionData.Type.SLAYER, 25, List.of(v(0, 127, 157)), "icewalker")
        );
        all.add(new CommissionData.Commission(List.of("Treasure Hoarder Puncher"), CommissionData.Type.SLAYER, 25, List.of(v(-117, 204, -56)), "treasure"));
        return all;
    }

    static CommissionData.Commission find(String name) {
        for (CommissionData.Commission commission : ALL) {
            if (commission.names().contains(name)) {
                return commission;
            }
        }

        return null;
    }

    static boolean isKnown(String name) {
        return find(name) != null;
    }

    static CommissionData.Mob mob(String key) {
        return switch (key) {
            case "goblin" -> GOBLIN;
            case "icewalker" -> ICE_WALKER;
            default -> TREASURE;
        };
    }

    private static Vec3 v(int x, int y, int z) {
        return new Vec3(x + 0.5, y, z + 0.5);
    }

    interface Bounds {
        boolean contains(double x, double y, double z);
    }

    record Commission(List<String> names, CommissionData.Type type, int cost, List<Vec3> spots, String mob) {
        boolean titanium(String name) {
            return name.contains("Titanium");
        }
    }

    record Mob(List<String> names, CommissionData.Bounds bounds) {
    }

    static enum Type {
        MINING,
        SLAYER;
    }
}
