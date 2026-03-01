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
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import com.tacticsflame.system.*
import com.tacticsflame.util.FontManager

/**
 * バトル画面
 *
 * CTベースの個別ユニットターン制で戦闘を行う画面。
 * 各ユニットのSPDに応じてCTが蓄積され、閾値に達したユニットから順に行動する。
 * プレイヤーユニットは手動操作、敵・同盟ユニットはAIで自動行動する。
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
        /** プレイヤーユニットのターン（移動先選択待ち） */
        PLAYER_TURN,
        /** AIユニットのターン（自動行動実行中） */
        AI_TURN,
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
        // 画面クリア
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()
        centerCameraOnMap()
        shapeRenderer.projectionMatrix = camera.combined

        // マップ・ユニット描画
        renderMap()
        renderUnits()

        // 移動範囲表示（プレイヤーターン時）
        if (battleState == BattleState.PLAYER_TURN) {
            renderMovableRange()
        }

        // ステータスパネル描画
        val displayUnit = inspectedUnit ?: selectedUnit ?: turnManager.activeUnit
        if (displayUnit != null) {
            renderStatusPanel(displayUnit)
        }

        // 行動順キュー描画
        renderActionQueue()

        // ターン情報描画
        renderTurnInfo()

        // 状態に応じた処理
        when (battleState) {
            BattleState.CT_ADVANCING -> advanceCT()
            BattleState.PLAYER_TURN -> handlePlayerInput()
            BattleState.AI_TURN -> executeAITurn()
            BattleState.RESULT -> handleResultInput()
        }
    }

    // ==================== ゲームロジック ====================

    /**
     * CTを進行させて次の行動ユニットを決定する
     *
     * 全ユニットのCTにSPDを加算し、閾値に達したユニットのターンに遷移する。
     * プレイヤーユニットの場合は移動範囲を自動表示、AIユニットは自動行動へ。
     */
    private fun advanceCT() {
        val allUnits = battleMap.getAllUnits().map { it.second }
        val nextUnit = turnManager.advanceToNextUnit(allUnits) ?: return

        // 行動順予測を更新
        updateActionQueue()

        when (nextUnit.faction) {
            Faction.PLAYER -> {
                // プレイヤーユニットのターン：自動的に選択して移動範囲を表示
                selectedUnit = nextUnit
                val unitPos = battleMap.getUnitPosition(nextUnit)!!
                val reachable = pathFinder.getMovablePositions(nextUnit, unitPos, battleMap)
                movablePositions = reachable + unitPos  // 現在位置も含める（待機用）
                attackablePositions = pathFinder.getAttackablePositions(nextUnit, reachable, battleMap)
                battleState = BattleState.PLAYER_TURN
                Gdx.app.log(TAG, "${nextUnit.name} のターン（CT: ${nextUnit.ct}）")
            }
            Faction.ENEMY, Faction.ALLY -> {
                battleState = BattleState.AI_TURN
                Gdx.app.log(TAG, "${nextUnit.name} のターン（CT: ${nextUnit.ct}）")
            }
        }
    }

    /**
     * プレイヤーユニットの入力処理
     *
     * 移動可能範囲内をタップで移動実行、現在位置タップで待機。
     * 範囲外タップではユニット情報を表示する。
     */
    private fun handlePlayerInput() {
        if (!Gdx.input.justTouched()) return

        val screenX = Gdx.input.x.toFloat()
        val screenY = Gdx.input.y.toFloat()
        val worldCoords = viewport.unproject(
            com.badlogic.gdx.math.Vector2(screenX, screenY)
        )

        val tileX = (worldCoords.x / GameConfig.TILE_SIZE).toInt()
        val tileY = (worldCoords.y / GameConfig.TILE_SIZE).toInt()
        val tappedPos = Position(tileX, tileY)

        val activeUnit = turnManager.activeUnit ?: return

        if (tappedPos in movablePositions) {
            // 移動実行（現在位置タップは待機）
            val unitPos = battleMap.getUnitPosition(activeUnit)!!
            if (tappedPos != unitPos) {
                battleMap.moveUnit(unitPos, tappedPos)
                Gdx.app.log(TAG, "${activeUnit.name} 移動完了")
            } else {
                Gdx.app.log(TAG, "${activeUnit.name} 待機")
            }

            // 行動完了処理
            completeUnitAction(activeUnit)
        } else {
            // 範囲外タップ：ユニット情報表示
            val unit = battleMap.getUnitAt(tappedPos)
            inspectedUnit = if (unit != null && unit != activeUnit) unit else null
        }
    }

    /**
     * AIユニットのターンを実行する
     *
     * 敵ユニットはAGGRESSIVE、同盟ユニットはDEFENSIVEパターンで行動する。
     */
    private fun executeAITurn() {
        val activeUnit = turnManager.activeUnit ?: run {
            battleState = BattleState.CT_ADVANCING
            return
        }

        val pattern = when (activeUnit.faction) {
            Faction.ALLY -> AISystem.AIPattern.DEFENSIVE
            else -> AISystem.AIPattern.AGGRESSIVE
        }

        val action = aiSystem.decideAction(activeUnit, battleMap, pattern)

        when (val act = action.action) {
            is AISystem.Action.MoveAndAttack -> {
                val unitPos = battleMap.getUnitPosition(activeUnit)!!
                if (act.moveTo != unitPos) {
                    battleMap.moveUnit(unitPos, act.moveTo)
                }
                val result = battleSystem.executeBattle(activeUnit, act.target, battleMap)
                Gdx.app.log(
                    TAG,
                    "AI攻撃: ${activeUnit.name} → ${act.target.name} " +
                        "(ダメージ: ${result.attacks.sumOf { it.damage }})"
                )
                // 撃破ユニットを除去
                if (result.defenderDefeated) {
                    val targetPos = battleMap.getUnitPosition(act.target)
                    if (targetPos != null) battleMap.removeUnit(targetPos)
                }
                if (result.attackerDefeated) {
                    battleMap.removeUnit(act.moveTo)
                }
            }
            is AISystem.Action.Move -> {
                val unitPos = battleMap.getUnitPosition(activeUnit)!!
                battleMap.moveUnit(unitPos, act.moveTo)
                Gdx.app.log(TAG, "AI移動: ${activeUnit.name}")
            }
            is AISystem.Action.Wait -> {
                Gdx.app.log(TAG, "AI待機: ${activeUnit.name}")
            }
        }

        // 行動完了処理
        completeUnitAction(activeUnit)
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
     */
    private fun renderUnits() {
        val activeUnit = turnManager.activeUnit
        val tileSize = GameConfig.TILE_SIZE

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((pos, unit) in battleMap.getAllUnits()) {
            if (unit.isDefeated) continue

            val cx = pos.x * tileSize + tileSize / 2
            val cy = pos.y * tileSize + tileSize / 2

            // アクティブユニットの金色リング
            if (unit == activeUnit) {
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
            val barX = pos.x * tileSize + (tileSize - barWidth) / 2
            val barY = pos.y * tileSize + 2f
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
        shapeRenderer.end()
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
        textY -= lineHeight  // テキスト分の行送り
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
        textY -= lineHeight  // テキスト分の行送り
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
