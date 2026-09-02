# MGKomik Reader (Android WebView + CBZ)

Aplikasi Android **WebView** untuk **web1.mgkomik.cc** dengan:

- 🌐 **WebView** bawaan (login/cookie & Cloudflare clearance otomatis teratasi karena memakai WebView system).
- 🚫 **AdBlocker** — memblokir request iklan/tracker (host list + CSS hide).
- ⬇️ **Download chapter → CBZ** — ekstrak daftar gambar dari halaman chapter yang sedang dibuka, unduh semua halaman, lalu simpan sebagai arsip **CBZ** (zip berisi gambar).
- 📖 **Library offline** — daftar chapter tersimpan, bisa dibaca langsung **offline** dengan reader bawaan (pinch-zoom, double-tap, tombol prev/next), di-share, diekspor, atau diimpor file CBZ lain.

---

## Struktur

```
app/src/main/java/com/mgkomik/reader/
├── MainActivity.kt          # WebView utama + toolbar + alur download
├── adblock/AdBlocker.kt     # Host list & CSS hide
├── download/
│   ├── ChapterImageExtractor.kt  # Jalankan JS di WebView untuk ambil daftar URL gambar
│   └── ChapterDownloader.kt      # Unduh gambar -> tulis CBZ via OkHttp
├── library/
│   ├── BookRepository.kt    # Manajemen file CBZ + metadata JSON
│   ├── CbzArchive.kt        # Baca urutan halaman dari CBZ
│   ├── CbzWriter.kt         # Tulis CBZ inkremental
│   ├── Book.kt
│   └── LibraryActivity.kt   # Daftar chapter tersimpan
└── reader/
    ├── ReaderActivity.kt    # Pembaca offline (ViewPager2)
    ├── PageAdapter.kt       # Decode halaman lazy
    └── ZoomImageView.kt     # Pinch-zoom / pan / double-tap
```

## Build

Butuh **JDK 17+** dan **Android SDK** (Android Studio langsung bisa).

```bash
# Windows
gradlew.bat assembleDebug

# macOS / Linux
./gradlew assembleDebug
```

APK hasil: `app/build/outputs/apk/debug/app-debug.apk`

> Catatan: project ini dibuat tanpa environment Android di mesin ini, jadi belum di-build di sini. Buka di Android Studio → biarkan Gradle sinkron → Run. Jika Gradle wrapper jar perlu regenerasi, jalankan `gradle wrapper` di folder project dengan Gradle terpasang.

## Cara pakai

1. Buka app → WebView menampilkan **web1.mgkomik.cc**.
2. Telusuri komik, buka halaman **chapter** (gambar-gambar halaman).
3. Ketuk ikon **Download (CBZ)** di toolbar.
   - Aplikasi menjalankan skrip di halaman untuk mengumpulkan URL gambar chapter.
   - Dialog konfirmasi muncul (jumlah halaman).
   - Progress unduh tampil; hasil disimpan otomatis ke **Perpustakaan**.
4. Buka **Perpustakaan** (ikon buku) → daftar **komik** (folder per judul) → ketuk komik → daftar **chapter** → baca offline.
   - Reader bergaya **Webtoon**: semua halaman tersusun vertikal, scroll kontinu.
   - Tap layar untuk toggle bar atas (back, label halaman, share CBZ).

## Struktur penyimpanan offline

Setiap komik tersimpan dalam folder sendiri, jadi chapter dari judul yang sama
terkumpul rapi:

```
<externalFilesDir>/library/
└── Punishing Gray Raven/
    ├── Punishing Gray Raven - Chapter 05.cbz
    └── Punishing Gray Raven - Chapter 06.cbz
```

- Download baru otomatis masuk ke folder judul komiknya.
- File lama (sebelum struktur folder) dimigrasikan otomatis saat Perpustakaan dibuka.
- Hapus komik = hapus folder beserta semua chapter-nya.

## Catatan AdBlocker

- Host list bersifat *suffix match* (subdomain ikut diblokir).
- Situs utama (`web1.mgkomik.cc` dan semua subdomain `*.mgkomik.cc`) **tidak** diblokir, **kecuali** resource iklan in-house:
  - Gambar banner di path `/banner/` (mis. `koko88.gif`, `rusia777.gif`, `kaiko.gif`, `arab777.gif`)
  - Gambar dengan nama mengandung kata/brand judi/slot (`slot`, `togel`, `judi`, `gaza88`, `klikhoki`, dll.)
- CSS hide diterapkan di `onPageFinished` **plus skrip penghapusan DOM** (`removeAdsScript`) yang menghapus elemen iklan sepenuhnya — gambar banner, link pembungkusnya, iframe, dan container iklan generik — lalu diulang 1,5s & 4s kemudian untuk menangkap iklan yang muncul belakangan.
- Navigasi ke URL iklan juga diblokir di `shouldOverrideUrlLoading` (lapisan pengaman terakhir).
- Extractor gambar chapter juga menyaring URL banner agar tidak ikut ter-download ke CBZ.
- Jika iklan tertentu masih muncul, tambahkan ke `BLOCKED_HOSTS` / `AD_FILE_KEYWORDS` / `CSS_HIDE` / regex di `removeAdsScript` pada `AdBlocker.kt`.

> **Kenapa bukan DNS ad-blocker?** Iklan di situs ini di-host di domain situs sendiri (`id.mgkomik.cc/banner/`), jadi DNS blokir (AdGuard dkk) tidak bisa memblokirnya tanpa mematikan seluruh situs. Solusi yang dipakai: hapus elemen dari DOM + blokir request/navigasi.

## Catatan Download

- Ekstraksi gambar memakai `evaluateJavascript` callback (tanpa `JavascriptInterface`), dengan beberapa strategi:
  - Container reader umum (`#reader-area`, `.reader img`, `#chapter-content img`, dst)
  - Semua `<img>` yang URL-nya mirip gambar chapter (fallback)
  - Hasil difilter untuk membuang banner iklan
- Gambar chapter di situs ini di-host di **`static.mgis.my.id`** (path `WP-manga/data/...`), diunduh OkHttp dengan `Referer` + UA mobile.
- Format file: `.cbz` (zip berisi gambar bernama `0001.webp`, `0002.webp`, ...). Kompatibel dengan reader lain (Koreader, Perfect Viewer, dll).
- Nama file download otomatis: `{Judul Komik} - Chapter {nomor}.cbz` (judul diekstrak dari slug URL, contoh: `Punishing Gray Raven - Chapter 05.cbz`).
- Hasil uji nyata di HP (Android 13): chapter 05 Punishing Gray Raven → 12 halaman berhasil di-download sebagai CBZ (797 KB) dengan nama yang benar dan dibaca offline di reader bawaan.

## Terverifikasi di Perangkat

Diuji langsung di HP Android (USB debugging):
1. WebView memuat web1.mgkomik.cc (login/cookie & Cloudflare clearance otomatis).
2. Iklan judi/slot (KOKO88, RUSIA 777, KAIKO, ARAB 777) **dihapus total dari DOM** — tidak ada sisa ruang kosong, tidak ada link yang bisa diklik (verifikasi DOM: `adImgs:[]`, `adLinks:[]`, `leftover:[]`).
3. Download chapter → CBZ: dialog konfirmasi (12 halaman) → progress → tersimpan ke Perpustakaan.
4. Reader offline **gaya Webtoon**: halaman tersusun vertikal, scroll kontinu antar halaman berfungsi.

