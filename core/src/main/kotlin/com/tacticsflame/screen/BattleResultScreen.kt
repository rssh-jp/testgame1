package com.tacticsflame.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.campaign.BattleResultData
import com.tacticsflame.util.FontManager

/**
 * バトルリザルト画面
 *
 * 戦闘結果（勝利/敗北）と詳細情報を表示する。
 * タップでワールドマップに戻る。
 *
 * 画面フロー:
 * - 勝利時: チャプタークリア処理 → ワールドマップへ
 * - 敗北時: ワールドマップへ（チャプターはクリアされない）
 */
class BattleResultScreen(private val game: TacticsFlameGame) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var titleFont: BitmapFont
    private lateinit var font: BitmapFont
    private lateinit var smallFont: BitmapFont
    private val viewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)

    /** バトル結果データ */
    private lateinit var resultData: BattleResultData

    /** アニメーション用タイマー */
    private var animTimer: Float = 0f

    /** タップ入力受付までのディレイ（秒） */
    private val inputDelay = 1.5f

    companion object {
        private const val TAG = "BattleResultScreen"
    }

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        titleFont = FontManager.getFont(size = 72)
        font = FontManager.getFont(size = 36)
        smallFont = FontManager.getFont(size = 28)

        resultData = game.currentBattleResult ?: BattleResultData(
            chapterInfo = game.gameProgress.selectedChapter!!,
            isVictory = false
        )

        // 勝利時はチャプタークリア処理
        if (resultData.isVictory) {
            game.gameProgress.completeChapter(resultData.chapterInfo.id)
            game.gameProgress.healAllUnits()
            Gdx.app.log(TAG, "勝利! チャプタークリア: ${resultData.chapterInfo.name}")
        } else {
            // 敗北時もHP回復（リトライ可能にするため）
            game.gameProgress.healAllUnits()
            Gdx.app.log(TAG, "敗北... ${resultData.chapterInfo.name}")
        }

        animTimer = 0f
    }

    /**
     * フレーム描画処理
     *
     * @param delta 前フレームからの経過時間（秒）
     */
    override fun render(delta: Float) {
        animTimer += delta

        // 入力処理（ディレイ後）
        if (animTimer > inputDelay && Gdx.input.justTouched()) {
            game.screenManager.navigateToWorldMap()
            return
        }

        // 背景色（勝利: 青系、敗北: 赤系）
        if (resultData.isVictory) {
            Gdx.gl.glClearColor(0.08f, 0.12f, 0.3f, 1f)
        } else {
            Gdx.gl.glClearColor(0.25f, 0.08f, 0.08f, 1f)
        }
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        shapeRenderer.projectionMatrix = viewport.camera.combined
        batch.projectionMatrix = viewport.camera.combined

        renderResultTitle()
        renderResultDetails()
        renderContinuePrompt()
    }

    // ==================== 描画メソッド ====================

    /**
     * リザルトタイトルを描画する
     */
    private fun renderResultTitle() {
        batch.begin()

        if (resultData.isVictory) {
            titleFont.color = Color.GOLD
            titleFont.draw(
                batch, "勝 利 ！",
                GameConfig.VIRTUAL_WIDTH / 2f - 200f,
                GameConfig.VIRTUAL_HEIGHT - 120f
            )
        } else {
            titleFont.color = Color(1f, 0.4f, 0.4f, 1f)
            titleFont.draw(
                batch, "敗 北 ...",
                GameConfig.VIRTUAL_WIDTH / 2f - 200f,
                GameConfig.VIRTUAL_HEIGHT - 120f
            )
        }

        // チャプター名
        font.color = Color.WHITE
        font.draw(
            batch, resultData.chapterInfo.name,
            GameConfig.VIRTUAL_WIDTH / 2f - 180f,
            GameConfig.VIRTUAL_HEIGHT - 200f
        )

        batch.end()
    }

    /**
     * 戦闘結果の詳細を描画する
     */
    private fun renderResultDetails() {
        val panelW = 700f
        val panelH = 440f
        val panelX = GameConfig.VIRTUAL_WIDTH / 2f - panelW / 2f
        val panelY = GameConfig.VIRTUAL_HEIGHT / 2f - panelH / 2f - 40f

        // パネル背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.6f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()

        val borderColor = if (resultData.isVictory) Color.GOLD else Color(1f, 0.3f, 0.3f, 1f)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = borderColor
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 詳細テキスト
        batch.begin()

        var textY = panelY + panelH - 30f
        val textX = panelX + 30f
        val lineH = 36f

        font.color = Color.WHITE
        font.draw(batch, "経過ラウンド: ${resultData.roundCount}", textX, textY)
        textY -= lineH

        font.draw(batch, "撃破した敵: ${resultData.defeatedEnemies} / ${resultData.totalEnemies}", textX, textY)
        textY -= lineH + 16f

        // 生存ユニット一覧
        font.color = Color.CYAN
        font.draw(batch, "— 生存ユニット —", textX, textY)
        textY -= lineH

        for (unit in resultData.survivingUnits) {
            smallFont.color = Color.WHITE
            val hpText = "HP: ${unit.currentHp}/${unit.maxHp}"
            smallFont.draw(batch, "${unit.name}  Lv.${unit.level}  $hpText", textX + 16f, textY)

            // 獲得経験値
            val exp = resultData.expGained[unit.id]
            if (exp != null && exp > 0) {
                smallFont.color = Color.GOLD
                smallFont.draw(batch, "EXP +$exp", textX + 500f, textY)
            }
            textY -= 30f
        }

        if (resultData.survivingUnits.isEmpty()) {
            smallFont.color = Color.GRAY
            smallFont.draw(batch, "なし", textX + 16f, textY)
        }

        batch.end()
    }

    /**
     * 「タップして続ける」プロンプトを描画する
     */
    private fun renderContinuePrompt() {
        if (animTimer < inputDelay) return

        // 点滅効果
        val alpha = ((Math.sin((animTimer * 3f).toDouble()) + 1f) / 2f).toFloat() * 0.7f + 0.3f

        batch.begin()
        font.color = Color(1f, 1f, 1f, alpha)
        font.draw(
            batch, "タップして続ける",
            GameConfig.VIRTUAL_WIDTH / 2f - 140f,
            140f
        )
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
