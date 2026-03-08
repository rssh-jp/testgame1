# 12. 共通ジョブシステム仕様

## 概要

味方ユニットと敵ユニットが **共通のジョブ（UnitClass）** で動作し、ジョブがユニットの「形」（基礎ステータス配分）を決定するように、ステータス計算モデルを抜本的に再設計する。

### 設計思想

> 「ジョブが形を決める」— 同じジョブ・同じレベルの2体は、個人差がなければ同一ステータスになる。

現状の問題を解消し、以下の原則に基づく新モデルを導入する：

1. **ジョブ＝ステータスの土台**: `UnitClass.baseStats` がLv.1の基礎ステータスを定義する
2. **個人差は小さな補正**: `personalModifier` で味方キャラの個性を表現（±2程度）
3. **成長は従来通り**: `personalGrowthRate + classGrowthRate` の確定加算
4. **敵味方共通**: 同じクラスなら同じ baseStats を参照。敵は personalModifier=0 でジョブ通りの形

---

## 1. 新しいステータス計算式

### 1.1 現行の計算式（廃止）

```
GameUnit.stats = baseStats + levelUpStats
```

- `baseStats`: ユニット個別にハードコードされた固定値（クラスと無関係）
- `levelUpStats`: レベルアップ時の成長累積

### 1.2 新しい計算式

```
GameUnit.stats = unitClass.baseStats + personalModifier + levelUpStats
```

| 項目 | 型 | 説明 | 所有者 |
|------|-----|------|--------|
| `unitClass.baseStats` | `Stats` | ジョブが定義する基礎ステータス（Lv.1の土台） | `UnitClass` |
| `personalModifier` | `Stats` | ユニット固有のステータス補正（個人差） | `GameUnit` |
| `levelUpStats` | `Stats` | レベルアップ成長の累積値 | `GameUnit` |

#### 実効値

ゲーム内の全計算には `stats.effectiveXxx`（Float → Int 切り捨て）を使用する（従来仕様と同一）。

### 1.3 レベルアップ時の成長（変更なし）

```
effectiveGrowth = personalGrowthRate + unitClass.classGrowthRate
levelUpStats += effectiveGrowth  (各レベルアップごとに確定加算)
```

成長システムそのものは `docs/spec/10-growth-system.md` から変更なし。

### 1.4 クラスチェンジ時のステータス変化ルール

**旧仕様**: `changeClass()` は `unitClass` を差し替えるだけで、ステータスは変化しない。

**新仕様**: クラスチェンジにより `unitClass.baseStats` が変わるため、総合ステータスが即座に変化する。

#### ステータス変化の計算

```kotlin
// クラスチェンジ前
oldStats = oldClass.baseStats + personalModifier + levelUpStats

// クラスチェンジ後
newStats = newClass.baseStats + personalModifier + levelUpStats

// 差分
statsDiff = newStats - oldStats  (= newClass.baseStats - oldClass.baseStats)
```

#### currentHp のクランプ処理

```kotlin
fun changeClass(newClass: UnitClass) {
    val oldMaxHp = maxHp           // 変更前の最大HP
    unitClass = newClass
    val newMaxHp = maxHp           // 変更後の最大HP
    val hpDiff = newMaxHp - oldMaxHp

    // HP差分を反映: maxHpが増えたらcurrentHpも増える、減ったら減る
    currentHp = (currentHp + hpDiff).coerceIn(1, newMaxHp)

    // 装備不可武器の自動外し（既存ロジック維持）
    // ...
}
```

| ケース | oldMaxHp | newMaxHp | hpDiff | currentHp(before) | currentHp(after) |
|--------|----------|----------|--------|-------------------|------------------|
| HP増加 | 18 | 22 | +4 | 15 | 19 |
| HP減少（余裕あり） | 22 | 16 | -6 | 20 | 14 |
| HP減少（クランプ） | 22 | 16 | -6 | 22 | 16 |
| HP減少（下限保護） | 16 | 14 | -2 | 2 | 1（下限=1）|
| HP同じ | 18 | 18 | 0 | 12 | 12 |

> **設計根拠**: HP差分を直接反映する方式は、FEシリーズのクラスチェンジ時HP処理に準拠。「受けたダメージ量」が保存されることでプレイヤーの直感に合う。

---

## 2. 全10クラスの baseStats 定義

### 2.1 設計方針

- 各クラスの **役割（タンク・アタッカー・魔法型等）** を baseStats に明確に反映する
- 既存の味方ユニットのLv.1ステータスとの整合を取り、`personalModifier` が ±2 以内に収まるように調整
- ステータス合計のバランスを保つ（物理クラスはHP/STR/DEF重視、魔法クラスはMAG/RES重視）

### 2.2 クラス baseStats 一覧

| クラス | HP | STR | MAG | SKL | SPD | LCK | DEF | RES | 合計 | 特徴 |
|--------|-----|-----|-----|-----|-----|-----|-----|-----|------|------|
| ロード | 18 | 5 | 1 | 6 | 7 | 4 | 4 | 1 | 46 | バランス型。全能力が中の上 |
| ソードファイター | 17 | 5 | 0 | 7 | 8 | 3 | 3 | 0 | 43 | SKL/SPD突出。命中・回避の技巧派 |
| ランサー | 18 | 6 | 0 | 5 | 5 | 2 | 6 | 0 | 42 | STR/DEF均等。攻守バランスの前衛 |
| アクスファイター | 20 | 7 | 0 | 3 | 4 | 1 | 4 | 0 | 39 | HP/STR突出。低SKL/SPDの脳筋型 |
| アーチャー | 16 | 5 | 0 | 7 | 7 | 3 | 3 | 1 | 42 | SKL/SPD高め。遠距離から確実に狙撃 |
| メイジ | 15 | 1 | 6 | 5 | 5 | 3 | 2 | 5 | 42 | MAG/RES特化。紙装甲だが魔法火力は一級 |
| ヒーラー | 14 | 0 | 5 | 4 | 4 | 5 | 1 | 4 | 37 | MAG/LCK/RES型。最低HP/DEFの支援職 |
| ナイト | 20 | 6 | 0 | 4 | 4 | 2 | 7 | 0 | 43 | HP/DEF突出。騎馬の壁役 |
| ペガサスナイト | 16 | 4 | 1 | 5 | 7 | 4 | 3 | 4 | 44 | SPD/RES型。魔法に強い飛行機動兵 |
| アーマーナイト | 22 | 6 | 0 | 3 | 2 | 1 | 9 | 0 | 43 | 最高HP/DEF。SPD絶望的な鉄壁 |

### 2.3 既存味方ユニットとの整合性検証

| ユニット | クラス | 現行baseStats | クラスbaseStats | personalModifier | 合計（一致確認） |
|---------|--------|---------------|-----------------|-------------------|-----------------|
| アレス | ロード | HP=20,STR=6,MAG=0,SKL=7,SPD=8,LCK=5,DEF=5,RES=0 | HP=18,STR=5,MAG=1,SKL=6,SPD=7,LCK=4,DEF=4,RES=1 | HP+2,STR+1,MAG-1,SKL+1,SPD+1,LCK+1,DEF+1,RES-1 | ✓ 一致 |
| リーナ | ランサー | HP=18,STR=7,MAG=0,SKL=5,SPD=5,LCK=3,DEF=7,RES=0 | HP=18,STR=6,MAG=0,SKL=5,SPD=5,LCK=2,DEF=6,RES=0 | HP+0,STR+1,MAG+0,SKL+0,SPD+0,LCK+1,DEF+1,RES+0 | ✓ 一致 |
| マリア | アーチャー | HP=16,STR=5,MAG=0,SKL=8,SPD=7,LCK=4,DEF=3,RES=0 | HP=16,STR=5,MAG=0,SKL=7,SPD=7,LCK=3,DEF=3,RES=1 | HP+0,STR+0,MAG+0,SKL+1,SPD+0,LCK+1,DEF+0,RES-1 | ✓ 一致 |
| エリック | メイジ | HP=15,STR=1,MAG=7,SKL=5,SPD=5,LCK=4,DEF=2,RES=6 | HP=15,STR=1,MAG=6,SKL=5,SPD=5,LCK=3,DEF=2,RES=5 | HP+0,STR+0,MAG+1,SKL+0,SPD+0,LCK+1,DEF+0,RES+1 | ✓ 一致 |
| セシリア | ヒーラー | HP=14,STR=0,MAG=6,SKL=4,SPD=4,LCK=6,DEF=1,RES=5 | HP=14,STR=0,MAG=5,SKL=4,SPD=4,LCK=5,DEF=1,RES=4 | HP+0,STR+0,MAG+1,SKL+0,SPD+0,LCK+1,DEF+0,RES+1 | ✓ 一致 |

> **結果**: 全味方ユニットの `personalModifier` が **±2以内** に収まり、Lv.1ステータスは現行と完全に一致する。

### 2.4 personalModifier の設計ガイドライン

| 項目 | 指針 |
|------|------|
| 推奨範囲 | 各能力値 ±3 以内 |
| 合計の目安 | ±5 程度（突出したキャラでも ±8 以下） |
| 敵ユニット | 通常は全0（ジョブ通りの形）。ボス級は小さな正補正を付与してもよい |
| 新キャラ追加時 | クラスのbaseStatsで基本形を確認し、キャラの個性を±2程度で表現する |

---

## 3. GameUnit の変更仕様

### 3.1 コンストラクタの変更

```kotlin
class GameUnit(
    val id: String,
    val name: String,
    var unitClass: UnitClass,
    val faction: Faction,
    var level: Int = 1,
    var exp: Int = 0,
    val personalModifier: Stats = Stats(),  // 旧: baseStats → 個人補正値に変更
    val levelUpStats: Stats = Stats(),
    val personalGrowthRate: GrowthRate,
    val weapons: MutableList<Weapon> = mutableListOf(),
    val isLord: Boolean = false
)
```

| 変更点 | 旧 | 新 |
|--------|-----|-----|
| パラメータ名 | `baseStats: Stats` | `personalModifier: Stats = Stats()` |
| 意味 | ユニット個別の絶対基礎値 | クラスbaseStatsからの差分（個人差） |
| デフォルト値 | なし（必須引数） | `Stats()`（全0 = クラス通り） |

### 3.2 stats getter の変更

```kotlin
// 旧
val stats: Stats
    get() = baseStats + levelUpStats

// 新
val stats: Stats
    get() = unitClass.baseStats + personalModifier + levelUpStats
```

3項加算。`Stats.plus()` は既存の2項演算子を連続適用する。

### 3.3 changeClass() の変更

```kotlin
fun changeClass(newClass: UnitClass) {
    val oldMaxHp = maxHp

    unitClass = newClass

    // HP差分を反映し、下限1にクランプ
    val newMaxHp = maxHp
    val hpDiff = newMaxHp - oldMaxHp
    currentHp = (currentHp + hpDiff).coerceIn(1, newMaxHp)

    // 右手武器が装備不可なら外す（既存ロジック）
    rightHand?.let { weapon ->
        if (weapon.type !in newClass.usableWeapons) {
            weapons.add(0, weapon)
            rightHand = null
        }
    }

    // 左手武器が装備不可なら外す（既存ロジック）
    leftHand?.let { weapon ->
        if (weapon.type !in newClass.usableWeapons) {
            weapons.add(0, weapon)
            leftHand = null
        }
    }

    // 二刀流非対応クラスに変更した場合、左手を外す（既存ロジック）
    if (!newClass.canDualWield && leftHand != null) {
        weapons.add(0, leftHand!!)
        leftHand = null
    }
}
```

### 3.4 currentHp 初期化の変更

```kotlin
// 旧
var currentHp: Int = stats.effectiveHp

// 新（変更なし — stats getter が3項になるだけで、初期化ロジックは同一）
var currentHp: Int = stats.effectiveHp
```

`stats` の getter が `unitClass.baseStats + personalModifier + levelUpStats` に変わるため、初期化時にクラスbaseStatsが反映される。

### 3.5 その他の影響なし

以下は `stats.effectiveXxx` を参照しているだけなので、`stats` getter の変更で自動的に正しく動作する：
- `maxHp` → `stats.effectiveHp`
- `effectiveSpeed()` → `stats.effectiveSpd`
- `DamageCalc` → `stats.effectiveStr`, `stats.effectiveDef` 等
- `TurnManager` → `effectiveSpeed()`

---

## 4. UnitClass.baseStats の設定

### 4.1 UnitClass の変更

`UnitClass` のデータ定義に、実際のbaseStats値を設定する。

```kotlin
/** ロード（主人公専用） */
private val DEFAULT_LORD = UnitClass(
    id = "lord", name = "ロード",
    moveType = MoveType.INFANTRY, baseMov = 5,
    usableWeapons = listOf(WeaponType.SWORD),
    baseStats = Stats(hp = 18f, str = 5f, mag = 1f, skl = 6f, spd = 7f, lck = 4f, def = 4f, res = 1f),
    canDualWield = true, dualWieldPenalty = 2,
    classGrowthRate = GrowthRate(...)  // 既存と同一
)
```

### 4.2 全クラスの baseStats 定義値

```kotlin
// ロード
baseStats = Stats(hp = 18f, str = 5f, mag = 1f, skl = 6f, spd = 7f, lck = 4f, def = 4f, res = 1f)

// ソードファイター
baseStats = Stats(hp = 17f, str = 5f, mag = 0f, skl = 7f, spd = 8f, lck = 3f, def = 3f, res = 0f)

// ランサー
baseStats = Stats(hp = 18f, str = 6f, mag = 0f, skl = 5f, spd = 5f, lck = 2f, def = 6f, res = 0f)

// アクスファイター
baseStats = Stats(hp = 20f, str = 7f, mag = 0f, skl = 3f, spd = 4f, lck = 1f, def = 4f, res = 0f)

// アーチャー
baseStats = Stats(hp = 16f, str = 5f, mag = 0f, skl = 7f, spd = 7f, lck = 3f, def = 3f, res = 1f)

// メイジ
baseStats = Stats(hp = 15f, str = 1f, mag = 6f, skl = 5f, spd = 5f, lck = 3f, def = 2f, res = 5f)

// ヒーラー
baseStats = Stats(hp = 14f, str = 0f, mag = 5f, skl = 4f, spd = 4f, lck = 5f, def = 1f, res = 4f)

// ナイト
baseStats = Stats(hp = 20f, str = 6f, mag = 0f, skl = 4f, spd = 4f, lck = 2f, def = 7f, res = 0f)

// ペガサスナイト
baseStats = Stats(hp = 16f, str = 4f, mag = 1f, skl = 5f, spd = 7f, lck = 4f, def = 3f, res = 4f)

// アーマーナイト
baseStats = Stats(hp = 22f, str = 6f, mag = 0f, skl = 3f, spd = 2f, lck = 1f, def = 9f, res = 0f)
```

---

## 5. 個人差の扱い

### 5.1 同ジョブ・同レベルのステータス同一性

**意図通りである。** personalModifier が 0 であれば、同じジョブ・同じレベルのユニットは完全に同一のステータスを持つ。

| 項目 | 説明 |
|------|------|
| Lv.1 敵アクスファイター | `axeFighter.baseStats + Stats() + Stats()` = `HP=20, STR=7, ...` |
| Lv.1 味方アクスファイター（personalModifier=0） | 同上（完全一致） |
| Lv.1 味方アクスファイター（personalModifier≠0） | クラスbaseStats + 個人差で少しずれる |

### 5.2 個人差の表現手段

個人差は以下の **2つのレイヤー** で表現する。

#### レイヤー1: personalModifier（初期能力差）

- Lv.1時点でクラス基準からの差分を表現
- 味方キャラごとに固有の値を設定（通常 ±2 以内）
- 敵は通常 `Stats()` （全0）。ボス級のみ小さな正補正を設定可
- クラスチェンジしても `personalModifier` は変わらない（キャラ固有の才能）

#### レイヤー2: personalGrowthRate（成長率差）

- レベルアップごとの差が累積するため、高レベルになるほど差が顕著になる
- 同じクラスでも、personalGrowthRate の違いで Lv.10以降は明確なステータス差が生まれる
- 既存の成長率テーブルはそのまま維持

#### 個人差の効果量シミュレーション

以下は「同一クラス（ランサー）のリーナ vs personalModifier=0の汎用ランサー」の比較。

| レベル | リーナ STR | 汎用ランサー STR | 差 |
|--------|-----------|-----------------|-----|
| 1 | 7 (6+1+0) | 6 (6+0+0) | +1 |
| 5 | 9.2→9 | 7.8→7 | +2 |
| 10 | 11.95→11 | 10.35→10 | +1~2 |
| 20 | 17.45→17 | 15.45→15 | +2 |

> personalModifier のSTR+1 と personalGrowthRate の差が複合して、一貫した個人差が維持される。

### 5.3 この方式の利点

| 利点 | 説明 |
|------|------|
| ジョブの形が明確 | 「メイジは紙装甲で魔法火力が高い」が baseStats で即座に分かる |
| 敵生成が簡単 | クラス + レベルだけで合理的なステータスが自動決定 |
| 個人差も表現可能 | personalModifier で味方キャラの個性を維持 |
| クラスチェンジに意味がある | クラスを変えるとステータス配分が実際に変わる |

---

## 6. 敵ユニット生成への影響

### 6.1 JSON固定配置敵

**現行仕様**: JSON に `stats` オブジェクトを持ち、それが `baseStats` としてそのまま使用される。

**新仕様**: `stats` オブジェクトがない場合、`personalModifier = Stats()` となり、クラスのbaseStatsがそのまま使用される。

#### JSON 新フォーマット（推奨）

```json
{
  "id": "enemy_01",
  "classId": "axeFighter",
  "name": "山賊A",
  "level": 3,
  "weaponId": "ironAxe"
}
```

- `classId` のみでステータスが自動決定
- `stats` フィールドは不要（省略可能）
- レベルが2以上の場合、`(level - 1)` 回分の成長累積（デフォルト敵成長率）を `levelUpStats` に自動計算

#### JSON 従来互換フォーマット（personalModifier指定）

```json
{
  "id": "boss_01",
  "classId": "knight",
  "name": "敵将マルクス",
  "level": 8,
  "weaponId": "silverLance",
  "personalModifier": {"hp": 3, "str": 2, "def": 2},
  "isLord": true
}
```

- ボス級の敵に個人補正を付与する場合に使用
- `personalModifier` フィールドがある場合、それが `GameUnit.personalModifier` に設定される

#### 旧JSONとの下位互換（マイグレーション期間）

```json
{
  "id": "enemy_01",
  "classId": "axeFighter",
  "name": "山賊A",
  "level": 1,
  "stats": {"hp": 18, "str": 6, "mag": 0, "skl": 3, "spd": 4, "lck": 1, "def": 3, "res": 0},
  "weaponId": "ironAxe"
}
```

- `stats` フィールドが存在する場合: `personalModifier = stats - unitClass.baseStats` として計算
- これにより、旧JSONを変更せずに新システムで読み込み可能

#### MapLoader.parseEnemyUnit() の変更ロジック

```kotlin
fun parseEnemyUnit(json: JsonValue): GameUnit? {
    // ... classId, name, level 等のパース（既存と同様）
    val unitClass = UnitClass.ALL[classId] ?: return null

    // personalModifier の決定
    val personalModifier = when {
        // 新フォーマット: personalModifier フィールドがある場合
        json.has("personalModifier") -> parseStats(json.get("personalModifier"))
        // 旧互換: stats フィールドがある場合、差分として計算
        json.has("stats") -> {
            val totalStats = parseStats(json.get("stats"))
            totalStats - unitClass.baseStats  // Stats に minus 演算子を追加
        }
        // デフォルト: クラス通り（補正なし）
        else -> Stats()
    }

    // レベルに応じた levelUpStats を計算
    val levelUpStats = generateLevelUpStats(level)

    return GameUnit(
        id = id, name = name,
        unitClass = unitClass, faction = Faction.ENEMY,
        level = level,
        personalModifier = personalModifier,
        levelUpStats = levelUpStats,
        personalGrowthRate = GrowthRate(),
        isLord = isLord
    )
}
```

### 6.2 ランダム敵生成

**現行仕様**: `ENEMY_BASE_STATS` 定数（全クラス共通値）を使用。

**新仕様**: クラスの `baseStats` を使用し、`personalModifier = Stats()`。

#### 変更内容

```kotlin
// 旧
val unit = GameUnit(
    ...
    baseStats = ENEMY_BASE_STATS.copy(),
    ...
)

// 新
val unit = GameUnit(
    ...
    unitClass = unitClass,
    personalModifier = Stats(),  // クラス通りの形（補正なし）
    levelUpStats = generateLevelUpStats(level),
    ...
)
```

- `ENEMY_BASE_STATS` 定数は **削除**
- クラスごとに異なるLv.1ステータスで生成されるため、「メイジなのにSTR=5」のような不整合が解消される

#### ランダム敵のステータス例（Lv.5）

| クラス | HP | STR | MAG | SKL | SPD | DEF | RES |
|--------|-----|-----|-----|-----|-----|-----|-----|
| アクスファイター | 22 | 8 | 0 | 4 | 4 | 5 | 0 |
| メイジ | 17 | 1 | 7 | 6 | 5 | 2 | 5 |
| アーチャー | 18 | 6 | 0 | 8 | 7 | 3 | 1 |

> Lv.5 = baseStats + growthRate × 4。各クラスの個性が初期状態から反映される。

---

## 7. Stats への minus 演算子追加

旧JSON互換の `personalModifier = stats - unitClass.baseStats` 計算のため、`Stats` に減算演算子を追加する。

```kotlin
data class Stats(...) {
    // 既存の plus
    operator fun plus(other: Stats): Stats { ... }

    // 新規: minus
    operator fun minus(other: Stats): Stats {
        return Stats(
            hp = hp - other.hp,
            str = str - other.str,
            mag = mag - other.mag,
            skl = skl - other.skl,
            spd = spd - other.spd,
            lck = lck - other.lck,
            def = def - other.def,
            res = res - other.res
        )
    }
}
```

---

## 8. セーブ/ロードの変更

### 8.1 セーブデータバージョン

`SAVE_VERSION` を **v5** に変更。

### 8.2 書き出し（writeUnit）

```kotlin
// 旧: writer.object("baseStats"); writeStats(writer, unit.baseStats)
// 新: writer.object("personalModifier"); writeStats(writer, unit.personalModifier)
```

### 8.3 読み込み（readUnit）— 下位互換

```kotlin
// personalModifier の復元
val personalModifier = when {
    // v5: personalModifier フィールドがある
    node.has("personalModifier") -> readStats(node.get("personalModifier"))
    // v4: baseStats をそのまま personalModifier とみなす
    // ※ v4の baseStats は「ユニット個別の絶対基礎値」だったため、
    //   unitClass.baseStats を引いて差分にすることで正しいpersonalModifierを復元
    node.has("baseStats") -> {
        val oldBaseStats = readStats(node.get("baseStats"))
        val classBaseStats = unitClass.baseStats
        oldBaseStats - classBaseStats
    }
    // v3以前: stats をそのまま差分に変換
    node.has("stats") -> {
        val oldStats = readStats(node.get("stats"))
        oldStats - unitClass.baseStats
    }
    else -> Stats()
}
```

### 8.4 マイグレーション検証

| セーブバージョン | データ | 復元結果 |
|-----------------|--------|---------|
| v5 | `personalModifier: {hp: 2, str: 1, ...}` | そのまま使用 |
| v4 | `baseStats: {hp: 20, str: 6, ...}`, classId: "lord" | `{hp:20,...} - lord.baseStats{hp:18,...}` = `{hp:2, str:1, ...}` |
| v3以前 | `stats: {hp: 20, str: 6, ...}` | 同上（stats を baseStats として扱い差分計算） |

---

## 9. 受け入れ条件（Given/When/Then 形式）

### AC-01: クラスbaseStatsがLv.1ステータスを決定する

```
Given: personalModifier = Stats(), levelUpStats = Stats() のユニットを作成する
  And: unitClass = UnitClass.MAGE
When: stats を取得する
Then: stats == UnitClass.MAGE.baseStats
  And: stats.effectiveMag == 6, stats.effectiveHp == 15, stats.effectiveDef == 2
```

### AC-02: personalModifierがステータスに加算される

```
Given: unitClass = UnitClass.MAGE, personalModifier = Stats(mag = 1f, lck = 1f, res = 1f)
  And: levelUpStats = Stats()
When: stats を取得する
Then: stats.effectiveMag == 7  (6 + 1)
  And: stats.effectiveLck == 4  (3 + 1)
  And: stats.effectiveRes == 6  (5 + 1)
```

### AC-03: クラスチェンジでステータスが変化する

```
Given: unitClass = UnitClass.LANCER のLv.5ユニット
  And: personalModifier = Stats(str = 1f)
  And: levelUpStats = Stats(hp = 2.4f, str = 1.8f, def = 1.6f)
When: changeClass(UnitClass.MAGE) を実行する
Then: stats.effectiveStr == 2 + 1 + 1 = 4  (mage.STR=1 + mod=1 + levelUp=1.8→1)
  And: stats.effectiveMag == 6 + 0 + 0 = 6  (mage.MAG=6)
  And: stats.effectiveDef == 2 + 0 + 1 = 3  (mage.DEF=2 + levelUp=1.6→1)
```

### AC-04: クラスチェンジでcurrentHpが正しくクランプされる

```
Given: unitClass = UnitClass.ARMOR_KNIGHT (HP base=22)
  And: personalModifier = Stats(), levelUpStats = Stats()
  And: currentHp == 22 (全快)
When: changeClass(UnitClass.HEALER) を実行する (HP base=14)
Then: maxHp == 14
  And: currentHp == 14  (22 + (14-22) = 14, 下限=1以上なので14)
```

### AC-05: クラスチェンジでcurrentHpの下限が1になる

```
Given: unitClass = UnitClass.ARMOR_KNIGHT (HP base=22)
  And: currentHp == 3 (瀕死)
When: changeClass(UnitClass.HEALER) を実行する (HP base=14)
Then: currentHp == 1  (3 + (14-22) = -5 → coerceIn(1, 14) = 1)
```

### AC-06: 敵ユニットがクラスbaseStatsで自動生成される

```
Given: JSON に classId="axeFighter", level=1, stats フィールドなし の敵データ
When: MapLoader.parseEnemyUnit() で読み込む
Then: unit.personalModifier == Stats()
  And: unit.stats == UnitClass.AXE_FIGHTER.baseStats
  And: unit.stats.effectiveHp == 20, unit.stats.effectiveStr == 7
```

### AC-07: ランダム敵がクラスごとに異なるステータスで生成される

```
Given: ランダム敵生成で axeFighter と mage を各1体生成する
  And: 両方 Lv.1
When: それぞれの stats を比較する
Then: axeFighter.stats.effectiveStr > mage.stats.effectiveStr
  And: mage.stats.effectiveMag > axeFighter.stats.effectiveMag
  And: axeFighter.stats.effectiveHp > mage.stats.effectiveHp
```

### AC-08: 同じクラス・同レベルの味方と敵のステータス一致性

```
Given: unitClass = UnitClass.LANCER, personalModifier = Stats(), level = 1 の味方ユニット
  And: unitClass = UnitClass.LANCER, personalModifier = Stats(), level = 1 の敵ユニット
When: 両者の stats を比較する
Then: 全ステータスが完全一致する
```

### AC-09: 旧JSON（statsフィールドあり）の下位互換

```
Given: JSON に classId="axeFighter", stats={hp:18, str:6, mag:0, skl:3, spd:4, lck:1, def:3, res:0}
When: MapLoader.parseEnemyUnit() で読み込む
Then: personalModifier == Stats(hp:-2, str:-1, mag:0, skl:0, spd:0, lck:0, def:-1, res:0)
  And: stats == Stats(hp:18, str:6, mag:0, skl:3, spd:4, lck:1, def:3, res:0)
  (= axeFighter.baseStats{hp:20,str:7,...} + personalModifier{hp:-2,str:-1,...})
```

### AC-10: レベルアップ時の成長がクラス成長率を反映する（回帰テスト）

```
Given: unitClass = UnitClass.KNIGHT, personalModifier = Stats(), personalGrowthRate = GrowthRate()
  And: exp = 99
When: gainExp(1) でレベルアップを発動する
Then: levelUpStats にナイトの classGrowthRate 分が加算される
  And: levelUpStats.def の増加量 == UnitClass.KNIGHT.classGrowthRate.def
```

### AC-11: 装備自動外しが維持される（回帰テスト）

```
Given: unitClass = UnitClass.SWORD_FIGHTER で右手に剣を装備
When: changeClass(UnitClass.ARCHER) を実行する
Then: rightHand == null (弓クラスに剣は装備不可)
  And: weapons に剣が移動している
```

### AC-12: v4セーブデータの正常マイグレーション

```
Given: v4セーブデータ（baseStats={hp:20, str:6, mag:0, skl:7, spd:8, lck:5, def:5, res:0}, classId="lord"）
When: SaveManager.readUnit() で読み込む
Then: personalModifier == Stats(hp:2, str:1, mag:-1, skl:1, spd:1, lck:1, def:1, res:-1)
  And: stats == baseStats（v4の元の値と同一）
```

---

## 10. 変更対象ファイル一覧

### プロダクションコード

| # | ファイル | 変更内容 | 影響度 |
|---|---------|---------|--------|
| 1 | `core/.../model/unit/UnitClass.kt` | 全10クラスの `baseStats` に実値を設定 | 中 |
| 2 | `core/.../model/unit/GameUnit.kt` | `baseStats` → `personalModifier` に改名、`stats` getter 変更、`changeClass()` にHP差分処理追加 | **大** |
| 3 | `core/.../model/unit/Stats.kt` | `operator fun minus()` 追加 | 小 |
| 4 | `core/.../data/MapLoader.kt` | `parseEnemyUnit()` の personalModifier 対応、`ENEMY_BASE_STATS` 削除、`generateRandomEnemies()` 変更 | **大** |
| 5 | `core/.../data/SaveManager.kt` | v5 対応: `personalModifier` の保存/復元、v4以前からのマイグレーション | 中 |
| 6 | `core/.../screen/BattleScreen.kt` | テストデータ生成の `baseStats` → `personalModifier` 変更 | 中 |

### テストコード

| # | ファイル | 変更内容 |
|---|---------|---------|
| 7 | `core/.../model/unit/ClassChangeTest.kt` | `baseStats` → `personalModifier` 変更、クラスチェンジ時HP変化テスト追加 |
| 8 | `core/.../model/unit/ClassGrowthTest.kt` | `baseStats` → `personalModifier` 変更 |
| 9 | `core/.../model/unit/GameUnitEquipmentTest.kt` | `baseStats` → `personalModifier` 変更 |
| 10 | `core/.../model/unit/GameUnitExpTest.kt` | `baseStats` → `personalModifier` 変更 |
| 11 | `core/.../system/LevelUpSystemTest.kt` | `baseStats` → `personalModifier` 変更 |
| 12 | `core/.../system/BattleSystemExpTest.kt` | `baseStats` → `personalModifier` 変更 |
| 13 | `core/.../system/BattleSystemCounterTest.kt` | `baseStats` → `personalModifier` 変更 |
| 14 | `core/.../system/VictoryCheckerTest.kt` | `baseStats` → `personalModifier` 変更 |
| 15 | `core/.../system/PathFinderTest.kt` | `baseStats` → `personalModifier` 変更 |
| 16 | `core/.../system/TurnManagerTest.kt` | `baseStats` → `personalModifier` 変更 |
| 17 | `core/.../system/AITacticTest.kt` | `baseStats` → `personalModifier` 変更 |
| 18 | `core/.../screen/DeploymentSwapTest.kt` | `baseStats` → `personalModifier` 変更 |
| 19 | `core/.../model/battle/DamageCalcTest.kt` | `baseStats` → `personalModifier` 変更 |
| 20 | `core/.../data/MapLoaderTest.kt` | 敵パース関連テスト更新 |
| 21 | `core/.../data/SaveManagerTest.kt` | v5 保存/v4マイグレーションテスト追加 |

### データファイル

| # | ファイル | 変更内容 |
|---|---------|---------|
| 22 | `core/assets/data/classes.json`（存在する場合） | baseStats 値の追加/更新 |
| 23 | `core/assets/maps/chapter_*.json` | 敵の `stats` フィールドは残しても互換動作するため **変更不要**（推奨: 段階的に `personalModifier` フォーマットへ移行） |

### 仕様ドキュメント

| # | ファイル | 変更内容 |
|---|---------|---------|
| 24 | `docs/spec/03-unit-class.md` | クラスbaseStatsの記載追加 |
| 25 | `docs/spec/10-growth-system.md` | ステータス計算式の記載更新 |
| 26 | `docs/spec/11-class-change.md` | R5ルール更新（ステータスがクラスbaseStats差分で変化する旨を明記） |

---

## 11. 制約の確認

| 制約 | 充足状況 | 根拠 |
|------|---------|------|
| 装備システム（4スロット制）に影響しない | ✅ | 装備ロジックは `rightHand`, `leftHand`, `armorSlot1/2` で独立。`stats` getter の変更は装備スロットに影響しない |
| CTベースターン管理に影響しない | ✅ | CT計算は `effectiveSpeed()` → `stats.effectiveSpd` を参照。`stats` 内部の算出方法が変わるだけで、CT蓄積ロジックは不変 |
| 既存テスト（7ファイル50件超）の大部分が移行可能 | ✅ | テスト内の `baseStats = Stats(...)` を `personalModifier = Stats(...)` に機械的リネーム可能。ただし数値が `unitClass.baseStats` からの差分になるため、テストの期待値調整が必要なケースあり（AC-03参照） |
| `docs/spec/10-growth-system.md` の確定成長との整合性 | ✅ | 成長式 `personalGrowthRate + classGrowthRate` は不変。`levelUpStats` の蓄積ロジックに変更なし |

---

## 12. 実装順序（推奨）

段階的に実装し、各ステップでテストを通すことを推奨する。

### Phase 1: 基盤変更（破壊的変更）

1. `Stats.kt` に `minus` 演算子を追加
2. `UnitClass.kt` の全10クラスに baseStats 値を設定
3. `GameUnit.kt` の `baseStats` → `personalModifier` リネーム、`stats` getter 変更、`changeClass()` 更新
4. 全テストの `baseStats` パラメータを `personalModifier` にリネーム（値はとりあえずそのまま）

### Phase 2: テスト修正

5. テストの期待値を新計算式に合わせて調整
6. `ClassChangeTest` にHP変化テストを追加
7. AC-01 〜 AC-12 の新規テストを追加

### Phase 3: データ層対応

8. `MapLoader.kt` の敵パースを新仕様に更新（`ENEMY_BASE_STATS` 削除）
9. `SaveManager.kt` の v5 対応
10. `BattleScreen.kt` のテストデータ更新

### Phase 4: ドキュメント・クリーンアップ

11. 関連仕様書の更新
12. 旧JSON の段階的マイグレーション（任意）
