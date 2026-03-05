package com.tacticsflame.model.unit

/**
 * レベルアップ時の実効ステータス変化量を表すデータクラス
 *
 * 内部ステータス（Float）の変化を実効値（Int、切り捨て）で表現する。
 * UI でのレベルアップ演出や成長表示に使用する。
 *
 * 例: 内部 HP が 20.3 → 21.0 に変化した場合、effectiveHp は 20 → 21 で hp = 1
 *      内部 HP が 20.0 → 20.7 に変化した場合、effectiveHp は 20 → 20 で hp = 0
 *
 * @property hp HP の実効値変化量
 * @property str STR の実効値変化量
 * @property mag MAG の実効値変化量
 * @property skl SKL の実効値変化量
 * @property spd SPD の実効値変化量
 * @property lck LCK の実効値変化量
 * @property def DEF の実効値変化量
 * @property res RES の実効値変化量
 */
data class StatGrowth(
    val hp: Int = 0,
    val str: Int = 0,
    val mag: Int = 0,
    val skl: Int = 0,
    val spd: Int = 0,
    val lck: Int = 0,
    val def: Int = 0,
    val res: Int = 0
)
