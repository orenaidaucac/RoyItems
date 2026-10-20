package com.github.orenaidaucac.royItems.integration;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault Economy Hook.
 */
public class VaultHook {

    private Economy economy;

    public boolean setup() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean hasMoney(Player player, double amount) {
        if (economy == null || player == null) return true;
        return economy.has(player, amount);
    }

    public boolean withdraw(Player player, double amount) {
        if (economy == null || player == null) return true;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public boolean deposit(Player player, double amount) {
        if (economy == null || player == null) return true;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }
}
