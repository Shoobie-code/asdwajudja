package com.mojang.brigadier.suggestion;
public class SuggestionsBuilder { public String getRemaining() { return ""; } public String getRemainingLowerCase() { return ""; } public SuggestionsBuilder suggest(String text) { return this; } public java.util.concurrent.CompletableFuture<Suggestions> buildFuture() { return null; } }
