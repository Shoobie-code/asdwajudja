package com.skyblockminer.macro.combat;

import java.util.List;

/** Mobs to fight: name-tag words to look for, and where such a mob may stand. */
public record Mob(List<String> names, Mob.Bounds bounds) {
    public interface Bounds {
        boolean contains(double x, double y, double z);
    }
}
