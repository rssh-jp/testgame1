package com.tacticsflame.model.unit

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
 * @property id ユニットID
 * @property name ユニット名
 * @property unitClass 兵種
 * @property faction 所属陣営
 * @property level レベル
 * @property exp 現在の経験値（0〜99）
 * @property stats 現在のステータス
 * @property growthRate 成長率
 * @property weapons 所持武器リスト
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
    /** 現在HP */
    var currentHp: Int = stats.hp
        private set

    /** 行動済みフラグ（レガシー: CTベースシステムでは未使用。将来の拡張用に保持） */
    var hasActed: Boolean = false

    /** チャージタイム（CT）: SPDに基づいて蓄積、閾値到達で行動可能 */
    var ct: Int = 0

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
     * 装備中の武器を取得する（リストの先頭）
     *
     * @return 装備中の武器（なければnull）
     */
    fun equippedWeapon(): Weapon? {
        return weapons.firstOrNull { it.isUsable() }
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
     * @param amount 獲得経験値
     * @return レベルアップした場合は成長したステータス、しなかった場合はnull
     */
    fun gainExp(amount: Int): Stats? {
        exp += amount
        if (exp >= 100) {
            exp -= 100
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
