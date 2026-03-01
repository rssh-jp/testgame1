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
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.battle.BattleResult
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
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

    // マップとユニット
    private lateinit var battleMap: BattleMap

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

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        font = FontManager.getFont(size = 28)

        // テスト用マップ生成
        battleMap = createTestMap()

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
     * フレーム描画処理
     */
    override fun render(delta: Float) {
        // 状態に応じたロジック更新（描画前に実行）
        when (battleState) {
            BattleState.CT_ADVANCING -> advanceCT()
            BattleState.AI_THINKING -> processAIThinking(delta)
            BattleState.UNIT_MOVING -> processUnitMoving(delta)
            BattleState.COMBAT_RESULT -> processCombatResult(delta)
            BattleState.POST_ACTION -> processPostAction(delta)
            BattleState.RESULT -> handleResultInput()
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

        // ステータスパネル描画
        val displayUnit = inspectedUnit ?: selectedUnit ?: turnManager.activeUnit
        if (displayUnit != null) {
            renderStatusPanel(displayUnit)
        }

        // 行動順キュー描画
        renderActionQueue()

        // ターン情報描画
        renderTurnInfo()
    }

    // ==================== ゲームロジック ====================

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

        // AIパターン決定: 同盟はDEFENSIVE、その他はAGGRESSIVE
        val pattern = when (activeUnit.faction) {
            Faction.ALLY -> AISystem.AIPattern.DEFENSIVE
            else -> AISystem.AIPattern.AGGRESSIVE
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
     * 表示時間経過後、撃破ユニットを除去して行動後ウェイトに遷移する。
     *
     * @param delta フレームデルタタイム（秒）
     */
    private fun processCombatResult(delta: Float) {
        stateTimer += delta
        if (stateTimer < GameConfig.COMBAT_RESULT_DELAY) return

        // 撃破ユニットを除去
        val result = pendingBattleResult
        if (result != null) {
            if (result.defenderDefeated) {
                val targetPos = battleMap.getUnitPosition(result.defender)
                if (targetPos != null) battleMap.removeUnit(targetPos)
            }
            if (result.attackerDefeated) {
                val attackerPos = battleMap.getUnitPosition(result.attacker)
                if (attackerPos != null) battleMap.removeUnit(attackerPos)
            }
        }

        pendingBattleResult = null
        enterPostAction()
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
        // 選択状態をクリア
        selectedUnit = null
        movablePositions = emptySet()
        attackablePositions = emptySet()
        inspectedUnit = null

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

        // 勝敗判定
        val outcome = victoryChecker.checkOutcome(
            battleMap, VictoryChecker.VictoryConditionType.DEFEAT_ALL
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
     * 各ユニットの下にCTバーを表示してチャージ状態を可視化する。
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
     * ユニットの図形（円・CTバー）を描画する
     *
     * @param unit 描画対象ユニット
     * @param cx ユニット中心X座標（ピクセル）
     * @param cy ユニット中心Y座標（ピクセル）
     * @param isActive アクティブユニットかどうか
     */
    private fun drawUnitShape(unit: GameUnit, cx: Float, cy: Float, isActive: Boolean) {
        val tileSize = GameConfig.TILE_SIZE

        // アクティブユニットの金色リング
        if (isActive) {
            shapeRenderer.setColor(1f, 0.85f, 0.1f, 1f)
            shapeRenderer.circle(cx, cy, tileSize / 2.5f)
        }

        // 陣営に応じた色
        when (unit.faction) {
            Faction.PLAYER -> shapeRenderer.setColor(0.2f, 0.4f, 1f, 1f)
            Faction.ENEMY -> shapeRenderer.setColor(1f, 0.2f, 0.2f, 1f)
            Faction.ALLY -> shapeRenderer.setColor(0.2f, 1f, 0.4f, 1f)
        }
        shapeRenderer.circle(cx, cy, tileSize / 3)

        // CTバー（ユニット下部に小さなバーを描画）
        val barWidth = tileSize * 0.7f
        val barHeight = 4f
        val barX = cx - barWidth / 2
        val barY = cy - tileSize / 2 + 2f
        val ctRatio = (unit.ct.toFloat() / GameConfig.CT_THRESHOLD).coerceIn(0f, 1f)

        // バー背景
        shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 1f)
        shapeRenderer.rect(barX, barY, barWidth, barHeight)

        // バー本体（CT割合に応じた色）
        when {
            ctRatio >= 0.8f -> shapeRenderer.setColor(1f, 0.9f, 0.2f, 1f)
            ctRatio >= 0.5f -> shapeRenderer.setColor(0.3f, 0.8f, 1f, 1f)
            else -> shapeRenderer.setColor(0.4f, 0.4f, 0.4f, 1f)
        }
        shapeRenderer.rect(barX, barY, barWidth * ctRatio, barHeight)
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
        val panelHeight = 520f
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
        font.draw(batch, "SPD  ${stats.spd}", col2X, textY)
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
            font.color = Color.GRAY
            font.draw(batch, "-- No Weapon --", col1X, textY)
        }

        batch.end()
    }

    // ==================== テストデータ・ユーティリティ ====================

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
        playerUnit1.weapons.add(Weapon("ironSword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3, durability = 46))

        val playerUnit2 = GameUnit(
            id = "hero_02", name = "リーナ",
            unitClass = UnitClass.LANCER, faction = Faction.PLAYER,
            stats = Stats(hp = 18, str = 7, mag = 0, skl = 5, spd = 5, lck = 3, def = 7, res = 1),
            growthRate = GrowthRate(hp = 60, str = 55, mag = 5, skl = 45, spd = 40, lck = 30, def = 50, res = 20)
        )
        playerUnit2.weapons.add(Weapon("ironLance", "鉄の槍", WeaponType.LANCE, might = 7, hit = 80, weight = 5, durability = 45))

        val playerUnit3 = GameUnit(
            id = "hero_03", name = "マリア",
            unitClass = UnitClass.ARCHER, faction = Faction.PLAYER,
            stats = Stats(hp = 16, str = 5, mag = 0, skl = 8, spd = 7, lck = 4, def = 3, res = 3),
            growthRate = GrowthRate(hp = 55, str = 45, mag = 5, skl = 60, spd = 55, lck = 40, def = 25, res = 30)
        )
        playerUnit3.weapons.add(Weapon("ironBow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2, durability = 45))

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
        enemy1.weapons.add(Weapon("ironAxe", "鉄の斧", WeaponType.AXE, might = 8, hit = 75, weight = 6, durability = 40))

        val enemy2 = GameUnit(
            id = "enemy_02", name = "山賊B",
            unitClass = UnitClass.AXE_FIGHTER, faction = Faction.ENEMY,
            stats = Stats(hp = 18, str = 5, mag = 0, skl = 2, spd = 3, lck = 0, def = 3, res = 0),
            growthRate = GrowthRate()
        )
        enemy2.weapons.add(Weapon("ironAxe2", "鉄の斧", WeaponType.AXE, might = 8, hit = 75, weight = 6, durability = 40))

        val enemy3 = GameUnit(
            id = "enemy_03", name = "盗賊",
            unitClass = UnitClass.SWORD_FIGHTER, faction = Faction.ENEMY,
            stats = Stats(hp = 16, str = 4, mag = 0, skl = 6, spd = 9, lck = 2, def = 2, res = 1),
            growthRate = GrowthRate()
        )
        enemy3.weapons.add(Weapon("ironSword2", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3, durability = 46))

        map.placeUnit(enemy1, Position(11, 3))
        map.placeUnit(enemy2, Position(12, 6))
        map.placeUnit(enemy3, Position(10, 5))

        return map
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
