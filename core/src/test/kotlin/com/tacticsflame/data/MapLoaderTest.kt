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
        assertEquals(6, result.playerSpawns.size, "スポーン位置は6箇所")
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
        assertNotNull(enemy1.rightHand, "武器が装備されている")
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
        assertEquals(6, result.playerSpawns.size)
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
        // chapter_1.json: enemy_01 classId=axeFighter → AXE_FIGHTER.baseStats.hp = 21
        assertEquals(21f, enemy.stats.hp)
        assertEquals(21, enemy.currentHp) // 初期HPはmaxHP
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

    // ==================== レベルアップステータスのテスト ====================

    @Test
    fun `parseEnemyUnitでレベル1の敵はlevelUpStatsがゼロであること`() {
        val result = mapLoader.loadMap("chapter_1.json")!!

        // enemy_01: axeFighter, level 1 → 成長0回なので stats = baseStats のみ
        val (enemy, _) = result.enemies[0]
        assertEquals("enemy_01", enemy.id)
        assertEquals(1, enemy.level)

        val base = enemy.unitClass.baseStats
        assertEquals(base.hp, enemy.stats.hp, 0.0001f, "HP = baseStats.hp(21.0)")
        assertEquals(base.str, enemy.stats.str, 0.0001f, "STR = baseStats.str(8.0)")
        assertEquals(base.mag, enemy.stats.mag, 0.0001f, "MAG = baseStats.mag(0.0)")
        assertEquals(base.skl, enemy.stats.skl, 0.0001f, "SKL = baseStats.skl(4.0)")
        assertEquals(base.spd, enemy.stats.spd, 0.0001f, "SPD = baseStats.spd(4.0)")
        assertEquals(base.lck, enemy.stats.lck, 0.0001f, "LCK = baseStats.lck(2.0)")
        assertEquals(base.def, enemy.stats.def, 0.0001f, "DEF = baseStats.def(5.0)")
        assertEquals(base.res, enemy.stats.res, 0.0001f, "RES = baseStats.res(0.0)")
    }

    @Test
    fun `parseEnemyUnitでレベル6の剣士にクラス成長率に基づくlevelUpStatsが適用されること`() {
        val result = mapLoader.loadMap("chapter_5.json")!!

        // enemy_ch5_06: swordFighter, level 6 → 成長5回
        val enemy = result.enemies.find { it.first.id == "enemy_ch5_06" }
        assertNotNull(enemy, "渡河兵(enemy_ch5_06)が見つかること")
        val (unit, _) = enemy
        assertEquals(6, unit.level)

        // swordFighter classGrowthRate: hp=1.55, str=0.45, skl=0.50, spd=0.40, def=0.15, res=0.05
        // levelUpStats = classGrowthRate × (level - 1) = × 5
        val base = unit.unitClass.baseStats   // hp=18, str=6, skl=8, spd=8, def=4, res=1
        val growth = unit.unitClass.classGrowthRate
        val lvUps = 5

        // stats = baseStats + personalModifier(0) + levelUpStats
        assertEquals(base.hp + growth.hp * lvUps, unit.stats.hp, 0.0001f,
            "HP = 18.0 + 1.55×5 = 25.75")
        assertEquals(base.str + growth.str * lvUps, unit.stats.str, 0.0001f,
            "STR = 6.0 + 0.45×5 = 8.25")
        assertEquals(base.mag + growth.mag * lvUps, unit.stats.mag, 0.0001f,
            "MAG = 0.0 + 0.00×5 = 0.00")
        assertEquals(base.skl + growth.skl * lvUps, unit.stats.skl, 0.0001f,
            "SKL = 8.0 + 0.50×5 = 10.50")
        assertEquals(base.spd + growth.spd * lvUps, unit.stats.spd, 0.0001f,
            "SPD = 8.0 + 0.40×5 = 10.00")
        assertEquals(base.lck + growth.lck * lvUps, unit.stats.lck, 0.0001f,
            "LCK = 4.0 + 0.15×5 = 4.75")
        assertEquals(base.def + growth.def * lvUps, unit.stats.def, 0.0001f,
            "DEF = 4.0 + 0.15×5 = 4.75")
        assertEquals(base.res + growth.res * lvUps, unit.stats.res, 0.0001f,
            "RES = 1.0 + 0.05×5 = 1.25")
    }

    // ==================== ランダム敵生成のテスト ====================

    @Test
    fun `randomEnemiesは指定平均レベルに固定される`() {
        val result = mapLoader.loadMap("another_chapter.json", partyAverageLevel = 7)!!

        assertTrue(result.enemies.isNotEmpty(), "ランダム敵が生成されている")
        assertTrue(result.enemies.all { it.first.level == 7 }, "全ランダム敵が平均Lv.7固定")
    }

    @Test
    fun `randomEnemiesの能力上昇はGrowthRate加算方式で反映される`() {
        val result = mapLoader.loadMap("another_chapter.json", partyAverageLevel = 10)!!

        assertTrue(result.enemies.isNotEmpty(), "ランダム敵が生成されている")
        val firstEnemy = result.enemies.first().first

        // stats = unitClass.baseStats + personalModifier(0) + levelUpStats
        // levelUpStats = classGrowthRate × (level - 1) = classGrowthRate × 9
        val base = firstEnemy.unitClass.baseStats
        val growth = firstEnemy.unitClass.classGrowthRate
        val lvUps = 9 // level 10 → 9回レベルアップ
        assertEquals(base.hp + growth.hp * lvUps, firstEnemy.stats.hp, 0.0001f, "HPはクラスベース + (classGrowthRate.hp × 9)")
        assertEquals(base.str + growth.str * lvUps, firstEnemy.stats.str, 0.0001f, "STRはクラスベース + (classGrowthRate.str × 9)")
        assertEquals(base.skl + growth.skl * lvUps, firstEnemy.stats.skl, 0.0001f, "SKLはクラスベース + (classGrowthRate.skl × 9)")
    }

    @Test
    fun `randomEnemiesがfalseのマップは平均レベル指定時も固定敵を維持する`() {
        val result = mapLoader.loadMap("chapter_1.json", partyAverageLevel = 99)!!

        assertEquals(3, result.enemies.size, "固定敵の体数が維持される")
        assertEquals("enemy_01", result.enemies[0].first.id, "固定配置の敵IDが維持される")
        assertTrue(result.enemies.none { it.first.id.startsWith("random_enemy_") }, "ランダム敵が混入しない")
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
