# 02. マップ・地形仕様

## グリッドシステム

- 正方形タイルによるマス目ベースのマップ
- タイルサイズ: **64px**（`GameConfig.TILE_SIZE`）
- マップサイズ: 最小 10×10 ～ 最大 20×20
- 現在のテストマップ: **15×10**

## 地形タイプ

`TerrainType` enum で定義。全 8 種。

| 地形 | ID | 移動コスト | 回避補正 | 防御補正 | HP回復 | 通行可否 |
|------|-----|-----------|---------|---------|--------|---------|
| 平地 | `PLAIN` | 1 | 0 | 0 | なし | 可 |
| 森 | `FOREST` | 2 | +20 | +1 | なし | 可 |
| 山 | `MOUNTAIN` | 3 | +30 | +2 | なし | 可 |
| 砦 | `FORT` | 1 | +20 | +3 | あり | 可 |
| 水域 | `WATER` | -1（不可） | - | - | - | 不可 |
| 壁 | `WALL` | -1（不可） | - | - | - | 不可 |
| 村 | `VILLAGE` | 1 | +10 | +1 | なし | 可 |
| 橋 | `BRIDGE` | 1 | 0 | 0 | なし | 可 |

### 移動タイプごとの地形コスト

`PathFinder.getMoveCost()` で計算。

| 移動タイプ | 平地 | 森 | 山 | 砦 | 水域 | 壁 | 村 | 橋 |
|-----------|------|-----|-----|-----|------|-----|-----|-----|
| 歩兵 (INFANTRY) | 1 | 2 | 3 | 1 | 不可 | 不可 | 1 | 1 |
| 騎馬 (CAVALRY) | 1 | 3 | 不可 | 1 | 不可 | 不可 | 1 | 1 |
| 飛行 (FLYING) | 1 | 1 | 1 | 1 | 1 | 不可 | 1 | 1 |
| 重装 (ARMORED) | 1 | 2 | 4 | 1 | 不可 | 不可 | 1 | 1 |

> 飛行ユニットは壁以外すべてコスト 1 で通過可能。

## BattleMap クラス

| メソッド | 説明 |
|---------|------|
| `getTile(x, y)` / `getTile(pos)` | 指定座標のタイルを取得 |
| `isInBounds(x, y)` | 座標がマップ範囲内か判定 |
| `placeUnit(unit, pos)` | ユニットを配置 |
| `moveUnit(from, to)` | ユニットを移動 |
| `getUnitAt(pos)` | 座標上のユニットを取得 |
| `removeUnit(pos)` | ユニットを除去 |
| `getUnitPosition(unit)` | ユニットの座標を逆引き |
| `getAllUnits()` | 全ユニット（座標, ユニット）のリスト |

## マップデータ形式（JSON）

```json
{
  "id": "chapter_1",
  "name": "最初の戦い",
  "width": 15,
  "height": 10,
  "terrain": [[0, 0, 1, ...], ...],
  "playerSpawns": [{"x": 1, "y": 1}],
  "enemies": [{"classId": "swordFighter", "level": 1, "x": 10, "y": 5, "ai": "aggressive"}],
  "victoryCondition": {"type": "defeatAll"},
  "defeatCondition": {"type": "lordDefeated"}
}
```

> **注記**: 現在はハードコードの `createTestMap()` でマップを生成している。JSONローダーは未実装。
