# Fork Development Notes

**Dil / Language:** [Türkçe](#tr) · [English](#en)

---

<a id="tr"></a>

## Türkçe

Değişiklikler 3 kategoride yapılıyor:

- **A – Güvenlik**
- **B – Hata düzeltmeleri**
- **C – Keyfî GUI tasarımı**

Bu dosya, Panther Launcher'ın kaynağı olan [mLauncher (Multi Launcher)](https://github.com/CodeWorksCreativeHub/mLauncher) projesinden hangi noktalarda ayrıldığının özetidir. Ayrıntılar git geçmişindedir.

### A – Güvenlik

- **Sunucuya çökme raporu gönderen kod silindi.** Kaynak projede, çökme kaydını ve cihaz bilgisini `crash.5646316.xyz` adresine gönderen bir fonksiyon vardı. Çağrısı devre dışıydı, yani çalışmıyordu; ileride yanlışlıkla etkinleşmemesi için tamamen kaldırıldı.
- **Çökme raporu e-postasının alıcısı boşaltıldı.** Rapor artık kaynak projenin geliştiricisine gitmiyor; alıcıyı kullanıcı kendisi yazar.
- **Eski alan adına (`5646316.xyz`) giden bağlantılar kaldırıldı:** tema indirme sayfası, gizlilik politikası bağlantıları ve ilk kurulumdaki gizlilik politikası satırı.

### B – Hata düzeltmeleri

Aşağıdaki ilk iki düzeltme, kaynak projeye birleştirme isteği olarak gönderilmek üzere hazırlanmıştı. Gönderilmediler; artık doğrudan bu depoda yer alıyorlar.

- **Uygulama çekmecesi kalıcı olarak açılmaz hale geliyordu.** Liste ekrana sığacak kadar kısayken çok hızlı art arda iki kaydırma, çekmeceyi iki kez kapatıp ana ekranı da gezinme yığınından atıyordu. Sonrasında kaydırma, menü tuşu ve uzun basma hiçbir şey yapmıyor, yalnızca uygulamayı zorla durdurmak çözüyordu. Çekmece artık yalnızca gerçekten açıkken kapatılıyor; yığın yine de boşalırsa uygulama ilk dokunuşta kendiliğinden ana ekrana dönüyor.
- **Çekmece art arda kaydırmalarda açılıp kapanıyordu.** Liste ekrana sığacak kadar kısayken her kaydırma, yukarı doğru olanlar dahil, çekmeceyi kapatıyordu. Çekmece artık yalnızca aşağı çekilince kapanıyor.
- **Yeniden adlandırılan uygulamanın adı ana ekranda görünmüyordu.** Takma ad kaydediliyordu, ama kullanım süresi görünümünde ve bildirim noktası açıkken ana ekran özgün adı yazıyordu. Ana ekran, sıralama ekranı ve ana ekran widget'ı artık her modda takma adı gösteriyor.
- **Türkçe çeviri gözden geçirildi.** 349 metnin 106'sı düzeltildi; çoğu, anlamı bozan makine çevirisi hatalarıydı.

### C – Keyfî GUI tasarımı

- **İkinci arama şekli eklendi: "Akıllı arama".** Sonuç artık adın uzunluğuna bağlı değil; uzun adlarda da birkaç harf yetiyor. Kelime başları ("gm" → Google Maps), sıralı harfler ("yt" → YouTube) ve dört harften itibaren tek harflik yazım hataları eşleşiyor; en iyi eşleşme en üstte çıkıyor. Varsayılan olarak açık; ayarlardan kapatılınca eski arama (esnek arama ve hassasiyeti) aynen geri geliyor.
- **Çekmecedeki arama alanı büyütüldü** ve uygulama listesiyle arasına boşluk eklendi.
- **Günün Sözü kaldırıldı.** Ana ekrandaki metin, widget, ayarlardaki seçenekler, söz listesi içe aktarma menüsü ve hazır söz listeleri artık yok.
- **Ad ve kimlik değişti.** Uygulama "Panther Launcher" adını ve `app.pantherlauncher` kimliğini kullanıyor; özgün mLauncher ile yan yana kurulabilir.
- **Hakkında ekranı sadeleştirildi.** Bağış bağlantıları, topluluk desteği (Discord) ve "Uygulamayı Paylaş" kaldırıldı.

---

<a id="en"></a>

## English

Changes are made in 3 categories:

- **A – Security**
- **B – Bug fixes**
- **C – Discretionary GUI design**

This file summarises where Panther Launcher departs from its source, [mLauncher (Multi Launcher)](https://github.com/CodeWorksCreativeHub/mLauncher). The details are in the git history.

### A – Security

- **The code that uploads crash reports to a server was deleted.** The source project contained a function that sent the crash log and device information to `crash.5646316.xyz`. Its call was disabled, so it never ran; it was removed entirely so that it cannot be switched on by accident later.
- **The crash report e-mail no longer has a preset recipient.** Reports no longer go to the source project's developer; the user enters the recipient.
- **Links to the old domain (`5646316.xyz`) were removed:** the theme download page, the privacy policy links, and the privacy policy line shown during first-run setup.

### B – Bug fixes

The first two fixes below were prepared to be sent to the source project as merge requests. They were never sent and now live directly in this repository.

- **The app drawer could become permanently impossible to open.** When the list was short enough to fit on screen, two swipes in very quick succession closed the drawer twice and also removed the home screen from the navigation stack. After that, swiping, the menu key and long-press did nothing, and only force-stopping the app helped. The drawer is now closed only when it is actually open; if the stack is emptied anyway, the app returns to the home screen by itself on the next touch.
- **The drawer opened and closed on repeated swipes.** When the list was short enough to fit on screen, every swipe closed the drawer, upward ones included. The drawer now closes only when pulled down.
- **A renamed app's name was not shown on the home screen.** The alias was saved, but the home screen showed the original name in the usage-time view and whenever notification dots were enabled. The home screen, the reorder screen and the home widget now show the alias in every mode.
- **The Turkish translation was reviewed.** 106 of 349 strings were corrected; most were machine-translation errors that changed the meaning.

### C – Discretionary GUI design

- **A second search mode was added: "Smart Search".** The result no longer depends on the length of the name, so a few letters are enough for long names too. Word beginnings ("gm" → Google Maps), letters in order ("yt" → YouTube) and, from four letters on, single-letter typos match, and the best match is listed first. It is on by default; switching it off in the settings brings back the previous search (fuzzy finder and its strength) unchanged.
- **The search area in the drawer was enlarged** and a gap was added between it and the app list.
- **Word of the Day was removed.** The home screen text, the widget, its settings, the word-list import menu and the bundled word lists are gone.
- **Name and identity changed.** The app is called "Panther Launcher" and uses the `app.pantherlauncher` ID, so it can be installed alongside the original mLauncher.
- **The About screen was simplified.** Donation links, community support (Discord) and "Share Application" were removed.
