# 04. 武器・アイテム・防具仕様

## 武器タイプ (WeaponType)

| 値 | 表示名 | 三すくみ | 備考 |
|-----|-------|---------|------|
| `SWORD` | 剣 | 斧に有利 / 槍に不利 | |
| `LANCE` | 槍 | 剣に有利 / 斧に不利 | |
| `AXE` | 斧 | 槍に有利 / 剣に不利 | |
| `BOW` | 弓 | 中立 | 射程2固定、飛行特効（未実装） |
| `MAGIC` | 魔法 | 中立 | MAGベースでRES軽減 |
| `STAFF` | 杖 | 中立 | 回復用（未実装） |

## 武器三すくみ

`WeaponTriangle.getAdvantage()` で判定。

```
剣（SWORD） → 斧（AXE） → 槍（LANCE） → 剣（SWORD）
```

| 関係 | ダメージ補正 | 命中補正 |
|------|------------|---------|
| 有利 (WIN) | +1 | +15 |
| 不利 (LOSE) | -1 | -15 |
| 中立 (NEUTRAL) | 0 | 0 |

> 弓・魔法・杖は三すくみの対象外（常に NEUTRAL）。

## Weapon data class

| プロパティ | 型 | デフォルト | 説明 |
|-----------|-----|-----------|------|
| `id` | String | - | 武器固有ID |
| `name` | String | - | 表示名 |
| `type` | WeaponType | - | 武器種別 |
| `might` | Int | - | 威力 |
| `hit` | Int | - | 基本命中率 |
| `critical` | Int | 0 | 基本必殺率 |
| `weight` | Int | - | 重さ（攻速に影響） |
| `minRange` | Int | 1 | 最小射程 |
| `maxRange` | Int | 1 | 最大射程 |
| `durability` | Int | -1 | 耐久値（-1 = 無限） |
| `currentDurability` | Int | durability | 残り耐久 |

### メソッド

| メソッド | 説明 |
|---------|------|
| `isUsable()` | 耐久残りありまたは耐久無限なら `true` |
| `use()` | 使用時に `currentDurability` を -1（耐久無限時は何もしない） |

## テスト用武器データ

| 名前 | 種別 | 威力 | 命中 | 必殺 | 重さ | 射程 | 耐久 |
|------|------|------|------|------|------|------|------|
| 鉄の剣 | SWORD | 5 | 90 | 0 | 3 | 1 | 46 |
| 鉄の槍 | LANCE | 7 | 80 | 0 | 5 | 1 | 45 |
| 鉄の斧 | AXE | 8 | 75 | 0 | 6 | 1 | 40 |
| 鉄の弓 | BOW | 6 | 85 | 0 | 3 | 2-2 | 45 |

## 攻速（実効速度）の計算

```
実効速度(effectiveSpeed) = SPD - 武器の重さ(weight) - 防具の重さ(weight)
```

- 武器未装備の場合: 武器の重さ = `UNARMED_WEIGHT`（0）
- 防具未装備の場合: 防具の重さ = 0
- 最低値は0（負の値にならない）
- 追撃条件で使用: `攻撃側の実効速度 - 防御側の実効速度 ≥ 5`
- 重い装備を持つと追撃を受けやすくなる
- CT蓄積にも使用: `CT += maxOf(1, effectiveSpeed)`

## 素手攻撃

武器を装備していないユニットでも攻撃可能。

| パラメータ | 値 | 定数名 |
|-----------|-----|--------|
| 威力 (might) | 0 | `GameConfig.UNARMED_MIGHT` |
| 命中 (hit) | 80 | `GameConfig.UNARMED_HIT` |
| 必殺 (critical) | 0 | `GameConfig.UNARMED_CRITICAL` |
| 重さ (weight) | 0 | `GameConfig.UNARMED_WEIGHT` |
| 射程 | 1-1 | (固定) |

- 素手攻撃は **射程1のみ**（隣接マスのみ攻撃・反撃可能）
- 武器三すくみの補正を受けない（素手vs武器、素手vs素手いずれも NEUTRAL）
- ダメージ計算: `STR + 0 - DEF = STR - DEF`（物理攻撃扱い）

## 防具 (Armor)

### 防具タイプ (ArmorType)

| 値 | 表示名 | 説明 |
|-----|-------|------|
| `LIGHT_ARMOR` | 軽装 | 低DEF・低重量。速度重視のユニット向け |
| `HEAVY_ARMOR` | 重装 | 高DEF・高重量。前衛タンク向け |
| `SHIELD` | 盾 | DEF+RESバランス型 |
| `MAGIC_ROBE` | 魔道服 | 高RES・低重量。魔法ユニット向け |
| `ACCESSORY` | 装飾品 | 特殊効果（速度ブースト等） |

### Armor data class

| プロパティ | 型 | 説明 |
|-----------|-----|------|
| `id` | String | 防具固有ID |
| `name` | String | 表示名 |
| `type` | ArmorType | 防具種別 |
| `defBonus` | Int | 防御力ボーナス（物理ダメージ軽減に加算） |
| `resBonus` | Int | 魔防ボーナス（魔法ダメージ軽減に加算） |
| `weight` | Int | 重さ（実効速度を低下させる。負値は速度ブースト） |

### 防具マスターデータ (armors.json)

| ID | 名前 | タイプ | DEF | RES | 重さ |
|----|------|--------|-----|-----|------|
| leatherArmor | 革の鎧 | LIGHT_ARMOR | +1 | 0 | 1 |
| chainMail | 鎖帷子 | LIGHT_ARMOR | +2 | 0 | 2 |
| ironArmor | 鉄の鎧 | HEAVY_ARMOR | +4 | 0 | 5 |
| steelArmor | 鋼の鎧 | HEAVY_ARMOR | +6 | 0 | 8 |
| ironShield | 鉄の盾 | SHIELD | +2 | +1 | 3 |
| steelShield | 鋼の盾 | SHIELD | +4 | +1 | 5 |
| magicRobe | 魔道服 | MAGIC_ROBE | 0 | +3 | 1 |
| sageRobe | 賢者のローブ | MAGIC_ROBE | +1 | +5 | 2 |
| speedRing | 疾風の指輪 | ACCESSORY | 0 | 0 | -2 |
| guardCharm | 守りのお守り | ACCESSORY | +1 | +1 | 0 |

### ダメージ計算への影響

- **物理攻撃**: ダメージ = STR + 武器威力 - (DEF + 防具defBonus + 地形DEF)
- **魔法攻撃**: ダメージ = MAG + 武器威力 - (RES + 防具resBonus + 地形RES)

## 未実装の項目

- [ ] 杖による回復行動
- [ ] 飛行特効（弓ダメージ倍増）
- [ ] 武器ランク制限
- [ ] 武器の売買・入手
- [ ] 鋼/銀などの上位武器
- [ ] 防具装備変更UI
- [ ] 防具の売買・入手
- [ ] 防具のドロップ獲得
