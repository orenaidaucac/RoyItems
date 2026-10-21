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
                .disableAllInteractions()
                .create();

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
            player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Đã lấy 1x &f" + def.getId() + " &avào túi đồ!"));
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
                new EditorGuiConfig.ButtonConfig(45, Material.ARROW, "<#FFD166><bold>◀ Quay Lại</bold></#FFD166>", List.of(), 0));
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
                new EditorGuiConfig.ButtonConfig(48, Material.SPECTRAL_ARROW, "<#FFD166>◀ Trang Trước</#FFD166>", List.of(), 0));
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
                    TextUtility.formatGui("<gray>◀ Không có trang trước</gray>"),
                    List.of(TextUtility.formatGui("<dark_gray>Bạn đang ở trang đầu tiên.</dark_gray>")),
                    null
            ));
        }

        // 3. Save button (slot 49)
        EditorGuiConfig.ButtonConfig saveCfg = config.getDetailNavigationButton("save",
                new EditorGuiConfig.ButtonConfig(49, Material.EMERALD_BLOCK, "<#06D6A0><bold>✔ Lưu Thay Đổi</bold></#06D6A0>", List.of(), 0));
        gui.setItem(saveCfg.slot(), GuiHelper.createGuiItem(
                saveCfg.material(),
                TextUtility.formatGui(saveCfg.name()),
                TextUtility.formatGuiLore(saveCfg.lore()),
                event -> {
                    def.setVersion(def.getVersion() + 1);
                    boolean success = manager.getConfigManager().saveItem(def);
                    if (success) {
                        manager.getLiveUpdateService().updateAllOnlinePlayers();
                        player.sendMessage(TextUtility.formatMessage("&a[RoyItems] Đã lưu thành công vật phẩm &f" + def.getId() + " &avà kích hoạt LiveUpdate!"));
                        try {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                        } catch (Throwable ignored) {}
                        open(); // Refresh view
                    } else {
                        player.sendMessage(TextUtility.formatMessage("&c[RoyItems] Lưu thất bại! Vui lòng kiểm tra console server."));
                    }
                }
        ));

        // 4. Next Page button (slot 50)
        EditorGuiConfig.ButtonConfig nextCfg = config.getDetailNavigationButton("next-page",
                new EditorGuiConfig.ButtonConfig(50, Material.SPECTRAL_ARROW, "<#FFD166>Trang Tiếp Theo ▶</#FFD166>", List.of(), 0));
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
                    TextUtility.formatGui("<gray>Không có trang sau ▶</gray>"),
                    List.of(TextUtility.formatGui("<dark_gray>Bạn đang ở trang cuối cùng.</dark_gray>")),
                    null
            ));
        }

        // 5. Close button (slot 53)
        EditorGuiConfig.ButtonConfig closeCfg = config.getDetailNavigationButton("close",
                new EditorGuiConfig.ButtonConfig(53, Material.BARRIER, "<#EF476F><bold>Đóng Menu</bold></#EF476F>", List.of(), 0));
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
    // PAGE 1: CƠ BẢN & HIỂN THỊ (GENERAL & VISUALS)
    // =========================================================================

    private void renderPage1General(Gui gui) {
        // Material
        renderProperty(gui, "material", 1, 10, Material.ANVIL,
                Map.of("%value%", def.getMaterial()),
                event -> {
                    if (event.isRightClick()) {
                        cycleMaterial();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Chất Liệu (Material)", def.getMaterial(),
                                "Nhập tên Material Minecraft (vd: DIAMOND_SWORD, NETHERITE_CHESTPLATE...):",
                                input -> Material.matchMaterial(input.trim()) != null,
                                input -> def.setMaterial(Material.matchMaterial(input.trim()).name()));
                    }
                });

        // Name
        renderProperty(gui, "name", 1, 11, Material.NAME_TAG,
                Map.of("%value%", def.getName().isEmpty() ? "Mặc định theo Type" : def.getName()),
                event -> {
                    if (event.isRightClick()) {
                        def.setName("");
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Tên Hiển Thị (Name)", def.getName(),
                                "Nhập tên mới cho vật phẩm (Hỗ trợ MiniMessage & mã màu):",
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
                                "Thêm Dòng Lore", "",
                                "Nhập dòng mô tả mới muốn thêm vào Lore (Hỗ trợ mã màu):",
                                null,
                                input -> def.getLore().add(input));
                    }
                });

        // Item Model
        renderProperty(gui, "item-model", 1, 13, Material.PAINTING,
                Map.of("%value%", def.getItemModel() != null ? def.getItemModel() : "Mặc định"),
                event -> {
                    if (event.isRightClick()) {
                        def.setItemModel(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Item Model (Custom Model)", def.getItemModel(),
                                "Nhập Item Model ID (vd: custom_sword_1):",
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
                Map.of("%status%", def.isUnbreakable() ? "<#06D6A0>Bật</#06D6A0>" : "<#EF476F>Tắt</#EF476F>"),
                event -> {
                    def.setUnbreakable(!def.isUnbreakable());
                    open();
                });

        // Glow
        renderProperty(gui, "glow", 1, 16, Material.GLOWSTONE_DUST,
                Map.of("%status%", def.isGlow() ? "<#06D6A0>Bật</#06D6A0>" : "<#EF476F>Tắt</#EF476F>"),
                event -> {
                    def.setGlow(!def.isGlow());
                    open();
                });

        // Item Tier
        renderProperty(gui, "item-tier", 1, 20, Material.AMETHYST_SHARD,
                Map.of("%value%", def.getItemTier() != null ? def.getItemTier() : "Không"),
                event -> {
                    if (event.isRightClick()) {
                        def.setItemTier(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Cấp Bậc (Tier)", def.getItemTier(),
                                "Nhập tên Tier (vd: common, rare, epic, legendary, mythic):",
                                null,
                                input -> def.setItemTier(input.toLowerCase().trim()));
                    }
                });

        // Item Set
        renderProperty(gui, "item-set", 1, 21, Material.CHAINMAIL_CHESTPLATE,
                Map.of("%value%", def.getItemSet() != null ? def.getItemSet() : "Không"),
                event -> {
                    if (event.isRightClick()) {
                        def.setItemSet(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Bộ Trang Bị (Set)", def.getItemSet(),
                                "Nhập tên Set (vd: dragon_slayer, shadow_assassin):",
                                null,
                                input -> def.setItemSet(input.toLowerCase().trim()));
                    }
                });

        // Hide Enchantments
        renderProperty(gui, "hide-enchantments", 1, 23, Material.ENCHANTED_BOOK,
                Map.of("%status%", def.isHideEnchantments() ? "<#06D6A0>Đang ẩn</#06D6A0>" : "<#EF476F>Đang hiện</#EF476F>"),
                event -> {
                    def.setHideEnchantments(!def.isHideEnchantments());
                    open();
                });

        // Hide Attributes
        renderProperty(gui, "hide-attributes", 1, 24, Material.COMPASS,
                Map.of("%status%", def.isHideAttributes() ? "<#06D6A0>Đang ẩn</#06D6A0>" : "<#EF476F>Đang hiện</#EF476F>"),
                event -> {
                    def.setHideAttributes(!def.isHideAttributes());
                    open();
                });
    }

    // =========================================================================
    // PAGE 2: CHỈ SỐ CHIẾN ĐẤU & RPG (COMBAT & RPG STATS)
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
    // PAGE 3: YÊU CẦU, CƠ CHẾ & SOCKET (REQUIREMENTS & MECHANICS)
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
                                "Cấp Độ Yêu Cầu (Player Level)", String.valueOf(def.getLevelRequired()),
                                "Nhập cấp độ kinh nghiệm (XP Level >= 0) tối thiểu:",
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
                Map.of("%value%", def.getPermission() != null ? def.getPermission() : "Không"),
                event -> {
                    if (event.isRightClick()) {
                        def.setPermission(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Quyền Hạn (Permission)", def.getPermission(),
                                "Nhập Permission node (vd: royitems.use.sword):",
                                null,
                                def::setPermission);
                    }
                });

        // Drop on Death
        renderProperty(gui, "drop-on-death", 3, 12, Material.SKELETON_SKULL,
                Map.of("%status%", def.isDrop() ? "<#06D6A0>Có rơi</#06D6A0>" : "<#EF476F>Không rơi (Bảo vệ)</#EF476F>"),
                event -> {
                    def.setDrop(!def.isDrop());
                    open();
                });

        // MMOCore Class
        renderProperty(gui, "mmocore-class", 3, 13, Material.IRON_HELMET,
                Map.of("%value%", def.getMmocoreClass() != null ? def.getMmocoreClass() : "Không"),
                event -> {
                    if (event.isRightClick()) {
                        def.setMmocoreClass(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Class MMOCore", def.getMmocoreClass(),
                                "Nhập tên Class MMOCore (vd: WARRIOR, MAGE):",
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
                                "Cấp MMOCore", String.valueOf(def.getMmocoreLevel()),
                                "Nhập cấp độ Class MMOCore tối thiểu (>= 0):",
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
                Map.of("%value%", def.getAuraSkill() != null ? def.getAuraSkill() : "Không"),
                event -> {
                    if (event.isRightClick()) {
                        def.setAuraSkill(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Kỹ Năng AuraSkills", def.getAuraSkill(),
                                "Nhập tên Skill AuraSkills (vd: FIGHTING, ARCHERY):",
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
                                "Cấp AuraSkills", String.valueOf(def.getAuraSkillLevel()),
                                "Nhập cấp độ AuraSkills tối thiểu (>= 0):",
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
                Map.of("%value%", def.getWorldRequired().isEmpty() ? "Tất cả" : String.join(", ", def.getWorldRequired())),
                event -> {
                    if (event.isRightClick()) {
                        def.getWorldRequired().clear();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Thêm Thế Giới", "",
                                "Nhập tên Thế giới muốn thêm (vd: world, world_nether):",
                                null,
                                input -> def.getWorldRequired().add(input.trim()));
                    }
                });

        // Biome Required
        renderProperty(gui, "biome-required", 3, 20, Material.OAK_SAPLING,
                Map.of("%value%", def.getBiomeRequired().isEmpty() ? "Tất cả" : String.join(", ", def.getBiomeRequired())),
                event -> {
                    if (event.isRightClick()) {
                        def.getBiomeRequired().clear();
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Thêm Quần Xã (Biome)", "",
                                "Nhập tên Quần xã muốn thêm (vd: plains, desert):",
                                null,
                                input -> def.getBiomeRequired().add(input.trim()));
                    }
                });

        // Gem Sockets (Only on Gear)
        if (def.getType().isGear()) {
            renderProperty(gui, "gem-sockets", 3, 21, Material.EMERALD,
                    Map.of("%value%", def.getGemSockets().isEmpty() ? "Không có" : def.getGemSockets().toString()),
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
                                    "Thêm Lỗ Khảm (Socket)", "",
                                    "Nhập mã socket (vd: '1' cho ngọc bất kỳ, 'A', 'B'...):",
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
                                "Hồi Chiêu Kỹ Năng", String.valueOf(def.getCooldown()),
                                "Nhập thời gian hồi chiêu tính bằng giây (vd: 1.5, 3.0):",
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
                Map.of("%value%", def.getExpired() != null ? def.getExpired() : "Vĩnh viễn"),
                event -> {
                    if (event.isRightClick()) {
                        def.setExpired(null);
                        open();
                    } else {
                        manager.getChatInputManager().requestInput(player, def, currentPage,
                                "Hạn Dùng Real-Time", def.getExpired(),
                                "Nhập hạn dùng đếm lùi thời gian thực (vd: 3 days, 12 hours):",
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
                                "Thêm Phù Phép (Enchantment)", "",
                                "Nhập: '<Tên enchant> <Cấp>' (vd: sharpness 5, unbreaking 3):",
                                input -> {
                                    String[] parts = input.trim().split("\\s+");
                                    return parts.length >= 2;
                                },
                                input -> {
                                    String[] parts = input.trim().split("\\s+");
                                    Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(parts[0].toLowerCase()));
                                    if (ench != null) {
                                        int lvl = Integer.parseInt(parts[1]);
                                        def.getEnchantments().put(ench, lvl);
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
                                "Thêm Hiệu Ứng Bùa Chú", "",
                                "Nhập: '<Tên effect> <Cấp>' (vd: SPEED 2, STRENGTH 1):",
                                input -> {
                                    String[] parts = input.trim().split("\\s+");
                                    return parts.length >= 2;
                                },
                                input -> {
                                    String[] parts = input.trim().split("\\s+");
                                    PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(parts[0].toLowerCase()));
                                    if (type != null) {
                                        int amp = Integer.parseInt(parts[1]) - 1;
                                        def.getEffects().add(new PotionEffectData(type, Math.max(0, amp), "MAIN_HAND"));
                                    }
                                });
                    }
                });

        // Armor Trim (Only on Armor)
        if (def.getType().isArmor()) {
            renderProperty(gui, "trim-customization", 3, 30, Material.ARMOR_STAND,
                    Map.of(
                            "%material%", def.getTrimMaterial() != null ? def.getTrimMaterial() : "Không",
                            "%pattern%", def.getTrimPattern() != null ? def.getTrimPattern() : "Không"
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
                    Map.of("%value%", def.getDyeColor() != null ? def.getDyeColor() : "Không"),
                    event -> {
                        if (event.isRightClick()) {
                            def.setDyeColor(null);
                            open();
                        } else {
                            manager.getChatInputManager().requestInput(player, def, currentPage,
                                    "Màu Nhuộm Giáp Da", def.getDyeColor(),
                                    "Nhập mã màu Hex (vd: #FF0000, #00FF00):",
                                    input -> input.trim().matches("#[0-9A-Fa-f]{6}"),
                                    def::setDyeColor);
                        }
                    });
        }

        // Gem ID (Only on GEM_STONE)
        if (def.getType() == com.github.orenaidaucac.royItems.item.ItemType.GEM_STONE) {
            renderProperty(gui, "gem-id", 3, 32, Material.NETHER_STAR,
                    Map.of("%value%", def.getGemId() != null ? def.getGemId() : "Chưa đặt"),
                    event -> {
                        if (event.isRightClick()) {
                            def.setGemId(null);
                            open();
                        } else {
                            manager.getChatInputManager().requestInput(player, def, currentPage,
                                    "Mã Nhận Diện Ngọc (Gem ID)", def.getGemId(),
                                    "Nhập mã ngọc (vd: 1, A, FIRE_GEM):",
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
                        "Nhập giá trị mới (vd: '15' cho cộng thẳng, '+10%' cho phần trăm, hoặc '15 10%' cho cả hai):",
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
