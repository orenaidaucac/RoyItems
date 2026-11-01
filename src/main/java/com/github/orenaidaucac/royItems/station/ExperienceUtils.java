package com.github.orenaidaucac.royItems.station;

import org.bukkit.entity.Player;

/**
 * Utility for manipulating total experience points of a Bukkit player.
 */
public final class ExperienceUtils {

    private ExperienceUtils() {}

    public static int getPlayerExp(Player player) {
        if (player == null) return 0;
        int exp = Math.round(getExpToLevel(player.getLevel()) * player.getExp());
        int currentLevel = player.getLevel();
        while (currentLevel > 0) {
            currentLevel--;
            exp += getExpToLevel(currentLevel);
        }
        return Math.max(0, exp);
    }

    public static boolean hasExp(Player player, int amount) {
        return getPlayerExp(player) >= amount;
    }

    public static void takeExp(Player player, int amount) {
        if (player == null || amount <= 0) return;
        int current = getPlayerExp(player);
        setPlayerExp(player, Math.max(0, current - amount));
    }

    public static void giveExp(Player player, int amount) {
        if (player == null || amount <= 0) return;
        int current = getPlayerExp(player);
        setPlayerExp(player, current + amount);
    }

    public static void setPlayerExp(Player player, int exp) {
        if (player == null) return;
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);

        int level = 0;
        while (exp > 0) {
            int toNext = getExpToLevel(level);
            if (exp >= toNext) {
                exp -= toNext;
                level++;
            } else {
                player.setExp((float) exp / (float) toNext);
                break;
            }
        }
        player.setLevel(level);
        player.setTotalExperience(getPlayerExp(player));
    }

    private static int getExpToLevel(int level) {
        if (level <= 15) {
            return 2 * level + 7;
        } else if (level <= 30) {
            return 5 * level - 38;
        } else {
            return 9 * level - 158;
        }
    }
}
