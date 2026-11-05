# ⚔️ RoyItems — Advanced MMORPG Custom Items Engine

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x%20%7C%201.21.4%2B-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft" />
  <img src="https://img.shields.io/badge/Platforms-Paper%20%7C%20Purpur%20%7C%20Folia%20%7C%20Spigot-f38ba8?style=for-the-badge" alt="Platforms" />
  <img src="https://img.shields.io/badge/Java-21%2B-ea76cb?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Release-v2026.1.6-blue?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/Modrinth-RoyItems-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth" />
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/bstats/servers/34832?style=for-the-badge&logo=chartdotjs&logoColor=white&color=007ec6" alt="bStats Servers" /></a>
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/bstats/players/34832?style=for-the-badge&logo=minecraft&logoColor=white&color=007ec6" alt="bStats Players" /></a>
</p>

<p align="center">
  <a href="https://github.com/orenaidaucac/RoyItems"><img src="https://img.shields.io/badge/GitHub-Wiki_&_Documentation-181717?style=for-the-badge&logo=github&logoColor=white" alt="GitHub Wiki" /></a>
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832"><img src="https://img.shields.io/badge/bStats-Live_Telemetry_Dashboard-007ec6?style=for-the-badge&logo=chartdotjs&logoColor=white" alt="bStats Dashboard" /></a>
</p>

> **Empower your Minecraft server with an enterprise-grade MMORPG item system.**  
> Create limitless custom weapons, armor, accessories, consumables, and socketable gems with an autonomous stat engine, 3-mode defense math, wide-area **Mine Radius** excavation, in-place **AdvancedOreGen** generator preservation, drag-and-drop gem socketing, real-time item expiration, Anvil rename protection, EcoEnchants & Vanilla enchantment dynamic lore, zero-lag **ExtraStorage** batching, and an intuitive in-game GUI editor. Built natively for **Minecraft 1.21.4+** with full **Folia multi-threading** architecture.

---

## 🌟 Why RoyItems?

Crafting a balanced, memorable MMORPG experience requires more than basic lore lines or vanilla attribute modifiers. **RoyItems** was engineered from the ground up to provide server administrators and game designers with total creative control, bulletproof performance, and seamless administration.

* ⛏️ **Mine Radius & Zero-Lag ExtraStorage Excavation:** Expandable horizontal 2D plane mining (3×3, 5×5, 7×7, 9×9, 11×11+) that aggregates all mined drops and deposits them directly into the player's **ExtraStorage** space, completely eliminating dropped item entity lag for 24/24 AFK miners.
* 🌋 **AdvancedOreGen Compatibility:** Soft-dependency hook recognizes SkyBlock generator blocks (fence-water, cobble, basalt) and triggers in-place regeneration via AdvancedOreGen, preventing broken fluid flows, cobblestone lockups, and destroyed machines.
* 🔄 **Fine-Grained LiveUpdate Engine:** Modify stats in your YAML files and run `/ri reload` — RoyItems hot-updates items across online player inventories and ender chests in real time with configurable preservation toggles for gems, durability, enchantments, and lore.
* ✨ **EcoEnchants & Vanilla Dynamic Lore:** Full native support for Vanilla and **EcoEnchants** enchantments inside `#enchantments#` lore placeholders with real-time anvil synchronization and customizable formatting.
* 🔨 **Anvil Rename Protection:** Built-in protection blocks unauthorized item renaming in anvils (including color codes, unicode, and shift-click bypasses) while safely preserving repairs and enchantment combinations.
* 🛡️ **3-Mode Custom Defense System with MythicMobs Mitigation:** Choose between **Simple subtraction**, **MMORPG diminishing returns**, or **Clamped damage boundaries**. Automatically mitigates incoming MythicMobs Skill Mechanic damage with zero double-mitigation.
* 🎯 **Packet-Based Training Dummies & DPS Meter:** Client-side DPS & damage testing dummies with Text Display holograms, immune to entity purges and `/kill @e`, with zero tick performance cost.
* 💎 **Drag & Drop Gem Sockets:** Players pick up gems and drop them directly onto equipment inside their inventory. Supports universal sockets (`1`), specialized sockets (`A`), and multi-gem sockets (`ABC`).
* 🖥️ **Ingame GUI Editor (`/ri editor`):** Edit 100% of item properties in-game with intuitive Previous/Next page navigation, real-time live preview, chat input prompts, and anti-italic text enforcement.
* 🧵 **Native Folia Multi-Threading:** Built strictly around thread-safe scheduling (`entity.getScheduler()`, `getRegionScheduler()`, and `getAsyncScheduler()`) for flawless zero-lag performance on modern servers.
* ⚡ **High-Damage Particle Optimization:** Integrated PacketEvents / ProtocolLib filter eliminates vanilla damage heart spam that causes client FPS drops during high-damage attacks.

---

## 📦 Supported Item Types (14 Total)

Every item belongs to a specific functional category that dictates how it behaves, where it can be equipped, and what mechanics it triggers:

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

## 📊 17 Custom MMORPG Stats & Formulas

RoyItems features an independent stat computation engine that combines base weapon power, flat bonuses, and percentage scalers:

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

## ⛏️ Mine Radius & Zero-Lag ExtraStorage Mining

RoyItems features a specialized wide-area horizontal excavation mechanic for `type: TOOL` items:

* **Strict 2D Horizontal Plane Mining:** Operates strictly on the identical Y-coordinate ($Y = Y_0$, $X \in [X_0 - r, X_0 + r]$, $Z \in [Z_0 - r, Z_0 + r]$). Never mines ceilings or floors, completely preventing accidental pit falls or cave-ins.
* **In-Place AdvancedOreGen Regeneration:** Detects cobblestone, basalt, and fence-water SkyBlock ore generators. Rather than converting blocks to `AIR`, RoyItems triggers AdvancedOreGen's generation pipeline directly on each block, preventing liquid flow disruption and allowing continuous 24/24 mining.
* **Zero-Lag ExtraStorage Batching:** Mined block drops (with Fortune and Silk Touch) are aggregated by Material and deposited in a single batch directly into the player's **ExtraStorage**. No physical item entities are dropped into the world, eliminating server FPS drops.
* **Unbreaking Durability Simulation:** Durability wear is computed using vanilla Unbreaking probabilities ($\frac{1}{\text{level} + 1}$) and committed in a single write operation.
* **Region Protection Check:** Simulates `BlockBreakEvent` on each block — if WorldGuard or island protection cancels the break, the block remains unharmed.

---

## 📈 Live Metrics & bStats Statistics

RoyItems collects anonymous usage telemetry via **bStats** to help track active server deployments, player counts, Java runtimes, and operating systems worldwide.

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

## 🔌 20 Supported Soft Dependencies

RoyItems natively integrates with the Minecraft server ecosystem through dynamic, reflection-safe soft hooks:

### 💰 Economy & Placeholders
* [Vault](https://www.spigotmc.org/resources/vault.34315/) — Core economy integration for crafting station purchases and recipe costs.
* [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) — Expansion exposing `%royitems_stat_<stat>%`, `%royitems_tier%`, and dynamic lore.
* [PlayerPoints](https://modrinth.com/project/playerpoints) — Secondary premium currency support in crafting stations.
* [ExcellentEconomy](https://modrinth.com/plugin/excellenteconomy) — Multi-currency economy integration.

### ⚡ Packets & Performance
* [PacketEvents](https://packetevents.com/) — Protocol-level packet filtering for damage heart particles and client training dummies.
* [ProtocolLib](https://github.com/dmulloy2/ProtocolLib) — Alternative protocol layer for particle suppression and dummy display packets.

### ⚔️ RPG, Combat & Enchantments
* [MythicMobs](https://modrinth.com/plugin/mythicmobs) — Cast Mythic skills from items and mitigate incoming skill damage via RoyItems Defense.
* [MMOCore](https://www.spigotmc.org/resources/mmocore.70575/) — Level/Class item requirements and unified Mana pool scaling.
* [AuraSkills](https://modrinth.com/plugin/auraskills) — Stat scaling, skill level requirements, and mana integration.
* [EcoEnchants](https://hub.auxilor.io/wiki/ecoenchants/ecoenchants) — Dynamic recognition and custom lore rendering of EcoEnchants scrolls and enchantments.

### 🌍 World Management
* [WorldGuard](https://enginehub.org/worldguard) — Region protection verification during block breaking and Mine Radius excavation.
* [WorldEdit](https://enginehub.org/worldedit) — World editing and region coordination.
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

## 📜 Configuration Files (Default YAML)

Click on any section below to expand and view the complete default configuration file:

<details>
<summary><b>⚙️ config.yml (Main Plugin Configuration)</b></summary>

```yaml
# ==============================================================================
#                      ROYITEMS — CONFIGURATION ENGINE
# ==============================================================================
config-version: 2

# General settings
debug: false

# Active localization language:
# Supported: en-US, vi-VN, de-DE, ja-JP, ko-KR, ru-RU, es-ES (or custom lang/<language>.yml)
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
# UPDATE CHECKER
# Automatically checks for new releases on Modrinth (https://modrinth.com/plugin/royitems)
# and notifies server administrators.
# ==============================================================================
update-checker:
  enabled: true
  notify-admins-on-join: true

# ==============================================================================
# COMBAT & CUSTOM DEFENSE SYSTEM
# 'defense' is an independent custom stat directly mitigating incoming damage,
# operating cleanly without conflicting with Vanilla Armor or Armor Toughness.
# ==============================================================================
combat:
  # Particle and sound effects when an attack triggers a critical strike
  critical-hit-particles: true

  # Completely removes damage indicator heart particles (DAMAGE_INDICATOR) from reaching player clients
  # via PacketEvents or ProtocolLib to eliminate screen freeze/stutter during high MMORPG damage hits
  disable-damage-heart-particles: true

  # Hard ceiling for final damage reduction percentage (Damage Reduction stat)
  max-damage-reduction: 0.85

  defense:
    mode: "MMORPG"

    simple:
      floor-damage: 0.0

    mmorpg:
      scale: 100.0
      damage-factor: 1.0
      min-damage-floor: 1.0

    custom-limit:
      min-damage:
        enabled: true
        value: 2.0
      max-damage:
        enabled: false
        value: 100.0

# ==============================================================================
# LIVE UPDATE SYSTEM
# Automatically updates existing custom items across online player inventories,
# equipment, off-hand, and ender chests upon /royitems reload or GUI Editor save.
# ==============================================================================
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

# ==============================================================================
# ENCHANTMENT SYSTEM & ECOENCHANTS INTEGRATION
# ==============================================================================
enchantments:
  auto-sync-lore: true
  hide-vanilla-tooltip: true
  default-format: "&7%name% %level%"
  curse-format: "&c%name% %level%"
  symbol: ""
  level-style: "ROMAN"
  order: "VANILLA_FIRST"

# ==============================================================================
# ANVIL MECHANICS & RESTRICTIONS
# ==============================================================================
anvil:
  prevent-rename: true

# ==============================================================================
# MINE RADIUS MECHANICS & EXECUTOR
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

  allowed-blocks:
    - STONE
    - GRANITE
    - DIORITE
    - ANDESITE
    - DEEPSLATE
    - TUFF
    - CALCITE
    - COBBLESTONE
    - COBBLED_DEEPSLATE
    - NETHERRACK
    - BASALT
    - BLACKSTONE
    - END_STONE
    - SANDSTONE
    - RED_SANDSTONE
    - COAL_ORE
    - IRON_ORE
    - COPPER_ORE
    - GOLD_ORE
    - REDSTONE_ORE
    - EMERALD_ORE
    - LAPIS_ORE
    - DIAMOND_ORE
    - NETHER_QUARTZ_ORE
    - NETHER_GOLD_ORE
    - ANCIENT_DEBRIS
    - DEEPSLATE_COAL_ORE
    - DEEPSLATE_IRON_ORE
    - DEEPSLATE_COPPER_ORE
    - DEEPSLATE_GOLD_ORE
    - DEEPSLATE_REDSTONE_ORE
    - DEEPSLATE_EMERALD_ORE
    - DEEPSLATE_LAPIS_ORE
    - DEEPSLATE_DIAMOND_ORE

  allowed-tools:
    - WOODEN_PICKAXE
    - STONE_PICKAXE
    - IRON_PICKAXE
    - GOLDEN_PICKAXE
    - DIAMOND_PICKAXE
    - NETHERITE_PICKAXE
    - WOODEN_SHOVEL
    - STONE_SHOVEL
    - IRON_SHOVEL
    - GOLDEN_SHOVEL
    - DIAMOND_SHOVEL
    - NETHERITE_SHOVEL
    - WOODEN_AXE
    - STONE_AXE
    - IRON_AXE
    - GOLDEN_AXE
    - DIAMOND_AXE
    - NETHERITE_AXE
    - WOODEN_HOE
    - STONE_HOE
    - IRON_HOE
    - GOLDEN_HOE
    - DIAMOND_HOE
    - NETHERITE_HOE
```
</details>

<details>
<summary><b>🏷️ item-tier.yml (Item Tiers & Drop Glowing Colors)</b></summary>

```yaml
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
  drop-color: LIGHT_PURPLE
```
</details>

<details>
<summary><b>🐉 item-set.yml (Equipment Sets & Multi-Piece Bonuses)</b></summary>

```yaml
dragon_slayer:
  display-name: "&6Dragon Slayer Set"
  bonuses:
    2:
      attack-damage: 15
      crit-rate: 10%
    4:
      attack-damage: 35
      crit-damage: 50%
      pvp-damage: 20

shadow_assassin:
  display-name: "&8Shadow Assassin Set"
  bonuses:
    2:
      movement-speed: 15%
      crit-rate: 15%
    4:
      crit-damage: 75%
      damage-reduction: 10%
```
</details>

<details>
<summary><b>🔨 crafting-station/blacksmith.yml (Sample Workstation Recipe Configuration)</b></summary>

```yaml
title: "<gold><bold>Royal Blacksmith</bold></gold>"
size: 54

permission: ""
commands:
  - blacksmith
  - thoren
  - forge

fancynpcs:
  - "blacksmith_npc"
citizen:
  - 10

navigation:
  prev-page:
    slot: 48
    material: SPECTRAL_ARROW
    name: "<#FFD166>◀ Previous Page <gray>(%prev_page%/%max_pages%)</gray></#FFD166>"
    lore:
      - "<gray>Click to return to previous page."
  next-page:
    slot: 50
    material: SPECTRAL_ARROW
    name: "<#FFD166>Next Page <gray>(%next_page%/%max_pages%)</gray> ▶</#FFD166>"
    lore:
      - "<gray>Click to navigate to next page."
  close:
    slot: 49
    material: BARRIER
    name: "<#EF476F><bold>Close Menu</bold></#EF476F>"
    lore:
      - "<gray>Click to exit crafting station."

sounds:
  craft-success: "entity.player.levelup"
  craft-fail: "entity.villager.no"
  craft-countdown: "block.note_block.hat"

recipes:
  excalibur_forge:
    slot: 20
    output:
      type: "SWORD"
      id: "excalibur"
      amount: 1
    craft-time: 5
    conditions:
      permission: "royitems.craft.excalibur"
      level: 15
    costs:
      vault: 5000.0
      playerpoints: 100
    ingredients:
      - material: DIAMOND_BLOCK
        amount: 5
      - type: "MATERIAL"
        id: "dragon_scale"
        amount: 2
```
</details>

<details>
<summary><b>📖 lore-format/_example.yml (Lore Format Wiki & Reference)</b></summary>

```yaml
# Supported Placeholders:
#   #name#, #type#, #tier#, #tier-color#, #set#, #lore#, #enchantments#,
#   #requirements#, #sockets#, #bar#, #durability#, #cooldown#, #expiration#,
#   #custom-model-data#, #stats#, #mine-radius#

bar: "<dark_gray><strikethrough>------------------------</strikethrough></dark_gray>"

format:
  - "<yellow><bold>%type%</bold></yellow> <dark_gray>•</dark_gray> %tier-color%%tier%"
  - "#bar#"
  - "#description#"
  - "#bar#"
  - "#attack-damage#"
  - "#defense#"
  - "#pve-defense#"
  - "#pvp-damage#"
  - "#attack-speed#"
  - "#max-health#"
  - "#critical-rate#"
  - "#critical-damage#"
  - "#movement-speed#"
  - "#health-regeneration#"
  - "#lifesteal#"
  - "#armor-penetration#"
  - "#damage-reduction#"
  - "#cooldown-reduction#"
  - "#mana#"
  - "#mana-regeneration#"
  - "#mine-radius#"
  - "#vanilla-attack-damage#"
  - "#vanilla-armor#"
  - "#bar#"
  - "#enchantments#"
  - "#sockets#"
  - "#requirements#"
  - "#bar#"
  - "<gray>Durability: <white>#durability#</white>"

stat-format:
  attack-damage:
    display-name: "Attack Damage"
    symbol: "⚔"
    format: "<gray>%symbol% %name%: <#EF476F>+%value%</#EF476F></gray>"
    percent-format: "<gray>%symbol% %name%: <#EF476F>+%value%%</#EF476F></gray>"
  defense:
    display-name: "Defense"
    symbol: "🛡"
    format: "<gray>%symbol% %name%: <#118AB2>+%value%</#118AB2></gray>"
    percent-format: "<gray>%symbol% %name%: <#118AB2>+%value%%</#118AB2></gray>"
  mine-radius:
    display-name: "Mine Radius"
    symbol: "⛏"
    format: "<gray>%symbol% %name%: <#FFD166>%value%</#FFD166></gray>"
    percent-format: "<gray>%symbol% %name%: <#FFD166>%value%</#FFD166></gray>"

sockets-format:
  empty: "&8[&7◇&8] &7Empty Socket (%type%)"
  filled: "&8[&a◆&8] &f%gem%"

enchantments-format:
  format: "&7%name% %level%"
  curse-format: "&c%name% %level%"
  symbol: ""
  level-style: "ROMAN"
  order: "VANILLA_FIRST"

durability-format:
  unbreakable: "&aUnbreakable"
  format: "%current%/%max%"
```
</details>

<details>
<summary><b>⚔️ item/SWORD.yml (Sample Weapons)</b></summary>

```yaml
excalibur:
  material: DIAMOND_SWORD
  name: "&6&lExcalibur"
  lore:
    - "&7The legendary blade of Camelot,"
    - "&7radiating with divine blinding light."
  lore-format: weapon
  unbreakable: true
  glow: true
  stats:
    attack-damage: 25
    crit-rate: 20%
    crit-damage: 50%
    pvp-damage: 10
  level-required: 15
  item-tier: legendary
  item-set: dragon_slayer
  gem-socket:
    - "1"
    - "A"
  version: 1
```
</details>

<details>
<summary><b>🛡️ item/ARMOR.yml (Sample Armor Pieces)</b></summary>

```yaml
dragon_chestplate:
  material: NETHERITE_CHESTPLATE
  name: "&c&lDragon Slayer Chestplate"
  lore:
    - "&7Forged from the tempered scales"
    - "&7of an ancient fire drake."
  lore-format: armor
  unbreakable: true
  stats:
    defense: 45
    damage-reduction: 10%
    max-health: 10
  trim-material: netherite
  trim-pattern: silence
  level-required: 20
  item-tier: epic
  item-set: dragon_slayer
  gem-socket:
    - "1"
    - "ABC"
  version: 1
```
</details>

<details>
<summary><b>💎 item/GEM_STONE.yml (Sample Gem Stones)</b></summary>

```yaml
ruby_gem:
  material: REDSTONE
  name: "&c&lRuby of Destruction"
  lore:
    - "&7An ancient blood gem that"
    - "&7infuses weapons with raw power."
  lore-format: default
  item-tier: epic
  gem-stats:
    attack-damage: 8
    crit-damage: 15%
  gem-target: SWORD
  gem-socket-type: A
  version: 1
```
</details>

<details>
<summary><b>🪶 item/WING.yml (Sample Elytra Wings)</b></summary>

```yaml
celestial_wings:
  material: ELYTRA
  name: "&b&lCelestial Wings"
  lore:
    - "&7Feathers gathered from seraphim,"
    - "&7granting effortless aerial gliding."
  lore-format: default
  unbreakable: true
  stats:
    movement-speed: 25%
    defense: 20
  item-tier: mythic
  version: 1
```
</details>

<details>
<summary><b>🧪 item/CONSUMABLE.yml (Sample Consumables)</b></summary>

```yaml
healing_elixir:
  material: POTION
  name: "&a&lElixir of Rejuvenation"
  lore:
    - "&7Instantly restores vital energies"
    - "&7and cleanses minor afflictions."
  lore-format: default
  potion-effects:
    - REGENERATION:2:10
    - SPEED:1:20
  cooldown: "30 seconds"
  item-tier: uncommon
  version: 1
```
</details>

<details>
<summary><b>📜 item/VOUCHER.yml (Sample Command Vouchers)</b></summary>

```yaml
rank_vip_voucher:
  material: PAPER
  name: "&6&l[VIP] Rank Voucher"
  lore:
    - "&7Right-click to claim the VIP rank!"
    - "&cSingle-use consumable scroll."
  lore-format: default
  glow: true
  commands:
    - "lp user %player% parent add vip"
    - "broadcast &a%player% has redeemed a VIP Voucher!"
  item-tier: legendary
  version: 1
```
</details>

---

## 💻 Commands & Permissions

All commands use the base command `/royitems` or shortcut `/ri`:

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

## 📥 Installation & Requirements

1. **System Requirements:**
   * **Java 21 or higher**
   * **Paper**, **Purpur**, or **Folia 1.21.4+** (Spigot supported)
2. **Installation Steps:**
   * Download the latest `RoyItems-2026.1.6.jar` release.
   * Place the jar into your server's `plugins/` directory.
   * *(Recommended)* Install **ProtocolLib** or **PacketEvents** for heart particle suppression and packet training dummies.
   * *(Optional)* Install **Vault**, **PlaceholderAPI**, **ExtraStorage**, and **AdvancedOreGen** to take advantage of advanced integrations.
   * Start or restart your server.
