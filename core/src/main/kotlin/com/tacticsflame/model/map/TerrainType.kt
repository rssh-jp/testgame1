package com.tacticsflame.model.map

/**
 * 地形の種類を定義するenum
 *
 * @property displayName 表示名
 * @property moveCost 移動コスト（-1は通行不可）
 * @property avoidBonus 回避補正
 * @property defenseBonus 防御補正
 * @property healsHp ターン開始時のHP回復（true/false）
 */
enum class TerrainType(
    val displayName: String,
    val moveCost: Int,
    val avoidBonus: Int,
    val defenseBonus: Int,
    val healsHp: Boolean = false
) {
    /** 平地 - 標準的な地形 */
    PLAIN("平地", 1, 0, 0),

    /** 森 - 隠れやすく移動コストが高い */
    FOREST("森", 2, 20, 1),

    /** 山 - 高所の利あり、移動困難 */
    MOUNTAIN("山", 3, 30, 2),

    /** 砦 - 守りやすく、HP回復効果あり */
    FORT("砦", 1, 20, 3, healsHp = true),

    /** 水域 - 通行不可（一部ユニット除く） */
    WATER("水域", -1, 0, 0),

    /** 壁 - 完全通行不可 */
    WALL("壁", -1, 0, 0),

    /** 村 - 訪問可能イベントポイント */
    VILLAGE("村", 1, 10, 1),

    /** 橋 - 水上の通行路 */
    BRIDGE("橋", 1, 0, 0);

    /**
     * 通行可能かどうかを返す
     *
     * @return 通行可能なら true
     */
    fun isPassable(): Boolean = moveCost > 0
}
