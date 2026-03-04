package com.tacticsflame.model.map

/**
 * 地形の種類を定義するenum
 *
 * @property displayName 表示名
 * @property moveCost 移動コスト（-1は通行不可）
 * @property avoidBonus 回避補正（防御側の地形に基づく）
 * @property defenseBonus 防御補正
 * @property hitBonus 命中補正（攻撃側の地形に基づく）
 * @property healsHp ターン開始時のHP回復（true/false）
 */
enum class TerrainType(
    val displayName: String,
    val moveCost: Int,
    val avoidBonus: Int,
    val defenseBonus: Int,
    val hitBonus: Int = 0,
    val healsHp: Boolean = false
) {
    /** 平地 - 標準的な地形 */
    PLAIN("平地", 1, 0, 0),

    /** 森 - 隠れやすく移動コストが高い、地の利で命中+10 */
    FOREST("森", 2, 20, 1, hitBonus = 10),

    /** 山 - 高所の利あり、移動困難、高台から命中+15 */
    MOUNTAIN("山", 3, 30, 2, hitBonus = 15),

    /** 砦 - 守りやすく、HP回復効果あり、防壁から命中+10 */
    FORT("砦", 1, 20, 3, hitBonus = 10, healsHp = true),

    /** 水域 - 通行不可（飛行ユニット除く）、水上では命中-15・回避-15 */
    WATER("水域", -1, -15, 0, hitBonus = -15),

    /** 壁 - 完全通行不可 */
    WALL("壁", -1, 0, 0),

    /** 村 - 訪問可能イベントポイント、地の利で命中+5 */
    VILLAGE("村", 1, 10, 1, hitBonus = 5),

    /** 橋 - 水上の通行路 */
    BRIDGE("橋", 1, 0, 0);

    /**
     * 通行可能かどうかを返す
     *
     * @return 通行可能なら true
     */
    fun isPassable(): Boolean = moveCost > 0
}
