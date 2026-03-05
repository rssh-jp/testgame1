package com.tacticsflame.system

import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.StatGrowth

/**
 * レベルアップ処理を管理するシステム
 */
class LevelUpSystem {

    /**
     * レベルアップ結果
     *
     * @property unit レベルアップしたユニット
     * @property newLevel 新しいレベル
     * @property growthResult 実効ステータスの変化量
     */
    data class LevelUpResult(
        val unit: GameUnit,
        val newLevel: Int,
        val growthResult: StatGrowth
    )

    /**
     * 経験値を付与し、レベルアップを処理する
     *
     * @param unit 対象ユニット
     * @param expGained 獲得経験値
     * @return レベルアップした場合はその結果、しない場合はnull
     */
    fun awardExp(unit: GameUnit, expGained: Int): LevelUpResult? {
        val growth = unit.gainExp(expGained)
        return if (growth != null) {
            LevelUpResult(unit, unit.level, growth)
        } else {
            null
        }
    }
}
