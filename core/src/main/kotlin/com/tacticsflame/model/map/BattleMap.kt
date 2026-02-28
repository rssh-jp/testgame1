package com.tacticsflame.model.map

import com.tacticsflame.model.unit.GameUnit

/**
 * バトルマップ全体を管理するクラス
 *
 * @property id マップID
 * @property name マップ名
 * @property width マップの幅（タイル数）
 * @property height マップの高さ（タイル数）
 * @property tiles タイルの2次元配列 [y][x]
 */
class BattleMap(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    private val tiles: Array<Array<Tile>>
) {
    /** マップ上のユニット配置（座標 → ユニット） */
    private val unitPositions = mutableMapOf<Position, GameUnit>()

    /**
     * 指定座標のタイルを取得する
     *
     * @param x X座標
     * @param y Y座標
     * @return タイル（範囲外の場合はnull）
     */
    fun getTile(x: Int, y: Int): Tile? {
        if (x < 0 || x >= width || y < 0 || y >= height) return null
        return tiles[y][x]
    }

    /**
     * 指定座標のタイルを取得する
     *
     * @param pos 座標
     * @return タイル（範囲外の場合はnull）
     */
    fun getTile(pos: Position): Tile? = getTile(pos.x, pos.y)

    /**
     * 座標がマップ範囲内かどうかを判定する
     *
     * @param x X座標
     * @param y Y座標
     * @return 範囲内なら true
     */
    fun isInBounds(x: Int, y: Int): Boolean {
        return x in 0 until width && y in 0 until height
    }

    /**
     * ユニットを配置する
     *
     * @param unit ユニット
     * @param pos 配置先座標
     */
    fun placeUnit(unit: GameUnit, pos: Position) {
        unitPositions[pos] = unit
    }

    /**
     * ユニットを移動させる
     *
     * @param from 移動元座標
     * @param to 移動先座標
     */
    fun moveUnit(from: Position, to: Position) {
        val unit = unitPositions.remove(from)
        if (unit != null) {
            unitPositions[to] = unit
        }
    }

    /**
     * 指定座標のユニットを取得する
     *
     * @param pos 座標
     * @return ユニット（存在しない場合はnull）
     */
    fun getUnitAt(pos: Position): GameUnit? = unitPositions[pos]

    /**
     * 指定座標からユニットを除去する
     *
     * @param pos 座標
     */
    fun removeUnit(pos: Position) {
        unitPositions.remove(pos)
    }

    /**
     * ユニットの座標を取得する
     *
     * @param unit 検索するユニット
     * @return 座標（見つからない場合はnull）
     */
    fun getUnitPosition(unit: GameUnit): Position? {
        return unitPositions.entries.find { it.value == unit }?.key
    }

    /**
     * 全ユニットの一覧を返す
     *
     * @return 座標とユニットのペアリスト
     */
    fun getAllUnits(): List<Pair<Position, GameUnit>> {
        return unitPositions.entries.map { it.key to it.value }
    }
}
