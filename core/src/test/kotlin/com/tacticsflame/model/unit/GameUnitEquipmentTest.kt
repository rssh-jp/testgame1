package com.tacticsflame.model.unit

import kotlin.test.*

/**
 * GameUnit の装備・実効速度・射程に関するテスト
 */
class GameUnitEquipmentTest {

    /** テスト用ユニットを生成する */
    private fun createUnit(
        spd: Int = 10,
        rightHand: Weapon? = null
    ): GameUnit {
        return GameUnit(
            id = "test_unit", name = "テストユニット",
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            stats = Stats(hp = 20f, str = 8f, mag = 3f, skl = 7f, spd = spd.toFloat(), lck = 5f, def = 5f, res = 2f),
            growthRate = GrowthRate()
        ).also { it.rightHand = rightHand }
    }

    /** テスト用武器を生成する */
    private fun createWeapon(
        weight: Int = 5,
        minRange: Int = 1,
        maxRange: Int = 1
    ): Weapon {
        return Weapon(
            id = "test_weapon", name = "テスト武器",
            type = WeaponType.SWORD, might = 5, hit = 90,
            weight = weight, minRange = minRange, maxRange = maxRange
        )
    }

    /** テスト用防具を生成する */
    private fun createArmor(
        type: ArmorType = ArmorType.HEAVY_ARMOR,
        defBonus: Int = 3,
        resBonus: Int = 0,
        weight: Int = 5
    ): Armor {
        return Armor(
            id = "test_armor", name = "テスト防具",
            type = type, defBonus = defBonus, resBonus = resBonus, weight = weight
        )
    }

    // ==================== effectiveSpeed テスト ====================

    @Test
    fun `武器のみ装備時のeffectiveSpeedが正しい`() {
        val unit = createUnit(spd = 10, rightHand = createWeapon(weight = 3))

        // SPD(10) - weaponWeight(3) - armorWeight(0) = 7
        assertEquals(7, unit.effectiveSpeed())
    }

    @Test
    fun `防具のみ装備時のeffectiveSpeedが正しい`() {
        val unit = createUnit(spd = 10)
        unit.armorSlot1 = createArmor(weight = 4)

        // SPD(10) - weaponWeight(0) - armorWeight(4) = 6
        assertEquals(6, unit.effectiveSpeed())
    }

    @Test
    fun `武器と防具の両方装備時のeffectiveSpeedが正しい`() {
        val unit = createUnit(spd = 10, rightHand = createWeapon(weight = 3))
        unit.armorSlot1 = createArmor(weight = 4)

        // SPD(10) - weaponWeight(3) - armorWeight(4) = 3
        assertEquals(3, unit.effectiveSpeed())
    }

    @Test
    fun `装備なしのeffectiveSpeedはSPDそのまま`() {
        val unit = createUnit(spd = 10)

        // SPD(10) - 0 - 0 = 10
        assertEquals(10, unit.effectiveSpeed())
    }

    @Test
    fun `重い装備でeffectiveSpeedが0以下にならない`() {
        val unit = createUnit(spd = 5, rightHand = createWeapon(weight = 10))
        unit.armorSlot1 = createArmor(weight = 10)

        // SPD(5) - weaponWeight(10) - armorWeight(10) = -15 → 0
        assertEquals(0, unit.effectiveSpeed())
    }

    @Test
    fun `武器重量だけでSPDを超えてもeffectiveSpeedは0`() {
        val unit = createUnit(spd = 3, rightHand = createWeapon(weight = 8))

        // SPD(3) - weaponWeight(8) = -5 → 0
        assertEquals(0, unit.effectiveSpeed())
    }

    @Test
    fun `防具重量だけでSPDを超えてもeffectiveSpeedは0`() {
        val unit = createUnit(spd = 2)
        unit.armorSlot1 = createArmor(weight = 7)

        // SPD(2) - armorWeight(7) = -5 → 0
        assertEquals(0, unit.effectiveSpeed())
    }

    // ==================== attackMinRange / attackMaxRange テスト ====================

    @Test
    fun `武器装備時のattackMinRangeは武器のminRange`() {
        val unit = createUnit(rightHand = createWeapon(minRange = 2, maxRange = 3))
        assertEquals(2, unit.attackMinRange())
    }

    @Test
    fun `武器装備時のattackMaxRangeは武器のmaxRange`() {
        val unit = createUnit(rightHand = createWeapon(minRange = 2, maxRange = 3))
        assertEquals(3, unit.attackMaxRange())
    }

    @Test
    fun `素手のattackMinRangeは1`() {
        val unit = createUnit()
        assertEquals(1, unit.attackMinRange())
    }

    @Test
    fun `素手のattackMaxRangeは1`() {
        val unit = createUnit()
        assertEquals(1, unit.attackMaxRange())
    }

    @Test
    fun `弓装備時のattackMinRangeとattackMaxRange`() {
        val bow = Weapon("bow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, minRange = 2, maxRange = 2)
        val unit = createUnit(rightHand = bow)

        assertEquals(2, unit.attackMinRange())
        assertEquals(2, unit.attackMaxRange())
    }

    // ==================== armorSlot1 装備・解除テスト ====================

    @Test
    fun `初期状態で防具は装備されていない`() {
        val unit = createUnit()
        assertNull(unit.armorSlot1)
    }

    @Test
    fun `防具を装備できる`() {
        val unit = createUnit()
        val armor = createArmor()
        unit.armorSlot1 = armor

        assertEquals(armor, unit.armorSlot1)
    }

    @Test
    fun `防具をnullにして解除できる`() {
        val unit = createUnit()
        unit.armorSlot1 = createArmor()
        unit.armorSlot1 = null

        assertNull(unit.armorSlot1)
    }

    @Test
    fun `防具を別の防具に変更できる`() {
        val unit = createUnit()
        val armor1 = createArmor(type = ArmorType.HEAVY_ARMOR, defBonus = 5, weight = 8)
        val armor2 = createArmor(type = ArmorType.MAGIC_ROBE, defBonus = 1, resBonus = 5, weight = 2)

        unit.armorSlot1 = armor1
        assertEquals(armor1, unit.armorSlot1)

        unit.armorSlot1 = armor2
        assertEquals(armor2, unit.armorSlot1)
    }

    @Test
    fun `防具変更でeffectiveSpeedが再計算される`() {
        val unit = createUnit(spd = 10)

        val heavyArmor = createArmor(type = ArmorType.HEAVY_ARMOR, weight = 8)
        unit.armorSlot1 = heavyArmor
        assertEquals(2, unit.effectiveSpeed()) // 10 - 8 = 2

        val lightArmor = createArmor(type = ArmorType.LIGHT_ARMOR, weight = 2)
        unit.armorSlot1 = lightArmor
        assertEquals(8, unit.effectiveSpeed()) // 10 - 2 = 8

        unit.armorSlot1 = null
        assertEquals(10, unit.effectiveSpeed()) // 10 - 0 = 10
    }
}
