# ⚔️ RoyItems — Advanced MMORPG Custom Items Engine

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x%20%7C%201.21.4%2B-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft" />
  <img src="https://img.shields.io/badge/Platforms-Paper%20%7C%20Purpur%20%7C%20Folia%20%7C%20Leaf%20%7C%20Spigot-f38ba8?style=for-the-badge" alt="Platforms" />
  <img src="https://img.shields.io/badge/Java-21%2B-ea76cb?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Release-v2026.1.11-blue?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Modrinth-RoyItems-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth" />
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/bstats/servers/34832?style=for-the-badge&logo=chartdotjs&logoColor=white&color=007ec6" alt="bStats Servers" /></a>
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/bstats/players/34832?style=for-the-badge&logo=minecraft&logoColor=white&color=007ec6" alt="bStats Players" /></a>
</p>

---

## 🌟 OVERVIEW

Welcome to **RoyItems**, the high-performance, modern MMORPG Custom Items and Attributes Engine engineered specifically for Minecraft **1.21+** servers running on **Paper**, **Purpur**, **Folia**, **Leaf**, and **Spigot**.

Whether you run a massive SkyBlock network, a hardcore MMORPG realm, a competitive Factions server, or a tight-knit Survival community, RoyItems delivers unprecedented power, flexibility, and architectural reliability.

### 💡 Why RoyItems Stands Apart
* 🚀 **100% Standalone Architecture:** Operates with **ZERO required dependencies**. No bloated prerequisite libraries or forced framework dependencies—just drop the JAR in and play.
* ⚡ **Folia & Leaf Native Multi-Threading:** Built around asynchronous data parsing and region-aware scheduling (`PlatformScheduler`). Large-scale operations, wide-area mining, and real-time inventory updates run smoothly with zero region thread stalls or tick lag.
* 🛡️ **Zero NMS Reflection:** Built strictly on clean, forward-compatible Paper APIs and PersistentDataContainer (PDC) storage. Updates smoothly across Minecraft minor versions without breaking item data or waiting weeks for reflection patches.
* ⚔️ **Independent MMORPG Stat & Defense Calculations:** Features 17 custom stats that scale deterministically:
  $$\text{Total Stat} = (\text{Base} + \sum \text{Flat}) \times \left(1 + \sum \text{Percent}\right)$$
  Operates without interfering with vanilla Minecraft armor limits, toughness caps, or attribute modifier caps.
* 🔄 **Hot-Reload LiveUpdate:** Modify item configurations, tiers, lore formats, or crafting recipes on the fly. Running `/ri reload` automatically updates existing items across online player inventories, armor slots, offhands, and enderchests while preserving custom durability, slotted gems, and enchantments.
* 🎨 **Minecraft 1.21+ Equippable Component Support:** Native support for modern Equippable components, seamlessly rendering 2D custom armor models from Nexo and custom resource packs directly onto player character models.

---

## 🚀 FEATURES

RoyItems is engineered with a modular suite of gameplay mechanics designed to elevate your server's player progression and economy:

### 1. 📦 14 Specialized Item Archetypes
Every custom item is assigned a functional type governing where it can be equipped, how it activates, and what rules apply:
* **Melee & Ranged Weapons:** `SWORD`, `BOW`, `CROSSBOW`, and `STAFF` with custom attack speed, critical strike scaling, arrow velocity, and right-click magic casting.
* **Defensive Gear & Vanity:** `ARMOR` (Head, Chest, Legs, Feet) supporting 2D custom equipment models, `OFF_HAND` (Shields/Orbs), `WING` (Gliders/Elytra), and `COSMETIC` items.
* **Harvesting & Excavation:** `TOOL` archetypes supporting wide-area horizontal slicing (`mine-radius`).
* **Progression, Economy & Sockets:** `GEM_STONE` for socketing, `TALISMAN` for passive inventory stat boosts, `CONSUMABLE` for food and potion buffs, `MATERIAL` for crafting recipes, and `VOUCHER` for interactive currency rewards.

### 2. ⛏️ Wide-Area Mine Radius with Zero Entity Lag
* **Strict Horizontal 2D Slicing ($Y = Y_0$):** Mines only on the horizontal layer around the broken block (Tier 1: $3\times 3$, Tier 2: $5\times 5$, Tier 3: $7\times 7$, Tier 4: $9\times 9$, Tier 5: $11\times 11$). Never carves deep vertical holes or breaks ceiling/floor builds accidentally.
* **Ghost Block Elimination:** Immediately broadcasts authoritative multi-client `sendBlockChange` packets within a 48-block radius, followed by a 1-tick delayed post-physics verification on the Region Scheduler.
* **AdvancedOreGen In-Place Regeneration:** Detects SkyBlock generator blocks (fence-water, cobble, basalt) and triggers in-place ore regeneration via AdvancedOreGen without turning blocks into AIR, protecting fluid flows from turning into obsidian.
* **Direct ExtraStorage Batching:** Deposits all collected drops directly into the player's ExtraStorage vault in memory, bypassing ground item entities and completely eliminating entity drop lag spikes.
* **Thread-Safe Active Mining Locks:** Concurrently locks active coordinates to prevent duplicate block breaks, item duplication exploits, or fluid physics lockups when multiple players mine simultaneously.

### 3. 🎁 Animated Title Gacha & Multi-Currency Vouchers
* **Air & Block Interaction:** Right-click anywhere—into the open air or against a block—with either the Main Hand or Off Hand to activate vouchers without targeting entities.
* **Dynamic Title Animation:** Displays a rolling title sequence with customizable sound effects, particle bursts, and dynamic pitches before revealing the awarded prize.
* **Multi-Currency Deposits:** Direct integration with **Vault** (Money), **PlayerPoints** (Points), and **ExcellentEconomy** (multi-currency balances).
* **Safe Atomic Transactions:** If an economy provider is unreachable or a transaction fails, the voucher is refunded automatically with zero item loss.
* **Command Automation:** Execute multiple console or player commands sequentially with placeholder support (`#player#`, `#amount#`, `#currency#`).

### 4. 💎 Real-Time Dynamic Lore & Set Bonuses
* **Dynamic Lore Engine:** Lore updates on the fly when equipping, enchanting, or socketing gems. Supports MiniMessage, Hex colors (`&#RRGGBB`), and legacy formatting.
* **Dynamic `#set#` Lore:** Armor set items dynamically switch between active and inactive set bonus descriptions in real time as pieces are equipped or unequipped.
* **Gem Socketing:** Configurable socket counts (`sockets: 3`), allowed gem types, and interactive drag-and-drop or socketing table GUIs.

### 5. 🎯 Packet-Based Training Dummies & DPS Meters
* **Virtual Combat Testing:** Spawn packet-based combat training dummies (`/ri dummy spawn <id> [mob_type]`) to test real player damage, critical strikes, and weapon DPS.
* **Floating Holographic Meters:** Real-time floating holograms display current strike damage, maximum damage, and DPS over 5-second intervals.
* **Zero Entity Lag:** Dummies use virtual packet entities, requiring no persistent entity ticking or AI pathfinding calculations.

### 6. 🛠️ Ingame Interactive GUI Editor
* Open the comprehensive GUI editor with `/ri edit` to browse, inspect, create, and configure custom items directly in-game without touching raw YAML files.

---

## 📥 INSTALLATION

Setting up RoyItems takes less than 60 seconds:

1. **Verify Requirements:**
   * Server Software: **Paper**, **Purpur**, **Folia**, **Leaf**, or **Spigot** (Minecraft **1.21+**).
   * Java Runtime: **Java 21** or newer.
2. **Download Release:**
   * Download the latest `RoyItems-2026.1.11.jar` from Modrinth.
3. **Verify Dependencies:**
   * RoyItems has **ZERO required dependencies**. You do not need to install any external libraries.
   * If you wish to use economy features, skills, or custom models, ensure your optional soft dependencies (e.g. Vault, Nexo, ExtraStorage) are installed.
4. **Place in Plugins Directory:**
   * Place `RoyItems-2026.1.11.jar` into your server's `plugins/` folder.
5. **Start Your Server:**
   * Boot or restart your server. Check console for:
     `[RoyItems] Initializing RoyItems v2026.1.11...`
     `[RoyItems] Successfully loaded custom items and crafting stations.`
6. **Customize Configurations:**
   * RoyItems automatically generates clean, default configurations in `plugins/RoyItems/`.
   * Edit settings via YAML or in-game using `/ri edit`. Apply changes instantly with `/ri reload`.
7. **Troubleshooting Tips:**
   * *UnsupportedClassVersionError:* Ensure your startup script uses Java 21+ (`java -version`).
   * *Permission Denied:* Ensure your permission plugin (e.g. LuckPerms) assigns `royitems.admin` to server administrators.

---

## 💻 COMMANDS

RoyItems provides an intuitive command system under `/royitems` (shorthand alias: `/ri`):

| Command Syntax | Arguments & Parameters | Required Permission | Description |
| :--- | :--- | :--- | :--- |
| `/ri help` | `[page]` | `royitems.command.help` | View interactive command help and usage instructions. |
| `/ri reload` | — | `royitems.command.reload` | Reload all configurations, items, language, and run LiveUpdate. |
| `/ri editor` | — | `royitems.command.editor` | Open the interactive Ingame GUI Item & Station Editor. |
| `/ri checkupdate` | — | `royitems.command.checkupdate` | Check for new RoyItems releases on Modrinth. |
| `/ri give` | `<player\|all> <type> <id> [amt]` | `royitems.command.give` | Give custom items directly to a player or all online players. |
| `/ri take` | `<player\|all> <type> <id> [amt]` | `royitems.command.take` | Remove custom items from a player or all online players. |
| `/ri drop` | `<player\|all> <type> <id> [amt]` | `royitems.command.drop` | Drop custom items at a player's location. |
| `/ri drop` | `<world> <x> <y> <z> <type> <id> [amt]` | `royitems.command.drop` | Drop custom items at exact world coordinates. |
| `/ri station open` | `<id> [player]` | `royitems.command.station.open` | Open a specific Crafting Station GUI for yourself or a target player. |
| `/ri station list` | — | `royitems.command.station.list` | Open the interactive Crafting Station list overview GUI. |
| `/ri station create` | `<id>` | `royitems.command.station.create` | Generate a new crafting station template file in `crafting-station/`. |
| `/ri station delete` | `<id>` | `royitems.command.station.delete` | Permanently delete a crafting station and remove its YAML file. |
| `/ri dummy spawn` | `<id> [mob_type]` | `royitems.command.dummy` | Spawn a virtual packet-based DPS training dummy at your location. |
| `/ri dummy despawn` | `<id\|all>` | `royitems.command.dummy` | Despawn a specific training dummy or all active dummies. |

---

## 🔑 PERMISSIONS

### ⚡ Dynamic Permission Stats (LuckPerms Integration)
Grant flat or percentage attribute bonuses directly to player ranks or groups without requiring players to wear or hold items:

```text
royitems.player.stats.<stat>.<value>   # Flat value boost (e.g. +25 Defense)
royitems.player.stats.<stat>.<value>%  # Percentage boost (e.g. +15% Attack Damage)
```

| Permission Node Example | Target Stat | Applied Effect |
| :--- | :--- | :--- |
| `royitems.player.stats.attack-damage.10` | Attack Damage | Grants +10 flat attack damage to all melee attacks. |
| `royitems.player.stats.max-health.40` | Max Health | Grants +40 flat maximum health (2 full extra rows of hearts). |
| `royitems.player.stats.defense.25` | Defense | Grants +25 flat defense mitigation against incoming damage. |
| `royitems.player.stats.crit-rate.15%` | Critical Rate | Increases critical strike chance by +15%. |
| `royitems.player.stats.movement-speed.10%` | Movement Speed | Grants +10% movement speed boost across all worlds. |
| `royitems.player.stats.pvp-damage.12%` | PvP Damage | Increases damage dealt against players by +12%. |
| `royitems.player.stats.generic.scale.1.2` | Vanilla Scale | Increases player model size by 20% (Minecraft 1.21+ attribute). |

### 🛡️ Administrative & Command Permissions

| Permission Node | Description | Default Access |
| :--- | :--- | :--- |
| `royitems.admin` | Full wildcard access to all RoyItems commands, editor menus, and bypasses. | OP Only |
| `royitems.command.help` | Permission to view `/ri help`. | OP Only |
| `royitems.command.reload` | Permission to reload configurations via `/ri reload`. | OP Only |
| `royitems.command.editor` | Permission to access the in-game GUI editor via `/ri editor`. | OP Only |
| `royitems.command.checkupdate` | Permission to check plugin version via `/ri checkupdate`. | OP Only |
| `royitems.command.give` | Permission to give custom items via `/ri give`. | OP Only |
| `royitems.command.take` | Permission to revoke custom items via `/ri take`. | OP Only |
| `royitems.command.drop` | Permission to drop items at players or coordinates via `/ri drop`. | OP Only |
| `royitems.command.station` | Parent permission for all crafting station management commands. | OP Only |
| `royitems.command.station.open` | Permission to open crafting stations via `/ri station open`. | OP Only |
| `royitems.command.station.list` | Permission to list all crafting stations via `/ri station list`. | OP Only |
| `royitems.command.station.create` | Permission to create crafting stations via `/ri station create`. | OP Only |
| `royitems.command.station.delete` | Permission to delete crafting stations via `/ri station delete`. | OP Only |
| `royitems.command.dummy` | Permission to spawn or despawn combat training dummies. | OP Only |
| `royitems.item.expired.bypass` | Bypass expiration date restrictions on timed custom items. | OP Only |
| `royitems.station.<station_id>` | Permission required to access a specific crafting station if configured. | Configurable |
| `royitems.item.use.<type>.<id>` | Permission requirement to wield or consume an item if configured. | Configurable |

---

## 🏷️ PLACEHOLDERS

### 🌐 PlaceholderAPI Integration (`%royitems_...%`)
RoyItems provides a built-in PlaceholderAPI expansion for HUDs, Scoreboards, TAB lists, and chat plugins:

| Placeholder | Return Value | Description |
| :--- | :--- | :--- |
| `%royitems_stat_<stat>%` | Number (e.g. `125.5`) | Returns player's calculated total value for `<stat>` (Gear + Perms). |
| `%royitems_stat_attack-damage%` | Number (e.g. `45.0`) | Returns total melee attack damage. |
| `%royitems_stat_defense%` | Number (e.g. `80.0`) | Returns total defense mitigation value. |
| `%royitems_stat_max-health%` | Number (e.g. `60.0`) | Returns player's maximum health attribute. |
| `%royitems_stat_crit-rate%` | Number (e.g. `25%`) | Returns player's critical strike probability percentage. |
| `%royitems_stat_mine-radius%` | Number (e.g. `2`) | Returns current wide-area horizontal mining radius level. |
| `%royitems_hand_type%` | String (e.g. `SWORD`) | Returns the custom ItemType of the item held in the Main Hand. |
| `%royitems_hand_id%` | String (e.g. `excalibur`) | Returns the unique configuration ID of the held item. |
| `%royitems_hand_name%` | Text (e.g. `Excalibur`) | Returns the formatted display name of the held item. |
| `%royitems_hand_tier%` | String (e.g. `LEGENDARY`) | Returns the rarity tier ID of the held item. |
| `%royitems_hand_version%` | Number (e.g. `2`) | Returns the persistent version number of the held item. |

### 📜 Internal Dynamic Lore Placeholders (`lore-format/*.yml`)
These placeholders are evaluated in item lore templates and rendered in real time:

| Lore Placeholder | Rendered Output | Context / Description |
| :--- | :--- | :--- |
| `#name#` | String | Formatted display name of the item. |
| `#type#` | String | Formatted type display name (e.g. `Melee Weapon`, `Armor Equipment`). |
| `#tier#` | String | Formatted rarity tier name (e.g. `&6Legendary`). |
| `#tier-color#` | Color code | Color formatting code assigned to the item's tier. |
| `#set#` | Text block | Dynamic Item Set lore (switches between active and inactive set bonus). |
| `#set-name#` | String | Official display name of the assigned Item Set. |
| `#lore#` | Text lines | User-defined custom item description lines. |
| `#stats#` | Block of lines | Rendered MMORPG stats list formatted according to `stat-format.yml`. |
| `#stat_<stat_name>#` | Number | Direct value of a specific stat on the item. |
| `#enchantments#` | Block of lines | Formatted enchantments list (supports Vanilla & EcoEnchants). |
| `#sockets#` | Block of lines | Visual socket indicators with filled gem details and empty slots. |
| `#requirements#` | Block of lines | Level, class, and permission requirements with dynamic satisfied coloring. |
| `#durability#` | String | Current custom durability vs max durability (e.g. `1,250/1,500`). |
| `#bar#` | String | Visual divider or progress bar defined in lore formatting templates. |
| `#cooldown#` | String | Cooldown duration in seconds for staves, consumables, or vouchers. |
| `#expiration#` | String | Expiration countdown timer or date for temporary items. |
| `#mine-radius#` | String (e.g. `3×3`) | Formatted horizontal area slicing dimension. |
| `#unbreakable#` | String | Unbreakable tag indicator if item is configured unbreakable. |

---

## 📦 ITEM TYPES

RoyItems supports **14 distinct item types**, each with strict equipment slot validation:

| Item Type | Default Slot | Valid Equipment Slots | Primary Role & Special Mechanics |
| :--- | :--- | :--- | :--- |
| `SWORD` | `HAND` | `HAND` (Main Hand) | Melee combat weapons. Grants offensive combat stats, life-steal, and crit. |
| `ARMOR` | Auto | `HEAD`, `CHEST`, `LEGS`, `FEET` | Defensive equipment. Supports Equippable 2D models, defense stats, and set bonuses. |
| `BOW` | `HAND` | `HAND` (Main Hand) | Ranged bows. Applies arrow damage, draw speed multipliers, and ranged crits. |
| `CROSSBOW` | `HAND` | `HAND` (Main Hand) | Heavy ranged crossbows with custom piercing and projectile stats. |
| `STAFF` | `HAND` | `HAND` (Main Hand) | Magical casting staves. Supports right-click spell casting, mana costs, and cooldowns. |
| `TOOL` | `HAND` | `HAND` (Main Hand) | Excavation tools (Pickaxes, Axes, Shovels, Hoes) with `mine-radius` slicing. |
| `GEM_STONE` | None | Socket Only | Socketable gems embedded into gear to boost flat or percentage stats. |
| `CONSUMABLE` | `HAND` | `HAND` (Main Hand) | Edible items applying custom potion effects and health restoration with cooldowns. |
| `MATERIAL` | None | Inventory / Recipe | Specialized ingredients used in Crafting Station recipes. |
| `OFF_HAND` | `OFF_HAND` | `OFF_HAND` (Offhand) | Defensive shields, magical orbs, and secondary offhand stat boosters. |
| `WING` | `CHEST` | `CHEST` (Chestplate) | Gliders and Elytras supporting custom durability and flight attributes. |
| `VOUCHER` | `HAND` | `HAND`, `OFF_HAND` (Air & Block) | Consumable reward scrolls granting currency, commands, and title gacha. |
| `PET` | None | Inventory / Companion | Companion items providing utility perks and aesthetic effects. |
| `TALISMAN` | None | Passive Inventory | Accessory charms granting passive stats simply by sitting in player inventory. |

---

## 📊 ITEM STATS

RoyItems includes **17 built-in MMORPG stats**, categorized into combat, defense, utility, and world interaction:

| Stat Identifier | Default Icon & Name | Modifier Type | Vanilla Mapping | Gameplay Function & Mechanics |
| :--- | :--- | :--- | :--- | :--- |
| `attack-damage` | `⚔ Attack Damage` | Flat / % | `generic.attack_damage` | Base physical damage dealt during melee attacks. |
| `arrow-damage` | `🏹 Arrow Damage` | Flat / % | None (Custom) | Bonus damage applied to arrows fired from bows and crossbows. |
| `max-health` | `❤ Max Health` | Flat / % | `generic.max_health` | Increases player maximum health hearts smoothly. |
| `attack-speed` | `⚡ Attack Speed` | Flat / % | `generic.attack_speed` | Weapon swing recovery speed and combat strike frequency. |
| `crit-rate` | `🎯 Critical Rate` | Flat / % | None (Custom) | Percentage probability (0% to 100%) to score a critical strike. |
| `crit-damage` | `💥 Critical Damage` | Flat / % | None (Custom) | Bonus damage multiplier applied upon triggering a critical strike. |
| `defense` | `🛡 Defense` | Flat / % | None (Custom) | Independent MMORPG defense formula mitigating all incoming damage. |
| `pvp-damage` | `⚔ PvP Damage` | Flat / % | None (Custom) | Extra damage dealt exclusively against other players in PvP. |
| `pve-damage` | `🗡 PvE Damage` | Flat / % | None (Custom) | Extra damage dealt exclusively against hostile monsters and dungeon mobs. |
| `pvp-defense` | `🛡 PvP Defense` | Flat / % | None (Custom) | Damage mitigation applied exclusively when taking damage from players. |
| `pve-defense` | `🛡 PvE Defense` | Flat / % | None (Custom) | Damage mitigation applied exclusively when taking damage from mobs. |
| `damage-reduction` | `🔰 Damage Reduction` | Flat / % | None (Custom) | Direct flat percentage reduction applied to final incoming damage. |
| `armor` | `🛡 Armor` | Flat / % | `generic.armor` | Vanilla Minecraft armor defense value. |
| `armor-toughness` | `🛡 Armor Toughness` | Flat / % | `generic.armor_toughness` | Vanilla Minecraft armor toughness mitigating high-damage blows. |
| `knockback-resistance`| `🏋 Knockback Res` | Flat / % | `generic.knockback_resistance` | Resistance to physical knockback from strikes and explosions. |
| `movement-speed` | `👟 Movement Speed` | Flat / % | `generic.movement_speed` | Player walking and sprinting speed multiplier. |
| `mine-radius` | `⛏ Mine Radius` | Level (1–5+) | None (Custom) | Horizontal 2D plane area mining dimension (Level 1: $3\times 3$, Level 2: $5\times 5$). |

---

## ⚙️ CONFIGURATION

RoyItems organizes configurations in a modular, clean YAML directory structure inside `plugins/RoyItems/`:

```text
plugins/RoyItems/
├── config.yml                  # Core plugin settings, combat formulas, performance, and updates
├── item-tier.yml               # Item rarity tiers, custom chat colors, and drop glow outlines
├── item-set.yml                # Armor set bonuses, equipment thresholds, and dynamic lore
├── items/                      # Modular item definition files grouped by archetype
│   ├── SWORD.yml               # Melee swords and daggers
│   ├── ARMOR.yml               # Helmets, chestplates, leggings, and boots
│   ├── BOW.yml                 # Ranged bows
│   ├── CROSSBOW.yml            # Ranged crossbows
│   ├── STAFF.yml               # Magic staves
│   ├── TOOL.yml                # Harvesting tools (Pickaxes, Shovels, Axes, Hoes)
│   ├── GEM_STONE.yml           # Socketable jewels and runes
│   ├── CONSUMABLE.yml          # Food and potion consumables
│   ├── TALISMAN.yml            # Passive inventory charms
│   ├── VOUCHER.yml             # Economy, reward, and command scrolls
│   └── WING.yml                # Elytras and gliders
├── crafting-station/           # Custom Crafting Station files with recipe chains & NPC links
│   └── blacksmith.yml          # Sample multi-page Blacksmith station
├── lore-format/                # Universal and per-type lore formatting templates
│   ├── default.yml             # Universal fallback lore template
│   ├── weapon.yml              # Weapon-specific lore layout
│   └── armor.yml               # Armor-specific lore layout
├── gui/                        # Ingame GUI layouts for menus and station browsers
│   ├── editor-gui.yml          # Ingame item editor GUI
│   ├── crafting-station-list.yml
│   └── crafting-station-detail.yml
└── lang/                       # Multi-language localization files
    ├── en-US.yml               # English (US)
    ├── vi-VN.yml               # Vietnamese (Tiếng Việt)
    ├── de-DE.yml               # German (Deutsch)
    ├── es-ES.yml               # Spanish (Español)
    ├── ja-JP.yml               # Japanese (日本語)
    ├── ko-KR.yml               # Korean (한국어)
    └── ru-RU.yml               # Russian (Русский)
```

Below are standard default configurations. Click each section to expand:

<details>
<summary><b>📄 Click to expand: config.yml (Core Settings & Combat Engine)</b></summary>

```yaml
# ==============================================================================
#                      ROYITEMS — CONFIGURATION ENGINE
# ==============================================================================
config-version: 2

# General settings
debug: false

# Active localization language:
# Supported: en-US, vi-VN, de-DE, ja-JP, ko-KR, ru-RU, es-ES
language: en-US

# Provider preference when multiple MMORPG providers exist (MMOCore, AuraSkills, Auto)
mana-provider: Auto

# Performance tuning
performance:
  # Frequency in seconds for item expiration check (low overhead)
  expiration-check-interval: 20
  # Frequency in seconds for periodic potion effect application
  potion-effect-interval: 3

# ==============================================================================
# UPDATE CHECKER (Checks Modrinth for new releases)
# ==============================================================================
update-checker:
  enabled: true
  notify-admins-on-join: true

# ==============================================================================
# COMBAT & CUSTOM DEFENSE SYSTEM
# ==============================================================================
combat:
  defense:
    # Mode: MMORPG, SIMPLE, or CUSTOM_LIMIT
    mode: MMORPG
    # MMORPG formula: damage = rawDamage * (100 / (100 + defense))
    mmorpg-constant: 100.0
    # Custom limit mode settings
    custom-limit:
      defense-cap: 500.0
      max-reduction-percentage: 80.0
    # Mitigate incoming damage from MythicMobs skill mechanics
    mitigate-mythic-skills: true

# ==============================================================================
# LIVE UPDATE SYSTEM (Updates items across player inventories on /ri reload)
# ==============================================================================
live-update:
  enabled: true
  preserve:
    enchantments: true
    gems: true
    durability: true
    custom-lore: true
    display-name: false
    lore: false
    tier: false

# ==============================================================================
# MINE RADIUS MECHANICS & ADVANCED ORE GEN INTEGRATION
# ==============================================================================
mine-radius:
  enabled: true
  activation-chance: 100.0
  cooldown-ms: 0
  limits:
    max-radius-tier: 5
    max-blocks-per-break: 150
  extra-storage:
    auto-deposit: true
    drop-leftover-to-world: true
  advanced-ore-gen:
    auto-regenerate: true

# ==============================================================================
# VOUCHER GACHA & CURRENCY MAPPING
# ==============================================================================
voucher:
  gacha:
    enabled: true
    duration-ticks: 30
    interval-ticks: 2
    sound-roll: "BLOCK_NOTE_BLOCK_PLING"
    sound-finish: "UI_TOAST_CHALLENGE_COMPLETE"
    particle: "FIREWORK"
    particle-count: 35

currencies:
  vault:
    name: "Money"
    symbol: "$"
    format: "#,##0.##"
  playerpoints:
    name: "Points"
    symbol: "✪"
    format: "#,##0"
```
</details>

<details>
<summary><b>📄 Click to expand: item-tier.yml (Rarity Tiers & Glow Outlines)</b></summary>

```yaml
# ==============================================================================
#                      ROYITEMS — ITEM TIER CONFIGURATION
# ==============================================================================
config-version: 2

common:
  display-name: "&fCommon"
  color: "&f"
  weight: 1
  drop-color: WHITE

uncommon:
  display-name: "&aUncommon"
  color: "&a"
  weight: 2
  drop-color: GREEN

rare:
  display-name: "&9Rare"
  color: "&9"
  weight: 3
  drop-color: BLUE

epic:
  display-name: "&5Epic"
  color: "&5"
  weight: 4
  drop-color: DARK_PURPLE

legendary:
  display-name: "&6Legendary"
  color: "&6"
  weight: 5
  drop-color: GOLD

mythic:
  display-name: "&dMythic"
  color: "&d"
  weight: 6
  drop-color: RED
```
</details>

<details>
<summary><b>📄 Click to expand: item-set.yml (Dynamic Set Bonus & Lore)</b></summary>

```yaml
# ==============================================================================
#                      ROYITEMS — ITEM SET CONFIGURATION
# ==============================================================================
dragon_slayer:
  display-name: "&6Dragon Slayer Set"
  required-pieces: 2
  lore:
    active:
      - "&6Set: Dragon Slayer &a[Active]"
      - "&7(2) &a+15 Attack Damage, +10% Crit Rate"
      - "&7(4) &a+35 Attack Damage, +50% Crit Damage, +20 PvP Damage"
    inactive:
      - "&8Set: Dragon Slayer &c[Inactive]"
      - "&8(2) +15 Attack Damage, +10% Crit Rate"
      - "&8(4) +35 Attack Damage, +50% Crit Damage, +20 PvP Damage"
  bonuses:
    2:
      attack-damage: 15.0
      crit-rate: 10.0
    4:
      attack-damage: 35.0
      crit-damage: 50.0
      pvp-damage: 20.0
```
</details>

<details>
<summary><b>📄 Click to expand: items/SWORD.yml (Sample Melee Weapon Definition)</b></summary>

```yaml
items:
  excalibur:
    type: SWORD
    name: "<gradient:#FFD700:#FFA500>Excalibur</gradient>"
    material: NETHERITE_SWORD
    item-tier: LEGENDARY
    item-set: dragon_slayer
    item-model: "royitems:weapon/excalibur"
    unbreakable: true
    stats:
      attack-damage:
        flat: 35.0
        percent: 0.15
      critical-rate:
        flat: 20.0
      critical-damage:
        flat: 50.0
      life-steal:
        flat: 5.0
    gem-sockets:
      max: 3
    enchantments:
      sharpness: 6
      unbreaking: 3
    requirements:
      level: 50
      permission: "royitems.equip.excalibur"
    lore-format: "weapon"
```
</details>

<details>
<summary><b>📄 Click to expand: items/ARMOR.yml (Sample 2D Custom Armor Definition)</b></summary>

```yaml
items:
  galaxy_chestplate:
    type: ARMOR
    name: "<gradient:#9B5DE5:#F15BB5>Galaxy Chestplate</gradient>"
    material: NETHERITE_CHESTPLATE
    item-tier: MYTHIC
    item-set: dragon_slayer
    # Minecraft 1.21+ Equippable Component model (Nexo resource pack asset)
    equipment-model: "nexo:galaxy_chestplate"
    stats:
      defense:
        flat: 45.0
        percent: 0.10
      max-health:
        flat: 30.0
      pve-defense:
        flat: 15.0
    gem-sockets:
      max: 2
    unbreakable: true
    lore-format: "armor"
```
</details>

<details>
<summary><b>📄 Click to expand: items/VOUCHER.yml (Sample Title Gacha Voucher)</b></summary>

```yaml
items:
  money_pouch:
    type: VOUCHER
    name: "<gradient:#00F5D4:#00BBF9>Mysterious Money Pouch</gradient>"
    material: PAPER
    item-tier: RARE
    custom-model-data: 1005
    reward:
      currency: "VAULT"
      min: 1000.0
      max: 50000.0
    commands:
      - "broadcast &aPlayer &e#player# &ajust unboxed &6$#amount# &afrom a Money Pouch!"
    cooldown: 5
```
</details>

<details>
<summary><b>📄 Click to expand: crafting-station/blacksmith.yml (Custom Crafting Station)</b></summary>

```yaml
title: "<gold><bold>Royal Blacksmith</bold></gold>"
size: 54

# Direct integration with NPC plugins (Open by right-clicking NPC)
fancynpcs:
  - "blacksmith_npc"
citizens:
  - 10
phoenixnpcs:
  - "blacksmith_npc"

navigation:
  prev-page:
    slot: 48
    material: SPECTRAL_ARROW
    name: "<#FFD166>◀ Previous Page</#FFD166>"
  next-page:
    slot: 50
    material: SPECTRAL_ARROW
    name: "<#FFD166>Next Page ▶</#FFD166>"

recipes:
  forge_excalibur:
    slot: 22
    output:
      type: SWORD
      id: excalibur
      amount: 1
    ingredients:
      - material: NETHERITE_INGOT
        amount: 8
      - material: DIAMOND_BLOCK
        amount: 4
      - custom-item:
          type: MATERIAL
          id: dragon_core
          amount: 1
    cost:
      vault: 50000.0
      playerpoints: 500
```
</details>

<details>
<summary><b>📄 Click to expand: lore-format/default.yml (Lore Template Layout)</b></summary>

```yaml
format:
  - "&7Type: &f#type#"
  - "#bar#"
  - "#stats#"
  - "#bar#"
  - "#sockets#"
  - "#bar#"
  - "#description#"
  - "#bar#"
  - "#requirements#"
  - "#bar#"
  - "&8#tier#"

stat-format:
  positive: "&7#name#: &a+#value#"
  negative: "&7#name#: &c-#value#"
  percentage-positive: "&7#name#: &a+#value#%"
  percentage-negative: "&7#name#: &c-#value#%"

bar: "&8&m------------------------"
```
</details>

---

## 🔌 INTEGRATIONS

RoyItems runs completely **standalone with ZERO required dependencies**. When any of the following plugins are present on your server, RoyItems automatically hooks into their official APIs to activate extended capabilities:

### 1. 💰 Economy & Currency Providers
* **Vault:** [SpigotMC Official](https://www.spigotmc.org/resources/vault.34315/?utm_source=chatgpt.com) — Server economy balances, crafting costs, and monetary voucher rewards.
* **PlayerPoints:** [SpigotMC Official](https://www.spigotmc.org/resources/playerpoints.80745/?utm_source=chatgpt.com) — Point currency transactions, gacha vouchers, and recipe point costs.
* **ExcellentEconomy:** [SpigotMC Official](https://www.spigotmc.org/resources/excellenteconomy.84121/?utm_source=chatgpt.com) — Multi-currency economy support, custom currency vouchers, and symbols.

### 2. ⚡ Packets & Core Optimization
* **PacketEvents:** [Modrinth Official](https://modrinth.com/plugin/packetevents?utm_source=chatgpt.com) — High-speed packet interceptor and client-side equipment synchronization.
* **ProtocolLib:** [GitHub Official Releases](https://github.com/dmulloy2/ProtocolLib/releases?utm_source=chatgpt.com) — Asynchronous packet listening, training dummy packets, and custom inventory titles.
* **PlaceholderAPI:** [Official Downloads](https://placeholderapi.com/downloads/?utm_source=chatgpt.com) — Universal placeholder parsing in lore, voucher commands, and `%royitems_<stat>%`.

### 3. ⚔️ RPG, Skills & Combat Engines
* **MythicMobs:** [MythicCraft Official](https://mythiccraft.io/index.php?resources%2Fmythicmobs.1%2F=&utm_source=chatgpt.com) — Intercepts Mythic skill mechanic damage with MMORPG defense mitigation.
* **MMOCore:** [SpigotMC Official](https://www.spigotmc.org/resources/mmocore.70575/?utm_source=chatgpt.com) — Player level, class requirements, and combat stat formulas.
* **AuraSkills:** [Modrinth Official](https://modrinth.com/plugin/auraskills?utm_source=chatgpt.com) — Skill level requirements and RPG attribute stat synchronization.
* **EcoEnchants:** [GitHub Official Releases](https://github.com/Auxilor/EcoEnchants/releases?utm_source=chatgpt.com) — Resolves EcoEnchants custom enchantments inside `#enchantments#` lore.

### 4. 🌍 World Management & Claims
* **WorldGuard:** [EngineHub Official](https://enginehub.org/worldguard?utm_source=chatgpt.com) — Region flags, block-break protection, and combat safety checks.
* **WorldEdit:** [EngineHub Official](https://enginehub.org/worldedit?utm_source=chatgpt.com) — Selection handling and schematic generation.
* **FastAsyncWorldEdit:** [Modrinth Official](https://modrinth.com/plugin/fastasyncworldedit?utm_source=chatgpt.com) — Asynchronous world modifications and zero-lag schematic loading.

### 5. 🎨 Custom Models, Textures & Items
* **Nexo:** [NexoMC Official](https://nexomc.com/products/nexo) — Full custom item models, glyph GUI titles, and Equippable 2D custom armor models.
* **ItemsAdder:** [ItemsAdder Official](https://itemsadder.com/?utm_source=chatgpt.com) — Custom block recognition, custom item model data, and furniture compatibility.
* **Oraxen:** [GitHub Official Releases](https://github.com/oraxen/oraxen/releases?utm_source=chatgpt.com) — Custom item resolution, mechanics compatibility, and model pack IDs.
* **HeadDatabase:** [SpigotMC Official – Arcaniax](https://www.spigotmc.org/resources/head-database.14280/?utm_source=chatgpt.com) — Custom player heads for GUI navigation, crafting station icons, and ingredients.

### 6. 👥 NPC Systems (Crafting Station Triggers)
* **Citizens:** [Citizens Official](https://citizensnpcs.co/?utm_source=chatgpt.com) — Open crafting stations by right-clicking configured Citizens NPC IDs.
* **FancyNpcs:** [Modrinth Official](https://modrinth.com/plugin/fancynpcs?utm_source=chatgpt.com) — Open crafting stations via interaction packets with FancyNPCs.
* **PhoenixNPCs:** [Phoenix Plugins Official](https://www.phoenixplugins.com/product/phoenix-npcs?utm_source=chatgpt.com) — Open crafting stations by interacting with configured PhoenixNPCs.

### 7. 📦 Mining, Generators & Permissions
* **ExtraStorage:** [Modrinth Official](https://modrinth.com/project/extrastorage-plugin?utm_source=chatgpt.com) — Direct zero-lag drop deposits for `mine-radius` and storage ingredient crafting.
* **AdvancedOreGen:** [SpigotMC Historical Listing](https://www.spigotmc.org/resources/advancedoregen.40527/?utm_source=chatgpt.com) — In-place ore regeneration for SkyBlock cobble/fence generators during wide-area mining. *(Note: Historical listing reference).*
* **LuckPerms:** [Official Downloads](https://luckperms.net/download?utm_source=chatgpt.com) — Dynamic permission-based player stats and automatic group stat inheritance.

---

## 📈 PLUGIN STATISTICS

RoyItems collects completely anonymous usage telemetry via **bStats** (Plugin ID: **34832**).

No sensitive player or server data is ever collected or transmitted. Telemetry tracks:
* Active Server Software (Paper / Folia / Purpur / Spigot)
* Minecraft & Java Runtime Version
* Total Registered Custom Items & Items by Archetype
* Active Ecosystem Integrations (15 verified modules)
* Combat Defense Mode (MMORPG / Simple / Custom)

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832">
    <img src="https://img.shields.io/bstats/servers/34832?style=for-the-badge&logo=chartdotjs&logoColor=white&color=007ec6" alt="bStats Servers" />
    <img src="https://img.shields.io/bstats/players/34832?style=for-the-badge&logo=minecraft&logoColor=white&color=007ec6" alt="bStats Players" />
  </a>
</p>

<p align="center">
  👉 <b><a href="https://bstats.org/plugin/bukkit/RoyItems/34832">View Full Statistics on bStats Official Dashboard</a></b>
</p>

*(Server administrators can opt out anytime by setting `enabled: false` in `plugins/bStats/config.yml`)*.

---

## 💬 COMMUNITY & SOURCE CODE

<p align="center">
  <a href="https://github.com/orenaidaucac/RoyItems">
    <img src="https://i.imgur.com/7HdemV1.png" alt="GitHub Repository - Source Code & Documentation" width="220" />
  </a>
  &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <a href="https://discord.gg/zNpkXGFQRS">
    <img src="https://i.imgur.com/61AJVlc.png" alt="Discord Community - Official Support & Announcements" width="220" />
  </a>
</p>

<p align="center">
  🔗 <b><a href="https://github.com/orenaidaucac/RoyItems">View Source Code & Issues on GitHub</a></b>
  &nbsp;•&nbsp;
  💬 <b><a href="https://discord.gg/zNpkXGFQRS">Join Official Community & Support on Discord</a></b>
</p>

---

<p align="center">
  <b>Crafted with ❤️ for Minecraft Server Creators worldwide by orenaidaucac</b>
</p>\n