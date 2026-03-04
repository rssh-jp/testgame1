package com.tacticsflame.model.battle

import com.tacticsflame.core.GameConfig
import com.tacticsflame.model.map.Position
import com.tacticsflame.model.map.TerrainType
import com.tacticsflame.model.map.Tile
import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * DamageCalc のテスト（素手攻撃・防具ボーナス・実効速度による追撃・武器三すくみ）
 */
class DamageCalcTest {

    /** 平地タイルを生成する（補正なし） */
    private fun plainTile(): Tile {
        return Tile(Position(0, 0), TerrainType.PLAIN)
    }

    /** テスト用ユニットを生成する */
    private fun createUnit(
        id: String = "test",
        str: Int = 8,
        mag: Int = 3,
        skl: Int = 7,
        spd: Int = 8,
        lck: Int = 5,
        def: Int = 5,
        res: Int = 2,
        hp: Int = 30,
        weapons: MutableList<Weapon> = mutableListOf(),
        armor: Armor? = null
    ): GameUnit {
        val unit = GameUnit(
            id = id, name = "テスト$id",
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            stats = Stats(hp = hp, str = str, mag = mag, skl = skl, spd = spd, lck = lck, def = def, res = res),
            growthRate = GrowthRate(),
            weapons = weapons
        )
        unit.rightHand = weapons.firstOrNull()
        unit.armorSlot1 = armor
        return unit
    }

    // ==================== 素手攻撃テスト ====================

    @Test
    fun `素手攻撃のダメージはSTR + UNARMED_MIGHT - DEFで計算される`() {
        val attacker = createUnit(str = 10, spd = 5)
        val defender = createUnit(def = 4, spd = 5)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // STR(10) + UNARMED_MIGHT(0) - DEF(4) = 6
        assertEquals(6, forecast.damage)
    }

    @Test
    fun `素手攻撃の命中率にUNARMED_HIT 80が使われる`() {
        // SKL=0, LCK=0 で命中基本値を純粋にする
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        // SPD=0, LCK=0 の防御側（平地: avoidBonus=0）
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 = 80
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + terrainAvoid(0) = 0
        // hitRate = 80 - 0 = 80
        assertEquals(80, forecast.hitRate)
    }

    @Test
    fun `素手攻撃の必殺率にUNARMED_CRITICAL 0が使われる`() {
        val attacker = createUnit(skl = 0, lck = 0, spd = 5)
        val defender = createUnit(lck = 0, spd = 5)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // critRate = (UNARMED_CRITICAL(0) + SKL(0)/2) - (LCK(0)/2) = 0
        assertEquals(0, forecast.critRate)
    }

    @Test
    fun `素手攻撃のダメージが0未満にならない`() {
        val attacker = createUnit(str = 2, spd = 5) // STR=2
        val defender = createUnit(def = 10, spd = 5) // DEF=10
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // STR(2) + UNARMED_MIGHT(0) - DEF(10) = -8 → 0
        assertEquals(0, forecast.damage)
    }

    // ==================== 防具のDEFボーナステスト ====================

    @Test
    fun `防具のdefBonusがダメージ軽減に反映される`() {
        val sword = Weapon("sword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3)
        val attacker = createUnit(str = 10, spd = 5, weapons = mutableListOf(sword))

        // 防具なしの防御側
        val defenderNoArmor = createUnit(id = "d1", def = 4, spd = 5)
        // 防具ありの防御側（DEFボーナス+3）
        val heavyArmor = Armor("armor", "鉄の鎧", ArmorType.HEAVY_ARMOR, defBonus = 3, resBonus = 0, weight = 5)
        val defenderWithArmor = createUnit(id = "d2", def = 4, spd = 5, armor = heavyArmor)

        val tile = plainTile()

        val forecastNoArmor = DamageCalc.calculateForecast(attacker, defenderNoArmor, tile, tile)
        val forecastWithArmor = DamageCalc.calculateForecast(attacker, defenderWithArmor, tile, tile)

        // STR(10) + might(5) - DEF(4) = 11 （防具なし）
        // STR(10) + might(5) - (DEF(4) + armorDef(3)) = 8 （防具あり）
        assertEquals(11, forecastNoArmor.damage)
        assertEquals(8, forecastWithArmor.damage)
    }

    @Test
    fun `魔法攻撃時にresBonusが適用される`() {
        val magic = Weapon("fire", "ファイアー", WeaponType.MAGIC, might = 4, hit = 90, weight = 2)
        val attacker = createUnit(mag = 8, spd = 5, weapons = mutableListOf(magic))

        // 防具なし
        val defenderNoArmor = createUnit(id = "d1", res = 3, spd = 5)
        // 魔法ローブ装備（RESボーナス+4）
        val magicRobe = Armor("robe", "魔法のローブ", ArmorType.MAGIC_ROBE, defBonus = 0, resBonus = 4, weight = 1)
        val defenderWithRobe = createUnit(id = "d2", res = 3, spd = 5, armor = magicRobe)

        val tile = plainTile()

        val forecastNoArmor = DamageCalc.calculateForecast(attacker, defenderNoArmor, tile, tile)
        val forecastWithRobe = DamageCalc.calculateForecast(attacker, defenderWithRobe, tile, tile)

        // MAG(8) + might(4) - RES(3) = 9 （防具なし）
        // MAG(8) + might(4) - (RES(3) + armorRes(4)) = 5 （ローブあり）
        assertEquals(9, forecastNoArmor.damage)
        assertEquals(5, forecastWithRobe.damage)
    }

    @Test
    fun `物理攻撃では防具のresBonusは適用されない`() {
        val sword = Weapon("sword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3)
        val attacker = createUnit(str = 10, spd = 5, weapons = mutableListOf(sword))

        // 魔法ローブ（resBonus=5, defBonus=0）を装備した防御側
        val magicRobe = Armor("robe", "魔法のローブ", ArmorType.MAGIC_ROBE, defBonus = 0, resBonus = 5, weight = 1)
        val defender = createUnit(def = 4, spd = 5, armor = magicRobe)

        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // 物理攻撃: STR(10) + might(5) - (DEF(4) + armorDef(0)) = 11
        // resBonus は物理攻撃に影響しない
        assertEquals(11, forecast.damage)
    }

    // ==================== effectiveSpeedによる追撃判定テスト ====================

    @Test
    fun `effectiveSpeedの差が5以上で追撃可能`() {
        val attacker = createUnit(spd = 15)
        val defender = createUnit(spd = 8)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // effectiveSpeed: 15 vs 8, diff = 7 >= DOUBLE_ATTACK_SPEED_DIFF(5)
        assertTrue(forecast.canDoubleAttack)
    }

    @Test
    fun `effectiveSpeedの差がちょうど5で追撃可能`() {
        val attacker = createUnit(spd = 13)
        val defender = createUnit(spd = 8)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // effectiveSpeed: 13 vs 8, diff = 5 >= 5
        assertTrue(forecast.canDoubleAttack)
    }

    @Test
    fun `effectiveSpeedの差が4以下で追撃不可`() {
        val attacker = createUnit(spd = 12)
        val defender = createUnit(spd = 8)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // effectiveSpeed: 12 vs 8, diff = 4 < 5
        assertFalse(forecast.canDoubleAttack)
    }

    @Test
    fun `重い武器装備で追撃不可になる`() {
        val heavyWeapon = Weapon("heavy", "重い武器", WeaponType.SWORD, might = 10, hit = 70, weight = 10)
        val attacker = createUnit(spd = 15, weapons = mutableListOf(heavyWeapon))
        val defender = createUnit(spd = 8)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // attacker effectiveSpeed: 15 - 10 = 5
        // defender effectiveSpeed: 8 - 0 = 8
        // diff = 5 - 8 = -3 < 5 → 追撃不可
        assertFalse(forecast.canDoubleAttack)
    }

    @Test
    fun `防具の重量も追撃判定に影響する`() {
        val sword = Weapon("sword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3)
        val heavyArmor = Armor("armor", "重鎧", ArmorType.HEAVY_ARMOR, defBonus = 5, resBonus = 0, weight = 8)

        val attacker = createUnit(spd = 15, weapons = mutableListOf(sword))
        attacker.armorSlot1 = heavyArmor

        val defender = createUnit(spd = 8)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // attacker effectiveSpeed: 15 - 3 - 8 = 4
        // defender effectiveSpeed: 8 - 0 - 0 = 8
        // diff = 4 - 8 = -4 < 5 → 追撃不可
        assertFalse(forecast.canDoubleAttack)
    }

    @Test
    fun `防御側の重い装備で攻撃側が追撃可能になる`() {
        val attacker = createUnit(spd = 10)
        val heavyArmor = Armor("armor", "重鎧", ArmorType.HEAVY_ARMOR, defBonus = 5, resBonus = 0, weight = 6)
        val defender = createUnit(spd = 10, armor = heavyArmor)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // attacker effectiveSpeed: 10
        // defender effectiveSpeed: 10 - 6 = 4
        // diff = 10 - 4 = 6 >= 5 → 追撃可能
        assertTrue(forecast.canDoubleAttack)
    }

    // ==================== 武器三すくみテスト ====================

    @Test
    fun `素手vs素手では武器三すくみが適用されない`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, skl = 0, lck = 0, spd = 5)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // STR(10) + UNARMED_MIGHT(0) - DEF(4) + triangleBonus(0) - terrainDef(0) = 6
        assertEquals(6, forecast.damage)
    }

    @Test
    fun `素手vs武器ありでも武器三すくみが適用されない`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val axe = Weapon("axe", "鉄の斧", WeaponType.AXE, might = 7, hit = 75)
        val defender = createUnit(def = 4, skl = 0, lck = 0, spd = 5, weapons = mutableListOf(axe))
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // 攻撃側が素手なので三すくみ補正なし
        // STR(10) + UNARMED_MIGHT(0) - DEF(4) = 6
        assertEquals(6, forecast.damage)
    }

    @Test
    fun `武器ありvs素手でも武器三すくみが適用されない`() {
        val sword = Weapon("sword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90)
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5, weapons = mutableListOf(sword))
        val defender = createUnit(def = 4, skl = 0, lck = 0, spd = 5)
        val tile = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, tile, tile)

        // 防御側が素手なので三すくみ補正なし
        // STR(10) + might(5) - DEF(4) = 11
        assertEquals(11, forecast.damage)
    }

    // ==================== 定数確認テスト ====================

    @Test
    fun `素手攻撃の定数が仕様通りであることを確認`() {
        assertEquals(0, GameConfig.UNARMED_MIGHT, "素手威力は0")
        assertEquals(80, GameConfig.UNARMED_HIT, "素手命中は80")
        assertEquals(0, GameConfig.UNARMED_CRITICAL, "素手必殺は0")
        assertEquals(0, GameConfig.UNARMED_WEIGHT, "素手重量は0")
    }

    @Test
    fun `追撃判定定数が仕様通りであることを確認`() {
        assertEquals(5, GameConfig.DOUBLE_ATTACK_SPEED_DIFF, "追撃に必要な速度差は5")
    }

    // ==================== 攻撃側地形の命中補正テスト ====================

    @Test
    fun `森から攻撃で命中率+10される`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val forestTile = Tile(Position(0, 0), TerrainType.FOREST)
        val plain = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, forestTile, plain)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 + hitBonus(10) = 90
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + avoidBonus(0) = 0
        // hitRate = 90 - 0 = 90
        assertEquals(90, forecast.hitRate)
    }

    @Test
    fun `山から攻撃で命中率+15される`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val mountainTile = Tile(Position(0, 0), TerrainType.MOUNTAIN)
        val plain = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, mountainTile, plain)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 + hitBonus(15) = 95
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + avoidBonus(0) = 0
        // hitRate = 95 - 0 = 95
        assertEquals(95, forecast.hitRate)
    }

    @Test
    fun `砦から攻撃で命中率+10される`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val fortTile = Tile(Position(0, 0), TerrainType.FORT)
        val plain = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, fortTile, plain)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 + hitBonus(10) = 90
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + avoidBonus(0) = 0
        // hitRate = 90 - 0 = 90
        assertEquals(90, forecast.hitRate)
    }

    @Test
    fun `村から攻撃で命中率+5される`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val villageTile = Tile(Position(0, 0), TerrainType.VILLAGE)
        val plain = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, villageTile, plain)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 + hitBonus(5) = 85
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + avoidBonus(0) = 0
        // hitRate = 85 - 0 = 85
        assertEquals(85, forecast.hitRate)
    }

    @Test
    fun `水域にいる攻撃側の命中率-15される`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val waterTile = Tile(Position(0, 0), TerrainType.WATER)
        val plain = plainTile()

        val forecast = DamageCalc.calculateForecast(attacker, defender, waterTile, plain)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 + hitBonus(-15) = 65
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + avoidBonus(0) = 0
        // hitRate = 65 - 0 = 65
        assertEquals(65, forecast.hitRate)
    }

    @Test
    fun `水域にいる防御側の回避が-15される`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val plain = plainTile()
        val waterTile = Tile(Position(0, 0), TerrainType.WATER)

        val forecast = DamageCalc.calculateForecast(attacker, defender, plain, waterTile)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 + hitBonus(0) = 80
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + avoidBonus(-15) = -15
        // hitRate = 80 - (-15) = 95
        assertEquals(95, forecast.hitRate)
    }

    @Test
    fun `両者が地形に立っている場合の合算テスト`() {
        val attacker = createUnit(str = 10, skl = 0, lck = 0, spd = 5)
        val defender = createUnit(def = 4, spd = 0, lck = 0)
        val forestTile = Tile(Position(0, 0), TerrainType.FOREST)
        val mountainTile = Tile(Position(1, 1), TerrainType.MOUNTAIN)

        val forecast = DamageCalc.calculateForecast(attacker, defender, forestTile, mountainTile)

        // baseHit = UNARMED_HIT(80) + SKL(0)*2 + LCK(0)/2 + hitBonus(10) = 90
        // avoid = effectiveSpeed(0)*2 + LCK(0)/2 + avoidBonus(30) = 30
        // hitRate = 90 - 30 = 60
        assertEquals(60, forecast.hitRate)
    }

    @Test
    fun `TerrainTypeのhitBonus値が仕様通りであることを確認`() {
        assertEquals(0, TerrainType.PLAIN.hitBonus, "平地のhitBonusは0")
        assertEquals(10, TerrainType.FOREST.hitBonus, "森のhitBonusは10")
        assertEquals(15, TerrainType.MOUNTAIN.hitBonus, "山のhitBonusは15")
        assertEquals(10, TerrainType.FORT.hitBonus, "砦のhitBonusは10")
        assertEquals(-15, TerrainType.WATER.hitBonus, "水域のhitBonusは-15")
        assertEquals(-15, TerrainType.WATER.avoidBonus, "水域のavoidBonusは-15")
        assertEquals(0, TerrainType.WALL.hitBonus, "壁のhitBonusは0")
        assertEquals(5, TerrainType.VILLAGE.hitBonus, "村のhitBonusは5")
        assertEquals(0, TerrainType.BRIDGE.hitBonus, "橋のhitBonusは0")
    }
}
