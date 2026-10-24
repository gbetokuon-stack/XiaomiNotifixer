# Xiaomi & HyperOS Notification Fixer (Dành cho Xiaomi 15 Ultra & Mọi máy Xiaomi/MIUI)

Ứng dụng Android chuyên sâu khắc phục triệt để tình trạng **chậm, trễ hoặc mất thông báo** (Zalo, Messenger, Telegram, Gmail...) trên dòng máy Xiaomi 15 Ultra chạy HyperOS 2 / Android 15 và các dòng Xiaomi, Redmi, POCO.

---

## 1. Nguyên nhân Xiaomi 15 Ultra bị trễ / mất thông báo
1. **NAT Socket Timeout (Google FCM)**: Khi tắt màn hình quá 5–10 phút, router Wi-Fi hoặc trạm 4G/5G xóa bảng định tuyến NAT của kết nối TCP FCM (`mtalk.google.com:5228`). Máy chủ Google không gửi được gói tin push về máy.
2. **Cơ chế PowerKeeper & Joyose của HyperOS**: Tự động chuyển ứng dụng nền sang trạng thái ngủ sâu (Deep Sleep) hoặc giết tiến trình nền sau 10 phút khóa màn hình.
3. **Chặn quyền Tự khởi chạy (Autostart)**: Ứng dụng khi bị hệ thống tắt sẽ không thể tự thức dậy khi có tin nhắn mới gửi đến.
4. **Android Doze Whitelist**: Hệ thống hạn chế truy cập mạng và wakelock trong chế độ Doze.

---

## 2. Giải pháp tích hợp trong ứng dụng
- **FCM Keep-Alive Engine**: Foreground Service chạy ngầm giữ kết nối TCP tới `mtalk.google.com:5228` và phát broadcast `GCM_RECONNECT` định kỳ mỗi 2 phút. Đảm bảo cổng NAT luôn mở, push notification nhận tức thì 0 giây trễ.
- **Tự Động Hóa 1-Chạm qua Shizuku / ADB**:
  Tự động gán quyền chạy nền vĩnh viễn cho danh sách ứng dụng chỉ với 1 click:
  ```bash
  dumpsys deviceidle whitelist +<package>
  cmd appops set <package> RUN_IN_BACKGROUND allow
  cmd appops set <package> RUN_ANY_IN_BACKGROUND allow
  cmd appops set <package> AUTO_START allow
  cmd appops set <package> WAKE_LOCK allow
  ```
- **Deep Links Cài Đặt Hệ Thống Xiaomi**:
  - Tự khởi chạy (Autostart Management)
  - Tiết kiệm pin HyperOS -> Không hạn chế (No restrictions)
  - Quyền bỏ tối ưu hóa pin hệ thống (Doze Whitelist)
  - Quyền Trình đọc thông báo (Notification Listener) giúp app có `oom_adj` ưu tiên cao nhất, miễn nhiễm bị văng RAM.
- **Khởi Động Cùng Hệ Thống (Boot Completed)**: Tự phục hồi service sau khi khởi động lại máy.

---

## 3. Cách Build File APK

### Cách 1: Bằng Android Studio
1. Mở thư mục dự án `d:\Hacking\GMC` trong Android Studio (Ladybug / Koala hoặc mới hơn).
2. Chờ Gradle sync hoàn tất.
3. Vào menu **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
4. File APK sẽ được tạo tại: `app/build/outputs/apk/debug/app-debug.apk`.

### Cách 2: Bằng Dòng Lệnh Gradle
```bash
./gradlew assembleRelease
# Hoặc debug:
./gradlew assembleDebug
```

---

## 4. Hướng dẫn thiết lập trên Xiaomi 15 Ultra để đạt hiệu quả tối đa
1. Mở app **Xiaomi Notification Fixer** -> Bật công tắc **FCM Keep-Alive Service**.
2. Nhấn vào từng nút cài đặt cốt lõi:
   - **Tự khởi chạy**: Bật cho ứng dụng cần nhận thông báo (Zalo, Messenger...).
   - **Tiết kiệm pin**: Chọn **Không hạn chế** (No restrictions).
   - **Bỏ tối ưu hóa pin**: Nhấn **Cho phép** (Allow).
3. (Tùy chọn khuyến nghị): Kích hoạt app **Shizuku** -> Vào mục **Chọn Ứng Dụng Cần Fix** -> Nhấn **Áp Dụng Fix Triệt Để** để hệ thống whitelist hoàn toàn nền.
4. Khóa đa nhiệm (Lock in Recents): Vuốt mở giao diện đa nhiệm -> Nhấn giữ vào app cần nhận thông báo -> Bấm biểu tượng Ổ Khóa.
"# XiaomiNotifixer" 
