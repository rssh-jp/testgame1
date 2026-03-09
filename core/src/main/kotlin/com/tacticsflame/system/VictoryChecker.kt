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

        // 敗北判定:
        // 1. ロードが存在して戦闘不能 → 敗北
        // 2. プレイヤーユニットが全滅（全員除去） → 敗北
        // ※ ロードが最初からいないマップでは、ロード不在だけでは敗北にならない
        val playerUnits = allUnits.filter { it.second.faction == Faction.PLAYER }
        val lordDefeated = playerUnits.any { it.second.isLord && it.second.isDefeated }
        val allPlayersEliminated = playerUnits.isEmpty() || playerUnits.all { it.second.isDefeated }
        if (lordDefeated || allPlayersEliminated) return BattleOutcome.DEFEAT

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

    /**
     * ウェーブ単位のクリア判定を行う
     *
     * 指定された敵IDのうち、マップ上に生存しているものが0体ならクリアとみなす。
     * 敗北判定は従来と同一（ロード死亡/全滅）で先に行う。
     *
     * @param battleMap バトルマップ
     * @param waveEnemyIds ウェーブに属する敵ユニットIDセット
     * @return 判定結果
     */
    fun checkWaveOutcome(
        battleMap: BattleMap,
        waveEnemyIds: Set<String>
    ): BattleOutcome {
        val allUnits = battleMap.getAllUnits()

        // 敗北判定（従来と同一）
        val playerUnits = allUnits.filter { it.second.faction == Faction.PLAYER }
        val lordDefeated = playerUnits.any { it.second.isLord && it.second.isDefeated }
        val allPlayersEliminated = playerUnits.isEmpty() || playerUnits.all { it.second.isDefeated }
        if (lordDefeated || allPlayersEliminated) return BattleOutcome.DEFEAT

        // ウェーブクリア判定
        val waveEnemiesAlive = allUnits.any { (_, unit) ->
            unit.id in waveEnemyIds && unit.faction == Faction.ENEMY && !unit.isDefeated
        }
        return if (!waveEnemiesAlive) BattleOutcome.VICTORY else BattleOutcome.ONGOING
    }
}
