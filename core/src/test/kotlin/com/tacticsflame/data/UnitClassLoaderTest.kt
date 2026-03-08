package com.tacticsflame.data

import com.tacticsflame.model.unit.*
import kotlin.test.*

/**
 * [UnitClassLoader] のユニットテスト
 *
 * Gdx 依存を避けるため [UnitClassLoader.loadFromString] を使用してテストする。
 * [UnitClass.initialize] / [UnitClass.reset] の動作も検証する。
 */
class UnitClassLoaderTest {

    @AfterTest
    fun tearDown() {
        // 各テスト後に UnitClass の状態をリセット
        UnitClass.reset()
    }

    // =========================================================================
    // 1. 正常系: 全フィールドが正しくパースされること
    // =========================================================================
    @Test
    fun `全フィールドが正しくパースされること`() {
        val json = """
        [
          {
            "id": "lord",
            "name": "ロード",
            "moveType": "INFANTRY",
            "baseMov": 5,
            "usableWeapons": ["SWORD"],
            "baseStats": {
              "hp": 20, "str": 6, "mag": 1, "skl": 7,
              "spd": 7, "lck": 5, "def": 5, "res": 2
            },
            "canDualWield": true,
            "dualWieldPenalty": 2,
            "classGrowthRate": {
              "hp": 1.60, "str": 0.40, "mag": 0.10, "skl": 0.35,
              "spd": 0.30, "lck": 0.25, "def": 0.25, "res": 0.15
            }
          }
        ]
        """.trimIndent()

        val result = UnitClassLoader.loadFromString(json)

        assertEquals(1, result.size)
        val lord = result["lord"]!!
        assertEquals("lord", lord.id)
        assertEquals("ロード", lord.name)
        assertEquals(MoveType.INFANTRY, lord.moveType)
        assertEquals(5, lord.baseMov)
        assertEquals(listOf(WeaponType.SWORD), lord.usableWeapons)
        assertTrue(lord.canDualWield)
        assertEquals(2, lord.dualWieldPenalty)
        assertEquals(1.60f, lord.classGrowthRate.hp, 0.001f)
        assertEquals(0.40f, lord.classGrowthRate.str, 0.001f)
        // baseStats のパース確認
        assertEquals(20f, lord.baseStats.hp)
        assertEquals(6f, lord.baseStats.str)
        assertEquals(1f, lord.baseStats.mag)
        assertEquals(7f, lord.baseStats.skl)
        assertEquals(7f, lord.baseStats.spd)
        assertEquals(5f, lord.baseStats.lck)
        assertEquals(5f, lord.baseStats.def)
        assertEquals(2f, lord.baseStats.res)
    }

    // =========================================================================
    // 2. 正常系: 複数クラスが正しくパースされること
    // =========================================================================
    @Test
    fun `複数クラスが正しくパースされること`() {
        val json = """
        [
          {
            "id": "lord",
            "name": "ロード",
            "moveType": "INFANTRY",
            "baseMov": 5,
            "usableWeapons": ["SWORD"]
          },
          {
            "id": "knight",
            "name": "ナイト",
            "moveType": "CAVALRY",
            "baseMov": 7,
            "usableWeapons": ["SWORD", "LANCE"]
          },
          {
            "id": "pegasusKnight",
            "name": "ペガサスナイト",
            "moveType": "FLYING",
            "baseMov": 7,
            "usableWeapons": ["LANCE"]
          }
        ]
        """.trimIndent()

        val result = UnitClassLoader.loadFromString(json)

        assertEquals(3, result.size)
        assertTrue(result.containsKey("lord"))
        assertTrue(result.containsKey("knight"))
        assertTrue(result.containsKey("pegasusKnight"))
    }

    // =========================================================================
    // 3. classGrowthRate のパース精度検証
    // =========================================================================
    @Test
    fun `classGrowthRate の各Float値が正確に読み込まれること`() {
        val json = """
        [
          {
            "id": "testClass",
            "name": "テストクラス",
            "moveType": "INFANTRY",
            "baseMov": 5,
            "usableWeapons": ["SWORD"],
            "classGrowthRate": {
              "hp": 2.30, "str": 0.55, "mag": 0.70, "skl": 0.45,
              "spd": 0.35, "lck": 0.20, "def": 0.60, "res": 0.15
            }
          }
        ]
        """.trimIndent()

        val result = UnitClassLoader.loadFromString(json)
        val gr = result["testClass"]!!.classGrowthRate

        assertEquals(2.30f, gr.hp, 0.001f)
        assertEquals(0.55f, gr.str, 0.001f)
        assertEquals(0.70f, gr.mag, 0.001f)
        assertEquals(0.45f, gr.skl, 0.001f)
        assertEquals(0.35f, gr.spd, 0.001f)
        assertEquals(0.20f, gr.lck, 0.001f)
        assertEquals(0.60f, gr.def, 0.001f)
        assertEquals(0.15f, gr.res, 0.001f)
    }

    // =========================================================================
    // 4. オプションフィールドの省略時デフォルト値
    // =========================================================================
    @Test
    fun `オプションフィールド省略時にデフォルト値が設定されること`() {
        val json = """
        [
          {
            "id": "minimal",
            "name": "ミニマルクラス",
            "moveType": "INFANTRY",
            "baseMov": 4,
            "usableWeapons": ["AXE"]
          }
        ]
        """.trimIndent()

        val result = UnitClassLoader.loadFromString(json)
        val cls = result["minimal"]!!

        // canDualWield のデフォルトは false
        assertFalse(cls.canDualWield)
        // dualWieldPenalty のデフォルトは 0
        assertEquals(0, cls.dualWieldPenalty)
        // classGrowthRate のデフォルトは全て 0.0f
        val gr = cls.classGrowthRate
        assertEquals(0.0f, gr.hp, 0.001f)
        assertEquals(0.0f, gr.str, 0.001f)
        assertEquals(0.0f, gr.mag, 0.001f)
        assertEquals(0.0f, gr.skl, 0.001f)
        assertEquals(0.0f, gr.spd, 0.001f)
        assertEquals(0.0f, gr.lck, 0.001f)
        assertEquals(0.0f, gr.def, 0.001f)
        assertEquals(0.0f, gr.res, 0.001f)
    }

    // =========================================================================
    // 5. 不正な moveType でのスキップ
    // =========================================================================
    @Test
    fun `不正なmoveTypeを持つエントリがスキップされ他は正常にロードされること`() {
        val json = """
        [
          {
            "id": "badClass",
            "name": "不正クラス",
            "moveType": "SWIMMING",
            "baseMov": 5,
            "usableWeapons": ["SWORD"]
          },
          {
            "id": "goodClass",
            "name": "正常クラス",
            "moveType": "INFANTRY",
            "baseMov": 5,
            "usableWeapons": ["LANCE"]
          }
        ]
        """.trimIndent()

        val result = UnitClassLoader.loadFromString(json)

        assertEquals(1, result.size)
        assertFalse(result.containsKey("badClass"))
        assertTrue(result.containsKey("goodClass"))
    }

    // =========================================================================
    // 6. 必須フィールド（id）欠如でのスキップ
    // =========================================================================
    @Test
    fun `idがないエントリがスキップされること`() {
        val json = """
        [
          {
            "name": "ID無しクラス",
            "moveType": "INFANTRY",
            "baseMov": 5,
            "usableWeapons": ["SWORD"]
          },
          {
            "id": "valid",
            "name": "有効クラス",
            "moveType": "INFANTRY",
            "baseMov": 5,
            "usableWeapons": ["SWORD"]
          }
        ]
        """.trimIndent()

        val result = UnitClassLoader.loadFromString(json)

        assertEquals(1, result.size)
        assertTrue(result.containsKey("valid"))
    }

    // =========================================================================
    // 7. UnitClass.initialize() による ALL 上書き
    // =========================================================================
    @Test
    fun `initializeによりALLがJSON値を返すこと`() {
        val json = """
        [
          {
            "id": "lord",
            "name": "カスタムロード",
            "moveType": "INFANTRY",
            "baseMov": 6,
            "usableWeapons": ["SWORD", "LANCE"],
            "canDualWield": true,
            "dualWieldPenalty": 3,
            "classGrowthRate": {
              "hp": 2.00, "str": 0.50, "mag": 0.20, "skl": 0.40,
              "spd": 0.35, "lck": 0.30, "def": 0.30, "res": 0.20
            }
          }
        ]
        """.trimIndent()

        val loaded = UnitClassLoader.loadFromString(json)
        UnitClass.initialize(loaded)

        // ALL から lord を取得し、JSON の値で上書きされていることを検証
        val lord = UnitClass.ALL["lord"]!!
        assertEquals("カスタムロード", lord.name)
        assertEquals(6, lord.baseMov)
        assertEquals(listOf(WeaponType.SWORD, WeaponType.LANCE), lord.usableWeapons)
        assertTrue(lord.canDualWield)
        assertEquals(3, lord.dualWieldPenalty)
        assertEquals(2.00f, lord.classGrowthRate.hp, 0.001f)
        assertEquals(0.50f, lord.classGrowthRate.str, 0.001f)

        // テスト末尾で reset
        UnitClass.reset()

        // reset 後はデフォルト値に戻ることを確認
        val defaultLord = UnitClass.ALL["lord"]!!
        assertEquals("ロード", defaultLord.name)
        assertEquals(5, defaultLord.baseMov)
    }

    // =========================================================================
    // 8. UnitClass.initialize() のマージ戦略
    // =========================================================================
    @Test
    fun `initializeのマージ戦略でJSONにないクラスもDEFAULT_ALLから取得できること`() {
        // 1クラスだけ JSON で渡す
        val json = """
        [
          {
            "id": "lord",
            "name": "カスタムロード",
            "moveType": "INFANTRY",
            "baseMov": 6,
            "usableWeapons": ["SWORD"]
          }
        ]
        """.trimIndent()

        val loaded = UnitClassLoader.loadFromString(json)
        UnitClass.initialize(loaded)

        // JSON で渡した lord はカスタム値
        val lord = UnitClass.ALL["lord"]!!
        assertEquals("カスタムロード", lord.name)
        assertEquals(6, lord.baseMov)

        // JSON に含まれていないクラスも DEFAULT_ALL から取得できる
        val archer = UnitClass.ALL["archer"]
        assertNotNull(archer)
        assertEquals("アーチャー", archer.name)

        val knight = UnitClass.ALL["knight"]
        assertNotNull(knight)
        assertEquals("ナイト", knight.name)

        val mage = UnitClass.ALL["mage"]
        assertNotNull(mage)
        assertEquals("メイジ", mage.name)

        // ALL のサイズはデフォルト全クラス数以上であること（マージなので減らない）
        assertTrue(UnitClass.ALL.size >= 10)
    }

    // =========================================================================
    // 9. 空配列でのロード
    // =========================================================================
    @Test
    fun `空配列を渡して空マップが返ること`() {
        val json = "[]"

        val result = UnitClassLoader.loadFromString(json)

        assertTrue(result.isEmpty())
    }

    // =========================================================================
    // 10. baseStats 未指定時はゼロ初期化されること
    // =========================================================================
    @Test
    fun `baseStats未指定時はデフォルト値が使われること`() {
        val json = """
        [
          {
            "id": "testClass",
            "name": "テストクラス",
            "moveType": "INFANTRY",
            "baseMov": 5,
            "usableWeapons": ["SWORD"]
          }
        ]
        """.trimIndent()

        val result = UnitClassLoader.loadFromString(json)

        val testClass = result["testClass"]!!
        // baseStats 未指定時は Stats() デフォルト（全て0）になる
        assertEquals(0f, testClass.baseStats.hp)
        assertEquals(0f, testClass.baseStats.str)
        assertEquals(0f, testClass.baseStats.mag)
        assertEquals(0f, testClass.baseStats.skl)
        assertEquals(0f, testClass.baseStats.spd)
        assertEquals(0f, testClass.baseStats.lck)
        assertEquals(0f, testClass.baseStats.def)
        assertEquals(0f, testClass.baseStats.res)
    }
}
