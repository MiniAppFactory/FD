# CHANGELOG — Frontline Defender

Bu dosya `source/` icinde, yani surum kontrolu ALTINDA. `docs/` disarida
oldugu icin oradaki belgeler gecmisle birlikte tasinmiyor ve bir tur
yalan soyleyebiliyorlardi; surum notu artik kodla ayni gecmisi paylasiyor.

---

## 2026-08-21 — ATES HATTI SINYALI (build oncesi menzil uyarisi)

Cihaz sikayeti: *"gatling topunun yerlesim yerine gore vurus alanini
kapsamiyor"* (bolum 8, pad 7). Olcum pad'i AKLADI — ayni mevziden Fuze
Rampasi haritanin en iyi ikinci kapsamasini veriyor (674 ref-px yol),
Gatling ise menzil sinirinda yetisemiyor. Pad yanlis yerde degil, **yanlis
kule icin secilmis**; kusur KURMADAN ONCE VERILMEYEN SINYALDE.

- **Kapsama hesabi tek yerde**: `GameConfig.pointToSegmentDistance` /
  `distanceToRoutes` / `coversRoute`. Cizim, panel ve testler ayni
  fonksiyondan geciyor; `GeometryTestSupport` kendi kopyasini birakip
  delege etti (bu depoda ikiz hesap "olu build pad" hatasini 22 bolum
  boyunca gizlemisti).
- **Harita**: bir kule karti BASILI iken o kulenin menzili yolu gormeyen
  pad'ler geri ceker (alfa 0,55 -> 0,22) ve halka + capraz cizgi isareti
  alir. Renk notr gri: soguk mavi "dost", haki "dusman", altin "secili"
  ailelerinin hicbirine girmez; anlami tasiyan asil kanal SEKIL.
- **Kart**: secili mevziden yetismeyen kule fiyatinin yanina rozet alir.
  Cekmece acik oldugu SURECE gorunur — oyuncunun cogu dokunusu basip-bekleme
  degil kisa tap ve cihazdaki hata da oyle olustu.
- **Serit**: kart basili iken sebep cumle olarak yazilir
  ("Gatling Topu buradan yola yetismiyor."). Mevcut ret seridi ikinci gorev
  aldi; RET her zaman uyariyi bastirir (kirmizi kontur = sonuc yok, notr
  gri kontur = sonuc bu).
- **ENGELLEME YOK**: insa yine gerceklesir. Menzil kalici olarak buyuyor
  (Gatling kd.1 150 -> kd.2 180 -> kd.3 210) ve meta menzil yukseltmesi var;
  bugun yetismeyen mevzi bilincli bir plan olabilir.

Kanit: `testDebugUnitTest lintDebug` yesil — 1059 test, 0 basarisiz, 0 lint
sorunu. `LineOfFireTest` 11 haritanin tam kapsama tablosunu raporluyor;
`LineOfFireUiTest` 740x360 dp'de EN ve TR icin tasma olmadigini kirpilmamis
dugum sinirlariyla olcuyor.

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
