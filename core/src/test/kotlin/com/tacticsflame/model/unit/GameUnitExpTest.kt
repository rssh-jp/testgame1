package com.tacticsflame.model.unit

import com.tacticsflame.core.GameConfig
import kotlin.test.*

/**
 * GameUnit の経験値・レベルアップシステムのテスト
 */
class GameUnitExpTest {

    /** テスト用ユニットを生成する */
    private fun createUnit(
        level: Int = 1,
        exp: Int = 0,
        spd: Int = 8,
        growthRate: GrowthRate = GrowthRate(
            hp = 1.0f, str = 1.0f, mag = 1.0f, skl = 1.0f,
            spd = 1.0f, lck = 1.0f, def = 1.0f, res = 1.0f
        )
    ): GameUnit {
        return GameUnit(
            id = "test_unit", name = "テストユニット",
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            level = level, exp = exp,
            stats = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = spd.toFloat(), lck = 5f, def = 5f, res = 2f),
            growthRate = growthRate
        )
    }

    // ==================== 経験値加算テスト ====================

    @Test
    fun `経験値加算で100未満ならレベルアップしない`() {
        val unit = createUnit(exp = 0)
        val result = unit.gainExp(50)

        assertNull(result, "レベルアップしていないので null が返るべき")
        assertEquals(50, unit.exp)
        assertEquals(1, unit.level)
    }

    @Test
    fun `経験値が100に達するとレベルアップする`() {
        val unit = createUnit(exp = 70)
        val result = unit.gainExp(30)

        assertNotNull(result, "レベルアップしたので成長結果が返るべき")
        assertEquals(2, unit.level)
        assertEquals(0, unit.exp, "100 - 100 = 0 の繰り越し")
    }

    @Test
    fun `経験値が100を超える場合は繰り越される`() {
        val unit = createUnit(exp = 80)
        val result = unit.gainExp(40)

        assertNotNull(result)
        assertEquals(2, unit.level)
        assertEquals(20, unit.exp, "120 - 100 = 20 の繰り越し")
    }

    @Test
    fun `経験値0でゲインしてもレベルアップしない`() {
        val unit = createUnit(exp = 0)
        val result = unit.gainExp(0)

        assertNull(result)
        assertEquals(0, unit.exp)
        assertEquals(1, unit.level)
    }

    @Test
    fun `EXP_TO_LEVEL_UP定数が100であることを確認`() {
        assertEquals(100, GameConfig.EXP_TO_LEVEL_UP)
    }

    // ==================== レベルアップ時のステータス成長テスト ====================

    @Test
    fun `レベルアップ時に成長率100で全ステータスが上昇する`() {
        val unit = createUnit(
            exp = 99,
            growthRate = GrowthRate(
                hp = 1.0f, str = 1.0f, mag = 1.0f, skl = 1.0f,
                spd = 1.0f, lck = 1.0f, def = 1.0f, res = 1.0f
            )
        )
        val initialStats = unit.stats.copy()
        val result = unit.gainExp(1)

        assertNotNull(result)
        assertEquals(2, unit.level)
        // 成長率100%なので全ステータスが +1
        assertEquals(1, result.hp)
        assertEquals(1, result.str)
        assertEquals(1, result.mag)
        assertEquals(1, result.skl)
        assertEquals(1, result.spd)
        assertEquals(1, result.lck)
        assertEquals(1, result.def)
        assertEquals(1, result.res)
        // ステータスが実際に上昇していること
        assertEquals(initialStats.hp + 1f, unit.stats.hp)
        assertEquals(initialStats.str + 1f, unit.stats.str)
    }

    @Test
    fun `レベルアップ時に成長率0ではステータスが上昇しない`() {
        val unit = createUnit(
            exp = 99,
            growthRate = GrowthRate(
                hp = 0f, str = 0f, mag = 0f, skl = 0f,
                spd = 0f, lck = 0f, def = 0f, res = 0f
            )
        )
        val initialStats = unit.stats.copy()
        val result = unit.gainExp(1)

        assertNotNull(result)
        assertEquals(2, unit.level)
        // 成長率0%なので全ステータス +0
        assertEquals(0, result.hp)
        assertEquals(0, result.str)
        assertEquals(0, result.mag)
        assertEquals(0, result.skl)
        assertEquals(0, result.spd)
        assertEquals(0, result.lck)
        assertEquals(0, result.def)
        assertEquals(0, result.res)
        // ステータスが変化していないこと
        assertEquals(initialStats.hp, unit.stats.hp)
        assertEquals(initialStats.str, unit.stats.str)
    }

    @Test
    fun `レベルアップ時にcurrentHpがHP成長分だけ回復する`() {
        val unit = createUnit(
            exp = 99,
            growthRate = GrowthRate(hp = 1.0f) // HPのみ100%成長
        )
        // ダメージを受けた状態
        unit.takeDamage(5)
        val hpBefore = unit.currentHp // 15

        val result = unit.gainExp(1)
        assertNotNull(result)
        assertEquals(1, result.hp)
        // currentHp = 15 + 1(HP成長分) = 16
        assertEquals(hpBefore + 1, unit.currentHp)
    }

    // ==================== 連続レベルアップテスト ====================

    @Test
    fun `複数回の経験値獲得でレベルが着実に上がる`() {
        val unit = createUnit(
            exp = 0,
            growthRate = GrowthRate() // 成長率0%
        )

        // 4回の戦闘（各25EXP）→ レベルアップ
        for (i in 1..3) {
            val result = unit.gainExp(25)
            assertNull(result, "まだレベルアップしないはず (EXP: ${unit.exp})")
        }
        assertEquals(75, unit.exp)
        assertEquals(1, unit.level)

        val result = unit.gainExp(25)
        assertNotNull(result, "100に達したのでレベルアップ")
        assertEquals(2, unit.level)
        assertEquals(0, unit.exp)
    }

    // ==================== レベル上限なしテスト ====================

    @Test
    fun `高レベルでも経験値が加算されレベルアップできる`() {
        val unit = createUnit(level = 99, exp = 0)
        val result = unit.gainExp(100)

        assertNotNull(result, "レベル上限がないためレベルアップする")
        assertEquals(100, unit.level)
        assertEquals(0, unit.exp)
    }

    // ==================== 入力バリデーションテスト ====================

    @Test
    fun `gainExpに負の値を渡しても経験値は減らない`() {
        val unit = createUnit(exp = 50)
        val result = unit.gainExp(-10)

        assertNull(result)
        assertEquals(50, unit.exp, "負の値は0に補正されるため変化しない")
    }

    @Test
    fun `gainExpにちょうど100を渡すとレベルアップする`() {
        val unit = createUnit(exp = 0, growthRate = GrowthRate())
        val result = unit.gainExp(100)

        assertNotNull(result)
        assertEquals(2, unit.level)
        assertEquals(0, unit.exp)
    }
}
