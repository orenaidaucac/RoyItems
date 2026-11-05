# ⚔️ RoyItems — Advanced MMORPG Custom Items Engine

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x%20%7C%201.21.4%2B-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft" />
  <img src="https://img.shields.io/badge/Platforms-Paper%20%7C%20Spigot%20%7C%20Purpur%20%7C%20Folia-orange?style=for-the-badge" alt="Platforms" />
  <img src="https://img.shields.io/badge/Java-21%2B-ea76cb?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Release-v2026.1.6-blue?style=for-the-badge&logo=git" alt="Version" />
  <img src="https://img.shields.io/badge/Modrinth-RoyItems-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth" />
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/bstats/servers/34832?style=for-the-badge&logo=chartdotjs&logoColor=white&color=007ec6" alt="bStats Servers" /></a>
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/bstats/players/34832?style=for-the-badge&logo=minecraft&logoColor=white&color=007ec6" alt="bStats Players" /></a>
  <img src="https://img.shields.io/badge/Build-Maven-informational?style=for-the-badge&logo=apachemaven" alt="Maven" />
</p>

<p align="center">
  <a href="https://github.com/orenaidaucac/RoyItems"><img src="https://img.shields.io/badge/GitHub-Wiki_&_Documentation-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Wiki" /></a>
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/badge/bStats-Live_Telemetry_Dashboard-007ec6?style=for-the-badge&logo=chartdotjs&logoColor=white" alt="bStats Dashboard" /></a>
  <a href="https://modrinth.com/plugin/royitems"><img src="https://img.shields.io/badge/Modrinth-Download_Release-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Download" /></a>
</p>

> **RoyItems** is an enterprise-grade, high-performance MMORPG Custom Items Engine engineered for Minecraft 1.21+ / 26.2+ servers running on **Paper**, **Purpur**, or **Folia** (multi-threaded regionized architecture). Define unlimited custom items, complex RPG stats, socketable gems, set bonuses, tiers, item security restrictions, and real-time expiration with 100% hot-reload synchronization — manageable entirely via YAML configuration or the intuitive Ingame GUI Editor.

---

## 📑 Table of Contents

1. [Architecture & Design Principles](#-architecture--design-principles)
2. [Feature Highlights](#-feature-highlights)
3. [Supported Item Types (14 Total)](#-supported-item-types-14-total)
4. [17 Custom RPG Stats & Formulas](#-17-custom-rpg-stats--formulas)
5. [Combat & Non-Linear Defense Engine](#-combat--non-linear-defense-engine)
6. [Mine Radius & Zero-Lag ExtraStorage Excavation](#-mine-radius--zero-lag-extrastorage-excavation)
7. [AdvancedOreGen Generator Compatibility](#-advancedoregen-generator-compatibility)
8. [Dynamic Lore Formatting & Pruning Engine](#-dynamic-lore-formatting--pruning-engine)
9. [Enchantment System & EcoEnchants Integration](#-enchantment-system--ecoenchants-integration)
10. [Anvil Rename Protection System](#-anvil-rename-protection-system)
11. [Modular Crafting Station Engine](#-modular-crafting-station-engine)
12. [Packet-Based Training Dummy System & DPS Meter](#-packet-based-training-dummy-system--dps-meter)
13. [Drag & Drop Gem Sockets](#-drag--drop-gem-sockets)
14. [Tier Glowing Drop Outline System](#-tier-glowing-drop-outline-system)
15. [Item Security & Protection Flags](#-item-security--protection-flags)
16. [LiveUpdate Service & Data Preservation](#-liveupdate-service--data-preservation)
17. [Ingame GUI Editor (`/ri editor`)](#-ingame-gui-editor-ri-editor)
18. [Commands & Permissions](#-commands--permissions)
19. [PlaceholderAPI Reference](#-placeholderapi-reference)
20. [bStats Metrics & Live Telemetry](#-bstats-metrics--live-telemetry)
21. [Compatible Plugins & Official Links](#-compatible-plugins--official-links)
22. [Building from Source](#-building-from-source)

---

## 🏛️ Architecture & Design Principles

* **Native PersistentDataContainer (PDC):** Items are tracked permanently using namespaced tags (`royitems:item_type`, `royitems:item_id`, `royitems:item_version`, `royitems:item_expire`, `royitems:item_sockets`, `royitems:item_tier`). Items are never identified by fragile display names or lore text.
* **Folia Regionized Multi-Threading:** All entity, location, and world interactions are scheduled through region-aware platform schedulers (`entity.getScheduler()`, `Bukkit.getRegionScheduler()`, and `Bukkit.getAsyncScheduler()`) ensuring zero desync and thread-safety on Folia.
* **Fine-Grained LiveUpdate Service:** When editing item stats, lore formats, or tiers in YAML, running `/ri reload` automatically hot-updates all existing items across online player inventories, armor, offhand, and ender chests without wiping durability, socketed gems, or enchantments.
* **Damage Particle Filter:** Seamlessly intercepts vanilla `DAMAGE_INDICATOR` heart particle packets using PacketEvents or ProtocolLib, eliminating client FPS drops and screen freezing when dealing massive MMORPG damage numbers.
* **Clean Attribute Isolation:** Suppresses default vanilla weapon and armor attributes via zero-luck modifiers, ensuring players only receive stats explicitly configured in RoyItems without hidden vanilla armor/damage drift.

---

## 🌟 Feature Highlights

* ⚔️ **14 Item Categories:** Weapons, armor, tools, accessories, wings, pets, vouchers, consumables, and gems.
* 📊 **17 Custom MMORPG Stats:** Damage, defense, critical strike, lifesteal, armor penetration, movement speed, mana, and wide-area mine radius.
* ⛏️ **Mine Radius Excavation:** 2D horizontal plane mining (3×3 to 11×11+) with direct **ExtraStorage** batching and **AdvancedOreGen** in-place generator regeneration.
* 🛡️ **3-Mode Defense Engine:** Choose between `MMORPG` (diminishing returns curve), `SIMPLE` (flat reduction), or `CUSTOM_LIMIT` (clamped min/max boundaries), with native **MythicMobs** Skill Mechanic damage mitigation.
* 🔨 **Anvil Rename Protection:** Blocks unauthorized item renaming in anvils while safely allowing repairs and enchantments.
* ✨ **EcoEnchants & Vanilla Dynamic Lore:** Real-time `#enchantments#` placeholder rendering Vanilla and EcoEnchants with customizable Roman/Number level formatting and hide-vanilla-tooltip option.
* 🔨 **Modular Crafting Stations:** Create unlimited GUI workstations with TriumphGUI, custom commands, FancyNpcs/Citizens integration, multi-currency recipes, and dynamic ExtraStorage lore.
* 🎯 **Packet-Based Training Dummies:** Client-side DPS & damage testing dummies with Text Display holograms, immune to entity purges and `/kill @e`, with zero tick performance cost.
* 💎 **Drag-and-Drop Gem Socketing:** Socket gems intuitively directly inside player inventories.
* 🌈 **Dropped Item Tier Glow:** Ground items glow with their tier's distinct Bukkit/Hex color.
* 🖥️ **Full-Featured GUI Editor:** Modify 100% of item properties in-game with live preview, concurrent editing locks, and anti-italic typography.
* 📜 **Smart Dynamic Lore Engine:** Conditional line pruning and automatic `#bar#` divider cleanup.
* 🔄 **Auto Config Migrator:** Automatically checks `config-version`, creates `.bak` backups on updates, and deep-merges new keys safely without overwriting server settings.

---

## 📦 Supported Item Types (14 Total)

| Item Type | Description & Primary Use Case | Active Equipment Slot / Trigger |
| :--- | :--- | :--- |
| `SWORD` | Melee weapons with attack damage, critical strikes, and PvP/PvE scaling | `Main Hand` |
| `ARMOR` | Helmets, Chestplates, Leggings, and Boots supporting Armor Trims & Leather Dye | `Head`, `Chest`, `Legs`, `Feet` |
| `BOW` | Ranged bows applying custom arrow damage and ranged critical modifiers | `Main Hand` |
| `CROSSBOW` | High-impact ranged crossbows | `Main Hand` |
| `STAFF` | Magic casting staves triggering abilities, mana consumption, or Mythic skills | `Main Hand` |
| `TOOL` | Harvesting pickaxes, shovels, axes, and hoes with Mine Radius & custom stats | `Main Hand` |
| `GEM_STONE` | Sockets upgrade stones slotted via drag-and-drop onto gear | `Inventory Drag & Drop` |
| `CONSUMABLE` | Potions, scrolls, and food granting timed potion effects and cooldowns | `Right-Click` |
| `MATERIAL` | Crafting resources and quest trade materials | `Inventory` |
| `OFF_HAND` | Shields, tomes, and off-hand defensive gear | `Off Hand` |
| `WING` | Gliders and wings (Elytra) enhancing flight velocity and protection | `Chestplate Slot` |
| `VOUCHER` | Claimable scrolls executing console commands upon single use | `Right-Click` |
| `TALISMAN` | Passive relics granting bonuses simply by resting in the player's inventory | `Inventory / Hand` |
| `PET` | Companion summoning tokens and collectible pets | `Inventory / Hand` |

---

## 📊 17 Custom RPG Stats & Formulas

RoyItems evaluates stats through an independent additive and multiplicative math pipeline:

$$\text{Final Stat Value} = (\text{Base} + \sum \text{Flat Modifiers}) \times (1 + \sum \text{Percent Modifiers})$$

| Statistic | Display Symbol | Description & Mechanic |
| :--- | :---: | :--- |
| `attack-damage` | ⚔ | Base physical attack damage dealt on melee/ranged strikes |
| `defense` | 🛡 | Mitigates incoming damage via the selected Defense formula |
| `pve-defense` | 🥋 | Additional defense applied exclusively against monster/mob attacks |
| `pvp-damage` | 🗡 | Bonus flat damage applied exclusively when attacking other players |
| `attack-speed` | ⚡ | Weapon swing recovery speed multiplier |
| `max-health` | ❤ | Maximum bonus health points (HP) added to the player entity |
| `critical-rate` | 🎯 | Critical hit strike chance percentage (%) |
| `critical-damage` | 💥 | Bonus damage multiplier applied on successful critical strikes (%) |
| `movement-speed` | 👟 | Walking and sprinting movement speed bonus (%) |
| `health-regeneration` | 💖 | Natural passive health regeneration rate multiplier (%) |
| `lifesteal` | 🩸 | Recovers health points based on a percentage of damage dealt (%) |
| `armor-penetration` | 🏹 | Ignores a percentage of the opponent's Defense points (%) |
| `damage-reduction` | 🔰 | Final incoming damage reduction percentage (%) |
| `cooldown-reduction` | ⏳ | Cooldown reduction percentage for item abilities (%) |
| `mana` | 🔮 | Maximum mana pool capacity (MMOCore, AuraSkills) |
| `mana-regeneration` | 🧪 | Passive mana regeneration rate multiplier (%) |
| `mine-radius` | ⛏ | Wide-area horizontal plane mining tier (1 = 3×3, 2 = 5×5, 3 = 7×7, 4 = 9×9, 5 = 11×11+) |

---

## 🛡️ Combat & Non-Linear Defense Engine

RoyItems operates an independent combat damage mitigation engine configured under `combat.defense` in `config.yml`:

### 1. MMORPG Mode (Diminishing Returns)
$$\text{Final Damage} = \text{Incoming Damage} \times \left(\frac{\text{scale}}{\text{scale} + \text{Effective Defense}}\right) \times \text{damage-factor}$$
* Example with `scale = 100`:
  * **100 Defense** $\rightarrow$ 50% damage reduction
  * **200 Defense** $\rightarrow$ 66.7% damage reduction
  * **300 Defense** $\rightarrow$ 75% damage reduction
* **Guaranteed Chip Damage:** Lower-level players always deal baseline damage (`min-damage-floor: 1.0`), preventing unkillable raid bosses or invincible tanks.

### 2. SIMPLE Mode (Flat Subtraction)
$$\text{Final Damage} = \max(\text{floor-damage}, \text{Incoming Damage} - \text{Effective Defense})$$

### 3. CUSTOM_LIMIT Mode (Bounded Clamp)
Flat subtraction bounded between configurable `min-damage` and `max-damage` thresholds.

### 4. MythicMobs Skill Damage Interception
Incoming damage from MythicMobs Skill Mechanics (`MythicDamageEvent`) is automatically intercepted and mitigated through RoyItems' Defense formula, with timestamp tracking preventing double-mitigation.

---

## ⛏️ Mine Radius & Zero-Lag ExtraStorage Excavation

RoyItems features a specialized wide-area horizontal excavation mechanic for `type: TOOL` items:

* **Strict 2D Horizontal Plane Mining:** Operates strictly on the identical Y-coordinate ($Y = Y_0$, $X \in [X_0 - r, X_0 + r]$, $Z \in [Z_0 - r, Z_0 + r]$). Never mines ceilings or floors, completely preventing accidental pit falls or cave-ins.
* **Tier Area Scaling:**
  * **Tier 1:** 3×3 area ($r = 1$, up to 9 blocks)
  * **Tier 2:** 5×5 area ($r = 2$, up to 25 blocks)
  * **Tier 3:** 7×7 area ($r = 3$, up to 49 blocks)
  * **Tier 4:** 9×9 area ($r = 4$, up to 81 blocks)
  * **Tier 5:** 11×11 area ($r = 5$, up to 121 blocks)
* **Zero-Lag ExtraStorage Batching:** Mined block drops (with Fortune and Silk Touch) are aggregated by Material and deposited in a single batch directly into the player's **ExtraStorage**. No physical item entities are dropped into the world, eliminating server FPS drops for 24/24 AFK miners.
* **Unbreaking Durability Simulation:** Durability wear is computed using vanilla Unbreaking probabilities ($\frac{1}{\text{level} + 1}$) and committed in a single write operation.
* **Region Protection Check:** Simulates `BlockBreakEvent` on each block — if WorldGuard or island protection cancels the break, the block remains unharmed.

---

## 🌋 AdvancedOreGen Generator Compatibility

RoyItems provides native integration with **AdvancedOreGen**:

* **Problem Solved:** Traditional vein miners or radius excavation set blocks to `AIR`, which disrupts water/fence generator physics, stops fluid tick events, and freezes cobblestone/ore generators.
* **In-Place Regeneration:** When Mine Radius breaks an ore generator block, RoyItems identifies the generator setup and invokes AdvancedOreGen's generation pipeline (`generateRandomBlock`) directly.
* **Zero Machine Breakage:** Generator blocks are regenerated in-place into the player's next random ore without turning to `AIR`. Water never floods into adjacent holes, and SkyBlock players can mine 24/24 continuously without breaking their generators.

---

## 📜 Dynamic Lore Formatting & Pruning Engine

RoyItems features a flexible template-based lore engine (`lore-format/<template>.yml`):

```yaml
format:
  - "<yellow><bold>%type%</bold></yellow> <dark_gray>•</dark_gray> %tier-color%%tier%"
  - "#bar#"
  - "#description#"
  - "#bar#"
  - "#attack-damage#"
  - "#defense#"
  - "#mine-radius#"
  - "#bar#"
  - "#enchantments#"
  - "#sockets#"
  - "#requirements#"
  - "#bar#"
  - "<gray>Durability: <white>#durability#</white>"
```

* **Smart Pruning:** If an item lacks a statistic, the entire line containing that placeholder is omitted cleanly without leaving blank lines. Isolated `#bar#` dividers are automatically stripped.
* **Modern Typography:** Full support for MiniMessage tags (`<gradient:#FFD166:#06D6A0>`, `<rainbow>`, `<bold>`), Hex color codes (`<#RRGGBB>`, `&#RRGGBB`), and legacy `&` codes.
* **Anti-Italic Enforcement:** Minecraft's default italics are suppressed by default across all items and GUIs.

---

## ✨ Enchantment System & EcoEnchants Integration

Configured under `enchantments:` in `config.yml`:

```yaml
enchantments:
  auto-sync-lore: true
  hide-vanilla-tooltip: true
  default-format: "&7%name% %level%"
  curse-format: "&c%name% %level%"
  level-style: "ROMAN"
  order: "VANILLA_FIRST"
```

* **Dynamic Placeholder `#enchantments#`:** Renders both Vanilla and **EcoEnchants** enchantments seamlessly.
* **Real-Time Synchronization:** Updates immediately upon anvil combination, enchanting table use, enchanted books, or `/enchant` commands.
* **Clean Lore Display:** Hides default vanilla grey enchantment tooltips (`hide-vanilla-tooltip: true`) to prevent duplicated enchantment lines.

---

## 🔨 Anvil Rename Protection System

Configured under `anvil:` in `config.yml`:

```yaml
anvil:
  prevent-rename: true
```

* **Airtight Renaming Block:** Prevents players from altering RoyItems item names in anvils, including color codes, special characters, and shift-click bypasses.
* **Preserves Repairs & Enchantments:** Players can still combine items, apply enchanted books (Vanilla & EcoEnchants), and repair equipment normally.
* **Per-Item Override:** Items can set `prevent-anvil-rename: false` in YAML to allow renaming for specific custom items.

---

## 🔨 Modular Crafting Station Engine

Create custom workstations (`crafting-station/<id>.yml`):

* **TriumphGUI Integration:** Multi-page menus (9 to 54 slots) with customizable borders, background buttons, and sound effects.
* **Multi-Currency Costs:** Recipes support Vault Economy, PlayerPoints, ExtraStorage, Vanilla materials, and RoyItems.
* **Live Material Placeholders:** Dynamic recipe lore shows `%have%` (Inventory + ExtraStorage), `%storage_have%` (ExtraStorage), and `%inv_have%` (Inventory).
* **NPC & Command Shortcuts:** Bind stations to **FancyNpcs** or **Citizens** NPCs, or assign custom command shortcuts (e.g. `/blacksmith`).

---

## 🎯 Packet-Based Training Dummy System & DPS Meter

Spawn client-side combat testing dummies with `/ri dummy spawn`:

* **Zero Server Tick Overhead:** Dummies exist purely as client-side packets. They do not tick on the main thread and are immune to `/kill @e` or entity clearers.
* **MMORPG Combat Duration DPS:** Measures exact elapsed time between the first strike and the last blow. When combat ends, the DPS score remains frozen during the reset countdown.
* **True Cooldown Reconstruction:** Calculates weapon attack speed and swing cooldown intervals, allowing jump crits and full damage output.

---

## 💎 Drag & Drop Gem Sockets

Upgrade equipment intuitively:

1. Pick up a `type: GEM_STONE` in your inventory.
2. Click directly on compatible equipment.
3. The gem slots into the first available socket, enhancing item stats immediately.

* **Universal Sockets (`1`):** Accepts any gem.
* **Specific Sockets (`A`):** Accepts only type `A` gems.
* **Multi-Gem Sockets (`ABC`):** Accepts `A`, `B`, or `C` gems.

---

## 🌈 Tier Glowing Drop Outline System

Configure glowing colors in `item-tier.yml`:

* Ground item entities glow with their tier's distinct Bukkit/Hex color.
* Uses lightweight scoreboard glow teams with zero packet spam.

---

## 🔒 Item Security & Protection Flags

Prevent exploitation on quest items, collectibles, and currency:

```yaml
excalibur:
  material: DIAMOND_SWORD
  disable-on-use: true         # Prevent right-click actions/consumption
  disable-on-drop: true        # Prevent dropping from inventory
  disable-on-crafting: true    # Prevent using as recipe ingredient in crafting tables
  disable-on-enchantment: true # Prevent enchanting in enchanting tables or anvils
```

---

## 🔄 LiveUpdate Service & Data Preservation

Configure in `config.yml`:

```yaml
live-update:
  enabled: true
  auto-update-on-reload: true
  containers:
    inventory: true
    armor: true
    offhand: true
    ender-chest: true
  preserve:
    enchantments: true
    gems: true
    durability: true
    display-name: false
    lore: false
    tier: false
    custom-model-data: false
    pdc: true
    attributes: false
```

When running `/ri reload`, RoyItems hot-updates all active items across online player containers while respecting preservation toggles.

---

## 🖥️ Ingame GUI Editor (`/ri editor`)

Modify item properties in-game:

* Edit materials, display names, lore, tiers, sets, stats, and gem sockets.
* Multi-page navigation with live item preview.
* Interactive chat prompt input for numbers, text, and hex colors.
* Concurrent session locking prevents conflicting administrative edits.

---

## 💻 Commands & Permissions

Base command: `/royitems` or shortcut `/ri`.

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ri help` | `royitems.command.help` | View help menu and command documentation |
| `/ri reload` | `royitems.command.reload` | Reload all configurations, templates, and trigger LiveUpdate |
| `/ri editor` | `royitems.command.editor` | Open the Ingame GUI Item Editor |
| `/ri give <player> <type> <id> [amt]` | `royitems.command.give` | Give custom items to an online player |
| `/ri take <player> <type> <id> [amt]` | `royitems.command.take` | Remove custom items from a player |
| `/ri drop <world> <x> <y> <z> <type> <id> [amt]` | `royitems.command.drop` | Drop custom items at coordinates |
| `/ri station open <id> [player]` | `royitems.command.station.open` | Open a Crafting Station for yourself or another player |
| `/ri station list` | `royitems.command.station.list` | List all available Crafting Stations |
| `/ri dummy spawn` | `royitems.command.dummy` | Spawn a client-side packet training dummy with DPS meter |
| `/ri dummy despawn` | `royitems.command.dummy` | Remove nearby training dummies |
| `/ri checkupdate` | `royitems.command.checkupdate` | Check for latest plugin updates on Modrinth |

---

## 🔌 PlaceholderAPI Reference

| Placeholder | Description |
| :--- | :--- |
| `%royitems_stat_<stat>%` | Returns player's total stat value (e.g. `%royitems_stat_attack-damage%`) |
| `%royitems_tier%` | Tier display name of item held in main hand |
| `%royitems_tier_id%` | Raw tier identifier of item held in main hand |
| `%royitems_tier_color%` | Tier color code of item held in main hand |
| `%royitems_type%` | Item category type of item held in main hand |
| `%royitems_id%` | Internal item identifier of item held in main hand |

---

## 📈 bStats Metrics & Live Telemetry

RoyItems collects anonymous usage telemetry via **bStats**:

| Metric | Official Telemetry Badge |
| :--- | :--- |
| **Active Servers** | [![bStats Servers](https://img.shields.io/bstats/servers/34832?style=for-the-badge&logo=chartdotjs&logoColor=white&color=007ec6)](https://bstats.org/plugin/bukkit/RoyItems/34832) |
| **Active Players** | [![bStats Players](https://img.shields.io/bstats/players/34832?style=for-the-badge&logo=minecraft&logoColor=white&color=007ec6)](https://bstats.org/plugin/bukkit/RoyItems/34832) |
| **Full Dashboard** | [🔗 View Official bStats Dashboard](https://bstats.org/plugin/bukkit/RoyItems/34832) |

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832">
    <img src="https://bstats.org/signatures/bukkit/RoyItems.svg" alt="RoyItems bStats Signature" />
  </a>
</p>

---

## 🔌 Compatible Plugins & Official Links

RoyItems features verified soft hooks with 20 official plugins:

### 💰 Economy & Placeholders
* [Vault](https://www.spigotmc.org/resources/vault.34315/) — Economy integration for crafting stations.
* [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) — Placeholder expansion for stats and lore.
* [PlayerPoints](https://modrinth.com/project/playerpoints) — Secondary premium currency in crafting stations.
* [ExcellentEconomy](https://modrinth.com/plugin/excellenteconomy) — Multi-currency economy integration.

### ⚡ Packets & Performance
* [PacketEvents](https://packetevents.com/) — Damage heart particle suppression and packet training dummies.
* [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) — Alternative protocol layer for particle suppression and dummy packets.

### ⚔️ RPG, Combat & Enchantments
* [MythicMobs](https://modrinth.com/plugin/mythicmobs) — Cast Mythic skills from items and mitigate skill damage via Defense.
* [MMOCore](https://www.spigotmc.org/resources/mmocore.70575/) — Level/Class requirements and unified Mana pool scaling.
* [AuraSkills](https://modrinth.com/plugin/auraskills) — Stat scaling, skill level requirements, and mana integration.
* [EcoEnchants](https://hub.auxilor.io/wiki/ecoenchants/ecoenchants) — Dynamic recognition and custom lore rendering of EcoEnchants.

### 🌍 World Management
* [WorldGuard](https://enginehub.org/worldguard) — Region protection verification during block breaking and Mine Radius.
* [WorldEdit](https://enginehub.org/worldedit) — World editing and coordinate handling.
* [FastAsyncWorldEdit](https://modrinth.com/plugin/fastasyncworldedit) — High-speed async world management.

### 📦 Custom Items, Generators & Storage
* [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/) — Custom skull heads in Crafting Station GUIs.
* [Nexo](https://nexomc.com/) — 3D custom model textures, glyphs, and custom items.
* [ItemsAdder](https://www.itemsadder.com/) — 3D item models, custom blocks, and textures.
* [Oraxen](https://oraxen.com/) — Custom model data items and glyphs.
* [ExtraStorage](https://github.com/HSGamer/ExtraStorage) — Direct input deduction for crafting stations and zero-lag Mine Radius deposit.

### 👥 NPC Integration
* [Citizens](https://www.citizensnpcs.co/) — Bind NPCs directly to open designated Crafting Stations upon right-click.
* [FancyNpcs](https://modrinth.com/plugin/fancynpcs) — Modern packet-based NPC binding for Crafting Stations.

---

## 🛠️ Building from Source

RoyItems is built using **Apache Maven** with Java 21:

```bash
# Clone the repository
git clone https://github.com/orenaidaucac/RoyItems.git
cd RoyItems

# Build package
mvn clean package
```

The compiled JAR will be output to `target/RoyItems-2026.1.6.jar`.
