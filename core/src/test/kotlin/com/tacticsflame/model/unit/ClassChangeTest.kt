package com.tacticsflame.model.unit

import kotlin.test.*

/**
 * ユニットの転職（changeClass）に関するテスト
 *
 * クラス変更時のステータス維持、装備の自動外し、
 * 転職後のレベルアップでの新クラス成長率適用を検証する。
 */
class ClassChangeTest {

    /** テスト用: クラス成長率ゼロの剣士クラス */
    private val zeroGrowthSwordClass = UnitClass(
        id = "zeroSword", name = "ゼロ剣士",
        moveType = MoveType.INFANTRY, baseMov = 5,
        usableWeapons = listOf(WeaponType.SWORD),
        canDualWield = true, dualWieldPenalty = 0,
        classGrowthRate = GrowthRate()
    )

    /** テスト用の剣 */
    private val testSword = Weapon(
        id = "test_sword", name = "テスト剣",
        type = WeaponType.SWORD, might = 5, hit = 90, weight = 3
    )

    /** テスト用の槍 */
    private val testLance = Weapon(
        id = "test_lance", name = "テスト槍",
        type = WeaponType.LANCE, might = 7, hit = 80, weight = 5
    )

    /** テスト用の弓 */
    private val testBow = Weapon(
        id = "test_bow", name = "テスト弓",
        type = WeaponType.BOW, might = 6, hit = 85, weight = 4,
        minRange = 2, maxRange = 2
    )

    /**
     * テスト用ユニットを生成する
     *
     * @param unitClass ユニットクラス
     * @param personalModifier キャラクター固有のステータス補正
     * @param levelUpStats レベルアップ累積ステータス
     * @return テスト用 GameUnit
     */
    private fun createUnit(
        unitClass: UnitClass = zeroGrowthSwordClass,
        personalModifier: Stats = Stats(hp = 20f, str = 5f, mag = 5f, skl = 5f, spd = 5f, lck = 5f, def = 5f, res = 5f),
        levelUpStats: Stats = Stats()
    ): GameUnit {
        return GameUnit(
            id = "test_unit", name = "テストユニット",
            unitClass = unitClass, faction = Faction.PLAYER,
            level = 5, exp = 0,
            personalModifier = personalModifier,
            levelUpStats = levelUpStats,
            personalGrowthRate = GrowthRate()
        )
    }

    // ==================== 基本的な転職テスト ====================

    /**
     * changeClass で unitClass が変更されることを確認する
     */
    @Test
    fun `changeClassでunitClassが変更されること`() {
        val unit = createUnit()
        assertEquals(zeroGrowthSwordClass, unit.unitClass)

        unit.changeClass(UnitClass.KNIGHT)

        assertEquals(UnitClass.KNIGHT, unit.unitClass)
    }

    // ==================== 転職後のレベルアップ ====================

    /**
     * changeClass 後のレベルアップで新クラスの classGrowthRate が適用されることを確認する
     */
    @Test
    fun `changeClass後のレベルアップで新クラスのclassGrowthRateが適用されること`() {
        val unit = createUnit()
        unit.changeClass(UnitClass.KNIGHT)

        // 経験値を99にしてレベルアップ直前にする
        unit.gainExp(99)

        // レベルアップ前の levelUpStats を記録
        val beforeHp = unit.levelUpStats.hp
        val beforeStr = unit.levelUpStats.str
        val beforeDef = unit.levelUpStats.def

        // レベルアップ発動
        val growth = unit.gainExp(1)
        assertNotNull(growth, "レベルアップが発生するはず")

        val knightGrowth = UnitClass.KNIGHT.classGrowthRate

        // 個人成長率が0なので、ナイトのクラス成長率のみが加算されるはず
        assertEquals(beforeHp + knightGrowth.hp, unit.levelUpStats.hp, 0.001f, "HP成長が一致しません")
        assertEquals(beforeStr + knightGrowth.str, unit.levelUpStats.str, 0.001f, "STR成長が一致しません")
        assertEquals(beforeDef + knightGrowth.def, unit.levelUpStats.def, 0.001f, "DEF成長が一致しません")
    }

    // ==================== 装備不可武器の自動外し ====================

    /**
     * 転職時に装備不可の武器が自動的に外されて予備に移動されることを確認する
     * （剣を持つユニットをアーチャーに転職 → 剣が外れて予備武器に）
     */
    @Test
    fun `転職時に装備不可の武器が自動的に外されること`() {
        val unit = createUnit()
        unit.equipWeaponToRightHand(testSword)
        assertEquals(testSword, unit.rightHand)

        // アーチャー（弓のみ装備可能）に転職
        unit.changeClass(UnitClass.ARCHER)

        // 剣が外れて予備武器に移動されるはず
        assertNull(unit.rightHand, "剣が右手から外れているはず")
        assertTrue(unit.weapons.contains(testSword), "剣が予備武器に移動されているはず")
    }

    // ==================== ステータス維持 ====================

    /**
     * 転職時に personalModifier と levelUpStats が維持されることを確認する
     */
    @Test
    fun `転職時にpersonalModifierとlevelUpStatsが維持されること`() {
        val originalPersonalModifier = Stats(hp = 25f, str = 8f, mag = 3f, skl = 7f, spd = 6f, lck = 4f, def = 6f, res = 3f)
        val originalLevelUpStats = Stats(hp = 3.5f, str = 1.2f, mag = 0.5f, skl = 1.0f, spd = 0.8f, lck = 0.6f, def = 0.9f, res = 0.3f)
        val unit = createUnit(
            personalModifier = originalPersonalModifier,
            levelUpStats = originalLevelUpStats
        )

        unit.changeClass(UnitClass.KNIGHT)

        // personalModifier が変更されていないことを確認
        assertEquals(originalPersonalModifier.hp, unit.personalModifier.hp, "personalModifier.hp が維持されていません")
        assertEquals(originalPersonalModifier.str, unit.personalModifier.str, "personalModifier.str が維持されていません")
        assertEquals(originalPersonalModifier.mag, unit.personalModifier.mag, "personalModifier.mag が維持されていません")
        assertEquals(originalPersonalModifier.skl, unit.personalModifier.skl, "personalModifier.skl が維持されていません")
        assertEquals(originalPersonalModifier.spd, unit.personalModifier.spd, "personalModifier.spd が維持されていません")
        assertEquals(originalPersonalModifier.lck, unit.personalModifier.lck, "personalModifier.lck が維持されていません")
        assertEquals(originalPersonalModifier.def, unit.personalModifier.def, "personalModifier.def が維持されていません")
        assertEquals(originalPersonalModifier.res, unit.personalModifier.res, "personalModifier.res が維持されていません")

        // levelUpStats が変更されていないことを確認
        assertEquals(originalLevelUpStats.hp, unit.levelUpStats.hp, "levelUpStats.hp が維持されていません")
        assertEquals(originalLevelUpStats.str, unit.levelUpStats.str, "levelUpStats.str が維持されていません")
        assertEquals(originalLevelUpStats.mag, unit.levelUpStats.mag, "levelUpStats.mag が維持されていません")
        assertEquals(originalLevelUpStats.skl, unit.levelUpStats.skl, "levelUpStats.skl が維持されていません")
        assertEquals(originalLevelUpStats.spd, unit.levelUpStats.spd, "levelUpStats.spd が維持されていません")
        assertEquals(originalLevelUpStats.lck, unit.levelUpStats.lck, "levelUpStats.lck が維持されていません")
        assertEquals(originalLevelUpStats.def, unit.levelUpStats.def, "levelUpStats.def が維持されていません")
        assertEquals(originalLevelUpStats.res, unit.levelUpStats.res, "levelUpStats.res が維持されていません")
    }

    /**
     * 転職時に stats（実効値）がクラス変更で変化することを確認する
     *
     * stats = unitClass.baseStats + personalModifier + levelUpStats なので、
     * unitClass が変わると stats も変わる。
     */
    @Test
    fun `転職時にstatsがクラス変更で変化すること`() {
        val unit = createUnit()
        val statsBefore = unit.stats.copy()

        // ゼロ成長剣士クラスからナイトに転職
        unit.changeClass(UnitClass.KNIGHT)
        val statsAfter = unit.stats

        // ナイトとゼロ成長剣士クラスの baseStats が異なるため、stats も変化するはず
        assertNotEquals(statsBefore.hp, statsAfter.hp, "クラス変更で stats.hp が変化するはず")
    }

    // ==================== 二刀流非対応クラスへの転職 ====================

    /**
     * 二刀流非対応クラスに転職した場合、左手武器が外されることを確認する
     */
    @Test
    fun `二刀流非対応クラスに転職した場合に左手武器が外されること`() {
        // 二刀流非対応のテスト用クラス（剣＋槍装備可能）
        val noDualWieldClass = UnitClass(
            id = "noDualWield", name = "二刀流不可クラス",
            moveType = MoveType.INFANTRY, baseMov = 5,
            usableWeapons = listOf(WeaponType.SWORD, WeaponType.LANCE),
            canDualWield = false, dualWieldPenalty = 0,
            classGrowthRate = GrowthRate()
        )

        val unit = createUnit()
        // 右手に剣、左手に槍を装備
        unit.equipWeaponToRightHand(testSword)
        unit.equipWeaponToLeftHand(testLance)
        assertEquals(testSword, unit.rightHand)
        assertEquals(testLance, unit.leftHand)

        // 二刀流非対応クラスに転職
        unit.changeClass(noDualWieldClass)

        // 右手はそのまま（剣は装備可能）
        assertEquals(testSword, unit.rightHand, "右手の剣は維持されるはず")
        // 左手は外される
        assertNull(unit.leftHand, "左手武器が外れているはず")
        // 槍が予備武器に移動される
        assertTrue(unit.weapons.contains(testLance), "槍が予備武器に移動されているはず")
    }
}
