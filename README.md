# 🚀 Xiaomi Notifixer (HyperOS & MIUI)

Giải pháp toàn diện khắc phục triệt để vấn đề **chậm, trễ hoặc mất thông báo FCM (Firebase Cloud Messaging)** (Zalo, Messenger, Telegram, Gmail...) trên các dòng điện thoại Xiaomi, Redmi, POCO chạy **HyperOS 1, HyperOS 2 (Android 14 - 15)** và **MIUI**.

> 💡 **Tương thích 100% với ứng dụng Ngân hàng, Ví điện tử và VNeID.** Không cần Root, không cần bật chế độ Nhà phát triển (Developer Options).

### 🆕 Có gì mới ở phiên bản 5.1.1 (Khắc phục triệt để lỗi chậm / không nổ thông báo)
- **Khôi phục phát sóng đánh thức FCM & GMS định kỳ**: Luôn phát bộ 3 intent `GCM_RECONNECT`, `GTALK_HEARTBEAT` và `MCS_HEARTBEAT` tới Google Play Services (`com.google.android.gms`) mỗi chu kỳ, loại bỏ hoàn toàn cơ chế throttle 30 phút gây trễ/mất thông báo.
- **Bảo vệ kép chống ngủ đông Doze Mode (Safe WakeLock & AlarmManager)**: Khôi phục WakeLock 2 giây và tích hợp `AlarmManager.setExactAndAllowWhileIdle()` đảm bảo CPU thức dậy đúng chu kỳ ngay cả khi màn hình tắt trên HyperOS / Android 15.
- **Bổ sung nút "⚡ Kích hoạt nổ thông báo ngay"**: Bấm 1 chạm ngay trên màn hình chính để phát xung kích hoạt FCM và xả tức thì mọi thông báo đang chờ.
- **Tùy chọn chu kỳ linh hoạt**: Hỗ trợ 3 mốc chu kỳ: `1 Phút` (Siêu tốc - Max nổ), `2 Phút` (Chuẩn HyperOS khuyên dùng) và `3 Phút` (Cân bằng).
- **Mở rộng đặc quyền Shizuku cho GMS**: Tự động cấp thêm quyền `RUN_ANY_IN_BACKGROUND`, `WAKE_LOCK`, `START_FOREGROUND`, `POST_NOTIFICATION`, `SCHEDULE_EXACT_ALARM` cho Google Play Services.

### 🆕 Có gì mới ở phiên bản 5.1.0
- **Giao diện tối giản hoàn toàn mới**: bảng màu tối dịu mắt, điểm nhấn xanh lá, bố cục gọn hơn, bỏ hiệu ứng neon rườm rà.
- **Trạng thái dịch vụ được lưu bền**: công tắc phản ánh đúng dịch vụ đang chạy, tự khôi phục sau khi khởi động lại máy và chỉ chạy khi người dùng đã bật.
- **Thông báo dịch vụ trực quan hơn**: cập nhật trạng thái ping theo thời gian thực, có nút **Tắt** ngay trên thông báo.
- **Luồng cấp quyền thông báo rõ ràng**: nếu từ chối quyền, công tắc tự tắt và mở hướng dẫn bật lại trong cài đặt.
- **Tự khởi động lại dịch vụ khi mở app** nếu HyperOS đã dọn tiến trình trước đó.
- **Tối ưu Shizuku hàng loạt an toàn hơn**: chia lệnh thành nhiều lô, tránh lỗi quá dài lệnh khi chọn hàng trăm ứng dụng.
- **Danh sách ứng dụng mới**: tìm kiếm, chọn tất cả/bỏ chọn, hiển thị `Đã tối ưu` / `Chưa tối ưu`, mở nhanh cài đặt pin của từng app.
- **Sửa xung đột callback Shizuku** giữa màn hình chính và màn hình danh sách ứng dụng.

---

## 📥 Tải Nhanh File Cài Đặt (APK)

👉 **[Tải XiaomiNotificationFixer.apk (Bản Mới Nhất - Siêu Tiết Kiệm Pin)](https://github.com/gbetokuon-stack/XiaomiNotifixer/raw/main/XiaomiNotificationFixer.apk)**

*(Hoặc tải artifact từ hệ thống build tự động tại tab **[Actions](../../actions)**).*

---

## 📱 HƯỚNG DẪN CÀI ĐẶT & SỬ DỤNG CHI TIẾT (BƯỚC-TỪNG-BƯỚC)

Dành cho người dùng dùng điện thoại trực tiếp (tương thích app Ngân hàng, VNeID):

### Bước 1: Tải và cài đặt ứng dụng
1. Bấm vào link tải trực tiếp file **[XiaomiNotificationFixer.apk](https://github.com/gbetokuon-stack/XiaomiNotifixer/raw/main/XiaomiNotificationFixer.apk)** bằng Chrome trên điện thoại.
2. Mở file APK vừa tải về, chọn **Cài đặt** (Cho phép cài đặt từ nguồn này nếu được hỏi).

---

### Bước 2: Thiết lập trong ứng dụng Xiaomi Notification Fixer
1. Mở ứng dụng **Xiaomi Notification Fixer**.
2. Bật công tắc **Giữ kết nối thông báo** ở trên cùng:
   * Chọn **Cho phép** khi hệ thống hỏi quyền gửi thông báo.
3. Chạm vào **Quyền đọc thông báo**:
   * Tìm dòng **Xiaomi Notification Fixer** -> Gạt bật công tắc cho phép. *(Đặc quyền này giúp app không bị HyperOS dọn RAM).*
4. Chạm vào **Tiết kiệm pin**:
   * Chọn mục **Không hạn chế (No restrictions)**.
5. Chạm vào **Miễn trừ Doze**:
   * Nhấn **Cho phép (Allow)** để đưa app vào danh sách không bị Doze đóng băng.
6. Chạm vào **Tự khởi chạy** và bật cho ứng dụng.
7. *(Tùy chọn)* Cài **Shizuku**, cấp quyền rồi mở **Chọn ứng dụng** để tối ưu hàng loạt app nhắn tin/ngân hàng.

---

### Bước 3: Cài đặt hệ thống HyperOS / MIUI (CỰC KỲ QUAN TRỌNG)

Để HyperOS không kill các app nhắn tin (Zalo, Messenger, Telegram...):

#### 1. Khóa đa nhiệm (Lock in Recents):
* Vuốt mở màn hình **Đa nhiệm (Recent Apps)**.
* Nhấn giữ vào thẻ app **Xiaomi Notification Fixer** -> Bấm biểu tượng **Ổ khóa** 🔒.
* *Làm tương tự với các app Zalo, Messenger, Telegram.*

#### 2. Bật Tự khởi chạy (Autostart):
* Vào **Cài đặt** > **Ứng dụng** > **Quyền truy cập** > **Tự khởi chạy**.
* Bật xanh cho **Xiaomi Notification Fixer** và tất cả app nhắn tin.

#### 3. Chế độ Pin cho App nhắn tin:
* Nhấn giữ icon app nhắn tin trên màn hình chính > **Thông tin ứng dụng**.
* Chọn **Tiết kiệm pin** > Chọn **Không hạn chế (No restrictions)**.

#### 4. Tắt Tùy chọn nhà phát triển (Nếu trước đó đã bật):
* Vào **Cài đặt** > **Cài đặt bổ sung** > **Tùy chọn cho nhà phát triển** > Gạt **TẮT** công tắc trên cùng.

---

## 💻 HƯỚNG DẪN DÙNG MÁY TÍNH & ADB (TÙY CHỌN CHO MÁY KHÔNG DÙNG BANK/VNeID)

1. Bật **Tùy chọn nhà phát triển** trên Xiaomi:
   * *Cài đặt > Thông tin thiết bị* > Chạm 7 lần vào *Phiên bản OS*.
   * *Cài đặt bổ sung > Tùy chọn cho nhà phát triển*.
   * Bật **Gỡ lỗi USB (USB Debugging)** & **Gỡ lỗi USB (Cài đặt bảo mật)**.
2. Kết nối điện thoại với máy tính qua cáp USB.
3. Chạy file **`fix_xiaomi.bat`** (Windows) để tự động hóa toàn bộ:
   * Vô hiệu hoá Phantom Process Killer (`max_phantom_processes 2147483647`).
   * Tinh chỉnh FCM Heartbeat 120s.
   * Whitelist Doze và cấp quyền ngầm cho 32 ứng dụng nhắn tin, ngân hàng, công việc.

---

## 🔋 CƠ CHẾ SIÊU TIẾT KIỆM PIN (Pin Sụt < 1% / Đêm)

1. **Chu kỳ thích ứng (Adaptive Interval):**
   * **Khi tắt màn hình:** Chu kỳ ping socket là **6 phút (360 giây)** — vừa khít bảng định tuyến NAT của nhà mạng, giảm 3 lần số lần đánh thức CPU.
   * **Khi bật màn hình:** Giãn chu kỳ ra **15 phút** vì máy đang kết nối mạng chủ động.
2. **Không dùng WakeLock:** Cho phép CPU đi vào chế độ ngủ sâu (Deep Sleep) bình thường.
3. **Loại bỏ Reconnect lặp vô hạn:** Chỉ gửi tín hiệu khi kết nối thật sự mất, không ép Google Play Services bắt tay TLS liên tục.
4. **Thông báo mức tối thiểu:** Không rung, không chuông, không làm sáng màn hình.

---

## 🏗️ Biên Dịch Từ Mã Nguồn

```bash
git clone https://github.com/gbetokuon-stack/XiaomiNotifixer.git
cd XiaomiNotifixer
./gradlew assembleDebug
```
File APK sau khi build: `app/build/outputs/apk/debug/app-debug.apk` hoặc `XiaomiNotificationFixer.apk`.

---

## ❓ CÂU HỎI THƯỜNG GẶP (FAQ)

* **App có an toàn với ứng dụng Ngân hàng và VNeID không?**  
  Có, 100% an toàn. Không root, không can thiệp bộ nhớ app khác, không cần bật chế độ Developer Options khi sử dụng.
* **Sau khi khởi động lại máy có cần mở lại app không?**  
  Không cần. App tích hợp sẵn `BOOT_COMPLETED` để tự kích hoạt lại chạy nền.

---

## 📄 Bản Quyền

Mã nguồn phát hành theo giấy phép **MIT License**. Tự do sử dụng, chỉnh sửa và chia sẻ.


