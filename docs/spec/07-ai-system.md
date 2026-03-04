# 07. AIシステム仕様

## 概要

敵・同盟ユニットの自動行動を制御するシステム。
ユニットごとに割り当てられた `AIPattern` に基づき、移動先と攻撃対象を決定する。

## AIパターン

| パターン | 行動方針 | 使用陣営 |
|---------|----------|---------|
| `AGGRESSIVE` | 最も近い敵に向かって移動し、攻撃可能なら攻撃 | ENEMY（一般） |
| `DEFENSIVE` | 攻撃範囲に敵がいれば攻撃、いなければ待機 | ALLY、一部 ENEMY |
| `GUARD` | 移動せず定位置に留まる。攻撃範囲に来た敵のみ攻撃 | ENEMY（ボスなど） |
| `CAUTIOUS` | 敵の攻撃圏外に留まりつつ、安全な位置から攻撃を狙う | PLAYER（作戦「後の先を狙え」） |
| `SUPPORT` | 味方が攻撃圏に捉えている敵を優先ターゲット。味方の近くを維持 | PLAYER（作戦「味方を援護しろ」） |
| `FLEE` | 全敵から最大距離に逃走。攻撃は行わない | PLAYER（作戦「逃げまどえ」） |

## AISystem クラス

### 主要メソッド

#### `decideAction(unit, battleMap, allUnits): AIAction`

- ユニットのAIパターンに基づいて行動を決定する
- 結果は `AIAction`（移動先座標＋攻撃対象）で返す

#### `decideAggressive(unit, battleMap, allUnits): AIAction`

1. 対象の敵陣営を特定（ENEMY → PLAYERを狙う、ALLY → ENEMYを狙う）
2. `findTargets()` で生存している敵リストを取得
3. `PathFinder.findReachable()` で移動可能マスを計算
4. 移動可能マスから武器射程内に敵がいるか確認
5. 攻撃可能な場合 → 最もHPが低い敵を対象に選択
6. 攻撃不可の場合 → 最も近い敵に向かって移動のみ

#### `decideDefensive(unit, battleMap, allUnits): AIAction`

1. 対象の敵リストを取得
2. 現在位置から武器射程内に敵がいるか確認
3. 射程内に敵がいる → 最もHPが低い敵を攻撃（移動なし）
4. 射程内に敵がいない → 行動しない（待機）

#### `decideGuard(unit, battleMap, allUnits): AIAction`

1. 移動しない（現在位置に留まる）
2. 現在位置から武器射程内に敵がいれば攻撃
3. いなければ待機

### 内部メソッド

| メソッド | 説明 |
|---------|------|
| `findTargets(unit, allUnits)` | 対象陣営の生存ユニットリストを返す |
| `isInAttackRange(from, target, weapon)` | マンハッタン距離が武器射程内か判定 |
| `findClosestEnemy(unit, targets)` | マンハッタン距離が最小の敵を返す |
| `findMoveTowards(unit, target, reachable)` | 対象に最も近い移動可能マスを返す |

## AIAction データクラス

```kotlin
data class AIAction(
    val moveX: Int,       // 移動先 X
    val moveY: Int,       // 移動先 Y
    val target: GameUnit? // 攻撃対象（null = 攻撃しない）
)
```

## AI行動の実行フロー（BattleScreen側）

CTベースの個別ターン制では、各ユニットは陣営を問わず個別にCTが貯まり、行動順が回ってくる。
ENEMY・ ALLY ユニットのCTが閾値（100）に達したら、64行動が実行される。

### CTベースAI行動フロー

```
// CTが100以上に達したENEMYまたはALLYユニット 1体に対して:
1. AISystem.decideAction(unit, battleMap, allUnits) → AIAction取得
2. unit を moveX, moveY に移動
3. target != null → BattleSystem.executeBattle(unit, target, map)
4. unit.ct -= 100  // 余剰CTは次回に繰り越し
5. 勝敗判定 → 継続なら次のCT進行へ
```

### 敵味方判定（isHostileFaction）

| 行動ユニットの陣営 | 敵対陣営 |
|---------------------|------------|
| ENEMY | PLAYER, ALLY |
| ALLY | ENEMY |
| PLAYER | ENEMY |

## ターゲット選択の優先度

| 条件 | 説明 |
|------|------|
| 1. 攻撃可能 | 移動可能マス＋武器射程内にいる敵 |
| 2. HP最小 | 同条件の場合、HPが最も低い敵を優先 |
| 3. 距離最小 | 攻撃不可の場合、マンハッタン距離が近い敵に移動 |

## 注意事項・制限

- AI は 1 武器のみを考慮（`weapons.firstOrNull()` で取得）
- 武器未装備のユニットは常に待機のみ
- 地形コスト計算には `PathFinder.findReachable()` を使用
- 同盟ユニットの AI パターンは `DEFENSIVE` 固定（BattleScreen側で制御）
- AIパターンはマップデータの JSON でユニットごとに指定可能

## 未実装の項目

- [ ] AI に「撤退」行動を追加（HP 低下時の後退）
- [ ] 回復アイテムの使用 AI
- [ ] 複数武器の切り替え判断
- [ ] 連携・集中攻撃の AI 協調行動
- [ ] ターゲットの脅威度に基づく優先度付け
