package com.mojang.brigadier.suggestion;
@FunctionalInterface
public interface SuggestionProvider<S> { java.util.concurrent.CompletableFuture<Suggestions> getSuggestions(com.mojang.brigadier.context.CommandContext<S> context, SuggestionsBuilder builder) throws com.mojang.brigadier.exceptions.CommandSyntaxException; }
