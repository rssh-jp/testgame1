package com.tacticsflame.model.battle

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.map.Tile
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.WeaponType

/**
 * ダメージ・命中率・必殺率などの戦闘計算を行うユーティリティ
 *
 * 武器未装備（素手）の場合でも攻撃可能（射程1のみ）。
 * 素手攻撃はSTR依存・威力0・命中80・三すくみ無しで計算される。
 * 防具のDEF/RESボーナスはダメージ軽減に反映される。
 * 実効速度（SPD - 武器重量 - 防具重量）が回避・追撃判定に使用される。
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
     * 武器未装備（素手）の場合は威力0・命中80・射程1で計算する。
     * 防御側の防具ボーナスもダメージに反映される。
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
        val isMagic = weapon?.type == WeaponType.MAGIC
        val weaponMight = weapon?.might ?: GameConfig.UNARMED_MIGHT

        // 防御側の防具ボーナス
        val armorDef = defender.equippedArmor?.defBonus ?: 0
        val armorRes = defender.equippedArmor?.resBonus ?: 0

        // ダメージ計算（素手は物理攻撃扱い）
        val rawDamage = if (isMagic) {
            attacker.stats.mag + weaponMight - (defender.stats.res + armorRes)
        } else {
            attacker.stats.str + weaponMight - (defender.stats.def + armorDef)
        }

        // 武器三すくみ補正（両者が武器を装備している場合のみ）
        val defenderWeapon = defender.equippedWeapon()
        val triangleBonus = if (weapon != null && defenderWeapon != null) {
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
        val hitRate = calculateHitRate(attacker, defender, defenderTile, weapon?.type, defenderWeapon?.type)

        // 必殺率計算
        val critRate = calculateCritRate(attacker, defender)

        // 追撃判定（実効速度を使用: SPD - 武器重量 - 防具重量）
        val canDoubleAttack = (attacker.effectiveSpeed() - defender.effectiveSpeed()) >= GameConfig.DOUBLE_ATTACK_SPEED_DIFF

        return BattleForecast(damage, hitRate, critRate, canDoubleAttack)
    }

    /**
     * 命中率を計算する
     *
     * 素手の場合は基本命中80を使用。
     * 回避には実効速度（装備重量を考慮した速度）を使用する。
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @param defenderTile 防御側の地形
     * @param attackerWeaponType 攻撃側の武器タイプ（null=素手）
     * @param defenderWeaponType 防御側の武器タイプ（null=素手）
     * @return 命中率（0〜100にクランプ）
     */
    private fun calculateHitRate(
        attacker: GameUnit,
        defender: GameUnit,
        defenderTile: Tile,
        attackerWeaponType: WeaponType?,
        defenderWeaponType: WeaponType?
    ): Int {
        val weapon = attacker.equippedWeapon()
        val weaponHit = weapon?.hit ?: GameConfig.UNARMED_HIT

        // 基本命中 = 武器命中（または素手命中） + 技×2 + 幸運÷2
        val baseHit = weaponHit + attacker.stats.skl * 2 + attacker.stats.lck / 2

        // 回避 = 実効速度×2 + 幸運÷2 + 地形回避補正
        val avoid = defender.effectiveSpeed() * 2 + defender.stats.lck / 2 + defenderTile.terrainType.avoidBonus

        // 武器三すくみ補正（両者が武器を持っている場合のみ）
        val triangleHitBonus = if (attackerWeaponType != null && defenderWeaponType != null) {
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
     * 素手の場合は武器必殺0として計算する。
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @return 必殺率（0〜100にクランプ）
     */
    private fun calculateCritRate(attacker: GameUnit, defender: GameUnit): Int {
        val weapon = attacker.equippedWeapon()
        val weaponCrit = weapon?.critical ?: GameConfig.UNARMED_CRITICAL
        val baseCrit = weaponCrit + attacker.stats.skl / 2
        val critAvoid = defender.stats.lck / 2
        return (baseCrit - critAvoid).coerceIn(0, 100)
    }
}
