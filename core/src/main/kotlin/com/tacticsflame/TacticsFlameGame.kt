package com.tacticsflame

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.tacticsflame.screen.TitleScreen

/**
 * メインゲームクラス
 * LibGDX の Game を継承し、画面遷移を管理する
 */
class TacticsFlameGame : Game() {

    /** アセットマネージャー（全画面で共有） */
    lateinit var assetManager: AssetManager
        private set

    /**
     * ゲーム初期化処理
     */
    override fun create() {
        Gdx.app.log(TAG, "ゲーム初期化開始")
        assetManager = AssetManager()
        // タイトル画面を表示
        setScreen(TitleScreen(this))
    }

    /**
     * リソース解放
     */
    override fun dispose() {
        super.dispose()
        assetManager.dispose()
        Gdx.app.log(TAG, "リソース解放完了")
    }

    companion object {
        private const val TAG = "TacticsFlameGame"
    }
}
