package com.tacticsflame.model.unit

/**
 * ユニットのステータスを表すデータクラス
 *
 * 内部的に Float で保持し、ゲーム内計算・表示には effectiveXxx（小数点切り捨て整数値）を使用する。
 * レベルアップ時に成長値（Float）が加算され、実効値は floor で切り捨てられる。
 *
 * @property hp 体力（内部値、Float）
 * @property str 力（物理攻撃力、Float）
 * @property mag 魔力（魔法攻撃力、Float）
 * @property skl 技（命中率・必殺率に影響、Float）
 * @property spd 速さ（追撃判定・回避率に影響、Float）
 * @property lck 幸運（必殺回避・各種判定に影響、Float）
 * @property def 守備（物理ダメージ軽減、Float）
 * @property res 魔防（魔法ダメージ軽減、Float）
 */
data class Stats(
    var hp: Float = 0f,
    var str: Float = 0f,
    var mag: Float = 0f,
    var skl: Float = 0f,
    var spd: Float = 0f,
    var lck: Float = 0f,
    var def: Float = 0f,
    var res: Float = 0f
) {
    // ==================== 実効値（小数点切り捨て） ====================

    /** 実効HP（ゲーム内計算・表示で使用） */
    val effectiveHp: Int get() = hp.toInt()
    /** 実効STR */
    val effectiveStr: Int get() = str.toInt()
    /** 実効MAG */
    val effectiveMag: Int get() = mag.toInt()
    /** 実効SKL */
    val effectiveSkl: Int get() = skl.toInt()
    /** 実効SPD */
    val effectiveSpd: Int get() = spd.toInt()
    /** 実効LCK */
    val effectiveLck: Int get() = lck.toInt()
    /** 実効DEF */
    val effectiveDef: Int get() = def.toInt()
    /** 実効RES */
    val effectiveRes: Int get() = res.toInt()

    /**
     * ステータスを加算する
     *
     * @param other 加算するステータス
     * @return 加算結果の新しい Stats
     */
    operator fun plus(other: Stats): Stats {
        return Stats(
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
}
