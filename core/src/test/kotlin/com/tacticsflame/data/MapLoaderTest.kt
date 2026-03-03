package com.tacticsflame.data

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.tacticsflame.model.map.TerrainType
import com.tacticsflame.model.unit.Faction
import com.tacticsflame.system.VictoryChecker
import kotlin.test.*

/**
 * MapLoader のテスト
 *
 * LibGDX の HeadlessApplication を利用して、
 * JSON ファイルからマップ・敵ユニット・スポーン位置を正しく読み込めることを検証する。
 */
class MapLoaderTest {

    private lateinit var mapLoader: MapLoader

    companion object {
        private var app: Application? = null

        /**
         * HeadlessApplication を1度だけ初期化する
         */
        private fun ensureGdxInitialized() {
            if (app == null) {
                val config = HeadlessApplicationConfiguration()
                app = HeadlessApplication(object : ApplicationAdapter() {}, config)
            }
        }
    }

    @BeforeTest
    fun setUp() {
        ensureGdxInitialized()
        mapLoader = MapLoader()
    }

    // ==================== chapter_1.json のテスト ====================

    @Test
    fun `chapter_1のマップを正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_1.json")

        assertNotNull(result, "chapter_1.json の読み込みに失敗")
        assertEquals(15, result.battleMap.width)
        assertEquals(10, result.battleMap.height)
        assertEquals("chapter_1", result.battleMap.id)
    }

    @Test
    fun `chapter_1のスポーン位置を正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_1.json")!!

        assertTrue(result.playerSpawns.isNotEmpty(), "スポーン位置が空")
        assertEquals(4, result.playerSpawns.size, "スポーン位置は4箇所")
        assertEquals(2, result.playerSpawns[0].x)
        assertEquals(2, result.playerSpawns[0].y)
    }

    @Test
    fun `chapter_1の敵ユニットを正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_1.json")!!

        assertEquals(3, result.enemies.size, "敵は3体")

        // 山賊A の確認
        val (enemy1, pos1) = result.enemies[0]
        assertEquals("enemy_01", enemy1.id)
        assertEquals("山賊A", enemy1.name)
        assertEquals(Faction.ENEMY, enemy1.faction)
        assertEquals(11, pos1.x)
        assertEquals(3, pos1.y)
        assertTrue(enemy1.weapons.isNotEmpty(), "武器が装備されている")
    }

    @Test
    fun `chapter_1の地形が正しくパースされる`() {
        val result = mapLoader.loadMap("chapter_1.json")!!
        val map = result.battleMap

        // terrainKey: 0=PLAIN, 1=FOREST, 3=FORT
        // chapter_1.json の terrain[0][0] = 0 → PLAIN
        val tile00 = map.getTile(0, 0)
        assertNotNull(tile00)
        assertEquals(TerrainType.PLAIN, tile00.terrainType)

        // terrain[0][7] = 1 → FOREST
        val tile70 = map.getTile(7, 0)
        assertNotNull(tile70)
        assertEquals(TerrainType.FOREST, tile70.terrainType)

        // terrain[5][10] = 3 → FORT
        val tile105 = map.getTile(10, 5)
        assertNotNull(tile105)
        assertEquals(TerrainType.FORT, tile105.terrainType)
    }

    @Test
    fun `chapter_1の勝利条件がDEFEAT_ALL`() {
        val result = mapLoader.loadMap("chapter_1.json")!!
        assertEquals(VictoryChecker.VictoryConditionType.DEFEAT_ALL, result.victoryConditionType)
    }

    // ==================== chapter_2〜6 の基本読み込みテスト ====================

    @Test
    fun `chapter_2のマップを正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_2.json")

        assertNotNull(result, "chapter_2.json の読み込みに失敗")
        assertEquals(15, result.battleMap.width)
        assertEquals(10, result.battleMap.height)
        assertEquals(4, result.enemies.size, "敵は4体")
        assertEquals(4, result.playerSpawns.size)
    }

    @Test
    fun `chapter_3のマップを正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_3.json")

        assertNotNull(result, "chapter_3.json の読み込みに失敗")
        assertEquals(15, result.battleMap.width)
        assertEquals(12, result.battleMap.height)
        assertEquals(5, result.enemies.size, "敵は5体")
    }

    @Test
    fun `chapter_4のマップを正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_4.json")

        assertNotNull(result, "chapter_4.json の読み込みに失敗")
        assertEquals(18, result.battleMap.width)
        assertEquals(12, result.battleMap.height)
        assertEquals(6, result.enemies.size, "敵は6体")
    }

    @Test
    fun `chapter_5のマップを正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_5.json")

        assertNotNull(result, "chapter_5.json の読み込みに失敗")
        assertEquals(16, result.battleMap.width)
        assertEquals(12, result.battleMap.height)
        assertEquals(7, result.enemies.size, "敵は7体")
    }

    @Test
    fun `chapter_6のマップを正しく読み込める`() {
        val result = mapLoader.loadMap("chapter_6.json")

        assertNotNull(result, "chapter_6.json の読み込みに失敗")
        assertEquals(20, result.battleMap.width)
        assertEquals(15, result.battleMap.height)
        assertEquals(8, result.enemies.size, "敵は8体")
        assertEquals(VictoryChecker.VictoryConditionType.DEFEAT_BOSS, result.victoryConditionType)
    }

    // ==================== 敵ユニットの能力テスト ====================

    @Test
    fun `chapter_6のボスユニットにisLordフラグが付いている`() {
        val result = mapLoader.loadMap("chapter_6.json")!!

        val boss = result.enemies.find { it.first.id == "enemy_ch6_08" }
        assertNotNull(boss, "ボスユニットが見つからない")
        assertTrue(boss.first.isLord, "ボスにisLordフラグが付いている")
        assertEquals("暗黒将軍ヴォルク", boss.first.name)
        assertEquals(8, boss.first.level)
    }

    @Test
    fun `敵ユニットのステータスが読み込まれる`() {
        val result = mapLoader.loadMap("chapter_1.json")!!

        val (enemy, _) = result.enemies[0]
        // chapter_1.json: enemy_01 の stats.hp = 18
        assertEquals(18, enemy.stats.hp)
        assertEquals(18, enemy.currentHp) // 初期HPはmaxHP
    }

    @Test
    fun `敵ユニットの武器がマスターデータから読み込まれる`() {
        val result = mapLoader.loadMap("chapter_1.json")!!

        val (enemy, _) = result.enemies[0] // 山賊A: weaponId = ironAxe
        val weapon = enemy.equippedWeapon()
        assertNotNull(weapon)
        assertEquals("ironAxe", weapon.id)
        assertEquals("鉄の斧", weapon.name)
        assertEquals(8, weapon.might)
    }

    // ==================== エラーハンドリング ====================

    @Test
    fun `存在しないマップファイルはnullを返す`() {
        val result = mapLoader.loadMap("nonexistent.json")
        assertNull(result, "存在しないファイルはnullを返す")
    }

    // ==================== 地形バリエーション ====================

    @Test
    fun `chapter_5の水域と橋が正しくパースされる`() {
        val result = mapLoader.loadMap("chapter_5.json")!!
        val map = result.battleMap

        // chapter_5.json terrain[1][6] = 4 → WATER
        val waterTile = map.getTile(6, 1)
        assertNotNull(waterTile)
        assertEquals(TerrainType.WATER, waterTile.terrainType)

        // chapter_5.json terrain[3][7] = 7 → BRIDGE
        val bridgeTile = map.getTile(7, 3)
        assertNotNull(bridgeTile)
        assertEquals(TerrainType.BRIDGE, bridgeTile.terrainType)
    }
}
