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
        GUARD
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
