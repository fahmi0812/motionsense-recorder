package id.ac.pcr.motionsense.recorder;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

/** Buffer lokal SQLite. Baris hanya dihapus status-nya (uploaded=1) setelah server mengonfirmasi 2xx. */
public class Db extends SQLiteOpenHelper {
  private static Db inst;
  public static synchronized Db get(Context c) {
    if (inst == null) inst = new Db(c.getApplicationContext());
    return inst;
  }
  private Db(Context c) { super(c, "motionsense.db", null, 1); }

  @Override public void onCreate(SQLiteDatabase db) {
    db.execSQL("CREATE TABLE readings (id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL, user_id TEXT NOT NULL," +
        " label TEXT NOT NULL, ts TEXT NOT NULL, ax REAL, ay REAL, az REAL, gx REAL, gy REAL, gz REAL, uploaded INTEGER NOT NULL DEFAULT 0)");
    db.execSQL("CREATE INDEX idx_up ON readings(uploaded, id)");
    db.execSQL("CREATE INDEX idx_sess ON readings(session_id, id)");
    db.execSQL("CREATE TABLE sessions (session_id TEXT PRIMARY KEY, user_id TEXT, label TEXT, started_at TEXT, n INTEGER DEFAULT 0)");
  }
  @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}

  public void addSession(String sid, String user, String label, String startedAt) {
    ContentValues v = new ContentValues();
    v.put("session_id", sid); v.put("user_id", user); v.put("label", label); v.put("started_at", startedAt);
    getWritableDatabase().insert("sessions", null, v);
  }

  /** Simpan satu batch dalam satu transaksi. row = {ts(String) dan 6 double}. */
  public void insertBatch(String sid, String user, String label, java.util.List<Object[]> rows) {
    SQLiteDatabase db = getWritableDatabase();
    SQLiteStatement st = db.compileStatement(
        "INSERT INTO readings(session_id,user_id,label,ts,ax,ay,az,gx,gy,gz) VALUES(?,?,?,?,?,?,?,?,?,?)");
    db.beginTransaction();
    try {
      for (Object[] r : rows) {
        st.clearBindings();
        st.bindString(1, sid); st.bindString(2, user); st.bindString(3, label); st.bindString(4, (String) r[0]);
        for (int i = 1; i <= 6; i++) st.bindDouble(4 + i, (Double) r[i]);
        st.executeInsert();
      }
      db.execSQL("UPDATE sessions SET n = n + ? WHERE session_id = ?", new Object[]{rows.size(), sid});
      db.setTransactionSuccessful();
    } finally { db.endTransaction(); }
  }

  public Cursor pending(int limit) {
    return getReadableDatabase().rawQuery(
        "SELECT id,session_id,user_id,label,ts,ax,ay,az,gx,gy,gz FROM readings WHERE uploaded=0 ORDER BY id LIMIT " + limit, null);
  }
  public void markUploaded(long fromId, long toId) {
    getWritableDatabase().execSQL("UPDATE readings SET uploaded=1 WHERE uploaded=0 AND id BETWEEN ? AND ?", new Object[]{fromId, toId});
  }
  public long count(String where) {
    Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM readings" + (where == null ? "" : " WHERE " + where), null);
    try { c.moveToFirst(); return c.getLong(0); } finally { c.close(); }
  }
  public Cursor sessions() {
    return getReadableDatabase().rawQuery("SELECT session_id,user_id,label,started_at,n FROM sessions ORDER BY started_at DESC", null);
  }
  public Cursor sessionRows(String sid) {
    return getReadableDatabase().rawQuery(
        "SELECT session_id,ts,ax,ay,az,gx,gy,gz,label,user_id FROM readings WHERE session_id=? ORDER BY id", new String[]{sid});
  }
}
