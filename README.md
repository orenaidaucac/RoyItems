# ⚔️ RoyItems — Advanced MMORPG Custom Items Engine

<p align="center">
  <img src="https://img.shields.io/badge/Version-2026.1.3-blue?style=for-the-badge&logo=git" alt="Version" />
  <img src="https://img.shields.io/badge/Minecraft-1.21.x%20%7C%2026.3%2B-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white" alt="Minecraft" />
  <img src="https://img.shields.io/badge/Platforms-Paper%20%7C%20Spigot%20%7C%20Purpur%20%7C%20Folia-orange?style=for-the-badge" alt="Platforms" />
  <img src="https://img.shields.io/badge/Java-21%2B-ea76cb?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java" />
  <img src="https://img.shields.io/badge/Modrinth-RoyItems-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white" alt="Modrinth" />
  <img src="https://img.shields.io/badge/Build-Maven-informational?style=for-the-badge&logo=apachemaven" alt="Maven" />
</p>

> **RoyItems** is an enterprise-grade, high-performance MMORPG Custom Items Engine engineered for Minecraft 1.21+ / 26.2+ servers running on **Paper**, **Purpur**, or **Folia** (multi-threaded regionized architecture). Define unlimited custom items, complex RPG stats, socketable gems, set bonuses, tiers, item security restrictions, and real-time expiration with 100% hot-reload synchronization — manageable entirely via YAML configuration or the intuitive Ingame GUI Editor.

---

## 📑 Table of Contents

1. [Architecture & Design Principles](#-architecture--design-principles)
2. [Feature Highlights](#-feature-highlights)
3. [Supported Item Types (14 Total)](#-supported-item-types-14-total)
4. [16 Custom RPG Stats & Calculation](#-16-custom-rpg-stats--calculation)
5. [Item Security & Protection Flags](#-item-security--protection-flags)
6. [Non-Linear Defense Engine](#-non-linear-defense-engine)
7. [Tier Glowing Drop Outline System](#-tier-glowing-drop-outline-system)
8. [Drag & Drop Gem Sockets](#-drag--drop-gem-sockets)
9. [Ingame GUI Editor (`/ri editor`)](#-ingame-gui-editor-ri-editor)
10. [Configuration Guide](#-configuration-guide)
11. [Commands & Permissions](#-commands--permissions)
12. [PlaceholderAPI Reference](#-placeholderapi-reference)
13. [Compatible Plugins & Official Links](#-compatible-plugins--official-links)
14. [Building from Source](#-building-from-source)

---

## 🏛️ Architecture & Design Principles

* **Native PersistentDataContainer (PDC):** Items are tracked permanently using namespaced tags (`royitems:item_type`, `royitems:item_id`, `royitems:item_version`, `royitems:item_expire`, `royitems:item_sockets`, `royitems:item_tier`). Items are never identified by fragile display names or lore text.
* **Folia Regionized Multi-Threading:** All entity, location, and world interactions are scheduled through region-aware platform schedulers (`entity.getScheduler()`, `Bukkit.getRegionScheduler()`, and `Bukkit.getAsyncScheduler()`) ensuring zero desync on Folia.
* **LiveUpdate Service:** When editing item stats, lore formats, or tiers in YAML, running `/ri reload` automatically hot-updates all existing items across online player inventories and ender chests without wiping durability or socketed gems.
* **Damage Particle Filter:** Seamlessly intercepts vanilla `DAMAGE_INDICATOR` heart particle packets using PacketEvents or ProtocolLib, eliminating client FPS drops and screen freezing when dealing massive MMORPG damage numbers.

---

## 🌟 Feature Highlights

* ⚔️ **14 Item Categories:** Weapons, armor, tools, accessories, wings, pets, vouchers, consumables, and gems.
* 🛡️ **3-Mode Defense Engine:** Choose between `MMORPG` (diminishing returns curve), `SIMPLE` (flat reduction), or `CUSTOM_LIMIT` (clamped min/max boundaries).
* 🔒 **Built-In Item Restrictions:** 4 airtight protection flags (`disable-on-use`, `disable-on-drop`, `disable-on-crafting`, `disable-on-enchantment`).
* 💎 **Drag-and-Drop Gem Socketing:** Socket gems intuitively directly inside player inventories.
* 🌈 **Dropped Item Tier Glow:** Ground items glow with their tier's distinct Bukkit/Hex color.
* 🖥️ **Full-Featured GUI Editor:** Modify 100% of item properties in-game with live preview, concurrent editing locks, and anti-italic typography.
* 📜 **Smart Dynamic Lore Engine:** Conditional line pruning and automatic `#bar#` divider cleanup.
* 🔄 **Auto Config Migrator:** Automatically checks `config-version`, creates `.bak` backups on updates, and deep-merges new keys safely without overwriting server settings.

---

## 📦 Supported Item Types (14 Total)

| Item Type | Description | Equipment Slot / Activation |
| :--- | :--- | :--- |
| `SWORD` | Melee swords with attack damage, critical strikes, and PvP/PvE scaling | `Main Hand` |
| `ARMOR` | Helmets, Chestplates, Leggings, and Boots with Armor Trim & Dye support | `Head`, `Chest`, `Legs`, `Feet` |
| `BOW` | Ranged bows applying custom arrow damage and critical hits | `Main Hand` |
| `CROSSBOW` | High-impact ranged crossbows | `Main Hand` |
| `STAFF` | Magic casting staves triggering abilities, mana, or Mythic skills | `Main Hand` |
| `TOOL` | Harvesting pickaxes, shovels, and axes with custom RPG stats | `Main Hand` |
| `GEM_STONE` | Sockets upgrade stones slotted via drag-and-drop onto gear | `Inventory Drag & Drop` |
| `CONSUMABLE` | Potions, scrolls, and food granting timed effects and cooldowns | `Right-Click` |
| `MATERIAL` | Crafting resources and quest trade materials | `Inventory` |
| `OFF_HAND` | Shields, tomes, and off-hand defensive gear | `Off Hand` |
| `WING` | Gliders and wings (Elytra) enhancing flight velocity and protection | `Chestplate Slot` |
| `VOUCHER` | Claimable scrolls executing console commands upon single use | `Right-Click` |
| `TALISMAN` | Passive relics granting bonuses simply by resting in the inventory | `Inventory / Hand` |
| `PET` | Companion summoning tokens and collectible pets | `Inventory / Hand` |

---

## 📊 16 Custom RPG Stats & Calculation

RoyItems calculates total stats using an industry-standard formula:

$$\text{Total Stat} = (\text{Base} + \sum \text{Flat Modifiers}) \times (1 + \sum \text{Percentage Modifiers})$$

```yaml
stats:
  attack-damage: 25        # Flat: +25 Attack Damage
  crit-rate: 20%           # Percent: +20% Critical Strike Chance
  crit-damage: 50%         # Percent: +50% Critical Damage
  pvp-damage: 10           # Flat: +10 Bonus Damage against Players
```

### Supported RPG Stats:
* ⚔️ `attack-damage` — Base physical melee damage.
* 🏹 `arrow-damage` — Custom ranged projectile damage.
* ⚡ `attack-speed` — Weapon swing recovery speed.
* 🎯 `crit-rate` — Critical strike chance percentage.
* 💥 `crit-damage` — Critical hit bonus damage multiplier.
* 🛡️ `defense` — Core defense points for damage mitigation.
* ⚔️ `pvp-damage` — Bonus damage dealt strictly against other players.
* 🗡️ `pve-damage` — Bonus damage dealt strictly against monsters and bosses.
* 🛡️ `pvp-defense` — Resistance reducing incoming damage from player attacks.
* 🛡️ `pve-defense` — Resistance reducing incoming damage from mob attacks.
* 🔰 `damage-reduction` — Direct percentage damage mitigation cap.
* ❤️ `max-health` — Increases player maximum health.
* 👟 `movement-speed` — Increases sprint and walk speed.
* 🏋️ `knockback-resistance` — Resistance against knockback.
* 🛡️ Vanilla Attributes — Direct binding to `generic.armor`, `generic.armor_toughness`, etc.

---

## 🔒 Item Security & Protection Flags

Add airtight restrictions to items in YAML or through the GUI Editor:

```yaml
disable-on-use: true         # Blocks right-click usage, block placing, consumption, shearing, shooting
disable-on-drop: true        # Blocks dropping via Q key, dragging outside GUI, or tossing from chests
disable-on-crafting: true    # Blocks using as ingredient in crafting tables, stonecutters, furnaces, brewing stands
disable-on-enchantment: true # Blocks vanilla enchanting, anvil repairs/renaming, grindstones, smithing tables
```

* **`disable-on-use`:** Cancels `PlayerInteractEvent` (right-click air/block), `BlockPlaceEvent`, `PlayerItemConsumeEvent`, `EntityShootBowEvent`, `PlayerShearEntityEvent`, bucket filling/emptying, and projectile launching.
* **`disable-on-drop`:** Cancels `PlayerDropItemEvent`, clicking outside inventory window (`SlotType.OUTSIDE`), pressing `Q` or `Ctrl+Q` over slots in any container, and dragging items outside windows.
* **`disable-on-crafting`:** Prevents item from being used in `PrepareItemCraftEvent`, `CraftItemEvent`, `FurnaceSmeltEvent`, `FurnaceBurnEvent`, `BrewEvent`, stonecutters, looms, cartography tables, and smithing tables.
* **`disable-on-enchantment`:** Blocks item from `PrepareItemEnchantEvent`, `EnchantItemEvent`, `PrepareAnvilEvent`, `PrepareGrindstoneEvent`, and `PrepareSmithingEvent`.

---

## 🛡️ Non-Linear Defense Engine

Configure damage formulas in `config.yml`:

```yaml
combat:
  defense:
    mode: "MMORPG" # MMORPG, SIMPLE, or CUSTOM_LIMIT
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
```

* **MMORPG Curve Formula:**
  $$\text{Final Damage} = \text{Damage} \times \left( \frac{\text{scale}}{\text{scale} + \text{Effective Defense}} \right)$$
* Automatically computes `Effective Defense = Defense + (PvP/PvE Defense)` depending on whether the attacker is a player or an entity.

---

## 🌈 Tier Glowing Drop Outline System

Configure dropped items to render glowing outlines with tier-specific colors:

```yaml
# item-tier.yml
epic:
  name: "Epic"
  color: "&5"
  weight: 10
  drop-color: "DARK_PURPLE" # Bukkit NamedTextColor or Hex color code (#A335EE)
```

Items dropped on the ground automatically glow with their tier's distinct color, smoothly visible to both the owner and nearby players.

---

## 💎 Drag & Drop Gem Sockets

1. Define socket slots on equipment:
   ```yaml
   gem-socket:
     - "1"  # Universal: accepts any gem
     - "A"  # Specific: accepts only Gem A
     - "ABC"# Multi-gem: accepts Gem A, B, or C
   ```
2. Pick up a `GEM_STONE` item on your inventory cursor.
3. Click directly onto the socketable equipment.
4. The gem is applied instantly, updating lore and stats while preserving durability and modifiers!

---

## 🖥️ Ingame GUI Editor (`/ri editor`)

Access the editor ingame with `/ri editor`:
* **Row 1:** Live Item Preview (slot 4) — click to receive a copy directly.
* **Row 2 (Page 1):** Material, Name, Lore, Model, Template, Unbreakable, Glow.
* **Row 3 (Page 1):** Tier, Set, Hide Enchantments, Hide Attributes.
* **Row 4 (Page 1):** Item Protections (`Disable On Use`, `Disable On Drop`, `Disable On Crafting`, `Disable On Enchantment`).
* **Page 2:** All 16 Combat and RPG Statistics.
* **Page 3:** Requirements (Level, Permissions, MMOCore, AuraSkills, Worlds, Biomes), Enchantments, Potion Effects, Sockets, Cooldowns, Expiration, Armor Trims, and Leather Dyes.

---

## ⚙️ Configuration Guide

### Item Configuration (`item/SWORD.yml`):
```yaml
excalibur:
  material: DIAMOND_SWORD
  name: "<gradient:#FFD166:#06D6A0><bold>Excalibur</bold></gradient>"
  lore:
    - "&7The legendary blade of Camelot,"
    - "&7radiating with divine blinding light."
  lore-format: weapon
  unbreakable: true
  glow: true
  item-tier: legendary
  item-set: dragon_slayer
  disable-on-drop: true
  disable-on-crafting: true
  stats:
    attack-damage: 25
    crit-rate: 20%
    crit-damage: 50%
    pvp-damage: 10
  level-required: 15
  mmocore-required:
    class: "WARRIOR"
    level: 10
  enchantments:
    sharpness: 5
    unbreaking: 3
  gem-socket:
    - "1"
    - "A"
  version: 1
```

---

## 📋 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ri help` | `royitems.command.help` | Displays command assistance menu |
| `/ri reload` | `royitems.command.reload` | Hot-reloads all configs and triggers LiveUpdate |
| `/ri editor` | `royitems.command.editor` | Opens the paginated Ingame GUI Editor |
| `/ri checkupdate` | `royitems.command.checkupdate` | Checks for updates on Modrinth |
| `/ri give <player\|all> <type> <id> [amount]` | `royitems.command.give` | Gives custom items (supports ranges: `1-5`) |
| `/ri take <player\|all> <type> <id> [amount]` | `royitems.command.take` | Removes specified amount of custom items |
| `/ri drop <player\|all> <type> <id> [amount]` | `royitems.command.drop` | Drops items at target player location |
| `/ri drop <world> <x> <y> <z> <type> <id> [amount]` | `royitems.command.drop` | Drops items at exact world coordinates |

---

## 🧩 PlaceholderAPI Reference

| Placeholder | Output / Description |
| :--- | :--- |
| `%royitems_stat_<stat>%` | Returns player's calculated stat (e.g. `%royitems_stat_attack_damage%`) |
| `%royitems_hand_type%` | Item type of held item (`SWORD`, `ARMOR`, etc.) |
| `%royitems_hand_id%` | Unique ID of held item |
| `%royitems_hand_name%` | Formatted display name of held item |
| `%royitems_hand_tier%` | Tier identifier of held item |
| `%royitems_hand_version%` | LiveUpdate internal version number of held item |

---

## 🔗 Compatible Plugins & Official Links

RoyItems is tested and natively integrated with:

* 🚀 **Server Engines:**
  * [PaperMC](https://papermc.io)
  * [Purpur](https://purpurmc.org)
  * [Folia](https://papermc.io/software/folia)
* ⚡ **Packet Infrastructure:**
  * [PacketEvents](https://modrinth.com/plugin/packetevents)
  * [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
* 💰 **Economy, Placeholders & Skills:**
  * [Vault](https://www.spigotmc.org/resources/vault.34315/)
  * [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
  * [MMOCore](https://www.spigotmc.org/resources/mmocore.70506/)
  * [AuraSkills](https://www.spigotmc.org/resources/auraskills.115867/)
* 🛡️ **Combat & Protection:**
  * [MythicMobs](https://mythiccraft.io/index.php?resources/mythicmobs.1/)
  * [WorldGuard](https://enginehub.org/worldguard)
* 🎨 **Resource Packs & Models:**
  * [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/)
  * [Oraxen](https://www.spigotmc.org/resources/oraxen.72448/)
  * [Nexo](https://modrinth.com/plugin/nexo)
  * [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/)

---

## 🔨 Building from Source

```bash
# Clone the repository
git clone https://github.com/orenaidaucac/RoyItems.git
cd RoyItems

# Build shaded distribution jar
mvn clean package
```

The compiled JAR will be output to `target/RoyItems-2026.1.3.jar`.
