package com.tacticsflame.screen

import com.tacticsflame.model.map.Position
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * 出撃前の配置入れ替えロジックのテスト
 *
 * BattlePrepScreen の deploymentMap 操作ロジックを
 * 独立してテストする。
 */
class DeploymentSwapTest {

    /** スポーン位置リスト（6箇所 — 出撃枠より多い） */
    private lateinit var spawnPositions: List<Position>

    /** 配置マッピング（スポーンインデックス → ユニット） */
    private lateinit var deploymentMap: MutableMap<Int, GameUnit>

    /** テスト用ユニット */
    private lateinit var unitA: GameUnit
    private lateinit var unitB: GameUnit
    private lateinit var unitC: GameUnit

    @BeforeTest
    fun setup() {
        spawnPositions = listOf(
            Position(2, 2), Position(2, 4), Position(3, 3),
            Position(1, 3), Position(1, 1), Position(3, 5)
        )

        unitA = createTestUnit("hero_01", "アレス")
        unitB = createTestUnit("hero_02", "リーナ")
        unitC = createTestUnit("hero_03", "マリア")

        // 初期配置: スポーン0にA、スポーン1にB、スポーン2にC（3〜5は空き）
        deploymentMap = mutableMapOf(
            0 to unitA,
            1 to unitB,
            2 to unitC
        )
    }

    @Test
    fun `ユニット同士の入れ替えが正しく動作する`() {
        swapDeployment(0, 1)

        assertEquals(unitB, deploymentMap[0], "スポーン0にリーナ")
        assertEquals(unitA, deploymentMap[1], "スポーン1にアレス")
        assertEquals(unitC, deploymentMap[2], "スポーン2はマリアのまま")
    }

    @Test
    fun `空きスポーンへの移動が正しく動作する`() {
        swapDeployment(0, 4)

        assertNull(deploymentMap[0], "スポーン0は空き")
        assertEquals(unitA, deploymentMap[4], "スポーン4にアレス")
        assertEquals(3, deploymentMap.size, "配置ユニット数は変わらない")
    }

    @Test
    fun `空きスポーンからの入れ替えは無視される`() {
        val originalMap = deploymentMap.toMap()
        swapDeployment(5, 0)

        assertEquals(originalMap, deploymentMap, "変更なし")
    }

    @Test
    fun `同じスポーンへの入れ替えは何もしない`() {
        swapDeployment(0, 0)

        assertEquals(unitA, deploymentMap[0])
        assertEquals(3, deploymentMap.size)
    }

    @Test
    fun `全ユニットを空きスポーンに移動できる`() {
        // A: 0→3, B: 1→4, C: 2→5
        swapDeployment(0, 3)
        swapDeployment(1, 4)
        swapDeployment(2, 5)

        assertNull(deploymentMap[0])
        assertNull(deploymentMap[1])
        assertNull(deploymentMap[2])
        assertEquals(unitA, deploymentMap[3])
        assertEquals(unitB, deploymentMap[4])
        assertEquals(unitC, deploymentMap[5])
    }

    @Test
    fun `連続入れ替えが正しく動作する`() {
        // A↔B → B↔C
        swapDeployment(0, 1)
        swapDeployment(0, 2)

        assertEquals(unitC, deploymentMap[0], "スポーン0にマリア")
        assertEquals(unitA, deploymentMap[1], "スポーン1にアレス")
        assertEquals(unitB, deploymentMap[2], "スポーン2にリーナ")
    }

    @Test
    fun `配置後もスポーン位置との対応が正しい`() {
        swapDeployment(0, 4)

        // スポーン4の位置は (1,1)
        val unitAPosition = spawnPositions[4]
        assertEquals(1, unitAPosition.x)
        assertEquals(1, unitAPosition.y)
    }

    @Test
    fun `スポーン数が出撃数より多い場合でも正しく動作する`() {
        assertEquals(6, spawnPositions.size, "スポーン数は6")
        assertEquals(3, deploymentMap.size, "出撃ユニットは3体")
        assertTrue(spawnPositions.size > deploymentMap.size, "スポーン数 > 出撃数")
    }

    // ==================== ヘルパーメソッド ====================

    /**
     * BattlePrepScreen.swapDeployment() と同じロジックをテスト用に再現
     */
    private fun swapDeployment(fromIndex: Int, toIndex: Int) {
        val unitFrom = deploymentMap[fromIndex]
        val unitTo = deploymentMap[toIndex]

        if (unitFrom == null) return
        if (fromIndex == toIndex) return

        if (unitTo != null) {
            deploymentMap[fromIndex] = unitTo
            deploymentMap[toIndex] = unitFrom
        } else {
            deploymentMap.remove(fromIndex)
            deploymentMap[toIndex] = unitFrom
        }
    }

    /**
     * テスト用の GameUnit を生成する
     */
    private fun createTestUnit(id: String, name: String): GameUnit {
        return GameUnit(
            id = id,
            name = name,
            unitClass = UnitClass.LORD,
            faction = Faction.PLAYER,
            personalModifier = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            personalGrowthRate = GrowthRate()
        )
    }
}
