package com.tacticsflame.model.campaign

import com.badlogic.gdx.Application
import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import kotlin.test.*

/**
 * GameProgress（ゲーム進行管理）のテスト
 *
 * 周回システムとチャプター7〜12の進行ロジックを検証する。
 * HeadlessApplication を使って Gdx.app.log() を有効化している。
 */
class GameProgressTest {

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

    private lateinit var gp: GameProgress

    @BeforeTest
    fun setUp() {
        ensureGdxInitialized()
        gp = GameProgress()
        gp.initialize()
    }

    // ==================== ヘルパー ====================

    /**
     * 指定IDのチャプターを取得する
     */
    private fun chapter(id: String): ChapterInfo =
        gp.chapters.first { it.id == id }

    /**
     * chapter_1 から指定チャプターまで順番にクリアする
     */
    private fun clearUpTo(chapterId: String) {
        for (ch in gp.chapters) {
            if (ch.id == "another_chapter" || ch.id == "campaign_1") continue
            gp.completeChapter(ch.id)
            if (ch.id == chapterId) break
        }
    }

    // ==================== A. completeChapter() - チャプター進行 ====================

    /** ch6クリア → ch7解放 */
    @Test
    fun test_completeChapter_ch6クリアでch7が解放される() {
        clearUpTo("chapter_6")

        assertTrue(chapter("chapter_6").completed, "chapter_6がクリア済みであること")
        assertTrue(chapter("chapter_7").unlocked, "chapter_7が解放されること")
    }

    /** ch7クリア → ch8解放 */
    @Test
    fun test_completeChapter_ch7クリアでch8が解放される() {
        clearUpTo("chapter_7")

        assertTrue(chapter("chapter_7").completed, "chapter_7がクリア済みであること")
        assertTrue(chapter("chapter_8").unlocked, "chapter_8が解放されること")
    }

    /** ch8クリア → ch9解放 */
    @Test
    fun test_completeChapter_ch8クリアでch9が解放される() {
        clearUpTo("chapter_8")

        assertTrue(chapter("chapter_8").completed, "chapter_8がクリア済みであること")
        assertTrue(chapter("chapter_9").unlocked, "chapter_9が解放されること")
    }

    /** ch9クリア → ch10解放 */
    @Test
    fun test_completeChapter_ch9クリアでch10が解放される() {
        clearUpTo("chapter_9")

        assertTrue(chapter("chapter_9").completed, "chapter_9がクリア済みであること")
        assertTrue(chapter("chapter_10").unlocked, "chapter_10が解放されること")
    }

    /** ch10クリア → ch11解放 */
    @Test
    fun test_completeChapter_ch10クリアでch11が解放される() {
        clearUpTo("chapter_10")

        assertTrue(chapter("chapter_10").completed, "chapter_10がクリア済みであること")
        assertTrue(chapter("chapter_11").unlocked, "chapter_11が解放されること")
    }

    /** ch11クリア → ch12解放 */
    @Test
    fun test_completeChapter_ch11クリアでch12が解放される() {
        clearUpTo("chapter_11")

        assertTrue(chapter("chapter_11").completed, "chapter_11がクリア済みであること")
        assertTrue(chapter("chapter_12").unlocked, "chapter_12が解放されること")
    }

    /** ch12クリア → 周回スタート（cycle=1） */
    @Test
    fun test_completeChapter_ch12クリアで周回が開始される() {
        clearUpTo("chapter_12")

        assertEquals(1, gp.cycle, "ch12クリアでcycleが1になること")
    }

    // ==================== B. startNewCycle() - 周回リセット ====================

    /** 全チャプター（1〜12）のcompletedがリセットされる */
    @Test
    fun test_startNewCycle_全チャプターのcompletedがリセットされる() {
        clearUpTo("chapter_6")
        gp.startNewCycle()

        for (i in 1..12) {
            assertFalse(
                chapter("chapter_$i").completed,
                "chapter_${i}のcompletedがfalseであること"
            )
        }
    }

    /** ch1のみ解放、ch2〜12は未解放 */
    @Test
    fun test_startNewCycle_ch1のみ解放される() {
        clearUpTo("chapter_6")
        gp.startNewCycle()

        assertTrue(chapter("chapter_1").unlocked, "chapter_1が解放されること")
        for (i in 2..12) {
            assertFalse(
                chapter("chapter_$i").unlocked,
                "chapter_${i}のunlockedがfalseであること"
            )
        }
    }

    /** ランダムマップ（another_chapter）の状態は変化しない */
    @Test
    fun test_startNewCycle_ランダムマップの状態が維持される() {
        // another_chapter は初期状態で unlocked=true
        val beforeUnlocked = chapter("another_chapter").unlocked

        gp.startNewCycle()

        assertEquals(
            beforeUnlocked,
            chapter("another_chapter").unlocked,
            "another_chapterのunlockedが変化しないこと"
        )
    }

    /** campaign_1 の状態は変化しない */
    @Test
    fun test_startNewCycle_campaign1の状態が維持される() {
        val beforeUnlocked = chapter("campaign_1").unlocked
        val beforeCompleted = chapter("campaign_1").completed

        gp.startNewCycle()

        assertEquals(
            beforeUnlocked,
            chapter("campaign_1").unlocked,
            "campaign_1のunlockedが変化しないこと"
        )
        assertEquals(
            beforeCompleted,
            chapter("campaign_1").completed,
            "campaign_1のcompletedが変化しないこと"
        )
    }

    /** cycle=0 → startNewCycle → cycle=1 */
    @Test
    fun test_startNewCycle_cycleが0から1にインクリメントされる() {
        assertEquals(0, gp.cycle, "初期cycleが0であること")

        gp.startNewCycle()

        assertEquals(1, gp.cycle, "startNewCycle後にcycleが1であること")
    }

    /** 連続周回: cycle=2 → startNewCycle → cycle=3 */
    @Test
    fun test_startNewCycle_連続周回でcycleが正しくインクリメントされる() {
        // cycle を 2 にする
        gp.startNewCycle() // cycle=1
        gp.startNewCycle() // cycle=2
        assertEquals(2, gp.cycle, "事前条件: cycleが2であること")

        gp.startNewCycle() // cycle=3

        assertEquals(3, gp.cycle, "startNewCycle後にcycleが3であること")
    }

    // ==================== C. 敵レベル補正（境界値） ====================

    /** cycle=0 で levelBonus=0 */
    @Test
    fun test_levelBonus_cycle0で補正なし() {
        assertEquals(0, gp.cycle, "初期cycleが0であること")
        val levelBonus = gp.cycle * 10
        assertEquals(0, levelBonus, "cycle=0でlevelBonusが0であること")
    }

    /** cycle=1 で levelBonus=10 */
    @Test
    fun test_levelBonus_cycle1でプラス10() {
        gp.startNewCycle()
        assertEquals(1, gp.cycle)
        val levelBonus = gp.cycle * 10
        assertEquals(10, levelBonus, "cycle=1でlevelBonusが10であること")
    }

    /** cycle=5 で levelBonus=50 */
    @Test
    fun test_levelBonus_cycle5でプラス50() {
        repeat(5) { gp.startNewCycle() }
        assertEquals(5, gp.cycle)
        val levelBonus = gp.cycle * 10
        assertEquals(50, levelBonus, "cycle=5でlevelBonusが50であること")
    }

    // ==================== D. チャプター数確認 ====================

    /** initialize後、全14チャプターが存在する */
    @Test
    fun test_initialize_全14チャプターが存在する() {
        assertEquals(14, gp.chapters.size, "チャプター数が14であること")
    }

    /** チャプターIDの順序が正しい */
    @Test
    fun test_initialize_チャプターの順序が正しい() {
        val expectedIds = (1..12).map { "chapter_$it" } + "another_chapter" + "campaign_1"
        val actualIds = gp.chapters.map { it.id }
        assertEquals(expectedIds, actualIds, "チャプターIDの順序が正しいこと")
    }
}
