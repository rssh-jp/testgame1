package com.tacticsflame.system

import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * PathFinder のテスト
 *
 * 移動範囲計算（getMovablePositions）と経路探索（findPath）において、
 * 敵ユニットのブロック（すり抜け防止）が正しく動作することを検証する。
 */
class PathFinderTest {

    private lateinit var pathFinder: PathFinder

    @BeforeTest
    fun setup() {
        pathFinder = PathFinder()
    }

    // ==================== ヘルパーメソッド ====================

    /**
     * 指定サイズの平地マップを生成する
     */
    private fun createPlainMap(width: Int = 10, height: Int = 10): BattleMap {
        val tiles = Array(height) { y ->
            Array(width) { x ->
                Tile(Position(x, y), TerrainType.PLAIN)
            }
        }
        return BattleMap(id = "test", name = "テストマップ", width = width, height = height, tiles = tiles)
    }

    /**
     * テスト用のプレイヤーユニットを生成する（移動力5）
     */
    private fun createPlayerUnit(id: String = "player_01"): GameUnit {
        return GameUnit(
            id = id,
            name = "プレイヤー",
            unitClass = UnitClass.LORD,
            faction = Faction.PLAYER,
            stats = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            growthRate = GrowthRate()
        )
    }

    /**
     * テスト用の敵ユニットを生成する
     */
    private fun createEnemyUnit(id: String = "enemy_01"): GameUnit {
        return GameUnit(
            id = id,
            name = "敵兵",
            unitClass = UnitClass.SWORD_FIGHTER,
            faction = Faction.ENEMY,
            stats = Stats(hp = 18f, str = 5f, mag = 0f, skl = 5f, spd = 5f, lck = 3f, def = 4f, res = 1f),
            growthRate = GrowthRate()
        )
    }

    // ==================== getMovablePositions テスト ====================

    @Test
    fun `敵ユニットがいるマスは移動可能範囲に含まれない`() {
        val map = createPlainMap()
        val player = createPlayerUnit()
        val enemy = createEnemyUnit()

        // プレイヤーを(2,2)、敵を(3,2)に配置
        map.placeUnit(player, Position(2, 2))
        map.placeUnit(enemy, Position(3, 2))

        val movable = pathFinder.getMovablePositions(player, Position(2, 2), map)

        // 敵がいる(3,2)は移動可能マスに含まれない
        assertFalse(Position(3, 2) in movable, "敵がいるマスは移動不可であるべき")
    }

    @Test
    fun `敵ユニットを通過して奥のマスには到達できない（一直線）`() {
        // 5x1の一直線マップ: P . E . .
        val map = createPlainMap(width = 7, height = 1)
        val player = createPlayerUnit()  // 移動力5
        val enemy = createEnemyUnit()

        map.placeUnit(player, Position(0, 0))
        map.placeUnit(enemy, Position(2, 0))

        val movable = pathFinder.getMovablePositions(player, Position(0, 0), map)

        // (1,0) は到達可能
        assertTrue(Position(1, 0) in movable, "敵の手前は移動可能であるべき")
        // (2,0) の敵マスは不可
        assertFalse(Position(2, 0) in movable, "敵のマスは移動不可であるべき")
        // (3,0) 以降は敵を通過しないと到達不可（一直線なので）
        assertFalse(Position(3, 0) in movable, "敵の奥は到達不可であるべき")
    }

    @Test
    fun `味方ユニットのマスは通過可能だが停止不可`() {
        val map = createPlainMap(width = 5, height = 1)
        val player1 = createPlayerUnit("player_01")
        val player2 = createPlayerUnit("player_02")

        map.placeUnit(player1, Position(0, 0))
        map.placeUnit(player2, Position(1, 0))

        val movable = pathFinder.getMovablePositions(player1, Position(0, 0), map)

        // 味方がいる(1,0)は停止不可
        assertFalse(Position(1, 0) in movable, "味方がいるマスは停止不可であるべき")
        // 味方の先の(2,0)は通過して到達可能
        assertTrue(Position(2, 0) in movable, "味方を通過した先のマスは移動可能であるべき")
    }

    // ==================== findPath テスト ====================

    @Test
    fun `findPathは敵ユニットを通過する経路を生成しない`() {
        // 5x3マップで敵が中央にいるケース
        //   0 1 2 3 4
        // 0 . . . . .
        // 1 P . E . G   (P=プレイヤー, E=敵, G=ゴール)
        // 2 . . . . .
        val map = createPlainMap(width = 5, height = 3)
        val player = createPlayerUnit()
        val enemy = createEnemyUnit()

        map.placeUnit(player, Position(0, 1))
        map.placeUnit(enemy, Position(2, 1))

        val path = pathFinder.findPath(player, Position(0, 1), Position(4, 1), map)

        // 経路が見つかるべき（迂回経路あり）
        assertTrue(path.isNotEmpty(), "迂回経路が見つかるべき")
        // 経路に敵のマス(2,1)が含まれていないことを確認
        assertFalse(Position(2, 1) in path, "経路に敵がいるマスが含まれてはいけない")
    }

    @Test
    fun `findPathは敵のいないマスを迂回して経路を生成する`() {
        // 7x3マップ:
        //   0 1 2 3 4 5 6
        // 0 . . . . . . .
        // 1 P . . E . . G
        // 2 . . . . . . .
        val map = createPlainMap(width = 7, height = 3)
        val player = createPlayerUnit()
        val enemy = createEnemyUnit()

        map.placeUnit(player, Position(0, 1))
        map.placeUnit(enemy, Position(3, 1))

        val path = pathFinder.findPath(player, Position(0, 1), Position(6, 1), map)

        assertTrue(path.isNotEmpty(), "迂回経路が見つかるべき")
        assertFalse(Position(3, 1) in path, "経路に敵マスが含まれてはいけない")
        // 経路の最後がゴールであること
        assertEquals(Position(6, 1), path.last(), "経路の終点はゴールであるべき")
    }

    @Test
    fun `findPathで敵に完全に塞がれた場合は空リストを返す`() {
        // 3x3マップ: 敵が壁のように塞ぐ
        //   0 1 2
        // 0 . E .
        // 1 P E G
        // 2 . E .
        val map = createPlainMap(width = 3, height = 3)
        val player = createPlayerUnit()

        map.placeUnit(player, Position(0, 1))
        map.placeUnit(createEnemyUnit("e1"), Position(1, 0))
        map.placeUnit(createEnemyUnit("e2"), Position(1, 1))
        map.placeUnit(createEnemyUnit("e3"), Position(1, 2))

        val path = pathFinder.findPath(player, Position(0, 1), Position(2, 1), map)

        // 敵に完全に塞がれているので経路なし
        assertTrue(path.isEmpty(), "敵に完全に塞がれた場合は到達不可であるべき")
    }

    @Test
    fun `findPathは味方ユニットを通過できる`() {
        // 5x1マップ: P A . . G  (A=味方)
        val map = createPlainMap(width = 5, height = 1)
        val player1 = createPlayerUnit("player_01")
        val player2 = createPlayerUnit("player_02")

        map.placeUnit(player1, Position(0, 0))
        map.placeUnit(player2, Position(1, 0))

        val path = pathFinder.findPath(player1, Position(0, 0), Position(4, 0), map)

        assertTrue(path.isNotEmpty(), "味方を通過する経路が見つかるべき")
        // 味方のマスを含む直線経路が可能
        assertTrue(Position(1, 0) in path, "味方のマスを通過できるべき")
        assertEquals(Position(4, 0), path.last(), "経路の終点はゴールであるべき")
    }

    @Test
    fun `findPathはゴール地点に敵がいる場合は空リストを返す`() {
        val map = createPlainMap(width = 3, height = 1)
        val player = createPlayerUnit()
        val enemy = createEnemyUnit()

        map.placeUnit(player, Position(0, 0))
        map.placeUnit(enemy, Position(2, 0))

        val path = pathFinder.findPath(player, Position(0, 0), Position(2, 0), map)

        assertTrue(path.isEmpty(), "敵がゴール地点にいる場合は到達不可であるべき")
    }
}
