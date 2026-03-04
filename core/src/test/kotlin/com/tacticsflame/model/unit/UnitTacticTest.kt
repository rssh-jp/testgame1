package com.tacticsflame.model.unit

import kotlin.test.*

/**
 * UnitTactic（作戦）の列挙型テスト
 *
 * サイクル切り替え、デフォルト値、表示名・説明文の検証を行う。
 */
class UnitTacticTest {

    // ==================== next() サイクルテスト ====================

    @Test
    fun `CHARGEの次はCAUTIOUS`() {
        assertEquals(UnitTactic.CAUTIOUS, UnitTactic.CHARGE.next())
    }

    @Test
    fun `CAUTIOUSの次はSUPPORT`() {
        assertEquals(UnitTactic.SUPPORT, UnitTactic.CAUTIOUS.next())
    }

    @Test
    fun `SUPPORTの次はFLEE`() {
        assertEquals(UnitTactic.FLEE, UnitTactic.SUPPORT.next())
    }

    @Test
    fun `FLEEの次はCHARGE（先頭に戻る）`() {
        assertEquals(UnitTactic.CHARGE, UnitTactic.FLEE.next())
    }

    @Test
    fun `全作戦を一周するとCHARGEに戻る`() {
        var tactic = UnitTactic.CHARGE
        val visited = mutableListOf(tactic)
        repeat(UnitTactic.entries.size) {
            tactic = tactic.next()
            visited.add(tactic)
        }
        // 4回 next() を呼ぶと元に戻る
        assertEquals(UnitTactic.CHARGE, visited.last(), "一周してCHARGEに戻る")
        assertEquals(UnitTactic.entries.size + 1, visited.size)
    }

    // ==================== デフォルト作戦テスト ====================

    @Test
    fun `GameUnit作成時のデフォルト作戦はCHARGE`() {
        val unit = GameUnit(
            id = "test_01",
            name = "テスト兵士",
            unitClass = UnitClass.LORD,
            faction = Faction.PLAYER,
            stats = Stats(hp = 20, str = 5, mag = 0, skl = 5, spd = 5, lck = 5, def = 5, res = 5),
            growthRate = GrowthRate()
        )
        assertEquals(UnitTactic.CHARGE, unit.tactic)
    }

    // ==================== 作戦の設定・取得テスト ====================

    @Test
    fun `作戦を設定した後に正しく取得できる`() {
        val unit = GameUnit(
            id = "test_02",
            name = "テスト兵士",
            unitClass = UnitClass.LORD,
            faction = Faction.PLAYER,
            stats = Stats(hp = 20, str = 5, mag = 0, skl = 5, spd = 5, lck = 5, def = 5, res = 5),
            growthRate = GrowthRate()
        )

        for (tactic in UnitTactic.entries) {
            unit.tactic = tactic
            assertEquals(tactic, unit.tactic, "${tactic.name} が正しく設定される")
        }
    }

    // ==================== displayName テスト ====================

    @Test
    fun `各作戦のdisplayNameが空でない`() {
        for (tactic in UnitTactic.entries) {
            assertTrue(tactic.displayName.isNotEmpty(), "${tactic.name} の displayName が空")
        }
    }

    // ==================== description テスト ====================

    @Test
    fun `各作戦のdescriptionが空でない`() {
        for (tactic in UnitTactic.entries) {
            assertTrue(tactic.description.isNotEmpty(), "${tactic.name} の description が空")
        }
    }
}
