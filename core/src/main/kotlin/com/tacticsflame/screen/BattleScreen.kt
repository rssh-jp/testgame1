package com.tacticsflame.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.battle.BattleResult
import com.tacticsflame.model.campaign.BattleConfig
import com.tacticsflame.model.campaign.BattleResultData
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import com.tacticsflame.render.UnitShapeRenderer
import com.tacticsflame.system.*
import com.tacticsflame.util.FontManager

/**
 * バトル画面
 *
 * CTベースの個別ユニットターン制で戦闘を行う画面。
 * 各ユニットのSPDに応じてCTが蓄積され、閾値に達したユニットから順に行動する。
 * 全ユニット（味方・敵・同盟）がAIにより自動行動する。
 * ユニットの移動はタイルごとにアニメーションで表示される。
 */
class BattleScreen(private val game: TacticsFlameGame) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val camera = OrthographicCamera()
    private lateinit var viewport: ExtendViewport

    // ゲームシステム
    private val turnManager = TurnManager()
    private val pathFinder = PathFinder()
    private val battleSystem = BattleSystem()
    private val aiSystem = AISystem(pathFinder, battleSystem)
    private val victoryChecker = VictoryChecker()
    private val levelUpSystem = LevelUpSystem()

    // バトル設定（BattlePrepScreen から渡されるデータ）
    private var battleConfig: BattleConfig? = null

    // マップとユニット
    private lateinit var battleMap: BattleMap

    /** 初期敵ユニット数（リザルト表示用） */
    private var initialEnemyCount: Int = 0

    // バトル画面の状態
    private var battleState: BattleState = BattleState.CT_ADVANCING

    /**
     * バトル画面の状態
     */
    enum class BattleState {
        /** CTを進行中（次の行動ユニットを決定中） */
        CT_ADVANCING,
        /** AI思考ウェイト中 */
        AI_THINKING,
        /** ユニット移動アニメーション中 */
        UNIT_MOVING,
        /** 戦闘結果表示中 */
        COMBAT_RESULT,
        /** 行動後ウェイト中 */
        POST_ACTION,
        /** 勝利/敗北 */
        RESULT
    }

    // 選択中のユニットと移動範囲
    private var selectedUnit: GameUnit? = null
    private var movablePositions: Set<Position> = emptySet()
    private var attackablePositions: Set<Position> = emptySet()

    /** 情報表示中のユニット（敵味方問わずタップで表示） */
    private var inspectedUnit: GameUnit? = null

    /** 行動順予測キュー */
    private var actionQueue: List<GameUnit> = emptyList()

    /** 勝利フラグ（RESULT状態で使用） */
    private var isVictory: Boolean = false

    // アニメーション・タイマー関連
    /** 汎用状態タイマー（秒） */
    private var stateTimer: Float = 0f

    /** AI決定結果の保持 */
    private var pendingAction: AISystem.AIAction? = null

    /** 移動アニメーション中のユニット */
    private var animatingUnit: GameUnit? = null

    /** 移動アニメーションの経路 */
    private var animationPath: List<Position> = emptyList()

    /** 移動アニメーションの進行度（0〜セグメント数） */
    private var animationProgress: Float = 0f

    /** 戦闘結果の一時保持 */
    private var pendingBattleResult: BattleResult? = null

    /** 撤退確認ダイアログ表示フラグ */
    private var showRetreatConfirm: Boolean = false

    // 撤退ボタンの画面座標（スクリーン座標系、renderRetreatButton で更新）
    private var retreatButtonScreenX: Float = 0f
    private var retreatButtonScreenY: Float = 0f
    private var retreatButtonWidth: Float = 0f
    private var retreatButtonHeight: Float = 0f

    // 確認ダイアログの「はい」「いいえ」ボタンのワールド座標
    private var confirmYesX: Float = 0f
    private var confirmYesY: Float = 0f
    private var confirmYesW: Float = 0f
    private var confirmYesH: Float = 0f
    private var confirmNoX: Float = 0f
    private var confirmNoY: Float = 0f
    private var confirmNoW: Float = 0f
    private var confirmNoH: Float = 0f

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        font = FontManager.getFont(size = 24)

        // BattleConfig からマップを構築、なければテスト用マップにフォールバック
        battleConfig = game.currentBattleConfig
        battleMap = setupBattleMap()

        // 初期敵数を記録
        initialEnemyCount = battleMap.getAllUnits()
            .count { it.second.faction == Faction.ENEMY }

        // マップサイズに基づいてビューポートを設定（マップが画面いっぱいに表示される）
        initViewport()

        // CT初期化
        val allUnits = battleMap.getAllUnits().map { it.second }
        turnManager.reset(allUnits)

        // 行動順予測を更新
        updateActionQueue()

        battleState = BattleState.CT_ADVANCING
        Gdx.app.log(TAG, "バトル画面初期化完了（CTベースターン制）")
    }

    /**
     * BattleConfig に基づいてマップとユニットを配置する
     *
     * BattleConfig がある場合はそのデータを使用し、
     * ない場合（レガシー互換）はテスト用マップを生成する。
     *
     * @return 構築された BattleMap
     */
    private fun setupBattleMap(): BattleMap {
        val config = battleConfig ?: return createTestMap()

        val map = config.battleMap

        // プレイヤーユニット配置
        for (unit in config.playerUnits) {
            val pos = config.playerPositions[unit.id] ?: continue
            map.placeUnit(unit, pos)
        }

        // 敵ユニット配置
        for (unit in config.enemyUnits) {
            val pos = config.enemyPositions[unit.id] ?: continue
            map.placeUnit(unit, pos)
        }

        Gdx.app.log(TAG, "BattleConfig からマップ構築: ${config.chapterInfo.name}")
        return map
    }

    /**
     * フレーム描画処理
     */
    override fun render(delta: Float) {
        // タッチ入力処理（RESULT以外の全状態で受付）
        if (battleState != BattleState.RESULT) {
            handleTouchInput()
        }

        // 確認ダイアログ表示中はゲーム進行を一時停止
        if (!showRetreatConfirm) {
            // 状態に応じたロジック更新（描画前に実行）
            when (battleState) {
                BattleState.CT_ADVANCING -> advanceCT()
                BattleState.AI_THINKING -> processAIThinking(delta)
                BattleState.UNIT_MOVING -> processUnitMoving(delta)
                BattleState.COMBAT_RESULT -> processCombatResult(delta)
                BattleState.POST_ACTION -> processPostAction(delta)
                BattleState.RESULT -> handleResultInput()
            }
        }

        // 画面クリア
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        centerCameraOnMap()
        shapeRenderer.projectionMatrix = camera.combined

        // マップ・ユニット描画
        renderMap()
        renderUnits()
        renderUnitNames()

        // アクティブユニットのステータスパネル（右上）
        val activeUnit = turnManager.activeUnit
        if (activeUnit != null) {
            renderStatusPanel(activeUnit)
        }

        // タップしたユニットの調査パネル（左下）
        val inspected = inspectedUnit
        if (inspected != null && !inspected.isDefeated) {
            renderInspectionPanel(inspected)
        }

        // 行動順キュー描画
        renderActionQueue()

        // ターン情報描画
        renderTurnInfo()

        // 撤退ボタン描画（RESULT以外で表示）
        if (battleState != BattleState.RESULT) {
            renderRetreatButton()
        }

        // 撤退確認ダイアログ描画
        if (showRetreatConfirm) {
            renderRetreatConfirmDialog()
        }
    }

    // ==================== タッチ入力処理 ====================

    /**
     * タッチ入力を処理する
     *
     * 画面上のユニットをタッチした場合、そのユニットを調査対象（inspectedUnit）にセットし、
     * 画面左下に調査パネルを表示する。空マスをタッチした場合は調査パネルを閉じる。
     * RESULT状態以外の全状態で受け付ける。
     */
    private fun handleTouchInput() {
        if (!Gdx.input.justTouched()) return

        val screenX = Gdx.input.x.toFloat()
        val screenY = Gdx.input.y.toFloat()

        // 撤退確認ダイアログが表示中の場合、ダイアログのボタンのみ受け付ける
        if (showRetreatConfirm) {
            handleRetreatConfirmInput(screenX, screenY)
            return
        }

        // 撤退ボタンのタッチ判定（スクリーン座標で判定）
        if (isRetreatButtonTouched(screenX, screenY)) {
            showRetreatConfirm = true
            Gdx.app.log(TAG, "撤退ボタン押下 → 確認ダイアログ表示")
            return
        }

        // スクリーン座標をワールド座標に変換
        val worldCoords = viewport.unproject(Vector3(screenX, screenY, 0f))

        // ワールド座標をタイル座標に変換（floor で負方向への丸めを正しく処理）
        val tileX = MathUtils.floor(worldCoords.x / GameConfig.TILE_SIZE)
        val tileY = MathUtils.floor(worldCoords.y / GameConfig.TILE_SIZE)

        // マップ範囲内かチェック
        if (tileX < 0 || tileX >= battleMap.width || tileY < 0 || tileY >= battleMap.height) {
            inspectedUnit = null
            return
        }

        // タップした座標にユニットがいるか確認
        val tappedUnit = battleMap.getUnitAt(Position(tileX, tileY))
        if (tappedUnit != null && !tappedUnit.isDefeated) {
            inspectedUnit = tappedUnit
            Gdx.app.log(TAG, "ユニット調査: ${tappedUnit.name}（${tappedUnit.faction}）")
        } else {
            // 空マスタップで調査パネルを閉じる
            inspectedUnit = null
        }
    }

    /**
     * 撤退ボタンのタッチ判定（スクリーン座標系）
     *
     * @param screenX タッチX座標（スクリーン座標）
     * @param screenY タッチY座標（スクリーン座標）
     * @return ボタン内をタッチした場合 true
     */
    private fun isRetreatButtonTouched(screenX: Float, screenY: Float): Boolean {
        return screenX >= retreatButtonScreenX &&
            screenX <= retreatButtonScreenX + retreatButtonWidth &&
            screenY >= retreatButtonScreenY &&
            screenY <= retreatButtonScreenY + retreatButtonHeight
    }

    /**
     * 撤退確認ダイアログのタッチ入力を処理する
     *
     * @param screenX タッチX座標（スクリーン座標）
     * @param screenY タッチY座標（スクリーン座標）
     */
    private fun handleRetreatConfirmInput(screenX: Float, screenY: Float) {
        val worldCoords = viewport.unproject(Vector3(screenX, screenY, 0f))
        val wx = worldCoords.x
        val wy = worldCoords.y

        // 「はい」ボタン判定
        if (wx >= confirmYesX && wx <= confirmYesX + confirmYesW &&
            wy >= confirmYesY && wy <= confirmYesY + confirmYesH) {
            Gdx.app.log(TAG, "撤退を実行")
            showRetreatConfirm = false
            executeRetreat()
            return
        }

        // 「いいえ」ボタン判定
        if (wx >= confirmNoX && wx <= confirmNoX + confirmNoW &&
            wy >= confirmNoY && wy <= confirmNoY + confirmNoH) {
            Gdx.app.log(TAG, "撤退をキャンセル")
            showRetreatConfirm = false
            return
        }
    }

    /**
     * 撤退を実行する（敗北扱いでリザルト画面に遷移）
     */
    private fun executeRetreat() {
        isVictory = false
        battleState = BattleState.RESULT
    }

    // ==================== ゲームロジック ==

    /**
     * CTを進行させて次の行動ユニットを決定する
     *
     * 全ユニットのCTにSPDを加算し、閾値に達したユニットのターンに遷移する。
     * 全陣営のユニットがAI自動行動する。
     */
    private fun advanceCT() {
        val allUnits = battleMap.getAllUnits().map { it.second }
        val nextUnit = turnManager.advanceToNextUnit(allUnits) ?: return

        // 行動順予測を更新
        updateActionQueue()

        // 全陣営AI自動行動：思考ウェイトに遷移
        selectedUnit = nextUnit
        stateTimer = 0f
        battleState = BattleState.AI_THINKING
        Gdx.app.log(TAG, "${nextUnit.name} のターン（CT: ${nextUnit.ct}）")
    }

    /**
     * AI思考ウェイトを処理する
     *
     * 思考時間経過後、AIの行動を決定し適切な次状態に遷移する。
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processAIThinking(delta: Float) {
        stateTimer += delta
        if (stateTimer < GameConfig.AI_THINK_DELAY) return

        val activeUnit = turnManager.activeUnit ?: run {
            battleState = BattleState.CT_ADVANCING
            return
        }

        // AIパターン決定: PLAYERは作戦に基づく、同盟はDEFENSIVE、敵はAGGRESSIVE
        val pattern = when (activeUnit.faction) {
            Faction.PLAYER -> when (activeUnit.tactic) {
                UnitTactic.CHARGE -> AISystem.AIPattern.AGGRESSIVE
                UnitTactic.CAUTIOUS -> AISystem.AIPattern.CAUTIOUS
                UnitTactic.SUPPORT -> AISystem.AIPattern.SUPPORT
                UnitTactic.FLEE -> AISystem.AIPattern.FLEE
            }
            Faction.ALLY -> AISystem.AIPattern.DEFENSIVE
            Faction.ENEMY -> AISystem.AIPattern.AGGRESSIVE
        }

        // 行動決定
        val action = aiSystem.decideAction(activeUnit, battleMap, pattern)
        pendingAction = action

        when (val act = action.action) {
            is AISystem.Action.MoveAndAttack -> {
                val unitPos = battleMap.getUnitPosition(activeUnit)!!
                if (act.moveTo != unitPos) {
                    startMovementAnimation(activeUnit, unitPos, act.moveTo)
                } else {
                    // 移動なしで攻撃
                    startCombat(activeUnit, act.target)
                }
            }
            is AISystem.Action.Move -> {
                val unitPos = battleMap.getUnitPosition(activeUnit)!!
                if (act.moveTo != unitPos) {
                    startMovementAnimation(activeUnit, unitPos, act.moveTo)
                } else {
                    enterPostAction()
                }
            }
            is AISystem.Action.Wait -> {
                Gdx.app.log(TAG, "AI待機: ${activeUnit.name}")
                enterPostAction()
            }
        }
    }

    /**
     * 移動アニメーションを開始する
     *
     * @param unit 移動ユニット
     * @param from 移動元座標
     * @param to 移動先座標
     */
    private fun startMovementAnimation(unit: GameUnit, from: Position, to: Position) {
        val path = pathFinder.findPath(unit, from, to, battleMap)
        if (path.size >= 2) {
            animatingUnit = unit
            animationPath = path
            animationProgress = 0f
            battleState = BattleState.UNIT_MOVING
        } else {
            // 経路が見つからない場合は直接移動（フォールバック）
            battleMap.moveUnit(from, to)
            Gdx.app.log(TAG, "AI移動完了（直接）: ${unit.name}")
            val action = pendingAction?.action
            if (action is AISystem.Action.MoveAndAttack) {
                startCombat(unit, action.target)
            } else {
                enterPostAction()
            }
        }
    }

    /**
     * ユニット移動アニメーションを進行させる
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processUnitMoving(delta: Float) {
        val totalSegments = (animationPath.size - 1).toFloat()
        if (totalSegments <= 0f) {
            finishMovement()
            return
        }

        animationProgress += delta / GameConfig.MOVE_TIME_PER_TILE
        if (animationProgress >= totalSegments) {
            animationProgress = totalSegments
            finishMovement()
        }
    }

    /**
     * 移動アニメーション完了処理
     *
     * マップ上の位置を更新し、攻撃があれば戦闘に遷移する。
     */
    private fun finishMovement() {
        val activeUnit = animatingUnit ?: turnManager.activeUnit ?: run {
            enterPostAction()
            return
        }

        // マップ上の位置を更新
        val unitPos = battleMap.getUnitPosition(activeUnit)
        val destination = animationPath.lastOrNull()
        if (unitPos != null && destination != null && unitPos != destination) {
            battleMap.moveUnit(unitPos, destination)
        }

        animatingUnit = null
        animationPath = emptyList()
        animationProgress = 0f

        Gdx.app.log(TAG, "AI移動完了: ${activeUnit.name}")

        // 攻撃がある場合は戦闘へ
        val action = pendingAction?.action
        if (action is AISystem.Action.MoveAndAttack) {
            startCombat(activeUnit, action.target)
        } else {
            enterPostAction()
        }
    }

    /**
     * 戦闘を開始する
     *
     * @param attacker 攻撃ユニット
     * @param target 攻撃対象ユニット
     */
    private fun startCombat(attacker: GameUnit, target: GameUnit) {
        val result = battleSystem.executeBattle(attacker, target, battleMap)
        pendingBattleResult = result
        Gdx.app.log(
            TAG,
            "AI攻撃: ${attacker.name} → ${target.name} " +
                "(ダメージ: ${result.attacks.sumOf { it.damage }})"
        )
        stateTimer = 0f
        battleState = BattleState.COMBAT_RESULT
    }

    /**
     * 戦闘結果表示を処理する
     *
     * 表示時間経過後、経験値を付与し、撃破ユニットを除去して行動後ウェイトに遷移する。
     * プレイヤーユニットが攻撃した場合のみ経験値を獲得する。
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processCombatResult(delta: Float) {
        stateTimer += delta
        if (stateTimer < GameConfig.COMBAT_RESULT_DELAY) return

        // 撃破ユニットを除去 & 経験値付与
        val result = pendingBattleResult
        if (result != null) {
            // 経験値付与: 攻撃側がプレイヤーで撃破されていない場合
            if (result.attacker.faction == Faction.PLAYER && !result.attackerDefeated) {
                awardExpToUnit(result.attacker, result.expGained)
            }
            // 経験値付与: 防御側がプレイヤーで撃破されていない場合（反撃で命中した場合のみ）
            if (result.defender.faction == Faction.PLAYER && !result.defenderDefeated) {
                // 防御側は反撃が命中した場合のみ経験値を得る
                val defenderHit = result.attacks.any { !it.attackerIsInitiator && it.hit }
                if (defenderHit) {
                    val defenderExp = battleSystem.calculateExp(result.defender, result.attacker)
                    awardExpToUnit(result.defender, defenderExp)
                }
            }

            if (result.defenderDefeated) {
                val targetPos = battleMap.getUnitPosition(result.defender)
                if (targetPos != null) battleMap.removeUnit(targetPos)
                // 調査中のユニットが撃破された場合はパネルを閉じる
                if (inspectedUnit == result.defender) inspectedUnit = null
            }
            if (result.attackerDefeated) {
                val attackerPos = battleMap.getUnitPosition(result.attacker)
                if (attackerPos != null) battleMap.removeUnit(attackerPos)
                // 調査中のユニットが撃破された場合はパネルを閉じる
                if (inspectedUnit == result.attacker) inspectedUnit = null
            }
        }

        pendingBattleResult = null
        enterPostAction()
    }

    /**
     * ユニットに経験値を付与し、レベルアップ処理を行う
     *
     * @param unit 対象ユニット
     * @param expGained 獲得経験値
     */
    private fun awardExpToUnit(unit: GameUnit, expGained: Int) {
        val levelUpResult = levelUpSystem.awardExp(unit, expGained)
        Gdx.app.log(TAG, "${unit.name} が経験値 $expGained を獲得（EXP: ${unit.exp}/${GameConfig.EXP_TO_LEVEL_UP}）")
        if (levelUpResult != null) {
            val g = levelUpResult.growthResult
            Gdx.app.log(
                TAG,
                "${unit.name} がレベル ${levelUpResult.newLevel} にアップ！ " +
                    "成長: HP+${g.hp} STR+${g.str} MAG+${g.mag} SKL+${g.skl} " +
                    "SPD+${g.spd} LCK+${g.lck} DEF+${g.def} RES+${g.res}"
            )
        }
    }

    /**
     * 行動後ウェイトを処理する
     *
     * ウェイト時間経過後、行動完了処理に進む。
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processPostAction(delta: Float) {
        stateTimer += delta
        if (stateTimer < GameConfig.POST_ACTION_DELAY) return

        val activeUnit = turnManager.activeUnit ?: run {
            battleState = BattleState.CT_ADVANCING
            return
        }

        completeUnitAction(activeUnit)
    }

    /**
     * 行動後ウェイト状態に遷移する
     */
    private fun enterPostAction() {
        stateTimer = 0f
        battleState = BattleState.POST_ACTION
    }

    /**
     * ユニットの行動完了処理（共通）
     *
     * CT消費・勝敗判定・状態遷移を行う。
     *
     * @param unit 行動を完了したユニット
     */
    private fun completeUnitAction(unit: GameUnit) {
        // 選択状態をクリア（inspectedUnit はプレイヤーが明示的に解除するまで保持）
        selectedUnit = null
        movablePositions = emptySet()
        attackablePositions = emptySet()

        // アニメーション状態をクリア
        pendingAction = null
        pendingBattleResult = null
        animatingUnit = null
        animationPath = emptyList()
        animationProgress = 0f
        stateTimer = 0f

        // TurnManagerの行動完了処理
        val allUnits = battleMap.getAllUnits().map { it.second }
        turnManager.completeAction(unit, allUnits)

        // 勝敗判定（BattleConfig の勝利条件を使用）
        val conditionType = battleConfig?.victoryCondition ?: VictoryChecker.VictoryConditionType.DEFEAT_ALL
        val outcome = victoryChecker.checkOutcome(
            battleMap, conditionType,
            turnNumber = turnManager.roundNumber,
            bossId = battleConfig?.enemyUnits?.find { it.isLord }?.id
        )
        when (outcome) {
            VictoryChecker.BattleOutcome.VICTORY -> {
                Gdx.app.log(TAG, "勝利！")
                isVictory = true
                battleState = BattleState.RESULT
            }
            VictoryChecker.BattleOutcome.DEFEAT -> {
                Gdx.app.log(TAG, "敗北...")
                isVictory = false
                battleState = BattleState.RESULT
            }
            VictoryChecker.BattleOutcome.ONGOING -> {
                // 行動順予測を更新して次のCT進行へ
                updateActionQueue()
                battleState = BattleState.CT_ADVANCING
            }
        }
    }

    /**
     * リザルト画面への遷移入力処理
     */
    private fun handleResultInput() {
        if (Gdx.input.justTouched()) {
            navigateToResult()
        }
    }

    /**
     * バトル結果をまとめてリザルト画面へ遷移する
     */
    private fun navigateToResult() {
        val config = battleConfig
        if (config != null) {
            // BattleConfig 経由の場合: BattleResultScreen へ
            val survivingPlayers = battleMap.getAllUnits()
                .filter { it.second.faction == Faction.PLAYER && !it.second.isDefeated }
                .map { it.second }
            val defeatedEnemies = initialEnemyCount - battleMap.getAllUnits()
                .count { it.second.faction == Faction.ENEMY && !it.second.isDefeated }

            val resultData = BattleResultData(
                chapterInfo = config.chapterInfo,
                isVictory = isVictory,
                roundCount = turnManager.roundNumber,
                defeatedEnemies = defeatedEnemies,
                totalEnemies = initialEnemyCount,
                survivingUnits = survivingPlayers
            )
            game.screenManager.navigateToBattleResult(resultData)
        } else {
            // レガシーフォールバック: 旧 ResultScreen へ
            game.screen = ResultScreen(game, isVictory)
        }
    }

    /**
     * 行動順予測キューを更新する
     */
    private fun updateActionQueue() {
        val allUnits = battleMap.getAllUnits().map { it.second }
        actionQueue = turnManager.predictActionOrder(allUnits, ACTION_QUEUE_SIZE)
    }

    // ==================== ビューポート・カメラ ====================

    /**
     * マップサイズに合わせてビューポートを初期化する
     *
     * ExtendViewportを使用し、マップ全体が常に画面内に収まるようにする。
     * 画面のアスペクト比に応じてビューポートが拡張されるため、
     * レターボックス（黒帯）が発生せず画面いっぱいにマップが表示される。
     */
    private fun initViewport() {
        val mapPixelW = battleMap.width * GameConfig.TILE_SIZE
        val mapPixelH = battleMap.height * GameConfig.TILE_SIZE
        val padding = GameConfig.TILE_SIZE * GameConfig.BATTLE_MAP_PADDING_TILES
        viewport = ExtendViewport(
            mapPixelW + padding * 2,
            mapPixelH + padding * 2,
            camera
        )
        // 現在のウィンドウサイズで更新
        viewport.update(Gdx.graphics.width, Gdx.graphics.height)
        centerCameraOnMap()
    }

    /**
     * カメラをマップ中央に配置する
     */
    private fun centerCameraOnMap() {
        camera.position.set(
            battleMap.width * GameConfig.TILE_SIZE / 2f,
            battleMap.height * GameConfig.TILE_SIZE / 2f,
            0f
        )
        camera.update()
    }

    // ==================== 描画メソッド ====================

    /**
     * マップを描画する（プロトタイプ：ShapeRendererで色分け）
     */
    private fun renderMap() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (y in 0 until battleMap.height) {
            for (x in 0 until battleMap.width) {
                val tile = battleMap.getTile(x, y) ?: continue
                when (tile.terrainType) {
                    TerrainType.PLAIN -> shapeRenderer.setColor(0.5f, 0.8f, 0.3f, 1f)
                    TerrainType.FOREST -> shapeRenderer.setColor(0.2f, 0.5f, 0.1f, 1f)
                    TerrainType.MOUNTAIN -> shapeRenderer.setColor(0.6f, 0.5f, 0.3f, 1f)
                    TerrainType.FORT -> shapeRenderer.setColor(0.7f, 0.7f, 0.7f, 1f)
                    TerrainType.WATER -> shapeRenderer.setColor(0.2f, 0.4f, 0.8f, 1f)
                    TerrainType.WALL -> shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f)
                    TerrainType.VILLAGE -> shapeRenderer.setColor(0.8f, 0.6f, 0.3f, 1f)
                    TerrainType.BRIDGE -> shapeRenderer.setColor(0.6f, 0.5f, 0.4f, 1f)
                }
                val tileSize = GameConfig.TILE_SIZE
                shapeRenderer.rect(
                    x * tileSize, y * tileSize,
                    tileSize - 1, tileSize - 1
                )
            }
        }
        shapeRenderer.end()
    }

    /**
     * ユニットを描画する
     *
     * 陣営カラーの円でユニットを表示し、
     * アクティブユニットには金色のリングを描画する。
     * 各ユニットの下にHPバーを、周囲に円形CTバーを表示する。
     * 移動アニメーション中のユニットは補間位置で描画する。
     */
    private fun renderUnits() {
        val activeUnit = turnManager.activeUnit
        val tileSize = GameConfig.TILE_SIZE

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((pos, unit) in battleMap.getAllUnits()) {
            if (unit.isDefeated) continue
            // 移動アニメーション中のユニットはスキップ（別途描画）
            if (unit == animatingUnit && battleState == BattleState.UNIT_MOVING) continue

            val cx = pos.x * tileSize + tileSize / 2
            val cy = pos.y * tileSize + tileSize / 2
            drawUnitShape(unit, cx, cy, unit == activeUnit)
        }

        // 移動アニメーション中のユニットを補間位置で描画
        if (battleState == BattleState.UNIT_MOVING && animatingUnit != null && animationPath.size >= 2) {
            val (cx, cy) = getAnimatedUnitPosition()
            drawUnitShape(animatingUnit!!, cx, cy, true)
        }
        shapeRenderer.end()
    }

    /**
     * マップ上の各ユニットの上に名前ラベルを描画する
     *
     * 各ユニットのタイル上方に暗い背景付きで名前を中央揃え表示する。
     * 背景を先に ShapeRenderer で一括描画し、その後 SpriteBatch で文字を描画する。
     * 撃破済みユニットは非表示、移動アニメーション中のユニットは補間位置に表示する。
     */
    private fun renderUnitNames() {
        val tileSize = GameConfig.TILE_SIZE

        // 1. 名前表示位置の一時リストを構築
        val nameEntries = mutableListOf<Triple<GameUnit, Float, Float>>()

        for ((pos, unit) in battleMap.getAllUnits()) {
            if (unit.isDefeated) continue
            if (unit == animatingUnit && battleState == BattleState.UNIT_MOVING) continue
            val cx = pos.x * tileSize + tileSize / 2f
            val cy = pos.y * tileSize + tileSize / 2f
            nameEntries.add(Triple(unit, cx, cy))
        }

        // 移動アニメーション中のユニット
        if (battleState == BattleState.UNIT_MOVING && animatingUnit != null && animationPath.size >= 2) {
            val (cx, cy) = getAnimatedUnitPosition()
            nameEntries.add(Triple(animatingUnit!!, cx, cy))
        }

        if (nameEntries.isEmpty()) return

        // 2. 暗い背景矩形を ShapeRenderer で一括描画
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((unit, cx, cy) in nameEntries) {
            glyphLayout.setText(font, unit.name)
            val textW = glyphLayout.width
            val textH = glyphLayout.height
            val padX = 6f
            val padY = 3f
            val bgX = cx - textW / 2f - padX
            val bgY = cy + tileSize / 2f + 4f - padY
            val bgW = textW + padX * 2
            val bgH = textH + padY * 2

            shapeRenderer.setColor(0f, 0f, 0f, 0.7f)
            shapeRenderer.rect(bgX, bgY, bgW, bgH)
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 3. 文字をSpriteBatchで描画
        batch.projectionMatrix = camera.combined
        batch.begin()
        for ((unit, cx, cy) in nameEntries) {
            drawUnitName(unit, cx, cy)
        }
        batch.end()
    }

    /**
     * 個別のユニット名ラベルを描画する
     *
     * タイル上端のやや上にユニット名を中央揃えで表示する。
     * 暗背景の上に明るい文字を描画して高コントラストを確保する。
     *
     * @param unit 描画対象ユニット
     * @param cx ユニット中心X座標（ピクセル）
     * @param cy ユニット中心Y座標（ピクセル）
     */
    private fun drawUnitName(unit: GameUnit, cx: Float, cy: Float) {
        val tileSize = GameConfig.TILE_SIZE

        // 陣営に応じた明るい文字色を設定
        font.color = when (unit.faction) {
            Faction.PLAYER -> Color(0.85f, 0.93f, 1f, 1f)    // 明るい青白
            Faction.ENEMY -> Color(1f, 0.75f, 0.75f, 1f)     // 明るいピンク
            Faction.ALLY -> Color(0.75f, 1f, 0.85f, 1f)      // 明るい黄緑
        }

        // テキスト幅を計算して中央揃えにする
        glyphLayout.setText(font, unit.name)
        val textX = cx - glyphLayout.width / 2f
        val textY = cy + tileSize / 2f + 4f + glyphLayout.height  // 背景矩形内に収まる位置

        font.draw(batch, unit.name, textX, textY)
    }

    /**
     * ユニットの図形（円・HPバー・円形CTバー）を描画する
     *
     * 描画順序:
     * 1. アクティブユニットの金色リング（最外周）
     * 2. 円形CTバー背景（暗い円）
     * 3. 円形CTバー本体（CT割合に応じた扇形、上から反時計回り）
     * 4. ユニット本体の陣営カラー円（CTリング内側を覆いリング効果を作る）
     * 5. HPバー（ユニット下部の小さな直線バー）
     *
     * 円形CTバーは360°で100（CT_THRESHOLD）を表し、
     * ユニット円の外側にリング状に表示される。
     *
     * @param unit 描画対象ユニット
     * @param cx ユニット中心X座標（ピクセル）
     * @param cy ユニット中心Y座標（ピクセル）
     * @param isActive アクティブユニットかどうか
     */
    private fun drawUnitShape(unit: GameUnit, cx: Float, cy: Float, isActive: Boolean) {
        val tileSize = GameConfig.TILE_SIZE
        val unitRadius = tileSize / 3f
        val ctRingRadius = tileSize * 0.42f

        // 1. アクティブユニットの金色リング（最外周）
        if (isActive) {
            shapeRenderer.setColor(1f, 0.85f, 0.1f, 1f)
            shapeRenderer.circle(cx, cy, tileSize / 2.2f)
        }

        // 2. 円形CTバー背景（暗い円でリングのベースを描画）
        shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 1f)
        shapeRenderer.circle(cx, cy, ctRingRadius)

        // 3. 円形CTバー本体（CT割合に応じた扇形を上から反時計回りに描画）
        val ctRatio = (unit.ct.toFloat() / GameConfig.CT_THRESHOLD).coerceIn(0f, 1f)
        if (ctRatio > 0f) {
            when {
                ctRatio >= 0.8f -> shapeRenderer.setColor(1f, 0.9f, 0.2f, 1f)
                ctRatio >= 0.5f -> shapeRenderer.setColor(0.3f, 0.8f, 1f, 1f)
                else -> shapeRenderer.setColor(0.4f, 0.7f, 0.4f, 1f)
            }
            shapeRenderer.arc(cx, cy, ctRingRadius, 90f, 360f * ctRatio, 64)
        }

        // 4a. ベース円でCTリング内側を確実にカバー（非円形シェイプでもリング幅を均一に保つ）
        shapeRenderer.setColor(0.12f, 0.12f, 0.18f, 1f)
        shapeRenderer.circle(cx, cy, unitRadius)

        // 4b. ユニット本体（兵種ごとの形状を上に重ねて描画）
        UnitShapeRenderer.drawClassShape(shapeRenderer, unit, cx, cy, unitRadius)

        // 5. HPバー（ユニット下部に小さなバーを描画）
        val barWidth = tileSize * 0.7f
        val barHeight = 4f
        val barX = cx - barWidth / 2
        val barY = cy - tileSize / 2 + 2f
        val hpRatio = (unit.currentHp.toFloat() / unit.maxHp.toFloat()).coerceIn(0f, 1f)

        // バー背景
        shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 1f)
        shapeRenderer.rect(barX, barY, barWidth, barHeight)

        // バー本体（HP割合に応じた色: 緑→黄→赤）
        when {
            hpRatio > 0.5f -> shapeRenderer.setColor(0.2f, 0.9f, 0.2f, 1f)
            hpRatio > 0.25f -> shapeRenderer.setColor(0.9f, 0.9f, 0.1f, 1f)
            else -> shapeRenderer.setColor(0.9f, 0.2f, 0.2f, 1f)
        }
        shapeRenderer.rect(barX, barY, barWidth * hpRatio, barHeight)
    }

    /**
     * 移動アニメーション中のユニットの補間位置を計算する
     *
     * @return (中心X座標, 中心Y座標) のペア
     */
    private fun getAnimatedUnitPosition(): Pair<Float, Float> {
        val tileSize = GameConfig.TILE_SIZE
        val maxSegment = (animationPath.size - 2).coerceAtLeast(0)
        val segmentIndex = animationProgress.toInt().coerceIn(0, maxSegment)
        val t = (animationProgress - segmentIndex).coerceIn(0f, 1f)
        val from = animationPath[segmentIndex]
        val to = animationPath[(segmentIndex + 1).coerceAtMost(animationPath.size - 1)]
        val cx = (from.x + (to.x - from.x) * t) * tileSize + tileSize / 2
        val cy = (from.y + (to.y - from.y) * t) * tileSize + tileSize / 2
        return cx to cy
    }

    /**
     * 移動可能範囲を描画する
     */
    private fun renderMovableRange() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        // 移動可能マス（青半透明）
        shapeRenderer.setColor(0.3f, 0.3f, 1f, 0.3f)
        for (pos in movablePositions) {
            val tileSize = GameConfig.TILE_SIZE
            shapeRenderer.rect(pos.x * tileSize, pos.y * tileSize, tileSize - 1, tileSize - 1)
        }
        // 攻撃可能マス（赤半透明）
        shapeRenderer.setColor(1f, 0.3f, 0.3f, 0.3f)
        for (pos in attackablePositions) {
            val tileSize = GameConfig.TILE_SIZE
            shapeRenderer.rect(pos.x * tileSize, pos.y * tileSize, tileSize - 1, tileSize - 1)
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    /**
     * 行動順キューを画面左側に描画する
     *
     * 今後行動するユニットの順番を陣営カラーで一覧表示する。
     */
    private fun renderActionQueue() {
        if (actionQueue.isEmpty()) return

        val viewLeft = camera.position.x - viewport.worldWidth / 2f
        val viewTop = camera.position.y + viewport.worldHeight / 2f
        val panelX = viewLeft + 16f
        val panelY = viewTop - 60f
        val entryHeight = 36f
        val panelWidth = 200f
        val panelHeight = entryHeight * actionQueue.size + 20f

        // 半透明背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.6f)
        shapeRenderer.rect(panelX, panelY - panelHeight, panelWidth, panelHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 各エントリーの描画
        batch.projectionMatrix = camera.combined
        batch.begin()

        var y = panelY - 10f
        font.color = Color.GOLD
        font.draw(batch, "ACTION", panelX + 8f, y)
        y -= entryHeight

        for ((index, unit) in actionQueue.withIndex()) {
            font.color = when (unit.faction) {
                Faction.PLAYER -> Color(0.4f, 0.6f, 1f, 1f)
                Faction.ENEMY -> Color(1f, 0.4f, 0.4f, 1f)
                Faction.ALLY -> Color(0.4f, 1f, 0.6f, 1f)
            }
            val marker = if (index == 0) ">> " else "${index + 1}. "
            font.draw(batch, "$marker${unit.name}", panelX + 8f, y)
            y -= entryHeight
        }

        batch.end()
    }

    /**
     * ターン情報を画面上部中央に描画する
     */
    private fun renderTurnInfo() {
        batch.projectionMatrix = camera.combined
        batch.begin()

        val activeUnit = turnManager.activeUnit
        val roundText = "Round ${turnManager.roundNumber}"

        val viewCenterX = camera.position.x
        val viewTop = camera.position.y + viewport.worldHeight / 2f

        font.color = Color.WHITE
        font.draw(batch, roundText, viewCenterX - 80f, viewTop - 16f)

        if (activeUnit != null) {
            font.color = when (activeUnit.faction) {
                Faction.PLAYER -> Color(0.4f, 0.6f, 1f, 1f)
                Faction.ENEMY -> Color(1f, 0.4f, 0.4f, 1f)
                Faction.ALLY -> Color(0.4f, 1f, 0.6f, 1f)
            }
            val activeText = "${activeUnit.name} のターン"
            font.draw(batch, activeText, viewCenterX - 100f, viewTop - 52f)
        }

        batch.end()
    }

    /**
     * ユニットのステータスパネルを描画する
     *
     * 画面右上に半透明の背景付きでユニット情報を表示。
     * HP・CT・各ステータス・装備武器を一覧表示する。
     *
     * @param unit 表示対象のユニット
     */
    private fun renderStatusPanel(unit: GameUnit) {
        val panelWidth = 380f
        val panelHeight = 560f
        val viewRight = camera.position.x + viewport.worldWidth / 2f
        val viewTop = camera.position.y + viewport.worldHeight / 2f
        val panelX = viewRight - panelWidth - 16f
        val panelY = viewTop - panelHeight - 16f

        // 半透明背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.75f)
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight)
        shapeRenderer.end()

        // 枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        val borderColor = when (unit.faction) {
            Faction.PLAYER -> Color(0.3f, 0.5f, 1f, 1f)
            Faction.ENEMY -> Color(1f, 0.3f, 0.3f, 1f)
            Faction.ALLY -> Color(0.3f, 1f, 0.5f, 1f)
        }
        shapeRenderer.color = borderColor
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト描画
        batch.projectionMatrix = camera.combined
        batch.begin()

        var textY = panelY + panelHeight - 20f
        val textX = panelX + 16f
        val lineHeight = 32f

        // ユニット名と兵種
        font.color = borderColor
        font.draw(batch, unit.name, textX, textY)
        textY -= lineHeight

        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Lv.${unit.level}  ${unit.unitClass.name}", textX, textY)
        textY -= lineHeight

        // 経験値テキスト（プレイヤーユニットのみ表示）
        if (unit.faction == Faction.PLAYER) {
            font.color = Color(0.8f, 0.7f, 0.3f, 1f)
            font.draw(batch, "EXP  ${unit.exp} / ${GameConfig.EXP_TO_LEVEL_UP}", textX, textY)
            textY -= lineHeight
        }

        // HPテキスト
        textY -= 12f
        font.color = Color.WHITE
        font.draw(batch, "HP  ${unit.currentHp} / ${unit.maxHp}", textX, textY)
        batch.end()

        // HPバー描画（テキストの下に十分な間隔を空ける）
        val barX = textX
        val barWidth = panelWidth - 32f
        val barHeight = 12f
        textY -= (lineHeight + 8f)  // テキスト分の行送り + 追加余白
        val hpBarY = textY
        val hpRatio = unit.currentHp.toFloat() / unit.maxHp.toFloat()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(barX, hpBarY, barWidth, barHeight)
        val hpColor = when {
            hpRatio > 0.5f -> Color(0.2f, 0.9f, 0.2f, 1f)
            hpRatio > 0.25f -> Color(0.9f, 0.9f, 0.1f, 1f)
            else -> Color(0.9f, 0.2f, 0.2f, 1f)
        }
        shapeRenderer.color = hpColor
        shapeRenderer.rect(barX, hpBarY, barWidth * hpRatio, barHeight)
        shapeRenderer.end()

        // CTテキスト
        textY -= (barHeight + 16f)  // バー高さ + 余白
        batch.begin()
        font.color = Color.WHITE
        font.draw(batch, "CT  ${unit.ct} / ${GameConfig.CT_THRESHOLD}", textX, textY)
        batch.end()

        // CTバー描画（テキストの下に十分な間隔を空ける）
        textY -= (lineHeight + 8f)  // テキスト分の行送り + 追加余白
        val ctBarY = textY

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(barX, ctBarY, barWidth, barHeight)
        val ctRatio = (unit.ct.toFloat() / GameConfig.CT_THRESHOLD).coerceIn(0f, 1f)
        when {
            ctRatio >= 0.8f -> shapeRenderer.setColor(1f, 0.9f, 0.2f, 1f)
            ctRatio >= 0.5f -> shapeRenderer.setColor(0.3f, 0.8f, 1f, 1f)
            else -> shapeRenderer.setColor(0.4f, 0.4f, 0.4f, 1f)
        }
        shapeRenderer.rect(barX, ctBarY, barWidth * ctRatio, barHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ステータス値
        textY -= (barHeight + 16f)  // バー高さ + 余白
        batch.begin()
        font.color = Color.WHITE

        val stats = unit.stats
        val weapon = unit.equippedWeapon()

        // 2列表示
        val col1X = textX
        val col2X = textX + 180f

        font.draw(batch, "STR  ${stats.str}", col1X, textY)
        font.draw(batch, "MAG  ${stats.mag}", col2X, textY)
        textY -= lineHeight

        font.draw(batch, "SKL  ${stats.skl}", col1X, textY)
        font.draw(batch, "SPD  ${stats.spd}(${unit.effectiveSpeed()})", col2X, textY)
        textY -= lineHeight

        font.draw(batch, "LCK  ${stats.lck}", col1X, textY)
        font.draw(batch, "DEF  ${stats.def}", col2X, textY)
        textY -= lineHeight

        font.draw(batch, "RES  ${stats.res}", col1X, textY)
        font.draw(batch, "MOV  ${unit.mov}", col2X, textY)
        textY -= lineHeight + 8f

        // 装備武器情報
        if (weapon != null) {
            font.color = Color.GOLD
            font.draw(batch, weapon.name, col1X, textY)
            textY -= lineHeight
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "Mt ${weapon.might}  Hit ${weapon.hit}  Wt ${weapon.weight}", col1X, textY)
        } else {
            font.color = Color(0.8f, 0.7f, 0.5f, 1f)
            font.draw(batch, "素手 (射程1)", col1X, textY)
        }

        // 装備防具情報
        val armor1 = unit.armorSlot1
        val armor2 = unit.armorSlot2
        if (armor1 != null || armor2 != null) {
            textY -= lineHeight
            font.color = Color(0.5f, 0.7f, 1f, 1f)
            val armorNames = listOfNotNull(armor1?.name, armor2?.name).joinToString(", ")
            font.draw(batch, armorNames, col1X, textY)
            textY -= lineHeight
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "DEF+${unit.totalArmorDef()}  RES+${unit.totalArmorRes()}", col1X, textY)
        }

        batch.end()
    }

    /**
     * タップしたユニットの調査パネルを画面左下に描画する
     *
     * アクティブユニットのステータスパネル（右上）とは別の位置に表示し、
     * プレイヤーが確認したいユニットの情報を閲覧できるようにする。
     *
     * @param unit 調査対象のユニット
     */
    private fun renderInspectionPanel(unit: GameUnit) {
        val panelWidth = 380f
        val panelHeight = 480f
        val viewLeft = camera.position.x - viewport.worldWidth / 2f
        val viewBottom = camera.position.y - viewport.worldHeight / 2f
        val panelX = viewLeft + 16f
        val panelY = viewBottom + 16f

        // 半透明背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.8f)
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight)
        shapeRenderer.end()

        // 枠線（陣営カラー）
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        val borderColor = when (unit.faction) {
            Faction.PLAYER -> Color(0.3f, 0.5f, 1f, 1f)
            Faction.ENEMY -> Color(1f, 0.3f, 0.3f, 1f)
            Faction.ALLY -> Color(0.3f, 1f, 0.5f, 1f)
        }
        shapeRenderer.color = borderColor
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト描画
        batch.projectionMatrix = camera.combined
        batch.begin()

        var textY = panelY + panelHeight - 20f
        val textX = panelX + 16f
        val lineHeight = 32f

        // ヘッダー: 「調査」ラベル
        font.color = Color.GOLD
        font.draw(batch, "INSPECT", textX, textY)
        textY -= lineHeight

        // ユニット名と兵種
        font.color = borderColor
        font.draw(batch, unit.name, textX, textY)
        textY -= lineHeight

        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Lv.${unit.level}  ${unit.unitClass.name}", textX, textY)
        textY -= lineHeight

        // 経験値テキスト（プレイヤーユニットのみ表示）
        if (unit.faction == Faction.PLAYER) {
            font.color = Color(0.8f, 0.7f, 0.3f, 1f)
            font.draw(batch, "EXP  ${unit.exp} / ${GameConfig.EXP_TO_LEVEL_UP}", textX, textY)
            textY -= lineHeight
        }

        // HPテキスト
        textY -= 8f
        font.color = Color.WHITE
        font.draw(batch, "HP  ${unit.currentHp} / ${unit.maxHp}", textX, textY)
        batch.end()

        // HPバー描画
        val barX = textX
        val barWidth = panelWidth - 32f
        val barHeight = 12f
        textY -= (lineHeight + 4f)
        val hpBarY = textY
        val hpRatio = unit.currentHp.toFloat() / unit.maxHp.toFloat()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(barX, hpBarY, barWidth, barHeight)
        val hpColor = when {
            hpRatio > 0.5f -> Color(0.2f, 0.9f, 0.2f, 1f)
            hpRatio > 0.25f -> Color(0.9f, 0.9f, 0.1f, 1f)
            else -> Color(0.9f, 0.2f, 0.2f, 1f)
        }
        shapeRenderer.color = hpColor
        shapeRenderer.rect(barX, hpBarY, barWidth * hpRatio, barHeight)
        shapeRenderer.end()

        // CTテキスト
        textY -= (barHeight + 12f)
        batch.begin()
        font.color = Color.WHITE
        font.draw(batch, "CT  ${unit.ct} / ${GameConfig.CT_THRESHOLD}", textX, textY)
        batch.end()

        // CTバー描画
        textY -= (lineHeight + 4f)
        val ctBarY = textY

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(barX, ctBarY, barWidth, barHeight)
        val ctRatio = (unit.ct.toFloat() / GameConfig.CT_THRESHOLD).coerceIn(0f, 1f)
        when {
            ctRatio >= 0.8f -> shapeRenderer.setColor(1f, 0.9f, 0.2f, 1f)
            ctRatio >= 0.5f -> shapeRenderer.setColor(0.3f, 0.8f, 1f, 1f)
            else -> shapeRenderer.setColor(0.4f, 0.4f, 0.4f, 1f)
        }
        shapeRenderer.rect(barX, ctBarY, barWidth * ctRatio, barHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ステータス値
        textY -= (barHeight + 12f)
        batch.begin()
        font.color = Color.WHITE

        val stats = unit.stats
        val weapon = unit.equippedWeapon()

        // 2列表示
        val col1X = textX
        val col2X = textX + 180f

        font.draw(batch, "STR  ${stats.str}", col1X, textY)
        font.draw(batch, "MAG  ${stats.mag}", col2X, textY)
        textY -= lineHeight

        font.draw(batch, "SKL  ${stats.skl}", col1X, textY)
        font.draw(batch, "SPD  ${stats.spd}(${unit.effectiveSpeed()})", col2X, textY)
        textY -= lineHeight

        font.draw(batch, "LCK  ${stats.lck}", col1X, textY)
        font.draw(batch, "DEF  ${stats.def}", col2X, textY)
        textY -= lineHeight

        font.draw(batch, "RES  ${stats.res}", col1X, textY)
        font.draw(batch, "MOV  ${unit.mov}", col2X, textY)
        textY -= lineHeight + 4f

        // 装備武器情報
        if (weapon != null) {
            font.color = Color.GOLD
            font.draw(batch, weapon.name, col1X, textY)
            textY -= lineHeight
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "Mt ${weapon.might}  Hit ${weapon.hit}  Wt ${weapon.weight}", col1X, textY)
        } else {
            font.color = Color(0.8f, 0.7f, 0.5f, 1f)
            font.draw(batch, "素手 (射程1)", col1X, textY)
        }

        // 装備防具情報
        val inspArmor1 = unit.armorSlot1
        val inspArmor2 = unit.armorSlot2
        if (inspArmor1 != null || inspArmor2 != null) {
            textY -= lineHeight
            font.color = Color(0.5f, 0.7f, 1f, 1f)
            val armorNames = listOfNotNull(inspArmor1?.name, inspArmor2?.name).joinToString(", ")
            font.draw(batch, armorNames, col1X, textY)
            textY -= lineHeight
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "DEF+${unit.totalArmorDef()}  RES+${unit.totalArmorRes()}", col1X, textY)
        }

        batch.end()
    }

    // ==================== テストデータ・ユーティリティ ==

    /**
     * テスト用マップを生成する
     */
    private fun createTestMap(): BattleMap {
        val width = 15
        val height = 10
        val tiles = Array(height) { y ->
            Array(width) { x ->
                val terrain = when {
                    x == 7 && y in 2..7 -> TerrainType.FOREST
                    x in 4..5 && y == 5 -> TerrainType.MOUNTAIN
                    x == 10 && y == 5 -> TerrainType.FORT
                    x in 12..13 && y in 3..6 -> TerrainType.FOREST
                    else -> TerrainType.PLAIN
                }
                Tile(Position(x, y), terrain)
            }
        }

        val map = BattleMap("test_map", "テストマップ", width, height, tiles)

        // プレイヤーユニット配置
        val playerUnit1 = GameUnit(
            id = "hero_01", name = "アレス",
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            stats = Stats(hp = 20, str = 6, mag = 1, skl = 7, spd = 8, lck = 5, def = 5, res = 2),
            growthRate = GrowthRate(hp = 70, str = 50, mag = 10, skl = 55, spd = 60, lck = 40, def = 35, res = 25),
            isLord = true
        )
        playerUnit1.weapons.add(Weapon("ironSword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3))

        val playerUnit2 = GameUnit(
            id = "hero_02", name = "リーナ",
            unitClass = UnitClass.LANCER, faction = Faction.PLAYER,
            stats = Stats(hp = 18, str = 7, mag = 0, skl = 5, spd = 5, lck = 3, def = 7, res = 1),
            growthRate = GrowthRate(hp = 60, str = 55, mag = 5, skl = 45, spd = 40, lck = 30, def = 50, res = 20)
        )
        playerUnit2.weapons.add(Weapon("ironLance", "鉄の槍", WeaponType.LANCE, might = 7, hit = 80, weight = 5))

        val playerUnit3 = GameUnit(
            id = "hero_03", name = "マリア",
            unitClass = UnitClass.ARCHER, faction = Faction.PLAYER,
            stats = Stats(hp = 16, str = 5, mag = 0, skl = 8, spd = 7, lck = 4, def = 3, res = 3),
            growthRate = GrowthRate(hp = 55, str = 45, mag = 5, skl = 60, spd = 55, lck = 40, def = 25, res = 30)
        )
        playerUnit3.weapons.add(Weapon("ironBow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2))

        map.placeUnit(playerUnit1, Position(2, 2))
        map.placeUnit(playerUnit2, Position(2, 4))
        map.placeUnit(playerUnit3, Position(3, 3))

        // 敵ユニット配置
        val enemy1 = GameUnit(
            id = "enemy_01", name = "山賊A",
            unitClass = UnitClass.AXE_FIGHTER, faction = Faction.ENEMY,
            stats = Stats(hp = 18, str = 6, mag = 0, skl = 3, spd = 4, lck = 1, def = 3, res = 0),
            growthRate = GrowthRate()
        )
        enemy1.weapons.add(Weapon("ironAxe", "鉄の斧", WeaponType.AXE, might = 8, hit = 75, weight = 6))

        val enemy2 = GameUnit(
            id = "enemy_02", name = "山賊B",
            unitClass = UnitClass.AXE_FIGHTER, faction = Faction.ENEMY,
            stats = Stats(hp = 18, str = 5, mag = 0, skl = 2, spd = 3, lck = 0, def = 3, res = 0),
            growthRate = GrowthRate()
        )
        enemy2.weapons.add(Weapon("ironAxe2", "鉄の斧", WeaponType.AXE, might = 8, hit = 75, weight = 6))

        val enemy3 = GameUnit(
            id = "enemy_03", name = "盗賊",
            unitClass = UnitClass.SWORD_FIGHTER, faction = Faction.ENEMY,
            stats = Stats(hp = 16, str = 4, mag = 0, skl = 6, spd = 9, lck = 2, def = 2, res = 1),
            growthRate = GrowthRate()
        )
        enemy3.weapons.add(Weapon("ironSword2", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3))

        map.placeUnit(enemy1, Position(11, 3))
        map.placeUnit(enemy2, Position(12, 6))
        map.placeUnit(enemy3, Position(10, 5))

        return map
    }

    // ==================== 撤退ボタン・確認ダイアログ ====================

    /**
     * 撤退ボタンを画面右下に描画する
     *
     * ボタンのスクリーン座標を保存し、タッチ入力判定に使用する。
     * ワールド座標で描画するが、タッチ判定はスクリーン座標で行う。
     */
    private fun renderRetreatButton() {
        val btnWidth = 160f
        val btnHeight = 56f
        val viewRight = camera.position.x + viewport.worldWidth / 2f
        val viewBottom = camera.position.y - viewport.worldHeight / 2f
        val btnX = viewRight - btnWidth - 20f
        val btnY = viewBottom + 20f

        // スクリーン座標に変換して保存（タッチ判定用）
        val screenBottomRight = viewport.project(Vector3(btnX, btnY, 0f))
        val screenTopLeft = viewport.project(Vector3(btnX + btnWidth, btnY + btnHeight, 0f))
        retreatButtonScreenX = screenBottomRight.x
        retreatButtonScreenY = screenTopLeft.y  // LibGDXのproject後はY上向き→スクリーン座標に変換
        retreatButtonWidth = screenTopLeft.x - screenBottomRight.x
        retreatButtonHeight = screenBottomRight.y - screenTopLeft.y

        // ボタン背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.6f, 0.15f, 0.15f, 0.85f)
        shapeRenderer.rect(btnX, btnY, btnWidth, btnHeight)
        shapeRenderer.end()

        // ボタン枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(1f, 0.4f, 0.4f, 1f)
        shapeRenderer.rect(btnX, btnY, btnWidth, btnHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ボタンテキスト
        batch.projectionMatrix = camera.combined
        batch.begin()
        font.color = Color.WHITE
        glyphLayout.setText(font, "撤退")
        font.draw(
            batch, "撤退",
            btnX + (btnWidth - glyphLayout.width) / 2f,
            btnY + (btnHeight + glyphLayout.height) / 2f
        )
        batch.end()
    }

    /**
     * 撤退確認ダイアログを画面中央に描画する
     *
     * 「はい」「いいえ」のボタンを含む確認ダイアログ。
     * ダイアログ表示中は他のタッチ入力を受け付けない。
     */
    private fun renderRetreatConfirmDialog() {
        val dialogWidth = 400f
        val dialogHeight = 200f
        val centerX = camera.position.x
        val centerY = camera.position.y
        val dialogX = centerX - dialogWidth / 2f
        val dialogY = centerY - dialogHeight / 2f

        // 画面全体を暗くするオーバーレイ
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f)
        shapeRenderer.rect(
            camera.position.x - viewport.worldWidth / 2f,
            camera.position.y - viewport.worldHeight / 2f,
            viewport.worldWidth,
            viewport.worldHeight
        )

        // ダイアログ背景
        shapeRenderer.setColor(0.12f, 0.12f, 0.18f, 0.95f)
        shapeRenderer.rect(dialogX, dialogY, dialogWidth, dialogHeight)
        shapeRenderer.end()

        // ダイアログ枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0.8f, 0.6f, 0.2f, 1f)
        shapeRenderer.rect(dialogX, dialogY, dialogWidth, dialogHeight)
        shapeRenderer.end()

        // 「はい」「いいえ」ボタン
        val btnW = 140f
        val btnH = 48f
        val btnY = dialogY + 24f
        val yesBtnX = centerX - btnW - 20f
        val noBtnX = centerX + 20f

        // ボタン座標を保存（タッチ判定用）
        confirmYesX = yesBtnX
        confirmYesY = btnY
        confirmYesW = btnW
        confirmYesH = btnH
        confirmNoX = noBtnX
        confirmNoY = btnY
        confirmNoW = btnW
        confirmNoH = btnH

        // 「はい」ボタン
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.6f, 0.15f, 0.15f, 0.9f)
        shapeRenderer.rect(yesBtnX, btnY, btnW, btnH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(1f, 0.4f, 0.4f, 1f)
        shapeRenderer.rect(yesBtnX, btnY, btnW, btnH)
        shapeRenderer.end()

        // 「いいえ」ボタン
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.3f, 0.9f)
        shapeRenderer.rect(noBtnX, btnY, btnW, btnH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.setColor(0.5f, 0.5f, 0.7f, 1f)
        shapeRenderer.rect(noBtnX, btnY, btnW, btnH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト描画
        batch.projectionMatrix = camera.combined
        batch.begin()

        // メッセージ
        font.color = Color.WHITE
        glyphLayout.setText(font, "撤退しますか？")
        font.draw(
            batch, "撤退しますか？",
            centerX - glyphLayout.width / 2f,
            dialogY + dialogHeight - 40f
        )

        // 「はい」テキスト
        font.color = Color.WHITE
        glyphLayout.setText(font, "はい")
        font.draw(
            batch, "はい",
            yesBtnX + (btnW - glyphLayout.width) / 2f,
            btnY + (btnH + glyphLayout.height) / 2f
        )

        // 「いいえ」テキスト
        font.color = Color.WHITE
        glyphLayout.setText(font, "いいえ")
        font.draw(
            batch, "いいえ",
            noBtnX + (btnW - glyphLayout.width) / 2f,
            btnY + (btnH + glyphLayout.height) / 2f
        )

        batch.end()
    }

    /**
     * ウィンドウリサイズ処理
     */
    override fun resize(width: Int, height: Int) {
        if (::viewport.isInitialized) {
            viewport.update(width, height)
            centerCameraOnMap()
        }
    }

    /**
     * リソース解放
     */
    override fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        // フォントは FontManager が管理するため、ここでは dispose しない
    }

    companion object {
        private const val TAG = "BattleScreen"

        /** 行動順キューの表示数 */
        private const val ACTION_QUEUE_SIZE = 8
    }
}
