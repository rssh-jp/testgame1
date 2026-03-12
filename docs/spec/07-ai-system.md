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
| `HEAL` | HPが減った味方を優先回復。杖未装備時はSUPPORTにフォールバック | ENEMY（ヒーラー自動検出）、PLAYER（作戦「味方を回復しろ」） |

## AISystem クラス

### 主要メソッド

#### `decideAction(unit, battleMap, pattern): AIAction`

- ユニットの `AIPattern` に基づいて行動を決定する
- `pathFinder.getMovablePositions()` で移動可能マスを取得し、各パターンの専用メソッドに委譲
- 結果は `AIAction`（unit + Action sealed class）で返す

#### `decideAggressiveAction(unit, unitPos, movablePositions, battleMap): AIAction`

1. `findAttackableTargets()` で移動後に攻撃可能な敵リストを取得
2. 攻撃可能な場合 → **予測ダメージが最大**の敵を対象に `MoveAndAttack`
3. 攻撃不可の場合 → 最も近い敵座標を取得し、**`pathFinder.buildCostMapFrom()` で逆方向コストマップを構築**
4. 移動可能マスの中で**パスコストが最小**の位置へ `Move`
5. **フォールバック**: 敵座標に直接到達不可（コストマップにエントリなし）の場合:
   - `getAttackPositionsFor()` で敵の武器射程内の立てる位置を算出
   - `buildCostMapFromMultiple()` で複数攻撃位置からのコストマップを構築
   - 移動可能マスの中でコスト最小の位置へ `Move`

#### `decideDefensiveAction(unit, unitPos, movablePositions, battleMap): AIAction`

1. 現在位置のみ（`setOf(unitPos)`）を対象に `findAttackableTargets()` を呼び出す
2. 射程内に敵がいる → 最大ダメージの敵を `MoveAndAttack`
3. 射程内に敵がいない → `Wait`

#### `decideCautiousAction(unit, unitPos, movablePositions, battleMap): AIAction`

1. `calculateThreatZone()` で敵全体の脅威圏を計算
2. 脅威圏外の安全な移動可能マスをフィルタ
3. 安全圏から攻撃可能な敵がいれば → 最大ダメージで `MoveAndAttack`
4. 安全に攻撃不可 → **`buildCostMapFrom()` で最も近い敵へのコストマップを構築**、安全圏内でパスコスト最小の位置へ `Move`
5. **フォールバック**: 敵座標に直接到達不可の場合:
   - `getAttackPositionsFor()` で敵の武器射程内の立てる位置を算出
   - `buildCostMapFromMultiple()` で複数攻撃位置からのコストマップを構築
   - **安全圏内**のマスでコスト最小の位置へ `Move`
6. 安全な場所がない → `Wait`

#### `decideSupportAction(unit, unitPos, movablePositions, battleMap): AIAction`

1. `findEnemiesNearAllies()` で味方が交戦中の敵を特定
2. 交戦中の敵を優先して `findAttackableTargets()` から選択 → `MoveAndAttack`
3. 攻撃不可の場合 → `findAllyNearestToEnemy()` で最前線の味方座標を取得
4. **`buildCostMapFrom()` で味方座標へのコストマップを構築**、パスコスト最小の位置へ `Move`
5. **フォールバック**: 味方座標に直接到達不可の場合:
   - 味方の**隣接マス**（射程1）から `isPassableFor()` で立てる位置をフィルタ
   - `buildCostMapFromMultiple()` で隣接位置群からのコストマップを構築
   - 移動可能マスの中でコスト最小の位置へ `Move`

#### `decideFleeAction(unit, unitPos, movablePositions, battleMap): AIAction`

1. 全敵ユニットの座標を取得
2. 各移動可能マスについて「最も近い敵までのマンハッタン距離」を計算
3. その値が最大のマスへ `Move`（攻撃は行わない）

#### `decideHealAction(unit, unitPos, movablePositions, battleMap): AIAction`

1. 回復杖未装備 → `decideSupportAction()` にフォールバック
2. `calculateThreatZone()` で安全/危険マスを分類
3. `findHealableTargets()` で回復可能な味方を探索
4. 自己HP ≤ 1/3 の場合は自己回復を優先
5. 回復対象がいれば → 安全優先でソートし `MoveAndHeal`
6. 射程外 → **`buildCostMapFrom()` で負傷味方へのコストマップ構築**、パスコスト最小の位置へ `Move`
   - **フォールバック**: 負傷味方に直接到達不可の場合:
     - 味方の**杖射程内**の位置から `isPassableFor()` で立てる位置をフィルタ
     - `buildCostMapFromMultiple()` で回復可能位置群からのコストマップを構築
     - パスコスト最小 + 安全優先で位置を選択し `Move`
7. 全員満タン → 最も近い味方に寄り添う

### 内部メソッド

| メソッド | 説明 |
|---------|------|
| `findAttackableTargets(unit, unitPos, movablePositions, battleMap)` | 移動後に攻撃可能な (移動先, 予測ダメージ, ターゲット) のリストを返す |
| `findNearestEnemy(from, faction, battleMap)` | マンハッタン距離が最小の敵座標を返す |
| `findHealableTargets(unit, unitPos, movablePositions, battleMap)` | 移動後に回復可能な (移動先, 距離, 回復対象) のリストを返す |
| `findMostInjuredAlly(unit, battleMap)` | HP割合が最低の味方座標を返す |
| `findNearestFriendly(unit, unitPos, battleMap)` | 自分以外で最も近い味方座標を返す |
| `findEnemiesNearAllies(unit, battleMap)` | 味方の武器射程内にいる敵ユニットリストを返す |
| `findAllyNearestToEnemy(unit, battleMap)` | 敵に最も近い味方の座標を返す |
| `calculateThreatZone(myFaction, battleMap)` | 敵全体の移動+攻撃可能マスの集合を返す |
| `getAttackPositionsFor(enemyPos, unit, battleMap)` | 敵座標から武器射程内の立てる位置（`isPassableFor` でフィルタ）を返す |
| `getPositionsAtRange(center, range)` | 指定マンハッタン距離の座標群を返す |
| `isHostileFaction(from, to)` | 敵対関係の判定 |
| `isFriendlyFaction(from, to)` | 友好関係の判定 |

## AIAction / Action

```kotlin
data class AIAction(
    val unit: GameUnit,   // 行動ユニット
    val action: Action    // 行動内容
)

sealed class Action {
    data class MoveAndAttack(val moveTo: Position, val target: GameUnit) : Action()
    data class MoveAndHeal(val moveTo: Position, val target: GameUnit) : Action()
    data class Move(val moveTo: Position) : Action()
    data object Wait : Action()
}
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
| 2. **最大ダメージ** | `DamageCalc.calculateForecast()` の予測ダメージが最大の敵を優先 |
| 3. **パスコスト最小** | 攻撃不可の場合、`buildCostMapFrom()` によるパスコストで最も近いマスへ移動 |

## 接近先選定のアルゴリズム（パスコスト基準）

旧実装ではマンハッタン距離で最寄りの敵・味方を選定していたが、
地形コストを無視するため壁や水域を挟んで「近い」と誤判定する問題があった。

### 改善後のフロー（AGGRESSIVE / CAUTIOUS / SUPPORT / HEAL 共通）

```
1. 接近対象の座標（敵 or 味方）を特定
2. PathFinder.buildCostMapFrom(unit, 対象座標, battleMap)
   → 対象座標を起点に逆方向ダイクストラでコストマップを一括構築
3. 移動可能マスの中で costMap[pos] が最小の位置を選択
4. costMap に含まれない位置（到達不可）は候補から除外
```

### フォールバック接近（到達不可時）

対象座標に直接到達できない場合（水域・壁で分断されている等）、武器射程内の攻撃/回復可能位置へ迂回して接近する。

```
1. 対象座標からのコストマップで移動可能マスに到達不可を検出
2. getAttackPositionsFor() or getPositionsAtRange() で
   対象の武器射程内（SUPPORTは隣接1マス、HEALは杖射程）の立てる位置を算出
   → isPassableFor() で地形通行可否をフィルタ
3. PathFinder.buildCostMapFromMultiple(unit, 候補位置リスト, battleMap)
   → 複数起点の multi-source ダイクストラでコストマップを一括構築
4. 移動可能マスの中で altCostMap[pos] が最小の位置を選択
5. CAUTIOUSは安全圏内マスに限定、HEALは安全優先ソート
```

- 計算量: $O(N)$（$N$ = バウンディングボックス内タイル数）でコストマップ構築 + $O(M)$（$M$ = 移動可能マス数）のルックアップ
- 1回の `buildCostMapFrom()` / `buildCostMapFromMultiple()` で全候補マスのコストが得られるため、マスごとに経路探索を繰り返す必要がない

## PathFinder との連携

| PathFinder メソッド | AI での用途 |
|---|---|
| `getMovablePositions(unit, pos, battleMap)` | 移動可能マスの取得（全パターン共通） |
| `buildCostMapFrom(unit, origin, battleMap)` | 接近先選定コストマップ（AGGRESSIVE/CAUTIOUS/SUPPORT/HEAL） |
| `buildCostMapFromMultiple(unit, origins, battleMap)` | 到達不可時のフォールバック接近コストマップ（複数起点 multi-source ダイクストラ） |
| `isPassableFor(unit, pos, battleMap)` | 攻撃/回復可能位置の地形通行可否フィルタ |
| `findPath(unit, start, goal, battleMap)` | 移動アニメーション用の経路取得（BattleScreen側） |

> `getPathCostTo()` はテスト用途に存在するが、AI側は全て `buildCostMapFrom()` / `buildCostMapFromMultiple()` に移行済み。

## 注意事項・制限

- AI は 1 武器のみを考慮（`weapons.firstOrNull()` で取得）— 素手時は射程1で攻撃可能
- 地形コスト計算には `PathFinder.getMovablePositions()` を使用
- 同盟ユニットの AI パターンは `DEFENSIVE` 固定（BattleScreen側で制御）
- AIパターンはマップデータの JSON でユニットごとに指定可能
- `isHealingStaff` 装備の敵ユニットは自動で `HEAL` パターンが適用される
- 経路が見つからない場合、移動・攻撃・回復をキャンセルして待機（テレポート防止）

## 未実装の項目

- [ ] 回復アイテムの使用 AI
- [ ] 複数武器の切り替え判断
- [ ] 連携・集中攻撃の AI 協調行動
- [ ] ターゲットの脅威度に基づく優先度付け
