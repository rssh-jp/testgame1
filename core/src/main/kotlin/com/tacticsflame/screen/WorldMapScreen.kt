package com.tacticsflame.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.campaign.ChapterInfo
import com.tacticsflame.util.FontManager

/**
 * ワールドマップ画面
 *
 * チャプター（ステージ）の選択と部隊編成への遷移を行う。
 * 各チャプターはマップ上のノードとして表示され、タップで選択する。
 *
 * 画面フロー:
 * - チャプターノードをタップ → 戦闘準備画面 (BattlePrepScreen) へ
 * - 「編成」ボタンをタップ → 部隊編成画面 (FormationScreen) へ
 */
class WorldMapScreen(private val game: TacticsFlameGame) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var titleFont: BitmapFont
    private lateinit var font: BitmapFont
    private lateinit var smallFont: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val viewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)

    /** 選択中のチャプター（ホバー表示用） */
    private var hoveredChapter: ChapterInfo? = null

    /** 編成ボタンの領域 */
    private val formationButtonX = 80f
    private val formationButtonY = 80f
    private val formationButtonW = 280f
    private val formationButtonH = 80f

    companion object {
        private const val TAG = "WorldMapScreen"

        /** チャプターノードの半径 */
        private const val NODE_RADIUS = 40f

        /** ノードのタップ判定半径 */
        private const val NODE_TAP_RADIUS = 60f
    }

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        titleFont = FontManager.getFont(size = 48)
        font = FontManager.getFont(size = 32)
        smallFont = FontManager.getFont(size = 24)
        Gdx.app.log(TAG, "ワールドマップ画面を表示")
    }

    /**
     * フレーム描画処理
     *
     * @param delta 前フレームからの経過時間（秒）
     */
    override fun render(delta: Float) {
        handleInput()

        // 画面クリア
        Gdx.gl.glClearColor(0.08f, 0.12f, 0.18f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()

        // ノード間の接続線を描画
        renderConnections()

        // チャプターノードを描画
        renderChapterNodes()

        // 画面タイトル
        renderHeader()

        // 編成ボタン
        renderFormationButton()

        // 選択中チャプターの詳細パネル
        renderChapterDetail()
    }

    // ==================== 入力処理 ====================

    /**
     * タッチ入力を処理する
     */
    private fun handleInput() {
        if (!Gdx.input.justTouched()) return

        // ビューポート上の座標に変換
        val screenX = Gdx.input.x.toFloat()
        val screenY = Gdx.input.y.toFloat()
        val worldCoord = viewport.unproject(com.badlogic.gdx.math.Vector2(screenX, screenY))
        val touchX = worldCoord.x
        val touchY = worldCoord.y

        // 編成ボタンのタップ判定
        if (touchX in formationButtonX..(formationButtonX + formationButtonW) &&
            touchY in formationButtonY..(formationButtonY + formationButtonH)
        ) {
            Gdx.app.log(TAG, "編成画面へ遷移")
            game.screenManager.navigateToFormation()
            return
        }

        // チャプターノードのタップ判定
        val chapters = game.gameProgress.chapters
        for (chapter in chapters) {
            if (!chapter.unlocked) continue
            val nodeX = chapter.worldMapX * GameConfig.VIRTUAL_WIDTH
            val nodeY = chapter.worldMapY * GameConfig.VIRTUAL_HEIGHT
            val dist = com.badlogic.gdx.math.Vector2.dst(touchX, touchY, nodeX, nodeY)
            if (dist <= NODE_TAP_RADIUS) {
                if (hoveredChapter == chapter) {
                    // 同じノードを二回タップで戦闘準備へ
                    Gdx.app.log(TAG, "チャプター選択: ${chapter.name}")
                    game.screenManager.navigateToBattlePrep(chapter)
                    return
                } else {
                    hoveredChapter = chapter
                    Gdx.app.log(TAG, "チャプターフォーカス: ${chapter.name}")
                    return
                }
            }
        }

        // どのノードでもない場合はフォーカス解除
        hoveredChapter = null
    }

    // ==================== 描画メソッド ====================

    /**
     * ヘッダー（画面タイトル）を描画する
     */
    private fun renderHeader() {
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        titleFont.color = Color.WHITE
        val headerText = "— ワールドマップ —"
        glyphLayout.setText(titleFont, headerText)
        titleFont.draw(
            batch, headerText,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 40f
        )
        batch.end()
    }

    /**
     * チャプターノード間の接続線を描画する
     */
    private fun renderConnections() {
        val chapters = game.gameProgress.chapters
        if (chapters.size < 2) return

        shapeRenderer.projectionMatrix = viewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.3f, 0.4f, 0.5f, 1f)

        for (i in 0 until chapters.size - 1) {
            val from = chapters[i]
            val to = chapters[i + 1]
            shapeRenderer.line(
                from.worldMapX * GameConfig.VIRTUAL_WIDTH,
                from.worldMapY * GameConfig.VIRTUAL_HEIGHT,
                to.worldMapX * GameConfig.VIRTUAL_WIDTH,
                to.worldMapY * GameConfig.VIRTUAL_HEIGHT
            )
        }
        shapeRenderer.end()
    }

    /**
     * チャプターノードを描画する
     *
     * - 未開放: 暗いグレー
     * - 開放済み・未クリア: 明るい青
     * - クリア済み: 緑
     * - 選択中: 金色のリング
     */
    private fun renderChapterNodes() {
        val chapters = game.gameProgress.chapters

        shapeRenderer.projectionMatrix = viewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (chapter in chapters) {
            val cx = chapter.worldMapX * GameConfig.VIRTUAL_WIDTH
            val cy = chapter.worldMapY * GameConfig.VIRTUAL_HEIGHT

            // 選択リング
            if (chapter == hoveredChapter) {
                shapeRenderer.setColor(1f, 0.85f, 0.1f, 1f)
                shapeRenderer.circle(cx, cy, NODE_RADIUS + 8f)
            }

            // ノード本体
            when {
                !chapter.unlocked -> shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f)
                chapter.completed -> shapeRenderer.setColor(0.2f, 0.8f, 0.3f, 1f)
                else -> shapeRenderer.setColor(0.3f, 0.5f, 1f, 1f)
            }
            shapeRenderer.circle(cx, cy, NODE_RADIUS)
        }
        shapeRenderer.end()

        // ノードラベル
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        for (chapter in chapters) {
            val cx = chapter.worldMapX * GameConfig.VIRTUAL_WIDTH
            val cy = chapter.worldMapY * GameConfig.VIRTUAL_HEIGHT

            smallFont.color = if (chapter.unlocked) Color.WHITE else Color.DARK_GRAY
            smallFont.draw(batch, chapter.name, cx - 80f, cy - NODE_RADIUS - 12f)

            if (chapter.completed) {
                smallFont.color = Color.GREEN
                smallFont.draw(batch, "✓ CLEAR", cx - 40f, cy + NODE_RADIUS + 32f)
            }
        }
        batch.end()
    }

    /**
     * 編成ボタンを描画する
     */
    private fun renderFormationButton() {
        shapeRenderer.projectionMatrix = viewport.camera.combined

        // ボタン背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.3f, 0.6f, 0.9f)
        shapeRenderer.rect(formationButtonX, formationButtonY, formationButtonW, formationButtonH)
        shapeRenderer.end()

        // ボタン枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.5f, 0.6f, 1f, 1f)
        shapeRenderer.rect(formationButtonX, formationButtonY, formationButtonW, formationButtonH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ボタンテキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        font.color = Color.WHITE
        font.draw(
            batch, "部隊編成",
            formationButtonX + 60f,
            formationButtonY + formationButtonH / 2f + 12f
        )
        batch.end()
    }

    /**
     * 選択中チャプターの詳細パネルを表示する
     */
    private fun renderChapterDetail() {
        val chapter = hoveredChapter ?: return

        val panelW = 500f
        val panelH = 200f
        val panelX = GameConfig.VIRTUAL_WIDTH / 2f - panelW / 2f
        val panelY = 160f

        shapeRenderer.projectionMatrix = viewport.camera.combined

        // パネル背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.75f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()

        // パネル枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.GOLD
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        var textY = panelY + panelH - 24f
        val textX = panelX + 20f
        val lineH = 36f

        font.color = Color.GOLD
        font.draw(batch, chapter.name, textX, textY)
        textY -= lineH

        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(batch, chapter.description, textX, textY)
        textY -= lineH

        smallFont.color = Color.WHITE
        smallFont.draw(batch, "最大出撃数: ${chapter.maxDeployCount}", textX, textY)
        textY -= lineH

        val deployedCount = game.gameProgress.party.deployedIds.size
        smallFont.color = if (deployedCount > 0) Color.CYAN else Color.RED
        smallFont.draw(batch, "現在の出撃メンバー: ${deployedCount}人", textX, textY)
        textY -= lineH

        font.color = Color.GOLD
        font.draw(batch, "▶ タップで出撃", textX + 120f, textY)

        batch.end()
    }

    /**
     * ウィンドウリサイズ処理
     */
    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    /**
     * リソース解放
     */
    override fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
    }
}
