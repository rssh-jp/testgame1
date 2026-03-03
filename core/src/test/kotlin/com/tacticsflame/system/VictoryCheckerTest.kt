package com.tacticsflame.system

import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * VictoryChecker（勝敗判定）のテスト
 *
 * 特に、ロードがマップから除去された後の敗北判定（バグ修正回帰テスト）を重点的に検証する。
 */
class VictoryCheckerTest {

    private lateinit var victoryChecker: VictoryChecker

    @BeforeTest
    fun setUp() {
        victoryChecker = VictoryChecker()
    }

    // ==================== ヘルパー ====================

    /**
     * テスト用の BattleMap を生成する（5×5 の平地マップ）
     */
    private fun createTestMap(): BattleMap {
        val tiles = Array(5) { y ->
            Array(5) { x ->
                Tile(Position(x, y), TerrainType.PLAIN)
            }
        }
        return BattleMap(id = "test", name = "テスト", width = 5, height = 5, tiles = tiles)
    }

    /**
     * テスト用ユニットを生成する
     */
    private fun createUnit(
        id: String,
        name: String,
        faction: Faction,
        isLord: Boolean = false,
        hp: Int = 20
    ): GameUnit {
        return GameUnit(
            id = id, name = name,
            unitClass = UnitClass.LORD, faction = faction,
            stats = Stats(hp = hp, str = 5, mag = 0, skl = 5, spd = 5, lck = 5, def = 5, res = 5),
            growthRate = GrowthRate(),
            isLord = isLord
        )
    }

    // ==================== 勝利判定テスト ====================

    @Test
    fun `敵全滅で勝利になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        map.placeUnit(lord, Position(0, 0))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(VictoryChecker.BattleOutcome.VICTORY, outcome)
    }

    @Test
    fun `敵が残っている場合は継続中になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val enemy = createUnit("enemy", "山賊", Faction.ENEMY)
        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(enemy, Position(4, 4))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(VictoryChecker.BattleOutcome.ONGOING, outcome)
    }

    // ==================== 敗北判定テスト ====================

    @Test
    fun `ロードが戦闘不能の場合は敗北になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val enemy = createUnit("enemy", "山賊", Faction.ENEMY)
        lord.takeDamage(lord.maxHp) // ロードを戦闘不能にする
        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(enemy, Position(4, 4))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(VictoryChecker.BattleOutcome.DEFEAT, outcome)
    }

    @Test
    fun `ロードがマップから除去された場合は敗北になる_バグ修正回帰テスト`() {
        // バグ再現: ロードが撃破されてマップから removeUnit された後に
        // checkOutcome が呼ばれるケース
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val ally = createUnit("ally", "リーナ", Faction.PLAYER)
        val enemy = createUnit("enemy", "山賊", Faction.ENEMY)

        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(ally, Position(1, 0))
        map.placeUnit(enemy, Position(4, 4))

        // ロードをマップから除去（敵に撃破された想定）
        map.removeUnit(Position(0, 0))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(
            VictoryChecker.BattleOutcome.DEFEAT, outcome,
            "ロードがマップから除去された場合、敗北が検出されるべき"
        )
    }

    @Test
    fun `全プレイヤーユニットがマップから除去された場合は敗北になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val enemy = createUnit("enemy", "山賊", Faction.ENEMY)
        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(enemy, Position(4, 4))

        // 全プレイヤーユニットを除去
        map.removeUnit(Position(0, 0))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(VictoryChecker.BattleOutcome.DEFEAT, outcome)
    }

    @Test
    fun `ロード以外が撤退しても継続中になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val ally = createUnit("ally", "リーナ", Faction.PLAYER)
        val enemy = createUnit("enemy", "山賊", Faction.ENEMY)

        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(ally, Position(1, 0))
        map.placeUnit(enemy, Position(4, 4))

        // ロード以外をマップから除去
        map.removeUnit(Position(1, 0))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(
            VictoryChecker.BattleOutcome.ONGOING, outcome,
            "ロードが生存している限りバトルは継続するべき"
        )
    }

    // ==================== ボス撃破条件テスト ====================

    @Test
    fun `ボス撃破で勝利になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val boss = createUnit("boss", "ボス", Faction.ENEMY, isLord = true)

        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(boss, Position(4, 4))

        // ボスを撃破してマップから除去
        map.removeUnit(Position(4, 4))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_BOSS, bossId = "boss"
        )

        assertEquals(VictoryChecker.BattleOutcome.VICTORY, outcome)
    }

    @Test
    fun `ボスが生存中は継続中になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val boss = createUnit("boss", "ボス", Faction.ENEMY, isLord = true)

        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(boss, Position(4, 4))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_BOSS, bossId = "boss"
        )

        assertEquals(VictoryChecker.BattleOutcome.ONGOING, outcome)
    }

    // ==================== ターン防衛条件テスト ====================

    @Test
    fun `防衛ターン到達で勝利になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val enemy = createUnit("enemy", "山賊", Faction.ENEMY)

        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(enemy, Position(4, 4))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.SURVIVE_TURNS,
            turnNumber = 10, targetTurn = 10
        )

        assertEquals(VictoryChecker.BattleOutcome.VICTORY, outcome)
    }

    @Test
    fun `防衛ターン未到達は継続中になる`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        val enemy = createUnit("enemy", "山賊", Faction.ENEMY)

        map.placeUnit(lord, Position(0, 0))
        map.placeUnit(enemy, Position(4, 4))

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.SURVIVE_TURNS,
            turnNumber = 5, targetTurn = 10
        )

        assertEquals(VictoryChecker.BattleOutcome.ONGOING, outcome)
    }

    // ==================== 敗北が勝利より優先されるテスト ====================

    @Test
    fun `敵全滅かつロード戦闘不能の場合は敗北が優先される`() {
        val map = createTestMap()
        val lord = createUnit("hero", "アレス", Faction.PLAYER, isLord = true)
        lord.takeDamage(lord.maxHp) // ロードを戦闘不能にする
        map.placeUnit(lord, Position(0, 0))
        // 敵なし = 勝利条件も満たしているが、敗北が優先される

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(
            VictoryChecker.BattleOutcome.DEFEAT, outcome,
            "勝利条件を満たしていても、ロードが戦闘不能なら敗北が優先されるべき"
        )
    }

    @Test
    fun `ロード除去かつ敵全滅でも敗北が優先される`() {
        val map = createTestMap()
        val ally = createUnit("ally", "リーナ", Faction.PLAYER)
        map.placeUnit(ally, Position(0, 0))
        // ロードはマップ上に存在しない、敵もいない

        val outcome = victoryChecker.checkOutcome(
            map, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        assertEquals(
            VictoryChecker.BattleOutcome.DEFEAT, outcome,
            "ロードがマップにいなければ、敵がいなくても敗北"
        )
    }
}
