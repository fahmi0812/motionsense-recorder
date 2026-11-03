package id.ac.pcr.motionsense.recorder;

import android.content.Context;
import android.content.SharedPreferences;

/** URL/anon key: nilai dari layar Pengaturan (SharedPreferences) menimpa nilai BuildConfig dari local.properties. */
public final class Config {
  private Config() {}
  static SharedPreferences p(Context c) { return c.getSharedPreferences("cfg", Context.MODE_PRIVATE); }
  public static String url(Context c) {
    String v = p(c).getString("url", "");
    if (v.isEmpty()) v = BuildConfig.SUPABASE_URL;
    while (v.endsWith("/")) v = v.substring(0, v.length() - 1);
    return v;
  }
  public static String key(Context c) {
    String v = p(c).getString("key", "");
    return v.isEmpty() ? BuildConfig.SUPABASE_ANON_KEY : v;
  }
  public static boolean ok(Context c) { return url(c).startsWith("https://") && !key(c).isEmpty(); }
}
