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
import com.tacticsflame.model.unit.GrowthRate
import com.tacticsflame.model.unit.MoveType
import com.tacticsflame.model.unit.UnitClass
import com.tacticsflame.model.unit.WeaponType
import com.tacticsflame.util.FontManager

/**
 * ジョブチェンジ画面
 *
 * ユニットのクラス（兵種）を変更する画面。
 * 全クラス一覧から変更先を選択し、現クラスとの成長率比較を確認したうえで変更を実行する。
 *
 * @param game ゲームインスタンス
 * @param unit ジョブチェンジ対象のユニット
 */
class ClassChangeScreen(
    private val game: TacticsFlameGame,
    private val unit: GameUnit
) : ScreenAdapter() {

    private lateinit var batch: SpriteBatch
    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var titleFont: BitmapFont
    private lateinit var font: BitmapFont
    private lateinit var smallFont: BitmapFont
    private val glyphLayout = GlyphLayout()
    private val viewport = FitViewport(GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)

    /** 全クラス一覧 */
    private val allClasses: List<UnitClass> = UnitClass.ALL.values.toList()

    /** 選択中のクラス（比較表示用） */
    private var selectedClass: UnitClass? = null

    /** 確認ダイアログ表示フラグ */
    private var showConfirmDialog: Boolean = false

    companion object {
        private const val TAG = "ClassChangeScreen"

        /** クラス一覧の1行の高さ */
        private const val ROW_HEIGHT = 100f

        /** クラス一覧の行幅 */
        private const val ROW_WIDTH = 960f

        /** クラス一覧の左端X */
        private const val ROW_X = 60f

        /** クラス一覧の上端Y */
        private const val LIST_TOP = 1360f

        /** クラス一覧の下端Y */
        private const val LIST_BOTTOM = 100f

        /** 情報パネルのY座標 */
        private const val INFO_PANEL_Y = 1400f

        /** 情報パネルの高さ */
        private const val INFO_PANEL_H = 380f

        /** 下部ボタンのY座標 */
        private const val BUTTON_Y = 20f

        /** 下部ボタンの高さ */
        private const val BUTTON_H = 60f
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
        Gdx.app.log(TAG, "ジョブチェンジ画面を表示: ${unit.name}")
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
        renderInfoPanel()
        renderClassList()
        renderBottomButtons()

        if (showConfirmDialog) {
            renderConfirmDialog()
        }
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

        // 確認ダイアログ表示中はダイアログ内のボタンのみ受け付ける
        if (showConfirmDialog) {
            handleConfirmDialogInput(touchX, touchY)
            return
        }

        // 戻るボタン判定（左下）
        val backBtnX = 60f
        val backBtnW = 200f
        if (touchX in backBtnX..(backBtnX + backBtnW) &&
            touchY in BUTTON_Y..(BUTTON_Y + BUTTON_H)
        ) {
            Gdx.app.log(TAG, "編成画面へ戻る")
            game.screenManager.navigateToFormation()
            return
        }

        // 変更実行ボタン判定（右下）
        val execBtnX = GameConfig.VIRTUAL_WIDTH - 60f - 280f
        val execBtnW = 280f
        if (selectedClass != null && isClassSelectable(selectedClass!!) &&
            touchX in execBtnX..(execBtnX + execBtnW) &&
            touchY in BUTTON_Y..(BUTTON_Y + BUTTON_H)
        ) {
            Gdx.app.log(TAG, "変更確認ダイアログ表示: ${selectedClass!!.name}")
            showConfirmDialog = true
            return
        }

        // クラス一覧のタップ判定
        for (i in allClasses.indices) {
            val rowY = LIST_TOP - i * ROW_HEIGHT
            if (rowY - ROW_HEIGHT < LIST_BOTTOM) break
            if (touchX in ROW_X..(ROW_X + ROW_WIDTH) &&
                touchY in (rowY - ROW_HEIGHT)..rowY
            ) {
                val cls = allClasses[i]
                if (isClassSelectable(cls)) {
                    selectedClass = if (selectedClass == cls) null else cls
                    Gdx.app.log(TAG, "クラス選択: ${cls.name}")
                }
                return
            }
        }
    }

    /**
     * 確認ダイアログの入力処理
     *
     * @param touchX タッチX座標（ワールド座標）
     * @param touchY タッチY座標（ワールド座標）
     */
    private fun handleConfirmDialogInput(touchX: Float, touchY: Float) {
        val dialogW = 600f
        val dialogH = 300f
        val dialogX = GameConfig.VIRTUAL_WIDTH / 2f - dialogW / 2f
        val dialogY = GameConfig.VIRTUAL_HEIGHT / 2f - dialogH / 2f

        val btnW = 200f
        val btnH = 60f
        val btnY = dialogY + 30f

        // 「はい」ボタン
        val yesBtnX = dialogX + dialogW / 2f - btnW - 20f
        if (touchX in yesBtnX..(yesBtnX + btnW) && touchY in btnY..(btnY + btnH)) {
            val newClass = selectedClass!!
            val oldClassName = unit.unitClass.name
            Gdx.app.log(TAG, "${unit.name}: クラス変更 $oldClassName → ${newClass.name}")
            unit.changeClass(newClass)
            game.screenManager.navigateToFormation()
            return
        }

        // 「いいえ」ボタン
        val noBtnX = dialogX + dialogW / 2f + 20f
        if (touchX in noBtnX..(noBtnX + btnW) && touchY in btnY..(btnY + btnH)) {
            Gdx.app.log(TAG, "ジョブチェンジキャンセル")
            showConfirmDialog = false
            return
        }
    }

    /**
     * クラスが選択可能かどうかを判定する
     *
     * @param cls 判定対象のクラス
     * @return 選択可能なら true
     */
    private fun isClassSelectable(cls: UnitClass): Boolean {
        // ロードユニット自体はジョブチェンジ不可（defence-in-depth）
        if (unit.isLord) return false
        // 現在のクラスは選択不可
        if (cls.id == unit.unitClass.id) return false
        // ロードクラスは主人公のみ（非主人公は選択不可）
        if (cls.id == "lord") return false
        return true
    }

    // ==================== 描画メソッド ====================

    /**
     * ヘッダーを描画する
     */
    private fun renderHeader() {
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        titleFont.color = Color.WHITE
        val headerText = "— ジョブチェンジ —"
        glyphLayout.setText(titleFont, headerText)
        titleFont.draw(
            batch, headerText,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            1920f
        )

        font.color = Color(0.6f, 0.8f, 1f, 1f)
        val unitInfo = "${unit.name}  Lv.${unit.level}  ${unit.unitClass.name}"
        glyphLayout.setText(font, unitInfo)
        font.draw(
            batch, unitInfo,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            1860f
        )

        batch.end()
    }

    /**
     * 現クラス情報パネル（選択クラスがある場合は比較表示付き）を描画する
     */
    private fun renderInfoPanel() {
        val panelW = 960f
        val panelX = GameConfig.VIRTUAL_WIDTH / 2f - panelW / 2f

        // パネル背景
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.8f)
        shapeRenderer.rect(panelX, INFO_PANEL_Y, panelW, INFO_PANEL_H)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.4f, 0.6f, 1f, 1f)
        shapeRenderer.rect(panelX, INFO_PANEL_Y, panelW, INFO_PANEL_H)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        val currentClass = unit.unitClass
        val leftX = panelX + 20f
        val rightX = panelX + panelW / 2f + 20f
        var textY = INFO_PANEL_Y + INFO_PANEL_H - 30f
        val lineH = 28f

        // --- 左側: 現クラス情報 ---
        font.color = Color(0.4f, 0.6f, 1f, 1f)
        font.draw(batch, "現在: ${currentClass.name}", leftX, textY)

        // 右側: 選択クラス情報（あれば）
        if (selectedClass != null) {
            font.color = Color(1f, 0.8f, 0.3f, 1f)
            font.draw(batch, "変更先: ${selectedClass!!.name}", rightX, textY)
        }
        textY -= lineH + 4f

        // 移動タイプ・MOV
        smallFont.color = Color.WHITE
        smallFont.draw(batch, "移動: ${moveTypeName(currentClass.moveType)}  MOV: ${currentClass.baseMov}", leftX, textY)
        if (selectedClass != null) {
            smallFont.draw(batch, "移動: ${moveTypeName(selectedClass!!.moveType)}  MOV: ${selectedClass!!.baseMov}", rightX, textY)
        }
        textY -= lineH

        // 装備可能武器
        val currentWeapons = currentClass.usableWeapons.joinToString("/") { weaponTypeName(it) }
        smallFont.draw(batch, "武器: $currentWeapons", leftX, textY)
        if (selectedClass != null) {
            val selWeapons = selectedClass!!.usableWeapons.joinToString("/") { weaponTypeName(it) }
            smallFont.draw(batch, "武器: $selWeapons", rightX, textY)
        }
        textY -= lineH

        // 二刀流可否
        smallFont.draw(batch, "二刀流: ${if (currentClass.canDualWield) "可" else "不可"}", leftX, textY)
        if (selectedClass != null) {
            smallFont.draw(batch, "二刀流: ${if (selectedClass!!.canDualWield) "可" else "不可"}", rightX, textY)
        }
        textY -= lineH + 8f

        // 成長率比較
        font.color = Color.GOLD
        font.draw(batch, "クラス成長率", leftX, textY)
        if (selectedClass != null) {
            font.draw(batch, "クラス成長率", rightX, textY)
        }
        textY -= lineH + 2f

        val cg = currentClass.classGrowthRate
        val growthLabels = listOf(
            Pair("HP", { gr: GrowthRate -> gr.hp }),
            Pair("STR", { gr: GrowthRate -> gr.str }),
            Pair("MAG", { gr: GrowthRate -> gr.mag }),
            Pair("SKL", { gr: GrowthRate -> gr.skl }),
            Pair("SPD", { gr: GrowthRate -> gr.spd }),
            Pair("LCK", { gr: GrowthRate -> gr.lck }),
            Pair("DEF", { gr: GrowthRate -> gr.def }),
            Pair("RES", { gr: GrowthRate -> gr.res })
        )

        // 成長率を2列×4行で表示
        val col1Left = leftX
        val col2Left = leftX + 220f
        val col1Right = rightX
        val col2Right = rightX + 220f

        for (row in 0 until 4) {
            for (col in 0..1) {
                val idx = row * 2 + col
                if (idx >= growthLabels.size) break
                val (label, getter) = growthLabels[idx]
                val currentVal = getter(cg)
                val lx = if (col == 0) col1Left else col2Left

                smallFont.color = Color.WHITE
                smallFont.draw(batch, "$label %.2f".format(currentVal), lx, textY)

                if (selectedClass != null) {
                    val sg = selectedClass!!.classGrowthRate
                    val selVal = getter(sg)
                    val rx = if (col == 0) col1Right else col2Right
                    smallFont.color = when {
                        selVal > currentVal -> Color(0.3f, 1f, 0.3f, 1f)
                        selVal < currentVal -> Color(1f, 0.3f, 0.3f, 1f)
                        else -> Color.WHITE
                    }
                    smallFont.draw(batch, "$label %.2f".format(selVal), rx, textY)
                }
            }
            textY -= lineH
        }

        batch.end()
    }

    /**
     * クラス一覧を描画する
     */
    private fun renderClassList() {
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // Filled パス
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (i in allClasses.indices) {
            val rowY = LIST_TOP - i * ROW_HEIGHT
            if (rowY - ROW_HEIGHT < LIST_BOTTOM) break
            renderClassRowFilled(allClasses[i], ROW_X, rowY)
        }
        shapeRenderer.end()

        // Line パス（選択枠のある行のみ）
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        for (i in allClasses.indices) {
            val rowY = LIST_TOP - i * ROW_HEIGHT
            if (rowY - ROW_HEIGHT < LIST_BOTTOM) break
            renderClassRowLine(allClasses[i], ROW_X, rowY)
        }
        shapeRenderer.end()

        Gdx.gl.glDisable(GL20.GL_BLEND)

        // Text パス
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        for (i in allClasses.indices) {
            val rowY = LIST_TOP - i * ROW_HEIGHT
            if (rowY - ROW_HEIGHT < LIST_BOTTOM) break
            renderClassRowText(allClasses[i], ROW_X, rowY)
        }
        batch.end()
    }

    /**
     * クラス行の背景を描画する（Filled パス用）
     */
    private fun renderClassRowFilled(cls: UnitClass, x: Float, y: Float) {
        val isCurrent = cls.id == unit.unitClass.id
        val isLordRestricted = cls.id == "lord" && !unit.isLord
        val selectable = isClassSelectable(cls)
        val isSelected = cls == selectedClass

        when {
            isSelected -> shapeRenderer.setColor(0.25f, 0.15f, 0.4f, 0.9f)
            isCurrent -> shapeRenderer.setColor(0.15f, 0.15f, 0.15f, 0.7f)
            isLordRestricted -> shapeRenderer.setColor(0.12f, 0.12f, 0.12f, 0.7f)
            selectable -> shapeRenderer.setColor(0.1f, 0.1f, 0.18f, 0.8f)
            else -> shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.7f)
        }
        shapeRenderer.rect(x, y - ROW_HEIGHT, ROW_WIDTH, ROW_HEIGHT)
    }

    /**
     * クラス行の選択枠を描画する（Line パス用）
     */
    private fun renderClassRowLine(cls: UnitClass, x: Float, y: Float) {
        if (cls == selectedClass) {
            shapeRenderer.color = Color(0.7f, 0.4f, 1f, 1f)
            shapeRenderer.rect(x, y - ROW_HEIGHT, ROW_WIDTH, ROW_HEIGHT)
        }
    }

    /**
     * クラス行のテキストを描画する（SpriteBatch パス用）
     */
    private fun renderClassRowText(cls: UnitClass, x: Float, y: Float) {
        val isCurrent = cls.id == unit.unitClass.id
        val isLordRestricted = cls.id == "lord" && !unit.isLord
        val selectable = isClassSelectable(cls)
        val isSelected = cls == selectedClass

        val textX = x + 20f
        var textY = y - 24f

        // クラス名
        font.color = when {
            isCurrent -> Color.GRAY
            isLordRestricted -> Color.GRAY
            isSelected -> Color(0.7f, 0.4f, 1f, 1f)
            selectable -> Color.WHITE
            else -> Color.GRAY
        }
        font.draw(batch, cls.name, textX, textY)

        // 移動タイプ・MOV
        smallFont.color = if (selectable || isSelected) Color.LIGHT_GRAY else Color.DARK_GRAY
        smallFont.draw(batch, "${moveTypeName(cls.moveType)}  MOV:${cls.baseMov}", textX + 280f, textY)

        textY -= 32f

        // 装備可能武器
        val weaponsStr = cls.usableWeapons.joinToString("/") { weaponTypeName(it) }
        smallFont.color = if (selectable || isSelected) Color(0.8f, 0.8f, 0.6f, 1f) else Color.DARK_GRAY
        smallFont.draw(batch, weaponsStr, textX, textY)

        // 状態ラベル（右端）
        val statusLabel = when {
            isCurrent -> "現在"
            isLordRestricted -> "主人公専用"
            else -> ""
        }
        if (statusLabel.isNotEmpty()) {
            smallFont.color = Color.GRAY
            glyphLayout.setText(smallFont, statusLabel)
            smallFont.draw(batch, statusLabel, x + ROW_WIDTH - glyphLayout.width - 20f, y - 50f)
        }
    }

    /**
     * 下部ボタン（戻る・変更実行）を描画する
     */
    private fun renderBottomButtons() {
        // 戻るボタン
        val backBtnX = 60f
        val backBtnW = 200f

        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.4f, 0.2f, 0.2f, 0.9f)
        shapeRenderer.rect(backBtnX, BUTTON_Y, backBtnW, BUTTON_H)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(1f, 0.5f, 0.5f, 1f)
        shapeRenderer.rect(backBtnX, BUTTON_Y, backBtnW, BUTTON_H)
        shapeRenderer.end()

        // 変更実行ボタン
        val execBtnX = GameConfig.VIRTUAL_WIDTH - 60f - 280f
        val execBtnW = 280f
        val canExec = selectedClass != null && isClassSelectable(selectedClass!!)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (canExec) {
            shapeRenderer.setColor(0.35f, 0.2f, 0.5f, 0.9f)
        } else {
            shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.6f)
        }
        shapeRenderer.rect(execBtnX, BUTTON_Y, execBtnW, BUTTON_H)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (canExec) Color(0.7f, 0.4f, 1f, 1f) else Color.DARK_GRAY
        shapeRenderer.rect(execBtnX, BUTTON_Y, execBtnW, BUTTON_H)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ボタンテキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        font.color = Color.WHITE
        font.draw(batch, "← 戻る", backBtnX + 40f, BUTTON_Y + BUTTON_H / 2f + 12f)

        font.color = if (canExec) Color.WHITE else Color.DARK_GRAY
        val execLabel = "変更実行"
        glyphLayout.setText(font, execLabel)
        font.draw(
            batch, execLabel,
            execBtnX + execBtnW / 2f - glyphLayout.width / 2f,
            BUTTON_Y + BUTTON_H / 2f + 12f
        )

        batch.end()
    }

    /**
     * 確認ダイアログを描画する
     */
    private fun renderConfirmDialog() {
        val dialogW = 600f
        val dialogH = 300f
        val dialogX = GameConfig.VIRTUAL_WIDTH / 2f - dialogW / 2f
        val dialogY = GameConfig.VIRTUAL_HEIGHT / 2f - dialogH / 2f

        // 半透明オーバーレイ
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.6f)
        shapeRenderer.rect(0f, 0f, GameConfig.VIRTUAL_WIDTH, GameConfig.VIRTUAL_HEIGHT)
        shapeRenderer.end()

        // ダイアログ背景
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.1f, 0.1f, 0.2f, 0.95f)
        shapeRenderer.rect(dialogX, dialogY, dialogW, dialogH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.7f, 0.4f, 1f, 1f)
        shapeRenderer.rect(dialogX, dialogY, dialogW, dialogH)
        shapeRenderer.end()

        // ボタン
        val btnW = 200f
        val btnH = 60f
        val btnY = dialogY + 30f
        val yesBtnX = dialogX + dialogW / 2f - btnW - 20f
        val noBtnX = dialogX + dialogW / 2f + 20f

        // 「はい」ボタン
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.35f, 0.2f, 0.5f, 0.9f)
        shapeRenderer.rect(yesBtnX, btnY, btnW, btnH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.7f, 0.4f, 1f, 1f)
        shapeRenderer.rect(yesBtnX, btnY, btnW, btnH)
        shapeRenderer.end()

        // 「いいえ」ボタン
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.3f, 0.15f, 0.15f, 0.9f)
        shapeRenderer.rect(noBtnX, btnY, btnW, btnH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(1f, 0.4f, 0.4f, 1f)
        shapeRenderer.rect(noBtnX, btnY, btnW, btnH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        font.color = Color.WHITE
        val msg1 = "${unit.name} のクラスを"
        glyphLayout.setText(font, msg1)
        font.draw(batch, msg1, dialogX + dialogW / 2f - glyphLayout.width / 2f, dialogY + dialogH - 60f)

        font.color = Color(0.7f, 0.4f, 1f, 1f)
        val msg2 = "${selectedClass!!.name} に変更しますか？"
        glyphLayout.setText(font, msg2)
        font.draw(batch, msg2, dialogX + dialogW / 2f - glyphLayout.width / 2f, dialogY + dialogH - 100f)

        // ボタンラベル
        font.color = Color.WHITE
        val yesLabel = "はい"
        glyphLayout.setText(font, yesLabel)
        font.draw(batch, yesLabel, yesBtnX + btnW / 2f - glyphLayout.width / 2f, btnY + btnH / 2f + 12f)

        font.color = Color.WHITE
        val noLabel = "いいえ"
        glyphLayout.setText(font, noLabel)
        font.draw(batch, noLabel, noBtnX + btnW / 2f - glyphLayout.width / 2f, btnY + btnH / 2f + 12f)

        batch.end()
    }

    // ==================== ユーティリティ ====================

    /**
     * 武器タイプを日本語名に変換する
     *
     * @param type 武器タイプ
     * @return 日本語名
     */
    private fun weaponTypeName(type: WeaponType): String = when (type) {
        WeaponType.SWORD -> "剣"
        WeaponType.LANCE -> "槍"
        WeaponType.AXE   -> "斧"
        WeaponType.BOW   -> "弓"
        WeaponType.MAGIC -> "魔法"
        WeaponType.STAFF -> "杖"
    }

    /**
     * 移動タイプを日本語名に変換する
     *
     * @param type 移動タイプ
     * @return 日本語名
     */
    private fun moveTypeName(type: MoveType): String = when (type) {
        MoveType.INFANTRY -> "歩兵"
        MoveType.CAVALRY  -> "騎馬"
        MoveType.FLYING   -> "飛行"
        MoveType.ARMORED  -> "重装"
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
