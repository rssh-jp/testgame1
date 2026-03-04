package com.tacticsflame.model.battle

import com.tacticsflame.model.unit.GameUnit

/**
 * 1回の戦闘結果を表すデータクラス
 *
 * @property attacker 攻撃ユニット
 * @property defender 防御ユニット
 * @property attacks 攻撃のリスト（攻撃側→防御側→追撃 等）
 * @property attackerDefeated 攻撃側が戦闘不能になったか
 * @property defenderDefeated 防御側が戦闘不能になったか
 * @property expGained 獲得経験値
 */
data class BattleResult(
    val attacker: GameUnit,
    val defender: GameUnit,
    val attacks: List<AttackResult>,
    val attackerDefeated: Boolean,
    val defenderDefeated: Boolean,
    val expGained: Int
)

/**
 * 1回の攻撃結果
 *
 * @property attackerIsInitiator 攻撃を仕掛けた側かどうか
 * @property damage 実際のダメージ
 * @property hit 命中したかどうか
 * @property critical 必殺が出たかどうか
 */
data class AttackResult(
    val attackerIsInitiator: Boolean,
    val damage: Int,
    val hit: Boolean,
    val critical: Boolean,
    /** 左手（副手）による攻撃かどうか */
    val isLeftHand: Boolean = false
)
