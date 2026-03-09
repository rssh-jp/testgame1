package com.tacticsflame.model.campaign

/**
 * ウェーブの矩形領域（大マップ内のオフセットとサイズ）
 *
 * @property offsetX 大マップ上のX方向オフセット（タイル単位）
 * @property offsetY 大マップ上のY方向オフセット（タイル単位）
 * @property width 領域の幅（タイル数）
 * @property height 領域の高さ（タイル数）
 */
data class WaveRegion(
    val offsetX: Int,
    val offsetY: Int,
    val width: Int,
    val height: Int
)
