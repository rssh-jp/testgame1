package com.tacticsflame.system

import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.unit.Faction
import com.tacticsflame.model.unit.GameUnit

/**
 * 勝敗判定を行うシステム
 */
class VictoryChecker {

    /**
     * 勝利条件のタイプ
     */
    enum class VictoryConditionType {
        /** 敵全滅 */
        DEFEAT_ALL,
        /** ボス撃破 */
        DEFEAT_BOSS,
        /** 特定地点到達 */
        REACH_POINT,
        /** 指定ターン防衛 */
        SURVIVE_TURNS
    }

    /**
     * 勝敗判定結果
     */
    enum class BattleOutcome {
        /** 継続中 */
        ONGOING,
        /** 勝利 */
        VICTORY,
        /** 敗北 */
        DEFEAT
    }

    /**
     * 勝敗判定を行う
     *
     * @param battleMap バトルマップ
     * @param conditionType 勝利条件タイプ
     * @param turnNumber 現在のターン番号
     * @param targetTurn 防衛目標ターン数（SURVIVE_TURNS用）
     * @param bossId ボスID（DEFEAT_BOSS用）
     * @return 判定結果
     */
    fun checkOutcome(
        battleMap: BattleMap,
        conditionType: VictoryConditionType,
        turnNumber: Int = 0,
        targetTurn: Int = 0,
        bossId: String? = null
    ): BattleOutcome {
        val allUnits = battleMap.getAllUnits()

        // 敗北判定: ロードが戦闘不能
        val lordDefeated = allUnits
            .filter { it.second.faction == Faction.PLAYER }
            .any { it.second.isLord && it.second.isDefeated }
        if (lordDefeated) return BattleOutcome.DEFEAT

        // 勝利判定
        return when (conditionType) {
            VictoryConditionType.DEFEAT_ALL -> {
                val enemiesAlive = allUnits.any {
                    it.second.faction == Faction.ENEMY && !it.second.isDefeated
                }
                if (!enemiesAlive) BattleOutcome.VICTORY else BattleOutcome.ONGOING
            }

            VictoryConditionType.DEFEAT_BOSS -> {
                val bossAlive = allUnits.any {
                    it.second.id == bossId && !it.second.isDefeated
                }
                if (!bossAlive) BattleOutcome.VICTORY else BattleOutcome.ONGOING
            }

            VictoryConditionType.SURVIVE_TURNS -> {
                if (turnNumber >= targetTurn) BattleOutcome.VICTORY else BattleOutcome.ONGOING
            }

            VictoryConditionType.REACH_POINT -> {
                // 到達判定は別途実装
                BattleOutcome.ONGOING
            }
        }
    }
}
