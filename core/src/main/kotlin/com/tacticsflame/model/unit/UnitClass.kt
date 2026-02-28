package com.tacticsflame.model.unit

/**
 * 移動タイプ（地形コスト計算に使用）
 */
enum class MoveType {
    /** 歩兵 */
    INFANTRY,
    /** 騎馬 */
    CAVALRY,
    /** 飛行 */
    FLYING,
    /** 重装 */
    ARMORED
}

/**
 * ユニットクラス（兵種）を定義するデータクラス
 *
 * @property id クラスID
 * @property name 表示名
 * @property moveType 移動タイプ
 * @property baseMov 基本移動力
 * @property usableWeapons 装備可能な武器タイプ
 * @property baseStats クラス基本ステータス補正
 */
data class UnitClass(
    val id: String,
    val name: String,
    val moveType: MoveType,
    val baseMov: Int,
    val usableWeapons: List<WeaponType>,
    val baseStats: Stats = Stats()
) {
    companion object {
        /** ロード（主人公専用） */
        val LORD = UnitClass(
            id = "lord", name = "ロード",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.SWORD)
        )

        /** ソードファイター */
        val SWORD_FIGHTER = UnitClass(
            id = "swordFighter", name = "ソードファイター",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.SWORD)
        )

        /** ランサー */
        val LANCER = UnitClass(
            id = "lancer", name = "ランサー",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.LANCE)
        )

        /** アクスファイター */
        val AXE_FIGHTER = UnitClass(
            id = "axeFighter", name = "アクスファイター",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.AXE)
        )

        /** アーチャー */
        val ARCHER = UnitClass(
            id = "archer", name = "アーチャー",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.BOW)
        )

        /** メイジ */
        val MAGE = UnitClass(
            id = "mage", name = "メイジ",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.MAGIC)
        )

        /** ヒーラー */
        val HEALER = UnitClass(
            id = "healer", name = "ヒーラー",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.STAFF)
        )

        /** ナイト（騎馬） */
        val KNIGHT = UnitClass(
            id = "knight", name = "ナイト",
            moveType = MoveType.CAVALRY, baseMov = 7,
            usableWeapons = listOf(WeaponType.SWORD, WeaponType.LANCE)
        )

        /** ペガサスナイト（飛行） */
        val PEGASUS_KNIGHT = UnitClass(
            id = "pegasusKnight", name = "ペガサスナイト",
            moveType = MoveType.FLYING, baseMov = 7,
            usableWeapons = listOf(WeaponType.LANCE)
        )

        /** アーマーナイト（重装） */
        val ARMOR_KNIGHT = UnitClass(
            id = "armorKnight", name = "アーマーナイト",
            moveType = MoveType.ARMORED, baseMov = 4,
            usableWeapons = listOf(WeaponType.LANCE)
        )

        /** 全クラスのマップ */
        val ALL: Map<String, UnitClass> = listOf(
            LORD, SWORD_FIGHTER, LANCER, AXE_FIGHTER,
            ARCHER, MAGE, HEALER, KNIGHT, PEGASUS_KNIGHT, ARMOR_KNIGHT
        ).associateBy { it.id }
    }
}
