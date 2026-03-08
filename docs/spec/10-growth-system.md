# 10. 確定成長システム仕様

## 概要

レベルアップ時の能力値成長を **確率ベース** から **確定加算ベース（FFT方式）** に抜本的に変更する。
毎レベルアップで固定の小数値が確実に加算され、ランダム要素を排除する。

## 基本ルール

### 内部ステータス（Float）

- 全能力値（HP/STR/MAG/SKL/SPD/LCK/DEF/RES）は **内部的に Float（小数）で保持** する
- 例: HP = 20.7, STR = 6.35

### 実効ステータス（Int、小数点切り捨て）

- ゲーム内の全計算（ダメージ/命中/回避/追撃/CT蓄積等）には **小数点以下を切り捨てた整数値** を使用する
- 例:
  - 内部 10.2 → 実効値 **10**
  - 内部 10.99 → 実効値 **10**
  - 内部 11.0 → 実効値 **11**
- Kotlin の `Float.toInt()` で実装（正の数に対しては floor と同等）

### 確定成長

- レベルアップ時、各能力値に GrowthRate の値（Float）を **確実に加算**
- **確率によるブレは一切なし** — `rollGrowth()` を廃止
- 例: HP 成長値 0.70 → 毎レベルアップで HP に +0.70

---

## SPD 共通成長（FFT方式）

| 項目 | 値 |
|------|-----|
| SPD 成長値 | **全ユニット共通 0.20** |
| 実効値 +1 に必要なレベル数 | **5レベル** |

- SPD の成長値は **全ユニット共通で 0.20**
- キャラクターやクラスに依存しない、フラットな SPD 成長
- 装備重量による実効速度への影響は従来通り (`effectiveSpeed()`)

---

## 成長値テーブル（味方ユニット）

各ユニットに設定された GrowthRate の現在値を、レベルアップ時にそのまま加算する。

### 味方

| ユニット | HP | STR | MAG | SKL | SPD | LCK | DEF | RES |
|---------|-----|-----|-----|-----|-----|-----|-----|-----|
| アレス | 0.70 | 0.50 | 0.10 | 0.55 | **0.20** | 0.40 | 0.35 | 0.25 |
| リーナ | 0.60 | 0.55 | 0.05 | 0.45 | **0.20** | 0.30 | 0.50 | 0.20 |
| マリア | 0.55 | 0.45 | 0.05 | 0.60 | **0.20** | 0.40 | 0.25 | 0.30 |
| エリック | 0.45 | 0.10 | 0.60 | 0.50 | **0.20** | 0.35 | 0.15 | 0.50 |
| セシリア | 0.50 | 0.05 | 0.65 | 0.40 | **0.20** | 0.50 | 0.10 | 0.55 |

### 敵（ランダム生成用のデフォルト成長値）

| HP | STR | MAG | SKL | SPD | LCK | DEF | RES |
|-----|-----|-----|-----|-----|-----|-----|-----|
| 0.60 | 0.40 | 0.20 | 0.35 | **0.20** | 0.25 | 0.30 | 0.20 |

> 固定配置の敵は JSON で直接ステータスを指定するため、成長率は不要（`GrowthRate()` デフォルト）。

#### ランダム敵のレベル基準と成長適用

- 基準レベル: 出撃中味方ユニットの平均レベル（整数除算）
- レベル下限: `maxOf(1, averageLevel)`
- 成長加算回数: `maxOf(0, level - 1)`（Lv.1は加算0回）
- 算出式: `baseStats + growthRate × (level - 1)`

---

## データモデルの変更

### Stats data class

```kotlin
data class Stats(
    var hp: Float = 0f,
    var str: Float = 0f,
    var mag: Float = 0f,
    var skl: Float = 0f,
    var spd: Float = 0f,
    var lck: Float = 0f,
    var def: Float = 0f,
    var res: Float = 0f
) {
    // 実効値（小数点切り捨て） — ゲーム内計算・表示で使用
    val effectiveHp: Int get() = hp.toInt()
    val effectiveStr: Int get() = str.toInt()
    val effectiveMag: Int get() = mag.toInt()
    val effectiveSkl: Int get() = skl.toInt()
    val effectiveSpd: Int get() = spd.toInt()
    val effectiveLck: Int get() = lck.toInt()
    val effectiveDef: Int get() = def.toInt()
    val effectiveRes: Int get() = res.toInt()
}
```

### GrowthRate data class

```kotlin
data class GrowthRate(
    val hp: Float = 0f,
    val str: Float = 0f,
    val mag: Float = 0f,
    val skl: Float = 0f,
    val spd: Float = 0f,
    val lck: Float = 0f,
    val def: Float = 0f,
    val res: Float = 0f
)
```

旧: 0〜100 のパーセンテージ → **新: レベルアップごとの固定加算値（Float）**

> 注記: 現行仕様では、ロード時に GrowthRate を百分率として再解釈（100 で割る）しない。

### StatGrowth data class（新規）

レベルアップ時の **実効値の変化量** を表す。UI 表示用。

```kotlin
data class StatGrowth(
    val hp: Int = 0,
    val str: Int = 0,
    val mag: Int = 0,
    val skl: Int = 0,
    val spd: Int = 0,
    val lck: Int = 0,
    val def: Int = 0,
    val res: Int = 0
)
```

---

## レベルアップ処理の変更

### 旧ロジック（確率ベース）

```kotlin
private fun applyLevelUp(): Stats {
    val growth = Stats()
    if (rollGrowth(growthRate.hp)) { stats.hp++; growth.hp = 1 }
    // ...（各ステータスで確率判定）
    return growth
}
```

### 新ロジック（確定加算）

```kotlin
private fun applyLevelUp(): StatGrowth {
    val beforeHp = stats.effectiveHp
    val beforeStr = stats.effectiveStr
    // ... 全ステータスの実効値を記録

    // 成長値を加算（確定）
    stats.hp += growthRate.hp
    stats.str += growthRate.str
    // ... 全ステータスに加算

    // 実効値の差分を返す（UI表示用）
    val growth = StatGrowth(
        hp = stats.effectiveHp - beforeHp,
        str = stats.effectiveStr - beforeStr,
        // ...
    )

    // HP成長分だけ currentHp も加算
    currentHp += growth.hp
    return growth
}
```

---

## 影響範囲

### 計算式（DamageCalc）

全計算で `stats.xxx` → `stats.effectiveXxx` に置換:

- 物理ダメージ: `stats.effectiveStr + 武器威力 - (stats.effectiveDef + 防具DEF)`
- 魔法ダメージ: `stats.effectiveMag + 魔法威力 - (stats.effectiveRes + 防具RES)`
- 命中率: `武器命中 + stats.effectiveSkl × 2 + stats.effectiveLck / 2`
- 回避: `effectiveSpeed() × 2 + stats.effectiveLck / 2`
- 必殺率: `武器必殺 + stats.effectiveSkl / 2`
- 必殺回避: `stats.effectiveLck / 2`
- 回復量: `stats.effectiveMag + healPower`

### GameUnit

- `maxHp` → `stats.effectiveHp`
- `currentHp` 初期値 → `stats.effectiveHp`
- `effectiveSpeed()` → `stats.effectiveSpd` を使用

### CTベースターン管理

- `effectiveSpeed()` を通じて間接的に対応（`stats.effectiveSpd` を使用）

### セーブ/ロード

- Stats の各フィールドを **Float** で保存・復元
- GrowthRate の各フィールドを **Float** で保存・復元

### JSON データ

- `units.json`: `growthRates` フィールドを Float 値に変更
- `chapter_*.json`: 敵の `stats` は整数値のまま（Float でパースされる）
- `generateStatsForLevel`: 確定成長に変更

---

## 成長シミュレーション例

### アレス（HP: 初期 20.0, 成長値 0.70）

| Lv | 内部HP | 実効HP | SPD内部 | 実効SPD |
|----|--------|--------|---------|---------|
| 1 | 20.00 | 20 | 8.00 | 8 |
| 2 | 20.70 | 20 | 8.20 | 8 |
| 3 | 21.40 | 21 | 8.40 | 8 |
| 4 | 22.10 | 22 | 8.60 | 8 |
| 5 | 22.80 | 22 | 8.80 | 8 |
| 6 | 23.50 | 23 | 9.00 | 9 |
| 7 | 24.20 | 24 | 9.20 | 9 |
| 10 | 26.30 | 26 | 9.80 | 9 |
| 11 | 27.00 | 27 | 10.00 | 10 |
| 20 | 33.30 | 33 | 11.80 | 11 |

SPD は5レベルごとに +1 される設計が確認できる。
