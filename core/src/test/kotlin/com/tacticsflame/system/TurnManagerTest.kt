package com.tacticsflame.system

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * TurnManager（CTベース行動順管理）のテスト
 */
class TurnManagerTest {

    private lateinit var turnManager: TurnManager

    /** テスト用ユニットを生成する */
    private fun createUnit(id: String, name: String, spd: Int, faction: Faction = Faction.PLAYER): GameUnit {
        return GameUnit(
            id = id, name = name,
            unitClass = UnitClass(
                id = "testClass", name = "テストクラス",
                moveType = MoveType.INFANTRY, baseMov = 5,
                usableWeapons = listOf(WeaponType.SWORD),
                baseStats = Stats(hp = 20f, str = 5f, mag = 0f, skl = 5f, spd = spd.toFloat(), lck = 5f, def = 5f, res = 5f),
                canDualWield = true, dualWieldPenalty = 0
            ),
            faction = faction,
            personalModifier = Stats(hp = 20f, str = 5f, mag = 0f, skl = 5f, spd = spd.toFloat(), lck = 5f, def = 5f, res = 5f),
            personalGrowthRate = GrowthRate()
        )
    }

    @BeforeTest
    fun setUp() {
        turnManager = TurnManager()
    }

    @Test
    fun `初期状態はラウンド1で累計行動0`() {
        val units = listOf(createUnit("u1", "ユニットA", 5))
        turnManager.reset(units)

        assertEquals(1, turnManager.roundNumber)
        assertEquals(0, turnManager.totalActions)
        assertNull(turnManager.activeUnit)
    }

    @Test
    fun `リセットで全ユニットのCTが0になる`() {
        val units = listOf(
            createUnit("u1", "ユニットA", 5),
            createUnit("u2", "ユニットB", 8)
        )
        units[0].ct = 50
        units[1].ct = 80

        turnManager.reset(units)

        assertEquals(0, units[0].ct)
        assertEquals(0, units[1].ct)
    }

    @Test
    fun `SPDが最も高いユニットが最初に行動する`() {
        val slow = createUnit("u1", "遅いユニット", 3)
        val fast = createUnit("u2", "速いユニット", 9)
        val medium = createUnit("u3", "普通のユニット", 5)
        val units = listOf(slow, fast, medium)

        turnManager.reset(units)
        val firstActor = turnManager.advanceToNextUnit(units)

        assertEquals(fast, firstActor)
        assertEquals(fast, turnManager.activeUnit)
    }

    @Test
    fun `CT超過分は持ち越される`() {
        val unit = createUnit("u1", "テスト", 8)
        val units = listOf(unit)

        turnManager.reset(units)
        turnManager.advanceToNextUnit(units)

        // SPD=8 なので 13ティック後に CT=104 で行動権取得
        val ctBeforeAction = unit.ct
        assertTrue(ctBeforeAction >= GameConfig.CT_THRESHOLD)

        turnManager.completeAction(unit, units)

        // CT -= 100 なので超過分が持ち越される
        assertEquals(ctBeforeAction - GameConfig.CT_THRESHOLD, unit.ct)
    }

    @Test
    fun `速いユニットは遅いユニットより頻繁に行動する`() {
        val fast = createUnit("u1", "速い", 9, Faction.PLAYER)
        val slow = createUnit("u2", "遅い", 3, Faction.ENEMY)
        val units = listOf(fast, slow)

        turnManager.reset(units)

        var fastActCount = 0
        var slowActCount = 0

        // 10回の行動を追跡
        repeat(10) {
            val actor = turnManager.advanceToNextUnit(units)!!
            if (actor == fast) fastActCount++
            else slowActCount++
            turnManager.completeAction(actor, units)
        }

        // SPD 9 vs 3 なので速いユニットが約3倍行動するはず
        assertTrue(fastActCount > slowActCount, "fast=$fastActCount, slow=$slowActCount")
    }

    @Test
    fun `全ユニット行動でラウンドが進む`() {
        val u1 = createUnit("u1", "A", 5, Faction.PLAYER)
        val u2 = createUnit("u2", "B", 5, Faction.ENEMY)
        val units = listOf(u1, u2)

        turnManager.reset(units)
        assertEquals(1, turnManager.roundNumber)

        // 1人目行動
        val first = turnManager.advanceToNextUnit(units)!!
        turnManager.completeAction(first, units)
        assertEquals(1, turnManager.roundNumber)  // まだ全員行動していない

        // 2人目行動
        val second = turnManager.advanceToNextUnit(units)!!
        assertNotEquals(first, second)  // 別のユニットが行動
        turnManager.completeAction(second, units)
        assertEquals(2, turnManager.roundNumber)  // 全員行動したのでラウンド+1
    }

    @Test
    fun `戦闘不能ユニットは行動しない`() {
        val alive = createUnit("u1", "生存", 5)
        val dead = createUnit("u2", "戦闘不能", 9)
        dead.takeDamage(100)  // HPを0にする
        assertTrue(dead.isDefeated)

        val units = listOf(alive, dead)
        turnManager.reset(units)

        val actor = turnManager.advanceToNextUnit(units)
        assertEquals(alive, actor)
    }

    @Test
    fun `行動順予測が正しく動作する`() {
        val fast = createUnit("u1", "速い", 9)
        val slow = createUnit("u2", "遅い", 3, Faction.ENEMY)
        val units = listOf(fast, slow)

        turnManager.reset(units)

        val predicted = turnManager.predictActionOrder(units, 5)

        assertEquals(5, predicted.size)
        // 最初は速いユニットが行動するはず
        assertEquals(fast, predicted[0])
    }

    @Test
    fun `同じSPDの場合はCTが高い方が優先`() {
        val u1 = createUnit("u1", "ユニットA", 5, Faction.PLAYER)
        val u2 = createUnit("u2", "ユニットB", 5, Faction.ENEMY)
        val units = listOf(u1, u2)

        turnManager.reset(units)

        // 手動でCTを設定して同時に閾値到達させる
        u1.ct = 105
        u2.ct = 102

        val actor = turnManager.advanceToNextUnit(units)
        // CT値が高いu1が優先
        assertEquals(u1, actor)
    }

    @Test
    fun `ユニットが1体のみでも正常に動作する`() {
        val unit = createUnit("u1", "ソロ", 5)
        val units = listOf(unit)

        turnManager.reset(units)

        val actor = turnManager.advanceToNextUnit(units)
        assertEquals(unit, actor)

        turnManager.completeAction(unit, units)
        assertEquals(2, turnManager.roundNumber)  // 1体なので即ラウンド更新
        assertEquals(1, turnManager.totalActions)
    }

    @Test
    fun `空リストではnullを返す`() {
        val units = emptyList<GameUnit>()
        turnManager.reset(units)

        val actor = turnManager.advanceToNextUnit(units)
        assertNull(actor)
    }

    // ==================== 装備重量によるCT蓄積テスト ====================

    @Test
    fun `重い装備のユニットはCT蓄積が遅く行動が遅れる`() {
        // 同じSPDだが装備重量が異なる2ユニット
        val light = createUnit("u1", "軽装", 10)
        val heavy = createUnit("u2", "重装", 10, Faction.ENEMY)
        heavy.rightHand = Weapon("axe", "重い斧", WeaponType.AXE, might = 10, hit = 70, weight = 5)
        heavy.armorSlot1 = Armor("armor", "重鎧", ArmorType.HEAVY_ARMOR, defBonus = 5, resBonus = 0, weight = 5)

        val units = listOf(light, heavy)
        turnManager.reset(units)

        // light: effectiveSpeed = 10（装備なし）
        // heavy: effectiveSpeed = 10 - 5 - 5 = 0 → CT加算は max(1, 0) = 1
        val firstActor = turnManager.advanceToNextUnit(units)
        assertEquals(light, firstActor, "軽装ユニットが重装ユニットより先に行動するべき")
    }

    @Test
    fun `装備重量でeffectiveSpeedが同一ならCTで公平に行動する`() {
        val u1 = createUnit("u1", "ユニットA", 10)
        val sword = Weapon("sword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3)
        u1.rightHand = sword // effectiveSpeed = 10 - 3 = 7

        val u2 = createUnit("u2", "ユニットB", 10, Faction.ENEMY)
        val lightArmor = Armor("armor", "革鎧", ArmorType.LIGHT_ARMOR, defBonus = 1, resBonus = 0, weight = 3)
        u2.armorSlot1 = lightArmor // effectiveSpeed = 10 - 3 = 7

        val units = listOf(u1, u2)
        turnManager.reset(units)

        // 同じeffectiveSpeed(7)なので、行動回数はほぼ均等のはず
        var u1Count = 0
        var u2Count = 0
        repeat(10) {
            val actor = turnManager.advanceToNextUnit(units)!!
            if (actor == u1) u1Count++ else u2Count++
            turnManager.completeAction(actor, units)
        }
        assertEquals(5, u1Count, "同速度なので行動回数は均等")
        assertEquals(5, u2Count, "同速度なので行動回数は均等")
    }

    @Test
    fun `重い装備のユニットは行動頻度が低い`() {
        val fast = createUnit("u1", "軽装", 10) // effectiveSpeed = 10
        val heavy = createUnit("u2", "重装", 10, Faction.ENEMY)
        val heavyWeapon = Weapon("heavy", "巨大斧", WeaponType.AXE, might = 15, hit = 60, weight = 8)
        heavy.rightHand = heavyWeapon
        // heavy effectiveSpeed = 10 - 8 = 2

        val units = listOf(fast, heavy)
        turnManager.reset(units)

        var fastCount = 0
        var heavyCount = 0
        repeat(10) {
            val actor = turnManager.advanceToNextUnit(units)!!
            if (actor == fast) fastCount++ else heavyCount++
            turnManager.completeAction(actor, units)
        }

        // effectiveSpeed 10 vs 2 → 軽装が圧倒的に多く行動するはず
        assertTrue(fastCount > heavyCount, "軽装=$fastCount, 重装=$heavyCount: 軽装が多く行動すべき")
    }
}
