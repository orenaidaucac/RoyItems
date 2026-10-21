# ⚔️ RoyItems — Advanced MMORPG Custom Items Engine

![Version](https://img.shields.io/badge/version-2026.1.1-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-1.21.x%20%7C%2026.2%2B-brightgreen.svg)
![Platforms](https://img.shields.io/badge/platforms-Paper%20%7C%20Spigot%20%7C%20Folia-orange.svg)
![Java](https://img.shields.io/badge/java-21%2B-red.svg)
![Build](https://img.shields.io/badge/build-Maven-informational.svg)
![Discord](https://img.shields.io/badge/community-Discord-7289DA.svg)

> **RoyItems** is an enterprise-grade, high-performance MMORPG Custom Items Engine engineered for Minecraft 1.21+ / 26.2+ servers running on **Paper**, **Spigot**, or **Folia** (multi-threaded regionized architecture). Define unlimited custom items, complex RPG stats, socketable gems, set bonuses, tiers, and real-time expiration with 100% hot-reload synchronization — manageable entirely via YAML configuration or the intuitive Ingame GUI Editor.

---

## 📑 Table of Contents
1. [Overview & Architecture](#1-overview--architecture)
2. [Feature Highlights](#2-feature-highlights)
3. [System Requirements](#3-system-requirements)
4. [Installation & Setup](#4-installation--setup)
5. [Directory & File Layout](#5-directory--file-layout)
6. [Supported Item Types (14 Total)](#6-supported-item-types-14-total)
7. [Material Providers](#7-material-providers)
8. [Combat & Custom Defense System](#8-combat--custom-defense-system)
9. [16 Custom Stats & Vanilla Attributes](#9-16-custom-stats--vanilla-attributes)
10. [Dynamic Lore Engine & Formatting](#10-dynamic-lore-engine--formatting)
11. [Item Tiers & Set Bonuses](#11-item-tiers--set-bonuses)
12. [Gem Sockets & Drag-and-Drop Socketing](#12-gem-sockets--drag-and-drop-socketing)
13. [Real-Time Item Expiration & LiveUpdate](#13-real-time-item-expiration--liveupdate)
14. [Ingame GUI Editor (`/ri editor`)](#14-ingame-gui-editor-ri-editor)
15. [Internationalization & Language System](#15-internationalization--language-system)
16. [Commands & Permissions](#16-commands--permissions)
17. [Third-Party Integrations](#17-third-party-integrations)
18. [Item Configuration Example](#18-item-configuration-example)
19. [Building from Source](#19-building-from-source)
20. [Community & Support](#20-community--support)

---

## 1. Overview & Architecture
RoyItems is built from the ground up to solve the most demanding challenges of modern Minecraft MMORPG servers:
- **Decoupled Architecture:** Strict separation between static configuration (`ItemDefinition`) and active player items (`ItemStack` / `ItemInstance`).
- **Persistent Identification (PDC):** Items are permanently tracked through native `PersistentDataContainer` keys (`royitems:item_type`, `royitems:item_id`, `royitems:item_version`, `royitems:item_expire`, `royitems:item_sockets`). Custom items are never identified by volatile display names or lore.
- **Native Folia Multi-Threading:** Every operation interacting with players, entities, inventories, or locations is dispatched through safe platform schedulers (`entity.getScheduler()`, `Bukkit.getRegionScheduler()`, and `Bukkit.getAsyncScheduler()`).

---

## 2. Feature Highlights
- 💎 **14 Default MMORPG Item Types:** Supports `SWORD`, `ARMOR`, `BOW`, `CROSSBOW`, `STAFF`, `TOOL`, `GEM_STONE`, `CONSUMABLE`, `MATERIAL`, `OFF_HAND`, `WING`, `VOUCHER`, `PET`, and `TALISMAN`.
- ⚔️ **16 Custom RPG Stats + Full Vanilla Attributes:** Flat and percentage stat scaling computed via:
  $$\text{Total} = (\text{Base} + \sum \text{Flat}) \times (1 + \sum \text{Percent})$$
- 🛡️ **3-Mode Custom Defense System:** Flat reduction (`SIMPLE`), non-linear diminishing returns curve (`MMORPG`), or clamped bounds (`CUSTOM_LIMIT`).
- 📜 **Dynamic Lore Engine:** Independent placeholders per stat (`#attack-damage#`, `#defense#`), smart `#bar#` auto-pruning, and automatic suppression of absent/zero stats without blank lines.
- 🌍 **Multi-Language Localization:** Built-in grammar-accurate translations for 7 languages (`en-US`, `vi-VN`, `de-DE`, `ja-JP`, `ko-KR`, `ru-RU`, `es-ES`) with seamless fallback.
- 🔄 **LiveUpdate Engine:** Hot-reloads all existing items in player inventories and ender chests on `/ri reload` without wiping socketed gems or durability.
- 🖥️ **Ingame GUI Editor:** Paginated MMOItems-style editor with live preview, dedicated buttons for every attribute, safe chat input, and concurrent edit locks.
- ⚡ **Auto Config Migrator:** Automatically checks `config-version`, creates `.bak` backups on version upgrades, and deep-merges missing keys without overwriting server settings.
- 🔔 **Admin-Only Update Checker & `/ri checkupdate`:** Asynchronously verifies releases against GitHub and reports exactly how many releases are ahead.

---

## 3. System Requirements
- **Java Runtime:** `Java 21` or newer.
- **Server Platform:** `Paper 1.21.x+`, `Purpur 1.21.x+`, `Spigot 1.21.x+`, or `Folia 1.21.x+` (Minecraft 26.2+ ready).
- **Memory Allocation:** Minimum 4 GB RAM recommended for Paper/Folia.

---

## 4. Installation & Setup
1. Download the latest release `RoyItems-2026.1.1.jar`.
2. Place the jar file into your server's `plugins/` directory.
3. Start or restart your server.
4. Default configuration files, language templates, and item examples will generate automatically.

---

## 5. Directory & File Layout
```
plugins/RoyItems/
 ├── config.yml              # Core settings, defense formulas, update checker
 ├── lang/                   # Localization files
 │    ├── en-US.yml          # English (Default)
 │    ├── vi-VN.yml          # Vietnamese
 │    ├── de-DE.yml          # German
 │    ├── ja-JP.yml          # Japanese
 │    ├── ko-KR.yml          # Korean
 │    ├── ru-RU.yml          # Russian
 │    └── es-ES.yml          # Spanish
 ├── item/                   # Item definitions organized by TYPE
 │    ├── SWORD.yml
 │    ├── ARMOR.yml
 │    ├── BOW.yml
 │    ├── STAFF.yml
 │    ├── GEM_STONE.yml
 │    ├── WING.yml
 │    ├── TALISMAN.yml
 │    ├── CONSUMABLE.yml
 │    └── VOUCHER.yml
 ├── lore-format/            # Dynamic lore layout templates
 │    ├── _example.yml       # Protected wiki documentation reference
 │    ├── default.yml
 │    ├── weapon.yml
 │    └── armor.yml
 ├── item-tier.yml           # Rarity tiers (Common, Rare, Legendary, etc.)
 ├── item-set.yml            # Equipment sets and threshold bonus stats
 └── gui/
      └── editor-gui.yml     # Ingame GUI Editor layout and buttons
```

---

## 6. Supported Item Types (14 Total)

| Item Type | Primary Purpose | Trigger Slot / Action |
|---|---|---|
| `SWORD` | Melee combat weapon with attack damage, crit, and PvP/PvE bonuses | `MAIN_HAND` |
| `ARMOR` | Helmets, Chestplates, Leggings, Boots with trims and dye colors | Armor Slots |
| `BOW` | Ranged bow weapon with arrow damage scaling | `MAIN_HAND` |
| `CROSSBOW` | Ranged crossbow weapon | `MAIN_HAND` |
| `STAFF` | Magical catalyst triggering spellcasts and MythicMobs skills | `MAIN_HAND` (Right-Click) |
| `TOOL` | Pickaxes, axes, shovels, hoes | `MAIN_HAND` |
| `GEM_STONE` | Socketable gem drag-and-dropped directly onto slotted items | Inventory Cursor |
| `CONSUMABLE` | Potions, scrolls, and food applying potion effects and cooldowns | Right-Click |
| `MATERIAL` | Crafting reagents, smithing components, and raw materials | Inventory |
| `OFF_HAND` | Shields, tomes, and off-hand charms | `OFF_HAND` |
| `WING` | Elytra-based flight gear granting defense and gliding speed | `CHEST` |
| `VOUCHER` | Reward coupons executing console commands | Right-Click |
| `PET` | Companion pet items | Hand / Inventory |
| `TALISMAN` | Passive stat-granting trinkets active while in the inventory | Inventory (`INV`) |

---

## 7. Material Providers
RoyItems supports standard and custom resource pack identifiers:
- **Vanilla:** `DIAMOND_SWORD`, `NETHERITE_CHESTPLATE`, `BOW`...
- **HeadDatabase:** `hdb-<id>` (Fetches custom skulls via HeadDatabase).
- **Base64 Head:** `basehead-<base64>` (Direct Base64 encoded skull skin).
- **Nexo:** `nexo:<id>` (Preserves custom item model and component data).
- **ItemsAdder:** `itemsadder:<id>` or `ia:<id>`.
- **Oraxen:** `oraxen:<id>`.

---

## 8. Combat & Custom Defense System
Custom defense is configured in `config.yml` and operates independently of vanilla armor formulas:

- **Mode 1 — SIMPLE:**
  $$\text{Final Damage} = \text{Attack Damage} - \text{Defense}$$
- **Mode 2 — MMORPG (Default):**
  $$\text{Final Damage} = \text{Damage} \times \left(\frac{\text{scale}}{\text{scale} + \text{Defense}}\right) \times \text{damage-factor}$$
- **Mode 3 — CUSTOM_LIMIT:** Flat subtraction bounded by configurable `min-damage` and `max-damage` limits.

---

## 9. 16 Custom Stats & Vanilla Attributes

### Custom MMORPG Stats:
- `attack-damage`: Base physical melee damage.
- `arrow-damage`: Bonus ranged projectile damage.
- `max-health`: Additional maximum player health points.
- `attack-speed`: Attack bar recharge and swing speed multiplier.
- `crit-rate`: Critical strike chance percentage (`%`).
- `crit-damage`: Bonus damage multiplier on critical strike (`%`).
- `defense`: Custom defense mitigating incoming damage via the MMORPG curve.
- `pvp-damage`: Bonus damage dealt against players.
- `pve-damage`: Bonus damage dealt against monsters and bosses.
- `pvp-defense`: Bonus defense against player attacks.
- `pve-defense`: Bonus defense against monster attacks.
- `damage-reduction`: Percentage of total damage absorbed directly (`%`).
- `lifesteal`: Restores health as a percentage of damage dealt (`%`).
- `armor-penetration`: Bypasses a percentage of enemy defense (`%`).
- `cooldown-reduction`: Reduces cooldowns for skills and items (`%`).
- `mana`: Increases player maximum mana pool (MMOCore / AuraSkills).
- `mana-regeneration`: Accelerates passive mana regeneration rate (`%`).

---

## 10. Dynamic Lore Engine & Formatting
Configure layout templates in `lore-format/<name>.yml`:
- Use `#bar#` for smart dividers that automatically prune consecutive or dangling lines.
- Use independent stat placeholders (e.g. `#attack-damage#`, `#defense#`). Any stat not present on the item is omitted cleanly without leaving blank lines.
- Refer to `lore-format/_example.yml` for an exhaustive wiki of all placeholders.

---

## 11. Item Tiers & Set Bonuses
- **Item Tiers (`item-tier.yml`):** Define item rarities (Common, Uncommon, Rare, Epic, Legendary, Mythic) with custom prefixes and colors.
- **Item Sets (`item-set.yml`):** Configure multi-piece equipment sets granting bonus stats at piece milestones (e.g. 2-piece and 4-piece bonuses).

---

## 12. Gem Sockets & Drag-and-Drop Socketing
Equip gems onto gear seamlessly via cursor drag-and-drop:
```yaml
gem-socket:
  - "1"     # Universal socket (accepts any gem)
  - "A"     # Strict socket (only accepts gems with gem-id: "A")
  - "ABC"   # Flexible socket (accepts gem-id "A", "B", or "C")
```

---

## 13. Real-Time Item Expiration & LiveUpdate
- **Expiration (`expired: 3 days`):** Tracks real-time Epoch countdowns. Expired items turn to dust when handled. Server operators and players with `royitems.item.expired.bypass` are exempt.
- **LiveUpdate:** Executing `/ri reload` scans player inventories and ender chests, instantly upgrading old item versions to new configs without wiping socketed gems or durability.

---

## 14. Ingame GUI Editor (`/ri editor`)
Access the editor anytime via:
```bash
/ri editor
```
- **Category Selector:** Browse the 14 item types with item counts.
- **Paginated Item Browser:** Inspect, give, or delete items.
- **Detail Property Editor:** 3 organized pages to adjust stats, models, lore, enchantments, and requirements with real-time preview and multi-admin concurrency locking.

---

## 15. Internationalization & Language System
Set your preferred language in `config.yml`:
```yaml
language: en-US
```
Supported out of the box:
- `en-US` — English (Default)
- `vi-VN` — Vietnamese (Tiếng Việt)
- `de-DE` — German (Deutsch)
- `ja-JP` — Japanese (日本語)
- `ko-KR` — Korean (한국어)
- `ru-RU` — Russian (Русский)
- `es-ES` — Spanish (Español)

Missing keys in any language file automatically fall back to `en-US.yml` without disruption.

---

## 16. Commands & Permissions

| Command | Description | Permission |
|---|---|---|
| `/ri help` | Displays the help menu | `royitems.command.help` |
| `/ri reload` | Reloads configurations and triggers LiveUpdate | `royitems.command.reload` |
| `/ri editor` | Opens the Ingame GUI Editor | `royitems.command.editor` |
| `/ri checkupdate` | Checks current version and pending releases | `royitems.command.checkupdate` |
| `/ri give <player\|all> <type> <id> [amount]` | Gives custom items (supports ranges like `1-3`) | `royitems.command.give` |
| `/ri take <player\|all> <type> <id> [amount]` | Removes custom items by exact ID | `royitems.command.take` |
| `/ri drop <player\|all> <type> <id> [amount]` | Drops item at player location | `royitems.command.drop` |
| `/ri drop <world> <x> <y> <z> <type> <id> [amount]` | Drops item at specified world coordinates | `royitems.command.drop` |

---

## 17. Third-Party Integrations

| Plugin | Type | Compatibility Status |
|---|---|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Soft-depend | Economy balances and transaction validation |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Soft-depend | Stat placeholders (`%royitems_stat_<stat>%`) |
| [MythicMobs](https://mythiccraft.io/) | Soft-depend | Triggers MythicMobs skills via weapon clicks |
| [MMOCore](https://www.spigotmc.org/resources/mmocore.70575/) | Soft-depend | Class, Level, and Mana integration |
| [AuraSkills](https://www.spigotmc.org/resources/auraskills.81069/) | Soft-depend | Skill Level requirements and Mana pools |
| [WorldGuard](https://enginehub.org/worldguard/) | Soft-depend | Region flag protection (PvP, item usage) |
| [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/) | Soft-depend | Skull texture provider (`hdb-<id>`) |
| [Nexo](https://polymart.org/resource/nexo.6212) | Soft-depend | Custom models and item components |
| [ItemsAdder](https://itemsadder.devs.beer/) | Soft-depend | Custom models and texture packs |
| [Oraxen](https://oraxen.com/) | Soft-depend | Custom models and glyphs |
| [TriumphGUI](https://github.com/TriumphTeam/triumph-gui) | Shaded | Modern, zero-NMS GUI library compatible with Paper and Folia |

---

## 18. Item Configuration Example
```yaml
excalibur:
  material: DIAMOND_SWORD
  name: "<gradient:#FFD166:#06D6A0>Excalibur</gradient>"
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

---

## 19. Building from Source
RoyItems is built with **Maven** and requires **Java 21+**:
```bash
git clone https://github.com/orenaidaucac/RoyItems.git
cd RoyItems
mvn clean package
```
The compiled jar will be located at `target/RoyItems-2026.1.1.jar`.

---

## 20. Community & Support
- 💬 **Discord Support:** [https://discord.gg/zNpkXGFQRS](https://discord.gg/zNpkXGFQRS)
- 🐛 **Issue Tracker:** Report bugs or submit feature requests on GitHub.

---
*Maintained by orenaidaucac with uncompromising standards for Minecraft MMORPG servers.*
