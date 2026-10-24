# 🚀 Xiaomi Notifixer (HyperOS & MIUI)

Giải pháp toàn diện khắc phục triệt để vấn đề **chậm, trễ hoặc mất thông báo FCM (Firebase Cloud Messaging)** (Zalo, Messenger, Telegram, Gmail...) trên các dòng điện thoại Xiaomi, Redmi, POCO chạy **HyperOS 1, HyperOS 2 (Android 14 - 15)** và **MIUI**.

> 💡 **Tương thích 100% với ứng dụng Ngân hàng, Ví điện tử và VNeID.** Không cần Root, không cần bật chế độ Nhà phát triển (Developer Options).

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
2. Bật công tắc **FCM Keep-Alive Service** ở trên cùng:
   * Chọn **Cho phép** khi hệ thống hỏi quyền gửi thông báo.
3. Bấm nút **Truy Cập Thông Báo (Notification Access)**:
   * Tìm dòng **Xiaomi Notification Fixer** -> Gạt bật công tắc cho phép. *(Đặc quyền này giúp app không bị HyperOS dọn RAM).*
4. Bấm nút **Tiết Kiệm Pin (Battery Saver)**:
   * Chọn mục **Không hạn chế (No restrictions)**.
5. Bấm nút **Bỏ Tối Ưu Pin (Doze Whitelist)**:
   * Nhấn **Cho phép (Allow)** để đưa app vào danh sách không bị Doze đóng băng.

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


