package com.github.orenaidaucac.royItems.gui;

import com.github.orenaidaucac.royItems.core.text.TextUtility;
import com.github.orenaidaucac.royItems.item.ItemDefinition;
import com.github.orenaidaucac.royItems.mechanics.PotionEffectData;
import com.github.orenaidaucac.royItems.stat.StatModifier;
import com.github.orenaidaucac.royItems.stat.StatType;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Modern MMOItems-style Paginated Item Editor GUI.
 * Sequential Previous/Next navigation, independent property icons,
 * 100% anti-italic by default, fully configurable via gui/editor-gui.yml.
 */
public class ItemDetailEditorGui {

    private final EditorGUIManager manager;
    private final Player player;
    private final ItemDefinition def;
    private final int currentPage;
    private final int maxPages;
    private final EditorGuiConfig config;

    public ItemDetailEditorGui(EditorGUIManager manager, Player player, ItemDefinition def) {
        this(manager, player, def, 1);
    }

    public ItemDetailEditorGui(EditorGUIManager manager, Player player, ItemDefinition def, int currentPage) {
        this.manager = manager;
        this.player = player;
        this.def = def;
        this.config = manager.getGuiConfig();
        this.maxPages = config.getDetailEditorMaxPages();
        this.currentPage = Math.max(1, Math.min(maxPages, currentPage));
    }

    public void open() {
        Gui gui = Gui.gui()
                .title(config.getDetailEditorTitle(def.getId(), currentPage, maxPages))
                .rows(config.getDetailEditorRows())
                .disableItemPlace()
                .disableItemTake()
                .disableItemSwap()
                .disableItemDrop()
                .create();

        // Allow dragging item onto Material slot to quickly set material
        gui.setDragAction(dragEvent -> {
            int materialSlot = config.getPropertyConfig("material",
                    new EditorGuiConfig.PropertyConfig(1, 10, Material.ANVIL, "", List.of(), 0, true)).slot();
            if (currentPage == 1 && dragEvent.getRawSlots().contains(materialSlot)) {
                ItemStack oldCursor = dragEvent.getOldCursor();
                if (oldCursor != null && !oldCursor.getType().isAir()) {
                    Material newMat = oldCursor.getType();
                    def.setMaterial(newMat.name());
                    player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Changed Material to &e" + newMat.name() + " &afrom dragged item!"));
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    } catch (Throwable ignored) {}
                    manager.getScheduler().runEntityDelayed(player, this::open, 1L);
                }
            }
            dragEvent.setCancelled(true);
        });

        // Allow shift-clicking item in player inventory on Page 1 to set material
        gui.setPlayerInventoryAction(clickEvent -> {
            if (currentPage == 1 && clickEvent.isShiftClick()) {
                ItemStack current = clickEvent.getCurrentItem();
                if (current != null && !current.getType().isAir()) {
                    Material newMat = current.getType();
                    def.setMaterial(newMat.name());
                    player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Changed Material to &e" + newMat.name() + " &afrom inventory item!"));
                    try {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                    } catch (Throwable ignored) {}
                    clickEvent.setCancelled(true);
                    manager.getScheduler().runEntityDelayed(player, this::open, 1L);
                }
            }
        });

        // 1. Fill background and border
        gui.getFiller().fill(GuiHelper.createFiller(config.getDetailEditorFiller()));
        gui.getFiller().fillBorder(GuiHelper.createFiller(config.getDetailEditorBorder()));

        // 2. Live Item Preview at Row 1, Center (Slot 4)
        renderLivePreview(gui);

        // 3. Render Property Icons for Current Page
        switch (currentPage) {
            case 1 -> renderPage1General(gui);
            case 2 -> renderPage2Stats(gui);
            case 3 -> renderPage3Mechanics(gui);
        }

        // 4. Render Bottom Navigation Controls (Row 6)
        renderNavigationControls(gui);

        manager.registerGui(player, gui);
        gui.open(player);
    }

    // =========================================================================
    // ROW 1: LIVE ITEM PREVIEW
    // =========================================================================

    private void renderLivePreview(Gui gui) {
        int previewSlot = config.getPreviewSlot();
        ItemStack previewStack = manager.getItemBuilder().build(def, 1);
        ItemMeta pMeta = previewStack.getItemMeta();
        if (pMeta != null) {
            List<Component> pLore = pMeta.hasLore() ? new ArrayList<>(pMeta.lore()) : new ArrayList<>();
            pLore.addAll(TextUtility.formatGuiLore(config.getPreviewLoreHint()));
            pMeta.lore(pLore);
            previewStack.setItemMeta(pMeta);
        }

        GuiItem previewItem = new GuiItem(previewStack, event -> {
            player.getInventory().addItem(manager.getItemBuilder().build(def, 1));
            player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Added 1x &f" + def.getId() + " &ato your inventory!"));
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
            } catch (Throwable ignored) {}
        });
        gui.setItem(previewSlot, previewItem);
    }

    // =========================================================================
    // ROW 6: NAVIGATION CONTROLS (MMOITEMS STYLE)
    // =========================================================================

    private void renderNavigationControls(Gui gui) {
        // 1. Back button (slot 45)
        EditorGuiConfig.ButtonConfig backCfg = config.getDetailNavigationButton("back",
                new EditorGuiConfig.ButtonConfig(45, Material.ARROW, "<#FFD166><bold>◀ Back</bold></#FFD166>", List.of(), 0));
        gui.setItem(backCfg.slot(), GuiHelper.createGuiItem(
                backCfg.material(),
                TextUtility.formatGui(backCfg.name()),
                TextUtility.formatGuiLore(backCfg.lore()),
                event -> {
                    manager.getSessionManager().releaseLock(player, def.getType(), def.getId());
                    manager.openBrowser(player, def.getType());
                }
        ));

        // 2. Previous Page button (slot 48)
        EditorGuiConfig.ButtonConfig prevCfg = config.getDetailNavigationButton("prev-page",
                new EditorGuiConfig.ButtonConfig(48, Material.SPECTRAL_ARROW, "<#FFD166>◀ Previous Page</#FFD166>", List.of(), 0));
        if (currentPage > 1) {
            String name = prevCfg.name()
                    .replace("%prev_page%", String.valueOf(currentPage - 1))
                    .replace("%max_pages%", String.valueOf(maxPages));
            gui.setItem(prevCfg.slot(), GuiHelper.createGuiItem(
                    prevCfg.material(),
                    TextUtility.formatGui(name),
                    TextUtility.formatGuiLore(prevCfg.lore()),
                    event -> manager.openDetailEditor(player, def, currentPage - 1)
            ));
        } else {
            // Disabled indicator on first page
            gui.setItem(prevCfg.slot(), GuiHelper.createGuiItem(
                    Material.GRAY_DYE,
                    TextUtility.formatGui("<gray>◀ No previous page</gray>"),
                    List.of(TextUtility.formatGui("<dark_gray>You are on the first page.</dark_gray>")),
                    null
            ));
        }

        // 3. Save button (slot 49)
        EditorGuiConfig.ButtonConfig saveCfg = config.getDetailNavigationButton("save",
                new EditorGuiConfig.ButtonConfig(49, Material.EMERALD_BLOCK, "<#06D6A0><bold>✔ Save Changes</bold></#06D6A0>", List.of(), 0));
        gui.setItem(saveCfg.slot(), GuiHelper.createGuiItem(
                saveCfg.material(),
                TextUtility.formatGui(saveCfg.name()),
                TextUtility.formatGuiLore(saveCfg.lore()),
                event -> {
                    boolean success = manager.getConfigManager().saveItem(def);
                    if (success) {
                        manager.getLiveUpdateService().updateAllOnlinePlayers();
                        player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Successfully saved &f" + def.getId() + " &aand triggered LiveUpdate!"));
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        } catch (Throwable ignored) {}
                        open(); // Refresh view
                    } else {
                        player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Failed to save! Please check server console."));
                    }
                }
        ));

        // 4. Next Page button (slot 50)
        EditorGuiConfig.ButtonConfig nextCfg = config.getDetailNavigationButton("next-page",
                new EditorGuiConfig.ButtonConfig(50, Material.SPECTRAL_ARROW, "<#FFD166>Next Page ▶</#FFD166>", List.of(), 0));
        if (currentPage < maxPages) {
            String name = nextCfg.name()
                    .replace("%next_page%", String.valueOf(currentPage + 1))
                    .replace("%max_pages%", String.valueOf(maxPages));
            gui.setItem(nextCfg.slot(), GuiHelper.createGuiItem(
                    nextCfg.material(),
                    TextUtility.formatGui(name),
                    TextUtility.formatGuiLore(nextCfg.lore()),
                    event -> manager.openDetailEditor(player, def, currentPage + 1)
            ));
        } else {
            // Disabled indicator on last page
            gui.setItem(nextCfg.slot(), GuiHelper.createGuiItem(
                    Material.GRAY_DYE,
                    TextUtility.formatGui("<gray>No next page ▶</gray>"),
                    List.of(TextUtility.formatGui("<dark_gray>You are on the last page.</dark_gray>")),
                    null
            ));
        }

        // 5. Close button (slot 53)
        EditorGuiConfig.ButtonConfig closeCfg = config.getDetailNavigationButton("close",
                new EditorGuiConfig.ButtonConfig(53, Material.BARRIER, "<#EF476F><bold>Close Menu</bold></#EF476F>", List.of(), 0));
        gui.setItem(closeCfg.slot(), GuiHelper.createGuiItem(
                closeCfg.material(),
                TextUtility.formatGui(closeCfg.name()),
                TextUtility.formatGuiLore(closeCfg.lore()),
                event -> {
                    manager.getSessionManager().releaseLock(player, def.getType(), def.getId());
                    gui.close(player);
                }
        ));
    }

    // =========================================================================
    // PAGE 1: GENERAL & VISUALS
    // =========================================================================

    private void renderPage1General(Gui gui) {
        // Material (Supports drag & drop from inventory cursor or chat input)
        renderProperty(gui, "material", 1, 10, Material.ANVIL,
                Map.of("%value%", def.getMaterial()),
                event -> {
                    ItemStack cursor = event.getCursor();
                    if (cursor != null && !cursor.getType().isAir()) {
                        Material newMat = cursor.getType();
                        def.setMaterial(newMat.name());
                        player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Changed material to &e" + newMat.name() + " &afrom dragged item!"));
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
                        } catch (Throwable ignored) {}
                        open();
                        return;
                    }

                    if (event.isRightClick()) {
                        cycleMaterial();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Material", def.getMaterial(),
                                "Enter Minecraft Material name (e.g. DIAMOND_SWORD, NETHERITE_CHESTPLATE...) or drag-and-drop an item into the Material slot:",
                                input -> Material.matchMaterial(input.trim()) != null,
                                input -> def.setMaterial(Material.matchMaterial(input.trim()).name()));
                    }
                });

        // Name
        renderProperty(gui, "name", 1, 11, Material.NAME_TAG,
                Map.of("%value%", def.getName().isEmpty() ? "Default by Type" : def.getName()),
                event -> {
                    if (event.isRightClick()) {
                        def.setName("");
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Display Name", def.getName(),
                                "Enter new display name for this item (Supports MiniMessage & color codes):",
                                null,
                                def::setName);
                    }
                });

        // Lore
        renderProperty(gui, "lore", 1, 12, Material.WRITABLE_BOOK,
                Map.of("%count%", String.valueOf(def.getLore().size())),
                event -> {
                    if (event.isShiftClick() && event.isRightClick()) {
                        def.getLore().clear();
                        open();
                    } else if (event.isRightClick()) {
                        if (!def.getLore().isEmpty()) {
                            def.getLore().remove(def.getLore().size() - 1);
                        }
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Add Lore Line", "",
                                "Enter new description line to add to Lore (Supports color codes):",
                                null,
                                input -> def.getLore().add(input));
                    }
                });

        // Item Model
        renderProperty(gui, "item-model", 1, 13, Material.PAINTING,
                Map.of("%value%", def.getItemModel() != null ? def.getItemModel() : "Default"),
                event -> {
                    if (event.isRightClick()) {
                        def.setItemModel(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Custom Item Model", def.getItemModel(),
                                "Enter Custom Item Model ID (e.g. custom_sword_1):",
                                null,
                                def::setItemModel);
                    }
                });

        // Lore Format
        renderProperty(gui, "lore-format", 1, 14, Material.MAP,
                Map.of("%value%", def.getLoreFormat() != null ? def.getLoreFormat() : "default"),
                event -> {
                    if (event.isRightClick()) {
                        def.setLoreFormat("default");
                    } else {
                        cycleLoreFormat();
                    }
                    open();
                });

        // Unbreakable
        renderProperty(gui, "unbreakable", 1, 15, Material.NETHERITE_INGOT,
                Map.of("%status%", def.isUnbreakable() ? "<#06D6A0>Enabled</#06D6A0>" : "<#EF476F>Disabled</#EF476F>"),
                event -> {
                    def.setUnbreakable(!def.isUnbreakable());
                    open();
                });

        // Glow
        renderProperty(gui, "glow", 1, 16, Material.GLOWSTONE_DUST,
                Map.of("%status%", def.isGlow() ? "<#06D6A0>Enabled</#06D6A0>" : "<#EF476F>Disabled</#EF476F>"),
                event -> {
                    def.setGlow(!def.isGlow());
                    open();
                });

        // Item Tier
        renderProperty(gui, "item-tier", 1, 20, Material.AMETHYST_SHARD,
                Map.of("%value%", def.getItemTier() != null ? def.getItemTier() : "None"),
                event -> {
                    if (event.isRightClick()) {
                        def.setItemTier(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Item Tier", def.getItemTier(),
                                "Enter Tier name (e.g. common, rare, epic, legendary, mythic):",
                                null,
                                input -> def.setItemTier(input.toLowerCase().trim()));
                    }
                });

        // Item Set
        renderProperty(gui, "item-set", 1, 21, Material.CHAINMAIL_CHESTPLATE,
                Map.of("%value%", def.getItemSet() != null ? def.getItemSet() : "None"),
                event -> {
                    if (event.isRightClick()) {
                        def.setItemSet(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Item Set", def.getItemSet(),
                                "Enter Set name (e.g. dragon_slayer, shadow_assassin):",
                                null,
                                input -> def.setItemSet(input.toLowerCase().trim()));
                    }
                });

        // Hide Enchantments
        renderProperty(gui, "hide-enchantments", 1, 23, Material.ENCHANTED_BOOK,
                Map.of("%status%", def.isHideEnchantments() ? "<#06D6A0>Hidden</#06D6A0>" : "<#EF476F>Visible</#EF476F>"),
                event -> {
                    def.setHideEnchantments(!def.isHideEnchantments());
                    open();
                });

        // Hide Attributes
        renderProperty(gui, "hide-attributes", 1, 24, Material.COMPASS,
                Map.of("%status%", def.isHideAttributes() ? "<#06D6A0>Hidden</#06D6A0>" : "<#EF476F>Visible</#EF476F>"),
                event -> {
                    def.setHideAttributes(!def.isHideAttributes());
                    open();
                });

        // Hide Unbreakable (Slot 25)
        renderProperty(gui, "hide-unbreakable", 1, 25, Material.SHIELD,
                Map.of("%status%", def.isHideUnbreakable() ? "<#06D6A0>Hidden</#06D6A0>" : "<#EF476F>Visible</#EF476F>"),
                event -> {
                    def.setHideUnbreakable(!def.isHideUnbreakable());
                    open();
                });

        // =====================================================================
        // ROW 4: ITEM RESTRICTIONS & PROTECTIONS
        // =====================================================================

        // Disable on Use (Slot 29)
        renderProperty(gui, "disable-on-use", 1, 29, Material.BARRIER,
                Map.of("%status%", def.isDisableOnUse() ? "<#EF476F>Blocked</#EF476F>" : "<#06D6A0>Allowed</#06D6A0>"),
                event -> {
                    def.setDisableOnUse(!def.isDisableOnUse());
                    open();
                });

        // Disable on Drop (Slot 30)
        renderProperty(gui, "disable-on-drop", 1, 30, Material.CHAIN,
                Map.of("%status%", def.isDisableOnDrop() ? "<#EF476F>Blocked</#EF476F>" : "<#06D6A0>Allowed</#06D6A0>"),
                event -> {
                    def.setDisableOnDrop(!def.isDisableOnDrop());
                    open();
                });

        // Disable on Crafting (Slot 32)
        renderProperty(gui, "disable-on-crafting", 1, 32, Material.CRAFTING_TABLE,
                Map.of("%status%", def.isDisableOnCrafting() ? "<#EF476F>Blocked</#EF476F>" : "<#06D6A0>Allowed</#06D6A0>"),
                event -> {
                    def.setDisableOnCrafting(!def.isDisableOnCrafting());
                    open();
                });

        // Disable on Enchantment (Slot 33)
        renderProperty(gui, "disable-on-enchantment", 1, 33, Material.ENCHANTING_TABLE,
                Map.of("%status%", def.isDisableOnEnchantment() ? "<#EF476F>Blocked</#EF476F>" : "<#06D6A0>Allowed</#06D6A0>"),
                event -> {
                    def.setDisableOnEnchantment(!def.isDisableOnEnchantment());
                    open();
                });
    }

    // =========================================================================
    // PAGE 2: COMBAT & RPG STATS
    // =========================================================================

    private void renderPage2Stats(Gui gui) {
        // Combat Core
        renderStatIcon(gui, StatType.ATTACK_DAMAGE, "attack-damage", 2, 10, Material.IRON_SWORD);
        renderStatIcon(gui, StatType.ATTACK_SPEED, "attack-speed", 2, 11, Material.FEATHER);
        renderStatIcon(gui, StatType.CRIT_RATE, "crit-rate", 2, 12, Material.TARGET);
        renderStatIcon(gui, StatType.CRIT_DAMAGE, "crit-damage", 2, 13, Material.TNT);
        renderStatIcon(gui, StatType.ARROW_DAMAGE, "arrow-damage", 2, 14, Material.ARROW);
        renderStatIcon(gui, StatType.DEFENSE, "defense", 2, 15, Material.SHIELD);
        renderStatIcon(gui, StatType.ARMOR, "armor", 2, 16, Material.IRON_CHESTPLATE);

        // Defensive & RPG Stats
        renderStatIcon(gui, StatType.ARMOR_TOUGHNESS, "armor-toughness", 2, 19, Material.DIAMOND_CHESTPLATE);
        renderStatIcon(gui, StatType.MAX_HEALTH, "max-health", 2, 20, Material.RED_DYE);
        renderStatIcon(gui, StatType.MOVEMENT_SPEED, "movement-speed", 2, 21, Material.SUGAR);
        renderStatIcon(gui, StatType.KNOCKBACK_RESISTANCE, "knockback-resistance", 2, 22, Material.ANVIL);
        renderStatIcon(gui, StatType.DAMAGE_REDUCTION, "damage-reduction", 2, 23, Material.TOTEM_OF_UNDYING);
        renderStatIcon(gui, StatType.PVP_DAMAGE, "pvp-damage", 2, 24, Material.GOLDEN_SWORD);
        renderStatIcon(gui, StatType.PVE_DAMAGE, "pve-damage", 2, 25, Material.CROSSBOW);

        // Specialized PvP & PvE Defense
        renderStatIcon(gui, StatType.PVP_DEFENSE, "pvp-defense", 2, 30, Material.TURTLE_HELMET);
        renderStatIcon(gui, StatType.PVE_DEFENSE, "pve-defense", 2, 32, Material.CARVED_PUMPKIN);
    }

    // =========================================================================
    // PAGE 3: REQUIREMENTS, MECHANICS & SOCKETS
    // =========================================================================

    private void renderPage3Mechanics(Gui gui) {
        // Level Required
        renderProperty(gui, "level-required", 3, 10, Material.EXPERIENCE_BOTTLE,
                Map.of("%value%", String.valueOf(def.getLevelRequired())),
                event -> {
                    if (event.isRightClick()) {
                        def.setLevelRequired(0);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Required Player Level", String.valueOf(def.getLevelRequired()),
                                "Enter minimum required player XP level (>= 0):",
                                input -> {
                                    try {
                                        return Integer.parseInt(input.trim()) >= 0;
                                    } catch (Exception e) {
                                        return false;
                                    }
                                },
                                input -> def.setLevelRequired(Integer.parseInt(input.trim())));
                    }
                });

        // Permission
        renderProperty(gui, "permission", 3, 11, Material.TRIPWIRE_HOOK,
                Map.of("%value%", def.getPermission() != null ? def.getPermission() : "None"),
                event -> {
                    if (event.isRightClick()) {
                        def.setPermission(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Required Permission", def.getPermission(),
                                "Enter permission node (e.g. royitems.use.sword):",
                                null,
                                def::setPermission);
                    }
                });

        // Drop on Death
        renderProperty(gui, "drop-on-death", 3, 12, Material.SKELETON_SKULL,
                Map.of("%status%", def.isDrop() ? "<#06D6A0>Drop on Death</#06D6A0>" : "<#EF476F>Keep on Death</#EF476F>"),
                event -> {
                    def.setDrop(!def.isDrop());
                    open();
                });

        // MMOCore Class
        renderProperty(gui, "mmocore-class", 3, 13, Material.IRON_HELMET,
                Map.of("%value%", def.getMmocoreClass() != null ? def.getMmocoreClass() : "None"),
                event -> {
                    if (event.isRightClick()) {
                        def.setMmocoreClass(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "MMOCore Class", def.getMmocoreClass(),
                                "Enter MMOCore Class name (e.g. WARRIOR, MAGE):",
                                null,
                                def::setMmocoreClass);
                    }
                });

        // MMOCore Level
        renderProperty(gui, "mmocore-level", 3, 14, Material.DRAGON_BREATH,
                Map.of("%value%", String.valueOf(def.getMmocoreLevel())),
                event -> {
                    if (event.isRightClick()) {
                        def.setMmocoreLevel(0);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "MMOCore Level", String.valueOf(def.getMmocoreLevel()),
                                "Enter minimum MMOCore Class level (>= 0):",
                                input -> {
                                    try {
                                        return Integer.parseInt(input.trim()) >= 0;
                                    } catch (Exception e) {
                                        return false;
                                    }
                                },
                                input -> def.setMmocoreLevel(Integer.parseInt(input.trim())));
                    }
                });

        // AuraSkills Skill
        renderProperty(gui, "auraskill", 3, 15, Material.BOOKSHELF,
                Map.of("%value%", def.getAuraSkill() != null ? def.getAuraSkill() : "None"),
                event -> {
                    if (event.isRightClick()) {
                        def.setAuraSkill(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "AuraSkills Skill", def.getAuraSkill(),
                                "Enter AuraSkills Skill name (e.g. FIGHTING, ARCHERY):",
                                null,
                                def::setAuraSkill);
                    }
                });

        // AuraSkills Level
        renderProperty(gui, "auraskill-level", 3, 16, Material.ENCHANTING_TABLE,
                Map.of("%value%", String.valueOf(def.getAuraSkillLevel())),
                event -> {
                    if (event.isRightClick()) {
                        def.setAuraSkillLevel(0);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "AuraSkills Level", String.valueOf(def.getAuraSkillLevel()),
                                "Enter minimum AuraSkills level (>= 0):",
                                input -> {
                                    try {
                                        return Integer.parseInt(input.trim()) >= 0;
                                    } catch (Exception e) {
                                        return false;
                                    }
                                },
                                input -> def.setAuraSkillLevel(Integer.parseInt(input.trim())));
                    }
                });

        // World Required
        renderProperty(gui, "world-required", 3, 19, Material.GRASS_BLOCK,
                Map.of("%value%", def.getWorldRequired().isEmpty() ? "All Worlds" : String.join(", ", def.getWorldRequired())),
                event -> {
                    if (event.isRightClick()) {
                        def.getWorldRequired().clear();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Add Required World", "",
                                "Enter World name to add (e.g. world, world_nether):",
                                null,
                                input -> def.getWorldRequired().add(input.trim()));
                    }
                });

        // Biome Required
        renderProperty(gui, "biome-required", 3, 20, Material.OAK_SAPLING,
                Map.of("%value%", def.getBiomeRequired().isEmpty() ? "All Biomes" : String.join(", ", def.getBiomeRequired())),
                event -> {
                    if (event.isRightClick()) {
                        def.getBiomeRequired().clear();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Add Required Biome", "",
                                "Enter Biome name to add (e.g. plains, desert):",
                                null,
                                input -> def.getBiomeRequired().add(input.trim()));
                    }
                });

        // Gem Sockets (Only on Gear)
        if (def.getType().isGear()) {
            renderProperty(gui, "gem-sockets", 3, 21, Material.EMERALD,
                    Map.of("%value%", def.getGemSockets().isEmpty() ? "None" : def.getGemSockets().toString()),
                    event -> {
                        if (event.isShiftClick() && event.isRightClick()) {
                            def.getGemSockets().clear();
                            open();
                        } else if (event.isRightClick()) {
                            if (!def.getGemSockets().isEmpty()) {
                                def.getGemSockets().remove(def.getGemSockets().size() - 1);
                            }
                            open();
                        } else {
                            manager.getChatInputManager().requestInput(player, def, currentPage,
                                    "Add Gem Socket", "",
                                    "Enter socket identifier (e.g. '1' for any gem, 'A', 'B'...):",
                                    null,
                                    input -> def.getGemSockets().add(input.trim()));
                        }
                    });
        }

        // Cooldown
        renderProperty(gui, "cooldown", 3, 22, Material.CLOCK,
                Map.of("%value%", String.valueOf(def.getCooldown())),
                event -> {
                    if (event.isRightClick()) {
                        def.setCooldown(0.0);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Skill Cooldown", String.valueOf(def.getCooldown()),
                                "Enter cooldown in seconds (e.g. 1.5, 3.0):",
                                input -> {
                                    try {
                                        return Double.parseDouble(input.trim()) >= 0;
                                    } catch (Exception e) {
                                        return false;
                                    }
                                },
                                input -> def.setCooldown(Double.parseDouble(input.trim())));
                    }
                });

        // Expiration
        renderProperty(gui, "expiration", 3, 23, Material.COMPASS,
                Map.of("%value%", def.getExpired() != null ? def.getExpired() : "Permanent"),
                event -> {
                    if (event.isRightClick()) {
                        def.setExpired(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Real-Time Expiration", def.getExpired(),
                                "Enter real-time countdown duration (e.g. 3 days, 12 hours):",
                                null,
                                def::setExpired);
                    }
                });

        // Enchantments
        renderProperty(gui, "enchantments", 3, 24, Material.ENCHANTED_BOOK,
                Map.of("%count%", String.valueOf(def.getEnchantments().size())),
                event -> {
                    if (event.isRightClick()) {
                        def.getEnchantments().clear();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Add Enchantment", "",
                                "Enter: '<enchant> <level>' (e.g. sharpness 5, unbreaking 3):",
                                input -> input != null && !input.trim().isEmpty(),
                                input -> {
                                    String trimmed = input.trim();
                                    int lvl = 1;
                                    String enchPart = trimmed;
                                    int lastSpace = trimmed.lastIndexOf(' ');
                                    if (lastSpace > 0) {
                                        try {
                                            lvl = Integer.parseInt(trimmed.substring(lastSpace + 1).trim());
                                            enchPart = trimmed.substring(0, lastSpace).trim();
                                        } catch (NumberFormatException ignored) {}
                                    } else {
                                        int lastColon = trimmed.lastIndexOf(':');
                                        if (lastColon > 0) {
                                            try {
                                                lvl = Integer.parseInt(trimmed.substring(lastColon + 1).trim());
                                                enchPart = trimmed.substring(0, lastColon).trim();
                                            } catch (NumberFormatException ignored) {}
                                        }
                                    }

                                    String enchRaw = enchPart.toLowerCase().trim().replace('-', '_');
                                    NamespacedKey key = enchRaw.contains(":") ? NamespacedKey.fromString(enchRaw) : NamespacedKey.minecraft(enchRaw);
                                    Enchantment ench = (key != null) ? Registry.ENCHANTMENT.get(key) : null;
                                    if (ench != null && lvl > 0) {
                                        def.getEnchantments().put(ench, lvl);
                                        String displayKey = "minecraft".equalsIgnoreCase(key.getNamespace()) ? key.getKey() : key.toString();
                                        player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Added enchantment &e" + displayKey + " &a(Level " + lvl + ")!"));
                                    } else {
                                        player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Enchantment not found: " + enchPart));
                                    }
                                });
                    }
                });

        // Potion Effects
        renderProperty(gui, "effects", 3, 25, Material.POTION,
                Map.of("%count%", String.valueOf(def.getEffects().size())),
                event -> {
                    if (event.isRightClick()) {
                        def.getEffects().clear();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Add Potion Effect", "",
                                "Enter: '<effect> <level> [slot]' (e.g. speed 2, strength 1 HAND):",
                                input -> input != null && !input.trim().isEmpty(),
                                input -> {
                                    String trimmed = input.trim();
                                    String[] tokens = trimmed.split("\\s+");
                                    String effPart = tokens[0];
                                    int lvl = 1;
                                    String slot = "MAIN_HAND";

                                    if (tokens.length >= 2) {
                                        try {
                                            lvl = Integer.parseInt(tokens[1]);
                                            if (tokens.length >= 3) {
                                                slot = tokens[2].toUpperCase();
                                            }
                                        } catch (NumberFormatException e) {
                                            slot = tokens[1].toUpperCase();
                                        }
                                    }
                                    if (effPart.contains(":") && !effPart.startsWith("minecraft:")) {
                                        String[] sub = effPart.split(":", 2);
                                        effPart = sub[0];
                                        try {
                                            lvl = Integer.parseInt(sub[1]);
                                        } catch (NumberFormatException ignored) {}
                                    }

                                    String effRaw = effPart.toLowerCase().trim().replace('-', '_');
                                    NamespacedKey key = effRaw.contains(":") ? NamespacedKey.fromString(effRaw) : NamespacedKey.minecraft(effRaw);
                                    PotionEffectType type = (key != null) ? Registry.POTION_EFFECT_TYPE.get(key) : null;
                                    if (type != null && lvl > 0) {
                                        def.getEffects().add(new PotionEffectData(type, Math.max(0, lvl - 1), slot));
                                        String displayKey = "minecraft".equalsIgnoreCase(key.getNamespace()) ? key.getKey() : key.toString();
                                        player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Added potion effect &e" + displayKey + " &a(Level " + lvl + ", Slot: " + slot + ")!"));
                                    } else {
                                        player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Potion effect not found: " + effPart));
                                    }
                                });
                    }
                });

        // Armor Trim (Only on Armor)
        if (def.getType().isArmor()) {
            renderProperty(gui, "trim-customization", 3, 30, Material.ARMOR_STAND,
                    Map.of(
                            "%material%", def.getTrimMaterial() != null ? def.getTrimMaterial() : "None",
                            "%pattern%", def.getTrimPattern() != null ? def.getTrimPattern() : "None"
                    ),
                    event -> {
                        if (event.isRightClick()) {
                            def.setTrimMaterial(null);
                            def.setTrimPattern(null);
                        } else {
                            cycleTrim();
                        }
                        open();
                    });

            // Leather Dye Color (Only on Armor)
            renderProperty(gui, "dye-color", 3, 31, Material.CYAN_DYE,
                    Map.of("%value%", def.getDyeColor() != null ? def.getDyeColor() : "None"),
                    event -> {
                        if (event.isRightClick()) {
                            def.setDyeColor(null);
                            open();
                        } else {
                            manager.getChatInputManager().requestInput(player, def, currentPage,
                                    "Leather Dye Color", def.getDyeColor(),
                                    "Enter Hex color code (e.g. #FF0000, #00FF00):",
                                    input -> input.trim().matches("#[0-9A-Fa-f]{6}"),
                                    def::setDyeColor);
                        }
                    });
        }

        // Gem ID (Only on GEM_STONE)
        if (def.getType() == com.github.orenaidaucac.royItems.item.ItemType.GEM_STONE) {
            renderProperty(gui, "gem-id", 3, 32, Material.NETHER_STAR,
                    Map.of("%value%", def.getGemId() != null ? def.getGemId() : "Not set"),
                    event -> {
                        if (event.isRightClick()) {
                            def.setGemId(null);
                            open();
                        } else {
                            manager.getChatInputManager().requestInput(player, def, currentPage,
                                    "Gem Identifier (Gem ID)", def.getGemId(),
                                    "Enter gem ID (e.g. 1, A, FIRE_GEM):",
                                    null,
                                    def::setGemId);
                        }
                    });
        }
    }

    // =========================================================================
    // HELPER METHODS: PROPERTY & STAT RENDERING
    // =========================================================================

    private void renderProperty(Gui gui, String configKey, int expectedPage, int defaultSlot, Material defaultMaterial,
                                Map<String, String> placeholders,
                                dev.triumphteam.gui.components.GuiAction<org.bukkit.event.inventory.InventoryClickEvent> action) {
        EditorGuiConfig.PropertyConfig propCfg = config.getPropertyConfig(configKey,
                new EditorGuiConfig.PropertyConfig(expectedPage, defaultSlot, defaultMaterial, "<#FFD166>" + configKey + "</#FFD166>", List.of(), 0, true));

        // Skip if configured for another page, hidden, or invalid slot
        if (propCfg.page() != currentPage || !propCfg.visible() || propCfg.slot() < 0) return;

        List<Component> lore = config.formatPropertyLore(propCfg.lore(), placeholders);
        GuiItem item = GuiHelper.createGuiItem(
                propCfg.material(),
                TextUtility.formatGui(propCfg.name()),
                lore,
                action
        );
        gui.setItem(propCfg.slot(), item);
    }

    private static final java.text.DecimalFormat STAT_DF = new java.text.DecimalFormat("#,##0.##");

    private void renderStatIcon(Gui gui, StatType type, String configKey, int expectedPage, int defaultSlot, Material defaultMaterial) {
        StatModifier mod = def.getStat(type);
        Map<String, String> placeholders = Map.of(
                "%flat%", STAT_DF.format(mod.getFlat()),
                "%percent%", STAT_DF.format(mod.getPercentValue())
        );

        renderProperty(gui, configKey, expectedPage, defaultSlot, defaultMaterial, placeholders, event -> {
            if (event.isRightClick()) {
                def.setStat(type, null);
                open();
            } else {
                manager.getChatInputManager().requestInput(player, def, currentPage,
                        type.getDefaultDisplayName(),
                        String.format("Flat: %s, Percent: %s%%", STAT_DF.format(mod.getFlat()), STAT_DF.format(mod.getPercentValue())),
                        "Enter new modifier value (e.g. '15' for flat, '+10%' for percent, or '15 10%' for both):",
                        input -> parseStatModifier(input) != null,
                        input -> {
                            StatModifier parsed = parseStatModifier(input);
                            if (parsed != null) {
                                def.setStat(type, parsed);
                            }
                        });
            }
        });
    }

    public static StatModifier parseStatModifier(String input) {
        if (input == null || input.trim().isEmpty()) return null;
        try {
            String s = input.trim();
            if (s.contains(" ")) {
                String[] parts = s.split("\\s+");
                double flat = Double.parseDouble(parts[0].replace("+", ""));
                double percentPoints = Double.parseDouble(parts[1].replace("%", "").replace("+", ""));
                return new StatModifier(flat, percentPoints / 100.0);
            } else if (s.endsWith("%")) {
                double percentPoints = Double.parseDouble(s.replace("%", "").replace("+", ""));
                return new StatModifier(0, percentPoints / 100.0);
            } else {
                double flat = Double.parseDouble(s.replace("+", ""));
                return new StatModifier(flat, 0);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private void cycleMaterial() {
        String cur = def.getMaterial().toUpperCase();
        if (cur.equals("DIAMOND_SWORD")) def.setMaterial("NETHERITE_SWORD");
        else if (cur.equals("NETHERITE_SWORD")) def.setMaterial("BOW");
        else if (cur.equals("BOW")) def.setMaterial("CROSSBOW");
        else if (cur.equals("CROSSBOW")) def.setMaterial("BLAZE_ROD");
        else if (cur.equals("BLAZE_ROD")) def.setMaterial("NETHERITE_CHESTPLATE");
        else if (cur.equals("NETHERITE_CHESTPLATE")) def.setMaterial("SHIELD");
        else if (cur.equals("SHIELD")) def.setMaterial("NETHERITE_PICKAXE");
        else if (cur.equals("NETHERITE_PICKAXE")) def.setMaterial("EMERALD");
        else def.setMaterial("DIAMOND_SWORD");
    }

    private void cycleLoreFormat() {
        String cur = def.getLoreFormat() != null ? def.getLoreFormat() : "default";
        if (cur.equalsIgnoreCase("default")) def.setLoreFormat("weapon");
        else if (cur.equalsIgnoreCase("weapon")) def.setLoreFormat("armor");
        else def.setLoreFormat("default");
    }

    private void cycleTrim() {
        if (def.getTrimMaterial() == null) {
            def.setTrimMaterial("netherite");
            def.setTrimPattern("vex");
        } else if (def.getTrimMaterial().equalsIgnoreCase("netherite")) {
            def.setTrimMaterial("diamond");
            def.setTrimPattern("silence");
        } else if (def.getTrimMaterial().equalsIgnoreCase("diamond")) {
            def.setTrimMaterial("gold");
            def.setTrimPattern("ward");
        } else if (def.getTrimMaterial().equalsIgnoreCase("gold")) {
            def.setTrimMaterial("amethyst");
            def.setTrimPattern("spire");
        } else {
            def.setTrimMaterial(null);
            def.setTrimPattern(null);
        }
    }
}
