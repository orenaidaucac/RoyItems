# ⚔ RoyItems — Advanced MMORPG Custom Items Engine

![Version](https://img.shields.io/badge/version-2026.1-blue.svg)
![Minecraft](https://img.shields.io/badge/minecraft-26.2%2B%20%2F%201.21.4%2B-brightgreen.svg)
![Platforms](https://img.shields.io/badge/platforms-Paper%20%7C%20Spigot%20%7C%20Folia-orange.svg)
![Java](https://img.shields.io/badge/java-21%2B-red.svg)
![Build](https://img.shields.io/badge/build-Maven-informational.svg)
![Discord](https://img.shields.io/badge/community-Discord-7289DA.svg)

> **RoyItems** là một nền tảng MMORPG Custom Items Engine chuyên nghiệp, hiện đại, hiệu năng cao và sở hữu bản sắc riêng dành cho Minecraft máy chủ 26.2+ (Paper, Spigot và Folia đa luồng). Plugin cho phép quản trị viên định nghĩa không giới hạn vật phẩm tùy chỉnh, chỉ số mở rộng, socket khảm ngọc, bộ trang bị (Item Sets), phẩm chất (Tiers), hiệu ứng độc quyền và quản lý 100% qua file YAML hoặc Ingame GUI Editor.

---

## 📑 Mục Lục
1. [Giới thiệu & Triết lý Thiết kế](#1-giới-thiệu--triết-lý-thiết-kế)
2. [Điểm nổi bật (Feature Highlights)](#2-điểm-nổi-bật-feature-highlights)
3. [Yêu cầu Hệ thống](#3-yêu-cầu-hệ-thống)
4. [Cài đặt & Khởi động lần đầu](#4-cài-đặt--khởi-động-lần-đầu)
5. [Cấu trúc Thư mục & Tệp tin](#5-cấu-trúc-thư-mục--tệp-tin)
6. [Hệ thống 14 Item Types](#6-hệ-thống-14-item-types)
7. [Material Providers](#7-material-providers)
8. [Engine Chỉ số (Custom Stats & Vanilla Attributes)](#8-engine-chỉ-số-custom-stats--vanilla-attributes)
9. [Dynamic Lore Engine (#stats# & #bar#)](#9-dynamic-lore-engine-stats--bar)
10. [Hệ thống Phẩm chất (Tier) & Bộ Trang Bị (Set)](#10-hệ-thống-phẩm-chất-tier--bộ-trang-bị-set)
11. [Cơ chế Khảm Ngọc (Gem Socket & Gem Stone)](#11-cơ-chế-khảm-ngọc-gem-socket--gem-stone)
12. [Hạn Sử Dụng (Item Expiration) & LiveUpdate](#12-hạn-sử-dụng-item-expiration--liveupdate)
13. [Ingame GUI Editor (`/ri editor`)](#13-ingame-gui-editor-ri-editor)
14. [Lệnh & Quyền hạn (Commands & Permissions)](#14-lệnh--quyền-hạn-commands--permissions)
15. [Tích hợp Bên thứ ba (Integrations)](#15-tích-hợp-bên-thứ-ba-integrations)
16. [Tài liệu Chi tiết Cấu hình Vật phẩm](#16-tài-liệu-chi-tiết-cấu-hình-vật-phẩm)
17. [Hướng dẫn Biên dịch từ Mã nguồn](#17-hướng-dẫn-biên-dịch-từ-mã-nguồn)
18. [Hỗ trợ & Cộng đồng](#18-hỗ-trợ--cộng-đồng)

---

## 1. Giới thiệu & Triết lý Thiết kế
RoyItems được xây dựng nhằm mang lại giải pháp toàn diện cho các máy chủ RPG/MMORPG:
- **Kiến trúc Tách biệt:** Tách rời `ItemDefinition` (cấu hình mẫu) và `ItemInstance` (vật phẩm thực tế trong tay người chơi).
- **Định danh Tuyệt đối qua PDC:** Sử dụng `PersistentDataContainer` để nhận diện `royitems:item_type`, `royitems:item_id`, `royitems:item_version`, `royitems:item_expire`, `royitems:item_sockets`. Không bao giờ dựa vào Tên hoặc Lore để nhận diện vật phẩm.
- **Tương thích Folia Native:** Mọi tác vụ truy cập Entity, Player, Inventory hay Location đều được gửi qua Scheduler tương ứng (`entity.getScheduler()`, `Bukkit.getRegionScheduler()`).

---

## 2. Điểm nổi bật (Feature Highlights)
- 💎 **14 Item Types MMORPG Mặc định:** Hỗ trợ SWORD, ARMOR, BOW, CROSSBOW, STAFF, TOOL, GEM_STONE, CONSUMABLE, MATERIAL, OFF_HAND, WING, VOUCHER, PET, TALISMAN.
- ⚔ **16 Custom Stats + Full Vanilla Attributes:** Tính toán theo công thức:
  $$\text{Total} = (\text{Base} + \sum \text{Flat}) \times (1 + \sum \text{Percent})$$
- 📜 **Dynamic Lore Engine Độc quyền:** Tự động tạo lore với `#stats#`, lọc phân cách `#bar#` thông minh không để lại dòng trống hoặc separator dư thừa.
- 💎 **Gem Socket System:** Kéo thả trực tiếp ngọc vào trang bị, kiểm tra tương thích socket mã màu/loại (`1`, `A`, `ABC`), tháo ngọc và bảo toàn dữ liệu.
- ⏳ **Real-Time Expiration:** Đếm ngược thời gian thực (Epoch Timestamp), tự động hủy khi hết hạn trong kho đồ hoặc rương chứa.
- 🔄 **LiveUpdate Engine:** Tự động cập nhật chỉ số, lore, model của toàn bộ item cũ trong túi đồ và Ender Chest người chơi ngay khi Admin `/ri reload` cấu hình.
- 🖥 **Chuyên nghiệp với Ingame GUI Editor:** Quản trị toàn bộ thuộc tính, tìm kiếm, phân trang, khóa chỉnh sửa đa admin chống xung đột.

---

## 3. Yêu cầu Hệ thống
- **Java:** `Java 21` trở lên (Khuyến nghị Java 21 hoặc Java 25 LTS).
- **Minecraft Server:** `Paper 1.21.x+`, `Spigot 1.21.x+`, hoặc `Folia 1.21.x+` (Chuẩn Minecraft 26.2+).
- **RAM:** Tối thiểu 4GB RAM cho máy chủ Paper/Folia.

---

## 4. Cài đặt & Khởi động lần đầu
1. Tải bản build mới nhất `RoyItems-2026.1.jar` từ thư mục `target/` hoặc Releases.
2. Đặt `RoyItems-2026.1.jar` vào thư mục `plugins/` của máy chủ.
3. Khởi động lại máy chủ (hoặc dùng `/ri reload`).
4. Plugin sẽ tự động tạo cấu trúc thư mục mặc định với các ví dụ chuẩn.

---

## 5. Cấu trúc Thư mục & Tệp tin
```
plugins/RoyItems/
 ├── config.yml              # Cấu hình chung, combat, performance
 ├── lang/
 │    └── message.yml        # Toàn bộ thông báo trong game
 ├── item/                   # Tệp cấu hình phân loại theo TYPE
 │    ├── SWORD.yml
 │    ├── ARMOR.yml
 │    ├── BOW.yml
 │    ├── STAFF.yml
 │    ├── GEM_STONE.yml
 │    ├── WING.yml
 │    ├── TALISMAN.yml
 │    ├── CONSUMABLE.yml
 │    └── VOUCHER.yml
 ├── lore-format/            # Định dạng lore động
 │    ├── default.yml
 │    ├── weapon.yml
 │    └── armor.yml
 ├── item-tier.yml           # Định nghĩa phẩm chất (Common, Rare, Legendary...)
 ├── item-set.yml            # Định nghĩa bộ trang bị và mốc kích hoạt bonus
 └── gui/
      └── editor-gui.yml     # Cấu hình giao diện GUI Editor
```

---

## 6. Hệ thống 14 Item Types

| TYPE | Mục đích sử dụng | Vị trí kích hoạt / Slot |
|---|---|---|
| `SWORD` | Vũ khí cận chiến, kích hoạt Attack Damage, Crit, PvP/PvE | `MAIN_HAND` |
| `ARMOR` | Mũ, Giáp, Quần, Giày (hỗ trợ Armor Trim & Dye) | `HEAD`, `CHEST`, `LEGS`, `FEET` |
| `BOW` | Cung tên bắn xa, áp dụng Arrow Damage | `MAIN_HAND` |
| `CROSSBOW` | Nỏ bắn xa | `MAIN_HAND` |
| `STAFF` | Gậy phép, Right-Click gọi skill MythicMobs hoặc phép thuật | `MAIN_HAND` |
| `TOOL` | Cuốc, xẻng, rìu khai khoáng | `MAIN_HAND` |
| `GEM_STONE` | Ngọc khảm socket, kéo thả trực tiếp lên trang bị | Inventory drag |
| `CONSUMABLE` | Bình thuốc, thức ăn kích hoạt Potion Effects và cooldown | Right-Click |
| `MATERIAL` | Nguyên liệu rèn đúc, nâng cấp | Kho đồ |
| `OFF_HAND` | Khiên, trang bị tay phụ | `OFF_HAND` |
| `WING` | Cánh bay (Elytra model) tăng tốc độ lượn và phòng ngự | `CHEST` |
| `VOUCHER` | Phiếu quà tặng, thẻ đặc quyền thực thi lệnh | Right-Click |
| `PET` | Thú cưng đồng hành | Cầm tay / Kho đồ |
| `TALISMAN` | Bùa chú hỗ trợ tăng chỉ số bị động | Nằm trong kho đồ (`INV`) |

---

## 7. Material Providers
RoyItems hỗ trợ linh hoạt các nguồn vật liệu:
- **Vanilla:** `DIAMOND_SWORD`, `NETHERITE_CHESTPLATE`, `BOW`...
- **HeadDatabase:** `hdb-<id>` (Tự động lấy skin đầu từ plugin HeadDatabase).
- **Base64 Head:** `basehead-<base64>` (Skin đầu giải mã trực tiếp từ chuỗi Base64).
- **Nexo:** `nexo:<id>` (Bảo tồn model và dữ liệu Nexo).
- **ItemsAdder:** `itemsadder:<id>` hoặc `ia:<id>`.
- **Oraxen:** `oraxen:<id>`.

---

## 8. Engine Chỉ số (Custom Stats & Vanilla Attributes)

### Danh sách 16 Custom Stats:
- `attack-damage`: Sát thương vật lý cận chiến.
- `arrow-damage`: Sát thương bắn cung tên.
- `max-health`: Máu tối đa của nhân vật.
- `attack-speed`: Tốc độ ra đòn.
- `crit-rate`: Tỷ lệ bộc kích chí mạng (%).
- `crit-damage`: Sát thương chí mạng thêm (%).
- `defense`: Chỉ số phòng thủ (theo đường cong `100 / (100 + defense)`).
- `pvp-damage`: Sát thương cộng thêm khi đánh người chơi.
- `pve-damage`: Sát thương cộng thêm khi đánh quái vật.
- `pvp-defense`: Kháng sát thương từ người chơi.
- `pve-defense`: Kháng sát thương từ quái vật.
- `damage-reduction`: Giảm trực tiếp % sát thương nhận vào (hỗ trợ migration cho `damage-redution`).
- `armor`: Giáp Vanilla.
- `armor-toughness`: Độ cứng giáp Vanilla.
- `knockback-resistance`: Kháng giật lùi.
- `movement-speed`: Tốc độ di chuyển.

---

## 9. Dynamic Lore Engine (`#stats#` & `#bar#`)
Cấu hình tại `lore-format/<id>.yml`:
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
  - "&8#tier#"
```
- `#stats#`: Tự động điền các dòng chỉ số thực tế của vật phẩm.
- `#bar#`: Bộ phân cách thông minh. Tự động lược bỏ nếu khối phía trên hoặc phía dưới rỗng, không bao giờ xuất hiện 2 thanh phân cách liên tiếp.

---

## 10. Hệ thống Phẩm chất (Tier) & Bộ Trang Bị (Set)
- **Item Tier (`item-tier.yml`):** Định nghĩa phẩm chất (Common, Rare, Epic, Legendary, Mythic) với màu sắc và tên hiển thị riêng.
- **Item Set (`item-set.yml`):** Cung cấp các mốc bonus khi người chơi trang bị đủ số món trong cùng 1 bộ (ví dụ: mốc 2 món, mốc 4 món).

---

## 11. Cơ chế Khảm Ngọc (Gem Socket & Gem Stone)
- Định nghĩa socket trên trang bị:
  ```yaml
  gem-socket:
    - "1"     # Universal Socket (Nhận mọi loại ngọc)
    - "A"     # Chỉ nhận ngọc có gem-id: "A"
    - "ABC"   # Nhận ngọc có gem-id "A", "B", hoặc "C"
  ```
- **Cách khảm:** Người chơi cầm viên ngọc `GEM_STONE` trên con trỏ chuột, nhấp chuột vào trang bị có socket tương thích. Ngọc sẽ được khảm ngay lập tức vào ổ trống đầu tiên, cập nhật Lore và cộng chỉ số.

---

## 12. Hạn Sử Dụng (Item Expiration) & LiveUpdate
- **Expiration (`expired: 3 days` / `1 month`):** Lưu trữ theo Epoch Timestamp trong PDC. Khi quá hạn, vật phẩm tự động tan biến thành bụi khói khi người chơi mở kho đồ hoặc nhặt lên. Người có quyền OP hoặc `royitems.item.expired.bypass` sẽ được miễn trừ.
- **LiveUpdate:** Khi quản trị viên cập nhật file YAML và gõ `/ri reload`:
  - Quét an toàn túi đồ và Ender Chest của toàn bộ người chơi đang online.
  - So khớp phiên bản `item_version`, tự động xây dựng lại ItemStack theo cấu hình mới nhất.
  - Giữ nguyên vẹn toàn bộ ngọc đã khảm và thời gian hết hạn của từng item!

---

## 13. Ingame GUI Editor (`/ri editor`)
Truy cập qua lệnh:
```bash
/ri editor
```
- **Dashboard Type Selector:** Chọn 1 trong 14 Item Types với số lượng vật phẩm hiển thị trực quan.
- **Item Browser:** Phân trang, hiển thị mô hình thực tế, xem trước, xóa hoặc nhận vật phẩm ngay lập tức.
- **Item Detail Editor:**
  - Chỉnh sửa Material, Custom Model Data / Item Model.
  - Điều chỉnh Stats thô và % trực quan.
  - Bật/tắt Unbreakable, Glow, Hide Enchants, Hide Attributes.
  - Thêm bớt Gem Sockets (`1`, `A`, `ABC`).
  - Tùy chỉnh Armor Trim (Netherite, Silence, Vex...) và màu nhuộm áo da.
  - Khóa chỉnh sửa đa Admin (`EditorSessionLock`) chống ghi đè tệp.

---

## 14. Lệnh & Quyền hạn (Commands & Permissions)

| Lệnh | Mô tả | Quyền hạn |
|---|---|---|
| `/ri help` | Hiển thị menu hướng dẫn | `royitems.command.help` |
| `/ri reload` | Reload cấu hình và kích hoạt LiveUpdate | `royitems.command.reload` |
| `/ri editor` | Mở Ingame GUI Editor | `royitems.command.editor` |
| `/ri give <player\|all> <type> <id> [amount]` | Phát vật phẩm (hỗ trợ khoảng ngẫu nhiên e.g. `1-3`) | `royitems.command.give` |
| `/ri take <player\|all> <type> <id> [amount]` | Thu hồi vật phẩm chính xác theo ID | `royitems.command.take` |
| `/ri drop <player\|all> <type> <id> [amount]` | Rơi vật phẩm tại vị trí người chơi | `royitems.command.drop` |
| `/ri drop <world> <x> <y> <z> <type> <id> [amount]` | Rơi vật phẩm tại tọa độ thế giới | `royitems.command.drop` |

---

## 15. Tích hợp Bên thứ ba (Integrations)

| Plugin | Loại | Trạng thái Tương thích & Tài liệu |
|---|---|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | Optional | Hỗ trợ kiểm tra và giao dịch kinh tế. |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Optional | Cung cấp placeholder `%royitems_stat_<stat>%`. |
| [MythicMobs](https://mythiccraft.io/) | Optional | Kích hoạt kỹ năng MythicMobs từ gậy phép và vũ khí. |
| [MMOCore](https://www.spigotmc.org/resources/mmocore.70575/) | Optional | Kiểm tra Class, Level và tiêu hao Mana. |
| [AuraSkills](https://www.spigotmc.org/resources/auraskills.81069/) | Optional | Kiểm tra Skill Level và Mana. |
| [WorldGuard](https://enginehub.org/worldguard/) | Optional | Kiểm tra cờ khu vực (PvP, item-use). |
| [HeadDatabase](https://www.spigotmc.org/resources/head-database.14280/) | Optional | Lấy skin đầu `hdb-<id>`. |
| [Nexo](https://polymart.org/resource/nexo.6212) | Optional | Giữ nguyên model và data component. |
| [ItemsAdder](https://itemsadder.devs.beer/) | Optional | Tương thích model và custom items. |
| [Oraxen](https://oraxen.com/) | Optional | Tương thích model và textures. |
| [TriumphGUI](https://github.com/TriumphTeam/triumph-gui) | **Shaded** | Thư viện GUI hiện đại, không NMS, an toàn tuyệt đối trên Paper 26.2+ & Folia. |

---

## 16. Tài liệu Chi tiết Cấu hình Vật phẩm

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

---

## 17. Hướng dẫn Biên dịch từ Mã nguồn

Dự án sử dụng **Maven** và yêu cầu **Java 21 trở lên**:

```bash
# Clone repository
git clone https://github.com/orenaidaucac/RoyItems.git

# Biên dịch và đóng gói
mvn clean package
```
Tệp JAR hoàn thiện sẽ xuất hiện tại `target/RoyItems-2026.1.jar`.

---

## 18. Hỗ trợ & Cộng đồng

- 💬 **Discord Hỗ trợ Chính thức:** [https://discord.gg/zNpkXGFQRS](https://discord.gg/zNpkXGFQRS)
- 🐛 **Báo cáo lỗi & Đóng góp:** Tạo Issue trên GitHub Repository.

---
*Phát triển bởi orenaidaucac với tiêu chuẩn chất lượng cao nhất dành cho cộng đồng Minecraft MMORPG.*
