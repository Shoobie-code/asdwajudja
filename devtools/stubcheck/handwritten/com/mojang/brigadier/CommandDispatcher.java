package com.mojang.brigadier;
public class CommandDispatcher<S> {
    public com.mojang.brigadier.tree.LiteralCommandNode<S> register(com.mojang.brigadier.builder.LiteralArgumentBuilder<S> command) { return null; }
    public int execute(String input, S source) throws com.mojang.brigadier.exceptions.CommandSyntaxException { return 0; }
}
