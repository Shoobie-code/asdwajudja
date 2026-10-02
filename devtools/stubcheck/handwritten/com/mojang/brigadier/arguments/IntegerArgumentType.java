package com.mojang.brigadier.arguments;
public class IntegerArgumentType implements ArgumentType<Integer> {
    public static IntegerArgumentType integer() { return null; }
    public static IntegerArgumentType integer(int min) { return null; }
    public static IntegerArgumentType integer(int min, int max) { return null; }
    public static int getInteger(com.mojang.brigadier.context.CommandContext<?> c, String name) { return 0; }
}
