package com.tacticsflame.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.util.FontManager

/**
 * リザルト画面
 * 戦闘結果を表示する画面
 */
class ResultScreen(
    private val game: TacticsFlameGame,
    private val isVictory: Boolean
) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var titleFont: BitmapFont
    private lateinit var subFont: BitmapFont
    private val viewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)

    /**
     * 画面表示時の初期化
     */
    override fun show() {
        batch = SpriteBatch()
        titleFont = FontManager.getFont(size = 64)
        subFont = FontManager.getFont(size = 32)
    }

    /**
     * フレーム描画処理
     */
    override fun render(delta: Float) {
        val bgColor = if (isVictory) Triple(0.1f, 0.2f, 0.4f) else Triple(0.3f, 0.1f, 0.1f)
        Gdx.gl.glClearColor(bgColor.first, bgColor.second, bgColor.third, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined

        batch.begin()
        val text = if (isVictory) "勝利！" else "敗北..."
        titleFont.draw(
            batch, text,
            GameConfig.VIRTUAL_WIDTH / 2f - 150f,
            GameConfig.VIRTUAL_HEIGHT / 2f + 50f
        )
        subFont.draw(
            batch, "タップして続ける",
            GameConfig.VIRTUAL_WIDTH / 2f - 100f,
            GameConfig.VIRTUAL_HEIGHT / 2f - 50f
        )
        batch.end()

        if (Gdx.input.justTouched()) {
            game.screen = TitleScreen(game)
        }
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
        // フォントは FontManager が管理するため、ここでは dispose しない
    }
}
