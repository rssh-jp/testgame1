package com.tacticsflame.system

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * BattleSystem の経験値計算テスト
 */
class BattleSystemExpTest {

    private lateinit var battleSystem: BattleSystem

    /** テスト用ユニットを生成する */
    private fun createUnit(
        id: String = "test",
        name: String = "テスト",
        level: Int = 1,
        faction: Faction = Faction.PLAYER,
        hp: Int = 20,
        isDefeated: Boolean = false
    ): GameUnit {
        val unit = GameUnit(
            id = id, name = name,
            unitClass = UnitClass(
                id = "testClass", name = "テストクラス",
                moveType = MoveType.INFANTRY, baseMov = 5,
                usableWeapons = listOf(WeaponType.SWORD),
                baseStats = Stats(hp = hp.toFloat(), str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
                canDualWield = true, dualWieldPenalty = 0
            ),
            faction = faction,
            level = level,
            personalModifier = Stats(hp = hp.toFloat(), str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            personalGrowthRate = GrowthRate()
        )
        if (isDefeated) {
            unit.takeDamage(hp) // HPを0にする
        }
        return unit
    }

    @BeforeTest
    fun setUp() {
        battleSystem = BattleSystem()
    }

    // ==================== 基本経験値計算テスト ====================

    @Test
    fun `同レベルの敵を倒さなかった場合の基本経験値`() {
        val attacker = createUnit(level = 1)
        val defender = createUnit(level = 1, faction = Faction.ENEMY)

        val exp = battleSystem.calculateExp(attacker, defender)

        // baseExp(30) + levelDiff(0) * 3 + bonus(0) = 30
        assertEquals(30, exp)
    }

    @Test
    fun `同レベルの敵を撃破した場合は撃破ボーナスが加算される`() {
        val attacker = createUnit(level = 1)
        val defender = createUnit(level = 1, faction = Faction.ENEMY, isDefeated = true)

        val exp = battleSystem.calculateExp(attacker, defender)

        // baseExp(30) + levelDiff(0) * 3 + bonus(20) = 50
        assertEquals(50, exp)
    }

    @Test
    fun `高レベルの敵と戦うと経験値が増える`() {
        val attacker = createUnit(level = 1)
        val defender = createUnit(level = 5, faction = Faction.ENEMY)

        val exp = battleSystem.calculateExp(attacker, defender)

        // baseExp(30) + levelDiff(4) * 3 + bonus(0) = 42
        assertEquals(42, exp)
    }

    @Test
    fun `低レベルの敵と戦うと経験値が減る`() {
        val attacker = createUnit(level = 5)
        val defender = createUnit(level = 1, faction = Faction.ENEMY)

        val exp = battleSystem.calculateExp(attacker, defender)

        // baseExp(30) + levelDiff(-4) * 3 + bonus(0) = 18
        assertEquals(18, exp)
    }

    @Test
    fun `高レベルの敵を撃破すると多くの経験値を得る`() {
        val attacker = createUnit(level = 1)
        val defender = createUnit(level = 10, faction = Faction.ENEMY, isDefeated = true)

        val exp = battleSystem.calculateExp(attacker, defender)

        // baseExp(30) + levelDiff(9) * 3 + bonus(20) = 77
        assertEquals(77, exp)
    }

    // ==================== 経験値のクランプテスト ====================

    @Test
    fun `経験値は最低1になる`() {
        val attacker = createUnit(level = 20)
        val defender = createUnit(level = 1, faction = Faction.ENEMY)

        val exp = battleSystem.calculateExp(attacker, defender)

        // baseExp(30) + levelDiff(-19) * 3 = -27 → clamp to 1
        assertEquals(GameConfig.EXP_MIN, exp)
    }

    @Test
    fun `経験値は最大100になる`() {
        val attacker = createUnit(level = 1)
        val defender = createUnit(level = 30, faction = Faction.ENEMY, isDefeated = true)

        val exp = battleSystem.calculateExp(attacker, defender)

        // baseExp(30) + levelDiff(29) * 3 + bonus(20) = 137 → clamp to 100
        assertEquals(GameConfig.EXP_MAX, exp)
    }

    // ==================== 定数確認テスト ====================

    @Test
    fun `経験値定数が仕様通りであることを確認`() {
        assertEquals(30, GameConfig.EXP_BASE, "基本経験値は30")
        assertEquals(3, GameConfig.EXP_LEVEL_DIFF_MULTIPLIER, "レベル差補正係数は3")
        assertEquals(20, GameConfig.EXP_DEFEAT_BONUS, "撃破ボーナスは20")
        assertEquals(1, GameConfig.EXP_MIN, "最小経験値は1")
        assertEquals(100, GameConfig.EXP_MAX, "最大経験値は100")
    }
}
