package com.tacticsflame.model.campaign

import com.tacticsflame.model.unit.GameUnit

/**
 * バトル結果データ
 *
 * BattleScreen の戦闘終了時に生成され、BattleResultScreen に渡される。
 *
 * @property chapterInfo 戦闘したチャプター情報
 * @property isVictory 勝利したかどうか
 * @property roundCount 経過ラウンド数
 * @property defeatedEnemies 撃破した敵ユニット数
 * @property totalEnemies 敵ユニットの総数
 * @property survivingUnits 生存しているプレイヤーユニットのリスト
 * @property expGained ユニットごとの獲得経験値（ユニットID → 経験値）
 */
data class BattleResultData(
    val chapterInfo: ChapterInfo,
    val isVictory: Boolean,
    val roundCount: Int = 0,
    val defeatedEnemies: Int = 0,
    val totalEnemies: Int = 0,
    val survivingUnits: List<GameUnit> = emptyList(),
    val expGained: Map<String, Int> = emptyMap()
)
