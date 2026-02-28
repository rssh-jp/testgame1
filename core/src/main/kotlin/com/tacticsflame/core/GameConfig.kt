package com.tacticsflame.core

/**
 * ゲーム全体の設定定数
 */
object GameConfig {
    /** ゲームタイトル */
    const val TITLE = "Tactics Flame"

    /** 仮想解像度（横） */
    const val VIRTUAL_WIDTH = 1920f

    /** 仮想解像度（縦） */
    const val VIRTUAL_HEIGHT = 1080f

    /** 1タイルのピクセルサイズ */
    const val TILE_SIZE = 64f

    /** 追撃に必要な速さの差 */
    const val DOUBLE_ATTACK_SPEED_DIFF = 5

    /** レベルアップに必要な経験値 */
    const val EXP_TO_LEVEL_UP = 100

    /** 必殺ダメージ倍率 */
    const val CRITICAL_MULTIPLIER = 3

    /** 武器三すくみの命中補正 */
    const val WEAPON_TRIANGLE_HIT_BONUS = 15

    /** 武器三すくみのダメージ補正 */
    const val WEAPON_TRIANGLE_DAMAGE_BONUS = 1

    /** CT（チャージタイム）の行動閾値 */
    const val CT_THRESHOLD = 100

    /** FPS */
    const val TARGET_FPS = 60
}
