package com.tacticsflame.model.battle

import com.tacticsflame.model.unit.WeaponType

/**
 * 武器三すくみの判定を行うユーティリティ
 */
object WeaponTriangle {

    /**
     * 三すくみの結果を表すenum
     */
    enum class Advantage {
        /** 有利 */
        WIN,
        /** 不利 */
        LOSE,
        /** 中立 */
        NEUTRAL
    }

    /**
     * 攻撃側から見た武器三すくみの有利不利を判定する
     *
     * 剣 → 斧（有利）→ 槍（有利）→ 剣（有利）
     *
     * @param attacker 攻撃側の武器タイプ
     * @param defender 防御側の武器タイプ
     * @return 有利不利の結果
     */
    fun getAdvantage(attacker: WeaponType, defender: WeaponType): Advantage {
        return when (attacker) {
            WeaponType.SWORD -> when (defender) {
                WeaponType.AXE -> Advantage.WIN
                WeaponType.LANCE -> Advantage.LOSE
                else -> Advantage.NEUTRAL
            }
            WeaponType.LANCE -> when (defender) {
                WeaponType.SWORD -> Advantage.WIN
                WeaponType.AXE -> Advantage.LOSE
                else -> Advantage.NEUTRAL
            }
            WeaponType.AXE -> when (defender) {
                WeaponType.LANCE -> Advantage.WIN
                WeaponType.SWORD -> Advantage.LOSE
                else -> Advantage.NEUTRAL
            }
            else -> Advantage.NEUTRAL
        }
    }
}
