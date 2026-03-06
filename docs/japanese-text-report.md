# Tactics Flame — 全日本語テキスト一覧

---

## 1. JSONデータファイル / マップファイルの日本語テキスト

### core/assets/data/units.json

| フィールド | テキスト |
|-----------|---------|
| name | アレス |
| name | リーナ |
| name | マリア |
| name | エリック |

### core/assets/data/weapons.json

| フィールド | テキスト |
|-----------|---------|
| name | 鉄の剣 |
| name | 鋼の剣 |
| name | 鉄の槍 |
| name | 鉄の斧 |
| name | 鉄の弓 |
| name | ファイアー |
| name | ライブ |

### core/assets/data/armors.json

| フィールド | テキスト |
|-----------|---------|
| name | 革の鎧 |
| name | 鎖帷子 |
| name | 鉄の鎧 |
| name | 鋼の鎧 |
| name | 鉄の盾 |
| name | 鋼の盾 |
| name | 魔法のローブ |
| name | 賢者のローブ |
| name | 疾風の指輪 |
| name | 守りの護符 |

### core/assets/maps/chapter_1.json

| フィールド | テキスト |
|-----------|---------|
| name | 第1章 - 始まりの戦い |
| enemy name | 山賊A |
| enemy name | 山賊B |
| enemy name | 盗賊 |
| dialogue speaker | アレス |
| dialogue text | ここが山賊のアジトか...。みんな、気を引き締めろ！ |
| dialogue speaker | リーナ |
| dialogue text | はい、アレス様！いつでも行けます！ |

### core/assets/maps/chapter_2.json

| フィールド | テキスト |
|-----------|---------|
| name | 第2章 - 国境の防衛線 |
| enemy name | 国境兵A |
| enemy name | 国境兵B |
| enemy name | 弓兵 |
| enemy name | 傭兵隊長 |
| dialogue speaker | アレス |
| dialogue text | 国境に敵軍が...！ここを突破されるわけにはいかない！ |
| dialogue speaker | リーナ |
| dialogue text | 防衛線を維持しましょう。私が前線を支えます！ |

### core/assets/maps/chapter_3.json

| フィールド | テキスト |
|-----------|---------|
| name | 第3章 - 暗黒の森 |
| enemy name | 森の盗賊A |
| enemy name | 森の盗賊B |
| enemy name | 山賊 |
| enemy name | 闇魔道士 |
| enemy name | 狙撃手 |
| dialogue speaker | マリア |
| dialogue text | この森、視界が悪いわ...弓で援護するから、気をつけて進んで！ |
| dialogue speaker | アレス |
| dialogue text | ああ、頼む。マリア、エリック、後方から支援を頼む。 |
| dialogue speaker | エリック |
| dialogue text | 任せてください。魔法で道を切り開きましょう。 |

### core/assets/maps/chapter_4.json

| フィールド | テキスト |
|-----------|---------|
| name | 第4章 - 騎士団の砦 |
| enemy name | 騎士A |
| enemy name | 騎士B |
| enemy name | 重装兵A |
| enemy name | 重装兵B |
| enemy name | 弓騎兵 |
| enemy name | 砦隊長 |
| dialogue speaker | アレス |
| dialogue text | あの砦を落とす。騎士団は手強いが、ここを越えなければ先へ進めない。 |
| dialogue speaker | リーナ |
| dialogue text | 重装兵には槍で突きましょう。力比べなら負けません！ |
| dialogue speaker | エリック |
| dialogue text | 砦の守りは固い...魔法で崩していきましょう。 |

### core/assets/maps/chapter_5.json

| フィールド | テキスト |
|-----------|---------|
| name | 第5章 - 水辺の決戦  |
| enemy name | 天馬騎士A |
| enemy name | 天馬騎士B |
| enemy name | 水の魔道士 |
| enemy name | 橋守りA |
| enemy name | 橋守りB |
| enemy name | 渡河兵 |
| enemy name | 水辺の将 |
| dialogue speaker | アレス |
| dialogue text | 川が行く手を阻んでいる...。橋を確保して渡る他ない。 |
| dialogue speaker | マリア |
| dialogue text | 天馬騎士がいるわ！弓で落とすから、橋は任せて！ |
| dialogue speaker | エリック |
| dialogue text | 水辺の敵は魔法が効きやすい。援護します。 |

### core/assets/maps/chapter_6.json

| フィールド | テキスト |
|-----------|---------|
| name | 第6章 - 王城攻略戦 |
| enemy name | 城門守備兵A |
| enemy name | 城門守備兵B |
| enemy name | 近衛騎士A |
| enemy name | 近衛騎士B |
| enemy name | 宮廷魔道士A |
| enemy name | 宮廷魔道士B |
| enemy name | 城壁射手 |
| enemy name | 暗黒将軍ヴォルク |
| dialogue speaker | アレス |
| dialogue text | ついにここまで来た...。暗黒将軍ヴォルクを倒し、王城を取り戻す！ |
| dialogue speaker | リーナ |
| dialogue text | 最後の戦い...全力で行きましょう！ |
| dialogue speaker | エリック |
| dialogue text | 城内は敵の精鋭ばかりだ。油断せず行こう。 |
| dialogue speaker | マリア |
| dialogue text | みんな、ここまで来られたのはチームの力よ。最後まで一緒に戦うわ！ |

### core/assets/maps/another_chapter.json

| フィールド | テキスト |
|-----------|---------|
| name | ランダムマップ - 遭遇戦 |

---

## 2. Kotlinソースファイルの日本語文字列

### TerrainType.kt（displayName値）

| 文字列 | 用途 |
|--------|------|
| 平地 | 地形表示名 |
| 森 | 地形表示名 |
| 山 | 地形表示名 |
| 砦 | 地形表示名 |
| 水域 | 地形表示名 |
| 壁 | 地形表示名 |
| 村 | 地形表示名 |
| 橋 | 地形表示名 |

### UnitClass.kt（displayName値）

| 文字列 | 用途 |
|--------|------|
| ロード | クラス表示名 |
| ソードファイター | クラス表示名 |
| ランサー | クラス表示名 |
| アクスファイター | クラス表示名 |
| アーチャー | クラス表示名 |
| メイジ | クラス表示名 |
| ヒーラー | クラス表示名 |
| ナイト | クラス表示名 |
| ペガサスナイト | クラス表示名 |
| アーマーナイト | クラス表示名 |

### UnitTactic.kt（displayName / description）

| 文字列 | 用途 |
|--------|------|
| 勇猛果敢に戦え | 作戦名（CHARGE） |
| 一番近い敵に向かって移動し、攻撃を試みる | 作戦説明（CHARGE） |
| 後の先を狙え | 作戦名（CAUTIOUS） |
| 敵の攻撃圏外から攻撃を狙う | 作戦説明（CAUTIOUS） |
| 味方を援護しろ | 作戦名（SUPPORT） |
| 味方が狙っている敵を優先して攻撃する | 作戦説明（SUPPORT） |
| 逃げまどえ | 作戦名（FLEE） |
| 敵から離れるように移動する | 作戦説明（FLEE） |

### ResultScreen.kt（UI表示）

| 文字列 | 用途 |
|--------|------|
| 勝利！ | 結果タイトル |
| 敗北... | 結果タイトル |
| タップして続ける | ガイド |

### BattleResultScreen.kt（UI表示）

| 文字列 | 用途 |
|--------|------|
| 勝 利 ！ | 結果タイトル |
| 敗 北 ... | 結果タイトル |
| 経過ラウンド: | 統計ラベル |
| 撃破した敵: | 統計ラベル |
| — 生存ユニット — | セクションヘッダ |
| なし | 空リスト表示 |
| タップして続ける | ガイド |

### BattleResultScreen.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| 勝利! チャプタークリア: ... | Gdx.app.log |
| 敗北... | Gdx.app.log |

### WorldMapScreen.kt（UI表示）

| 文字列 | 用途 |
|--------|------|
| — ワールドマップ — | 画面タイトル |
| 部隊編成 | ボタンラベル |
| 最大出撃数: ... | 情報表示 |
| 現在の出撃メンバー: ${deployedCount}人 | 情報表示 |
| ▶ タップで出撃 | ボタンラベル |

### WorldMapScreen.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| ワールドマップ画面を表示 | Gdx.app.log |
| 編成画面へ遷移 | Gdx.app.log |
| チャプター選択: ... | Gdx.app.log |
| チャプターフォーカス: ... | Gdx.app.log |

### FormationScreen.kt（UI表示）

| 文字列 | 用途 |
|--------|------|
| — 部隊編成 — | 画面タイトル |
| 出撃メンバー: $currentDeploy / $maxDeploy | 表示ラベル |
| 素手 | 武器なし時表示 |
| 出撃 | ボタンラベル |
| 外す | ボタンラベル |
| 出撃する | ボタンラベル |
| 装備変更 | ボタンラベル |
| 作戦: ${unit.tactic.displayName} | 情報表示 |
| ← 戻る | ボタンラベル |
| 威力: | 武器ステータス |
| 命中: | 武器ステータス |
| 重さ: | 武器ステータス |

### FormationScreen.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| 部隊編成画面を表示 | Gdx.app.log |
| ワールドマップへ戻る | Gdx.app.log |

### BattlePrepScreen.kt（UI表示）

| 文字列 | 用途 |
|--------|------|
| 戦闘準備 - ${chapter.name} | 画面タイトル |
| 出撃メンバー (${deployedCount}人) | ラベル |
| 敵: ${enemyUnits.size}体 | ラベル |
| 別の位置をタップで入替 | ガイド |
| ユニットをタップで配置変更 | ガイド |
| ▶ 出撃開始 | ボタンラベル |
| ← 戻る | ボタンラベル |

### BattlePrepScreen.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| チャプター未選択、ワールドマップに戻る | Gdx.app.log |
| 戦闘準備画面: ... | Gdx.app.log |
| マップ読み込み完了: ... | Gdx.app.log |
| バトル開始: ... | Gdx.app.log |

### BattleScreen.kt（UI表示）

| 文字列 | 用途 |
|--------|------|
| ${activeUnit.name} のターン | ターン表示 |
| 素手 (射程1) | 武器なし表示 |
| 撤退 | ボタンラベル |
| 撤退しますか？ | ダイアログメッセージ |
| はい | ボタンラベル |
| いいえ | ボタンラベル |
| テストマップ | テストデータ用マップ名 |

### BattleScreen.kt テストデータ（createTestMap内）

| 文字列 | 用途 |
|--------|------|
| アレス | テスト用ユニット名 |
| リーナ | テスト用ユニット名 |
| マリア | テスト用ユニット名 |
| 山賊A | テスト用敵名 |
| 山賊B | テスト用敵名 |
| 盗賊 | テスト用敵名 |
| 鉄の剣 | テスト用武器名 |
| 鉄の槍 | テスト用武器名 |
| 鉄の弓 | テスト用武器名 |
| 鉄の斧 | テスト用武器名 |

### BattleScreen.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| バトル画面初期化完了（CTベースターン制） | Gdx.app.log |
| 撤退ボタン押下 → 確認ダイアログ表示 | Gdx.app.log |
| 撤退を実行 | Gdx.app.log |
| 撤退をキャンセル | Gdx.app.log |
| AI待機: ... | Gdx.app.log |
| AI移動完了: ... | Gdx.app.log |
| AI攻撃: ... | Gdx.app.log |

### WeaponEquipScreen.kt（UI表示）

| 文字列 | 用途 |
|--------|------|
| — 装備変更 — | 画面タイトル |
| 【右手】 | スロットラベル |
| 【左手】 | スロットラベル |
| 【防具1】 | スロットラベル |
| 【防具2】 | スロットラベル |
| （なし） | 空スロット表示 |
| 二刀流: ON (ペナ-...) | ステータス表示 |
| 二刀流: 可 | ステータス表示 |
| 二刀流: × | ステータス表示 |
| 実効SPD: | ステータス表示 |
| 在庫がありません | 空在庫時メッセージ |
| 【パーティ在庫: 武器】 | セクションヘッダ |
| 【パーティ在庫: 防具】 | セクションヘッダ |
| 在庫 | ボタンラベル |
| 外す | ボタンラベル |
| 右手に装備 | ボタンラベル |
| 左手に装備 | ボタンラベル |
| 防具1に装備 | ボタンラベル |
| 防具2に装備 | ボタンラベル |
| ← 戻る | ボタンラベル |
| 威力: | 武器ステータス |
| 命中: | 武器ステータス |
| 必殺: | 武器ステータス |
| 重さ: | 武器ステータス |
| 射程: | 武器ステータス |

### WeaponEquipScreen.kt — weaponTypeLabel()

| 文字列 | 用途 |
|--------|------|
| 剣 | 武器種別表示 |
| 槍 | 武器種別表示 |
| 斧 | 武器種別表示 |
| 弓 | 武器種別表示 |
| 魔法 | 武器種別表示 |
| 杖 | 武器種別表示 |

### WeaponEquipScreen.kt — armorTypeLabel()

| 文字列 | 用途 |
|--------|------|
| 軽装 | 防具種別表示 |
| 重装 | 防具種別表示 |
| 盾 | 防具種別表示 |
| ローブ | 防具種別表示 |
| 装飾品 | 防具種別表示 |
| 頭 | 防具種別表示 |
| 足 | 防具種別表示 |

### WeaponEquipScreen.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| 装備変更画面を表示: ... | Gdx.app.log |
| 部隊編成画面へ戻る | Gdx.app.log |

### BattleSystem.kt（エラーメッセージ）

| 文字列 | 用途 |
|--------|------|
| 攻撃ユニットがマップ上に存在しません: ... | error() |
| 防御ユニットがマップ上に存在しません: ... | error() |

### FontManager.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| フォント生成完了: size=$size | Gdx.app.log |
| FreeTypeFontGenerator 初期化完了: ... | Gdx.app.log |
| FontManager リソース解放完了 | Gdx.app.log |

### MapLoader.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| マップファイルが見つかりません: ... | Gdx.app.log |
| 武器データファイルが見つかりません: ... | Gdx.app.log |
| 武器マスターデータ読み込み完了: ...件 | Gdx.app.log |
| 武器データ読み込みエラー | Gdx.app.error |
| 防具データファイルが見つかりません: ...（省略） | Gdx.app.log |
| 防具マスターデータ読み込み完了: ...件 | Gdx.app.log |
| 防具データ読み込みエラー | Gdx.app.error |
| マップ読み込み完了: ... | Gdx.app.log |
| マップ読み込みエラー: ... | Gdx.app.log |
| 不明なクラスID: ... (ユニット: ...) | Gdx.app.error |
| 武器マスターに未登録: ... (ユニット: ...)。フォールバック生成。 | Gdx.app.log |
| フォールバック武器生成: ... → ... (ユニット: ...) | Gdx.app.log |
| 防具マスターに未登録: ... (ユニット: ...) | Gdx.app.log |
| ランダム敵生成完了: ...体 (平均Lv....) | Gdx.app.log |
| 不明な勝利条件: ... → DEFEAT_ALL にフォールバック | Gdx.app.log |
| 不明な地形タイプ: ... | Gdx.app.error |
| 不明な武器タイプ: ... → SWORD にフォールバック | Gdx.app.error |
| 不明な防具タイプ: ... → LIGHT_ARMOR にフォールバック | Gdx.app.error |

### MapLoader.kt（ランダム敵テンプレート名）

| 文字列 | 用途 |
|--------|------|
| 剣士 | ランダム敵名のベース |
| 槍兵 | ランダム敵名のベース |
| 斧戦士 | ランダム敵名のベース |
| 弓兵 | ランダム敵名のベース |
| 魔道士 | ランダム敵名のベース |

### SaveManager.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| セーブ完了: ... | Gdx.app.log |
| セーブ失敗: ... | Gdx.app.error |
| セーブデータが見つかりません。新規ゲームです。 | Gdx.app.log |
| ロード完了 | Gdx.app.log |
| ロード失敗: ... | Gdx.app.error |
| セーブデータ削除完了 | Gdx.app.log |
| セーブデータ削除失敗: ... | Gdx.app.error |
| セーブデータバージョン: ... | Gdx.app.log |
| セーブデータが現在のアプリより新しいバージョンです（データ: v...、アプリ: v...） | Gdx.app.error |
| 不明なクラスID: ... | Gdx.app.error |
| 不明な陣営: ... | Gdx.app.error |
| 不明な武器タイプ: ...、SWORD で代替 | Gdx.app.error |
| 不明な防具タイプ: ...、LIGHT_ARMOR で代替 | Gdx.app.error |
| ユニット読み込み失敗: ... | Gdx.app.error |

### ScreenManager.kt（ログ出力 / エラーメッセージ）

| 文字列 | 用途 |
|--------|------|
| 画面遷移: → Title | Gdx.app.log |
| 画面遷移: → WorldMap | Gdx.app.log |
| 画面遷移: → Formation | Gdx.app.log |
| 画面遷移: → BattlePrep (...) | Gdx.app.log |
| 画面遷移: → Battle (...) | Gdx.app.log |
| 画面遷移: → BattleResult (勝利: ...) | Gdx.app.log |
| 画面遷移: → WeaponEquip (...) | Gdx.app.log |
| weaponEquipTarget が null です | requireNotNull メッセージ |

### TacticsFlameGame.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| ゲーム初期化開始 | Gdx.app.log |
| セーブデータロード: 成功 / 失敗（新規データで開始） | Gdx.app.log |
| リソース解放完了 | Gdx.app.log |

### GameProgress.kt（チャプター定義 — UI表示データ）

| 文字列 | 用途 |
|--------|------|
| 第1章 - 始まりの戦い | チャプター名 |
| 山賊のアジトを制圧せよ。 | チャプター説明 |
| 第2章 - 国境の防衛線 | チャプター名 |
| 敵軍の侵攻を防げ。 | チャプター説明 |
| 第3章 - 暗黒の森 | チャプター名 |
| 森を抜けて要塞を目指せ。 | チャプター説明 |
| 第4章 - 騎士団の砦 | チャプター名 |
| 砦を守る騎士団を撃破せよ。 | チャプター説明 |
| 第5章 - 水辺の決戦 | チャプター名 |
| 川を渡り敵陣を突破せよ。 | チャプター説明 |
| 第6章 - 王城攻略戦 | チャプター名 |
| 暗黒将軍ヴォルクを倒し、王城を奪還せよ。 | チャプター説明 |
| ランダムマップ - 遭遇戦 | チャプター名 |
| パーティの平均レベルに応じた敵が出現する特殊マップ。何度でも挑戦可能。 | チャプター説明 |

### GameProgress.kt（初期パーティ — ハードコードデータ）

| 文字列 | 用途 |
|--------|------|
| アレス | ユニット名 |
| 鉄の剣 | 武器名 |
| 革の鎧 | 防具名 |
| リーナ | ユニット名 |
| 鉄の槍 | 武器名 |
| 鎖帷子 | 防具名 |
| マリア | ユニット名 |
| 鉄の弓 | 武器名 |
| エリック | ユニット名 |
| ファイアー | 武器名 |
| 魔法のローブ | 防具名 |

### GameProgress.kt（初期在庫 — ハードコードデータ）

| 文字列 | 用途 |
|--------|------|
| 鋼の剣 | 武器名 |
| 鉄の斧 | 武器名 |
| 鉄の槍 | 武器名 |
| 鉄の剣 | 武器名 |
| ファイアー | 武器名 |
| ライブ | 武器名 |
| 鉄の鎧 | 防具名 |
| 鉄の盾 | 防具名 |
| 革の鎧 | 防具名 |
| 守りの護符 | 防具名 |
| 疾風の指輪 | 防具名 |
| 鉄の兜 | 防具名（HEAD） |
| 革の靴 | 防具名（FEET） |
| 鉄の脛当て | 防具名（FEET） |

### GameProgress.kt（ログ出力）

| 文字列 | 用途 |
|--------|------|
| ゲーム進行状態を初期化（チャプター: ...、ユニット: ...） | Gdx.app.log |
| チャプター完了: ... | Gdx.app.log |
| 初期在庫配布完了（武器: ...、防具: ...） | Gdx.app.log |

---

## 3. ユニークな漢字一覧（重複排除・五十音順）

全テキスト（JSON + Kotlin文字列リテラル）から自動抽出した漢字を、重複排除のうえUnicodeコードポイント順にソートしたものです。

```
一 上 下 不 中 了 二 人 付 代 件 伏 位 体 何 作 侵 係 保 倒 値 停 備 傭 優 先
兜 入 全 兵 具 内 出 刀 列 初 別 利 到 制 削 前 剣 力 功 効 勇 動 勝 化 北 危 厚
反 可 右 合 同 名 向 含 周 味 命 品 営 噂 器 回 団 囲 国 圏 圧 在 地 均 型 城 域
基 報 場 塞 境 壁 士 変 外 多 大 天 失 奥 奪 始 威 子 字 存 守 完 定 実 宮 対 射
将 小 山 川 左 巨 差 布 帷 平 度 庫 廷 弓 強 当 形 待 後 得 御 必 応 悪 情 意 感
態 慎 成 戦 戻 所 手 抜 択 押 持 指 挑 掃 接 援 撃 撤 攻 放 敗 敢 数 敵 文 斧 断
新 方 明 普 暗 更 替 最 有 期 未 本 村 杖 条 来 果 査 森 構 槍 橋 機 止 正 武 殊
残 殺 気 水 決 河 油 法 注 流 済 渡 満 準 滅 潜 点 特 状 狙 猛 獲 王 現 生 画 界
略 番 疾 登 盗 目 直 盾 省 砦 破 確 示 移 程 種 空 突 章 符 第 等 箇 築 素 終 経
結 継 続 線 編 繰 置 者 能 脛 致 行 衛 表 装 補 要 見 規 視 解 設 試 認 読 調 護
象 負 賊 賢 越 足 距 路 軍 軽 輪 辺 込 迂 近 返 追 退 逃 通 速 進 遅 遇 過 道 達
遠 遭 遷 選 還 部 配 重 量 鉄 鋼 録 鎖 鎧 長 門 開 闇 闘 防 限 陣 除 険 隊 隣 離
面 革 靴 頭 風 飛 飾 馬 騎 験 高 魔 黒
```

合計: 336 字（自動抽出）

---

## 備考

- **日本語文字列を含まないKotlinファイル**: `PathFinder.kt`, `DamageCalc.kt`, `WeaponTriangle.kt`, `BattleMap.kt`, `Tile.kt`, `Position.kt`, `Stats.kt`, `GrowthRate.kt`, `Armor.kt`, `Weapon.kt`, `GameUnit.kt`, `ChapterInfo.kt`, `PartyState.kt`, `BattleConfig.kt`, `BattleResultData.kt`, `AssetPaths.kt`, `GameConfig.kt` — これらはコメント（KDoc）にのみ日本語が含まれ、文字列リテラルには日本語が含まれません。
- `GameConfig.TITLE` は `"Tactics Flame"`（英語）です。
- `TitleScreen.kt` の "Tap to Start" は英語のため、上記一覧には含まれていません。
