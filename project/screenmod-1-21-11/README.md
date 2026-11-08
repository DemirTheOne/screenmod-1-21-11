# Simply Screens – Fabric 1.21.11 (bağımsız sürüm)

Orijinal Simply Screens (Forge 1.20.1, MIT) modunun çekirdeğinin Fabric 1.21.11 için yeniden yazılmış hali.
OpenUI MC / PacketWeave gerekmez. Sadece Fabric API gerekir.

## Var olanlar
- Ekran bloğu (6 yöne yerleştirilebilir), yaratıcı sekmede ve tarifle (demir/cam/ametist)
- Çoklu blok: birbirine bitişik, aynı yöne bakan ekranlar tam dikdörtgen olunca tek büyük ekran (en fazla 64x64)
- Sağ tık ile ayar ekranı: resim içe aktarma (dosya yolu, Gözat penceresi veya http/https URL), sunucudaki resim listesi, en-boy oranı
- Screen ID: aynı ID'ye sahip ekranlar aynı resmi gösterir (dünyaya kaydedilir)
- Resimler sunucuda `<dünya>/screenmod_images/` altında, PNG/JPEG, en fazla 8 MB

## Çıkarılanlar (istediğin gibi)
Shader/bloom uyumu, playlist, erişim/sahiplik yönetimi, Sable uyumu, Patchouli rehberi.
Dikkat: ekranı herkes düzenleyebilir ve resim yükleyebilir.

## Derleme
JDK 21 + internet gerekir.

    ./gradlew build        # Windows: gradlew.bat build

Jar: `build/libs/screenmod-1.21.11-1.0.0.jar`
Test: `./gradlew runClient`
Gereken: Fabric Loader >= 0.18, Fabric API 0.141.1+1.21.11 (veya uyumlu).

## Doğrulanmamış noktalar
Bu kod derleyip çalıştırılmadan yazıldı. Hata çıkarsa en olası yerler:
`RenderTypes.text(...)` (ScreenRenderer), `NativeImage.setPixel` renk sırası (kırmızı/mavi ters görünürse),
`Screen.rebuildWidgets`, `Font.plainSubstrByWidth`, `BlockEntity.setLevel/setRemoved` override'ları.
