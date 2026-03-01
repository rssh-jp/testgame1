package com.tacticsflame.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.campaign.BattleConfig
import com.tacticsflame.model.campaign.ChapterInfo
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*
import com.tacticsflame.system.VictoryChecker
import com.tacticsflame.util.FontManager

/**
 * 戦闘準備画面
 *
 * バトルマップ上にユニットを配置してから戦闘を開始する画面。
 * チャプターのマップを表示し、プレイヤースポーン位置を一覧表示する。
 * 出撃メンバーは FormationScreen で事前に選択されている前提。
 *
 * 画面フロー:
 * - 「出撃」ボタン → BattleScreen（戦闘開始）
 * - 「戻る」ボタン → WorldMapScreen
 */
class BattlePrepScreen(private val game: TacticsFlameGame) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var titleFont: BitmapFont
    private lateinit var font: BitmapFont
    private lateinit var smallFont: BitmapFont

    // マップ表示用
    private val mapCamera = OrthographicCamera()
    private lateinit var mapViewport: ExtendViewport

    // UIオーバーレイ用
    private val uiViewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)

    /** 対象チャプター */
    private lateinit var chapter: ChapterInfo

    /** プレビュー用マップ */
    private lateinit var previewMap: BattleMap

    /** スポーン位置リスト */
    private var spawnPositions: List<Position> = emptyList()

    /** 出撃ユニットの配置マッピング（スポーンインデックス → ユニット） */
    private val deploymentMap: MutableMap<Int, GameUnit> = mutableMapOf()

    /** 敵ユニットリスト（表示用） */
    private var enemyUnits: List<Pair<GameUnit, Position>> = emptyList()

    // ボタン領域
    private val startButtonX = GameConfig.VIRTUAL_WIDTH - 360f
    private val startButtonY = 60f
    private val startButtonW = 280f
    private val startButtonH = 80f

    private val backButtonX = 80f
    private val backButtonY = 60f
    private val backButtonW = 200f
    private val backButtonH = 70f

    companion object {
        private const val TAG = "BattlePrepScreen"
    }

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        titleFont = FontManager.getFont(size = 42)
        font = FontManager.getFont(size = 32)
        smallFont = FontManager.getFont(size = 24)

        // チャプター情報の取得
        chapter = game.gameProgress.selectedChapter ?: run {
            Gdx.app.log(TAG, "チャプター未選択、ワールドマップに戻る")
            game.screenManager.navigateToWorldMap()
            return
        }

        // マップ生成（テストマップ）
        setupBattlePreview()

        // 出撃メンバーを初期配置
        autoDeployUnits()

        Gdx.app.log(TAG, "戦闘準備画面: ${chapter.name}")
    }

    /**
     * マップのプレビューを構築する
     *
     * 現状は chapter_1 のみハードコード。
     * TODO: JSON読み込みに切り替え
     */
    private fun setupBattlePreview() {
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

        previewMap = BattleMap(chapter.id, chapter.name, width, height, tiles)

        // スポーン位置
        spawnPositions = listOf(
            Position(2, 2),
            Position(2, 4),
            Position(3, 3),
            Position(1, 3)
        )

        // 敵ユニット準備
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

        enemyUnits = listOf(
            enemy1 to Position(11, 3),
            enemy2 to Position(12, 6),
            enemy3 to Position(10, 5)
        )

        // マップビューポート初期化
        val mapPixelW = previewMap.width * GameConfig.TILE_SIZE
        val mapPixelH = previewMap.height * GameConfig.TILE_SIZE
        val padding = GameConfig.TILE_SIZE * GameConfig.BATTLE_MAP_PADDING_TILES
        mapViewport = ExtendViewport(
            mapPixelW + padding * 2,
            mapPixelH + padding * 2,
            mapCamera
        )
        mapViewport.update(Gdx.graphics.width, Gdx.graphics.height)
        mapCamera.position.set(mapPixelW / 2f, mapPixelH / 2f, 0f)
        mapCamera.update()
    }

    /**
     * 出撃メンバーをスポーン位置に自動配置する
     */
    private fun autoDeployUnits() {
        deploymentMap.clear()
        val deployedUnits = game.gameProgress.party.getDeployedUnits()
        for ((index, unit) in deployedUnits.withIndex()) {
            if (index < spawnPositions.size) {
                deploymentMap[index] = unit
            }
        }
    }

    /**
     * フレーム描画処理
     *
     * @param delta 前フレームからの経過時間（秒）
     */
    override fun render(delta: Float) {
        handleInput()

        Gdx.gl.glClearColor(0.15f, 0.18f, 0.22f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        // マップ描画（マップビューポートを使用）
        mapViewport.apply()
        mapCamera.position.set(
            previewMap.width * GameConfig.TILE_SIZE / 2f,
            previewMap.height * GameConfig.TILE_SIZE / 2f,
            0f
        )
        mapCamera.update()
        shapeRenderer.projectionMatrix = mapCamera.combined
        batch.projectionMatrix = mapCamera.combined

        renderPreviewMap()
        renderSpawnPositions()
        renderEnemyPositions()

        // UIオーバーレイ描画（UIビューポートを使用）
        uiViewport.apply()
        shapeRenderer.projectionMatrix = uiViewport.camera.combined
        batch.projectionMatrix = uiViewport.camera.combined

        renderHeader()
        renderDeploymentInfo()
        renderStartButton()
        renderBackButton()
    }

    // ==================== 入力処理 ====================

    /**
     * タッチ入力を処理する
     */
    private fun handleInput() {
        if (!Gdx.input.justTouched()) return

        val screenX = Gdx.input.x.toFloat()
        val screenY = Gdx.input.y.toFloat()
        val uiCoord = uiViewport.unproject(Vector2(screenX, screenY))
        val touchX = uiCoord.x
        val touchY = uiCoord.y

        // 出撃ボタン
        if (touchX in startButtonX..(startButtonX + startButtonW) &&
            touchY in startButtonY..(startButtonY + startButtonH)
        ) {
            if (deploymentMap.isNotEmpty()) {
                startBattle()
            }
            return
        }

        // 戻るボタン
        if (touchX in backButtonX..(backButtonX + backButtonW) &&
            touchY in backButtonY..(backButtonY + backButtonH)
        ) {
            game.screenManager.navigateToWorldMap()
            return
        }
    }

    /**
     * バトルを開始する
     *
     * BattleConfig を構築して BattleScreen に遷移する。
     */
    private fun startBattle() {
        // ユニットの配置マッピングを構築
        val playerPositions = mutableMapOf<String, Position>()
        val playerUnits = mutableListOf<GameUnit>()

        for ((spawnIdx, unit) in deploymentMap) {
            val pos = spawnPositions[spawnIdx]
            playerPositions[unit.id] = pos
            playerUnits.add(unit)
        }

        val enemyPositionMap = mutableMapOf<String, Position>()
        val enemies = mutableListOf<GameUnit>()
        for ((enemy, pos) in enemyUnits) {
            enemyPositionMap[enemy.id] = pos
            enemies.add(enemy)
        }

        val config = BattleConfig(
            chapterInfo = chapter,
            battleMap = previewMap,
            playerUnits = playerUnits,
            playerPositions = playerPositions,
            enemyUnits = enemies,
            enemyPositions = enemyPositionMap,
            victoryCondition = VictoryChecker.VictoryConditionType.DEFEAT_ALL
        )

        Gdx.app.log(TAG, "バトル開始: ${chapter.name} (出撃: ${playerUnits.size}人)")
        game.screenManager.navigateToBattle(config)
    }

    // ==================== 描画メソッド ====================

    /**
     * マッププレビューを描画する
     */
    private fun renderPreviewMap() {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (y in 0 until previewMap.height) {
            for (x in 0 until previewMap.width) {
                val tile = previewMap.getTile(x, y) ?: continue
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
     * スポーン位置と配置ユニットを描画する
     */
    private fun renderSpawnPositions() {
        val tileSize = GameConfig.TILE_SIZE

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((index, pos) in spawnPositions.withIndex()) {
            val cx = pos.x * tileSize + tileSize / 2f
            val cy = pos.y * tileSize + tileSize / 2f

            val unit = deploymentMap[index]
            if (unit != null) {
                // 配置済み: 青い円
                shapeRenderer.setColor(0.2f, 0.4f, 1f, 1f)
                shapeRenderer.circle(cx, cy, tileSize / 3f)
            } else {
                // 空きスポーン: 半透明の枠
                shapeRenderer.setColor(0.5f, 0.5f, 1f, 0.4f)
                shapeRenderer.circle(cx, cy, tileSize / 4f)
            }
        }
        shapeRenderer.end()

        // ユニット名を表示
        batch.begin()
        for ((index, pos) in spawnPositions.withIndex()) {
            val cx = pos.x * tileSize + tileSize / 2f
            val cy = pos.y * tileSize + tileSize / 2f
            val unit = deploymentMap[index]
            if (unit != null) {
                smallFont.color = Color.WHITE
                smallFont.draw(batch, unit.name, cx - 24f, cy + tileSize / 2f + 20f)
            }
        }
        batch.end()
    }

    /**
     * 敵ユニットの位置を描画する
     */
    private fun renderEnemyPositions() {
        val tileSize = GameConfig.TILE_SIZE

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((enemy, pos) in enemyUnits) {
            val cx = pos.x * tileSize + tileSize / 2f
            val cy = pos.y * tileSize + tileSize / 2f
            shapeRenderer.setColor(1f, 0.2f, 0.2f, 1f)
            shapeRenderer.circle(cx, cy, tileSize / 3f)
        }
        shapeRenderer.end()

        batch.begin()
        for ((enemy, pos) in enemyUnits) {
            val cx = pos.x * tileSize + tileSize / 2f
            val cy = pos.y * tileSize + tileSize / 2f
            smallFont.color = Color(1f, 0.6f, 0.6f, 1f)
            smallFont.draw(batch, enemy.name, cx - 24f, cy + tileSize / 2f + 20f)
        }
        batch.end()
    }

    /**
     * ヘッダーを描画する
     */
    private fun renderHeader() {
        batch.begin()
        titleFont.color = Color.WHITE
        titleFont.draw(
            batch, "戦闘準備 - ${chapter.name}",
            GameConfig.VIRTUAL_WIDTH / 2f - 320f,
            GameConfig.VIRTUAL_HEIGHT - 30f
        )
        batch.end()
    }

    /**
     * 出撃情報パネルを描画する
     */
    private fun renderDeploymentInfo() {
        val panelW = 360f
        val panelH = 300f
        val panelX = GameConfig.VIRTUAL_WIDTH - panelW - 30f
        val panelY = GameConfig.VIRTUAL_HEIGHT - panelH - 80f

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.7f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.3f, 0.5f, 1f, 1f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.begin()
        var textY = panelY + panelH - 24f
        val textX = panelX + 16f
        val lineH = 34f

        font.color = Color(0.3f, 0.5f, 1f, 1f)
        font.draw(batch, "出撃メンバー", textX, textY)
        textY -= lineH + 8f

        for ((index, _) in spawnPositions.withIndex()) {
            val unit = deploymentMap[index]
            if (unit != null) {
                smallFont.color = Color.WHITE
                smallFont.draw(batch, "${index + 1}. ${unit.name} (Lv.${unit.level})", textX, textY)
            } else {
                smallFont.color = Color.DARK_GRAY
                smallFont.draw(batch, "${index + 1}. ---", textX, textY)
            }
            textY -= 28f
        }

        textY -= 16f
        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(batch, "敵: ${enemyUnits.size}体", textX, textY)

        batch.end()
    }

    /**
     * 出撃ボタンを描画する
     */
    private fun renderStartButton() {
        val canStart = deploymentMap.isNotEmpty()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (canStart) {
            shapeRenderer.setColor(0.15f, 0.4f, 0.8f, 0.95f)
        } else {
            shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 0.7f)
        }
        shapeRenderer.rect(startButtonX, startButtonY, startButtonW, startButtonH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (canStart) Color.CYAN else Color.DARK_GRAY
        shapeRenderer.rect(startButtonX, startButtonY, startButtonW, startButtonH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.begin()
        font.color = if (canStart) Color.WHITE else Color.GRAY
        font.draw(
            batch, "▶ 出撃開始",
            startButtonX + 50f,
            startButtonY + startButtonH / 2f + 14f
        )
        batch.end()
    }

    /**
     * 戻るボタンを描画する
     */
    private fun renderBackButton() {
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.4f, 0.2f, 0.2f, 0.9f)
        shapeRenderer.rect(backButtonX, backButtonY, backButtonW, backButtonH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(1f, 0.5f, 0.5f, 1f)
        shapeRenderer.rect(backButtonX, backButtonY, backButtonW, backButtonH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.begin()
        font.color = Color.WHITE
        font.draw(batch, "← 戻る", backButtonX + 40f, backButtonY + backButtonH / 2f + 12f)
        batch.end()
    }

    /**
     * ウィンドウリサイズ処理
     */
    override fun resize(width: Int, height: Int) {
        mapViewport.update(width, height)
        uiViewport.update(width, height, true)
    }

    /**
     * リソース解放
     */
    override fun dispose() {
        batch.dispose()
        shapeRenderer.dispose()
    }
}
