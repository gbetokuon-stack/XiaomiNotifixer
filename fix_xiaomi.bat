@echo off
chcp 65001 >nul
set "ADB=d:\Hacking\GMC\platform-tools\adb.exe"

echo [1/3] Đang kiểm tra kết nối điện thoại Xiaomi...
%ADB% devices -l

echo.
echo [2/3] Đang tắt Phantom Process Killer (Chống HyperOS kill tiến trình ngầm)...
%ADB% shell "/system/bin/device_config put activity_manager max_phantom_processes 2147483647" 2>nul
%ADB% shell "setprop persist.sys.miui_optimization 1" 2>nul

echo.
echo [3/3] Đang cấp quyền chạy ngầm vĩnh viễn và Whitelist Doze cho các ứng dụng...
set APPS=com.zing.zalo com.facebook.orca com.facebook.katana org.telegram.messenger org.thunderdog.challegram com.whatsapp com.google.android.gm com.instagram.android com.viber.voip com.discord com.microsoft.office.outlook com.google.android.gms com.google.android.gsf

for %%a in (%APPS%) do (
    echo - Đang fix: %%a
    %ADB% shell "dumpsys deviceidle whitelist +%%a" >nul 2>&1
    %ADB% shell "cmd appops set %%a RUN_IN_BACKGROUND allow" >nul 2>&1
    %ADB% shell "cmd appops set %%a RUN_ANY_IN_BACKGROUND allow" >nul 2>&1
    %ADB% shell "cmd appops set %%a AUTO_START allow" >nul 2>&1
    %ADB% shell "cmd appops set %%a WAKE_LOCK allow" >nul 2>&1
    %ADB% shell "cmd appops set %%a START_FOREGROUND allow" >nul 2>&1
    %ADB% shell "cmd appops set %%a POST_NOTIFICATION allow" >nul 2>&1
    %ADB% shell "cmd appops set %%a SCHEDULE_EXACT_ALARM allow" >nul 2>&1
)

echo.
echo ========================================================
echo HOÀN TẤT TỐI ƯU HÓA THÔNG BÁO CHO XIAOMI 15 ULTRA!
echo ========================================================
pause
