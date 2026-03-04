package com.tacticsflame.model.campaign

import com.tacticsflame.model.unit.Armor
import com.tacticsflame.model.unit.GameUnit
import com.tacticsflame.model.unit.Weapon

/**
 * プレイヤーの部隊（パーティ）状態を管理するクラス
 *
 * 所持ユニットの追加・削除、出撃メンバーの選択、共有在庫の管理を行う。
 * ゲーム全体を通して1つのインスタンスが保持される。
 */
class PartyState {

    /** 所持している全ユニット（味方パーティ全体） */
    private val _roster: MutableList<GameUnit> = mutableListOf()
    val roster: List<GameUnit> get() = _roster

    /** 現在の出撃メンバーのIDリスト */
    private val _deployedIds: MutableList<String> = mutableListOf()
    val deployedIds: List<String> get() = _deployedIds

    /** パーティ共有の武器在庫（ユニットに装備されていない武器） */
    private val _weaponInventory: MutableList<Weapon> = mutableListOf()
    val weaponInventory: List<Weapon> get() = _weaponInventory

    /** パーティ共有の防具在庫（ユニットに装備されていない防具） */
    private val _armorInventory: MutableList<Armor> = mutableListOf()
    val armorInventory: List<Armor> get() = _armorInventory

    /**
     * ロスターを全クリアする（セーブデータ復元用）
     */
    fun clearRoster() {
        _roster.clear()
        _deployedIds.clear()
    }

    // ==================== 在庫管理 ====================

    /**
     * 武器を在庫に追加する
     *
     * @param weapon 追加する武器
     */
    fun addWeaponToInventory(weapon: Weapon) {
        _weaponInventory.add(weapon)
    }

    /**
     * 複数の武器を在庫に一括追加する
     *
     * @param weapons 追加する武器リスト
     */
    fun addWeaponsToInventory(weapons: List<Weapon>) {
        _weaponInventory.addAll(weapons)
    }

    /**
     * 防具を在庫に追加する
     *
     * @param armor 追加する防具
     */
    fun addArmorToInventory(armor: Armor) {
        _armorInventory.add(armor)
    }

    /**
     * 複数の防具を在庫に一括追加する
     *
     * @param armors 追加する防具リスト
     */
    fun addArmorsToInventory(armors: List<Armor>) {
        _armorInventory.addAll(armors)
    }

    /**
     * 在庫から武器をユニットの右手に装備させる
     *
     * 現在の右手武器は在庫に戻される。
     *
     * @param weapon 渡す武器
     * @param unit 受け取るユニット
     * @return 成功した場合 true
     */
    fun giveWeaponToRightHand(weapon: Weapon, unit: GameUnit): Boolean {
        val idx = _weaponInventory.indexOf(weapon)
        if (idx < 0) return false
        _weaponInventory.removeAt(idx)
        unit.rightHand?.let { _weaponInventory.add(it) }
        unit.rightHand = weapon
        return true
    }

    /**
     * 在庫から武器をユニットの左手に装備させる（二刀流）
     *
     * クラスが二刀流非対応の場合は失敗する。
     * 現在の左手武器は在庫に戻される。
     *
     * @param weapon 渡す武器
     * @param unit 受け取るユニット
     * @return 成功した場合 true
     */
    fun giveWeaponToLeftHand(weapon: Weapon, unit: GameUnit): Boolean {
        if (!unit.unitClass.canDualWield) return false
        val idx = _weaponInventory.indexOf(weapon)
        if (idx < 0) return false
        _weaponInventory.removeAt(idx)
        unit.leftHand?.let { _weaponInventory.add(it) }
        unit.leftHand = weapon
        return true
    }

    /**
     * 後方互換用: 在庫から武器をユニットの右手に渡す
     */
    fun giveWeaponToUnit(weapon: Weapon, unit: GameUnit): Boolean {
        return giveWeaponToRightHand(weapon, unit)
    }

    /**
     * ユニットから武器を在庫に戻す
     *
     * 右手・左手・予備リストから該当武器を探して在庫に戻す。
     *
     * @param weapon 戻す武器
     * @param unit 武器を返すユニット
     * @return 成功した場合 true
     */
    fun returnWeaponFromUnit(weapon: Weapon, unit: GameUnit): Boolean {
        when {
            unit.rightHand == weapon -> unit.rightHand = null
            unit.leftHand == weapon -> unit.leftHand = null
            else -> {
                val idx = unit.weapons.indexOf(weapon)
                if (idx < 0) return false
                unit.weapons.removeAt(idx)
            }
        }
        _weaponInventory.add(weapon)
        return true
    }

    /**
     * 在庫から防具をユニットの防具スロットに装備させる
     *
     * 指定スロットに既に防具がある場合、現在の防具は在庫に戻される。
     *
     * @param armor 装備させる防具
     * @param unit 装備するユニット
     * @param slot スロット番号（1 or 2）
     * @return 成功した場合 true
     */
    fun giveArmorToUnit(armor: Armor, unit: GameUnit, slot: Int = 1): Boolean {
        val idx = _armorInventory.indexOf(armor)
        if (idx < 0) return false
        _armorInventory.removeAt(idx)
        when (slot) {
            1 -> {
                unit.armorSlot1?.let { _armorInventory.add(it) }
                unit.armorSlot1 = armor
            }
            2 -> {
                unit.armorSlot2?.let { _armorInventory.add(it) }
                unit.armorSlot2 = armor
            }
            else -> {
                _armorInventory.add(armor) // 無効なスロット → 在庫に戻す
                return false
            }
        }
        return true
    }

    /**
     * ユニットの防具を外して在庫に戻す
     *
     * @param unit 防具を外すユニット
     * @param slot スロット番号（1 or 2）
     * @return 成功した場合 true
     */
    fun returnArmorFromUnit(unit: GameUnit, slot: Int = 1): Boolean {
        val armor = when (slot) {
            1 -> unit.armorSlot1
            2 -> unit.armorSlot2
            else -> return false
        } ?: return false
        when (slot) {
            1 -> unit.armorSlot1 = null
            2 -> unit.armorSlot2 = null
        }
        _armorInventory.add(armor)
        return true
    }

    /**
     * 在庫を全クリアする（セーブデータ復元用）
     */
    fun clearInventory() {
        _weaponInventory.clear()
        _armorInventory.clear()
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
