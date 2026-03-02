package com.tacticsflame.core

/**
 * ゲーム全体の設定定数
 */
object GameConfig {
    /** ゲームタイトル */
    const val TITLE = "Tactics Flame"

    /** 仮想解像度（横） */
    const val VIRTUAL_WIDTH = 1080f

    /** 仮想解像度（縦） */
    const val VIRTUAL_HEIGHT = 1920f

    /** 1タイルのピクセルサイズ */
    const val TILE_SIZE = 64f

    /** 追撃に必要な速さの差 */
    const val DOUBLE_ATTACK_SPEED_DIFF = 5

    /** レベルアップに必要な経験値 */
    const val EXP_TO_LEVEL_UP = 100

    /** レベル上限 */
    const val MAX_LEVEL = 20

    /** 経験値計算: 基本経験値 */
    const val EXP_BASE = 30

    /** 経験値計算: レベル差補正の係数 */
    const val EXP_LEVEL_DIFF_MULTIPLIER = 3

    /** 経験値計算: 敵撃破ボーナス */
    const val EXP_DEFEAT_BONUS = 20

    /** 経験値の最小値 */
    const val EXP_MIN = 1

    /** 経験値の最大値 */
    const val EXP_MAX = 100

    /** 必殺ダメージ倍率 */
    const val CRITICAL_MULTIPLIER = 3

    /** 武器三すくみの命中補正 */
    const val WEAPON_TRIANGLE_HIT_BONUS = 15

    /** 武器三すくみのダメージ補正 */
    const val WEAPON_TRIANGLE_DAMAGE_BONUS = 1

    /** CT（チャージタイム）の行動閾値 */
    const val CT_THRESHOLD = 100

    /** AI思考ウェイト時間（秒） */
    const val AI_THINK_DELAY = 0.4f

    /** 移動アニメーション1タイルあたりの時間（秒） */
    const val MOVE_TIME_PER_TILE = 0.15f

    /** 戦闘結果表示時間（秒） */
    const val COMBAT_RESULT_DELAY = 0.6f

    /** 行動後ウェイト時間（秒） */
    const val POST_ACTION_DELAY = 0.3f

    /** バトル画面のマップ外余白（タイル数） */
    const val BATTLE_MAP_PADDING_TILES = 1.0f

    /** FPS */
    const val TARGET_FPS = 60
}
