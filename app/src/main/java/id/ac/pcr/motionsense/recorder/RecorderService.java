package id.ac.pcr.motionsense.recorder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;

/**
 * Foreground service: merekam akselerometer (m/s², termasuk gravitasi) + giroskop (rad/s) dengan
 * samplingPeriodUs=20000 (50 Hz diminta). Satu baris per event akselerometer, dipasangkan dengan
 * nilai giroskop terakhir. Timestamp = waktu event sensor dikonversi ke UTC (ISO-8601, mikrodetik).
 */
public class RecorderService extends Service implements SensorEventListener {
  public static final String EXTRA_USER = "user", EXTRA_LABEL = "label";
  private static final String CH = "rec";

  // Status untuk UI (dibaca MainActivity)
  public static volatile boolean running = false;
  public static volatile String sessionId = "";
  public static volatile long samples = 0;
  public static volatile double hz = 0;
  public static volatile long startedElapsed = 0;

  private SensorManager sm;
  private HandlerThread sensorThread, ioThread;
  private Handler sensorH, ioH;
  private PowerManager.WakeLock wl;
  private final float[] gyro = new float[3];
  private long offsetNs; // epochNs - elapsedRealtimeNanos
  private String user, label;
  private List<Object[]> buf = new ArrayList<>();
  private final long[] recent = new long[51];
  private int ri = 0;
  private SimpleDateFormat fmt;

  @Override public IBinder onBind(Intent i) { return null; }

  @Override public int onStartCommand(Intent intent, int flags, int startId) {
    if (running || intent == null) return START_NOT_STICKY;
    user = intent.getStringExtra(EXTRA_USER);
    label = intent.getStringExtra(EXTRA_LABEL);
    sessionId = UUID.randomUUID().toString();
    samples = 0; hz = 0; startedElapsed = SystemClock.elapsedRealtime();
    fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
    fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
    offsetNs = System.currentTimeMillis() * 1_000_000L - SystemClock.elapsedRealtimeNanos();
    Db.get(this).addSession(sessionId, user, label, iso(System.currentTimeMillis() * 1_000_000L));

    startFg();
    PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "motionsense:rec");
    wl.acquire(6 * 60 * 60 * 1000L);

    ioThread = new HandlerThread("io"); ioThread.start(); ioH = new Handler(ioThread.getLooper());
    sensorThread = new HandlerThread("sensor"); sensorThread.start(); sensorH = new Handler(sensorThread.getLooper());
    sm = (SensorManager) getSystemService(SENSOR_SERVICE);
    sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 20000, sensorH);
    sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 20000, sensorH);
    running = true;
    ioH.postDelayed(tick, 2000);
    return START_NOT_STICKY;
  }

  private void startFg() {
    NotificationManager nm = getSystemService(NotificationManager.class);
    if (Build.VERSION.SDK_INT >= 26)
      nm.createNotificationChannel(new NotificationChannel(CH, "Perekaman", NotificationManager.IMPORTANCE_LOW));
    PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
    Notification n = new NotificationCompat.Builder(this, CH)
        .setContentTitle("MotionSense merekam")
        .setContentText(user + " • " + label)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setOngoing(true).setContentIntent(pi).build();
    int type = Build.VERSION.SDK_INT >= 34 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH : 0;
    ServiceCompat.startForeground(this, 1, n, type);
  }

  /** Setiap 2 dtk: pindahkan buffer RAM ke SQLite lalu coba upload. */
  private final Runnable tick = new Runnable() {
    @Override public void run() {
      persist();
      Uploader.flush(RecorderService.this, 5);
      if (running) ioH.postDelayed(this, 2000);
    }
  };

  private void persist() {
    List<Object[]> out;
    synchronized (this) { out = buf; buf = new ArrayList<>(); }
    if (!out.isEmpty()) Db.get(this).insertBatch(sessionId, user, label, out);
  }

  private String iso(long epochNs) {
    long ms = epochNs / 1_000_000L;
    long us = (epochNs / 1000L) % 1_000_000L;
    return fmt.format(new Date(ms)) + String.format(Locale.US, ".%06dZ", us);
  }

  @Override public void onSensorChanged(SensorEvent e) {
    if (e.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
      System.arraycopy(e.values, 0, gyro, 0, 3);
      return;
    }
    long epochNs = e.timestamp + offsetNs;
    Object[] row = {iso(epochNs), (double) e.values[0], (double) e.values[1], (double) e.values[2],
        (double) gyro[0], (double) gyro[1], (double) gyro[2]};
    synchronized (this) { buf.add(row); }
    samples++;
    // Hz aktual dari 50 event terakhir
    recent[ri % recent.length] = e.timestamp; ri++;
    if (ri >= recent.length) {
      long oldest = recent[ri % recent.length];
      hz = (recent.length - 1) * 1e9 / (e.timestamp - oldest);
    }
  }

  @Override public void onAccuracyChanged(Sensor s, int a) {}

  @Override public void onDestroy() {
    running = false;
    if (sm != null) sm.unregisterListener(this);
    if (ioH != null) {
      ioH.removeCallbacks(tick);
      ioH.post(() -> { persist(); Uploader.flush(getApplicationContext(), 50); ioThread.quitSafely(); });
    }
    if (sensorThread != null) sensorThread.quitSafely();
    if (wl != null && wl.isHeld()) wl.release();
    super.onDestroy();
  }
}
