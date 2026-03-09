package com.tacticsflame.model.campaign

import com.tacticsflame.system.VictoryChecker

/**
 * ウェーブ（Campaign Map 内の1チャプター相当）の設定
 *
 * @property waveId ウェーブ番号
 * @property name ウェーブ名
 * @property sourceChapter 元となるチャプターID
 * @property region ウェーブの矩形領域
 * @property enemies ウェーブ内の敵配置リスト
 * @property victoryConditionType 勝利条件タイプ
 * @property cameraFocusX カメラ初期フォーカスX座標
 * @property cameraFocusY カメラ初期フォーカスY座標
 * @property healPercent ウェーブ間のHP回復率（0〜100）
 * @property isLast 最終ウェーブかどうか
 */
data class WaveConfig(
    val waveId: Int,
    val name: String,
    val sourceChapter: String,
    val region: WaveRegion,
    val enemies: List<WaveEnemy>,
    val victoryConditionType: VictoryChecker.VictoryConditionType,
    val cameraFocusX: Int,
    val cameraFocusY: Int,
    val healPercent: Int = 30,
    val isLast: Boolean = false
)
