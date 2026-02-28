// core モジュール - ゲームロジック・プラットフォーム非依存コード
plugins {
    kotlin("jvm")
}

val gdxVersion = "1.12.1"

dependencies {
    // LibGDX コア
    api("com.badlogicgames.gdx:gdx:$gdxVersion")

    // テスト
    testImplementation(kotlin("test"))
    testImplementation("com.badlogicgames.gdx:gdx-backend-headless:$gdxVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}
