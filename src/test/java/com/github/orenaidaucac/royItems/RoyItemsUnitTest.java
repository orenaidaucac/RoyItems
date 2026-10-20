package com.github.orenaidaucac.royItems;

import com.github.orenaidaucac.royItems.command.RoyItemsCommand;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.gui.GuiHelper;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.socket.GemSocketManager;
import com.github.orenaidaucac.royItems.stat.DefenseManager;
import com.github.orenaidaucac.royItems.stat.PlayerStatCache;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoyItemsUnitTest {

    @Test
    @DisplayName("Stat Calculation: Total = (Base + Flat) * (1 + Percent)")
    void testStatFormula() {
        PlayerStatCache cache = new PlayerStatCache();

        // Base 10, Flat +15, Percent +50% -> (10 + 15) * 1.5 = 37.5
        cache.addStat(StatType.ATTACK_DAMAGE, new StatModifier(15.0, 0.50));
        double total = cache.calculateTotal(StatType.ATTACK_DAMAGE, 10.0);
        assertEquals(37.5, total, 0.001);

        // Multiple additive modifiers
        cache.addStat(StatType.ATTACK_DAMAGE, new StatModifier(5.0, 0.20));
        // Flat = 20, Percent = 0.70 -> (10 + 20) * 1.70 = 51.0
        double totalCombined = cache.calculateTotal(StatType.ATTACK_DAMAGE, 10.0);
        assertEquals(51.0, totalCombined, 0.001);
    }

    @Test
    @DisplayName("Stat Modifier Parsing: numbers, percentages, and maps")
    void testStatModifierParsing() {
        StatModifier m1 = StatModifier.parse(25);
        assertEquals(25.0, m1.getFlat());
        assertEquals(0.0, m1.getPercent());

        StatModifier m2 = StatModifier.parse("25%");
        assertEquals(0.0, m2.getFlat());
        assertEquals(0.25, m2.getPercent(), 0.0001);

        StatModifier m3 = StatModifier.parse("-15%");
        assertEquals(-0.15, m3.getPercent(), 0.0001);
    }

    @Test
    @DisplayName("Gem Socket Compatibility Rules")
    void testGemSocketRules() {
        GemSocketManager manager = new GemSocketManager(null, null, null);

        // Universal socket '1' accepts any gem
        assertTrue(manager.isSocketCompatible("1", "A"));
        assertTrue(manager.isSocketCompatible("1", "RUBY"));
        assertTrue(manager.isSocketCompatible("1", "ANYTHING"));

        // Exact match
        assertTrue(manager.isSocketCompatible("A", "A"));
        assertFalse(manager.isSocketCompatible("A", "B"));

        // Multi-type socket 'ABC' accepts A, B, or C
        assertTrue(manager.isSocketCompatible("ABC", "A"));
        assertTrue(manager.isSocketCompatible("ABC", "B"));
        assertTrue(manager.isSocketCompatible("ABC", "C"));
        assertFalse(manager.isSocketCompatible("ABC", "D"));
    }

    @Test
    @DisplayName("Duration string parsing")
    void testDurationParsing() {
        long sec = ItemBuilder.parseDurationMillis("10 seconds");
        assertEquals(10_000L, sec);

        long hour = ItemBuilder.parseDurationMillis("2 hours");
        assertEquals(7_200_000L, hour);

        long days = ItemBuilder.parseDurationMillis("3 days");
        assertEquals(259_200_000L, days);

        long month = ItemBuilder.parseDurationMillis("1 month");
        assertEquals(2_592_000_000L, month);
    }

    @Test
    @DisplayName("Command Amount Range Parsing: specific and ranges")
    void testAmountRangeParsing() {
        assertEquals(1, RoyItemsCommand.parseAmount("1"));
        assertEquals(64, RoyItemsCommand.parseAmount("64"));

        for (int i = 0; i < 50; i++) {
            int val = RoyItemsCommand.parseAmount("1-3");
            assertTrue(val >= 1 && val <= 3);
        }

        for (int i = 0; i < 50; i++) {
            int val = RoyItemsCommand.parseAmount("10-20");
            assertTrue(val >= 10 && val <= 20);
        }
    }

    @Test
    @DisplayName("Text Formatting: Anti-Italic by Default and Explicit Italic Preservation")
    void testAntiItalicFormatting() {
        // Standard legacy text without italic code: MUST have ITALIC == FALSE
        Component comp1 = TextUtility.format("&6&lTest Sword");
        assertEquals(TextDecoration.State.FALSE, comp1.decoration(TextDecoration.ITALIC),
                "Default text must have ITALIC == FALSE to prevent Minecraft client italicization");

        // MiniMessage text without italic: MUST have ITALIC == FALSE
        Component comp2 = TextUtility.formatGui("<gold><bold>GUI Item</bold></gold>");
        assertEquals(TextDecoration.State.FALSE, comp2.decoration(TextDecoration.ITALIC));

        // Explicit italic with &o: MUST preserve italic
        Component comp3 = TextUtility.format("&6&oItalic Sword");
        assertNotEquals(TextDecoration.State.FALSE, comp3.decoration(TextDecoration.ITALIC),
                "Explicitly italicized text must not be forced to FALSE");

        // Explicit italic with <italic>
        Component comp4 = TextUtility.formatGui("<italic><gold>Italic GUI</gold></italic>");
        assertNotEquals(TextDecoration.State.FALSE, comp4.decoration(TextDecoration.ITALIC));

        // Hex formatting
        Component comp5 = TextUtility.format("&#FF5555Custom Hex");
        assertEquals(TextDecoration.State.FALSE, comp5.decoration(TextDecoration.ITALIC));

        // GuiHelper ensureAntiItalic
        Component comp6 = Component.text("Raw Component");
        assertEquals(TextDecoration.State.NOT_SET, comp6.decoration(TextDecoration.ITALIC));
        Component safeComp = GuiHelper.ensureAntiItalic(comp6);
        assertEquals(TextDecoration.State.FALSE, safeComp.decoration(TextDecoration.ITALIC));
    }

    @Test
    @DisplayName("Defense System: Mode 1 (SIMPLE) - Subtraction and Safe Non-Negative Floor")
    void testDefenseSimpleMode() {
        DefenseManager manager = new DefenseManager(null, null);
        manager.setMode(DefenseManager.DefenseMode.SIMPLE);
        manager.setSimpleFloor(0.0);

        // Standard: 20 Damage - 5 Defense = 15
        double res1 = manager.applyDefense(20.0, 5.0);
        assertEquals(15.0, res1, 0.001);

        // Defense exceeds damage: 10 Damage - 25 Defense = -15 -> Safe Floor 0.0 (No healing!)
        double res2 = manager.applyDefense(10.0, 25.0);
        assertEquals(0.0, res2, 0.001);

        // Zero damage
        double res3 = manager.applyDefense(0.0, 10.0);
        assertEquals(0.0, res3, 0.001);
    }

    @Test
    @DisplayName("Defense System: Mode 2 (MMORPG) - Non-Linear Diminishing Returns")
    void testDefenseMmorpgMode() {
        DefenseManager manager = new DefenseManager(null, null);
        manager.setMode(DefenseManager.DefenseMode.MMORPG);
        manager.setMmorpgScale(100.0);
        manager.setMmorpgDamageFactor(1.0);
        manager.setMmorpgMinDamageFloor(1.0);

        // 100 Defense -> 50% damage reduction -> 40 * (100 / 200) = 20.0
        double res1 = manager.applyDefense(40.0, 100.0);
        assertEquals(20.0, res1, 0.001);

        // 300 Defense -> 75% damage reduction -> 40 * (100 / 400) = 10.0
        double res2 = manager.applyDefense(40.0, 300.0);
        assertEquals(10.0, res2, 0.001);

        // Low damage against huge defense: still hits min floor (1.0)
        double res3 = manager.applyDefense(2.0, 5000.0);
        assertTrue(res3 >= 1.0, "Low damage attacks must still deal at least floor damage");
    }

    @Test
    @DisplayName("Defense System: Mode 3 (CUSTOM_LIMIT) - Min and Max Damage Clamping")
    void testDefenseCustomLimitMode() {
        DefenseManager manager = new DefenseManager(null, null);
        manager.setMode(DefenseManager.DefenseMode.CUSTOM_LIMIT);
        manager.setCustomLimitMinEnabled(true);
        manager.setCustomLimitMinValue(2.0);
        manager.setCustomLimitMaxEnabled(true);
        manager.setCustomLimitMaxValue(50.0);

        // Regular: 20 Damage - 5 Defense = 15.0
        double res1 = manager.applyDefense(20.0, 5.0);
        assertEquals(15.0, res1, 0.001);

        // Defense exceeds damage: 5 Damage - 10 Defense = -5 -> Clamped to min 2.0
        double res2 = manager.applyDefense(5.0, 10.0);
        assertEquals(2.0, res2, 0.001);

        // Massive damage: 100 Damage - 10 Defense = 90 -> Clamped to max 50.0
        double res3 = manager.applyDefense(100.0, 10.0);
        assertEquals(50.0, res3, 0.001);
    }

    @Test
    @DisplayName("bStats Metrics: Plugin ID Verification")
    void testBStatsPluginId() {
        assertEquals(34832, com.github.orenaidaucac.royItems.metrics.RoyMetricsManager.BSTATS_PLUGIN_ID);
    }
}
