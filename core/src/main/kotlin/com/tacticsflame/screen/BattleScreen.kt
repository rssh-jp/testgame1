package com.tacticsflame.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import com.tacticsflame.system.*

/**
 * バトル画面
 * メインのゲームプレイが行われる画面
 */
class BattleScreen(private val game: TacticsFlameGame) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var font: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val camera = OrthographicCamera()
    private val viewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT, camera)

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
    private var battleState: BattleState = BattleState.IDLE

    /**
     * バトル画面の状態
     */
    enum class BattleState {
        /** 待機中（ユニット選択待ち） */
        IDLE,
        /** ユニット選択済み（移動先選択待ち） */
        UNIT_SELECTED,
        /** 移動完了（アクション選択待ち） */
        ACTION_SELECT,
        /** 攻撃対象選択 */
        ATTACK_SELECT,
        /** 戦闘アニメーション中 */
        BATTLE_ANIMATION,
        /** 敵フェイズ */
        ENEMY_PHASE,
        /** 勝利/敗北 */
        RESULT
    }

    // 選択中のユニットと移動範囲
    private var selectedUnit: GameUnit? = null
    private var movablePositions: Set<Position> = emptySet()
    private var attackablePositions: Set<Position> = emptySet()

    /** 情報表示中のユニット（敵味方問わずタップで表示） */
    private var inspectedUnit: GameUnit? = null

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        font = BitmapFont().apply {
            data.setScale(2f)
            color = Color.WHITE
        }

        // テスト用マップ生成
        battleMap = createTestMap()

        // ターン開始
        val allUnits = battleMap.getAllUnits().map { it.second }
        turnManager.reset()
        turnManager.startPhase(allUnits)

        Gdx.app.log(TAG, "バトル画面初期化完了")
    }

    /**
     * フレーム描画処理
     */
    override fun render(delta: Float) {
        // 画面クリア
        Gdx.gl.glClearColor(0.2f, 0.3f, 0.2f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply(true)

        // マップ描画（シェイプレンダラーで仮表示）
        shapeRenderer.projectionMatrix = camera.combined
        renderMap()
        renderUnits()

        // 移動範囲表示
        if (battleState == BattleState.UNIT_SELECTED) {
            renderMovableRange()
        }

        // ステータスパネル描画
        val displayUnit = selectedUnit ?: inspectedUnit
        if (displayUnit != null) {
            renderStatusPanel(displayUnit)
        }

        // 入力処理
        handleInput()
    }

    /**
     * マップを描画する（プロトタイプ：ShapeRendererで色分け）
     */
    private fun renderMap() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (y in 0 until battleMap.height) {
            for (x in 0 until battleMap.width) {
                val tile = battleMap.getTile(x, y) ?: continue
                // 地形に応じた色設定
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
     * ユニットを描画する（プロトタイプ：色付き丸で仮表示）
     */
    private fun renderUnits() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((pos, unit) in battleMap.getAllUnits()) {
            if (unit.isDefeated) continue
            // 陣営に応じた色
            when (unit.faction) {
                Faction.PLAYER -> shapeRenderer.setColor(0.2f, 0.4f, 1f, 1f)
                Faction.ENEMY -> shapeRenderer.setColor(1f, 0.2f, 0.2f, 1f)
                Faction.ALLY -> shapeRenderer.setColor(0.2f, 1f, 0.4f, 1f)
            }
            // 行動済みは暗くする
            if (unit.hasActed) {
                shapeRenderer.setColor(
                    shapeRenderer.color.r * 0.5f,
                    shapeRenderer.color.g * 0.5f,
                    shapeRenderer.color.b * 0.5f,
                    1f
                )
            }
            val tileSize = GameConfig.TILE_SIZE
            shapeRenderer.circle(
                pos.x * tileSize + tileSize / 2,
                pos.y * tileSize + tileSize / 2,
                tileSize / 3
            )
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
     * 入力処理
     */
    private fun handleInput() {
        if (!Gdx.input.justTouched()) return

        // スクリーン座標をワールド座標に変換
        val screenX = Gdx.input.x.toFloat()
        val screenY = Gdx.input.y.toFloat()
        val worldCoords = viewport.unproject(
            com.badlogic.gdx.math.Vector2(screenX, screenY)
        )

        val tileX = (worldCoords.x / GameConfig.TILE_SIZE).toInt()
        val tileY = (worldCoords.y / GameConfig.TILE_SIZE).toInt()
        val tappedPos = Position(tileX, tileY)

        when (battleState) {
            BattleState.IDLE -> {
                val unit = battleMap.getUnitAt(tappedPos)
                if (unit != null && unit.faction == Faction.PLAYER && !unit.hasActed) {
                    // 自軍ユニット選択 → 移動モードへ
                    selectedUnit = unit
                    inspectedUnit = null
                    movablePositions = pathFinder.getMovablePositions(unit, tappedPos, battleMap)
                    attackablePositions = pathFinder.getAttackablePositions(unit, movablePositions, battleMap)
                    battleState = BattleState.UNIT_SELECTED
                    Gdx.app.log(TAG, "ユニット選択: ${unit.name}")
                } else if (unit != null) {
                    // 敵・同盟・行動済みユニットの情報表示
                    inspectedUnit = unit
                    Gdx.app.log(TAG, "ユニット情報表示: ${unit.name}")
                } else {
                    // 何もないマスをタップ → 情報パネルを閉じる
                    inspectedUnit = null
                }
            }

            BattleState.UNIT_SELECTED -> {
                if (tappedPos in movablePositions) {
                    // 移動実行
                    val unitPos = battleMap.getUnitPosition(selectedUnit!!)!!
                    battleMap.moveUnit(unitPos, tappedPos)
                    selectedUnit!!.hasActed = true
                    battleState = BattleState.IDLE
                    movablePositions = emptySet()
                    attackablePositions = emptySet()
                    selectedUnit = null
                    inspectedUnit = null

                    // 全員行動済みチェック
                    val allUnits = battleMap.getAllUnits().map { it.second }
                    if (turnManager.allUnitsActed(allUnits)) {
                        endPlayerPhase()
                    }
                    Gdx.app.log(TAG, "移動完了")
                } else {
                    // 選択解除
                    selectedUnit = null
                    inspectedUnit = null
                    movablePositions = emptySet()
                    attackablePositions = emptySet()
                    battleState = BattleState.IDLE
                }
            }

            else -> { /* 他の状態は後続フェーズで実装 */ }
        }
    }

    /**
     * プレイヤーフェイズ終了処理
     */
    private fun endPlayerPhase() {
        Gdx.app.log(TAG, "プレイヤーフェイズ終了 → エネミーフェイズ開始")
        turnManager.advancePhase() // PLAYER → ENEMY
        val allUnits = battleMap.getAllUnits().map { it.second }
        turnManager.startPhase(allUnits) // 敵ユニットの hasActed をリセット
        battleState = BattleState.ENEMY_PHASE
        executeEnemyPhase()
    }

    /**
     * エネミーフェイズを実行する
     */
    private fun executeEnemyPhase() {
        val enemyUnits = battleMap.getAllUnits()
            .filter { it.second.faction == Faction.ENEMY && !it.second.isDefeated }

        for ((_, enemy) in enemyUnits) {
            val action = aiSystem.decideAction(enemy, battleMap)
            when (val act = action.action) {
                is AISystem.Action.MoveAndAttack -> {
                    val enemyPos = battleMap.getUnitPosition(enemy)!!
                    battleMap.moveUnit(enemyPos, act.moveTo)
                    val result = battleSystem.executeBattle(enemy, act.target, battleMap)
                    Gdx.app.log(TAG, "敵攻撃: ${enemy.name} → ${act.target.name} (ダメージ: ${result.attacks.sumOf { it.damage }})")
                    // 撃破されたユニットを除去
                    if (result.defenderDefeated) {
                        battleMap.removeUnit(battleMap.getUnitPosition(act.target) ?: continue)
                    }
                    if (result.attackerDefeated) {
                        battleMap.removeUnit(act.moveTo)
                    }
                }
                is AISystem.Action.Move -> {
                    val enemyPos = battleMap.getUnitPosition(enemy)!!
                    battleMap.moveUnit(enemyPos, act.moveTo)
                }
                is AISystem.Action.Wait -> { /* 何もしない */ }
            }
            enemy.hasActed = true
        }

        // 勝敗判定
        val outcome = victoryChecker.checkOutcome(
            battleMap, VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )
        when (outcome) {
            VictoryChecker.BattleOutcome.VICTORY -> {
                Gdx.app.log(TAG, "勝利！")
                battleState = BattleState.RESULT
            }
            VictoryChecker.BattleOutcome.DEFEAT -> {
                Gdx.app.log(TAG, "敗北...")
                battleState = BattleState.RESULT
            }
            VictoryChecker.BattleOutcome.ONGOING -> {
                // ENEMY → ALLYフェイズ
                turnManager.advancePhase()
                val allUnits = battleMap.getAllUnits().map { it.second }

                // 同盟ユニットがいる場合はALLYフェイズを実行
                val allyUnits = allUnits.filter { it.faction == Faction.ALLY && !it.isDefeated }
                if (allyUnits.isNotEmpty()) {
                    turnManager.startPhase(allUnits) // 同盟ユニットの hasActed をリセット
                    executeAllyPhase()
                }

                // ALLY → PLAYER（ターン+1）
                turnManager.advancePhase()
                turnManager.startPhase(allUnits) // プレイヤーユニットの hasActed をリセット
                battleState = BattleState.IDLE
                Gdx.app.log(TAG, "ターン${turnManager.turnNumber} プレイヤーフェイズ開始")
            }
        }
    }

    /**
     * アライ（同盟軍）フェイズを実行する
     * 同盟ユニットはAIで自動行動する
     */
    private fun executeAllyPhase() {
        val allyUnits = battleMap.getAllUnits()
            .filter { it.second.faction == Faction.ALLY && !it.second.isDefeated }

        for ((_, ally) in allyUnits) {
            val action = aiSystem.decideAction(ally, battleMap, AISystem.AIPattern.DEFENSIVE)
            when (val act = action.action) {
                is AISystem.Action.MoveAndAttack -> {
                    val allyPos = battleMap.getUnitPosition(ally)!!
                    battleMap.moveUnit(allyPos, act.moveTo)
                    val result = battleSystem.executeBattle(ally, act.target, battleMap)
                    Gdx.app.log(TAG, "同盟攻撃: ${ally.name} → ${act.target.name}")
                    if (result.defenderDefeated) {
                        battleMap.removeUnit(battleMap.getUnitPosition(act.target) ?: continue)
                    }
                    if (result.attackerDefeated) {
                        battleMap.removeUnit(act.moveTo)
                    }
                }
                is AISystem.Action.Move -> {
                    val allyPos = battleMap.getUnitPosition(ally)!!
                    battleMap.moveUnit(allyPos, act.moveTo)
                }
                is AISystem.Action.Wait -> { /* 何もしない */ }
            }
            ally.hasActed = true
        }
        Gdx.app.log(TAG, "同盟フェイズ完了")
    }

    /**
     * ユニットのステータスパネルを描画する
     * 画面右上に半透明の背景付きでユニット情報を表示
     *
     * @param unit 表示対象のユニット
     */
    private fun renderStatusPanel(unit: GameUnit) {
        val panelWidth = 380f
        val panelHeight = 420f
        val panelX = GameConfig.VIRTUAL_WIDTH - panelWidth - 16f
        val panelY = GameConfig.VIRTUAL_HEIGHT - panelHeight - 16f

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

        // HPバー
        textY -= 8f
        font.color = Color.WHITE
        font.draw(batch, "HP  ${unit.currentHp} / ${unit.maxHp}", textX, textY)
        textY -= 8f
        batch.end()

        // HPバー描画（シェイプ）
        val barX = textX
        val barY = textY - 14f
        val barWidth = panelWidth - 32f
        val barHeight = 10f
        val hpRatio = unit.currentHp.toFloat() / unit.maxHp.toFloat()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(barX, barY, barWidth, barHeight)
        val hpColor = when {
            hpRatio > 0.5f -> Color(0.2f, 0.9f, 0.2f, 1f)
            hpRatio > 0.25f -> Color(0.9f, 0.9f, 0.1f, 1f)
            else -> Color(0.9f, 0.2f, 0.2f, 1f)
        }
        shapeRenderer.color = hpColor
        shapeRenderer.rect(barX, barY, barWidth * hpRatio, barHeight)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ステータス値
        textY = barY - 20f
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
        viewport.update(width, height, true)
    }

    /**
     * リソース解放
     */
    override fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
        font.dispose()
    }

    companion object {
        private const val TAG = "BattleScreen"
    }
}
