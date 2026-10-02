package com.mojang.brigadier.arguments;
public class StringArgumentType implements ArgumentType<String> {
    public static StringArgumentType word() { return null; }
    public static StringArgumentType string() { return null; }
    public static StringArgumentType greedyString() { return null; }
    public static String getString(com.mojang.brigadier.context.CommandContext<?> c, String name) { return null; }
}
