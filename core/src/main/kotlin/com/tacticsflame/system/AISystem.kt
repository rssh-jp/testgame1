package com.tacticsflame.system

import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.map.Position
import com.tacticsflame.model.unit.Faction
import com.tacticsflame.model.unit.GameUnit

/**
 * 敵AIの行動を決定するシステム
 */
class AISystem(
    private val pathFinder: PathFinder,
    private val battleSystem: BattleSystem
) {

    /**
     * AIの行動パターン
     */
    enum class AIPattern {
        /** 積極的に攻撃する */
        AGGRESSIVE,
        /** その場で待機し、範囲内に入った敵を攻撃 */
        DEFENSIVE,
        /** 特定の地点を守る */
        GUARD,
        /** 敵の攻撃圏外から攻撃を狙う（後の先） */
        CAUTIOUS,
        /** 味方が狙っている敵を優先して攻撃する（援護） */
        SUPPORT,
        /** 敵から逃げるように移動する（撤退） */
        FLEE
    }

    /**
     * AIユニットの行動を決定する
     *
     * @property unit 行動ユニット
     * @property action 行動内容
     */
    data class AIAction(
        val unit: GameUnit,
        val action: Action
    )

    /**
     * 行動内容
     */
    sealed class Action {
        /** 移動して攻撃 */
        data class MoveAndAttack(val moveTo: Position, val target: GameUnit) : Action()
        /** 移動のみ */
        data class Move(val moveTo: Position) : Action()
        /** 待機 */
        data object Wait : Action()
    }

    /**
     * 敵ユニットの行動を決定する
     *
     * @param unit 敵ユニット
     * @param battleMap バトルマップ
     * @param pattern AIパターン
     * @return 決定された行動
     */
    fun decideAction(unit: GameUnit, battleMap: BattleMap, pattern: AIPattern = AIPattern.AGGRESSIVE): AIAction {
        val unitPos = battleMap.getUnitPosition(unit)
            ?: return AIAction(unit, Action.Wait)

        val movablePositions = pathFinder.getMovablePositions(unit, unitPos, battleMap)

        return when (pattern) {
            AIPattern.AGGRESSIVE -> decideAggressiveAction(unit, unitPos, movablePositions, battleMap)
            AIPattern.DEFENSIVE -> decideDefensiveAction(unit, unitPos, movablePositions, battleMap)
            AIPattern.GUARD -> AIAction(unit, Action.Wait)
            AIPattern.CAUTIOUS -> decideCautiousAction(unit, unitPos, movablePositions, battleMap)
            AIPattern.SUPPORT -> decideSupportAction(unit, unitPos, movablePositions, battleMap)
            AIPattern.FLEE -> decideFleeAction(unit, unitPos, movablePositions, battleMap)
        }
    }

    /**
     * 積極的AIの行動を決定する
     * 最も近いプレイヤーユニットに向かって移動・攻撃する
     */
    private fun decideAggressiveAction(
        unit: GameUnit,
        unitPos: Position,
        movablePositions: Set<Position>,
        battleMap: BattleMap
    ): AIAction {
        val weapon = unit.equippedWeapon()

        // 攻撃可能な敵を探す
        if (weapon != null) {
            val targets = findAttackableTargets(unit, unitPos, movablePositions, battleMap)
            if (targets.isNotEmpty()) {
                // 最もダメージを与えられる対象を選択
                val bestTarget = targets.maxByOrNull { it.second }
                if (bestTarget != null) {
                    return AIAction(unit, Action.MoveAndAttack(bestTarget.first, bestTarget.third))
                }
            }
        }

        // 攻撃できない場合、最も近い敵に接近
        val nearestEnemy = findNearestEnemy(unitPos, unit.faction, battleMap)
        if (nearestEnemy != null) {
            val bestMovePos = movablePositions.minByOrNull { it.manhattanDistance(nearestEnemy) }
            if (bestMovePos != null) {
                return AIAction(unit, Action.Move(bestMovePos))
            }
        }

        return AIAction(unit, Action.Wait)
    }

    /**
     * 防御的AIの行動を決定する
     * 攻撃範囲内の敵のみ攻撃する
     */
    private fun decideDefensiveAction(
        unit: GameUnit,
        unitPos: Position,
        movablePositions: Set<Position>,
        battleMap: BattleMap
    ): AIAction {
        val weapon = unit.equippedWeapon() ?: return AIAction(unit, Action.Wait)

        // 現在位置から攻撃可能な敵のみ対象
        val targets = findAttackableTargets(unit, unitPos, setOf(unitPos), battleMap)
        if (targets.isNotEmpty()) {
            val bestTarget = targets.maxByOrNull { it.second }
            if (bestTarget != null) {
                return AIAction(unit, Action.MoveAndAttack(bestTarget.first, bestTarget.third))
            }
        }

        return AIAction(unit, Action.Wait)
    }

    // ==================== 新AIパターン実装 ====================

    /**
     * 慎重なAIの行動を決定する（後の先を狙え）
     *
     * 敵の攻撃圏外に留まりつつ、自ユニットの攻撃が届く位置へ移動して攻撃する。
     * 安全に攻撃できる位置がない場合は、敵の攻撃圏外で最も敵に近い位置へ移動する。
     *
     * @param unit 行動ユニット
     * @param unitPos 現在位置
     * @param movablePositions 移動可能座標
     * @param battleMap バトルマップ
     * @return 決定された行動
     */
    private fun decideCautiousAction(
        unit: GameUnit,
        unitPos: Position,
        movablePositions: Set<Position>,
        battleMap: BattleMap
    ): AIAction {
        // 敵全体の脅威圏（敵が移動+攻撃できる全マス）を計算
        val threatZone = calculateThreatZone(unit.faction, battleMap)

        // 安全な移動可能マス（脅威圏外）
        val safePositions = (movablePositions + unitPos).filter { it !in threatZone }.toSet()

        val weapon = unit.equippedWeapon()

        // 安全な位置から攻撃可能な敵を探す
        // 注意: findAttackableTargets は内部で unitPos を自動追加するため、
        // 結果を safePositions でフィルタして安全な位置からの攻撃のみに絞る
        if (weapon != null && safePositions.isNotEmpty()) {
            val allTargets = findAttackableTargets(unit, unitPos, movablePositions, battleMap)
            val safeTargets = allTargets.filter { (movePos, _, _) -> movePos in safePositions }
            if (safeTargets.isNotEmpty()) {
                // 安全な位置から最大ダメージを与えられる対象を選択
                val bestTarget = safeTargets.maxByOrNull { it.second }
                if (bestTarget != null) {
                    return AIAction(unit, Action.MoveAndAttack(bestTarget.first, bestTarget.third))
                }
            }
        }

        // 安全に攻撃できない場合、安全圏で最も敵に近い位置へ移動
        if (safePositions.isNotEmpty()) {
            val nearestEnemy = findNearestEnemy(unitPos, unit.faction, battleMap)
            if (nearestEnemy != null) {
                val bestSafePos = safePositions.minByOrNull { it.manhattanDistance(nearestEnemy) }
                if (bestSafePos != null && bestSafePos != unitPos) {
                    return AIAction(unit, Action.Move(bestSafePos))
                }
            }
        }

        // 安全な場所がない場合は待機（現在位置を維持）
        return AIAction(unit, Action.Wait)
    }

    /**
     * 援護AIの行動を決定する（味方を援護しろ）
     *
     * 味方ユニットが攻撃圏内に捉えている敵を優先的に攻撃する。
     * そのような敵がいない場合は、味方の近くに移動して連携攻撃を狙う。
     *
     * @param unit 行動ユニット
     * @param unitPos 現在位置
     * @param movablePositions 移動可能座標
     * @param battleMap バトルマップ
     * @return 決定された行動
     */
    private fun decideSupportAction(
        unit: GameUnit,
        unitPos: Position,
        movablePositions: Set<Position>,
        battleMap: BattleMap
    ): AIAction {
        val weapon = unit.equippedWeapon()

        if (weapon != null) {
            // 味方が隣接している敵を特定
            val enemiesNearAllies = findEnemiesNearAllies(unit, battleMap)

            // 移動可能範囲から攻撃可能な全ターゲットを取得
            val allTargets = findAttackableTargets(unit, unitPos, movablePositions, battleMap)

            if (allTargets.isNotEmpty()) {
                // 味方が隣接している敵を優先
                val supportTargets = allTargets.filter { target ->
                    enemiesNearAllies.any { it.id == target.third.id }
                }

                val bestTarget = if (supportTargets.isNotEmpty()) {
                    supportTargets.maxByOrNull { it.second }
                } else {
                    // 連携対象がなければ通常の最大ダメージターゲット
                    allTargets.maxByOrNull { it.second }
                }

                if (bestTarget != null) {
                    return AIAction(unit, Action.MoveAndAttack(bestTarget.first, bestTarget.third))
                }
            }
        }

        // 攻撃できない場合、味方で最も敵に近い仲間へ移動
        val nearestAllyToEnemy = findAllyNearestToEnemy(unit, battleMap)
        if (nearestAllyToEnemy != null) {
            val bestMovePos = movablePositions.minByOrNull {
                it.manhattanDistance(nearestAllyToEnemy)
            }
            if (bestMovePos != null) {
                return AIAction(unit, Action.Move(bestMovePos))
            }
        }

        // フォールバック: 味方も敵も見つからない場合は待機
        return AIAction(unit, Action.Wait)
    }

    /**
     * 逃走AIの行動を決定する（逃げまどえ）
     *
     * 全ての敵ユニットからできるだけ遠くに移動する。
     * 攻撃は行わない。
     *
     * @param unit 行動ユニット
     * @param unitPos 現在位置
     * @param movablePositions 移動可能座標
     * @param battleMap バトルマップ
     * @return 決定された行動
     */
    private fun decideFleeAction(
        unit: GameUnit,
        unitPos: Position,
        movablePositions: Set<Position>,
        battleMap: BattleMap
    ): AIAction {
        // 全敵ユニットの座標を取得
        val enemyPositions = battleMap.getAllUnits()
            .filter { isHostileFaction(unit.faction, it.second.faction) && !it.second.isDefeated }
            .map { it.first }

        if (enemyPositions.isEmpty()) {
            return AIAction(unit, Action.Wait)
        }

        // 各移動可能マスについて「最も近い敵までの距離」を計算し、それが最大のマスへ移動
        val candidates = (movablePositions + unitPos)
        val bestFleePos = candidates.maxByOrNull { pos ->
            enemyPositions.minOf { enemyPos -> pos.manhattanDistance(enemyPos) }
        }

        if (bestFleePos != null && bestFleePos != unitPos) {
            return AIAction(unit, Action.Move(bestFleePos))
        }

        return AIAction(unit, Action.Wait)
    }

    // ==================== ヘルパーメソッド ====================

    /**
     * 敵陣営の脅威圏（移動+攻撃可能な全マス）を計算する
     *
     * 全ての敵ユニットについて、移動可能範囲 + 武器射程のマスを集約する。
     *
     * @param myFaction 自ユニットの陣営（この陣営に対して敵対する陣営を計算）
     * @param battleMap バトルマップ
     * @return 脅威圏の座標セット
     */
    private fun calculateThreatZone(myFaction: Faction, battleMap: BattleMap): Set<Position> {
        val threatZone = mutableSetOf<Position>()

        for ((pos, enemy) in battleMap.getAllUnits()) {
            if (!isHostileFaction(myFaction, enemy.faction) || enemy.isDefeated) continue

            val weapon = enemy.equippedWeapon() ?: continue
            val enemyMovable = pathFinder.getMovablePositions(enemy, pos, battleMap)
            val allPositions = enemyMovable + pos

            for (movePos in allPositions) {
                for (range in weapon.minRange..weapon.maxRange) {
                    for (attackPos in getPositionsAtRange(movePos, range)) {
                        if (battleMap.isInBounds(attackPos.x, attackPos.y)) {
                            threatZone.add(attackPos)
                        }
                    }
                }
            }
        }

        return threatZone
    }

    /**
     * 味方ユニットが隣接している敵ユニット一覧を返す
     *
     * 「隣接」は味方の武器射程内にいる敵、または敵の武器射程内にいる味方がいる敵を指す。
     *
     * @param unit 自ユニット（これ以外の味方を対象に判定）
     * @param battleMap バトルマップ
     * @return 味方が交戦中の敵ユニットリスト
     */
    private fun findEnemiesNearAllies(unit: GameUnit, battleMap: BattleMap): List<GameUnit> {
        val allUnits = battleMap.getAllUnits()
        val allies = allUnits.filter {
            it.second.faction == unit.faction && it.second.id != unit.id && !it.second.isDefeated
        }
        val enemies = allUnits.filter {
            isHostileFaction(unit.faction, it.second.faction) && !it.second.isDefeated
        }

        val engagedEnemies = mutableSetOf<GameUnit>()
        for ((allyPos, ally) in allies) {
            val allyWeapon = ally.equippedWeapon() ?: continue
            for ((enemyPos, enemy) in enemies) {
                val dist = allyPos.manhattanDistance(enemyPos)
                if (dist in allyWeapon.minRange..allyWeapon.maxRange) {
                    engagedEnemies.add(enemy)
                }
            }
        }

        return engagedEnemies.toList()
    }

    /**
     * 味方の中で最も敵に近い仲間の座標を返す
     *
     * @param unit 自ユニット（除外）
     * @param battleMap バトルマップ
     * @return 味方の座標（味方がいない場合は null）
     */
    private fun findAllyNearestToEnemy(unit: GameUnit, battleMap: BattleMap): Position? {
        val allUnits = battleMap.getAllUnits()
        val allies = allUnits.filter {
            it.second.faction == unit.faction && it.second.id != unit.id && !it.second.isDefeated
        }
        val enemies = allUnits.filter {
            isHostileFaction(unit.faction, it.second.faction) && !it.second.isDefeated
        }

        if (allies.isEmpty() || enemies.isEmpty()) return null

        // 各味方について「最も近い敵までの距離」を計算し、それが最小の味方を選択
        return allies.minByOrNull { (allyPos, _) ->
            enemies.minOf { (enemyPos, _) -> allyPos.manhattanDistance(enemyPos) }
        }?.first
    }

    /**
     * 攻撃可能なターゲットを探す
     *
     * @return (移動先, 予測ダメージ, ターゲットユニット) のリスト
     */
    private fun findAttackableTargets(
        unit: GameUnit,
        unitPos: Position,
        movablePositions: Set<Position>,
        battleMap: BattleMap
    ): List<Triple<Position, Int, GameUnit>> {
        val weapon = unit.equippedWeapon() ?: return emptyList()
        val results = mutableListOf<Triple<Position, Int, GameUnit>>()

        val positionsToCheck = movablePositions + unitPos

        for (pos in positionsToCheck) {
            for (range in weapon.minRange..weapon.maxRange) {
                for (neighbor in getPositionsAtRange(pos, range)) {
                    val target = battleMap.getUnitAt(neighbor)
                    if (target != null && isHostileFaction(unit.faction, target.faction) && !target.isDefeated) {
                        val tile = battleMap.getTile(pos)!!
                        val targetTile = battleMap.getTile(neighbor)!!
                        val forecast = com.tacticsflame.model.battle.DamageCalc.calculateForecast(
                            unit, target, tile, targetTile
                        )
                        results.add(Triple(pos, forecast.damage, target))
                    }
                }
            }
        }

        return results
    }

    /**
     * 最も近い敵ユニットの座標を返す
     */
    private fun findNearestEnemy(from: Position, faction: Faction, battleMap: BattleMap): Position? {
        return battleMap.getAllUnits()
            .filter { isHostileFaction(faction, it.second.faction) && !it.second.isDefeated }
            .minByOrNull { it.first.manhattanDistance(from) }
            ?.first
    }

    /**
     * 二つの陣営が敵対関係にあるかを判定する
     *
     * @param from 行動ユニットの陣営
     * @param to 対象ユニットの陣営
     * @return 敵対関係なら true
     */
    private fun isHostileFaction(from: Faction, to: Faction): Boolean {
        return when (from) {
            Faction.PLAYER -> to == Faction.ENEMY
            Faction.ENEMY -> to == Faction.PLAYER || to == Faction.ALLY
            Faction.ALLY -> to == Faction.ENEMY
        }
    }

    /**
     * 指定距離の座標群を取得
     */
    private fun getPositionsAtRange(center: Position, range: Int): List<Position> {
        val positions = mutableListOf<Position>()
        for (dx in -range..range) {
            val dy = range - Math.abs(dx)
            positions.add(Position(center.x + dx, center.y + dy))
            if (dy != 0) {
                positions.add(Position(center.x + dx, center.y - dy))
            }
        }
        return positions
    }
}
