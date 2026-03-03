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
            _chapters[index].completed = true
            // 次のチャプターを開放
            if (index + 1 < _chapters.size) {
                _chapters[index + 1].unlocked = true
            }
            Gdx.app.log(TAG, "チャプター完了: ${_chapters[index].name}")
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
            stats = Stats(hp = 20, str = 6, mag = 1, skl = 7, spd = 8, lck = 5, def = 5, res = 2),
            growthRate = GrowthRate(hp = 70, str = 50, mag = 10, skl = 55, spd = 60, lck = 40, def = 35, res = 25),
            isLord = true
        )
        ares.weapons.add(Weapon("ironSword", "鉄の剣", WeaponType.SWORD, might = 5, hit = 90, weight = 3, durability = 46))

        val leena = GameUnit(
            id = "hero_02", name = "リーナ",
            unitClass = UnitClass.LANCER, faction = Faction.PLAYER,
            stats = Stats(hp = 18, str = 7, mag = 0, skl = 5, spd = 5, lck = 3, def = 7, res = 1),
            growthRate = GrowthRate(hp = 60, str = 55, mag = 5, skl = 45, spd = 40, lck = 30, def = 50, res = 20)
        )
        leena.weapons.add(Weapon("ironLance", "鉄の槍", WeaponType.LANCE, might = 7, hit = 80, weight = 5, durability = 45))

        val maria = GameUnit(
            id = "hero_03", name = "マリア",
            unitClass = UnitClass.ARCHER, faction = Faction.PLAYER,
            stats = Stats(hp = 16, str = 5, mag = 0, skl = 8, spd = 7, lck = 4, def = 3, res = 3),
            growthRate = GrowthRate(hp = 55, str = 45, mag = 5, skl = 60, spd = 55, lck = 40, def = 25, res = 30)
        )
        maria.weapons.add(Weapon("ironBow", "鉄の弓", WeaponType.BOW, might = 6, hit = 85, weight = 3, minRange = 2, maxRange = 2, durability = 45))

        val eric = GameUnit(
            id = "hero_04", name = "エリック",
            unitClass = UnitClass.MAGE, faction = Faction.PLAYER,
            stats = Stats(hp = 15, str = 1, mag = 7, skl = 6, spd = 6, lck = 4, def = 2, res = 6),
            growthRate = GrowthRate(hp = 45, str = 10, mag = 60, skl = 50, spd = 45, lck = 35, def = 15, res = 50)
        )
        eric.weapons.add(Weapon("fire", "ファイアー", WeaponType.MAGIC, might = 5, hit = 90, weight = 2, minRange = 1, maxRange = 2, durability = 40))

        party.addUnits(listOf(ares, leena, maria, eric))

        // 初期出撃メンバー設定（最初の3人）
        party.setDeployedUnits(listOf("hero_01", "hero_02", "hero_03"))
    }

    companion object {
        private const val TAG = "GameProgress"
    }
}
