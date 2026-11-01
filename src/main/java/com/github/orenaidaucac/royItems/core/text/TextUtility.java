package com.github.orenaidaucac.royItems.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text formatting engine supporting MiniMessage, Legacy '&'/'§', and Hex codes
 * while removing default Minecraft italic on items and GUI text by default
 * unless explicitly requested by the user.
 */
public class TextUtility {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    // Regex for hex formats: &#RRGGBB, {#RRGGBB}, <#RRGGBB>, &x&r&r&g&g&b&b
    private static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile("(?i)[&§]x([&§][0-9a-fA-F]){6}");
    private static final Pattern HEX_AMPERSAND_PATTERN = Pattern.compile("(?i)[&§]#([0-9a-fA-F]{6})");
    private static final Pattern HEX_CURLY_PATTERN = Pattern.compile("(?i)\\{#?([0-9a-fA-F]{6})\\}");
    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");

    /**
     * Translates a raw string into an Adventure Component with anti-italic by default.
     *
     * @param input Raw text with possible legacy/hex/minimessage codes.
     * @return Formatted Adventure Component.
     */
    public static Component format(String input) {
        return format(input, true);
    }

    /**
     * Translates a raw string into an Adventure Component.
     * Handles MiniMessage, legacy '&'/'§' colors, and hex variants, with anti-italic for items and GUIs.
     *
     * @param input Raw text with possible legacy/hex/minimessage codes.
     * @param disableDefaultItalic If true, non-italic style will be applied unless explicitly set to italic.
     * @return Formatted Adventure Component.
     */
    public static Component format(String input, boolean disableDefaultItalic) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        // 1. Check if user explicitly requested italic (e.g. &o, §o, <italic>, <i>)
        boolean hasExplicitItalic = containsExplicitItalic(input);

        // 2. Normalize legacy Hex codes and legacy color codes into MiniMessage compatible tags
        String converted = normalizeHexAndLegacy(input);

        // 3. Parse via MiniMessage
        Component component;
        try {
            component = MINI_MESSAGE.deserialize(converted);
        } catch (Exception e) {
            // Fallback to legacy serializer if MiniMessage fails
            try {
                component = LEGACY_SERIALIZER.deserialize(input.replace('§', '&'));
            } catch (Exception ignored) {
                component = Component.text(input);
            }
        }

        // 4. Enforce anti-italic if requested and italic was not explicitly requested
        if (disableDefaultItalic && !hasExplicitItalic) {
            component = component.decoration(TextDecoration.ITALIC, false);
        }

        return component;
    }

    /**
     * Check whether the text explicitly contains italic formatting codes.
     */
    public static boolean containsExplicitItalic(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        return lower.contains("&o") || lower.contains("§o") ||
               lower.contains("<italic") || lower.contains("<i>");
    }

    /**
     * Format GUI titles and icon display names (always anti-italic by default).
     */
    public static Component formatGui(String input) {
        return format(input, true);
    }

    /**
     * Format GUI lore lines (always anti-italic by default).
     */
    public static List<Component> formatGuiLore(List<String> lines) {
        if (lines == null) return new ArrayList<>();
        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(format(line, true));
        }
        return components;
    }

    /**
     * Formats a line by substituting a named placeholder (e.g. %name% or <name>)
     * with an Adventure Component (such as a TranslatableComponent).
     * Retains full Adventure formatting and anti-italic decoration.
     */
    public static Component formatLineWithComponent(String input, String placeholderName, Component placeholderValue) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        boolean hasExplicitItalic = containsExplicitItalic(input);
        String prepared = input.replace("%" + placeholderName + "%", "<" + placeholderName + ">");
        String converted = normalizeHexAndLegacy(prepared);
        Component component;
        try {
            component = MINI_MESSAGE.deserialize(
                    converted,
                    Placeholder.component(placeholderName, placeholderValue != null ? placeholderValue : Component.empty())
            );
        } catch (Exception e) {
            try {
                component = format(input.replace("%" + placeholderName + "%", PlainTextComponentSerializer.plainText().serialize(placeholderValue)), false);
            } catch (Exception ignored) {
                component = Component.text(input);
            }
        }
        if (!hasExplicitItalic) {
            component = component.decoration(TextDecoration.ITALIC, false);
        }
        return component;
    }

    /**
     * Format an item display name (anti-italic by default).
     */
    public static Component formatItemName(String input) {
        return format(input, true);
    }

    /**
     * Format lore lines (anti-italic by default).
     */
    public static List<Component> formatLore(List<String> lines) {
        return formatGuiLore(lines);
    }

    /**
     * Format a message or announcement (preserves natural text style).
     */
    public static Component formatMessage(String input) {
        return format(input, false);
    }

    /**
     * Converts legacy hex and '&'/'§' codes into MiniMessage compatible tags.
     */
    public static String normalizeHexAndLegacy(String input) {
        if (input == null || input.isEmpty()) return "";

        String s = input;

        // 1. Convert BungeeCord hex format &x&r&r&g&g&b&b / §x§r§r§g§g§b§b to <#RRGGBB>
        Matcher bungeeMatcher = BUNGEE_HEX_PATTERN.matcher(s);
        StringBuffer bungeeBuffer = new StringBuffer();
        while (bungeeMatcher.find()) {
            String hexOnly = bungeeMatcher.group().replaceAll("[&§xX]", "");
            bungeeMatcher.appendReplacement(bungeeBuffer, "<#" + hexOnly + ">");
        }
        bungeeMatcher.appendTail(bungeeBuffer);
        s = bungeeBuffer.toString();

        // 2. Replace &#RRGGBB or §#RRGGBB with <#RRGGBB>
        Matcher m1 = HEX_AMPERSAND_PATTERN.matcher(s);
        StringBuffer sb1 = new StringBuffer();
        while (m1.find()) {
            m1.appendReplacement(sb1, "<#" + m1.group(1) + ">");
        }
        m1.appendTail(sb1);
        s = sb1.toString();

        // 3. Replace {#RRGGBB} or {RRGGBB} with <#RRGGBB>
        Matcher m2 = HEX_CURLY_PATTERN.matcher(s);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            m2.appendReplacement(sb2, "<#" + m2.group(1) + ">");
        }
        m2.appendTail(sb2);
        s = sb2.toString();

        // Note: <#RRGGBB> is native MiniMessage syntax.
        // Bare #RRGGBB is deliberately NOT converted to avoid corrupting MiniMessage tags (such as <gradient:#FFD166:#06D6A0>).

        // 4. Convert standard legacy color & formatting codes &0-&f, &k-&o, &r (and §) to MiniMessage tags
        Matcher legacyMatcher = LEGACY_CODE_PATTERN.matcher(s);
        StringBuffer sbLegacy = new StringBuffer();
        while (legacyMatcher.find()) {
            char code = Character.toLowerCase(legacyMatcher.group(1).charAt(0));
            String tag = switch (code) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'k' -> "<obfuscated>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> legacyMatcher.group();
            };
            legacyMatcher.appendReplacement(sbLegacy, Matcher.quoteReplacement(tag));
        }
        legacyMatcher.appendTail(sbLegacy);
        s = sbLegacy.toString();

        return s;
    }

    /**
     * Serialize a Component to plain text.
     */
    public static String toPlain(Component component) {
        if (component == null) return "";
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
