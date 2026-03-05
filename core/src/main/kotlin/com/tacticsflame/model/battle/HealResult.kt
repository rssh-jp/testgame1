package com.tacticsflame.model.battle

import com.tacticsflame.model.unit.GameUnit

/**
 * 回復行動の結果を表すデータクラス
 *
 * @property healer 回復を行ったユニット
 * @property target 回復対象ユニット
 * @property healAmount 実際の回復量（最大HPを超えない分）
 * @property targetHpBefore 回復前のHP
 * @property targetHpAfter 回復後のHP
 * @property expGained 獲得経験値
 */
data class HealResult(
    val healer: GameUnit,
    val target: GameUnit,
    val healAmount: Int,
    val targetHpBefore: Int,
    val targetHpAfter: Int,
    val expGained: Int
)
