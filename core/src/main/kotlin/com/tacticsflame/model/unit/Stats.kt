package com.tacticsflame.model.unit

/**
 * ユニットのステータスを表すデータクラス
 *
 * @property hp 体力
 * @property str 力（物理攻撃力）
 * @property mag 魔力（魔法攻撃力）
 * @property skl 技（命中率・必殺率に影響）
 * @property spd 速さ（追撃判定・回避率に影響）
 * @property lck 幸運（必殺回避・各種判定に影響）
 * @property def 守備（物理ダメージ軽減）
 * @property res 魔防（魔法ダメージ軽減）
 */
data class Stats(
    var hp: Int = 0,
    var str: Int = 0,
    var mag: Int = 0,
    var skl: Int = 0,
    var spd: Int = 0,
    var lck: Int = 0,
    var def: Int = 0,
    var res: Int = 0
) {
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
