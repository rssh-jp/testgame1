package com.tacticsflame.system

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.unit.GameUnit

/**
 * CT（チャージタイム）ベースの行動順管理クラス
 *
 * 各ユニットのSPDに基づいてCTが蓄積され、
 * CTが閾値（CT_THRESHOLD）に達したユニットから順に行動する。
 * 従来の「チーム毎のフェイズ制」は廃止し、ユニット個別のターン制となる。
 */
class TurnManager {

    /** 現在のラウンド番号（全生存ユニットが1回ずつ行動すると+1） */
    var roundNumber: Int = 1
        private set

    /** 現在行動中のユニット */
    var activeUnit: GameUnit? = null
        private set

    /** 累計行動回数 */
    var totalActions: Int = 0
        private set

    /** 現在のラウンドで行動済みのユニットIDセット */
    private val actedThisRound = mutableSetOf<String>()

    /**
     * CTを進行させ、次に行動するユニットを決定する
     *
     * 全生存ユニットのCTにSPDを加算し続け、
     * CT >= CT_THRESHOLD に達したユニットのうち、
     * CT値が最も高い（同値ならSPDが高い）ユニットが行動権を得る。
     *
     * @param units 全ユニットのリスト
     * @return 行動権を得たユニット（ユニット無しの場合null）
     */
    fun advanceToNextUnit(units: List<GameUnit>): GameUnit? {
        val livingUnits = units.filter { !it.isDefeated }
        if (livingUnits.isEmpty()) return null

        // 無限ループ防止（SPD=0のユニットのみの場合に備える）
        val maxTicks = 1000
        var ticks = 0

        while (ticks < maxTicks) {
            // CT >= 閾値のユニットがいるか確認
            val readyUnit = findReadyUnit(livingUnits)
            if (readyUnit != null) {
                activeUnit = readyUnit
                return readyUnit
            }

            // 全ユニットのCTを進行（SPD=0でも最低1は加算）
            livingUnits.forEach { it.ct += maxOf(1, it.stats.spd) }
            ticks++
        }

        // フォールバック: 最もCTが高いユニットを選択
        val fallback = livingUnits.maxWithOrNull(
            compareBy<GameUnit> { it.ct }.thenBy { it.stats.spd }
        )
        activeUnit = fallback
        return fallback
    }

    /**
     * ユニットの行動完了処理
     *
     * CTを閾値分だけ消費し（超過分は持ち越し）、ラウンドカウントを更新する。
     *
     * @param unit 行動完了したユニット
     * @param allUnits 全ユニットリスト
     */
    fun completeAction(unit: GameUnit, allUnits: List<GameUnit>) {
        unit.ct -= GameConfig.CT_THRESHOLD
        totalActions++
        actedThisRound.add(unit.id)

        // 全生存ユニットが行動済みならラウンド更新
        val livingUnits = allUnits.filter { !it.isDefeated }
        if (livingUnits.all { it.id in actedThisRound }) {
            roundNumber++
            actedThisRound.clear()
        }

        activeUnit = null
    }

    /**
     * 今後の行動順を予測する（UIの行動順キュー表示用）
     *
     * 現在のCT状態からシミュレーションし、今後行動するユニットの順序を返す。
     *
     * @param units 全ユニットのリスト
     * @param count 予測する行動数
     * @return 行動順のユニットリスト
     */
    fun predictActionOrder(units: List<GameUnit>, count: Int): List<GameUnit> {
        val livingUnits = units.filter { !it.isDefeated }
        if (livingUnits.isEmpty()) return emptyList()

        // CTのシミュレーション用コピー
        val simCt = livingUnits.associate { it.id to it.ct }.toMutableMap()
        val order = mutableListOf<GameUnit>()

        repeat(count) {
            // 閾値以上のユニットを探す
            var readyUnit = livingUnits
                .filter { (simCt[it.id] ?: 0) >= GameConfig.CT_THRESHOLD }
                .maxWithOrNull(
                    compareBy<GameUnit> { simCt[it.id] ?: 0 }.thenBy { it.stats.spd }
                )

            if (readyUnit == null) {
                // CTを進行させる
                var safety = 1000
                while (readyUnit == null && safety > 0) {
                    livingUnits.forEach {
                        simCt[it.id] = (simCt[it.id] ?: 0) + maxOf(1, it.stats.spd)
                    }
                    readyUnit = livingUnits
                        .filter { (simCt[it.id] ?: 0) >= GameConfig.CT_THRESHOLD }
                        .maxWithOrNull(
                            compareBy<GameUnit> { simCt[it.id] ?: 0 }.thenBy { it.stats.spd }
                        )
                    safety--
                }
            }

            if (readyUnit != null) {
                order.add(readyUnit)
                simCt[readyUnit.id] = (simCt[readyUnit.id] ?: 0) - GameConfig.CT_THRESHOLD
            }
        }

        return order
    }

    /**
     * ターン管理をリセットする
     *
     * @param units 全ユニットリスト（CTもリセットされる）
     */
    fun reset(units: List<GameUnit>) {
        roundNumber = 1
        totalActions = 0
        actedThisRound.clear()
        activeUnit = null
        units.forEach { it.ct = 0 }
    }

    /**
     * CT >= 閾値に達したユニットのうち最も優先度が高いものを返す
     *
     * 優先度: CT値が高い → SPDが高い の順
     *
     * @param livingUnits 生存ユニットのリスト
     * @return 行動可能なユニット（なければnull）
     */
    private fun findReadyUnit(livingUnits: List<GameUnit>): GameUnit? {
        return livingUnits
            .filter { it.ct >= GameConfig.CT_THRESHOLD }
            .maxWithOrNull(
                compareBy<GameUnit> { it.ct }.thenBy { it.stats.spd }
            )
    }
}
