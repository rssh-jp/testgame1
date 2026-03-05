package com.tacticsflame.model.unit

/**
 * 武器の種類を定義するenum
 */
enum class WeaponType {
    /** 剣 */
    SWORD,
    /** 槍 */
    LANCE,
    /** 斧 */
    AXE,
    /** 弓 */
    BOW,
    /** 魔法（アニマ系） */
    MAGIC,
    /** 杖（回復用） */
    STAFF
}

/**
 * 武器データクラス
 *
 * @property id 武器ID
 * @property name 表示名
 * @property type 武器種別
 * @property might 威力
 * @property hit 命中率
 * @property critical 必殺率
 * @property weight 重さ（攻速に影響）
 * @property minRange 最小射程
 * @property maxRange 最大射程
 * @property healPower 回復量（STAFF武器のみ使用。0の場合は攻撃武器扱い）
 */
data class Weapon(
    val id: String,
    val name: String,
    val type: WeaponType,
    val might: Int,
    val hit: Int,
    val critical: Int = 0,
    val weight: Int = 0,
    val minRange: Int = 1,
    val maxRange: Int = 1,
    val healPower: Int = 0
) {
    /** この武器が回復用杖かどうか */
    val isHealingStaff: Boolean
        get() = type == WeaponType.STAFF && healPower > 0
}
