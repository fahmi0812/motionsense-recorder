# MotionSense Recorder

Aplikasi Android (Java) untuk merekam data mentah **akselerometer + giroskop** (50 Hz) dan mengirimkannya ke **Supabase** (tabel `sensor_readings`) sebagai data privat untuk Big Project ML for IoT **MotionSense** (PCR 3 TRK A, Kelompok 1: Fahmi & Abid).

Repo pasangan (training + web): **https://github.com/fahmi0812/motionsense-har**

> Dibangun dari [InfluxCommunity/SensorLogger](https://github.com/InfluxCommunity/SensorLogger) (lisensi MIT, © penulis aslinya — lihat `LICENSE`). Kode upload InfluxDB dan grafik sudah diganti dengan perekaman + upload Supabase.

## Fitur
- Akselerometer (m/s², termasuk gravitasi) + giroskop (rad/s), `samplingPeriodUs = 20000` (50 Hz diminta). Satu baris per event akselerometer, dipasangkan dengan nilai giroskop terakhir.
- `ts` = timestamp event sensor yang dikonversi ke waktu UTC, format ISO-8601 dengan mikrodetik (mis. `2026-11-03T05:01:02.123456Z`).
- Input nama perekam (bebas, mis. `Fahmi`/`Abid` → kolom `user_id`) dan dropdown 6 aktivitas:

  | Tampilan | Label yang disimpan (sama dengan `common.py` & CHECK di Supabase) |
  |---|---|
  | berjalan | `WALKING` |
  | naik_tangga | `WALKING_UPSTAIRS` |
  | turun_tangga | `WALKING_DOWNSTAIRS` |
  | duduk | `SITTING` |
  | berdiri | `STANDING` |
  | berbaring | `LAYING` |

- Tiap START = `session_id` baru (UUID murni, karena kolom Supabase bertipe `uuid`).
- Foreground service + wake lock → tetap merekam saat layar mati.
- Buffer lokal SQLite; tiap 2 detik dikirim batch 100 baris ke `POST /rest/v1/sensor_readings` (header `apikey`, `Authorization: Bearer`, `Prefer: return=minimal,resolution=ignore-duplicates`, `on_conflict=session_id,ts`). Gagal → data tetap di HP dan dicoba ulang (tombol **Kirim ulang sekarang** juga ada). Data lokal tidak pernah dihapus otomatis.
- Ekspor sesi ke CSV (`Download/MotionSense/` + share intent) dengan header `session_id,ts,ax,ay,az,gx,gy,gz,label,user_id` — langsung bisa dibaca `training/02_siapkan_privat.py --csv`.

## Mengatur Supabase URL & anon key
Kredensial **tidak** di-hardcode. Pilih salah satu:
1. **Saat build:** salin `local.properties.example` → `local.properties` (sudah di `.gitignore`), isi:
   ```
   SUPABASE_URL=https://xxxx.supabase.co
   SUPABASE_ANON_KEY=eyJ...   # anon public key, BUKAN service_role
   ```
2. **Di aplikasi:** menu ⋮ → **Pengaturan Supabase** → isi URL dan anon key → Simpan (menimpa nilai dari build).

Tabel & RLS: ikuti `docs/SETUP-Supabase.md` di repo motionsense-har (anon hanya boleh INSERT dengan `sumber='privat'`; aplikasi mengirim `sumber='privat'` dan `sampling_rate=50`).

## Build APK
**Android Studio:** File → Open → folder repo ini → tunggu Gradle sync → Build → Build APK(s). APK: `app/build/outputs/apk/debug/app-debug.apk`.

**Command line** (JDK 17 + Android SDK 34):
```bash
cp local.properties.example local.properties   # isi sdk.dir + kredensial
./gradlew assembleDebug
```

## Cara merekam (sesuai README motionsense-har)
1. Isi nama perekam, pilih aktivitas, tekan **START**.
2. Masukkan HP ke **saku/klip di pinggang** (posisi & orientasi sama setiap sesi).
3. Lakukan aktivitas **±2 menit per aktivitas per orang** (Fahmi dan Abid masing-masing 6 aktivitas).
4. Ambil HP, tekan **STOP**. Periksa "Antre (belum terkirim)" turun ke 0; bila tidak ada internet, data tetap aman dan dikirim nanti.
5. Cadangan: **Ekspor sesi ke CSV**.

Catatan: Hz aktual tergantung HP (beberapa HP memberi sedikit di atas/bawah 50 Hz) — training melakukan resample ke 50 Hz.

## Lisensi
MIT — lihat `LICENSE` (hak cipta upstream tetap dipertahankan).
