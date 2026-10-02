package com.mojang.brigadier.arguments;
public class BoolArgumentType implements ArgumentType<Boolean> {
    public static BoolArgumentType bool() { return null; }
    
    
    public static boolean getBool(com.mojang.brigadier.context.CommandContext<?> c, String name) { return false; }
}
