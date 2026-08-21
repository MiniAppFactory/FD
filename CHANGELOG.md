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
## 2026-08-21 — Rota geometrisi v5 · rotalar SANATTAN yeniden uretildi

Cihaz geri bildirimi (tekrar eden): *"bu yoldan gelmeyen askerler var"*,
*"hala yol olmayan yerlerden geciyorlar, bu level 3 ornegin"*. Ayni sikayet
farkli cihazlarda (S8, S22, tablet) geldigi icin ekran/en-boy ile ilgisi yok.

### Kok neden — maske dogru soruyu sormuyordu
`map_masks_v1.bin` UC sinif tasiyordu: yol / bitki / **diger**. Kaya, kopru,
su, us rampasi ve spawn platformu hepsi ayni "diger" kovasindaydi ve testler
yalnizca bitkiyi yasakliyordu. Harita 3'un rotasi sag ucta boyali yolu birakip
**kayaliktan** ussun kuzeyine kesiyordu: maske "%0,00 cim" diyor, test yesil
yaniyor, oyuncu ekranda kayadan yuruyen asker goruyordu. Ikinci kusur, testin
kusuru **yasaklamak yerine saymasiydi** ("cime hic basmayan rota sayisi 11
olmali" = kalan bese acikca izin).

### Yapilanlar
- **Maske v2** (`map_masks_v2.bin`, sihir `FDMASK02`): kaya artik ayri sinif.
  Kaynak, uygulamanin gercekten yukledigi `drawable-nodpi/bg_level_XX.webp`
  (1920x1081); v1 asset-pack'teki 1672x941 PNG kopyalarindan pisirilmisti.
  Iki goruntunun cercevesi ayni cikti (en iyi kaydirma dx=0 dy=0), yani v1
  kayik degildi — EKSIKTI. v1 silindi.
- **16 rota** (11 harita + 5 `ALT_ROUTES`) yol koridorunun uzaklik donusumu
  uzerinde A* ile yeniden uretildi: koridorun ortasi ucuz, kenari karesel
  pahali. Uc noktalar koridorun sol/sag ucundan turetildi; mevcut uc 45
  ref-px'ten yakinsa dokunulmadi (harita 1'in gozle dogrulanmis uclari aynen
  korundu). Segment tavani 36 -> 32 ref-px (olculen en uzun 30,14).
- **Testler sayaci degil KURALI kilitliyor**: rota yolu yalnizca boyali yolun
  KOPUK oldugu yerde terk edebilir ve bu, maske uzerinde yol-only baglanti
  aranarak KANITLANIR — dondurulmus istisna listesi yok.

### Olcum (pisirilmis v2 maskesine karsi, 4 ref-px adim)
| | once (v4) | sonra (v5) |
|---|---|---|
| tamamen boyali yolda kosan rota | 10/16 | **12/16** |
| harita 3 A-kolu yol / cim / kaya | 93,3 / 3,4 / 2,9 | **99,6 / 0,4 / 0,0** |
| harita 4 A-kolu yol / cim / kaya | 92,2 / 0,2 / 6,9 | **100 / 0 / 0** |
| harita 10 A-kolu yol disi toplam | %9,5 | **%5,7** |
| en uzun segment | 35,1 ref-px | **30,1 ref-px** |
| kenar payi 23 ref-pxin altinda kalan yol orani | %9,5 | **%5,4** |

Kalan iki kesinti GERCEK sanat kopuklugudur ve testte kanitlanir: harita 6'nin
tas koprusu (112 ref-px) ve harita 10'un nehir gecisi (100 ref-px) — her
ikisinde de boyali yol iki yakada kesiktir.

### Yan etkiler (hepsi olculdu)
- `OUT_OF_RANGE_PADS[3]`: `[10]` -> `emptyList()`. Yeni rota ussun guney
  kapisina varip koridorun ortasindan gectigi icin pad 10 327 -> 145 ref-px'e
  dustu; 12 pad'in 12'si de menzil icinde, gizlemenin mesru gerekcesi kalmadi.
  Bolum 3'te oyuncunun secenegi 11 -> 12 pad.
- Harita 6 pad 1 "yalniz-destek" bandina girdi (175 < d <= 270).
- Cozulebilirlik BOZULMADI: 55/55, 359 dalga, meta 0.
- Meta yukseltme etki testlerinin olcum evreni 8 sonda bolumden 55 bolume
  cikarildi: yeni rotalarla kampanya meta 0'da kolaylasti ve 8 bolumluk
  sondanin toplam sizintisi 18 -> 6'ya dustu; o tabanda tek bir bolumun
  simulator gurultusu isareti yutuyordu (OPTICS 8 bolumde 6/14/10/11,
  ayni kosuda 55 bolumde 127/123/98/74).

### Kanit
`1042 test / 0 basarisiz (2 atlanan)` · `lintDebug BUILD SUCCESSFUL` ·
gorsel: `docs/level_geometry/overlay/v5_mNN.png` (rota gercek arka planin
uzerinde) ve `karsilastir_mNN.png` (once kirmizi / sonra cyan, ustte sanat
altta 4 sinifli maske). Uretici: `docs/level_geometry/build_routes.js`,
`bake_masks.js`.

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
