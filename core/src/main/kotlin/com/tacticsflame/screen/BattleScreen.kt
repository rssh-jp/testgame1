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
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.battle.BattleResult
import com.tacticsflame.model.battle.HealResult
import com.tacticsflame.data.MapLoader
import com.tacticsflame.model.campaign.BattleConfig
import com.tacticsflame.model.campaign.BattleResultData
import com.tacticsflame.model.campaign.WaveConfig
import com.tacticsflame.model.campaign.WaveEnemy
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import com.tacticsflame.render.UnitShapeRenderer
import com.tacticsflame.system.*
import com.tacticsflame.ui.UnitStatusPanelRenderer
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
    private val uiViewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)
    private val tmpUiVec = Vector2()

    // ゲームシステム
    private val turnManager = TurnManager()
    private val pathFinder = PathFinder()
    private val battleSystem = BattleSystem()
    private val aiSystem = AISystem(pathFinder, battleSystem)
    private val victoryChecker = VictoryChecker()
    private val levelUpSystem = LevelUpSystem()
    private val mapLoader = MapLoader()

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
        /** 回復結果表示中 */
        HEAL_RESULT,
        /** 行動後ウェイト中 */
        POST_ACTION,
        /** 勝利/敗北 */
        RESULT,
        /** ウェーブクリア演出中 */
        WAVE_CLEAR,
        /** カメラパン + 次ウェーブ準備中 */
        WAVE_TRANSITION,
        /** 新ウェーブ開始演出中 */
        WAVE_START
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

    /** 回復結果の一時保持 */
    private var pendingHealResult: HealResult? = null

    /** 撤退確認ダイアログ表示フラグ */
    private var showRetreatConfirm: Boolean = false

    // ==================== Campaign Mode ====================

    /** Campaign Mode のウェーブ管理 */
    private val waveManager = WaveManager()

    /** Campaign Mode かどうか */
    private var isCampaignMode: Boolean = false

    /** カメラパンアニメーション: 開始位置 */
    private var cameraPanStartX: Float = 0f
    private var cameraPanStartY: Float = 0f

    /** カメラパンアニメーション: 目標位置 */
    private var cameraPanTargetX: Float = 0f
    private var cameraPanTargetY: Float = 0f

    /** ウェーブ間回復結果の一時保持 */
    private var waveHealResults: List<Pair<GameUnit, Int>> = emptyList()

    // 撤退ボタンのUI座標（uiViewport座標系、renderRetreatButton で更新）
    private var retreatButtonUiX: Float = 0f
    private var retreatButtonUiY: Float = 0f
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

    /** ピンチズーム用: 前フレームの2点間距離（px） */
    private var previousPinchDistance: Float = 0f

    /** カメラドラッグ判定用: タッチ追跡中フラグ */
    private var isTouchTracking: Boolean = false

    /** カメラドラッグ判定用: ドラッグ開始座標（スクリーン座標） */
    private var touchStartScreenX: Float = 0f
    private var touchStartScreenY: Float = 0f

    /** カメラドラッグ中フラグ */
    private var isCameraDragging: Boolean = false

    /** カメラパン用: 前フレームのワールド座標 */
    private var lastPanWorldX: Float = 0f
    private var lastPanWorldY: Float = 0f

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
        uiViewport.update(Gdx.graphics.width, Gdx.graphics.height, true)

        // 初期表示をやや拡大
        camera.zoom = DEFAULT_CAMERA_ZOOM
        centerCameraOnMap()

        // CT初期化
        val allUnits = battleMap.getAllUnits().map { it.second }
        turnManager.reset(allUnits)

        // 行動順予測を更新
        updateActionQueue()

        // Campaign Mode の初期化
        val config = battleConfig
        if (config != null && config.isCampaignMode && config.waves.isNotEmpty()) {
            isCampaignMode = true
            waveManager.initialize(config.waves)
            // キャンペーンモードは先にisCampaignModeを設定してからビューポートを再初期化
            initViewport()
            // Wave1のカメラフォーカス位置に移動
            val firstWave = waveManager.currentWave
            if (firstWave != null) {
                val focusX = firstWave.cameraFocusX * GameConfig.TILE_SIZE.toFloat()
                val focusY = firstWave.cameraFocusY * GameConfig.TILE_SIZE.toFloat()
                camera.position.set(focusX, focusY, 0f)
                camera.update()
            }
            Gdx.app.log(TAG, "Campaign Mode 開始: ${config.waves.size} ウェーブ")
        }

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
        updateCameraZoomByPinch()

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
                BattleState.HEAL_RESULT -> processHealResult(delta)
                BattleState.POST_ACTION -> processPostAction(delta)
                BattleState.RESULT -> handleResultInput()
                BattleState.WAVE_CLEAR -> processWaveClear(delta)
                BattleState.WAVE_TRANSITION -> processWaveTransition(delta)
                BattleState.WAVE_START -> processWaveStart(delta)
            }
        }

        // 画面クリア
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // マップ描画（ズーム・パン対象）
        viewport.apply()
        shapeRenderer.projectionMatrix = camera.combined
        batch.projectionMatrix = camera.combined

        // マップ・ユニット描画（ワールド座標）
        renderMap()
        renderUnits()
        renderUnitNames()

        // UI描画（ズーム非依存）
        uiViewport.apply()
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        batch.projectionMatrix = uiViewport.camera.combined

        // 上部UIエリア背景（戦闘エリアを下半分に寄せて見せる）
        renderTopUiBackground()

        // アクティブユニットのステータスパネル（右上）
        val activeUnit = turnManager.activeUnit
        if (activeUnit != null) {
            renderStatusPanel(activeUnit)
        }

        // タップしたユニットの調査パネル（左上）
        val inspected = inspectedUnit
        if (inspected != null && !inspected.isDefeated) {
            renderInspectionPanel(inspected)
        }

        // ターン情報描画
        renderTurnInfo()

        // Campaign Mode: ウェーブ進捗UI
        if (isCampaignMode) {
            renderWaveProgress()
        }

        // ウェーブクリア/開始演出のオーバーレイ
        when (battleState) {
            BattleState.WAVE_CLEAR -> renderWaveClearOverlay()
            BattleState.WAVE_START -> renderWaveStartOverlay()
            else -> {}
        }

        // 回復結果表示（HEAL_RESULT状態のとき）
        if (battleState == BattleState.HEAL_RESULT) {
            renderHealResultOverlay()
        }

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
        // ウェーブ演出中は入力を受け付けない
        if (battleState in listOf(BattleState.WAVE_CLEAR, BattleState.WAVE_TRANSITION, BattleState.WAVE_START)) return

        // 撤退確認ダイアログが表示中の場合、ダイアログのボタンのみ受け付ける
        if (showRetreatConfirm) {
            if (!Gdx.input.justTouched()) return
            val uiCoords = uiViewport.unproject(tmpUiVec.set(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
            handleRetreatConfirmInput(uiCoords.x, uiCoords.y)
            return
        }

        val isFirstTouched = Gdx.input.isTouched(0)
        val isSecondTouched = Gdx.input.isTouched(1)

        if (isFirstTouched) {
            val screenX = Gdx.input.getX(0).toFloat()
            val screenY = Gdx.input.getY(0).toFloat()

            if (!isTouchTracking) {
                isTouchTracking = true
                isCameraDragging = false
                touchStartScreenX = screenX
                touchStartScreenY = screenY
                val world = viewport.unproject(Vector3(screenX, screenY, 0f))
                lastPanWorldX = world.x
                lastPanWorldY = world.y
                return
            }

            if (isSecondTouched) {
                isCameraDragging = true
                return
            }

            val dragDx = screenX - touchStartScreenX
            val dragDy = screenY - touchStartScreenY
            val dragDistanceSq = dragDx * dragDx + dragDy * dragDy
            if (!isCameraDragging && dragDistanceSq >= CAMERA_PAN_START_DISTANCE_PX * CAMERA_PAN_START_DISTANCE_PX) {
                isCameraDragging = true
            }

            if (isCameraDragging) {
                val world = viewport.unproject(Vector3(screenX, screenY, 0f))
                camera.position.add(lastPanWorldX - world.x, lastPanWorldY - world.y, 0f)
                clampCameraToMap()
                camera.update()
                // カメラ移動後の座標系で再unprojectし、次フレーム用の基準座標を正しく取得
                val updatedWorld = viewport.unproject(Vector3(screenX, screenY, 0f))
                lastPanWorldX = updatedWorld.x
                lastPanWorldY = updatedWorld.y
            }
            return
        }

        // タッチ終了時に、ドラッグしていなければタップ処理を実行
        if (!isTouchTracking) return
        if (!isCameraDragging) {
            handleTap(touchStartScreenX, touchStartScreenY)
        }
        isTouchTracking = false
        isCameraDragging = false
    }

    /**
     * タップ入力（ドラッグではないタッチ）を処理する
     */
    private fun handleTap(screenX: Float, screenY: Float) {
        val uiCoords = uiViewport.unproject(tmpUiVec.set(screenX, screenY))

        // 撤退ボタンのタッチ判定（UI座標で判定）
        if (isRetreatButtonTouched(uiCoords.x, uiCoords.y)) {
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
    private fun isRetreatButtonTouched(uiX: Float, uiY: Float): Boolean {
        return uiX >= retreatButtonUiX &&
            uiX <= retreatButtonUiX + retreatButtonWidth &&
            uiY >= retreatButtonUiY &&
            uiY <= retreatButtonUiY + retreatButtonHeight
    }

    /**
     * 撤退確認ダイアログのタッチ入力を処理する
     *
     * @param uiX タッチX座標（UI座標）
     * @param uiY タッチY座標（UI座標）
     */
    private fun handleRetreatConfirmInput(uiX: Float, uiY: Float) {
        // 「はい」ボタン判定
        if (uiX >= confirmYesX && uiX <= confirmYesX + confirmYesW &&
            uiY >= confirmYesY && uiY <= confirmYesY + confirmYesH) {
            Gdx.app.log(TAG, "撤退を実行")
            showRetreatConfirm = false
            executeRetreat()
            return
        }

        // 「いいえ」ボタン判定
        if (uiX >= confirmNoX && uiX <= confirmNoX + confirmNoW &&
            uiY >= confirmNoY && uiY <= confirmNoY + confirmNoH) {
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

        // AIパターン決定
        val pattern = when (activeUnit.faction) {
            Faction.PLAYER, Faction.ALLY -> when (activeUnit.tactic) {
                UnitTactic.CHARGE -> AISystem.AIPattern.AGGRESSIVE
                UnitTactic.CAUTIOUS -> AISystem.AIPattern.CAUTIOUS
                UnitTactic.SUPPORT -> AISystem.AIPattern.SUPPORT
                UnitTactic.HEAL -> AISystem.AIPattern.HEAL
                UnitTactic.FLEE -> AISystem.AIPattern.FLEE
            }
            Faction.ENEMY -> {
                // 敵ヒーラー（回復杖装備）は自動で回復AI
                if (activeUnit.equippedHealingStaff() != null) {
                    AISystem.AIPattern.HEAL
                } else {
                    AISystem.AIPattern.AGGRESSIVE
                }
            }
        }

        Gdx.app.log(
            TAG,
            "AI思考: ${activeUnit.name} faction=${activeUnit.faction} tactic=${activeUnit.tactic} pattern=$pattern"
        )

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
            is AISystem.Action.MoveAndHeal -> {
                val unitPos = battleMap.getUnitPosition(activeUnit)!!
                if (act.moveTo != unitPos) {
                    startMovementAnimation(activeUnit, unitPos, act.moveTo)
                } else {
                    // 移動なしで回復
                    startHealing(activeUnit, act.target)
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
            // 経路が見つからない場合は移動をキャンセルして待機（テレポート防止）
            Gdx.app.log(TAG, "警告: 経路が見つかりません: ${unit.name} ($from → $to) — 移動をキャンセルし待機します")
            val action = pendingAction?.action
            when (action) {
                is AISystem.Action.MoveAndAttack -> {
                    Gdx.app.log(TAG, "攻撃もキャンセル: 移動先に到達できないため")
                    enterPostAction()
                }
                is AISystem.Action.MoveAndHeal -> {
                    Gdx.app.log(TAG, "回復もキャンセル: 移動先に到達できないため")
                    enterPostAction()
                }
                else -> enterPostAction()
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

        // 攻撃がある場合は戦闘へ、回復がある場合は回復へ
        val action = pendingAction?.action
        when (action) {
            is AISystem.Action.MoveAndAttack -> startCombat(activeUnit, action.target)
            is AISystem.Action.MoveAndHeal -> startHealing(activeUnit, action.target)
            else -> enterPostAction()
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
     * 回復行動を開始する
     *
     * @param healer 回復ユニット
     * @param target 回復対象ユニット
     */
    private fun startHealing(healer: GameUnit, target: GameUnit) {
        val result = battleSystem.executeHeal(healer, target)
        pendingHealResult = result
        Gdx.app.log(
            TAG,
            "AI回復: ${healer.name} → ${target.name} " +
                "(回復量: ${result.healAmount}, HP: ${result.targetHpBefore}→${result.targetHpAfter})"
        )
        stateTimer = 0f
        battleState = BattleState.HEAL_RESULT
    }

    /**
     * 回復結果表示を処理する
     *
     * 表示時間経過後、経験値を付与して行動後ウェイトに遷移する。
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processHealResult(delta: Float) {
        stateTimer += delta
        if (stateTimer < GameConfig.COMBAT_RESULT_DELAY) return

        val result = pendingHealResult
        if (result != null) {
            // 経験値付与: 回復ユニットがプレイヤー陣営の場合
            if (result.healer.faction == Faction.PLAYER) {
                awardExpToUnit(result.healer, result.expGained)
            }
        }

        pendingHealResult = null
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
        pendingHealResult = null
        animatingUnit = null
        animationPath = emptyList()
        animationProgress = 0f
        stateTimer = 0f

        // TurnManagerの行動完了処理
        val allUnits = battleMap.getAllUnits().map { it.second }
        turnManager.completeAction(unit, allUnits)

        if (isCampaignMode) {
            // Campaign Mode: ウェーブ単位の判定
            checkCampaignOutcome()
        } else {
            // 通常モード: 従来通り
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
                survivingUnits = survivingPlayers,
                isCampaignMode = isCampaignMode,
                wavesCleared = if (isCampaignMode) waveManager.currentWaveIndex + (if (isVictory && waveManager.isLastWave) 1 else 0) else 0,
                totalWaves = if (isCampaignMode) waveManager.totalWaves else 0
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

    // ==================== Campaign Mode ====================

    /**
     * Campaign Mode の勝敗・ウェーブクリア判定
     */
    private fun checkCampaignOutcome() {
        val allUnits = battleMap.getAllUnits()

        // 敗北判定（最優先）
        val playerUnits = allUnits.filter { it.second.faction == Faction.PLAYER }
        val lordDefeated = playerUnits.any { it.second.isLord && it.second.isDefeated }
        val allPlayersEliminated = playerUnits.isEmpty() || playerUnits.all { it.second.isDefeated }
        if (lordDefeated || allPlayersEliminated) {
            Gdx.app.log(TAG, "Campaign 敗北 - Wave ${waveManager.currentWaveIndex + 1}")
            isVictory = false
            battleState = BattleState.RESULT
            return
        }

        // ウェーブクリア判定
        if (waveManager.isCurrentWaveCleared(battleMap)) {
            if (waveManager.isLastWave) {
                Gdx.app.log(TAG, "Campaign Complete!")
                isVictory = true
                battleState = BattleState.RESULT
            } else {
                Gdx.app.log(TAG, "Wave ${waveManager.currentWaveIndex + 1} クリア!")
                stateTimer = 0f
                battleState = BattleState.WAVE_CLEAR
            }
            return
        }

        // 継続
        updateActionQueue()
        battleState = BattleState.CT_ADVANCING
    }

    /**
     * ウェーブクリア演出を処理する
     *
     * クリアテキスト表示 → 回復実行 → カメラパンに遷移
     */
    private fun processWaveClear(delta: Float) {
        stateTimer += delta

        // スキップ判定（タップでスキップ）
        if (stateTimer > 0.3f && Gdx.input.justTouched()) {
            startWaveTransition()
            return
        }

        if (stateTimer >= GameConfig.WAVE_CLEAR_DURATION) {
            startWaveTransition()
        }
    }

    /**
     * ウェーブ遷移（カメラパン + 回復 + 敵配置）を開始する
     */
    private fun startWaveTransition() {
        // ウェーブ間回復を実行
        val healPercent = waveManager.currentWave?.healPercent ?: GameConfig.WAVE_DEFAULT_HEAL_PERCENT
        val playerUnits = battleMap.getAllUnits()
            .filter { it.second.faction == Faction.PLAYER }
            .map { it.second }
        waveHealResults = waveManager.healBetweenWaves(playerUnits, healPercent)

        if (waveHealResults.isNotEmpty()) {
            Gdx.app.log(TAG, "ウェーブ間回復: ${waveHealResults.size}体")
        }

        // 次のウェーブに進む
        val nextWave = waveManager.advanceToNextWave() ?: return

        // 次ウェーブの敵をマップに配置
        spawnWaveEnemies(nextWave)

        // カメラパンの開始・目標位置を設定
        cameraPanStartX = camera.position.x
        cameraPanStartY = camera.position.y
        cameraPanTargetX = nextWave.cameraFocusX * GameConfig.TILE_SIZE
        cameraPanTargetY = nextWave.cameraFocusY * GameConfig.TILE_SIZE

        stateTimer = 0f
        battleState = BattleState.WAVE_TRANSITION
    }

    /**
     * ウェーブの敵をマップ上に配置する
     *
     * MapLoader の公開メソッドを使用して WaveEnemy から GameUnit を構築し、
     * マップ上に配置する。配置位置が既に埋まっている場合は隣接する空きマスを探す。
     *
     * @param wave 配置対象のウェーブ設定
     */
    private fun spawnWaveEnemies(wave: WaveConfig) {
        val levelBonus = game.gameProgress.cycle * 10

        for (waveEnemy in wave.enemies) {
            val unit = mapLoader.createUnitFromWaveEnemy(waveEnemy, levelBonus) ?: continue
            val pos = Position(waveEnemy.x, waveEnemy.y)

            // 既存ユニットと重複しないか確認
            if (battleMap.getUnitAt(pos) != null) {
                val altPos = findEmptyAdjacentPosition(pos)
                if (altPos != null) {
                    battleMap.placeUnit(unit, altPos)
                }
            } else {
                battleMap.placeUnit(unit, pos)
            }
        }

        // 初期敵数を更新（リザルト表示用）
        initialEnemyCount += wave.enemies.size

        Gdx.app.log(TAG, "Wave ${wave.waveId} の敵を配置: ${wave.enemies.size}体")
    }

    /**
     * 隣接する空きマスを探す
     *
     * @param pos 基準座標
     * @return 配置可能な隣接座標。見つからなければ null
     */
    private fun findEmptyAdjacentPosition(pos: Position): Position? {
        for (neighbor in pos.neighbors()) {
            if (battleMap.isInBounds(neighbor.x, neighbor.y) && battleMap.getUnitAt(neighbor) == null) {
                val tile = battleMap.getTile(neighbor)
                if (tile != null && tile.terrainType.moveCost > 0) {
                    return neighbor
                }
            }
        }
        return null
    }

    /**
     * カメラパンを処理する（EaseInOutCubic補間）
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processWaveTransition(delta: Float) {
        stateTimer += delta
        val duration = GameConfig.WAVE_CAMERA_PAN_DURATION
        val t = (stateTimer / duration).coerceIn(0f, 1f)

        // EaseInOutCubic 補間
        val eased = if (t < 0.5f) {
            4f * t * t * t
        } else {
            1f - (-2f * t + 2f).let { it * it * it } / 2f
        }

        camera.position.x = cameraPanStartX + (cameraPanTargetX - cameraPanStartX) * eased
        camera.position.y = cameraPanStartY + (cameraPanTargetY - cameraPanStartY) * eased
        camera.update()

        if (t >= 1f) {
            stateTimer = 0f
            battleState = BattleState.WAVE_START
        }
    }

    /**
     * ウェーブ開始演出を処理する
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processWaveStart(delta: Float) {
        stateTimer += delta

        // スキップ判定
        if (stateTimer > 0.3f && Gdx.input.justTouched()) {
            startNextWaveBattle()
            return
        }

        if (stateTimer >= GameConfig.WAVE_START_DURATION) {
            startNextWaveBattle()
        }
    }

    /**
     * 次のウェーブのバトルを開始する
     */
    private fun startNextWaveBattle() {
        waveHealResults = emptyList()
        updateActionQueue()
        stateTimer = 0f
        battleState = BattleState.CT_ADVANCING
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
        val padding = GameConfig.TILE_SIZE * GameConfig.BATTLE_MAP_PADDING_TILES
        if (isCampaignMode) {
            // キャンペーンモード: 15×15タイルが収まるサイズ
            val visibleTiles = 15f
            val viewSize = visibleTiles * GameConfig.TILE_SIZE
            viewport = ExtendViewport(
                viewSize + padding * 2,
                viewSize + padding * 2,
                camera
            )
        } else {
            val mapPixelW = battleMap.width * GameConfig.TILE_SIZE
            val mapPixelH = battleMap.height * GameConfig.TILE_SIZE
            viewport = ExtendViewport(
                mapPixelW + padding * 2,
                mapPixelH + padding * 2,
                camera
            )
        }
        // 現在のウィンドウサイズで更新
        viewport.update(Gdx.graphics.width, Gdx.graphics.height)
        centerCameraOnMap()
        clampCameraToMap()
    }

    /**
     * カメラをマップ中央に配置する
     */
    private fun centerCameraOnMap() {
        val mapCenterX = battleMap.width * GameConfig.TILE_SIZE / 2f
        val mapCenterY = battleMap.height * GameConfig.TILE_SIZE / 2f
        if (isCampaignMode) {
            // キャンペーンモードではシフトなし
            camera.position.set(mapCenterX, mapCenterY, 0f)
        } else {
            val loweredY = mapCenterY + viewHalfHeight() * BATTLE_AREA_SHIFT_RATIO
            camera.position.set(mapCenterX, loweredY, 0f)
        }
        camera.update()
    }

    /**
     * カメラ位置をマップ範囲内に制限する
     *
     * キャンペーンモードでは BATTLE_AREA_SHIFT_RATIO を適用せず、
     * マップ全タイルが確実に画面内に表示できるようにする。
     * ビューポートがマップより大きい場合はマップ中央に固定する。
     */
    private fun clampCameraToMap() {
        val mapWidth = battleMap.width * GameConfig.TILE_SIZE
        val mapHeight = battleMap.height * GameConfig.TILE_SIZE

        val halfW = viewHalfWidth()
        val halfH = viewHalfHeight()

        if (isCampaignMode) {
            // キャンペーンモード: シフトなし、マップ端が画面端に来る位置まで移動可能
            val minX = halfW
            val maxX = mapWidth - halfW
            val minY = halfH
            val maxY = mapHeight - halfH

            camera.position.x = if (minX <= maxX) {
                camera.position.x.coerceIn(minX, maxX)
            } else {
                mapWidth / 2f
            }

            camera.position.y = if (minY <= maxY) {
                camera.position.y.coerceIn(minY, maxY)
            } else {
                // ビューポートがマップ高さを超える場合はマップ中央（シフトなし）
                mapHeight / 2f
            }
        } else {
            val mapCenterY = mapHeight / 2f
            val loweredY = mapCenterY + halfH * BATTLE_AREA_SHIFT_RATIO

            val minX = halfW
            val maxX = mapWidth - halfW
            val minY = halfH
            val maxY = mapHeight - halfH

            camera.position.x = if (minX <= maxX) {
                camera.position.x.coerceIn(minX, maxX)
            } else {
                mapWidth / 2f
            }

            camera.position.y = if (minY <= maxY) {
                camera.position.y.coerceIn(minY, maxY)
            } else {
                loweredY
            }
        }
    }

    /**
     * 上部UIエリアの背景を描画する
     */
    private fun renderTopUiBackground() {
        val uiWidth = uiViewport.worldWidth
        val uiHeight = uiViewport.worldHeight * 0.5f * TOP_UI_RATIO
        val uiTop = uiViewport.worldHeight

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.40f)
        shapeRenderer.rect(0f, uiTop - uiHeight, uiWidth, uiHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    // ==================== 描画メソッド ====================

    /**
     * マップを描画する（プロトタイプ：ShapeRendererで色分け）
     */
    private fun renderMap() {
        val tileSize = GameConfig.TILE_SIZE

        // カメラの可視範囲をタイル座標に変換（カリング）
        val halfW = viewHalfWidth()
        val halfH = viewHalfHeight()
        val minTileX = MathUtils.floor((camera.position.x - halfW) / tileSize).coerceAtLeast(0)
        val maxTileX = MathUtils.ceil((camera.position.x + halfW) / tileSize).coerceAtMost(battleMap.width - 1)
        val minTileY = MathUtils.floor((camera.position.y - halfH) / tileSize).coerceAtLeast(0)
        val maxTileY = MathUtils.ceil((camera.position.y + halfH) / tileSize).coerceAtMost(battleMap.height - 1)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (y in minTileY..maxTileY) {
            for (x in minTileX..maxTileX) {
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
     * ターン情報を画面上部中央に描画する
     */
    private fun renderTurnInfo() {
        batch.projectionMatrix = uiViewport.camera.combined
        batch.begin()

        val activeUnit = turnManager.activeUnit
        val roundText = "Round ${turnManager.roundNumber}"

        val viewCenterX = uiViewport.worldWidth / 2f
        val viewTop = uiViewport.worldHeight

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
     * 回復結果オーバーレイを描画する
     *
     * 回復対象ユニットの上に緑色の回復量テキストを表示する。
     */
    private fun renderHealResultOverlay() {
        val result = pendingHealResult ?: return
        val targetPos = battleMap.getUnitPosition(result.target) ?: return

        val tileSize = GameConfig.TILE_SIZE
        val cx = targetPos.x * tileSize + tileSize / 2f
        val cy = targetPos.y * tileSize + tileSize / 2f

        // 回復量テキストの背景
        val healText = "+${result.healAmount} HP"
        glyphLayout.setText(font, healText)
        val textWidth = glyphLayout.width
        val textHeight = glyphLayout.height
        val bgPadding = 6f
        val bgX = cx - textWidth / 2f - bgPadding
        val bgY = cy + tileSize * 0.7f - bgPadding
        val bgW = textWidth + bgPadding * 2
        val bgH = textHeight + bgPadding * 2

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0.3f, 0f, 0.8f)
        shapeRenderer.rect(bgX, bgY, bgW, bgH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 回復量テキスト描画
        batch.projectionMatrix = uiViewport.camera.combined
        batch.begin()
        font.color = Color(0.3f, 1f, 0.3f, 1f)
        font.draw(batch, healText, cx - textWidth / 2f, bgY + bgH - bgPadding)
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
        val areaLeft = 0f
        val areaTop = uiViewport.worldHeight
        val areaWidth = uiViewport.worldWidth
        val areaHeight = uiViewport.worldHeight * 0.5f * TOP_UI_RATIO

        Gdx.gl.glEnable(GL20.GL_BLEND)
        UnitStatusPanelRenderer.render(
            shapeRenderer = shapeRenderer,
            batch = batch,
            unit = unit,
            areaLeft = areaLeft,
            areaTop = areaTop,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            slot = UnitStatusPanelRenderer.Slot.RIGHT,
            title = null
        )
        Gdx.gl.glDisable(GL20.GL_BLEND)
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
        val areaLeft = 0f
        val areaTop = uiViewport.worldHeight
        val areaWidth = uiViewport.worldWidth
        val areaHeight = uiViewport.worldHeight * 0.5f * TOP_UI_RATIO

        Gdx.gl.glEnable(GL20.GL_BLEND)
        UnitStatusPanelRenderer.render(
            shapeRenderer = shapeRenderer,
            batch = batch,
            unit = unit,
            areaLeft = areaLeft,
            areaTop = areaTop,
            areaWidth = areaWidth,
            areaHeight = areaHeight,
            slot = UnitStatusPanelRenderer.Slot.LEFT,
            title = "INSPECT"
        )
        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    // ==================== Campaign Mode 描画 ====================

    /**
     * ウェーブ進捗UIを描画する（画面上部中央、ターン情報の下）
     */
    private fun renderWaveProgress() {
        batch.projectionMatrix = uiViewport.camera.combined
        batch.begin()

        val waveText = "Wave ${waveManager.currentWaveIndex + 1} / ${waveManager.totalWaves}"
        font.color = Color(1f, 0.85f, 0.2f, 1f)
        val viewCenterX = uiViewport.worldWidth / 2f
        val viewTop = uiViewport.worldHeight
        glyphLayout.setText(font, waveText)
        font.draw(batch, waveText, viewCenterX - glyphLayout.width / 2f, viewTop - 88f)

        batch.end()
    }

    /**
     * ウェーブクリア演出のオーバーレイを描画する
     */
    private fun renderWaveClearOverlay() {
        // 半透明黒オーバーレイ
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f)
        shapeRenderer.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = uiViewport.camera.combined
        batch.begin()
        val clearText = "Wave ${waveManager.currentWaveIndex + 1} Complete!"
        font.color = Color(1f, 0.9f, 0.2f, 1f)
        glyphLayout.setText(font, clearText)
        font.draw(
            batch, clearText,
            uiViewport.worldWidth / 2f - glyphLayout.width / 2f,
            uiViewport.worldHeight / 2f + glyphLayout.height / 2f
        )
        batch.end()
    }

    /**
     * ウェーブ開始演出のオーバーレイを描画する
     */
    private fun renderWaveStartOverlay() {
        val wave = waveManager.currentWave ?: return

        // 半透明黒オーバーレイ
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f)
        shapeRenderer.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = uiViewport.camera.combined
        batch.begin()
        font.color = Color(1f, 0.85f, 0.2f, 1f)
        glyphLayout.setText(font, wave.name)
        font.draw(
            batch, wave.name,
            uiViewport.worldWidth / 2f - glyphLayout.width / 2f,
            uiViewport.worldHeight / 2f + glyphLayout.height / 2f
        )
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
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate(hp = 0.70f, str = 0.50f, mag = 0.10f, skl = 0.55f, spd = 0.20f, lck = 0.40f, def = 0.35f, res = 0.25f),
            isLord = true
        )
        playerUnit1.weapons.add(Weapon("ironSword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3))

        val playerUnit2 = GameUnit(
            id = "hero_02", name = "リーナ",
            unitClass = UnitClass.LANCER, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate(hp = 0.60f, str = 0.55f, mag = 0.05f, skl = 0.45f, spd = 0.20f, lck = 0.30f, def = 0.50f, res = 0.20f)
        )
        playerUnit2.weapons.add(Weapon("ironLance", "鉄の槍", WeaponType.LANCE, might = 7, hit = 80, weight = 5))

        val playerUnit3 = GameUnit(
            id = "hero_03", name = "マリア",
            unitClass = UnitClass.ARCHER, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate(hp = 0.55f, str = 0.45f, mag = 0.05f, skl = 0.60f, spd = 0.20f, lck = 0.40f, def = 0.25f, res = 0.30f)
        )
        playerUnit3.weapons.add(Weapon("ironBow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2))

        map.placeUnit(playerUnit1, Position(2, 2))
        map.placeUnit(playerUnit2, Position(2, 4))
        map.placeUnit(playerUnit3, Position(3, 3))

        // 敵ユニット配置
        val enemy1 = GameUnit(
            id = "enemy_01", name = "山賊A",
            unitClass = UnitClass.AXE_FIGHTER, faction = Faction.ENEMY,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
        enemy1.weapons.add(Weapon("ironAxe", "鉄の斧", WeaponType.AXE, might = 8, hit = 75, weight = 6))

        val enemy2 = GameUnit(
            id = "enemy_02", name = "山賊B",
            unitClass = UnitClass.AXE_FIGHTER, faction = Faction.ENEMY,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
        enemy2.weapons.add(Weapon("ironAxe2", "鉄の斧", WeaponType.AXE, might = 8, hit = 75, weight = 6))

        val enemy3 = GameUnit(
            id = "enemy_03", name = "盗賊",
            unitClass = UnitClass.SWORD_FIGHTER, faction = Faction.ENEMY,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
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
        val viewRight = uiViewport.worldWidth
        val viewBottom = 0f
        val btnX = viewRight - btnWidth - 20f
        val btnY = viewBottom + 20f

        // UI座標で保存（タッチ判定用）
        retreatButtonUiX = btnX
        retreatButtonUiY = btnY
        retreatButtonWidth = btnWidth
        retreatButtonHeight = btnHeight

        // ボタン背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
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
        batch.projectionMatrix = uiViewport.camera.combined
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
        val centerX = uiViewport.worldWidth / 2f
        val centerY = uiViewport.worldHeight / 2f
        val dialogX = centerX - dialogWidth / 2f
        val dialogY = centerY - dialogHeight / 2f

        // 画面全体を暗くするオーバーレイ
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.5f)
        shapeRenderer.rect(0f, 0f, uiViewport.worldWidth, uiViewport.worldHeight)

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
        batch.projectionMatrix = uiViewport.camera.combined
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
            uiViewport.update(width, height, true)
            clampCameraToMap()
            camera.update()
        }
    }

    /**
     * 2本指ピンチ操作でカメラズームを更新する
     */
    private fun updateCameraZoomByPinch() {
        val isFirstTouched = Gdx.input.isTouched(0)
        val isSecondTouched = Gdx.input.isTouched(1)
        if (!isFirstTouched || !isSecondTouched) {
            previousPinchDistance = 0f
            return
        }

        val dx = (Gdx.input.getX(0) - Gdx.input.getX(1)).toFloat()
        val dy = (Gdx.input.getY(0) - Gdx.input.getY(1)).toFloat()
        val currentDistance = kotlin.math.sqrt(dx * dx + dy * dy)

        if (previousPinchDistance > 0f && currentDistance > 0f) {
            val zoomFactor = previousPinchDistance / currentDistance
            camera.zoom = (camera.zoom * zoomFactor).coerceIn(MIN_CAMERA_ZOOM, MAX_CAMERA_ZOOM)
            clampCameraToMap()
            camera.update()
        }
        previousPinchDistance = currentDistance
    }

    /** 表示中ワールドの半幅（zoom反映） */
    private fun viewHalfWidth(): Float = viewport.worldWidth * camera.zoom / 2f

    /** 表示中ワールドの半高さ（zoom反映） */
    private fun viewHalfHeight(): Float = viewport.worldHeight * camera.zoom / 2f

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

        /** 上部UIの高さ割合（画面上半分想定） */
        private const val TOP_UI_RATIO = 1.0f

        /** 戦闘エリアを下半分に寄せる割合 */
        private const val BATTLE_AREA_SHIFT_RATIO = 0.50f

        /** カメラ初期ズーム（1.0より小さいと拡大表示） */
        private const val DEFAULT_CAMERA_ZOOM = 0.85f

        /** カメラ最小ズーム（これ以上は拡大しない） */
        private const val MIN_CAMERA_ZOOM = 0.6f

        /** カメラ最大ズーム（これ以上は縮小しない。Campaign Mode の大マップ対応で拡張） */
        private const val MAX_CAMERA_ZOOM = 3.0f

        /** カメラパン開始とみなす最小移動量（px） */
        private const val CAMERA_PAN_START_DISTANCE_PX = 16f
    }
}
