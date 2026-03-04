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
 * 装備変更画面
 *
 * ユニットの4つの装備スロット（右手・左手・防具1・防具2）と
 * パーティ在庫を表示し、装備の着脱を行う。
 *
 * 操作:
 * - 装備スロットをタップ → 選択 → 「外す」ボタンで在庫に戻す
 * - 在庫武器をタップ → 「右手に装備」「左手に装備」
 * - 在庫防具をタップ → 「防具1に装備」「防具2に装備」
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
    private enum class SelectionType {
        NONE,
        SLOT_RIGHT,   // 右手スロット選択中
        SLOT_LEFT,    // 左手スロット選択中
        SLOT_ARMOR1,  // 防具1スロット選択中
        SLOT_ARMOR2,  // 防具2スロット選択中
        INV_WEAPON,   // 在庫武器選択中
        INV_ARMOR     // 在庫防具選択中
    }

    /** 現在の選択種別 */
    private var selType: SelectionType = SelectionType.NONE

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

        /** 在庫スロット1つの高さ */
        private const val SLOT_HEIGHT = 100f

        /** スロット間ギャップ */
        private const val SLOT_GAP = 6f

        /** スロットの幅 */
        private const val SLOT_WIDTH = 960f

        /** スロット左端 */
        private const val SLOT_X = 60f

        /** リスト領域の上端Y */
        private const val LIST_TOP = 1200f

        /** リスト領域の下端Y */
        private const val LIST_BOTTOM = 100f

        /** セクションラベル高さ */
        private const val LABEL_H = 34f

        /** タップ判定閾値 */
        private const val TAP_THRESHOLD = 12f

        /** 装備パネル定数 */
        private const val EQUIP_PANEL_X = 60f
        private const val EQUIP_PANEL_Y = 1290f
        private const val EQUIP_PANEL_W = 960f
        private const val EQUIP_PANEL_H = 460f
        private const val EQUIP_SLOT_H = 70f
    }

    /** 戻るボタン（下端に配置） */
    private val backBtnX = 40f
    private val backBtnY = 20f
    private val backBtnW = 180f
    private val backBtnH = 60f

    /** メインアクションボタン（右） */
    private val actBtnX = GameConfig.VIRTUAL_WIDTH - 320f
    private val actBtnY = 20f
    private val actBtnW = 280f
    private val actBtnH = 60f

    /** サブアクションボタン（中央） */
    private val subBtnX = 240f
    private val subBtnY = 20f
    private val subBtnW = 240f
    private val subBtnH = 60f

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
     */
    override fun render(delta: Float) {
        handleInput()

        Gdx.gl.glClearColor(0.06f, 0.08f, 0.13f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        viewport.apply()

        renderHeader()
        renderEquipPanel()
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
                touchStartX = tx
                touchStartY = ty
                prevTouchY = ty
                dragDist = 0f
            } else if (prevTouchY >= 0 && ty in LIST_BOTTOM..LIST_TOP) {
                val dy = ty - prevTouchY
                dragDist += Math.abs(dy)
                scrollOffset += dy
                clampScroll()
                prevTouchY = ty
            }
            wasTouching = true
        } else if (wasTouching) {
            wasTouching = false
            if (dragDist < TAP_THRESHOLD) {
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

        // メインアクションボタン
        if (tx in actBtnX..(actBtnX + actBtnW) && ty in actBtnY..(actBtnY + actBtnH)) {
            handleActionButton()
            return
        }

        // サブアクションボタン
        if (tx in subBtnX..(subBtnX + subBtnW) && ty in subBtnY..(subBtnY + subBtnH)) {
            handleSubActionButton()
            return
        }

        // 装備パネル内タップ
        if (tx in EQUIP_PANEL_X..(EQUIP_PANEL_X + EQUIP_PANEL_W) &&
            ty in EQUIP_PANEL_Y..(EQUIP_PANEL_Y + EQUIP_PANEL_H)
        ) {
            handleEquipPanelTap(ty)
            return
        }

        // 在庫リスト領域タップ
        if (ty in LIST_BOTTOM..LIST_TOP && tx in SLOT_X..(SLOT_X + SLOT_WIDTH)) {
            handleListTap(ty)
            return
        }

        // 何もない場所 → 選択解除
        clearSelection()
    }

    /**
     * 装備パネル内のスロットタップ判定
     */
    private fun handleEquipPanelTap(tapY: Float) {
        val panelTop = EQUIP_PANEL_Y + EQUIP_PANEL_H
        var y = panelTop - 30f

        // 右手スロット
        if (tapY in (y - EQUIP_SLOT_H)..y) {
            if (unit.rightHand != null) {
                toggleSlotSelect(SelectionType.SLOT_RIGHT)
            }
            return
        }
        y -= (EQUIP_SLOT_H + 4f)

        // 左手スロット
        if (tapY in (y - EQUIP_SLOT_H)..y) {
            if (unit.leftHand != null) {
                toggleSlotSelect(SelectionType.SLOT_LEFT)
            }
            return
        }
        y -= (EQUIP_SLOT_H + 4f)

        // 防具スロット1
        if (tapY in (y - EQUIP_SLOT_H)..y) {
            if (unit.armorSlot1 != null) {
                toggleSlotSelect(SelectionType.SLOT_ARMOR1)
            }
            return
        }
        y -= (EQUIP_SLOT_H + 4f)

        // 防具スロット2
        if (tapY in (y - EQUIP_SLOT_H)..y) {
            if (unit.armorSlot2 != null) {
                toggleSlotSelect(SelectionType.SLOT_ARMOR2)
            }
            return
        }
    }

    /**
     * 在庫リスト内のタップ判定
     */
    private fun handleListTap(tapY: Float) {
        var y = LIST_TOP + scrollOffset

        // パーティ在庫: 武器
        val invW = game.gameProgress.party.weaponInventory
        if (invW.isNotEmpty()) {
            y -= LABEL_H
            for (i in invW.indices) {
                val top = y
                val bottom = y - SLOT_HEIGHT
                y -= (SLOT_HEIGHT + SLOT_GAP)
                if (tapY in bottom..top) {
                    toggleInvSelect(SelectionType.INV_WEAPON, i)
                    return
                }
            }
        }

        // パーティ在庫: 防具
        val invA = game.gameProgress.party.armorInventory
        if (invA.isNotEmpty()) {
            y -= (LABEL_H + 8f)
            for (i in invA.indices) {
                val top = y
                val bottom = y - SLOT_HEIGHT
                y -= (SLOT_HEIGHT + SLOT_GAP)
                if (tapY in bottom..top) {
                    toggleInvSelect(SelectionType.INV_ARMOR, i)
                    return
                }
            }
        }

        clearSelection()
    }

    // ==================== 選択管理 ====================

    /**
     * 装備スロットの選択をトグルする
     */
    private fun toggleSlotSelect(type: SelectionType) {
        if (selType == type) {
            clearSelection()
        } else {
            selType = type
            selInvWeaponIdx = -1
            selInvArmorIdx = -1
        }
    }

    /**
     * 在庫アイテムの選択をトグルする
     */
    private fun toggleInvSelect(type: SelectionType, idx: Int) {
        val isSame = when (type) {
            SelectionType.INV_WEAPON -> selType == type && selInvWeaponIdx == idx
            SelectionType.INV_ARMOR -> selType == type && selInvArmorIdx == idx
            else -> false
        }
        if (isSame) {
            clearSelection()
        } else {
            selType = type
            selInvWeaponIdx = if (type == SelectionType.INV_WEAPON) idx else -1
            selInvArmorIdx = if (type == SelectionType.INV_ARMOR) idx else -1
        }
    }

    /**
     * 選択状態をクリアする
     */
    private fun clearSelection() {
        selType = SelectionType.NONE
        selInvWeaponIdx = -1
        selInvArmorIdx = -1
    }

    // ==================== アクションボタン ====================

    /**
     * メインアクションボタンの処理
     *
     * スロット選択時 → 装備を外して在庫に戻す
     * 在庫武器選択時 → 右手に装備
     * 在庫防具選択時 → 防具1に装備
     */
    private fun handleActionButton() {
        when (selType) {
            SelectionType.SLOT_RIGHT -> {
                unit.rightHand?.let { w ->
                    game.gameProgress.party.returnWeaponFromUnit(w, unit)
                    Gdx.app.log(TAG, "${unit.name}: 右手を外す → ${w.name}")
                    clearSelection()
                }
            }
            SelectionType.SLOT_LEFT -> {
                unit.leftHand?.let { w ->
                    game.gameProgress.party.returnWeaponFromUnit(w, unit)
                    Gdx.app.log(TAG, "${unit.name}: 左手を外す → ${w.name}")
                    clearSelection()
                }
            }
            SelectionType.SLOT_ARMOR1 -> {
                val name = unit.armorSlot1?.name ?: ""
                game.gameProgress.party.returnArmorFromUnit(unit, 1)
                Gdx.app.log(TAG, "${unit.name}: 防具1を外す → $name")
                clearSelection()
            }
            SelectionType.SLOT_ARMOR2 -> {
                val name = unit.armorSlot2?.name ?: ""
                game.gameProgress.party.returnArmorFromUnit(unit, 2)
                Gdx.app.log(TAG, "${unit.name}: 防具2を外す → $name")
                clearSelection()
            }
            SelectionType.INV_WEAPON -> {
                val inv = game.gameProgress.party.weaponInventory
                val idx = selInvWeaponIdx
                if (idx in inv.indices) {
                    val w = inv[idx]
                    game.gameProgress.party.giveWeaponToRightHand(w, unit)
                    Gdx.app.log(TAG, "${unit.name}: 右手に装備 → ${w.name}")
                    clearSelection()
                }
            }
            SelectionType.INV_ARMOR -> {
                val inv = game.gameProgress.party.armorInventory
                val idx = selInvArmorIdx
                if (idx in inv.indices) {
                    val a = inv[idx]
                    game.gameProgress.party.giveArmorToUnit(a, unit, 1)
                    Gdx.app.log(TAG, "${unit.name}: 防具1に装備 → ${a.name}")
                    clearSelection()
                }
            }
            else -> {}
        }
    }

    /**
     * サブアクションボタンの処理
     *
     * 在庫武器選択時 → 左手に装備（二刀流）
     * 在庫防具選択時 → 防具2に装備
     */
    private fun handleSubActionButton() {
        when (selType) {
            SelectionType.INV_WEAPON -> {
                if (!unit.unitClass.canDualWield) return
                val inv = game.gameProgress.party.weaponInventory
                val idx = selInvWeaponIdx
                if (idx in inv.indices) {
                    val w = inv[idx]
                    game.gameProgress.party.giveWeaponToLeftHand(w, unit)
                    Gdx.app.log(TAG, "${unit.name}: 左手に装備 → ${w.name}")
                    clearSelection()
                }
            }
            SelectionType.INV_ARMOR -> {
                val inv = game.gameProgress.party.armorInventory
                val idx = selInvArmorIdx
                if (idx in inv.indices) {
                    val a = inv[idx]
                    game.gameProgress.party.giveArmorToUnit(a, unit, 2)
                    Gdx.app.log(TAG, "${unit.name}: 防具2に装備 → ${a.name}")
                    clearSelection()
                }
            }
            else -> {}
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
        var h = 0f
        val invW = game.gameProgress.party.weaponInventory
        if (invW.isNotEmpty()) {
            h += LABEL_H + invW.size * (SLOT_HEIGHT + SLOT_GAP)
        }
        val invA = game.gameProgress.party.armorInventory
        if (invA.isNotEmpty()) {
            h += LABEL_H + 8f + invA.size * (SLOT_HEIGHT + SLOT_GAP)
        }
        if (h == 0f) h = 50f
        return h
    }

    // ==================== 描画: ヘッダー ====================

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

    // ==================== 描画: 装備パネル ====================

    /**
     * 4つの装備スロットとステータス情報のパネルを描画する
     */
    private fun renderEquipPanel() {
        val px = EQUIP_PANEL_X
        val py = EQUIP_PANEL_Y
        val pw = EQUIP_PANEL_W
        val ph = EQUIP_PANEL_H

        // パネル背景
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.05f, 0.08f, 0.14f, 0.9f)
        shapeRenderer.rect(px, py, pw, ph)
        shapeRenderer.end()
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color(0.3f, 0.5f, 0.8f, 1f)
        shapeRenderer.rect(px, py, pw, ph)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        val panelTop = py + ph
        var slotY = panelTop - 30f

        // 右手スロット
        renderEquipSlotRow("【右手】", unit.rightHand, null, slotY, selType == SelectionType.SLOT_RIGHT)
        slotY -= (EQUIP_SLOT_H + 4f)

        // 左手スロット
        renderEquipSlotRow("【左手】", unit.leftHand, null, slotY, selType == SelectionType.SLOT_LEFT)
        slotY -= (EQUIP_SLOT_H + 4f)

        // 防具スロット1
        renderEquipSlotRow("【防具1】", null, unit.armorSlot1, slotY, selType == SelectionType.SLOT_ARMOR1)
        slotY -= (EQUIP_SLOT_H + 4f)

        // 防具スロット2
        renderEquipSlotRow("【防具2】", null, unit.armorSlot2, slotY, selType == SelectionType.SLOT_ARMOR2)
        slotY -= (EQUIP_SLOT_H + 4f)

        // ステータス行
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        val statusX = px + 20f
        smallFont.color = Color(0.8f, 0.9f, 0.4f, 1f)
        smallFont.draw(batch, "実効SPD: ${unit.effectiveSpeed()} (SPD ${unit.stats.spd})", statusX, slotY)

        val dualLabel = if (unit.unitClass.canDualWield) {
            if (unit.isDualWielding()) "二刀流: ON (ペナ-${unit.unitClass.dualWieldPenalty})" else "二刀流: 可"
        } else "二刀流: ×"
        smallFont.color = if (unit.isDualWielding()) Color(1f, 0.7f, 0.3f, 1f) else Color.GRAY
        smallFont.draw(batch, dualLabel, statusX + 480f, slotY)

        batch.end()
    }

    /**
     * 装備パネル内の1スロット行を描画する
     *
     * @param label スロットラベル（【右手】等）
     * @param weapon 武器（武器スロットの場合）
     * @param armor 防具（防具スロットの場合）
     * @param topY スロット上端Y座標
     * @param isSelected 選択中かどうか
     */
    private fun renderEquipSlotRow(
        label: String,
        weapon: Weapon?,
        armor: Armor?,
        topY: Float,
        isSelected: Boolean
    ) {
        val x = EQUIP_PANEL_X + 10f
        val w = EQUIP_PANEL_W - 20f
        val h = EQUIP_SLOT_H

        // スロット背景
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        when {
            isSelected -> shapeRenderer.setColor(0.15f, 0.15f, 0.30f, 0.9f)
            weapon != null || armor != null -> shapeRenderer.setColor(0.08f, 0.15f, 0.08f, 0.8f)
            else -> shapeRenderer.setColor(0.06f, 0.06f, 0.10f, 0.6f)
        }
        shapeRenderer.rect(x, topY - h, w, h)
        shapeRenderer.end()

        // 枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = when {
            isSelected -> Color(0.5f, 0.5f, 1f, 1f)
            weapon != null || armor != null -> Color(0.3f, 0.7f, 0.3f, 0.7f)
            else -> Color(0.2f, 0.2f, 0.3f, 0.5f)
        }
        shapeRenderer.rect(x, topY - h, w, h)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()

        val textX = x + 16f
        var textY = topY - 18f

        smallFont.color = Color(0.6f, 0.8f, 1f, 1f)
        smallFont.draw(batch, label, textX, textY)

        if (weapon != null) {
            font.color = if (isSelected) Color(0.8f, 0.8f, 1f, 1f) else Color.WHITE
            font.draw(batch, weapon.name, textX + 140f, textY + 4f)
            textY -= 30f
            smallFont.color = Color.LIGHT_GRAY
            smallFont.draw(
                batch,
                "${weaponTypeLabel(weapon.type)}  威力:${weapon.might}  命中:${weapon.hit}  重さ:${weapon.weight}  射程:${weapon.minRange}〜${weapon.maxRange}",
                textX + 20f, textY
            )
        } else if (armor != null) {
            font.color = if (isSelected) Color(0.8f, 0.8f, 1f, 1f) else Color.WHITE
            font.draw(batch, armor.name, textX + 140f, textY + 4f)
            textY -= 30f
            smallFont.color = Color.LIGHT_GRAY
            smallFont.draw(
                batch,
                "${armorTypeLabel(armor.type)}  DEF+${armor.defBonus}  RES+${armor.resBonus}  重さ:${armor.weight}",
                textX + 20f, textY
            )
        } else {
            font.color = Color.GRAY
            font.draw(batch, "（なし）", textX + 140f, textY + 4f)
        }

        batch.end()
    }

    // ==================== 描画: リスト領域 ====================

    /**
     * パーティ在庫（武器・防具）のスクロール可能リストを描画する
     */
    private fun renderListArea() {
        var y = LIST_TOP + scrollOffset

        // パーティ在庫: 武器
        val invW = game.gameProgress.party.weaponInventory
        if (invW.isNotEmpty()) {
            renderSectionLabel("【パーティ在庫: 武器】", y, Color(1f, 0.85f, 0.4f, 1f))
            y -= LABEL_H

            for (i in invW.indices) {
                if (y > LIST_BOTTOM - SLOT_HEIGHT && y <= LIST_TOP + SLOT_HEIGHT) {
                    renderWeaponSlot(
                        weapon = invW[i],
                        slotTopY = y,
                        isSelected = (selType == SelectionType.INV_WEAPON && selInvWeaponIdx == i)
                    )
                }
                y -= (SLOT_HEIGHT + SLOT_GAP)
            }
        }

        // パーティ在庫: 防具
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

        // 在庫が空の場合
        if (invW.isEmpty() && invA.isEmpty()) {
            batch.projectionMatrix = viewport.camera.combined
            batch.begin()
            font.color = Color.GRAY
            font.draw(batch, "在庫がありません", SLOT_X + 20f, LIST_TOP - 40f)
            batch.end()
        }
    }

    /**
     * セクションラベルを描画する
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
     * 在庫武器スロットを1つ描画する
     */
    private fun renderWeaponSlot(weapon: Weapon, slotTopY: Float, isSelected: Boolean) {
        shapeRenderer.projectionMatrix = viewport.camera.combined
        Gdx.gl.glEnable(GL20.GL_BLEND)

        // スロット背景
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        if (isSelected) {
            shapeRenderer.setColor(0.12f, 0.12f, 0.28f, 0.90f)
        } else {
            shapeRenderer.setColor(0.14f, 0.12f, 0.05f, 0.85f)
        }
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()

        // 左端インジケータ（黄色）
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(1f, 0.8f, 0.2f, 0.8f)
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, 4f, SLOT_HEIGHT)
        shapeRenderer.end()

        // 枠線
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = if (isSelected) Color(0.5f, 0.5f, 1f, 1f) else Color(0.6f, 0.5f, 0.2f, 0.6f)
        shapeRenderer.rect(SLOT_X, slotTopY - SLOT_HEIGHT, SLOT_WIDTH, SLOT_HEIGHT)
        shapeRenderer.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        // テキスト
        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        val textX = SLOT_X + 20f
        var textY = slotTopY - 20f

        font.color = if (isSelected) Color(0.8f, 0.8f, 1f, 1f) else Color.WHITE
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
        smallFont.color = Color(1f, 0.85f, 0.3f, 0.8f)
        smallFont.draw(batch, "在庫", SLOT_X + SLOT_WIDTH - 100f, slotTopY - SLOT_HEIGHT / 2f + 10f)

        batch.end()
    }

    /**
     * 在庫防具スロットを1つ描画する
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

    // ==================== 描画: 下部ボタン ====================

    /**
     * 下部ボタン群を描画する
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

        // メインアクションボタン
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

        // サブアクションボタン
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
     * メインアクションボタンのラベルを返す
     */
    private fun getActionLabel(): String? = when (selType) {
        SelectionType.SLOT_RIGHT, SelectionType.SLOT_LEFT,
        SelectionType.SLOT_ARMOR1, SelectionType.SLOT_ARMOR2 -> "外す"
        SelectionType.INV_WEAPON -> "右手に装備"
        SelectionType.INV_ARMOR -> "防具1に装備"
        else -> null
    }

    /**
     * サブアクションボタンのラベルを返す
     */
    private fun getSubActionLabel(): String? = when (selType) {
        SelectionType.INV_WEAPON -> if (unit.unitClass.canDualWield) "左手に装備" else null
        SelectionType.INV_ARMOR -> "防具2に装備"
        else -> null
    }

    // ==================== ユーティリティ ====================

    /**
     * 武器タイプの日本語ラベルを返す
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
     */
    private fun armorTypeLabel(type: ArmorType): String = when (type) {
        ArmorType.LIGHT_ARMOR -> "軽装"
        ArmorType.HEAVY_ARMOR -> "重装"
        ArmorType.SHIELD      -> "盾"
        ArmorType.MAGIC_ROBE  -> "ローブ"
        ArmorType.ACCESSORY   -> "装飾品"
        ArmorType.HEAD        -> "頭"
        ArmorType.FEET        -> "足"
    }
}
