# Ay Halkası — Geliştirici Yol Haritası

> Kaynak: Flo / Clue / Period Calendar / GP Apps / Bearable / PCOS Tracker / Private
> Period Tracker gibi popüler uygulamaların 2026 mağaza yorumları, Reddit
> (r/Periods, r/TwoXChromosomes, r/degoogle) tartışmaları ve akademik/TechCrunch
> incelemeleri. Amaç: kullanıcıların gerçekten istediği ve nefret ettiği şeyleri
> ayıklayıp Ay Halkası'na uyarlamak.

---

## 1. Araştırma özeti — kullanıcılar neyi seviyor / neyden nefret ediyor

### En çok şikâyet edilenler (bizim fırsatımız)
1. **Reklam + aşırı paywall.** Flo/Clue'da en çok geçen kelime "greedy"; temel
   döngü takibi bile paraya bağlandı. → *Bizim avantajımız: her şey ücretsiz.*
2. **Veri gizliliği.** Flo FTC cezası + Meta'ya veri davası; "zero internet, no
   signup" isteği. → *Bizim avantajımız: tam çevrimdışı, hesap yok.*
3. **VERİ KAYBI.** Telefon değişince/güncellemede yılların kaydı gidiyor,
   "restore yok". En çok öfkelenilen konu. → **Yedekleme/geri yükleme ŞART.**
4. **Yanlış ve "kesin görünen" tahmin.** Düzensiz döngüde tek bir tarih veriyor;
   90 uygulamanın %54'ü sadece takvim tarihiyle yumurtlama "tahmin ediyor".
   → *Aralık + güven düzeyi gösterelim.*
5. **Zorunlu hesap + senkron bağımlılığı.** "Bir kez kaydol, sonra veri senin
   değil" hissi.
6. **Zorla doldurulan uzun anket / tetikleyici içerik.** "Perimenopause stuff
   shoved in my face" (genç kullanıcı), cinsiyet varsayımları.

### En çok sevilen / istenen özellikler
- **Basitlik + hız** (2 dokunuşta günlük kayıt), **reklamsız**, **çevrimdışı**.
- **Ana ekran widget'ı** ("gün 14, adede 13 gün") — sık isteniyor.
- **Uygulama kilidi** (PIN/parmak izi/yüz) + gizli ikon.
- **Hatırlatmalar:** adet öncesi, günlük kayıt, **doğum kontrol / hap**.
- **Doktor için PDF/CSV rapor** ("screenshot almayı bıraktım").
- **Düzensiz döngü / PCOS desteği** (aralık tahmini, atlanan döngüler).
- **BBT / servikal mukus / ovülasyon testi** (TTC - gebe kalmak isteyenler).
- **Belirti–faz içgörüleri** ("luteal fazdasın, yorgunluk normal").
- **Partner paylaşımı** (seçileni paylaş).
- **Döngü geçmişi analizi** (ortalama, değişkenlik, grafik).

### Tıbbi gerçek (dürüst olalım)
- Takvim tabanlı yumurtlama tahmini **doğru değil**; kesin yumurtlama için BBT /
  mukus / LH gerekir. Uygulamayı **doğum kontrol yöntemi** gibi göstermemeliyiz.
- Kısa, net bir **sorumluluk reddi** eklenmeli (zaten var; görünür tutalım).

---

## 2. Yol haritası (öncelik sırasıyla)

### A. v1.1 — Güven + temel eksikler (hemen)
1. **Yedekle / Geri yükle (dosya).** `FileProvider` ile `ay-halkasi-yedek.json`
   dışa aktar + içe aktar (önce onay, mevcut veriyi ezme uyarısı). *En büyük
   şikâyete doğrudan çözüm.*
2. **Uygulama kilidi.** PIN (4–6 hane) + isteğe bağlı biyometrik
   (`BiometricPrompt`). `onResume`'da kilit ekranı; `qibla` tarzı prefs.
3. **Ana ekran widget'ı (AppWidget).** Küçük: "Gün N", "Adede X gün"; dokununca
   uygulama açılır. Orta: sıradaki adet tarihi. *Günlük görünürlük = bağlılık.*
4. **Hızlı işaretleme + "gecikti/başlamadı" durumu.** Takvimde bugüne uzun
   bas → "Adet başladı" / "Bugün başlamadı (atla)" / "Hamilelik notu". Şu an
   her ay başlangıcı elle işaretlenmeli — kullanıcı bunu istiyor.
5. **Gecikme modu.** Tahmini geçti ve işaret yoksa takvim/özet "gecikti" gösterir
   (bugün var ama nazik ton). Yanlış "kesin" tarih vermemek için.

### B. v1.2–1.4 — Kullanıcı değeri ve dürüst tahmin
6. **Tahmin aralığı + güven.** Tek tarih yerine "2–4 Şubat" gibi aralık; döngü
   değişkenliği arttıkça aralık genişler. Özette "tahmin, kesin değil" notu.
7. **Düzensiz döngü / PCOS modu.** 35+ gün, atlanan ay, çok değişken döngü
   tespiti; ortalamayı medyanla sağlamlaştır (aykırı değerlere dayanıklı).
8. **Belirti–faz içgörüleri.** Döngü fazına (menstrual/foliküler/fertile/luteal)
   göre bugünün notu + sık belirti sıralaması ("son 6 döngüde en sık: kramp").
9. **Grafikler.** Döngü uzunluğu trendi (çizgi), belirti ısı haritası (ay × belirti).
   Saf Canvas veya küçük custom View; ek kütüphane yok.
10. **Doğum kontrolü / ilaç hatırlatıcısı.** Hap/patch/iğne takvimi + günlük
    "hapı aldım" işareti + ayrı bildirim.
11. **Eğitici içerik.** Kısa, çevrimdışı: döngü fazları, PMS/PMDD, ne zaman
    doktora gidilmeli, adet hijyeni (Diyanet uyumlu notlar opsiyonel).

### C. v1.5+ — Partner & ekosistem
12. **Partner paylaşımı (seçmeli).** Yalnız seçilen: "bulunduğu faz" + "sonraki
    adet" + istersen doğurgan pencere. Metin/özet paylaşımı ile başla; ileri
    aşamada şifreli dosya/QR. (Kullanıcı kararı: ayrı telefon + paylaşım.)
13. **Android Health Connect.** Adet/temperature yaz-oku (izinliyse).
14. **TTC derinleştirme.** BBT, servikal mukus, LH/ovülasyon testi, "bugün gebe
    kalma şansı" göstergesi.
15. **Doktor raporu PDF/CSV.** Döngü + belirti + not özeti; paylaş.

### D. Konfor & cila
16. **Bildirimden hızlı kayıt** (aksiyon butonları: "Adet başladı").
17. **Tema/renk seçenekleri** (pembe, mor, yeşil, bordo) + özel vurgu rengi.
18. **Çoklu dil altyapısı** (TR/EN) — `values-en/`.
19. **Şifreli yedek** (parola) ve içe aktarma.
20. **Tablet/foldable düzeni**, büyük widget.

---

## 3. Teknik notlar (nasıl yapacağız)

- **Veri modeli:** `Store`'a ek alanlar: `flow`'a "gecikti/atla" işareti,
  ayrı `DayLog.bbt: Double?`, `cervicalMucus: Int`, `pillTaken: Boolean`,
  `special: String` (özel gün). JSON zaten esnek; geriye uyumlu okuma şart.
- **Tahmin aralığı:** `CycleEngine` ortalamaya ek olarak **standart sapma / IQR**
  hesaplasın; alt/üst sınır = `nextStart ± yayılım`. `upcoming` yerine
  `window: Pair<LocalDate, LocalDate>`.
- **Widget:** `AppWidgetProvider` + `RemoteViews`; veriyi `Store`'tan okuyup
  periyodik güncelle (günde 1, `WorkManager` yerine basit `AlarmManager`).
- **Kilit:** `androidx.biometric` + basit PIN; kilit ekranı ayrı Activity,
  `MainActivity.onResume`'da `isUnlocked` kontrolü.
- **PDF:** Android `PdfDocument` ile çiz (kütüphane gerekmez) veya HTML→print.
- **Health Connect:** `androidx.health.connect` (opsiyonel modül).

## 4. Yapmayacağız / kaçınacağız
- Reklam, hesap zorunluluğu, bulut senkron, veri satışı, tetikleyici/paywall
  içerik, kesin görünen yanlış tahmin. **Ay Halkası'nın kimliği: sade, ücretsiz,
  çevrimdışı, gizli.**

## 5. Önerilen sıralama (özet kontrol listesi)
- [ ] v1.1: Yedek/geri yükleme (dosya)  ← en kritik
- [ ] v1.1: Uygulama kilidi (PIN/biyometrik)
- [ ] v1.1: Ana ekran widget'ı
- [ ] v1.1: Hızlı işaretleme + gecikme modu
- [ ] v1.2: Tahmin aralığı + güven
- [ ] v1.2: Düzensiz döngü / PCOS modu
- [ ] v1.3: Belirti–faz içgörüleri + grafikler
- [ ] v1.3: Doğum kontrolü/ilaç hatırlatıcısı
- [ ] v1.4: Eğitici içerik
- [ ] v1.5: Partner paylaşımı (seçmeli) + doktor raporu
- [ ] v1.6: Health Connect + TTC (BBT/mukus/LH)
- [ ] sürekli: Tema/dil/cila
