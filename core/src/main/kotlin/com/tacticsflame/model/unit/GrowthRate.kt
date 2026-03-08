package com.tacticsflame.model.unit

/**
 * レベルアップ時の確定成長値を表すデータクラス
 *
 * 各値はレベルアップごとに加算される固定の Float 値。
 * 例: hp = 0.70f → 毎レベルアップで HP に +0.70 加算。
 *
 * SPD は原則として全ユニット共通の値（0.20）を設定する（FFT方式）。
 *
 * @property hp HP成長値
 * @property str 力成長値
 * @property mag 魔力成長値
 * @property skl 技成長値
 * @property spd 速さ成長値（全ユニット共通 0.20 推奨）
 * @property lck 幸運成長値
 * @property def 守備成長値
 * @property res 魔防成長値
 */
data class GrowthRate(
    val hp: Float = 0f,
    val str: Float = 0f,
    val mag: Float = 0f,
    val skl: Float = 0f,
    val spd: Float = 0f,
    val lck: Float = 0f,
    val def: Float = 0f,
    val res: Float = 0f
) {
    /**
     * 2つの [GrowthRate] を成分ごとに加算した新しい [GrowthRate] を返す
     *
     * @param other 加算する成長率
     * @return 各フィールドを成分ごとに加算した [GrowthRate]
     */
    operator fun plus(other: GrowthRate): GrowthRate = GrowthRate(
        hp = hp + other.hp,
        str = str + other.str,
        mag = mag + other.mag,
        skl = skl + other.skl,
        spd = spd + other.spd,
        lck = lck + other.lck,
        def = def + other.def,
        res = res + other.res
    )
}
