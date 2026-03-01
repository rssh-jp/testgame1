package com.tacticsflame.model.campaign

/**
 * チャプター（ステージ）の情報を保持するデータクラス
 *
 * ワールドマップ上での表示と、バトル画面への引き渡しに使用する。
 *
 * @property id チャプターID（例: "chapter_1"）
 * @property name チャプター表示名
 * @property description チャプター説明文
 * @property mapFileName マップJSON ファイル名（例: "chapter_1.json"）
 * @property worldMapX ワールドマップ上のX座標（0.0〜1.0の正規化座標）
 * @property worldMapY ワールドマップ上のY座標（0.0〜1.0の正規化座標）
 * @property unlocked 解放済みかどうか
 * @property completed クリア済みかどうか
 * @property requiredUnits 出撃必須ユニットIDリスト
 * @property maxDeployCount 最大出撃人数
 */
data class ChapterInfo(
    val id: String,
    val name: String,
    val description: String = "",
    val mapFileName: String,
    val worldMapX: Float = 0f,
    val worldMapY: Float = 0f,
    var unlocked: Boolean = false,
    var completed: Boolean = false,
    val requiredUnits: List<String> = emptyList(),
    val maxDeployCount: Int = 4
)
