package id.ac.pcr.motionsense.recorder;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
  private EditText etName;
  private Spinner spLabel;
  private Button btn;
  private TextView tvStatus, tvSync;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final ExecutorService bg = Executors.newSingleThreadExecutor();
  private SharedPreferences prefs;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    setContentView(R.layout.activity_main);
    prefs = getSharedPreferences("ui", MODE_PRIVATE);
    etName = findViewById(R.id.etName);
    spLabel = findViewById(R.id.spLabel);
    btn = findViewById(R.id.btnStartStop);
    tvStatus = findViewById(R.id.tvStatus);
    tvSync = findViewById(R.id.tvSync);
    etName.setText(prefs.getString("name", ""));
    ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Labels.DISPLAY);
    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spLabel.setAdapter(ad);
    btn.setOnClickListener(v -> toggle());
    findViewById(R.id.btnSync).setOnClickListener(v -> bg.execute(() -> {
      int n = Uploader.flush(this, 1000);
      ui.post(() -> Toast.makeText(this, "Terkirim " + n + " baris" +
          (Uploader.lastError.isEmpty() ? "" : " — " + Uploader.lastError), Toast.LENGTH_LONG).show());
    }));
    findViewById(R.id.btnExport).setOnClickListener(v -> chooseSession());
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
  }

  private void toggle() {
    if (RecorderService.running) {
      stopService(new Intent(this, RecorderService.class));
    } else {
      String name = etName.getText().toString().trim();
      if (name.isEmpty()) { etName.setError("Isi nama perekam"); return; }
      if (!Config.ok(this))
        Toast.makeText(this, "Supabase belum diatur — data disimpan lokal dulu", Toast.LENGTH_LONG).show();
      prefs.edit().putString("name", name).apply();
      Intent i = new Intent(this, RecorderService.class)
          .putExtra(RecorderService.EXTRA_USER, name)
          .putExtra(RecorderService.EXTRA_LABEL, Labels.CODE[spLabel.getSelectedItemPosition()]);
      ContextCompat.startForegroundService(this, i);
    }
    ui.postDelayed(this::refresh, 300);
  }

  private final Runnable loop = new Runnable() {
    @Override public void run() { refresh(); ui.postDelayed(this, 500); }
  };

  private void refresh() {
    boolean r = RecorderService.running;
    btn.setText(r ? "STOP" : "START");
    etName.setEnabled(!r); spLabel.setEnabled(!r);
    if (r) {
      long sec = (SystemClock.elapsedRealtime() - RecorderService.startedElapsed) / 1000;
      tvStatus.setText(String.format(Locale.US, "Merekam %02d:%02d\nSampel: %d\nHz aktual: %.1f\nSesi: %s",
          sec / 60, sec % 60, RecorderService.samples, RecorderService.hz, RecorderService.sessionId));
    } else {
      tvStatus.setText(RecorderService.sessionId.isEmpty() ? "Siap" :
          "Berhenti. Sesi terakhir: " + RecorderService.sessionId + "\nSampel: " + RecorderService.samples);
    }
    bg.execute(() -> {
      long pend = Db.get(this).count("uploaded=0"), up = Db.get(this).count("uploaded=1");
      String err = Uploader.lastError;
      ui.post(() -> tvSync.setText("Terkirim ke Supabase: " + up + "\nAntre (belum terkirim): " + pend +
          (err.isEmpty() ? "" : "\n⚠ " + err)));
    });
  }

  private void chooseSession() {
    bg.execute(() -> {
      List<String> ids = new ArrayList<>(), names = new ArrayList<>();
      Cursor c = Db.get(this).sessions();
      try {
        while (c.moveToNext()) {
          ids.add(c.getString(0));
          names.add(c.getString(3) + " • " + c.getString(1) + " • " + c.getString(2) + " (" + c.getLong(4) + ")");
        }
      } finally { c.close(); }
      ui.post(() -> {
        if (ids.isEmpty()) { Toast.makeText(this, "Belum ada sesi", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Ekspor sesi ke CSV")
            .setItems(names.toArray(new String[0]), (d, w) -> export(ids.get(w))).show();
      });
    });
  }

  private void export(String sid) {
    bg.execute(() -> {
      try {
        Uri dl = CsvExport.toDownloads(this, sid);
        File f = CsvExport.toCache(this, sid);
        Uri share = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
        ui.post(() -> {
          if (dl != null) Toast.makeText(this, "Disimpan di Download/MotionSense", Toast.LENGTH_LONG).show();
          Intent s = new Intent(Intent.ACTION_SEND).setType("text/csv")
              .putExtra(Intent.EXTRA_STREAM, share).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
          startActivity(Intent.createChooser(s, "Bagikan CSV"));
        });
      } catch (Exception e) {
        ui.post(() -> Toast.makeText(this, "Gagal ekspor: " + e.getMessage(), Toast.LENGTH_LONG).show());
      }
    });
  }

  @Override protected void onResume() { super.onResume(); ui.post(loop); }
  @Override protected void onPause() { super.onPause(); ui.removeCallbacks(loop); }

  @Override public boolean onCreateOptionsMenu(Menu m) { m.add(0, 1, 0, "Pengaturan Supabase"); return true; }
  @Override public boolean onOptionsItemSelected(MenuItem it) {
    if (it.getItemId() == 1) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
    return super.onOptionsItemSelected(it);
  }
}
