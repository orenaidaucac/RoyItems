package com.github.orenaidaucac.royItems.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

    // Regex for hex formats: &#RRGGBB, <#RRGGBB>, #RRGGBB, &x&r&r&g&g&b&b
    private static final Pattern HEX_AMPERSAND_PATTERN = Pattern.compile("[&§]#([A-Fa-f0-9]{6})");
    private static final Pattern HEX_HASH_PATTERN = Pattern.compile("(?<!<)#([A-Fa-f0-9]{6})(?!>)");
    private static final Pattern BUNGEE_HEX_PATTERN = Pattern.compile("[&§]x([&§][A-Fa-f0-9]){6}");

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
                component = LEGACY_SERIALIZER.deserialize(input);
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
        if (input == null) return "";

        String s = input;

        // 1. Convert BungeeCord hex format &x&r&r&g&g&b&b to <#RRGGBB>
        Matcher bungeeMatcher = BUNGEE_HEX_PATTERN.matcher(s);
        StringBuffer bungeeBuffer = new StringBuffer();
        while (bungeeMatcher.find()) {
            String match = bungeeMatcher.group();
            StringBuilder hex = new StringBuilder("<#");
            for (int i = 2; i < match.length(); i += 2) {
                hex.append(match.charAt(i));
            }
            hex.append(">");
            bungeeMatcher.appendReplacement(bungeeBuffer, hex.toString());
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

        // 3. Replace standalone #RRGGBB with <#RRGGBB> (excluding already bracketed tags)
        Matcher m2 = HEX_HASH_PATTERN.matcher(s);
        StringBuffer sb2 = new StringBuffer();
        while (m2.find()) {
            m2.appendReplacement(sb2, "<#" + m2.group(1) + ">");
        }
        m2.appendTail(sb2);
        s = sb2.toString();

        // 4. Normalize section symbol § to &
        s = s.replace('§', '&');

        // 5. Convert standard legacy colors &0-&f, &k-&o, &r to MiniMessage tags
        s = s.replace("&0", "<black>")
             .replace("&1", "<dark_blue>")
             .replace("&2", "<dark_green>")
             .replace("&3", "<dark_aqua>")
             .replace("&4", "<dark_red>")
             .replace("&5", "<dark_purple>")
             .replace("&6", "<gold>")
             .replace("&7", "<gray>")
             .replace("&8", "<dark_gray>")
             .replace("&9", "<blue>")
             .replace("&a", "<green>")
             .replace("&b", "<aqua>")
             .replace("&c", "<red>")
             .replace("&d", "<light_purple>")
             .replace("&e", "<yellow>")
             .replace("&f", "<white>")
             .replace("&l", "<bold>")
             .replace("&m", "<strikethrough>")
             .replace("&n", "<underlined>")
             .replace("&o", "<italic>")
             .replace("&k", "<obfuscated>")
             .replace("&r", "<reset>");

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
