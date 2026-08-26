# CHANGELOG — Frontline Defender

Bu dosya `source/` icinde, yani surum kontrolu ALTINDA. `docs/` disarida
oldugu icin oradaki belgeler gecmisle birlikte tasinmiyor ve bir tur
yalan soyleyebiliyorlardi; surum notu artik kodla ayni gecmisi paylasiyor.

---

## 2026-08-26 (d) — KAMPANYA EKRANI HEDEF TASARIMA CEKILDI (v48)

Kullanici hedef mockup'i gonderdi ("hedef tasarim bu"). Ekran ona cekildi.

- **`TacticalFrame.kt` (YENI)** — kosesi kesilmis govde, cift kontur, kose
  isaret cizgileri, duruma gore hale. GORSEL DOSYA DEGIL, Compose ile cizim:
  PNG olsaydi 4 boyut x 2 durum = 8 dosya gerekir ve her boyut degisiminde
  yeniden uretim isterdi; cizim her olcude keskin ve APK'ya sifir bayt.
- Bolum karti dort duruma dort ton esler (MUTED/ACTIVE/CLEARED/NEXT);
  SIRADAKI kart mockup'taki gibi ALTIN cerceve + hale tasir ve ekrandaki tek
  altin vurgu odur. "Yesil iz + tek altin" okumasi (cihaz geri bildirimiyle
  kazanilmisti) birebir korundu.
- Yildizlar glif ("*"/"-") degil SPRITE: zafer ekranindakiyle ayni
  `spr_ic_victory_star`, kazanilmamis olan ayni sprite'in 0,22 soluk hali.
- Perde ayraci artik ilerleme soyluyor: perde adi + "n/11" + kilitli perdede
  ayracin solmasi. Mockup'in SABIT SOL RAYI kasten alinmadi: 55 kartlik
  yatay seritte sabit sutun ekranin ~%15'ini surekli yer ve kart alanini
  daraltirdi; ayni bilgi kaydirmayla akan ayraca tasindi.

Asset muhasebesi de kapandi (kullanici sordu): pack'te islenmemis varlik
KALMADI. `assets/raw_generated/` icindeki 10 dosyanin 8'i `ui_components/`
ile bayt-bayt ayni (md5), 2'si mockup kopyasi.

Kanit: `testDebugUnitTest lintDebug` yesil; cihazda siradaki kartin altin
cerceve+hale ile tek vurgu oldugu, yildizlarin sprite ciktigi ve "KISIM I
1/11" ayracinin gorundugu ekran goruntusuyle dogrulandi.

---

## 2026-08-26 (c) — BOLUM KARTLARINDA HARITA KUPURU + IKON HIZASI (v47)

Kullanici iki sey bildirdi: ana menudeki ikincil buton ikonlari yuvadan
kaymisti, ve bolum kartlari hâlâ eski gorunuyordu. Ikisi de kapandi.

### Ikon hizasi — GOZ KARARI KONMUSTU, OLCULDU

Ikincil butonun sol ucundaki sekizgen yuvaya ikon `padding(start = w * 0.055f)`
ile konmustu; bu sayi OLCULMEMIS, tahmin edilmisti. Cihazda ikon yuvanin
sol-altina kayip kenarindan tasti.

Yuva artik olculu: sanatin sol ucu 3x buyutulup sekizgenin IC kenari okundu →
`Art.SecondaryIconSlot = ArtInset(0.070, 0.203, 0.785, 0.242)`. Ikon once o
kutuya konumlaniyor, sonra kutunun %72'sini kaplayacak sekilde ORTALANIYOR;
yani ikonun kendi en-boy orani ne olursa olsun sekizgenin disina cikamiyor.

### Bolum kartlarinda HARITA KUPURU

Kart, oynanacak yerin neresi oldugunu yalnizca ADIYLA soyluyordu. Artik
tepesinde haritanin kendisi var; oyuncu bolumu goruntusunden taniyor.

- **Kupur AYRI bir varlik, savas arka plani DEGIL.** `bg_level_NN` 1920x1081
  ve bolum seridi `horizontalScroll` (lazy DEGIL), yani 55 kart ayni anda
  besteleniyor ve 11 benzersiz bitmap'in tamami bellege giriyor:
      tam boy  11 x 7,92 MB = **87 MB**   -> Galaxy S8'de kesin OOM
      kupur    11 x 0,17 MB = **1,86 MB** -> 46 kat az
  Uretici `tools/ui_art_pipeline.py` (`build_level_thumbs`), 320x132, 118,8 KB.
- **Dikey maliyet ~6 dp.** Kupur 43,7 dp (106 dp genislik / 2,424); yerine
  gectigi numara madalyonu 38 dp'ydi. Numara ayri bir satir acmadi, kupurun
  SOL USTUNE bindirildi — bu ekranda pay dar (ust seride plaka koyma denemesi
  +32 dp ile kartlari kirpmisti).
- Kupurun uzerine koyu perde biner ve **kilitli kartta perde daha koyu**:
  kilit durumu renkten baska bir kanaldan da okunmali.
- **Biyom recolor'i kupurlere uygulanmaz.** Kart zaten "GECE" rozetiyle biyomu
  soyluyor; 11 yerine 55 kupur uretmek bellek kazancini geri verirdi.

Kanit: `testDebugUnitTest lintDebug` yesil; cihazda kartlarin her birinin
KENDI haritasini gosterdigi ve hicbir satirin kirpilmadigi, ikonlarin da
yuvalara oturdugu ekran goruntusuyle dogrulandi.

---

## 2026-08-26 (b) — ANA MENU: CEPHANELIK ve GOREVLER KISAYOLLARI (v46)

Landing mockup'inda iki ikincil buton vardi, kodda yoktu. Eklendi.

- **GOREVLER** ve **CEPHANELIK** artik ana menuden de aciliyor. Ikisi de
  YENI EKRAN DEGIL: bolum secim ekranindan zaten ulasilan ayni iki panel,
  ayni durum ve ayni veri. Degisen tek sey giris noktasi sayisi.
- **Mockup'taki "CAMPAIGN" butonu KONULMADI.** Bu oyunda "HAREKATI BASLAT"
  ile ayni yere (bolum secim ekrani) gidiyor; ayni hedefe iki buton koymak
  oyuncuya bir secim varmis gibi yalan soyler. Yerine GOREVLER kondu —
  gercek, ayri ve o ana kadar yalnizca bolum secimden ulasilan bir yer.
- Ikincil buton sanatinin sol ucundaki sekizgen yuva bos kalinca buton yarim
  cizilmis gorunuyordu. GOREVLER'e `spr_ic_objective_flag`, CEPHANELIK'e
  `spr_ic_upgrade` kondu. Birincisi **oksuz asset'ti** — paketteydi, hicbir
  yerden cagrilmiyordu (HANDOVER acik isler §8).
- Dikey butce yeniden bolusturuldu (%30 baslik / %19 birincil / %16 ikincil
  satir / %15 disli). **44 dp dokunma tabani artik yuzdeyi EZIYOR**:
  `coerceAtLeast(MinTouchTarget * oran)` ile ikincil butonlar ve disli, kisa
  bir ekranda yuzde hesabi 36 dp verse bile 44 dp'nin altina inemiyor.
- `MainMenuOverlay`in iki yeni parametresi de **varsayilani null**: cagri
  yeri lambda vermezse buton hic cizilmez, yani bagli olmayan bir cagri yeri
  (onizleme, test) menuyu bozmuyor.
- `LevelSelectScreen` kendi `missionsOpen` durumunu ICINDE tutmaya devam
  ediyor; menunun durumu AYRI (`menuMissionsOpen`). Tek degiskende
  birlestirmek, bolum secimden menuye donuldugunde panelin kendiliginden
  acilmasina yol acardi.

Kanit: `testDebugUnitTest lintDebug` yesil, cihazda (Galaxy S8) iki butonun
da dogru paneli actigi ve geri tusunun menuye dondugu ekran goruntusuyle
dogrulandi.

---

## 2026-08-26 — UI ART PACK v2 (menu / ayarlar / sonuc ekranlari) + UYGULAMA ICI DIL

Yeni sanat paketi (`asset-pack/Frontline_Defender_assets_individual_full/`)
oyuna baglandi. Oynanis yuzeylerine (GameCanvas, HUD, kule/dalga seritleri)
**dokunulmadi**; degisen yalnizca menu, ayarlar, sonuc modallari ve perde karti.

### Ne eklendi

- **`ArtSurfaces.kt`** — sanat plakalarini Compose'a baglayan ince katman.
  Her yuzey kendi **en-boy oranini korur**; yukseklik genislikten TURETILIR,
  boylece "bu ekran 360 dp'ye siger mi" sorusu cagri yerinde aritmetikle
  cevaplanabilir. Ic alan kesirleri (`ArtInset`) tahmin edilmedi, sanatin
  merkezinden disa tarama ile OLCULDU.
- **Ana menu**: baslik plakasi, birincil eylem butonu, ayarlar dislisi.
  Yerlesim artik sabit dp degil **yukseklik butcesi** uzerinden hesaplaniyor.
- **Ayarlar**: tum modal tek bir panel sanatina tasindi; Material `Switch`
  yerine sanat anahtari (`ArtToggleVisual`).
- **Zafer / yenilgi**: baslik plakasi + sanat butonlari.
- **Perde acilis karti**: perde afisi (ust seritte "KISIM III", altta perde
  basligi).
- **Uygulama ici dil secimi (YENI OZELLIK)** — `AppLanguage.kt`. Ayarlarda
  Ingilizce/Turkce segmentli secici; secim aninda uygulaniyor ve kaliciyor.
  `appcompat` EKLENMEDI; Compose kokunde `LocalContext` cevrilmis bir
  `ContextThemeWrapper` ile degistiriliyor — `createConfigurationContext`
  KULLANILMADI cunku o Activity zincirini kopariyor ve `findActivity()`
  null donunce UMP "Reklam Ayarlari" satiri sessizce kaybolurdu.
- **Kamuflaj zemini degisti.** Yeni desen olculen luma ort. 26,2 (eskisi
  100,7); uzerindeki perde 0,72/0,88 -> **0,10/0,34** yapildi, aksi halde
  desen simsiyah olurdu.

### Yol boyunca DUZELTILEN uc mevcut hata

1. **`DefeatModal`'da yukseklik kilidi ve kaydirma YOKTU.** `VictoryModal` ve
   `PauseMenuModal` bunu 2026-08-18'de cihaz bulgusuyla kazanmisti, yenilgi
   tarafi atlanmisti: TR govde metni uc satira ciktiginda "TEKRAR DENE"
   ekran disina itiliyordu, yani yenilgiden cikis yolu yoktu.
2. **`ActIntroOverlay`'de de yoktu.** Icerik ~270 dp, kutu 258 dp; "ANLASILDI"
   cipi en uzun perde metinlerinde ekran disinda kalabiliyordu.
3. **Sonuc modallarinin genisligi 420 -> 480 dp.** Sanat butonlari orani
   korudugu icin 420'de buton yuksekligi 43,9 dp'ye, yani **44 dp dokunma
   tabaninin altina** duyuyordu.

### Cihazda olcup GERI ALDIGIMIZ iki sey

- **Kampanya baslik plakasi**: 190 dp'lik plaka ust seridi +32 dp buyuttu ve
  bes bolum kartinin BESINDE de "SEVK ET" satiri alttan kirpildi — yani
  bolume girmenin tek butonu. Ust serit DUZ METIN kaldi.
- **Kilit acma penceresi plakasi**: dort ayri bicimde denendi, dordunde de
  pencerenin iki `Surface` butonu metinsiz kaldi (120 coin harcayan onay
  butonu gorunmez oldu). Sanat kaldirilinca dordunde de duzeldi. Kok neden
  bulunamadi (muhtemel: API 24'te buyuk `Image` + `SubcomposeLayout` ile ayni
  kapsayicidaki `Surface` arasinda bir cizim etkilesimi). `ArtNameplate`,
  `Art.Nameplate` ve `ui_plate_nameplate.webp` bu yuzden SILINDI — olu kod ve
  oksuz varlik birakilmadi.

### Kontrast: sanata bakarak degil, pikselden olculdu

Ilk surumde buton etiketleri KOYU cizildi cunku sanat "parlak sari-yesil"
gorunuyordu. Cihazda olculen gercek: parlak olan yalnizca kenar isiltisi.

| yuzey | koyu yaziyla | acik yaziyla |
|---|---|---|
| birincil buton | **1,58:1** ✘ | **12,86:1** ✔ |
| secili dil segmenti | **2,31:1** ✘ | 6,6:1 ✔ |
| baslik plakasi | — | **13,79:1** ✔ |

`ArtOnBright` sabiti kaldirildi.

### Metin-gomulu 142 PNG KULLANILMADI

Pack ayrica `level_titles/` (110), `map_name_labels/` (22) ve `act_titles/`
(10) altinda metni piksele gomulmus plakalar iceriyordu (ham ~250 MB). Hicbiri
alinmadi: yazi `stringResource` ile geliyor, sanatin USTUNE ciziliyor. Aksi
halde her yeni dil 142 yeni dosya demek olurdu, `AutoShrinkText` devre disi
kalirdi ve ayni bolum adi biri kodda biri pikselde olmak uzere IKI yerde
yasardi — bu deponun en sik hata sinifi.

### Yeni testler

- `ArtSurfaceContractTest` — beyan edilen en-boy orani dosyanin GERCEK
  pikselinden okunanla karsilastirilir (WebP basligi elle cozulur; Robolectric
  bu soruyu cevaplayamaz). Ayrica oksuz/eksik varlik nobeti ve silinmis
  nameplate'in geri gelmemesi.
- `ArtButtonTouchTargetTest` — modal butonlari 44 dp tabaninin altina dusmez
  (genisligi GERCEK koddan, `ResultModalInnerWidth`'ten okur) ve sanat
  uzerindeki etiket renkleri olculen zeminlerde 4,5:1'i gecer.

Ikisi de **mutasyonla dogrulandi**: oran 3,50 -> 3,70 yapilinca ve modal
genisligi 480 -> 420 cekilince kiriliyorlar.

### Varlik butcesi

10 metinsiz bilesen, kirpilip WebP'ye cevrildi: **toplam 379 KB**
(uretici `tools/ui_art_pipeline.py`, surum kontrolu altinda ve yeniden
kosturulabilir). Kapali durum anahtari ayri bir dosya DEGIL, acik durumdan
turetiliyor (yatay ayna + doygunluk dusurme), yani "renk tek ayrim kanali
olamaz" kurali korunuyor.

Kanit: cihaz ekran goruntuleri `docs/device_evidence/ui_art_pack_v2/`
(Galaxy S8 / API 24 / 740x360 dp, TR ve EN).

---

## 2026-08-21 — COIN CIPINDEN ODULLU REKLAM (R1b, `COIN_TOP_UP`)

Tekrar oynama geliri kaldiriliyor (`replayReward` -> 0). Yerine gelen yol:
bolum secim ekranindaki **coin cipine dokunmak** odullu reklam teklifini
aciyor.

- **Yeni bir coin muslugu YOK.** Cip, var olan R1 "Tedarik Talebi" odulunun
  IKINCI GIRIS NOKTASIDIR: ayni `grantSupplyDrop` yolu, ayni gunluk tavan
  (`R1_COIN_BUDGET_PER_DAY` = 450 coin, `R1_VIEWS_PER_DAY` = 3). `game/economy/**`
  DEGISMEDI — tek satir sabit eklenmedi, cunku tavan zaten oradaydi.
- **Reklam katmani hak kovasi da PAYLASILIR** (`InMemoryRewardedQuotaStore`):
  cipten yapilan gosterim seridin hakkini da dusurur, arbitraj fallback tavani
  da ortak. Iki kova olsaydi ucak modunda gunluk azaltilmis odul 3 x 50 yerine
  6 x 50 olurdu.
- **Ayri enum degerinin tek sebebi analitik**: `placement.name` dogrudan olay
  etiketine gidiyor, yani serit mi cip mi gosterim uretiyor artik olculebilir.
- **Odul buyumedi, GORUNURLUK buyudu.** Hedef gunluk coin miktarini artirmak
  degil, zaten var olan 450 coin/gun butcesinin daha yuksek oranda talep
  edilmesi — yani gosterim/gun artisi, coin enflasyonu olmadan.
- Teklif tukendiginde cip **bakiyeyi gostermeye devam eder**, yalnizca "+"
  rozeti ve tiklanabilirlik gider. Hicbir ilerleme yolu kapanmaz.
- Rozet **28 dp** (20 dp daire + 8 dp aralik) ve `translatable=false` tek
  karakter; 740x360 dp yatayda baslik satirinda ~150 dp pay kaliyor.
- **YAN DUZELTME — R3 "Cift Odeme" bos teklif acmiyor.** Zafer ekranindaki
  teklifin kosulu yalnizca `isRewardedOffered` idi, yani `doublableAmount = 0`
  iken de aciliyor ve oyuncu reklami izleyip **+0 coin** aliyordu. Bugune kadar
  yalnizca gunun boost'lu tekrar hakki bittikten sonraki tekrarlarda oluyordu;
  tekrar odulu kaldirilinca HER tekrar zaferinde olurdu. Kosula
  `doublableAmount > 0` eklendi (R2'deki `reinforcementSupported` ile ayni
  kural).

Kanit: `testDebugUnitTest lintDebug` yesil — **1.078 test, 0 basarisiz,
2 atlandi; lint 0 hata**. Yeni testler: `CoinTopUpPlacementTest` (11),
`CoinChipAdEntryTest` (6, 740x360 dp), `LevelSelectHeaderWidthBudgetTest` (3).

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
