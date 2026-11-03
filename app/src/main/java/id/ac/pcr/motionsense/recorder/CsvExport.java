package id.ac.pcr.motionsense.recorder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

/** Ekspor sesi ke CSV format training: session_id,ts,ax,ay,az,gx,gy,gz,label (+ user_id di kolom terakhir). */
public final class CsvExport {
  private CsvExport() {}
  public static final String HEADER = "session_id,ts,ax,ay,az,gx,gy,gz,label,user_id\n";

  public static File toCache(Context ctx, String sid) throws Exception {
    File dir = new File(ctx.getCacheDir(), "exports"); dir.mkdirs();
    File f = new File(dir, "motionsense_" + sid + ".csv");
    try (OutputStream os = new FileOutputStream(f)) { write(ctx, sid, os); }
    return f;
  }

  /** Simpan ke folder Download publik (Android 10+). @return uri atau null. */
  public static Uri toDownloads(Context ctx, String sid) throws Exception {
    if (Build.VERSION.SDK_INT < 29) return null;
    ContentValues v = new ContentValues();
    v.put(MediaStore.Downloads.DISPLAY_NAME, "motionsense_" + sid + ".csv");
    v.put(MediaStore.Downloads.MIME_TYPE, "text/csv");
    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/MotionSense");
    Uri uri = ctx.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
    try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) { write(ctx, sid, os); }
    return uri;
  }

  private static void write(Context ctx, String sid, OutputStream os) throws Exception {
    Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8);
    w.write(HEADER);
    Cursor c = Db.get(ctx).sessionRows(sid);
    try {
      StringBuilder sb = new StringBuilder();
      while (c.moveToNext()) {
        sb.setLength(0);
        sb.append(c.getString(0)).append(',').append(c.getString(1));
        for (int i = 2; i <= 7; i++) sb.append(',').append(c.getDouble(i));
        sb.append(',').append(c.getString(8)).append(',').append(c.getString(9)).append('\n');
        w.write(sb.toString());
      }
    } finally { c.close(); }
    w.flush();
  }
}
