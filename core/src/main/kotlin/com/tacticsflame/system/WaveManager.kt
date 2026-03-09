package com.tacticsflame.system

import com.tacticsflame.model.campaign.WaveConfig
import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.unit.Faction
import com.tacticsflame.model.unit.GameUnit

/**
 * Campaign Mode のウェーブ進行を管理するシステム
 *
 * 各ウェーブのクリア判定・次ウェーブへの遷移・ウェーブ間回復を担う。
 */
class WaveManager {
    /** 現在のウェーブインデックス */
    var currentWaveIndex: Int = 0
        private set

    /** ウェーブ設定リスト */
    private var waves: List<WaveConfig> = emptyList()

    /** 現在アクティブなウェーブに属する敵ユニットIDリスト */
    private var activeEnemyIds: MutableSet<String> = mutableSetOf()

    /** 現在アクティブなウェーブ設定（未初期化の場合は null） */
    val currentWave: WaveConfig?
        get() = waves.getOrNull(currentWaveIndex)

    /** 次のウェーブ設定（存在しない場合は null） */
    val nextWave: WaveConfig?
        get() = waves.getOrNull(currentWaveIndex + 1)

    /** 現在が最終ウェーブかどうか */
    val isLastWave: Boolean
        get() = currentWaveIndex >= waves.size - 1

    /** ウェーブの総数 */
    val totalWaves: Int
        get() = waves.size

    /**
     * ウェーブ設定を初期化する
     *
     * @param waveConfigs ウェーブ設定リスト
     */
    fun initialize(waveConfigs: List<WaveConfig>) {
        waves = waveConfigs
        currentWaveIndex = 0
        activeEnemyIds.clear()
        // 最初のウェーブの敵IDを登録
        waveConfigs.firstOrNull()?.let { wave ->
            activeEnemyIds.addAll(wave.enemies.map { it.id })
        }
    }

    /**
     * 現在のウェーブがクリア条件を満たしたか判定する
     *
     * ウェーブに属する敵が全てマップ上で撃破済みなら true を返す。
     *
     * @param battleMap 現在のバトルマップ
     * @return クリア済みなら true
     */
    fun isCurrentWaveCleared(battleMap: BattleMap): Boolean {
        val wave = currentWave ?: return false
        // アクティブウェーブの敵がマップ上に生存しているか確認
        val allUnits = battleMap.getAllUnits()
        return !allUnits.any { (_, unit) ->
            unit.id in activeEnemyIds && unit.faction == Faction.ENEMY && !unit.isDefeated
        }
    }

    /**
     * 次のウェーブに進む
     *
     * @return 次のウェーブ設定。最終ウェーブの場合は null
     */
    fun advanceToNextWave(): WaveConfig? {
        if (isLastWave) return null
        currentWaveIndex++
        activeEnemyIds.clear()
        val next = currentWave ?: return null
        activeEnemyIds.addAll(next.enemies.map { it.id })
        return next
    }

    /**
     * ウェーブ間のHP回復を実行する
     *
     * 戦闘不能ユニットは回復対象外。回復量は maxHp を超えない。
     * healPercent が 0 以下の場合は空リストを返す。
     *
     * @param units プレイヤーユニットのリスト
     * @param healPercent 回復率（0〜100）
     * @return 回復されたユニットと回復量のペアリスト
     */
    fun healBetweenWaves(units: List<GameUnit>, healPercent: Int): List<Pair<GameUnit, Int>> {
        if (healPercent <= 0) return emptyList()

        val healed = mutableListOf<Pair<GameUnit, Int>>()
        for (unit in units) {
            if (unit.isDefeated) continue
            if (unit.currentHp >= unit.maxHp) continue

            val healAmount = (unit.maxHp * healPercent / 100).coerceAtLeast(1)
            val actualHeal = minOf(healAmount, unit.maxHp - unit.currentHp)
            if (actualHeal > 0) {
                unit.setCurrentHp(unit.currentHp + actualHeal)
                healed.add(unit to actualHeal)
            }
        }
        return healed
    }

    /**
     * 指定ウェーブから中間復帰用に状態をリセットする
     *
     * @param waveIndex 復帰先のウェーブインデックス
     */
    fun restoreToWave(waveIndex: Int) {
        currentWaveIndex = waveIndex.coerceIn(0, waves.size - 1)
        activeEnemyIds.clear()
        currentWave?.let { wave ->
            activeEnemyIds.addAll(wave.enemies.map { it.id })
        }
    }
}
