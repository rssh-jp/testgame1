package com.tacticsflame.model.campaign

import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.map.Position
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.system.VictoryChecker

/**
 * バトル画面に渡す戦闘設定データ
 *
 * BattlePrepScreen で構成され、BattleScreen のコンストラクタに渡される。
 * BattleScreen は BattleConfig の内容に基づいてバトルを実行する。
 *
 * @property chapterInfo チャプター情報
 * @property battleMap 戦場マップ（ユニット未配置）
 * @property playerUnits 出撃するプレイヤーユニット
 * @property playerPositions プレイヤーユニットの配置位置（ユニットIDをキー）
 * @property enemyUnits 敵ユニット
 * @property enemyPositions 敵ユニットの配置位置（ユニットIDをキー）
 * @property victoryCondition 勝利条件
 */
data class BattleConfig(
    val chapterInfo: ChapterInfo,
    val battleMap: BattleMap,
    val playerUnits: List<GameUnit>,
    val playerPositions: Map<String, Position>,
    val enemyUnits: List<GameUnit>,
    val enemyPositions: Map<String, Position>,
    val victoryCondition: VictoryChecker.VictoryConditionType = VictoryChecker.VictoryConditionType.DEFEAT_ALL,
    val isCampaignMode: Boolean = false,
    val waves: List<WaveConfig> = emptyList()
)
