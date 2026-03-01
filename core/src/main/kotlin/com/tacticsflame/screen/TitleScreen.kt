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
 * タイトル画面
 * ゲーム起動時に最初に表示される画面
 */
class TitleScreen(private val game: TacticsFlameGame) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var titleFont: BitmapFont
    private lateinit var subFont: BitmapFont
    private val viewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        titleFont = FontManager.getFont(size = 64)
        subFont = FontManager.getFont(size = 32)
    }

    /**
     * フレーム描画処理
     *
     * @param delta 前フレームからの経過時間（秒）
     */
    override fun render(delta: Float) {
        // 画面クリア
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        batch.projectionMatrix = viewport.camera.combined

        batch.begin()
        // タイトル表示
        titleFont.draw(
            batch,
            GameConfig.TITLE,
            GameConfig.VIRTUAL_WIDTH / 2f - 200f,
            GameConfig.VIRTUAL_HEIGHT / 2f + 100f
        )
        // 操作案内
        subFont.draw(
            batch,
            "Tap to Start",
            GameConfig.VIRTUAL_WIDTH / 2f - 100f,
            GameConfig.VIRTUAL_HEIGHT / 2f - 50f
        )
        batch.end()

        // タッチでワールドマップ画面へ遷移
        if (Gdx.input.justTouched()) {
            game.screenManager.navigateToWorldMap()
        }
    }

    /**
     * ウィンドウリサイズ時の処理
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
