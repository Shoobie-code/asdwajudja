package com.skyblockminer.gui;

import com.skyblockminer.Macro;
import com.skyblockminer.MacroType;
import com.skyblockminer.MinerConfig;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The main menu: a sidebar of categories, a searchable page of settings cards and a start/stop panel.
 * Layout is rebuilt every frame from the settings registry (a few dozen rows), and clicks are hit-tested
 * against the rows laid out in the previous frame.
 */
public final class ClickGuiScreen extends Screen {
    private static final int SIDEBAR = 116;
    private static final int ROW = 22;
    private static final int HEADER = 16;
    private static final int PAD = 10;
    private static final int CONTROL_H = 14;
    private static final int TOGGLE_W = 22;
    private static final int SLIDER_W = 92;
    private static final int CHOICE_W = 112;
    private static final int TEXT_W = 140;
    private static final int BUTTON_W = 60;
    private static final int CHIP_H = 13;
    private static final long OPEN_MS = 180L;

    private enum Kind {
        HEADER,
        SETTING,
        CHIPS
    }

    /** A laid-out line of the page. {@code y} is relative to the top of the scrolled content. */
    private record Row(Kind kind, String title, Setting setting, int y, int height, boolean first, boolean last) {
    }

    private final Macro macro;
    private final MinerConfig config;
    private final List<Setting.Category> categories;
    private final Input input = new Input();
    private final TextInput search = new TextInput();
    private final TextInput editor = new TextInput();
    private final Map<Setting, Float> anim = new IdentityHashMap<>();
    private final List<Row> rows = new ArrayList<>();
    private Setting.Category category;
    private boolean searching;
    private Setting.Text editing;
    private Setting.Slider dragging;
    private boolean draggingScrollbar;
    private float scroll;
    private float scrollTarget;
    private int contentHeight;
    private long openedAt;
    private long lastFrame;
    private Setting hovered;
    private int x0;
    private int y0;
    private int x1;
    private int y1;

    private ClickGuiScreen(Macro macro, List<Setting.Category> categories) {
        super(Component.literal("Skyblock Macro"));
        this.macro = macro;
        this.config = macro.config();
        this.categories = categories;
        this.category = categories.stream().filter(c -> c.name().equals(this.config.guiCategory)).findFirst().orElse(categories.get(0));
    }

    public static void open(Macro macro, List<Setting.Category> categories) {
        Minecraft.getInstance().gui.setScreen(new ClickGuiScreen(macro, categories));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 24, 500);
        int h = Math.min(this.height - 24, 310);
        this.x0 = (this.width - w) / 2;
        this.y0 = (this.height - h) / 2;
        this.x1 = this.x0 + w;
        this.y1 = this.y0 + h;
        if (this.openedAt == 0L) {
            this.openedAt = System.currentTimeMillis();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.commitEdit();
        this.config.guiCategory = this.category.name();
        this.config.save();
        super.onClose();
    }

    /** Escape first leaves a focused text field; vanilla calls this before closing. */
    public boolean shouldCloseOnEsc() {
        return this.editing == null && !this.searching;
    }

    /** Wheel scrolling. Declared without @Override so a changed vanilla signature only disables the wheel. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Setting.Slider slider = this.hovered instanceof Setting.Slider s ? s : null;
        if (slider != null && this.input.shift()) {
            slider.nudge(scrollY > 0 ? 1 : -1);
        } else {
            this.scrollBy((float) -scrollY * 24.0F);
        }
        return true;
    }

    private void scrollBy(float amount) {
        this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget + amount));
    }

    private int maxScroll() {
        return Math.max(0, this.contentHeight - (this.contentBottom() - this.contentTop()));
    }

    private int contentTop() {
        return this.y0 + 34;
    }

    private int contentBottom() {
        return this.y1 - 22;
    }

    private int contentLeft() {
        return this.x0 + SIDEBAR + PAD;
    }

    private int contentRight() {
        return this.x1 - PAD - 6;
    }

    // ---- frame ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        long now = System.currentTimeMillis();
        float dt = this.lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.lastFrame) / 1000.0F);
        this.lastFrame = now;
        float open = Math.min(1.0F, (now - this.openedAt) / (float) OPEN_MS);
        float alpha = 1.0F - (1.0F - open) * (1.0F - open);

        this.input.poll();
        this.handleKeys();
        this.layout();
        this.handleMouse(mouseX, mouseY);
        this.scroll = Draw.approach(this.scroll, this.scrollTarget, dt, 18.0F);

        int accent = Theme.accent(this.config.accent);
        int lift = (int) ((1.0F - alpha) * 8.0F);
        Font font = this.font;
        Draw.shadow(g, this.x0, this.y0 + lift, this.x1, this.y1 + lift, alpha);
        Draw.round(g, this.x0, this.y0 + lift, this.x1, this.y1 + lift, Draw.fade(Theme.BACKGROUND, alpha));
        this.drawSidebar(g, font, mouseX, mouseY, lift, alpha, accent);
        this.drawTopBar(g, font, lift, alpha, accent, now);
        this.drawRows(g, font, mouseX, mouseY, lift, alpha, accent, dt, now);
        this.drawFooter(g, font, lift, alpha);
    }

    // ---- layout ----

    private void layout() {
        this.rows.clear();
        int y = 0;
        String query = this.search.text().trim();
        List<Setting.Category> source = query.isEmpty() ? List.of(this.category) : this.categories;
        int width = this.contentRight() - this.contentLeft();
        for (Setting.Category cat : source) {
            for (Setting.Section section : cat.sections()) {
                List<Setting> shown = new ArrayList<>();
                for (Setting setting : section.settings()) {
                    if (setting.visible() && (query.isEmpty() || setting.matches(query))) {
                        shown.add(setting);
                    }
                }
                if (shown.isEmpty()) {
                    continue;
                }
                String title = query.isEmpty() ? section.title() : cat.name() + "  ›  " + section.title();
                this.rows.add(new Row(Kind.HEADER, title, null, y, HEADER, false, false));
                y += HEADER;
                for (int i = 0; i < shown.size(); i++) {
                    Setting setting = shown.get(i);
                    boolean first = i == 0;
                    boolean last = i == shown.size() - 1;
                    if (setting instanceof Setting.Multi multi) {
                        int chips = this.chipRows(multi, width - 16);
                        int height = ROW + chips * (CHIP_H + 4) + 2;
                        this.rows.add(new Row(Kind.CHIPS, null, setting, y, height, first, last));
                        y += height;
                    } else {
                        this.rows.add(new Row(Kind.SETTING, null, setting, y, ROW, first, last));
                        y += ROW;
                    }
                }
                y += 8;
            }
        }
        this.contentHeight = y;
        this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget));
    }

    private int chipRows(Setting.Multi multi, int width) {
        int rows = 1;
        int x = 0;
        for (String option : multi.options) {
            int w = this.font.width(option) + 12;
            if (x + w > width && x > 0) {
                rows++;
                x = 0;
            }
            x += w + 4;
        }
        return rows;
    }

    // ---- input ----

    private void handleKeys() {
        if (this.editing != null) {
            TextInput.Result result = this.editor.handle(this.input);
            if (result == TextInput.Result.SUBMIT) {
                this.commitEdit();
            } else if (result == TextInput.Result.CANCEL) {
                this.editing = null;
            }
            return;
        }
        if (this.searching) {
            TextInput.Result result = this.search.handle(this.input);
            if (result == TextInput.Result.CANCEL) {
                this.searching = false;
                this.search.set("", 40);
            } else if (result == TextInput.Result.SUBMIT) {
                this.searching = false;
            }
            this.scrollTarget = 0.0F;
            return;
        }
        for (int i = 0; i < this.input.typedCount(); i++) {
            int key = this.input.typed(i);
            if (key == GLFW.GLFW_KEY_F && this.input.shortcut() || key == GLFW.GLFW_KEY_SLASH) {
                this.searching = true;
            } else if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN) {
                this.scrollBy(key == GLFW.GLFW_KEY_DOWN ? 24.0F : 120.0F);
            } else if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP) {
                this.scrollBy(key == GLFW.GLFW_KEY_UP ? -24.0F : -120.0F);
            } else if (key == GLFW.GLFW_KEY_TAB) {
                int index = this.categories.indexOf(this.category);
                this.selectCategory(this.categories.get(Math.floorMod(index + (this.input.shift() ? -1 : 1), this.categories.size())));
            }
        }
    }

    private void handleMouse(int mx, int my) {
        if (this.input.released(0)) {
            this.dragging = null;
            this.draggingScrollbar = false;
        }
        if (this.dragging != null) {
            int right = this.contentRight() - 10;
            this.dragging.setFraction((mx - (right - SLIDER_W)) / (double) SLIDER_W);
        }
        if (this.draggingScrollbar) {
            int track = this.contentBottom() - this.contentTop();
            float fraction = (my - this.contentTop()) / (float) track;
            this.scrollTarget = this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), fraction * this.contentHeight - track / 2.0F));
        }
        Row hoveredRow = this.rowAt(mx, my);
        this.hovered = hoveredRow == null ? null : hoveredRow.setting();

        boolean left = this.input.clicked(0);
        boolean right = this.input.clicked(1);
        if (!left && !right) {
            return;
        }
        int button = left ? 0 : 1;
        if (this.editing != null && !this.inEditor(mx, my)) {
            this.commitEdit();
        }
        if (this.searching && !this.inSearch(mx, my)) {
            this.searching = false;
        }

        if (this.clickSidebar(mx, my, button)) {
            return;
        }
        if (left && this.inSearch(mx, my)) {
            this.searching = true;
            return;
        }
        if (left && mx >= this.x1 - PAD - 4 && mx < this.x1 - PAD + 2 && my >= this.contentTop() && my < this.contentBottom() && this.maxScroll() > 0) {
            this.draggingScrollbar = true;
            return;
        }
        Row row = this.rowAt(mx, my);
        if (row != null && row.setting() != null) {
            this.clickRow(row, mx, my, button);
        }
    }

    private Row rowAt(int mx, int my) {
        if (mx < this.contentLeft() || mx >= this.contentRight() || my < this.contentTop() || my >= this.contentBottom()) {
            return null;
        }
        int local = my - this.contentTop() + Math.round(this.scroll);
        for (Row row : this.rows) {
            if (local >= row.y() && local < row.y() + row.height()) {
                // Rows cut off by the edges are not drawn, so they must not take clicks either.
                int top = this.rowTop(row);
                return top >= this.contentTop() && top + row.height() <= this.contentBottom() ? row : null;
            }
        }
        return null;
    }

    private int rowTop(Row row) {
        return this.contentTop() + row.y() - Math.round(this.scroll);
    }

    private void clickRow(Row row, int mx, int my, int button) {
        Setting setting = row.setting();
        int right = this.contentRight() - 10;
        int top = this.rowTop(row);
        int controlTop = top + (ROW - CONTROL_H) / 2;
        boolean inControlRow = my >= controlTop && my < controlTop + CONTROL_H;
        switch (setting) {
            case Setting.Toggle toggle -> toggle.toggle();
            case Setting.Slider slider -> {
                if (inControlRow && mx >= right - SLIDER_W - 4 && mx < right + 4) {
                    if (button == 0) {
                        this.dragging = slider;
                        slider.setFraction((mx - (right - SLIDER_W)) / (double) SLIDER_W);
                    } else {
                        slider.nudge(-1);
                    }
                }
            }
            case Setting.Choice choice -> {
                if (inControlRow && mx >= right - CHOICE_W) {
                    boolean back = button == 1 || mx < right - CHOICE_W + 14;
                    choice.cycle(back ? -1 : 1);
                }
            }
            case Setting.Text text -> {
                if (button == 0 && inControlRow && mx >= right - TEXT_W) {
                    this.editing = text;
                    this.editor.set(text.get(), text.maxLength);
                } else if (button == 1 && inControlRow && mx >= right - TEXT_W) {
                    text.put("");
                }
            }
            case Setting.Button pressable -> {
                if (button == 0 && inControlRow && mx >= right - BUTTON_W) {
                    pressable.press();
                }
            }
            case Setting.Multi multi -> {
                String chip = this.chipAt(multi, top, mx, my);
                if (chip != null) {
                    multi.toggle(chip);
                }
            }
            default -> {
            }
        }
    }

    private String chipAt(Setting.Multi multi, int top, int mx, int my) {
        int left = this.contentLeft() + 8;
        int width = this.contentRight() - this.contentLeft() - 16;
        int x = 0;
        int y = top + ROW;
        for (String option : multi.options) {
            int w = this.font.width(option) + 12;
            if (x + w > width && x > 0) {
                x = 0;
                y += CHIP_H + 4;
            }
            if (Draw.inside(mx, my, left + x, y, left + x + w, y + CHIP_H)) {
                return option;
            }
            x += w + 4;
        }
        return null;
    }

    private boolean clickSidebar(int mx, int my, int button) {
        if (mx < this.x0 || mx >= this.x0 + SIDEBAR) {
            return false;
        }
        int y = this.y0 + 44;
        for (Setting.Category cat : this.categories) {
            if (my >= y && my < y + 19) {
                this.selectCategory(cat);
                return true;
            }
            y += 20;
        }
        int panel = this.y1 - 62;
        if (my >= panel + 12 && my < panel + 26) {
            MacroType selected = this.macro.selected();
            this.macro.select(button == 1 ? selected.previous() : selected.next());
            return true;
        }
        if (my >= this.y1 - 26 && my < this.y1 - 8) {
            boolean wasRunning = this.macro.running();
            if (!wasRunning) {
                this.onClose();
            }
            this.macro.toggle();
            return true;
        }
        return true;
    }

    private void selectCategory(Setting.Category cat) {
        this.category = cat;
        this.scroll = this.scrollTarget = 0.0F;
        this.search.set("", 40);
        this.searching = false;
    }

    private boolean inSearch(int mx, int my) {
        int right = this.x1 - PAD;
        return Draw.inside(mx, my, right - 120, this.y0 + 9, right, this.y0 + 25);
    }

    private boolean inEditor(int mx, int my) {
        for (Row row : this.rows) {
            if (row.setting() == this.editing) {
                int top = this.rowTop(row) + (ROW - CONTROL_H) / 2;
                int right = this.contentRight() - 10;
                return Draw.inside(mx, my, right - TEXT_W, top, right, top + CONTROL_H);
            }
        }
        return false;
    }

    private void commitEdit() {
        if (this.editing != null) {
            this.editing.put(this.editor.text());
            this.editing = null;
        }
    }

    // ---- drawing ----

    private void drawSidebar(GuiGraphicsExtractor g, Font font, int mx, int my, int lift, float alpha, int accent) {
        int left = this.x0;
        int right = this.x0 + SIDEBAR;
        g.fill(left + 2, this.y0 + lift, right, this.y1 + lift, Draw.fade(Theme.SIDEBAR, alpha));
        g.fill(left, this.y0 + 2 + lift, left + 2, this.y1 - 2 + lift, Draw.fade(Theme.SIDEBAR, alpha));
        Draw.textShadow(g, font, "SKYBLOCK", left + 12, this.y0 + 12 + lift, Draw.fade(accent, alpha));
        Draw.text(g, font, "MACRO", left + 12 + font.width("SKYBLOCK "), this.y0 + 12 + lift, Draw.fade(Theme.TEXT, alpha));
        Draw.text(g, font, "v2.0", left + 12, this.y0 + 24 + lift, Draw.fade(Theme.FAINT, alpha));

        int y = this.y0 + 44;
        for (Setting.Category cat : this.categories) {
            boolean selected = cat == this.category && this.search.text().isBlank();
            boolean hover = Draw.inside(mx, my, left, y, right, y + 19);
            if (selected) {
                Draw.round(g, left + 6, y + lift, right - 6, y + 19 + lift, Draw.fade(Draw.withAlpha(accent, 0x33), alpha));
                g.fill(left + 6, y + 4 + lift, left + 8, y + 15 + lift, Draw.fade(accent, alpha));
            } else if (hover) {
                Draw.round(g, left + 6, y + lift, right - 6, y + 19 + lift, Draw.fade(0x14FFFFFF, alpha));
            }
            int color = selected ? Theme.TEXT : hover ? Theme.TEXT : Theme.MUTED;
            Draw.text(g, font, cat.icon(), left + 14, y + 6 + lift, Draw.fade(selected ? accent : Theme.FAINT, alpha));
            Draw.text(g, font, cat.name(), left + 28, y + 6 + lift, Draw.fade(color, alpha));
            y += 20;
        }

        int panel = this.y1 - 62;
        g.fill(left + 8, panel - 6 + lift, right - 8, panel - 5 + lift, Draw.fade(Theme.BORDER, alpha));
        Draw.text(g, font, "Macro", left + 12, panel + lift, Draw.fade(Theme.FAINT, alpha));
        MacroType selected = this.macro.selected();
        boolean hoverType = Draw.inside(mx, my, left, panel + 12, right, panel + 26);
        Draw.text(g, font, Draw.fit(font, "‹ " + selected.label + " ›", SIDEBAR - 20), left + 12, panel + 14 + lift,
            Draw.fade(hoverType ? accent : Theme.TEXT, alpha));
        boolean running = this.macro.running();
        String status = running ? this.macro.status() : "Idle";
        Draw.text(g, font, Draw.fit(font, status, SIDEBAR - 20), left + 12, panel + 26 + lift, Draw.fade(Theme.MUTED, alpha));

        int bx1 = left + 10;
        int bx2 = right - 10;
        int by1 = this.y1 - 26;
        boolean hoverButton = Draw.inside(mx, my, bx1, by1, bx2, by1 + 18);
        int base = running ? Theme.BAD : accent;
        Draw.round(g, bx1, by1 + lift, bx2, by1 + 18 + lift, Draw.fade(hoverButton ? Draw.mix(base, 0xFFFFFFFF, 0.15F) : base, alpha));
        Draw.centered(g, font, running ? "Stop" : "Start", (bx1 + bx2) / 2, by1 + 5 + lift, Draw.fade(0xFFFFFFFF, alpha));
    }

    private void drawTopBar(GuiGraphicsExtractor g, Font font, int lift, float alpha, int accent, long now) {
        int left = this.contentLeft();
        String query = this.search.text();
        String title = query.isBlank() ? this.category.name() : "Search";
        Draw.textShadow(g, font, title, left, this.y0 + 13 + lift, Draw.fade(Theme.TEXT, alpha));

        int right = this.x1 - PAD;
        int sx = right - 120;
        int sy = this.y0 + 9;
        Draw.round(g, sx, sy + lift, right, sy + 16 + lift, Draw.fade(this.searching ? Draw.withAlpha(accent, 0x55) : Theme.BORDER, alpha));
        Draw.round(g, sx + 1, sy + 1 + lift, right - 1, sy + 15 + lift, Draw.fade(Theme.FIELD, alpha));
        if (query.isEmpty() && !this.searching) {
            Draw.text(g, font, "Search...  ( / )", sx + 6, sy + 4 + lift, Draw.fade(Theme.FAINT, alpha));
        } else {
            String shown = Draw.fit(font, query, 104);
            Draw.text(g, font, shown, sx + 6, sy + 4 + lift, Draw.fade(Theme.TEXT, alpha));
            if (this.searching && now / 500L % 2L == 0L) {
                int cx = sx + 6 + font.width(query.substring(0, Math.min(this.search.caret(), shown.length())));
                g.fill(cx, sy + 3 + lift, cx + 1, sy + 13 + lift, Draw.fade(Theme.TEXT, alpha));
            }
        }
        g.fill(left, this.y0 + 29 + lift, right, this.y0 + 30 + lift, Draw.fade(Theme.BORDER, alpha));
    }

    private void drawRows(GuiGraphicsExtractor g, Font font, int mx, int my, int lift, float alpha, int accent, float dt, long now) {
        int top = this.contentTop();
        int bottom = this.contentBottom();
        int left = this.contentLeft();
        int right = this.contentRight();
        if (this.rows.isEmpty()) {
            Draw.centered(g, font, "Nothing matches \"" + this.search.text() + "\"", (left + right) / 2, top + 20 + lift, Draw.fade(Theme.MUTED, alpha));
        }
        for (Row row : this.rows) {
            int y = this.rowTop(row);
            if (y < top || y + row.height() > bottom) {
                continue;
            }
            int yy = y + lift;
            if (row.kind() == Kind.HEADER) {
                Draw.text(g, font, row.title().toUpperCase(), left + 2, yy + 5, Draw.fade(accent, alpha));
                continue;
            }
            Setting setting = row.setting();
            boolean hover = Draw.inside(mx, my, left, y, right, y + row.height());
            int card = hover ? Theme.CARD_HOVER : Theme.CARD;
            if (row.first() && row.last()) {
                Draw.round(g, left, yy, right, yy + row.height(), Draw.fade(card, alpha));
            } else if (row.first()) {
                Draw.round(g, left, yy, right, yy + row.height() + 2, Draw.fade(card, alpha));
            } else if (row.last()) {
                Draw.round(g, left, yy - 2, right, yy + row.height(), Draw.fade(card, alpha));
                g.fill(left + 8, yy, right - 8, yy + 1, Draw.fade(Theme.BORDER, alpha));
            } else {
                g.fill(left, yy, right, yy + row.height(), Draw.fade(card, alpha));
                g.fill(left + 8, yy, right - 8, yy + 1, Draw.fade(Theme.BORDER, alpha));
            }
            Draw.text(g, font, Draw.fit(font, setting.name, right - left - 170), left + 10, yy + 7, Draw.fade(Theme.TEXT, alpha));
            this.drawControl(g, font, row, setting, yy, mx, my, y, alpha, accent, dt, now);
        }
        this.drawScrollbar(g, lift, alpha);
    }

    private void drawControl(GuiGraphicsExtractor g, Font font, Row row, Setting setting, int yy, int mx, int my, int y, float alpha,
        int accent, float dt, long now) {
        int right = this.contentRight() - 10;
        int cy = yy + (ROW - CONTROL_H) / 2;
        int hitY = y + (ROW - CONTROL_H) / 2;
        switch (setting) {
            case Setting.Toggle toggle -> {
                float t = this.anim.getOrDefault(toggle, toggle.get() ? 1.0F : 0.0F);
                t = Draw.approach(t, toggle.get() ? 1.0F : 0.0F, dt, 16.0F);
                this.anim.put(toggle, t);
                int tx = right - TOGGLE_W;
                int ty = cy + 2;
                Draw.round(g, tx, ty, right, ty + 11, Draw.fade(Draw.mix(Theme.OFF, accent, t), alpha));
                int knob = tx + 1 + Math.round(t * (TOGGLE_W - 11));
                Draw.round(g, knob, ty + 1, knob + 9, ty + 10, Draw.fade(0xFFF4F5F8, alpha));
            }
            case Setting.Slider slider -> {
                int tx = right - SLIDER_W;
                int ty = cy + 6;
                double fraction = Math.max(0.0, Math.min(1.0, slider.fraction()));
                int fill = tx + (int) Math.round(fraction * SLIDER_W);
                g.fill(tx, ty, right, ty + 3, Draw.fade(Theme.OFF, alpha));
                g.fill(tx, ty, fill, ty + 3, Draw.fade(accent, alpha));
                boolean active = this.dragging == slider || Draw.inside(mx, my, tx - 4, hitY, right + 4, hitY + CONTROL_H);
                Draw.round(g, fill - 2, ty - 3, fill + 3, ty + 6, Draw.fade(active ? 0xFFFFFFFF : 0xFFD0D3DC, alpha));
                Draw.right(g, font, slider.valueText(), tx - 8, cy + 3, Draw.fade(Theme.MUTED, alpha));
            }
            case Setting.Choice choice -> {
                int bx = right - CHOICE_W;
                boolean hover = Draw.inside(mx, my, bx, hitY, right, hitY + CONTROL_H);
                Draw.round(g, bx, cy, right, cy + CONTROL_H, Draw.fade(hover ? Draw.withAlpha(accent, 0x66) : Theme.BORDER, alpha));
                Draw.round(g, bx + 1, cy + 1, right - 1, cy + CONTROL_H - 1, Draw.fade(Theme.FIELD, alpha));
                Draw.text(g, font, "‹", bx + 5, cy + 3, Draw.fade(Theme.MUTED, alpha));
                Draw.right(g, font, "›", right - 5, cy + 3, Draw.fade(Theme.MUTED, alpha));
                Draw.centered(g, font, Draw.fit(font, choice.label(), CHOICE_W - 24), bx + CHOICE_W / 2, cy + 3, Draw.fade(Theme.TEXT, alpha));
            }
            case Setting.Text text -> {
                int bx = right - TEXT_W;
                boolean focused = this.editing == text;
                Draw.round(g, bx, cy, right, cy + CONTROL_H, Draw.fade(focused ? accent : Theme.BORDER, alpha));
                Draw.round(g, bx + 1, cy + 1, right - 1, cy + CONTROL_H - 1, Draw.fade(Theme.FIELD, alpha));
                String value = focused ? this.editor.text() : text.get();
                if (value.isEmpty() && !focused) {
                    Draw.text(g, font, Draw.fit(font, text.placeholder, TEXT_W - 10), bx + 5, cy + 3, Draw.fade(Theme.FAINT, alpha));
                } else {
                    String shown = text.secret && !focused ? "•".repeat(Math.min(value.length(), 24)) : value;
                    int caret = focused ? this.editor.caret() : shown.length();
                    int start = 0;
                    while (font.width(shown.substring(start, caret)) > TEXT_W - 12 && start < caret) {
                        start++;
                    }
                    String visible = Draw.fit(font, shown.substring(start), TEXT_W - 10);
                    Draw.text(g, font, visible, bx + 5, cy + 3, Draw.fade(Theme.TEXT, alpha));
                    if (focused && now / 500L % 2L == 0L) {
                        int cx = bx + 5 + font.width(shown.substring(start, caret));
                        g.fill(cx, cy + 2, cx + 1, cy + CONTROL_H - 2, Draw.fade(Theme.TEXT, alpha));
                    }
                }
            }
            case Setting.Button button -> {
                int bx = right - BUTTON_W;
                boolean hover = Draw.inside(mx, my, bx, hitY, right, hitY + CONTROL_H);
                Draw.round(g, bx, cy, right, cy + CONTROL_H, Draw.fade(hover ? accent : Draw.withAlpha(accent, 0x55), alpha));
                Draw.centered(g, font, button.label, bx + BUTTON_W / 2, cy + 3, Draw.fade(0xFFFFFFFF, alpha));
            }
            case Setting.Multi multi -> this.drawChips(g, font, multi, yy, mx, my, y, alpha, accent);
            default -> {
            }
        }
    }

    private void drawChips(GuiGraphicsExtractor g, Font font, Setting.Multi multi, int yy, int mx, int my, int y, float alpha, int accent) {
        int left = this.contentLeft() + 8;
        int width = this.contentRight() - this.contentLeft() - 16;
        int x = 0;
        int cy = yy + ROW;
        int hy = y + ROW;
        for (String option : multi.options) {
            int w = font.width(option) + 12;
            if (x + w > width && x > 0) {
                x = 0;
                cy += CHIP_H + 4;
                hy += CHIP_H + 4;
            }
            boolean on = multi.selected(option);
            boolean hover = Draw.inside(mx, my, left + x, hy, left + x + w, hy + CHIP_H);
            int back = on ? accent : hover ? Theme.OFF : Theme.FIELD;
            Draw.round(g, left + x, cy, left + x + w, cy + CHIP_H, Draw.fade(back, alpha));
            Draw.centered(g, font, option, left + x + w / 2, cy + 3, Draw.fade(on ? 0xFFFFFFFF : Theme.MUTED, alpha));
            x += w + 4;
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int lift, float alpha) {
        int max = this.maxScroll();
        if (max <= 0) {
            return;
        }
        int top = this.contentTop();
        int track = this.contentBottom() - top;
        int thumb = Math.max(20, track * track / this.contentHeight);
        int y = top + Math.round((track - thumb) * (this.scroll / max));
        int x = this.x1 - PAD - 2;
        g.fill(x, top + lift, x + 2, top + track + lift, Draw.fade(0x22FFFFFF, alpha));
        g.fill(x, y + lift, x + 2, y + thumb + lift, Draw.fade(this.draggingScrollbar ? 0xCCFFFFFF : 0x77FFFFFF, alpha));
    }

    private void drawFooter(GuiGraphicsExtractor g, Font font, int lift, float alpha) {
        int left = this.contentLeft();
        int y = this.y1 - 16 + lift;
        String text;
        if (this.editing != null) {
            text = "Enter to save, Esc to cancel, Ctrl+V to paste";
        } else if (this.hovered != null) {
            text = this.hovered.description + (this.hovered.hasValue() ? "   /sm set " + this.hovered.id : "");
        } else {
            text = "Click to change, right click to go back. Scroll to see more. Tab switches pages.";
        }
        Draw.text(g, font, Draw.fit(font, text, this.x1 - PAD - left), left, y, Draw.fade(Theme.FAINT, alpha));
    }
}
