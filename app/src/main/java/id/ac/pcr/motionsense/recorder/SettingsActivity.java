package id.ac.pcr.motionsense.recorder;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    setContentView(R.layout.activity_settings);
    EditText url = findViewById(R.id.etUrl), key = findViewById(R.id.etKey);
    url.setText(Config.url(this));
    key.setText(Config.p(this).getString("key", ""));
    key.setHint(BuildConfig.SUPABASE_ANON_KEY.isEmpty() ? "anon public key" : "(pakai key dari local.properties)");
    Button save = findViewById(R.id.btnSave);
    save.setOnClickListener(v -> {
      Config.p(this).edit().putString("url", url.getText().toString().trim())
          .putString("key", key.getText().toString().trim()).apply();
      Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show();
      finish();
    });
  }
}
