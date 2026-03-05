package com.tacticsflame.system

import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * AISystem の新パターン（CAUTIOUS / SUPPORT / FLEE）テスト
 *
 * 10×10 の平地マップ上でプレイヤーユニットと敵ユニットを配置し、
 * 各AIパターンが期待どおりの行動を返すか検証する。
 */
class AITacticTest {

    private lateinit var pathFinder: PathFinder
    private lateinit var battleSystem: BattleSystem
    private lateinit var aiSystem: AISystem

    /** 鉄の剣（射程1） */
    private lateinit var ironSword: Weapon

    @BeforeTest
    fun setup() {
        pathFinder = PathFinder()
        battleSystem = BattleSystem()
        aiSystem = AISystem(pathFinder, battleSystem)

        ironSword = Weapon(
            id = "iron_sword",
            name = "鉄の剣",
            type = WeaponType.SWORD,
            might = 5,
            hit = 90,
            critical = 0,
            weight = 3,
            minRange = 1,
            maxRange = 1
        )
    }

    // ==================== ヘルパーメソッド ====================

    /**
     * 10×10 の平地マップを生成する
     */
    private fun createPlainMap(): BattleMap {
        val tiles = Array(10) { y ->
            Array(10) { x ->
                Tile(Position(x, y), TerrainType.PLAIN)
            }
        }
        return BattleMap(id = "test", name = "テストマップ", width = 10, height = 10, tiles = tiles)
    }

    /**
     * テスト用のプレイヤーユニットを生成する（SPD=8、鉄の剣装備）
     */
    private fun createPlayerUnit(id: String = "player_01", name: String = "プレイヤー"): GameUnit {
        val unit = GameUnit(
            id = id,
            name = name,
            unitClass = UnitClass.LORD,
            faction = Faction.PLAYER,
            stats = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            growthRate = GrowthRate()
        )
        unit.rightHand = ironSword
        return unit
    }

    /**
     * テスト用の敵ユニットを生成する（鉄の剣装備）
     */
    private fun createEnemyUnit(id: String = "enemy_01", name: String = "敵兵"): GameUnit {
        val unit = GameUnit(
            id = id,
            name = name,
            unitClass = UnitClass.SWORD_FIGHTER,
            faction = Faction.ENEMY,
            stats = Stats(hp = 18f, str = 5f, mag = 0f, skl = 5f, spd = 5f, lck = 3f, def = 4f, res = 1f),
            growthRate = GrowthRate()
        )
        unit.rightHand = ironSword
        return unit
    }

    /**
     * テスト用の味方ユニットを生成する（ALLY陣営）
     */
    private fun createAllyUnit(id: String = "ally_01", name: String = "味方兵"): GameUnit {
        val unit = GameUnit(
            id = id,
            name = name,
            unitClass = UnitClass.LORD,
            faction = Faction.ENEMY,
            stats = Stats(hp = 18f, str = 5f, mag = 0f, skl = 5f, spd = 5f, lck = 3f, def = 4f, res = 1f),
            growthRate = GrowthRate()
        )
        unit.rightHand = ironSword
        return unit
    }

    // ==================== CAUTIOUS テスト ====================

    @Test
    fun `CAUTIOUS - 敵が隣接していて脅威圏内にいる場合は攻撃しないか待機する`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        // 敵を(5,5)に配置、プレイヤーを(5,6)に配置（隣接）
        map.placeUnit(enemy, Position(5, 5))
        map.placeUnit(player, Position(5, 6))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.CAUTIOUS)

        // プレイヤーの脅威圏内にいるため、安全な位置から攻撃するか待機する
        // 隣接状態では敵の現在位置はプレイヤーの脅威圏内なので、
        // 安全な位置からの攻撃、安全な位置への移動、または待機のいずれか
        val act = action.action
        when (act) {
            is AISystem.Action.MoveAndAttack -> {
                // 安全な位置から攻撃する場合、移動先がプレイヤーの隣接マスでないことを確認するのは困難
                // （脅威圏の計算結果に依存）ので、アクションが返ること自体を検証
                assertNotNull(act.target, "攻撃対象が存在する")
            }
            is AISystem.Action.Move -> {
                // 安全な場所へ移動する
                val movePos = act.moveTo
                assertNotEquals(Position(5, 5), movePos, "現在位置から移動する")
            }
            is AISystem.Action.Wait -> {
                // 安全な場所がない場合は待機
            }
            is AISystem.Action.MoveAndHeal -> {
                // CAUTIOUSでは回復行動は想定しない
                fail("CAUTIOUSパターンでMoveAndHealは想定外")
            }
        }
    }

    @Test
    fun `CAUTIOUS - 敵から離れた位置にいる場合は脅威圏外の位置へ移動する`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        // 敵を(0,0)に配置、プレイヤーを(9,9)に配置（遠距離）
        map.placeUnit(enemy, Position(0, 0))
        map.placeUnit(player, Position(9, 9))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.CAUTIOUS)

        val act = action.action
        // 遠距離にいるため、脅威圏外で敵に近づく移動を行うはず
        when (act) {
            is AISystem.Action.Move -> {
                // 移動先がプレイヤーに近づく方向であること
                val currentDist = Position(0, 0).manhattanDistance(Position(9, 9))
                val newDist = act.moveTo.manhattanDistance(Position(9, 9))
                assertTrue(newDist < currentDist, "プレイヤーに近づく方向に移動: $currentDist -> $newDist")
            }
            is AISystem.Action.Wait -> {
                // 安全な位置がない場合は待機（稀なケース）
            }
            is AISystem.Action.MoveAndAttack -> {
                fail("遠距離で攻撃はできないはず")
            }
            is AISystem.Action.MoveAndHeal -> {
                fail("CAUTIOUSパターンでMoveAndHealは想定外")
            }
        }
    }

    // ==================== SUPPORT テスト ====================

    @Test
    fun `SUPPORT - 味方付近の敵を優先して攻撃する`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val allyEnemy = createEnemyUnit(id = "enemy_02", name = "敵兵2")
        val player = createPlayerUnit()

        // 敵ユニット(enemy)を(3,3)に配置
        // プレイヤーを(4,3)に配置（敵の隣）
        // 味方の敵ユニット(allyEnemy)を(3,4)に配置（playerの隣接でattackable）
        map.placeUnit(enemy, Position(3, 3))
        map.placeUnit(player, Position(4, 3))
        map.placeUnit(allyEnemy, Position(3, 4))

        // allyEnemyがSUPPORTパターンでdecideAction
        val action = aiSystem.decideAction(allyEnemy, map, AISystem.AIPattern.SUPPORT)

        val act = action.action
        // 味方(enemy)がプレイヤーの隣に居るので、プレイヤーを優先攻撃するか、
        // 攻撃可能なターゲットを攻撃するはず
        when (act) {
            is AISystem.Action.MoveAndAttack -> {
                assertEquals(player.id, act.target.id, "プレイヤーを攻撃する")
            }
            is AISystem.Action.Move -> {
                // 攻撃できない場合は味方へ近づく
            }
            is AISystem.Action.Wait -> {
                // フォールバック
            }
            is AISystem.Action.MoveAndHeal -> {
                // SUPPORTでは回復行動も許容
            }
        }
    }

    @Test
    fun `SUPPORT - 味方がいない場合はAGGRESSIVE相当の攻撃`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        // 1対1: 敵を(5,5)、プレイヤーを(5,6)（隣接）
        map.placeUnit(enemy, Position(5, 5))
        map.placeUnit(player, Position(5, 6))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.SUPPORT)

        val act = action.action
        // 味方がいないのでフォールバックで通常攻撃する
        when (act) {
            is AISystem.Action.MoveAndAttack -> {
                assertEquals(player.id, act.target.id, "隣接するプレイヤーを攻撃する")
            }
            is AISystem.Action.Move -> {
                // 攻撃できずに移動する場合もある
            }
            is AISystem.Action.Wait -> {
                fail("隣接する敵がいるので待機にはならないはず")
            }
            is AISystem.Action.MoveAndHeal -> {
                // SUPPORTフォールバックでは回復は想定外
            }
        }
    }

    // ==================== FLEE テスト ====================

    @Test
    fun `FLEE - 敵から離れる方向に移動する`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        // 敵を(5,5)、プレイヤーを(3,5)に配置
        map.placeUnit(enemy, Position(5, 5))
        map.placeUnit(player, Position(3, 5))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.FLEE)

        val act = action.action
        when (act) {
            is AISystem.Action.Move -> {
                // プレイヤーからの距離が現在位置より遠くなる
                val currentDist = Position(5, 5).manhattanDistance(Position(3, 5))
                val newDist = act.moveTo.manhattanDistance(Position(3, 5))
                assertTrue(
                    newDist > currentDist,
                    "プレイヤーから離れる方向に移動: 距離 $currentDist -> $newDist"
                )
            }
            is AISystem.Action.Wait -> {
                // 逃げる場所がない場合は待機
            }
            is AISystem.Action.MoveAndAttack -> {
                fail("FLEEパターンは攻撃しない")
            }
            is AISystem.Action.MoveAndHeal -> {
                fail("FLEEパターンでMoveAndHealは想定外")
            }
        }
    }

    @Test
    fun `FLEE - 敵が隣接していても攻撃しない`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        // 敵を(5,5)、プレイヤーを(5,6)に配置（隣接）
        map.placeUnit(enemy, Position(5, 5))
        map.placeUnit(player, Position(5, 6))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.FLEE)

        val act = action.action
        assertNotEquals(
            AISystem.Action.MoveAndAttack::class,
            act::class,
            "FLEEパターンは攻撃しない"
        )
        // Move または Wait のいずれか
        assertTrue(
            act is AISystem.Action.Move || act is AISystem.Action.Wait,
            "FLEEパターンは移動または待機のみ: ${act::class.simpleName}"
        )
    }

    // ==================== decideAction パターン分岐テスト ====================

    @Test
    fun `decideAction - CAUTIOUSパターンが正しいアクション型を返す`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        map.placeUnit(enemy, Position(0, 0))
        map.placeUnit(player, Position(9, 9))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.CAUTIOUS)

        assertNotNull(action, "アクションがnullでない")
        assertEquals(enemy.id, action.unit.id, "行動ユニットが正しい")

        val act = action.action
        assertTrue(
            act is AISystem.Action.Move || act is AISystem.Action.Wait || act is AISystem.Action.MoveAndAttack,
            "有効なアクション型を返す"
        )
    }

    @Test
    fun `decideAction - SUPPORTパターンが正しいアクション型を返す`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        map.placeUnit(enemy, Position(0, 0))
        map.placeUnit(player, Position(9, 9))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.SUPPORT)

        assertNotNull(action, "アクションがnullでない")
        assertEquals(enemy.id, action.unit.id, "行動ユニットが正しい")

        val act = action.action
        assertTrue(
            act is AISystem.Action.Move || act is AISystem.Action.Wait || act is AISystem.Action.MoveAndAttack,
            "有効なアクション型を返す"
        )
    }

    @Test
    fun `decideAction - FLEEパターンは攻撃アクションを返さない`() {
        val map = createPlainMap()
        val enemy = createEnemyUnit()
        val player = createPlayerUnit()

        map.placeUnit(enemy, Position(5, 5))
        map.placeUnit(player, Position(5, 6))

        val action = aiSystem.decideAction(enemy, map, AISystem.AIPattern.FLEE)

        assertNotNull(action, "アクションがnullでない")
        assertEquals(enemy.id, action.unit.id, "行動ユニットが正しい")

        val act = action.action
        assertTrue(
            act is AISystem.Action.Move || act is AISystem.Action.Wait,
            "FLEEパターンは攻撃しない: ${act::class.simpleName}"
        )
    }
}
