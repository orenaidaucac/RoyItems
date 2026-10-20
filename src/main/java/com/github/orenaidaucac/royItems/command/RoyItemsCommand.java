package com.github.orenaidaucac.royItems.command;

import com.github.orenaidaucac.royItems.RoyItems;
import com.github.orenaidaucac.royItems.core.platform.PlatformScheduler;
import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.gui.EditorGUIManager;
import com.github.orenaidaucac.royItems.item.*;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Command handler and full context-aware tab completer for /royitems and /ri.
 */
public class RoyItemsCommand implements CommandExecutor, TabCompleter {

    private final RoyItems plugin;
    private final ItemRegistry registry;
    private final ItemBuilder itemBuilder;
    private final ItemPDCManager pdcManager;
    private final EditorGUIManager guiManager;
    private final PlatformScheduler scheduler;

    public RoyItemsCommand(RoyItems plugin) {
        this.plugin = plugin;
        this.registry = plugin.getItemRegistry();
        this.itemBuilder = plugin.getItemBuilder();
        this.pdcManager = plugin.getItemPDCManager();
        this.guiManager = plugin.getEditorGUIManager();
        this.scheduler = plugin.getPlatformScheduler();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("royitems.command.reload")) {
                    sender.sendMessage(TextUtility.formatMessage("&cYou do not have permission to use this command."));
                    return true;
                }
                plugin.reloadAll();
                sender.sendMessage(TextUtility.formatMessage("&a[RoyItems] Configuration, items, and lore formats reloaded successfully!"));
                return true;
            }

            case "editor" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtility.formatMessage("&cThis command can only be executed by a player in-game."));
                    return true;
                }
                if (!player.hasPermission("royitems.command.editor")) {
                    player.sendMessage(TextUtility.formatMessage("&cYou do not have permission to access the GUI editor."));
                    return true;
                }
                guiManager.openMainDashboard(player);
                return true;
            }

            case "give" -> {
                // /ri give <player|all> <type> <id> <amount>
                if (!sender.hasPermission("royitems.command.give")) {
                    sender.sendMessage(TextUtility.formatMessage("&cYou do not have permission to give items."));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(TextUtility.formatMessage("&cUsage: /ri give <player|all> <type> <id> [amount]"));
                    return true;
                }

                String target = args[1];
                ItemType type = ItemType.fromString(args[2]);
                if (type == null) {
                    sender.sendMessage(TextUtility.formatMessage("&cInvalid Item Type: &f" + args[2]));
                    return true;
                }

                String id = args[3].toLowerCase();
                ItemDefinition def = registry.get(type, id);
                if (def == null) {
                    sender.sendMessage(TextUtility.formatMessage("&cCustom item not found: &f" + type.name() + ":" + id));
                    return true;
                }

                int amount = args.length >= 5 ? parseAmount(args[4]) : 1;
                if (amount <= 0) amount = 1;

                if (target.equalsIgnoreCase("all")) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        giveItemToPlayer(online, def, amount);
                    }
                    sender.sendMessage(TextUtility.formatMessage("&aGave &f" + amount + "x " + id + " &ato all online players!"));
                } else {
                    Player p = Bukkit.getPlayer(target);
                    if (p == null || !p.isOnline()) {
                        sender.sendMessage(TextUtility.formatMessage("&cPlayer not found or offline: &f" + target));
                        return true;
                    }
                    giveItemToPlayer(p, def, amount);
                    sender.sendMessage(TextUtility.formatMessage("&aGave &f" + amount + "x " + id + " &ato &f" + p.getName() + "&a!"));
                }
                return true;
            }

            case "take" -> {
                // /ri take <player|all> <type> <id> <amount>
                if (!sender.hasPermission("royitems.command.take")) {
                    sender.sendMessage(TextUtility.formatMessage("&cYou do not have permission to take items."));
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage(TextUtility.formatMessage("&cUsage: /ri take <player|all> <type> <id> [amount]"));
                    return true;
                }

                String target = args[1];
                ItemType type = ItemType.fromString(args[2]);
                if (type == null) {
                    sender.sendMessage(TextUtility.formatMessage("&cInvalid Item Type: &f" + args[2]));
                    return true;
                }

                String id = args[3].toLowerCase();
                int amount = args.length >= 5 ? parseAmount(args[4]) : 1;
                if (amount <= 0) amount = 1;

                if (target.equalsIgnoreCase("all")) {
                    for (Player online : Bukkit.getOnlinePlayers()) {
                        takeItemFromPlayer(online, type, id, amount);
                    }
                    sender.sendMessage(TextUtility.formatMessage("&aTook up to &f" + amount + "x " + id + " &afrom all players!"));
                } else {
                    Player p = Bukkit.getPlayer(target);
                    if (p == null || !p.isOnline()) {
                        sender.sendMessage(TextUtility.formatMessage("&cPlayer not found: &f" + target));
                        return true;
                    }
                    int removed = takeItemFromPlayer(p, type, id, amount);
                    sender.sendMessage(TextUtility.formatMessage("&aRemoved &f" + removed + "x " + id + " &afrom &f" + p.getName() + "&a!"));
                }
                return true;
            }

            case "drop" -> {
                // /ri drop <player|all> <type> <id> <amount>
                // /ri drop <world> <x> <y> <z> <type> <id> <amount>
                if (!sender.hasPermission("royitems.command.drop")) {
                    sender.sendMessage(TextUtility.formatMessage("&cYou do not have permission to drop items."));
                    return true;
                }

                if (args.length >= 7) {
                    // Location drop
                    World world = Bukkit.getWorld(args[1]);
                    if (world == null) {
                        sender.sendMessage(TextUtility.formatMessage("&cWorld not found: &f" + args[1]));
                        return true;
                    }
                    try {
                        double x = Double.parseDouble(args[2]);
                        double y = Double.parseDouble(args[3]);
                        double z = Double.parseDouble(args[4]);
                        ItemType type = ItemType.fromString(args[5]);
                        String id = args[6].toLowerCase();
                        ItemDefinition def = registry.get(type, id);
                        if (def == null) {
                            sender.sendMessage(TextUtility.formatMessage("&cItem not found: &f" + id));
                            return true;
                        }
                        int amount = args.length >= 8 ? parseAmount(args[7]) : 1;

                        Location loc = new Location(world, x, y, z);
                        scheduler.runLocation(loc, () -> {
                            ItemStack dropStack = itemBuilder.build(def, amount);
                            world.dropItemNaturally(loc, dropStack);
                        });
                        sender.sendMessage(TextUtility.formatMessage("&aDropped &f" + amount + "x " + id + " &aat specified location!"));
                    } catch (NumberFormatException e) {
                        sender.sendMessage(TextUtility.formatMessage("&cInvalid coordinates format."));
                    }
                    return true;
                } else if (args.length >= 4) {
                    // Player drop
                    String target = args[1];
                    ItemType type = ItemType.fromString(args[2]);
                    String id = args[3].toLowerCase();
                    ItemDefinition def = registry.get(type, id);
                    if (def == null) {
                        sender.sendMessage(TextUtility.formatMessage("&cItem not found: &f" + id));
                        return true;
                    }
                    int amount = args.length >= 5 ? parseAmount(args[4]) : 1;

                    if (target.equalsIgnoreCase("all")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            scheduler.runEntity(p, () -> {
                                ItemStack dropStack = itemBuilder.build(def, amount);
                                p.getWorld().dropItemNaturally(p.getLocation(), dropStack);
                            });
                        }
                        sender.sendMessage(TextUtility.formatMessage("&aDropped &f" + amount + "x " + id + " &aat all players' locations!"));
                    } else {
                        Player p = Bukkit.getPlayer(target);
                        if (p == null || !p.isOnline()) {
                            sender.sendMessage(TextUtility.formatMessage("&cPlayer not found: &f" + target));
                            return true;
                        }
                        scheduler.runEntity(p, () -> {
                            ItemStack dropStack = itemBuilder.build(def, amount);
                            p.getWorld().dropItemNaturally(p.getLocation(), dropStack);
                        });
                        sender.sendMessage(TextUtility.formatMessage("&aDropped &f" + amount + "x " + id + " &aat &f" + p.getName() + "&a's location!"));
                    }
                    return true;
                }
            }
        }

        sendHelp(sender);
        return true;
    }

    private void giveItemToPlayer(Player player, ItemDefinition def, int amount) {
        scheduler.runEntity(player, () -> {
            ItemStack item = itemBuilder.build(def, amount);
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            if (!overflow.isEmpty()) {
                for (ItemStack dropItem : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
                }
            }
        });
    }

    private int takeItemFromPlayer(Player player, ItemType type, String id, int amountToTake) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amountToTake;

        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && pdcManager.isRoyItem(item)) {
                if (pdcManager.getType(item) == type && pdcManager.getId(item).equalsIgnoreCase(id)) {
                    if (item.getAmount() <= remaining) {
                        remaining -= item.getAmount();
                        contents[i] = null;
                    } else {
                        item.setAmount(item.getAmount() - remaining);
                        remaining = 0;
                    }
                }
            }
        }
        player.getInventory().setContents(contents);
        return amountToTake - remaining;
    }

    public static int parseAmount(String input) {
        if (input == null || input.isEmpty()) return 1;
        input = input.trim();
        if (input.contains("-")) {
            String[] parts = input.split("-");
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                if (max < min) {
                    int temp = min; min = max; max = temp;
                }
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            } catch (Exception ignored) {}
        }
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtility.formatMessage("&6&lRoyItems &7— &eAdvanced MMORPG Items Engine v2026.1"));
        sender.sendMessage(TextUtility.formatMessage("&e/ri help &7- Display help message"));
        sender.sendMessage(TextUtility.formatMessage("&e/ri reload &7- Reload all configs, items, and lore formats"));
        sender.sendMessage(TextUtility.formatMessage("&e/ri editor &7- Open Ingame GUI Editor"));
        sender.sendMessage(TextUtility.formatMessage("&e/ri give <player|all> <type> <id> [amount] &7- Give custom item"));
        sender.sendMessage(TextUtility.formatMessage("&e/ri take <player|all> <type> <id> [amount] &7- Remove custom item"));
        sender.sendMessage(TextUtility.formatMessage("&e/ri drop <player|all> <type> <id> [amount] &7- Drop item at player"));
        sender.sendMessage(TextUtility.formatMessage("&e/ri drop <world> <x> <y> <z> <type> <id> [amount] &7- Drop at location"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = List.of("help", "reload", "editor", "give", "take", "drop");
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
            return completions;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("give") || sub.equals("take")) {
            if (args.length == 2) {
                completions.add("all");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
                }
                return completions;
            }
            if (args.length == 3) {
                for (ItemType type : ItemType.values()) {
                    if (type.name().toLowerCase().startsWith(args[2].toLowerCase())) completions.add(type.name());
                }
                return completions;
            }
            if (args.length == 4) {
                ItemType type = ItemType.fromString(args[2]);
                if (type != null) {
                    for (ItemDefinition def : registry.getByType(type)) {
                        if (def.getId().toLowerCase().startsWith(args[3].toLowerCase())) completions.add(def.getId());
                    }
                }
                return completions;
            }
            if (args.length == 5) {
                return List.of("1", "1-3", "5", "10", "1-64");
            }
        }

        if (sub.equals("drop")) {
            if (args.length == 2) {
                completions.add("all");
                for (World w : Bukkit.getWorlds()) completions.add(w.getName());
                for (Player p : Bukkit.getOnlinePlayers()) completions.add(p.getName());
                return completions;
            }
            if (args.length == 3) {
                for (ItemType type : ItemType.values()) completions.add(type.name());
                return completions;
            }
            if (args.length == 4) {
                ItemType type = ItemType.fromString(args[2]);
                if (type != null) {
                    for (ItemDefinition def : registry.getByType(type)) {
                        completions.add(def.getId());
                    }
                }
                return completions;
            }
        }

        return completions;
    }
}
