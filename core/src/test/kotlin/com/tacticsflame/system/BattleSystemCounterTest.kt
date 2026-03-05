package com.tacticsflame.system

import com.tacticsflame.model.map.BattleMap
import com.tacticsflame.model.map.Position
import com.tacticsflame.model.map.TerrainType
import com.tacticsflame.model.map.Tile
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * BattleSystem の反撃（カウンターアタック）判定テスト
 *
 * 素手ユニットは射程1でのみ反撃可能。
 * 射程2の攻撃に対しては素手では反撃できない。
 */
class BattleSystemCounterTest {

    private lateinit var battleSystem: BattleSystem

    /** テスト用の平地マップを生成する */
    private fun createPlainMap(width: Int = 5, height: Int = 5): BattleMap {
        val tiles = Array(height) { y ->
            Array(width) { x ->
                Tile(Position(x, y), TerrainType.PLAIN)
            }
        }
        return BattleMap("test_map", "テストマップ", width, height, tiles)
    }

    /** テスト用ユニットを生成する */
    private fun createUnit(
        id: String,
        faction: Faction = Faction.PLAYER,
        str: Int = 8,
        spd: Int = 5,
        def: Int = 5,
        hp: Int = 50,
        weapons: MutableList<Weapon> = mutableListOf()
    ): GameUnit {
        val unit = GameUnit(
            id = id, name = "テスト$id",
            unitClass = UnitClass.LORD, faction = faction,
            stats = Stats(hp = hp.toFloat(), str = str.toFloat(), mag = 1f, skl = 7f, spd = spd.toFloat(), lck = 5f, def = def.toFloat(), res = 2f),
            growthRate = GrowthRate(),
            weapons = weapons
        )
        unit.rightHand = weapons.firstOrNull()
        return unit
    }

    @BeforeTest
    fun setUp() {
        battleSystem = BattleSystem()
    }

    @Test
    fun `素手ユニットが射程1で反撃可能`() {
        val map = createPlainMap()
        val sword = Weapon("sword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3)
        val attacker = createUnit("attacker", Faction.PLAYER, str = 6, weapons = mutableListOf(sword))
        val defender = createUnit("defender", Faction.ENEMY, str = 6, hp = 50) // 素手

        map.placeUnit(attacker, Position(0, 0))
        map.placeUnit(defender, Position(1, 0)) // マンハッタン距離 = 1

        val result = battleSystem.executeBattle(attacker, defender, map)

        // 防御側の反撃（attackerIsInitiator = false）が存在するはず
        val counterAttacks = result.attacks.filter { !it.attackerIsInitiator }
        assertTrue(counterAttacks.isNotEmpty(), "素手ユニットは射程1で反撃可能であるべき")
    }

    @Test
    fun `素手ユニットが射程2から攻撃された場合反撃不可`() {
        val map = createPlainMap()
        val bow = Weapon("bow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2)
        val attacker = createUnit("attacker", Faction.PLAYER, str = 6, weapons = mutableListOf(bow))
        val defender = createUnit("defender", Faction.ENEMY, str = 6, hp = 50) // 素手

        map.placeUnit(attacker, Position(0, 0))
        map.placeUnit(defender, Position(2, 0)) // マンハッタン距離 = 2

        val result = battleSystem.executeBattle(attacker, defender, map)

        // 防御側の反撃が存在しないはず
        val counterAttacks = result.attacks.filter { !it.attackerIsInitiator }
        assertTrue(counterAttacks.isEmpty(), "素手ユニットは射程2では反撃不可であるべき")
    }

    @Test
    fun `武器装備ユニットが射程内なら反撃可能`() {
        val map = createPlainMap()
        val bow = Weapon("bow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2)
        val attacker = createUnit("attacker", Faction.PLAYER, str = 6, weapons = mutableListOf(bow))

        val defenderBow = Weapon("bow2", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2)
        val defender = createUnit("defender", Faction.ENEMY, str = 6, hp = 50, weapons = mutableListOf(defenderBow))

        map.placeUnit(attacker, Position(0, 0))
        map.placeUnit(defender, Position(2, 0)) // マンハッタン距離 = 2

        val result = battleSystem.executeBattle(attacker, defender, map)

        // 防御側も弓（射程2）なので反撃可能
        val counterAttacks = result.attacks.filter { !it.attackerIsInitiator }
        assertTrue(counterAttacks.isNotEmpty(), "弓装備ユニットは射程2で反撃可能であるべき")
    }

    @Test
    fun `近接武器ユニットが射程2から攻撃されると反撃不可`() {
        val map = createPlainMap()
        val bow = Weapon("bow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2)
        val attacker = createUnit("attacker", Faction.PLAYER, str = 6, weapons = mutableListOf(bow))

        val sword = Weapon("sword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3)
        val defender = createUnit("defender", Faction.ENEMY, str = 6, hp = 50, weapons = mutableListOf(sword))

        map.placeUnit(attacker, Position(0, 0))
        map.placeUnit(defender, Position(2, 0)) // マンハッタン距離 = 2

        val result = battleSystem.executeBattle(attacker, defender, map)

        // 防御側は剣（射程1）なので距離2では反撃不可
        val counterAttacks = result.attacks.filter { !it.attackerIsInitiator }
        assertTrue(counterAttacks.isEmpty(), "近接武器ユニットは射程2では反撃不可であるべき")
    }
}
