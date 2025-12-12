gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest && ^
adb install -r -t app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk && ^
adb install -r -t app\build\outputs\apk\debug\app-debug.apk