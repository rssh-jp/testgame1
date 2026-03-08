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
 * @property canDualWield 二刀流が可能か
 * @property dualWieldPenalty 二刀流時の追加速度ペナルティ
 * @property classGrowthRate クラス固有の成長率補正
 */
data class UnitClass(
    val id: String,
    val name: String,
    val moveType: MoveType,
    val baseMov: Int,
    val usableWeapons: List<WeaponType>,
    val baseStats: Stats = Stats(),
    /** 二刀流が可能か */
    val canDualWield: Boolean = false,
    /** 二刀流時の追加速度ペナルティ */
    val dualWieldPenalty: Int = 0,
    /** クラス固有の成長率補正 */
    val classGrowthRate: GrowthRate = GrowthRate()
) {
    companion object {
        /** ロード（主人公専用） — バランス型の成長。全能力が万遍なく伸びる */
        private val DEFAULT_LORD = UnitClass(
            id = "lord", name = "ロード",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.SWORD),
            baseStats = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 7f, lck = 5f, def = 5f, res = 2f),
            canDualWield = true, dualWieldPenalty = 2,
            classGrowthRate = GrowthRate(
                hp = 1.60f, str = 0.40f, mag = 0.10f, skl = 0.35f,
                spd = 0.30f, lck = 0.25f, def = 0.25f, res = 0.15f
            )
        )

        /** ソードファイター — SKL/SPD が高成長。手数と命中で勝負する技巧派 */
        private val DEFAULT_SWORD_FIGHTER = UnitClass(
            id = "swordFighter", name = "ソードファイター",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.SWORD),
            baseStats = Stats(hp = 18f, str = 6f, mag = 0f, skl = 8f, spd = 8f, lck = 4f, def = 4f, res = 1f),
            canDualWield = true, dualWieldPenalty = 2,
            classGrowthRate = GrowthRate(
                hp = 1.55f, str = 0.45f, mag = 0.00f, skl = 0.50f,
                spd = 0.40f, lck = 0.15f, def = 0.15f, res = 0.05f
            )
        )

        /** ランサー — STR/DEF が均等に成長。攻守バランスの前衛 */
        private val DEFAULT_LANCER = UnitClass(
            id = "lancer", name = "ランサー",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.LANCE),
            baseStats = Stats(hp = 19f, str = 7f, mag = 0f, skl = 5f, spd = 5f, lck = 3f, def = 7f, res = 1f),
            canDualWield = true, dualWieldPenalty = 4,
            classGrowthRate = GrowthRate(
                hp = 1.70f, str = 0.45f, mag = 0.00f, skl = 0.25f,
                spd = 0.15f, lck = 0.15f, def = 0.40f, res = 0.10f
            )
        )

        /** アクスファイター — STR が非常に高成長。一撃の破壊力で押す脳筋型 */
        private val DEFAULT_AXE_FIGHTER = UnitClass(
            id = "axeFighter", name = "アクスファイター",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.AXE),
            baseStats = Stats(hp = 21f, str = 8f, mag = 0f, skl = 4f, spd = 4f, lck = 2f, def = 5f, res = 0f),
            canDualWield = true, dualWieldPenalty = 3,
            classGrowthRate = GrowthRate(
                hp = 1.90f, str = 0.65f, mag = 0.00f, skl = 0.15f,
                spd = 0.10f, lck = 0.05f, def = 0.25f, res = 0.00f
            )
        )

        /** アーチャー — SKL/SPD が突出。遠距離から確実に当てる狙撃手 */
        private val DEFAULT_ARCHER = UnitClass(
            id = "archer", name = "アーチャー",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.BOW),
            baseStats = Stats(hp = 17f, str = 5f, mag = 0f, skl = 8f, spd = 7f, lck = 4f, def = 3f, res = 1f),
            canDualWield = true, dualWieldPenalty = 3,
            classGrowthRate = GrowthRate(
                hp = 1.55f, str = 0.25f, mag = 0.00f, skl = 0.60f,
                spd = 0.45f, lck = 0.20f, def = 0.05f, res = 0.15f
            )
        )

        /** メイジ — MAG/RES が高成長。物理防御は紙だが魔法火力とRESは一級品 */
        private val DEFAULT_MAGE = UnitClass(
            id = "mage", name = "メイジ",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.MAGIC),
            baseStats = Stats(hp = 15f, str = 1f, mag = 7f, skl = 5f, spd = 5f, lck = 4f, def = 2f, res = 6f),
            canDualWield = true, dualWieldPenalty = 3,
            classGrowthRate = GrowthRate(
                hp = 1.55f, str = 0.00f, mag = 0.65f, skl = 0.30f,
                spd = 0.15f, lck = 0.15f, def = 0.00f, res = 0.45f
            )
        )

        /** ヒーラー — MAG/LCK/RES が高成長。LCKの高さで必殺回避にも貢献 */
        private val DEFAULT_HEALER = UnitClass(
            id = "healer", name = "ヒーラー",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.STAFF, WeaponType.MAGIC),
            baseStats = Stats(hp = 14f, str = 0f, mag = 6f, skl = 4f, spd = 4f, lck = 6f, def = 1f, res = 5f),
            canDualWield = true, dualWieldPenalty = 2,
            classGrowthRate = GrowthRate(
                hp = 1.55f, str = 0.00f, mag = 0.50f, skl = 0.15f,
                spd = 0.15f, lck = 0.40f, def = 0.00f, res = 0.35f
            )
        )

        /** ナイト（騎馬） — HP/DEF が突出。壁役として最も頼りになる騎馬兵 */
        private val DEFAULT_KNIGHT = UnitClass(
            id = "knight", name = "ナイト",
            moveType = MoveType.CAVALRY, baseMov = 7,
            usableWeapons = listOf(WeaponType.SWORD, WeaponType.LANCE),
            baseStats = Stats(hp = 22f, str = 6f, mag = 0f, skl = 5f, spd = 5f, lck = 3f, def = 7f, res = 1f),
            canDualWield = true, dualWieldPenalty = 3,
            classGrowthRate = GrowthRate(
                hp = 2.10f, str = 0.35f, mag = 0.00f, skl = 0.10f,
                spd = 0.10f, lck = 0.10f, def = 0.60f, res = 0.05f
            )
        )

        /** ペガサスナイト（飛行） — SPD/RES が高成長。魔法に強い機動兵 */
        private val DEFAULT_PEGASUS_KNIGHT = UnitClass(
            id = "pegasusKnight", name = "ペガサスナイト",
            moveType = MoveType.FLYING, baseMov = 7,
            usableWeapons = listOf(WeaponType.LANCE),
            baseStats = Stats(hp = 17f, str = 5f, mag = 2f, skl = 6f, spd = 8f, lck = 5f, def = 3f, res = 5f),
            canDualWield = true, dualWieldPenalty = 4,
            classGrowthRate = GrowthRate(
                hp = 1.55f, str = 0.20f, mag = 0.10f, skl = 0.30f,
                spd = 0.50f, lck = 0.20f, def = 0.05f, res = 0.30f
            )
        )

        /** アーマーナイト（重装） — HP/DEF が圧倒的。SPDは絶望的に低い鉄壁 */
        private val DEFAULT_ARMOR_KNIGHT = UnitClass(
            id = "armorKnight", name = "アーマーナイト",
            moveType = MoveType.ARMORED, baseMov = 4,
            usableWeapons = listOf(WeaponType.LANCE),
            baseStats = Stats(hp = 24f, str = 7f, mag = 0f, skl = 3f, spd = 2f, lck = 1f, def = 10f, res = 0f),
            canDualWield = true, dualWieldPenalty = 5,
            classGrowthRate = GrowthRate(
                hp = 2.30f, str = 0.30f, mag = 0.00f, skl = 0.05f,
                spd = 0.00f, lck = 0.05f, def = 0.70f, res = 0.00f
            )
        )

        /** テスト専用クラス（baseStats が全て0、全武器装備可能） */
        private val DEFAULT_TEST_CLASS = UnitClass(
            id = "test", name = "テスト",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.SWORD, WeaponType.LANCE, WeaponType.AXE, WeaponType.BOW, WeaponType.MAGIC, WeaponType.STAFF),
            baseStats = Stats(),
            canDualWield = true, dualWieldPenalty = 2
        )

        /** デフォルト全クラスのマップ */
        private val DEFAULT_ALL: Map<String, UnitClass> = listOf(
            DEFAULT_LORD, DEFAULT_SWORD_FIGHTER, DEFAULT_LANCER, DEFAULT_AXE_FIGHTER,
            DEFAULT_ARCHER, DEFAULT_MAGE, DEFAULT_HEALER, DEFAULT_KNIGHT,
            DEFAULT_PEGASUS_KNIGHT, DEFAULT_ARMOR_KNIGHT
        ).associateBy { it.id }

        /** 外部データで上書きされたクラスマップ（null の場合はデフォルトを使用） */
        private var _all: Map<String, UnitClass>? = null

        /** 全クラスのマップ */
        val ALL: Map<String, UnitClass>
            get() = _all ?: DEFAULT_ALL

        // --- 名前付きアクセサ（ALL から取得、フォールバックはデフォルト値） ---

        /** ロード（主人公専用） */
        val LORD: UnitClass get() = ALL["lord"] ?: DEFAULT_LORD
        /** ソードファイター */
        val SWORD_FIGHTER: UnitClass get() = ALL["swordFighter"] ?: DEFAULT_SWORD_FIGHTER
        /** ランサー */
        val LANCER: UnitClass get() = ALL["lancer"] ?: DEFAULT_LANCER
        /** アクスファイター */
        val AXE_FIGHTER: UnitClass get() = ALL["axeFighter"] ?: DEFAULT_AXE_FIGHTER
        /** アーチャー */
        val ARCHER: UnitClass get() = ALL["archer"] ?: DEFAULT_ARCHER
        /** メイジ */
        val MAGE: UnitClass get() = ALL["mage"] ?: DEFAULT_MAGE
        /** ヒーラー */
        val HEALER: UnitClass get() = ALL["healer"] ?: DEFAULT_HEALER
        /** ナイト（騎馬） */
        val KNIGHT: UnitClass get() = ALL["knight"] ?: DEFAULT_KNIGHT
        /** ペガサスナイト（飛行） */
        val PEGASUS_KNIGHT: UnitClass get() = ALL["pegasusKnight"] ?: DEFAULT_PEGASUS_KNIGHT
        /** アーマーナイト（重装） */
        val ARMOR_KNIGHT: UnitClass get() = ALL["armorKnight"] ?: DEFAULT_ARMOR_KNIGHT
        /** テスト専用クラス（baseStats全ゼロ） */
        val TEST_CLASS: UnitClass get() = DEFAULT_TEST_CLASS

        /**
         * 外部データから読み込んだクラスデータで ALL を初期化する
         * JSON にないクラスは DEFAULT_ALL から補完する（マージ戦略）
         */
        fun initialize(loaded: Map<String, UnitClass>) {
            _all = DEFAULT_ALL + loaded  // loaded が優先（上書き）
        }

        /**
         * 初期化をリセットする（テスト用）
         */
        fun reset() {
            _all = null
        }
    }
}
