package com.github.orenaidaucac.royItems.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * ExcellentEconomy Integration Hook using pure reflection safe provider.
 * Completely independent of external compile-time jar versions.
 */
public class ExcellentEconomyHook {

    private Object api;
    private Method getBalanceMethod;
    private Method withdrawMethod;
    private Method depositMethod;
    private Method hasCurrencyMethod;
    private Method getCurrencyMethod;

    public boolean setup() {
        if (Bukkit.getServer() == null || Bukkit.getPluginManager() == null) {
            this.api = null;
            return false;
        }
        Plugin plugin = Bukkit.getPluginManager().getPlugin("ExcellentEconomy");
        if (plugin != null && plugin.isEnabled()) {
            try {
                Method getApiMethod = plugin.getClass().getMethod("getAPI");
                Object apiObj = getApiMethod.invoke(plugin);
                if (apiObj != null) {
                    this.api = apiObj;
                    Class<?> apiClass = apiObj.getClass();
                    for (Method m : apiClass.getMethods()) {
                        if ("getBalance".equals(m.getName()) && m.getParameterCount() == 2 && m.getParameterTypes()[0] == Player.class && m.getParameterTypes()[1] == String.class) {
                            this.getBalanceMethod = m;
                        } else if ("withdraw".equals(m.getName()) && m.getParameterCount() == 3 && m.getParameterTypes()[0] == Player.class && m.getParameterTypes()[1] == String.class && m.getParameterTypes()[2] == double.class) {
                            this.withdrawMethod = m;
                        } else if ("deposit".equals(m.getName()) && m.getParameterCount() == 3 && m.getParameterTypes()[0] == Player.class && m.getParameterTypes()[1] == String.class && m.getParameterTypes()[2] == double.class) {
                            this.depositMethod = m;
                        } else if ("hasCurrency".equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                            this.hasCurrencyMethod = m;
                        } else if ("getCurrency".equals(m.getName()) && m.getParameterCount() == 1 && m.getParameterTypes()[0] == String.class) {
                            this.getCurrencyMethod = m;
                        }
                    }
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        this.api = null;
        return false;
    }

    public boolean isAvailable() {
        return api != null;
    }

    public double getBalance(Player player, String currencyId) {
        if (api == null || player == null || currencyId == null) return 0.0;
        try {
            if (getBalanceMethod != null) {
                Object res = getBalanceMethod.invoke(api, player, currencyId);
                if (res instanceof Number num) return num.doubleValue();
            }
        } catch (Throwable ignored) {}
        return 0.0;
    }

    public boolean hasBalance(Player player, String currencyId, double amount) {
        return getBalance(player, currencyId) >= amount;
    }

    public boolean withdraw(Player player, String currencyId, double amount) {
        if (api == null || player == null || currencyId == null || amount <= 0) return true;
        try {
            if (withdrawMethod != null) {
                Object res = withdrawMethod.invoke(api, player, currencyId, amount);
                if (res instanceof Boolean b) return b;
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean deposit(Player player, String currencyId, double amount) {
        if (api == null || player == null || currencyId == null || amount <= 0) return true;
        try {
            if (depositMethod != null) {
                Object res = depositMethod.invoke(api, player, currencyId, amount);
                if (res instanceof Boolean b) return b;
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean hasCurrency(String currencyId) {
        if (api == null || currencyId == null) return false;
        try {
            if (hasCurrencyMethod != null) {
                return (boolean) hasCurrencyMethod.invoke(api, currencyId);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public String getCurrencyDisplayName(String currencyId) {
        if (api == null || currencyId == null) return currencyId;
        try {
            if (getCurrencyMethod != null) {
                Object curr = getCurrencyMethod.invoke(api, currencyId);
                if (curr != null) {
                    Method getNameMethod = curr.getClass().getMethod("getName");
                    Object nameObj = getNameMethod.invoke(curr);
                    if (nameObj instanceof String str && !str.isBlank()) {
                        return str;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return currencyId;
    }

    public String getCurrencySymbol(String currencyId) {
        if (api == null || currencyId == null) return "";
        try {
            if (getCurrencyMethod != null) {
                Object curr = getCurrencyMethod.invoke(api, currencyId);
                if (curr != null) {
                    Method getSymbolMethod = curr.getClass().getMethod("getSymbol");
                    Object symObj = getSymbolMethod.invoke(curr);
                    if (symObj instanceof String str) {
                        return str;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public String format(String currencyId, double amount) {
        if (api == null || currencyId == null) return String.valueOf(amount);
        try {
            if (getCurrencyMethod != null) {
                Object curr = getCurrencyMethod.invoke(api, currencyId);
                if (curr != null) {
                    Method formatMethod = curr.getClass().getMethod("format", double.class);
                    Object formatted = formatMethod.invoke(curr, amount);
                    if (formatted instanceof String str) {
                        return str;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return String.valueOf(amount);
    }
}
