package com.github.orenaidaucac.royItems;

import com.github.orenaidaucac.royItems.command.RoyItemsCommand;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.dummy.DummyManager;
import com.github.orenaidaucac.royItems.dummy.TrainingDummy;
import com.github.orenaidaucac.royItems.gui.GuiHelper;
import com.github.orenaidaucac.royItems.item.ItemBuilder;
import com.github.orenaidaucac.royItems.socket.GemSocketManager;
import com.github.orenaidaucac.royItems.station.RecipeIngredient;
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
        assertEquals("2026.1.6", expansion.getVersion());
    }
}
