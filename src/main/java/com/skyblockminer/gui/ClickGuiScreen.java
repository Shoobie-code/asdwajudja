package com.skyblockminer.gui;

import com.skyblockminer.Macro;
import com.skyblockminer.MinerConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The main menu, laid out like the Skymacro overlay design: a gold title bar ("SKY" + accent "MACRO"), a sidebar
 * of icon tabs with Settings at the bottom, and a content area with the tab's title, description and search box
 * over a list of cards. Each card has an ON/OFF button, a keybind chip and a gear that opens its settings in
 * place. The Settings tab has the master switch, the menu key, panel opacity and the accent theme.
 *
 * <p>Layout is rebuilt every frame (a few dozen rows), and clicks are hit-tested against the previous frame's rows.
 */
public final class ClickGuiScreen extends Screen {
    private static final int TITLE_H = 20;
    private static final int SIDEBAR = 54;
    private static final int PAD = 10;
    private static final int HEAD_H = 54;
    private static final int FOOT_H = 13;
    private static final int CARD_H = 30;
    private static final int GAP = 5;
    private static final int ROW = 18;
    private static final int SUBHEAD = 12;
    private static final int CONTROL_H = 13;
    private static final int TOGGLE_W = 20;
    private static final int SLIDER_W = 80;
    private static final int CHOICE_W = 104;
    private static final int TEXT_W = 120;
    private static final int BUTTON_W = 56;
    private static final int CHIP_H = 12;
    private static final int ONOFF_W = 28;
    private static final int ONOFF_H = 18;
    private static final long OPEN_MS = 180L;
    private static final int OPACITY_MIN = 40;

    private enum Kind {
        CARD,
        SUBHEAD,
        SETTING,
        CHIPS,
        MASTER,
        KEYBIND,
        OPACITY,
        ACCENT,
        EMPTY
    }

    /** A laid-out line of the page. {@code y} is relative to the top of the scrolled content. */
    private static final class Row {
        final Kind kind;
        final Menu.Card card;
        final Setting setting;
        final String title;
        final int y;
        final int height;
        int block;

        Row(Kind kind, Menu.Card card, Setting setting, String title, int y, int height) {
            this.kind = kind;
            this.card = card;
            this.setting = setting;
            this.title = title;
            this.y = y;
            this.height = height;
            this.block = height;
        }
    }

    private final Macro macro;
    private final MinerConfig config;
    private final Menu.Layout layout;
    private final List<Menu.Tab> tabs;
    private final Input input = new Input();
    private final TextInput search = new TextInput();
    private final TextInput editor = new TextInput();
    private final Map<Object, Float> anim = new IdentityHashMap<>();
    private final Set<Menu.Card> open = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    /** The selected tab, or null for the Settings page. */
    private Menu.Tab tab;
    private boolean searching;
    private boolean capturingKey;
    private Setting.Text editing;
    private Setting.Slider dragging;
    private boolean draggingOpacity;
    private boolean draggingScrollbar;
    private boolean draggingPanel;
    private int grabX;
    private int grabY;
    private int offsetX;
    private int offsetY;
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

    private ClickGuiScreen(Macro macro, Menu.Layout layout) {
        super(Component.literal("Skymacro"));
        this.macro = macro;
        this.config = macro.config();
        this.layout = layout;
        this.tabs = layout.tabs();
        this.tab = this.config.guiCategory.equals("Settings") ? null
            : this.tabs.stream().filter(t -> t.name().equals(this.config.guiCategory)).findFirst().orElse(this.tabs.get(0));
    }

    public static void open(Macro macro, Menu.Layout layout) {
        Minecraft.getInstance().gui.setScreen(new ClickGuiScreen(macro, layout));
    }

    @Override
    protected void init() {
        int w = Math.min(this.width - 16, 420);
        int h = Math.min(this.height - 16, 300);
        this.offsetX = Math.max(-(this.width - w) / 2, Math.min((this.width - w) / 2, this.offsetX));
        this.offsetY = Math.max(-(this.height - h) / 2, Math.min((this.height - h) / 2, this.offsetY));
        this.x0 = (this.width - w) / 2 + this.offsetX;
        this.y0 = (this.height - h) / 2 + this.offsetY;
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
        this.config.guiCategory = this.tab == null ? "Settings" : this.tab.name();
        this.config.save();
        super.onClose();
    }

    /** Escape first leaves a focused field or key capture; vanilla calls this before closing. */
    public boolean shouldCloseOnEsc() {
        return this.editing == null && !this.searching && !this.capturingKey;
    }

    /** Wheel scrolling. Declared without @Override so a changed vanilla signature only disables the wheel. */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.hovered instanceof Setting.Slider slider && this.input.shift()) {
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
        return Math.max(0, this.contentHeight - (this.listBottom() - this.listTop()));
    }

    private int bodyTop() {
        return this.y0 + TITLE_H;
    }

    private int contentLeft() {
        return this.x0 + SIDEBAR + PAD;
    }

    private int contentRight() {
        return this.x1 - PAD - 4;
    }

    private int listTop() {
        return this.bodyTop() + (this.tab == null ? 34 : HEAD_H);
    }

    private int listBottom() {
        return this.y1 - FOOT_H - 2;
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
        this.buildRows();
        this.handleMouse(mouseX, mouseY);
        this.scroll = Draw.approach(this.scroll, this.scrollTarget, dt, 18.0F);

        int accent = Theme.accent(this.config.accent);
        float back = alpha * this.config.guiOpacity / 100.0F;
        int lift = (int) ((1.0F - alpha) * 8.0F);
        Font font = this.font;
        Draw.shadow(g, this.x0, this.y0 + lift, this.x1, this.y1 + lift, back);
        Draw.round(g, this.x0, this.y0 + lift, this.x1, this.y1 + lift, Draw.fade(Theme.PANEL_EDGE, back));
        Draw.round(g, this.x0 + 1, this.y0 + 1 + lift, this.x1 - 1, this.y1 - 1 + lift, Draw.fade(Theme.BACKGROUND, back));
        this.drawTitleBar(g, font, lift, alpha, back, accent);
        this.drawSidebar(g, font, mouseX, mouseY, lift, alpha, back, accent);
        g.fill(this.x0 + SIDEBAR, this.bodyTop() + lift, this.x1 - 1, this.y1 - 2 + lift, Draw.fade(Theme.CONTENT, back));
        this.drawHeader(g, font, lift, alpha, accent, now);
        this.drawRows(g, font, mouseX, mouseY, lift, alpha, back, accent, dt, now);
        this.drawFooter(g, font, lift, alpha);
    }

    // ---- layout ----

    private void buildRows() {
        this.rows.clear();
        int y = 0;
        if (this.tab == null) {
            this.rows.add(new Row(Kind.MASTER, null, null, null, y, 30));
            y += 30 + GAP;
            this.rows.add(new Row(Kind.KEYBIND, null, null, null, y, 34));
            y += 34 + GAP;
            this.rows.add(new Row(Kind.OPACITY, null, null, null, y, 30));
            y += 30 + GAP;
            this.rows.add(new Row(Kind.ACCENT, null, null, null, y, 38));
            y += 38 + GAP;
            y = this.addCards(this.layout.settingsCards(), "", y);
        } else {
            String query = this.search.text().trim();
            List<Menu.Card> shown = new ArrayList<>();
            for (Menu.Card card : this.tab.cards()) {
                if (query.isEmpty() || card.matches(query)) {
                    shown.add(card);
                }
            }
            if (shown.isEmpty()) {
                this.rows.add(new Row(Kind.EMPTY, null, null, "Nothing here matches \"" + query + "\"", y, 20));
                y += 20;
            }
            y = this.addCards(shown, query, y);
        }
        this.contentHeight = y;
        this.scrollTarget = Math.max(0.0F, Math.min(this.maxScroll(), this.scrollTarget));
    }

    private int addCards(List<Menu.Card> cards, String query, int y) {
        int inner = this.contentRight() - this.contentLeft() - 16;
        for (Menu.Card card : cards) {
            Row head = new Row(Kind.CARD, card, null, null, y, CARD_H);
            this.rows.add(head);
            int start = y;
            y += CARD_H;
            // A search that only matched settings inside a card opens it, showing just those settings.
            boolean nameHit = query.isEmpty() || card.name().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT));
            boolean expanded = this.open.contains(card) || !nameHit;
            if (expanded && !card.sections().isEmpty()) {
                for (Setting.Section section : card.sections()) {
                    List<Setting> settings = new ArrayList<>();
                    for (Setting setting : section.settings()) {
                        if (setting.visible() && (nameHit || setting.matches(query))) {
                            settings.add(setting);
                        }
                    }
                    if (settings.isEmpty()) {
                        continue;
                    }
                    if (card.sections().size() > 1) {
                        this.rows.add(new Row(Kind.SUBHEAD, card, null, section.title(), y, SUBHEAD));
                        y += SUBHEAD;
                    }
                    for (Setting setting : settings) {
                        if (setting instanceof Setting.Multi multi) {
                            int height = ROW + this.chipRows(multi, inner) * (CHIP_H + 3) + 2;
                            this.rows.add(new Row(Kind.CHIPS, card, setting, null, y, height));
                            y += height;
                        } else {
                            this.rows.add(new Row(Kind.SETTING, card, setting, null, y, ROW));
                            y += ROW;
                        }
                    }
                }
                y += 4;
            }
            head.block = y - start;
            y += GAP;
        }
        return y;
    }

    private int chipRows(Setting.Multi multi, int width) {
        int rows = 1;
        int x = 0;
        for (String option : multi.options) {
            int w = this.font.width(option) + 10;
            if (x + w > width && x > 0) {
                rows++;
                x = 0;
            }
            x += w + 3;
        }
        return rows;
    }

    // ---- input ----

    private void handleKeys() {
        if (this.capturingKey) {
            for (int i = 0; i < this.input.typedCount(); i++) {
                int key = this.input.typed(i);
                if (key != GLFW.GLFW_KEY_ESCAPE) {
                    this.layout.keys().rebindMenu(key);
                    Toasts.push("Menu key", "Now " + this.layout.keys().menuKey(), Toasts.Kind.SUCCESS);
                }
                this.capturingKey = false;
                return;
            }
            return;
        }
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
            if ((key == GLFW.GLFW_KEY_F && this.input.shortcut() || key == GLFW.GLFW_KEY_SLASH) && this.tab != null) {
                this.searching = true;
            } else if (key == GLFW.GLFW_KEY_PAGE_DOWN || key == GLFW.GLFW_KEY_DOWN) {
                this.scrollBy(key == GLFW.GLFW_KEY_DOWN ? 24.0F : 120.0F);
            } else if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_UP) {
                this.scrollBy(key == GLFW.GLFW_KEY_UP ? -24.0F : -120.0F);
            } else if (key == GLFW.GLFW_KEY_TAB) {
                int count = this.tabs.size() + 1;
                int index = this.tab == null ? this.tabs.size() : this.tabs.indexOf(this.tab);
                int next = Math.floorMod(index + (this.input.shift() ? -1 : 1), count);
                this.selectTab(next == this.tabs.size() ? null : this.tabs.get(next));
            }
        }
    }

    private void handleMouse(int mx, int my) {
        if (this.input.released(0)) {
            this.dragging = null;
            this.draggingOpacity = false;
            this.draggingScrollbar = false;
            this.draggingPanel = false;
        }
        if (this.draggingPanel) {
            this.offsetX += mx - this.grabX;
            this.offsetY += my - this.grabY;
            this.grabX = mx;
            this.grabY = my;
            this.init();
        }
        if (this.dragging != null) {
            Row row = this.rowOf(this.dragging);
            if (row != null) {
                int right = this.settingRight(row);
                this.dragging.setFraction((mx - (right - SLIDER_W)) / (double) SLIDER_W);
            }
        }
        if (this.draggingOpacity) {
            this.setOpacityFrom(mx);
        }
        if (this.draggingScrollbar) {
            int track = this.listBottom() - this.listTop();
            float fraction = (my - this.listTop()) / (float) track;
            this.scrollTarget = this.scroll = Math.max(0.0F, Math.min(this.maxScroll(), fraction * this.contentHeight - track / 2.0F));
        }
        Row hoveredRow = this.rowAt(mx, my);
        this.hovered = hoveredRow == null ? null : hoveredRow.setting;

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
        if (this.capturingKey) {
            this.capturingKey = false;
        }
        if (left && Draw.inside(mx, my, this.x0, this.y0, this.x1, this.bodyTop())) {
            this.draggingPanel = true;
            this.grabX = mx;
            this.grabY = my;
            return;
        }
        if (this.clickSidebar(mx, my)) {
            return;
        }
        if (left && this.tab != null && this.inSearch(mx, my)) {
            this.searching = true;
            return;
        }
        if (left && mx >= this.x1 - PAD - 2 && mx < this.x1 - 2 && my >= this.listTop() && my < this.listBottom() && this.maxScroll() > 0) {
            this.draggingScrollbar = true;
            return;
        }
        Row row = this.rowAt(mx, my);
        if (row != null) {
            this.clickRow(row, mx, my, button);
        }
    }

    private Row rowAt(int mx, int my) {
        if (mx < this.contentLeft() || mx >= this.contentRight() || my < this.listTop() || my >= this.listBottom()) {
            return null;
        }
        int local = my - this.listTop() + Math.round(this.scroll);
        for (Row row : this.rows) {
            if (local >= row.y && local < row.y + row.height) {
                // Rows cut off by the edges are not drawn, so they must not take clicks either.
                int top = this.rowTop(row);
                return top >= this.listTop() && top + row.height <= this.listBottom() ? row : null;
            }
        }
        return null;
    }

    private Row rowOf(Setting setting) {
        for (Row row : this.rows) {
            if (row.setting == setting) {
                return row;
            }
        }
        return null;
    }

    private int rowTop(Row row) {
        return this.listTop() + row.y - Math.round(this.scroll);
    }

    /** Right edge of the controls in a setting row (settings sit inside their card's padding). */
    private int settingRight(Row row) {
        return this.contentRight() - 8;
    }

    private int settingLeft() {
        return this.contentLeft() + 8;
    }

    private void clickRow(Row row, int mx, int my, int button) {
        int top = this.rowTop(row);
        int left = this.contentLeft();
        int right = this.contentRight();
        switch (row.kind) {
            case CARD -> {
                Menu.Card card = row.card;
                if (card.hasToggle() && Draw.inside(mx, my, left + 6, top + 6, left + 6 + ONOFF_W, top + 6 + ONOFF_H)) {
                    card.toggle().run();
                    return;
                }
                if (Draw.inside(mx, my, right - 24, top + 6, right - 6, top + 24) || card.select() == null) {
                    this.toggleOpen(card);
                    return;
                }
                card.select().run();
            }
            case MASTER -> {
                if (Draw.inside(mx, my, right - 70, top + 6, right - 8, top + 24)) {
                    this.config.masterSwitch = !this.config.masterSwitch;
                    if (!this.config.masterSwitch && this.macro.running()) {
                        this.macro.stop("Master switch off");
                    }
                    this.config.markDirty();
                }
            }
            case KEYBIND -> {
                if (button == 0 && Draw.inside(mx, my, left + 8, top + 17, right - 8, top + 30)) {
                    this.capturingKey = true;
                }
            }
            case OPACITY -> {
                if (button == 0) {
                    this.draggingOpacity = true;
                    this.setOpacityFrom(mx);
                }
            }
            case ACCENT -> {
                int x = left + 8;
                for (String name : Theme.ACCENT_NAMES) {
                    if (Draw.inside(mx, my, x, top + 17, x + 18, top + 33)) {
                        this.config.accent = name;
                        this.config.markDirty();
                    }
                    x += 24;
                }
            }
            case SETTING, CHIPS -> this.clickSetting(row, mx, my, button);
            default -> {
            }
        }
    }

    private void toggleOpen(Menu.Card card) {
        if (!this.open.remove(card)) {
            this.open.add(card);
        }
    }

    private void setOpacityFrom(int mx) {
        int left = this.contentLeft() + 8;
        int width = this.contentRight() - 8 - left;
        double fraction = Math.max(0.0, Math.min(1.0, (mx - left) / (double) width));
        int value = (int) Math.round(OPACITY_MIN + fraction * (100 - OPACITY_MIN));
        if (value != this.config.guiOpacity) {
            this.config.guiOpacity = value;
            this.config.markDirty();
        }
    }

    private void clickSetting(Row row, int mx, int my, int button) {
        Setting setting = row.setting;
        int right = this.settingRight(row);
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
        int left = this.settingLeft();
        int width = this.contentRight() - 8 - left;
        int x = 0;
        int y = top + ROW;
        for (String option : multi.options) {
            int w = this.font.width(option) + 10;
            if (x + w > width && x > 0) {
                x = 0;
                y += CHIP_H + 3;
            }
            if (Draw.inside(mx, my, left + x, y, left + x + w, y + CHIP_H)) {
                return option;
            }
            x += w + 3;
        }
        return null;
    }

    private int navHeight() {
        int available = this.y1 - this.bodyTop() - 16;
        return Math.max(18, Math.min(30, available / (this.tabs.size() + 1) - 2));
    }

    private boolean clickSidebar(int mx, int my) {
        if (mx < this.x0 || mx >= this.x0 + SIDEBAR || my < this.bodyTop()) {
            return false;
        }
        int h = this.navHeight();
        int y = this.bodyTop() + 6;
        for (Menu.Tab t : this.tabs) {
            if (my >= y && my < y + h) {
                this.selectTab(t);
                return true;
            }
            y += h + 2;
        }
        int settingsTop = this.y1 - 6 - h;
        if (my >= settingsTop && my < settingsTop + h) {
            this.selectTab(null);
        }
        return true;
    }

    private void selectTab(Menu.Tab t) {
        this.tab = t;
        this.scroll = this.scrollTarget = 0.0F;
        this.search.set("", 40);
        this.searching = false;
        this.capturingKey = false;
    }

    private boolean inSearch(int mx, int my) {
        int top = this.bodyTop() + 34;
        return Draw.inside(mx, my, this.contentLeft(), top, this.contentRight(), top + 15);
    }

    private boolean inEditor(int mx, int my) {
        Row row = this.rowOf(this.editing);
        if (row == null) {
            return false;
        }
        int top = this.rowTop(row) + (ROW - CONTROL_H) / 2;
        int right = this.settingRight(row);
        return Draw.inside(mx, my, right - TEXT_W, top, right, top + CONTROL_H);
    }

    private void commitEdit() {
        if (this.editing != null) {
            this.editing.put(this.editor.text());
            this.editing = null;
        }
    }

    // ---- drawing ----

    private void drawTitleBar(GuiGraphicsExtractor g, Font font, int lift, float alpha, float back, int accent) {
        int top = this.y0 + 1 + lift;
        int bottom = this.bodyTop() + lift;
        int half = (top + bottom) / 2;
        g.fill(this.x0 + 2, top, this.x1 - 2, half, Draw.fade(Theme.TITLE_TOP, back));
        g.fill(this.x0 + 1, half, this.x1 - 1, bottom, Draw.fade(Theme.TITLE_BOTTOM, back));
        g.fill(this.x0 + 1, bottom - 1, this.x1 - 1, bottom, Draw.fade(Theme.TITLE_LINE, back));
        // Grip dots, as in the design.
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                g.fill(this.x0 + 9 + i * 3, top + 7 + j * 4, this.x0 + 11 + i * 3, top + 9 + j * 4, Draw.fade(Theme.GRIP, alpha));
            }
        }
        int tx = this.x0 + 20;
        int ty = top + 6;
        Draw.textShadow(g, font, "SKY", tx, ty, Draw.fade(Theme.TEXT, alpha));
        Draw.textShadow(g, font, "MACRO", tx + font.width("SKY"), ty, Draw.fade(accent, alpha));

        String status;
        int color;
        if (!this.config.masterSwitch) {
            status = "Master switch off";
            color = Theme.BAD;
        } else if (this.macro.running()) {
            status = "● " + this.macro.mode().label + ": " + this.macro.status();
            color = accent;
        } else {
            status = "Idle";
            color = Theme.FAINT;
        }
        int maxWidth = this.x1 - 10 - (tx + font.width("SKYMACRO") + 16);
        Draw.right(g, font, Draw.fit(font, status, maxWidth), this.x1 - 10, ty, Draw.fade(color, alpha));
    }

    private void drawSidebar(GuiGraphicsExtractor g, Font font, int mx, int my, int lift, float alpha, float back, int accent) {
        int left = this.x0 + 1;
        int right = this.x0 + SIDEBAR;
        int top = this.bodyTop();
        g.fill(left, top + lift, right, this.y1 - 2 + lift, Draw.fade(Theme.SIDEBAR, back));
        g.fill(right - 1, top + lift, right, this.y1 - 2 + lift, Draw.fade(Theme.SIDEBAR_LINE, back));
        int h = this.navHeight();
        int y = top + 6;
        for (Menu.Tab t : this.tabs) {
            this.drawNav(g, font, t.icon(), t.name(), y, h, t == this.tab, mx, my, lift, alpha, accent);
            y += h + 2;
        }
        int settingsTop = this.y1 - 6 - h;
        g.fill(left + 12, settingsTop - 5 + lift, right - 12, settingsTop - 4 + lift, Draw.fade(Theme.BORDER, alpha));
        this.drawNav(g, font, "⚙", "Settings", settingsTop, h, this.tab == null, mx, my, lift, alpha, accent);
    }

    private void drawNav(GuiGraphicsExtractor g, Font font, String icon, String label, int y, int h, boolean active, int mx, int my, int lift,
        float alpha, int accent) {
        int left = this.x0 + 5;
        int right = this.x0 + SIDEBAR - 5;
        boolean hover = Draw.inside(mx, my, left, y, right, y + h);
        if (active) {
            Draw.round(g, left, y + lift, right, y + h + lift, Draw.fade(Theme.NAV_ACTIVE_LINE, alpha));
            Draw.round(g, left + 1, y + 1 + lift, right - 1, y + h - 1 + lift, Draw.fade(Theme.NAV_ACTIVE, alpha));
        } else if (hover) {
            Draw.round(g, left, y + lift, right, y + h + lift, Draw.fade(0x10FFFFFF, alpha));
        }
        int color = active ? accent : hover ? Theme.TEXT : Theme.MUTED;
        int cx = (left + right) / 2;
        if (h >= 26) {
            Draw.centered(g, font, icon, cx, y + 5 + lift, Draw.fade(color, alpha));
            Draw.centered(g, font, Draw.fit(font, label, right - left - 2), cx, y + h - 11 + lift, Draw.fade(color, alpha));
        } else {
            Draw.centered(g, font, icon, cx, y + (h - 8) / 2 + lift, Draw.fade(color, alpha));
        }
    }

    private void drawHeader(GuiGraphicsExtractor g, Font font, int lift, float alpha, int accent, long now) {
        int left = this.contentLeft();
        int right = this.contentRight();
        int top = this.bodyTop() + 9 + lift;
        String title = this.tab == null ? "Global Settings" : this.tab.name();
        String description = this.tab == null ? "Controls that apply across every module." : this.tab.description();
        Draw.textShadow(g, font, title, left, top, Draw.fade(Theme.TEXT, alpha));
        Draw.text(g, font, Draw.fit(font, description, right - left), left, top + 11, Draw.fade(Theme.MUTED, alpha));
        if (this.tab == null) {
            return;
        }
        int sy = this.bodyTop() + 34 + lift;
        Draw.round(g, left, sy, right, sy + 15, Draw.fade(this.searching ? accent : Theme.BORDER, alpha));
        Draw.round(g, left + 1, sy + 1, right - 1, sy + 14, Draw.fade(Theme.CARD, alpha));
        Draw.text(g, font, "⌕", left + 5, sy + 4, Draw.fade(Theme.FAINT, alpha));
        String query = this.search.text();
        int tx = left + 16;
        if (query.isEmpty() && !this.searching) {
            Draw.text(g, font, "Search this tab  ( / )", tx, sy + 4, Draw.fade(Theme.FAINT, alpha));
        } else {
            String shown = Draw.fit(font, query, right - tx - 6);
            Draw.text(g, font, shown, tx, sy + 4, Draw.fade(Theme.TEXT, alpha));
            if (this.searching && now / 500L % 2L == 0L) {
                int cx = tx + font.width(query.substring(0, Math.min(this.search.caret(), shown.length())));
                g.fill(cx, sy + 3, cx + 1, sy + 12, Draw.fade(Theme.TEXT, alpha));
            }
        }
    }

    private void drawRows(GuiGraphicsExtractor g, Font font, int mx, int my, int lift, float alpha, float back, int accent, float dt, long now) {
        int top = this.listTop();
        int bottom = this.listBottom();
        int left = this.contentLeft();
        int right = this.contentRight();
        for (Row row : this.rows) {
            int y = this.rowTop(row);
            if (row.kind == Kind.CARD) {
                this.drawCardBack(g, row, y, top, bottom, lift, back, accent, mx, my);
            }
            if (y < top || y + row.height > bottom) {
                continue;
            }
            int yy = y + lift;
            switch (row.kind) {
                case CARD -> this.drawCardHead(g, font, row.card, yy, y, mx, my, alpha, accent);
                case SUBHEAD -> Draw.text(g, font, row.title.toUpperCase(), this.settingLeft(), yy + 3, Draw.fade(Draw.withAlpha(accent, 0xCC), alpha));
                case SETTING, CHIPS -> {
                    Setting setting = row.setting;
                    boolean hover = Draw.inside(mx, my, left + 4, y, right - 4, y + row.height);
                    if (hover) {
                        g.fill(left + 4, yy, right - 4, yy + row.height, Draw.fade(0x0CFFFFFF, alpha));
                    }
                    Draw.text(g, font, Draw.fit(font, setting.name, right - left - 150), this.settingLeft(), yy + 5, Draw.fade(Theme.TEXT, alpha));
                    this.drawControl(g, font, row, setting, yy, mx, my, y, alpha, accent, dt, now);
                }
                case MASTER -> this.drawMaster(g, font, yy, y, mx, my, alpha, back, accent);
                case KEYBIND -> this.drawKeybind(g, font, yy, alpha, back, accent, now);
                case OPACITY -> this.drawOpacity(g, font, yy, alpha, back, accent);
                case ACCENT -> this.drawAccents(g, font, yy, alpha, back);
                case EMPTY -> Draw.centered(g, font, row.title, (left + right) / 2, yy + 6, Draw.fade(Theme.MUTED, alpha));
            }
        }
        this.drawScrollbar(g, lift, alpha);
    }

    /** Card background over the header and any open settings, clipped to the list area. */
    private void drawCardBack(GuiGraphicsExtractor g, Row row, int y, int top, int bottom, int lift, float back, int accent, int mx, int my) {
        int left = this.contentLeft();
        int right = this.contentRight();
        int y1 = y;
        int y2 = y + row.block;
        if (y2 <= top || y1 >= bottom) {
            return;
        }
        Menu.Card card = row.card;
        boolean on = card.hasToggle() && card.on().getAsBoolean();
        boolean hover = Draw.inside(mx, my, left, Math.max(y1, top), right, Math.min(y1 + CARD_H, bottom));
        int border = on ? Draw.mix(Theme.BORDER, accent, 0.45F) : Theme.BORDER;
        int fill = hover ? Theme.CARD_HOVER : Theme.CARD;
        int cy1 = Math.max(y1, top) + lift;
        int cy2 = Math.min(y2, bottom) + lift;
        if (y1 >= top && y2 <= bottom) {
            Draw.round(g, left, cy1, right, cy2, Draw.fade(border, back));
            Draw.round(g, left + 1, cy1 + 1, right - 1, cy2 - 1, Draw.fade(fill, back));
        } else {
            g.fill(left, cy1, right, cy2, Draw.fade(border, back));
            g.fill(left + 1, cy1 + (y1 >= top ? 1 : 0), right - 1, cy2 - (y2 <= bottom ? 1 : 0), Draw.fade(fill, back));
        }
        if (row.block > CARD_H && y1 + CARD_H > top && y1 + CARD_H < bottom) {
            g.fill(left + 8, y1 + CARD_H - 1 + lift, right - 8, y1 + CARD_H + lift, Draw.fade(Theme.BORDER, back));
        }
    }

    private void drawCardHead(GuiGraphicsExtractor g, Font font, Menu.Card card, int yy, int y, int mx, int my, float alpha, int accent) {
        int left = this.contentLeft();
        int right = this.contentRight();
        int nameX = left + 10;
        if (card.hasToggle()) {
            boolean on = card.on().getAsBoolean();
            int bx = left + 6;
            int by = yy + 6;
            boolean hover = Draw.inside(mx, my, bx, y + 6, bx + ONOFF_W, y + 6 + ONOFF_H);
            if (on) {
                Draw.round(g, bx, by, bx + ONOFF_W, by + ONOFF_H, Draw.fade(Theme.accentDeep(accent), alpha));
                Draw.round(g, bx, by, bx + ONOFF_W, by + ONOFF_H - 2, Draw.fade(hover ? Draw.mix(accent, 0xFFFFFFFF, 0.15F) : accent, alpha));
                Draw.centered(g, font, "ON", bx + ONOFF_W / 2, by + 5, Draw.fade(Theme.onAccent(accent), alpha));
            } else {
                Draw.round(g, bx, by, bx + ONOFF_W, by + ONOFF_H, Draw.fade(hover ? Theme.NAV_ACTIVE_LINE : Theme.OFF_LINE, alpha));
                Draw.round(g, bx + 1, by + 1, bx + ONOFF_W - 1, by + ONOFF_H - 1, Draw.fade(Theme.OFF_BUTTON, alpha));
                Draw.centered(g, font, "OFF", bx + ONOFF_W / 2, by + 5, Draw.fade(Theme.MUTED, alpha));
            }
            nameX = bx + ONOFF_W + 8;
        }
        String chip = card.chip() == null ? null : card.chip().get();
        int nameY = chip == null ? yy + 11 : yy + 5;
        Draw.text(g, font, Draw.fit(font, card.name(), right - 32 - nameX), nameX, nameY, Draw.fade(Theme.TEXT, alpha));
        if (chip != null) {
            int cw = font.width(chip) + 8;
            int cy = yy + 16;
            Draw.round(g, nameX, cy, nameX + cw, cy + 11, Draw.fade(Theme.BORDER, alpha));
            Draw.round(g, nameX + 1, cy + 1, nameX + cw - 1, cy + 10, Draw.fade(Theme.FIELD, alpha));
            Draw.text(g, font, chip, nameX + 4, cy + 2, Draw.fade(Theme.MUTED, alpha));
        }
        if (!card.sections().isEmpty()) {
            boolean opened = this.open.contains(card);
            int gx = right - 24;
            int gy = yy + 6;
            boolean hover = Draw.inside(mx, my, gx, y + 6, gx + 18, y + 24);
            Draw.round(g, gx, gy, gx + 18, gy + 18, Draw.fade(Theme.BORDER, alpha));
            Draw.round(g, gx + 1, gy + 1, gx + 17, gy + 17, Draw.fade(opened ? Theme.NAV_ACTIVE : hover ? Theme.CARD_HOVER : Theme.CARD, alpha));
            Draw.centered(g, font, "⚙", gx + 9, gy + 5, Draw.fade(opened ? accent : hover ? Theme.TEXT : Theme.MUTED, alpha));
        }
    }

    private void drawPanelCard(GuiGraphicsExtractor g, int yy, int height, float back) {
        int left = this.contentLeft();
        int right = this.contentRight();
        Draw.round(g, left, yy, right, yy + height, Draw.fade(Theme.BORDER, back));
        Draw.round(g, left + 1, yy + 1, right - 1, yy + height - 1, Draw.fade(Theme.CARD, back));
    }

    private void drawMaster(GuiGraphicsExtractor g, Font font, int yy, int y, int mx, int my, float alpha, float back, int accent) {
        int left = this.contentLeft();
        int right = this.contentRight();
        this.drawPanelCard(g, yy, 30, back);
        Draw.text(g, font, "Master Switch", left + 8, yy + 6, Draw.fade(Theme.TEXT, alpha));
        Draw.text(g, font, "Off stops the macro and blocks every start", left + 8, yy + 17, Draw.fade(Theme.FAINT, alpha));
        boolean on = this.config.masterSwitch;
        int bx = right - 70;
        int by = yy + 6;
        boolean hover = Draw.inside(mx, my, bx, y + 6, right - 8, y + 24);
        if (on) {
            Draw.round(g, bx, by, right - 8, by + 18, Draw.fade(Theme.accentDeep(accent), alpha));
            Draw.round(g, bx, by, right - 8, by + 16, Draw.fade(hover ? Draw.mix(accent, 0xFFFFFFFF, 0.15F) : accent, alpha));
            Draw.centered(g, font, "ENABLED", (bx + right - 8) / 2, by + 5, Draw.fade(Theme.onAccent(accent), alpha));
        } else {
            Draw.round(g, bx, by, right - 8, by + 18, Draw.fade(Theme.OFF_LINE, alpha));
            Draw.round(g, bx + 1, by + 1, right - 9, by + 17, Draw.fade(Theme.OFF_BUTTON, alpha));
            Draw.centered(g, font, "DISABLED", (bx + right - 8) / 2, by + 5, Draw.fade(Theme.MUTED, alpha));
        }
    }

    private void drawKeybind(GuiGraphicsExtractor g, Font font, int yy, float alpha, float back, int accent, long now) {
        int left = this.contentLeft();
        int right = this.contentRight();
        this.drawPanelCard(g, yy, 34, back);
        Draw.text(g, font, "Menu Key", left + 8, yy + 5, Draw.fade(Theme.TEXT, alpha));
        String toggle = this.layout.keys().toggleKey();
        Draw.right(g, font, "Start/stop key: " + (toggle == null ? "not bound" : toggle), right - 8, yy + 5, Draw.fade(Theme.FAINT, alpha));
        int fy = yy + 17;
        Draw.round(g, left + 8, fy, right - 8, fy + 13, Draw.fade(this.capturingKey ? accent : Theme.BORDER, alpha));
        Draw.round(g, left + 9, fy + 1, right - 9, fy + 12, Draw.fade(Theme.FIELD, alpha));
        String text = this.capturingKey ? (now / 500L % 2L == 0L ? "Press a key...  (Esc cancels)" : "Press a key...")
            : this.layout.keys().menuKey();
        Draw.text(g, font, text, left + 13, fy + 3, Draw.fade(this.capturingKey ? accent : Theme.TEXT, alpha));
    }

    private void drawOpacity(GuiGraphicsExtractor g, Font font, int yy, float alpha, float back, int accent) {
        int left = this.contentLeft();
        int right = this.contentRight();
        this.drawPanelCard(g, yy, 30, back);
        Draw.text(g, font, "Panel Opacity", left + 8, yy + 5, Draw.fade(Theme.TEXT, alpha));
        Draw.right(g, font, this.config.guiOpacity + "%", right - 8, yy + 5, Draw.fade(Theme.MUTED, alpha));
        int tx = left + 8;
        int tw = right - 8 - tx;
        int ty = yy + 20;
        double fraction = (this.config.guiOpacity - OPACITY_MIN) / (double) (100 - OPACITY_MIN);
        int fill = tx + (int) Math.round(fraction * tw);
        g.fill(tx, ty, tx + tw, ty + 3, Draw.fade(Theme.OFF, alpha));
        g.fill(tx, ty, fill, ty + 3, Draw.fade(accent, alpha));
        Draw.round(g, fill - 2, ty - 3, fill + 3, ty + 6, Draw.fade(this.draggingOpacity ? 0xFFFFFFFF : 0xFFD0D3DC, alpha));
    }

    private void drawAccents(GuiGraphicsExtractor g, Font font, int yy, float alpha, float back) {
        int left = this.contentLeft();
        this.drawPanelCard(g, yy, 38, back);
        Draw.text(g, font, "Accent Theme", left + 8, yy + 5, Draw.fade(Theme.TEXT, alpha));
        int x = left + 8;
        for (String name : Theme.ACCENT_NAMES) {
            boolean selected = name.equals(this.config.accent);
            int sy = yy + 17;
            if (selected) {
                Draw.round(g, x - 2, sy - 2, x + 20, sy + 18, Draw.fade(Theme.TEXT, alpha));
                Draw.round(g, x - 1, sy - 1, x + 19, sy + 17, Draw.fade(Theme.CARD, alpha));
            }
            Draw.round(g, x, sy, x + 18, sy + 16, Draw.fade(Theme.ACCENTS.get(name), alpha));
            x += 24;
        }
        String chosen = this.config.accent;
        Draw.text(g, font, Character.toUpperCase(chosen.charAt(0)) + chosen.substring(1), x + 4, yy + 21, Draw.fade(Theme.MUTED, alpha));
    }

    private void drawControl(GuiGraphicsExtractor g, Font font, Row row, Setting setting, int yy, int mx, int my, int y, float alpha,
        int accent, float dt, long now) {
        int right = this.settingRight(row);
        int cy = yy + (ROW - CONTROL_H) / 2;
        int hitY = y + (ROW - CONTROL_H) / 2;
        switch (setting) {
            case Setting.Toggle toggle -> {
                float t = this.anim.getOrDefault(toggle, toggle.get() ? 1.0F : 0.0F);
                t = Draw.approach(t, toggle.get() ? 1.0F : 0.0F, dt, 16.0F);
                this.anim.put(toggle, t);
                int tx = right - TOGGLE_W;
                int ty = cy + 1;
                Draw.round(g, tx, ty, right, ty + 11, Draw.fade(Draw.mix(Theme.OFF, accent, t), alpha));
                int knob = tx + 1 + Math.round(t * (TOGGLE_W - 11));
                Draw.round(g, knob, ty + 1, knob + 9, ty + 10, Draw.fade(0xFFF4F5F8, alpha));
            }
            case Setting.Slider slider -> {
                int tx = right - SLIDER_W;
                int ty = cy + 5;
                double fraction = Math.max(0.0, Math.min(1.0, slider.fraction()));
                int fill = tx + (int) Math.round(fraction * SLIDER_W);
                g.fill(tx, ty, right, ty + 3, Draw.fade(Theme.OFF, alpha));
                g.fill(tx, ty, fill, ty + 3, Draw.fade(accent, alpha));
                boolean active = this.dragging == slider || Draw.inside(mx, my, tx - 4, hitY, right + 4, hitY + CONTROL_H);
                Draw.round(g, fill - 2, ty - 3, fill + 3, ty + 6, Draw.fade(active ? 0xFFFFFFFF : 0xFFD0D3DC, alpha));
                Draw.right(g, font, slider.valueText(), tx - 6, cy + 3, Draw.fade(Theme.MUTED, alpha));
            }
            case Setting.Choice choice -> {
                int bx = right - CHOICE_W;
                boolean hover = Draw.inside(mx, my, bx, hitY, right, hitY + CONTROL_H);
                Draw.round(g, bx, cy, right, cy + CONTROL_H, Draw.fade(hover ? accent : Theme.BORDER, alpha));
                Draw.round(g, bx + 1, cy + 1, right - 1, cy + CONTROL_H - 1, Draw.fade(Theme.FIELD, alpha));
                Draw.text(g, font, "‹", bx + 4, cy + 3, Draw.fade(Theme.MUTED, alpha));
                Draw.right(g, font, "›", right - 4, cy + 3, Draw.fade(Theme.MUTED, alpha));
                Draw.centered(g, font, Draw.fit(font, choice.label(), CHOICE_W - 22), bx + CHOICE_W / 2, cy + 3, Draw.fade(Theme.TEXT, alpha));
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
                Draw.round(g, bx, cy, right, cy + CONTROL_H, Draw.fade(hover ? accent : Theme.accentDeep(accent), alpha));
                Draw.centered(g, font, button.label, bx + BUTTON_W / 2, cy + 3, Draw.fade(Theme.onAccent(accent), alpha));
            }
            case Setting.Multi multi -> this.drawChips(g, font, multi, yy, mx, my, y, alpha, accent);
            default -> {
            }
        }
    }

    private void drawChips(GuiGraphicsExtractor g, Font font, Setting.Multi multi, int yy, int mx, int my, int y, float alpha, int accent) {
        int left = this.settingLeft();
        int width = this.contentRight() - 8 - left;
        int x = 0;
        int cy = yy + ROW;
        int hy = y + ROW;
        for (String option : multi.options) {
            int w = font.width(option) + 10;
            if (x + w > width && x > 0) {
                x = 0;
                cy += CHIP_H + 3;
                hy += CHIP_H + 3;
            }
            boolean on = multi.selected(option);
            boolean hover = Draw.inside(mx, my, left + x, hy, left + x + w, hy + CHIP_H);
            int backColor = on ? accent : hover ? Theme.OFF : Theme.FIELD;
            Draw.round(g, left + x, cy, left + x + w, cy + CHIP_H, Draw.fade(backColor, alpha));
            Draw.centered(g, font, option, left + x + w / 2, cy + 2, Draw.fade(on ? Theme.onAccent(accent) : Theme.MUTED, alpha));
            x += w + 3;
        }
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int lift, float alpha) {
        int max = this.maxScroll();
        if (max <= 0) {
            return;
        }
        int top = this.listTop();
        int track = this.listBottom() - top;
        int thumb = Math.max(20, track * track / this.contentHeight);
        int y = top + Math.round((track - thumb) * (this.scroll / max));
        int x = this.x1 - PAD + 1;
        g.fill(x, top + lift, x + 2, top + track + lift, Draw.fade(0x22FFFFFF, alpha));
        g.fill(x, y + lift, x + 2, y + thumb + lift, Draw.fade(this.draggingScrollbar ? 0xCCFFFFFF : 0x66FFFFFF, alpha));
    }

    private void drawFooter(GuiGraphicsExtractor g, Font font, int lift, float alpha) {
        int left = this.contentLeft();
        int y = this.y1 - FOOT_H + lift;
        String text;
        if (this.editing != null) {
            text = "Enter to save, Esc to cancel, Ctrl+V to paste";
        } else if (this.hovered != null) {
            text = this.hovered.description + (this.hovered.hasValue() ? "   /sm set " + this.hovered.id : "");
        } else if (this.tab == null) {
            text = "Drag the title bar to move the menu. Tab switches pages.";
        } else {
            text = "ON/OFF runs a macro, ⚙ opens its settings, click a name to make it the start/stop key's macro.";
        }
        Draw.text(g, font, Draw.fit(font, text, this.x1 - PAD - left), left, y, Draw.fade(Theme.FAINT, alpha));
    }
}
