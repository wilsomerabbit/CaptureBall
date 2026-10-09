# CaptureBall

[English](#english) | [繁體中文](#繁體中文)

---

## English

**CaptureBall** is a Paper plugin that lets players capture a mob into a ball, carry it around, and release it somewhere else. Balls are created by admins only, and the plugin works with **WorldGuard** and **Residence** so players cannot use it to bypass land protection.

### What is it for?

- Move animals, villagers, and other mobs to a new base or farm without leads or boats.
- Let trusted players relocate mobs while keeping them out of protected areas.
- Reward players with capture balls from events, quests, or a custom shop (balls are given by command).

### How it works

1. An admin gives a player an **empty capture ball** with `/captureball give`.
2. The player **right-clicks a mob** while holding the empty ball. The mob is removed and the ball becomes a **filled ball**.
3. The player **right-clicks a block** while holding the filled ball. The mob appears next to that block and the ball is consumed.

Both capture and release are **single use**.

### Features

- **Full mob data is preserved**: name, health, equipment, age, and other data are stored inside the item.
- **Not a spawn egg**: the mob data is stored in the item's persistent data, so a filled ball cannot be placed into a spawner.
- **Admin-only creation**: no crafting recipe and no shop. Balls cannot be used as crafting ingredients.
- **Filled balls never stack.**
- **Safe interactions**: item frames, armor stands, chests, doors, and other interactable blocks keep their normal behavior (sneak to release onto an interactable block).
- **Capture chance**: optionally make captures fail with a configurable probability. A failed attempt consumes the ball.
- **Mob blacklist**: block specific entity types from being captured.
- **Disabled worlds**: turn the plugin off in chosen worlds.
- **Pet protection**: tamed pets can only be captured by their owner (unless the player has the bypass permission).
- **Mounted mobs are ignored**: mobs that are riding or carrying passengers cannot be captured.
- **Data size limit**: prevents oversized mob data from being stored.
- **Fully customizable** item names, lore, item model, and messages using [MiniMessage](https://docs.advntr.dev/minimessage/format.html).
- **Fails closed**: if a protection check errors out, the action is denied.

### Land protection

These rules apply to **both capture and release**:

| Plugin | Rule |
| --- | --- |
| **WorldGuard** | Inside **any** region (for example spawn), only **OPs** can capture or release. |
| **Residence** | Inside a residence, the player must be the **owner** or have the **`build`** flag. |

- When the player is denied, the ball is **not consumed**.
- WorldGuard and Residence are **optional**. If a plugin is not installed, its rule is skipped.
- Players with `captureball.bypass` skip the Residence check. Only OPs skip the WorldGuard check.
- Each rule can be turned off in `config.yml`.

### Requirements

- **Paper** (or a Paper fork such as Purpur). Spigot is not supported.
- Minecraft **1.21.4 or newer** (uses entity snapshots and the item model API).
- Java **21**.
- Optional: WorldGuard, Residence.

### Installation

1. Download the latest `.jar` from [Releases](../../releases) or Modrinth.
2. Put it in your server's `plugins/` folder.
3. Restart the server.
4. Edit `plugins/CaptureBall/config.yml` if you want, then run `/captureball reload`.

### Commands

| Command | Description |
| --- | --- |
| `/captureball give <player> [amount]` | Give empty capture balls (amount 1–64). |
| `/captureball reload` | Reload `config.yml`. |

### Permissions

| Permission | Description |
| --- | --- |
| `captureball.use` | Allows using capture balls (capture and release). |
| `captureball.bypass` | Skips the Residence check and the pet-owner check. |

Remember to grant `captureball.use` to the players or groups that should be able to use the balls. Example with LuckPerms:

```
/lp group default permission set captureball.use true
```

### Configuration

Main options in `config.yml`:

| Key | Description |
| --- | --- |
| `item.material` | Base item of the ball (default `FIREWORK_STAR`). Pick an item with no right-click use. |
| `empty.name` / `empty.lore` / `empty.item-model` | Look of the empty ball. |
| `filled.name` / `filled.lore` / `filled.item-model` | Look of the filled ball. `<mob>` is replaced by the mob's name. |
| `capture-chance` | Success chance from `0.0` to `1.0` (default `1.0`). |
| `max-data-length` | Maximum stored data length (default `30000`). |
| `blacklist` | List of entity types that cannot be captured, e.g. `WITHER`. |
| `disabled-worlds` | Worlds where the plugin is disabled. |
| `protection.worldguard-block-non-op` | Block non-OPs inside WorldGuard regions (default `true`). |
| `protection.residence-require-build` | Require Residence `build` inside residences (default `true`). |
| `messages.*` | All player-facing messages. |

### Building from source

```
mvn clean package
```

The jar is created in `target/`.

### License

Released under the [GNU GPL v3.0](LICENSE). You are free to use, modify, and redistribute this plugin, as long as you keep the original copyright notice and license. Any modified version you distribute must also be released as open source under the GPL v3.0.

---

## 繁體中文

**CaptureBall（捕捉球）** 是一個 Paper 插件，讓玩家把生物收進球裡帶著走，再到別的地方放出來。捕捉球只能由管理員發放，並且支援 **WorldGuard** 和 **Residence**，玩家無法利用它繞過領地與保護區。

### 有什麼用？

- 不用拴繩或船，就能把動物、村民等生物搬到新的基地或農場。
- 讓信任的玩家搬移生物，同時避免他們在受保護的區域使用。
- 把捕捉球當作活動、任務或自訂商店的獎勵（球由指令發放）。

### 使用方式

1. 管理員用 `/captureball give` 給玩家**空的捕捉球**。
2. 玩家手持空球，對生物按**右鍵**，生物會被收進去，空球變成**裝著生物的捕捉球**。
3. 玩家手持滿球，對方塊按**右鍵**，生物就會出現在該方塊旁邊，球同時被消耗。

捕捉與釋放都是**單次使用**。

### 特性

- **完整保留生物資料**：名字、血量、裝備、年齡等資料都存在物品裡。
- **不是生怪蛋**：資料存放在物品的持久資料中，滿球無法放進刷怪籠。
- **只有管理員能發放**：沒有合成配方，也沒有商店，球不能當作合成材料。
- **滿球不能堆疊。**
- **不干擾其他互動**：展示框、盔甲架、箱子、門等照常運作（要釋放在可互動方塊上時請蹲下）。
- **捕捉成功率**：可以設定捕捉失敗的機率，失敗會消耗球。
- **生物黑名單**：指定不可捕捉的生物種類。
- **停用世界**：在指定世界關閉插件功能。
- **寵物保護**：已馴服的寵物只有主人能捕捉（擁有 bypass 權限者除外）。
- **騎乘中的生物無法捕捉**：正在騎乘或載著乘客的生物不能被收服。
- **資料大小限制**：避免儲存過大的生物資料。
- **高度可自訂**：物品名稱、lore、物品模型和所有訊息都能用 [MiniMessage](https://docs.advntr.dev/minimessage/format.html) 調整顏色與格式。
- **保守的失敗策略**：保護檢查出錯時，一律拒絕該次操作。

### 領地與保護區規則

以下規則**同時適用於捕捉與釋放**：

| 插件 | 規則 |
| --- | --- |
| **WorldGuard** | 在**任何**區域（例如 spawn）內，只有 **OP** 能捕捉或釋放。 |
| **Residence** | 在領地內，玩家必須是**領地主人**，或擁有 **`build`** 權限。 |

- 被拒絕時，球**不會被消耗**。
- WorldGuard 和 Residence 都是**選用**的，沒安裝就會略過對應規則。
- 擁有 `captureball.bypass` 的玩家可以略過 Residence 檢查；只有 OP 能略過 WorldGuard 檢查。
- 每條規則都可以在 `config.yml` 裡關閉。

### 需求

- **Paper**（或 Purpur 等 Paper 分支），不支援 Spigot。
- Minecraft **1.21.4 以上**（使用了實體快照與物品模型 API）。
- Java **21**。
- 選用：WorldGuard、Residence。

### 安裝

1. 從 [Releases](../../releases) 或 Modrinth 下載最新的 `.jar`。
2. 放進伺服器的 `plugins/` 資料夾。
3. 重啟伺服器。
4. 需要的話編輯 `plugins/CaptureBall/config.yml`，再執行 `/captureball reload`。

### 指令

| 指令 | 說明 |
| --- | --- |
| `/captureball give <玩家> [數量]` | 給予空的捕捉球（數量 1–64）。 |
| `/captureball reload` | 重新載入 `config.yml`。 |

### 權限

| 權限 | 說明 |
| --- | --- |
| `captureball.use` | 允許使用捕捉球（捕捉與釋放）。 |
| `captureball.bypass` | 略過 Residence 檢查與寵物主人檢查。 |

請記得把 `captureball.use` 給需要使用的玩家或群組。以 LuckPerms 為例：

```
/lp group default permission set captureball.use true
```

### 設定檔

`config.yml` 的主要選項：

| 設定 | 說明 |
| --- | --- |
| `item.material` | 捕捉球的基底物品（預設 `FIREWORK_STAR`），請選沒有右鍵功能的物品。 |
| `empty.name` / `empty.lore` / `empty.item-model` | 空球的外觀。 |
| `filled.name` / `filled.lore` / `filled.item-model` | 滿球的外觀，`<mob>` 會被換成生物名稱。 |
| `capture-chance` | 捕捉成功率，`0.0` 到 `1.0`（預設 `1.0`）。 |
| `max-data-length` | 可儲存的資料長度上限（預設 `30000`）。 |
| `blacklist` | 不可捕捉的生物種類，例如 `WITHER`。 |
| `disabled-worlds` | 停用插件的世界。 |
| `protection.worldguard-block-non-op` | 非 OP 在 WorldGuard 區域內是否禁止使用（預設 `true`）。 |
| `protection.residence-require-build` | 領地內是否需要 Residence `build` 權限（預設 `true`）。 |
| `messages.*` | 所有給玩家看的訊息。 |

### 從原始碼編譯

```
mvn clean package
```

編譯出的 jar 會在 `target/` 資料夾。

### 授權

以 [GNU GPL v3.0](LICENSE) 釋出。你可以自由使用、修改與再散布，但必須保留原作者的版權聲明與授權文字；散布修改後的版本時，也必須以 GPL v3.0 開源。