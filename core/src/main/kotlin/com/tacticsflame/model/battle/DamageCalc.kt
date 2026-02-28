package com.tacticsflame.model.battle

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.map.Tile
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.WeaponType

/**
 * ダメージ・命中率・必殺率などの戦闘計算を行うユーティリティ
 */
object DamageCalc {

    /**
     * 戦闘予測データ
     *
     * @property damage 予測ダメージ
     * @property hitRate 命中率（0〜100）
     * @property critRate 必殺率（0〜100）
     * @property canDoubleAttack 追撃可能かどうか
     */
    data class BattleForecast(
        val damage: Int,
        val hitRate: Int,
        val critRate: Int,
        val canDoubleAttack: Boolean
    )

    /**
     * 攻撃側の戦闘予測を計算する
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @param attackerTile 攻撃側の地形
     * @param defenderTile 防御側の地形
     * @return 戦闘予測データ
     */
    fun calculateForecast(
        attacker: GameUnit,
        defender: GameUnit,
        attackerTile: Tile,
        defenderTile: Tile
    ): BattleForecast {
        val weapon = attacker.equippedWeapon()
            ?: return BattleForecast(0, 0, 0, false)

        // ダメージ計算
        val rawDamage = if (weapon.type == WeaponType.MAGIC) {
            attacker.stats.mag + weapon.might - defender.stats.res
        } else {
            attacker.stats.str + weapon.might - defender.stats.def
        }

        // 武器三すくみ補正
        val defenderWeapon = defender.equippedWeapon()
        val triangleBonus = if (defenderWeapon != null) {
            when (WeaponTriangle.getAdvantage(weapon.type, defenderWeapon.type)) {
                WeaponTriangle.Advantage.WIN -> GameConfig.WEAPON_TRIANGLE_DAMAGE_BONUS
                WeaponTriangle.Advantage.LOSE -> -GameConfig.WEAPON_TRIANGLE_DAMAGE_BONUS
                WeaponTriangle.Advantage.NEUTRAL -> 0
            }
        } else 0

        // 地形防御補正
        val terrainDef = defenderTile.terrainType.defenseBonus
        val damage = maxOf(0, rawDamage + triangleBonus - terrainDef)

        // 命中率計算
        val hitRate = calculateHitRate(attacker, defender, defenderTile, weapon.type, defenderWeapon?.type)

        // 必殺率計算
        val critRate = calculateCritRate(attacker, defender)

        // 追撃判定
        val attackSpeed = attacker.stats.spd - weapon.weight
        val defenseSpeed = defender.stats.spd - (defenderWeapon?.weight ?: 0)
        val canDoubleAttack = (attackSpeed - defenseSpeed) >= GameConfig.DOUBLE_ATTACK_SPEED_DIFF

        return BattleForecast(damage, hitRate, critRate, canDoubleAttack)
    }

    /**
     * 命中率を計算する
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @param defenderTile 防御側の地形
     * @param attackerWeaponType 攻撃側の武器タイプ
     * @param defenderWeaponType 防御側の武器タイプ
     * @return 命中率（0〜100にクランプ）
     */
    private fun calculateHitRate(
        attacker: GameUnit,
        defender: GameUnit,
        defenderTile: Tile,
        attackerWeaponType: WeaponType,
        defenderWeaponType: WeaponType?
    ): Int {
        val weapon = attacker.equippedWeapon() ?: return 0

        // 基本命中 = 武器命中 + 技×2 + 幸運÷2
        val baseHit = weapon.hit + attacker.stats.skl * 2 + attacker.stats.lck / 2

        // 回避 = 速さ×2 + 幸運÷2 + 地形回避補正
        val avoid = defender.stats.spd * 2 + defender.stats.lck / 2 + defenderTile.terrainType.avoidBonus

        // 武器三すくみ補正
        val triangleHitBonus = if (defenderWeaponType != null) {
            when (WeaponTriangle.getAdvantage(attackerWeaponType, defenderWeaponType)) {
                WeaponTriangle.Advantage.WIN -> GameConfig.WEAPON_TRIANGLE_HIT_BONUS
                WeaponTriangle.Advantage.LOSE -> -GameConfig.WEAPON_TRIANGLE_HIT_BONUS
                WeaponTriangle.Advantage.NEUTRAL -> 0
            }
        } else 0

        return (baseHit - avoid + triangleHitBonus).coerceIn(0, 100)
    }

    /**
     * 必殺率を計算する
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @return 必殺率（0〜100にクランプ）
     */
    private fun calculateCritRate(attacker: GameUnit, defender: GameUnit): Int {
        val weapon = attacker.equippedWeapon() ?: return 0
        val baseCrit = weapon.critical + attacker.stats.skl / 2
        val critAvoid = defender.stats.lck / 2
        return (baseCrit - critAvoid).coerceIn(0, 100)
    }
}
