# CHANGELOG — Frontline Defender

Bu dosya `source/` icinde, yani surum kontrolu ALTINDA. `docs/` disarida
oldugu icin oradaki belgeler gecmisle birlikte tasinmiyor ve bir tur
yalan soyleyebiliyorlardi; surum notu artik kodla ayni gecmisi paylasiyor.

---

## 2026-08-19 — Faz 20 · ekip toplantisi turu (7 commit)

Toplanti kapsami: kod, oynanabilirlik, hikaye, oyun ekonomisi, gorseller.
Tespit edilen eksikler asagida; her madde bir commit ve en az bir olcum.

### Icerik hatalari
- **L54 ile L50 birebir ayni bolumdu.** Gec perde dalgalari saf bir
  fonksiyonla uretiliyor, iki `LatePlan` satiri ayni girdiyi tasiyordu —
  oyuncu 55 bolumluk kampanyada ayni bolumu iki kez oynuyordu. Arketip
  `'S'` -> `'C'`. Ayni hata sinifi ucuncu kez olusuyordu, regresyon testi
  eklendi (`noTwoLevelsGenerateTheSameWaveTable`).
- **Perde etiketi 55 bolumun 33'unde yanlisti.** `actLabelRes` yalnizca iki
  perde taniyordu; Act III, IV ve V hepsi "KISIM II" yaziyordu.
- **Ipucu, oyuncunun o bolumde gormedigi dusmani anlatiyordu.** L3'te Top
  acilir, ders Kalkanli Er'i anlatirdi, o dusman ilk kez L9'da cikar. Ders
  dusurulmedi, ornek sahaya cikana kadar ertelendi.
- **Haftalik gorev kazanilamayacak yildiz istiyordu.** Kampanyayi uc yildizla
  bitiren oyuncuda "N yildiz kazan" gorevi sonsuza dek asili kaliyordu.

### Gorseller
- **Dost/dusman ayrimi.** Olcum: kule sprite'larinin ton ortalamalari
  37,8 / 50,6 / 57,4 derece, dusman ton bandi 35,8-60,2 — dort kulenin ucu
  bandin tam icinde. Kulelerin altina soguk mavi taban plakasi; bes biyomun
  hepsinde WCAG 3,0 esigi gecildi.
- **Namlu alevi ve tracer kontrasti** ~2,1'den 4,66-8,01'e.
- **Hava taarruzu ucagi artik ucak.** Ekranda ucan sey gerilmis fuze
  sprite'iydi; prosedurel siluetle degistirildi, APK'ya 0 bayt.

### Hikaye
- **Perde acilis karti**: her perdenin ilk bolumunde bir kez, dort satir.
  Oyuncu 55 bolum boyunca neden savastigini hicbir yerde ogrenmiyordu.

### Ekonomi
- **Coin fazlasi** en kotu bantta +15.245 -> +9.545 (bakiyenin %52'si ->
  %33'u). Meta agaci 13.900 -> 19.600 (yalnizca Tahkimat ve Hurda
  buyuyebiliyordu, ikisinin de tavani olculdu), rutbe kapilari elle degil
  kuralla kuruldu. Agac tamamlama 3 yildizda L22 -> L50.
- Kapanmayan kisim durustce olculup teste sabitlendi: 3 yildiz bandindaki
  45.295 coin ancak tekrarlanabilir bir emiciyle kapanir.

### Kanit
`984 test / 0 basarisiz (1 atlanan)` · `lintDebug 0 hata` ·
`assembleDebug BUILD SUCCESSFUL` · APK:
`builds/frontline-defender-debug-faz20b-toplanti-tam-2026-08-19.apk`
