package com.skyblockminer.gui;

import com.skyblockminer.market.AuctionFlipper;
import com.skyblockminer.market.BazaarFlipper;
import com.skyblockminer.market.CraftCalc;
import com.skyblockminer.market.Flips;
import com.skyblockminer.market.Market;
import com.skyblockminer.market.NpcFlipper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Money-making dashboard: bazaar order flips, BIN auction flips, craft flips, NPC flips and a minion
 * crafting calculator, each as a sortable, searchable table. Clicking a row opens the item in game.
 */
public final class MarketScreen extends Screen {
    private static final String[] TABS = {"Bazaar", "Auctions", "Crafts", "NPC", "Minions"};
    private static final int ROW = 13;
    private static final int PAD = 10;
    private static final long REBUILD_MS = 500L;

    /** A table line: display cells, numeric sort keys and what clicking does. */
    private record Line(String[] cells, double[] keys, Runnable action) {
    }

    private final Market market;
    private final Flips flips;
    private final Consumer<String> command;
    private final int accent;
    private final Input input = new Input();
    private final TextInput search = new TextInput();
    private int tab;
    private boolean searching;
    private String[] header = new String[0];
    private int[] widths = new int[0];
    private List<Line> lines = List.of();
    private int sortColumn = -1;
    private boolean sortDescending = true;
    private long builtAt;
    private float scroll;
    private int x0;
    private int y0;
    private int x1;
    private int y1;
    private String minionFamily;
    private int minionTier = 1;
    private List<String> shopping = List.of();

    private MarketScreen(Market market, Flips flips, int accent, Consumer<String> command) {
        super(Component.literal("Market"));
        this.market = market;
        this.flips = flips;
        this.accent = accent;
        this.command = command;
    }

    public static void open(Market market, Flips flips, int accent, Consumer<String> command) {
        Minecraft.getInstance().gui.setScreen(new MarketScreen(market, flips, accent, command));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 20, 560);
        int h = Math.min(this.height - 20, 320);
        this.x0 = (this.width - w) / 2;
        this.y0 = (this.height - h) / 2;
        this.x1 = this.x0 + w;
        this.y1 = this.y0 + h;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public boolean shouldCloseOnEsc() {
        return !this.searching;
    }

    /** Wheel scrolling; no @Override so a changed vanilla signature only disables the wheel. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), this.scroll - (float) scrollY * ROW * 3));
        return true;
    }

    private int tableTop() {
        return this.y0 + 58;
    }

    private int tableBottom() {
        return this.y1 - 18;
    }

    private int maxScroll() {
        return Math.max(0, this.lines.size() * ROW - (this.tableBottom() - this.tableTop()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        this.input.poll();
        this.handleKeys();
        long now = System.currentTimeMillis();
        if (now - this.builtAt > REBUILD_MS) {
            this.builtAt = now;
            this.rebuild();
        }
        this.handleMouse(mouseX, mouseY);

        Font font = this.font;
        Draw.shadow(g, this.x0, this.y0, this.x1, this.y1, 1.0F);
        Draw.round(g, this.x0, this.y0, this.x1, this.y1, Theme.BACKGROUND);
        Draw.textShadow(g, font, "MARKET", this.x0 + PAD, this.y0 + 10, this.accent);
        this.drawTabs(g, font, mouseX, mouseY);
        this.drawSearch(g, font, now);
        Draw.text(g, font, Draw.fit(font, this.status(), this.x1 - this.x0 - 2 * PAD), this.x0 + PAD, this.y0 + 44, Theme.FAINT);
        if (this.tab == 4) {
            this.drawMinions(g, font, mouseX, mouseY);
        } else {
            this.drawTable(g, font, mouseX, mouseY, this.x0 + PAD, this.x1 - PAD);
        }
        Draw.text(g, font, "Click a row to open it in game. Click a column to sort. / to search.", this.x0 + PAD, this.y1 - 13, Theme.FAINT);
    }

    // ---- data ----

    private String status() {
        Market m = this.market;
        StringBuilder text = new StringBuilder();
        if (m.bazaar().isEmpty()) {
            text.append("Loading bazaar...");
        } else {
            text.append(String.format(Locale.ROOT, "Bazaar %ds ago, %d products", (System.currentTimeMillis() - m.bazaar().updatedAt()) / 1000L,
                m.bazaar().products().size()));
        }
        if (m.auctionsUpdatedAt() > 0L) {
            text.append(String.format(Locale.ROOT, "  |  AH %ds ago, %,d BINs over %d pages", (System.currentTimeMillis() - m.auctionsUpdatedAt()) / 1000L,
                m.auctions().size(), m.auctionPages()));
        } else if (this.tab == 1) {
            text.append("  |  AH scan off or loading (Market page: Scan auctions)");
        }
        if (m.recipesPending() > 0) {
            text.append(String.format(Locale.ROOT, "  |  recipes %d loaded, %d loading", m.recipesKnown(), m.recipesPending()));
        }
        if (m.error() != null) {
            text.append("  |  ").append(m.error());
        }
        return text.toString();
    }

    private void rebuild() {
        String query = this.search.text().trim().toLowerCase(Locale.ROOT);
        List<Line> out = new ArrayList<>();
        switch (this.tab) {
            case 0 -> {
                this.columns(new String[]{"Item", "Buy order", "Sell offer", "Margin", "Amount", "Profit", "Volume/day"},
                    new int[]{150, 62, 62, 70, 56, 62, 66});
                for (BazaarFlipper.Flip f : this.flips.bazaar()) {
                    String name = this.market.name(f.id());
                    out.add(new Line(new String[]{name, coins(f.buyAt()), coins(f.sellAt()), coins(f.marginEach()) + String.format(Locale.ROOT, " %.0f%%", f.marginPercent()),
                        String.format(Locale.ROOT, "%,d", f.amount()), coins(f.profit()), coins(f.dailyVolume())},
                        new double[]{0, f.buyAt(), f.sellAt(), f.marginEach(), f.amount(), f.profit(), f.dailyVolume()},
                        () -> this.command.accept("bz " + name)));
                }
            }
            case 1 -> {
                this.columns(new String[]{"Item", "Price", "Resell at", "Profit", "Margin", "Listings"}, new int[]{190, 70, 70, 70, 56, 56});
                for (AuctionFlipper.Flip f : this.flips.auctions()) {
                    out.add(new Line(new String[]{f.listing().name(), coins(f.listing().price()), coins(f.resellAt()), coins(f.profit()),
                        String.format(Locale.ROOT, "%.0f%%", f.marginPercent()), String.valueOf(f.listings())},
                        new double[]{0, f.listing().price(), f.resellAt(), f.profit(), f.marginPercent(), f.listings()},
                        () -> this.command.accept("viewauction " + f.listing().uuid())));
                }
            }
            case 2 -> {
                this.columns(new String[]{"Item", "Craft cost", "Sell offer", "Profit each", "Margin", "Volume/day"}, new int[]{180, 70, 70, 70, 56, 66});
                for (CraftCalc.CraftFlip f : this.flips.crafts()) {
                    String name = f.recipe().name();
                    out.add(new Line(new String[]{name, coins(f.cost()), coins(f.sellEach()), coins(f.profitEach()),
                        String.format(Locale.ROOT, "%.0f%%", f.marginPercent()), coins(f.dailyVolume())},
                        new double[]{0, f.cost(), f.sellEach(), f.profitEach(), f.marginPercent(), f.dailyVolume()},
                        () -> this.command.accept("bz " + name)));
                }
            }
            case 3 -> {
                this.columns(new String[]{"Item", "Bazaar", "NPC pays", "Profit each", "Amount", "Profit"}, new int[]{180, 64, 64, 70, 60, 70});
                for (NpcFlipper.Flip f : this.flips.npc()) {
                    String name = this.market.name(f.id());
                    out.add(new Line(new String[]{name, coins(f.buyEach()), coins(f.npcEach()), coins(f.profitEach()),
                        String.format(Locale.ROOT, "%,d", f.amount()), coins(f.profit())},
                        new double[]{0, f.buyEach(), f.npcEach(), f.profitEach(), f.amount(), f.profit()},
                        () -> this.command.accept("bz " + name)));
                }
            }
            default -> {
                this.buildMinions(query);
                return;
            }
        }
        if (!query.isEmpty()) {
            out.removeIf(line -> !line.cells()[0].toLowerCase(Locale.ROOT).contains(query));
        }
        if (this.sortColumn >= 0 && this.sortColumn < this.header.length) {
            int column = this.sortColumn;
            Comparator<Line> order = column == 0
                ? Comparator.comparing(line -> line.cells()[0].toLowerCase(Locale.ROOT))
                : Comparator.comparingDouble(line -> line.keys()[column]);
            out.sort(this.sortDescending ? order.reversed() : order);
        }
        this.lines = out;
        this.scroll = Math.min(this.scroll, this.maxScroll());
    }

    private void columns(String[] header, int[] widths) {
        if (this.header != header && !java.util.Arrays.equals(this.header, header)) {
            this.sortColumn = -1;
        }
        this.header = header;
        this.widths = widths;
    }

    // ---- minions ----

    private final Map<String, Integer> families = new TreeMap<>();

    private void buildMinions(String query) {
        this.families.clear();
        for (String id : this.market.npcPrices().keySet()) {
            this.addMinion(id);
        }
        for (String id : this.market.bazaar().products().keySet()) {
            this.addMinion(id);
        }
        if (this.families.isEmpty()) {
            // The item list has not loaded; offer the common ones so the tab is still useful.
            for (String family : List.of("COBBLESTONE", "WHEAT", "SNOW", "CLAY", "SLIME", "TARANTULA", "REVENANT", "MAGMA_CUBE", "GRAVEL", "FLOWER")) {
                this.families.put(family, family.equals("FLOWER") ? 12 : 11);
            }
        }
        List<Line> out = new ArrayList<>();
        for (Map.Entry<String, Integer> family : this.families.entrySet()) {
            String name = Market.pretty(family.getKey()) + " Minion";
            if (!query.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            String key = family.getKey();
            out.add(new Line(new String[]{name}, new double[]{0}, () -> {
                this.minionFamily = key;
                this.minionTier = 1;
            }));
        }
        this.lines = out;
        if (this.minionFamily != null) {
            this.shopping = this.minionShopping();
        }
    }

    private void addMinion(String id) {
        Object[] parsed = CraftCalc.minion(id);
        if (parsed != null) {
            this.families.merge((String) parsed[0], (Integer) parsed[1], Math::max);
        }
    }

    private List<String> minionShopping() {
        String id = this.minionFamily + "_GENERATOR_" + this.minionTier;
        CraftCalc.Cost cost = CraftCalc.cost(id, 1, this.market.bazaar(), this.market::recipe);
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "%s %s: %s%s", Market.pretty(this.minionFamily), roman(this.minionTier), coins(cost.coins()),
            cost.complete() ? "" : " + unpriced items"));
        cost.shopping().entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .forEach(e -> {
                double each = this.market.bazaar().instaBuy(e.getKey());
                out.add(String.format(Locale.ROOT, "%,d x %s%s", e.getValue(), this.market.name(e.getKey()),
                    Double.isNaN(each) ? "  (no bazaar price)" : "  " + coins(each * e.getValue())));
            });
        if (this.market.recipesPending() > 0) {
            out.add("Loading recipes...");
        }
        return out;
    }

    private void drawMinions(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int split = this.x0 + 170;
        this.header = new String[]{"Minion"};
        this.widths = new int[]{150};
        this.drawTable(g, font, mx, my, this.x0 + PAD, split - 6);
        int x = split + 4;
        int y = this.tableTop();
        if (this.minionFamily == null) {
            Draw.text(g, font, "Pick a minion to see what crafting it costs.", x, y, Theme.MUTED);
            return;
        }
        int max = this.families.getOrDefault(this.minionFamily, 11);
        int cx = x;
        for (int tier = 1; tier <= max; tier++) {
            String label = roman(tier);
            int w = font.width(label) + 8;
            boolean on = tier == this.minionTier;
            boolean hover = Draw.inside(mx, my, cx, y, cx + w, y + 12);
            Draw.round(g, cx, y, cx + w, y + 12, on ? this.accent : hover ? Theme.OFF : Theme.FIELD);
            Draw.centered(g, font, label, cx + w / 2, y + 2, on ? 0xFFFFFFFF : Theme.MUTED);
            if (hover && this.input.clicked(0)) {
                this.minionTier = tier;
                this.shopping = this.minionShopping();
            }
            cx += w + 3;
        }
        int line = y + 20;
        for (int i = 0; i < this.shopping.size() && line < this.tableBottom() - 10; i++) {
            Draw.text(g, font, Draw.fit(font, this.shopping.get(i), this.x1 - PAD - x), x, line, i == 0 ? Theme.TEXT : Theme.MUTED);
            line += 11;
        }
    }

    // ---- input ----

    private void handleKeys() {
        if (this.searching) {
            TextInput.Result result = this.search.handle(this.input);
            if (result != TextInput.Result.NONE) {
                this.searching = false;
                if (result == TextInput.Result.CANCEL) {
                    this.search.set("", 40);
                }
            }
            this.builtAt = 0L;
            return;
        }
        for (int i = 0; i < this.input.typedCount(); i++) {
            int key = this.input.typed(i);
            if (key == GLFW.GLFW_KEY_SLASH || key == GLFW.GLFW_KEY_F && this.input.shortcut()) {
                this.searching = true;
            } else if (key == GLFW.GLFW_KEY_TAB) {
                this.selectTab((this.tab + (this.input.shift() ? TABS.length - 1 : 1)) % TABS.length);
            }
        }
    }

    private void handleMouse(int mx, int my) {
        if (!this.input.clicked(0)) {
            return;
        }
        int x = this.x0 + PAD;
        for (int i = 0; i < TABS.length; i++) {
            int w = this.font.width(TABS[i]) + 14;
            if (Draw.inside(mx, my, x, this.y0 + 24, x + w, this.y0 + 38)) {
                this.selectTab(i);
                return;
            }
            x += w + 4;
        }
        if (Draw.inside(mx, my, this.x1 - PAD - 130, this.y0 + 7, this.x1 - PAD, this.y0 + 21)) {
            this.searching = true;
            return;
        }
        this.searching = false;
        int hx = this.x0 + PAD;
        for (int c = 0; c < this.header.length; c++) {
            if (Draw.inside(mx, my, hx, this.tableTop() - 12, hx + this.widths[c], this.tableTop())) {
                this.sortDescending = this.sortColumn != c || !this.sortDescending;
                this.sortColumn = c;
                this.builtAt = 0L;
                return;
            }
            hx += this.widths[c];
        }
        int right = this.tab == 4 ? this.x0 + 164 : this.x1 - PAD;
        if (mx >= this.x0 + PAD && mx < right && my >= this.tableTop() && my < this.tableBottom()) {
            int index = (int) ((my - this.tableTop() + this.scroll) / ROW);
            if (index >= 0 && index < this.lines.size()) {
                if (this.tab != 4) {
                    this.onClose();
                }
                this.lines.get(index).action().run();
                if (this.tab == 4) {
                    this.shopping = this.minionShopping();
                }
            }
        }
    }

    private void selectTab(int index) {
        this.tab = index;
        this.scroll = 0.0F;
        this.sortColumn = -1;
        this.builtAt = 0L;
    }

    // ---- drawing ----

    private void drawTabs(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int x = this.x0 + PAD;
        for (int i = 0; i < TABS.length; i++) {
            int w = font.width(TABS[i]) + 14;
            boolean on = i == this.tab;
            boolean hover = Draw.inside(mx, my, x, this.y0 + 24, x + w, this.y0 + 38);
            Draw.round(g, x, this.y0 + 24, x + w, this.y0 + 38, on ? Draw.withAlpha(this.accent, 0x44) : hover ? Theme.CARD_HOVER : Theme.CARD);
            Draw.centered(g, font, TABS[i], x + w / 2, this.y0 + 27, on ? Theme.TEXT : Theme.MUTED);
            if (on) {
                g.fill(x + 3, this.y0 + 37, x + w - 3, this.y0 + 38, this.accent);
            }
            x += w + 4;
        }
    }

    private void drawSearch(GuiGraphicsExtractor g, Font font, long now) {
        int right = this.x1 - PAD;
        int left = right - 130;
        int top = this.y0 + 7;
        Draw.round(g, left, top, right, top + 14, this.searching ? this.accent : Theme.BORDER);
        Draw.round(g, left + 1, top + 1, right - 1, top + 13, Theme.FIELD);
        String text = this.search.text();
        if (text.isEmpty() && !this.searching) {
            Draw.text(g, font, "Search ( / )", left + 5, top + 3, Theme.FAINT);
        } else {
            Draw.text(g, font, Draw.fit(font, text, 118), left + 5, top + 3, Theme.TEXT);
            if (this.searching && now / 500L % 2L == 0L) {
                int cx = left + 5 + font.width(text.substring(0, Math.min(this.search.caret(), text.length())));
                g.fill(cx, top + 2, cx + 1, top + 12, Theme.TEXT);
            }
        }
    }

    private void drawTable(GuiGraphicsExtractor g, Font font, int mx, int my, int left, int right) {
        int top = this.tableTop();
        int bottom = this.tableBottom();
        int hx = left;
        for (int c = 0; c < this.header.length; c++) {
            String label = this.header[c] + (c == this.sortColumn ? (this.sortDescending ? " ↓" : " ↑") : "");
            Draw.text(g, font, Draw.fit(font, label, this.widths[c] - 4), hx, top - 11, c == this.sortColumn ? this.accent : Theme.MUTED);
            hx += this.widths[c];
        }
        g.fill(left, top - 1, right, top, Theme.BORDER);
        if (this.lines.isEmpty()) {
            Draw.text(g, font, this.tab == 4 ? "No minions found yet." : "No flips match right now. Loosen the filters on the Market settings page.",
                left, top + 6, Theme.MUTED);
            return;
        }
        int first = (int) (this.scroll / ROW);
        for (int i = first; i < this.lines.size(); i++) {
            int y = top + i * ROW - Math.round(this.scroll);
            if (y < top) {
                continue;
            }
            if (y + ROW > bottom) {
                break;
            }
            Line line = this.lines.get(i);
            boolean hover = Draw.inside(mx, my, left, y, right, y + ROW);
            boolean selected = this.tab == 4 && this.minionFamily != null && line.cells()[0].startsWith(Market.pretty(this.minionFamily) + " ");
            if (hover || selected) {
                g.fill(left - 2, y, right, y + ROW, selected ? Draw.withAlpha(this.accent, 0x33) : Theme.CARD_HOVER);
            } else if (i % 2 == 1) {
                g.fill(left - 2, y, right, y + ROW, 0x10FFFFFF);
            }
            int cx = left;
            for (int c = 0; c < line.cells().length && c < this.widths.length; c++) {
                Draw.text(g, font, Draw.fit(font, line.cells()[c], this.widths[c] - 4), cx, y + 2, c == 0 ? Theme.TEXT : Theme.MUTED);
                cx += this.widths[c];
            }
        }
        int max = this.maxScroll();
        if (max > 0) {
            int track = bottom - top;
            int thumb = Math.max(16, track * track / (this.lines.size() * ROW));
            int y = top + Math.round((track - thumb) * (this.scroll / max));
            g.fill(right + 2, top, right + 4, bottom, 0x22FFFFFF);
            g.fill(right + 2, y, right + 4, y + thumb, 0x88FFFFFF);
        }
    }

    // ---- formatting ----

    /** 950, 12.4k, 3.25M, 1.1B. */
    public static String coins(double value) {
        double abs = Math.abs(value);
        if (abs >= 1e9) {
            return String.format(Locale.ROOT, "%.2fB", value / 1e9);
        }
        if (abs >= 1e6) {
            return String.format(Locale.ROOT, "%.2fM", value / 1e6);
        }
        if (abs >= 1e4) {
            return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        }
        return abs >= 100 ? String.format(Locale.ROOT, "%,.0f", value) : String.format(Locale.ROOT, "%.1f", value);
    }

    static String roman(int n) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII"};
        return n >= 0 && n < numerals.length ? numerals[n] : String.valueOf(n);
    }
}
