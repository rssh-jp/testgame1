package com.tacticsflame.model.unit

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * ジョブごとのレベルアップ成長とクラス変更後の成長率適用を検証するテスト
 */
class JobGrowthProgressionTest {

    /** 比較時の許容誤差 */
    private val epsilon = 0.001f

    /**
     * 各テスト前に UnitClass のグローバル状態を初期化する
     */
    @BeforeTest
    fun setUp() {
        UnitClass.reset()
    }

    /**
     * 各テスト後に UnitClass のグローバル状態を初期化する
     */
    @AfterTest
    fun tearDown() {
        UnitClass.reset()
    }

    /**
     * ジョブ定義が仕様どおりであることを検証する
     */
    private fun assertUnitClassCatalog() {
        val expectedIds = setOf(
            "lord",
            "swordFighter",
            "lancer",
            "axeFighter",
            "archer",
            "mage",
            "healer",
            "knight",
            "pegasusKnight",
            "armorKnight"
        )

        assertEquals(10, UnitClass.ALL.size, "ジョブ定義数は10件固定であるべき")
        assertEquals(expectedIds, UnitClass.ALL.keys, "ジョブIDセットが仕様と一致しません")
    }

    /**
     * テスト用ユニットを生成する
     */
    private fun createUnit(
        unitClass: UnitClass,
        level: Int = 1,
        exp: Int = 0,
        levelUpStats: Stats = Stats()
    ): GameUnit {
        return GameUnit(
            id = "test_unit_${unitClass.id}",
            name = "テストユニット",
            unitClass = unitClass,
            faction = Faction.PLAYER,
            level = level,
            exp = exp,
            personalModifier = Stats(),
            levelUpStats = levelUpStats,
            personalGrowthRate = GrowthRate()
        )
    }

    /**
     * 1レベルアップ時に levelUpStats が classGrowthRate 分だけ増加することを全ジョブで検証する
     */
    @Test
    fun test_levelUp_allUnitClass_oneLevelGrowthApplied() {
        assertUnitClassCatalog()

        UnitClass.ALL.values.forEach { unitClass ->
            val unit = createUnit(unitClass = unitClass, level = 1, exp = 0)
            val growthRate = unitClass.classGrowthRate

            val growth = unit.gainExp(100)
            assertNotNull(growth, "${unitClass.id}: レベルアップが発生するはず")

            assertEquals(2, unit.level, "${unitClass.id}: レベルが1上がるはず")
            assertEquals(growthRate.hp, unit.levelUpStats.hp, epsilon, "${unitClass.id}: HP成長が一致しません")
            assertEquals(growthRate.str, unit.levelUpStats.str, epsilon, "${unitClass.id}: STR成長が一致しません")
            assertEquals(growthRate.mag, unit.levelUpStats.mag, epsilon, "${unitClass.id}: MAG成長が一致しません")
            assertEquals(growthRate.skl, unit.levelUpStats.skl, epsilon, "${unitClass.id}: SKL成長が一致しません")
            assertEquals(growthRate.spd, unit.levelUpStats.spd, epsilon, "${unitClass.id}: SPD成長が一致しません")
            assertEquals(growthRate.lck, unit.levelUpStats.lck, epsilon, "${unitClass.id}: LCK成長が一致しません")
            assertEquals(growthRate.def, unit.levelUpStats.def, epsilon, "${unitClass.id}: DEF成長が一致しません")
            assertEquals(growthRate.res, unit.levelUpStats.res, epsilon, "${unitClass.id}: RES成長が一致しません")
        }
    }

    /**
     * 10回レベルアップ時に classGrowthRate の累積値と一致することを全ジョブで検証する
     */
    @Test
    fun test_levelUp_allUnitClass_tenLevelGrowthAccumulated() {
        val levelUpCount = 10
        assertUnitClassCatalog()

        UnitClass.ALL.values.forEach { unitClass ->
            val unit = createUnit(unitClass = unitClass, level = 1, exp = 0)
            val growthRate = unitClass.classGrowthRate

            repeat(levelUpCount) {
                val growth = unit.gainExp(100)
                assertNotNull(growth, "${unitClass.id}: 毎回レベルアップが発生するはず")
            }

            assertEquals(1 + levelUpCount, unit.level, "${unitClass.id}: レベル累積が一致しません")
            assertEquals(growthRate.hp * levelUpCount, unit.levelUpStats.hp, epsilon, "${unitClass.id}: HP累積成長が一致しません")
            assertEquals(growthRate.str * levelUpCount, unit.levelUpStats.str, epsilon, "${unitClass.id}: STR累積成長が一致しません")
            assertEquals(growthRate.mag * levelUpCount, unit.levelUpStats.mag, epsilon, "${unitClass.id}: MAG累積成長が一致しません")
            assertEquals(growthRate.skl * levelUpCount, unit.levelUpStats.skl, epsilon, "${unitClass.id}: SKL累積成長が一致しません")
            assertEquals(growthRate.spd * levelUpCount, unit.levelUpStats.spd, epsilon, "${unitClass.id}: SPD累積成長が一致しません")
            assertEquals(growthRate.lck * levelUpCount, unit.levelUpStats.lck, epsilon, "${unitClass.id}: LCK累積成長が一致しません")
            assertEquals(growthRate.def * levelUpCount, unit.levelUpStats.def, epsilon, "${unitClass.id}: DEF累積成長が一致しません")
            assertEquals(growthRate.res * levelUpCount, unit.levelUpStats.res, epsilon, "${unitClass.id}: RES累積成長が一致しません")
        }
    }

    /**
     * Lv11でクラス変更した後の次回レベルアップが変更後クラスの成長率で加算されることと、既存累積成長が保持されることを検証する
     */
    @Test
    fun test_changeClass_fromLevel11_nextGrowthUsesNewClassRate_andKeepsCumulativeStats() {
        val classA = UnitClass.SWORD_FIGHTER
        val classB = UnitClass.KNIGHT
        val initialLevelUpStats = Stats(
            hp = 3.0f,
            str = 1.5f,
            mag = 0.5f,
            skl = 2.0f,
            spd = 1.2f,
            lck = 0.8f,
            def = 0.7f,
            res = 0.4f
        )
        val unit = createUnit(
            unitClass = classA,
            level = 11,
            exp = 0,
            levelUpStats = initialLevelUpStats.copy()
        )

        val beforeChange = unit.levelUpStats.copy()
        unit.changeClass(classB)

        // クラス変更時点で累積成長が保持されることを確認
        assertEquals(beforeChange.hp, unit.levelUpStats.hp, epsilon, "クラス変更でHP累積成長が変化してはいけません")
        assertEquals(beforeChange.str, unit.levelUpStats.str, epsilon, "クラス変更でSTR累積成長が変化してはいけません")
        assertEquals(beforeChange.mag, unit.levelUpStats.mag, epsilon, "クラス変更でMAG累積成長が変化してはいけません")
        assertEquals(beforeChange.skl, unit.levelUpStats.skl, epsilon, "クラス変更でSKL累積成長が変化してはいけません")
        assertEquals(beforeChange.spd, unit.levelUpStats.spd, epsilon, "クラス変更でSPD累積成長が変化してはいけません")
        assertEquals(beforeChange.lck, unit.levelUpStats.lck, epsilon, "クラス変更でLCK累積成長が変化してはいけません")
        assertEquals(beforeChange.def, unit.levelUpStats.def, epsilon, "クラス変更でDEF累積成長が変化してはいけません")
        assertEquals(beforeChange.res, unit.levelUpStats.res, epsilon, "クラス変更でRES累積成長が変化してはいけません")

        val growth = unit.gainExp(100)
        assertNotNull(growth, "クラス変更後の次回レベルアップが発生するはず")

        val classBGrowth = classB.classGrowthRate
        assertEquals(12, unit.level, "レベル11から1回レベルアップして12になるはず")
        assertEquals(beforeChange.hp + classBGrowth.hp, unit.levelUpStats.hp, epsilon, "HP増分は変更後クラス成長率であるべき")
        assertEquals(beforeChange.str + classBGrowth.str, unit.levelUpStats.str, epsilon, "STR増分は変更後クラス成長率であるべき")
        assertEquals(beforeChange.mag + classBGrowth.mag, unit.levelUpStats.mag, epsilon, "MAG増分は変更後クラス成長率であるべき")
        assertEquals(beforeChange.skl + classBGrowth.skl, unit.levelUpStats.skl, epsilon, "SKL増分は変更後クラス成長率であるべき")
        assertEquals(beforeChange.spd + classBGrowth.spd, unit.levelUpStats.spd, epsilon, "SPD増分は変更後クラス成長率であるべき")
        assertEquals(beforeChange.lck + classBGrowth.lck, unit.levelUpStats.lck, epsilon, "LCK増分は変更後クラス成長率であるべき")
        assertEquals(beforeChange.def + classBGrowth.def, unit.levelUpStats.def, epsilon, "DEF増分は変更後クラス成長率であるべき")
        assertEquals(beforeChange.res + classBGrowth.res, unit.levelUpStats.res, epsilon, "RES増分は変更後クラス成長率であるべき")
    }
}
