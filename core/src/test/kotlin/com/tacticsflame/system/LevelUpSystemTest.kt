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
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            level = level, exp = exp,
            stats = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            growthRate = growthRate
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
}
