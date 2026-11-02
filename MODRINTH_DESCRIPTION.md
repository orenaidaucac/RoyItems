# ⚔️ RoyItems — Advanced MMORPG Custom Items Engine

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x%20%7C%201.21.4%2B-2ea44f?style=for-the-badge&logo=minecraft&logoColor=white)
![Platforms](https://img.shields.io/badge/Platforms-Paper%20%7C%20Purpur%20%7C%20Folia%20%7C%20Spigot-f38ba8?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21%2B-ea76cb?style=for-the-badge&logo=openjdk&logoColor=white)
![Version](https://img.shields.io/badge/Release-v2026.1.4-blue?style=for-the-badge)
![Modrinth](https://img.shields.io/badge/Modrinth-RoyItems-00AF5C?style=for-the-badge&logo=modrinth&logoColor=white)
[![bStats](https://img.shields.io/bstats/servers/34832?style=for-the-badge&logo=chartdotjs&logoColor=white&color=007ec6)](https://bstats.org/plugin/bukkit/RoyItems/34832)

> **Empower your Minecraft server with an enterprise-grade MMORPG item system.**  
> Create limitless custom weapons, armor, accessories, consumables, and gems with a dedicated stat engine, non-linear defense formulas, drag-and-drop gem socketing, real-time item expiration, airtight item security restrictions, and a sleek in-game GUI editor. Built natively for **Minecraft 1.21.4+** with full **Folia multi-threading** support.

---

## 🌟 Why RoyItems?

Crafting a balanced, memorable MMORPG experience requires more than basic lore lines or vanilla attribute modifiers. **RoyItems** was engineered from the ground up to provide server administrators and game designers with total creative control, bulletproof performance, and seamless administration.

* 🔄 **LiveUpdate Engine:** Modify stats in your YAML files and run `/ri reload` — RoyItems instantly scans online player inventories and ender chests, hot-updating items to the newest version while preserving slotted gems, durability, and timers.
* 🔨 **Modular Crafting Stations:** Create unlimited GUI workstations powered by TriumphGUI with custom command shortcuts, FancyNpcs/Citizens NPC binds, multi-currency recipes, and dynamic client-translated lore.
* 🎯 **Packet-Based Training Dummies:** Client-side DPS & damage testing dummies with Text Display holograms, immune to entity purges and `/kill @e`, with zero tick performance cost.
* 🛡️ **3-Mode Custom Defense System:** Say goodbye to broken armor math. Choose between **Simple subtraction**, **MMORPG diminishing returns** (low-attack players still deal partial damage to high-defense bosses), or **Clamped damage boundaries**.
* 🔒 **Built-In Item Security & Protection:** Natively prevent players from right-clicking/consuming, dropping, crafting, or enchanting specific items with zero external scripts.
* 💎 **Drag & Drop Gem Sockets:** Players pick up gems and drop them directly onto equipment inside their inventory. Supports universal sockets (`1`), specialized sockets (`A`), and multi-gem sockets (`ABC`).
* 🖥️ **Ingame GUI Editor (`/ri editor`):** Edit 100% of item properties in-game with intuitive Previous/Next page navigation, real-time live preview, chat input prompts, and anti-italic text enforcement.
* 🧵 **Native Folia Multi-Threading:** Built strictly around thread-safe scheduling (`entity.getScheduler()`, `getRegionScheduler()`, and `getAsyncScheduler()`) for flawless zero-lag performance on modern servers.
* ⚡ **High-Damage Particle Optimization:** Integrated PacketEvents / ProtocolLib filter eliminates vanilla damage heart spam that causes client FPS drops during high-damage attacks.
* 📜 **Smart Dynamic Lore Engine:** Modular lore templates with automated `#bar#` cleanup — never worry about awkward double-dividers or trailing blank lines again.

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
| `TOOL` | Harvesting pickaxes, shovels, and axes with custom RPG stats | `Main Hand` |
| `GEM_STONE` | Sockets upgrade stones slotted via drag-and-drop onto gear | `Inventory Drag & Drop` |
| `CONSUMABLE` | Potions, scrolls, and food granting timed potion effects and cooldowns | `Right-Click` |
| `MATERIAL` | Crafting resources and quest trade materials | `Inventory` |
| `OFF_HAND` | Shields, tomes, and off-hand defensive gear | `Off Hand` |
| `WING` | Gliders and wings (Elytra) enhancing flight velocity and protection | `Chestplate Slot` |
| `VOUCHER` | Claimable scrolls executing console commands upon single use | `Right-Click` |
| `TALISMAN` | Passive relics granting bonuses simply by resting in the player's inventory | `Inventory / Hand` |
| `PET` | Companion summoning tokens and collectible pets | `Inventory / Hand` |

---

## 🔒 Item Security & Restriction System

Need quest items, display collectibles, currency, or event trophies that players cannot exploit? RoyItems includes 4 built-in protection flags toggleable via YAML or directly inside the GUI Editor:

```yaml
excalibur:
  material: DIAMOND_SWORD
  disable-on-use: true         # Blocks right-click usage, block placing, consumption, shearing, shooting
  disable-on-drop: true        # Blocks dropping via Q key, dragging outside GUI, or tossing from chests
  disable-on-crafting: true    # Blocks using as ingredient in crafting tables, stonecutters, furnaces, brewing stands
  disable-on-enchantment: true # Blocks vanilla enchanting, anvil repairs/renaming, grindstones, smithing tables
```

* **`disable-on-use`:** Completely denies right-clicking in air/blocks, placing blocks, consuming food/potions, shearing entities, shooting bows, throwing projectiles, or interacting with armor stands.
* **`disable-on-drop`:** Prevents tossing items out of inventory or containers (chests, barrels, shulkers) via hotkeys (`Q`, `Ctrl+Q`), mouse drag, or clicking outside window slots.
* **`disable-on-crafting`:** Prevents the item from ever being consumed in 2x2/3x3 crafting grids, stonecutters, furnace smelting, furnace fuel burning, brewing stands, looms, cartography tables, or smithing tables.
* **`disable-on-enchantment`:** Disallows inserting the item into Enchanting Tables, combining/repairing in Anvils, disenchanting in Grindstones, or modifying in Smithing Tables.

---

## 🔨 Advanced GUI Crafting Stations (New in v2026.1.4)

Easily build custom crafting stations, smithing tables, and alchemical forges in `plugins/RoyItems/crafting-station/<station-id>.yml` using [TriumphGUI](https://github.com/TriumphTeam/TriumphGUI).

```yaml
title: "<gradient:#FF512F:#DD2476>✦ Blacksmith Forge ✦</gradient>"
size: 54
permission: "royitems.station.blacksmith"

# Custom commands registered at runtime with tab-completion
commands:
  - blacksmith
  - forge

# Link to NPCs (right-click to open station)
fancynpcs:
  - 10
citizen:
  - 5

# Decorative background icons with fast slot ranges
backgrounds:
  border:
    material: "GRAY_STAINED_GLASS_PANE"
    name: "&7"
    slots:
      - 0-8
      - 45-53

# Unlimited multi-input and multi-output recipes
recipes:
  legendary_blade:
    input:
      - "royitems[type=MATERIAL,id=ancient_core,amount=2]"
      - "vanilla[type=NETHERITE_INGOT,amount=3]"
      - "money=10000"
      - "exp=500"
      - "point=150"
      - "excellenteconomy:crystals=25"
    output:
      - "royitems[type=SWORD,id=excalibur,amount=1]"
    icon:
      - "royitems[type=SWORD,id=excalibur,amount=1]"
```

* 🌐 **Dynamic Client-Language Translation:** Vanilla ingredient names in lore automatically match the player's Minecraft client language setting.
* 📦 **Identical Item Output Stacking:** Items produced by recipes stack seamlessly without NBT divergence.
* 💳 **Multi-Currency Support:** Seamlessly mix RoyItems, Vanilla materials, Vault balance, EXP points, PlayerPoints, and ExcellentEconomy currencies in recipe costs.
* 🤖 **FancyNpcs & Citizens Integration:** Bind stations to NPC clicks with one configuration line.

---

## 🎯 Packet-Based Training Dummy & DPS Meter (New in v2026.1.4)

Test player weapon damage, critical strikes, and output DPS in real time using client-side packet entities powered by **PacketEvents** or **ProtocolLib**:

```bash
/ri dummy spawn dps_dummy ZOMBIE  # Spawns dummy centered at looked block
/ri dummy despawn dps_dummy       # Despawns specific dummy
/ri dummy despawn all             # Despawns all dummies
```

* 🚀 **Zero Server Tick Overhead:** Runs 100% via client-side packets. Never affects main thread TPS.
* 🛡️ **Total Immunity & Region Bypass:** Immune to `/kill @e` and clear-lag sweeps. Bypasses WorldGuard flags for smooth spawn & attack registration anywhere.
* ⚔️ **True Final Output Damage:** Accurately factors in weapon attack damage, attack cooldown recovery, enchantments (Sharpness, Smite on undead, Bane of Arthropods, Impaling), Strength/Weakness potions, vanilla jump crits, and Mace smash attacks.
* 🏹 **Full Projectile Support:** Real-time in-flight collision tracking for Bows, Crossbows, and thrown Tridents, calculating velocity, Power enchantments, and arrow stats.
* 🚫 **Right-Click Attacks Blocked:** Only legitimate left-click melee strikes and projectile hits register; right-click interactions are ignored.
* 📊 **Text Display Hologram:** Displays live **DPS**, **Total Damage**, **Last Hit**, and **Hit Count** with customizable MiniMessage and RGB colors.
* ⚙️ **Individual Configuration:** Configure lines, colors, and billboard modes in `plugins/RoyItems/dummy/<dummy_id>.yml`.
* 🔄 **Reload Safe:** Safe cleanup on server reloads and PlugmanX reloads—no ghost entities or text displays.

---

## ⚔️ 16 Custom RPG Stats & Calculation Engine

RoyItems calculates player attributes using an industry-standard formula combining base values, flat bonuses, and percentage scalings:

$$\text{Total Stat} = (\text{Base} + \sum \text{Flat Modifiers}) \times (1 + \sum \text{Percentage Modifiers})$$

### Available Statistics:
* ⚔️ **`attack-damage`:** Base physical melee damage.
* 🏹 **`arrow-damage`:** Custom ranged projectile damage.
* ⚡ **`attack-speed`:** Weapon swing and recovery speed multiplier.
* 🎯 **`crit-rate`:** Percentage chance to land a critical strike (e.g. `20%`).
* 💥 **`crit-damage`:** Bonus damage multiplier applied on critical strikes (e.g. `50%`).
* 🛡️ **`defense`:** Core defense points processed through the active defense calculation engine.
* ⚔️ **`pvp-damage`:** Bonus damage dealt exclusively against other players.
* 🗡️ **`pve-damage`:** Bonus damage dealt exclusively against monsters and dungeon bosses.
* 🛡️ **`pvp-defense`:** Resistance reducing incoming damage from player attacks.
* 🛡️ **`pve-defense`:** Resistance reducing incoming damage from mob/boss attacks.
* 🔰 **`damage-reduction`:** Direct percentage damage mitigation cap (e.g. `10%`).
* ❤️ **`max-health`:** Increases the player's maximum health pool.
* 👟 **`movement-speed`:** Increases player sprint and movement speed.
* 🏋️ **`knockback-resistance`:** Resistance against being tossed or pushed by heavy blows.
* 🛡️ **Vanilla Attributes:** Direct access to Minecraft's modern attributes (`generic.armor`, `generic.armor_toughness`, etc.).

---

## 🛡️ Next-Gen Defense System

Configure how damage mitigation works in `config.yml`:

```yaml
combat:
  defense:
    # Choose: MMORPG, SIMPLE, or CUSTOM_LIMIT
    mode: "MMORPG"
    
    # Mode 1: SIMPLE (Flat Damage = Damage - Effective Defense)
    simple:
      floor-damage: 0.0

    # Mode 2: MMORPG (Non-Linear Diminishing Returns)
    # Formula: Final Damage = Damage * (scale / (scale + Effective Defense))
    mmorpg:
      scale: 100.0
      damage-factor: 1.0
      min-damage-floor: 1.0

    # Mode 3: CUSTOM_LIMIT (Strict Clamped Damage Boundaries)
    custom-limit:
      min-damage:
        enabled: true
        value: 2.0
      max-damage:
        enabled: false
        value: 100.0
```

> [!TIP]
> **Why MMORPG curve mode?**  
> Simple subtraction often results in invincible players against low-level monsters, or useless armor against late-game bosses. The MMORPG formula guarantees diminishing returns so defense is always rewarding without breaking the game.

---

## 🌈 Tier Glowing Outline System

When high-tier equipment drops on the ground, RoyItems renders a client-synchronized glowing outline with custom colors:

```yaml
# item-tier.yml
epic:
  name: "Epic"
  color: "&5"
  weight: 10
  drop-color: "DARK_PURPLE" # Bukkit NamedTextColor or Hex color code (#A335EE)
```

Items dropped in the world automatically glow with their tier's distinct color, visible smoothly to the player and surrounding teammates.

---

## 🖥️ In-Game GUI Editor (`/ri editor`)

Manage your entire item catalog without opening text editors or reloading server files:
* **Category Browser:** View items sorted by type with live item counters.
* **Drag-and-Drop Material Setting:** Drag any item from your player inventory directly into the Material slot to clone its material.
* **Symmetrical 3-Page Navigation:**
  * **Page 1:** Material, Name, Lore, Model, Template, Unbreakable, Glow, Tier, Set, and Item Protections (`disable-on-use`, `disable-on-drop`, `disable-on-crafting`, `disable-on-enchantment`).
  * **Page 2:** All 16 Combat and RPG Statistics.
  * **Page 3:** Requirements (Level, Permissions, MMOCore, AuraSkills, Worlds, Biomes), Enchantments, Potion Effects, Sockets, Cooldowns, Expiration, Armor Trims, and Leather Dyes.
* **Concurrent Lock Guard:** Prevents multiple staff members from editing and overwriting the same item simultaneously.

---

## 📋 Commands & Permissions

| Command | Permission | Description |
| :--- | :--- | :--- |
| `/ri help` | `royitems.command.help` | Displays command assistance menu |
| `/ri reload` | `royitems.command.reload` | Hot-reloads all configs, stations, dummies, and triggers LiveUpdate |
| `/ri editor` | `royitems.command.editor` | Opens the paginated Ingame GUI Editor |
| `/ri checkupdate` | `royitems.command.checkupdate` | Checks for new releases and updates on Modrinth |
| `/ri give <player\|all> <type> <id> [amount]` | `royitems.command.give` | Gives custom items directly to inventory (supports ranges: `1-5`) |
| `/ri take <player\|all> <type> <id> [amount]` | `royitems.command.take` | Removes specified amount of custom items from player |
| `/ri drop <player\|all> <type> <id> [amount]` | `royitems.command.drop` | Drops custom items at target player location |
| `/ri drop <world> <x> <y> <z> <type> <id> [amount]` | `royitems.command.drop` | Drops custom items at exact world coordinates |
| `/ri station open <station_id> [player]` | `royitems.command.station.open` | Opens a crafting station GUI directly |
| `/ri station list` or `/ri stations` | `royitems.command.station.list` | Opens the global Crafting Stations list GUI |
| `/ri dummy spawn <dummy_id> [mob_type]` | `royitems.command.dummy` | Spawns a packet-based training dummy centered at target block |
| `/ri dummy despawn <dummy_id\|all>` | `royitems.command.dummy` | Despawns a training dummy or all active dummies |

---

## 🧩 PlaceholderAPI Placeholders

RoyItems integrates seamlessly with [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/):

| Placeholder | Description |
| :--- | :--- |
| `%royitems_stat_<stat>%` | Returns player's calculated stat (e.g. `%royitems_stat_attack_damage%`) |
| `%royitems_hand_type%` | Item type of held item (`SWORD`, `ARMOR`, etc.) |
| `%royitems_hand_id%` | Unique ID of held item |
| `%royitems_hand_name%` | Formatted display name of held item |
| `%royitems_hand_tier%` | Tier identifier of held item |
| `%royitems_hand_version%` | LiveUpdate internal version number of held item |

---

## 📊 Anonymous Metrics & Statistics (bStats)

RoyItems uses [bStats](https://bstats.org/plugin/bukkit/RoyItems/34832) to collect anonymous telemetry (such as Minecraft server versions, Java runtimes, and online players) to help prioritize optimizations and guide future updates. Server owners can opt out at any time in `plugins/bStats/config.yml`.

| Metric | Live Status |
| :--- | :--- |
| **bStats Page** | [bstats.org/plugin/bukkit/RoyItems/34832](https://bstats.org/plugin/bukkit/RoyItems/34832) |
| **Plugin ID** | `34832` |
| **Active Servers** | [![bStats Servers](https://img.shields.io/bstats/servers/34832?style=flat-square&color=007ec6)](https://bstats.org/plugin/bukkit/RoyItems/34832) |
| **Online Players** | [![bStats Players](https://img.shields.io/bstats/players/34832?style=flat-square&color=2ea44f)](https://bstats.org/plugin/bukkit/RoyItems/34832) |

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/RoyItems/34832" target="_blank">
    <img src="https://bstats.org/signatures/bukkit/RoyItems.svg" alt="bStats Server Statistics" />
  </a>
</p>

---

## 🔗 Compatible Plugins & Integrations

RoyItems is tested and fully compatible with the modern Minecraft server ecosystem:

* 🚀 **Server Software:** [PaperMC](https://papermc.io) • [Purpur](https://purpurmc.org) • [Folia](https://papermc.io/software/folia) (Multi-Threaded)
* ⚡ **Packet Infrastructure:** [PacketEvents](https://modrinth.com/plugin/packetevents) • [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
* 💰 **Economy & Skills:** [Vault](https://www.spigotmc.org/resources/vault.34315/) • [PlayerPoints](https://www.spigotmc.org/resources/playerpoints.80745/) • [ExcellentEconomy](https://modrinth.com/plugin/excellenteconomy) • [ExtraStorage](https://github.com/HSGamer/ExtraStorage) • [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) • [MMOCore](https://www.spigotmc.org/resources/mmocore.70506/) • [AuraSkills](https://www.spigotmc.org/resources/auraskills.115867/)
* 🤖 **NPCs & Interactive Systems:** [FancyNpcs](https://modrinth.com/plugin/fancynpcs) • [Citizens](https://www.spigotmc.org/resources/citizens.13811/)
* 🛡️ **Combat & Regions:** [MythicMobs](https://mythiccraft.io/index.php?resources/mythicmobs.1/) • [WorldGuard](https://enginehub.org/worldguard)
* 🎨 **Custom Textures & Cosmetics:** [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/) • [Oraxen](https://www.spigotmc.org/resources/oraxen.72448/) • [Nexo](https://modrinth.com/plugin/nexo) • [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/)

---

## 💬 Community & Support

* 🐛 **Issue Tracker:** Found a bug or need a feature? Open an issue on GitHub.
* 📦 **Modrinth Page:** [https://modrinth.com/plugin/royitems](https://modrinth.com/plugin/royitems)
