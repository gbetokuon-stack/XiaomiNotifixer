# 🚀 Xiaomi Notifixer (HyperOS & MIUI)

Giải pháp toàn diện khắc phục triệt để vấn đề **chậm, trễ hoặc mất thông báo FCM (Firebase Cloud Messaging)** trên các dòng máy Xiaomi, Redmi, POCO chạy **HyperOS 1, HyperOS 2 (Android 14 - 15)** và **MIUI**.

---

## 📥 Tải Nhanh File Cài Đặt (APK)

Bạn có thể tải trực tiếp file APK đã được biên dịch sẵn:

👉 **[Tải XiaomiNotificationFixer.apk (Direct Download)](https://github.com/gbetokuon-stack/XiaomiNotifixer/raw/main/XiaomiNotificationFixer.apk)**

Hoặc tải bản mới nhất được GitHub Actions tự động build tại tab **[Actions](../../actions)**.

---

## ⚡ Nguyên Nhân Trễ Thông Báo Trên HyperOS

1. **NAT Timeout của Nhà Mạng:** Router Wi-Fi hoặc mạng 4G/5G thường đóng kết nối TCP không hoạt động sau 5-15 phút. Chu kỳ FCM mặc định của Google lên đến 15-28 phút khiến điện thoại mất liên lạc với server Google cho đến khi màn hình bật.
2. **Cơ chế PowerKeeper & Joyose của HyperOS:** Trình quản lý pin đóng băng (freeze) hoặc kill tiến trình background của các app nhắn tin sau 10 phút khóa màn hình.
3. **Phantom Process Killer:** Android 12 trở lên giới hạn số tiến trình con chạy ngầm (tối đa 32), tự động terminate khi vượt ngưỡng.
4. **Android Doze & Quyền Autostart:** Hệ thống hạn chế truy cập mạng và wakelock trong chế độ Doze nếu app không được whitelist.

---

## 🛠️ Giải Pháp Của Dự Án

Dự án kết hợp **2 tầng tối ưu**:

### 1. Ứng Dụng Android Chạy Nền (`XiaomiNotificationFixer.apk`)
* **FCM Heartbeat Pinger:** Chủ động ping socket TCP tới các máy chủ Google FCM (`mtalk.google.com:5228`) định kỳ mỗi **120 giây**, đảm bảo tunnel kết nối luôn mở, push notification nhận tức thì.
* **Notification Listener Service:** Tận dụng đặc quyền của hệ thống Android — tiến trình sở hữu Notification Listener Service không bị hệ điều hành tiêu diệt khi thiếu RAM.
* **Tích Hợp Shizuku:** Cấp quyền tối ưu hệ thống và whitelist app trực tiếp ngay trên điện thoại mà không cần cắm cáp vào máy tính.
* **Tự khởi động cùng hệ thống:** Lắng nghe broadcast `BOOT_COMPLETED` để tự duy trì dịch vụ sau khi khởi động lại máy.

### 2. Kịch Bản Tối Ưu Hệ Thống Qua ADB (`fix_xiaomi.bat` / `apply_fix.ps1`)
* Tăng trần **Phantom Process Killer** lên mức tối đa (`max_phantom_processes 2147483647`).
* Thiết lập chu kỳ **FCM Heartbeat toàn hệ thống** về `120000ms` (2 phút).
* Đưa danh sách 32 ứng dụng phổ biến vào danh sách loại trừ tối ưu pin (**Doze Whitelist**):
  * *Nhắn tin / Mạng xã hội:* Zalo, Messenger, Telegram, WhatsApp, Viber, Skype, Instagram, Facebook, TikTok.
  * *Công việc:* Gmail, Outlook, Slack, Microsoft Teams, Discord, Google Chat.
  * *Ngân hàng & Mua sắm:* VCB Digibank, Techcombank, BIDV, MB Bank, MoMo, Shopee, Lazada, Grab.
* Cấp quyền chạy ngầm bắt buộc qua AppOps: `RUN_IN_BACKGROUND`, `START_FOREGROUND`, `WAKE_LOCK`.

---

## 📖 Hướng Dẫn Sử Dụng

### Cách 1: Tối Ưu Nhanh Bằng Máy Tính (Khuyên Dùng)

1. Bật **Tùy chọn nhà phát triển** trên Xiaomi:
   * Vào *Cài đặt > Thông tin thiết bị* > Chạm 7 lần vào *Phiên bản OS*.
   * Vào *Cài đặt bổ sung > Tùy chọn cho nhà phát triển*.
   * Bật **Gỡ lỗi USB (USB Debugging)**.
   * Bật **Gỡ lỗi USB (Cài đặt bảo mật) / USB Debugging (Security settings)**.
2. Kết nối điện thoại với máy tính qua cáp USB và chọn *Luôn cho phép từ máy tính này*.
3. Nhấp đúp chuột để chạy file:
   * **`fix_xiaomi.bat`** (trên Windows)
   * Script sẽ tự động áp dụng toàn bộ tweak hệ thống, cài đặt APK và cấp toàn bộ quyền cần thiết.

---

### Cách 2: Cài Đặt Trực Tiếp Trên Điện Thoại

1. Tải và cài đặt file **[XiaomiNotificationFixer.apk](https://github.com/gbetokuon-stack/XiaomiNotifixer/raw/main/XiaomiNotificationFixer.apk)**.
2. Mở ứng dụng và kích hoạt các quyền cốt lõi:
   * **FCM Keep-Alive Service**: Bật công tắc chạy nền.
   * **Truy cập thông báo**: Cấp quyền Notification Listener.
   * **Tiết kiệm pin**: Chọn **Không hạn chế** (No restrictions).
   * **Tự khởi chạy**: Bật Autostart trong cài đặt HyperOS.
   * (Tùy chọn khuyến nghị): Kết nối với **Shizuku** -> Chọn ứng dụng cần fix -> Nhấn **Áp Dụng Fix Triệt Để**.
3. **Khóa đa nhiệm (Lock in Recents):** Vuốt mở đa nhiệm -> Nhấn giữ vào app cần nhận thông báo -> Bấm biểu tượng Ổ Khóa.

---

## 🏗️ Biên Dịch Từ Mã Nguồn

```bash
git clone https://github.com/gbetokuon-stack/XiaomiNotifixer.git
cd XiaomiNotifixer
./gradlew assembleDebug
```
File APK sau khi build nằm tại: `app/build/outputs/apk/debug/app-debug.apk` hoặc `XiaomiNotificationFixer.apk`.

---

## 📄 Bản Quyền

Mã nguồn được phát hành theo giấy phép **MIT License**. Hoàn toàn an toàn, sử dụng API tiêu chuẩn của Android và ADB, không làm ảnh hưởng bảo hành hoặc hệ điều hành.

