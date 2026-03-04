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
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.data.MapLoader
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
    private val glyphLayout = GlyphLayout()

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

    /** 勝利条件（JSONから読み込み） */
    private var victoryConditionType: VictoryChecker.VictoryConditionType = VictoryChecker.VictoryConditionType.DEFEAT_ALL

    /** マップローダー */
    private val mapLoader = MapLoader()

    /** 入れ替え元として選択中のスポーンインデックス（null = 未選択） */
    private var selectedSpawnIndex: Int? = null

    /** 初期化完了フラグ（setupBattlePreview 成功時に true） */
    private var isInitialized: Boolean = false

    /** 座標変換用の一時 Vector2（GC軽減） */
    private val tmpVec = Vector2()

    // ボタン領域（縦画面レイアウト: 下部に配置）
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
        titleFont = FontManager.getFont(size = 48)
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
     * チャプターの mapFileName に対応するJSONファイルから
     * マップ・敵ユニット・スポーン位置・勝利条件を読み込む。
     */
    private fun setupBattlePreview() {
        // パーティの平均レベルを計算（ランダム敵生成用）
        val roster = game.gameProgress.party.roster
        val partyAverageLevel = if (roster.isNotEmpty()) {
            roster.sumOf { it.level } / roster.size
        } else {
            1
        }

        val result = mapLoader.loadMap(chapter.mapFileName, partyAverageLevel)

        if (result == null) {
            Gdx.app.error(TAG, "マップ読み込み失敗: ${chapter.mapFileName}、ワールドマップに戻る")
            game.screenManager.navigateToWorldMap()
            return
        }

        previewMap = result.battleMap
        spawnPositions = result.playerSpawns
        enemyUnits = result.enemies
        victoryConditionType = result.victoryConditionType
        isInitialized = true

        Gdx.app.log(TAG, "マップ読み込み完了: ${chapter.mapFileName} " +
            "(${previewMap.width}x${previewMap.height}, スポーン: ${spawnPositions.size}, " +
            "敵: ${enemyUnits.size}, 勝利条件: $victoryConditionType)")

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
        // 初期化未完了時は描画をスキップ（マップ読み込み失敗時のクラッシュ防止）
        if (!isInitialized) return

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
        val uiCoord = uiViewport.unproject(tmpVec.set(screenX, screenY))
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

        // マップ上のスポーン位置タップ（配置入れ替え）
        handleMapTap(screenX, screenY)
    }

    /**
     * マップ上のタップを処理し、ユニット配置の入れ替えを行う
     *
     * 操作フロー:
     * 1. ユニットがいるスポーンをタップ → 選択状態にする
     * 2. 選択中に別のスポーンをタップ → 入れ替え（ユニット同士 or 空きスポーンに移動）
     * 3. 選択中に同じスポーンをタップ → 選択解除
     * 4. 空のスポーンを未選択状態でタップ → 何もしない
     *
     * @param screenX タッチスクリーン座標X
     * @param screenY タッチスクリーン座標Y
     */
    private fun handleMapTap(screenX: Float, screenY: Float) {
        // スクリーン座標をマップワールド座標に変換
        val mapCoord = mapViewport.unproject(tmpVec.set(screenX, screenY))
        val tileSize = GameConfig.TILE_SIZE
        val tileX = (mapCoord.x / tileSize).toInt()
        val tileY = (mapCoord.y / tileSize).toInt()

        // タップ位置がスポーン位置と一致するか判定
        val tappedSpawnIndex = spawnPositions.indexOfFirst { it.x == tileX && it.y == tileY }
        if (tappedSpawnIndex < 0) {
            // スポーン外タップ → 選択解除
            selectedSpawnIndex = null
            return
        }

        val currentSelected = selectedSpawnIndex
        if (currentSelected == null) {
            // 未選択状態 → ユニットがいるスポーンを選択
            if (deploymentMap.containsKey(tappedSpawnIndex)) {
                selectedSpawnIndex = tappedSpawnIndex
                Gdx.app.log(TAG, "スポーン選択: ${tappedSpawnIndex + 1} (${deploymentMap[tappedSpawnIndex]?.name})")
            }
        } else if (currentSelected == tappedSpawnIndex) {
            // 同じスポーンを再タップ → 選択解除
            selectedSpawnIndex = null
        } else {
            // 別のスポーンをタップ → 入れ替え
            swapDeployment(currentSelected, tappedSpawnIndex)
            selectedSpawnIndex = null
        }
    }

    /**
     * 2つのスポーン位置間でユニット配置を入れ替える
     *
     * 両方にユニットがいる場合は入れ替え、
     * 片方が空の場合はユニットを移動する。
     *
     * @param fromIndex 入れ替え元のスポーンインデックス
     * @param toIndex 入れ替え先のスポーンインデックス
     */
    private fun swapDeployment(fromIndex: Int, toIndex: Int) {
        val unitA = deploymentMap[fromIndex]
        val unitB = deploymentMap[toIndex]

        // 入れ替え元にユニットがいない場合は何もしない
        if (unitA == null) return

        if (unitB != null) {
            // 両方にユニットがいる → 入れ替え
            deploymentMap[fromIndex] = unitB
            deploymentMap[toIndex] = unitA
            Gdx.app.log(TAG, "配置入れ替え: ${unitA.name} ↔ ${unitB.name}")
        } else {
            // 移動先が空き → 移動
            deploymentMap.remove(fromIndex)
            deploymentMap[toIndex] = unitA
            Gdx.app.log(TAG, "配置移動: ${unitA.name} → スポーン${toIndex + 1}")
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
            victoryCondition = victoryConditionType
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
     *
     * 選択中のスポーンにはオレンジ色のハイライトリングを表示し、
     * 入れ替え先候補のスポーンには半透明の枠を表示する。
     */
    private fun renderSpawnPositions() {
        val tileSize = GameConfig.TILE_SIZE
        val selected = selectedSpawnIndex

        // 配置エリアのハイライト（全スポーン位置を半透明で表示）
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((index, pos) in spawnPositions.withIndex()) {
            val rx = pos.x * tileSize.toFloat()
            val ry = pos.y * tileSize.toFloat()

            // 配置エリアの背景ハイライト
            if (deploymentMap.containsKey(index)) {
                shapeRenderer.setColor(0.2f, 0.3f, 0.6f, 0.2f)
            } else {
                shapeRenderer.setColor(0.4f, 0.4f, 0.6f, 0.15f)
            }
            shapeRenderer.rect(rx, ry, tileSize - 1f, tileSize - 1f)
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ユニット本体とスポーンマーカーの描画（半透明あり）
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for ((index, pos) in spawnPositions.withIndex()) {
            val cx = pos.x * tileSize + tileSize / 2f
            val cy = pos.y * tileSize + tileSize / 2f
            val isSelected = index == selected

            val unit = deploymentMap[index]
            if (unit != null) {
                // 選択中のユニットにオレンジ色リング表示
                if (isSelected) {
                    shapeRenderer.setColor(1f, 0.7f, 0.1f, 1f)
                    shapeRenderer.circle(cx, cy, tileSize / 2.2f)
                }

                // 配置済み: 青い円
                shapeRenderer.setColor(0.2f, 0.4f, 1f, 1f)
                shapeRenderer.circle(cx, cy, tileSize / 3f)
            } else {
                // 空きスポーン: 選択中は入れ替え先候補としてハイライト
                if (selected != null) {
                    shapeRenderer.setColor(1f, 0.7f, 0.1f, 0.5f)
                    shapeRenderer.circle(cx, cy, tileSize / 3.5f)
                } else {
                    shapeRenderer.setColor(0.5f, 0.5f, 1f, 0.4f)
                    shapeRenderer.circle(cx, cy, tileSize / 4f)
                }
            }
        }
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ユニット名を表示
        batch.begin()
        for ((index, pos) in spawnPositions.withIndex()) {
            val cx = pos.x * tileSize + tileSize / 2f
            val cy = pos.y * tileSize + tileSize / 2f
            val unit = deploymentMap[index]
            if (unit != null) {
                smallFont.color = if (index == selected) Color.GOLD else Color.WHITE
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
        for ((_, pos) in enemyUnits) {
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
        val headerText = "戦闘準備 - ${chapter.name}"
        glyphLayout.setText(titleFont, headerText)
        titleFont.draw(
            batch, headerText,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 30f
        )
        batch.end()
    }

    /**
     * 出撃情報パネルを描画する
     *
     * スポーン位置ごとのユニット配置一覧と、
     * 配置入れ替えの操作ガイドを表示する。
     */
    private fun renderDeploymentInfo() {
        val deployedCount = deploymentMap.size
        val panelW = 360f
        val panelH = 340f + spawnPositions.size * 28f
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
        font.draw(batch, "出撃メンバー (${deployedCount}人)", textX, textY)
        textY -= lineH + 8f

        val selected = selectedSpawnIndex
        for ((index, _) in spawnPositions.withIndex()) {
            val unit = deploymentMap[index]
            val isSelected = index == selected
            if (unit != null) {
                smallFont.color = if (isSelected) Color.GOLD else Color.WHITE
                val marker = if (isSelected) "▶" else " "
                smallFont.draw(batch, "$marker${index + 1}. ${unit.name} (Lv.${unit.level})", textX, textY)
            } else {
                smallFont.color = if (selected != null) Color(0.6f, 0.6f, 0.4f, 1f) else Color.DARK_GRAY
                smallFont.draw(batch, "  ${index + 1}. ---", textX, textY)
            }
            textY -= 28f
        }

        textY -= 16f
        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(batch, "敵: ${enemyUnits.size}体", textX, textY)
        textY -= 32f

        // 操作ガイド
        smallFont.color = Color(0.7f, 0.7f, 0.5f, 1f)
        if (selected != null) {
            smallFont.draw(batch, "別の位置をタップで入替", textX, textY)
        } else {
            smallFont.draw(batch, "ユニットをタップで配置変更", textX, textY)
        }

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
