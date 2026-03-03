package com.tacticsflame.core

import com.badlogic.gdx.Gdx
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.model.campaign.BattleConfig
import com.tacticsflame.model.campaign.BattleResultData
import com.tacticsflame.model.campaign.ChapterInfo
import com.tacticsflame.model.unit.GameUnit

/**
 * 画面遷移を一元管理するマネージャー
 *
 * 各 Screen は ScreenManager を介して次の画面に遷移する。
 * Screen 間の直接依存を排除し、疎結合なナビゲーションを実現する。
 *
 * 画面フロー:
 * ```
 * Title → WorldMap → Formation（部隊編成）→ WorldMap
 *                  → BattlePrep（戦闘準備）→ Battle → BattleResult → WorldMap
 * ```
 */
class ScreenManager(private val game: TacticsFlameGame) {

    /**
     * タイトル画面へ遷移する
     */
    fun navigateToTitle() {
        Gdx.app.log(TAG, "画面遷移: → Title")
        // 遅延インポートを避けるためリフレクション不要、直接生成
        game.setScreen(createScreen(ScreenType.TITLE))
    }

    /**
     * ワールドマップ画面へ遷移する
     */
    fun navigateToWorldMap() {
        Gdx.app.log(TAG, "画面遷移: → WorldMap")
        game.setScreen(createScreen(ScreenType.WORLD_MAP))
    }

    /**
     * 部隊編成画面へ遷移する
     */
    fun navigateToFormation() {
        Gdx.app.log(TAG, "画面遷移: → Formation")
        game.setScreen(createScreen(ScreenType.FORMATION))
    }

    /**
     * 戦闘準備画面へ遷移する
     *
     * @param chapter 対象チャプター
     */
    fun navigateToBattlePrep(chapter: ChapterInfo) {
        Gdx.app.log(TAG, "画面遷移: → BattlePrep (${chapter.name})")
        game.gameProgress.selectedChapter = chapter
        game.setScreen(createScreen(ScreenType.BATTLE_PREP))
    }

    /**
     * バトル画面へ遷移する
     *
     * @param config バトル設定
     */
    fun navigateToBattle(config: BattleConfig) {
        Gdx.app.log(TAG, "画面遷移: → Battle (${config.chapterInfo.name})")
        game.currentBattleConfig = config
        game.setScreen(createScreen(ScreenType.BATTLE))
    }

    /**
     * バトルリザルト画面へ遷移する
     *
     * @param resultData バトル結果データ
     */
    fun navigateToBattleResult(resultData: BattleResultData) {
        Gdx.app.log(TAG, "画面遷移: → BattleResult (勝利: ${resultData.isVictory})")
        game.currentBattleResult = resultData
        game.setScreen(createScreen(ScreenType.BATTLE_RESULT))
    }

    /**
     * 武器装備変更画面へ遷移する
     *
     * @param unit 装備変更対象のユニット
     */
    fun navigateToWeaponEquip(unit: GameUnit) {
        Gdx.app.log(TAG, "画面遷移: → WeaponEquip (${unit.name})")
        game.weaponEquipTarget = unit
        game.setScreen(createScreen(ScreenType.WEAPON_EQUIP))
    }

    /**
     * Screen インスタンスを生成する
     *
     * コンパニオンで Screen クラスを直接参照せず、
     * 型に応じた生成を行う（循環依存の回避）。
     */
    private fun createScreen(type: ScreenType): com.badlogic.gdx.Screen {
        return when (type) {
            ScreenType.TITLE -> com.tacticsflame.screen.TitleScreen(game)
            ScreenType.WORLD_MAP -> com.tacticsflame.screen.WorldMapScreen(game)
            ScreenType.FORMATION -> com.tacticsflame.screen.FormationScreen(game)
            ScreenType.BATTLE_PREP -> com.tacticsflame.screen.BattlePrepScreen(game)
            ScreenType.BATTLE -> com.tacticsflame.screen.BattleScreen(game)
            ScreenType.BATTLE_RESULT -> com.tacticsflame.screen.BattleResultScreen(game)
            ScreenType.WEAPON_EQUIP -> com.tacticsflame.screen.WeaponEquipScreen(
                game,
                requireNotNull(game.weaponEquipTarget) { "weaponEquipTarget が null です" }
            )
        }
    }

    /**
     * 画面種別
     */
    private enum class ScreenType {
        TITLE,
        WORLD_MAP,
        FORMATION,
        BATTLE_PREP,
        BATTLE,
        BATTLE_RESULT,
        WEAPON_EQUIP
    }

    companion object {
        private const val TAG = "ScreenManager"
    }
}
