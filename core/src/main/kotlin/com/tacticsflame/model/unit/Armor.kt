package com.tacticsflame.model.unit

/**
 * 防具の種類を定義するenum
 */
enum class ArmorType {
    /** 軽装鎧（革鎧など。軽量で速度低下が少ない） */
    LIGHT_ARMOR,
    /** 重装鎧（鉄鎧など。高防御だが速度が大きく落ちる） */
    HEAVY_ARMOR,
    /** 盾（中程度の防御、片手が塞がる） */
    SHIELD,
    /** 魔法ローブ（魔防重視。軽量で速度低下が少ない） */
    MAGIC_ROBE,
    /** アクセサリ（指輪・護符など。軽量で微補正） */
    ACCESSORY
}

/**
 * 防具データクラス
 *
 * 装備するとDEF/RESが上昇するが、重さに応じて速度が低下する。
 * 軽い防具（革鎧、ローブ等）は速度への影響が少ないが防御力も控えめ。
 * 重い防具（鉄鎧、鋼鎧等）は高い防御力を持つが速度が大きく落ちる。
 *
 * @property id 防具ID
 * @property name 表示名
 * @property type 防具種別
 * @property defBonus 守備ボーナス
 * @property resBonus 魔防ボーナス
 * @property weight 重さ（実効速度を低下させる）
 */
data class Armor(
    val id: String,
    val name: String,
    val type: ArmorType,
    val defBonus: Int,
    val resBonus: Int,
    val weight: Int
)
