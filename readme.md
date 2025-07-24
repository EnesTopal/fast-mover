# 📂 Fast Mover – Android Floating File Transfer Tool

Fast Mover, Android cihazınızda iki klasör arasında dosyaları otomatik olarak taşıyan bir yardımcı araçtır. Uygulama, foreground servis ile çalışan bir baloncuk (floating bubble) UI sunar ve kullanıcıdan alınan URI'lere göre kaynak klasöre yeni dosya geldikçe bunları hedef klasöre taşır.

## 🚀 Özellikler

- 📌 **Jetpack Compose** tabanlı modern UI
- 📂 **SAF (Storage Access Framework)** ile kullanıcıdan alınan klasör URI’leri
- 📤 Kaynaktaki yeni dosyaları hedef klasöre **otomatik taşıma**
- 🫧 **Floating Bubble** UI ile baloncuk üzerinden kontrol
- 🔔 Baloncukta son taşınan dosya adını canlı gösterme
- 🛑 Uygulama kapalıyken yeniden başlama veya URI kaydı yok – **tamamen geçici ve tek seferlik oturum**

## 🖼️ Ekran Görüntüsü

> *(Görsel eklenmesi önerilir)*

## ⚙️ Kullanım

1. Uygulama açıldığında kaynak ve hedef klasörü seçin.
2. Gerekli izinleri verin (overlay, all-files-access).
3. Balon servisini başlatın.
4. Kaynak klasöre yeni bir dosya geldiğinde otomatik olarak hedefe taşınacaktır.
5. Balon üzerinde son taşınan dosya adı görünür.

## 📁 URI Erişimi

Bu uygulama `DocumentFile` ve `ContentResolver` kullanarak `Uri` tabanlı işlem yapar. Bu sayede tüm Android sürümlerinde güvenli erişim sağlanır.

## 🔐 İzinler

- `MANAGE_EXTERNAL_STORAGE` (Android 11+)
- `SYSTEM_ALERT_WINDOW` (overlay için)
- `FOREGROUND_SERVICE`

## 💡 Teknik Notlar

- Servis başlatıldığında kaynak klasördeki mevcut dosyalar göz ardı edilir.
- Yalnızca servis çalışırken yeni gelen dosyalar taşınır.
- `FileObserver` yerine güvenli `DocumentFile` listesiyle manuel izleme yapılır.

## 🛠️ Geliştirme

- Dil: Kotlin
- UI: Jetpack Compose
- Balon Servis: `torrydo/floating-bubble-view`
- Minimum API: 26

## 🤝 Katkı

Katkıda bulunmak isterseniz PR göndermekten çekinmeyin. Her öneri değerlidir!

---

© 2025 Enes Topal. MIT License.
