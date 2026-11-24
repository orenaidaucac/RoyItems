# ⚔️ RoyItems — Changelog

## [2026.1.10] - 2026-11-24

### 🌟 Highlights
* **Smithing Table Corruption Protection:** Completely prevents players from corrupting RoyItems via Netherite upgrades or Armor Trims in Smithing Tables, eliminating PDC data loss and vanilla-ization.
* **Grindstone Disenchanting Protection:** Prevents stripping pre-configured enchantments from RoyItems and blocks combining items that would erase custom metadata, completely neutralizing EXP exploitation.
* **Advanced Extraction Protection (`remove-enchants`):** Limits CONSUMABLE enchantment removal strictly to player/admin-added enchantments, providing absolute protection for pre-configured default enchantments and the `glow: true` glint effect.
* **Flexible Downgrade Configuration (Option 3):** Server administrators can configure whether upgraded default enchantments are locked completely (default) or can be downgraded back to their default tier with configurable book return levels.

---

### ➕ Added
* **Smithing Table Security (`ItemProtectionListener`):**
  * Blocks placing, shift-clicking, number-key swapping, or dragging RoyItems into Smithing Table input slots.
  * Clears result slot (`setResult(null)`) on `PrepareSmithingEvent` with `EventPriority.HIGHEST`.
  * Configurable toggle `smithing-table.prevent-corrupt: true` and localized message `smithing-table-blocked`.
* **Grindstone Disenchanting Security:**
  * Checks pre-configured enchantments via `ItemDefinition.getEnchantments()` at runtime.
  * Blocks result pickup with 0 EXP awarded (`EventPriority.HIGHEST`).
  * Blocks combining two items if either item is a RoyItem to prevent PDC wipe.
  * Configurable toggle `grindstone.prevent-disenchant-configured: true` and localized message `grindstone-disenchant-blocked`.
* **CONSUMABLE Remove-Enchants Default Enchantment Protection:**
  * Live dynamic resolution from `ItemRegistry` ensuring real-time accuracy upon `/royitems reload`.
  * Whitelist and empty removable enchantment check prevents opening the GUI when an item only possesses default enchantments.
  * Auto-cancellation of drag-and-drop interaction with inventory synchronization (`player.updateInventory()`), preventing dupe exploits and consumable consumption.
  * Configurable extraction section in `config.yml`: `protect-default-enchantments`, `allow-downgrade-to-default`, and `downgrade-book-level`.
  * Added 4 new message keys across all 7 supported language files (`en-US`, `vi-VN`, `de-DE`, `es-ES`, `ja-JP`, `ko-KR`, `ru-RU`).

---

### ⚡ Improved
* **Cosmetic Glow Preservation:**
  * Automated filtering of dummy glow enchantments (`LUCK_OF_THE_SEA 1`) from the extraction candidate list.
  * Guaranteed persistence of `glow: true` glint override across extraction, lore synchronization, and item resets.
* **Server Performance & Safe Reloads:**
  * Full thread safety on Paper, Purpur, Folia, and Leaf region threads.
  * Clean reload lifecycle hook in `ExtractionManager.loadConfig()`.

---

## [2026.1.9] - 2026-11-23

### 🌟 Highlights
* **Advanced Shift-Click Mass Crafting:** Eliminated the artificial 64-item cap per shift-click craft operation in Crafting Stations. Players can now craft the exact maximum quantity supported by their raw materials and available inventory space in a single click (yielding hundreds or thousands of items across multiple stacks).
* **Zero-Drop Guarantee & Inventory Pre-Checks:** Integrated high-speed inventory capacity simulation preventing item spills on the ground. If inventory space is completely full, crafting halts safely with an instant localized message without consuming any ingredients.
* **Real-Time Dynamic Enchantment Lore Synchronization:** The `#enchantments#` placeholder now automatically updates lore in real time whenever enchantments are added or altered via Anvils, Enchanting Tables, Drag & Drop books, Admin commands, or Plugin APIs.
* **Enchantment Extraction System (`CONSUMABLE`):** Introduced a drag-and-drop enchantment extractor mechanic. Players can extract single Vanilla or EcoEnchants enchantments off gear into functional Enchanted Books through an interactive GUI with configurable success rates and item whitelists.
* **EcoEnchants Voucher Rewards & Gacha Integration:** Upgraded `type: VOUCHER` to award randomized EcoEnchants books filtered by Rarity or Type ID, with dynamic level boundaries and animated gacha roll effects.

---

### ➕ Added
* **Shift-Click Mass Crafting Engine:**
  * Sub-millisecond pre-calculation (`SimulatedSlot`) that accurately calculates open inventory slots, partial stack merges, and maximum item stack sizes.
  * Multi-stack item generation directly into player inventory without dropping surplus items onto the ground.
  * Added `craft-failed-inventory-full` message across all 7 supported language files (`en-US`, `vi-VN`, `zh-CN`, `es-ES`, `ru-RU`, `ja-JP`, `ko-KR`).
  * Automatic ingredient pulling from both Player Inventory and **ExtraStorage** with full atomic transaction rollback safety.
* **Consumable Enchantment Extraction:**
  * Enabled `remove-enchants` feature for `type: CONSUMABLE` items.
  * Drag-and-drop activation on equipment opens an interactive, responsive extraction GUI.
  * Each enchantment is rendered as an interactive book with Roman numeral tiers, localized names, and rarity indicators.
  * Configurable `success-chance`, `book-return-chance`, and `consume-on-failure`.
  * Configurable `item-whitelist` (e.g. `SWORD`, `AXE`, `BOW`, `HELMET`, `CHESTPLATE`, etc.) preventing unauthorized item extraction.
* **EcoEnchants Voucher Rewards:**
  * Support for `ecoenchants.rarity` and `ecoenchants.type` in voucher reward definitions.
  * Customizable `min-level` and `max-level` parameters with automatic verification against EcoEnchants registration limits.
  * Creates fully authentic enchanted books with EcoEnchants PDC metadata and formatted lore.
* **Dynamic Language Configuration Migration:**
  * Intelligent configuration migration engine that scans disk language files on startup or reload.
  * Automatically injects missing localization keys from new plugin versions while preserving existing comments, indentation, and custom admin translations.

---

### ⚡ Improved
* **Authoritative Enchantment Lore Updating:**
  * Rebuilt `#enchantments#` processor to immediately reflect enchantment changes across Anvils, Enchanting Tables, command upgrades, and external plugins.
  * Implemented an internal glow-filter (`getEffectiveEnchantments`) that hides synthetic glowing enchantments (e.g. `LUCK_OF_THE_SEA` with hidden flags) from visible lore lines.
  * Real-time sync on hand-switch, inventory interaction, and item repair.
* **Crafting Station Performance:**
  * Shift-click calculations execute in memory with integer simulation, incurring zero garbage collection pressure or TPS impact even during massive thousand-item crafts.
  * Unified storage deduction ensures player inventory items are exhausted before consuming remote ExtraStorage items.

---

### 🐛 Bug Fixes
* **Fixed Shift-Click 64-Item Crafting Ceiling:** Removed the hardcoded single-stack ceiling that prevented players with ample inventory space from mass-crafting items.
* **Fixed Static Enchantment Lore Desync:** Resolved an issue where combining books in an Anvil or enchanting items in an Enchanting Table left custom `#enchantments#` lore unrefreshed until a full server reload.
* **Fixed Glowing Enchantment Pollution:** Filtered internal cosmetic glow enchantments from generating phantom enchantment lines in custom item lore.
* **Fixed Ground Item Spills in Crafting Stations:** Prevented overflow items from dropping on the ground when crafting large amounts with limited inventory space.

---

### 🔌 Compatibility
* **Minecraft / Server Software:** Paper, Purpur, Folia, Leaf, and Spigot 1.21.4+ (Java 21+).
* **Soft Dependencies:** EcoEnchants, ExtraStorage, AdvancedOreGen, MythicMobs 5.x, MMOCore, AuraSkills, Vault, PlaceholderAPI, WorldGuard, Nexo, ItemsAdder, Oraxen, and Citizens/FancyNpcs/PhoenixNPCs.

---

## [2026.1.8] - 2026-11-15

### 🐛 Bug Fixes
* **Fixed Ghost Blocks in AdvancedOreGen Mine Radius:** Completely eliminated invisible/ghost blocks appearing after using `mine-radius` tools on cobblestone and ore generators. Mined generator blocks now regenerate immediately with authoritative client-server block synchronization and a 1-tick verification pass.
* **Fixed Voucher Air Right-Click:** Resolved an issue where `type: VOUCHER` items could only be redeemed when right-clicking a block. Vouchers can now be freely used in both mid-air and on blocks using either the Main Hand or Off Hand.
* **Fixed Client Inventory Sync on Voucher Consumption:** Player hand stacks now decrement and synchronize with the server immediately upon voucher use, preventing ghost items from visually remaining in inventory.
* **Prevented Mining Race Conditions & Item Duplication:** Added concurrent block-locking during multi-block mining passes, preventing multiple players from exploiting overlapping radius breaks or causing fluid flow desyncs.

### ⚡ Improvements
* **Real-Time Multi-Client Block Updates:** Broadcasts authoritative block changes to all nearby tracking players within 48 blocks whenever wide-area mining modifies block states.
* **Folia & Leaf Multi-Threading Safety:** Hardened region boundary checks and chunk loading validations to guarantee zero asynchronous block access or server stalls across Paper, Purpur, Folia, and Leaf.
* **Expanded bStats Telemetry:** Added custom metrics tracking for all 15 active server integrations, including PhoenixNPCs, ExtraStorage, LuckPerms, and Economy providers.

### 🔌 Integrations
* **PhoenixNPCs Support:** Fully integrated with PhoenixNPCs, allowing players to open custom Crafting Stations by interacting with configured NPC IDs (alongside Citizens and FancyNPCs).
* **Hardened AdvancedOreGen Hook:** Optimized generator recognition and direct in-place block replacement to preserve water and lava mechanics during continuous generator mining.

---

## [2026.1.7] - 2026-11-11

### ✨ New Features
* **Animated Title Gacha for Vouchers:** Random currency vouchers now trigger an exciting rolling title animation with dynamic sounds and fireworks upon opening.
* **Multi-Economy Voucher Support:** Vouchers now seamlessly grant Vault money, PlayerPoints, and custom server economy currencies alongside custom console and player commands.
* **Permission-Based Player Stats:** Server owners can grant permanent or temporary stat bonuses (such as extra health, damage, or speed) directly through permissions without requiring players to hold or wear items.
* **Dynamic Item Set Lore:** Armor set items now automatically switch between active and inactive set bonus descriptions in real-time as pieces are equipped or unequipped.
* **Custom Currency Display:** Full control over friendly currency names, symbols, and number formatting to keep technical IDs hidden from players.

### ⚡ Improvements
* **Reliable Voucher Commands:** Vouchers now execute multiple commands in sequence as either the server console or the player with full placeholder support.
* **Safe Voucher Transactions:** Protected voucher redemption with anti-spam safeguards and automatic item protection so players never lose rewards if a transaction fails.
* **Set Bonus Equip Notifications:** Optional chat messages inform players whenever they activate or lose an item set bonus.
* **Instant Stat Recalculations:** Player stats granted via permissions update instantly upon rank changes, player logins, and configuration reloads.

### 🐛 Bug Fixes
* **Fixed Voucher Commands Not Running:** Resolved an issue where commands configured on voucher items were not executed when redeemed.
* **Fixed Storage Sync in Crafting Stations:** Fixed an issue where crafting stations showed zero ingredients from player storage after a server restart until reloaded.
* **Fixed Item Duplication on Fast Voucher Clicks:** Prevented players from rapidly clicking vouchers during opening animations to avoid duplicate reward payouts.

---

## [2026.1.6] - 2026-11-05

### 🌟 Highlights
* **Mine Radius & Zero-Lag ExtraStorage Mining:** Added `mine-radius` stat supporting configurable 2D horizontal mining areas (3×3, 5×5, 7×7, 9×9, 11×11+), batching and depositing mined drops directly into ExtraStorage without entity drop lag.
* **AdvancedOreGen In-Place Regeneration:** Mined generator blocks between water, lava, and fences are directly regenerated in-place using AdvancedOreGen's generation pipeline, preventing liquid flow disruption and generator breakage.
* **EcoEnchants & Dynamic Enchantments:** Native support for Vanilla and EcoEnchants enchantments inside `#enchantments#` lore placeholders with real-time anvil synchronization and customizable formatting.
* **Anvil Rename Protection:** Built-in anvil protection blocks unauthorized item renaming while safely preserving repairing, combining, and enchanting operations.
* **MythicMobs Skill Defense Mitigation:** MMORPG Defense formula now intercepts and mitigates incoming MythicMobs Skill Mechanic damage with zero double-mitigation.
* **Fine-Grained LiveUpdate Preservation:** Admins can now configure exactly which item properties (gems, durability, custom lore, enchantments, tiers, PDC) are preserved or synchronized on reload.

---

### ➕ Added
* **Mine Radius System (`mine-radius`):**
  * New `mine-radius` stat supporting expandable horizontal tiers: Tier 1 (3×3), Tier 2 (5×5), Tier 3 (7×7), Tier 4 (9×9), Tier 5 (11×11), and beyond.
  * Strict horizontal 2D plane mining ($Y = Y_0$) — never digs into ceilings or floors.
  * Full configuration in `config.yml`: `allowed-blocks`, `allowed-tools`, `activation-chance`, `cooldown-ms`, and `limits`.
  * `#mine-radius#` placeholder in `lore-format` with customizable area formatting (`%area%`, `%radius%`, e.g. `3×3`, `5×5`).
  * Ingame GUI Editor integration: slot 31 on Page 2 with interactive chat prompt.
  * Direct **ExtraStorage** batch integration: automatically deposits all mined drops directly into player storage, eliminating ground item entities and TPS drops.
* **AdvancedOreGen Soft-Hook Integration:**
  * Detects SkyBlock generator blocks (fence-water, cobble, basalt) and triggers in-place regeneration via AdvancedOreGen without converting blocks to AIR.
  * Keeps water/fence generator setups fully functional during continuous 24/24 mining.
* **EcoEnchants Integration:**
  * Full integration with modern EcoEnchants via reflection-safe hook.
  * Automatically resolves EcoEnchants custom enchantments, localized names, tier styling, and formatting in `#enchantments#`.
* **Anvil Rename Protection:**
  * Added `anvil.prevent-rename` in `config.yml` (default `true`) and per-item override `prevent-anvil-rename`.
  * Blocks unauthorized renaming in Anvils while allowing repairs and enchantments.
* **Fine-Grained LiveUpdate Preservation:**
  * Independent preservation toggles for `enchantments`, `gems`, `durability`, `display-name`, `lore`, `tier`, `custom-model-data`, `pdc`, and `attributes`.

---

### ⚡ Improved
* **MythicMobs Skill Damage Mitigation:**
  * Intercepts `MythicDamageEvent` from MythicMobs skill mechanics and applies RoyItems defense formulas.
  * Deduplication guards prevent double-mitigation between skill events and entity damage events.
* **Dynamic Lore Performance:**
  * Smart line pruning and batch enchantment formatting with hide-vanilla-tooltip option.
* **Server Performance & Folia Safety:**
  * Regionized scheduler thread verification (`Bukkit.isOwnedByCurrentRegion`) ensures complete multi-threaded safety on Folia servers.

---

### 🐛 Fixed
* **Fixed AdvancedOreGen Generator Block Freezing:**
  * Resolved an issue where Mine Radius set generator blocks to `Material.AIR, false`, freezing fluid updates and permanently disabling water/fence ore generators.
* **Fixed Anvil Rename Bypasses:**
  * Prevented bypasses via color codes, unicode, or shift-clicking output slots.
* **Fixed Vanilla Enchantment Tooltip Clutter:**
  * Automatically hides default vanilla grey enchantments when `#enchantments#` is configured, preventing duplicated lore lines.

---

### 🔌 Compatibility
* **Minecraft / Server Software:** Paper, Purpur, Spigot, and Folia 1.21.4+ (Java 21+).
* **Soft Dependencies:** Verified and tested with AdvancedOreGen, EcoEnchants, ExtraStorage, MythicMobs 5.x, MMOCore, AuraSkills, Vault, PlaceholderAPI, WorldGuard, Nexo, ItemsAdder, Oraxen, and Citizens/FancyNpcs.

---

## [2026.1.5] - 2026-11-03

### 🌟 Highlights
* **ExtraStorage Crafting Integration:** Crafting Stations now seamlessly pull ingredients from both the player's Inventory and **ExtraStorage**, prioritizing inventory first with atomic transaction safety.
* **Upgraded Dynamic Recipe Lore:** Real-time `%storage_have%` and unified `%have%` placeholders display live material counts in crafting menus.
* **MMORPG DPS & Dummy Damage Overhaul:** Completely solved packet-entity cooldown resets, fixed the 10x damage drop, and introduced MMORPG-standard combat duration DPS metering.

---

### ✨ New Features & Enhancements

* **ExtraStorage Native Integration:**
  * Crafting Stations automatically detect ingredients stored inside player's **ExtraStorage** alongside their regular inventory.
  * **Inventory-First Consumption:** When crafting, items are deducted from the player's inventory first; only the remaining shortage is taken from ExtraStorage.
  * **Atomic Transaction Safety:** Strictly validates all recipe ingredients prior to consumption to eliminate any risk of item duplication, item loss, or partial deduction if storage transactions fail.
  * **Seamless Fallback:** When ExtraStorage is not installed or disabled, crafting stations automatically fallback to standard player inventory.

* **Enhanced Dynamic Lore Placeholders:**
  * `%have%`: Displays the total combined quantity of the ingredient across **Inventory + ExtraStorage**.
  * `%storage_have%`: Displays the exact quantity available in **ExtraStorage**.
  * `%inv_have%`: Displays the quantity present directly in the player's **Inventory**.
  * Dynamic recipe lore updates immediately in real-time without caching lag or performance overhead.

* **MMORPG DPS Metering & Dummy Damage Accuracy:**
  * **True Combat Duration DPS:** DPS calculation now follows MMORPG standards, measuring the exact combat interval between the first strike and the last blow. When combat ends, the final DPS score remains frozen during the reset countdown so players can accurately inspect their combat output.
  * **True Cooldown Reconstruction:** Solved Paper's arm-swing packet ticker reset on packet entities by computing authentic weapon cooldown using `generic.attack_speed` and physical elapsed intervals. Full swings reliably deal 100% full attack damage with enchantments and jump crits.
  * **Unified Outgoing Damage:** Base attack power now references the player's full `generic.attack_damage` attribute, incorporating vanilla material damage, RoyItems stats, potion effects, and third-party buffs.
  * **`DummyDamageEvent` API:** Fired whenever a player damages a training dummy, allowing third-party plugins to inspect, scale, or cancel dummy strikes cleanly.
  * **Projectile Deduplication & State Validation:** Dead, spectator, or out-of-range players cannot damage dummies, and projectile metadata safeguards prevent duplicate hits from arrows or thrown tridents.

---

### 🐛 Bug Fixes

* **Fixed 10x Damage Reduction on Training Dummies:** Fixed a critical bug where attacking a packet dummy triggered client arm-swing packets that reset Paper's attack ticker to 0, causing `getAttackCooldown()` to report 0.04 and cutting damage output by 10x.
* **Fixed Double DPS on First Hit:** Fixed an issue where the first strike on a dummy was divided by 0.5s, displaying double the actual DPS. First strikes now correctly report the hit damage.
* **Fixed Projectile Collision Double Hits:** Resolved an edge-case where arrows landing near a dummy could trigger both the continuous raytrace ticker and the projectile hit fallback simultaneously.

---

## [2026.1.4] - 2026-11-01

### 🌟 Highlights
* **Crafting Station GUI Engine:** Create unlimited custom workstations and forges with multi-currency recipes, client-language translation, and NPC support.
* **Packet-Based Training Dummy System:** Test weapon damage and DPS with client-side packet entities and live Text Display holograms—zero tick overhead on your server!
* **Combat & Attribute Accuracy Overhaul:** Suppressed vanilla material attribute drift and eliminated double-counting bugs on attack damage.

---

### ✨ New Features & Enhancements

* **Modular Crafting Stations (`crafting-station/<station_id>.yml`):**
  * Create unlimited crafting stations with customizable GUI sizes (9 to 54 slots).
  * Menu titles fully support MiniMessage, Hex colors, and PlaceholderAPI.
  * Define custom command shortcuts with dynamic tab-completion that register automatically upon config reload.
  * Native NPC binding: Right-click NPCs from **FancyNpcs** or **Citizens** to open designated stations instantly.
  * Decorative background items supporting slot ranges (e.g. `1-9`), clickable actions (`[player]`, `[console]`, `[sound]`), and textures from Vanilla, Nexo, ItemsAdder, Oraxen, HeadDatabase, and custom Base64 heads.
  * Multi-currency & multi-item recipe inputs and outputs:
    * RoyItems custom items (`royitems[...]`)
    * Vanilla materials (`vanilla[...]`)
    * Vault economy (`money=X`)
    * Experience points (`exp=X`)
    * PlayerPoints (`point=X`)
    * ExcellentEconomy currencies (`excellenteconomy:<id>=X`)
  * Dynamic client-side language translation: Vanilla item names in recipes and lore adapt to each player's Minecraft client language setting.

* **Packet-Based Training Dummies & DPS Holograms:**
  * Spawn packet-based dummies anywhere with `/ri dummy spawn <dummy_id> [mob_type]`.
  * Runs 100% client-side via PacketEvents / ProtocolLib with zero impact on server main thread or TPS.
  * Immune to `/kill @e`, clear-lag plugins, entity purges, and world mob sweeps.
  * WorldGuard bypass: Dummies spawn and register hits seamlessly inside protected regions, spawns, and PvP-disabled zones.
  * **Comprehensive Outgoing Damage Engine:** Accurately recognizes the player's true final output damage when attacking the dummy, accounting for weapon attack cooldown, enchantments (Sharpness, Smite on undead, Bane of Arthropods, Impaling), Strength and Weakness potion effects, vanilla jump critical hits, Mace fall-distance smash attacks, and RoyItems RPG stats.
  * **Real-Time Projectile Hit Detection:** Full support for ranged combat against dummies! Arrows shot from Bows/Crossbows and thrown Tridents are tracked in real time, detecting mid-air collisions with the dummy and calculating projectile velocity, Power/Impaling enchantments, and arrow stats.
  * **Removed Right-Click Attacks:** Right-clicking dummies is completely blocked from registering attacks. Damage is exclusively triggered by legitimate melee strikes and projectile hits.
  * Text Display Holograms: Track and display real-time **DPS**, **Total Damage**, **Last Hit**, and **Hit Count** with MiniMessage and color gradients.
  * Per-dummy YAML configuration in `plugins/RoyItems/dummy/<dummy_id>.yml` for idle/combat lines, billboard angles, shadows, and background colors.
  * Safe hot-reload: No ghost entities or detached holograms when reloading via `/ri reload` or PlugmanX.

* **Attribute Isolation & Precision:**
  * Default vanilla armor and weapon attributes (inherent armor points on armor and damage on swords) are now cleanly suppressed, ensuring players only receive stats explicitly configured in RoyItems.
  * Clean number formatting on dummy holograms: whole numbers display cleanly (e.g. `6` instead of `6.0`), while decimals display with one decimal place (e.g. `6.5`).

---

### 🐛 Bug Fixes

* **Fixed Attack Damage Double-Counting:** Fixed an issue where attacking a training dummy with a weapon having `attack-damage: 6` displayed `13` damage due to the flat modifier being counted both in the player's attribute instance and the stat calculator. Weapons now deal and display exactly their configured damage.
* **Fixed Crafted Item Stacking:** Fixed an issue where items crafted from stations could not stack together due to subtle NBT divergence. Crafted outputs now stack properly.
* **Fixed Max-Health Stat Application:** Resolved an issue where the `max-health` attribute was not properly adding health when items were equipped in certain armor slots or hand slots.
* **Fixed Client Disconnect on Packet Dummy Spawn:** Resolved a client protocol error on modern Minecraft versions by synchronizing Text Display packet metadata indices to modern 1.20+/1.21+ standards.

---

### 📋 New Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ri station open <station_id> [player]` | `royitems.command.station.open` | Opens a crafting station GUI directly |
| `/ri station list` or `/ri stations` | `royitems.command.station.list` | Opens the global Crafting Stations list menu |
| `/ri dummy spawn <dummy_id> [mob_type]` | `royitems.command.dummy` | Spawns a packet-based training dummy centered at target block |
| `/ri dummy despawn <dummy_id\|all>` | `royitems.command.dummy` | Despawns a specific training dummy or all active dummies |
