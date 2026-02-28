package com.tacticsflame.model.map

/**
 * マップ上の1タイルを表すデータクラス
 *
 * @property position タイルの座標
 * @property terrainType 地形の種類
 */
data class Tile(
    val position: Position,
    val terrainType: TerrainType
)
