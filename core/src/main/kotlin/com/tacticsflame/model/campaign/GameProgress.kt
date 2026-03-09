package com.tacticsflame.model.campaign

import com.badlogic.gdx.Gdx
import com.tacticsflame.model.map.*
import com.tacticsflame.model.unit.*

/**
 * キャンペーン全体のゲーム進行状態を管理するクラス
 *
 * チャプターの開放状況、パーティ状態、現在の選択チャプターなどを保持する。
 * TacticsFlameGame から1つだけ生成され、全 Screen から参照される。
 */
class GameProgress {

    /** プレイヤーの部隊状態 */
    val party: PartyState = PartyState()

    /** チャプター情報リスト（ゲーム全体） */
    private val _chapters: MutableList<ChapterInfo> = mutableListOf()
    val chapters: List<ChapterInfo> get() = _chapters

    /** 現在選択中のチャプター */
    var selectedChapter: ChapterInfo? = null

    /**
     * 初期データをセットアップする
     *
     * ゲーム開始時に呼び出し、チャプター一覧と初期パーティを構築する。
     */
    fun initialize() {
        setupChapters()
        setupInitialParty()
        Gdx.app.log(TAG, "ゲーム進行状態を初期化（チャプター: ${_chapters.size}, ユニット: ${party.roster.size}）")
    }

    /**
     * チャプターをクリア済みにして次チャプターを開放する
     *
     * @param chapterId クリアしたチャプターID
     */
    fun completeChapter(chapterId: String) {
        val index = _chapters.indexOfFirst { it.id == chapterId }
        if (index >= 0) {
            val chapter = _chapters[index]
            // ランダムマップは何度でも挑戦可能なので完了状態にしない
            if (chapter.id != "another_chapter") {
                chapter.completed = true
            }
            // chapter_1クリア時にcampaign_1も解放
            if (chapterId == "chapter_1") {
                _chapters.find { it.id == "campaign_1" }?.unlocked = true
            }
            // 次のチャプターを開放（ランダムマップ・キャンペーンマップの次は開放しない）
            if (chapter.id != "another_chapter" && chapter.id != "campaign_1" && index + 1 < _chapters.size) {
                _chapters[index + 1].unlocked = true
            }
            Gdx.app.log(TAG, "チャプター完了: ${chapter.name}")
        }
    }

    /**
     * プレイヤーユニットのHPを全回復する（チャプタークリア後など）
     */
    fun healAllUnits() {
        party.roster.forEach { it.fullHeal() }
    }

    // ==================== 初期データ構築 ====================

    /**
     * チャプター一覧を構築する
     */
    private fun setupChapters() {
        _chapters.clear()
        _chapters.addAll(
            listOf(
                ChapterInfo(
                    id = "chapter_1",
                    name = "第1章 - 始まりの戦い",
                    description = "山賊のアジトを制圧せよ。",
                    mapFileName = "chapter_1.json",
                    worldMapX = 0.25f,
                    worldMapY = 0.6f,
                    unlocked = true,
                    maxDeployCount = 3,
                    requiredUnits = listOf("hero_01")
                ),
                ChapterInfo(
                    id = "chapter_2",
                    name = "第2章 - 国境の防衛線",
                    description = "敵軍の侵攻を防げ。",
                    mapFileName = "chapter_2.json",
                    worldMapX = 0.5f,
                    worldMapY = 0.4f,
                    unlocked = false,
                    maxDeployCount = 4,
                    requiredUnits = listOf("hero_01")
                ),
                ChapterInfo(
                    id = "chapter_3",
                    name = "第3章 - 暗黒の森",
                    description = "森を抜けて要塞を目指せ。",
                    mapFileName = "chapter_3.json",
                    worldMapX = 0.75f,
                    worldMapY = 0.55f,
                    unlocked = false,
                    maxDeployCount = 4,
                    requiredUnits = listOf("hero_01")
                ),
                ChapterInfo(
                    id = "chapter_4",
                    name = "第4章 - 騎士団の砦",
                    description = "砦を守る騎士団を撃破せよ。",
                    mapFileName = "chapter_4.json",
                    worldMapX = 0.4f,
                    worldMapY = 0.3f,
                    unlocked = false,
                    maxDeployCount = 4,
                    requiredUnits = listOf("hero_01")
                ),
                ChapterInfo(
                    id = "chapter_5",
                    name = "第5章 - 水辺の決戦",
                    description = "川を渡り敵陣を突破せよ。",
                    mapFileName = "chapter_5.json",
                    worldMapX = 0.6f,
                    worldMapY = 0.25f,
                    unlocked = false,
                    maxDeployCount = 4,
                    requiredUnits = listOf("hero_01")
                ),
                ChapterInfo(
                    id = "chapter_6",
                    name = "第6章 - 王城攻略戦",
                    description = "暗黒将軍ヴォルクを倒し、王城を奪還せよ。",
                    mapFileName = "chapter_6.json",
                    worldMapX = 0.8f,
                    worldMapY = 0.15f,
                    unlocked = false,
                    maxDeployCount = 4,
                    requiredUnits = listOf("hero_01")
                ),
                ChapterInfo(
                    id = "another_chapter",
                    name = "ランダムマップ - 遭遇戦",
                    description = "パーティの平均レベルに応じた敵が出現する特殊マップ。何度でも挑戦可能。",
                    mapFileName = "another_chapter.json",
                    worldMapX = 0.5f,
                    worldMapY = 0.75f,
                    unlocked = true,
                    maxDeployCount = 4,
                    requiredUnits = emptyList()
                ),
                ChapterInfo(
                    id = "campaign_1",
                    name = "連続マップ進行",
                    description = "全6チャプターを一つの大マップで連続攻略する。HP引き継ぎ＋ウェーブ間30%回復。",
                    mapFileName = "campaign_map.json",
                    worldMapX = 0.5f,
                    worldMapY = 0.9f,
                    unlocked = true,
                    maxDeployCount = 4,
                    requiredUnits = listOf("hero_01")
                )
            )
        )
    }

    /**
     * 初期パーティを構築する（テスト用ハードコードデータ）
     */
    private fun setupInitialParty() {
        val ares = GameUnit(
            id = "hero_01", name = "アレス",
            unitClass = UnitClass.LORD, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate(),
            isLord = true
        )
        ares.rightHand = Weapon("ironSword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3)
        ares.armorSlot1 = Armor("leatherArmor", "革の鎧", ArmorType.LIGHT_ARMOR, defBonus = 1, resBonus = 0, weight = 1)

        val leena = GameUnit(
            id = "hero_02", name = "リーナ",
            unitClass = UnitClass.LANCER, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
        leena.rightHand = Weapon("ironLance", "鉄の槍", WeaponType.LANCE, might = 7, hit = 80, weight = 5)
        leena.armorSlot1 = Armor("chainMail", "鎖帷子", ArmorType.LIGHT_ARMOR, defBonus = 2, resBonus = 0, weight = 2)

        val maria = GameUnit(
            id = "hero_03", name = "マリア",
            unitClass = UnitClass.ARCHER, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
        maria.rightHand = Weapon("ironBow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2)
        maria.armorSlot1 = Armor("leatherArmor", "革の鎧", ArmorType.LIGHT_ARMOR, defBonus = 1, resBonus = 0, weight = 1)

        val eric = GameUnit(
            id = "hero_04", name = "エリック",
            unitClass = UnitClass.MAGE, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
        eric.rightHand = Weapon("fire", "ファイアー", WeaponType.MAGIC, might = 5, hit = 90, weight = 2, minRange = 1, maxRange = 2)
        eric.leftHand = Weapon("heal", "ライブ", WeaponType.STAFF, might = 0, hit = 100, weight = 1, healPower = 10)
        eric.armorSlot1 = Armor("magicRobe", "魔法のローブ", ArmorType.MAGIC_ROBE, defBonus = 0, resBonus = 3, weight = 1)

        val cecilia = GameUnit(
            id = "hero_05", name = "セシリア",
            unitClass = UnitClass.HEALER, faction = Faction.PLAYER,
            personalModifier = Stats(),
            personalGrowthRate = GrowthRate()
        )
        cecilia.rightHand = Weapon("heal", "ライブ", WeaponType.STAFF, might = 0, hit = 100, weight = 1, healPower = 10)
        cecilia.tactic = UnitTactic.HEAL

        party.addUnits(listOf(ares, leena, maria, eric, cecilia))

        // 初期出撃メンバー設定（最初の3人）
        party.setDeployedUnits(listOf("hero_01", "hero_02", "hero_03"))

        // パーティ共有在庫に予備の武器・防具を追加
        setupInitialInventory()
    }

    /**
     * パーティ共有在庫に初期装備を配布する
     */
    private fun setupInitialInventory() {
        // 予備の武器
        party.addWeaponsToInventory(
            listOf(
                Weapon("steelSword", "鋼の剣", WeaponType.SWORD, might = 8, hit = 80, weight = 5),
                Weapon("ironAxe", "鉄の斧", WeaponType.AXE, might = 8, hit = 75, weight = 6),
                Weapon("ironLance2", "鉄の槍", WeaponType.LANCE, might = 7, hit = 80, weight = 5),
                Weapon("ironSword2", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3),
                Weapon("fire2", "ファイアー", WeaponType.MAGIC, might = 5, hit = 90, weight = 2, minRange = 1, maxRange = 2),
                Weapon("heal", "ライブ", WeaponType.STAFF, might = 0, hit = 100, weight = 1, healPower = 10)
            )
        )

        // 予備の防具
        party.addArmorsToInventory(
            listOf(
                Armor("ironArmor", "鉄の鎧", ArmorType.HEAVY_ARMOR, defBonus = 4, resBonus = 0, weight = 5),
                Armor("ironShield", "鉄の盾", ArmorType.SHIELD, defBonus = 2, resBonus = 1, weight = 3),
                Armor("leatherArmor2", "革の鎧", ArmorType.LIGHT_ARMOR, defBonus = 1, resBonus = 0, weight = 1),
                Armor("guardCharm", "守りの護符", ArmorType.ACCESSORY, defBonus = 1, resBonus = 1, weight = 0),
                Armor("speedRing", "疾風の指輪", ArmorType.ACCESSORY, defBonus = 0, resBonus = 0, weight = -2),
                Armor("ironHelm", "鉄の兜", ArmorType.HEAD, defBonus = 2, resBonus = 0, weight = 2),
                Armor("leatherBoots", "革の靴", ArmorType.FEET, defBonus = 0, resBonus = 0, weight = -1),
                Armor("ironGreaves", "鉄の脛当て", ArmorType.FEET, defBonus = 1, resBonus = 0, weight = 1)
            )
        )

        Gdx.app.log(TAG, "初期在庫配布完了（武器: ${party.weaponInventory.size}, 防具: ${party.armorInventory.size}）")
    }

    companion object {
        private const val TAG = "GameProgress"
    }
}
