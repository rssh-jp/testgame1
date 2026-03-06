package com.tacticsflame.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.unit.Faction
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.util.FontManager

/**
 * ユニットの基本ステータスパネルを描画する共通レンダラー
 *
 * BattleScreen / BattlePrepScreen の両方で同一レイアウトを利用する。
 */
object UnitStatusPanelRenderer {

    /** 上部領域内の表示スロット */
    enum class Slot {
        LEFT,
        RIGHT
    }

    private const val PANEL_MARGIN = 16f
    private const val PANEL_GAP = 20f
    private const val FONT_SIZE = 37

    /**
     * ステータスパネルを描画する
     *
     * @param shapeRenderer ShapeRenderer
     * @param batch SpriteBatch
     * @param unit 表示対象ユニット
     * @param areaLeft 上部エリア左端X
     * @param areaTop 上部エリア上端Y
     * @param areaWidth 上部エリア幅
     * @param areaHeight 上部エリア高さ
     * @param slot 左右どちらに配置するか
     * @param title パネル見出し（nullなら非表示）
     */
    fun render(
        shapeRenderer: ShapeRenderer,
        batch: SpriteBatch,
        unit: GameUnit,
        areaLeft: Float,
        areaTop: Float,
        areaWidth: Float,
        areaHeight: Float,
        slot: Slot,
        title: String? = null
    ) {
        val font = FontManager.getFont(size = FONT_SIZE)
        val panelWidth = (areaWidth - PANEL_MARGIN * 2f - PANEL_GAP) / 2f
        val panelHeight = areaHeight - PANEL_MARGIN * 2f
        val panelX = when (slot) {
            Slot.LEFT -> areaLeft + PANEL_MARGIN
            Slot.RIGHT -> areaLeft + PANEL_MARGIN + panelWidth + PANEL_GAP
        }
        val panelY = areaTop - panelHeight - PANEL_MARGIN

        val borderColor = when (unit.faction) {
            Faction.PLAYER -> Color(0.3f, 0.5f, 1f, 1f)
            Faction.ENEMY -> Color(1f, 0.3f, 0.3f, 1f)
            Faction.ALLY -> Color(0.3f, 1f, 0.5f, 1f)
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0f, 0f, 0f, 0.78f)
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight)
        shapeRenderer.end()

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = borderColor
        shapeRenderer.rect(panelX, panelY, panelWidth, panelHeight)
        shapeRenderer.end()

        val padX = panelWidth * 0.04f
        val lineHeight = (font.lineHeight * 0.88f).coerceIn(panelHeight * 0.040f, panelHeight * 0.060f)
        val barHeight = panelHeight * 0.024f
        val textToBarGap = lineHeight * 0.65f
        val barToTextGap = lineHeight * 0.75f
        val compactLineHeight = lineHeight * 0.88f
        val leftX = panelX + padX
        val rightX = panelX + panelWidth * 0.54f
        val barWidth = panelWidth - padX * 2f

        batch.begin()
        var textY = panelY + panelHeight - panelHeight * 0.06f

        if (!title.isNullOrEmpty()) {
            font.color = Color.GOLD
            font.draw(batch, title, leftX, textY)
            textY -= lineHeight
        }

        font.color = borderColor
        font.draw(batch, unit.name, leftX, textY)
        textY -= lineHeight

        font.color = Color.LIGHT_GRAY
        font.draw(batch, "Lv.${unit.level}  ${unit.unitClass.name}", leftX, textY)
        textY -= lineHeight

        if (unit.faction == Faction.PLAYER) {
            font.color = Color(0.8f, 0.7f, 0.3f, 1f)
            font.draw(batch, "EXP  ${unit.exp} / ${GameConfig.EXP_TO_LEVEL_UP}", leftX, textY)
            textY -= lineHeight
        }

        font.color = Color.WHITE
        font.draw(batch, "HP  ${unit.currentHp} / ${unit.maxHp}", leftX, textY)
        batch.end()

        val hpRatio = (unit.currentHp.toFloat() / unit.maxHp.toFloat()).coerceIn(0f, 1f)
        val hpBarY = textY - textToBarGap - barHeight
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(leftX, hpBarY, barWidth, barHeight)
        val hpColor = when {
            hpRatio > 0.5f -> Color(0.2f, 0.9f, 0.2f, 1f)
            hpRatio > 0.25f -> Color(0.9f, 0.9f, 0.1f, 1f)
            else -> Color(0.9f, 0.2f, 0.2f, 1f)
        }
        shapeRenderer.color = hpColor
        shapeRenderer.rect(leftX, hpBarY, barWidth * hpRatio, barHeight)
        shapeRenderer.end()

        batch.begin()
        textY = hpBarY - barToTextGap
        font.color = Color.WHITE
        font.draw(batch, "CT  ${unit.ct} / ${GameConfig.CT_THRESHOLD}", leftX, textY)
        batch.end()

        val ctRatio = (unit.ct.toFloat() / GameConfig.CT_THRESHOLD).coerceIn(0f, 1f)
        val ctBarY = textY - textToBarGap - barHeight
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f)
        shapeRenderer.rect(leftX, ctBarY, barWidth, barHeight)
        when {
            ctRatio >= 0.8f -> shapeRenderer.setColor(1f, 0.9f, 0.2f, 1f)
            ctRatio >= 0.5f -> shapeRenderer.setColor(0.3f, 0.8f, 1f, 1f)
            else -> shapeRenderer.setColor(0.4f, 0.4f, 0.4f, 1f)
        }
        shapeRenderer.rect(leftX, ctBarY, barWidth * ctRatio, barHeight)
        shapeRenderer.end()

        val stats = unit.stats
        val weapon = unit.equippedWeapon()

        batch.begin()
        textY = ctBarY - barToTextGap
        font.color = Color.WHITE
        font.draw(batch, "STR  ${stats.effectiveStr}", leftX, textY)
        font.draw(batch, "MAG  ${stats.effectiveMag}", rightX, textY)
        textY -= compactLineHeight

        font.draw(batch, "SKL  ${stats.effectiveSkl}", leftX, textY)
        font.draw(batch, "SPD  ${stats.effectiveSpd}(${unit.effectiveSpeed()})", rightX, textY)
        textY -= compactLineHeight

        font.draw(batch, "LCK  ${stats.effectiveLck}", leftX, textY)
        font.draw(batch, "DEF  ${stats.effectiveDef}", rightX, textY)
        textY -= compactLineHeight

        font.draw(batch, "RES  ${stats.effectiveRes}", leftX, textY)
        font.draw(batch, "MOV  ${unit.mov}", rightX, textY)
        textY -= compactLineHeight

        if (weapon != null) {
            font.color = Color.GOLD
            font.draw(batch, weapon.name, leftX, textY)
            textY -= compactLineHeight
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "Mt ${weapon.might}  Hit ${weapon.hit}  Wt ${weapon.weight}", leftX, textY)
        } else {
            font.color = Color(0.8f, 0.7f, 0.5f, 1f)
            font.draw(batch, "素手 (射程1)", leftX, textY)
        }

        val armor1 = unit.armorSlot1
        val armor2 = unit.armorSlot2
        if (armor1 != null || armor2 != null) {
            textY -= compactLineHeight
            font.color = Color(0.5f, 0.7f, 1f, 1f)
            val armorNames = listOfNotNull(armor1?.name, armor2?.name).joinToString(", ")
            font.draw(batch, armorNames, leftX, textY)
            textY -= compactLineHeight
            font.color = Color.LIGHT_GRAY
            font.draw(batch, "DEF+${unit.totalArmorDef()}  RES+${unit.totalArmorRes()}", leftX, textY)
        }

        batch.end()
    }
}
