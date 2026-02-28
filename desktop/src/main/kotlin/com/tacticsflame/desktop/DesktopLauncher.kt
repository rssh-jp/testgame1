package com.tacticsflame.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.tacticsflame.TacticsFlameGame
import com.tacticsflame.core.GameConfig

/**
 * デスクトップ版のランチャー（開発・デバッグ用）
 */
fun main() {
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle(GameConfig.TITLE)
        setWindowedMode(1280, 720)
        setForegroundFPS(GameConfig.TARGET_FPS)
        useVsync(true)
    }
    Lwjgl3Application(TacticsFlameGame(), config)
}
