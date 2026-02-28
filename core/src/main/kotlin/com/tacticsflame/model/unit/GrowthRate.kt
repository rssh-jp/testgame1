package com.tacticsflame.model.unit

/**
 * レベルアップ時の成長率を表すデータクラス
 * 各値は0〜100のパーセンテージ
 *
 * @property hp HP成長率（%）
 * @property str 力成長率（%）
 * @property mag 魔力成長率（%）
 * @property skl 技成長率（%）
 * @property spd 速さ成長率（%）
 * @property lck 幸運成長率（%）
 * @property def 守備成長率（%）
 * @property res 魔防成長率（%）
 */
data class GrowthRate(
    val hp: Int = 0,
    val str: Int = 0,
    val mag: Int = 0,
    val skl: Int = 0,
    val spd: Int = 0,
    val lck: Int = 0,
    val def: Int = 0,
    val res: Int = 0
)
