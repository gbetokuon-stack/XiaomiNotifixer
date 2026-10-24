$adb = "d:\Hacking\GMC\platform-tools\adb.exe"

Write-Host ">>> [1/4] Thiet lap Phantom Process Killer..." -ForegroundColor Cyan
& $adb shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647"

Write-Host ">>> [2/4] Rut ngan chu ky FCM Push Heartbeat xuong 2 phut..." -ForegroundColor Cyan
& $adb shell "settings put global gcm_heartbeat_interval_wifi 120000"
& $adb shell "settings put global gcm_heartbeat_interval_mobile 120000"
& $adb shell "dumpsys deviceidle whitelist +com.google.android.gms"
& $adb shell "dumpsys deviceidle whitelist +com.google.android.gsf"

Write-Host ">>> [3/4] Mo khoa chay ngam & Whitelist Doze cho toan bo ung dung..." -ForegroundColor Cyan
$packages = @(
    "com.zing.zalo",
    "com.facebook.orca",
    "com.facebook.katana",
    "com.facebook.lite",
    "org.telegram.messenger",
    "com.whatsapp",
    "com.google.android.gm",
    "com.microsoft.office.outlook",
    "com.discord",
    "com.locket.Locket",
    "com.instagram.barcelona",
    "com.twitter.android",
    "com.tencent.mm",
    "com.shopee.vn",
    "com.lazada.android",
    "com.mservice.momotransfer",
    "vn.com.vng.zalopay",
    "vn.com.techcombank.bb.app",
    "com.vnpay.bidv",
    "com.VCB",
    "com.tpb.mb.gprsandroid",
    "com.vietinbank.ipay",
    "com.mbmobile",
    "com.binance.dev",
    "com.bybit.app",
    "com.okinc.okex.gp",
    "com.wallet.crypto.trustapp",
    "xyz.be.customer",
    "com.grabtaxi.passenger",
    "com.deliverynow",
    "com.openai.chatgpt",
    "com.deepseek.chat"
)

foreach ($pkg in $packages) {
    Write-Host "  -> Dang toi uu: $pkg"
    & $adb shell "dumpsys deviceidle whitelist +$pkg"
    & $adb shell "cmd appops set $pkg RUN_IN_BACKGROUND allow"
    & $adb shell "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow"
    & $adb shell "cmd appops set $pkg AUTO_START allow"
    & $adb shell "cmd appops set $pkg WAKE_LOCK allow"
    & $adb shell "cmd appops set $pkg START_FOREGROUND allow"
    & $adb shell "cmd appops set $pkg POST_NOTIFICATION allow"
    & $adb shell "cmd appops set $pkg SCHEDULE_EXACT_ALARM allow"
}

Write-Host ">>> [4/4] Kiem tra ket qua Whitelist Doze tren Xiaomi 15 Ultra..." -ForegroundColor Cyan
& $adb shell "dumpsys deviceidle whitelist"

Write-Host "HOAN TAT TOI UU NEN TOAN BO UNG DUNG!" -ForegroundColor Green
