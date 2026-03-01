package com.tacticsflame

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.tacticsflame.core.ScreenManager
import com.tacticsflame.model.campaign.BattleConfig
import com.tacticsflame.model.campaign.BattleResultData
import com.tacticsflame.model.campaign.GameProgress
import com.tacticsflame.screen.TitleScreen
import com.tacticsflame.util.FontManager

/**
 * メインゲームクラス
 *
 * LibGDX の Game を継承し、画面遷移とゲーム全体の状態を管理する。
 * ScreenManager を介して各 Screen に遷移し、GameProgress でキャンペーン進行を追跡する。
 */
class TacticsFlameGame : Game() {

    /** アセットマネージャー（全画面で共有） */
    lateinit var assetManager: AssetManager
        private set

    /** 画面遷移マネージャー */
    lateinit var screenManager: ScreenManager
        private set

    /** ゲーム進行状態（チャプター・パーティ） */
    lateinit var gameProgress: GameProgress
        private set

    /** バトル設定（BattlePrepScreen → BattleScreen 間の受け渡し） */
    var currentBattleConfig: BattleConfig? = null

    /** バトル結果（BattleScreen → BattleResultScreen 間の受け渡し） */
    var currentBattleResult: BattleResultData? = null

    /**
     * ゲーム初期化処理
     */
    override fun create() {
        Gdx.app.log(TAG, "ゲーム初期化開始")
        assetManager = AssetManager()
        screenManager = ScreenManager(this)
        gameProgress = GameProgress()
        gameProgress.initialize()
        // タイトル画面を表示
        setScreen(TitleScreen(this))
    }

    /**
     * リソース解放
     */
    override fun dispose() {
        super.dispose()
        FontManager.dispose()
        assetManager.dispose()
        Gdx.app.log(TAG, "リソース解放完了")
    }

    companion object {
        private const val TAG = "TacticsFlameGame"
    }
}
