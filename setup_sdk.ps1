$ErrorActionPreference = "Stop"
$sdkDir = "d:\Hacking\Android-SDK"
$zipPath = "d:\Hacking\cmdline-tools.zip"
$toolsDir = "$sdkDir\cmdline-tools"

if (!(Test-Path -Path $sdkDir)) {
    New-Item -ItemType Directory -Path $sdkDir -Force | Out-Null
}

Write-Host ">>> [1/4] Dang tai Android SDK Commandline Tools (140MB)..." -ForegroundColor Cyan
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
$wc = New-Object System.Net.WebClient
$wc.DownloadFile("https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip", $zipPath)

Write-Host ">>> [2/4] Dang giai nen..." -ForegroundColor Cyan
Expand-Archive -Path $zipPath -DestinationPath "$toolsDir\temp" -Force
Remove-Item $zipPath -Force

if (Test-Path "$toolsDir\latest") {
    Remove-Item "$toolsDir\latest" -Recurse -Force
}
Move-Item "$toolsDir\temp\cmdline-tools" "$toolsDir\latest"
Remove-Item "$toolsDir\temp" -Recurse -Force

Write-Host ">>> [3/4] Dang cai dat Android SDK 35 va Build-Tools..." -ForegroundColor Cyan
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$env:PATH = "$env:JAVA_HOME\bin;$toolsDir\latest\bin;$env:PATH"

cmd.exe /c "echo y | `"$toolsDir\latest\bin\sdkmanager.bat`" --sdk_root=`"$sdkDir`" `"platforms;android-35`" `"build-tools;35.0.0`""

Write-Host ">>> [4/4] Tao file local.properties..." -ForegroundColor Cyan
Set-Content -Path "d:\Hacking\GMC\local.properties" -Value "sdk.dir=d\:\\Hacking\\Android-SDK"

Write-Host ">>> THIET LAP SDK THANH CONG! Bat dau build APK..." -ForegroundColor Green
cmd.exe /c "cd /d d:\Hacking\GMC && gradlew.bat assembleDebug"

Write-Host ">>> Cai dat app truc tiep vao Xiaomi 15 Ultra..." -ForegroundColor Cyan
$apk = "d:\Hacking\GMC\app\build\outputs\apk\debug\app-debug.apk"
if (Test-Path $apk) {
    & "d:\Hacking\GMC\platform-tools\adb.exe" install -r $apk
    Write-Host ">>> CAI DAT THANH CONG APP-DEBUG.APK LEN XIAOMI 15 ULTRA!" -ForegroundColor Green
} else {
    Write-Host "Chua tim thay file APK sau khi build." -ForegroundColor Red
}
