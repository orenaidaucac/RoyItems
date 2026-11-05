# ⚔️ RoyItems — Changelog

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
