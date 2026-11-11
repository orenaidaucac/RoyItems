package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI;

import java.lang.reflect.Method;

/**
 * ExcellentEconomy Integration Hook using reflection safe provider.
 */
public class ExcellentEconomyHook {

    private ExcellentEconomyAPI api;

    public boolean setup() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
        if (plugin != null) {
            try {
                Method getApiMethod = plugin.getClass().getMethod("getAPI");
                Object apiObj = getApiMethod.invoke(plugin);
                if (apiObj instanceof ExcellentEconomyAPI eeApi) {
                    this.api = eeApi;
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    public boolean isAvailable() {
        return api != null;
    }

    public double getBalance(Player player, String currencyId) {
        if (api == null || player == null || currencyId == null) return 0.0;
        try {
            return api.getBalance(player, currencyId);
        } catch (Throwable e) {
            return 0.0;
        }
    }

    public boolean hasBalance(Player player, String currencyId, double amount) {
        return getBalance(player, currencyId) >= amount;
    }

    public boolean withdraw(Player player, String currencyId, double amount) {
        if (api == null || player == null || currencyId == null || amount <= 0) return true;
        try {
            return api.withdraw(player, currencyId, amount);
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean hasCurrency(String currencyId) {
        if (api == null || currencyId == null) return false;
        try {
            Method hasMethod = api.getClass().getMethod("hasCurrency", String.class);
            return (boolean) hasMethod.invoke(api, currencyId);
        } catch (Throwable e) {
            return false;
        }
    }

    public String getCurrencyDisplayName(String currencyId) {
        if (api == null || currencyId == null) return currencyId;
        try {
            Method getCurrencyMethod = api.getClass().getMethod("getCurrency", String.class);
            Object curr = getCurrencyMethod.invoke(api, currencyId);
            if (curr != null) {
                Method getNameMethod = curr.getClass().getMethod("getName");
                Object nameObj = getNameMethod.invoke(curr);
                if (nameObj instanceof String str && !str.isBlank()) {
                    return str;
                }
            }
        } catch (Throwable ignored) {}
        return currencyId;
    }

    public String getCurrencySymbol(String currencyId) {
        if (api == null || currencyId == null) return "";
        try {
            Method getCurrencyMethod = api.getClass().getMethod("getCurrency", String.class);
            Object curr = getCurrencyMethod.invoke(api, currencyId);
            if (curr != null) {
                Method getSymbolMethod = curr.getClass().getMethod("getSymbol");
                Object symObj = getSymbolMethod.invoke(curr);
                if (symObj instanceof String str) {
                    return str;
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public String format(String currencyId, double amount) {
        if (api == null || currencyId == null) return String.valueOf(amount);
        try {
            Method getCurrencyMethod = api.getClass().getMethod("getCurrency", String.class);
            Object curr = getCurrencyMethod.invoke(api, currencyId);
            if (curr != null) {
                Method formatMethod = curr.getClass().getMethod("format", double.class);
                Object formatted = formatMethod.invoke(curr, amount);
                if (formatted instanceof String str) {
                    return str;
                }
            }
        } catch (Throwable ignored) {}
        return String.valueOf(amount);
    }

    public boolean deposit(Player player, String currencyId, double amount) {
        if (api == null || player == null || currencyId == null || amount <= 0) return true;
        try {
            return api.deposit(player, currencyId, amount);
        } catch (Throwable e) {
            return false;
        }
    }
}
