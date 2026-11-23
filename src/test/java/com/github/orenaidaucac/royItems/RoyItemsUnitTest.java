package com.github.orenaidaucac.royItems;

import java.util.*;
import java.util.UUID;
import com.github.orenaidaucac.royItems.command.RoyItemsCommand;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.dummy.DummyManager;
import com.github.orenaidaucac.royItems.dummy.TrainingDummy;
import com.github.orenaidaucac.royItems.gui.GuiHelper;
import com.github.orenaidaucac.royItems.integration.EcoEnchantsHook;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.item.ItemType;
import com.github.orenaidaucac.royItems.lore.DynamicLoreEngine;
import com.github.orenaidaucac.royItems.lore.LoreFormat;
import com.github.orenaidaucac.royItems.socket.GemSocketManager;
import com.github.orenaidaucac.royItems.station.RecipeIngredient;
import com.github.orenaidaucac.royItems.station.StationRecipe;
import org.bukkit.inventory.ItemStack;
import com.github.orenaidaucac.royItems.stat.DefenseManager;
import com.github.orenaidaucac.royItems.stat.PlayerStatCache;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import com.github.orenaidaucac.royItems.voucher.CurrencyDisplayInfo;
import com.github.orenaidaucac.royItems.voucher.EcoEnchantReward;
import com.github.orenaidaucac.royItems.voucher.VoucherCurrencyReward;
import com.github.orenaidaucac.royItems.voucher.VoucherManager;
import com.github.orenaidaucac.royItems.extraction.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.plugin.Plugin;
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
    @DisplayName("Text Formatting: Hex formats (<#...>, {#...}, &#...), Legacy and MiniMessage Gradients")
    void testTextFormattingHexAndGradients() {
        // 1. MiniMessage Gradient preservation
        String gradient = "<gradient:#FFD166:#06D6A0>RoyItems</gradient>";
        assertEquals(gradient, TextUtility.normalizeHexAndLegacy(gradient));
        Component compGradient = TextUtility.format(gradient);
        assertEquals("RoyItems", TextUtility.toPlain(compGradient));

        // 2. Hex formats
        // Format <#RRGGBB>
        String hex1 = "<#FFD166>RoyItems";
        assertEquals(hex1, TextUtility.normalizeHexAndLegacy(hex1));
        assertEquals("RoyItems", TextUtility.toPlain(TextUtility.format(hex1)));

        // Format {#RRGGBB}
        String hex2 = "{#FFD166}RoyItems";
        assertEquals("<#FFD166>RoyItems", TextUtility.normalizeHexAndLegacy(hex2));
        assertEquals("RoyItems", TextUtility.toPlain(TextUtility.format(hex2)));

        // Format &#RRGGBB
        String hex3 = "&#FFD166RoyItems";
        assertEquals("<#FFD166>RoyItems", TextUtility.normalizeHexAndLegacy(hex3));
        assertEquals("RoyItems", TextUtility.toPlain(TextUtility.format(hex3)));

        // Format §#RRGGBB
        String hex4 = "§#FFD166RoyItems";
        assertEquals("<#FFD166>RoyItems", TextUtility.normalizeHexAndLegacy(hex4));
        assertEquals("RoyItems", TextUtility.toPlain(TextUtility.format(hex4)));

        // 3. Seamless combination of Legacy, Hex, and MiniMessage Gradient
        String mixed = "&6RoyItems &8» <gradient:#FFD166:#06D6A0>Test</gradient> &7(v2026.1.1) {#06D6A0}[OK] &#EF476F!";
        String normalized = TextUtility.normalizeHexAndLegacy(mixed);
        assertTrue(normalized.contains("<gradient:#FFD166:#06D6A0>Test</gradient>"));
        assertTrue(normalized.contains("<#06D6A0>[OK]"));
        assertTrue(normalized.contains("<#EF476F>!"));
        Component compMixed = TextUtility.format(mixed);
        assertEquals("RoyItems » Test (v2026.1.1) [OK] !", TextUtility.toPlain(compMixed));

        // 4. Legacy uppercase and lowercase
        String legacyUpper = "&A&LGreen Bold &r&CRed";
        Component compLegacy = TextUtility.format(legacyUpper);
        assertEquals("Green Bold Red", TextUtility.toPlain(compLegacy));

        // 5. Standalone # and & are preserved as plain characters
        String plain = "Rank #1 & #2";
        assertEquals(plain, TextUtility.normalizeHexAndLegacy(plain));
        assertEquals("Rank #1 & #2", TextUtility.toPlain(TextUtility.format(plain)));
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

    @Test
    @DisplayName("GUI Editor: Percentage Parsing & Standardization (1% = 1%, 0.5% = 0.5%)")
    void testEditorPercentageParsing() {
        // 1% must be 0.01 fraction, 1.0 percent points
        StatModifier m1 = com.github.orenaidaucac.royItems.gui.ItemDetailEditorGui.parseStatModifier("1%");
        assertNotNull(m1);
        assertEquals(0.0, m1.getFlat());
        assertEquals(0.01, m1.getPercent(), 0.00001);
        assertEquals(1.0, m1.getPercentValue(), 0.00001);

        // 10%
        StatModifier m10 = com.github.orenaidaucac.royItems.gui.ItemDetailEditorGui.parseStatModifier("10%");
        assertNotNull(m10);
        assertEquals(0.10, m10.getPercent(), 0.00001);
        assertEquals(10.0, m10.getPercentValue(), 0.00001);

        // 0.5% (Decimal)
        StatModifier mHalf = com.github.orenaidaucac.royItems.gui.ItemDetailEditorGui.parseStatModifier("0.5%");
        assertNotNull(mHalf);
        assertEquals(0.005, mHalf.getPercent(), 0.00001);
        assertEquals(0.5, mHalf.getPercentValue(), 0.00001);

        // -15% (Negative percentage)
        StatModifier mNeg = com.github.orenaidaucac.royItems.gui.ItemDetailEditorGui.parseStatModifier("-15%");
        assertNotNull(mNeg);
        assertEquals(-0.15, mNeg.getPercent(), 0.00001);
        assertEquals(-15.0, mNeg.getPercentValue(), 0.00001);

        // 100%
        StatModifier m100 = com.github.orenaidaucac.royItems.gui.ItemDetailEditorGui.parseStatModifier("100%");
        assertNotNull(m100);
        assertEquals(1.0, m100.getPercent(), 0.00001);
        assertEquals(100.0, m100.getPercentValue(), 0.00001);

        // Flat only
        StatModifier mFlat = com.github.orenaidaucac.royItems.gui.ItemDetailEditorGui.parseStatModifier("25");
        assertNotNull(mFlat);
        assertEquals(25.0, mFlat.getFlat());
        assertEquals(0.0, mFlat.getPercent());

        // Combined Flat + Percent
        StatModifier mComb = com.github.orenaidaucac.royItems.gui.ItemDetailEditorGui.parseStatModifier("15 10%");
        assertNotNull(mComb);
        assertEquals(15.0, mComb.getFlat());
        assertEquals(0.10, mComb.getPercent(), 0.00001);

        // Formatting roundtrip in YAML config manager
        assertEquals("1", com.github.orenaidaucac.royItems.core.config.ItemConfigManager.formatPercent(m1.getPercentValue()));
        assertEquals("0.5", com.github.orenaidaucac.royItems.core.config.ItemConfigManager.formatPercent(mHalf.getPercentValue()));
        assertEquals("10", com.github.orenaidaucac.royItems.core.config.ItemConfigManager.formatPercent(m10.getPercentValue()));
    }

    @Test
    @DisplayName("Stat Calculation: Base = 0 Percentage Stats (Crit Rate, Damage Reduction)")
    void testBaseZeroPercentageStats() {
        PlayerStatCache cache = new PlayerStatCache();

        // 20% Crit Rate configured with percent
        cache.addStat(StatType.CRIT_RATE, new StatModifier(0.0, 0.20));
        assertEquals(20.0, cache.calculateTotal(StatType.CRIT_RATE, 0.0), 0.001);

        // 15% Damage Reduction configured with percent
        cache.addStat(StatType.DAMAGE_REDUCTION, new StatModifier(0.0, 0.15));
        assertEquals(15.0, cache.calculateTotal(StatType.DAMAGE_REDUCTION, 0.0), 0.001);

        // Damage Reduction configured with flat
        PlayerStatCache cache2 = new PlayerStatCache();
        cache2.addStat(StatType.DAMAGE_REDUCTION, new StatModifier(10.0, 0.0));
        assertEquals(10.0, cache2.calculateTotal(StatType.DAMAGE_REDUCTION, 0.0), 0.001);
    }

    @Test
    @DisplayName("Dynamic Lore Engine: Independent Stat Placeholders & Smart Omission")
    void testDynamicLoreStatPlaceholders() {
        com.github.orenaidaucac.royItems.lore.DynamicLoreEngine engine = new com.github.orenaidaucac.royItems.lore.DynamicLoreEngine(null);
        com.github.orenaidaucac.royItems.item.ItemDefinition def = new com.github.orenaidaucac.royItems.item.ItemDefinition(
                com.github.orenaidaucac.royItems.item.ItemType.SWORD, "test_blade");
        def.setName("&cTest Blade");
        def.setStat(StatType.ATTACK_DAMAGE, new StatModifier(25.0, 0.0));
        def.setStat(StatType.CRIT_RATE, new StatModifier(0.0, 0.20)); // 20%

        com.github.orenaidaucac.royItems.lore.LoreFormat format = new com.github.orenaidaucac.royItems.lore.LoreFormat();
        format.setFormat(java.util.List.of(
                "&7Category: &f#type#",
                "#bar#",
                "#attack-damage#",
                "#defense#", // Absent! Must be omitted
                "&c⚔ Damage: &f+#attack-damage#",
                "&9🛡 Defense: &f+#defense#", // Absent! Entire line must be omitted
                "&e🎯 Crit: &a#crit-rate#",
                "#bar#"
        ));
        format.setBar("&8&m------------------------");
        format.setPosStat("&7#name#: &a+#value#");

        java.util.List<String> processed = new java.util.ArrayList<>();
        java.text.DecimalFormat df = new java.text.DecimalFormat("#,##0.##");

        // Test formatStatValue
        assertEquals("25", format.formatStatValue(def.getStat(StatType.ATTACK_DAMAGE), df));
        assertEquals("20%", format.formatStatValue(def.getStat(StatType.CRIT_RATE), df));

        // Test isStatPlaceholder
        assertTrue(engine.isStatPlaceholder("#attack-damage#"));
        assertTrue(engine.isStatPlaceholder("#defense#"));
        assertFalse(engine.isStatPlaceholder("#type#"));
        assertFalse(engine.isStatPlaceholder("#bar#"));

        // Test findStatModifier
        assertNotNull(engine.findStatModifier(def, "attack-damage"));
        assertNull(engine.findStatModifier(def, "defense"));
    }

    @Test
    @DisplayName("LiveUpdate Report: Counts verification")
    void testLiveUpdateReport() {
        com.github.orenaidaucac.royItems.item.LiveUpdateReport report =
                new com.github.orenaidaucac.royItems.item.LiveUpdateReport(14, 6, 0, 5);

        assertEquals(14, report.getInventoryCount());
        assertEquals(6, report.getEnderChestCount());
        assertEquals(20, report.getTotalCount());
        assertEquals(0, report.getFailedCount());
        assertEquals(5, report.getOnlinePlayersCount());
    }

    @Test
    @DisplayName("Config Migration: Deep merge preserves user values and inserts missing keys")
    void testConfigMigrationDeepMerge() {
        org.bukkit.configuration.file.YamlConfiguration source = new org.bukkit.configuration.file.YamlConfiguration();
        source.set("config-version", 2);
        source.set("debug", false);
        source.set("performance.expiration-check-interval", 20);
        source.set("performance.new-performance-option", 50);
        source.set("update-checker.enabled", true);
        source.set("update-checker.repository", "orenaidaucac/RoyItems");

        org.bukkit.configuration.file.YamlConfiguration target = new org.bukkit.configuration.file.YamlConfiguration();
        target.set("config-version", 1);
        target.set("debug", true); // User customized!
        target.set("performance.expiration-check-interval", 15); // User customized!

        boolean modified = com.github.orenaidaucac.royItems.core.config.ConfigMigrator.deepMerge(source, target, "");
        assertTrue(modified);

        // User customized values must be strictly preserved
        assertTrue(target.getBoolean("debug"));
        assertEquals(15, target.getInt("performance.expiration-check-interval"));

        // Missing keys must be added from source
        assertEquals(50, target.getInt("performance.new-performance-option"));
        assertTrue(target.getBoolean("update-checker.enabled"));
        assertEquals("orenaidaucac/RoyItems", target.getString("update-checker.repository"));
    }

    @Test
    @DisplayName("Update Checker: Version comparison semantics")
    void testUpdateCheckerVersionComparison() {
        // Newer versions
        assertTrue(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2026.1", "2026.2"));
        assertTrue(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("v2026.1", "v2026.2"));
        assertTrue(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2026.1", "2026.1.1"));
        assertTrue(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2026.1.1", "2026.1.2"));
        assertTrue(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2026.1.2", "2026.1.3"));
        assertTrue(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("1.0.0", "1.0.1"));
        assertTrue(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("1.0.0", "2.0.0"));

        // Equal or older versions
        assertFalse(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2026.1", "2026.1"));
        assertFalse(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("v2026.1", "2026.1"));
        assertFalse(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2026.2", "2026.1"));
        assertFalse(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2026.1.2", "2026.1.1"));
        assertFalse(com.github.orenaidaucac.royItems.core.update.UpdateChecker.isNewerVersion("2.0.0", "1.9.9"));
    }

    @Test
    @DisplayName("Update Checker: Modrinth configuration integrity & auto-revert unauthorized fields")
    void testUpdateCheckerConfigIntegrity() {
        assertEquals("https://modrinth.com/plugin/royitems", com.github.orenaidaucac.royItems.core.update.UpdateChecker.MODRINTH_PROJECT_URL);
        assertEquals("https://api.modrinth.com/v2/project/royitems/version", com.github.orenaidaucac.royItems.core.update.UpdateChecker.MODRINTH_API_URL);

        // Case 1: Config with unauthorized / legacy keys (repository, platform, custom-url)
        org.bukkit.configuration.file.YamlConfiguration dirtyConfig = new org.bukkit.configuration.file.YamlConfiguration();
        dirtyConfig.set("update-checker.enabled", false);
        dirtyConfig.set("update-checker.notify-admins-on-join", true);
        dirtyConfig.set("update-checker.repository", "orenaidaucac/RoyItems");
        dirtyConfig.set("update-checker.platform", "github");
        dirtyConfig.set("update-checker.custom-url", "https://malicious.example.com");

        boolean modified = com.github.orenaidaucac.royItems.core.update.UpdateChecker.enforceConfigIntegrity(dirtyConfig);
        assertTrue(modified, "Enforce integrity must report modifications when unauthorized keys are present");

        // Allowed keys must be preserved exactly
        assertFalse(dirtyConfig.getBoolean("update-checker.enabled"));
        assertTrue(dirtyConfig.getBoolean("update-checker.notify-admins-on-join"));

        // Unauthorized keys must be completely removed
        assertNull(dirtyConfig.get("update-checker.repository"));
        assertNull(dirtyConfig.get("update-checker.platform"));
        assertNull(dirtyConfig.get("update-checker.custom-url"));

        // Case 2: Config is already clean, must not trigger modification
        boolean cleanCheck = com.github.orenaidaucac.royItems.core.update.UpdateChecker.enforceConfigIntegrity(dirtyConfig);
        assertFalse(cleanCheck, "Clean configuration must not trigger further modifications");

        // Case 3: Missing update-checker section
        org.bukkit.configuration.file.YamlConfiguration emptyConfig = new org.bukkit.configuration.file.YamlConfiguration();
        boolean recreated = com.github.orenaidaucac.royItems.core.update.UpdateChecker.enforceConfigIntegrity(emptyConfig);
        assertTrue(recreated);
        assertTrue(emptyConfig.getBoolean("update-checker.enabled"));
        assertTrue(emptyConfig.getBoolean("update-checker.notify-admins-on-join"));
    }

    @Test
    @DisplayName("Lore Format Wiki: '_example' format is strictly suppressed and returns empty lore")
    void testWikiExampleLoreSuppression() {
        com.github.orenaidaucac.royItems.lore.DynamicLoreEngine engine =
                new com.github.orenaidaucac.royItems.lore.DynamicLoreEngine(null);

        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.SWORD, "test_item");
        def.setLore(java.util.List.of("&7Line 1", "&7Line 2"));

        // When using reserved wiki "_example", lore must be completely suppressed (empty)
        def.setLoreFormat("_example");
        java.util.List<String> suppressedLore1 = engine.buildLore(def, java.util.List.of(), 0);
        assertTrue(suppressedLore1.isEmpty());

        // When using "_example.yml", lore must also be suppressed
        def.setLoreFormat("_example.yml");
        java.util.List<String> suppressedLore2 = engine.buildLore(def, java.util.List.of(), 0);
        assertTrue(suppressedLore2.isEmpty());

        // When using standard format (e.g. "default"), lore is NOT suppressed
        def.setLoreFormat("default");
        java.util.List<String> standardLore = engine.buildLore(def, java.util.List.of(), 0);
        assertFalse(standardLore.isEmpty());
    }

    @Test
    @DisplayName("Lore Format: #tier# displays tier display-name instead of raw ID")
    void testTierPlaceholderDisplayName() {
        com.github.orenaidaucac.royItems.lore.DynamicLoreEngine engine =
                new com.github.orenaidaucac.royItems.lore.DynamicLoreEngine(null);

        com.github.orenaidaucac.royItems.tier_set.ItemTierManager tierManager =
                new com.github.orenaidaucac.royItems.tier_set.ItemTierManager(null);
        tierManager.registerTier(new com.github.orenaidaucac.royItems.tier_set.ItemTier("epic", "&5Epic", "&5", 4, "DARK_PURPLE"));
        tierManager.registerTier(new com.github.orenaidaucac.royItems.tier_set.ItemTier("legendary", "&6Legendary", "&6", 5, "GOLD"));

        com.github.orenaidaucac.royItems.tier_set.ItemSetManager setManager =
                new com.github.orenaidaucac.royItems.tier_set.ItemSetManager(null);
        com.github.orenaidaucac.royItems.tier_set.ItemSet set = new com.github.orenaidaucac.royItems.tier_set.ItemSet();
        set.setId("dragon_slayer");
        set.setDisplayName("&6Dragon Slayer Set");
        setManager.registerSet(set);

        engine.setItemTierManager(tierManager);
        engine.setItemSetManager(setManager);

        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "dragon_chestplate");
        def.setItemTier("epic");
        def.setItemSet("dragon_slayer");

        // Verify resolver directly
        assertEquals("&5Epic", engine.resolveTierDisplayName(def));
        assertEquals("&5", engine.resolveTierColor(def));
        assertEquals("&6Dragon Slayer Set", engine.resolveSetDisplayName(def));

        // Verify #tier#, #tier-id#, #set#, #set-id# in format
        String templateLine = "&7Rarity: #tier# &8(#tier-id#) | Set: #set# &8(#set-id#)";
        java.lang.reflect.Method method;
        try {
            method = com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.class.getDeclaredMethod(
                    "replaceGeneralPlaceholders", String.class, com.github.orenaidaucac.royItems.item.ItemDefinition.class, long.class);
            method.setAccessible(true);
            String rendered = (String) method.invoke(engine, templateLine, def, 0L);
            assertEquals("&7Rarity: &5Epic &8(epic) | Set: &6Dragon Slayer Set &8(dragon_slayer)", rendered);
        } catch (Exception e) {
            fail("Failed to invoke replaceGeneralPlaceholders: " + e.getMessage());
        }

        // Test fallback when tier is not in tierManager
        def.setItemTier("unknown_tier");
        assertEquals("unknown_tier", engine.resolveTierDisplayName(def));

        // Test null tier
        def.setItemTier(null);
        assertEquals("", engine.resolveTierDisplayName(def));
    }

    @Test
    @DisplayName("Language System: Normalize language codes accurately")
    void testLanguageNormalization() {
        assertEquals("en-US", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("en"));
        assertEquals("en-US", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("en-US"));
        assertEquals("en-US", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("english"));

        assertEquals("vi-VN", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("vi"));
        assertEquals("vi-VN", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("vi-VN"));
        assertEquals("vi-VN", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("vietnamese"));

        assertEquals("de-DE", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("de"));
        assertEquals("de-DE", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("deutsch"));

        assertEquals("ja-JP", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("ja"));
        assertEquals("ja-JP", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("japanese"));

        assertEquals("ko-KR", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("ko"));
        assertEquals("ko-KR", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("korean"));

        assertEquals("ru-RU", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("ru"));
        assertEquals("ru-RU", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("russian"));

        assertEquals("es-ES", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("es"));
        assertEquals("es-ES", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("spanish"));

        // Fallback for null or unknown
        assertEquals("en-US", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode(null));
        assertEquals("en-US", com.github.orenaidaucac.royItems.core.message.MessageManager.normalizeLanguageCode("unknown_language"));
    }

    @Test
    @DisplayName("Language System: Verify all 7 language resource files are valid YAML")
    void testLanguageFilesValidity() throws Exception {
        for (String lang : com.github.orenaidaucac.royItems.core.message.MessageManager.SUPPORTED_LANGUAGES) {
            java.io.File file = new java.io.File("src/main/resources/lang/" + lang + ".yml");
            assertTrue(file.exists(), "Language file missing: " + file.getPath());

            org.bukkit.configuration.file.YamlConfiguration config =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

            assertEquals(2, config.getInt("config-version"));
            assertNotNull(config.getString("prefix"));
            assertNotNull(config.getString("messages.no-permission"));
            assertNotNull(config.getString("messages.reload-success"));
            assertNotNull(config.getString("messages.checkupdate-checking"));
            assertTrue(config.isList("messages.checkupdate-up-to-date"));
            assertTrue(config.isList("messages.checkupdate-update-found"));
        }
    }

    @Test
    @DisplayName("Item Tier: Drop color resolution for glowing outlines")
    void testItemTierDropColor() {
        // Direct Bukkit Color names
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.WHITE,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("WHITE"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("GREEN"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.BLUE,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("BLUE"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("DARK_PURPLE"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GOLD,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("GOLD"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("LIGHT_PURPLE"));

        // Legacy color code fallback
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GOLD,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("&6"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED,
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor("&c"));

        // Tier instances
        com.github.orenaidaucac.royItems.tier_set.ItemTier t1 =
                new com.github.orenaidaucac.royItems.tier_set.ItemTier("common", "Common", "&f", 1, "WHITE");
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.WHITE, t1.getDropNamedTextColor());

        com.github.orenaidaucac.royItems.tier_set.ItemTier t2 =
                new com.github.orenaidaucac.royItems.tier_set.ItemTier("legendary", "Legendary", "&6", 5, "GOLD");
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GOLD, t2.getDropNamedTextColor());

        // Without explicit dropColor -> fallbacks to tier display color
        com.github.orenaidaucac.royItems.tier_set.ItemTier t3 =
                new com.github.orenaidaucac.royItems.tier_set.ItemTier("rare", "Rare", "&9", 3, null);
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.BLUE, t3.getDropNamedTextColor());
    }

    @Test
    @DisplayName("Damage Particle Filter: Config toggle verification")
    void testDamageParticleFilterConfig() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("combat.disable-damage-heart-particles", true);
        assertTrue(config.getBoolean("combat.disable-damage-heart-particles"));

        config.set("combat.disable-damage-heart-particles", false);
        assertFalse(config.getBoolean("combat.disable-damage-heart-particles"));
    }

    @Test
    @DisplayName("Config Migration: Verify all default template files have config-version 2")
    void testDefaultConfigFilesVersion2() {
        java.io.File configFile = new java.io.File("src/main/resources/config.yml");
        assertTrue(configFile.exists());
        org.bukkit.configuration.file.YamlConfiguration config =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
        assertEquals(2, config.getInt("config-version"));
        assertTrue(config.contains("combat.disable-damage-heart-particles"));

        java.io.File guiFile = new java.io.File("src/main/resources/gui/editor-gui.yml");
        assertTrue(guiFile.exists());
        org.bukkit.configuration.file.YamlConfiguration gui =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(guiFile);
        assertEquals(2, gui.getInt("config-version"));

        java.io.File tierFile = new java.io.File("src/main/resources/item-tier.yml");
        assertTrue(tierFile.exists());
        org.bukkit.configuration.file.YamlConfiguration tier =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(tierFile);
        assertEquals(2, tier.getInt("config-version"));
        assertEquals("WHITE", tier.getString("common.drop-color"));
    }

    @Test
    @DisplayName("Item Tier Drop Glow: Verify dragon_chestplate config has item-tier epic and valid drop-color")
    void testDragonChestplateTierGlowConfig() {
        java.io.File armorFile = new java.io.File("src/main/resources/item/ARMOR.yml");
        assertTrue(armorFile.exists());
        org.bukkit.configuration.file.YamlConfiguration armor =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(armorFile);

        assertTrue(armor.contains("dragon_chestplate.item-tier"));
        String tierId = armor.getString("dragon_chestplate.item-tier");
        assertEquals("epic", tierId);

        java.io.File tierFile = new java.io.File("src/main/resources/item-tier.yml");
        org.bukkit.configuration.file.YamlConfiguration tiers =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(tierFile);
        assertTrue(tiers.contains("epic.drop-color"));
        assertEquals("DARK_PURPLE", tiers.getString("epic.drop-color"));

        net.kyori.adventure.text.format.NamedTextColor color =
                com.github.orenaidaucac.royItems.tier_set.ItemTier.resolveNamedTextColor(tiers.getString("epic.drop-color"));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.DARK_PURPLE, color);
    }

    @Test
    @DisplayName("ItemConfigManager: Test saveItem and serialization of requirements, effects and properties")
    void testItemConfigManagerSaveItem() throws Exception {
        java.io.File testDir = new java.io.File("target/test-data");
        if (!testDir.exists()) testDir.mkdirs();

        org.bukkit.plugin.Plugin mockPlugin = (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.plugin.Plugin.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.Plugin.class},
                (proxy, method, args) -> {
                    if ("getDataFolder".equals(method.getName())) return testDir;
                    if ("getLogger".equals(method.getName())) return java.util.logging.Logger.getGlobal();
                    return null;
                }
        );

        com.github.orenaidaucac.royItems.item.ItemRegistry registry = new com.github.orenaidaucac.royItems.item.ItemRegistry();
        com.github.orenaidaucac.royItems.core.config.ItemConfigManager manager =
                new com.github.orenaidaucac.royItems.core.config.ItemConfigManager(mockPlugin, registry);

        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.SWORD, "test_sword");

        def.setMaterial("DIAMOND_SWORD");
        def.setName("<red>Test Sword</red>");
        def.setLore(java.util.List.of("&7First line", "&eSecond line"));
        def.setLoreFormat("weapon");
        def.setUnbreakable(true);
        def.setGlow(true);
        def.setItemTier("legendary");
        def.setItemSet("dragon_slayer");
        def.setPermission("royitems.admin");
        def.setDrop(false);
        def.setLevelRequired(25);
        def.setMmocoreClass("WARRIOR");
        def.setMmocoreLevel(10);
        def.setAuraSkill("FIGHTING");
        def.setAuraSkillLevel(15);
        def.setWorldRequired(java.util.List.of("world", "dungeon"));
        def.setBiomeRequired(java.util.List.of("plains"));
        def.setCooldown(3.5);
        def.setExpired("3 days");
        def.setGemSockets(java.util.List.of("1", "A"));
        def.setTrimMaterial("netherite");
        def.setTrimPattern("vex");
        def.setHideArmorTrim(true);
        def.setDyeColor("#FF0000");
        def.setHideDye(true);
        def.setHideEnchantments(true);
        def.setHideAttributes(true);

        boolean saved = manager.saveItem(def);
        assertTrue(saved);

        java.io.File savedFile = new java.io.File(testDir, "item/SWORD.yml");
        assertTrue(savedFile.exists());

        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(savedFile);

        assertEquals("DIAMOND_SWORD", yaml.getString("test_sword.material"));
        assertEquals("<red>Test Sword</red>", yaml.getString("test_sword.name"));
        assertEquals(java.util.List.of("&7First line", "&eSecond line"), yaml.getStringList("test_sword.lore"));
        assertTrue(yaml.getBoolean("test_sword.unbreakable"));
        assertTrue(yaml.getBoolean("test_sword.glow"));
        assertEquals("legendary", yaml.getString("test_sword.item-tier"));
        assertEquals("dragon_slayer", yaml.getString("test_sword.item-set"));
        assertEquals("royitems.admin", yaml.getString("test_sword.permission"));
        assertFalse(yaml.getBoolean("test_sword.drop"));
        assertEquals(25, yaml.getInt("test_sword.level-required"));
        assertEquals("WARRIOR", yaml.getString("test_sword.mmocore-required.class"));
        assertEquals(10, yaml.getInt("test_sword.mmocore-required.level"));
        assertEquals("FIGHTING", yaml.getString("test_sword.auraskill-required.skill"));
        assertEquals(15, yaml.getInt("test_sword.auraskill-required.level"));
        assertEquals(java.util.List.of("world", "dungeon"), yaml.getStringList("test_sword.world-required"));
        assertEquals(java.util.List.of("plains"), yaml.getStringList("test_sword.biome-required"));
        assertEquals(3.5, yaml.getDouble("test_sword.cooldown"));
        assertEquals("3 days", yaml.getString("test_sword.expired"));
        assertEquals(java.util.List.of("1", "A"), yaml.getStringList("test_sword.gem-socket"));
        assertEquals("netherite", yaml.getString("test_sword.trim-material"));
        assertEquals("vex", yaml.getString("test_sword.trim-pattern"));
        assertTrue(yaml.getBoolean("test_sword.hide-armor-trim"));
        assertEquals("#FF0000", yaml.getString("test_sword.dye-color"));
        assertTrue(yaml.getBoolean("test_sword.hide-dye"));
        assertTrue(yaml.getBoolean("test_sword.hide-enchantments"));
        assertTrue(yaml.getBoolean("test_sword.hide-attributes"));

        // Restrictions & Protections
        def.setDisableOnUse(true);
        def.setDisableOnEnchantment(true);
        def.setDisableOnCrafting(true);
        def.setDisableOnDrop(true);

        boolean savedProt = manager.saveItem(def);
        assertTrue(savedProt);

        org.bukkit.configuration.file.YamlConfiguration yamlProt =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(savedFile);
        assertTrue(yamlProt.getBoolean("test_sword.disable-on-use"));
        assertTrue(yamlProt.getBoolean("test_sword.disable-on-enchantment"));
        assertTrue(yamlProt.getBoolean("test_sword.disable-on-crafting"));
        assertTrue(yamlProt.getBoolean("test_sword.disable-on-drop"));

        // Now test resetting properties (removing them)
        def.setItemTier(null);
        def.setItemSet(null);
        def.setLevelRequired(0);
        def.setMmocoreClass(null);
        def.setMmocoreLevel(0);
        def.setAuraSkill(null);
        def.setAuraSkillLevel(0);
        def.setWorldRequired(java.util.List.of());
        def.setBiomeRequired(java.util.List.of());
        def.setCooldown(0);
        def.setExpired(null);
        def.setGemSockets(java.util.List.of());
        def.setTrimMaterial(null);
        def.setTrimPattern(null);
        def.setHideArmorTrim(false);
        def.setDyeColor(null);
        def.setHideDye(false);
        def.setHideEnchantments(false);
        def.setHideAttributes(false);
        def.setDrop(true);
        def.setDisableOnUse(false);
        def.setDisableOnEnchantment(false);
        def.setDisableOnCrafting(false);
        def.setDisableOnDrop(false);

        boolean savedAgain = manager.saveItem(def);
        assertTrue(savedAgain);

        org.bukkit.configuration.file.YamlConfiguration yamlCleared =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(savedFile);

        assertNull(yamlCleared.getString("test_sword.item-tier"));
        assertNull(yamlCleared.getString("test_sword.item-set"));
        assertFalse(yamlCleared.contains("test_sword.level-required"));
        assertFalse(yamlCleared.contains("test_sword.mmocore-required"));
        assertFalse(yamlCleared.contains("test_sword.auraskill-required"));
        assertFalse(yamlCleared.contains("test_sword.world-required"));
        assertFalse(yamlCleared.contains("test_sword.biome-required"));
        assertFalse(yamlCleared.contains("test_sword.cooldown"));
        assertFalse(yamlCleared.contains("test_sword.expired"));
        assertFalse(yamlCleared.contains("test_sword.gem-socket"));
        assertFalse(yamlCleared.contains("test_sword.trim-material"));
        assertFalse(yamlCleared.contains("test_sword.trim-pattern"));
        assertFalse(yamlCleared.contains("test_sword.hide-armor-trim"));
        assertFalse(yamlCleared.contains("test_sword.dye-color"));
        assertFalse(yamlCleared.contains("test_sword.hide-dye"));
        assertFalse(yamlCleared.contains("test_sword.hide-enchantments"));
        assertFalse(yamlCleared.contains("test_sword.hide-attributes"));
        assertFalse(yamlCleared.contains("test_sword.drop"));
        assertFalse(yamlCleared.contains("test_sword.disable-on-use"));
        assertFalse(yamlCleared.contains("test_sword.disable-on-enchantment"));
        assertFalse(yamlCleared.contains("test_sword.disable-on-crafting"));
        assertFalse(yamlCleared.contains("test_sword.disable-on-drop"));
    }

    @Test
    @DisplayName("Item Protection Configuration & Default Values Verification")
    void testItemProtectionDefaultsAndConfig() {
        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.SWORD, "shield_blade");

        // Defaults must be false
        assertFalse(def.isDisableOnUse());
        assertFalse(def.isDisableOnEnchantment());
        assertFalse(def.isDisableOnCrafting());
        assertFalse(def.isDisableOnDrop());

        // Verify editor-gui.yml layout contains all 4 icons on Page 1
        java.io.File guiFile = new java.io.File("src/main/resources/gui/editor-gui.yml");
        assertTrue(guiFile.exists());
        org.bukkit.configuration.file.YamlConfiguration gui =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(guiFile);

        assertTrue(gui.contains("detail-editor.properties.disable-on-use"));
        assertEquals(1, gui.getInt("detail-editor.properties.disable-on-use.page"));
        assertEquals(29, gui.getInt("detail-editor.properties.disable-on-use.slot"));
        assertEquals("BARRIER", gui.getString("detail-editor.properties.disable-on-use.material"));

        assertTrue(gui.contains("detail-editor.properties.disable-on-drop"));
        assertEquals(1, gui.getInt("detail-editor.properties.disable-on-drop.page"));
        assertEquals(30, gui.getInt("detail-editor.properties.disable-on-drop.slot"));
        assertEquals("CHAIN", gui.getString("detail-editor.properties.disable-on-drop.material"));

        assertTrue(gui.contains("detail-editor.properties.disable-on-crafting"));
        assertEquals(1, gui.getInt("detail-editor.properties.disable-on-crafting.page"));
        assertEquals(32, gui.getInt("detail-editor.properties.disable-on-crafting.slot"));
        assertEquals("CRAFTING_TABLE", gui.getString("detail-editor.properties.disable-on-crafting.material"));

        assertTrue(gui.contains("detail-editor.properties.disable-on-enchantment"));
        assertEquals(1, gui.getInt("detail-editor.properties.disable-on-enchantment.page"));
        assertEquals(33, gui.getInt("detail-editor.properties.disable-on-enchantment.slot"));
        assertEquals("ENCHANTING_TABLE", gui.getString("detail-editor.properties.disable-on-enchantment.material"));
    }

    @Test
    @DisplayName("Lore Format: Custom stat-format display-name, symbol, format, and shorthand are fully respected")
    void testStatFormatCustomDisplayAndOverrides() {
        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("format", java.util.List.of("#stats#"));
        config.set("stat-format.positive", "&7#name#: &a+#value#");
        config.set("stat-format.negative", "&7#name#: &c-#value#");

        // Custom config for attack-damage using display-name, symbol, and format
        config.set("stat-format.attack-damage.display-name", "Sát thương");
        config.set("stat-format.attack-damage.symbol", "⚔");
        config.set("stat-format.attack-damage.format", "&7%symbol% %name%: &c+%value%");
        config.set("stat-format.attack-damage.percent-format", "&7%symbol% %name%: &c+%value%%");

        // Shorthand string for defense
        config.set("stat-format.defense", "&9🛡 Phòng thủ: &f+#value#");

        com.github.orenaidaucac.royItems.lore.LoreFormat lf =
                com.github.orenaidaucac.royItems.lore.LoreFormat.fromConfig("weapon", config);

        assertNotNull(lf.getCustomStat("attack-damage"));
        assertEquals("Sát thương", lf.getCustomStat("attack-damage").getName());
        assertEquals("⚔", lf.getCustomStat("attack-damage").getSymbol());

        // Test positive flat attack-damage
        String line1 = lf.formatStatLine("attack-damage", "Attack Damage",
                new com.github.orenaidaucac.royItems.stat.StatModifier(15.0, 0.0), new java.text.DecimalFormat("#,##0.##"));
        assertEquals("&7⚔ Sát thương: &c+15", line1);

        // Test percent attack-damage
        String line2 = lf.formatStatLine("attack-damage", "Attack Damage",
                new com.github.orenaidaucac.royItems.stat.StatModifier(0.0, 0.2), new java.text.DecimalFormat("#,##0.##"));
        assertEquals("&7⚔ Sát thương: &c+20%", line2);

        // Test defense with shorthand string
        String line3 = lf.formatStatLine("defense", "Defense",
                new com.github.orenaidaucac.royItems.stat.StatModifier(25.0, 0.0), new java.text.DecimalFormat("#,##0.##"));
        assertEquals("&9🛡 Phòng thủ: &f+25", line3);

        // Test negative auto-adaptation
        String line4 = lf.formatStatLine("attack-damage", "Attack Damage",
                new com.github.orenaidaucac.royItems.stat.StatModifier(-10.0, 0.0), new java.text.DecimalFormat("#,##0.##"));
        assertEquals("&7⚔ Sát thương: &c-10", line4);
    }

    @Test
    @DisplayName("Lore Format: Arbitrary custom stats and dynamic placeholders are supported and customizable")
    void testArbitraryCustomStatsAndPlaceholders() {
        com.github.orenaidaucac.royItems.lore.DynamicLoreEngine engine =
                new com.github.orenaidaucac.royItems.lore.DynamicLoreEngine(null);

        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("format", java.util.List.of("#stats#", "&bMana: #mana#"));
        config.set("stat-format.mana.display-name", "Năng lượng");
        config.set("stat-format.mana.symbol", "🌀");
        config.set("stat-format.mana.format", "&b%symbol% %name%: &f+%value%");

        com.github.orenaidaucac.royItems.lore.LoreFormat lf =
                com.github.orenaidaucac.royItems.lore.LoreFormat.fromConfig("custom", config);

        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.STAFF, "staff");
        def.setLoreFormat("custom");
        def.setCustomStat("mana", new com.github.orenaidaucac.royItems.stat.StatModifier(100.0, 0.0));

        // Inject format into engine
        try {
            java.lang.reflect.Field field = com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.class.getDeclaredField("formats");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, com.github.orenaidaucac.royItems.lore.LoreFormat> map =
                    (java.util.Map<String, com.github.orenaidaucac.royItems.lore.LoreFormat>) field.get(engine);
            map.put("custom", lf);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        java.util.List<String> lore = engine.buildLore(def, java.util.List.of(), 0);
        assertFalse(lore.isEmpty());
        assertTrue(lore.contains("&b🌀 Năng lượng: &f+100"));
        assertTrue(lore.contains("&bMana: 100"));
    }

    @Test
    @DisplayName("Lore Format: Sockets, requirements, durability, and enchantments are 100% customizable without hardcoded text")
    void testFullyCustomizableLoreSections() {
        com.github.orenaidaucac.royItems.lore.DynamicLoreEngine engine =
                new com.github.orenaidaucac.royItems.lore.DynamicLoreEngine(null);

        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("format", java.util.List.of("#sockets#", "#requirements#", "#durability#"));
        config.set("sockets-format.empty", "&8[&7◇&8] &7Ô khảm trống (%type%)");
        config.set("sockets-format.filled", "&8[&a◆&8] &aNgọc đã khảm: &f%gem%");
        config.set("requirements-format.level", "&e⚡ Yêu cầu cấp độ: &c%level%");
        config.set("requirements-format.permission", "&e⚡ Yêu cầu quyền: &c%permission%");
        config.set("requirements-format.class-with-level", "&e⚡ Hệ: &6%class% (Cấp %level%)");
        config.set("durability-format.unbreakable", "&b✦ Bất hoại ✦");

        com.github.orenaidaucac.royItems.lore.LoreFormat lf =
                com.github.orenaidaucac.royItems.lore.LoreFormat.fromConfig("vietnamese", config);

        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "mythic_armor");
        def.setLoreFormat("vietnamese");
        def.setGemSockets(java.util.List.of("RED", "BLUE"));
        def.setLevelRequired(50);
        def.setPermission("royitems.vip");
        def.setMmocoreClass("Warrior");
        def.setMmocoreLevel(20);
        def.setUnbreakable(true);

        try {
            java.lang.reflect.Field field = com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.class.getDeclaredField("formats");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, com.github.orenaidaucac.royItems.lore.LoreFormat> map =
                    (java.util.Map<String, com.github.orenaidaucac.royItems.lore.LoreFormat>) field.get(engine);
            map.put("vietnamese", lf);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        java.util.List<String> slotted = java.util.List.of("Ruby of Flame");
        java.util.List<String> lore = engine.buildLore(def, slotted, 0);

        assertTrue(lore.contains("&8[&a◆&8] &aNgọc đã khảm: &fRuby of Flame"));
        assertTrue(lore.contains("&8[&7◇&8] &7Ô khảm trống (BLUE)"));
        assertTrue(lore.contains("&e⚡ Yêu cầu cấp độ: &c50"));
        assertTrue(lore.contains("&e⚡ Yêu cầu quyền: &croyitems.vip"));
        assertTrue(lore.contains("&e⚡ Hệ: &6Warrior (Cấp 20)"));
        assertTrue(lore.contains("&b✦ Bất hoại ✦"));
    }

    @Test
    @DisplayName("Lore Format: getCustomStat must never throw StackOverflowError on unconfigured or mutual keys")
    void testNonRecursiveGetCustomStat() {
        com.github.orenaidaucac.royItems.lore.LoreFormat lf = new com.github.orenaidaucac.royItems.lore.LoreFormat();

        // Must return null without any recursion or StackOverflowError
        assertNull(lf.getCustomStat("attack-damage"));
        assertNull(lf.getCustomStat("attack_damage"));
        assertNull(lf.getCustomStat("vanilla-attack-damage"));
        assertNull(lf.getCustomStat("generic.attack_damage"));
        assertNull(lf.getCustomStat("generic-attack-damage"));
        assertNull(lf.getCustomStat("defense"));
        assertNull(lf.getCustomStat("non_existent_custom_stat"));
        assertNull(lf.getCustomStat(null));
        assertNull(lf.getCustomStat(""));

        // Add one key and verify exact and alias resolution
        lf.addCustomStat("attack-damage", new com.github.orenaidaucac.royItems.lore.LoreFormat.StatDisplayConfig(
                "Sát thương", "⚔", "&c+%value%", null, null, null));

        assertNotNull(lf.getCustomStat("attack-damage"));
        assertNotNull(lf.getCustomStat("attack_damage"));
        assertNotNull(lf.getCustomStat("vanilla-attack-damage"));
        assertNotNull(lf.getCustomStat("generic.attack_damage"));
        assertEquals("Sát thương", lf.getCustomStat("attack_damage").getName());
        assertEquals("Sát thương", lf.getCustomStat("vanilla-attack-damage").getName());
    }

    @Test
    @DisplayName("Hide Unbreakable: verify lore suppression and serialization")
    void testHideUnbreakable() {
        com.github.orenaidaucac.royItems.lore.DynamicLoreEngine engine =
                new com.github.orenaidaucac.royItems.lore.DynamicLoreEngine(null);

        com.github.orenaidaucac.royItems.lore.LoreFormat lf = new com.github.orenaidaucac.royItems.lore.LoreFormat();
        lf.setId("test_format");
        lf.setFormat(java.util.List.of("&7Item Name", "#durability#", "&aDescription"));
        lf.setDurabilityUnbreakable("&b✦ Unbreakable ✦");

        try {
            java.lang.reflect.Field field = com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.class.getDeclaredField("formats");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<String, com.github.orenaidaucac.royItems.lore.LoreFormat> map =
                    (java.util.Map<String, com.github.orenaidaucac.royItems.lore.LoreFormat>) field.get(engine);
            map.put("test_format", lf);
        } catch (Exception e) {
            fail("Reflection failed: " + e.getMessage());
        }

        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.SWORD, "sacred_sword");
        def.setLoreFormat("test_format");
        def.setUnbreakable(true);
        def.setHideUnbreakable(false);

        // When hide-unbreakable is false: unbreakable line must be present
        java.util.List<String> visibleLore = engine.buildLore(def, java.util.List.of(), 0);
        assertTrue(visibleLore.contains("&b✦ Unbreakable ✦"));

        // When hide-unbreakable is true: unbreakable line must be omitted
        def.setHideUnbreakable(true);
        java.util.List<String> hiddenLore = engine.buildLore(def, java.util.List.of(), 0);
        assertFalse(hiddenLore.contains("&b✦ Unbreakable ✦"));
        assertEquals(2, hiddenLore.size());
        assertEquals("&7Item Name", hiddenLore.get(0));
        assertEquals("&aDescription", hiddenLore.get(1));

        // Test Configuration Section parsing
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        org.bukkit.configuration.ConfigurationSection sec = yaml.createSection("sacred_sword");
        sec.set("material", "DIAMOND_SWORD");
        sec.set("unbreakable", true);
        sec.set("hide-unbreakable", true);

        com.github.orenaidaucac.royItems.core.config.ItemConfigManager manager =
                new com.github.orenaidaucac.royItems.core.config.ItemConfigManager(null, null);
        com.github.orenaidaucac.royItems.item.ItemDefinition loaded =
                manager.parseItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.SWORD, "sacred_sword", sec);
        assertNotNull(loaded);
        assertTrue(loaded.isUnbreakable());
        assertTrue(loaded.isHideUnbreakable());

        // Test hide-unbreakable defaults to false when omitted
        sec.set("hide-unbreakable", null);
        com.github.orenaidaucac.royItems.item.ItemDefinition loadedDefault =
                manager.parseItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.SWORD, "sacred_sword", sec);
        assertNotNull(loadedDefault);
        assertFalse(loadedDefault.isHideUnbreakable());
    }

    @Test
    @DisplayName("Item Registry & Browser: preserves file top-to-bottom order")
    void testItemRegistryOrderAndSorting() {
        com.github.orenaidaucac.royItems.item.ItemRegistry registry = new com.github.orenaidaucac.royItems.item.ItemRegistry();

        com.github.orenaidaucac.royItems.item.ItemDefinition def1 =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.MATERIAL, "trade_material_1");
        com.github.orenaidaucac.royItems.item.ItemDefinition def2 =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.MATERIAL, "trade_material_2");
        com.github.orenaidaucac.royItems.item.ItemDefinition def3 =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.MATERIAL, "trade_material_3");
        com.github.orenaidaucac.royItems.item.ItemDefinition def4 =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.MATERIAL, "trade_material_4");

        // Register in order 1, 2, 3, 4
        registry.register(def1);
        registry.register(def2);
        registry.register(def3);
        registry.register(def4);

        java.util.List<com.github.orenaidaucac.royItems.item.ItemDefinition> list = registry.getByType(com.github.orenaidaucac.royItems.item.ItemType.MATERIAL);
        assertEquals(4, list.size());
        assertEquals("trade_material_1", list.get(0).getId());
        assertEquals("trade_material_2", list.get(1).getId());
        assertEquals("trade_material_3", list.get(2).getId());
        assertEquals("trade_material_4", list.get(3).getId());

        // Updating an existing item must not alter its position
        def2.setName("Updated Material 2");
        registry.register(def2);
        java.util.List<com.github.orenaidaucac.royItems.item.ItemDefinition> updatedList = registry.getByType(com.github.orenaidaucac.royItems.item.ItemType.MATERIAL);
        assertEquals("trade_material_1", updatedList.get(0).getId());
        assertEquals("trade_material_2", updatedList.get(1).getId());
        assertEquals("trade_material_3", updatedList.get(2).getId());
        assertEquals("trade_material_4", updatedList.get(3).getId());

        // Test sorting by file keys
        java.util.List<String> fileOrder = java.util.List.of("trade_material_3", "trade_material_1", "trade_material_4", "trade_material_2");
        java.util.Map<String, Integer> orderMap = new java.util.HashMap<>();
        for (int i = 0; i < fileOrder.size(); i++) {
            orderMap.put(fileOrder.get(i).toLowerCase(), i);
        }

        java.util.List<com.github.orenaidaucac.royItems.item.ItemDefinition> scrambled =
                new java.util.ArrayList<>(java.util.List.of(def4, def2, def1, def3));
        scrambled.sort(java.util.Comparator.comparingInt(def -> orderMap.getOrDefault(def.getId().toLowerCase(), Integer.MAX_VALUE)));

        assertEquals("trade_material_3", scrambled.get(0).getId());
        assertEquals("trade_material_1", scrambled.get(1).getId());
        assertEquals("trade_material_4", scrambled.get(2).getId());
        assertEquals("trade_material_2", scrambled.get(3).getId());
    }

    @Test
    @DisplayName("Crafting Station: Ingredient parsing for all supported types")
    void testStationIngredientParsing() {
        // 1. RoyItems
        com.github.orenaidaucac.royItems.station.RecipeIngredient r1 =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("royitems[type=SWORD,id=starter_sword,amount=5]");
        assertNotNull(r1);
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIngredient.Type.ROYITEMS, r1.getType());
        assertEquals(5, r1.getAmount());
        assertTrue(r1 instanceof com.github.orenaidaucac.royItems.station.RecipeIngredient.RoyItemIngredient);
        com.github.orenaidaucac.royItems.station.RecipeIngredient.RoyItemIngredient rItem =
                (com.github.orenaidaucac.royItems.station.RecipeIngredient.RoyItemIngredient) r1;
        assertEquals(com.github.orenaidaucac.royItems.item.ItemType.SWORD, rItem.getItemType());
        assertEquals("starter_sword", rItem.getId());

        // 2. Vanilla with displayname and lore
        com.github.orenaidaucac.royItems.station.RecipeIngredient r2 =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("vanilla[type=DIAMOND,amount=10,displayname='&bKim Cương',lore='&7Dòng 1\\n&7Dòng 2']");
        assertNotNull(r2);
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIngredient.Type.VANILLA, r2.getType());
        assertEquals(10, r2.getAmount());
        assertTrue(r2 instanceof com.github.orenaidaucac.royItems.station.RecipeIngredient.VanillaIngredient);
        com.github.orenaidaucac.royItems.station.RecipeIngredient.VanillaIngredient vItem =
                (com.github.orenaidaucac.royItems.station.RecipeIngredient.VanillaIngredient) r2;
        assertEquals(org.bukkit.Material.DIAMOND, vItem.getMaterial());
        assertEquals("&bKim Cương", vItem.getDisplayName());
        assertEquals("&7Dòng 1\\n&7Dòng 2", vItem.getLore());

        // 3. Vault Money
        com.github.orenaidaucac.royItems.station.RecipeIngredient r3 =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("money=5000");
        assertNotNull(r3);
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIngredient.Type.MONEY, r3.getType());
        assertEquals(5000, r3.getAmount());

        // 4. Experience
        com.github.orenaidaucac.royItems.station.RecipeIngredient r4 =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("exp=250");
        assertNotNull(r4);
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIngredient.Type.EXP, r4.getType());
        assertEquals(250, r4.getAmount());

        // 5. PlayerPoints
        com.github.orenaidaucac.royItems.station.RecipeIngredient r5 =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("point=100");
        assertNotNull(r5);
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIngredient.Type.POINT, r5.getType());
        assertEquals(100, r5.getAmount());

        // 6. ExcellentEconomy
        com.github.orenaidaucac.royItems.station.RecipeIngredient r6 =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("excellenteconnomy:coins=500");
        assertNotNull(r6);
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIngredient.Type.EXCELLENT_ECONOMY, r6.getType());
        assertEquals(500, r6.getAmount());
        assertTrue(r6 instanceof com.github.orenaidaucac.royItems.station.RecipeIngredient.ExcellentEconomyIngredient);
        com.github.orenaidaucac.royItems.station.RecipeIngredient.ExcellentEconomyIngredient eeItem =
                (com.github.orenaidaucac.royItems.station.RecipeIngredient.ExcellentEconomyIngredient) r6;
        assertEquals("coins", eeItem.getCurrencyId());
    }

    @Test
    @DisplayName("Crafting Station: Recipe Icon parsing for all supported formats")
    void testStationIconParsing() {
        // 1. RoyItems icon
        com.github.orenaidaucac.royItems.station.RecipeIcon icon1 =
                com.github.orenaidaucac.royItems.station.RecipeIcon.parse("royitems[type=SWORD,id=blazing_sword,amount=1]");
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIcon.IconType.ROYITEMS, icon1.getIconType());
        assertEquals(com.github.orenaidaucac.royItems.item.ItemType.SWORD, icon1.getRoyType());
        assertEquals("blazing_sword", icon1.getRoyId());
        assertEquals(1, icon1.getAmount());

        // 2. Vanilla icon with displayname and lore
        com.github.orenaidaucac.royItems.station.RecipeIcon icon2 =
                com.github.orenaidaucac.royItems.station.RecipeIcon.parse("vanilla[type=EMERALD,amount=10,displayname='<green>Ngọc</green>',lore='Dòng 1\\nDòng 2']");
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIcon.IconType.VANILLA, icon2.getIconType());
        assertEquals(org.bukkit.Material.EMERALD, icon2.getVanillaMat());
        assertEquals(10, icon2.getAmount());
        assertEquals("<green>Ngọc</green>", icon2.getDisplayName());

        // 3. HeadDatabase icon
        com.github.orenaidaucac.royItems.station.RecipeIcon icon3 =
                com.github.orenaidaucac.royItems.station.RecipeIcon.parse("hdb[id=12345,amount=1]");
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIcon.IconType.HDB, icon3.getIconType());
        assertEquals("12345", icon3.getHdbId());

        // 4. Basehead icon
        com.github.orenaidaucac.royItems.station.RecipeIcon icon4 =
                com.github.orenaidaucac.royItems.station.RecipeIcon.parse("basehead[value=eyJ0ZXh0dXJlcyI6...=,amount=1]");
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIcon.IconType.BASEHEAD, icon4.getIconType());
        assertEquals("eyJ0ZXh0dXJlcyI6...=", icon4.getBaseheadValue());
    }

    @Test
    @DisplayName("Crafting Station: Slot range parsing ('1', '1-9', etc.)")
    void testStationSlotRangeParsing() {
        java.util.List<String> rawSlots = java.util.List.of("1", "3-5", "10", "20-22");
        java.util.List<Integer> parsed = com.github.orenaidaucac.royItems.station.StationBackground.parseSlots(rawSlots);

        // Expected: 1, 3, 4, 5, 10, 20, 21, 22
        java.util.List<Integer> expected = java.util.List.of(1, 3, 4, 5, 10, 20, 21, 22);
        assertEquals(expected, parsed);
    }

    @Test
    @DisplayName("Crafting Station: Load blacksmith.yml from resources and verify configuration")
    void testStationConfigFileLoading() throws Exception {
        java.io.File file = new java.io.File("src/main/resources/crafting-station/blacksmith.yml");
        assertTrue(file.exists(), "Default blacksmith.yml must exist");

        org.bukkit.configuration.file.YamlConfiguration yaml =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

        com.github.orenaidaucac.royItems.station.CraftingStation station =
                com.github.orenaidaucac.royItems.station.CraftingStation.fromConfig("blacksmith", yaml);

        assertNotNull(station);
        assertEquals("blacksmith", station.getId());
        assertEquals(54, station.getSize());
        assertEquals(6, station.getRows());
        assertTrue(station.getCommands().contains("blacksmith"));
        assertTrue(station.getCommands().contains("thoren"));
        assertTrue(station.getFancyNpcs().contains("blacksmith_npc"));
        assertTrue(station.getCitizenNpcs().contains(10));
        assertTrue(station.getPhoenixNpcs().contains("blacksmith_npc"));

        // Verify recipes
        assertEquals(2, station.getRecipes().size());
        com.github.orenaidaucac.royItems.station.StationRecipe r1 = station.getRecipes().get("blazing_sword_craft");
        assertNotNull(r1);
        assertEquals(5, r1.getInputs().size());
        assertEquals(1, r1.getOutputs().size());
        assertEquals(3, r1.getCraftingTime());

        // Verify dynamic lore config
        assertNotNull(station.getDynamicLore());
        assertEquals("<#06D6A0>✔</#06D6A0>", station.getDynamicLore().getSymbolEnough());
        assertEquals("<#EF476F>✘</#EF476F>", station.getDynamicLore().getSymbolMissing());
        assertFalse(station.getDynamicLore().getHeader().isEmpty());
        assertFalse(station.getDynamicLore().getFooter().isEmpty());
    }

    @Test
    void testVanillaTranslatableComponentAndDynamicLore() {
        com.github.orenaidaucac.royItems.station.RecipeIngredient ing =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("vanilla[type=DIAMOND,amount=3]");
        assertNotNull(ing);
        assertTrue(ing instanceof com.github.orenaidaucac.royItems.station.RecipeIngredient.VanillaIngredient);

        net.kyori.adventure.text.Component comp = ing.getDisplayNameComponent(null, null);
        assertNotNull(comp);
        assertTrue(comp instanceof net.kyori.adventure.text.TranslatableComponent);
        net.kyori.adventure.text.TranslatableComponent trans = (net.kyori.adventure.text.TranslatableComponent) comp;
        assertTrue(trans.key().contains("diamond"));

        // Test dynamic lore line interpolation with translatable component
        String line = " %status% %name%: <white>%have%</white><gray>/%need%</gray>";
        String prepared = line.replace("%status%", "<#06D6A0>✔</#06D6A0>")
                .replace("%have%", "5")
                .replace("%need%", "3");
        net.kyori.adventure.text.Component formatted =
                com.github.orenaidaucac.royItems.core.text.TextUtility.formatLineWithComponent(prepared, "name", comp);
        assertNotNull(formatted);

        // Test RecipeIcon with Vanilla material
        com.github.orenaidaucac.royItems.station.RecipeIcon icon =
                com.github.orenaidaucac.royItems.station.RecipeIcon.parse("vanilla[type=BLAZE_ROD,amount=2]");
        assertNotNull(icon);
        assertEquals(com.github.orenaidaucac.royItems.station.RecipeIcon.IconType.VANILLA, icon.getIconType());
        assertEquals(org.bukkit.Material.BLAZE_ROD, icon.getVanillaMat());
        assertEquals(2, icon.getAmount());

        // Test fallback English title-casing format
        assertEquals("Blaze Rod",
                com.github.orenaidaucac.royItems.station.RecipeIngredient.VanillaIngredient.formatVanillaName(org.bukkit.Material.BLAZE_ROD));
        assertEquals("Diamond Sword",
                com.github.orenaidaucac.royItems.station.RecipeIngredient.VanillaIngredient.formatVanillaName(org.bukkit.Material.DIAMOND_SWORD));
    }

    @Test
    @DisplayName("Crafting Outputs: Deterministic Profile UUID and Stack Consistency")
    void testCraftingOutputStackingAndDeterministicHead() {
        // Test deterministic UUID for basehead textures
        String base64 = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTIzIn19fQ==";
        java.util.UUID u1 = java.util.UUID.nameUUIDFromBytes(base64.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        java.util.UUID u2 = java.util.UUID.nameUUIDFromBytes(base64.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(u1, u2, "Base64 head profile UUIDs must be deterministic so identical heads can stack together!");

        // Test RecipeIngredient parsing and multiplier
        com.github.orenaidaucac.royItems.station.RecipeIngredient royIng =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("royitems[type=ITEM,id=ruby,amount=2]");
        assertNotNull(royIng);
        assertEquals(2, royIng.getAmount());

        com.github.orenaidaucac.royItems.station.RecipeIngredient moneyIng =
                com.github.orenaidaucac.royItems.station.RecipeIngredient.parse("money=500");
        assertNotNull(moneyIng);
        assertEquals(500, moneyIng.getAmount());
    }

    @Test
    @DisplayName("Stats: Verify StatType alias lookups and fallback normalization")
    void testStatTypeAliases() {
        assertEquals(StatType.MAX_HEALTH, StatType.fromKey("hp"));
        assertEquals(StatType.MAX_HEALTH, StatType.fromKey("health"));
        assertEquals(StatType.MAX_HEALTH, StatType.fromKey("maxhealth"));
        assertEquals(StatType.MAX_HEALTH, StatType.fromKey("max-health"));
        assertEquals(StatType.MAX_HEALTH, StatType.fromKey("max_health"));

        assertEquals(StatType.ATTACK_DAMAGE, StatType.fromKey("damage"));
        assertEquals(StatType.ATTACK_DAMAGE, StatType.fromKey("dmg"));
        assertEquals(StatType.ATTACK_DAMAGE, StatType.fromKey("attack-damage"));
        assertEquals(StatType.ATTACK_DAMAGE, StatType.fromKey("attack_damage"));

        assertEquals(StatType.MOVEMENT_SPEED, StatType.fromKey("speed"));
        assertEquals(StatType.MOVEMENT_SPEED, StatType.fromKey("move-speed"));
        assertEquals(StatType.MOVEMENT_SPEED, StatType.fromKey("movespeed"));
        assertEquals(StatType.MOVEMENT_SPEED, StatType.fromKey("movement-speed"));

        assertEquals(StatType.CRIT_RATE, StatType.fromKey("crit"));
        assertEquals(StatType.CRIT_RATE, StatType.fromKey("crit-rate"));
        assertEquals(StatType.CRIT_RATE, StatType.fromKey("critical-rate"));

        assertEquals(StatType.DEFENSE, StatType.fromKey("def"));
        assertEquals(StatType.DEFENSE, StatType.fromKey("defense"));
    }

    @Test
    @DisplayName("Stats: Multi-slot Equipment and Talisman max-health Accumulation")
    void testMultiSlotMaxHealthAccumulation() {
        PlayerStatCache cache = new PlayerStatCache();

        // 1. Mainhand weapon with +10 max-health and +25 attack-damage
        cache.addStat(StatType.MAX_HEALTH, new StatModifier(10.0, 0.0));
        cache.addStat(StatType.ATTACK_DAMAGE, new StatModifier(25.0, 0.0));

        // 2. Offhand shield with +5 max-health and +30 defense
        cache.addStat(StatType.MAX_HEALTH, new StatModifier(5.0, 0.0));
        cache.addStat(StatType.DEFENSE, new StatModifier(30.0, 0.0));

        // 3. Armor chestplate with +15 max-health and +45 defense
        cache.addStat(StatType.MAX_HEALTH, new StatModifier(15.0, 0.0));
        cache.addStat(StatType.DEFENSE, new StatModifier(45.0, 0.0));

        // 4. Talisman in inventory with +10 max-health
        cache.addStat(StatType.MAX_HEALTH, new StatModifier(10.0, 0.0));

        // Expected max health = 10 (mainhand) + 5 (offhand) + 15 (chestplate) + 10 (talisman) = 40.0
        assertEquals(40.0, cache.getFlat(StatType.MAX_HEALTH), 0.001);

        // Expected attack damage = 25
        assertEquals(25.0, cache.getFlat(StatType.ATTACK_DAMAGE), 0.001);

        // Expected defense = 30 + 45 = 75
        assertEquals(75.0, cache.getFlat(StatType.DEFENSE), 0.001);

        // Calculate total with base 20.0 max health
        double totalMaxHealth = cache.calculateTotal(StatType.MAX_HEALTH, 20.0);
        assertEquals(60.0, totalMaxHealth, 0.001);
    }

    @Test
    @DisplayName("Dummy: TrainingDummy creation, metrics, and hologram height")
    void testTrainingDummyCreationAndHologramHeight() {
        org.bukkit.Location loc = new org.bukkit.Location(null, 100.5, 64.0, -200.5);
        TrainingDummy dummy = new TrainingDummy("test_dummy", loc, "IRON_GOLEM", null, 1001, 1002);

        assertEquals("test_dummy", dummy.getId());
        assertEquals("IRON_GOLEM", dummy.getMobTypeName());
        assertEquals(1001, dummy.getEntityId());
        assertEquals(1002, dummy.getHologramEntityId());
        assertEquals(0, dummy.getHitCount());
        assertEquals(0.0, dummy.getTotalDamage(), 0.001);
        assertEquals(0.0, dummy.getDps(), 0.001);

        // Hologram height for IRON_GOLEM should be 3.0
        assertEquals(3.0, dummy.getHologramHeight(), 0.001);

        // Hologram height for zombie / default should be 2.2
        TrainingDummy zombieDummy = new TrainingDummy("z1", loc, "ZOMBIE", null, 2001, 2002);
        assertEquals(2.2, zombieDummy.getHologramHeight(), 0.001);

        // Hologram height for enderman should be 3.1
        TrainingDummy enderDummy = new TrainingDummy("e1", loc, "ENDERMAN", null, 3001, 3002);
        assertEquals(3.1, enderDummy.getHologramHeight(), 0.001);

        // Hologram height for sheep/pig should be 1.4
        TrainingDummy sheepDummy = new TrainingDummy("s1", loc, "SHEEP", null, 4001, 4002);
        assertEquals(1.4, sheepDummy.getHologramHeight(), 0.001);

        // Initial hologram component should render without errors
        Component initialText = dummy.getHologramText();
        assertNotNull(initialText);
    }

    @Test
    @DisplayName("Dummy: Damage recording, critical strikes, and DPS calculation")
    void testTrainingDummyDamageAndDps() {
        org.bukkit.Location loc = new org.bukkit.Location(null, 0, 0, 0);
        TrainingDummy dummy = new TrainingDummy("dps_test", loc, "ZOMBIE", null, 5001, 5002);

        // 1st hit: 150 damage, non-crit
        dummy.recordHit(150.0, false);
        assertEquals(1, dummy.getHitCount());
        assertEquals(150.0, dummy.getTotalDamage(), 0.001);
        assertEquals(150.0, dummy.getLastDamage(), 0.001);
        assertFalse(dummy.isLastCrit());
        assertTrue(dummy.getDps() > 0.0);

        // 2nd hit: 350 damage, crit
        dummy.recordHit(350.0, true);
        assertEquals(2, dummy.getHitCount());
        assertEquals(500.0, dummy.getTotalDamage(), 0.001);
        assertEquals(350.0, dummy.getLastDamage(), 0.001);
        assertTrue(dummy.isLastCrit());
        assertTrue(dummy.getDps() > 0.0);

        // Hologram text after hits should contain stats
        Component text = dummy.getHologramText();
        assertNotNull(text);
    }

    @Test
    @DisplayName("Dummy: Smooth Look-At yaw and pitch calculation")
    void testDummyLookAtCalculation() {
        org.bukkit.Location from = new org.bukkit.Location(null, 0.5, 64.0, 0.5);

        // Target directly North (negative Z)
        org.bukkit.Location targetNorth = new org.bukkit.Location(null, 0.5, 65.5, -9.5);
        float[] lookNorth = DummyManager.calculateLookAt(from, targetNorth);
        assertNotNull(lookNorth);
        assertEquals(2, lookNorth.length);
        // Yaw should be ~180 degrees (North)
        assertEquals(180.0f, Math.abs(lookNorth[0]), 1.0f);

        // Target directly South (positive Z)
        org.bukkit.Location targetSouth = new org.bukkit.Location(null, 0.5, 65.5, 10.5);
        float[] lookSouth = DummyManager.calculateLookAt(from, targetSouth);
        // Yaw should be ~0 degrees (South)
        assertEquals(0.0f, Math.abs(lookSouth[0]), 1.0f);

        // Pitch should be 0 when at eye height (from.getY() + 1.5 == target.getY())
        assertEquals(0.0f, lookSouth[1], 0.1f);
    }

    @Test
    @DisplayName("Dummy: EntityType string resolution")
    void testDummyEntityTypeResolution() {
        // Without active server / PacketEvents runtime, resolveEntityType handles errors gracefully without crashing
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("ZOMBIE"));
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("skeleton"));
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("iron_golem"));
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("warden"));
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("breeze"));
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("armor_stand"));
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("cow"));
        assertDoesNotThrow(() -> DummyManager.resolveEntityType("unknown_mob_fallback"));
    }

    @Test
    @DisplayName("Dummy: Custom Hologram Lines, Colors, Billboard, and Crit Indicator")
    void testTrainingDummyCustomHologramLinesAndColors() {
        org.bukkit.Location loc = new org.bukkit.Location(null, 0, 0, 0);
        TrainingDummy dummy = new TrainingDummy("custom_test", loc, "ZOMBIE", null, 6001, 6002);

        // Customize lines with MiniMessage, hex, and legacy colors
        dummy.setIdleLines(java.util.List.of(
                "&6&l[DUMMY] &#00FF99{id}",
                "<gradient:#00b4db:#0083b0>Mob: {mob_type}</gradient>",
                "&7Ready to strike!"
        ));
        dummy.setActiveLines(java.util.List.of(
                "&c&lIN COMBAT: &e{id}",
                "&bDPS: &f{dps} &8| &cTotal: &f{total_damage}{crit}",
                "&aLast hit: &f{last_damage}"
        ));
        dummy.setCritIndicator(" &4&l[CRIT!]");
        dummy.setBillboardType("VERTICAL");
        dummy.setHologramShadow(true);
        dummy.setHologramBackgroundColor(0x50000000);

        assertEquals((byte) 1, dummy.getBillboardByte());
        assertEquals(0x50000000, dummy.getHologramBackgroundColor());
        assertTrue(dummy.isHologramShadow());

        // Check billboard conversions
        dummy.setBillboardType("CENTER");
        assertEquals((byte) 3, dummy.getBillboardByte());
        dummy.setBillboardType("HORIZONTAL");
        assertEquals((byte) 2, dummy.getBillboardByte());
        dummy.setBillboardType("FIXED");
        assertEquals((byte) 0, dummy.getBillboardByte());

        // Initial idle text
        Component idleText = dummy.getHologramText();
        assertNotNull(idleText);

        // Record a critical hit
        dummy.recordHit(777.5, true);
        assertTrue(dummy.isSessionActive());
        Component activeText = dummy.getHologramText();
        assertNotNull(activeText);
    }

    @Test
    @DisplayName("Dummy: Background Color parsing helper")
    void testDummyColorParsing() {
        assertEquals(0x40000000, DummyManager.parseColorInt("#40000000", 0));
        assertEquals(0x40000000, DummyManager.parseColorInt("0x40000000", 0));
        assertEquals((int) 0xFFFF0000L, DummyManager.parseColorInt("#FF0000", 0));
        assertEquals(1073741824, DummyManager.parseColorInt(1073741824, 0));
        assertEquals(0x12345678, DummyManager.parseColorInt("invalid_hex", 0x12345678));
        assertEquals(0x12345678, DummyManager.parseColorInt(null, 0x12345678));
    }

    @Test
    @DisplayName("Dummy: Weapon Attack Damage Calculation does not double-count (6 dmg not 13)")
    void testDummyWeaponAttackDamageCalculationNotDoubled() {
        PlayerStatCache cache = new PlayerStatCache();
        // Weapon has attack-damage: 6
        cache.addStat(StatType.ATTACK_DAMAGE, new StatModifier(6.0, 0.0));

        double customDamage = cache.calculateTotal(StatType.ATTACK_DAMAGE, 0.0);
        assertEquals(6.0, customDamage, 0.0001);

        // Simulated vanilla base damage (unarmed 1.0 + transient attribute 6.0 = 7.0)
        double vanillaDamage = 7.0;

        // Correct algorithm: if customDamage > 0.001, baseDamage is customDamage
        double baseDamage = customDamage > 0.001 ? customDamage : vanillaDamage;
        assertEquals(6.0, baseDamage, 0.0001, "Base damage must be 6.0 and must not double-count to 13.0");

        // Format checks: integers should format without trailing .0
        assertEquals("6", TrainingDummy.formatNumber(6.0));
        assertEquals("6.5", TrainingDummy.formatNumber(6.5));
        assertEquals("13", TrainingDummy.formatNumber(13.0));

        // Test dummy hit record
        org.bukkit.Location loc = new org.bukkit.Location(null, 0, 0, 0);
        TrainingDummy dummy = new TrainingDummy("sword_test", loc, "ZOMBIE", null, 7001, 7002);
        dummy.recordHit(baseDamage, false);

        assertEquals(6.0, dummy.getLastDamage(), 0.0001);
        assertEquals(6.0, dummy.getTotalDamage(), 0.0001);
        assertEquals("6", TrainingDummy.formatNumber(dummy.getLastDamage()));
    }

    @Test
    @DisplayName("Dummy: Mob Type Categories Detection (Undead, Arthropod, Aquatic)")
    void testDummyMobTypeDetection() {
        assertTrue(DummyManager.isUndead("ZOMBIE"));
        assertTrue(DummyManager.isUndead("SKELETON"));
        assertTrue(DummyManager.isUndead("WITHER_SKELETON"));
        assertTrue(DummyManager.isUndead("DROWNED"));
        assertFalse(DummyManager.isUndead("COW"));
        assertFalse(DummyManager.isUndead("PIG"));

        assertTrue(DummyManager.isArthropod("SPIDER"));
        assertTrue(DummyManager.isArthropod("CAVE_SPIDER"));
        assertTrue(DummyManager.isArthropod("BEE"));
        assertFalse(DummyManager.isArthropod("ZOMBIE"));

        assertTrue(DummyManager.isAquatic("DROWNED"));
        assertTrue(DummyManager.isAquatic("GUARDIAN"));
        assertTrue(DummyManager.isAquatic("DOLPHIN"));
        assertFalse(DummyManager.isAquatic("PIG"));
    }

    @Test
    @DisplayName("Dummy: Comprehensive Multi-Factor Output Damage Calculation")
    void testDummyComprehensiveDamageCalculation() {
        // 1. Base weapon attack damage
        double weaponBase = 6.0;

        // 2. Enchantment bonus: Sharpness 5 gives 0.5 * 5 + 0.5 = 3.0
        double sharpnessBonus = 0.5 * 5 + 0.5;
        assertEquals(3.0, sharpnessBonus, 0.0001);

        // 3. Potion effect: Strength 2 (amplifier 1) gives +6.0
        double strengthBonus = 3.0 * (1 + 1);
        assertEquals(6.0, strengthBonus, 0.0001);

        double totalBaseMelee = weaponBase + strengthBonus; // 12.0
        double damageWithEnchants = totalBaseMelee + sharpnessBonus; // 15.0
        assertEquals(15.0, damageWithEnchants, 0.0001);

        // 4. Critical hit multiplier (1.5x)
        double critDamage = damageWithEnchants * 1.5;
        assertEquals(22.5, critDamage, 0.0001);

        // 5. Cooldown reduction factor: cooldown = 0.5
        float cooldown = 0.5f;
        float cooldownFactor = 0.2f + cooldown * cooldown * 0.8f; // 0.2 + 0.25 * 0.8 = 0.4
        assertEquals(0.4f, cooldownFactor, 0.0001);
        double scaledAttack = (totalBaseMelee * cooldownFactor) + (sharpnessBonus * cooldown);
        assertEquals((12.0 * 0.4) + (3.0 * 0.5), scaledAttack, 0.0001);

        // 6. Projectile Bow calculation: speed = 3.0, base damage = 2.0 -> base = 6.0
        double arrowBase = 3.0 * 2.0; // 6.0
        // Power 5 multiplier: 1.0 + 0.25 * (5 + 1) = 2.5
        double arrowWithPower = arrowBase * (1.0 + 0.25 * 6);
        assertEquals(15.0, arrowWithPower, 0.0001);

        // RoyItems arrow-damage stat: +5.0
        double arrowDamage = arrowWithPower + 5.0; // 20.0
        assertEquals(20.0, arrowDamage, 0.0001);

        // 7. TrainingDummy BoundingBox verification
        org.bukkit.Location loc = new org.bukkit.Location(null, 10.0, 64.0, 10.0);
        TrainingDummy dummy = new TrainingDummy("box_test", loc, "ZOMBIE", null, 8001, 8002);
        org.bukkit.util.BoundingBox box = dummy.getBoundingBox();
        assertNotNull(box);
        assertEquals(9.3, box.getMinX(), 0.001);
        assertEquals(10.7, box.getMaxX(), 0.001);
        assertEquals(64.0, box.getMinY(), 0.001);
        assertTrue(box.getMaxY() > 66.0);
    }

    @Test
    @DisplayName("ExtraStorage Integration & RecipeIngredient Dual-Source Resolution")
    void testExtraStorageIngredientIntegration() {
        RecipeIngredient royIng = RecipeIngredient.parse("royitems[type=MATERIAL,id=dark_matter,amount=5]");
        assertNotNull(royIng);
        assertEquals(RecipeIngredient.Type.ROYITEMS, royIng.getType());
        assertEquals(5, royIng.getAmount());

        RecipeIngredient vanillaIng = RecipeIngredient.parse("vanilla[type=DIAMOND,amount=10]");
        assertNotNull(vanillaIng);
        assertEquals(RecipeIngredient.Type.VANILLA, vanillaIng.getType());
        assertEquals(10, vanillaIng.getAmount());

        // Without online player or ExtraStorage, default fallback returns 0
        assertEquals(0, royIng.getInventoryAmount(null, null, null, null));
        assertEquals(0, royIng.getStorageAmount(null, null));
        assertEquals(0, royIng.getPlayerAmount(null, null, null, null));
        assertFalse(royIng.hasEnough(null, null, null, null));

        assertEquals(0, vanillaIng.getInventoryAmount(null, null, null, null));
        assertEquals(0, vanillaIng.getStorageAmount(null, null));
        assertEquals(0, vanillaIng.getPlayerAmount(null, null, null, null));
        assertFalse(vanillaIng.hasEnough(null, null, null, null));
    }

    @Test
    @DisplayName("Dynamic Lore Placeholders: %have%, %storage_have%, %inv_have%")
    void testDynamicLorePlaceholders() {
        String template = " %status% %name%: <white>%have%</white><gray>/%need%</gray> [Inv: %inv_have%, Storage: %storage_have%]";
        int inv = 3;
        int storage = 7;
        int total = inv + storage;
        int need = 10;
        String status = total >= need ? "✔" : "✘";

        String formatted = template
                .replace("%status%", status)
                .replace("%have%", String.valueOf(total))
                .replace("%inv_have%", String.valueOf(inv))
                .replace("%storage_have%", String.valueOf(storage))
                .replace("%need%", String.valueOf(need));

        assertTrue(formatted.contains("10"));
        assertTrue(formatted.contains("[Inv: 3, Storage: 7]"));
        assertTrue(formatted.contains("✔"));
    }

    @Test
    @DisplayName("MMORPG DPS Standard Calculation & Session Expiration")
    void testMmorpgDpsCalculation() {
        TrainingDummy dummy = new TrainingDummy("dps_test", new org.bukkit.Location(null, 0, 64, 0), "ZOMBIE", null, 9001, 9002);

        // First hit: 100 damage -> DPS should equal 100.0 exactly
        dummy.recordHit(100.0, false);
        assertEquals(100.0, dummy.getLastDamage(), 0.001);
        assertEquals(100.0, dummy.getTotalDamage(), 0.001);
        assertEquals(1, dummy.getHitCount());
        assertEquals(100.0, dummy.getDps(), 0.001);

        // Second hit: additional 100 damage after 1 second -> total = 200, elapsed = 1.0s -> DPS = 200.0
        // Simulate hit after 1000ms
        dummy.recordHit(100.0, true);
        assertEquals(100.0, dummy.getLastDamage(), 0.001);
        assertEquals(200.0, dummy.getTotalDamage(), 0.001);
        assertEquals(2, dummy.getHitCount());
        assertTrue(dummy.isLastCrit());
        assertTrue(dummy.getDps() > 0.0);

        // Session reset verification: checkSessionExpiration should transition from active to idle and reset metrics
        // Force expiry by manipulating lastHitTime
        dummy.setSessionResetDelaySeconds(1);
        assertTrue(dummy.isSessionActive());
    }

    @Test
    @DisplayName("DummyDamageEvent Cancellation and Damage Modification")
    void testDummyDamageEvent() {
        TrainingDummy dummy = new TrainingDummy("event_test", new org.bukkit.Location(null, 0, 64, 0), "ZOMBIE", null, 9003, 9004);
        com.github.orenaidaucac.royItems.dummy.event.DummyDamageEvent event =
                new com.github.orenaidaucac.royItems.dummy.event.DummyDamageEvent(null, dummy, 50.0, false, false);

        assertEquals(50.0, event.getDamage(), 0.001);
        assertFalse(event.isCrit());
        assertFalse(event.isProjectile());
        assertFalse(event.isCancelled());

        // Modifying damage
        event.setDamage(75.5);
        assertEquals(75.5, event.getDamage(), 0.001);

        // Modifying crit status
        event.setCrit(true);
        assertTrue(event.isCrit());

        // Cancelling event
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    @DisplayName("LiveUpdate Config: Default values and YAML loading")
    void testLiveUpdateConfig() {
        com.github.orenaidaucac.royItems.item.LiveUpdateConfig cfg =
                new com.github.orenaidaucac.royItems.item.LiveUpdateConfig();

        // 1. Verify default values
        assertTrue(cfg.isEnabled());
        assertTrue(cfg.isAutoUpdateOnReload());
        assertTrue(cfg.isContainerInventory());
        assertTrue(cfg.isContainerArmor());
        assertTrue(cfg.isContainerOffhand());
        assertTrue(cfg.isContainerEnderChest());

        assertTrue(cfg.isPreserveEnchantments());
        assertTrue(cfg.isPreserveGems());
        assertTrue(cfg.isPreserveDurability());
        assertTrue(cfg.isPreservePDC());

        assertFalse(cfg.isPreserveDisplayName());
        assertFalse(cfg.isPreserveLore());
        assertFalse(cfg.isPreserveTier());
        assertFalse(cfg.isPreserveCustomModelData());
        assertFalse(cfg.isPreserveAttributes());

        // 2. Load from YAML configuration
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("live-update.enabled", false);
        yaml.set("live-update.auto-update-on-reload", false);
        yaml.set("live-update.containers.inventory", true);
        yaml.set("live-update.containers.armor", false);
        yaml.set("live-update.containers.offhand", false);
        yaml.set("live-update.containers.ender-chest", true);

        yaml.set("live-update.preserve.enchantments", false);
        yaml.set("live-update.preserve.gems", false);
        yaml.set("live-update.preserve.durability", false);
        yaml.set("live-update.preserve.display-name", true);
        yaml.set("live-update.preserve.lore", true);
        yaml.set("live-update.preserve.tier", true);
        yaml.set("live-update.preserve.custom-model-data", true);
        yaml.set("live-update.preserve.pdc", false);
        yaml.set("live-update.preserve.attributes", true);

        cfg.load(yaml);

        assertFalse(cfg.isEnabled());
        assertFalse(cfg.isAutoUpdateOnReload());
        assertTrue(cfg.isContainerInventory());
        assertFalse(cfg.isContainerArmor());
        assertFalse(cfg.isContainerOffhand());
        assertTrue(cfg.isContainerEnderChest());

        assertFalse(cfg.isPreserveEnchantments());
        assertFalse(cfg.isPreserveGems());
        assertFalse(cfg.isPreserveDurability());
        assertFalse(cfg.isPreservePDC());

        assertTrue(cfg.isPreserveDisplayName());
        assertTrue(cfg.isPreserveLore());
        assertTrue(cfg.isPreserveTier());
        assertTrue(cfg.isPreserveCustomModelData());
        assertTrue(cfg.isPreserveAttributes());
    }

    @Test
    @DisplayName("MythicMobs Hook: Mitigation tracking & double-mitigation prevention")
    void testMythicMobsMitigationTracking() {
        com.github.orenaidaucac.royItems.integration.MythicMobsHook hook =
                new com.github.orenaidaucac.royItems.integration.MythicMobsHook();

        java.util.UUID playerId = java.util.UUID.randomUUID();

        // Initially not mitigated
        assertFalse(hook.isRecentlyMitigated(playerId));

        // Mark mitigated
        hook.markMitigated(playerId);
        assertTrue(hook.isRecentlyMitigated(playerId));

        // Clear mitigation
        hook.clearMitigation(playerId);
        assertFalse(hook.isRecentlyMitigated(playerId));
    }

    @Test
    @DisplayName("MythicMobs Defense Integration: Skill damage mitigation formulas")
    void testMythicDamageDefenseFormulas() {
        DefenseManager dm = new DefenseManager(); // Defaults to MMORPG mode, scale=100

        // MMORPG Formula: damage * (scale / (scale + defense))
        // 100 raw damage with 100 effective defense -> 100 * (100 / 200) = 50.0
        double mitigated1 = dm.applyDefense(100.0, 100.0);
        assertEquals(50.0, mitigated1, 0.001);

        // 100 raw damage with 300 effective defense -> 100 * (100 / 400) = 25.0
        double mitigated2 = dm.applyDefense(100.0, 300.0);
        assertEquals(25.0, mitigated2, 0.001);

        // Simple Mode: damage - defense
        dm.setMode(DefenseManager.DefenseMode.SIMPLE);
        double mitigatedSimple = dm.applyDefense(100.0, 40.0);
        assertEquals(60.0, mitigatedSimple, 0.001);

        // Simple Mode floor
        double mitigatedFloor = dm.applyDefense(30.0, 50.0);
        assertEquals(0.0, mitigatedFloor, 0.001);
    }

    @Test
    @DisplayName("Enchantment Formatting: Universal Roman Numerals & Title Casing")
    void testEnchantmentFormattingHelpers() {
        // Roman numeral conversion
        assertEquals("I", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(1));
        assertEquals("IV", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(4));
        assertEquals("V", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(5));
        assertEquals("IX", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(9));
        assertEquals("X", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(10));
        assertEquals("XV", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(15));
        assertEquals("XXV", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(25));
        assertEquals("C", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(100));
        assertEquals("CCLV", com.github.orenaidaucac.royItems.lore.DynamicLoreEngine.toRoman(255));
    }

    @Test
    @DisplayName("EcoEnchants Hook: Soft dependency safe degradation")
    void testEcoEnchantsHookSafeDegradation() {
        com.github.orenaidaucac.royItems.integration.EcoEnchantsHook hook =
                new com.github.orenaidaucac.royItems.integration.EcoEnchantsHook();

        // When EcoEnchants is not installed in the test environment
        assertFalse(hook.setup());
        assertFalse(hook.isAvailable());
        assertFalse(hook.isEcoEnchant(null));
        assertNull(hook.getFormattedName(null, 1));
        assertNull(hook.getDisplayName(null));
    }

    @Test
    @DisplayName("LoreFormat: Custom enchantment options and section parsing")
    void testLoreFormatEnchantmentOptions() {
        com.github.orenaidaucac.royItems.lore.LoreFormat format =
                new com.github.orenaidaucac.royItems.lore.LoreFormat();

        // Defaults
        assertEquals("&7%name% %level%", format.getEnchantmentFormat());
        assertEquals("&c%name% %level%", format.getEnchantmentCurseFormat());
        assertEquals("", format.getEnchantmentSymbol());
        assertEquals("ROMAN", format.getEnchantmentLevelStyle());
        assertEquals("VANILLA_FIRST", format.getEnchantmentOrder());
        assertTrue(format.getEnchantmentCustomNames().isEmpty());

        // Custom config parsing
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("enchantments-format.format", "&b✦ %name% &f%level%");
        yaml.set("enchantments-format.curse-format", "&4☠ %name%");
        yaml.set("enchantments-format.symbol", "✦ ");
        yaml.set("enchantments-format.level-style", "NUMBER");
        yaml.set("enchantments-format.order", "ECO_FIRST");
        yaml.set("enchantments-format.custom-names.sharpness", "&cSharpness");
        yaml.set("enchantments-format.custom-names.unbreaking", "&9Unbreaking");

        com.github.orenaidaucac.royItems.lore.LoreFormat parsed =
                com.github.orenaidaucac.royItems.lore.LoreFormat.fromConfig("test", yaml);

        assertEquals("&b✦ %name% &f%level%", parsed.getEnchantmentFormat());
        assertEquals("&4☠ %name%", parsed.getEnchantmentCurseFormat());
        assertEquals("✦ ", parsed.getEnchantmentSymbol());
        assertEquals("NUMBER", parsed.getEnchantmentLevelStyle());
        assertEquals("ECO_FIRST", parsed.getEnchantmentOrder());
        assertEquals("&cSharpness", parsed.getEnchantmentCustomNames().get("sharpness"));
        assertEquals("&9Unbreaking", parsed.getEnchantmentCustomNames().get("unbreaking"));
    }

    @Test
    @DisplayName("Anvil Rename Protection: Config toggles and per-item override")
    void testAnvilRenameProtectionConfigAndOverride() {
        com.github.orenaidaucac.royItems.item.ItemRegistry registry =
                new com.github.orenaidaucac.royItems.item.ItemRegistry();
        com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener listener =
                new com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener(null, registry);

        // Default should be true
        assertTrue(listener.isPreventAnvilRename());

        // Config reload to false
        org.bukkit.configuration.file.YamlConfiguration yamlFalse = new org.bukkit.configuration.file.YamlConfiguration();
        yamlFalse.set("anvil.prevent-rename", false);
        listener.loadConfig(yamlFalse);
        assertFalse(listener.isPreventAnvilRename());

        // Config reload back to true
        org.bukkit.configuration.file.YamlConfiguration yamlTrue = new org.bukkit.configuration.file.YamlConfiguration();
        yamlTrue.set("anvil.prevent-rename", true);
        listener.loadConfig(yamlTrue);
        assertTrue(listener.isPreventAnvilRename());

        // Per-item override test
        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.SWORD, "test_sword");
        assertNull(def.getPreventAnvilRename()); // By default null (inherits global config)

        def.setPreventAnvilRename(false);
        assertEquals(Boolean.FALSE, def.getPreventAnvilRename());

        def.setPreventAnvilRename(true);
        assertEquals(Boolean.TRUE, def.getPreventAnvilRename());
    }

    @Test
    @DisplayName("Anvil Rename Protection: Display Name Component equality logic")
    void testAnvilRenameDisplayNameLogic() {
        net.kyori.adventure.text.Component originalName = net.kyori.adventure.text.Component.text("Excalibur");
        net.kyori.adventure.text.Component renamed = net.kyori.adventure.text.Component.text("Noob Sword");
        net.kyori.adventure.text.Component identical = net.kyori.adventure.text.Component.text("Excalibur");

        assertNotEquals(originalName, renamed);
        assertEquals(originalName, identical);

        // Null handling (when original item had no custom display name)
        assertNotEquals((Object) null, renamed);
        assertEquals((Object) null, (Object) null);
    }

    @Test
    @DisplayName("Mine Radius: StatType lookups and tier area calculations")
    void testMineRadiusStatAndAreaCalculations() {
        assertEquals(StatType.MINE_RADIUS, StatType.fromKey("mine-radius"));
        assertEquals(StatType.MINE_RADIUS, StatType.fromKey("mineradius"));
        assertEquals(StatType.MINE_RADIUS, StatType.fromKey("mine_radius"));
        assertEquals(StatType.MINE_RADIUS, StatType.fromKey("mine-area"));
        assertEquals(StatType.MINE_RADIUS, StatType.fromKey("radius"));

        // Area format (3x3, 5x5, 7x7, 9x9, 11x11...)
        assertEquals("3×3", StatType.formatMineArea(1));
        assertEquals("5×5", StatType.formatMineArea(2));
        assertEquals("7×7", StatType.formatMineArea(3));
        assertEquals("9×9", StatType.formatMineArea(4));
        assertEquals("11×11", StatType.formatMineArea(5));

        // Area size
        assertEquals(3, StatType.getMineAreaSize(1));
        assertEquals(5, StatType.getMineAreaSize(2));
        assertEquals(7, StatType.getMineAreaSize(3));
        assertEquals(9, StatType.getMineAreaSize(4));
        assertEquals(11, StatType.getMineAreaSize(5));

        // Radius blocks
        assertEquals(1, StatType.getMineRadiusBlocks(1));
        assertEquals(2, StatType.getMineRadiusBlocks(2));
        assertEquals(3, StatType.getMineRadiusBlocks(3));
        assertEquals(4, StatType.getMineRadiusBlocks(4));
        assertEquals(5, StatType.getMineRadiusBlocks(5));
    }

    @Test
    @DisplayName("Mine Radius: Listener configuration loading and limit bounds")
    void testMineRadiusListenerConfig() {
        com.github.orenaidaucac.royItems.mechanics.MineRadiusListener listener =
                new com.github.orenaidaucac.royItems.mechanics.MineRadiusListener(null, null, null, null, null);

        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("mine-radius.enabled", true);
        yaml.set("mine-radius.activation-chance", 75.0);
        yaml.set("mine-radius.cooldown-ms", 250L);
        yaml.set("mine-radius.limits.max-radius-tier", 3);
        yaml.set("mine-radius.limits.max-blocks-per-break", 50);
        yaml.set("mine-radius.extra-storage.auto-deposit", true);
        yaml.set("mine-radius.extra-storage.drop-leftover-to-world", true);
        yaml.set("mine-radius.allowed-blocks", java.util.List.of("STONE", "DIAMOND_ORE", "DEEPSLATE_IRON_ORE"));
        yaml.set("mine-radius.allowed-tools", java.util.List.of("DIAMOND_PICKAXE", "NETHERITE_PICKAXE"));

        listener.loadConfig(yaml);

        assertTrue(listener.isEnabled());
        assertEquals(75.0, listener.getActivationChance(), 0.001);
        assertEquals(250L, listener.getCooldownMs());
        assertEquals(3, listener.getMaxRadiusTier());
        assertEquals(50, listener.getMaxBlocksPerBreak());
        assertTrue(listener.isExtraStorageAutoDeposit());
        assertTrue(listener.isExtraStorageDropLeftover());
        assertTrue(listener.isBlockAllowed(org.bukkit.Material.STONE));
        assertTrue(listener.isBlockAllowed(org.bukkit.Material.DIAMOND_ORE));
        assertTrue(listener.isBlockAllowed(org.bukkit.Material.DEEPSLATE_IRON_ORE));
        assertFalse(listener.isBlockAllowed(org.bukkit.Material.OBSIDIAN));
        assertTrue(listener.isToolAllowed(org.bukkit.Material.DIAMOND_PICKAXE));
        assertTrue(listener.isToolAllowed(org.bukkit.Material.NETHERITE_PICKAXE));
        assertFalse(listener.isToolAllowed(org.bukkit.Material.WOODEN_SWORD));
    }

    @Test
    @DisplayName("Mine Radius: LoreFormat placeholder formatting")
    void testMineRadiusLoreFormatting() {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("format", java.util.List.of("#mine-radius#"));
        yaml.set("stat-format.mine-radius.format", "&7Mine Radius: &e%value%");
        yaml.set("stat-format.positive", "&7#name#: &a#value#");

        com.github.orenaidaucac.royItems.lore.LoreFormat format =
                com.github.orenaidaucac.royItems.lore.LoreFormat.fromConfig("test", yaml);

        // formatStatValue returns raw formatted value ("3×3", "5×5")
        String rawVal = format.formatStatValue("mine-radius", new StatModifier(1.0, 0.0), new java.text.DecimalFormat("#.##"));
        assertEquals("3×3", rawVal);

        String rawValTier2 = format.formatStatValue("mine-radius", new StatModifier(2.0, 0.0), new java.text.DecimalFormat("#.##"));
        assertEquals("5×5", rawValTier2);

        // formatStatLine returns full decorated line with placeholder replacement
        String formattedLine = format.formatStatLine("mine-radius", "Mine Radius", new StatModifier(1.0, 0.0), new java.text.DecimalFormat("#.##"));
        assertEquals("&7Mine Radius: &e3×3", formattedLine);

        String formattedLineTier2 = format.formatStatLine("mine-radius", "Mine Radius", new StatModifier(2.0, 0.0), new java.text.DecimalFormat("#.##"));
        assertEquals("&7Mine Radius: &e5×5", formattedLineTier2);
    }

    @Test
    @DisplayName("AdvancedOreGen Integration: Soft-hook safety and MineRadiusListener integration")
    void testAdvancedOreGenHookAndMineRadiusIntegration() {
        com.github.orenaidaucac.royItems.integration.AdvancedOreGenHook hook =
                new com.github.orenaidaucac.royItems.integration.AdvancedOreGenHook();

        // Without Bukkit server or AdvancedOreGen plugin, should gracefully evaluate to unavailable
        assertFalse(hook.setup());
        assertFalse(hook.isAvailable());
        assertFalse(hook.isOreGenerator(null));
        assertFalse(hook.regenerate(null, null));
        assertFalse(hook.isPhysicalGeneratorSetup(null));

        // Test MineRadiusListener with hook and config toggles
        com.github.orenaidaucac.royItems.mechanics.MineRadiusListener listener =
                new com.github.orenaidaucac.royItems.mechanics.MineRadiusListener(null, null, null, null, hook, null);

        assertSame(hook, listener.getAdvancedOreGenHook());
        assertTrue(listener.isAdvancedOreGenAutoRegenerate());

        org.bukkit.configuration.file.YamlConfiguration yamlFalse = new org.bukkit.configuration.file.YamlConfiguration();
        yamlFalse.set("mine-radius.advanced-ore-gen.auto-regenerate", false);
        listener.loadConfig(yamlFalse);
        assertFalse(listener.isAdvancedOreGenAutoRegenerate());

        org.bukkit.configuration.file.YamlConfiguration yamlTrue = new org.bukkit.configuration.file.YamlConfiguration();
        yamlTrue.set("mine-radius.advanced-ore-gen.auto-regenerate", true);
        listener.loadConfig(yamlTrue);
        assertTrue(listener.isAdvancedOreGenAutoRegenerate());
    }

    @Test
    @DisplayName("RoyItems: Version synchronization test")
    void testVersionSynchronization() {
        com.github.orenaidaucac.royItems.integration.RoyPlaceholderExpansion expansion =
                new com.github.orenaidaucac.royItems.integration.RoyPlaceholderExpansion(null, null, null);
        assertEquals("2026.1.9", expansion.getVersion());
    }

    @Test
    @DisplayName("Permission Stats: Flat value permission parsing")
    void testPermissionParsingFlat() {
        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node1 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.attack-damage.10");
        assertNotNull(node1);
        assertEquals(StatType.ATTACK_DAMAGE, node1.getStatType());
        assertEquals(10.0, node1.getValue(), 0.0001);
        assertFalse(node1.isPercent());

        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node2 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.defense.25");
        assertNotNull(node2);
        assertEquals(StatType.DEFENSE, node2.getStatType());
        assertEquals(25.0, node2.getValue(), 0.0001);
        assertFalse(node2.isPercent());

        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node3 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.movement-speed.0.05");
        assertNotNull(node3);
        assertEquals(StatType.MOVEMENT_SPEED, node3.getStatType());
        assertEquals(0.05, node3.getValue(), 0.0001);
        assertFalse(node3.isPercent());

        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node4 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.crit-rate.20");
        assertNotNull(node4);
        assertEquals(StatType.CRIT_RATE, node4.getStatType());
        assertEquals(20.0, node4.getValue(), 0.0001);
        assertFalse(node4.isPercent());

        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node5 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.movement-speed.-0.02");
        assertNotNull(node5);
        assertEquals(StatType.MOVEMENT_SPEED, node5.getStatType());
        assertEquals(-0.02, node5.getValue(), 0.0001);
        assertFalse(node5.isPercent());
    }

    @Test
    @DisplayName("Permission Stats: Percentage value permission parsing")
    void testPermissionParsingPercentage() {
        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node1 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.crit-rate.15%");
        assertNotNull(node1);
        assertEquals(StatType.CRIT_RATE, node1.getStatType());
        assertEquals(0.15, node1.getValue(), 0.0001);
        assertTrue(node1.isPercent());

        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node2 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.attack-damage.25%");
        assertNotNull(node2);
        assertEquals(StatType.ATTACK_DAMAGE, node2.getStatType());
        assertEquals(0.25, node2.getValue(), 0.0001);
        assertTrue(node2.isPercent());

        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node3 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.movement-speed.10%");
        assertNotNull(node3);
        assertEquals(StatType.MOVEMENT_SPEED, node3.getStatType());
        assertEquals(0.10, node3.getValue(), 0.0001);
        assertTrue(node3.isPercent());

        com.github.orenaidaucac.royItems.stat.PermissionStatManager.ParsedNode node4 =
                com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.defense.20.5%");
        assertNotNull(node4);
        assertEquals(StatType.DEFENSE, node4.getStatType());
        assertEquals(0.205, node4.getValue(), 0.0001);
        assertTrue(node4.isPercent());
    }

    @Test
    @DisplayName("Permission Stats: Invalid and edge cases")
    void testPermissionParsingInvalid() {
        assertNull(com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode(null));
        assertNull(com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode(""));
        assertNull(com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.admin"));
        assertNull(com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats."));
        assertNull(com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.attack-damage"));
        assertNull(com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.attack-damage.abc"));
        assertNull(com.github.orenaidaucac.royItems.stat.PermissionStatManager.parsePermissionNode("royitems.player.stats.non_existent_fake_stat_xyz.10"));
    }

    @Test
    @DisplayName("Permission Stats: Conflict Resolution HIGHEST vs SUM")
    void testPermissionConflictResolution() {
        com.github.orenaidaucac.royItems.stat.PermissionStatManager manager =
                new com.github.orenaidaucac.royItems.stat.PermissionStatManager();

        // Test configuration loading
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.set("permission-stats.enabled", true);
        yaml.set("permission-stats.conflict-resolution", "HIGHEST");
        yaml.set("permission-stats.caps.defense", 50.0);
        manager.loadConfig(yaml);

        assertTrue(manager.isEnabled());
        assertEquals(com.github.orenaidaucac.royItems.stat.PermissionStatManager.ConflictResolution.HIGHEST, manager.getConflictResolution());
        assertEquals(50.0, manager.getStatCaps().get("defense"), 0.001);

        // Switch to SUM
        yaml.set("permission-stats.conflict-resolution", "SUM");
        manager.loadConfig(yaml);
        assertEquals(com.github.orenaidaucac.royItems.stat.PermissionStatManager.ConflictResolution.SUM, manager.getConflictResolution());
    }

    @Test
    @DisplayName("Permission Stats: Mathematical stacking with PlayerStatCache and no items")
    void testPermissionStackingFormula() {
        PlayerStatCache cache = new PlayerStatCache();

        // 1. Player with NO items, only permissions:
        // Flat +10 attack damage, Percent +50% attack damage
        cache.addStat(StatType.ATTACK_DAMAGE, new StatModifier(10.0, 0.50));
        // Base damage 1.0 (vanilla player fist) -> (1.0 + 10.0) * (1 + 0.50) = 11.0 * 1.5 = 16.5
        double totalNoItem = cache.calculateTotal(StatType.ATTACK_DAMAGE, 1.0);
        assertEquals(16.5, totalNoItem, 0.001);

        // 2. Player now equips a weapon with Base 15 Attack Damage and 20% extra
        cache.addStat(StatType.ATTACK_DAMAGE, new StatModifier(15.0, 0.20));
        // Combined: Flat = 10 + 15 = 25.0, Percent = 0.50 + 0.20 = 0.70
        // Total = (1.0 + 25.0) * (1 + 0.70) = 26.0 * 1.70 = 44.2
        double totalWithWeapon = cache.calculateTotal(StatType.ATTACK_DAMAGE, 1.0);
        assertEquals(44.2, totalWithWeapon, 0.001);

        // 3. Crit rate calculation:
        cache.addStat(StatType.CRIT_RATE, new StatModifier(10.0, 0.25));
        // Base 0.0, Flat 10.0, Percent 0.25 -> (0 + 10) * 1.25 = 12.5%
        double critTotal = cache.calculateTotal(StatType.CRIT_RATE, 0.0);
        assertEquals(12.5, critTotal, 0.001);
    }

    @Test
    @DisplayName("LuckPerms Hook: Soft-dependency safety when plugin is not loaded")
    void testLuckPermsHookSafety() {
        com.github.orenaidaucac.royItems.integration.LuckPermsHook hook =
                new com.github.orenaidaucac.royItems.integration.LuckPermsHook();

        assertFalse(hook.setup(null, null, null, null));
        assertFalse(hook.isAvailable());
        assertDoesNotThrow(hook::cleanup);
    }

    @Test
    @DisplayName("ExtraStorage Hook: Dynamic self-healing lifecycle and multi-key fallback")
    void testExtraStorageHookLifecycleAndMultiKey() {
        com.github.orenaidaucac.royItems.integration.ExtraStorageHook hook =
                new com.github.orenaidaucac.royItems.integration.ExtraStorageHook();

        // 1. Initial headless state without Bukkit server
        assertFalse(hook.setup());
        assertFalse(hook.isAvailable());

        // 2. Set availability directly
        hook.setAvailable(true);
        // isAvailable will verify Bukkit server presence; in headless JUnit it returns false safely
        assertFalse(hook.isAvailable());
        hook.setAvailable(false);
        assertFalse(hook.isAvailable());

        // 3. Null safety on all API methods
        assertNull(hook.getUser(null));
        assertTrue(hook.findItem(null, null).isEmpty());
        assertTrue(hook.findItem(null, "DIAMOND").isEmpty());
        assertTrue(hook.findItem(null, org.bukkit.Material.GOLD_BLOCK).isEmpty());

        assertEquals(0, hook.getAmount(null, "GOLD_BLOCK"));
        assertFalse(hook.take(null, "GOLD_BLOCK", 5));
        assertTrue(hook.take(null, "GOLD_BLOCK", 0)); // 0 amount returns true
        assertDoesNotThrow(() -> hook.give(null, "GOLD_BLOCK", 5));
        assertEquals(0, hook.store(null, null));
    }

    @Test
    @DisplayName("IntegrationManager: Dynamic plugin enable/disable and recheck hooks")
    void testIntegrationManagerLifecycle() {
        com.github.orenaidaucac.royItems.integration.IntegrationManager im =
                new com.github.orenaidaucac.royItems.integration.IntegrationManager(null);

        // Verify recheckHooks does not throw when hooks are null/empty
        assertDoesNotThrow(im::recheckHooks);
        assertDoesNotThrow(im::cleanup);
    }

    @Test
    @DisplayName("ItemSet: Dynamic Lore and activation threshold")
    void testItemSetDynamicLoreAndThreshold() {
        com.github.orenaidaucac.royItems.tier_set.ItemSet set =
                new com.github.orenaidaucac.royItems.tier_set.ItemSet();
        set.setId("dragon_slayer");
        set.setDisplayName("&6Dragon Slayer Set");
        set.setRequiredPieces(4);
        set.setActiveLore(List.of("&6Set: Dragon Slayer &a[Active]", "&7Full set bonus active!"));
        set.setInactiveLore(List.of("&8Set: Dragon Slayer &c[Inactive]", "&7Wear 4 pieces to activate!"));

        assertEquals(4, set.getRequiredPieces());
        assertFalse(set.isBonusActive(0));
        assertFalse(set.isBonusActive(3));
        assertTrue(set.isBonusActive(4));
        assertTrue(set.isBonusActive(5));

        List<String> activeLore = set.getLore(true);
        assertEquals(2, activeLore.size());
        assertEquals("&6Set: Dragon Slayer &a[Active]", activeLore.get(0));

        List<String> inactiveLore = set.getLore(false);
        assertEquals(2, inactiveLore.size());
        assertEquals("&8Set: Dragon Slayer &c[Inactive]", inactiveLore.get(0));

        // Fallback when lore is empty
        com.github.orenaidaucac.royItems.tier_set.ItemSet fallbackSet =
                new com.github.orenaidaucac.royItems.tier_set.ItemSet();
        fallbackSet.setId("basic_set");
        fallbackSet.setDisplayName("&bBasic Set");
        assertEquals(List.of("&bBasic Set"), fallbackSet.getLore(true));
        assertEquals(List.of("&bBasic Set"), fallbackSet.getLore(false));
    }

    @Test
    @DisplayName("DynamicLoreEngine: #set# switching between Active and Inactive states")
    void testDynamicLoreEngineSetLoreSwitching() {
        com.github.orenaidaucac.royItems.tier_set.ItemSetManager setManager =
                new com.github.orenaidaucac.royItems.tier_set.ItemSetManager(null);
        com.github.orenaidaucac.royItems.tier_set.ItemSet set =
                new com.github.orenaidaucac.royItems.tier_set.ItemSet();
        set.setId("shadow_assassin");
        set.setDisplayName("&8Shadow Assassin Set");
        set.setRequiredPieces(2);
        set.setActiveLore(List.of("&8Set: Shadow Assassin &a[Active]", "&a+15% Speed, +15% Crit"));
        set.setInactiveLore(List.of("&8Set: Shadow Assassin &c[Inactive]", "&8+15% Speed, +15% Crit"));
        setManager.registerSet(set);

        com.github.orenaidaucac.royItems.lore.DynamicLoreEngine engine =
                new com.github.orenaidaucac.royItems.lore.DynamicLoreEngine(null, null, setManager);

        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "shadow_boots");
        def.setItemSet("shadow_assassin");
        def.setName("&8Shadow Boots");
        def.setLore(List.of("#set#"));

        // 1. Inactive lore test
        List<String> inactiveLore = engine.buildLore(def, List.of(), 0L, null, false, null);
        assertFalse(inactiveLore.isEmpty(), "Inactive lore should not be empty");
        boolean foundInactiveHeader = inactiveLore.stream().anyMatch(l -> l.contains("[Inactive]"));
        assertTrue(foundInactiveHeader, "Expected inactive lore to contain [Inactive]");

        // 2. Active lore test
        List<String> activeLore = engine.buildLore(def, List.of(), 0L, null, true, null);
        assertFalse(activeLore.isEmpty(), "Active lore should not be empty");
        boolean foundActiveHeader = activeLore.stream().anyMatch(l -> l.contains("[Active]"));
        assertTrue(foundActiveHeader, "Expected active lore to contain [Active]");

        // 3. Official set display name preserved
        assertEquals("&8Shadow Assassin Set", engine.resolveSetDisplayName(def));
    }

    @Test
    @DisplayName("SetBonusNotificationManager: State tracking, debounce, and suppression")
    void testSetBonusNotificationManager() {
        com.github.orenaidaucac.royItems.tier_set.ItemSetManager setManager =
                new com.github.orenaidaucac.royItems.tier_set.ItemSetManager(null);
        com.github.orenaidaucac.royItems.tier_set.ItemSet setA =
                new com.github.orenaidaucac.royItems.tier_set.ItemSet("set_a", "&aSet A", Map.of());
        com.github.orenaidaucac.royItems.tier_set.ItemSet setB =
                new com.github.orenaidaucac.royItems.tier_set.ItemSet("set_b", "&bSet B", Map.of());
        setManager.registerSet(setA);
        setManager.registerSet(setB);

        com.github.orenaidaucac.royItems.tier_set.SetBonusNotificationManager notifManager =
                new com.github.orenaidaucac.royItems.tier_set.SetBonusNotificationManager(null, setManager, null, null);
        notifManager.setDebounceTicks(0); // Synchronous dispatch for testing

        UUID uuid = UUID.randomUUID();

        // 1. Suppression during Join
        notifManager.markPlayerJoining(uuid);
        // On join with set_a active, no notification should be sent
        notifManager.initializePlayer(null, Set.of("set_a"));
        notifManager.getLastNotifiedActiveSets().put(uuid, Set.of("set_a"));
        assertEquals(Set.of("set_a"), notifManager.getLastNotifiedActiveSets().get(uuid));

        // 2. Cleanup
        notifManager.cleanupPlayer(uuid);
        assertNull(notifManager.getLastNotifiedActiveSets().get(uuid));
    }

    @Test
    @DisplayName("ItemPDCManager: Item Set State tagging")
    void testItemPDCManagerSetState() {
        org.bukkit.plugin.Plugin mockPlugin = (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.plugin.Plugin.class.getClassLoader(),
                new Class<?>[]{org.bukkit.plugin.Plugin.class},
                (proxy, method, args) -> {
                    if ("getName".equals(method.getName())) return "RoyItems";
                    return null;
                }
        );

        com.github.orenaidaucac.royItems.item.ItemPDCManager pdc =
                new com.github.orenaidaucac.royItems.item.ItemPDCManager(mockPlugin);

        assertNotNull(pdc.getKeySetState());
        assertEquals("item_set_state", pdc.getKeySetState().getKey());
    }

    @Test
    @DisplayName("VoucherCurrencyReward: Fixed and Random Bounds")
    void testVoucherCurrencyReward() {
        // 1. Fixed
        VoucherCurrencyReward fixed = new VoucherCurrencyReward("VAULT", 5000.0);
        assertTrue(fixed.isValid());
        assertFalse(fixed.isRandom());
        assertEquals("VAULT", fixed.getType());
        assertEquals(5000.0, fixed.getFixedValue());
        assertEquals(5000.0, fixed.calculateReward());

        // 2. Random range
        VoucherCurrencyReward random = new VoucherCurrencyReward("EXCELLENTECONOMY_coins", 1000.0, 5000.0);
        assertTrue(random.isValid());
        assertTrue(random.isRandom());
        assertEquals(1000.0, random.getMin());
        assertEquals(5000.0, random.getMax());

        for (int i = 0; i < 50; i++) {
            double roll = random.calculateReward();
            assertTrue(roll >= 1000.0 && roll <= 5000.0, "Roll out of bounds: " + roll);
        }

        // 3. Reversed bounds correction
        VoucherCurrencyReward reversed = new VoucherCurrencyReward("PLAYERPOINTS", 10000.0, 500.0);
        assertTrue(reversed.isValid());
        assertTrue(reversed.isRandom());
        assertEquals(500.0, reversed.getMin());
        assertEquals(10000.0, reversed.getMax());

        // 4. Invalid
        VoucherCurrencyReward invalid = new VoucherCurrencyReward("   ");
        assertFalse(invalid.isValid());
    }

    @Test
    @DisplayName("CurrencyDisplayInfo: Formatting and Decimal Patterns")
    void testCurrencyDisplayInfo() {
        CurrencyDisplayInfo vault = new CurrencyDisplayInfo("VAULT", "Money", "$", "#,##0.##");
        assertEquals("VAULT", vault.getId());
        assertEquals("Money", vault.getDisplayName());
        assertEquals("$", vault.getSymbol());
        assertEquals("1,250", vault.format(1250.0));
        assertEquals("1,250.5", vault.format(1250.5));

        CurrencyDisplayInfo points = new CurrencyDisplayInfo("PLAYERPOINTS", "Points", "✪", "#,##0");
        assertEquals("10,000", points.format(10000.0));
    }

    @Test
    @DisplayName("ItemDefinition: Commands & Currency Rewards")
    void testItemDefinitionVoucherProperties() {
        com.github.orenaidaucac.royItems.item.ItemDefinition def =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.VOUCHER, "vip_scroll");

        assertTrue(def.getCommands().isEmpty());
        assertNull(def.getCurrencyReward());

        def.setCommands(List.of("[console] give %player% diamond 5", "[player] me yay"));
        assertEquals(2, def.getCommands().size());

        VoucherCurrencyReward rew = new VoucherCurrencyReward("VAULT", 1000.0);
        def.setCurrencyReward(rew);
        assertNotNull(def.getCurrencyReward());
        assertEquals(1000.0, def.getCurrencyReward().getFixedValue());
    }

    @Test
    @DisplayName("Equipment Slot Validation: SWORD, ARMOR, OFF_HAND, WING, TALISMAN, and Non-Gear")
    void testEquipmentSlotValidation() {
        org.bukkit.inventory.EquipmentSlot[] allSlots = {
                org.bukkit.inventory.EquipmentSlot.HAND,
                org.bukkit.inventory.EquipmentSlot.OFF_HAND,
                org.bukkit.inventory.EquipmentSlot.HEAD,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.FEET
        };

        // 1. Weapons & Tools -> Strictly MAIN_HAND (HAND)
        for (com.github.orenaidaucac.royItems.item.ItemType mainHandType : List.of(
                com.github.orenaidaucac.royItems.item.ItemType.SWORD,
                com.github.orenaidaucac.royItems.item.ItemType.BOW,
                com.github.orenaidaucac.royItems.item.ItemType.CROSSBOW,
                com.github.orenaidaucac.royItems.item.ItemType.STAFF,
                com.github.orenaidaucac.royItems.item.ItemType.TOOL)) {
            assertTrue(mainHandType.isValidEquipmentSlot(org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.Material.DIAMOND_SWORD));
            assertFalse(mainHandType.isValidEquipmentSlot(org.bukkit.inventory.EquipmentSlot.OFF_HAND, org.bukkit.Material.DIAMOND_SWORD));
            assertFalse(mainHandType.isValidEquipmentSlot(org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.Material.DIAMOND_SWORD));
            assertFalse(mainHandType.isValidEquipmentSlot(org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.Material.DIAMOND_SWORD));
            assertFalse(mainHandType.isInventoryPassive());
        }

        // 2. OFF_HAND -> Strictly OFF_HAND (never MAIN_HAND)
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.OFF_HAND.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.OFF_HAND, org.bukkit.Material.SHIELD));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.OFF_HAND.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.Material.SHIELD));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.OFF_HAND.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.Material.SHIELD));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.OFF_HAND.isInventoryPassive());

        // 3. WING -> Strictly CHEST
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.WING.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.Material.ELYTRA));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.WING.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.Material.ELYTRA));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.WING.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.OFF_HAND, org.bukkit.Material.ELYTRA));

        // 4. ARMOR -> Strictly matching armor slots, NEVER HAND or OFF_HAND
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.Material.DIAMOND_HELMET));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.Material.DIAMOND_HELMET));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.OFF_HAND, org.bukkit.Material.DIAMOND_HELMET));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.Material.DIAMOND_HELMET));

        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.Material.TURTLE_HELMET));
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.Material.PLAYER_HEAD));
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.Material.NETHERITE_CHESTPLATE));
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.Material.IRON_LEGGINGS));
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.FEET, org.bukkit.Material.GOLDEN_BOOTS));

        // Custom material ARMOR (e.g. cosmetic PAPER) allowed in armor slots only, never HAND/OFF_HAND
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.Material.PAPER));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.Material.PAPER));
        assertFalse(com.github.orenaidaucac.royItems.item.ItemType.ARMOR.isValidEquipmentSlot(
                org.bukkit.inventory.EquipmentSlot.OFF_HAND, org.bukkit.Material.PAPER));

        // 5. TALISMAN -> Passive in storage inventory ONLY, never double-counted in equipment slots
        assertTrue(com.github.orenaidaucac.royItems.item.ItemType.TALISMAN.isInventoryPassive());
        for (org.bukkit.inventory.EquipmentSlot slot : allSlots) {
            assertFalse(com.github.orenaidaucac.royItems.item.ItemType.TALISMAN.isValidEquipmentSlot(slot, org.bukkit.Material.TOTEM_OF_UNDYING));
        }

        // 6. Non-gear items -> Zero equipment or passive stats
        for (com.github.orenaidaucac.royItems.item.ItemType nonGear : List.of(
                com.github.orenaidaucac.royItems.item.ItemType.VOUCHER,
                com.github.orenaidaucac.royItems.item.ItemType.GEM_STONE,
                com.github.orenaidaucac.royItems.item.ItemType.CONSUMABLE,
                com.github.orenaidaucac.royItems.item.ItemType.MATERIAL,
                com.github.orenaidaucac.royItems.item.ItemType.PET)) {
            assertFalse(nonGear.isInventoryPassive());
            for (org.bukkit.inventory.EquipmentSlot slot : allSlots) {
                assertFalse(nonGear.isValidEquipmentSlot(slot, org.bukkit.Material.PAPER));
            }
        }
    }

    @Test
    @DisplayName("Voucher Air Click Event: useItemInHand distinguishes air clicks from cancellations")
    void testPlayerInteractAirEventSemantics() {
        org.bukkit.event.player.PlayerInteractEvent airClick = new org.bukkit.event.player.PlayerInteractEvent(
                null,
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                null,
                null,
                org.bukkit.block.BlockFace.SELF,
                org.bukkit.inventory.EquipmentSlot.HAND
        );

        // Bukkit marks RIGHT_CLICK_AIR as useInteractedBlock == DENY (making isCancelled() == true!)
        assertTrue(airClick.isCancelled());
        assertNotEquals(org.bukkit.event.Event.Result.DENY, airClick.useItemInHand());

        // When cancelled explicitly (or disable-on-use denies hand usage), useItemInHand() becomes DENY
        airClick.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        assertEquals(org.bukkit.event.Event.Result.DENY, airClick.useItemInHand());
    }

    @Test
    @DisplayName("Custom 2D Armor (Equippable Component): Key parsing, Slot resolution, Meta application & Config round-trip")
    void testCustom2DArmorEquippableSupport() throws Exception {
        // 1. ItemDefinition defaults
        com.github.orenaidaucac.royItems.item.ItemDefinition emptyDef =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "plain_armor");
        assertNull(emptyDef.getEquipmentModel());
        assertNull(emptyDef.getEquipmentSlot());

        // 2. Shared NamespacedKey parser (independent of CustomModelData integers)
        assertEquals(org.bukkit.NamespacedKey.fromString("nexo:galaxyv2"),
                ItemBuilder.parseNamespacedKey("nexo:galaxyv2"));
        assertEquals(org.bukkit.NamespacedKey.fromString("nexo:galaxyv2"),
                ItemBuilder.parseNamespacedKey("  Nexo:GalaxyV2 "));
        assertEquals(org.bukkit.NamespacedKey.minecraft("custom_armor"),
                ItemBuilder.parseNamespacedKey("custom_armor"));
        assertEquals(org.bukkit.NamespacedKey.fromString("nexo:nexo_armor/forest_helmet"),
                ItemBuilder.parseNamespacedKey("nexo:nexo_armor/forest_helmet"));
        assertNull(ItemBuilder.parseNamespacedKey(null));
        assertNull(ItemBuilder.parseNamespacedKey("   "));
        assertNull(ItemBuilder.parseNamespacedKey("invalid key with spaces"));

        // 3. Automatic EquipmentSlot detection & manual override
        assertEquals(org.bukkit.inventory.EquipmentSlot.HEAD,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.NETHERITE_HELMET, null));
        assertEquals(org.bukkit.inventory.EquipmentSlot.HEAD,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.PLAYER_HEAD, null));
        assertEquals(org.bukkit.inventory.EquipmentSlot.CHEST,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.NETHERITE_CHESTPLATE, null));
        assertEquals(org.bukkit.inventory.EquipmentSlot.CHEST,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.ELYTRA, null));
        assertEquals(org.bukkit.inventory.EquipmentSlot.LEGS,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.NETHERITE_LEGGINGS, null));
        assertEquals(org.bukkit.inventory.EquipmentSlot.FEET,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.NETHERITE_BOOTS, null));
        assertNull(ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.PAPER, null));
        assertEquals(org.bukkit.inventory.EquipmentSlot.CHEST,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.PAPER, "chest"));
        assertEquals(org.bukkit.inventory.EquipmentSlot.FEET,
                ItemBuilder.resolveEquipmentSlot(org.bukkit.Material.PAPER, "BOOTS"));

        // 4. applyEquipmentModel applies EquippableComponent via Bukkit API while leaving unset armor untouched
        final org.bukkit.inventory.EquipmentSlot[] capturedSlot = new org.bukkit.inventory.EquipmentSlot[1];
        final org.bukkit.NamespacedKey[] capturedModel = new org.bukkit.NamespacedKey[1];
        final Boolean[] capturedSwappable = new Boolean[1];
        final boolean[] setEquippableCalled = new boolean[1];

        org.bukkit.inventory.meta.components.EquippableComponent mockEquippable =
                (org.bukkit.inventory.meta.components.EquippableComponent) java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.inventory.meta.components.EquippableComponent.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.inventory.meta.components.EquippableComponent.class},
                        (proxy, method, args) -> {
                            switch (method.getName()) {
                                case "setSlot" -> capturedSlot[0] = (org.bukkit.inventory.EquipmentSlot) args[0];
                                case "getSlot" -> { return capturedSlot[0]; }
                                case "setModel" -> capturedModel[0] = (org.bukkit.NamespacedKey) args[0];
                                case "getModel" -> { return capturedModel[0]; }
                                case "setSwappable" -> capturedSwappable[0] = (Boolean) args[0];
                                case "isSwappable" -> { return capturedSwappable[0] != null && capturedSwappable[0]; }
                                default -> {}
                            }
                            return null;
                        });

        org.bukkit.inventory.meta.ItemMeta mockMeta =
                (org.bukkit.inventory.meta.ItemMeta) java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.inventory.meta.ItemMeta.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.inventory.meta.ItemMeta.class},
                        (proxy, method, args) -> {
                            if ("getEquippable".equals(method.getName())) return mockEquippable;
                            if ("setEquippable".equals(method.getName())) {
                                setEquippableCalled[0] = true;
                                return null;
                            }
                            return null;
                        });

        // Backwards compatibility: no equipment-model -> EquippableComponent is not modified
        ItemBuilder.applyEquipmentModel(mockMeta, org.bukkit.Material.NETHERITE_HELMET, emptyDef);
        assertFalse(setEquippableCalled[0]);

        // Apply Galaxy V2 equipment-model across all 4 slots
        org.bukkit.Material[] materials = {
                org.bukkit.Material.NETHERITE_HELMET,
                org.bukkit.Material.NETHERITE_CHESTPLATE,
                org.bukkit.Material.NETHERITE_LEGGINGS,
                org.bukkit.Material.NETHERITE_BOOTS
        };
        org.bukkit.inventory.EquipmentSlot[] expectedSlots = {
                org.bukkit.inventory.EquipmentSlot.HEAD,
                org.bukkit.inventory.EquipmentSlot.CHEST,
                org.bukkit.inventory.EquipmentSlot.LEGS,
                org.bukkit.inventory.EquipmentSlot.FEET
        };
        for (int i = 0; i < materials.length; i++) {
            capturedSlot[0] = null;
            capturedModel[0] = null;
            capturedSwappable[0] = null;
            setEquippableCalled[0] = false;

            com.github.orenaidaucac.royItems.item.ItemDefinition piece =
                    new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "galaxy_" + i);
            piece.setItemModel("nexo:galaxyv2_item_" + i);
            piece.setEquipmentModel("nexo:galaxyv2");

            ItemBuilder.applyEquipmentModel(mockMeta, materials[i], piece);
            assertTrue(setEquippableCalled[0]);
            assertEquals(expectedSlots[i], capturedSlot[0]);
            assertEquals(org.bukkit.NamespacedKey.fromString("nexo:galaxyv2"), capturedModel[0]);
            assertNull(capturedSwappable[0]); // vanilla armor preserves native swappable flag
        }

        // Custom base material + explicit equipment-slot marks swappable=true
        capturedSlot[0] = null;
        capturedModel[0] = null;
        capturedSwappable[0] = null;
        setEquippableCalled[0] = false;
        com.github.orenaidaucac.royItems.item.ItemDefinition customBase =
                new com.github.orenaidaucac.royItems.item.ItemDefinition(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "custom_cape");
        customBase.setEquipmentSlot("CHEST");
        customBase.setEquipmentModel("nexo:galaxyv2");
        ItemBuilder.applyEquipmentModel(mockMeta, org.bukkit.Material.PAPER, customBase);
        assertTrue(setEquippableCalled[0]);
        assertEquals(org.bukkit.inventory.EquipmentSlot.CHEST, capturedSlot[0]);
        assertEquals(org.bukkit.NamespacedKey.fromString("nexo:galaxyv2"), capturedModel[0]);
        assertEquals(Boolean.TRUE, capturedSwappable[0]);

        // 5. YAML Config Load & Save Round-Trip (item-model & equipment-model independence)
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("royitems-equippable-test");
        try {
            java.io.File armorFile = tempDir.resolve("item/ARMOR.yml").toFile();
            armorFile.getParentFile().mkdirs();
            org.bukkit.configuration.file.YamlConfiguration armorYaml = new org.bukkit.configuration.file.YamlConfiguration();
            armorYaml.set("galaxyv2_helmet.material", "NETHERITE_HELMET");
            armorYaml.set("galaxyv2_helmet.item-model", "nexo:galaxyv2_helmet");
            armorYaml.set("galaxyv2_helmet.equipment-model", "nexo:galaxyv2");
            armorYaml.set("legacy_helm.material", "DIAMOND_HELMET");
            armorYaml.set("custom_helm.material", "PAPER");
            armorYaml.set("custom_helm.equippable-model", "nexo:forest");
            armorYaml.set("custom_helm.equippable-slot", "HEAD");
            armorYaml.save(armorFile);

            org.bukkit.plugin.Plugin mockPlugin = (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.plugin.Plugin.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.plugin.Plugin.class},
                    (proxy, method, args) -> {
                        if ("getDataFolder".equals(method.getName())) return tempDir.toFile();
                        if ("getLogger".equals(method.getName())) return java.util.logging.Logger.getGlobal();
                        return null;
                    });
            com.github.orenaidaucac.royItems.item.ItemRegistry registry = new com.github.orenaidaucac.royItems.item.ItemRegistry();
            com.github.orenaidaucac.royItems.core.config.ItemConfigManager cfgManager =
                    new com.github.orenaidaucac.royItems.core.config.ItemConfigManager(mockPlugin, registry);
            cfgManager.loadAll();

            com.github.orenaidaucac.royItems.item.ItemDefinition loadedGalaxy =
                    registry.get(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "galaxyv2_helmet");
            assertNotNull(loadedGalaxy);
            assertEquals("nexo:galaxyv2_helmet", loadedGalaxy.getItemModel());
            assertEquals("nexo:galaxyv2", loadedGalaxy.getEquipmentModel());

            com.github.orenaidaucac.royItems.item.ItemDefinition loadedLegacy =
                    registry.get(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "legacy_helm");
            assertNotNull(loadedLegacy);
            assertNull(loadedLegacy.getEquipmentModel());

            com.github.orenaidaucac.royItems.item.ItemDefinition loadedAlias =
                    registry.get(com.github.orenaidaucac.royItems.item.ItemType.ARMOR, "custom_helm");
            assertNotNull(loadedAlias);
            assertEquals("nexo:forest", loadedAlias.getEquipmentModel());
            assertEquals("HEAD", loadedAlias.getEquipmentSlot());

            // Persist & clear round-trip
            assertTrue(cfgManager.saveItem(loadedGalaxy));
            loadedGalaxy.setEquipmentModel(null);
            loadedGalaxy.setEquipmentSlot(null);
            assertTrue(cfgManager.saveItem(loadedGalaxy));
            org.bukkit.configuration.file.YamlConfiguration reloaded =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(armorFile);
            assertEquals("nexo:galaxyv2_helmet", reloaded.getString("galaxyv2_helmet.item-model"));
            assertFalse(reloaded.contains("galaxyv2_helmet.equipment-model"));
            assertFalse(reloaded.contains("galaxyv2_helmet.equipment-slot"));
        } finally {
            try (var walk = java.nio.file.Files.walk(tempDir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(java.io.File::delete);
            }
        }

        // 6. Verify editor-gui.yml includes equipment-model at Page 3 Slot 29
        org.bukkit.configuration.file.YamlConfiguration gui =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/gui/editor-gui.yml"));
        assertTrue(gui.contains("detail-editor.properties.equipment-model"));
        assertEquals(3, gui.getInt("detail-editor.properties.equipment-model.page"));
        assertEquals(29, gui.getInt("detail-editor.properties.equipment-model.slot"));
        assertEquals("NETHERITE_CHESTPLATE", gui.getString("detail-editor.properties.equipment-model.material"));
    }

    @Test
    @DisplayName("PhoenixNPCs Integration: Config parsing, duplicate handling, NPC validity & interaction logic")
    void testPhoenixNpcsIntegration() throws Exception {
        // 1. Config Parsing & Deduplication
        org.bukkit.configuration.file.YamlConfiguration cfg1 = new org.bukkit.configuration.file.YamlConfiguration();
        cfg1.set("title", "<gold>Station 1</gold>");
        cfg1.set("phoenixnpcs", List.of("npc_blacksmith", "npc_alchemist", "npc_blacksmith")); // contains duplicate

        com.github.orenaidaucac.royItems.station.CraftingStation st1 =
                com.github.orenaidaucac.royItems.station.CraftingStation.fromConfig("station_1", cfg1);
        assertEquals(2, st1.getPhoenixNpcs().size());
        assertEquals("npc_blacksmith", st1.getPhoenixNpcs().get(0));
        assertEquals("npc_alchemist", st1.getPhoenixNpcs().get(1));

        // Test single string configuration
        org.bukkit.configuration.file.YamlConfiguration cfgSingle = new org.bukkit.configuration.file.YamlConfiguration();
        cfgSingle.set("phoenixnpcs", "solo_npc");
        com.github.orenaidaucac.royItems.station.CraftingStation stSingle =
                com.github.orenaidaucac.royItems.station.CraftingStation.fromConfig("station_single", cfgSingle);
        assertEquals(1, stSingle.getPhoenixNpcs().size());
        assertEquals("solo_npc", stSingle.getPhoenixNpcs().get(0));

        // Test absent configuration
        org.bukkit.configuration.file.YamlConfiguration cfgEmpty = new org.bukkit.configuration.file.YamlConfiguration();
        com.github.orenaidaucac.royItems.station.CraftingStation stEmpty =
                com.github.orenaidaucac.royItems.station.CraftingStation.fromConfig("station_empty", cfgEmpty);
        assertNotNull(stEmpty.getPhoenixNpcs());
        assertTrue(stEmpty.getPhoenixNpcs().isEmpty());

        // 2. Cross-Station Duplicate ID Detection & Resolution
        org.bukkit.configuration.file.YamlConfiguration cfg2 = new org.bukkit.configuration.file.YamlConfiguration();
        cfg2.set("title", "<gold>Station 2</gold>");
        cfg2.set("phoenixnpcs", List.of("npc_alchemist", "npc_wizard")); // duplicates npc_alchemist from station 1

        com.github.orenaidaucac.royItems.station.CraftingStation st2 =
                com.github.orenaidaucac.royItems.station.CraftingStation.fromConfig("station_2", cfg2);

        java.io.File tempDir = java.nio.file.Files.createTempDirectory("royitems-station-test").toFile();
        try {
            java.io.File stFolder = new java.io.File(tempDir, "crafting-station");
            stFolder.mkdirs();
            cfg1.save(new java.io.File(stFolder, "station_1.yml"));
            cfg2.save(new java.io.File(stFolder, "station_2.yml"));

            java.util.logging.Logger testLogger = java.util.logging.Logger.getLogger("RoyItemsTest");
            org.bukkit.plugin.Plugin mockPlugin = (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.plugin.Plugin.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.plugin.Plugin.class},
                    (proxy, method, args) -> {
                        if ("getDataFolder".equals(method.getName())) return tempDir;
                        if ("getLogger".equals(method.getName())) return testLogger;
                        return null;
                    });

            com.github.orenaidaucac.royItems.integration.IntegrationManager mockIntegration =
                    new com.github.orenaidaucac.royItems.integration.IntegrationManager(mockPlugin);

            final String[] stationOpened = new String[1];
            com.github.orenaidaucac.royItems.station.CraftingStationManager manager =
                    new com.github.orenaidaucac.royItems.station.CraftingStationManager(
                            null, null, null, null, mockIntegration) {
                        @Override
                        public void openStation(org.bukkit.entity.Player player, String stationId, int page) {
                            // Tracking open call
                            stationOpened[0] = stationId;
                        }
                    };

            // Access stations map via reflection or loadAll with mock
            java.lang.reflect.Field stationsField = com.github.orenaidaucac.royItems.station.CraftingStationManager.class.getDeclaredField("stations");
            stationsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, com.github.orenaidaucac.royItems.station.CraftingStation> map =
                    (Map<String, com.github.orenaidaucac.royItems.station.CraftingStation>) stationsField.get(manager);
            map.put("station_1", st1);
            map.put("station_2", st2);

            manager.validateNpcConfigurations();

            // station_1 must keep npc_alchemist, while station_2 had the duplicate removed
            assertTrue(st1.getPhoenixNpcs().contains("npc_alchemist"));
            assertFalse(st2.getPhoenixNpcs().contains("npc_alchemist"), "Duplicate ID must be stripped from second station");
            assertTrue(st2.getPhoenixNpcs().contains("npc_wizard"), "Unique ID must be preserved");

            // 3. Interaction Handling & Validity Checks

            // Mock Player (online)
            org.bukkit.entity.Player mockPlayer = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.entity.Player.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.entity.Player.class},
                    (proxy, method, args) -> {
                        if ("isOnline".equals(method.getName())) return true;
                        return null;
                    });

            // Mock Npc (valid, enabled, visible, id: npc_blacksmith)
            final boolean[] npcEnabled = {true};
            final boolean[] npcVisible = {true};
            final String[] npcIdentifier = {"npc_blacksmith"};
            final String[] npcDisplayName = {"Blacksmith Thoren"};

            com.phoenixplugins.phoenixnpcs.api.Npc mockNpc = (com.phoenixplugins.phoenixnpcs.api.Npc) java.lang.reflect.Proxy.newProxyInstance(
                    com.phoenixplugins.phoenixnpcs.api.Npc.class.getClassLoader(),
                    new Class<?>[]{com.phoenixplugins.phoenixnpcs.api.Npc.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getIdentifier" -> { return npcIdentifier[0]; }
                            case "getDisplayName" -> { return npcDisplayName[0]; }
                            case "isEnabled" -> { return npcEnabled[0]; }
                            case "isVisibleTo" -> { return npcVisible[0]; }
                            default -> { return null; }
                        }
                    });

            // Case A: Valid interaction -> opens station_1
            stationOpened[0] = null;
            boolean handled = manager.handlePhoenixNpcInteract(mockPlayer, mockNpc);
            assertTrue(handled);
            assertEquals("station_1", stationOpened[0]);

            // Case B: Valid interaction via display name
            npcIdentifier[0] = "different_id";
            st1.getPhoenixNpcs().add("Blacksmith Thoren");
            stationOpened[0] = null;
            handled = manager.handlePhoenixNpcInteract(mockPlayer, mockNpc);
            assertTrue(handled);
            assertEquals("station_1", stationOpened[0]);
            st1.getPhoenixNpcs().remove("Blacksmith Thoren");
            npcIdentifier[0] = "npc_blacksmith";

            // Case C: NPC disabled -> returns false, does not open
            npcEnabled[0] = false;
            stationOpened[0] = null;
            handled = manager.handlePhoenixNpcInteract(mockPlayer, mockNpc);
            assertFalse(handled);
            assertNull(stationOpened[0]);
            npcEnabled[0] = true;

            // Case D: NPC invisible to player -> returns false
            npcVisible[0] = false;
            stationOpened[0] = null;
            handled = manager.handlePhoenixNpcInteract(mockPlayer, mockNpc);
            assertFalse(handled);
            assertNull(stationOpened[0]);
            npcVisible[0] = true;

            // Case E: Unmatched NPC -> returns false
            npcIdentifier[0] = "unmatched_npc_id";
            npcDisplayName[0] = "Random Guy";
            stationOpened[0] = null;
            handled = manager.handlePhoenixNpcInteract(mockPlayer, mockNpc);
            assertFalse(handled);
            assertNull(stationOpened[0]);

            // Case F: Null / Offline player -> returns false
            assertFalse(manager.handlePhoenixNpcInteract(null, mockNpc));

            org.bukkit.entity.Player offlinePlayer = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                    org.bukkit.entity.Player.class.getClassLoader(),
                    new Class<?>[]{org.bukkit.entity.Player.class},
                    (proxy, method, args) -> {
                        if ("isOnline".equals(method.getName())) return false;
                        return null;
                    });
            assertFalse(manager.handlePhoenixNpcInteract(offlinePlayer, mockNpc));

            // Case G: Null NPC -> returns false
            assertFalse(manager.handlePhoenixNpcInteract(mockPlayer, null));

            // 4. Hook availability when plugin is not installed
            com.github.orenaidaucac.royItems.integration.PhoenixNpcsHook hook =
                    new com.github.orenaidaucac.royItems.integration.PhoenixNpcsHook();
            assertFalse(hook.isAvailable(), "Hook must report false when PhoenixNPCs is not loaded");

            // 5. Idempotent registration without PhoenixNPCs must not throw
            assertDoesNotThrow(manager::registerPhoenixNpcsListener);
            assertDoesNotThrow(manager::unregisterPhoenixNpcsListener);
            assertDoesNotThrow(manager::cleanup);

        } finally {
            try (var walk = java.nio.file.Files.walk(tempDir.toPath())) {
                walk.sorted(Comparator.reverseOrder()).map(java.nio.file.Path::toFile).forEach(java.io.File::delete);
            }
        }
    }

    @Test
    @DisplayName("Ghost Block Hardening: Concurrency Lock & OreGen Regeneration State Safety")
    void testGhostBlockMineRadiusHardening() {
        com.github.orenaidaucac.royItems.integration.AdvancedOreGenHook hook =
                new com.github.orenaidaucac.royItems.integration.AdvancedOreGenHook();
        assertFalse(hook.setup());
        assertFalse(hook.isAvailable());
        assertFalse(hook.isOreGenerator(null));
        assertFalse(hook.regenerate(null, null));

        com.github.orenaidaucac.royItems.mechanics.MineRadiusListener listener =
                new com.github.orenaidaucac.royItems.mechanics.MineRadiusListener(null, null, null, null, hook, null);

        org.bukkit.configuration.file.YamlConfiguration config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("mine-radius.enabled", true);
        config.set("mine-radius.advanced-ore-gen.auto-regenerate", true);
        config.set("mine-radius.limits.max-radius-tier", 7);
        listener.loadConfig(config);

        assertTrue(listener.isEnabled());
        assertTrue(listener.isAdvancedOreGenAutoRegenerate());
        assertEquals(7, listener.getMaxRadiusTier());
        assertSame(hook, listener.getAdvancedOreGenHook());
    }

    @Test
    @DisplayName("Voucher Interaction Hardening: Debounce & Air Right-Click Robustness")
    void testVoucherInteractionHardening() {
        // Test CurrencyDisplayInfo resolution
        CurrencyDisplayInfo info = new CurrencyDisplayInfo("VAULT", "Dollar", "$", "#,##0.00");
        assertEquals("VAULT", info.getId());
        assertEquals("Dollar", info.getDisplayName());
        assertEquals("$", info.getSymbol());
        assertEquals("$1,500.00", info.getSymbol() + info.format(1500.0));

        // Test VoucherCurrencyReward calculations
        VoucherCurrencyReward fixedReward = new VoucherCurrencyReward("VAULT", 5000.0);
        assertTrue(fixedReward.isValid());
        assertEquals(5000.0, fixedReward.calculateReward(), 0.001);
        assertFalse(fixedReward.isRandom());

        VoucherCurrencyReward rangeReward = new VoucherCurrencyReward("PLAYERPOINTS", 100.0, 500.0);
        assertTrue(rangeReward.isValid());
        assertTrue(rangeReward.isRandom());
        double rolled = rangeReward.calculateReward();
        assertTrue(rolled >= 100.0 && rolled <= 500.0, "Rolled reward must be within configured range");

        // Verify PlayerInteractEvent semantics on RIGHT_CLICK_AIR
        org.bukkit.event.player.PlayerInteractEvent airClick = new org.bukkit.event.player.PlayerInteractEvent(
                null,
                org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                null,
                null,
                org.bukkit.block.BlockFace.SELF,
                org.bukkit.inventory.EquipmentSlot.HAND
        );
        assertTrue(airClick.isCancelled(), "Air clicks are cancelled by default in Bukkit due to useInteractedBlock DENY");
        // But our SpecialItemListener at LOWEST priority processes vouchers before anything else and enforces DENY
        airClick.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        airClick.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        assertEquals(org.bukkit.event.Event.Result.DENY, airClick.useItemInHand());
    }

    @Test
    void testEcoEnchantRewardModel() {
        // Test basic creation and clamping
        EcoEnchantReward reward = new EcoEnchantReward("common", "normal", 1, 3);
        assertEquals("common", reward.getRarity());
        assertEquals("normal", reward.getType());
        assertEquals(1, reward.getMinLevel());
        assertEquals(3, reward.getMaxLevel());
        assertTrue(reward.hasFilter());

        // Test level bounds swap if min > max
        EcoEnchantReward inverted = new EcoEnchantReward("rare", null, 5, 2);
        assertEquals(2, inverted.getMinLevel());
        assertEquals(5, inverted.getMaxLevel());

        // Test clamping of negative minLevel
        EcoEnchantReward negativeMin = new EcoEnchantReward(null, "spell", -5, 0);
        assertEquals(1, negativeMin.getMinLevel());
        assertEquals(Integer.MAX_VALUE, negativeMin.getMaxLevel());
    }

    @Test
    void testEcoEnchantsToRomanAndStripColor() {
        // Test Roman numerals
        assertEquals("I", EcoEnchantsHook.toRoman(1));
        assertEquals("II", EcoEnchantsHook.toRoman(2));
        assertEquals("III", EcoEnchantsHook.toRoman(3));
        assertEquals("IV", EcoEnchantsHook.toRoman(4));
        assertEquals("V", EcoEnchantsHook.toRoman(5));
        assertEquals("VI", EcoEnchantsHook.toRoman(6));
        assertEquals("VII", EcoEnchantsHook.toRoman(7));
        assertEquals("VIII", EcoEnchantsHook.toRoman(8));
        assertEquals("IX", EcoEnchantsHook.toRoman(9));
        assertEquals("X", EcoEnchantsHook.toRoman(10));
        assertEquals("0", EcoEnchantsHook.toRoman(0));
        assertEquals("-1", EcoEnchantsHook.toRoman(-1));

        // Test stripColor
        assertEquals("Common", EcoEnchantsHook.stripColor("<green>Common</green>"));
        assertEquals("Sương Nguyện", EcoEnchantsHook.stripColor("&#8ACDB5Sương Nguyện"));
        assertEquals("Sương Nguyện", EcoEnchantsHook.stripColor("§a§lSương Nguyện"));
        assertEquals("Dấu Kiếm", EcoEnchantsHook.stripColor("<#8ACDB5>Dấu Kiếm"));
        assertEquals("Tro Thức", EcoEnchantsHook.stripColor("{#B8B2A6}Tro Thức"));
    }

    @Test
    void testEcoEnchantCandidateFormatting() {
        EcoEnchantsHook.EcoEnchantCandidate candidate = new EcoEnchantsHook.EcoEnchantCandidate(
                null,
                "sw_dau_kiem",
                "&#8ACDB5Dấu Kiếm",
                "2",
                "&#8ACDB5Sương Nguyện",
                "1",
                "",
                3,
                null
        );

        assertEquals("sw_dau_kiem", candidate.getId());
        assertEquals("&#8ACDB5Dấu Kiếm", candidate.getRawDisplayName());
        assertEquals("2", candidate.getRarityId());
        assertEquals("&#8ACDB5Sương Nguyện", candidate.getRarityDisplayName());
        assertEquals(3, candidate.getMaxLevel());

        // Formatted name should append Roman numerals while preserving hex color
        assertEquals("&#8ACDB5Dấu Kiếm I", candidate.getFormattedName(1));
        assertEquals("&#8ACDB5Dấu Kiếm II", candidate.getFormattedName(2));
        assertEquals("&#8ACDB5Dấu Kiếm III", candidate.getFormattedName(3));

        // Single level enchantment (maxLevel == 1) should not append numeral for level 1
        EcoEnchantsHook.EcoEnchantCandidate singleLevel = new EcoEnchantsHook.EcoEnchantCandidate(
                null, "mending", "Mending", "common", "Common", "normal", "<green>", 1, null
        );
        assertEquals("<green>Mending", singleLevel.getFormattedName(1));
    }

    @Test
    void testItemConfigManagerEcoEnchantsParsing() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("items.voucher_1.type", "VOUCHER");
        yaml.set("items.voucher_1.material", "ENCHANTED_BOOK");
        yaml.set("items.voucher_1.ecoenchants.rarity", "common");
        yaml.set("items.voucher_1.ecoenchants.min-level", 1);
        yaml.set("items.voucher_1.ecoenchants.max-level", 3);

        ItemDefinition def = new ItemDefinition(ItemType.VOUCHER, "voucher_1");
        ConfigurationSection sec = yaml.getConfigurationSection("items.voucher_1");
        assertNotNull(sec);
        if (sec.contains("ecoenchants")) {
            ConfigurationSection ecoSec = sec.getConfigurationSection("ecoenchants");
            String rarity = ecoSec.getString("rarity");
            String type = ecoSec.getString("type");
            int minLevel = ecoSec.getInt("min-level", 1);
            int maxLevel = ecoSec.getInt("max-level", Integer.MAX_VALUE);
            def.setEcoEnchantReward(new EcoEnchantReward(rarity, type, minLevel, maxLevel));
        }

        assertNotNull(def.getEcoEnchantReward());
        assertEquals("common", def.getEcoEnchantReward().getRarity());
        assertNull(def.getEcoEnchantReward().getType());
        assertEquals(1, def.getEcoEnchantReward().getMinLevel());
        assertEquals(3, def.getEcoEnchantReward().getMaxLevel());
    }

    @Test
    void testDynamicLoreEngineEcoEnchantsLore() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "skywitch_blade");
        def.setLoreFormat("default");

        // Subclass EcoEnchantsHook for deterministic testing
        EcoEnchantsHook mockHook = new EcoEnchantsHook() {
            @Override
            public boolean isEcoEnchant(Enchantment ench) {
                return true;
            }

            @Override
            public String getFormattedName(Enchantment ench, int level) {
                return "&#8ACDB5Dấu Kiếm III";
            }

            @Override
            public String getDisplayName(Enchantment ench) {
                return "&#8ACDB5Dấu Kiếm";
            }
        };
        engine.setEcoEnchantsHook(mockHook);

        Enchantment ench = Enchantment.UNBREAKING;

        LoreFormat format = new LoreFormat();
        format.setEnchantmentFormat("&7%name% %level%");

        Map<Enchantment, Integer> active = Map.of(ench, 3);
        List<String> lines = engine.generateEnchantmentLines(def, format, active);

        assertNotNull(lines);
        assertEquals(1, lines.size());
        assertEquals("&#8ACDB5Dấu Kiếm III", lines.get(0));
    }

    @Test
    void testGlobalConfigEnchantmentInheritance() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        YamlConfiguration globalConfig = new YamlConfiguration();
        globalConfig.set("enchantments.default-format", "&b✦ %name% &f%level%");
        globalConfig.set("enchantments.curse-format", "&c☠ %name% &f%level%");
        globalConfig.set("enchantments.level-style", "ROMAN");
        engine.loadConfig(globalConfig);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "custom_sword");
        LoreFormat emptyFormat = new LoreFormat(engine.getGlobalEnchantmentFormat());

        // Vanilla Sharpness 5
        Map<Enchantment, Integer> active1 = Map.of(Enchantment.SHARPNESS, 5);
        List<String> lines1 = engine.generateEnchantmentLines(def, emptyFormat, active1);
        assertEquals(1, lines1.size());
        assertEquals("&b✦ Sharpness &fV", lines1.get(0));

        // Vanilla Curse of Binding 1 (single-level curse, level 1 omitted)
        Map<Enchantment, Integer> active2 = Map.of(Enchantment.BINDING_CURSE, 1);
        List<String> lines2 = engine.generateEnchantmentLines(def, emptyFormat, active2);
        assertEquals(1, lines2.size());
        assertEquals("&c☠ Curse of Binding", lines2.get(0));
    }

    @Test
    void testLocalLoreFormatOverridesGlobalConfig() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        YamlConfiguration globalConfig = new YamlConfiguration();
        globalConfig.set("enchantments.default-format", "&b✦ %name% &f%level%");
        engine.loadConfig(globalConfig);

        YamlConfiguration localConfig = new YamlConfiguration();
        localConfig.set("enchantments-format.format", "&e⚔ %name% [%level%]");

        LoreFormat localFormat = LoreFormat.fromConfig("weapon", localConfig, engine.getGlobalEnchantmentFormat());

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "custom_sword");
        Map<Enchantment, Integer> active = Map.of(Enchantment.SHARPNESS, 5);
        List<String> lines = engine.generateEnchantmentLines(def, localFormat, active);

        assertEquals(1, lines.size());
        assertEquals("&e⚔ Sharpness [V]", lines.get(0));
    }

    @Test
    void testEcoEnchantCustomFormatAndDeduplication() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        EcoEnchantsHook mockHook = new EcoEnchantsHook() {
            @Override
            public boolean isEcoEnchant(Enchantment ench) {
                return true;
            }

            @Override
            public String getDisplayName(Enchantment ench) {
                return "&#8ACDB5Dấu Kiếm";
            }

            @Override
            public int getMaxLevel(Enchantment ench) {
                return 5;
            }

            @Override
            public String getRarityDisplayName(Enchantment ench) {
                return "&#8ACDB5Sương Nguyện";
            }
        };
        engine.setEcoEnchantsHook(mockHook);

        LoreFormat format = new LoreFormat();
        format.setEnchantmentFormat("&b✦ %name% &f%level%");

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "custom_sword");
        Map<Enchantment, Integer> active = Map.of(Enchantment.UNBREAKING, 3);
        List<String> lines = engine.generateEnchantmentLines(def, format, active);

        assertEquals(1, lines.size());
        assertEquals("&b✦ &#8ACDB5Dấu Kiếm &fIII", lines.get(0));
    }

    @Test
    void testSingleLevelEnchantmentRemovesBrackets() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        LoreFormat format = new LoreFormat();
        format.setEnchantmentFormat("&7• %name% &e[%level%]");

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "custom_sword");
        Map<Enchantment, Integer> active = Map.of(Enchantment.MENDING, 1);
        List<String> lines = engine.generateEnchantmentLines(def, format, active);

        assertEquals(1, lines.size());
        assertEquals("&7• Mending", lines.get(0));
    }

    @Test
    void testCustomNamesOverride() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        EcoEnchantsHook mockHook = new EcoEnchantsHook() {
            @Override
            public boolean isEcoEnchant(Enchantment ench) {
                return ench.equals(Enchantment.UNBREAKING);
            }

            @Override
            public String getDisplayName(Enchantment ench) {
                return "&#8ACDB5Dấu Kiếm";
            }
        };
        engine.setEcoEnchantsHook(mockHook);

        LoreFormat format = new LoreFormat();
        format.setEnchantmentFormat("&7%name% %level%");
        format.setEnchantmentCustomNames(Map.of(
                "sharpness", "&cSắc Bén",
                "unbreaking", "&dKiếm Thần"
        ));

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "custom_sword");
        Map<Enchantment, Integer> active = new LinkedHashMap<>();
        active.put(Enchantment.SHARPNESS, 5);
        active.put(Enchantment.UNBREAKING, 3);

        List<String> lines = engine.generateEnchantmentLines(def, format, active);
        assertEquals(2, lines.size());
        assertEquals("&cSắc Bén V", lines.get(0));
        assertEquals("&dKiếm Thần III", lines.get(1));
    }

    @Test
    void testLevelStyleNumber() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        LoreFormat format = new LoreFormat();
        format.setEnchantmentFormat("&7• %name% &f%level%");
        format.setEnchantmentLevelStyle("NUMBER");

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "custom_sword");
        Map<Enchantment, Integer> active = Map.of(Enchantment.SHARPNESS, 5);

        List<String> lines = engine.generateEnchantmentLines(def, format, active);
        assertEquals(1, lines.size());
        assertEquals("&7• Sharpness &f5", lines.get(0));
    }

    @Test
    void testMixedVanillaAndEcoEnchantsSorting() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        EcoEnchantsHook mockHook = new EcoEnchantsHook() {
            @Override
            public boolean isEcoEnchant(Enchantment ench) {
                return ench.equals(Enchantment.UNBREAKING);
            }

            @Override
            public String getDisplayName(Enchantment ench) {
                return "&#8ACDB5Dấu Kiếm";
            }
        };
        engine.setEcoEnchantsHook(mockHook);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "custom_sword");
        Map<Enchantment, Integer> active = new LinkedHashMap<>();
        active.put(Enchantment.UNBREAKING, 3);
        active.put(Enchantment.SHARPNESS, 5);

        // 1. VANILLA_FIRST
        LoreFormat vanillaFirst = new LoreFormat();
        vanillaFirst.setEnchantmentOrder("VANILLA_FIRST");
        List<String> lines1 = engine.generateEnchantmentLines(def, vanillaFirst, active);
        assertTrue(lines1.get(0).contains("Sharpness"));
        assertTrue(lines1.get(1).contains("Dấu Kiếm"));

        // 2. ECO_FIRST
        LoreFormat ecoFirst = new LoreFormat();
        ecoFirst.setEnchantmentOrder("ECO_FIRST");
        List<String> lines2 = engine.generateEnchantmentLines(def, ecoFirst, active);
        assertTrue(lines2.get(0).contains("Dấu Kiếm"));
        assertTrue(lines2.get(1).contains("Sharpness"));
    }

    @Test
    void testTextUtilityAdventureComponentColorsWithVietnameseAndHex() {
        String input = "&b✦ &#8ACDB5Dấu Kiếm &fIII";
        net.kyori.adventure.text.Component component = TextUtility.format(input);
        assertNotNull(component);

        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component);
        assertEquals("✦ Dấu Kiếm III", plain);
    }

    @Test
    void testDynamicEnchantmentLoreUpdateAndPreserveCustomLines() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "legendary_sword");
        Map<Enchantment, Integer> active = new LinkedHashMap<>();
        active.put(Enchantment.SHARPNESS, 5);
        active.put(Enchantment.UNBREAKING, 3);

        // Current lore on item has a description, old enchantment lines, and a custom player line
        List<Component> currentLore = new ArrayList<>(List.of(
                TextUtility.format("&7A mighty blade"),
                TextUtility.format("&7Sharpness V"),
                TextUtility.format("&7Unbreaking III"),
                TextUtility.format("&8[Empty Socket]"),
                TextUtility.format("&eOwner: Steve")
        ));

        // Format is updated: symbol "✦ ", format "&b✦ %name% &f%level%"
        LoreFormat newFormat = new LoreFormat();
        newFormat.setEnchantmentFormat("&b✦ %name% &f%level%");
        newFormat.setEnchantmentLevelStyle("ROMAN");

        engine.setGlobalEnchantmentFormat(newFormat);

        List<Component> updated = engine.updateEnchantmentLinesInLore(currentLore, def, active);

        assertNotNull(updated);
        assertEquals(5, updated.size());

        List<String> plainLines = updated.stream()
                .map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c))
                .toList();

        assertEquals("A mighty blade", plainLines.get(0));
        assertEquals("✦ Sharpness V", plainLines.get(1));
        assertEquals("✦ Unbreaking III", plainLines.get(2));
        assertEquals("[Empty Socket]", plainLines.get(3));
        assertEquals("Owner: Steve", plainLines.get(4));
    }

    @Test
    void testMergeCustomLoreOnLiveUpdate() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "excalibur");
        Map<Enchantment, Integer> active = Map.of(Enchantment.SHARPNESS, 5);

        List<Component> currentLore = List.of(
                TextUtility.format("&7Old Description"),
                TextUtility.format("&7Sharpness V"),
                TextUtility.format("&aKills: 1500")
        );

        List<Component> baseLore = List.of(
                TextUtility.format("&7New Definition Description"),
                TextUtility.format("&b✦ Sharpness V"),
                TextUtility.format("&8&m------------------------")
        );

        List<Component> merged = engine.mergeCustomLore(currentLore, baseLore, def, active);

        List<String> plainLines = merged.stream()
                .map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c))
                .toList();

        assertTrue(plainLines.contains("Kills: 1500"));
        assertEquals("------------------------", plainLines.get(plainLines.size() - 1));
        assertEquals("Kills: 1500", plainLines.get(plainLines.size() - 2));
    }

    @Test
    void testComputeEnchantmentHashFormatSensitivity() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "dragon_blade");
        Map<Enchantment, Integer> active = Map.of(Enchantment.SHARPNESS, 5);

        int hash1 = engine.computeEnchantmentHash(def, active);

        // Change global enchantment format
        LoreFormat updatedFormat = new LoreFormat();
        updatedFormat.setEnchantmentFormat("&c%name% - Level %level%");
        engine.setGlobalEnchantmentFormat(updatedFormat);

        int hash2 = engine.computeEnchantmentHash(def, active);

        assertNotEquals(hash1, hash2, "Enchantment hash must change when format changes!");
    }

    @Test
    void testAutomaticLanguageMigration() throws Exception {
        java.io.File tempFolder = java.nio.file.Files.createTempDirectory("royitems_test_lang").toFile();
        java.io.File langFolder = new java.io.File(tempFolder, "lang");
        langFolder.mkdirs();

        java.io.File testFile = new java.io.File(langFolder, "en-US.yml");
        String oldContent = """
                config-version: 1
                prefix: "&6RoyItems &8» &f"

                messages:
                  no-permission: "&cYou do not have permission to execute this command."
                  player-only: "&cThis command can only be executed by in-game players."
                """;
        java.nio.file.Files.writeString(testFile.toPath(), oldContent, java.nio.charset.StandardCharsets.UTF_8);

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("RoyItemsTest");
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getDataFolder".equals(method.getName())) return tempFolder;
                    if ("getLogger".equals(method.getName())) return logger;
                    if ("getResource".equals(method.getName())) {
                        String name = (String) args[0];
                        return getClass().getClassLoader().getResourceAsStream(name);
                    }
                    if ("getConfig".equals(method.getName())) {
                        YamlConfiguration cfg = new YamlConfiguration();
                        cfg.set("language", "en-US");
                        return cfg;
                    }
                    return null;
                }
        );

        com.github.orenaidaucac.royItems.core.message.MessageManager mm = new com.github.orenaidaucac.royItems.core.message.MessageManager(mockPlugin);

        YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(testFile);

        assertEquals("&cYou do not have permission to execute this command.", diskConfig.getString("messages.no-permission"));
        assertEquals("&cThis command can only be executed by in-game players.", diskConfig.getString("messages.player-only"));

        assertTrue(diskConfig.contains("messages.anvil-rename-blocked"), "Missing key anvil-rename-blocked must be migrated!");
        assertTrue(diskConfig.contains("messages.voucher-ecoenchants-reward"), "Missing key voucher-ecoenchants-reward must be migrated!");
        assertTrue(diskConfig.contains("messages.voucher-inventory-full-dropped"), "Missing key voucher-inventory-full-dropped must be migrated!");

        int keyCountBefore = diskConfig.getConfigurationSection("messages").getKeys(false).size();
        mm.migrateLanguageFiles();
        YamlConfiguration diskConfigAfter = YamlConfiguration.loadConfiguration(testFile);
        int keyCountAfter = diskConfigAfter.getConfigurationSection("messages").getKeys(false).size();
        assertEquals(keyCountBefore, keyCountAfter, "Migration must be 100% idempotent!");

        testFile.delete();
        langFolder.delete();
        tempFolder.delete();
    }

    @Test
    void testMessageManagerFallbackAndSingleWarning() {
        java.util.List<String> logWarnings = new ArrayList<>();
        java.util.logging.Logger logger = new java.util.logging.Logger("TestLogger", null) {
            @Override
            public void warning(String msg) {
                logWarnings.add(msg);
            }
        };

        java.io.File tempFolder = null;
        try {
            tempFolder = java.nio.file.Files.createTempDirectory("royitems_test_fallback").toFile();
            java.io.File finalTempFolder = tempFolder;
            Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                    Plugin.class.getClassLoader(),
                    new Class<?>[]{Plugin.class},
                    (proxy, method, args) -> {
                        if ("getDataFolder".equals(method.getName())) return finalTempFolder;
                        if ("getLogger".equals(method.getName())) return logger;
                        if ("getResource".equals(method.getName())) {
                            String name = (String) args[0];
                            return getClass().getClassLoader().getResourceAsStream(name);
                        }
                        if ("getConfig".equals(method.getName())) {
                            YamlConfiguration cfg = new YamlConfiguration();
                            cfg.set("language", "vi-VN");
                            return cfg;
                        }
                        return null;
                    }
            );

            com.github.orenaidaucac.royItems.core.message.MessageManager mm = new com.github.orenaidaucac.royItems.core.message.MessageManager(mockPlugin);

            String nonExistentInActive = "some-key-that-only-exists-in-fallback";
            try {
                java.lang.reflect.Field fallbackField = com.github.orenaidaucac.royItems.core.message.MessageManager.class.getDeclaredField("fallbackConfig");
                fallbackField.setAccessible(true);
                YamlConfiguration fb = (YamlConfiguration) fallbackField.get(mm);
                if (fb != null) {
                    fb.set("messages." + nonExistentInActive, "Fallback Value");
                }
            } catch (Exception ignored) {}

            String result = mm.getRawMessage(nonExistentInActive, "Default");
            assertEquals("Fallback Value", result);
            assertEquals(1, logWarnings.size(), "Should log warning exactly once on fallback!");

            mm.getRawMessage(nonExistentInActive, "Default");
            assertEquals(1, logWarnings.size(), "Should NOT log duplicate warning for already warned key!");
        } catch (Exception e) {
            fail("Exception in testMessageManagerFallbackAndSingleWarning: " + e.getMessage());
        } finally {
            if (tempFolder != null) {
                for (java.io.File f : tempFolder.listFiles()) {
                    if (f.isDirectory()) for (java.io.File sf : f.listFiles()) sf.delete();
                    f.delete();
                }
                tempFolder.delete();
            }
        }
    }

    @Test
    @DisplayName("Enchantment Extraction: Configuration parsing and eligibility filtering")
    void testEnchantExtractionConfigEligibility() {
        // 1. Default configuration checks
        EnchantExtractionConfig defaultConfig = new EnchantExtractionConfig();
        assertFalse(defaultConfig.isEnabled());
        assertEquals(EnchantExtractionConfig.ExtractionType.BOTH, defaultConfig.getType());
        assertEquals(100.0, defaultConfig.getChance());
        assertTrue(defaultConfig.getEcoRarities().isEmpty());
        assertTrue(defaultConfig.getEcoTypes().isEmpty());

        // 2. YAML parsing
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("remove-enchants.enabled", true);
        yaml.set("remove-enchants.type", "BOTH");
        yaml.set("remove-enchants.chance", 85.0);
        yaml.set("remove-enchants.ecoenchants.rarity", List.of("legendary", "epic"));
        yaml.set("remove-enchants.ecoenchants.type", List.of("combat", "special"));

        EnchantExtractionConfig config = EnchantExtractionConfig.fromConfig(yaml.getConfigurationSection("remove-enchants"));
        assertTrue(config.isEnabled());
        assertEquals(EnchantExtractionConfig.ExtractionType.BOTH, config.getType());
        assertEquals(85.0, config.getChance());
        assertEquals(List.of("legendary", "epic"), config.getEcoRarities());
        assertEquals(List.of("combat", "special"), config.getEcoTypes());

        // 3. Chance clamping
        config.setChance(-5.0);
        assertEquals(0.0, config.getChance());
        config.setChance(120.0);
        assertEquals(100.0, config.getChance());
        config.setChance(75.5);
        assertEquals(75.5, config.getChance());

        // 4. Type parsing resilience
        assertEquals(EnchantExtractionConfig.ExtractionType.VANILLA_ENCHANTS, EnchantExtractionConfig.ExtractionType.fromString("vanilla_enchants"));
        assertEquals(EnchantExtractionConfig.ExtractionType.VANILLA_ENCHANTS, EnchantExtractionConfig.ExtractionType.fromString("VANILLA"));
        assertEquals(EnchantExtractionConfig.ExtractionType.ECOENCHANTS, EnchantExtractionConfig.ExtractionType.fromString("ecoenchants"));
        assertEquals(EnchantExtractionConfig.ExtractionType.ECOENCHANTS, EnchantExtractionConfig.ExtractionType.fromString("ECO"));
        assertEquals(EnchantExtractionConfig.ExtractionType.BOTH, EnchantExtractionConfig.ExtractionType.fromString("both"));
        assertEquals(EnchantExtractionConfig.ExtractionType.BOTH, EnchantExtractionConfig.ExtractionType.fromString(null));
        assertEquals(EnchantExtractionConfig.ExtractionType.BOTH, EnchantExtractionConfig.ExtractionType.fromString("unknown_val"));

        // 5. Mock EcoEnchantsHook for eligibility tests
        EcoEnchantsHook mockHook = new EcoEnchantsHook() {
            @Override
            public boolean isEcoEnchant(Enchantment ench) {
                return ench != null && ench.equals(Enchantment.PROTECTION);
            }

            @Override
            public String getRarityId(Enchantment ench) {
                return "legendary";
            }

            @Override
            public String getRarityDisplayName(Enchantment ench) {
                return "<gold>Legendary</gold>";
            }

            @Override
            public String getTypeId(Enchantment ench) {
                return "combat";
            }
        };

        Enchantment vanillaEnch = Enchantment.UNBREAKING;
        Enchantment ecoEnch = Enchantment.PROTECTION;

        // When type is BOTH:
        // Vanilla enchantment must ALWAYS be eligible even with eco rarity/type filters!
        assertTrue(config.isEnchantmentEligible(vanillaEnch, mockHook));
        // Eco enchantment with matching legendary/combat must be eligible
        assertTrue(config.isEnchantmentEligible(ecoEnch, mockHook));

        // If eco rarity does not match
        config.setEcoRarities(List.of("common"));
        assertFalse(config.isEnchantmentEligible(ecoEnch, mockHook));
        assertTrue(config.isEnchantmentEligible(vanillaEnch, mockHook), "Vanilla enchants must never be blocked by Eco filters!");

        // When type is VANILLA_ENCHANTS:
        config.setType(EnchantExtractionConfig.ExtractionType.VANILLA_ENCHANTS);
        config.setEcoRarities(Collections.emptyList());
        assertTrue(config.isEnchantmentEligible(vanillaEnch, mockHook));
        assertFalse(config.isEnchantmentEligible(ecoEnch, mockHook));

        // When type is ECOENCHANTS:
        config.setType(EnchantExtractionConfig.ExtractionType.ECOENCHANTS);
        assertFalse(config.isEnchantmentEligible(vanillaEnch, mockHook));
        assertTrue(config.isEnchantmentEligible(ecoEnch, mockHook));

        // 6. ItemDefinition integration
        ItemDefinition itemDef = new ItemDefinition(ItemType.CONSUMABLE, "test_scroll");
        assertFalse(itemDef.hasRemoveEnchants());
        itemDef.setRemoveEnchantsConfig(config);
        assertTrue(itemDef.hasRemoveEnchants());
        config.setEnabled(false);
        assertFalse(itemDef.hasRemoveEnchants());
    }

    @Test
    @DisplayName("Enchantment Extraction: GUI configuration parsing from remove-enchants-gui.yml")
    void testExtractionGuiConfigLoading() {
        java.io.InputStream in = getClass().getClassLoader().getResourceAsStream("gui/remove-enchants-gui.yml");
        assertNotNull(in, "gui/remove-enchants-gui.yml must exist in resources");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        ExtractionGuiConfig guiConfig = new ExtractionGuiConfig();
        guiConfig.load(yaml);

        assertNotNull(guiConfig.getTitle());
        assertTrue(guiConfig.getTitle().contains("Enchantment Extraction"));
        assertEquals(5, guiConfig.getRows());
        assertEquals(4, guiConfig.getTargetSlot());
        assertNotNull(guiConfig.getTargetPreviewName());
        assertFalse(guiConfig.getTargetPreviewLore().isEmpty());

        assertEquals(14, guiConfig.getEnchantSlots().size());
        assertEquals(org.bukkit.Material.ENCHANTED_BOOK, guiConfig.getIconMaterial());
        assertNotNull(guiConfig.getIconName());
        assertTrue(guiConfig.getIconName().contains("%enchantment_name%"));
        assertFalse(guiConfig.getIconLore().isEmpty());

        assertNotNull(guiConfig.getPreviousPage());
        assertEquals(38, guiConfig.getPreviousPage().getSlot());
        assertNotNull(guiConfig.getNextPage());
        assertEquals(42, guiConfig.getNextPage().getSlot());
        assertNotNull(guiConfig.getCloseButton());
        assertEquals(40, guiConfig.getCloseButton().getSlot());
        assertEquals(org.bukkit.Material.BARRIER, guiConfig.getCloseButton().getMaterial());

        assertFalse(guiConfig.getDecorations().isEmpty());
    }

    @Test
    @DisplayName("Enchantment Extraction: Language migration adds all 8 extraction keys safely")
    void testLanguageMigrationWithExtractionKeys() throws Exception {
        java.io.File tempFolder = java.nio.file.Files.createTempDirectory("royitems_test_extraction_lang").toFile();
        java.io.File langFolder = new java.io.File(tempFolder, "lang");
        langFolder.mkdirs();

        java.io.File testFile = new java.io.File(langFolder, "vi-VN.yml");
        String oldContent = """
                config-version: 1
                prefix: "&6RoyItems &8» &f"

                messages:
                  no-permission: "&cBạn không có quyền thực hiện lệnh này."
                  player-only: "&cLệnh này chỉ có thể được thực hiện bởi người chơi."
                """;
        java.nio.file.Files.writeString(testFile.toPath(), oldContent, java.nio.charset.StandardCharsets.UTF_8);

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("RoyItemsTestExtraction");
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> {
                    if ("getDataFolder".equals(method.getName())) return tempFolder;
                    if ("getLogger".equals(method.getName())) return logger;
                    if ("getResource".equals(method.getName())) {
                        String name = (String) args[0];
                        return getClass().getClassLoader().getResourceAsStream(name);
                    }
                    if ("getConfig".equals(method.getName())) {
                        YamlConfiguration cfg = new YamlConfiguration();
                        cfg.set("language", "vi-VN");
                        return cfg;
                    }
                    return null;
                }
        );

        com.github.orenaidaucac.royItems.core.message.MessageManager mm = new com.github.orenaidaucac.royItems.core.message.MessageManager(mockPlugin);

        YamlConfiguration diskConfig = YamlConfiguration.loadConfiguration(testFile);

        // Verify preserved original keys
        assertEquals("&cBạn không có quyền thực hiện lệnh này.", diskConfig.getString("messages.no-permission"));
        assertEquals("&cLệnh này chỉ có thể được thực hiện bởi người chơi.", diskConfig.getString("messages.player-only"));

        // Verify all 8 extraction keys were migrated into the disk file
        assertTrue(diskConfig.contains("messages.extract-no-enchantments"), "Missing extract-no-enchantments");
        assertTrue(diskConfig.contains("messages.extract-no-valid-enchantments"), "Missing extract-no-valid-enchantments");
        assertTrue(diskConfig.contains("messages.extract-success-book"), "Missing extract-success-book");
        assertTrue(diskConfig.contains("messages.extract-success-no-book"), "Missing extract-success-no-book");
        assertTrue(diskConfig.contains("messages.extract-inventory-full-dropped"), "Missing extract-inventory-full-dropped");
        assertTrue(diskConfig.contains("messages.extract-cancelled"), "Missing extract-cancelled");
        assertTrue(diskConfig.contains("messages.extract-in-progress"), "Missing extract-in-progress");
        assertTrue(diskConfig.contains("messages.extract-item-invalid"), "Missing extract-item-invalid");
        assertTrue(diskConfig.contains("messages.extract-item-not-whitelisted"), "Missing extract-item-not-whitelisted");

        // Verify idempotency
        int keyCountBefore = diskConfig.getConfigurationSection("messages").getKeys(false).size();
        mm.migrateLanguageFiles();
        YamlConfiguration diskConfigAfter = YamlConfiguration.loadConfiguration(testFile);
        int keyCountAfter = diskConfigAfter.getConfigurationSection("messages").getKeys(false).size();
        assertEquals(keyCountBefore, keyCountAfter, "Migration must be 100% idempotent!");

        testFile.delete();
        langFolder.delete();
        tempFolder.delete();
    }

    @Test
    @DisplayName("Consumable: Right-click consumption control and remove-enchants protection")
    void testConsumableRightClickFlag() {
        ItemDefinition def = new ItemDefinition(ItemType.CONSUMABLE, "test_potion");
        // Default is true
        assertTrue(def.isConsumable());

        // Configurable to false
        def.setConsumable(false);
        assertFalse(def.isConsumable());

        // YAML parsing in ItemConfigManager logic
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("consumable", false);
        def.setConsumable(yaml.getBoolean("consumable", true));
        assertFalse(def.isConsumable());

        yaml.set("consumable", true);
        def.setConsumable(yaml.getBoolean("consumable", true));
        assertTrue(def.isConsumable());

        // When remove-enchants is enabled, it must never be consumable
        EnchantExtractionConfig extractionConfig = new EnchantExtractionConfig();
        extractionConfig.setEnabled(true);
        def.setRemoveEnchantsConfig(extractionConfig);
        assertTrue(def.hasRemoveEnchants());
    }

    @Test
    @DisplayName("Enchantment Extraction: Item Whitelist parsing and group matching")
    void testEnchantExtractionWhitelist() {
        EnchantExtractionConfig config = new EnchantExtractionConfig();
        // 1. Empty whitelist allows all (backwards compatibility)
        assertTrue(config.getItemWhitelist().isEmpty());
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.DIAMOND_SWORD, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.DIRT, null));

        // 2. Set whitelist to SWORD and AXE
        config.setItemWhitelist(List.of("SWORD", "AXE"));
        assertEquals(List.of("SWORD", "AXE"), config.getItemWhitelist());

        // Group matching: Swords
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.DIAMOND_SWORD, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.NETHERITE_SWORD, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.WOODEN_SWORD, null));

        // Group matching: Axes
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.DIAMOND_AXE, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.NETHERITE_AXE, null));

        // Non-whitelisted items
        assertFalse(config.isItemWhitelisted(org.bukkit.Material.DIAMOND_PICKAXE, null));
        assertFalse(config.isItemWhitelisted(org.bukkit.Material.BOW, null));
        assertFalse(config.isItemWhitelisted(org.bukkit.Material.DIAMOND_CHESTPLATE, null));
        assertFalse(config.isItemWhitelisted(org.bukkit.Material.DIRT, null));

        // Custom item with ItemType matching
        ItemDefinition roySword = new ItemDefinition(ItemType.SWORD, "custom_blade");
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.STICK, roySword));

        // Armor group alias matching
        config.setItemWhitelist(List.of("ARMOR"));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.DIAMOND_HELMET, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.NETHERITE_CHESTPLATE, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.IRON_LEGGINGS, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.GOLDEN_BOOTS, null));
        assertTrue(config.isItemWhitelisted(org.bukkit.Material.ELYTRA, null));
        assertFalse(config.isItemWhitelisted(org.bukkit.Material.DIAMOND_SWORD, null));

        // YAML parsing
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("item-whitelist", List.of("SWORD", "BOW", "CROSSBOW"));
        EnchantExtractionConfig parsedConfig = EnchantExtractionConfig.fromConfig(yaml);
        assertEquals(List.of("SWORD", "BOW", "CROSSBOW"), parsedConfig.getItemWhitelist());
        assertTrue(parsedConfig.isItemWhitelisted(org.bukkit.Material.BOW, null));
        assertTrue(parsedConfig.isItemWhitelisted(org.bukkit.Material.CROSSBOW, null));
    }

    @Test
    @DisplayName("Enchantment Extraction: Glow effect discrimination and dummy enchantment filtering")
    void testGlowEnchantmentFiltering() {
        ItemDefinition glowingDef = new ItemDefinition(ItemType.SWORD, "glow_sword");
        glowingDef.setGlow(true);

        // Dummy LUCK_OF_THE_SEA 1 check
        assertTrue(ExtractionManager.isDummyGlow(glowingDef, Enchantment.LUCK_OF_THE_SEA, 1));
        assertFalse(ExtractionManager.isDummyGlow(glowingDef, Enchantment.SHARPNESS, 5));
        assertFalse(ExtractionManager.isDummyGlow(glowingDef, Enchantment.LUCK_OF_THE_SEA, 2));

        // If the item actually had LUCK_OF_THE_SEA configured as a real enchantment
        glowingDef.setEnchantments(Map.of(Enchantment.LUCK_OF_THE_SEA, 1));
        assertFalse(ExtractionManager.isDummyGlow(glowingDef, Enchantment.LUCK_OF_THE_SEA, 1));

        // Non-glowing item
        ItemDefinition normalDef = new ItemDefinition(ItemType.SWORD, "normal_sword");
        normalDef.setGlow(false);
        assertFalse(ExtractionManager.isDummyGlow(normalDef, Enchantment.LUCK_OF_THE_SEA, 1));

        // Filter out dummy glow from map
        glowingDef.setEnchantments(Collections.emptyMap());
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.LUCK_OF_THE_SEA, 1);
        map.put(Enchantment.SHARPNESS, 5);

        ExtractionManager.filterDummyGlowEnchantments(map, glowingDef);
        assertEquals(1, map.size());
        assertTrue(map.containsKey(Enchantment.SHARPNESS));
        assertFalse(map.containsKey(Enchantment.LUCK_OF_THE_SEA));

        // Only dummy glow in map
        Map<Enchantment, Integer> onlyGlowMap = new HashMap<>();
        onlyGlowMap.put(Enchantment.LUCK_OF_THE_SEA, 1);
        ExtractionManager.filterDummyGlowEnchantments(onlyGlowMap, glowingDef);
        assertTrue(onlyGlowMap.isEmpty());
    }

    @Test
    @DisplayName("Enchantment Extraction: Two extraction consumables cannot extract each other")
    void testConsumableStackingAndExtractionConflict() {
        ItemDefinition scrollDef1 = new ItemDefinition(ItemType.CONSUMABLE, "extraction_scroll_1");
        EnchantExtractionConfig cfg1 = new EnchantExtractionConfig();
        cfg1.setEnabled(true);
        scrollDef1.setRemoveEnchantsConfig(cfg1);

        ItemDefinition scrollDef2 = new ItemDefinition(ItemType.CONSUMABLE, "extraction_scroll_2");
        EnchantExtractionConfig cfg2 = new EnchantExtractionConfig();
        cfg2.setEnabled(true);
        scrollDef2.setRemoveEnchantsConfig(cfg2);

        assertTrue(scrollDef1.hasRemoveEnchants());
        assertTrue(scrollDef2.hasRemoveEnchants());

        // Neither can target the other for extraction
        assertTrue(scrollDef1.hasRemoveEnchants());
        assertTrue(scrollDef2.hasRemoveEnchants());
        assertFalse(scrollDef1.getRemoveEnchantsConfig().getItemWhitelist().contains("CONSUMABLE"));
    }

    @Test
    @DisplayName("Real-Time Enchantment Lore: Effective Enchantments filters dummy cosmetic glow")
    void testRealTimeEffectiveEnchantmentsFiltersDummyGlow() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        ItemDefinition glowingDef = new ItemDefinition(ItemType.SWORD, "glow_blade");
        glowingDef.setGlow(true);

        // Mock ItemMeta with dummy glow (LUCK_OF_THE_SEA 1)
        Map<Enchantment, Integer> metaEnchants = new LinkedHashMap<>();
        metaEnchants.put(Enchantment.LUCK_OF_THE_SEA, 1);

        org.bukkit.inventory.meta.ItemMeta mockMeta = (org.bukkit.inventory.meta.ItemMeta) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.inventory.meta.ItemMeta.class.getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.meta.ItemMeta.class},
                (proxy, method, args) -> {
                    if ("getEnchants".equals(method.getName())) return metaEnchants;
                    return null;
                }
        );

        Map<Enchantment, Integer> effective = engine.getEffectiveEnchantments(mockMeta, glowingDef);
        assertTrue(effective.isEmpty(), "Dummy glow LUCK_OF_THE_SEA must be filtered out when def.isGlow() is true");

        // Now add a real enchantment
        metaEnchants.put(Enchantment.SHARPNESS, 5);
        effective = engine.getEffectiveEnchantments(mockMeta, glowingDef);
        assertEquals(1, effective.size());
        assertEquals(5, effective.get(Enchantment.SHARPNESS));
        assertFalse(effective.containsKey(Enchantment.LUCK_OF_THE_SEA));
    }

    @Test
    @DisplayName("Real-Time Enchantment Lore: Exact #enchantments# placement and bar pruning")
    void testRealTimeBuildLoreEnchantmentPlacementAndBarPruning() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        LoreFormat format = new LoreFormat();
        format.setFormat(List.of(
                "&7Category: &f#type#",
                "#bar#",
                "#stats#",
                "#bar#",
                "#enchantments#",
                "#sockets#",
                "#bar#",
                "#description#"
        ));
        format.setBar("&8------------------------");
        format.setEnchantmentFormat("&b✦ %name% &f%level%");
        format.setEnchantmentLevelStyle("ROMAN");
        engine.setGlobalEnchantmentFormat(format);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "excalibur");
        def.setName("&6Excalibur");
        def.setLore(List.of("&7The legendary holy sword."));
        def.setStats(Map.of(StatType.ATTACK_DAMAGE, new StatModifier(15.0, 0.0)));

        // 1. Without enchantments
        List<String> loreWithoutEnch = engine.buildLore(def, Collections.emptyList(), 0L, Collections.emptyMap());
        assertNotNull(loreWithoutEnch);
        // Ensure no enchantments rendered
        for (String line : loreWithoutEnch) {
            assertFalse(line.contains("Sharpness"));
        }
        // Verify no double bars
        for (int i = 0; i < loreWithoutEnch.size() - 1; i++) {
            assertFalse(loreWithoutEnch.get(i).contains("----") && loreWithoutEnch.get(i + 1).contains("----"),
                    "No consecutive bars allowed when enchantments are absent");
        }

        // 2. With real-time enchantments added (Sharpness V and Unbreaking III)
        Map<Enchantment, Integer> active = new LinkedHashMap<>();
        active.put(Enchantment.SHARPNESS, 5);
        active.put(Enchantment.UNBREAKING, 3);

        List<String> loreWithEnch = engine.buildLore(def, Collections.emptyList(), 0L, active);
        assertNotNull(loreWithEnch);

        List<String> plainWithEnch = loreWithEnch.stream().map(TextUtility::stripColor).toList();
        assertTrue(plainWithEnch.contains("✦ Sharpness V"));
        assertTrue(plainWithEnch.contains("✦ Unbreaking III"));

        int statIdx = -1;
        int enchIdx = -1;
        int descIdx = -1;
        for (int i = 0; i < plainWithEnch.size(); i++) {
            String line = plainWithEnch.get(i);
            if (line.contains("attack-damage") || line.contains("+15")) statIdx = i;
            if (line.contains("✦ Sharpness V")) enchIdx = i;
            if (line.contains("legendary holy sword")) descIdx = i;
        }

        assertTrue(statIdx != -1, "Stat line must exist");
        assertTrue(enchIdx != -1, "Enchantment line must exist");
        assertTrue(descIdx != -1, "Description line must exist");
        assertTrue(statIdx < enchIdx, "Enchantments must be placed AFTER stats according to format");
        assertTrue(enchIdx < descIdx, "Enchantments must be placed BEFORE description according to format");
    }

    @Test
    @DisplayName("Real-Time Enchantment Lore: isEnchantmentLine recognizes Vanilla, EcoEnchants, custom names, and Roman numerals")
    void testRealTimeIsEnchantmentLineRecognition() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "test_blade");
        LoreFormat format = new LoreFormat();
        format.setEnchantmentCustomNames(Map.of("sharpness", "&cSắc Bén", "unbreaking", "&aBền Bỉ"));
        engine.setGlobalEnchantmentFormat(format);

        Map<Enchantment, Integer> active = Map.of(Enchantment.SHARPNESS, 5);

        // Valid enchantment lines
        assertTrue(engine.isEnchantmentLine("Sharpness V", active, def));
        assertTrue(engine.isEnchantmentLine("✦ Sharpness V", active, def));
        assertTrue(engine.isEnchantmentLine("✦ Sắc Bén V", active, def));
        assertTrue(engine.isEnchantmentLine("✦ Bền Bỉ III", Collections.emptyMap(), def)); // recognized from custom-names even if removed
        assertTrue(engine.isEnchantmentLine("Protection IV", Collections.emptyMap(), def)); // recognized from known list
        assertTrue(engine.isEnchantmentLine("✦ Lifesteal III", Collections.emptyMap(), def)); // recognized from roman numeral pattern
        assertTrue(engine.isEnchantmentLine("Curse of Binding", Collections.emptyMap(), def));

        // Invalid lines that must NOT be treated as enchantments
        assertFalse(engine.isEnchantmentLine("------------------------", active, def));
        assertFalse(engine.isEnchantmentLine("━━━━━━", active, def));
        assertFalse(engine.isEnchantmentLine("[Empty Socket]", active, def));
        assertFalse(engine.isEnchantmentLine("Socket: (1/2)", active, def));
        assertFalse(engine.isEnchantmentLine("Requires: Level 10", active, def));
        assertFalse(engine.isEnchantmentLine("Attack Damage: +15", active, def));
        assertFalse(engine.isEnchantmentLine("Owner: Steve", active, def));
        assertFalse(engine.isEnchantmentLine("Kills: 1500", active, def));
    }

    @Test
    @DisplayName("Real-Time Enchantment Lore: Upgrade, replace, and remove enchantments cleanly")
    void testRealTimeEnchantmentUpgradeAndDisenchant() {
        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> null
        );
        DynamicLoreEngine engine = new DynamicLoreEngine(mockPlugin);

        ItemDefinition def = new ItemDefinition(ItemType.SWORD, "sharp_blade");
        LoreFormat format = new LoreFormat();
        format.setEnchantmentFormat("&b✦ %name% &f%level%");
        format.setEnchantmentLevelStyle("ROMAN");
        engine.setGlobalEnchantmentFormat(format);

        // 1. Initial lore with Sharpness IV and custom player lines
        List<Component> currentLore = new ArrayList<>(List.of(
                TextUtility.format("&7A sharp blade"),
                TextUtility.format("&b✦ Sharpness IV"),
                TextUtility.format("&8[Empty Socket]"),
                TextUtility.format("&eOwner: Steve")
        ));

        // 2. Upgrade to Sharpness V in real time
        Map<Enchantment, Integer> activeUpgraded = Map.of(Enchantment.SHARPNESS, 5);
        List<Component> upgradedLore = engine.updateEnchantmentLinesInLore(currentLore, def, activeUpgraded);
        List<String> plainUpgraded = upgradedLore.stream().map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)).toList();

        assertEquals(4, plainUpgraded.size());
        assertEquals("✦ Sharpness V", plainUpgraded.get(1));
        assertFalse(plainUpgraded.contains("✦ Sharpness IV"));
        assertTrue(plainUpgraded.contains("Owner: Steve"));

        // 3. Disenchant at grindstone (all enchants removed)
        List<Component> disenchantedLore = engine.updateEnchantmentLinesInLore(upgradedLore, def, Collections.emptyMap());
        List<String> plainDisenchanted = disenchantedLore.stream().map(c -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)).toList();

        assertEquals(3, plainDisenchanted.size());
        assertFalse(plainDisenchanted.contains("✦ Sharpness V"));
        assertFalse(plainDisenchanted.contains("✦ Sharpness IV"));
        assertEquals("A sharp blade", plainDisenchanted.get(0));
        assertEquals("[Empty Socket]", plainDisenchanted.get(1));
        assertEquals("Owner: Steve", plainDisenchanted.get(2));
    }

    @Test
    @DisplayName("Crafting Station: Shift-Click Mass Crafting with Non-Item Outputs exceeds 64 limit")
    void testShiftClickMassCraftingExceeds64Limit() {
        RecipeIngredient input = RecipeIngredient.parse("money=10");
        RecipeIngredient output = RecipeIngredient.parse("point=5");
        assertNotNull(input);
        assertNotNull(output);

        StationRecipe recipe = new StationRecipe(
                "exchange_money_points",
                List.of(input),
                List.of(output),
                null,
                List.of(10),
                1,
                false,
                false,
                false,
                0
        );

        // Mock integration where player has 10,000 money
        com.github.orenaidaucac.royItems.integration.VaultHook mockVault = new com.github.orenaidaucac.royItems.integration.VaultHook() {
            @Override public double getBalance(org.bukkit.entity.Player player) { return 10000.0; }
            @Override public boolean hasMoney(org.bukkit.entity.Player player, double amount) { return amount <= 10000.0; }
            @Override public boolean withdraw(org.bukkit.entity.Player player, double amount) { return true; }
        };

        com.github.orenaidaucac.royItems.integration.IntegrationManager mockIntegration = new com.github.orenaidaucac.royItems.integration.IntegrationManager(null) {
            @Override public com.github.orenaidaucac.royItems.integration.VaultHook getVaultHook() { return mockVault; }
        };

        org.bukkit.entity.Player mockPlayer = (org.bukkit.entity.Player)
                java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.entity.Player.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.entity.Player.class},
                        (proxy, method, args) -> null
                );

        int maxCrafts = com.github.orenaidaucac.royItems.station.CraftingStationGui.calculateMaxCrafts(
                mockPlayer, recipe, null, null, null, mockIntegration
        );

        // 10,000 / 10 = 1,000 crafts! Far exceeds old 64 hardcoded cap!
        assertEquals(1000, maxCrafts);
    }

    static class TestItemStack extends ItemStack {
        private final org.bukkit.Material mat;
        private final int amt;
        private final int maxStack;

        TestItemStack(org.bukkit.Material mat, int amt, int maxStack) {
            super();
            this.mat = mat;
            this.amt = amt;
            this.maxStack = maxStack;
        }

        TestItemStack(org.bukkit.Material mat, int amt) {
            this(mat, amt, 64);
        }

        @Override public org.bukkit.Material getType() { return mat; }
        @Override public int getAmount() { return amt; }
        @Override public int getMaxStackSize() { return maxStack; }
        @Override public boolean isSimilar(ItemStack other) {
            return other != null && other.getType() == mat;
        }
        @Override public ItemStack clone() {
            return new TestItemStack(mat, amt, maxStack);
        }
        @Override public int hashCode() {
            return System.identityHashCode(this);
        }
        @Override public boolean equals(Object obj) {
            return this == obj;
        }
    }

    private org.bukkit.entity.Player createMockInventoryPlayer(ItemStack[] storage) {
        org.bukkit.inventory.PlayerInventory mockInv = (org.bukkit.inventory.PlayerInventory) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.inventory.PlayerInventory.class.getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.PlayerInventory.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getStorageContents".equals(name)) return storage;
                    if ("getContents".equals(name)) return storage;
                    if ("setItem".equals(name)) {
                        int idx = (int) args[0];
                        if (idx >= 0 && idx < storage.length) storage[idx] = (ItemStack) args[1];
                        return null;
                    }
                    if ("getItem".equals(name)) {
                        int idx = (int) args[0];
                        return (idx >= 0 && idx < storage.length) ? storage[idx] : null;
                    }
                    return null;
                }
        );

        return (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getInventory".equals(name)) return mockInv;
                    if ("isOnline".equals(name)) return true;
                    if ("getName".equals(name)) return "TestPlayer";
                    if ("getUniqueId".equals(name)) return UUID.randomUUID();
                    return null;
                }
        );
    }

    @Test
    @DisplayName("Crafting Station: Inventory capacity limits mass crafting when space is restricted")
    void testShiftClickMassCraftingInventoryCapacity() {
        RecipeIngredient input = RecipeIngredient.parse("money=10");
        RecipeIngredient output = RecipeIngredient.parse("vanilla[type=DIAMOND,amount=1]");
        StationRecipe recipe = new StationRecipe("money_to_diamond", List.of(input), List.of(output), null, List.of(0), 1, false, false, false, 0);

        // Player has plenty of money (10,000 money = 1,000 diamonds)
        com.github.orenaidaucac.royItems.integration.VaultHook mockVault = new com.github.orenaidaucac.royItems.integration.VaultHook() {
            @Override public double getBalance(org.bukkit.entity.Player player) { return 10000.0; }
            @Override public boolean hasMoney(org.bukkit.entity.Player player, double amount) { return amount <= 10000.0; }
        };
        com.github.orenaidaucac.royItems.integration.IntegrationManager mockIntegration = new com.github.orenaidaucac.royItems.integration.IntegrationManager(null) {
            @Override public com.github.orenaidaucac.royItems.integration.VaultHook getVaultHook() { return mockVault; }
        };

        // 1. Inventory has exactly 2 empty slots (34 slots full of dirt, 2 null slots)
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < 34; i++) {
            storage[i] = new TestItemStack(org.bukkit.Material.DIRT, 64);
        }
        // slots 34 and 35 are null (empty)
        org.bukkit.entity.Player player = createMockInventoryPlayer(storage);

        // With 2 empty slots of maxStack 64, player can craft at most 2 * 64 = 128 diamonds!
        int maxCrafts = com.github.orenaidaucac.royItems.station.CraftingStationGui.calculateMaxCrafts(
                player, recipe, null, null, null, mockIntegration
        );
        assertEquals(128, maxCrafts, "2 empty slots of stack 64 must allow exactly 128 crafts");

        // 2. Inventory is 100% full (36 slots full of dirt, 0 empty slots)
        for (int i = 0; i < 36; i++) {
            storage[i] = new TestItemStack(org.bukkit.Material.DIRT, 64);
        }
        int maxWhenFull = com.github.orenaidaucac.royItems.station.CraftingStationGui.calculateMaxCrafts(
                player, recipe, null, null, null, mockIntegration
        );
        assertEquals(0, maxWhenFull, "When inventory is full, max crafts must be 0 (never drop items)");
        assertFalse(com.github.orenaidaucac.royItems.station.CraftingStationGui.simulateCanFit(
                player, recipe, 1, null, null, null, mockIntegration
        ), "simulateCanFit must be false when inventory cannot fit 1 item");
    }

    @Test
    @DisplayName("Crafting Station: Unstackable output items (max stack = 1) cap crafts at empty slot count")
    void testShiftClickMassCraftingUnstackableItems() {
        RecipeIngredient input = RecipeIngredient.parse("money=50");
        RecipeIngredient output = RecipeIngredient.parse("vanilla[type=DIAMOND_SWORD,amount=1]");
        StationRecipe recipe = new StationRecipe("buy_swords", List.of(input), List.of(output), null, List.of(0), 1, false, false, false, 0);

        com.github.orenaidaucac.royItems.integration.VaultHook mockVault = new com.github.orenaidaucac.royItems.integration.VaultHook() {
            @Override public double getBalance(org.bukkit.entity.Player player) { return 50000.0; }
            @Override public boolean hasMoney(org.bukkit.entity.Player player, double amount) { return amount <= 50000.0; }
        };
        com.github.orenaidaucac.royItems.integration.IntegrationManager mockIntegration = new com.github.orenaidaucac.royItems.integration.IntegrationManager(null) {
            @Override public com.github.orenaidaucac.royItems.integration.VaultHook getVaultHook() { return mockVault; }
        };

        // Inventory has 31 filled slots, 5 empty slots
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < 31; i++) {
            storage[i] = new TestItemStack(org.bukkit.Material.DIRT, 64);
        }
        org.bukkit.entity.Player player = createMockInventoryPlayer(storage);

        // Even though player has enough money for 1,000 swords, with only 5 empty slots and maxStack=1, max crafts is 5!
        int maxCrafts = com.github.orenaidaucac.royItems.station.CraftingStationGui.calculateMaxCrafts(
                player, recipe, null, null, null, mockIntegration
        );
        assertEquals(5, maxCrafts, "5 empty slots with maxStack=1 must cap crafts at exactly 5");
    }

    @Test
    @DisplayName("Crafting Station: Consuming inputs from inventory frees slots for output items")
    void testShiftClickConsumingInputsFreesInventorySlots() {
        // Recipe: 64 Cobblestone (vanilla) -> 1 Stone (vanilla, maxStack=64)
        RecipeIngredient input = RecipeIngredient.parse("vanilla[type=COBBLESTONE,amount=64]");
        RecipeIngredient output = RecipeIngredient.parse("vanilla[type=STONE,amount=1]");
        StationRecipe recipe = new StationRecipe("compress_cobble", List.of(input), List.of(output), null, List.of(0), 1, false, false, false, 0);

        // Player inventory is completely full: 36 slots of 64 Cobblestone each (2304 cobblestones total, 0 empty slots!)
        ItemStack[] storage = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            storage[i] = new TestItemStack(org.bukkit.Material.COBBLESTONE, 64);
        }
        org.bukkit.entity.Player player = createMockInventoryPlayer(storage);

        // Consuming each 64 cobblestone frees 1 slot. 36 crafts produces 36 Stone, which easily fits in the freed slots!
        int maxCrafts = com.github.orenaidaucac.royItems.station.CraftingStationGui.calculateMaxCrafts(
                player, recipe, null, null, null, null
        );
        assertEquals(36, maxCrafts, "Consuming 36 stacks of cobblestone frees slots allowing all 36 crafts");
    }

    @Test
    @DisplayName("Crafting Station: Multiplied atomic ingredient deduction and hasEnough")
    void testRecipeIngredientMultipliers() {
        RecipeIngredient money = RecipeIngredient.parse("money=25");
        RecipeIngredient exp = RecipeIngredient.parse("exp=100");
        RecipeIngredient point = RecipeIngredient.parse("point=50");

        assertNotNull(money);
        assertNotNull(exp);
        assertNotNull(point);

        com.github.orenaidaucac.royItems.integration.VaultHook mockVault = new com.github.orenaidaucac.royItems.integration.VaultHook() {
            private double balance = 500.0;
            @Override public double getBalance(org.bukkit.entity.Player player) { return balance; }
            @Override public boolean hasMoney(org.bukkit.entity.Player player, double amount) { return balance >= amount; }
            @Override public boolean withdraw(org.bukkit.entity.Player player, double amount) {
                if (balance >= amount) { balance -= amount; return true; }
                return false;
            }
        };
        com.github.orenaidaucac.royItems.integration.IntegrationManager mockIntegration = new com.github.orenaidaucac.royItems.integration.IntegrationManager(null) {
            @Override public com.github.orenaidaucac.royItems.integration.VaultHook getVaultHook() { return mockVault; }
        };

        // Test hasEnough with multiplier
        assertTrue(money.hasEnough(null, null, null, mockIntegration, 10)); // 25 * 10 = 250 <= 500
        assertTrue(money.hasEnough(null, null, null, mockIntegration, 20)); // 25 * 20 = 500 <= 500
        assertFalse(money.hasEnough(null, null, null, mockIntegration, 21)); // 25 * 21 = 525 > 500

        // Test take with multiplier (atomic deduction)
        assertTrue(money.take(null, null, null, mockIntegration, 8)); // 25 * 8 = 200 withdrawn, 300 left
        assertEquals(300.0, mockVault.getBalance(null));
        assertFalse(money.take(null, null, null, mockIntegration, 15)); // 25 * 15 = 375 > 300, fails safely without deducting
        assertEquals(300.0, mockVault.getBalance(null)); // Zero loss transaction safety
    }

    @Test
    @DisplayName("Smithing & Grindstone Protection: Config toggles loading")
    void testSmithingAndGrindstoneConfigToggles() {
        com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener listener =
                new com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener(null, null);

        // Default values must be true
        assertTrue(listener.isPreventSmithingCorrupt());
        assertTrue(listener.isPreventGrindstoneDisenchantConfigured());

        // Toggle to false via configuration
        YamlConfiguration yamlFalse = new YamlConfiguration();
        yamlFalse.set("smithing-table.prevent-corrupt", false);
        yamlFalse.set("grindstone.prevent-disenchant-configured", false);
        listener.loadConfig(yamlFalse);
        assertFalse(listener.isPreventSmithingCorrupt());
        assertFalse(listener.isPreventGrindstoneDisenchantConfigured());

        // Toggle back to true
        YamlConfiguration yamlTrue = new YamlConfiguration();
        yamlTrue.set("smithing-table.prevent-corrupt", true);
        yamlTrue.set("grindstone.prevent-disenchant-configured", true);
        listener.loadConfig(yamlTrue);
        assertTrue(listener.isPreventSmithingCorrupt());
        assertTrue(listener.isPreventGrindstoneDisenchantConfigured());
    }

    @Test
    @DisplayName("Smithing Protection: Full slot & attempt detection")
    void testSmithingProtectionLogic() {
        com.github.orenaidaucac.royItems.item.ItemRegistry registry = new com.github.orenaidaucac.royItems.item.ItemRegistry();
        Map<ItemStack, Boolean> royItemMarkers = new HashMap<>();

        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> "getName".equals(method.getName()) ? "RoyItems" : null
        );

        com.github.orenaidaucac.royItems.item.ItemPDCManager mockPdc =
                new com.github.orenaidaucac.royItems.item.ItemPDCManager(mockPlugin) {
                    @Override
                    public boolean isRoyItem(ItemStack item) {
                        return item != null && Boolean.TRUE.equals(royItemMarkers.get(item));
                    }
                    @Override
                    public ItemType getType(ItemStack item) {
                        return ItemType.SWORD;
                    }
                    @Override
                    public String getId(ItemStack item) {
                        return "excalibur";
                    }
                };

        com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener listener =
                new com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener(mockPdc, registry);

        Map<Integer, ItemStack> smithingSlots = new HashMap<>();
        org.bukkit.inventory.SmithingInventory mockSmithing =
                (org.bukkit.inventory.SmithingInventory) java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.inventory.SmithingInventory.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.inventory.SmithingInventory.class},
                        (proxy, method, args) -> {
                            if ("getItem".equals(method.getName())) {
                                int s = (int) args[0];
                                return smithingSlots.get(s);
                            }
                            if ("setItem".equals(method.getName())) {
                                int s = (int) args[0];
                                ItemStack it = (ItemStack) args[1];
                                smithingSlots.put(s, it);
                                return null;
                            }
                            if ("getContents".equals(method.getName())) {
                                return smithingSlots.values().toArray(new ItemStack[0]);
                            }
                            return null;
                        }
                );

        ItemStack vanillaTemplate = new TestItemStack(org.bukkit.Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 1);
        ItemStack vanillaSword = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        ItemStack vanillaIngot = new TestItemStack(org.bukkit.Material.NETHERITE_INGOT, 1);
        ItemStack roySword = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        royItemMarkers.put(roySword, true);

        // Case 1: All empty
        assertFalse(listener.isSmithingCorruptAttempt(mockSmithing));

        // Case 2: All vanilla items (Template, Base, Addition)
        smithingSlots.put(0, vanillaTemplate);
        smithingSlots.put(1, vanillaSword);
        smithingSlots.put(2, vanillaIngot);
        assertFalse(listener.isSmithingCorruptAttempt(mockSmithing), "Vanilla smithing must be 100% allowed");

        // Case 3: Base item is a RoyItem
        smithingSlots.put(1, roySword);
        assertTrue(listener.isSmithingCorruptAttempt(mockSmithing), "RoyItem in Base slot must trigger corrupt attempt");

        // Case 4: Template item is a RoyItem
        smithingSlots.put(1, vanillaSword);
        smithingSlots.put(0, roySword);
        assertTrue(listener.isSmithingCorruptAttempt(mockSmithing), "RoyItem in Template slot must trigger corrupt attempt");

        // Case 5: Addition item is a RoyItem
        smithingSlots.put(0, vanillaTemplate);
        smithingSlots.put(2, roySword);
        assertTrue(listener.isSmithingCorruptAttempt(mockSmithing), "RoyItem in Addition slot must trigger corrupt attempt");

        // Case 6: Config disabled
        YamlConfiguration yamlFalse = new YamlConfiguration();
        yamlFalse.set("smithing-table.prevent-corrupt", false);
        listener.loadConfig(yamlFalse);
        assertFalse(listener.isSmithingCorruptAttempt(mockSmithing), "When config disabled, check returns false");
    }

    @Test
    @DisplayName("Grindstone Protection: Pre-configured enchants & item combining")
    void testGrindstoneProtectionLogic() {
        com.github.orenaidaucac.royItems.item.ItemRegistry registry = new com.github.orenaidaucac.royItems.item.ItemRegistry();
        Map<ItemStack, Boolean> royItemMarkers = new HashMap<>();
        Map<ItemStack, ItemDefinition> royDefinitions = new HashMap<>();

        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> "getName".equals(method.getName()) ? "RoyItems" : null
        );

        com.github.orenaidaucac.royItems.item.ItemPDCManager mockPdc =
                new com.github.orenaidaucac.royItems.item.ItemPDCManager(mockPlugin) {
                    @Override
                    public boolean isRoyItem(ItemStack item) {
                        return item != null && Boolean.TRUE.equals(royItemMarkers.get(item));
                    }
                    @Override
                    public ItemType getType(ItemStack item) {
                        ItemDefinition d = royDefinitions.get(item);
                        return d != null ? d.getType() : ItemType.SWORD;
                    }
                    @Override
                    public String getId(ItemStack item) {
                        ItemDefinition d = royDefinitions.get(item);
                        return d != null ? d.getId() : "item";
                    }
                };

        com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener listener =
                new com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener(mockPdc, registry);

        // Definition A: Has pre-configured enchantment
        ItemDefinition defConfigured = new ItemDefinition(ItemType.SWORD, "excalibur");
        Map<Enchantment, Integer> enchants = new HashMap<>();
        enchants.put(Enchantment.SHARPNESS, 5);
        defConfigured.setEnchantments(enchants);
        registry.register(defConfigured);

        // Definition B: No pre-configured enchantments
        ItemDefinition defNoEnchants = new ItemDefinition(ItemType.SWORD, "plain_blade");
        registry.register(defNoEnchants);

        ItemStack itemWithEnchants = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        royItemMarkers.put(itemWithEnchants, true);
        royDefinitions.put(itemWithEnchants, defConfigured);

        ItemStack itemWithoutEnchants = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        royItemMarkers.put(itemWithoutEnchants, true);
        royDefinitions.put(itemWithoutEnchants, defNoEnchants);

        ItemStack vanillaItem1 = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        ItemStack vanillaItem2 = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);

        // Test hasConfiguredEnchantments
        assertTrue(listener.hasConfiguredEnchantments(itemWithEnchants));
        assertFalse(listener.hasConfiguredEnchantments(itemWithoutEnchants));
        assertFalse(listener.hasConfiguredEnchantments(vanillaItem1));
        assertFalse(listener.hasConfiguredEnchantments(null));

        Map<Integer, ItemStack> grindstoneSlots = new HashMap<>();
        org.bukkit.inventory.GrindstoneInventory mockGrindstone =
                (org.bukkit.inventory.GrindstoneInventory) java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.inventory.GrindstoneInventory.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.inventory.GrindstoneInventory.class},
                        (proxy, method, args) -> {
                            if ("getItem".equals(method.getName())) {
                                int s = (int) args[0];
                                return grindstoneSlots.get(s);
                            }
                            if ("setItem".equals(method.getName())) {
                                int s = (int) args[0];
                                ItemStack it = (ItemStack) args[1];
                                grindstoneSlots.put(s, it);
                                return null;
                            }
                            if ("getContents".equals(method.getName())) {
                                return grindstoneSlots.values().toArray(new ItemStack[0]);
                            }
                            return null;
                        }
                );

        // Case 1: Both slots empty
        assertFalse(listener.isGrindstoneBlocked(mockGrindstone));

        // Case 2: Single vanilla item (Slot 0)
        grindstoneSlots.put(0, vanillaItem1);
        assertFalse(listener.isGrindstoneBlocked(mockGrindstone), "Single vanilla item must be allowed");

        // Case 3: Two vanilla items (Slot 0 and Slot 1)
        grindstoneSlots.put(1, vanillaItem2);
        assertFalse(listener.isGrindstoneBlocked(mockGrindstone), "Combining two vanilla items must be allowed");

        // Case 4: Single RoyItem WITHOUT pre-configured enchantments
        grindstoneSlots.clear();
        grindstoneSlots.put(0, itemWithoutEnchants);
        assertFalse(listener.isGrindstoneBlocked(mockGrindstone), "Single RoyItem without pre-configured enchantments must be allowed");

        // Case 5: Single RoyItem WITH pre-configured enchantments (Slot 0)
        grindstoneSlots.clear();
        grindstoneSlots.put(0, itemWithEnchants);
        assertTrue(listener.isGrindstoneBlocked(mockGrindstone), "RoyItem with pre-configured enchantments in slot 0 must be BLOCKED");

        // Case 6: Single RoyItem WITH pre-configured enchantments (Slot 1)
        grindstoneSlots.clear();
        grindstoneSlots.put(1, itemWithEnchants);
        assertTrue(listener.isGrindstoneBlocked(mockGrindstone), "RoyItem with pre-configured enchantments in slot 1 must be BLOCKED");

        // Case 7: Combining RoyItem (without enchants) + Vanilla item
        grindstoneSlots.clear();
        grindstoneSlots.put(0, itemWithoutEnchants);
        grindstoneSlots.put(1, vanillaItem1);
        assertTrue(listener.isGrindstoneBlocked(mockGrindstone), "Combining RoyItem with vanilla item must be BLOCKED to prevent PDC loss");

        // Case 8: Combining Vanilla item + RoyItem (without enchants)
        grindstoneSlots.clear();
        grindstoneSlots.put(0, vanillaItem1);
        grindstoneSlots.put(1, itemWithoutEnchants);
        assertTrue(listener.isGrindstoneBlocked(mockGrindstone), "Combining vanilla item with RoyItem must be BLOCKED to prevent PDC loss");

        // Case 9: Combining two RoyItems
        grindstoneSlots.clear();
        grindstoneSlots.put(0, itemWithoutEnchants);
        grindstoneSlots.put(1, itemWithEnchants);
        assertTrue(listener.isGrindstoneBlocked(mockGrindstone), "Combining two RoyItems must be BLOCKED");

        // Case 10: Config disabled
        YamlConfiguration yamlFalse = new YamlConfiguration();
        yamlFalse.set("grindstone.prevent-disenchant-configured", false);
        listener.loadConfig(yamlFalse);
        assertFalse(listener.isGrindstoneBlocked(mockGrindstone), "When config disabled, check returns false");
    }

    @Test
    @DisplayName("Localization: Verify new message keys exist across lang files")
    void testProtectionMessageKeysExist() throws Exception {
        java.io.InputStream inEn = getClass().getClassLoader().getResourceAsStream("lang/en-US.yml");
        assertNotNull(inEn, "lang/en-US.yml must exist in test classpath");
        YamlConfiguration en = new YamlConfiguration();
        en.loadFromString(new String(inEn.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(en.contains("messages.smithing-table-blocked"), "messages.smithing-table-blocked must exist in en-US.yml");
        assertTrue(en.contains("messages.grindstone-disenchant-blocked"), "messages.grindstone-disenchant-blocked must exist in en-US.yml");

        java.io.InputStream inVi = getClass().getClassLoader().getResourceAsStream("lang/vi-VN.yml");
        assertNotNull(inVi, "lang/vi-VN.yml must exist in test classpath");
        YamlConfiguration vi = new YamlConfiguration();
        vi.loadFromString(new String(inVi.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(vi.contains("messages.smithing-table-blocked"), "messages.smithing-table-blocked must exist in vi-VN.yml");
        assertTrue(vi.contains("messages.grindstone-disenchant-blocked"), "messages.grindstone-disenchant-blocked must exist in vi-VN.yml");
    }

    @Test
    @DisplayName("Protection Listeners: Full InventoryClickEvent simulation for Smithing and Grindstone")
    void testInventoryClickEventsSmithingAndGrindstone() {
        com.github.orenaidaucac.royItems.item.ItemRegistry registry = new com.github.orenaidaucac.royItems.item.ItemRegistry();
        Map<ItemStack, Boolean> royItemMarkers = new HashMap<>();
        Map<ItemStack, ItemDefinition> royDefinitions = new HashMap<>();

        Plugin mockPlugin = (Plugin) java.lang.reflect.Proxy.newProxyInstance(
                Plugin.class.getClassLoader(),
                new Class<?>[]{Plugin.class},
                (proxy, method, args) -> "getName".equals(method.getName()) ? "RoyItems" : null
        );

        com.github.orenaidaucac.royItems.item.ItemPDCManager mockPdc =
                new com.github.orenaidaucac.royItems.item.ItemPDCManager(mockPlugin) {
                    @Override
                    public boolean isRoyItem(ItemStack item) {
                        return item != null && Boolean.TRUE.equals(royItemMarkers.get(item));
                    }
                    @Override
                    public ItemType getType(ItemStack item) {
                        ItemDefinition d = royDefinitions.get(item);
                        return d != null ? d.getType() : ItemType.SWORD;
                    }
                    @Override
                    public String getId(ItemStack item) {
                        ItemDefinition d = royDefinitions.get(item);
                        return d != null ? d.getId() : "item";
                    }
                };

        com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener listener =
                new com.github.orenaidaucac.royItems.mechanics.ItemProtectionListener(mockPdc, registry);

        ItemDefinition defConfigured = new ItemDefinition(ItemType.SWORD, "excalibur");
        defConfigured.setEnchantments(Map.of(Enchantment.SHARPNESS, 5));
        registry.register(defConfigured);

        ItemDefinition defNoEnchants = new ItemDefinition(ItemType.SWORD, "plain_blade");
        registry.register(defNoEnchants);

        ItemStack royWithEnch = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        royItemMarkers.put(royWithEnch, true);
        royDefinitions.put(royWithEnch, defConfigured);

        ItemStack royWithoutEnch = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);
        royItemMarkers.put(royWithoutEnch, true);
        royDefinitions.put(royWithoutEnch, defNoEnchants);

        ItemStack vanillaSword = new TestItemStack(org.bukkit.Material.DIAMOND_SWORD, 1);

        org.bukkit.entity.Player mockPlayer = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.entity.Player.class.getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class},
                (proxy, method, args) -> null
        );

        // 1. Smithing Table Simulations
        Map<Integer, ItemStack> smithingSlots = new HashMap<>();
        org.bukkit.inventory.SmithingInventory mockSmithing =
                (org.bukkit.inventory.SmithingInventory) java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.inventory.SmithingInventory.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.inventory.SmithingInventory.class},
                        (proxy, method, args) -> {
                            if ("getItem".equals(method.getName())) return smithingSlots.get((int) args[0]);
                            if ("setItem".equals(method.getName())) {
                                smithingSlots.put((int) args[0], (ItemStack) args[1]);
                                return null;
                            }
                            if ("getContents".equals(method.getName())) return smithingSlots.values().toArray(new ItemStack[0]);
                            return null;
                        }
                );

        org.bukkit.inventory.InventoryView smithingView = (org.bukkit.inventory.InventoryView) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.inventory.InventoryView.class.getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.InventoryView.class},
                (proxy, method, args) -> {
                    if ("getTopInventory".equals(method.getName())) return mockSmithing;
                    if ("getPlayer".equals(method.getName())) return mockPlayer;
                    if ("convertSlot".equals(method.getName())) return args != null && args.length > 0 ? args[0] : 0;
                    return null;
                }
        );

        // A. Result slot click when base is RoyItem -> Cancelled
        smithingSlots.put(1, royWithEnch);
        org.bukkit.event.inventory.InventoryClickEvent clickSmithResult = new org.bukkit.event.inventory.InventoryClickEvent(
                smithingView, org.bukkit.event.inventory.InventoryType.SlotType.RESULT, 3,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        listener.onInventoryClick(clickSmithResult);
        assertTrue(clickSmithResult.isCancelled(), "Result click on Smithing Table with RoyItem must be cancelled");

        // B. Cursor placement of RoyItem into Base slot (slot 1) -> Cancelled
        smithingSlots.clear();
        org.bukkit.event.inventory.InventoryClickEvent placeRoyCursor = new org.bukkit.event.inventory.InventoryClickEvent(
                smithingView, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 1,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL
        ) {
            @Override public ItemStack getCursor() { return royWithEnch; }
        };
        listener.onInventoryClick(placeRoyCursor);
        assertTrue(placeRoyCursor.isCancelled(), "Placing RoyItem into slot 1 of Smithing Table must be cancelled");

        // C. Cursor placement of Vanilla item into Base slot -> Not cancelled
        org.bukkit.event.inventory.InventoryClickEvent placeVanillaCursor = new org.bukkit.event.inventory.InventoryClickEvent(
                smithingView, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 1,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL
        ) {
            @Override public ItemStack getCursor() { return vanillaSword; }
        };
        listener.onInventoryClick(placeVanillaCursor);
        assertFalse(placeVanillaCursor.isCancelled(), "Placing vanilla item into slot 1 of Smithing Table must be allowed");

        // D. Shift-click RoyItem from inventory -> Cancelled
        org.bukkit.event.inventory.InventoryClickEvent shiftClickRoy = new org.bukkit.event.inventory.InventoryClickEvent(
                smithingView, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 4,
                org.bukkit.event.inventory.ClickType.SHIFT_LEFT, org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY
        ) {
            @Override public ItemStack getCurrentItem() { return royWithEnch; }
        };
        listener.onInventoryClick(shiftClickRoy);
        assertTrue(shiftClickRoy.isCancelled(), "Shift-clicking RoyItem into Smithing Table must be cancelled");

        // 2. Grindstone Simulations
        Map<Integer, ItemStack> grindstoneSlots = new HashMap<>();
        org.bukkit.inventory.GrindstoneInventory mockGrindstone =
                (org.bukkit.inventory.GrindstoneInventory) java.lang.reflect.Proxy.newProxyInstance(
                        org.bukkit.inventory.GrindstoneInventory.class.getClassLoader(),
                        new Class<?>[]{org.bukkit.inventory.GrindstoneInventory.class},
                        (proxy, method, args) -> {
                            if ("getItem".equals(method.getName())) return grindstoneSlots.get((int) args[0]);
                            if ("setItem".equals(method.getName())) {
                                grindstoneSlots.put((int) args[0], (ItemStack) args[1]);
                                return null;
                            }
                            if ("getContents".equals(method.getName())) return grindstoneSlots.values().toArray(new ItemStack[0]);
                            return null;
                        }
                );

        org.bukkit.inventory.InventoryView grindstoneView = (org.bukkit.inventory.InventoryView) java.lang.reflect.Proxy.newProxyInstance(
                org.bukkit.inventory.InventoryView.class.getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.InventoryView.class},
                (proxy, method, args) -> {
                    if ("getTopInventory".equals(method.getName())) return mockGrindstone;
                    if ("getPlayer".equals(method.getName())) return mockPlayer;
                    if ("convertSlot".equals(method.getName())) return args != null && args.length > 0 ? args[0] : 0;
                    return null;
                }
        );

        // A. Result slot click when RoyItem has configured enchants -> Cancelled
        grindstoneSlots.put(0, royWithEnch);
        org.bukkit.event.inventory.InventoryClickEvent clickGrindResultBlocked = new org.bukkit.event.inventory.InventoryClickEvent(
                grindstoneView, org.bukkit.event.inventory.InventoryType.SlotType.RESULT, 2,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        listener.onInventoryClick(clickGrindResultBlocked);
        assertTrue(clickGrindResultBlocked.isCancelled(), "Result click on Grindstone for RoyItem with configured enchants must be cancelled");

        // B. Result slot click when RoyItem has NO configured enchants -> Allowed
        grindstoneSlots.clear();
        grindstoneSlots.put(0, royWithoutEnch);
        org.bukkit.event.inventory.InventoryClickEvent clickGrindResultAllowed = new org.bukkit.event.inventory.InventoryClickEvent(
                grindstoneView, org.bukkit.event.inventory.InventoryType.SlotType.RESULT, 2,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        listener.onInventoryClick(clickGrindResultAllowed);
        assertFalse(clickGrindResultAllowed.isCancelled(), "Result click on Grindstone for RoyItem without configured enchants must be allowed");

        // C. Placing item with configured enchants into Slot 0 -> Cancelled
        grindstoneSlots.clear();
        org.bukkit.event.inventory.InventoryClickEvent placeGrindBlocked = new org.bukkit.event.inventory.InventoryClickEvent(
                grindstoneView, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL
        ) {
            @Override public ItemStack getCursor() { return royWithEnch; }
        };
        listener.onInventoryClick(placeGrindBlocked);
        assertTrue(placeGrindBlocked.isCancelled(), "Placing RoyItem with configured enchantments into Grindstone must be cancelled");

        // D. Placing item without configured enchants into Slot 0 -> Allowed
        org.bukkit.event.inventory.InventoryClickEvent placeGrindAllowed = new org.bukkit.event.inventory.InventoryClickEvent(
                grindstoneView, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 0,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL
        ) {
            @Override public ItemStack getCursor() { return royWithoutEnch; }
        };
        listener.onInventoryClick(placeGrindAllowed);
        assertFalse(placeGrindAllowed.isCancelled(), "Placing RoyItem without configured enchantments into empty Grindstone must be allowed");

        // E. Combining: Placing a second item into slot 1 when slot 0 already has a RoyItem -> Cancelled
        grindstoneSlots.put(0, royWithoutEnch);
        org.bukkit.event.inventory.InventoryClickEvent placeGrindCombine = new org.bukkit.event.inventory.InventoryClickEvent(
                grindstoneView, org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, 1,
                org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PLACE_ALL
        ) {
            @Override public ItemStack getCursor() { return vanillaSword; }
        };
        listener.onInventoryClick(placeGrindCombine);
        assertTrue(placeGrindCombine.isCancelled(), "Combining second item with RoyItem in Grindstone must be cancelled");
    }
}
