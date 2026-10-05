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

- Çökme kaydını `crash.5646316.xyz` sunucusuna gönderen, devre dışı duran kod tamamen silindi.
- Çökme raporu e-postası artık kaynak projenin geliştiricisine gitmiyor; alıcıyı kullanıcı yazar.
- Eski alan adına (`5646316.xyz`) giden tema indirme ve gizlilik politikası bağlantıları kaldırıldı.

### B – Hata düzeltmeleri

- Hızlı art arda iki kaydırmadan sonra çekmecenin kalıcı olarak açılmaz hale gelmesi giderildi.
- Çekmece artık yalnızca aşağı çekilince kapanıyor; art arda kaydırmalarda açılıp kapanmıyor.
- Yeniden adlandırılan uygulamanın takma adı artık ana ekranda her modda görünüyor.
- Yavaş yapılan kaydırma hareketleri artık doğru algılanıyor.
- Ana ekrandaki uygulamalar dokunur dokunmaz açılıyor; 0,3 saniyelik bekleme kalktı.
- "Sistemi izle" teması, koyu mod zamanlanmış ya da otomatik olduğunda da doğru çalışıyor.
- Gizlenen uygulamalar, "Son kullanılanları göster" açıkken artık çekmecede ve aramada görünmüyor.
- Türkçe çevirideki 349 metnin 106'sı düzeltildi.

### C – Keyfî GUI tasarımı

- Adın uzunluğuna bağlı olmayan, kelime başlarını ve tek harflik yazım hatalarını eşleştiren "Akıllı arama" eklendi; kapatılınca eski arama geri geliyor.
- Çekmecedeki arama alanı büyütüldü ve listeyle arasına boşluk eklendi.
- Günün Sözü tüm parçalarıyla kaldırıldı.
- Uygulamanın adı "Panther Launcher", kimliği `app.pantherlauncher` oldu.
- Hakkında ekranından bağış, Discord ve "Uygulamayı Paylaş" bağlantıları kaldırıldı.

---

<a id="en"></a>

## English

Changes are made in 3 categories:

- **A – Security**
- **B – Bug fixes**
- **C – Discretionary GUI design**

This file summarises where Panther Launcher departs from its source, [mLauncher (Multi Launcher)](https://github.com/CodeWorksCreativeHub/mLauncher). The details are in the git history.

### A – Security

- The disabled code that uploaded crash logs to the `crash.5646316.xyz` server was deleted entirely.
- The crash report e-mail no longer goes to the source project's developer; the user enters the recipient.
- The theme download and privacy policy links to the old domain (`5646316.xyz`) were removed.

### B – Bug fixes

- The drawer no longer becomes permanently impossible to open after two swipes in quick succession.
- The drawer now closes only when pulled down, so it no longer opens and closes on repeated swipes.
- A renamed app's alias is now shown on the home screen in every mode.
- Slow swipe gestures are now detected correctly.
- Apps on the home screen open as soon as they are tapped; the 0.3-second wait is gone.
- The "follow system" theme now also works when dark mode is scheduled or automatic.
- Hidden apps no longer appear in the drawer or in search when "Show recent apps" is on.
- 106 of the 349 strings in the Turkish translation were corrected.

### C – Discretionary GUI design

- "Smart Search" was added: it does not depend on the length of the name and matches word beginnings and single-letter typos; switching it off brings back the previous search.
- The search area in the drawer was enlarged and a gap was added between it and the list.
- Word of the Day was removed with all its parts.
- The app is now named "Panther Launcher" with the ID `app.pantherlauncher`.
- Donation, Discord and "Share Application" links were removed from the About screen.
