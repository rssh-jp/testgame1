package com.tacticsflame.model.unit

import com.tacticsflame.core.GameConfig

/**
 * ユニットの所属陣営
 */
enum class Faction {
    /** プレイヤー陣営 */
    PLAYER,
    /** 敵軍 */
    ENEMY,
    /** 同盟軍（NPC） */
    ALLY
}

/**
 * ゲーム中の1ユニットを表すクラス
 *
 * 装備スロット:
 * - 右手（rightHand）: 主武器
 * - 左手（leftHand）: 副武器（二刀流用。クラスが canDualWield の場合のみ）
 * - 防具1（armorSlot1）: 任意の防具
 * - 防具2（armorSlot2）: 任意の防具
 *
 * @property id ユニットID
 * @property name ユニット名
 * @property unitClass 兵種
 * @property faction 所属陣営
 * @property level レベル
 * @property exp 現在の経験値（0〜99）
 * @property stats 現在のステータス
 * @property growthRate 成長率
 * @property weapons 予備武器リスト（装備スロット外の所持武器）
 * @property isLord ロード（主人公）かどうか
 */
class GameUnit(
    val id: String,
    val name: String,
    var unitClass: UnitClass,
    val faction: Faction,
    var level: Int = 1,
    var exp: Int = 0,
    val stats: Stats,
    val growthRate: GrowthRate,
    val weapons: MutableList<Weapon> = mutableListOf(),
    val isLord: Boolean = false
) {
    // ==================== 装備スロット ====================

    /** 右手装備（主武器） */
    var rightHand: Weapon? = null

    /** 左手装備（副武器、二刀流用） */
    var leftHand: Weapon? = null

    /** 防具スロット1 */
    var armorSlot1: Armor? = null

    /** 防具スロット2 */
    var armorSlot2: Armor? = null

    /** 現在HP */
    var currentHp: Int = stats.hp
        private set

    /**
     * 現在HPを直接設定する（セーブデータ復元用）
     *
     * @param hp 設定するHP値（0 ～ maxHp にクランプされる）
     */
    fun setCurrentHp(hp: Int) {
        currentHp = hp.coerceIn(0, maxHp)
    }

    /** 行動済みフラグ（レガシー: CTベースシステムでは未使用。将来の拡張用に保持） */
    var hasActed: Boolean = false

    /** チャージタイム（CT）: SPDに基づいて蓄積、閾値到達で行動可能 */
    var ct: Int = 0

    /** 作戦（AIの行動方針）: 部隊編成画面でユニットごとに設定可能 */
    var tactic: UnitTactic = UnitTactic.CHARGE

    /** 戦闘不能フラグ */
    val isDefeated: Boolean
        get() = currentHp <= 0

    /** 最大HP */
    val maxHp: Int
        get() = stats.hp

    /** 移動力 */
    val mov: Int
        get() = unitClass.baseMov

    /**
     * 装備中の主武器（右手）を取得する
     *
     * @return 装備中の武器（なければnull＝素手）
     */
    fun equippedWeapon(): Weapon? = rightHand

    /**
     * 副武器（左手）を取得する
     *
     * @return 左手の武器（なければnull）
     */
    fun secondaryWeapon(): Weapon? = leftHand

    /**
     * 二刀流状態かどうかを返す
     *
     * @return 両手に武器を装備している場合 true
     */
    fun isDualWielding(): Boolean = rightHand != null && leftHand != null

    /**
     * 全防具のDEFボーナス合計を返す
     *
     * @return DEFボーナス合計
     */
    fun totalArmorDef(): Int = (armorSlot1?.defBonus ?: 0) + (armorSlot2?.defBonus ?: 0)

    /**
     * 全防具のRESボーナス合計を返す
     *
     * @return RESボーナス合計
     */
    fun totalArmorRes(): Int = (armorSlot1?.resBonus ?: 0) + (armorSlot2?.resBonus ?: 0)

    /**
     * 実効速度を計算する（全装備の重量 + 二刀流ペナルティを考慮）
     *
     * 右手・左手の武器と防具1・防具2の重さが速度を低下させる。
     * 二刀流時は、クラスごとの追加速度ペナルティが適用される。
     *
     * @return 実効速度（最低0）
     */
    fun effectiveSpeed(): Int {
        val rWeight = rightHand?.weight ?: GameConfig.UNARMED_WEIGHT
        val lWeight = leftHand?.weight ?: 0
        val a1Weight = armorSlot1?.weight ?: 0
        val a2Weight = armorSlot2?.weight ?: 0
        val dualPenalty = if (isDualWielding()) unitClass.dualWieldPenalty else 0
        return maxOf(0, stats.spd - rWeight - lWeight - a1Weight - a2Weight - dualPenalty)
    }

    /**
     * 攻撃の最小射程を返す（素手の場合は1）
     *
     * @return 最小射程
     */
    fun attackMinRange(): Int = equippedWeapon()?.minRange ?: 1

    /**
     * 攻撃の最大射程を返す（素手の場合は1）
     *
     * @return 最大射程
     */
    fun attackMaxRange(): Int = equippedWeapon()?.maxRange ?: 1

    /**
     * 指定した武器を右手に装備する
     *
     * 現在の右手武器は予備武器リストへ移動する。
     * 対象武器が予備リストまたは左手にある場合はそこから取り出す。
     *
     * @param weapon 装備する武器
     */
    fun equipWeaponToRightHand(weapon: Weapon) {
        weapons.remove(weapon)
        if (leftHand == weapon) leftHand = null
        rightHand?.let { weapons.add(0, it) }
        rightHand = weapon
    }

    /**
     * 指定した武器を左手に装備する（二刀流）
     *
     * クラスが二刀流非対応の場合は何もしない。
     *
     * @param weapon 装備する武器
     */
    fun equipWeaponToLeftHand(weapon: Weapon) {
        if (!unitClass.canDualWield) return
        weapons.remove(weapon)
        if (rightHand == weapon) rightHand = null
        leftHand?.let { weapons.add(0, it) }
        leftHand = weapon
    }

    /**
     * 後方互換用: 指定した武器を右手に装備する
     *
     * @param weapon 装備する武器
     */
    fun equipWeapon(weapon: Weapon) {
        equipWeaponToRightHand(weapon)
    }

    /**
     * ダメージを受ける
     *
     * @param damage ダメージ量（0未満にはならない）
     */
    fun takeDamage(damage: Int) {
        currentHp = maxOf(0, currentHp - maxOf(0, damage))
    }

    /**
     * HPを回復する
     *
     * @param amount 回復量
     */
    fun heal(amount: Int) {
        currentHp = minOf(maxHp, currentHp + maxOf(0, amount))
    }

    /**
     * HPを全回復する
     */
    fun fullHeal() {
        currentHp = maxHp
    }

    /**
     * 経験値を加算し、レベルアップ判定を行う
     *
     * 経験値が [GameConfig.EXP_TO_LEVEL_UP] (100) に達するとレベルアップし、
     * 成長率に基づいてステータスが上昇する。レベル上限はなく、どこまでも成長可能。
     *
     * @param amount 獲得経験値（0以上）
     * @return レベルアップした場合は成長したステータス、しなかった場合はnull
     */
    fun gainExp(amount: Int): Stats? {
        val safeAmount = amount.coerceAtLeast(0)
        exp += safeAmount
        if (exp >= GameConfig.EXP_TO_LEVEL_UP) {
            exp -= GameConfig.EXP_TO_LEVEL_UP
            level++
            return applyLevelUp()
        }
        return null
    }

    /**
     * レベルアップ時のステータス成長を適用する
     *
     * @return 成長したステータス値
     */
    private fun applyLevelUp(): Stats {
        val growth = Stats()
        if (rollGrowth(growthRate.hp)) { stats.hp++; growth.hp = 1 }
        if (rollGrowth(growthRate.str)) { stats.str++; growth.str = 1 }
        if (rollGrowth(growthRate.mag)) { stats.mag++; growth.mag = 1 }
        if (rollGrowth(growthRate.skl)) { stats.skl++; growth.skl = 1 }
        if (rollGrowth(growthRate.spd)) { stats.spd++; growth.spd = 1 }
        if (rollGrowth(growthRate.lck)) { stats.lck++; growth.lck = 1 }
        if (rollGrowth(growthRate.def)) { stats.def++; growth.def = 1 }
        if (rollGrowth(growthRate.res)) { stats.res++; growth.res = 1 }
        // レベルアップ後、最大HPに合わせてHP調整
        currentHp += growth.hp
        return growth
    }

    /**
     * 成長率に基づいてステータスが上昇するか判定する
     *
     * @param rate 成長率（0〜100）
     * @return 上昇する場合 true
     */
    private fun rollGrowth(rate: Int): Boolean {
        return (Math.random() * 100).toInt() < rate
    }

    /**
     * ターン開始時の行動リセット
     */
    fun resetAction() {
        hasActed = false
    }

    override fun toString(): String {
        return "$name (Lv.$level ${unitClass.name}) HP:$currentHp/$maxHp"
    }
}
