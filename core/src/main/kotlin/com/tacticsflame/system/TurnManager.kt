package com.tacticsflame.system

import com.tacticsflame.model.unit.Faction
import com.tacticsflame.model.unit.GameUnit

/**
 * ターンの進行を管理するクラス
 */
class TurnManager {

    /** 現在のターン番号 */
    var turnNumber: Int = 1
        private set

    /** 現在のフェイズ */
    var currentPhase: Phase = Phase.PLAYER
        private set

    /**
     * フェイズ定義
     */
    enum class Phase {
        /** プレイヤーフェイズ */
        PLAYER,
        /** エネミーフェイズ */
        ENEMY,
        /** アライフェイズ */
        ALLY
    }

    /**
     * 次のフェイズに進める
     * プレイヤー → 敵 → 同盟 → プレイヤー（ターン+1）
     */
    fun advancePhase() {
        currentPhase = when (currentPhase) {
            Phase.PLAYER -> Phase.ENEMY
            Phase.ENEMY -> Phase.ALLY
            Phase.ALLY -> {
                turnNumber++
                Phase.PLAYER
            }
        }
    }

    /**
     * 現在のフェイズに対応する陣営を取得する
     *
     * @return 現在のフェイズの陣営
     */
    fun currentFaction(): Faction {
        return when (currentPhase) {
            Phase.PLAYER -> Faction.PLAYER
            Phase.ENEMY -> Faction.ENEMY
            Phase.ALLY -> Faction.ALLY
        }
    }

    /**
     * フェイズ開始時にユニットの行動状態をリセットする
     *
     * @param units リセット対象のユニットリスト
     */
    fun startPhase(units: List<GameUnit>) {
        units.filter { it.faction == currentFaction() && !it.isDefeated }
            .forEach { it.resetAction() }
    }

    /**
     * 現在フェイズの全ユニットが行動済みかどうかを判定する
     *
     * @param units ユニットリスト
     * @return 全員行動済みなら true
     */
    fun allUnitsActed(units: List<GameUnit>): Boolean {
        return units.filter { it.faction == currentFaction() && !it.isDefeated }
            .all { it.hasActed }
    }

    /**
     * ターン管理をリセットする
     */
    fun reset() {
        turnNumber = 1
        currentPhase = Phase.PLAYER
    }
}
