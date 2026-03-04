package com.tacticsflame.system

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.battle.AttackResult
import com.tacticsflame.model.battle.BattleResult
import com.tacticsflame.model.battle.DamageCalc
import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.map.Position
import com.tacticsflame.model.unit.GameUnit

/**
 * 戦闘処理を実行するシステム
 */
class BattleSystem {

    /**
     * 2ユニット間の戦闘を実行する
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @param battleMap バトルマップ
     * @return 戦闘結果
     */
    fun executeBattle(
        attacker: GameUnit,
        defender: GameUnit,
        battleMap: BattleMap
    ): BattleResult {
        val attackerPos = battleMap.getUnitPosition(attacker)
            ?: error("攻撃ユニットがマップ上に存在しません: ${attacker.name}")
        val defenderPos = battleMap.getUnitPosition(defender)
            ?: error("防御ユニットがマップ上に存在しません: ${defender.name}")

        val attackerTile = battleMap.getTile(attackerPos)!!
        val defenderTile = battleMap.getTile(defenderPos)!!

        val attacks = mutableListOf<AttackResult>()

        // 攻撃側の戦闘予測
        val attackerForecast = DamageCalc.calculateForecast(
            attacker, defender, attackerTile, defenderTile
        )

        // 攻撃側の攻撃
        attacks.add(resolveAttack(attackerForecast, isInitiator = true))
        applyAttackResult(attacks.last(), attacker, defender)

        // 防御側が生存していれば反撃
        if (!defender.isDefeated && canCounterAttack(defender, defenderPos, attackerPos)) {
            val defenderForecast = DamageCalc.calculateForecast(
                defender, attacker, defenderTile, attackerTile
            )
            attacks.add(resolveAttack(defenderForecast, isInitiator = false))
            applyAttackResult(attacks.last(), defender, attacker)
        }

        // 追撃（攻撃側）
        if (!attacker.isDefeated && !defender.isDefeated && attackerForecast.canDoubleAttack) {
            attacks.add(resolveAttack(attackerForecast, isInitiator = true))
            applyAttackResult(attacks.last(), attacker, defender)
        }

        // 追撃（防御側）
        if (!attacker.isDefeated && !defender.isDefeated && canCounterAttack(defender, defenderPos, attackerPos)) {
            val defenderForecast = DamageCalc.calculateForecast(
                defender, attacker, defenderTile, attackerTile
            )
            if (defenderForecast.canDoubleAttack) {
                attacks.add(resolveAttack(defenderForecast, isInitiator = false))
                applyAttackResult(attacks.last(), defender, attacker)
            }
        }

        // 経験値計算
        val expGained = calculateExp(attacker, defender)

        return BattleResult(
            attacker = attacker,
            defender = defender,
            attacks = attacks,
            attackerDefeated = attacker.isDefeated,
            defenderDefeated = defender.isDefeated,
            expGained = expGained
        )
    }

    /**
     * 1回の攻撃を解決する
     *
     * @param forecast 戦闘予測
     * @param isInitiator 攻撃を仕掛けた側かどうか
     * @return 攻撃結果
     */
    private fun resolveAttack(
        forecast: DamageCalc.BattleForecast,
        isInitiator: Boolean
    ): AttackResult {
        val hitRoll = (Math.random() * 100).toInt()
        val hit = hitRoll < forecast.hitRate

        val critRoll = (Math.random() * 100).toInt()
        val critical = hit && critRoll < forecast.critRate

        val damage = if (!hit) 0
        else if (critical) forecast.damage * GameConfig.CRITICAL_MULTIPLIER
        else forecast.damage

        return AttackResult(
            attackerIsInitiator = isInitiator,
            damage = damage,
            hit = hit,
            critical = critical
        )
    }

    /**
     * 攻撃結果をユニットに適用する
     *
     * @param result 攻撃結果
     * @param attacker この攻撃の攻撃者
     * @param defender この攻撃の防御者
     */
    private fun applyAttackResult(result: AttackResult, attacker: GameUnit, defender: GameUnit) {
        if (result.hit) {
            defender.takeDamage(result.damage)
        }
    }

    /**
     * 反撃可能かどうかを判定する
     *
     * 武器装備時は武器の射程、素手の場合は隣接（距離1）でのみ反撃可能。
     *
     * @param defender 防御ユニット
     * @param defenderPos 防御ユニットの座標
     * @param attackerPos 攻撃ユニットの座標
     * @return 反撃可能なら true
     */
    private fun canCounterAttack(defender: GameUnit, defenderPos: Position, attackerPos: Position): Boolean {
        val distance = defenderPos.manhattanDistance(attackerPos)
        return distance in defender.attackMinRange()..defender.attackMaxRange()
    }

    /**
     * 戦闘で得られる経験値を計算する
     *
     * 計算式: 基本経験値(30) + (相手Lv - 自分Lv) × 3 + 撃破ボーナス(20)
     * 範囲: 1〜100
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @return 獲得経験値
     */
    fun calculateExp(attacker: GameUnit, defender: GameUnit): Int {
        val baseExp = GameConfig.EXP_BASE
        val levelDiff = defender.level - attacker.level
        val bonus = if (defender.isDefeated) GameConfig.EXP_DEFEAT_BONUS else 0
        return (baseExp + levelDiff * GameConfig.EXP_LEVEL_DIFF_MULTIPLIER + bonus)
            .coerceIn(GameConfig.EXP_MIN, GameConfig.EXP_MAX)
    }
}
