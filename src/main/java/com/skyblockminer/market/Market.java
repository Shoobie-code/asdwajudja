package com.skyblockminer.market;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Live market data: bazaar prices, BIN auctions, NPC prices and crafting recipes. All requests run on the
 * HTTP client's threads; results are published through volatile snapshots so the game thread never blocks.
 * Call {@link #tick} from the client tick to schedule refreshes.
 */
public final class Market {
    private static final Logger LOGGER = LoggerFactory.getLogger("Skyblock Macro Market");
    private static final String API = "https://api.hypixel.net/v2";
    private static final String NEU = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/items/";
    private static final long BAZAAR_EVERY = 60_000L;
    private static final long AUCTIONS_EVERY = 60_000L;
    private static final long ITEMS_EVERY = 6 * 3_600_000L;
    private static final int PAGE_PARALLELISM = 4;
    private static final int RECIPE_PARALLELISM = 6;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Path recipeCache;
    private volatile Bazaar bazaar = Bazaar.EMPTY;
    private volatile List<Auctions.Listing> auctions = List.of();
    private volatile Map<String, Double> npcPrices = Map.of();
    private volatile Map<String, String> names = Map.of();
    private volatile String error;
    private volatile long auctionsAt;
    private volatile int auctionPages;
    private volatile boolean bazaarLoading;
    private volatile boolean auctionsLoading;
    private volatile boolean itemsLoading;
    private long bazaarAskedAt;
    private long auctionsAskedAt;
    private long itemsAskedAt;
    private final Set<String> seenAuctions = new HashSet<>();
    private final List<Consumer<List<Auctions.Listing>>> newListingListeners = new ArrayList<>();

    private final Map<String, Optional<Recipe>> recipes = new ConcurrentHashMap<>();
    private final Deque<String> recipeQueue = new ArrayDeque<>();
    private final Set<String> recipeQueued = new HashSet<>();
    private int recipesInFlight;

    public Market(Path cacheDir) {
        this.recipeCache = cacheDir.resolve("neu");
    }

    // ---- snapshots ----

    public Bazaar bazaar() {
        return this.bazaar;
    }

    public List<Auctions.Listing> auctions() {
        return this.auctions;
    }

    public Map<String, Double> npcPrices() {
        return this.npcPrices;
    }

    public long auctionsUpdatedAt() {
        return this.auctionsAt;
    }

    public int auctionPages() {
        return this.auctionPages;
    }

    public String error() {
        return this.error;
    }

    /** Display name for an item id, falling back to a prettified id. */
    public String name(String id) {
        String name = this.names.get(id);
        if (name != null) {
            return name;
        }
        Optional<Recipe> recipe = this.recipes.get(id);
        if (recipe != null && recipe.isPresent()) {
            return recipe.get().name();
        }
        return pretty(id);
    }

    public static String pretty(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase().split("_")) {
            if (!word.isEmpty()) {
                out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        return out.toString().trim();
    }

    /** Called with listings that appeared since the previous auction scan (on an HTTP thread). */
    public synchronized void onNewListings(Consumer<List<Auctions.Listing>> listener) {
        this.newListingListeners.add(listener);
    }

    // ---- scheduling ----

    /** Schedules whatever is due. Cheap; call every client tick. */
    public void tick(boolean wantBazaar, boolean wantAuctions) {
        long now = System.currentTimeMillis();
        if ((wantBazaar || wantAuctions) && !this.itemsLoading && now - this.itemsAskedAt > ITEMS_EVERY) {
            this.itemsAskedAt = now;
            this.refreshItems();
        }
        if (wantBazaar && !this.bazaarLoading && now - this.bazaarAskedAt > BAZAAR_EVERY) {
            this.bazaarAskedAt = now;
            this.refreshBazaar();
        }
        if (wantAuctions && !this.auctionsLoading && now - this.auctionsAskedAt > AUCTIONS_EVERY) {
            this.auctionsAskedAt = now;
            this.refreshAuctions();
        }
        this.pumpRecipes();
    }

    /** Forces the next tick to refresh everything. */
    public void refreshSoon() {
        this.bazaarAskedAt = 0L;
        this.auctionsAskedAt = 0L;
    }

    private CompletableFuture<JsonObject> json(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("User-Agent", "SkyblockMacro")
            .GET()
            .build();
        return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() != 200) {
                throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
            }
            return JsonParser.parseString(response.body()).getAsJsonObject();
        });
    }

    private void refreshBazaar() {
        this.bazaarLoading = true;
        this.json(API + "/skyblock/bazaar").whenComplete((root, failure) -> {
            if (failure == null) {
                this.bazaar = Bazaar.parse(root);
                this.error = null;
            } else {
                this.fail("bazaar", failure);
            }
            this.bazaarLoading = false;
        });
    }

    private void refreshItems() {
        this.itemsLoading = true;
        this.json(API + "/resources/skyblock/items").whenComplete((root, failure) -> {
            if (failure == null && root.has("items")) {
                Map<String, Double> npc = new HashMap<>();
                Map<String, String> display = new HashMap<>();
                for (JsonElement element : root.getAsJsonArray("items")) {
                    JsonObject item = element.getAsJsonObject();
                    String id = item.get("id").getAsString();
                    if (item.has("name")) {
                        display.put(id, item.get("name").getAsString());
                    }
                    if (item.has("npc_sell_price")) {
                        npc.put(id, item.get("npc_sell_price").getAsDouble());
                    }
                }
                this.npcPrices = Collections.unmodifiableMap(npc);
                this.names = Collections.unmodifiableMap(display);
            } else if (failure != null) {
                this.fail("item list", failure);
            }
            this.itemsLoading = false;
        });
    }

    private void refreshAuctions() {
        this.auctionsLoading = true;
        this.json(API + "/skyblock/auctions?page=0").thenCompose(first -> {
            Auctions.Page page0 = Auctions.parsePage(first);
            List<Auctions.Listing> all = Collections.synchronizedList(new ArrayList<>(page0.listings()));
            List<Integer> rest = new ArrayList<>();
            for (int page = 1; page < page0.totalPages(); page++) {
                rest.add(page);
            }
            // A few pages at a time: fast, without hammering the API.
            CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
            for (int start = 0; start < rest.size(); start += PAGE_PARALLELISM) {
                List<Integer> batch = rest.subList(start, Math.min(rest.size(), start + PAGE_PARALLELISM));
                chain = chain.thenCompose(ignored -> CompletableFuture.allOf(batch.stream()
                    .map(page -> this.json(API + "/skyblock/auctions?page=" + page)
                        .thenAccept(root -> all.addAll(Auctions.parsePage(root).listings()))
                        .exceptionally(error -> null))
                    .toArray(CompletableFuture[]::new)));
            }
            return chain.thenApply(ignored -> {
                this.auctionPages = page0.totalPages();
                return List.copyOf(all);
            });
        }).whenComplete((listings, failure) -> {
            if (failure == null) {
                this.publishAuctions(listings);
            } else {
                this.fail("auctions", failure);
            }
            this.auctionsLoading = false;
        });
    }

    private void publishAuctions(List<Auctions.Listing> listings) {
        List<Auctions.Listing> fresh = new ArrayList<>();
        boolean first;
        synchronized (this) {
            first = this.seenAuctions.isEmpty();
            Set<String> now = new HashSet<>(listings.size() * 2);
            for (Auctions.Listing listing : listings) {
                now.add(listing.uuid());
                if (!first && !this.seenAuctions.contains(listing.uuid())) {
                    fresh.add(listing);
                }
            }
            this.seenAuctions.clear();
            this.seenAuctions.addAll(now);
        }
        this.auctions = listings;
        this.auctionsAt = System.currentTimeMillis();
        this.error = null;
        if (!fresh.isEmpty()) {
            List<Consumer<List<Auctions.Listing>>> listeners;
            synchronized (this) {
                listeners = List.copyOf(this.newListingListeners);
            }
            List<Auctions.Listing> view = Collections.unmodifiableList(fresh);
            listeners.forEach(listener -> listener.accept(view));
        }
    }

    private void fail(String what, Throwable failure) {
        Throwable cause = failure.getCause() != null ? failure.getCause() : failure;
        this.error = "Could not load " + what + ": " + cause.getMessage();
        LOGGER.warn(this.error);
    }

    // ---- recipes ----

    /**
     * The crafting recipe for an item, or null when it is unknown, has no recipe, or is still loading.
     * Unknown ids are queued and fetched in the background (cached on disk after the first download).
     */
    public Recipe recipe(String id) {
        Optional<Recipe> known = this.recipes.get(id);
        if (known != null) {
            return known.orElse(null);
        }
        synchronized (this.recipeQueue) {
            if (this.recipeQueued.add(id)) {
                this.recipeQueue.add(id);
            }
        }
        return null;
    }

    /** Recipes loaded or confirmed absent so far, out of those asked for. */
    public int recipesKnown() {
        return this.recipes.size();
    }

    public int recipesPending() {
        synchronized (this.recipeQueue) {
            return this.recipeQueue.size() + this.recipesInFlight;
        }
    }

    private void pumpRecipes() {
        while (true) {
            String id;
            synchronized (this.recipeQueue) {
                if (this.recipesInFlight >= RECIPE_PARALLELISM || this.recipeQueue.isEmpty()) {
                    return;
                }
                id = this.recipeQueue.poll();
                this.recipesInFlight++;
            }
            this.loadRecipe(id).whenComplete((recipe, failure) -> {
                this.recipes.put(id, Optional.ofNullable(failure == null ? recipe : null));
                synchronized (this.recipeQueue) {
                    this.recipesInFlight--;
                    this.recipeQueued.remove(id);
                }
            });
        }
    }

    private CompletableFuture<Recipe> loadRecipe(String id) {
        if (!id.matches("[A-Z0-9_:;\\-]+")) {
            return CompletableFuture.completedFuture(null);
        }
        Path file = this.recipeCache.resolve(id.replace(':', '-') + ".json");
        return CompletableFuture.supplyAsync(() -> {
            try {
                return Files.exists(file) ? Files.readString(file) : null;
            } catch (IOException e) {
                return null;
            }
        }).thenCompose(cached -> {
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(NEU + id + ".json")).timeout(Duration.ofSeconds(20)).GET().build();
            return this.client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                // Missing items are cached too ("{}") so they are not fetched again.
                String body = response.statusCode() == 200 ? response.body() : "{}";
                try {
                    Files.createDirectories(this.recipeCache);
                    Files.writeString(file, body);
                } catch (IOException e) {
                    LOGGER.debug("Could not cache recipe {}", id, e);
                }
                return body;
            });
        }).thenApply(body -> {
            JsonElement parsed = JsonParser.parseString(body);
            return parsed.isJsonObject() ? Recipe.parse(parsed.getAsJsonObject()) : null;
        });
    }
}
