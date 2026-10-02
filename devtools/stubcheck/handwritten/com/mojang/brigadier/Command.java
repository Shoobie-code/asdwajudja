package com.mojang.brigadier;
@FunctionalInterface
public interface Command<S> { int SINGLE_SUCCESS = 1; int run(com.mojang.brigadier.context.CommandContext<S> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException; }
