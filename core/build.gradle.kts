// core モジュール - ゲームロジック・プラットフォーム非依存コード
plugins {
    kotlin("jvm")
}

val gdxVersion = "1.12.1"

dependencies {
    // LibGDX コア
    api("com.badlogicgames.gdx:gdx:$gdxVersion")

    // FreeType フォント拡張（日本語等の Unicode フォント対応）
    api("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")

    // テスト
    testImplementation(kotlin("test"))
    testImplementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
    testImplementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
}

// テスト実行時の作業ディレクトリをアセットフォルダに設定
// Gdx.files.internal() がマップ JSON などを解決できるようにする
tasks.test {
    workingDir = file("assets")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}
