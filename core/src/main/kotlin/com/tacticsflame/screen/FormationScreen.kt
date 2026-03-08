package com.tacticsflame.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.viewport.FitViewport
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.UnitTactic
import com.tacticsflame.util.FontManager

/**
 * 部隊編成画面
 *
 * プレイヤーの所持ユニット一覧を表示し、出撃メンバーを選択する。
 * ユニットをタップして出撃/非出撃を切り替える。
 *
 * 画面フロー:
 * - ユニットをタップ → 出撃/非出撃のトグル
 * - 「戻る」ボタン → ワールドマップ画面へ
 */
class FormationScreen(private val game: TacticsFlameGame) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var titleFont: BitmapFont
    private lateinit var font: BitmapFont
    private lateinit var smallFont: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val viewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)

    /** 現在のスクロール位置 */
    private var scrollOffset = 0f

    /** 選択中のユニット（詳細表示用） */
    private var selectedUnit: GameUnit? = null

    /** 戻るボタンの領域 */
    private val backButtonX = 80f
    private val backButtonY = 80f
    private val backButtonW = 200f
    private val backButtonH = 70f

    /** 装備変更ボタンの領域（詳細パネル内・右下） */
    private val equipButtonW = 260f
    private val equipButtonH = 65f
    private val equipButtonX = GameConfig.VIRTUAL_WIDTH / 2f + 480f - equipButtonW - 20f
    private val equipButtonY = 190f

    /** 作戦変更ボタンの領域（詳細パネル内・左下） */
    private val tacticButtonW = 380f
    private val tacticButtonH = 65f
    private val tacticButtonX = GameConfig.VIRTUAL_WIDTH / 2f - 480f + 20f
    private val tacticButtonY = 190f

    /** 出撃ボタンの領域（詳細パネル内） */
    private val deployButtonW = 200f
    private val deployButtonH = 65f
    private val deployButtonX = GameConfig.VIRTUAL_WIDTH / 2f - deployButtonW / 2f
    private val deployButtonY = 190f

    /** ジョブチェンジボタンの領域（詳細パネル内・2段目左） */
    private val classChangeButtonW = 280f
    private val classChangeButtonH = 65f
    private val classChangeButtonX = GameConfig.VIRTUAL_WIDTH / 2f - 480f + 20f
    private val classChangeButtonY = 270f

    companion object {
        private const val TAG = "FormationScreen"

        /** ユニットスロット1つの高さ */
        private const val SLOT_HEIGHT = 120f

        /** ユニットスロットの幅 */
        private const val SLOT_WIDTH = 960f

        /** スロット表示開始Y座標 */
        private const val SLOT_START_Y = 1740f

        /** スロット左端X座標 */
        private const val SLOT_X = 60f

        /** パーティ上限（現在の出撃上限とは別。編成画面における表示制約） */
        private const val MAX_DISPLAY = 12
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
        Gdx.app.log(TAG, "部隊編成画面を表示")
    }

    /**
     * フレーム描画処理
     *
     * @param delta 前フレームからの経過時間（秒）
     */
    override fun render(delta: Float) {
        handleInput()

        Gdx.gl.glClearColor(0.08f, 0.1f, 0.15f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()

        renderHeader()
        renderUnitSlots()
        renderDetailPanel()
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
        val worldCoord = viewport.unproject(Vector2(screenX, screenY))
        val touchX = worldCoord.x
        val touchY = worldCoord.y

        // 戻るボタン判定
        if (touchX in backButtonX..(backButtonX + backButtonW) &&
            touchY in backButtonY..(backButtonY + backButtonH)
        ) {
            Gdx.app.log(TAG, "ワールドマップへ戻る")
            game.screenManager.navigateToWorldMap()
            return
        }

        // 出撃ボタン判定（ユニット選択中のみ有効）
        if (selectedUnit != null &&
            touchX in deployButtonX..(deployButtonX + deployButtonW) &&
            touchY in deployButtonY..(deployButtonY + deployButtonH)
        ) {
            val unit = selectedUnit!!
            val maxDeploy = game.gameProgress.selectedChapter?.maxDeployCount ?: 4
            val deployed = game.gameProgress.party.toggleDeploy(unit.id, maxDeploy)
            Gdx.app.log(TAG, "${unit.name}: 出撃=${deployed}")
            return
        }

        // 装備変更ボタン判定（ユニット選択中のみ有効）
        if (selectedUnit != null &&
            touchX in equipButtonX..(equipButtonX + equipButtonW) &&
            touchY in equipButtonY..(equipButtonY + equipButtonH)
        ) {
            val unit = selectedUnit!!
            Gdx.app.log(TAG, "武器装備変更画面へ: ${unit.name}")
            game.screenManager.navigateToWeaponEquip(unit)
            return
        }

        // 作戦変更ボタン判定（ユニット選択中のみ有効）
        if (selectedUnit != null &&
            touchX in tacticButtonX..(tacticButtonX + tacticButtonW) &&
            touchY in tacticButtonY..(tacticButtonY + tacticButtonH)
        ) {
            val unit = selectedUnit!!
            unit.tactic = unit.tactic.next()
            Gdx.app.log(TAG, "${unit.name}: 作戦変更 → ${unit.tactic.displayName}")
            return
        }

        // ジョブチェンジボタン判定（ユニット選択中かつ非ロードのみ有効）
        if (selectedUnit != null && !selectedUnit!!.isLord &&
            touchX in classChangeButtonX..(classChangeButtonX + classChangeButtonW) &&
            touchY in classChangeButtonY..(classChangeButtonY + classChangeButtonH)
        ) {
            val unit = selectedUnit!!
            Gdx.app.log(TAG, "クラスチェンジ画面へ: ${unit.name}")
            game.screenManager.navigateToClassChange(unit)
            return
        }

        // ユニットスロットのタップ判定
        // 詳細パネルが表示中の場合、パネル領域内のタップはスロットに伝播させない
        if (selectedUnit != null) {
            val panelX = GameConfig.VIRTUAL_WIDTH / 2f - 480f
            val panelY = 170f
            if (touchX in panelX..(panelX + 960f) && touchY in panelY..(panelY + 500f)) {
                return
            }
        }

        val roster = game.gameProgress.party.roster
        for (i in roster.indices) {
            val slotY = SLOT_START_Y - i * (SLOT_HEIGHT + 10f) + scrollOffset
            if (touchX in SLOT_X..(SLOT_X + SLOT_WIDTH) &&
                touchY in slotY - SLOT_HEIGHT..slotY
            ) {
                val unit = roster[i]
                // 選択のみ（出撃トグルはしない）
                selectedUnit = if (selectedUnit == unit) null else unit
                Gdx.app.log(TAG, "${unit.name}: 選択")
                return
            }
        }

        // 何もない場所をタップで選択解除
        selectedUnit = null
    }

    // ==================== 描画メソッド ====================

    /**
     * ヘッダーを描画する
     */
    private fun renderHeader() {
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        titleFont.color = Color.WHITE
        val headerText = "— 部隊編成 —"
        glyphLayout.setText(titleFont, headerText)
        titleFont.draw(
            batch, headerText,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 40f
        )

        // 出撃人数表示
        val maxDeploy = game.gameProgress.selectedChapter?.maxDeployCount ?: 4
        val currentDeploy = game.gameProgress.party.deployedIds.size
        font.color = if (currentDeploy > 0) Color.CYAN else Color.RED
        val deployText = "出撃メンバー: $currentDeploy / $maxDeploy"
        glyphLayout.setText(font, deployText)
        font.draw(
            batch,
            deployText,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 100f
        )

        batch.end()
    }

    /**
     * ユニットスロット一覧を描画する
     */
    private fun renderUnitSlots() {
        val roster = game.gameProgress.party.roster
        val deployedIds = game.gameProgress.party.deployedIds

        for (i in roster.indices) {
            val unit = roster[i]
            val isDeployed = deployedIds.contains(unit.id)
            val isSelected = unit == selectedUnit
            val slotY = SLOT_START_Y - i * (SLOT_HEIGHT + 10f) + scrollOffset

            renderUnitSlot(unit, SLOT_X, slotY, isDeployed, isSelected)
        }
    }

    /**
     * 1つのユニットスロットを描画する
     *
     * @param unit ユニット
     * @param x スロット左端X
     * @param y スロット上端Y
     * @param isDeployed 出撃選択されているか
     * @param isSelected 詳細表示対象か
     */
    private fun renderUnitSlot(unit: GameUnit, x: Float, y: Float, isDeployed: Boolean, isSelected: Boolean) {
        shapeRenderer.projectionMatrix = viewport.camera.combined

        // スロット背景
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (isDeployed) {
            shapeRenderer.setColor(0.15f, 0.25f, 0.45f, 0.9f)
        } else {
            shapeRenderer.setColor(0.1f, 0.1f, 0.15f, 0.8f)
        }
        shapeRenderer.rect(x, y - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()

        // 選択枠
        if (isSelected) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = Color.GOLD
            shapeRenderer.rect(x, y - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
            shapeRenderer.end()
        }

        // 出撃インジケータ
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (isDeployed) {
            shapeRenderer.setColor(0.2f, 0.9f, 0.3f, 1f)
        } else {
            shapeRenderer.setColor(0.3f, 0.3f, 0.3f, 1f)
        }
        shapeRenderer.circle(x + 40f, y - SLOT_HEIGHT / 2f, 16f)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ユニット情報テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        val textX = x + 80f
        var textY = y - 20f

        // 名前
        font.color = if (isDeployed) Color.WHITE else Color.GRAY
        font.draw(batch, unit.name, textX, textY)
        textY -= 34f

        // 兵種・レベル
        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(batch, "Lv.${unit.level}  ${unit.unitClass.name}", textX, textY)
        textY -= 28f

        // ステータス簡易表示
        smallFont.color = Color(0.7f, 0.7f, 0.7f, 1f)
        val stats = unit.stats
        smallFont.draw(
            batch,
            "HP:${unit.currentHp}/${unit.maxHp}  STR:${stats.effectiveStr}  SPD:${stats.effectiveSpd}  DEF:${stats.effectiveDef}",
            textX, textY
        )

        // 武器名
        val weapon = unit.equippedWeapon()
        if (weapon != null) {
            smallFont.color = Color.GOLD
            smallFont.draw(batch, weapon.name, textX + 550f, y - 24f)
        } else {
            smallFont.color = Color(0.8f, 0.7f, 0.5f, 1f)
            smallFont.draw(batch, "素手", textX + 550f, y - 24f)
        }

        // 防具名
        val armor1 = unit.armorSlot1
        if (armor1 != null) {
            smallFont.color = Color(0.5f, 0.7f, 1f, 1f)
            smallFont.draw(batch, armor1.name, textX + 700f, y - 24f)
        }

        // 作戦表示（スロット右側にコンパクト表示）
        smallFont.color = getTacticColor(unit.tactic)
        smallFont.draw(batch, unit.tactic.displayName, textX + 550f, y - 54f)

        // 出撃状態ラベル
        if (isDeployed) {
            font.color = Color.CYAN
            font.draw(batch, "出撃", x + SLOT_WIDTH - 120f, y - SLOT_HEIGHT / 2f + 14f)
        }

        batch.end()
    }

    /**
     * 選択ユニットの詳細パネルを描画する
     */
    private fun renderDetailPanel() {
        val unit = selectedUnit ?: return

        val panelW = 960f
        val panelH = 500f
        val panelX = GameConfig.VIRTUAL_WIDTH / 2f - panelW / 2f
        val panelY = 170f

        shapeRenderer.projectionMatrix = viewport.camera.combined

        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.8f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.4f, 0.6f, 1f, 1f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // 詳細テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        var textY = panelY + panelH - 24f
        val textX = panelX + 20f
        val lineH = 34f

        font.color = Color(0.4f, 0.6f, 1f, 1f)
        font.draw(batch, unit.name, textX, textY)
        textY -= lineH

        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(batch, "Lv.${unit.level}  ${unit.unitClass.name}", textX, textY)
        textY -= lineH + 8f

        val stats = unit.stats
        // 装備後ステータスの計算
        val armorDef = unit.totalArmorDef()
        val armorRes = unit.totalArmorRes()
        val effSpd = unit.effectiveSpeed()
        val spdDiff = effSpd - stats.effectiveSpd

        // 攻撃力 = STR(物理) or MAG(魔法) + 武器威力
        val weapon = unit.equippedWeapon()
        val weaponMight = weapon?.might ?: 0
        val isMagic = weapon?.type == com.tacticsflame.model.unit.WeaponType.MAGIC
        val atk = if (isMagic) stats.effectiveMag + weaponMight else stats.effectiveStr + weaponMight
        // 総防御力 = DEF + 防具DEF
        val totalDef = stats.effectiveDef + armorDef
        // 総魔防 = RES + 防具RES
        val totalRes = stats.effectiveRes + armorRes

        /** 装備による差分を括弧付きで表示するヘルパー（例: "DEF  11(+2)" や "SPD  12(-7)"） */
        fun withDiff(label: String, base: Int, diff: Int): String =
            if (diff != 0) {
                val sign = if (diff > 0) "+" else ""
                "$label  $base($sign$diff)"
            } else {
                "$label  $base"
            }

        // ステータスを3列で表示（縦画面の横長パネル向けレイアウト）
        val col1X = textX
        val col2X = textX + 300f
        val col3X = textX + 600f
        val statRows = listOf(
            Triple("HP  ${unit.currentHp}/${unit.maxHp}", "SKL  ${stats.effectiveSkl}", "ATK  $atk"),
            Triple("STR  ${stats.effectiveStr}", withDiff("SPD", stats.effectiveSpd, spdDiff), withDiff("DEF", stats.effectiveDef, armorDef)),
            Triple("MAG  ${stats.effectiveMag}", "LCK  ${stats.effectiveLck}", withDiff("RES", stats.effectiveRes, armorRes)),
            Triple("MOV  ${unit.mov}", "", "")
        )

        smallFont.color = Color.WHITE
        for ((c1, c2, c3) in statRows) {
            smallFont.draw(batch, c1, col1X, textY)
            smallFont.draw(batch, c2, col2X, textY)
            smallFont.draw(batch, c3, col3X, textY)
            textY -= 28f
        }

        textY -= 8f
        if (weapon != null) {
            font.color = Color.GOLD
            font.draw(batch, weapon.name, textX, textY)
            textY -= lineH
            smallFont.color = Color.LIGHT_GRAY
            smallFont.draw(
                batch,
                "威力:${weapon.might}  命中:${weapon.hit}  重さ:${weapon.weight}",
                textX, textY
            )
        }

        batch.end()

        // 出撃ボタン
        renderDeployButton(unit)

        // 装備変更ボタン
        renderEquipButton(unit)

        // 作戦変更ボタン
        renderTacticButton(unit)

        // ジョブチェンジボタン
        renderClassChangeButton(unit)
    }

    /**
     * 出撃/非出撃ボタンを描画する
     *
     * @param unit 選択中のユニット
     */
    private fun renderDeployButton(unit: GameUnit) {
        val isDeployed = game.gameProgress.party.deployedIds.contains(unit.id)
        val maxDeploy = game.gameProgress.selectedChapter?.maxDeployCount ?: 4
        val currentDeploy = game.gameProgress.party.deployedIds.size
        val canDeploy = !isDeployed && currentDeploy < maxDeploy

        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (isDeployed) {
            // 「外す」ボタン: 赤系
            shapeRenderer.setColor(0.5f, 0.15f, 0.15f, 0.9f)
        } else if (canDeploy) {
            // 「出撃する」ボタン: 緑系
            shapeRenderer.setColor(0.1f, 0.35f, 0.15f, 0.9f)
        } else {
            // 出撃枠が満杯: グレー
            shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.6f)
        }
        shapeRenderer.rect(deployButtonX, deployButtonY, deployButtonW, deployButtonH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (isDeployed) Color(1f, 0.4f, 0.4f, 1f)
            else if (canDeploy) Color(0.3f, 1f, 0.4f, 1f)
            else Color.DARK_GRAY
        shapeRenderer.rect(deployButtonX, deployButtonY, deployButtonW, deployButtonH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        val label = if (isDeployed) "外す" else "出撃する"
        font.color = if (isDeployed || canDeploy) Color.WHITE else Color.DARK_GRAY
        glyphLayout.setText(font, label)
        font.draw(
            batch, label,
            deployButtonX + deployButtonW / 2f - glyphLayout.width / 2f,
            deployButtonY + deployButtonH / 2f + 12f
        )
        batch.end()
    }

    /**
     * 装備変更ボタンを描画する
     *
     * @param unit 選択中のユニット
     */
    private fun renderEquipButton(unit: GameUnit) {
        val hasEquipOptions = unit.rightHand != null || unit.leftHand != null || game.gameProgress.party.weaponInventory.isNotEmpty()
                || game.gameProgress.party.armorInventory.isNotEmpty()

        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (hasEquipOptions) {
            shapeRenderer.setColor(0.2f, 0.35f, 0.6f, 0.9f)
        } else {
            shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.6f)
        }
        shapeRenderer.rect(equipButtonX, equipButtonY, equipButtonW, equipButtonH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (hasEquipOptions) Color(0.4f, 0.6f, 1f, 1f) else Color.DARK_GRAY
        shapeRenderer.rect(equipButtonX, equipButtonY, equipButtonW, equipButtonH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        font.color = if (hasEquipOptions) Color.WHITE else Color.DARK_GRAY
        val equipLabel = "装備変更"
        glyphLayout.setText(font, equipLabel)
        font.draw(
            batch, equipLabel,
            equipButtonX + equipButtonW / 2f - glyphLayout.width / 2f,
            equipButtonY + equipButtonH / 2f + 12f
        )
        batch.end()
    }

    /**
     * 作戦変更ボタンを描画する
     *
     * @param unit 選択中のユニット
     */
    private fun renderTacticButton(unit: GameUnit) {
        val tacticColor = getTacticColor(unit.tactic)

        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.15f, 0.2f, 0.3f, 0.9f)
        shapeRenderer.rect(tacticButtonX, tacticButtonY, tacticButtonW, tacticButtonH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = tacticColor
        shapeRenderer.rect(tacticButtonX, tacticButtonY, tacticButtonW, tacticButtonH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        font.color = tacticColor
        val tacticLabel = "作戦: ${unit.tactic.displayName}"
        glyphLayout.setText(font, tacticLabel)
        font.draw(
            batch, tacticLabel,
            tacticButtonX + tacticButtonW / 2f - glyphLayout.width / 2f,
            tacticButtonY + tacticButtonH / 2f + 12f
        )
        batch.end()
    }

    /**
     * ジョブチェンジボタンを描画する
     *
     * @param unit 選択中のユニット
     */
    private fun renderClassChangeButton(unit: GameUnit) {
        val canChange = !unit.isLord

        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (canChange) {
            shapeRenderer.setColor(0.35f, 0.2f, 0.5f, 0.9f)
        } else {
            shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.6f)
        }
        shapeRenderer.rect(classChangeButtonX, classChangeButtonY, classChangeButtonW, classChangeButtonH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (canChange) Color(0.7f, 0.4f, 1f, 1f) else Color.DARK_GRAY
        shapeRenderer.rect(classChangeButtonX, classChangeButtonY, classChangeButtonW, classChangeButtonH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        font.color = if (canChange) Color.WHITE else Color.DARK_GRAY
        val label = "ジョブチェンジ"
        glyphLayout.setText(font, label)
        font.draw(
            batch, label,
            classChangeButtonX + classChangeButtonW / 2f - glyphLayout.width / 2f,
            classChangeButtonY + classChangeButtonH / 2f + 12f
        )
        batch.end()
    }

    /**
     * 作戦に対応する表示色を返す
     *
     * @param tactic 作戦種別
     * @return 対応するColor
     */
    private fun getTacticColor(tactic: UnitTactic): Color {
        return when (tactic) {
            UnitTactic.CHARGE -> Color(1f, 0.5f, 0.3f, 1f)   // オレンジ（積極）
            UnitTactic.CAUTIOUS -> Color(0.4f, 0.7f, 1f, 1f)  // 水色（慎重）
            UnitTactic.SUPPORT -> Color(0.4f, 1f, 0.5f, 1f)   // 緑（援護）
            UnitTactic.HEAL -> Color(0.5f, 1f, 0.8f, 1f)      // ミントグリーン（回復）
            UnitTactic.FLEE -> Color(0.9f, 0.8f, 0.3f, 1f)    // 黄（逃走）
        }
    }

    /**
     * 戻るボタンを描画する
     */
    private fun renderBackButton() {
        shapeRenderer.projectionMatrix = viewport.camera.combined

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

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        font.color = Color.WHITE
        font.draw(batch, "← 戻る", backButtonX + 40f, backButtonY + backButtonH / 2f + 12f)
        batch.end()
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
    }
}
