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
import com.tacticsflame.model.unit.Weapon
import com.tacticsflame.model.unit.WeaponType
import com.tacticsflame.util.FontManager

/**
 * 武器装備変更画面
 *
 * ユニットの所持武器一覧を表示し、装備する武器を選択・変更する。
 *
 * 操作フロー:
 * - 武器スロットをタップ → その武器を選択（プレビュー表示）
 * - 「装備する」ボタンをタップ → 選択武器を装備（リスト先頭に移動）
 * - 「← 戻る」ボタンをタップ → 部隊編成画面へ戻る
 *
 * @param game ゲームインスタンス
 * @param unit 武器を変更するユニット
 */
class WeaponEquipScreen(
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

    /**
     * 選択中（プレビュー）の武器インデックス
     * -1 = 未選択、0 = 装備中（装備変更不要）、>0 = 変更候補
     */
    private var selectedIndex: Int = -1

    // ==================== レイアウト定数 ====================

    companion object {
        private const val TAG = "WeaponEquipScreen"

        /** 武器スロット1つの高さ */
        private const val SLOT_HEIGHT = 140f

        /** 武器スロットの幅 */
        private const val SLOT_WIDTH = 960f

        /** スロット左端X座標 */
        private const val SLOT_X = 60f

        /** 武器リスト先頭スロットの上端Y座標 */
        private const val SLOT_START_Y = 1420f
    }

    /** 戻るボタン領域 */
    private val backButtonX = 80f
    private val backButtonY = 80f
    private val backButtonW = 200f
    private val backButtonH = 70f

    /** 「装備する」確定ボタン領域 */
    private val confirmButtonX = GameConfig.VIRTUAL_WIDTH - 360f
    private val confirmButtonY = 80f
    private val confirmButtonW = 280f
    private val confirmButtonH = 70f

    // ==================== ライフサイクル ====================

    /**
     * 画面表示時の初期化処理
     */
    override fun show() {
        batch = SpriteBatch()
        shapeRenderer = ShapeRenderer()
        titleFont = FontManager.getFont(size = 48)
        font = FontManager.getFont(size = 32)
        smallFont = FontManager.getFont(size = 24)
        Gdx.app.log(TAG, "武器装備変更画面を表示: ${unit.name}")
    }

    /**
     * フレーム描画処理
     *
     * @param delta 前フレームからの経過時間（秒）
     */
    override fun render(delta: Float) {
        handleInput()

        Gdx.gl.glClearColor(0.06f, 0.08f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()

        renderHeader()
        renderCurrentEquipPanel()
        renderWeaponList()
        renderComparisonPanel()
        renderBottomButtons()
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

    // ==================== 入力処理 ====================

    /**
     * タッチ入力を処理する
     */
    private fun handleInput() {
        if (!Gdx.input.justTouched()) return

        val worldCoord = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
        val tx = worldCoord.x
        val ty = worldCoord.y

        // 戻るボタン
        if (tx in backButtonX..(backButtonX + backButtonW) &&
            ty in backButtonY..(backButtonY + backButtonH)
        ) {
            Gdx.app.log(TAG, "部隊編成画面へ戻る")
            game.screenManager.navigateToFormation()
            return
        }

        // 「装備する」確定ボタン（選択中の武器がある場合）
        if (selectedIndex > 0 &&
            tx in confirmButtonX..(confirmButtonX + confirmButtonW) &&
            ty in confirmButtonY..(confirmButtonY + confirmButtonH)
        ) {
            val weapon = unit.weapons[selectedIndex]
            unit.equipWeapon(weapon)
            Gdx.app.log(TAG, "${unit.name}: 武器を装備 → ${weapon.name}")
            selectedIndex = 0  // 装備後はリスト先頭になる
            return
        }

        // 武器スロットのタップ
        for (i in unit.weapons.indices) {
            val slotTopY = SLOT_START_Y - i * (SLOT_HEIGHT + 10f)
            if (tx in SLOT_X..(SLOT_X + SLOT_WIDTH) &&
                ty in (slotTopY - SLOT_HEIGHT)..slotTopY
            ) {
                // 同じスロットを再タップで選択解除
                selectedIndex = if (selectedIndex == i) -1 else i
                return
            }
        }

        // その他の場所をタップで選択解除
        selectedIndex = -1
    }

    // ==================== 描画メソッド ====================

    /**
     * ヘッダー（タイトル・ユニット情報）を描画する
     */
    private fun renderHeader() {
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        // 画面タイトル
        titleFont.color = Color.WHITE
        val title = "— 武器装備変更 —"
        glyphLayout.setText(titleFont, title)
        titleFont.draw(
            batch, title,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 40f
        )

        // ユニット名・レベル・兵種
        font.color = Color.CYAN
        val unitInfo = "${unit.name}  Lv.${unit.level}  ${unit.unitClass.name}"
        glyphLayout.setText(font, unitInfo)
        font.draw(
            batch, unitInfo,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 110f
        )

        batch.end()
    }

    /**
     * 現在の装備パネルを描画する
     */
    private fun renderCurrentEquipPanel() {
        val panelX = SLOT_X
        val panelY = 1490f
        val panelW = SLOT_WIDTH
        val panelH = 190f

        // パネル背景
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.05f, 0.18f, 0.06f, 0.85f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.3f, 0.9f, 0.3f, 1f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        val textX = panelX + 20f
        var textY = panelY + panelH - 24f

        smallFont.color = Color(0.5f, 1f, 0.5f, 1f)
        smallFont.draw(batch, "【現在の装備】", textX, textY)
        textY -= 40f

        val equipped = unit.equippedWeapon()
        if (equipped != null) {
            font.color = Color.WHITE
            font.draw(batch, equipped.name, textX, textY)
            smallFont.color = Color(0.6f, 0.8f, 1f, 1f)
            smallFont.draw(batch, weaponTypeLabel(equipped.type), textX + 340f, textY - 4f)
            textY -= 40f

            smallFont.color = Color.LIGHT_GRAY
            smallFont.draw(
                batch,
                "威力:${equipped.might}  命中:${equipped.hit}  必殺:${equipped.critical}  重さ:${equipped.weight}  射程:${equipped.minRange}〜${equipped.maxRange}",
                textX, textY
            )
        } else {
            font.color = Color.GRAY
            font.draw(batch, "（なし）", textX, textY)
        }

        batch.end()
    }

    /**
     * 所持武器リストを描画する
     */
    private fun renderWeaponList() {
        // セクションラベル
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        smallFont.color = Color(0.7f, 0.7f, 1f, 1f)
        smallFont.draw(batch, "【所持武器】", SLOT_X, SLOT_START_Y + 32f)
        batch.end()

        if (unit.weapons.isEmpty()) {
            batch.projectionMatrix = viewport.camera.combined
            batch.begin()
            font.color = Color.GRAY
            font.draw(batch, "所持武器がありません", SLOT_X + 20f, SLOT_START_Y - 40f)
            batch.end()
            return
        }

        for (i in unit.weapons.indices) {
            val weapon = unit.weapons[i]
            val slotTopY = SLOT_START_Y - i * (SLOT_HEIGHT + 10f)
            renderWeaponSlot(
                weapon = weapon,
                slotTopY = slotTopY,
                isEquipped = (i == 0),
                isSelected = (i == selectedIndex)
            )
        }
    }

    /**
     * 武器スロットを1つ描画する
     *
     * @param weapon 武器データ
     * @param slotTopY スロット上端Y座標
     * @param isEquipped 装備中かどうか
     * @param isSelected 選択中（プレビュー）かどうか
     */
    private fun renderWeaponSlot(
        weapon: Weapon,
        slotTopY: Float,
        isEquipped: Boolean,
        isSelected: Boolean
    ) {
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // スロット背景
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        when {
            isEquipped  -> shapeRenderer.setColor(0.12f, 0.20f, 0.05f, 0.90f)
            isSelected  -> shapeRenderer.setColor(0.12f, 0.12f, 0.28f, 0.90f)
            else        -> shapeRenderer.setColor(0.08f, 0.08f, 0.12f, 0.85f)
        }
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()

        // 枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = when {
            isEquipped  -> Color.GOLD
            isSelected  -> Color(0.5f, 0.5f, 1f, 1f)
            else        -> Color(0.3f, 0.3f, 0.3f, 0.6f)
        }
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        val textX = SLOT_X + 20f
        var textY = slotTopY - 22f

        // 武器名
        font.color = when {
            isEquipped  -> Color.GOLD
            isSelected  -> Color(0.8f, 0.8f, 1f, 1f)
            else        -> Color.WHITE
        }
        font.draw(batch, weapon.name, textX, textY)
        textY -= 38f

        // 武器タイプ
        smallFont.color = Color(0.5f, 0.8f, 1f, 1f)
        smallFont.draw(batch, weaponTypeLabel(weapon.type), textX, textY)

        // ステータス
        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(
            batch,
            "威力:${weapon.might}  命中:${weapon.hit}  必殺:${weapon.critical}  重さ:${weapon.weight}  射程:${weapon.minRange}〜${weapon.maxRange}",
            textX + 80f, textY
        )

        // 装備中バッジ
        if (isEquipped) {
            font.color = Color(0.3f, 1f, 0.3f, 1f)
            font.draw(batch, "装備中", SLOT_X + SLOT_WIDTH - 140f, slotTopY - SLOT_HEIGHT / 2f + 14f)
        }

        batch.end()
    }

    /**
     * 比較パネルを描画する
     *
     * 選択中の武器がある場合に、現在の装備との差分（威力・命中・必殺・重さ）を表示する。
     * 改善項目は緑、悪化項目は赤で表示する。
     */
    private fun renderComparisonPanel() {
        val idx = selectedIndex
        // 装備中（idx=0）か未選択の場合は比較不要
        if (idx <= 0 || idx >= unit.weapons.size) return

        val current = unit.equippedWeapon() ?: return
        val preview = unit.weapons[idx]

        val panelX = SLOT_X
        val panelY = 180f
        val panelW = SLOT_WIDTH
        val panelH = 230f

        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0.1f, 0.88f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.5f, 0.5f, 1f, 1f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        var textY = panelY + panelH - 24f
        val textX = panelX + 20f

        // ヘッダー
        smallFont.color = Color(0.6f, 0.6f, 1f, 1f)
        smallFont.draw(batch, "【比較】 ${current.name}  →  ${preview.name}", textX, textY)
        textY -= 42f

        // 各ステータスの差分
        data class StatComp(val label: String, val cur: Int, val nxt: Int, val lowerIsBetter: Boolean = false)
        val stats = listOf(
            StatComp("威力", current.might, preview.might),
            StatComp("命中", current.hit, preview.hit),
            StatComp("必殺", current.critical, preview.critical),
            StatComp("重さ", current.weight, preview.weight, lowerIsBetter = true),
            StatComp("射程", current.maxRange, preview.maxRange)
        )

        val colW = (panelW - 40f) / stats.size
        var colX = textX
        for (stat in stats) {
            val diff = stat.nxt - stat.cur
            val isBetter = if (stat.lowerIsBetter) diff < 0 else diff > 0

            // ラベル
            smallFont.color = Color.GRAY
            glyphLayout.setText(smallFont, stat.label)
            smallFont.draw(batch, stat.label, colX + colW / 2f - glyphLayout.width / 2f, textY)

            // 現在値
            val curStr = stat.cur.toString()
            smallFont.color = Color.LIGHT_GRAY
            glyphLayout.setText(smallFont, curStr)
            smallFont.draw(batch, curStr, colX + colW / 2f - glyphLayout.width / 2f, textY - 32f)

            // 差分表示（矢印付き）
            val diffStr = when {
                diff > 0 -> "+$diff ▲"
                diff < 0 -> "$diff ▼"
                else -> "─"
            }
            smallFont.color = when {
                diff == 0   -> Color(0.5f, 0.5f, 0.5f, 1f)
                isBetter    -> Color(0.2f, 1f, 0.3f, 1f)
                else        -> Color(1f, 0.35f, 0.35f, 1f)
            }
            glyphLayout.setText(smallFont, diffStr)
            smallFont.draw(batch, diffStr, colX + colW / 2f - glyphLayout.width / 2f, textY - 66f)

            colX += colW
        }

        batch.end()
    }

    /**
     * 下部ボタン群（「← 戻る」「装備する」）を描画する
     */
    private fun renderBottomButtons() {
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // 戻るボタン（背景）
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.4f, 0.2f, 0.2f, 0.9f)
        shapeRenderer.rect(backButtonX, backButtonY, backButtonW, backButtonH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(1f, 0.5f, 0.5f, 1f)
        shapeRenderer.rect(backButtonX, backButtonY, backButtonW, backButtonH)
        shapeRenderer.end()

        // 「装備する」ボタン（選択中の武器がある場合のみ表示）
        if (selectedIndex > 0) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(0.15f, 0.25f, 0.55f, 0.92f)
            shapeRenderer.rect(confirmButtonX, confirmButtonY, confirmButtonW, confirmButtonH)
            shapeRenderer.end()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = Color(0.4f, 0.6f, 1f, 1f)
            shapeRenderer.rect(confirmButtonX, confirmButtonY, confirmButtonW, confirmButtonH)
            shapeRenderer.end()
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        font.color = Color.WHITE
        font.draw(batch, "← 戻る", backButtonX + 24f, backButtonY + backButtonH / 2f + 12f)

        if (selectedIndex > 0) {
            font.color = Color.WHITE
            val confirmLabel = "装備する"
            glyphLayout.setText(font, confirmLabel)
            font.draw(
                batch, confirmLabel,
                confirmButtonX + confirmButtonW / 2f - glyphLayout.width / 2f,
                confirmButtonY + confirmButtonH / 2f + 12f
            )
        }

        batch.end()
    }

    // ==================== ユーティリティ ====================

    /**
     * 武器タイプの日本語ラベルを返す
     *
     * @param type 武器タイプ
     * @return 日本語ラベル
     */
    private fun weaponTypeLabel(type: WeaponType): String = when (type) {
        WeaponType.SWORD  -> "剣"
        WeaponType.LANCE  -> "槍"
        WeaponType.AXE    -> "斧"
        WeaponType.BOW    -> "弓"
        WeaponType.MAGIC  -> "魔法"
        WeaponType.STAFF  -> "杖"
    }
}
