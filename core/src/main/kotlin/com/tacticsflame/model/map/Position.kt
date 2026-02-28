package com.tacticsflame.model.map

/**
 * マップ上の座標を表すデータクラス
 *
 * @property x X座標（列）
 * @property y Y座標（行）
 */
data class Position(val x: Int, val y: Int) {

    /**
     * マンハッタン距離を計算する
     *
     * @param other 比較対象の座標
     * @return マンハッタン距離
     */
    fun manhattanDistance(other: Position): Int {
        return Math.abs(x - other.x) + Math.abs(y - other.y)
    }

    /**
     * 隣接する4方向の座標を返す
     *
     * @return 上下左右の座標リスト
     */
    fun neighbors(): List<Position> {
        return listOf(
            Position(x, y - 1), // 上
            Position(x, y + 1), // 下
            Position(x - 1, y), // 左
            Position(x + 1, y)  // 右
        )
    }
}
