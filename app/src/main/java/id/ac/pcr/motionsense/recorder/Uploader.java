package id.ac.pcr.motionsense.recorder;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Kirim baris yang belum ter-upload ke Supabase REST, batch 100. Idempoten via on_conflict(session_id,ts). */
public class Uploader {
  public static final int BATCH = 100;
  public static volatile String lastError = "";

  /** @return jumlah baris yang berhasil dikirim pada panggilan ini (berhenti saat gagal pertama). */
  public static synchronized int flush(Context ctx, int maxBatches) {
    if (!Config.ok(ctx)) { lastError = "URL/key Supabase belum diisi"; return 0; }
    Db db = Db.get(ctx);
    int sent = 0;
    for (int b = 0; b < maxBatches; b++) {
      long first = -1, last = -1;
      JSONArray arr = new JSONArray();
      Cursor c = db.pending(BATCH);
      try {
        while (c.moveToNext()) {
          if (first < 0) first = c.getLong(0);
          last = c.getLong(0);
          JSONObject o = new JSONObject();
          o.put("session_id", c.getString(1));
          o.put("user_id", c.getString(2));
          o.put("label", c.getString(3));
          o.put("ts", c.getString(4));
          o.put("ax", c.getDouble(5)); o.put("ay", c.getDouble(6)); o.put("az", c.getDouble(7));
          o.put("gx", c.getDouble(8)); o.put("gy", c.getDouble(9)); o.put("gz", c.getDouble(10));
          o.put("sampling_rate", 50);
          o.put("sumber", "privat");
          arr.put(o);
        }
      } catch (Exception e) { lastError = e.toString(); return sent; } finally { c.close(); }
      if (arr.length() == 0) { lastError = ""; break; }
      int code = post(ctx, arr.toString());
      if (code >= 200 && code < 300) {
        db.markUploaded(first, last);
        sent += arr.length();
        lastError = "";
      } else {
        lastError = "HTTP " + code + " (akan dicoba ulang)";
        Log.w("Uploader", lastError);
        break; // data tetap di buffer, dicoba lagi nanti
      }
    }
    return sent;
  }

  private static int post(Context ctx, String body) {
    HttpURLConnection con = null;
    try {
      URL u = new URL(Config.url(ctx) + "/rest/v1/sensor_readings?on_conflict=session_id,ts");
      con = (HttpURLConnection) u.openConnection();
      con.setConnectTimeout(10000); con.setReadTimeout(15000);
      con.setRequestMethod("POST"); con.setDoOutput(true);
      String k = Config.key(ctx);
      con.setRequestProperty("apikey", k);
      con.setRequestProperty("Authorization", "Bearer " + k);
      con.setRequestProperty("Content-Type", "application/json");
      con.setRequestProperty("Prefer", "return=minimal,resolution=ignore-duplicates");
      try (OutputStream os = con.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
      int code = con.getResponseCode();
      if (code >= 300) {
        try (java.io.InputStream es = con.getErrorStream()) {
          if (es != null) { byte[] buf = new byte[400]; int n = es.read(buf); if (n > 0) Log.w("Uploader", new String(buf, 0, n)); }
        }
      }
      return code;
    } catch (Exception e) {
      lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
      return -1;
    } finally { if (con != null) con.disconnect(); }
  }
}
