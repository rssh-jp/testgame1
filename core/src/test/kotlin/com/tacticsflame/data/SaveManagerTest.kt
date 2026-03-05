package com.tacticsflame.data

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.utils.JsonReader
import com.tacticsflame.model.campaign.GameProgress
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * SaveManager のテスト
 *
 * LibGDX の HeadlessApplication を利用して、
 * セーブデータのシリアライズ・デシリアライズ・ファイル保存・復元を検証する。
 */
class SaveManagerTest {

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
        // テスト前にセーブデータを削除
        SaveManager.deleteSaveData()
    }

    @AfterTest
    fun tearDown() {
        // テスト後にもクリーンアップ
        SaveManager.deleteSaveData()
    }

    // ==================== シリアライズ基本テスト ====================

    @Test
    fun `初期状態のGameProgressをシリアライズできる`() {
        val gp = GameProgress()
        gp.initialize()

        val json = SaveManager.serializeToJson(gp)
        assertNotNull(json)
        assertTrue(json.isNotEmpty(), "JSON文字列が空")
        assertTrue(json.contains("\"version\""), "バージョン情報が含まれていない")
        assertTrue(json.contains("\"chapters\""), "チャプター情報が含まれていない")
        assertTrue(json.contains("\"party\""), "パーティ情報が含まれていない")
    }

    @Test
    fun `シリアライズされたJSONにユニット情報が含まれる`() {
        val gp = GameProgress()
        gp.initialize()

        val json = SaveManager.serializeToJson(gp)
        // 初期パーティの4ユニットが含まれていること
        assertTrue(json.contains("hero_01"), "アレスが含まれていない")
        assertTrue(json.contains("hero_02"), "リーナが含まれていない")
        assertTrue(json.contains("hero_03"), "マリアが含まれていない")
        assertTrue(json.contains("hero_04"), "エリックが含まれていない")
    }

    @Test
    fun `シリアライズされたJSONに武器情報が含まれる`() {
        val gp = GameProgress()
        gp.initialize()

        val json = SaveManager.serializeToJson(gp)
        assertTrue(json.contains("ironSword"), "鉄の剣IDが含まれていない")
        assertTrue(json.contains("SWORD"), "武器タイプSWORDが含まれていない")
    }

    // ==================== デシリアライズ基本テスト ====================

    @Test
    fun `シリアライズとデシリアライズでチャプター状態が保持される`() {
        val gp = GameProgress()
        gp.initialize()

        // チャプター1をクリア済みにする
        gp.completeChapter("chapter_1")

        val json = SaveManager.serializeToJson(gp)

        // 新しいGameProgressを作成してデシリアライズ
        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        // チャプター1がクリア済み・チャプター2が開放済みであること
        val ch1 = gp2.chapters.find { it.id == "chapter_1" }
        val ch2 = gp2.chapters.find { it.id == "chapter_2" }
        assertNotNull(ch1)
        assertNotNull(ch2)
        assertTrue(ch1.completed, "チャプター1がクリア済みでない")
        assertTrue(ch2.unlocked, "チャプター2が開放されていない")
    }

    @Test
    fun `シリアライズとデシリアライズでユニットステータスが保持される`() {
        val gp = GameProgress()
        gp.initialize()

        // ユニットのステータスを変更
        val ares = gp.party.findUnit("hero_01")!!
        ares.gainExp(50)

        val json = SaveManager.serializeToJson(gp)

        // 新しいGameProgressを作成してデシリアライズ
        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        val ares2 = gp2.party.findUnit("hero_01")!!
        assertEquals(50, ares2.exp, "経験値が保持されていない")
        assertEquals("アレス", ares2.name, "名前が保持されていない")
    }

    @Test
    fun `シリアライズとデシリアライズでレベルアップ後のステータスが保持される`() {
        val gp = GameProgress()
        gp.initialize()

        // レベルアップさせる（成長率を100%にして確実にステータスが上がるようにした別ユニットでテスト）
        val unit = GameUnit(
            id = "test_unit", name = "テスト",
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            level = 1, exp = 0,
            stats = Stats(hp = 20f, str = 6f, mag = 1f, skl = 7f, spd = 8f, lck = 5f, def = 5f, res = 2f),
            growthRate = GrowthRate(hp = 1.0f, str = 1.0f, mag = 1.0f, skl = 1.0f, spd = 1.0f, lck = 1.0f, def = 1.0f, res = 1.0f)
        )
        unit.rightHand = Weapon("testSword", "テスト剣", WeaponType.SWORD, might = 5, hit = 90)
        unit.gainExp(100) // レベルアップ
        gp.party.addUnit(unit)

        val json = SaveManager.serializeToJson(gp)

        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        val restored = gp2.party.findUnit("test_unit")!!
        assertEquals(2, restored.level, "レベルが保持されていない")
        // 成長率100%なので全ステータスが+1されているはず
        assertEquals(unit.stats.hp, restored.stats.hp, "HPステータスが保持されていない")
        assertEquals(unit.stats.str, restored.stats.str, "STRステータスが保持されていない")
    }

    @Test
    fun `シリアライズとデシリアライズで出撃メンバーが保持される`() {
        val gp = GameProgress()
        gp.initialize()

        // 出撃メンバーを変更
        gp.party.setDeployedUnits(listOf("hero_01", "hero_04"))

        val json = SaveManager.serializeToJson(gp)

        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        assertEquals(2, gp2.party.deployedIds.size, "出撃メンバー数が一致しない")
        assertTrue(gp2.party.deployedIds.contains("hero_01"), "hero_01が出撃メンバーにいない")
        assertTrue(gp2.party.deployedIds.contains("hero_04"), "hero_04が出撃メンバーにいない")
    }

    @Test
    fun `シリアライズとデシリアライズでHPが保持される`() {
        val gp = GameProgress()
        gp.initialize()

        val ares = gp.party.findUnit("hero_01")!!
        ares.takeDamage(5)
        val expectedHp = ares.currentHp

        val json = SaveManager.serializeToJson(gp)

        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        val ares2 = gp2.party.findUnit("hero_01")!!
        assertEquals(expectedHp, ares2.currentHp, "現在HPが保持されていない")
    }

    @Test
    fun `シリアライズとデシリアライズで武器情報が保持される`() {
        val gp = GameProgress()
        gp.initialize()

        val json = SaveManager.serializeToJson(gp)

        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        val ares2 = gp2.party.findUnit("hero_01")!!
        assertNotNull(ares2.rightHand, "武器が装備されていない")
        val sword = ares2.rightHand!!
        assertEquals("ironSword", sword.id, "武器IDが一致しない")
        assertEquals("鉄の剣", sword.name, "武器名が一致しない")
        assertEquals(WeaponType.SWORD, sword.type, "武器タイプが一致しない")
        assertEquals(5, sword.might, "武器の威力が一致しない")
        assertEquals(90, sword.hit, "武器の命中が一致しない")
    }

    // ==================== ファイル保存/読み込みテスト ====================

    @Test
    fun `セーブデータが存在しない場合hasSaveDataはfalseを返す`() {
        assertFalse(SaveManager.hasSaveData(), "セーブデータが存在しないはず")
    }

    @Test
    fun `セーブとロードが正常に動作する`() {
        val gp = GameProgress()
        gp.initialize()
        gp.completeChapter("chapter_1")

        // セーブ
        val saved = SaveManager.save(gp)
        assertTrue(saved, "セーブに失敗")
        assertTrue(SaveManager.hasSaveData(), "セーブデータが存在するはず")

        // ロード
        val gp2 = GameProgress()
        gp2.initialize()
        val loaded = SaveManager.load(gp2)
        assertTrue(loaded, "ロードに失敗")

        // チャプター状態が復元されていること
        val ch1 = gp2.chapters.find { it.id == "chapter_1" }!!
        assertTrue(ch1.completed, "チャプター1がクリア済みでない")
    }

    @Test
    fun `セーブデータがない場合ロードはfalseを返す`() {
        val gp = GameProgress()
        gp.initialize()
        val loaded = SaveManager.load(gp)
        assertFalse(loaded, "セーブデータがないのにロード成功した")
    }

    @Test
    fun `セーブデータを削除できる`() {
        val gp = GameProgress()
        gp.initialize()
        SaveManager.save(gp)
        assertTrue(SaveManager.hasSaveData())

        SaveManager.deleteSaveData()
        assertFalse(SaveManager.hasSaveData(), "削除後もセーブデータが存在する")
    }

    // ==================== エッジケーステスト ====================

    @Test
    fun `複数回セーブしても最新データが保持される`() {
        val gp = GameProgress()
        gp.initialize()

        // 1回目: チャプター1クリア
        gp.completeChapter("chapter_1")
        SaveManager.save(gp)

        // 2回目: チャプター2もクリア
        gp.completeChapter("chapter_2")
        SaveManager.save(gp)

        // ロード
        val gp2 = GameProgress()
        gp2.initialize()
        SaveManager.load(gp2)

        val ch1 = gp2.chapters.find { it.id == "chapter_1" }!!
        val ch2 = gp2.chapters.find { it.id == "chapter_2" }!!
        assertTrue(ch1.completed, "チャプター1がクリア済みでない")
        assertTrue(ch2.completed, "チャプター2がクリア済みでない")
    }

    @Test
    fun `弓の射程が正しく保存復元される`() {
        val gp = GameProgress()
        gp.initialize()

        val json = SaveManager.serializeToJson(gp)

        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        // マリア（アーチャー）の弓は射程2-2
        val maria = gp2.party.findUnit("hero_03")!!
        val bow = maria.rightHand!!
        assertEquals(2, bow.minRange, "弓の最小射程が一致しない")
        assertEquals(2, bow.maxRange, "弓の最大射程が一致しない")
    }

    @Test
    fun `魔法ユニットのステータスが正しく保存復元される`() {
        val gp = GameProgress()
        gp.initialize()

        val json = SaveManager.serializeToJson(gp)

        val gp2 = GameProgress()
        gp2.initialize()
        val root = JsonReader().parse(json)
        SaveManager.deserializeFromJson(root, gp2)

        // エリック（メイジ）
        val eric = gp2.party.findUnit("hero_04")!!
        assertEquals("メイジ", eric.unitClass.name, "兵種名が一致しない")
        assertEquals(WeaponType.MAGIC, eric.rightHand!!.type, "武器タイプが一致しない")
        assertEquals(7f, eric.stats.mag, "魔力が一致しない")
    }
}
