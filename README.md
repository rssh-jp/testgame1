# Tactics Flame

LibGDX + Kotlin で実装している Android 向けタクティカル RPG です。

詳細仕様は [docs/spec/README.md](docs/spec/README.md) を参照してください。

## 前提条件

- Android SDK がインストール済みであること
- `local.properties` に `sdk.dir` が設定されていること
- JDK 21 が利用可能であること

`JAVA_HOME` が未設定で Gradle が失敗する場合は、Android Studio 同梱 JBR を使えます。

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## Android Debug ビルド

```powershell
Set-Location "c:\Users\tarau\home\prj\github\testgame1"
.\gradlew.bat :android:assembleDebug
```

出力先:

- `android/build/outputs/apk/debug/android-debug.apk`

## エミュレータ起動

利用可能な AVD を確認します。

```powershell
$sdk='C:\Users\tarau\AppData\Local\Android\Sdk'
& "$sdk\emulator\emulator.exe" -list-avds
```

例: `Medium_Phone_API_36.1` を起動する場合。

```powershell
$sdk='C:\Users\tarau\AppData\Local\Android\Sdk'
& "$sdk\emulator\emulator.exe" -avd Medium_Phone_API_36.1
```

起動確認:

```powershell
$sdk='C:\Users\tarau\AppData\Local\Android\Sdk'
& "$sdk\platform-tools\adb.exe" devices -l
& "$sdk\platform-tools\adb.exe" -s emulator-5554 shell getprop sys.boot_completed
```

`sys.boot_completed` が `1` なら起動完了です。

## エミュレータへ転送

起動済みエミュレータに Debug APK をインストールします。

```powershell
Set-Location "c:\Users\tarau\home\prj\github\testgame1"
.\gradlew.bat :android:installDebug
```

直接 `adb` で入れる場合:

```powershell
$sdk='C:\Users\tarau\AppData\Local\Android\Sdk'
& "$sdk\platform-tools\adb.exe" -s emulator-5554 install -r \
  "c:\Users\tarau\home\prj\github\testgame1\android\build\outputs\apk\debug\android-debug.apk"
```

インストール確認:

```powershell
$sdk='C:\Users\tarau\AppData\Local\Android\Sdk'
& "$sdk\platform-tools\adb.exe" -s emulator-5554 shell pm list packages com.tacticsflame
```

## アプリ起動

```powershell
$sdk='C:\Users\tarau\AppData\Local\Android\Sdk'
& "$sdk\platform-tools\adb.exe" -s emulator-5554 shell monkey -p com.tacticsflame -c android.intent.category.LAUNCHER 1
```

## 今回確認した内容

- `Medium_Phone_API_36.1` のエミュレータ起動
- `com.tacticsflame` のインストール確認
- ランチャー起動確認