package com.skyblockminer.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SettingTest {
    private boolean flag;
    private double number;
    private String choice = "b";
    private String text = "";

    @Test
    void toggleParsesWords() {
        Setting.Toggle toggle = new Setting.Toggle("t", "T", "", () -> this.flag, v -> this.flag = v);
        assertNull(toggle.set("on"));
        assertTrue(this.flag);
        assertNull(toggle.set("false"));
        assertFalse(this.flag);
        assertNotNull(toggle.set("maybe"));
        toggle.toggle();
        assertTrue(this.flag);
    }

    @Test
    void sliderSnapsAndClamps() {
        AtomicInteger changes = new AtomicInteger();
        Setting.Slider slider = new Setting.Slider("s", "S", "", 0, 10, 0.5, "", () -> this.number, v -> this.number = v);
        slider.onChange(changes::incrementAndGet);
        slider.setFraction(0.33);
        assertEquals(3.5, this.number, 1e-9);
        slider.setFraction(2.0);
        assertEquals(10.0, this.number, 1e-9);
        slider.nudge(1);
        assertEquals(10.0, this.number, 1e-9);
        assertEquals(2, changes.get(), "unchanged values must not fire change events");
        assertNotNull(slider.set("11"));
        assertNotNull(slider.set("x"));
        assertNull(slider.set("2.2"));
        assertEquals(2.0, this.number, 1e-9);
        assertEquals("2.0", slider.valueText());
    }

    @Test
    void integerSliderShowsWholeNumbers() {
        Setting.Slider slider = new Setting.Slider("s", "S", "", 0, 100, 1, "%", () -> this.number, v -> this.number = v);
        slider.set("48");
        assertEquals("48%", slider.valueText());
    }

    @Test
    void choiceCyclesBothWays() {
        Setting.Choice choice = new Setting.Choice("c", "C", "", () -> List.of("a", "b", "c"), String::toUpperCase,
            () -> this.choice, v -> this.choice = v);
        choice.cycle(1);
        assertEquals("c", this.choice);
        choice.cycle(1);
        assertEquals("a", this.choice);
        choice.cycle(-1);
        assertEquals("c", this.choice);
        assertEquals("C", choice.label());
        assertNull(choice.set("B"));
        assertEquals("b", this.choice);
        assertNotNull(choice.set("z"));
    }

    @Test
    void multiAcceptsAllNoneAndLists() {
        List<String> chosen = new ArrayList<>();
        Setting.Multi multi = new Setting.Multi("m", "M", "", List.of("ruby", "jade", "topaz"), () -> chosen);
        assertNull(multi.set("all"));
        assertEquals(List.of("ruby", "jade", "topaz"), chosen);
        assertNull(multi.set("none"));
        assertTrue(chosen.isEmpty());
        assertNull(multi.set("topaz, ruby topaz"));
        assertEquals(List.of("topaz", "ruby"), chosen);
        assertNotNull(multi.set("diamond"));
        assertEquals(List.of("topaz", "ruby"), chosen, "a bad value leaves the selection alone");
        multi.toggle("ruby");
        assertFalse(multi.selected("ruby"));
    }

    @Test
    void textTruncatesAndClears() {
        Setting.Text field = new Setting.Text("x", "X", "", "", true, 5, () -> this.text, v -> this.text = v);
        field.put("abcdefgh");
        assertEquals("abcde", this.text);
        assertEquals("(hidden)", field.valueText());
        field.set("off");
        assertEquals("", this.text);
    }

    @Test
    void searchMatchesNameDescriptionAndId() {
        Setting.Toggle toggle = new Setting.Toggle("farming.snapyaw", "Snap yaw", "Rounds the yaw", () -> true, v -> { });
        assertTrue(toggle.matches("SNAP"));
        assertTrue(toggle.matches("rounds"));
        assertTrue(toggle.matches("farming."));
        assertFalse(toggle.matches("fishing"));
    }
}
