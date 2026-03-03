# Tactics Flame - ProGuard ルール
# Android デフォルトルールは build.gradle.kts で proguard-android-optimize.txt として追加済み

# ========================================
# LibGDX 共通ルール
# ========================================
-keep class com.badlogic.** { *; }
-keepclassmembers class com.badlogic.** { *; }
-dontwarn com.badlogic.**

# OpenGL ES / ネイティブメソッド
-keepclasseswithmembernames class * {
    native <methods>;
}

# ========================================
# Tactics Flame アプリ本体
# ========================================
-keep class com.tacticsflame.** { *; }
-keepclassmembers class com.tacticsflame.** { *; }

# JSON デシリアライズ対象のデータクラスを保護
-keepclassmembers class com.tacticsflame.model.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class com.tacticsflame.data.** {
    <fields>;
    <init>(...);
}

# ========================================
# Kotlin
# ========================================
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclasseswithmembers class * {
    @kotlin.Metadata *;
}
-keepclassmembers class * {
    ** INSTANCE;
}

# kotlin.jvm.functions
-keep interface kotlin.jvm.functions.** { *; }

# ========================================
# AndroidX / Android 基本
# ========================================
-dontwarn androidx.**
-keep class androidx.** { *; }

# ========================================
# デバッグ情報の保持（クラッシュレポート用）
# ========================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ========================================
# JSON / Gson（LibGDX Json を使っているが念のため）
# ========================================
-keepattributes Signature
-keepattributes *Annotation*
