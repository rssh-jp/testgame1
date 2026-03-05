package com.tacticsflame.model.battle

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.map.Tile
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.Weapon
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
     * 防御側の全防具ボーナス（2スロット分）もダメージに反映される。
     * 左手攻撃時は副手命中ペナルティが適用される。
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @param attackerTile 攻撃側の地形
     * @param defenderTile 防御側の地形
     * @param useLeftHand 左手（副手）武器で攻撃するか
     * @return 戦闘予測データ
     */
    fun calculateForecast(
        attacker: GameUnit,
        defender: GameUnit,
        attackerTile: Tile,
        defenderTile: Tile,
        useLeftHand: Boolean = false
    ): BattleForecast {
        val weapon = if (useLeftHand) attacker.secondaryWeapon() else attacker.equippedWeapon()
        val isMagic = weapon?.type == WeaponType.MAGIC
        val weaponMight = weapon?.might ?: GameConfig.UNARMED_MIGHT

        // 防御側の全防具ボーナス（2スロット合算）
        val armorDef = defender.totalArmorDef()
        val armorRes = defender.totalArmorRes()

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

        // 命中率計算（攻撃側地形命中補正・副手ペナルティ含む）
        val hitRate = calculateHitRate(attacker, defender, attackerTile, defenderTile, weapon?.type, defenderWeapon?.type, weapon, useLeftHand)

        // 必殺率計算
        val critRate = calculateCritRate(attacker, defender, weapon)

        // 追撃判定（実効速度を使用: SPD - 武器重量 - 防具重量）
        val canDoubleAttack = (attacker.effectiveSpeed() - defender.effectiveSpeed()) >= GameConfig.DOUBLE_ATTACK_SPEED_DIFF

        return BattleForecast(damage, hitRate, critRate, canDoubleAttack)
    }

    /**
     * 命中率を計算する
     *
     * 素手の場合は基本命中80を使用。
     * 攻撃側の地形命中補正（森+10、山+15 等）が命中率にプラスされる。
     * 防御側の地形回避補正（森+20、山+30 等）が回避に加算される。
     * 水域にいるユニットは命中・回避ともに-15のペナルティを受ける。
     * 回避には実効速度（装備重量を考慮した速度）を使用する。
     * 左手攻撃時は副手命中ペナルティが適用される。
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @param attackerTile 攻撃側の地形
     * @param defenderTile 防御側の地形
     * @param attackerWeaponType 攻撃側の武器タイプ（null=素手）
     * @param defenderWeaponType 防御側の武器タイプ（null=素手）
     * @param weapon 使用武器（null=素手）
     * @param isLeftHand 左手攻撃かどうか
     * @return 命中率（0〜100にクランプ）
     */
    private fun calculateHitRate(
        attacker: GameUnit,
        defender: GameUnit,
        attackerTile: Tile,
        defenderTile: Tile,
        attackerWeaponType: WeaponType?,
        defenderWeaponType: WeaponType?,
        weapon: Weapon?,
        isLeftHand: Boolean = false
    ): Int {
        val weaponHit = weapon?.hit ?: GameConfig.UNARMED_HIT

        // 基本命中 = 武器命中（または素手命中） + 技×2 + 幸運÷2 + 攻撃側地形命中補正
        val baseHit = weaponHit + attacker.stats.skl * 2 + attacker.stats.lck / 2 + attackerTile.terrainType.hitBonus

        // 回避 = 実効速度×2 + 幸運÷2 + 防御側地形回避補正
        val avoid = defender.effectiveSpeed() * 2 + defender.stats.lck / 2 + defenderTile.terrainType.avoidBonus

        // 武器三すくみ補正（両者が武器を持っている場合のみ）
        val triangleHitBonus = if (attackerWeaponType != null && defenderWeaponType != null) {
            when (WeaponTriangle.getAdvantage(attackerWeaponType, defenderWeaponType)) {
                WeaponTriangle.Advantage.WIN -> GameConfig.WEAPON_TRIANGLE_HIT_BONUS
                WeaponTriangle.Advantage.LOSE -> -GameConfig.WEAPON_TRIANGLE_HIT_BONUS
                WeaponTriangle.Advantage.NEUTRAL -> 0
            }
        } else 0

        // 副手命中ペナルティ
        val offHandPenalty = if (isLeftHand) GameConfig.DUAL_WIELD_HIT_PENALTY else 0

        return (baseHit - avoid + triangleHitBonus - offHandPenalty).coerceIn(0, 100)
    }

    /**
     * 必殺率を計算する
     *
     * 素手の場合は武器必殺0として計算する。
     *
     * @param attacker 攻撃ユニット
     * @param defender 防御ユニット
     * @param weapon 使用武器（null=素手）
     * @return 必殺率（0〜100にクランプ）
     */
    private fun calculateCritRate(attacker: GameUnit, defender: GameUnit, weapon: Weapon? = null): Int {
        val weaponCrit = weapon?.critical ?: GameConfig.UNARMED_CRITICAL
        val baseCrit = weaponCrit + attacker.stats.skl / 2
        val critAvoid = defender.stats.lck / 2
        return (baseCrit - critAvoid).coerceIn(0, 100)
    }

    // ==================== 回復計算 ====================

    /**
     * 回復予測データ
     *
     * @property healAmount 予測回復量
     * @property targetCurrentHp 回復前の対象HP
     * @property targetMaxHp 対象の最大HP
     */
    data class HealForecast(
        val healAmount: Int,
        val targetCurrentHp: Int,
        val targetMaxHp: Int
    ) {
        /** 回復後のHP（最大HPを超えない） */
        val hpAfterHeal: Int
            get() = minOf(targetCurrentHp + healAmount, targetMaxHp)

        /** 実効回復量（最大HPからの超過分を除いた実際の回復量） */
        val effectiveHealAmount: Int
            get() = hpAfterHeal - targetCurrentHp
    }

    /**
     * 回復予測を計算する
     *
     * 回復量 = 使用者のMAG + 杖のhealPower
     *
     * @param healer 回復を行うユニット
     * @param target 回復対象ユニット
     * @return 回復予測データ
     */
    fun calculateHealForecast(healer: GameUnit, target: GameUnit): HealForecast {
        val weapon = healer.equippedHealingStaff()
        val healPower = weapon?.healPower ?: 0
        val healAmount = healer.stats.mag + healPower

        return HealForecast(
            healAmount = healAmount,
            targetCurrentHp = target.currentHp,
            targetMaxHp = target.maxHp
        )
    }
}
