package com.mojang.brigadier.arguments;
public class DoubleArgumentType implements ArgumentType<Double> {
    public static DoubleArgumentType doubleArg() { return null; }
    public static DoubleArgumentType doubleArg(double min) { return null; }
    public static DoubleArgumentType doubleArg(double min, double max) { return null; }
    public static double getDouble(com.mojang.brigadier.context.CommandContext<?> c, String name) { return 0; }
}
