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
}
