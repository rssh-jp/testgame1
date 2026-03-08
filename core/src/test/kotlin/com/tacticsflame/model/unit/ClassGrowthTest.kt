package com.tacticsflame.model.unit

import kotlin.test.*

/**
 * クラス成長率に関するテスト
 *
 * 各クラスの classGrowthRate が正しく定義されているか、
 * クラス間の成長率バランス、レベルアップ時のクラス成長率適用を検証する。
 */
class ClassGrowthTest {

    /** テスト用: クラス成長率ゼロの職業 */
    private val zeroGrowthClass = UnitClass(
        id = "zeroGrowth", name = "ゼロ成長クラス",
        moveType = MoveType.INFANTRY, baseMov = 5,
        usableWeapons = listOf(WeaponType.SWORD, WeaponType.LANCE, WeaponType.AXE, WeaponType.BOW, WeaponType.MAGIC, WeaponType.STAFF),
        canDualWield = true, dualWieldPenalty = 0,
        classGrowthRate = GrowthRate()
    )

    /**
     * 個人成長率ゼロのテスト用ユニットを生成する
     *
     * @param unitClass ユニットクラス
     * @param exp 初期経験値
     * @return テスト用 GameUnit
     */
    private fun createZeroPersonalGrowthUnit(
        unitClass: UnitClass = zeroGrowthClass,
        exp: Int = 0
    ): GameUnit {
        return GameUnit(
            id = "test_unit", name = "テストユニット",
            unitClass = unitClass, faction = Faction.PLAYER,
            level = 1, exp = exp,
            personalModifier = Stats(hp = 20f, str = 5f, mag = 5f, skl = 5f, spd = 5f, lck = 5f, def = 5f, res = 5f),
            personalGrowthRate = GrowthRate()
        )
    }

    /**
     * GrowthRate が全てゼロでないことを検証するヘルパー
     *
     * @param rate 検証対象の成長率
     */
    private fun assertNonZeroGrowthRate(rate: GrowthRate) {
        val sum = rate.hp + rate.str + rate.mag + rate.skl +
            rate.spd + rate.lck + rate.def + rate.res
        assertTrue(sum > 0f, "成長率の合計がゼロです: $rate")
    }

    // ==================== 各クラスの classGrowthRate 非ゼロ確認 ====================

    /**
     * ナイトの classGrowthRate が非ゼロであることを確認する
     */
    @Test
    fun `ナイトのclassGrowthRateが非ゼロであること`() {
        assertNonZeroGrowthRate(UnitClass.KNIGHT.classGrowthRate)
    }

    /**
     * ランサーの classGrowthRate が非ゼロであることを確認する
     */
    @Test
    fun `ランサーのclassGrowthRateが非ゼロであること`() {
        assertNonZeroGrowthRate(UnitClass.LANCER.classGrowthRate)
    }

    /**
     * アーチャーの classGrowthRate が非ゼロであることを確認する
     */
    @Test
    fun `アーチャーのclassGrowthRateが非ゼロであること`() {
        assertNonZeroGrowthRate(UnitClass.ARCHER.classGrowthRate)
    }

    /**
     * メイジの MAG クラス成長率が 0.65f であることを確認する（blackMage 統合の根拠）
     */
    @Test
    fun `メイジの統合成長率でMAGが0_65fであること`() {
        assertEquals(
            0.65f,
            UnitClass.MAGE.classGrowthRate.mag,
            0.001f,
            "メイジのMAGクラス成長率が期待値(0.65f)と一致しません"
        )
    }

    /**
     * ヒーラーの装備可能武器に MAGIC が含まれることを確認する（whiteMage 統合の根拠）
     */
    @Test
    fun `ヒーラーの装備にMAGICが含まれること`() {
        assertTrue(
            UnitClass.HEALER.usableWeapons.contains(WeaponType.MAGIC),
            "ヒーラーの usableWeapons に MAGIC が含まれていません"
        )
    }

    /**
     * アクスファイターの HP クラス成長率が 1.90f、STR クラス成長率が 0.65f であることを確認する
     * （berserker 統合の根拠）
     */
    @Test
    fun `アクスファイターの統合成長率でHPが1_90fかつSTRが0_65fであること`() {
        assertEquals(
            1.90f,
            UnitClass.AXE_FIGHTER.classGrowthRate.hp,
            0.001f,
            "アクスファイターのHPクラス成長率が期待値(1.90f)と一致しません"
        )
        assertEquals(
            0.65f,
            UnitClass.AXE_FIGHTER.classGrowthRate.str,
            0.001f,
            "アクスファイターのSTRクラス成長率が期待値(0.65f)と一致しません"
        )
    }

    // ==================== ALL マップの網羅性 ====================

    /**
     * ALL マップに全クラスが含まれていることを確認する
     */
    @Test
    fun `ALLマップに全クラスが含まれていること`() {
        val allClasses = UnitClass.ALL
        val expectedIds = listOf(
            "lord", "swordFighter", "lancer", "axeFighter",
            "archer", "mage", "healer", "knight",
            "pegasusKnight", "armorKnight"
        )
        for (id in expectedIds) {
            assertTrue(allClasses.containsKey(id), "ALL マップに $id が含まれていません")
        }
        assertEquals(expectedIds.size, allClasses.size, "ALL マップのサイズが期待値と一致しません")
    }

    // ==================== クラス間の成長率比較 ====================

    /**
     * ナイトの DEF 成長率がアーチャーより高いことを確認する
     */
    @Test
    fun `ナイトのDEF成長率がアーチャーより高いこと`() {
        assertTrue(
            UnitClass.KNIGHT.classGrowthRate.def > UnitClass.ARCHER.classGrowthRate.def,
            "ナイトのDEF成長率(${UnitClass.KNIGHT.classGrowthRate.def})がアーチャー(${UnitClass.ARCHER.classGrowthRate.def})より高くありません"
        )
    }

    /**
     * メイジの MAG 成長率がナイトより高いことを確認する
     */
    @Test
    fun `メイジのMAG成長率がナイトより高いこと`() {
        assertTrue(
            UnitClass.MAGE.classGrowthRate.mag > UnitClass.KNIGHT.classGrowthRate.mag,
            "メイジのMAG成長率(${UnitClass.MAGE.classGrowthRate.mag})がナイト(${UnitClass.KNIGHT.classGrowthRate.mag})より高くありません"
        )
    }

    // ==================== レベルアップ時のクラス成長率適用 ====================

    /**
     * 個人成長率が全てゼロのユニットをナイトで作成し、
     * レベルアップするとナイトの classGrowthRate 分だけ levelUpStats が上昇することを確認する
     */
    @Test
    fun `レベルアップ時にクラス成長率が適用されること`() {
        val unit = createZeroPersonalGrowthUnit(
            unitClass = UnitClass.KNIGHT,
            exp = 99
        )
        val knightGrowth = UnitClass.KNIGHT.classGrowthRate

        // レベルアップ前の levelUpStats を記録
        val beforeHp = unit.levelUpStats.hp
        val beforeStr = unit.levelUpStats.str
        val beforeMag = unit.levelUpStats.mag
        val beforeSkl = unit.levelUpStats.skl
        val beforeSpd = unit.levelUpStats.spd
        val beforeLck = unit.levelUpStats.lck
        val beforeDef = unit.levelUpStats.def
        val beforeRes = unit.levelUpStats.res

        // 経験値を1追加してレベルアップを発動（99 + 1 = 100）
        val growth = unit.gainExp(1)
        assertNotNull(growth, "レベルアップが発生するはず")

        // 個人成長率が0なので、クラス成長率のみが levelUpStats に加算されるはず
        assertEquals(beforeHp + knightGrowth.hp, unit.levelUpStats.hp, 0.001f, "HP成長が一致しません")
        assertEquals(beforeStr + knightGrowth.str, unit.levelUpStats.str, 0.001f, "STR成長が一致しません")
        assertEquals(beforeMag + knightGrowth.mag, unit.levelUpStats.mag, 0.001f, "MAG成長が一致しません")
        assertEquals(beforeSkl + knightGrowth.skl, unit.levelUpStats.skl, 0.001f, "SKL成長が一致しません")
        assertEquals(beforeSpd + knightGrowth.spd, unit.levelUpStats.spd, 0.001f, "SPD成長が一致しません")
        assertEquals(beforeLck + knightGrowth.lck, unit.levelUpStats.lck, 0.001f, "LCK成長が一致しません")
        assertEquals(beforeDef + knightGrowth.def, unit.levelUpStats.def, 0.001f, "DEF成長が一致しません")
        assertEquals(beforeRes + knightGrowth.res, unit.levelUpStats.res, 0.001f, "RES成長が一致しません")
    }
}
