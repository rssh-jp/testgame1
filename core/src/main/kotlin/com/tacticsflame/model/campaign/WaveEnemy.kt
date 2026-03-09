package com.tacticsflame.model.campaign

/**
 * ウェーブ内の敵配置データ（大マップ座標）
 *
 * @property id 敵ユニットID
 * @property classId 兵種ID
 * @property name 敵ユニット名
 * @property level レベル
 * @property x 大マップ上のX座標
 * @property y 大マップ上のY座標
 * @property ai AIタイプ
 * @property weaponId 装備武器ID
 * @property armorId 装備防具ID（空文字の場合は防具なし）
 * @property isLord ロードフラグ（ボス判定用）
 */
data class WaveEnemy(
    val id: String,
    val classId: String,
    val name: String,
    val level: Int,
    val x: Int,
    val y: Int,
    val ai: String,
    val weaponId: String,
    val armorId: String = "",
    val isLord: Boolean = false
)
