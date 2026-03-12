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
            personalModifier = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            personalGrowthRate = GrowthRate()
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
            personalModifier = Stats(hp = 18f, str = 5f, mag = 0f, skl = 5f, spd = 5f, lck = 3f, def = 4f, res = 1f),
            personalGrowthRate = GrowthRate()
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

    // ==================== ヘルパーメソッド（追加） ====================

    /**
     * 森を含むマップを生成する
     * forestPositions で指定した座標が森、それ以外は平地になる
     */
    private fun createMapWithForest(
        width: Int = 10,
        height: Int = 10,
        forestPositions: Set<Position>
    ): BattleMap {
        val tiles = Array(height) { y ->
            Array(width) { x ->
                val pos = Position(x, y)
                val terrain = if (pos in forestPositions) TerrainType.FOREST else TerrainType.PLAIN
                Tile(pos, terrain)
            }
        }
        return BattleMap(id = "test_forest", name = "森マップ", width = width, height = height, tiles = tiles)
    }

    /**
     * 水域を含むマップを生成する
     * waterPositions で指定した座標が水域、それ以外は平地になる
     */
    private fun createMapWithWater(
        width: Int = 10,
        height: Int = 10,
        waterPositions: Set<Position>
    ): BattleMap {
        val tiles = Array(height) { y ->
            Array(width) { x ->
                val pos = Position(x, y)
                val terrain = if (pos in waterPositions) TerrainType.WATER else TerrainType.PLAIN
                Tile(pos, terrain)
            }
        }
        return BattleMap(id = "test_water", name = "水域マップ", width = width, height = height, tiles = tiles)
    }

    /**
     * テスト用の騎馬ユニットを生成する（移動力7）
     */
    private fun createCavalryUnit(id: String = "cavalry_01"): GameUnit {
        return GameUnit(
            id = id,
            name = "ナイト",
            unitClass = UnitClass.KNIGHT,
            faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
    }

    /**
     * テスト用の飛行ユニットを生成する（移動力7）
     */
    private fun createFlyingUnit(id: String = "pegasus_01"): GameUnit {
        return GameUnit(
            id = id,
            name = "ペガサスナイト",
            unitClass = UnitClass.PEGASUS_KNIGHT,
            faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
    }

    // ==================== getMovablePositionsWithCost テスト ====================

    @Test
    fun `getMovablePositionsWithCostは移動可能マスとコストを返す`() {
        // 3x3平地マップでユニット(1,1)配置、移動力5
        val map = createPlainMap(width = 5, height = 5)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(2, 2))

        val costMap = pathFinder.getMovablePositionsWithCost(player, Position(2, 2), map)

        // 隣接マス（距離1）のコストは1
        assertEquals(1, costMap[Position(2, 1)], "上方向のコストは1であるべき")
        assertEquals(1, costMap[Position(2, 3)], "下方向のコストは1であるべき")
        assertEquals(1, costMap[Position(1, 2)], "左方向のコストは1であるべき")
        assertEquals(1, costMap[Position(3, 2)], "右方向のコストは1であるべき")

        // 斜め（距離2）のコストは2
        assertEquals(2, costMap[Position(1, 1)], "左上方向のコストは2であるべき")
        assertEquals(2, costMap[Position(3, 3)], "右下方向のコストは2であるべき")
    }

    @Test
    fun `getMovablePositionsWithCostのキーはgetMovablePositionsの結果と一致する`() {
        val map = createPlainMap(width = 10, height = 10)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(5, 5))

        val movable = pathFinder.getMovablePositions(player, Position(5, 5), map)
        val costMap = pathFinder.getMovablePositionsWithCost(player, Position(5, 5), map)

        assertEquals(movable, costMap.keys, "キーセットが一致するべき")
    }

    @Test
    fun `getMovablePositionsWithCostは森の地形コストを正しく計算する`() {
        // 5x1マップ: P(平地) 平地 森 平地 平地
        val forestPositions = setOf(Position(2, 0))
        val map = createMapWithForest(width = 5, height = 1, forestPositions = forestPositions)
        val player = createPlayerUnit() // 歩兵、移動力5
        map.placeUnit(player, Position(0, 0))

        val costMap = pathFinder.getMovablePositionsWithCost(player, Position(0, 0), map)

        // (1,0) は平地コスト1
        assertEquals(1, costMap[Position(1, 0)], "平地のコストは1")
        // (2,0) は森コスト2 → 累計3
        assertEquals(3, costMap[Position(2, 0)], "森を通るコストは累計3")
        // (3,0) は平地コスト1 → 累計4
        assertEquals(4, costMap[Position(3, 0)], "森の先のコストは累計4")
    }

    @Test
    fun `getMovablePositionsWithCostは敵がいるマスを含まない`() {
        val map = createPlainMap(width = 5, height = 1)
        val player = createPlayerUnit()
        val enemy = createEnemyUnit()

        map.placeUnit(player, Position(0, 0))
        map.placeUnit(enemy, Position(2, 0))

        val costMap = pathFinder.getMovablePositionsWithCost(player, Position(0, 0), map)

        assertFalse(Position(2, 0) in costMap, "敵がいるマスはコストマップに含まれない")
    }

    @Test
    fun `getMovablePositionsWithCostは開始位置を含まない`() {
        val map = createPlainMap(width = 5, height = 5)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(2, 2))

        val costMap = pathFinder.getMovablePositionsWithCost(player, Position(2, 2), map)

        // 開始位置自体はコストマップに含まれない（移動先ではないため）
        assertFalse(Position(2, 2) in costMap, "開始位置はコストマップに含まれない")
    }

    // ==================== getPathCostTo テスト ====================

    @Test
    fun `getPathCostToは平地で正しいコストを返す`() {
        val map = createPlainMap(width = 10, height = 1)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(0, 0))

        // 平地3マスの直線移動: コスト3
        val cost = pathFinder.getPathCostTo(player, Position(0, 0), Position(3, 0), map)
        assertEquals(3, cost, "平地3マスのコストは3であるべき")
    }

    @Test
    fun `getPathCostToはfromとtoが同じ場合0を返す`() {
        val map = createPlainMap()
        val player = createPlayerUnit()
        map.placeUnit(player, Position(3, 3))

        val cost = pathFinder.getPathCostTo(player, Position(3, 3), Position(3, 3), map)
        assertEquals(0, cost, "同一座標間のコストは0であるべき")
    }

    @Test
    fun `getPathCostToは森を通る経路のコストが高くなる`() {
        // 5x1マップ: 平地 平地 森 平地 平地
        val forestPositions = setOf(Position(2, 0))
        val map = createMapWithForest(width = 5, height = 1, forestPositions = forestPositions)
        val player = createPlayerUnit() // 歩兵
        map.placeUnit(player, Position(0, 0))

        // (0,0)→(4,0): コスト = 1 + 2 + 1 + 1 = 5（森1マス含む）
        val cost = pathFinder.getPathCostTo(player, Position(0, 0), Position(4, 0), map)
        assertEquals(5, cost, "森を含む経路のコストは5であるべき")
    }

    @Test
    fun `getPathCostToは通行不可の場合MAX_VALUEを返す`() {
        // 水域で完全に分断されたマップ
        // 3x3: 水域が y=0〜2 の x=1 列を全て埋める
        val waterPositions = setOf(Position(1, 0), Position(1, 1), Position(1, 2))
        val map = createMapWithWater(width = 3, height = 3, waterPositions = waterPositions)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(0, 1))

        val cost = pathFinder.getPathCostTo(player, Position(0, 1), Position(2, 1), map)
        assertEquals(Int.MAX_VALUE, cost, "水域で分断された場合はMAX_VALUEであるべき")
    }

    @Test
    fun `getPathCostToは敵がいるマスを迂回するコストを返す`() {
        // 5x3マップ: 敵が(2,1)にいる
        val map = createPlainMap(width = 5, height = 3)
        val player = createPlayerUnit()
        val enemy = createEnemyUnit()
        map.placeUnit(player, Position(0, 1))
        map.placeUnit(enemy, Position(2, 1))

        val costDirect = pathFinder.getPathCostTo(player, Position(0, 1), Position(4, 1), map)

        // 直線なら4だが、敵を迂回するため6（上下を回る）
        assertTrue(costDirect > 4, "敵を迂回するためコストが直線より高くなるべき")
        assertEquals(6, costDirect, "迂回コストは6であるべき")
    }

    @Test
    fun `getPathCostToは騎馬ユニットの森コストが高い`() {
        // 5x1マップ: 平地 平地 森 平地 平地
        val forestPositions = setOf(Position(2, 0))
        val map = createMapWithForest(width = 5, height = 1, forestPositions = forestPositions)
        val cavalry = createCavalryUnit()
        map.placeUnit(cavalry, Position(0, 0))

        // 騎馬の森コスト=3（歩兵は2）: コスト = 1 + 3 + 1 + 1 = 6
        val cost = pathFinder.getPathCostTo(cavalry, Position(0, 0), Position(4, 0), map)
        assertEquals(6, cost, "騎馬ユニットの森コストは3であるべき（合計6）")
    }

    @Test
    fun `getPathCostToは飛行ユニットで森コスト1`() {
        // 5x1マップ: 平地 平地 森 平地 平地
        val forestPositions = setOf(Position(2, 0))
        val map = createMapWithForest(width = 5, height = 1, forestPositions = forestPositions)
        val flying = createFlyingUnit()
        map.placeUnit(flying, Position(0, 0))

        // 飛行は全地形コスト1: コスト = 1 + 1 + 1 + 1 = 4
        val cost = pathFinder.getPathCostTo(flying, Position(0, 0), Position(4, 0), map)
        assertEquals(4, cost, "飛行ユニットの森コストは1であるべき（合計4）")
    }

    // ==================== findPath バウンディングボックス テスト ====================

    @Test
    fun `findPathはバウンディングボックス内で正しく経路を見つける`() {
        // 大きなマップ（30x30）でも正しく経路を見つけること
        val map = createPlainMap(width = 30, height = 30)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(5, 5))

        val path = pathFinder.findPath(player, Position(5, 5), Position(10, 5), map)

        assertTrue(path.isNotEmpty(), "バウンディングボックス内で経路が見つかるべき")
        assertEquals(Position(10, 5), path.last(), "ゴールに到達すべき")
        // 最短経路は直線5マス + 開始地点 = 6ノード
        assertEquals(6, path.size, "最短経路長は6であるべき")
    }

    @Test
    fun `findPathはバウンディングボックス外のユニットの影響を受けない`() {
        // 大きなマップでstart/goal付近にユニットがいないケース
        val map = createPlainMap(width = 50, height = 50)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(5, 5))

        // 遠くに敵を配置（バウンディングボックスは拡張されるがゴール到達には影響しない）
        val enemy = createEnemyUnit()
        map.placeUnit(enemy, Position(45, 45))

        val path = pathFinder.findPath(player, Position(5, 5), Position(10, 5), map)

        assertTrue(path.isNotEmpty(), "遠方の敵の有無にかかわらず経路が見つかるべき")
        assertEquals(Position(10, 5), path.last(), "ゴールに到達すべき")
    }

    @Test
    fun `findPathのバウンディングボックスはマージン5で拡張される`() {
        // start=(10,10), goal=(20,10) → BBの最小X=5, 最大X=25（マージン5）
        // 迂回が必要な場合、マージン内ならルートが見つかる
        val map = createPlainMap(width = 30, height = 30)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(10, 10))

        // start-goal間の直線上に敵を壁状に配置
        for (y in 0 until 30) {
            if (y != 10) { // start/goalのy以外
                map.placeUnit(createEnemyUnit("e_$y"), Position(15, y))
            }
        }
        // (15,10)にも敵を配置して直線を塞ぐ
        map.placeUnit(createEnemyUnit("e_block"), Position(15, 10))

        val path = pathFinder.findPath(player, Position(10, 10), Position(20, 10), map)

        // 敵の列を超えられないので空リストになるはず
        // （バウンディングボックス内の全yが敵で塞がれている）
        assertTrue(path.isEmpty(), "全方位が敵で塞がれている場合は到達不可であるべき")
    }

    @Test
    fun `findPathは味方を含むバウンディングボックスで正しく動作する`() {
        val map = createPlainMap(width = 20, height = 20)
        val player1 = createPlayerUnit("player_01")
        val player2 = createPlayerUnit("player_02")

        map.placeUnit(player1, Position(5, 5))
        map.placeUnit(player2, Position(10, 5))

        val path = pathFinder.findPath(player1, Position(5, 5), Position(15, 5), map)

        assertTrue(path.isNotEmpty(), "味方を通過して経路が見つかるべき")
        assertEquals(Position(15, 5), path.last(), "ゴールに到達すべき")
        // 味方のマスを通過していること
        assertTrue(Position(10, 5) in path, "味方のマスを通過できるべき")
    }

    // ==================== ヘルパーメソッド（追加: カスタム地形マップ） ====================

    /**
     * 山岳を含むマップを生成する
     * mountainPositions で指定した座標が山、それ以外は平地になる
     */
    private fun createMapWithMountain(
        width: Int = 10,
        height: Int = 10,
        mountainPositions: Set<Position>
    ): BattleMap {
        val tiles = Array(height) { y ->
            Array(width) { x ->
                val pos = Position(x, y)
                val terrain = if (pos in mountainPositions) TerrainType.MOUNTAIN else TerrainType.PLAIN
                Tile(pos, terrain)
            }
        }
        return BattleMap(id = "test_mountain", name = "山岳マップ", width = width, height = height, tiles = tiles)
    }

    /**
     * 複数の地形を自由に配置できるマップを生成する
     * terrainOverrides で指定した座標に対応するTerrainTypeを設定し、それ以外は平地になる
     */
    private fun createCustomTerrainMap(
        width: Int = 10,
        height: Int = 10,
        terrainOverrides: Map<Position, TerrainType> = emptyMap()
    ): BattleMap {
        val tiles = Array(height) { y ->
            Array(width) { x ->
                val pos = Position(x, y)
                val terrain = terrainOverrides[pos] ?: TerrainType.PLAIN
                Tile(pos, terrain)
            }
        }
        return BattleMap(id = "test_custom", name = "カスタムマップ", width = width, height = height, tiles = tiles)
    }

    // ==================== buildCostMapFromMultiple テスト ====================

    @Test
    fun `buildCostMapFromMultiple - 複数起点から正しいコストマップを構築する`() {
        // 5x1の平地マップ: 起点を(0,0)と(4,0)に配置
        // 期待: 各マスのコストは最も近い起点からの距離
        //   (0,0)=0, (1,0)=1, (2,0)=2, (3,0)=1, (4,0)=0
        val map = createPlainMap(width = 5, height = 1)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(2, 0))

        val origins = listOf(Position(0, 0), Position(4, 0))
        val costMap = pathFinder.buildCostMapFromMultiple(player, origins, map)

        assertEquals(0, costMap[Position(0, 0)], "起点(0,0)のコストは0")
        assertEquals(1, costMap[Position(1, 0)], "(1,0)は起点(0,0)から距離1")
        assertEquals(2, costMap[Position(2, 0)], "(2,0)は両起点から距離2")
        assertEquals(1, costMap[Position(3, 0)], "(3,0)は起点(4,0)から距離1")
        assertEquals(0, costMap[Position(4, 0)], "起点(4,0)のコストは0")
    }

    @Test
    fun `buildCostMapFromMultiple - 空のoriginsで空マップを返す`() {
        val map = createPlainMap(width = 5, height = 5)
        val player = createPlayerUnit()
        map.placeUnit(player, Position(2, 2))

        val costMap = pathFinder.buildCostMapFromMultiple(player, emptyList(), map)

        assertTrue(costMap.isEmpty(), "空のoriginsでは空マップを返すべき")
    }

    @Test
    fun `buildCostMapFromMultiple - 水域を挟んだ起点からは水域の先にコストが設定されない`() {
        // 5x1マップ: 平地 平地 水域 平地 平地
        // 起点=(0,0) → (2,0)が水域なので(3,0),(4,0)には到達不可
        val waterPositions = setOf(Position(2, 0))
        val map = createMapWithWater(width = 5, height = 1, waterPositions = waterPositions)
        val player = createPlayerUnit() // 歩兵は水域通行不可

        val origins = listOf(Position(0, 0))
        val costMap = pathFinder.buildCostMapFromMultiple(player, origins, map)

        assertEquals(0, costMap[Position(0, 0)], "起点のコストは0")
        assertEquals(1, costMap[Position(1, 0)], "(1,0)は到達可能でコスト1")
        // 水域(2,0)は通行不可なのでコスト設定されない（または非常に高い）
        // 水域の先(3,0),(4,0)にも到達不可
        assertNull(costMap[Position(3, 0)], "水域の先(3,0)はコストが設定されない")
        assertNull(costMap[Position(4, 0)], "水域の先(4,0)はコストが設定されない")
    }

    // ==================== isPassableFor テスト ====================

    @Test
    fun `isPassableFor - 歩兵で平地は通行可能`() {
        val map = createPlainMap(width = 3, height = 3)
        val player = createPlayerUnit() // 歩兵（INFANTRY）

        val result = pathFinder.isPassableFor(player, Position(1, 1), map)

        assertTrue(result, "歩兵は平地を通行可能であるべき")
    }

    @Test
    fun `isPassableFor - 歩兵で水域は通行不可`() {
        val waterPositions = setOf(Position(1, 1))
        val map = createMapWithWater(width = 3, height = 3, waterPositions = waterPositions)
        val player = createPlayerUnit() // 歩兵（INFANTRY）

        val result = pathFinder.isPassableFor(player, Position(1, 1), map)

        assertFalse(result, "歩兵は水域を通行不可であるべき")
    }

    @Test
    fun `isPassableFor - 飛行で水域は通行可能`() {
        val waterPositions = setOf(Position(1, 1))
        val map = createMapWithWater(width = 3, height = 3, waterPositions = waterPositions)
        val flying = createFlyingUnit() // 飛行（FLYING）

        val result = pathFinder.isPassableFor(flying, Position(1, 1), map)

        assertTrue(result, "飛行ユニットは水域を通行可能であるべき")
    }

    @Test
    fun `isPassableFor - 騎馬で山は通行不可`() {
        val mountainPositions = setOf(Position(1, 1))
        val map = createMapWithMountain(width = 3, height = 3, mountainPositions = mountainPositions)
        val cavalry = createCavalryUnit() // 騎馬（CAVALRY）

        val result = pathFinder.isPassableFor(cavalry, Position(1, 1), map)

        assertFalse(result, "騎馬ユニットは山を通行不可であるべき")
    }
}
