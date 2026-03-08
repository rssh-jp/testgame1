package com.tacticsflame.system

import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * LevelUpSystem のテスト
 */
class LevelUpSystemTest {

    private lateinit var levelUpSystem: LevelUpSystem

    /** テスト用ユニットを生成する */
    private fun createUnit(
        exp: Int = 0,
        level: Int = 1,
        growthRate: GrowthRate = GrowthRate(
            hp = 1.0f, str = 1.0f, mag = 1.0f, skl = 1.0f,
            spd = 1.0f, lck = 1.0f, def = 1.0f, res = 1.0f
        )
    ): GameUnit {
        return GameUnit(
            id = "test_unit", name = "テストユニット",
            unitClass = UnitClass(
                id = "testClass", name = "テストクラス",
                moveType = MoveType.INFANTRY, baseMov = 5,
                usableWeapons = listOf(WeaponType.SWORD),
                baseStats = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
                canDualWield = true, dualWieldPenalty = 0,
                classGrowthRate = growthRate
            ),
            faction = Faction.PLAYER,
            level = level, exp = exp,
            personalModifier = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            personalGrowthRate = growthRate
        )
    }

    @BeforeTest
    fun setUp() {
        levelUpSystem = LevelUpSystem()
    }

    @Test
    fun `経験値付与でレベルアップしない場合はnullを返す`() {
        val unit = createUnit(exp = 0)
        val result = levelUpSystem.awardExp(unit, 30)

        assertNull(result)
        assertEquals(30, unit.exp)
        assertEquals(1, unit.level)
    }

    @Test
    fun `経験値付与でレベルアップした場合はLevelUpResultを返す`() {
        val unit = createUnit(exp = 80)
        val result = levelUpSystem.awardExp(unit, 30)

        assertNotNull(result)
        assertEquals(unit, result.unit)
        assertEquals(2, result.newLevel)
        // 成長率100%なので全ステータスが成長しているはず
        assertEquals(1, result.growthResult.hp)
        assertEquals(1, result.growthResult.str)
    }

    @Test
    fun `戦闘経験値のフルフローテスト`() {
        val unit = createUnit(exp = 0, growthRate = GrowthRate())

        // 3回の戦闘（30EXP x 3 = 90）
        for (i in 1..3) {
            val result = levelUpSystem.awardExp(unit, 30)
            assertNull(result, "まだレベルアップしないはず")
        }
        assertEquals(90, unit.exp)

        // 4回目の戦闘でレベルアップ（90 + 30 = 120 → Lv2, EXP=20）
        val result = levelUpSystem.awardExp(unit, 30)
        assertNotNull(result, "レベルアップするはず")
        assertEquals(2, result.newLevel)
        assertEquals(20, unit.exp)
    }

    @Test
    fun `レベルアップ後のステータスが正しく反映される`() {
        val unit = createUnit(
            exp = 99,
            growthRate = GrowthRate(hp = 1.0f, str = 1.0f)
        )
        val initialHp = unit.stats.hp
        val initialStr = unit.stats.str

        val result = levelUpSystem.awardExp(unit, 1)

        assertNotNull(result)
        assertEquals(initialHp + 1f, unit.stats.hp)
        assertEquals(initialStr + 1f, unit.stats.str)
    }

    /**
     * 味方共通化仕様の検証: personalModifier / personalGrowthRate を使わず
     * クラス値（baseStats / classGrowthRate）のみで成長することを確認する
     *
     * LORD classGrowthRate:
     *   hp=1.60, str=0.40, mag=0.10, skl=0.35, spd=0.30, lck=0.25, def=0.25, res=0.15
     *
     * Lv1 stats = LORD baseStats(20,6,1,7,7,5,5,2)
     * Lv5 stats = Lv1 stats + classGrowthRate × 4
     *   hp  = 20 + 1.60×4 = 26.40 → 26
     *   str =  6 + 0.40×4 =  7.60 →  7
     *   mag =  1 + 0.10×4 =  1.40 →  1
     *   skl =  7 + 0.35×4 =  8.40 →  8
     *   spd =  7 + 0.30×4 =  8.20 →  8
     *   lck =  5 + 0.25×4 =  6.00 →  6
     *   def =  5 + 0.25×4 =  6.00 →  6
     *   res =  2 + 0.15×4 =  2.60 →  2
     */
    @Test
    fun `アレスのレベルアップで仕様10のシミュレーション値と一致すること`() {
        val ares = GameUnit(
            id = "test_ares", name = "アレス",
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate(),
            isLord = true
        )

        // 4回レベルアップ（Lv1 → Lv5）
        repeat(4) { ares.gainExp(100) }

        assertEquals(5, ares.level, "レベルが5であること")

        // クラス値のみのシミュレーション値と一致することを検証
        val stats = ares.stats
        assertEquals(26, stats.effectiveHp, "HP: 20 + 1.60×4 = 26.40 → 26")
        assertEquals(7, stats.effectiveStr, "STR: 6 + 0.40×4 = 7.60 → 7")
        assertEquals(1, stats.effectiveMag, "MAG: 1 + 0.10×4 = 1.40 → 1")
        assertEquals(8, stats.effectiveSkl, "SKL: 7 + 0.35×4 = 8.40 → 8")
        assertEquals(8, stats.effectiveSpd, "SPD: 7 + 0.30×4 = 8.20 → 8")
        assertEquals(6, stats.effectiveLck, "LCK: 5 + 0.25×4 = 6.00 → 6")
        assertEquals(6, stats.effectiveDef, "DEF: 5 + 0.25×4 = 6.00 → 6")
        assertEquals(2, stats.effectiveRes, "RES: 2 + 0.15×4 = 2.60 → 2")
    }
}
