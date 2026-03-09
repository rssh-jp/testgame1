package com.tacticsflame

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.assets.AssetManager
import com.tacticsflame.core.ScreenManager
import com.tacticsflame.data.SaveManager
import com.tacticsflame.data.UnitClassLoader
import com.tacticsflame.model.campaign.BattleConfig
import com.tacticsflame.model.campaign.BattleResultData
import com.tacticsflame.model.campaign.GameProgress
import com.tacticsflame.model.unit.GameUnit
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

    /** 武器装備変更対象ユニット（FormationScreen → WeaponEquipScreen 間の受け渡し） */
    var weaponEquipTarget: GameUnit? = null

    /** ジョブチェンジ対象ユニット（FormationScreen → ClassChangeScreen 間の受け渡し） */
    var classChangeTarget: GameUnit? = null

    /**
     * ゲーム初期化処理
     */
    override fun create() {
        Gdx.app.log(TAG, "ゲーム初期化開始")
        assetManager = AssetManager()
        screenManager = ScreenManager(this)

        // マスターデータのロード（クラスデータを外部JSONから読み込み）
        val loadedClasses = UnitClassLoader.loadAll()
        if (loadedClasses.isNotEmpty()) {
            com.tacticsflame.model.unit.UnitClass.initialize(loadedClasses)
            Gdx.app.log(TAG, "クラスデータ初期化完了: ${loadedClasses.size}件")
        }

        gameProgress = GameProgress()
        gameProgress.initialize()

        // セーブデータがあればロード
        if (SaveManager.hasSaveData()) {
            val loaded = SaveManager.load(gameProgress)
            Gdx.app.log(TAG, "セーブデータロード: ${if (loaded) "成功" else "失敗（新規データで開始）"}")
        }

        // 起動直後はワールドマップへ直接遷移
        screenManager.navigateToWorldMap()
    }

    /**
     * ゲーム進行状態をセーブする
     *
     * チャプタークリア時や重要なタイミングで呼び出すこと。
     *
     * @return セーブに成功した場合 true
     */
    fun saveGame(): Boolean {
        return SaveManager.save(gameProgress)
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
