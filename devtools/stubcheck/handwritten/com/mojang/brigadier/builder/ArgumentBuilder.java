package com.mojang.brigadier.builder;
public abstract class ArgumentBuilder<S, T extends ArgumentBuilder<S, T>> {
    public T then(ArgumentBuilder<S, ?> argument) { return null; }
    public T executes(com.mojang.brigadier.Command<S> command) { return null; }
}
