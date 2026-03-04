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
import com.tacticsflame.model.unit.Armor
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.Weapon
import com.tacticsflame.model.unit.WeaponType
import com.tacticsflame.model.unit.ArmorType
import com.tacticsflame.util.FontManager

/**
 * 武器・防具装備変更画面
 *
 * ユニットの所持武器とパーティ在庫を表示し、装備を選択・変更する。
 *
 * 操作フロー:
 * - ユニットの武器をタップ → 選択（装備変更 or 在庫へ返却）
 * - パーティ在庫の武器をタップ → 受け取り・装備
 * - パーティ在庫の防具をタップ → 装備（現在の防具と入れ替え）
 * - 「← 戻る」ボタン → 部隊編成画面へ
 *
 * @param game ゲームインスタンス
 * @param unit 装備を変更するユニット
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

    /** 選択種別 */
    private enum class SelectionType { NONE, UNIT_WEAPON, INV_WEAPON, INV_ARMOR }

    /** 現在の選択種別 */
    private var selType: SelectionType = SelectionType.NONE

    /** ユニット武器の選択インデックス */
    private var selUnitWeaponIdx: Int = -1

    /** 在庫武器の選択インデックス */
    private var selInvWeaponIdx: Int = -1

    /** 在庫防具の選択インデックス */
    private var selInvArmorIdx: Int = -1

    /** スクロールオフセット */
    private var scrollOffset: Float = 0f

    /** タッチ開始時のワールド座標 */
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f

    /** 前フレームのタッチY座標（スクロール用） */
    private var prevTouchY: Float = -1f

    /** タッチ中のドラッグ累計距離 */
    private var dragDist: Float = 0f

    /** タッチ中フラグ */
    private var wasTouching: Boolean = false

    // ==================== レイアウト定数 ====================

    companion object {
        private const val TAG = "WeaponEquipScreen"

        /** スロット1つの高さ */
        private const val SLOT_HEIGHT = 105f

        /** スロット間ギャップ */
        private const val SLOT_GAP = 6f

        /** スロットの幅 */
        private const val SLOT_WIDTH = 960f

        /** スロット左端 */
        private const val SLOT_X = 60f

        /** リスト領域の上端Y */
        private const val LIST_TOP = 1280f

        /** リスト領域の下端Y */
        private const val LIST_BOTTOM = 170f

        /** セクションラベル高さ */
        private const val LABEL_H = 34f

        /** タップ判定閾値 */
        private const val TAP_THRESHOLD = 12f
    }

    /** 戻るボタン */
    private val backBtnX = 40f
    private val backBtnY = 80f
    private val backBtnW = 180f
    private val backBtnH = 65f

    /** アクションボタン（右） */
    private val actBtnX = GameConfig.VIRTUAL_WIDTH - 320f
    private val actBtnY = 80f
    private val actBtnW = 280f
    private val actBtnH = 65f

    /** サブアクションボタン（中央） */
    private val subBtnX = 240f
    private val subBtnY = 80f
    private val subBtnW = 240f
    private val subBtnH = 65f

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
        Gdx.app.log(TAG, "装備変更画面を表示: ${unit.name}")
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
        renderArmorPanel()
        renderListArea()
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
     * タッチ入力を処理する（スクロール＋タップ）
     */
    private fun handleInput() {
        val isTouching = Gdx.input.isTouched
        val world = viewport.unproject(Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat()))
        val tx = world.x
        val ty = world.y

        if (isTouching) {
            if (!wasTouching) {
                // タッチ開始
                touchStartX = tx
                touchStartY = ty
                prevTouchY = ty
                dragDist = 0f
            } else if (prevTouchY >= 0 && ty in LIST_BOTTOM..LIST_TOP) {
                // ドラッグ中（リスト領域）
                val dy = ty - prevTouchY
                dragDist += Math.abs(dy)
                scrollOffset += dy
                clampScroll()
                prevTouchY = ty
            }
            wasTouching = true
        } else if (wasTouching) {
            // タッチ終了
            wasTouching = false
            if (dragDist < TAP_THRESHOLD) {
                // タップとして処理
                handleTap(touchStartX, touchStartY)
            }
            prevTouchY = -1f
            dragDist = 0f
        }
    }

    /**
     * タップ処理
     */
    private fun handleTap(tx: Float, ty: Float) {
        // 戻るボタン
        if (tx in backBtnX..(backBtnX + backBtnW) && ty in backBtnY..(backBtnY + backBtnH)) {
            Gdx.app.log(TAG, "部隊編成画面へ戻る")
            game.screenManager.navigateToFormation()
            return
        }

        // アクションボタン（右）
        if (tx in actBtnX..(actBtnX + actBtnW) && ty in actBtnY..(actBtnY + actBtnH)) {
            handleActionButton()
            return
        }

        // サブアクションボタン（中央）
        if (tx in subBtnX..(subBtnX + subBtnW) && ty in subBtnY..(subBtnY + subBtnH)) {
            handleSubActionButton()
            return
        }

        // リスト領域のスロットタップ
        if (ty in LIST_BOTTOM..LIST_TOP && tx in SLOT_X..(SLOT_X + SLOT_WIDTH)) {
            handleSlotTap(ty)
            return
        }

        // 何もない場所 → 選択解除
        clearSelection()
    }

    /**
     * リスト内スロットのタップ判定
     */
    private fun handleSlotTap(tapY: Float) {
        var y = LIST_TOP + scrollOffset

        // ユニット武器セクション
        y -= LABEL_H
        for (i in unit.weapons.indices) {
            val top = y
            val bottom = y - SLOT_HEIGHT
            y -= (SLOT_HEIGHT + SLOT_GAP)
            if (tapY in bottom..top) {
                toggleSelect(SelectionType.UNIT_WEAPON, i)
                return
            }
        }
        if (unit.weapons.isEmpty()) y -= 36f

        // パーティ在庫: 武器セクション
        val invW = game.gameProgress.party.weaponInventory
        if (invW.isNotEmpty()) {
            y -= (LABEL_H + 8f)
            for (i in invW.indices) {
                val top = y
                val bottom = y - SLOT_HEIGHT
                y -= (SLOT_HEIGHT + SLOT_GAP)
                if (tapY in bottom..top) {
                    toggleSelect(SelectionType.INV_WEAPON, i)
                    return
                }
            }
        }

        // パーティ在庫: 防具セクション
        val invA = game.gameProgress.party.armorInventory
        if (invA.isNotEmpty()) {
            y -= (LABEL_H + 8f)
            for (i in invA.indices) {
                val top = y
                val bottom = y - SLOT_HEIGHT
                y -= (SLOT_HEIGHT + SLOT_GAP)
                if (tapY in bottom..top) {
                    toggleSelect(SelectionType.INV_ARMOR, i)
                    return
                }
            }
        }

        clearSelection()
    }

    // ==================== 選択管理 ====================

    /**
     * 選択をトグルする（同じアイテムを再タップで解除）
     */
    private fun toggleSelect(type: SelectionType, idx: Int) {
        val isSame = when (type) {
            SelectionType.UNIT_WEAPON -> selType == type && selUnitWeaponIdx == idx
            SelectionType.INV_WEAPON -> selType == type && selInvWeaponIdx == idx
            SelectionType.INV_ARMOR -> selType == type && selInvArmorIdx == idx
            else -> false
        }
        if (isSame) {
            clearSelection()
        } else {
            selType = type
            selUnitWeaponIdx = if (type == SelectionType.UNIT_WEAPON) idx else -1
            selInvWeaponIdx = if (type == SelectionType.INV_WEAPON) idx else -1
            selInvArmorIdx = if (type == SelectionType.INV_ARMOR) idx else -1
        }
    }

    /**
     * 選択状態をクリアする
     */
    private fun clearSelection() {
        selType = SelectionType.NONE
        selUnitWeaponIdx = -1
        selInvWeaponIdx = -1
        selInvArmorIdx = -1
    }

    // ==================== アクションボタン ====================

    /**
     * メインアクションボタンの処理
     */
    private fun handleActionButton() {
        when (selType) {
            SelectionType.UNIT_WEAPON -> {
                // 装備する（先頭に移動）
                val idx = selUnitWeaponIdx
                if (idx > 0 && idx < unit.weapons.size) {
                    val w = unit.weapons[idx]
                    unit.equipWeapon(w)
                    Gdx.app.log(TAG, "${unit.name}: 武器を装備 → ${w.name}")
                    selUnitWeaponIdx = 0
                }
            }
            SelectionType.INV_WEAPON -> {
                // 在庫から受け取る
                val inv = game.gameProgress.party.weaponInventory
                val idx = selInvWeaponIdx
                if (idx in inv.indices) {
                    val w = inv[idx]
                    game.gameProgress.party.giveWeaponToUnit(w, unit)
                    Gdx.app.log(TAG, "${unit.name}: 武器を受け取り → ${w.name}")
                    clearSelection()
                }
            }
            SelectionType.INV_ARMOR -> {
                // 防具を装備する
                val inv = game.gameProgress.party.armorInventory
                val idx = selInvArmorIdx
                if (idx in inv.indices) {
                    val a = inv[idx]
                    game.gameProgress.party.giveArmorToUnit(a, unit)
                    Gdx.app.log(TAG, "${unit.name}: 防具を装備 → ${a.name}")
                    clearSelection()
                }
            }
            else -> {}
        }
    }

    /**
     * サブアクションボタンの処理（返却/防具外す）
     */
    private fun handleSubActionButton() {
        when (selType) {
            SelectionType.UNIT_WEAPON -> {
                // 武器を在庫に返却
                val idx = selUnitWeaponIdx
                if (idx >= 0 && idx < unit.weapons.size) {
                    val w = unit.weapons[idx]
                    game.gameProgress.party.returnWeaponFromUnit(w, unit)
                    Gdx.app.log(TAG, "${unit.name}: 武器を返却 → ${w.name}")
                    clearSelection()
                }
            }
            else -> {
                // 防具を外す
                if (unit.equippedArmor != null) {
                    val name = unit.equippedArmor!!.name
                    game.gameProgress.party.returnArmorFromUnit(unit)
                    Gdx.app.log(TAG, "${unit.name}: 防具を外す → $name")
                }
            }
        }
    }

    // ==================== スクロール ====================

    /**
     * スクロールオフセットを有効範囲にクランプする
     */
    private fun clampScroll() {
        val contentH = calcContentHeight()
        val viewH = LIST_TOP - LIST_BOTTOM
        val maxScroll = maxOf(0f, contentH - viewH)
        scrollOffset = scrollOffset.coerceIn(-maxScroll, 0f)
    }

    /**
     * リストコンテンツの総高さを計算する
     */
    private fun calcContentHeight(): Float {
        var h = LABEL_H  // ユニット武器ラベル
        val weaponCount = if (unit.weapons.isEmpty()) 1 else unit.weapons.size
        h += weaponCount * (SLOT_HEIGHT + SLOT_GAP)

        val invW = game.gameProgress.party.weaponInventory
        if (invW.isNotEmpty()) {
            h += LABEL_H + 8f + invW.size * (SLOT_HEIGHT + SLOT_GAP)
        }

        val invA = game.gameProgress.party.armorInventory
        if (invA.isNotEmpty()) {
            h += LABEL_H + 8f + invA.size * (SLOT_HEIGHT + SLOT_GAP)
        }

        return h
    }

    // ==================== 描画メソッド ====================

    /**
     * ヘッダー（タイトル・ユニット情報）を描画する
     */
    private fun renderHeader() {
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        titleFont.color = Color.WHITE
        val title = "— 装備変更 —"
        glyphLayout.setText(titleFont, title)
        titleFont.draw(
            batch, title,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 40f
        )

        font.color = Color.CYAN
        val info = "${unit.name}  Lv.${unit.level}  ${unit.unitClass.name}"
        glyphLayout.setText(font, info)
        font.draw(
            batch, info,
            GameConfig.VIRTUAL_WIDTH / 2f - glyphLayout.width / 2f,
            GameConfig.VIRTUAL_HEIGHT - 110f
        )

        batch.end()
    }

    /**
     * 現在の武器パネルを描画する
     */
    private fun renderCurrentEquipPanel() {
        val panelX = SLOT_X
        val panelY = 1490f
        val panelW = SLOT_WIDTH
        val panelH = 190f

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
            font.color = Color(0.8f, 0.7f, 0.5f, 1f)
            font.draw(batch, "素手", textX, textY)
            textY -= 40f
            smallFont.color = Color.LIGHT_GRAY
            smallFont.draw(batch, "威力:0  命中:80  必殺:0  重さ:0  射程:1〜1", textX, textY)
        }

        textY -= 36f
        smallFont.color = Color(0.8f, 0.9f, 0.4f, 1f)
        smallFont.draw(batch, "実効速度: ${unit.effectiveSpeed()}  (SPD ${unit.stats.spd})", textX, textY)

        batch.end()
    }

    /**
     * 防具情報パネルを描画する
     */
    private fun renderArmorPanel() {
        val panelX = SLOT_X
        val panelY = 1290f
        val panelW = SLOT_WIDTH
        val panelH = 100f

        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.05f, 0.10f, 0.18f, 0.85f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.3f, 0.6f, 0.9f, 1f)
        shapeRenderer.rect(panelX, panelY, panelW, panelH)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        val textX = panelX + 20f
        var textY = panelY + panelH - 24f

        smallFont.color = Color(0.5f, 0.7f, 1f, 1f)
        smallFont.draw(batch, "【防具】", textX, textY)
        textY -= 40f

        val armor = unit.equippedArmor
        if (armor != null) {
            font.color = Color.WHITE
            font.draw(batch, armor.name, textX, textY)
            smallFont.color = Color(0.6f, 0.8f, 1f, 1f)
            smallFont.draw(batch, armorTypeLabel(armor.type), textX + 340f, textY - 4f)
            smallFont.color = Color.LIGHT_GRAY
            smallFont.draw(
                batch,
                "  DEF+${armor.defBonus}  RES+${armor.resBonus}  重さ:${armor.weight}",
                textX + 420f, textY - 4f
            )
        } else {
            font.color = Color.GRAY
            font.draw(batch, "（なし）", textX, textY)
        }

        batch.end()
    }

    /**
     * リスト領域（ユニット武器 + 在庫武器 + 在庫防具）を描画する
     */
    private fun renderListArea() {
        var y = LIST_TOP + scrollOffset

        // ユニットの武器セクション
        renderSectionLabel("【所持武器】", y, Color(0.7f, 0.7f, 1f, 1f))
        y -= LABEL_H

        if (unit.weapons.isEmpty()) {
            batch.projectionMatrix = viewport.camera.combined
            batch.begin()
            font.color = Color.GRAY
            if (y - 20f in LIST_BOTTOM..LIST_TOP) {
                font.draw(batch, "所持武器がありません", SLOT_X + 20f, y - 10f)
            }
            batch.end()
            y -= 36f
        } else {
            for (i in unit.weapons.indices) {
                if (y > LIST_BOTTOM - SLOT_HEIGHT && y <= LIST_TOP + SLOT_HEIGHT) {
                    renderWeaponSlot(
                        weapon = unit.weapons[i],
                        slotTopY = y,
                        isEquipped = (i == 0),
                        isSelected = (selType == SelectionType.UNIT_WEAPON && selUnitWeaponIdx == i),
                        isInventory = false
                    )
                }
                y -= (SLOT_HEIGHT + SLOT_GAP)
            }
        }

        // パーティ在庫: 武器セクション
        val invW = game.gameProgress.party.weaponInventory
        if (invW.isNotEmpty()) {
            y -= 8f
            renderSectionLabel("【パーティ在庫: 武器】", y, Color(1f, 0.85f, 0.4f, 1f))
            y -= LABEL_H

            for (i in invW.indices) {
                if (y > LIST_BOTTOM - SLOT_HEIGHT && y <= LIST_TOP + SLOT_HEIGHT) {
                    renderWeaponSlot(
                        weapon = invW[i],
                        slotTopY = y,
                        isEquipped = false,
                        isSelected = (selType == SelectionType.INV_WEAPON && selInvWeaponIdx == i),
                        isInventory = true
                    )
                }
                y -= (SLOT_HEIGHT + SLOT_GAP)
            }
        }

        // パーティ在庫: 防具セクション
        val invA = game.gameProgress.party.armorInventory
        if (invA.isNotEmpty()) {
            y -= 8f
            renderSectionLabel("【パーティ在庫: 防具】", y, Color(0.5f, 0.85f, 1f, 1f))
            y -= LABEL_H

            for (i in invA.indices) {
                if (y > LIST_BOTTOM - SLOT_HEIGHT && y <= LIST_TOP + SLOT_HEIGHT) {
                    renderArmorSlot(
                        armor = invA[i],
                        slotTopY = y,
                        isSelected = (selType == SelectionType.INV_ARMOR && selInvArmorIdx == i)
                    )
                }
                y -= (SLOT_HEIGHT + SLOT_GAP)
            }
        }
    }

    /**
     * セクションラベルを描画する
     *
     * @param text ラベルテキスト
     * @param topY ラベル上端Y座標
     * @param color テキスト色
     */
    private fun renderSectionLabel(text: String, topY: Float, color: Color) {
        if (topY < LIST_BOTTOM || topY > LIST_TOP + LABEL_H) return
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        smallFont.color = color
        smallFont.draw(batch, text, SLOT_X, topY)
        batch.end()
    }

    /**
     * 武器スロットを1つ描画する
     *
     * @param weapon 武器データ
     * @param slotTopY スロット上端Y座標
     * @param isEquipped 装備中かどうか
     * @param isSelected 選択中かどうか
     * @param isInventory 在庫アイテムかどうか
     */
    private fun renderWeaponSlot(
        weapon: Weapon,
        slotTopY: Float,
        isEquipped: Boolean,
        isSelected: Boolean,
        isInventory: Boolean
    ) {
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // スロット背景
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        when {
            isEquipped  -> shapeRenderer.setColor(0.12f, 0.20f, 0.05f, 0.90f)
            isSelected  -> shapeRenderer.setColor(0.12f, 0.12f, 0.28f, 0.90f)
            isInventory -> shapeRenderer.setColor(0.14f, 0.12f, 0.05f, 0.85f)
            else        -> shapeRenderer.setColor(0.08f, 0.08f, 0.12f, 0.85f)
        }
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()

        // 在庫アイテムの左端インジケータ
        if (isInventory) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(1f, 0.8f, 0.2f, 0.8f)
            shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, 4f, SLOT_HEIGHT)
            shapeRenderer.end()
        }

        // 枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = when {
            isEquipped  -> Color.GOLD
            isSelected  -> Color(0.5f, 0.5f, 1f, 1f)
            isInventory -> Color(0.6f, 0.5f, 0.2f, 0.6f)
            else        -> Color(0.3f, 0.3f, 0.3f, 0.6f)
        }
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        val textX = SLOT_X + 20f
        var textY = slotTopY - 20f

        font.color = when {
            isEquipped  -> Color.GOLD
            isSelected  -> Color(0.8f, 0.8f, 1f, 1f)
            else        -> Color.WHITE
        }
        font.draw(batch, weapon.name, textX, textY)
        textY -= 34f

        smallFont.color = Color(0.5f, 0.8f, 1f, 1f)
        smallFont.draw(batch, weaponTypeLabel(weapon.type), textX, textY)

        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(
            batch,
            "威力:${weapon.might}  命中:${weapon.hit}  必殺:${weapon.critical}  重さ:${weapon.weight}  射程:${weapon.minRange}〜${weapon.maxRange}",
            textX + 80f, textY
        )

        // バッジ
        if (isEquipped) {
            font.color = Color(0.3f, 1f, 0.3f, 1f)
            font.draw(batch, "装備中", SLOT_X + SLOT_WIDTH - 140f, slotTopY - SLOT_HEIGHT / 2f + 12f)
        } else if (isInventory) {
            smallFont.color = Color(1f, 0.85f, 0.3f, 0.8f)
            smallFont.draw(batch, "在庫", SLOT_X + SLOT_WIDTH - 100f, slotTopY - SLOT_HEIGHT / 2f + 10f)
        }

        batch.end()
    }

    /**
     * 防具スロットを1つ描画する
     *
     * @param armor 防具データ
     * @param slotTopY スロット上端Y座標
     * @param isSelected 選択中かどうか
     */
    private fun renderArmorSlot(armor: Armor, slotTopY: Float, isSelected: Boolean) {
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (isSelected) {
            shapeRenderer.setColor(0.12f, 0.12f, 0.28f, 0.90f)
        } else {
            shapeRenderer.setColor(0.06f, 0.10f, 0.16f, 0.85f)
        }
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()

        // 左端インジケータ（水色）
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.3f, 0.7f, 1f, 0.8f)
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, 4f, SLOT_HEIGHT)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (isSelected) Color(0.5f, 0.5f, 1f, 1f) else Color(0.2f, 0.4f, 0.6f, 0.6f)
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        val textX = SLOT_X + 20f
        var textY = slotTopY - 20f

        font.color = if (isSelected) Color(0.8f, 0.8f, 1f, 1f) else Color.WHITE
        font.draw(batch, armor.name, textX, textY)
        textY -= 34f

        smallFont.color = Color(0.6f, 0.8f, 1f, 1f)
        smallFont.draw(batch, armorTypeLabel(armor.type), textX, textY)

        smallFont.color = Color.LIGHT_GRAY
        smallFont.draw(
            batch,
            "DEF+${armor.defBonus}  RES+${armor.resBonus}  重さ:${armor.weight}",
            textX + 100f, textY
        )

        smallFont.color = Color(0.5f, 0.8f, 1f, 0.8f)
        smallFont.draw(batch, "在庫", SLOT_X + SLOT_WIDTH - 100f, slotTopY - SLOT_HEIGHT / 2f + 10f)

        batch.end()
    }

    /**
     * 下部ボタン群を描画する
     *
     * 選択状態に応じて適切なアクションボタンを表示する。
     */
    private fun renderBottomButtons() {
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // 戻るボタン
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.4f, 0.2f, 0.2f, 0.9f)
        shapeRenderer.rect(backBtnX, backBtnY, backBtnW, backBtnH)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(1f, 0.5f, 0.5f, 1f)
        shapeRenderer.rect(backBtnX, backBtnY, backBtnW, backBtnH)
        shapeRenderer.end()

        // アクションボタン（右: 装備する / 受け取る / 防具装備）
        val actLabel = getActionLabel()
        if (actLabel != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(0.15f, 0.25f, 0.55f, 0.92f)
            shapeRenderer.rect(actBtnX, actBtnY, actBtnW, actBtnH)
            shapeRenderer.end()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = Color(0.4f, 0.6f, 1f, 1f)
            shapeRenderer.rect(actBtnX, actBtnY, actBtnW, actBtnH)
            shapeRenderer.end()
        }

        // サブアクションボタン（中央: 返却する / 防具外す）
        val subLabel = getSubActionLabel()
        if (subLabel != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
            shapeRenderer.setColor(0.35f, 0.2f, 0.15f, 0.9f)
            shapeRenderer.rect(subBtnX, subBtnY, subBtnW, subBtnH)
            shapeRenderer.end()
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
            shapeRenderer.color = Color(1f, 0.6f, 0.3f, 1f)
            shapeRenderer.rect(subBtnX, subBtnY, subBtnW, subBtnH)
            shapeRenderer.end()
        }

        Gdx.gl.glDisable(GL20.GL_BLEND)

        // ボタンテキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        font.color = Color.WHITE
        font.draw(batch, "← 戻る", backBtnX + 16f, backBtnY + backBtnH / 2f + 12f)

        if (actLabel != null) {
            font.color = Color.WHITE
            glyphLayout.setText(font, actLabel)
            font.draw(
                batch, actLabel,
                actBtnX + actBtnW / 2f - glyphLayout.width / 2f,
                actBtnY + actBtnH / 2f + 12f
            )
        }

        if (subLabel != null) {
            font.color = Color.WHITE
            glyphLayout.setText(font, subLabel)
            font.draw(
                batch, subLabel,
                subBtnX + subBtnW / 2f - glyphLayout.width / 2f,
                subBtnY + subBtnH / 2f + 12f
            )
        }

        batch.end()
    }

    /**
     * 現在の選択状態に応じたアクションボタンのラベルを返す
     *
     * @return ボタンラベル（null = ボタン非表示）
     */
    private fun getActionLabel(): String? {
        return when (selType) {
            SelectionType.UNIT_WEAPON -> if (selUnitWeaponIdx > 0) "装備する" else null
            SelectionType.INV_WEAPON -> "受け取る"
            SelectionType.INV_ARMOR -> "防具装備"
            else -> null
        }
    }

    /**
     * 現在の選択状態に応じたサブアクションボタンのラベルを返す
     *
     * @return ボタンラベル（null = ボタン非表示）
     */
    private fun getSubActionLabel(): String? {
        return when (selType) {
            SelectionType.UNIT_WEAPON -> "返却する"
            SelectionType.NONE -> if (unit.equippedArmor != null) "防具外す" else null
            else -> null
        }
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

    /**
     * 防具タイプの日本語ラベルを返す
     *
     * @param type 防具タイプ
     * @return 日本語ラベル
     */
    private fun armorTypeLabel(type: ArmorType): String = when (type) {
        ArmorType.LIGHT_ARMOR -> "軽装"
        ArmorType.HEAVY_ARMOR -> "重装"
        ArmorType.SHIELD      -> "盾"
        ArmorType.MAGIC_ROBE  -> "ローブ"
        ArmorType.ACCESSORY   -> "装飾品"
    }
}
