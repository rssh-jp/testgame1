package com.tacticsflame.model.campaign

import com.tacticsflame.model.unit.GameUnit

/**
 * プレイヤーの部隊（パーティ）状態を管理するクラス
 *
 * 所持ユニットの追加・削除、出撃メンバーの選択を行う。
 * ゲーム全体を通して1つのインスタンスが保持される。
 */
class PartyState {

    /** 所持している全ユニット（味方パーティ全体） */
    private val _roster: MutableList<GameUnit> = mutableListOf()
    val roster: List<GameUnit> get() = _roster

    /** 現在の出撃メンバーのIDリスト */
    private val _deployedIds: MutableList<String> = mutableListOf()
    val deployedIds: List<String> get() = _deployedIds

    /**
     * ロスターを全クリアする（セーブデータ復元用）
     */
    fun clearRoster() {
        _roster.clear()
        _deployedIds.clear()
    }

    /**
     * ユニットをパーティに追加する
     *
     * @param unit 追加するユニット
     */
    fun addUnit(unit: GameUnit) {
        if (_roster.none { it.id == unit.id }) {
            _roster.add(unit)
        }
    }

    /**
     * 複数ユニットを一括でパーティに追加する
     *
     * @param units 追加するユニットリスト
     */
    fun addUnits(units: List<GameUnit>) {
        units.forEach { addUnit(it) }
    }

    /**
     * 出撃メンバーを設定する
     *
     * @param unitIds 出撃するユニットのIDリスト
     */
    fun setDeployedUnits(unitIds: List<String>) {
        _deployedIds.clear()
        _deployedIds.addAll(unitIds.filter { id -> _roster.any { it.id == id } })
    }

    /**
     * 出撃メンバーのトグル（追加/除去）
     *
     * @param unitId ユニットID
     * @param maxCount 最大出撃人数
     * @return トグル後の選択状態（true = 出撃, false = 非出撃）
     */
    fun toggleDeploy(unitId: String, maxCount: Int): Boolean {
        return if (_deployedIds.contains(unitId)) {
            _deployedIds.remove(unitId)
            false
        } else {
            if (_deployedIds.size < maxCount) {
                _deployedIds.add(unitId)
                true
            } else {
                false
            }
        }
    }

    /**
     * 出撃メンバーのユニット一覧を取得する
     *
     * @return 出撃対象の GameUnit リスト
     */
    fun getDeployedUnits(): List<GameUnit> {
        return _deployedIds.mapNotNull { id -> _roster.find { it.id == id } }
    }

    /**
     * IDでユニットを検索する
     *
     * @param id ユニットID
     * @return 見つかったユニット（なければnull）
     */
    fun findUnit(id: String): GameUnit? {
        return _roster.find { it.id == id }
    }
}
